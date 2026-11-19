package com.example.taskreminder

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * مخزن برنامه‌ها: الگوها + نوبت‌ها. همهٔ تغییرها زیر یک Mutex و هر نوبت یک ردیف مستقل است
 * (ایندکس یکتای templateId+dueDate مانع ثبت دوباره می‌شود).
 */
class PlanRepository private constructor(private val ctx: Context) {
    private val db = AppDatabase.get(ctx)
    private val dao = db.planDao()
    private val mutex = Mutex()

    fun observeItems(fromEpochDay: Long): Flow<List<PlanItem>> =
        combine(dao.observeTemplates(), dao.observeInstances(fromEpochDay)) { ts, ins ->
            val m = ts.associateBy { it.id }
            ins.mapNotNull { i -> m[i.templateId]?.let { PlanItem(i, it) } }
        }

    fun observeTemplates(): Flow<List<TemplateEntity>> = dao.observeTemplates()

    private fun today(): LocalDate = LocalDate.now()

    // ---------- کمکی‌ها (فقط زیر قفل صدا زده شوند) ----------

    private suspend fun ensureInstance(t: TemplateEntity, date: LocalDate): InstanceEntity? {
        val i = Engine.newInstance(t, date)
        val rid = dao.insertInstance(i)
        if (rid == -1L) return null
        val saved = i.copy(id = rid)
        Scheduler.scheduleItem(ctx, PlanItem(saved, t), false)
        return saved
    }

    /** سررسیدهای بعدی را می‌سازد تا آخرین نوبت از امروز جلوتر باشد */
    private suspend fun chain(t: TemplateEntity) {
        if (t.paused || t.unit == Every.NONE) return
        val today = today().toEpochDay()
        var latest = dao.instancesOf(t.id).maxOfOrNull { it.dueDate } ?: return
        var n = 0
        while (latest <= today && n < 400) {
            val next = Engine.nextDue(t, LocalDate.ofEpochDay(latest)) ?: break
            ensureInstance(t, next)
            latest = next.toEpochDay()
            n++
        }
    }

    private suspend fun ensureNextAfter(t: TemplateEntity, currentDue: Long) {
        if (t.unit == Every.NONE) return
        if (dao.instancesOf(t.id).any { it.dueDate > currentDue }) return
        val next = Engine.nextDue(t, LocalDate.ofEpochDay(currentDue)) ?: return
        ensureInstance(t, next)
    }

    private suspend fun generate(t: TemplateEntity) {
        if (t.paused) return
        val today = today()
        when (t.kind) {
            Kind.DAY -> ensureInstance(t, LocalDate.ofEpochDay(t.startDate))
            Kind.WEEKLY -> Engine.weeklyDates(t, today, today.plusDays(Engine.HORIZON_DAYS.toLong()))
                .forEach { ensureInstance(t, it) }
            Kind.DUE -> {
                ensureInstance(t, LocalDate.ofEpochDay(t.startDate))
                chain(t)
            }
        }
    }

    private suspend fun removePending(list: List<InstanceEntity>) {
        list.forEach {
            Scheduler.cancelInstance(ctx, it.id)
            dao.deleteInstance(it.id)
        }
    }

    // ---------- عملیات عمومی ----------

    suspend fun save(t: TemplateEntity): Long = mutex.withLock {
        if (t.id == 0L) {
            val id = dao.insertTemplate(t)
            generate(t.copy(id = id))
            return@withLock id
        }
        val old = dao.template(t.id)
        dao.updateTemplate(t)
        val pending = dao.instancesOf(t.id).filter { it.status == Status.PENDING }
        if (old == null || Engine.scheduleChanged(old, t)) {
            val todayE = today().toEpochDay()
            val drop = pending.filter {
                when (t.kind) {
                    Kind.DAY -> true
                    Kind.WEEKLY -> it.dueDate >= todayE
                    else -> it.dueDate >= todayE || (old != null && it.dueDate == old.startDate)
                }
            }
            removePending(drop)
            generate(t)
        } else {
            pending.forEach { Scheduler.scheduleItem(ctx, PlanItem(it, t), false) }
        }
        t.id
    }

    suspend fun deleteTemplate(id: Long) {
        mutex.withLock {
            dao.instancesOf(id).forEach { Scheduler.cancelInstance(ctx, it.id) }
            dao.deleteInstancesOf(id)
            dao.deleteTemplate(id)
        }
    }

