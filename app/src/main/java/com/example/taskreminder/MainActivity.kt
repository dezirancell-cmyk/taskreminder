package com.example.taskreminder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {

    private lateinit var store: TaskStore
    private var tasks by mutableStateOf<List<Task>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = TaskStore(this)
        Scheduler.ensureChannel(this)
        tasks = store.loadSynced()
        Scheduler.rescheduleAll(this)

        setContent {
            AppTheme {
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

    override fun onResume() {
        super.onResume()
        // اعلان‌ها ممکن است وقتی اپ بسته بوده شمارنده‌ها را جلو برده باشند
        tasks = store.loadSynced()
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
        Scheduler.cancel(this, t.id)
        Scheduler.schedule(this, t)
    }

    /** فقط برای کارهای یک‌بار */
    private fun toggle(t: Task) {
        val n = t.copy(done = !t.done)
        commit(tasks.map { if (it.id == t.id) n else it })
        Scheduler.cancel(this, t.id)
        Scheduler.schedule(this, n)
    }

    private fun delete(t: Task) {
        Scheduler.cancel(this, t.id)
        commit(tasks.filter { it.id != t.id })
    }
}
