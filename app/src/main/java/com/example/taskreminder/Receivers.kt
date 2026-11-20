package com.example.taskreminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
        try {
            block()
        } catch (e: Exception) {
            // خطا نباید ریسیور را بی‌پایان باز نگه دارد
        } finally {
            pending.finish()
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val app = ctx.applicationContext
        val id = intent.getLongExtra(Scheduler.EXTRA_ID, -1L)
        val action = intent.action
        if (id < 0) return
        runAsync {
            val repo = PlanRepository.get(app)
            repo.ensureMigrated()
            val now = System.currentTimeMillis()
            when (action) {
                Scheduler.ACTION_FIRE -> {
                    val item = repo.onAlert(id, now)
                    if (item != null) Scheduler.notify(app, item)
                }
                // «انجام شد» تنها راه تکمیل است؛ با آن هشدارهای باقی‌مانده لغو و سررسید بعدی ساخته می‌شود
                Scheduler.ACTION_DONE -> repo.setDone(id, true)
                Scheduler.ACTION_SNOOZE -> repo.snooze(id, now)
                // قطع زنگ: فقط صدا قطع می‌شود، کار انجام‌نشده می‌ماند
                Scheduler.ACTION_STOP -> Scheduler.dismiss(app, id)
                else -> Unit
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val app = ctx.applicationContext
        runAsync {
            Scheduler.ensureChannels(app)
            val repo = PlanRepository.get(app)
            repo.ensureMigrated()
            repo.sync(true)
            HabitAlarms.rescheduleAll(app)
        }
    }
}

class HabitReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val app = ctx.applicationContext
        val id = intent.getLongExtra(HabitAlarms.EXTRA_ID, -1L)
        val action = intent.action
        if (id < 0) return
        runAsync {
            val dao = AppDatabase.get(app).habitDao()
            val h = dao.habit(id) ?: return@runAsync
            val today = java.time.LocalDate.now().toEpochDay()
            when (action) {
                HabitAlarms.ACTION_FIRE -> {
                    // اول نوبت بعدی را بچین تا زنجیرهٔ یادآور قطع نشود
                    HabitAlarms.schedule(app, h)
                    if (HabitLogic.scheduled(h, today) && dao.logCount(id, today) == 0) {
                        HabitAlarms.notify(app, h)
                    }
                }
                HabitAlarms.ACTION_DONE -> {
                    dao.addLog(HabitLogEntity(id, today))
                    androidx.core.app.NotificationManagerCompat.from(app).cancel(HabitAlarms.notifId(id))
                }
                else -> Unit
            }
        }
    }
}
