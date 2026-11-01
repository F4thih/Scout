package com.pocketcore.assistant.core

import android.content.Context
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Result returned by an AssistantModule execution.
 */
data class CommandResult(
    val success: Boolean,
    val feedbackMessage: String,
    val shouldDismissUi: Boolean = false,
    val actionType: CommandActionType = CommandActionType.INFORMATIONAL
)

enum class CommandActionType {
    INFORMATIONAL,
    REMINDER_SCHEDULED,
    MACRO_EXECUTED,
    NOTE_SAVED,
    APP_LAUNCHED,
    ERROR
}

/**
 * AssistantModule is the core plugin interface for PocketCore Assistant.
 * Any new offline feature, hardware macro, or system utility must implement
 * this interface to be seamlessly recognized by the system.
 */
interface AssistantModule {
    /**
     * Unique identifier for the module (e.g. "reminder", "macro", "note").
     */
    val id: String

    /**
     * User-facing display name shown in settings / quick toggles.
     */
    val displayName: String

    /**
     * Brief description of capabilities and example phrases.
     */
    val description: String

    /**
     * Optional icon associated with this module.
     */
    val icon: ImageVector? get() = null

    /**
     * Priority order for handling inputs. Higher priority modules are evaluated first.
     */
    val priority: Int get() = 0

    /**
     * Determines whether this module can process the provided user text/voice input.
     * Note: [input] is passed in trimmed and normalized.
     */
    fun canHandle(input: String): Boolean

    /**
     * Executes the requested command offline and returns a [CommandResult].
     */
    suspend fun execute(context: Context, input: String): CommandResult
}
