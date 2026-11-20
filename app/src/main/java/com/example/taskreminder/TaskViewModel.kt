package com.example.taskreminder

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.core.app.NotificationManagerCompat
import java.time.LocalDate

class TaskViewModel(private val app: Application) : AndroidViewModel(app) {
    private val repo = PlanRepository.get(app)

    val items: StateFlow<List<PlanItem>> = repo
        .observeItems(LocalDate.now().toEpochDay() - 60)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val templates: StateFlow<List<TemplateEntity>> = repo
        .observeTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val shopDao = AppDatabase.get(app).shopDao()

    val shopping: StateFlow<List<ShoppingEntity>> = shopDao
        .observeAll(LocalDate.now().toEpochDay() - 120)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val suggestions: StateFlow<List<NameCat>> = shopDao
        .observeNames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val fin = FinanceRepository.get(app)

    val accounts = fin.accounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<AccountEntity>())
    val txs = fin.tx.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<TxEntity>())
    val parties = fin.parties.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<PartyEntity>())
    val products = fin.products.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<ProductEntity>())
    val invoices = fin.invoices.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<InvoiceEntity>())
    val invoiceLines = fin.lines.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<InvoiceLineEntity>())
    val budgets = fin.budgets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<BudgetEntity>())

    private val habitDao = AppDatabase.get(app).habitDao()

    val habits = habitDao.observeHabits().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<HabitEntity>())
    val habitLogs = habitDao.observeLogs().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<HabitLogEntity>())
    val goals = habitDao.observeGoals().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<GoalEntity>())

    private val _currency = MutableStateFlow(fin.currency())
    val currency: StateFlow<String> = _currency

    init {
        viewModelScope.launch(Dispatchers.IO) { fin.ensureSeed() }
        viewModelScope.launch(Dispatchers.IO) {
            Scheduler.ensureChannels(app)
            repo.ensureMigrated()
            repo.sync(false)
        }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) { repo.sync(false) }
    }

    fun save(t: TemplateEntity) {
        viewModelScope.launch(Dispatchers.IO) { repo.save(t) }
    }

    fun deleteTemplate(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { repo.deleteTemplate(id) }
    }

    fun togglePause(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { repo.togglePause(id) }
    }

    fun setDone(id: Long, done: Boolean) {
        viewModelScope.launch(Dispatchers.IO) { repo.setDone(id, done) }
    }

    fun postpone(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { repo.postpone(id) }
    }

    // ---------- خرید ----------

    /** افزودن با یک لمس: اگر همان کالا امروز هست یکی به تعداد اضافه می‌شود؛ قیمت از آخرین ثبت همان کالا می‌آید */
    fun quickAddShopping(name: String, category: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val today = LocalDate.now().toEpochDay()
            val all = shopping.value
            val existing = all.firstOrNull { !it.bought && it.name == name && it.date == today }
            if (existing != null) {
                shopDao.update(existing.copy(qty = existing.qty + 1))
            } else {
                val last = all.filter { it.name == name }.maxByOrNull { it.createdAt }
                val price = last?.let { if (it.actualPrice > 0) it.actualPrice else it.estPrice } ?: 0L
                shopDao.insert(
                    ShoppingEntity(
                        name = name, category = category, qty = 1, estPrice = price, actualPrice = 0L,
                        date = today, bought = false, boughtAt = 0L, note = "",
                        createdAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun addShopping(e: ShoppingEntity) {
        viewModelScope.launch(Dispatchers.IO) { shopDao.insert(e.copy(id = 0L, createdAt = System.currentTimeMillis())) }
    }

    fun updateShopping(e: ShoppingEntity) {
        viewModelScope.launch(Dispatchers.IO) { shopDao.update(e) }
    }

    fun deleteShopping(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { shopDao.delete(id) }
    }

    fun toggleBought(e: ShoppingEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val n = if (e.bought) e.copy(bought = false, boughtAt = 0L)
            else e.copy(bought = true, boughtAt = System.currentTimeMillis())
            shopDao.update(n)
        }
    }

    fun changeQty(e: ShoppingEntity, delta: Int) {
        val q = (e.qty + delta).coerceIn(1, 99)
        if (q == e.qty) return
        viewModelScope.launch(Dispatchers.IO) { shopDao.update(e.copy(qty = q)) }
    }

    /** کپی لیست برای روز/هفته/ماه بعد (به‌صورت «خریداری‌نشده») */
    fun repeatShopping(list: List<ShoppingEntity>, shiftDays: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val today = LocalDate.now().toEpochDay()
            val now = System.currentTimeMillis()
            shopDao.insertAll(
                list.map {
                    it.copy(
                        id = 0L, date = maxOf(it.date, today) + shiftDays, bought = false,
                        boughtAt = 0L, actualPrice = 0L, createdAt = now
                    )
                }
            )
        }
    }

    // ---------- عادت‌ها و اهداف ----------

    fun saveHabit(h: HabitEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = habitDao.upsertHabit(h)
            val saved = if (h.id == 0L) h.copy(id = id) else h
            HabitAlarms.ensureChannel(app)
            HabitAlarms.schedule(app, saved)
        }
    }

    fun deleteHabit(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            HabitAlarms.cancel(app, id)
            habitDao.deleteLogs(id)
            habitDao.deleteHabit(id)
        }
    }

    /** ثبت یا برداشتن انجام یک عادت در یک روز (امروز یا روزهای گذشته) */
    fun setHabitDay(habitId: Long, day: Long, done: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (done) habitDao.addLog(HabitLogEntity(habitId, day)) else habitDao.removeLog(habitId, day)
            if (done && day == LocalDate.now().toEpochDay()) {
                NotificationManagerCompat.from(app).cancel(HabitAlarms.notifId(habitId))
            }
        }
    }

    fun saveGoal(g: GoalEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val done = g.current >= g.target
            val n = when {
                done && g.doneAt == 0L -> g.copy(doneAt = System.currentTimeMillis())
                !done -> g.copy(doneAt = 0L)
                else -> g
            }
            habitDao.upsertGoal(n)
        }
    }

    fun deleteGoal(id: Long) { viewModelScope.launch(Dispatchers.IO) { habitDao.deleteGoal(id) } }

    // ---------- مالی ----------

    fun lastAccount(): Long = fin.lastAccount()
    fun lastCategory(type: String): String? = fin.lastCategory(type)

    fun setCurrency(c: String) {
        fin.setCurrency(c)
        _currency.value = c
    }

    fun saveAccount(a: AccountEntity) { viewModelScope.launch(Dispatchers.IO) { fin.saveAccount(a) } }

    fun deleteAccount(id: Long) { viewModelScope.launch(Dispatchers.IO) { fin.deleteAccount(id) } }

    fun saveTx(t: TxEntity) { viewModelScope.launch(Dispatchers.IO) { fin.saveTx(t) } }

    fun deleteTx(id: Long) { viewModelScope.launch(Dispatchers.IO) { fin.deleteTx(id) } }

    fun convertShopping(e: ShoppingEntity, accountId: Long) {
        viewModelScope.launch(Dispatchers.IO) { fin.convertShopping(e, accountId) }
    }

    fun saveParty(p: PartyEntity) { viewModelScope.launch(Dispatchers.IO) { fin.saveParty(p) } }

    fun deleteParty(id: Long) { viewModelScope.launch(Dispatchers.IO) { fin.deleteParty(id) } }

    fun saveProduct(p: ProductEntity) { viewModelScope.launch(Dispatchers.IO) { fin.saveProduct(p) } }

    fun deleteProduct(id: Long) { viewModelScope.launch(Dispatchers.IO) { fin.deleteProduct(id) } }

    fun saveInvoice(inv: InvoiceEntity, lines: List<InvoiceLineEntity>, receiveNow: Long, accountId: Long) {
        viewModelScope.launch(Dispatchers.IO) { fin.saveInvoice(inv, lines, receiveNow, accountId) }
    }

    fun addPayment(partyId: Long, invoiceId: Long, amount: Long, accountId: Long, date: Long, note: String) {
        viewModelScope.launch(Dispatchers.IO) { fin.addPayment(partyId, invoiceId, amount, accountId, date, note) }
    }

    fun deleteInvoice(id: Long) { viewModelScope.launch(Dispatchers.IO) { fin.deleteInvoice(id) } }

    fun setBudget(category: String, amount: Long) {
        viewModelScope.launch(Dispatchers.IO) { fin.setBudget(category, amount) }
    }
}
