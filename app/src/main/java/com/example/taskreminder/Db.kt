package com.example.taskreminder

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** جدول کارها (نسخهٔ ۱ دیتابیس). جدول‌های نوبت و هشدار در مرحلهٔ ۴ با Migration اضافه می‌شوند. */
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: Int,
    val title: String,
    val repeat: String,
    val hour: Int,
    val minute: Int,
    val monthDay: Int,
    val startDate: Long,
    val dueAt: Long,
    val done: Boolean,
    val maxCount: Int,
    val firedCount: Int
)

fun TaskEntity.toTask() = Task(
    id = id,
    title = title,
    repeat = runCatching { Repeat.valueOf(repeat) }.getOrDefault(Repeat.ONCE),
    hour = hour,
    minute = minute,
    monthDay = monthDay,
    startDate = startDate,
    dueAt = dueAt,
    done = done,
    maxCount = maxCount,
    firedCount = firedCount
)

fun Task.toEntity() = TaskEntity(
    id = id,
    title = title,
    repeat = repeat.name,
    hour = hour,
    minute = minute,
    monthDay = monthDay,
    startDate = startDate,
    dueAt = dueAt,
    done = done,
    maxCount = maxCount,
    firedCount = firedCount
)

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks")
    suspend fun getAll(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: Int): TaskEntity?

    @Query("SELECT COUNT(*) FROM tasks")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(t: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(list: List<TaskEntity>)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: Int)
}

/** الگوی وظیفه (روزانه / هفتگی / سررسید). نوبت‌ها جدا در جدول instances نگه‌داری می‌شوند. */
@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** DAY | WEEKLY | DUE */
    val kind: String,
    val category: String,
    /** ۰ عادی، ۱ مهم، ۲ فوری */
    val priority: Int,
    val note: String,
    /** epoch-day میلادی: روز کار / شروع / اولین سررسید */
    val startDate: Long,
    /** epoch-day پایان؛ ۰ یعنی بدون پایان */
    val endDate: Long,
    /** دقیقهٔ روز (ساعت*۶۰+دقیقه)؛ -۱ یعنی بدون ساعت */
    val timeMin: Int,
    /** بیت ۰ = شنبه ... بیت ۶ = جمعه */
    val weekMask: Int,
    /** NONE | DAY | WEEK | MONTH | YEAR (فقط سررسیدها) */
    val unit: String,
    val repeatEvery: Int,
    /** روز ماه شمسی برای تکرار ماهانه/سالانه */
    val monthDay: Int,
    /** مبلغ اختیاری به کوچک‌ترین واحد؛ ۰ یعنی ندارد */
    val amount: Long,
    val paused: Boolean,
    val alarmSound: Boolean,
    val alarmVibrate: Boolean,
    val alarmNotify: Boolean,
    /** فاصلهٔ هشدار به دقیقه؛ ۰ یعنی بدون تکرار هشدار */
    val alertGapMin: Int,
    /** حداکثر هشدار (۱ تا ۳) */
    val maxAlerts: Int,
    val createdAt: Long
)

/** هر نوبت یک نمونهٔ مستقل است (اجارهٔ آبان و آذر دو ردیف جدا). */
@Entity(
    tableName = "instances",
    indices = [Index(value = ["templateId", "dueDate"], unique = true)]
)
data class InstanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val dueDate: Long,
    val timeMin: Int,
    /** میلی‌ثانیهٔ سررسید (برای بدون‌ساعت‌ها ابتدای روز) */
    val dueAt: Long,
    /** PENDING | DONE | MISSED | CANCELED */
    val status: String,
    val doneAt: Long,
    val alertsFired: Int,
    val nextAlertAt: Long,
    val snoozedUntil: Long
)

@Dao
interface PlanDao {
    @Query("SELECT * FROM templates ORDER BY createdAt DESC")
    fun observeTemplates(): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM instances WHERE status = 'PENDING' OR dueDate >= :from")
    fun observeInstances(from: Long): Flow<List<InstanceEntity>>

