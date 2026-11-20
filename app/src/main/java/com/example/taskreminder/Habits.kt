package com.example.taskreminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId

object HabitKind {
    const val DAILY = "DAILY"
    const val WEEKLY = "WEEKLY"
}

object GoalKind {
    const val SHORT = "SHORT"
    const val LONG = "LONG"
}

/**
 * عادت. روزانه: روزهای هفته با [weekMask] (شنبه = بیت ۰ … جمعه = بیت ۶).
 * هفتگی: [target] بار در هفته، در هر روزی.
 * [timeMin] منفی یعنی بدون یادآور. [startDay] روز شروع (epoch day) برای محاسبهٔ نرخ موفقیت.
 */
@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val icon: String,
    val kind: String,
    val weekMask: Int,
    val target: Int,
    val timeMin: Int,
    val createdAt: Long,
    val startDay: Long
)

/** یک ردیف = «این عادت در این روز انجام شد» */
@Entity(tableName = "habit_logs", primaryKeys = ["habitId", "day"])
data class HabitLogEntity(
    val habitId: Long,
    val day: Long
)

/** هدف کوتاه‌مدت یا بلندمدت با پیشرفت عددی. [deadline] صفر یعنی بدون مهلت؛ [doneAt] صفر یعنی تکمیل نشده. */
@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val kind: String,
    val target: Long,
    val current: Long,
    val step: Long,
    val unit: String,
    val deadline: Long,
    val createdAt: Long,
    val doneAt: Long
)

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY createdAt")
    fun observeHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habit_logs")
    fun observeLogs(): Flow<List<HabitLogEntity>>

    @Query("SELECT * FROM habits")
    suspend fun allHabits(): List<HabitEntity>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun habit(id: Long): HabitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHabit(h: HabitEntity): Long

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun deleteHabit(id: Long)

    @Query("DELETE FROM habit_logs WHERE habitId = :id")
    suspend fun deleteLogs(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addLog(l: HabitLogEntity): Long

    @Query("DELETE FROM habit_logs WHERE habitId = :id AND day = :day")
    suspend fun removeLog(id: Long, day: Long)

    @Query("SELECT COUNT(*) FROM habit_logs WHERE habitId = :id AND day = :day")
    suspend fun logCount(id: Long, day: Long): Int

    @Query("SELECT * FROM goals ORDER BY createdAt DESC")
    fun observeGoals(): Flow<List<GoalEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGoal(g: GoalEntity): Long

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun deleteGoal(id: Long)
}

/** عادت‌های آماده: با یک لمس ساخته می‌شوند */
data class HabitPreset(val title: String, val icon: String, val kind: String, val target: Int)

object HabitPresets {
    val list = listOf(
        HabitPreset("ورزش", "fitness", HabitKind.WEEKLY, 3),
        HabitPreset("مطالعه", "book", HabitKind.DAILY, 1),
        HabitPreset("پیاده‌روی", "walk", HabitKind.DAILY, 1),
        HabitPreset("نوشیدن آب", "water", HabitKind.DAILY, 1),
        HabitPreset("خواب منظم", "sleep", HabitKind.DAILY, 1)
    )
}

class GoalProgress(val percent: Int, val remaining: Long, val daysLeft: Long?, val done: Boolean)

/** منطق خالص عادت‌ها و اهداف (بدون وابستگی به اندروید؛ قابل تست واحد) */
object HabitLogic {
    const val ALL_DAYS = 127

    fun dayBit(day: Long): Int = Engine.dayIndex(LocalDate.ofEpochDay(day))

    fun weekStart(day: Long): Long = day - dayBit(day)

    /** آیا این عادت در این روز برنامه دارد؟ (هفتگی‌ها هر روز قابل ثبت‌اند) */
    fun scheduled(h: HabitEntity, day: Long): Boolean =
        h.kind == HabitKind.WEEKLY || (h.weekMask and (1 shl dayBit(day))) != 0

    private fun weekCount(done: Set<Long>, ws: Long): Int = (ws..ws + 6).count { it in done }

