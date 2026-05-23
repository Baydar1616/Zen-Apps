package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.SoundSynthesizer
import com.example.database.AppDatabase
import com.example.database.Reminder
import com.example.database.ReminderRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ReminderViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: ReminderRepository
    val uiState: StateFlow<List<Reminder>>
    val selectedTheme = MutableStateFlow(com.example.ui.theme.ThemePreset.CLASSIC_ABYSSAL)

    val synth = SoundSynthesizer()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = ReminderRepository(database.reminderDao())

        uiState = repository.allReminders
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

        // Launch peaceful procedural ambient sounds
        synth.start()
    }

    fun addReminder(title: String, description: String, priority: String, fishType: Int, dueDate: Long) {
        viewModelScope.launch {
            val reminder = Reminder(
                title = title,
                description = description,
                priority = priority,
                fishType = fishType,
                dueDate = dueDate,
                progress = 0,
                isCompleted = false
            )
            repository.insert(reminder)
            // Visual trigger bubbles!
            synth.triggerBubblePop()
        }
    }

    fun updateProgress(reminder: Reminder, newProgress: Int) {
        viewModelScope.launch {
            val targetProgress = newProgress.coerceIn(0, 100)
            val isCompletedNow = targetProgress == 100

            // Trigger lovely procedural splashes and bubble bursts on full completion!
            if (isCompletedNow && !reminder.isCompleted) {
                synth.triggerSplash()
                repeat(3) { synth.triggerBubblePop() }
            }

            val updated = reminder.copy(
                progress = targetProgress,
                isCompleted = isCompletedNow
            )
            repository.update(updated)
        }
    }

    fun deleteReminder(reminder: Reminder) {
        viewModelScope.launch {
            repository.delete(reminder)
            synth.triggerBubblePop()
        }
    }

    fun toggleComplete(reminder: Reminder) {
        viewModelScope.launch {
            val nextCompleted = !reminder.isCompleted
            val nextProgress = if (nextCompleted) 100 else 0
            if (nextCompleted) {
                synth.triggerSplash()
                repeat(2) { synth.triggerBubblePop() }
            }
            repository.update(reminder.copy(isCompleted = nextCompleted, progress = nextProgress))
        }
    }

    fun splashPond() {
        synth.triggerSplash()
    }

    fun feedFish() {
        synth.triggerSplash()
        repeat(2) { synth.triggerBubblePop() }
    }

    fun selectTheme(theme: com.example.ui.theme.ThemePreset) {
        selectedTheme.value = theme
    }

    override fun onCleared() {
        super.onCleared()
        // Ensure no audio threads leak when finished
        synth.stop()
    }
}
