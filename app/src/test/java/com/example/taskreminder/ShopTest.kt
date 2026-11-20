package com.example.taskreminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ShopTest {
    private val today = LocalDate.of(2024, 3, 23)

    private fun item(
        name: String = "x", date: Long = today.toEpochDay(), bought: Boolean = false,
        qty: Int = 1, est: Long = 0L, actual: Long = 0L, cat: String = "food"
    ) = ShoppingEntity(
        id = 1L, name = name, category = cat, qty = qty, estPrice = est, actualPrice = actual,
        date = date, bought = bought, boughtAt = 0L, note = "", createdAt = 0L
    )

    @Test fun todayRangeCarriesUnboughtOnly() {
        val t = today.toEpochDay()
        assertTrue(Shop.inRange(item(date = t), Shop.RANGE_TODAY, today))
        assertTrue(Shop.inRange(item(date = t - 3), Shop.RANGE_TODAY, today))
        assertFalse(Shop.inRange(item(date = t - 3, bought = true), Shop.RANGE_TODAY, today))
        assertFalse(Shop.inRange(item(date = t + 1), Shop.RANGE_TODAY, today))
    }

    @Test fun weekRange() {
        val t = today.toEpochDay()
        assertTrue(Shop.inRange(item(date = t + 6), Shop.RANGE_WEEK, today))
        assertFalse(Shop.inRange(item(date = t + 7), Shop.RANGE_WEEK, today))
    }

    @Test fun monthRangeUsesJalaliMonth() {
        // ۴ فروردین ۱۴۰۳؛ ماه تا ۳۱ فروردین است
        val end = Jalali.toLocalDate(1403, 1, 31).toEpochDay()
        assertTrue(Shop.inRange(item(date = end), Shop.RANGE_MONTH, today))
        assertFalse(Shop.inRange(item(date = end + 1), Shop.RANGE_MONTH, today))
    }

    @Test fun totalsAreExactAndNeverGuessActual() {
        val list = listOf(
            item(qty = 2, est = 100, actual = 90, bought = true),
            item(qty = 1, est = 50),
            item(qty = 3, est = 10, actual = 0, bought = true)
        )
        val t = Shop.totals(list)
        assertEquals(3, t.total)
        assertEquals(2, t.bought)
        assertEquals(1, t.remaining)
        assertEquals(280L, t.estimated)
        assertEquals(180L, t.actual)
        assertEquals(50L, t.remainingEst)
        assertEquals(1, t.missingActual)
    }

    @Test fun boughtSortsLast() {
        val list = listOf(item(name = "ب", bought = true), item(name = "ز"), item(name = "الف"))
        val byName = Shop.sorted(list, Shop.SORT_NAME).map { it.name }
        assertEquals("ب", byName.last())
    }

    @Test fun categoryGuess() {
        assertEquals("food", Shop.guessCategory("نان"))
        assertEquals("other", Shop.guessCategory("چیز ناشناخته"))
    }

    @Test fun monthlyProjectionInRange() {
        val t = TemplateEntity(
            id = 1L, title = "t", kind = Kind.DUE, category = "personal", priority = 0, note = "",
            startDate = Jalali.toLocalDate(1403, 1, 10).toEpochDay(), endDate = 0L, timeMin = 540,
            weekMask = 0, unit = Every.MONTH, repeatEvery = 1, monthDay = 10, amount = 0L, paused = false,
            alarmSound = true, alarmVibrate = true, alarmNotify = true, alertGapMin = 30, maxAlerts = 3, createdAt = 0L
        )
        val last = t.startDate
        val got = Engine.projection(t, last, Jalali.toLocalDate(1403, 3, 1), Jalali.toLocalDate(1403, 3, 31))
        assertEquals(listOf(Jalali.toLocalDate(1403, 3, 10)), got)
    }

    @Test fun calendarUsesRealThenVirtual() {
        val t = TemplateEntity(
            id = 1L, title = "t", kind = Kind.WEEKLY, category = "personal", priority = 0, note = "",
            startDate = today.toEpochDay(), endDate = 0L, timeMin = -1, weekMask = 127, unit = Every.NONE,
            repeatEvery = 1, monthDay = 0, amount = 0L, paused = false, alarmSound = false,
            alarmVibrate = false, alarmNotify = false, alertGapMin = 0, maxAlerts = 1, createdAt = 0L
        )
        val real = Engine.newInstance(t, today).copy(id = 7L)
        val map = Engine.calendarEntries(listOf(PlanItem(real, t)), listOf(t), today, today.plusDays(2), today)
        assertEquals(listOf(7L), map[today.toEpochDay()]!!.map { it.inst.id })
        assertEquals(listOf(-1L), map[today.plusDays(1).toEpochDay()]!!.map { it.inst.id })
        assertEquals(3, map.size)
    }
}