    @Query("SELECT * FROM templates")
    suspend fun allTemplates(): List<TemplateEntity>

    @Query("SELECT * FROM templates WHERE id = :id")
    suspend fun template(id: Long): TemplateEntity?

    @Insert
    suspend fun insertTemplate(t: TemplateEntity): Long

    @Update
    suspend fun updateTemplate(t: TemplateEntity)

    @Query("DELETE FROM templates WHERE id = :id")
    suspend fun deleteTemplate(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertInstance(i: InstanceEntity): Long

    @Update
    suspend fun updateInstance(i: InstanceEntity)

    @Query("SELECT * FROM instances WHERE id = :id")
    suspend fun instance(id: Long): InstanceEntity?

    @Query("SELECT * FROM instances WHERE templateId = :tid")
    suspend fun instancesOf(tid: Long): List<InstanceEntity>

    @Query("SELECT * FROM instances WHERE status = 'PENDING'")
    suspend fun pending(): List<InstanceEntity>

    @Query("DELETE FROM instances WHERE templateId = :tid")
    suspend fun deleteInstancesOf(tid: Long)

    @Query("DELETE FROM instances WHERE id = :id")
    suspend fun deleteInstance(id: Long)

    @Query(
        "UPDATE instances SET status = 'MISSED' WHERE status = 'PENDING' AND dueDate < :today " +
            "AND templateId IN (SELECT id FROM templates WHERE kind = 'WEEKLY')"
    )
    suspend fun markMissedWeekly(today: Long)
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `templates` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, `kind` TEXT NOT NULL, `category` TEXT NOT NULL, " +
                "`priority` INTEGER NOT NULL, `note` TEXT NOT NULL, " +
                "`startDate` INTEGER NOT NULL, `endDate` INTEGER NOT NULL, " +
                "`timeMin` INTEGER NOT NULL, `weekMask` INTEGER NOT NULL, " +
                "`unit` TEXT NOT NULL, `repeatEvery` INTEGER NOT NULL, `monthDay` INTEGER NOT NULL, " +
                "`amount` INTEGER NOT NULL, `paused` INTEGER NOT NULL, " +
                "`alarmSound` INTEGER NOT NULL, `alarmVibrate` INTEGER NOT NULL, `alarmNotify` INTEGER NOT NULL, " +
                "`alertGapMin` INTEGER NOT NULL, `maxAlerts` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `instances` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`templateId` INTEGER NOT NULL, `dueDate` INTEGER NOT NULL, `timeMin` INTEGER NOT NULL, " +
                "`dueAt` INTEGER NOT NULL, `status` TEXT NOT NULL, `doneAt` INTEGER NOT NULL, " +
                "`alertsFired` INTEGER NOT NULL, `nextAlertAt` INTEGER NOT NULL, `snoozedUntil` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_instances_templateId_dueDate` " +
                "ON `instances` (`templateId`, `dueDate`)"
        )
    }
}

/** یک قلم از لیست خرید. قیمت‌ها قیمت هر واحد (به کوچک‌ترین واحد پولی)؛ ۰ یعنی ثبت نشده. */
@Entity(tableName = "shopping")
data class ShoppingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String,
    val qty: Int,
    val estPrice: Long,
    val actualPrice: Long,
    /** epoch-day برنامه‌ریزی یا خرید */
    val date: Long,
    val bought: Boolean,
    val boughtAt: Long,
    val note: String,
    val createdAt: Long,
    /** شناسهٔ هزینهٔ مالیِ ساخته‌شده از این قلم؛ ۰ یعنی هنوز ثبت نشده */
    @ColumnInfo(defaultValue = "0") val expenseId: Long = 0
)

data class NameCat(val name: String, val category: String)

@Dao
interface ShopDao {
    @Query("SELECT * FROM shopping WHERE bought = 0 OR date >= :from ORDER BY bought, date, id")
    fun observeAll(from: Long): Flow<List<ShoppingEntity>>

