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
    /** epoch-day میلادی برای کارهای یک‌بار */
    val onceDate: Long = 0L,
    /** زمان سررسید فعلی (میلی‌ثانیه) */
    val dueAt: Long,
    /** فقط برای کارهای یک‌بار معنی دارد */
    val done: Boolean = false
)

object Recurrence {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    private fun at(date: LocalDate, h: Int, m: Int): Long =
        date.atTime(h, m).atZone(zone).toInstant().toEpochMilli()

    /** اولین سررسید یک کار تازه ساخته‌شده/ویرایش‌شده */
    fun firstDue(t: Task, now: Long): Long =
        if (t.repeat == Repeat.ONCE) at(LocalDate.ofEpochDay(t.onceDate), t.hour, t.minute)
        else occurrenceAfter(t, now)

    /** اولین وقوع کار که اکیداً بعد از [after] است */
    fun occurrenceAfter(t: Task, after: Long): Long {
        val start = Instant.ofEpochMilli(after).atZone(zone).toLocalDate()
        return when (t.repeat) {
            Repeat.ONCE -> at(LocalDate.ofEpochDay(t.onceDate), t.hour, t.minute)
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
    fun alarmTime(t: Task, now: Long): Long? =
        if (t.repeat == Repeat.ONCE) {
            if (!t.done && t.dueAt > now) t.dueAt else null
        } else {
            occurrenceAfter(t, maxOf(now, t.dueAt - 1))
        }

    fun afterDone(t: Task, now: Long): Task =
        if (t.repeat == Repeat.ONCE) t.copy(done = true)
        else t.copy(dueAt = occurrenceAfter(t, maxOf(t.dueAt, now)))
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
                    onceDate = o.optLong("onceDate", 0L),
                    dueAt = o.getLong("dueAt"),
                    done = o.optBoolean("done", false)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
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
                    .put("onceDate", t.onceDate)
                    .put("dueAt", t.dueAt)
                    .put("done", t.done)
            )
        }
        prefs.edit().putString("list", arr.toString()).commit()
    }
}
