package com.example.taskreminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class LogicTest {
    private fun ms(d: LocalDate, h: Int, m: Int): Long =
        d.atTime(h, m).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun daily(start: LocalDate, max: Int = 0, fired: Int = 0, due: Long = 0L) = Task(
        id = 1, title = "t", repeat = Repeat.DAILY, hour = 9, minute = 0,
        startDate = start.toEpochDay(), dueAt = due, maxCount = max, firedCount = fired
    )

    @Test fun nowruz1403() {
        assertEquals(Triple(1403, 1, 1), Jalali.fromGregorian(2024, 3, 20))
        assertEquals(LocalDate.of(2024, 3, 20), Jalali.toLocalDate(1403, 1, 1))
    }

    @Test fun leapYears() {
        assertEquals(30, Jalali.monthLength(1403, 12))
        assertEquals(29, Jalali.monthLength(1404, 12))
    }

    @Test fun roundTrip() {
        var d = LocalDate.of(2010, 1, 1)
        val end = LocalDate.of(2060, 12, 31)
        while (!d.isAfter(end)) {
            assertEquals(d, Jalali.toLocalDate(Jalali.fromLocalDate(d).first, Jalali.fromLocalDate(d).second, Jalali.fromLocalDate(d).third))
            d = d.plusDays(1)
        }
    }

    @Test fun dailyNextOccurrence() {
        val d = LocalDate.of(2024, 1, 1)
        val t = daily(d)
        assertEquals(ms(d.plusDays(1), 9, 0), Recurrence.occurrenceAfter(t, ms(d, 10, 0)))
        assertEquals(ms(d, 9, 0), Recurrence.occurrenceAfter(t, ms(d, 8, 0)))
    }

    @Test fun monthlyClampsToLastDay() {
        val t = Task(
            id = 2, title = "m", repeat = Repeat.MONTHLY, hour = 8, minute = 0,
            monthDay = 31, startDate = LocalDate.of(2024, 1, 1).toEpochDay(), dueAt = 0L
        )
        val after = ms(Jalali.toLocalDate(1403, 7, 1), 0, 0)
        assertEquals(ms(Jalali.toLocalDate(1403, 7, 30), 8, 0), Recurrence.occurrenceAfter(t, after))
    }

    @Test fun afterFireAdvancesAndCounts() {
        val d = LocalDate.of(2024, 1, 1)
        val due = ms(d, 9, 0)
        val n = Recurrence.afterFire(daily(d, due = due), due)
        assertEquals(1, n.firedCount)
        assertEquals(ms(d.plusDays(1), 9, 0), n.dueAt)
        assertTrue(!n.done)
    }

    @Test fun afterFireFinishesAtMax() {
        val d = LocalDate.of(2024, 1, 1)
        val due = ms(d, 9, 0)
        val n = Recurrence.afterFire(daily(d, max = 2, fired = 1, due = due), due)
        assertEquals(2, n.firedCount)
        assertTrue(n.done)
    }

    @Test fun catchUpCountsMissedOccurrences() {
        val d = LocalDate.of(2024, 1, 1)
        val t = daily(d, max = 3, due = ms(d, 9, 0))
        val n = Recurrence.catchUp(t, ms(d.plusDays(9), 12, 0))
        assertEquals(3, n.firedCount)
        assertTrue(n.done)
    }

    @Test fun doneOnceHasNoAlarm() {
        val t = Task(id = 3, title = "o", repeat = Repeat.ONCE, hour = 9, minute = 0, dueAt = 1L, done = true)
        assertNull(Recurrence.alarmTime(t, 0L))
    }
}
