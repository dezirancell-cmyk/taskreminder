package com.example.taskreminder

import android.content.Context
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

@Database(
    entities = [TaskEntity::class, TemplateEntity::class, InstanceEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun planDao(): PlanDao

    companion object {
        @Volatile private var inst: AppDatabase? = null

        fun get(ctx: Context): AppDatabase = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(
                ctx.applicationContext, AppDatabase::class.java, "taskreminder.db"
            ).addMigrations(MIGRATION_1_2).build().also { inst = it }
        }
    }
}
