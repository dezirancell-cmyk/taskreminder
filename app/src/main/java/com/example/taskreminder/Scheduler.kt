package com.example.taskreminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Scheduler {
    const val ACTION_FIRE = "com.example.taskreminder.FIRE"
    const val ACTION_DONE = "com.example.taskreminder.DONE"
    const val ACTION_SNOOZE = "com.example.taskreminder.SNOOZE"
    const val ACTION_STOP = "com.example.taskreminder.STOP"
    const val EXTRA_ID = "instance_id"

    private const val CH_SV = "alarm_sv"
    private const val CH_S = "alarm_s"
    private const val CH_V = "alarm_v"
    private const val CH_Q = "alarm_q"
    private val VIBRATION = longArrayOf(0, 500, 300, 500, 300, 500)

    fun channelFor(t: TemplateEntity): String = when {
        t.alarmSound && t.alarmVibrate -> CH_SV
        t.alarmSound -> CH_S
        t.alarmVibrate -> CH_V
        else -> CH_Q
    }

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.deleteNotificationChannel("due") // کانال نسخه‌های قبلی
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        fun make(id: String, name: String, withSound: Boolean, withVibe: Boolean) {
            val ch = NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH)
            if (withSound) ch.setSound(sound, attrs) else ch.setSound(null, null)
            ch.enableVibration(withVibe)
            if (withVibe) ch.vibrationPattern = VIBRATION
            nm.createNotificationChannel(ch)
        }
        make(CH_SV, "هشدار با صدا و لرزش", true, true)
        make(CH_S, "هشدار با صدا", true, false)
        make(CH_V, "هشدار با لرزش", false, true)
        make(CH_Q, "اعلان بی‌صدا", false, false)
        HabitAlarms.ensureChannel(ctx)
    }

    private fun pi(ctx: Context, id: Long, action: String): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java)
            .setAction(action)
            .setData(Uri.parse("taskreminder://instance/$id"))
            .putExtra(EXTRA_ID, id)
        return PendingIntent.getBroadcast(
            ctx, id.toInt(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /** آلارم این نوبت را (دوباره) تنظیم می‌کند؛ اگر آلارمی لازم نیست لغوش می‌کند */
    fun scheduleItem(ctx: Context, item: PlanItem, allowLate: Boolean) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val p = pi(ctx, item.inst.id, ACTION_FIRE)
        val at = Engine.alarmAt(item.inst, item.tpl, System.currentTimeMillis(), allowLate)
        if (at == 0L) {
            am.cancel(p)
            return
        }
        val canExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (canExact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
    }

    /** لغو آلارم و اعلان یک نوبت */
    fun cancelInstance(ctx: Context, id: Long) {
        ctx.getSystemService(AlarmManager::class.java).cancel(pi(ctx, id, ACTION_FIRE))
        NotificationManagerCompat.from(ctx).cancel(id.toInt())
    }

    /** فقط اعلان را برمی‌دارد (قطع زنگ)؛ آلارم بعدی سر جایش می‌ماند */
    fun dismiss(ctx: Context, id: Long) {
        NotificationManagerCompat.from(ctx).cancel(id.toInt())
    }

    /** آلارم نسخه‌های قبلی اپ (کلید Int و بدون data) را لغو می‌کند */
    fun cancelLegacy(ctx: Context, legacyId: Int) {
        val i = Intent(ctx, AlarmReceiver::class.java).setAction(ACTION_FIRE)
        val p = PendingIntent.getBroadcast(
            ctx, legacyId, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        if (p != null) {
            ctx.getSystemService(AlarmManager::class.java).cancel(p)
            p.cancel()
        }
        NotificationManagerCompat.from(ctx).cancel(legacyId)
    }

    @SuppressLint("MissingPermission")
    fun notify(ctx: Context, item: PlanItem) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannels(ctx)
        val t = item.tpl
        val id = item.inst.id
        val open = PendingIntent.getActivity(
            ctx, id.toInt(), Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val cap = t.maxAlerts.coerceIn(1, 3)
        val text = if (cap > 1) "زمان انجام این کار رسیده است (هشدار ${fa(item.inst.alertsFired)} از ${fa(cap)})"
        else "زمان انجام این کار رسیده است"
        val b = NotificationCompat.Builder(ctx, channelFor(t))
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(t.title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setTimeoutAfter(60_000L)
            .setContentIntent(open)
            .addAction(0, "انجام شد", pi(ctx, id, ACTION_DONE))
            .addAction(0, "یادآوری بعداً", pi(ctx, id, ACTION_SNOOZE))
        if (t.alarmSound) b.addAction(0, "قطع زنگ", pi(ctx, id, ACTION_STOP))
        val n = b.build()
        // صدا تا قطع زنگ (یا حداکثر یک دقیقه) تکرار شود
        if (t.alarmSound) n.flags = n.flags or Notification.FLAG_INSISTENT
        NotificationManagerCompat.from(ctx).notify(id.toInt(), n)
    }
}
