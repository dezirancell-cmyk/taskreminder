package com.example.taskreminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Scheduler {
    const val CHANNEL = "due"
    const val ACTION_FIRE = "com.example.taskreminder.FIRE"
    const val ACTION_DONE = "com.example.taskreminder.DONE"
    const val EXTRA_ID = "task_id"

    fun ensureChannel(ctx: Context) {
        val ch = NotificationChannel(CHANNEL, "سررسید کارها", NotificationManager.IMPORTANCE_HIGH)
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun broadcast(ctx: Context, id: Int, action: String): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java).setAction(action).putExtra(EXTRA_ID, id)
        return PendingIntent.getBroadcast(
            ctx, id, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun schedule(ctx: Context, t: Task) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = broadcast(ctx, t.id, ACTION_FIRE)
        val at = Recurrence.alarmTime(t, System.currentTimeMillis())
        if (at == null) {
            am.cancel(pi)
            return
        }
        val canExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (canExact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    fun cancel(ctx: Context, id: Int) {
        ctx.getSystemService(AlarmManager::class.java).cancel(broadcast(ctx, id, ACTION_FIRE))
        NotificationManagerCompat.from(ctx).cancel(id)
    }

    fun rescheduleAll(ctx: Context) {
        TaskStore(ctx).loadSynced().forEach { schedule(ctx, it) }
    }

    @SuppressLint("MissingPermission")
    fun notify(ctx: Context, t: Task) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, t.id, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(t.title)
            .setContentText("زمان انجام این کار رسیده است")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, "انجام شد", broadcast(ctx, t.id, ACTION_DONE))
            .build()
        NotificationManagerCompat.from(ctx).notify(t.id, n)
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getIntExtra(Scheduler.EXTRA_ID, -1)
        val store = TaskStore(ctx)
        val tasks = store.load()
        val t = tasks.find { it.id == id } ?: return
        val now = System.currentTimeMillis()
        when (intent.action) {
            Scheduler.ACTION_FIRE -> {
                if (t.done) return
                if (t.repeat == Repeat.ONCE) {
                    Scheduler.notify(ctx, t)
                    return
                }
                // آلارم قدیمی (مثلاً بعد از ویرایش) نباید شمارنده را بالا ببرد
                if (t.dueAt > now + 60_000L) {
                    Scheduler.schedule(ctx, t)
                    return
                }
                Scheduler.notify(ctx, t)
                val n = Recurrence.catchUp(Recurrence.afterFire(t, now), now)
                store.save(tasks.map { if (it.id == id) n else it })
                Scheduler.schedule(ctx, n)
            }
            Scheduler.ACTION_DONE -> {
                NotificationManagerCompat.from(ctx).cancel(id)
                if (t.repeat == Repeat.ONCE) {
                    store.save(tasks.map { if (it.id == id) it.copy(done = true) else it })
                }
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Scheduler.rescheduleAll(ctx)
    }
}