    @Query("SELECT name, category FROM shopping GROUP BY name ORDER BY COUNT(*) DESC, MAX(createdAt) DESC LIMIT 60")
    fun observeNames(): Flow<List<NameCat>>

    @Insert
    suspend fun insert(e: ShoppingEntity): Long

    @Insert
    suspend fun insertAll(list: List<ShoppingEntity>)

    @Update
    suspend fun update(e: ShoppingEntity)

    @Query("DELETE FROM shopping WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE shopping SET expenseId = 0 WHERE expenseId = :txId")
    suspend fun unlinkExpense(txId: Long)
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `shopping` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `category` TEXT NOT NULL, `qty` INTEGER NOT NULL, " +
                "`estPrice` INTEGER NOT NULL, `actualPrice` INTEGER NOT NULL, `date` INTEGER NOT NULL, " +
                "`bought` INTEGER NOT NULL, `boughtAt` INTEGER NOT NULL, `note` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)"
        )
    }
}

// ---------- مالی ----------

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** BANK | CASH */
    val type: String,
    val initial: Long,
    val createdAt: Long
)

/** تراکنش: INCOME | EXPENSE | TRANSFER (انتقال نه درآمد است نه هزینه) */
@Entity(tableName = "finance_tx")
data class TxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val amount: Long,
    val category: String,
    val accountId: Long,
    /** مقصد انتقال؛ ۰ برای درآمد/هزینه */
    val toAccountId: Long,
    val date: Long,
    val note: String,
    /** حساب دیگران (مشتری) مرتبط؛ ۰ یعنی ندارد */
    val partyId: Long,
    /** فاکتور مرتبط (دریافتی فاکتور)؛ ۰ یعنی ندارد */
    val invoiceId: Long,
    val createdAt: Long
)

/** حساب دیگران: مشتری/طرف حساب با مانده (فاکتورها − دریافتی‌ها) */
@Entity(tableName = "parties")
data class PartyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String,
    val note: String,
    val createdAt: Long
)

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val unit: String,
    val price: Long,
    val createdAt: Long
)

@Entity(tableName = "invoices")
data class InvoiceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: Int,
    val partyId: Long,
    val date: Long,
    val note: String,
    val createdAt: Long
)

/** ردیف فاکتور؛ نام و قیمت در لحظهٔ صدور ذخیره می‌شود (تغییر محصول فاکتور قدیمی را عوض نمی‌کند) */
@Entity(tableName = "invoice_lines")
data class InvoiceLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceId: Long,
    val name: String,
    val unit: String,
    val qty: Int,
    val unitPrice: Long
)

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val category: String,
    val amount: Long
)

@Dao
interface FinDao {
    @Query("SELECT * FROM accounts ORDER BY id")
    fun observeAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM finance_tx ORDER BY date DESC, id DESC")
    fun observeTx(): Flow<List<TxEntity>>

    @Query("SELECT * FROM parties ORDER BY name")
    fun observeParties(): Flow<List<PartyEntity>>

    @Query("SELECT * FROM products ORDER BY name")
    fun observeProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM invoices ORDER BY number DESC")
    fun observeInvoices(): Flow<List<InvoiceEntity>>

    @Query("SELECT * FROM invoice_lines")
    fun observeLines(): Flow<List<InvoiceLineEntity>>

    @Query("SELECT * FROM budgets")
    fun observeBudgets(): Flow<List<BudgetEntity>>

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun accountCount(): Int

    @Insert
    suspend fun insertAccount(a: AccountEntity): Long

