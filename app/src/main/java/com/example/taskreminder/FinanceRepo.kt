package com.example.taskreminder

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class FinanceRepository private constructor(ctx: Context) {
    private val db = AppDatabase.get(ctx)
    private val dao = db.finDao()
    private val shop = db.shopDao()
    private val prefs = ctx.getSharedPreferences("finance_prefs", Context.MODE_PRIVATE)

    val accounts: Flow<List<AccountEntity>> = dao.observeAccounts()
    val tx: Flow<List<TxEntity>> = dao.observeTx()
    val parties: Flow<List<PartyEntity>> = dao.observeParties()
    val products: Flow<List<ProductEntity>> = dao.observeProducts()
    val invoices: Flow<List<InvoiceEntity>> = dao.observeInvoices()
    val lines: Flow<List<InvoiceLineEntity>> = dao.observeLines()
    val budgets: Flow<List<BudgetEntity>> = dao.observeBudgets()

    // ---------- تنظیمات و آخرین انتخاب‌ها ----------

    fun currency(): String = prefs.getString("currency", "تومان") ?: "تومان"
    fun setCurrency(c: String) = prefs.edit().putString("currency", c).apply()
    fun lastAccount(): Long = prefs.getLong("last_account", 0L)
    fun lastCategory(type: String): String? = prefs.getString("last_cat_$type", null)

    private fun remember(t: TxEntity) {
        val e = prefs.edit().putLong("last_account", t.accountId)
        if (t.type != TxType.TRANSFER) e.putString("last_cat_${t.type}", t.category)
        e.apply()
    }

    /** اولین بار یک «کیف پول نقدی» می‌سازد تا ثبت سریع بدون تنظیم کار کند */
    suspend fun ensureSeed() {
        if (prefs.getBoolean("seeded", false)) return
        if (dao.accountCount() == 0) {
            dao.insertAccount(AccountEntity(name = "کیف پول نقدی", type = "CASH", initial = 0L, createdAt = System.currentTimeMillis()))
        }
        prefs.edit().putBoolean("seeded", true).apply()
    }

    // ---------- حساب‌ها ----------

    suspend fun saveAccount(a: AccountEntity) {
        if (a.id == 0L) dao.insertAccount(a.copy(createdAt = System.currentTimeMillis())) else dao.updateAccount(a)
    }

    /** حسابی که تراکنش دارد حذف نمی‌شود؛ true یعنی حذف شد */
    suspend fun deleteAccount(id: Long): Boolean = db.withTransaction {
        if (dao.txCountForAccount(id) > 0) return@withTransaction false
        dao.deleteAccount(id)
        true
    }

    // ---------- تراکنش‌ها ----------

    suspend fun saveTx(t: TxEntity) {
        if (t.id == 0L) dao.insertTx(t.copy(createdAt = System.currentTimeMillis())) else dao.updateTx(t)
        remember(t)
    }

    suspend fun deleteTx(id: Long) {
        db.withTransaction {
            shop.unlinkExpense(id)
            dao.deleteTx(id)
        }
    }

    /** تبدیل یک قلم خریداری‌شده به هزینه؛ فقط یک‌بار (expenseId جلوی ثبت دوباره را می‌گیرد) */
    suspend fun convertShopping(e: ShoppingEntity, accountId: Long): Boolean = db.withTransaction {
        if (e.expenseId != 0L) return@withTransaction false
        val unit = if (e.actualPrice > 0) e.actualPrice else e.estPrice
        val amount = unit * e.qty
        if (amount <= 0L) return@withTransaction false
        val now = System.currentTimeMillis()
        val id = dao.insertTx(
            TxEntity(
                type = TxType.EXPENSE, amount = amount, category = Fin.expenseCategoryForShopping(e.category),
                accountId = accountId, toAccountId = 0L, date = LocalDate.now().toEpochDay(), note = e.name,
                partyId = 0L, invoiceId = 0L, createdAt = now
            )
        )
        shop.update(e.copy(bought = true, boughtAt = if (e.bought) e.boughtAt else now, expenseId = id))
        prefs.edit().putLong("last_account", accountId).apply()
        true
    }

    // ---------- حساب دیگران ----------

    suspend fun saveParty(p: PartyEntity): Long =
        if (p.id == 0L) dao.insertParty(p.copy(createdAt = System.currentTimeMillis()))
        else {
            dao.updateParty(p)
            p.id
        }

    /** حسابی که فاکتور یا دریافتی دارد حذف نمی‌شود */
    suspend fun deleteParty(id: Long): Boolean = db.withTransaction {
        if (dao.partyUsage(id) > 0) return@withTransaction false
        dao.deleteParty(id)
        true
    }

    // ---------- محصولات ----------

    suspend fun saveProduct(p: ProductEntity) {
        if (p.id == 0L) dao.insertProduct(p.copy(createdAt = System.currentTimeMillis())) else dao.updateProduct(p)
    }

    suspend fun deleteProduct(id: Long) = dao.deleteProduct(id)

    // ---------- فاکتور ----------

    /** ذخیرهٔ فاکتور با ردیف‌ها؛ اختیاری: دریافت نقدی هم‌زمان (به‌صورت درآمد پیوسته به همین فاکتور) */
    suspend fun saveInvoice(
        inv: InvoiceEntity,
        lines: List<InvoiceLineEntity>,
        receiveNow: Long,
        accountId: Long
    ): Long = db.withTransaction {
        val now = System.currentTimeMillis()
        var number = inv.number
        val id: Long
        if (inv.id == 0L) {
            number = (dao.maxInvoiceNumber() ?: 0) + 1
            id = dao.insertInvoice(inv.copy(number = number, createdAt = now))
        } else {
            dao.updateInvoice(inv)
            dao.deleteLines(inv.id)
            id = inv.id
        }
        dao.insertLines(lines.map { it.copy(id = 0L, invoiceId = id) })
        if (inv.id == 0L && receiveNow > 0L && accountId != 0L) {
            dao.insertTx(
                TxEntity(
                    type = TxType.INCOME, amount = receiveNow, category = "sales", accountId = accountId,
                    toAccountId = 0L, date = inv.date, note = "فاکتور ${fa(number)}", partyId = inv.partyId,
                    invoiceId = id, createdAt = now
                )
            )
            prefs.edit().putLong("last_account", accountId).apply()
        }
        id
    }

    /** ثبت دریافتی؛ [invoiceId] می‌تواند ۰ باشد (دریافتی بدون فاکتور) */
    suspend fun addPayment(partyId: Long, invoiceId: Long, amount: Long, accountId: Long, date: Long, note: String) {
        if (amount <= 0L) return
        dao.insertTx(
            TxEntity(
                type = TxType.INCOME, amount = amount, category = "sales", accountId = accountId,
                toAccountId = 0L, date = date, note = note, partyId = partyId, invoiceId = invoiceId,
                createdAt = System.currentTimeMillis()
            )
        )
        prefs.edit().putLong("last_account", accountId).apply()
    }

    /** فاکتوری که دریافتی دارد حذف نمی‌شود (اول دریافتی‌ها را از تراکنش‌ها پاک کن) */
    suspend fun deleteInvoice(id: Long): Boolean = db.withTransaction {
        if (dao.paymentCount(id) > 0) return@withTransaction false
        dao.deleteLines(id)
        dao.deleteInvoice(id)
        true
    }

    // ---------- بودجه ----------

    suspend fun setBudget(category: String, amount: Long) {
        if (amount > 0L) dao.setBudget(BudgetEntity(category, amount)) else dao.deleteBudget(category)
    }

    companion object {
        @Volatile private var inst: FinanceRepository? = null

        fun get(ctx: Context): FinanceRepository = inst ?: synchronized(this) {
            inst ?: FinanceRepository(ctx.applicationContext).also { inst = it }
        }
    }
}
