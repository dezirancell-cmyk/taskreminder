package com.example.taskreminder

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class TaskViewModel(private val app: Application) : AndroidViewModel(app) {
    private val repo = PlanRepository.get(app)

    val items: StateFlow<List<PlanItem>> = repo
        .observeItems(LocalDate.now().toEpochDay() - 60)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val templates: StateFlow<List<TemplateEntity>> = repo
        .observeTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
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
}
