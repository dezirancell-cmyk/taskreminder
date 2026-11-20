package com.example.taskreminder

import java.time.LocalDate
import java.time.ZoneId

object Kind {
    const val DAY = "DAY"
    const val WEEKLY = "WEEKLY"
    const val DUE = "DUE"
}

object Every {
    const val NONE = "NONE"
    const val DAY = "DAY"
    const val WEEK = "WEEK"
    const val MONTH = "MONTH"
    const val YEAR = "YEAR"
}

object Status {
    const val PENDING = "PENDING"
    const val DONE = "DONE"
    const val MISSED = "MISSED"
    const val CANCELED = "CANCELED"
}

data class PlanItem(val inst: InstanceEntity, val tpl: TemplateEntity)

data class DayProgress(val total: Int, val done: Int, val remaining: Int, val overdue: Int, val percent: Int)

/** منطق خالص (بدون اندروید) برای ساخت نوبت‌ها، سررسید بعدی، هشدار و پیشرفت روزانه */
object Engine {
    const val HORIZON_DAYS = 14
    private const val LATE_WINDOW_MS = 6 * 3_600_000L

    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun millis(date: LocalDate, timeMin: Int): Long {
        val h = if (timeMin < 0) 0 else timeMin / 60
        val m = if (timeMin < 0) 0 else timeMin % 60
        return date.atTime(h, m).atZone(zone).toInstant().toEpochMilli()
    }

    /** شنبه = ۰ ... جمعه = ۶ */
    fun dayIndex(d: LocalDate): Int = (d.dayOfWeek.value + 1) % 7

    private fun later(a: LocalDate, b: LocalDate) = if (a.isAfter(b)) a else b
    private fun earlier(a: LocalDate, b: LocalDate) = if (a.isAfter(b)) b else a

    fun weeklyDates(t: TemplateEntity, from: LocalDate, to: LocalDate): List<LocalDate> {
        val start = later(from, LocalDate.ofEpochDay(t.startDate))
        val end = if (t.endDate > 0) earlier(to, LocalDate.ofEpochDay(t.endDate)) else to
        val out = mutableListOf<LocalDate>()
        var d = start
        while (!d.isAfter(end)) {
            if ((t.weekMask and (1 shl dayIndex(d))) != 0) out.add(d)
            d = d.plusDays(1)
        }
        return out
    }

    /** سررسید بعد از [current]؛ null اگر تکرار ندارد یا از تاریخ پایان گذشته */
    fun nextDue(t: TemplateEntity, current: LocalDate): LocalDate? {
        val step = t.repeatEvery.coerceAtLeast(1)
        val n: LocalDate = when (t.unit) {
            Every.DAY -> current.plusDays(step.toLong())
            Every.WEEK -> current.plusWeeks(step.toLong())
            Every.MONTH -> {
                val j = Jalali.fromLocalDate(current)
                val md = if (t.monthDay > 0) t.monthDay else j.third
                val total = j.first * 12 + (j.second - 1) + step
                val ny = total / 12
                val nm = total % 12 + 1
                Jalali.toLocalDate(ny, nm, minOf(md, Jalali.monthLength(ny, nm)))
            }
            Every.YEAR -> {
                val j = Jalali.fromLocalDate(current)
                val md = if (t.monthDay > 0) t.monthDay else j.third
                val ny = j.first + step
                Jalali.toLocalDate(ny, j.second, minOf(md, Jalali.monthLength(ny, j.second)))
            }
            else -> return null
        }
        if (t.endDate > 0 && n.toEpochDay() > t.endDate) return null
        return n
    }

    fun newInstance(t: TemplateEntity, date: LocalDate) = InstanceEntity(
        templateId = t.id,
        dueDate = date.toEpochDay(),
        timeMin = t.timeMin,
        dueAt = millis(date, t.timeMin),
        status = Status.PENDING,
        doneAt = 0L,
        alertsFired = 0,
        nextAlertAt = 0L,
        snoozedUntil = 0L
    )

