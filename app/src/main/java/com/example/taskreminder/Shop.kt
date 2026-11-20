package com.example.taskreminder

import java.time.LocalDate

data class ShopTotals(
    val total: Int,
    val bought: Int,
    val remaining: Int,
    val estimated: Long,
    val actual: Long,
    val remainingEst: Long,
    val missingActual: Int
)

/** منطق خالص لیست خرید: بازه‌ها، جمع‌ها، مرتب‌سازی و متن اشتراک */
object Shop {
    const val RANGE_TODAY = "today"
    const val RANGE_WEEK = "week"
    const val RANGE_MONTH = "month"
    const val RANGE_ALL = "all"

    const val SORT_DATE = "date"
    const val SORT_NAME = "name"
    const val SORT_PRICE = "price"

    val categories = listOf(
        "food" to "خوراکی", "home" to "لوازم خانه", "clothes" to "پوشاک",
        "hygiene" to "بهداشت", "personal" to "لوازم شخصی", "other" to "سایر"
    )

    val presets: Map<String, List<String>> = mapOf(
        "food" to listOf("نان", "شیر", "تخم‌مرغ", "برنج", "مرغ", "گوشت", "میوه", "سبزی", "ماست", "پنیر", "روغن", "چای"),
        "home" to listOf("مایع ظرفشویی", "دستمال کاغذی", "پودر لباسشویی", "کیسه زباله", "لامپ", "باتری"),
        "clothes" to listOf("جوراب", "لباس زیر", "تی‌شرت", "کفش"),
        "hygiene" to listOf("شامپو", "مسواک", "خمیردندان", "صابون", "نخ دندان"),
        "personal" to listOf("کرم مرطوب‌کننده", "عطر", "ماشین اصلاح", "ضدآفتاب")
    )

    fun categoryName(key: String): String = categories.firstOrNull { it.first == key }?.second ?: "سایر"

    fun guessCategory(name: String): String =
        presets.entries.firstOrNull { name in it.value }?.key ?: "other"

    fun inRange(e: ShoppingEntity, range: String, today: LocalDate): Boolean {
        val t = today.toEpochDay()
        val carry = !e.bought && e.date < t
        return when (range) {
            RANGE_TODAY -> e.date == t || carry
            RANGE_WEEK -> carry || e.date in t..(t + 6)
            RANGE_MONTH -> {
                val j = Jalali.fromLocalDate(today)
                val start = Jalali.toLocalDate(j.first, j.second, 1).toEpochDay()
                val end = Jalali.toLocalDate(j.first, j.second, Jalali.monthLength(j.first, j.second)).toEpochDay()
                carry || e.date in start..end
            }
            else -> true
        }
    }

    fun lineEstimate(e: ShoppingEntity): Long = e.estPrice * e.qty

    fun totals(list: List<ShoppingEntity>): ShopTotals {
        val bought = list.filter { it.bought }
        val left = list.filter { !it.bought }
        return ShopTotals(
            total = list.size,
            bought = bought.size,
            remaining = left.size,
            estimated = list.sumOf { lineEstimate(it) },
            actual = bought.filter { it.actualPrice > 0 }.sumOf { it.actualPrice * it.qty },
            remainingEst = left.sumOf { lineEstimate(it) },
            missingActual = bought.count { it.actualPrice == 0L }
        )
    }

    fun sorted(list: List<ShoppingEntity>, sort: String): List<ShoppingEntity> = when (sort) {
        SORT_NAME -> list.sortedWith(compareBy<ShoppingEntity>({ it.bought }, { it.name }))
        SORT_PRICE -> list.sortedWith(compareBy<ShoppingEntity>({ it.bought }, { -lineEstimate(it) }))
        else -> list.sortedWith(compareBy<ShoppingEntity>({ it.bought }, { it.date }, { it.id }))
    }

    fun shareText(remaining: List<ShoppingEntity>): String {
        val sb = StringBuilder("لیست خرید\n")
        remaining.forEach { e ->
            sb.append("• ").append(e.name)
            if (e.qty > 1) sb.append(" × ").append(fa(e.qty))
            sb.append('\n')
        }
        return sb.toString().trimEnd()
    }
}
