package com.example.taskreminder

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * تنها نقطهٔ دسترسی به داده‌ها. تمام خواندن‌-تغییر-نوشتن‌ها زیر یک Mutex انجام می‌شود
 * تا اعلان (AlarmReceiver) و صفحهٔ اصلی هم‌زمان تغییر یکدیگر را از بین نبرند.
 */
class TaskRepository private constructor(private val appCtx: Context) {
    private val dao = AppDatabase.get(appCtx).taskDao()
    private val mutex = Mutex()

    val tasks: Flow<List<Task>> = dao.observeAll().map { l -> l.map { it.toTask() } }

    /** یک‌بار داده‌های قدیمی SharedPreferences را به Room منتقل می‌کند (داده قدیمی حذف نمی‌شود). */
    suspend fun migrateLegacyIfNeeded() = mutex.withLock {
        val flag = appCtx.getSharedPreferences("migration", Context.MODE_PRIVATE)
        if (flag.getBoolean("tasks_to_room_v1", false)) return@withLock
        if (dao.count() == 0) {
            val legacy = TaskStore(appCtx).load()
            if (legacy.isNotEmpty()) dao.upsertAll(legacy.map { it.toEntity() })
        }
        flag.edit().putBoolean("tasks_to_room_v1", true).commit()
    }

    /** به‌روزرسانی وقوع‌های جا افتاده (مثلاً وقتی گوشی خاموش بوده) و برگرداندن فهرست */
    suspend fun syncAll(): List<Task> = mutex.withLock {
        val now = System.currentTimeMillis()
        dao.getAll().map { e ->
            val t = e.toTask()
            val n = Recurrence.catchUp(t, now)
            if (n != t) dao.upsert(n.toEntity())
            n
        }
    }

    suspend fun upsert(t: Task) = mutex.withLock { dao.upsert(t.toEntity()) }

    suspend fun delete(id: Int) = mutex.withLock { dao.delete(id) }

    /** خواندن-تغییر-نوشتن اتمیک روی یک کار؛ اگر کار وجود نداشته باشد null برمی‌گرداند */
    suspend fun update(id: Int, change: (Task) -> Task): Task? = mutex.withLock {
        val cur = dao.get(id)?.toTask() ?: return@withLock null
        val n = change(cur)
        if (n != cur) dao.upsert(n.toEntity())
        n
    }

    companion object {
        @Volatile private var inst: TaskRepository? = null

        fun get(ctx: Context): TaskRepository = inst ?: synchronized(this) {
            inst ?: TaskRepository(ctx.applicationContext).also { inst = it }
        }
    }
}
