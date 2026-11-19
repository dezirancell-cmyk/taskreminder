package com.example.taskreminder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

class MainActivity : ComponentActivity() {

    private lateinit var store: TaskStore
    private var tasks by mutableStateOf<List<Task>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = TaskStore(this)
        Scheduler.ensureChannel(this)
        tasks = store.load()
        Scheduler.rescheduleAll(this)

        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    AskNotificationPermission()
                    TaskApp(
                        tasks = tasks,
                        onUpsert = ::upsert,
                        onToggle = ::toggle,
                        onDelete = ::delete
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // ممکن است کاربر از روی نوتیفیکیشن «انجام شد» را زده باشد
        tasks = store.load()
    }

    private fun commit(list: List<Task>) {
        tasks = list
        store.save(list)
    }

    private fun upsert(t: Task) {
        val l = tasks.toMutableList()
        val i = l.indexOfFirst { it.id == t.id }
        if (i >= 0) l[i] = t else l.add(t)
        commit(l)
        Scheduler.schedule(this, t)
    }

    private fun toggle(t: Task) {
        val n = if (t.repeat == Repeat.ONCE && t.done) t.copy(done = false)
        else Recurrence.afterDone(t, System.currentTimeMillis())
        commit(tasks.map { if (it.id == t.id) n else it })
        Scheduler.cancel(this, t.id) // نوتیفیکیشن باز را هم می‌بندد
        Scheduler.schedule(this, n)
    }

    private fun delete(t: Task) {
        Scheduler.cancel(this, t.id)
        commit(tasks.filter { it.id != t.id })
    }
}
