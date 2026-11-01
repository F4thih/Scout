package com.pocketcore.assistant.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pocketcore.assistant.core.CommandActionType
import com.pocketcore.assistant.core.CommandDispatcher
import com.pocketcore.assistant.core.CommandResult
import com.pocketcore.assistant.core.FeatureRegistry
import com.pocketcore.assistant.data.PocketCoreDatabase
import com.pocketcore.assistant.data.entity.CommandLogEntity
import com.pocketcore.assistant.data.entity.NoteEntity
import com.pocketcore.assistant.data.entity.ReminderEntity
import com.pocketcore.assistant.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AssistantUiState(
    val inputText: String = "",
    val isExecuting: Boolean = false,
    val lastResult: CommandResult? = null,
    val isListening: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM
)

class AssistantViewModel(application: Application) : AndroidViewModel(application) {

    private val db = PocketCoreDatabase.getInstance(application)
    private val dispatcher = CommandDispatcher()
    private val prefs = application.getSharedPreferences("scout_settings", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(
        AssistantUiState(
            themeMode = loadThemeMode()
        )
    )
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    // Database reactive streams
    val notes = db.noteDao().getAllNotes().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val reminders = db.reminderDao().getAllReminders().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val recentLogs = db.commandLogDao().getRecentLogs().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    private fun loadThemeMode(): ThemeMode {
        val saved = prefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        return try {
            ThemeMode.valueOf(saved)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
        _uiState.value = _uiState.value.copy(themeMode = mode)
    }

    fun onInputChanged(newText: String) {
        _uiState.value = _uiState.value.copy(inputText = newText)
    }

    fun executeCommand(context: Context, command: String = _uiState.value.inputText) {
        if (command.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExecuting = true)
            val result = dispatcher.dispatch(context, command)
            _uiState.value = _uiState.value.copy(
                inputText = "",
                isExecuting = false,
                lastResult = result
            )

            // Log command locally
            db.commandLogDao().insertLog(
                CommandLogEntity(
                    rawInput = command,
                    feedbackMessage = result.feedbackMessage,
                    success = result.success
                )
            )
        }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch {
            db.noteDao().deleteNote(note)
        }
    }

    fun deleteReminder(reminder: ReminderEntity) {
        viewModelScope.launch {
            db.reminderDao().deleteReminder(reminder)
        }
    }
}
