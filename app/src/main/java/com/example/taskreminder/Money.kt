package com.example.taskreminder

import java.time.LocalDate

object TxType {
    const val INCOME = "INCOME"
    const val EXPENSE = "EXPENSE"
    const val TRANSFER = "TRANSFER"
}

data class Summary(val income: Long, val expense: Long) {
    val net: Long get() = income - expense
}

/** منطق خالص مالی: مانده‌ها، خلاصه‌ها، بودجه، فاکتور و مانده حساب دیگران */
object Fin {
    val incomeCats = listOf(
        "salary" to "حقوق", "sales" to "فروش", "project" to "پروژه", "gift" to "هدیه", "other_income" to "درآمد متفرقه"
    )
    val expenseCats = listOf(
        "food" to "خوراک", "transport" to "حمل‌ونقل", "housing" to "مسکن", "rent" to "اجاره",
        "bills" to "قبوض", "household" to "خرید خانه", "clothing" to "پوشاک", "health" to "درمان",
        "fun" to "تفریح", "education" to "آموزش", "other" to "سایر"
    )
    val units = listOf("عدد", "ساعت", "پروژه", "کیلو", "متر")

    fun catName(key: String): String =
        (incomeCats + expenseCats).firstOrNull { it.first == key }?.second ?: "سایر"

    /** دستهٔ هزینه برای تبدیل یک قلم خرید */
    fun expenseCategoryForShopping(shopCategory: String): String = when (shopCategory) {
        "food" -> "food"
        "home", "hygiene" -> "household"
        "clothes" -> "clothing"
        else -> "other"
    }

    fun accountBalance(a: AccountEntity, txs: List<TxEntity>): Long = a.initial + txs.sumOf { t ->
        when {
            t.type == TxType.INCOME && t.accountId == a.id -> t.amount
            t.type == TxType.EXPENSE && t.accountId == a.id -> -t.amount
            t.type == TxType.TRANSFER && t.accountId == a.id -> -t.amount
            t.type == TxType.TRANSFER && t.toAccountId == a.id -> t.amount
            else -> 0L
        }
    }

    /** درآمد و هزینهٔ بازه (انتقال‌ها حساب نمی‌شوند) */
    fun summary(txs: List<TxEntity>, from: Long, to: Long): Summary {
        val inRange = txs.filter { it.date in from..to }
        return Summary(
            income = inRange.filter { it.type == TxType.INCOME }.sumOf { it.amount },
            expense = inRange.filter { it.type == TxType.EXPENSE }.sumOf { it.amount }
        )
    }

    fun spentByCategory(txs: List<TxEntity>, from: Long, to: Long): Map<String, Long> =
        txs.filter { it.type == TxType.EXPENSE && it.date in from..to }
            .groupBy { it.category }
            .mapValues { e -> e.value.sumOf { it.amount } }

    /** ۰ عادی، ۱ نزدیک سقف (۸۰٪ به بالا)، ۲ از بودجه گذشته */
    fun budgetLevel(spent: Long, budget: Long): Int = when {
        budget <= 0 -> 0
        spent >= budget -> 2
        spent * 10 >= budget * 8 -> 1
        else -> 0
    }

    fun monthBounds(today: LocalDate): Pair<Long, Long> {
        val j = Jalali.fromLocalDate(today)
        val start = Jalali.toLocalDate(j.first, j.second, 1).toEpochDay()
        val end = Jalali.toLocalDate(j.first, j.second, Jalali.monthLength(j.first, j.second)).toEpochDay()
        return start to end
    }

    fun weekBounds(today: LocalDate): Pair<Long, Long> {
        val start = today.minusDays(Engine.dayIndex(today).toLong())
        return start.toEpochDay() to start.plusDays(6).toEpochDay()
    }

    // ---------- فاکتور ----------

    fun lineTotal(l: InvoiceLineEntity): Long = l.unitPrice * l.qty

    fun invoiceTotal(lines: List<InvoiceLineEntity>): Long = lines.sumOf { lineTotal(it) }

    fun invoicePaid(invoiceId: Long, txs: List<TxEntity>): Long =
        txs.filter { it.type == TxType.INCOME && it.invoiceId == invoiceId }.sumOf { it.amount }

    /** مانده حساب دیگران = جمع فاکتورها − جمع دریافتی‌ها (مثبت یعنی طلب ما) */
    fun partyBalance(
        partyId: Long,
        invoices: List<InvoiceEntity>,
        lines: List<InvoiceLineEntity>,
        txs: List<TxEntity>
    ): Long {
        val ids = invoices.filter { it.partyId == partyId }.map { it.id }.toSet()
        val invoiced = lines.filter { it.invoiceId in ids }.sumOf { lineTotal(it) }
        val received = txs.filter { it.type == TxType.INCOME && it.partyId == partyId }.sumOf { it.amount }
        return invoiced - received
    }

    fun invoiceShareText(
        inv: InvoiceEntity,
        partyName: String,
        lines: List<InvoiceLineEntity>,
        paid: Long,
        unit: String
    ): String {
        val sb = StringBuilder()
        sb.append("فاکتور شمارهٔ ").append(fa(inv.number)).append('\n')
        if (partyName.isNotBlank()) sb.append("مشتری: ").append(partyName).append('\n')
        sb.append("تاریخ: ").append(dateText(Jalali.fromEpochDay(inv.date))).append("\n\n")
        lines.forEach {
            sb.append("• ").append(it.name).append(" — ").append(fa(it.qty)).append(" × ")
                .append(moneyText(it.unitPrice)).append(" = ").append(moneyText(lineTotal(it))).append('\n')
        }
        val total = invoiceTotal(lines)
        sb.append("\nجمع: ").append(moneyText(total)).append(' ').append(unit)
        if (paid > 0) {
            sb.append("\nدریافتی: ").append(moneyText(paid)).append(' ').append(unit)
            sb.append("\nمانده: ").append(moneyText(total - paid)).append(' ').append(unit)
        }
        return sb.toString()
    }
}