    suspend fun togglePause(id: Long) {
        mutex.withLock {
            val t = dao.template(id) ?: return@withLock
            val n = t.copy(paused = !t.paused)
            dao.updateTemplate(n)
            if (n.paused) {
                val todayE = today().toEpochDay()
                removePending(dao.instancesOf(id).filter { it.status == Status.PENDING && it.dueDate >= todayE })
            } else {
                generate(n)
            }
            Unit
        }
    }

    suspend fun setDone(id: Long, done: Boolean) {
        mutex.withLock {
            val i = dao.instance(id) ?: return@withLock
            val t = dao.template(i.templateId) ?: return@withLock
            if (done) {
                dao.updateInstance(
                    i.copy(status = Status.DONE, doneAt = System.currentTimeMillis(), nextAlertAt = 0L, snoozedUntil = 0L)
                )
                Scheduler.cancelInstance(ctx, id)
                if (t.kind == Kind.DUE) ensureNextAfter(t, i.dueDate)
            } else {
                val n = i.copy(status = Status.PENDING, doneAt = 0L)
                dao.updateInstance(n)
                Scheduler.scheduleItem(ctx, PlanItem(n, t), false)
            }
            Unit
        }
    }

    /** انتقال کار روزانه به فردا */
    suspend fun postpone(id: Long) {
        mutex.withLock {
            val i = dao.instance(id) ?: return@withLock
            val t = dao.template(i.templateId) ?: return@withLock
            if (t.kind != Kind.DAY || i.status != Status.PENDING) return@withLock
            val d = LocalDate.ofEpochDay(i.dueDate).plusDays(1)
            dao.updateInstance(i.copy(dueDate = d.toEpochDay(), dueAt = Engine.millis(d, i.timeMin)))
            dao.updateTemplate(t.copy(startDate = d.toEpochDay()))
            Unit
        }
    }

    suspend fun cancelInstance(id: Long) {
        mutex.withLock {
            val i = dao.instance(id) ?: return@withLock
            dao.updateInstance(i.copy(status = Status.CANCELED, nextAlertAt = 0L, snoozedUntil = 0L))
            Scheduler.cancelInstance(ctx, id)
            Unit
        }
    }

    /** یادآوری بعداً: هشدار بعدی بعد از فاصلهٔ تنظیم‌شده */
    suspend fun snooze(id: Long, now: Long) {
        mutex.withLock {
            val i = dao.instance(id) ?: return@withLock
            val t = dao.template(i.templateId) ?: return@withLock
            if (i.status != Status.PENDING) return@withLock
            val gap = if (t.alertGapMin > 0) t.alertGapMin else 10
            val next = now + gap * 60_000L
            val n = i.copy(nextAlertAt = next, snoozedUntil = next)
            dao.updateInstance(n)
            Scheduler.dismiss(ctx, id)
            Scheduler.scheduleItem(ctx, PlanItem(n, t), false)
        }
    }

    /**
     * رسیدن زمان هشدار: شمارندهٔ هشدار این نوبت را بالا می‌برد و هشدار بعدی را (تا سقف سه) می‌چیند.
     * اگر نباید اعلان داده شود null برمی‌گرداند.
     */
    suspend fun onAlert(id: Long, now: Long): PlanItem? = mutex.withLock {
        val i = dao.instance(id) ?: return@withLock null
        val t = dao.template(i.templateId) ?: return@withLock null
        if (i.status != Status.PENDING || !Engine.hasAlarm(t)) return@withLock null
        val target = if (i.nextAlertAt > 0) i.nextAlertAt else i.dueAt
        if (target > now + 60_000L) {
            Scheduler.scheduleItem(ctx, PlanItem(i, t), false)
            return@withLock null
        }
        val fired = i.alertsFired + 1
        val cap = t.maxAlerts.coerceIn(1, 3)
        val next = if (fired < cap && t.alertGapMin > 0) now + t.alertGapMin * 60_000L else 0L
        val n = i.copy(alertsFired = fired, nextAlertAt = next, snoozedUntil = 0L)
        dao.updateInstance(n)
        val item = PlanItem(n, t)
        Scheduler.scheduleItem(ctx, item, false)
        item
    }