    @Update
    suspend fun updateAccount(a: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteAccount(id: Long)

    @Query("SELECT COUNT(*) FROM finance_tx WHERE accountId = :id OR toAccountId = :id")
    suspend fun txCountForAccount(id: Long): Int

    @Insert
    suspend fun insertTx(t: TxEntity): Long

    @Update
    suspend fun updateTx(t: TxEntity)

    @Query("DELETE FROM finance_tx WHERE id = :id")
    suspend fun deleteTx(id: Long)

    @Insert
    suspend fun insertParty(p: PartyEntity): Long

    @Update
    suspend fun updateParty(p: PartyEntity)

    @Query("DELETE FROM parties WHERE id = :id")
    suspend fun deleteParty(id: Long)

    @Query("SELECT (SELECT COUNT(*) FROM invoices WHERE partyId = :id) + (SELECT COUNT(*) FROM finance_tx WHERE partyId = :id)")
    suspend fun partyUsage(id: Long): Int

    @Insert
    suspend fun insertProduct(p: ProductEntity): Long

    @Update
    suspend fun updateProduct(p: ProductEntity)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteProduct(id: Long)

    @Insert
    suspend fun insertInvoice(i: InvoiceEntity): Long

    @Update
    suspend fun updateInvoice(i: InvoiceEntity)

    @Query("DELETE FROM invoices WHERE id = :id")
    suspend fun deleteInvoice(id: Long)

    @Query("SELECT MAX(number) FROM invoices")
    suspend fun maxInvoiceNumber(): Int?

    @Insert
    suspend fun insertLines(list: List<InvoiceLineEntity>)

    @Query("DELETE FROM invoice_lines WHERE invoiceId = :invoiceId")
    suspend fun deleteLines(invoiceId: Long)

    @Query("SELECT COUNT(*) FROM finance_tx WHERE invoiceId = :invoiceId")
    suspend fun paymentCount(invoiceId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setBudget(b: BudgetEntity)

    @Query("DELETE FROM budgets WHERE category = :category")
    suspend fun deleteBudget(category: String)
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `shopping` ADD COLUMN `expenseId` INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `accounts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `type` TEXT NOT NULL, `initial` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `finance_tx` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`type` TEXT NOT NULL, `amount` INTEGER NOT NULL, `category` TEXT NOT NULL, " +
                "`accountId` INTEGER NOT NULL, `toAccountId` INTEGER NOT NULL, `date` INTEGER NOT NULL, " +
                "`note` TEXT NOT NULL, `partyId` INTEGER NOT NULL, `invoiceId` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `parties` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `phone` TEXT NOT NULL, `note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `products` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `unit` TEXT NOT NULL, `price` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `invoices` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`number` INTEGER NOT NULL, `partyId` INTEGER NOT NULL, `date` INTEGER NOT NULL, " +
                "`note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `invoice_lines` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`invoiceId` INTEGER NOT NULL, `name` TEXT NOT NULL, `unit` TEXT NOT NULL, " +
                "`qty` INTEGER NOT NULL, `unitPrice` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `budgets` (`category` TEXT NOT NULL, `amount` INTEGER NOT NULL, " +
                "PRIMARY KEY(`category`))"
        )
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `habits` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, `icon` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                "`weekMask` INTEGER NOT NULL, `target` INTEGER NOT NULL, `timeMin` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `startDay` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `habit_logs` (`habitId` INTEGER NOT NULL, `day` INTEGER NOT NULL, " +
                "PRIMARY KEY(`habitId`, `day`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `goals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, `kind` TEXT NOT NULL, `target` INTEGER NOT NULL, " +
                "`current` INTEGER NOT NULL, `step` INTEGER NOT NULL, `unit` TEXT NOT NULL, " +
                "`deadline` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `doneAt` INTEGER NOT NULL)"
        )
    }
}

@Database(
    entities = [
        TaskEntity::class, TemplateEntity::class, InstanceEntity::class, ShoppingEntity::class,
        AccountEntity::class, TxEntity::class, PartyEntity::class, ProductEntity::class,
        InvoiceEntity::class, InvoiceLineEntity::class, BudgetEntity::class,
        HabitEntity::class, HabitLogEntity::class, GoalEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun planDao(): PlanDao
    abstract fun shopDao(): ShopDao
    abstract fun finDao(): FinDao
    abstract fun habitDao(): HabitDao

    companion object {
        @Volatile private var inst: AppDatabase? = null

        fun get(ctx: Context): AppDatabase = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(
                ctx.applicationContext, AppDatabase::class.java, "taskreminder.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build().also { inst = it }
        }
    }
}
