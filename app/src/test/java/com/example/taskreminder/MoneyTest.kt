package com.example.taskreminder

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class MoneyTest {
    private fun acc(id: Long, initial: Long = 0L) = AccountEntity(id = id, name = "a$id", type = "BANK", initial = initial, createdAt = 0L)

    private fun tx(
        type: String, amount: Long, account: Long = 1L, to: Long = 0L, date: Long = 10L,
        party: Long = 0L, invoice: Long = 0L, cat: String = "x"
    ) = TxEntity(
        id = 1L, type = type, amount = amount, category = cat, accountId = account, toAccountId = to,
        date = date, note = "", partyId = party, invoiceId = invoice, createdAt = 0L
    )

    private fun line(invoice: Long, qty: Int, price: Long) =
        InvoiceLineEntity(invoiceId = invoice, name = "n", unit = "عدد", qty = qty, unitPrice = price)

    private fun inv(id: Long, party: Long) =
        InvoiceEntity(id = id, number = id.toInt(), partyId = party, date = 10L, note = "", createdAt = 0L)

    @Test fun balanceHandlesTransfers() {
        val a = acc(1, 1000)
        val txs = listOf(
            tx(TxType.INCOME, 500), tx(TxType.EXPENSE, 200),
            tx(TxType.TRANSFER, 300, account = 1, to = 2), tx(TxType.TRANSFER, 50, account = 2, to = 1)
        )
        assertEquals(1000 + 500 - 200 - 300 + 50, Fin.accountBalance(a, txs))
        assertEquals(300L - 50L, Fin.accountBalance(acc(2), txs))
    }

    @Test fun transferIsNotIncomeOrExpense() {
        val s = Fin.summary(listOf(tx(TxType.INCOME, 100), tx(TxType.EXPENSE, 30), tx(TxType.TRANSFER, 999, to = 2)), 0, 100)
        assertEquals(100L, s.income)
        assertEquals(30L, s.expense)
        assertEquals(70L, s.net)
    }

    @Test fun summaryRespectsRange() {
        val s = Fin.summary(listOf(tx(TxType.INCOME, 100, date = 5), tx(TxType.INCOME, 40, date = 20)), 10, 30)
        assertEquals(40L, s.income)
    }

    @Test fun budgetLevels() {
        assertEquals(0, Fin.budgetLevel(79, 100))
        assertEquals(1, Fin.budgetLevel(80, 100))
        assertEquals(2, Fin.budgetLevel(100, 100))
        assertEquals(0, Fin.budgetLevel(500, 0))
    }

    @Test fun spentByCategoryOnlyExpenses() {
        val m = Fin.spentByCategory(
            listOf(tx(TxType.EXPENSE, 10, cat = "food"), tx(TxType.EXPENSE, 5, cat = "food"), tx(TxType.INCOME, 99, cat = "food")),
            0, 100
        )
        assertEquals(mapOf("food" to 15L), m)
    }

    @Test fun invoiceTotalsAndPayments() {
        val lines = listOf(line(1, 2, 100), line(1, 1, 50))
        assertEquals(250L, Fin.invoiceTotal(lines))
        val txs = listOf(tx(TxType.INCOME, 100, invoice = 1), tx(TxType.INCOME, 70, invoice = 2), tx(TxType.EXPENSE, 5, invoice = 1))
        assertEquals(100L, Fin.invoicePaid(1, txs))
    }

    @Test fun partyBalanceCountsAllReceipts() {
        val invoices = listOf(inv(1, 7), inv(2, 8))
        val lines = listOf(line(1, 3, 100), line(2, 1, 999))
        val txs = listOf(
            tx(TxType.INCOME, 100, party = 7, invoice = 1),
            tx(TxType.INCOME, 50, party = 7, invoice = 0),
            tx(TxType.INCOME, 10, party = 8)
        )
        assertEquals(150L, Fin.partyBalance(7, invoices, lines, txs))
        assertEquals(989L, Fin.partyBalance(8, invoices, lines, txs))
    }

    @Test fun shoppingCategoryMapping() {
        assertEquals("food", Fin.expenseCategoryForShopping("food"))
        assertEquals("household", Fin.expenseCategoryForShopping("home"))
        assertEquals("clothing", Fin.expenseCategoryForShopping("clothes"))
        assertEquals("other", Fin.expenseCategoryForShopping("personal"))
    }

    @Test fun weekStartsSaturday() {
        val sat = LocalDate.of(2024, 3, 23)
        assertEquals(sat.toEpochDay(), Fin.weekBounds(sat).first)
        assertEquals(sat.toEpochDay(), Fin.weekBounds(LocalDate.of(2024, 3, 27)).first)
        assertEquals(sat.plusDays(6).toEpochDay(), Fin.weekBounds(sat).second)
    }

    @Test fun jalaliMonthBounds() {
        val (s, e) = Fin.monthBounds(LocalDate.of(2024, 3, 23))
        assertEquals(LocalDate.of(2024, 3, 20).toEpochDay(), s)
        assertEquals(LocalDate.of(2024, 4, 19).toEpochDay(), e)
    }
}