    fun hasAlarm(t: TemplateEntity): Boolean =
        t.alarmNotify && t.timeMin >= 0 && (t.kind == Kind.DUE || t.kind == Kind.WEEKLY)

    /** زمان آلارم بعدی این نوبت (میلی‌ثانیه) یا ۰ اگر آلارمی لازم نیست */
    fun alarmAt(i: InstanceEntity, t: TemplateEntity, now: Long, allowLate: Boolean): Long {
        if (i.status != Status.PENDING || !hasAlarm(t)) return 0L
        val target = when {
            i.nextAlertAt > 0 -> i.nextAlertAt
            i.alertsFired == 0 -> i.dueAt
            else -> return 0L
        }
        return when {
            target > now -> target
            allowLate && now - target < LATE_WINDOW_MS -> now + 3_000L
            else -> 0L
        }
    }

    fun scheduleChanged(a: TemplateEntity, b: TemplateEntity): Boolean =
        a.kind != b.kind || a.startDate != b.startDate || a.endDate != b.endDate ||
            a.timeMin != b.timeMin || a.weekMask != b.weekMask || a.unit != b.unit ||
            a.repeatEvery != b.repeatEvery || a.monthDay != b.monthDay

    fun progress(items: List<PlanItem>, today: Long): DayProgress {
        val todays = items.filter {
            it.inst.dueDate == today && (it.inst.status == Status.PENDING || it.inst.status == Status.DONE)
        }
        val done = todays.count { it.inst.status == Status.DONE }
        val total = todays.size
        val overdue = items.count { it.inst.status == Status.PENDING && it.inst.dueDate < today }
        val pct = if (total == 0) 0 else done * 100 / total
        return DayProgress(total, done, total - done, overdue, pct)
    }

    /** تاریخ‌های پیش‌بینی‌شدهٔ یک الگو (برای تقویم)؛ [lastDue] آخرین سررسید ساخته‌شده است */
    fun projection(t: TemplateEntity, lastDue: Long, from: LocalDate, to: LocalDate): List<LocalDate> {
        if (t.paused) return emptyList()
        return when (t.kind) {
            Kind.WEEKLY -> weeklyDates(t, from, to)
            Kind.DUE -> {
                if (t.unit == Every.NONE) return emptyList()
                val out = mutableListOf<LocalDate>()
                var cur = LocalDate.ofEpochDay(lastDue)
                var n = 0
                while (n < 400) {
                    val nx = nextDue(t, cur) ?: break
                    if (nx.isAfter(to)) break
                    if (!nx.isBefore(from)) out.add(nx)
                    cur = nx
                    n++
                }
                out
            }
            else -> emptyList()
        }
    }

    /**
     * نوبت‌های واقعی + پیش‌بینی نوبت‌های آینده (id = -۱، فقط نمایشی) به تفکیک روز.
     * نوبت‌های لغوشده نشان داده نمی‌شوند.
     */
    fun calendarEntries(
        items: List<PlanItem>,
        templates: List<TemplateEntity>,
        from: LocalDate,
        to: LocalDate,
        today: LocalDate
    ): Map<Long, List<PlanItem>> {
        val f = from.toEpochDay()
        val tt = to.toEpochDay()
        val map = mutableMapOf<Long, MutableList<PlanItem>>()
        items.filter { it.inst.dueDate in f..tt && it.inst.status != Status.CANCELED }
            .forEach { map.getOrPut(it.inst.dueDate) { mutableListOf() }.add(it) }
        val byTemplate = items.groupBy { it.tpl.id }
        for (t in templates) {
            if (t.paused) continue
            val real = byTemplate[t.id].orEmpty()
            val have = real.map { it.inst.dueDate }.toSet()
            val last = real.maxOfOrNull { it.inst.dueDate } ?: t.startDate
            val start = later(from, today)
            projection(t, last, start, to)
                .filter { it.toEpochDay() !in have }
                .forEach { d ->
                    val virtual = newInstance(t, d).copy(id = -1L)
                    map.getOrPut(d.toEpochDay()) { mutableListOf() }.add(PlanItem(virtual, t))
                }
        }
        return map
    }
}