    /** تعداد مورد انتظار در یک هفتهٔ کامل */
    fun expectedPerWeek(h: HabitEntity): Int =
        if (h.kind == HabitKind.WEEKLY) h.target.coerceAtLeast(1)
        else Integer.bitCount(h.weekMask and ALL_DAYS)

    fun countInWeek(h: HabitEntity, done: Set<Long>, ws: Long): Int {
        val c = if (h.kind == HabitKind.WEEKLY) weekCount(done, ws)
        else (ws..ws + 6).count { it in done && scheduled(h, it) }
        return c
    }

    private fun lowerBound(h: HabitEntity, done: Set<Long>): Long =
        minOf(h.startDay, done.minOrNull() ?: h.startDay)

    /** رشتهٔ فعلی: روزانه = روزهای برنامه‌دار متوالی (امروز انجام‌نشده رشته را نمی‌شکند)؛ هفتگی = هفته‌های متوالیِ رسیده به هدف */
    fun streak(h: HabitEntity, done: Set<Long>, today: Long): Int {
        if (expectedPerWeek(h) <= 0) return 0
        return if (h.kind == HabitKind.WEEKLY) {
            var w = weekStart(today)
            var n = 0
            if (weekCount(done, w) >= h.target.coerceAtLeast(1)) n++
            w -= 7
            var guard = 0
            while (guard++ < 520 && weekCount(done, w) >= h.target.coerceAtLeast(1)) {
                n++
                w -= 7
            }
            n
        } else {
            val low = lowerBound(h, done)
            var d = today
            if (scheduled(h, d) && d !in done) d--
            var n = 0
            var guard = 0
            while (d >= low && guard++ < 4000) {
                if (!scheduled(h, d)) {
                    d--
                    continue
                }
                if (d in done) {
                    n++
                    d--
                } else break
            }
            n
        }
    }

    /** بهترین رشتهٔ تاریخچه */
    fun bestStreak(h: HabitEntity, done: Set<Long>, today: Long): Int {
        if (expectedPerWeek(h) <= 0) return 0
        val low = lowerBound(h, done)
        var best = 0
        var run = 0
        if (h.kind == HabitKind.WEEKLY) {
            val cur = weekStart(today)
            var w = weekStart(low)
            while (w <= cur) {
                val met = weekCount(done, w) >= h.target.coerceAtLeast(1)
                if (met) {
                    run++
                    best = maxOf(best, run)
                } else if (w != cur) run = 0
                w += 7
            }
        } else {
            var d = low
            while (d <= today) {
                if (scheduled(h, d)) {
                    if (d in done) {
                        run++
                        best = maxOf(best, run)
                    } else if (d != today) run = 0
                }
                d++
            }
        }
        return best
    }

    /**
     * نرخ موفقیت (درصد): روزانه = ۳۰ روز اخیر؛ هفتگی = ۴ هفتهٔ اخیر (هفتهٔ جاری فقط اگر رسیده باشد).
     * null یعنی هنوز دادهٔ کافی برای محاسبه نیست.
     */
    fun rate(h: HabitEntity, done: Set<Long>, today: Long): Int? {
        if (h.kind == HabitKind.WEEKLY) {
            val cur = weekStart(today)
            val first = maxOf(weekStart(h.startDay), cur - 28)
            var total = 0
            var met = 0
            var w = first
            while (w <= cur) {
                val ok = weekCount(done, w) >= h.target.coerceAtLeast(1)
                if (w < cur || ok) {
                    total++
                    if (ok) met++
                }
                w += 7
            }
            return if (total == 0) null else met * 100 / total
        }
        val from = maxOf(h.startDay, today - 29)
        var total = 0
        var ok = 0
        var d = from
        while (d <= today) {
            if (scheduled(h, d)) {
                if (d in done) {
                    total++
                    ok++
                } else if (d != today) total++
            }
            d++
        }
        return if (total == 0) null else ok * 100 / total
    }

