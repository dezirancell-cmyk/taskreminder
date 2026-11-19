package com.example.taskreminder

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class Repeat { ONCE, DAILY, MONTHLY }

data class Task(
    val id: Int,
    val title: String,
    val repeat: Repeat,
    val hour: Int,
    val minute: Int,
    /** روز ماه شمسی برای کارهای ماهانه */
    val monthDay: Int = 1,
    /** تاریخ (epoch-day میلادی): برای کار یک‌بار روز اجرا، برای تکراری روز شروع */
    val startDate: Long = 0L,
    /** سررسید بعدی (میلی‌ثانیه) */
    val dueAt: Long,
    /** یک‌بار: انجام شده. تکراری: تعداد تکرار به پایان رسیده */
    val done: Boolean = false,
    /** فقط تکراری‌ها: ۰ یعنی نامحدود */
    val maxCount: Int = 0,
    /** فقط تکراری‌ها: چند بار اعلان داده شده */
    val firedCount: Int = 0
)

object Recurrence {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    private fun at(date: LocalDate, h: Int, m: Int): Long =
        date.atTime(h, m).atZone(zone).toInstant().toEpochMilli()

    private fun startMillis(t: Task): Long =
        at(LocalDate.ofEpochDay(t.startDate), t.hour, t.minute)

    /** اولین سررسید یک کار تازه ساخته‌شده/ویرایش‌شده */
    fun firstDue(t: Task, now: Long): Long =
        if (t.repeat == Repeat.ONCE) startMillis(t)
        else occurrenceAfter(t, maxOf(startMillis(t) - 1, now))

    /** اولین وقوع کار که اکیداً بعد از [after] است (فقط برای تکراری‌ها) */
    fun occurrenceAfter(t: Task, after: Long): Long {
        val start = Instant.ofEpochMilli(after).atZone(zone).toLocalDate()
        return when (t.repeat) {
            Repeat.ONCE -> startMillis(t)
            Repeat.DAILY -> {
                var d = start
                while (at(d, t.hour, t.minute) <= after) d = d.plusDays(1)
                at(d, t.hour, t.minute)
            }
            Repeat.MONTHLY -> {
                val j = Jalali.fromLocalDate(start)
                var jy = j.first
                var jm = j.second
                repeat(30) {
                    val day = minOf(t.monthDay, Jalali.monthLength(jy, jm))
                    val ms = at(Jalali.toLocalDate(jy, jm, day), t.hour, t.minute)
                    if (ms > after) return ms
                    jm++
                    if (jm > 12) {
                        jm = 1
                        jy++
                    }
                }
                after + 86_400_000L
            }
        }
    }

    /** زمان آلارم بعدی یا null اگر دیگر آلارمی لازم نیست */
    fun alarmTime(t: Task, now: Long): Long? {
        if (t.done) return null
        if (t.dueAt > now) return t.dueAt
        return if (t.repeat == Repeat.ONCE) null else occurrenceAfter(t, now)
    }

    /** یک وقوع تکراری اعلان شد: شمارنده بالا می‌رود و سررسید به نوبت بعد می‌رود */
    fun afterFire(t: Task, now: Long): Task {
        val fired = t.firedCount + 1
        if (t.maxCount > 0 && fired >= t.maxCount) return t.copy(firedCount = fired, done = true)
        return t.copy(firedCount = fired, dueAt = occurrenceAfter(t, maxOf(t.dueAt, now)))
    }

    /** اگر گوشی خاموش بوده و چند وقوع جا افتاده، شمارنده را به‌روز می‌کند */
    fun catchUp(t: Task, now: Long): Task {
        if (t.repeat == Repeat.ONCE || t.done || t.dueAt > now) return t
        if (t.maxCount == 0) return t.copy(dueAt = occurrenceAfter(t, now))
        var cur = t
        var n = 0
        while (!cur.done && cur.dueAt <= now && n < 5000) {
            cur = afterFire(cur, cur.dueAt)
            n++
        }
        return cur
    }
}

class TaskStore(ctx: Context) {
    private val prefs = ctx.applicationContext.getSharedPreferences("tasks", Context.MODE_PRIVATE)

    fun load(): List<Task> {
        val raw = prefs.getString("list", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Task(
                    id = o.getInt("id"),
                    title = o.getString("title"),
                    repeat = Repeat.valueOf(o.getString("repeat")),
                    hour = o.getInt("hour"),
                    minute = o.getInt("minute"),
                    monthDay = o.optInt("monthDay", 1),
                    startDate = o.optLong("startDate", o.optLong("onceDate", 0L)),
                    dueAt = o.getLong("dueAt"),
                    done = o.optBoolean("done", false),
                    maxCount = o.optInt("maxCount", 0),
                    firedCount = o.optInt("firedCount", 0)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** بارگذاری + به‌روزرسانی وقوع‌های جا افتاده؛ در صورت تغییر ذخیره می‌شود */
    fun loadSynced(): List<Task> {
        val now = System.currentTimeMillis()
        val list = load()
        val synced = list.map { Recurrence.catchUp(it, now) }
        if (synced != list) save(synced)
        return synced
    }

    fun save(list: List<Task>) {
        val arr = JSONArray()
        list.forEach { t ->
            arr.put(
                JSONObject()
                    .put("id", t.id)
                    .put("title", t.title)
                    .put("repeat", t.repeat.name)
                    .put("hour", t.hour)
                    .put("minute", t.minute)
                    .put("monthDay", t.monthDay)
                    .put("startDate", t.startDate)
                    .put("dueAt", t.dueAt)
                    .put("done", t.done)
                    .put("maxCount", t.maxCount)
                    .put("firedCount", t.firedCount)
            )
        }
        prefs.edit().putString("list", arr.toString()).commit()
    }
}
