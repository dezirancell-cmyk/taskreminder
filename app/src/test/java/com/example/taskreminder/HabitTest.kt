package com.example.taskreminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HabitTest {
    // چهارشنبه ۱۴ ژانویهٔ ۲۰۲۶ (میانهٔ هفته، تا آزمون‌های هفتگی معنی‌دار باشند)
    private val today = LocalDate.of(2026, 1, 14).toEpochDay()

    private fun habit(
        kind: String = HabitKind.DAILY,
        mask: Int = HabitLogic.ALL_DAYS,
        target: Int = 1,
        start: Long = today - 60,
        timeMin: Int = -1
    ) = HabitEntity(
        id = 1L, title = "h", icon = "star", kind = kind, weekMask = mask, target = target,
        timeMin = timeMin, createdAt = 0L, startDay = start
    )

    private fun days(vararg offsets: Int): Set<Long> = offsets.map { today + it }.toSet()

    @Test fun dayBitIsSaturdayZero() {
        // ۱۴ ژانویهٔ ۲۰۲۶ چهارشنبه است ← شنبه=۰، یکشنبه=۱، دوشنبه=۲، سه‌شنبه=۳، چهارشنبه=۴
        assertEquals(4, HabitLogic.dayBit(today))
        assertEquals(today - 4, HabitLogic.weekStart(today))
    }

    @Test fun dailyStreakCountsConsecutiveDays() {
        assertEquals(3, HabitLogic.streak(habit(), days(0, -1, -2), today))
    }

    @Test fun todayNotDoneDoesNotBreakStreak() {
        assertEquals(3, HabitLogic.streak(habit(), days(-1, -2, -3), today))
    }

    @Test fun missedDayBreaksStreak() {
        assertEquals(1, HabitLogic.streak(habit(), days(0, -2, -3), today))
    }

    @Test fun unscheduledDaysAreSkipped() {
        val mask = HabitLogic.ALL_DAYS and (1 shl HabitLogic.dayBit(today - 1)).inv()
        assertEquals(2, HabitLogic.streak(habit(mask = mask), days(0, -2), today))
    }

    @Test fun weeklyStreakCountsWeeksReachingTarget() {
        val ws = HabitLogic.weekStart(today)
        val done = setOf(ws, ws + 1, ws - 7, ws - 6, ws - 14)
        assertEquals(2, HabitLogic.streak(habit(HabitKind.WEEKLY, target = 2), done, today))
    }

    @Test fun weeklyCurrentWeekNotYetMetKeepsPreviousStreak() {
        val ws = HabitLogic.weekStart(today)
        val done = setOf(ws, ws - 7, ws - 6)
        assertEquals(1, HabitLogic.streak(habit(HabitKind.WEEKLY, target = 2), done, today))
    }

    @Test fun bestStreakFindsLongestRun() {
        val done = days(-10, -9, -8, -7, -5, -4, 0)
        assertEquals(4, HabitLogic.bestStreak(habit(), done, today))
    }

    @Test fun rateIsDoneOverScheduledDays() {
        val h = habit(start = today - 9)
        assertEquals(50, HabitLogic.rate(h, days(0, -1, -2, -3, -4), today))
    }

    @Test fun rateIgnoresTodayWhenNotDone() {
        val h = habit(start = today - 1)
        // دیروز انجام شده، امروز هنوز تمام نشده ← ۱ از ۱
        assertEquals(100, HabitLogic.rate(h, days(-1), today))
    }

    @Test fun rateIsNullWithoutData() {
        assertNull(HabitLogic.rate(habit(start = today), emptySet(), today))
    }

    @Test fun weekProgressSumsAcrossHabits() {
        val ws = HabitLogic.weekStart(today)
        val a = habit(HabitKind.WEEKLY, target = 3).copy(id = 1L)
        val b = habit(mask = 0b0000011).copy(id = 2L) // شنبه و یکشنبه
        val logs = mapOf(1L to setOf(ws, ws + 2), 2L to setOf(ws))
        val p = HabitLogic.weekProgress(listOf(a, b), logs, today)
        assertEquals(Pair(3, 5), p)
    }

    @Test fun weeklyFractionsAreClamped() {
        val ws = HabitLogic.weekStart(today)
        val h = habit(HabitKind.WEEKLY, target = 2)
        val f = HabitLogic.weeklyFractions(h, setOf(ws, ws + 1, ws + 2), today, 3)
        assertEquals(3, f.size)
        assertEquals(1f, f.last(), 0.001f)
        assertEquals(0f, f.first(), 0.001f)
    }

    @Test fun goalProgress() {
        val g = GoalEntity(
            id = 1L, title = "g", kind = GoalKind.LONG, target = 10L, current = 4L, step = 1L,
            unit = "کتاب", deadline = today + 5, createdAt = 0L, doneAt = 0L
        )
        val p = HabitLogic.goalProgress(g, today)
        assertEquals(40, p.percent)
        assertEquals(6L, p.remaining)
        assertEquals(5L, p.daysLeft)
        assertFalse(p.done)
        assertTrue(HabitLogic.goalProgress(g.copy(current = 12L), today).done)
        assertEquals(100, HabitLogic.goalProgress(g.copy(current = 12L), today).percent)
    }
}