    /** پیشرفت هفتهٔ جاری برای همهٔ عادت‌ها: انجام‌شده از مورد انتظار */
    fun weekProgress(habits: List<HabitEntity>, logs: Map<Long, Set<Long>>, today: Long): Pair<Int, Int> {
        val ws = weekStart(today)
        var done = 0
        var expected = 0
        for (h in habits) {
            val e = expectedPerWeek(h)
            if (e <= 0) continue
            expected += e
            done += minOf(e, countInWeek(h, logs[h.id].orEmpty(), ws))
        }
        return Pair(done, expected)
    }

    /** نسبت انجام (۰ تا ۱) هر یک از [weeks] هفتهٔ اخیر، قدیمی‌ترین اول */
    fun weeklyFractions(h: HabitEntity, done: Set<Long>, today: Long, weeks: Int): List<Float> {
        val cur = weekStart(today)
        val e = expectedPerWeek(h)
        return (weeks - 1 downTo 0).map { k ->
            if (e <= 0) 0f
            else (countInWeek(h, done, cur - 7L * k).toFloat() / e).coerceIn(0f, 1f)
        }
    }

    fun goalProgress(g: GoalEntity, today: Long): GoalProgress {
        val t = g.target.coerceAtLeast(1)
        val pct = (g.current * 100 / t).toInt().coerceIn(0, 100)
        val done = g.current >= g.target
        val left = if (g.deadline > 0L) g.deadline - today else null
        return GoalProgress(pct, (g.target - g.current).coerceAtLeast(0), left, done)
    }
}

/** یادآور روزانهٔ عادت‌ها (هر عادت یک آلارم یک‌باره که پس از هر بار اجرا برای نوبت بعد تنظیم می‌شود) */
object HabitAlarms {
    const val ACTION_FIRE = "com.example.taskreminder.HABIT_FIRE"
    const val ACTION_DONE = "com.example.taskreminder.HABIT_DONE"
    const val EXTRA_ID = "habit_id"
    private const val CH = "habit_reminder"

    fun notifId(habitId: Long): Int = 1_000_000 + habitId.toInt()

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH, "یادآور عادت‌ها", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    private fun pi(ctx: Context, id: Long, action: String): PendingIntent {
        val i = Intent(ctx, HabitReceiver::class.java)
            .setAction(action)
            .setData(Uri.parse("taskreminder://habit/$id"))
            .putExtra(EXTRA_ID, id)
        return PendingIntent.getBroadcast(
            ctx, notifId(id), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /** زمان بعدی یادآور (میلی‌ثانیه) یا ۰ اگر یادآور ندارد */
    fun nextFire(h: HabitEntity, nowMs: Long): Long {
        if (h.timeMin < 0) return 0L
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        for (off in 0L..8L) {
            val d = today.plusDays(off)
            if (!HabitLogic.scheduled(h, d.toEpochDay())) continue
            val t = d.atTime(h.timeMin / 60, h.timeMin % 60).atZone(zone).toInstant().toEpochMilli()
            if (t > nowMs) return t
        }
        return 0L
    }

    fun schedule(ctx: Context, h: HabitEntity) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val p = pi(ctx, h.id, ACTION_FIRE)
        val at = nextFire(h, System.currentTimeMillis())
        if (at == 0L) {
            am.cancel(p)
            return
        }
        val canExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (canExact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
    }

    fun cancel(ctx: Context, id: Long) {
        ctx.getSystemService(AlarmManager::class.java).cancel(pi(ctx, id, ACTION_FIRE))
        NotificationManagerCompat.from(ctx).cancel(notifId(id))
    }

    suspend fun rescheduleAll(ctx: Context) {
        AppDatabase.get(ctx).habitDao().allHabits().forEach { schedule(ctx, it) }
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun notify(ctx: Context, h: HabitEntity) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, notifId(h.id), Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, CH)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(h.title)
            .setContentText("وقت انجام این عادت است")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, "انجام شد", pi(ctx, h.id, ACTION_DONE))
            .build()
        NotificationManagerCompat.from(ctx).notify(notifId(h.id), n)
    }
}
