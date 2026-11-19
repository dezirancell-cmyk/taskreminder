package com.example.taskreminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class EngineTest {
    private fun tpl(
        kind: String = Kind.DUE,
        unit: String = Every.NONE,
        every: Int = 1,
        monthDay: Int = 0,
        mask: Int = 0,
        start: Long = 0L,
        end: Long = 0L,
        timeMin: Int = 9 * 60,
        notify: Boolean = true
    ) = TemplateEntity(
        id = 1L, title = "t", kind = kind, category = "personal", priority = 0, note = "",
        startDate = start, endDate = end, timeMin = timeMin, weekMask = mask, unit = unit,
        repeatEvery = every, monthDay = monthDay, amount = 0L, paused = false,
        alarmSound = true, alarmVibrate = true, alarmNotify = notify,
        alertGapMin = 30, maxAlerts = 3, createdAt = 0L
    )

    private fun inst(
        t: TemplateEntity, dueAt: Long, status: String = Status.PENDING,
        fired: Int = 0, next: Long = 0L, date: Long = 100L
    ) = InstanceEntity(
        id = 5L, templateId = t.id, dueDate = date, timeMin = t.timeMin, dueAt = dueAt,
        status = status, doneAt = 0L, alertsFired = fired, nextAlertAt = next, snoozedUntil = 0L
    )

    @Test fun persianWeekStartsSaturday() {
        assertEquals(0, Engine.dayIndex(LocalDate.of(2024, 3, 23)))
        assertEquals(6, Engine.dayIndex(LocalDate.of(2024, 3, 22)))
    }

    @Test fun weeklyDatesFollowMask() {
        val t = tpl(kind = Kind.WEEKLY, mask = 0b101) // شنبه و دوشنبه
        val from = LocalDate.of(2024, 3, 23)
        val dates = Engine.weeklyDates(t, from, LocalDate.of(2024, 3, 29))
        assertEquals(listOf(LocalDate.of(2024, 3, 23), LocalDate.of(2024, 3, 25)), dates)
    }

    @Test fun weeklyDatesRespectEnd() {
        val t = tpl(kind = Kind.WEEKLY, mask = 127, end = LocalDate.of(2024, 3, 24).toEpochDay())
        val dates = Engine.weeklyDates(t, LocalDate.of(2024, 3, 23), LocalDate.of(2024, 3, 29))
        assertEquals(2, dates.size)
    }

    @Test fun monthlyClampsDay() {
        val t = tpl(unit = Every.MONTH, monthDay = 31)
        val cur = Jalali.toLocalDate(1403, 6, 31)
        assertEquals(Jalali.toLocalDate(1403, 7, 30), Engine.nextDue(t, cur))
    }

    @Test fun yearlyClampsEsfand() {
        val t = tpl(unit = Every.YEAR, monthDay = 30)
        val cur = Jalali.toLocalDate(1403, 12, 30)
        assertEquals(Jalali.toLocalDate(1404, 12, 29), Engine.nextDue(t, cur))
    }

    @Test fun nextDueStopsAtEndAndOnce() {
        val cur = LocalDate.of(2024, 1, 1)
        assertNull(Engine.nextDue(tpl(unit = Every.NONE), cur))
        val t = tpl(unit = Every.DAY, end = cur.toEpochDay())
        assertNull(Engine.nextDue(t, cur))
    }

    @Test fun weeklyIntervalAdds() {
        val t = tpl(unit = Every.WEEK, every = 2)
        val cur = LocalDate.of(2024, 1, 1)
        assertEquals(cur.plusWeeks(2), Engine.nextDue(t, cur))
    }

    @Test fun firstAlarmAtDueTime() {
        val t = tpl()
        val now = 1_000_000L
        assertEquals(now + 5_000L, Engine.alarmAt(inst(t, now + 5_000L), t, now, false))
    }

    @Test fun noAlarmAfterAllAlertsOrWhenDone() {
        val t = tpl()
        val now = 1_000_000L
        assertEquals(0L, Engine.alarmAt(inst(t, now - 10, fired = 3), t, now, true))
        assertEquals(0L, Engine.alarmAt(inst(t, now + 10, status = Status.DONE), t, now, false))
    }

    @Test fun nextAlertUsedAfterFirst() {
        val t = tpl()
        val now = 1_000_000L
        val i = inst(t, now - 100, fired = 1, next = now + 1_800_000L)
        assertEquals(now + 1_800_000L, Engine.alarmAt(i, t, now, false))
    }

    @Test fun lateAlarmOnlyWhenAllowed() {
        val t = tpl()
        val now = 10_000_000L
        val i = inst(t, now - 60_000L)
        assertEquals(0L, Engine.alarmAt(i, t, now, false))
        assertEquals(now + 3_000L, Engine.alarmAt(i, t, now, true))
    }

    @Test fun noAlarmWithoutTimeOrNotify() {
        val d = tpl(kind = Kind.DAY, timeMin = -1)
        assertEquals(0L, Engine.alarmAt(inst(d, 5_000L), d, 0L, false))
        val q = tpl(notify = false)
        assertEquals(0L, Engine.alarmAt(inst(q, 5_000L), q, 0L, false))
    }

    @Test fun progressCountsOnlyToday() {
        val t = tpl(kind = Kind.DAY, timeMin = -1)
        fun item(date: Long, status: String) = PlanItem(inst(t, 0L, status = status, date = date), t)
        val items = listOf(
            item(10, Status.DONE), item(10, Status.PENDING), item(10, Status.CANCELED),
            item(11, Status.PENDING), item(9, Status.PENDING), item(9, Status.DONE)
        )
        val p = Engine.progress(items, 10)
        assertEquals(2, p.total)
        assertEquals(1, p.done)
        assertEquals(1, p.remaining)
        assertEquals(1, p.overdue)
        assertEquals(50, p.percent)
    }

    @Test fun emptyDayIsZeroPercent() {
        assertEquals(0, Engine.progress(emptyList(), 10).percent)
    }
}