    /** نگه‌داری: نوبت‌های هفتگی آینده، سررسیدهای بعدی، علامت «انجام‌نشده» و تنظیم دوبارهٔ آلارم‌ها */
    suspend fun sync(allowLate: Boolean) {
        mutex.withLock {
            dao.markMissedWeekly(today().toEpochDay())
            val ts = dao.allTemplates()
            for (t in ts) {
                if (t.paused) continue
                if (t.kind == Kind.WEEKLY) generate(t)
                if (t.kind == Kind.DUE) chain(t)
            }
            val byId = ts.associateBy { it.id }
            dao.pending().forEach { i ->
                val t = byId[i.templateId]
                if (t != null) Scheduler.scheduleItem(ctx, PlanItem(i, t), allowLate)
            }
        }
    }

    /** انتقال یک‌باره از جدول قدیمی tasks به الگو/نوبت */
    suspend fun ensureMigrated() {
        val flag = ctx.getSharedPreferences("migration", Context.MODE_PRIVATE)
        if (flag.getBoolean(FLAG, false)) return
        TaskRepository.get(ctx).migrateLegacyIfNeeded()
        mutex.withLock {
            if (flag.getBoolean(FLAG, false)) return@withLock
            val zone = ZoneId.systemDefault()
            val todayD = today()
            val now = System.currentTimeMillis()
            val old = db.taskDao().getAll().map { it.toTask() }
            old.forEach { Scheduler.cancelLegacy(ctx, it.id) }
            for (t in old) {
                val base = TemplateEntity(
                    title = t.title, kind = Kind.DUE, category = "personal", priority = 0, note = "",
                    startDate = 0L, endDate = 0L, timeMin = t.hour * 60 + t.minute, weekMask = 0,
                    unit = Every.NONE, repeatEvery = 1, monthDay = 0, amount = 0L, paused = false,
                    alarmSound = true, alarmVibrate = true, alarmNotify = true,
                    alertGapMin = 30, maxAlerts = 1, createdAt = now
                )
                when (t.repeat) {
                    Repeat.ONCE -> {
                        val date = LocalDate.ofEpochDay(t.startDate)
                        val tpl = base.copy(startDate = date.toEpochDay(), monthDay = Jalali.fromLocalDate(date).third)
                        val id = dao.insertTemplate(tpl)
                        val full = tpl.copy(id = id)
                        var inst = Engine.newInstance(full, date)
                        if (t.done) inst = inst.copy(status = Status.DONE, doneAt = now)
                        val rid = dao.insertInstance(inst)
                        if (!t.done) Scheduler.scheduleItem(ctx, PlanItem(inst.copy(id = rid), full), false)
                    }
                    Repeat.DAILY -> {
                        val start = LocalDate.ofEpochDay(t.startDate)
                        var end = 0L
                        if (t.maxCount > 0) {
                            val remaining = (t.maxCount - t.firedCount).coerceAtLeast(0)
                            val from = if (start.isAfter(todayD)) start else todayD
                            end = from.toEpochDay() + remaining - 1
                        }
                        val tpl = base.copy(
                            kind = Kind.WEEKLY, weekMask = 127, startDate = start.toEpochDay(),
                            endDate = end, paused = t.done
                        )
                        val id = dao.insertTemplate(tpl)
                        generate(tpl.copy(id = id))
                    }
                    Repeat.MONTHLY -> {
                        val due = Instant.ofEpochMilli(t.dueAt).atZone(zone).toLocalDate()
                        var end = 0L
                        if (t.maxCount > 0) {
                            val probe = base.copy(unit = Every.MONTH, monthDay = t.monthDay, startDate = due.toEpochDay())
                            var rem = (t.maxCount - t.firedCount - 1).coerceAtLeast(0)
                            var d = due
                            while (rem > 0) {
                                d = Engine.nextDue(probe, d) ?: break
                                rem--
                            }
                            end = d.toEpochDay()
                        }
                        val tpl = base.copy(
                            unit = Every.MONTH, monthDay = t.monthDay, startDate = due.toEpochDay(),
                            endDate = end, paused = t.done
                        )
                        val id = dao.insertTemplate(tpl)
                        generate(tpl.copy(id = id))
                    }
                }
            }
            flag.edit().putBoolean(FLAG, true).commit()
            Unit
        }
    }

    companion object {
        private const val FLAG = "tasks_to_plan_v2"
        @Volatile private var inst: PlanRepository? = null

        fun get(ctx: Context): PlanRepository = inst ?: synchronized(this) {
            inst ?: PlanRepository(ctx.applicationContext).also { inst = it }
        }
    }
}
