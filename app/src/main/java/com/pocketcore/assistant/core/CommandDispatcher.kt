package com.pocketcore.assistant.core

import android.content.Context
import android.util.Log

/**
 * CommandDispatcher routes incoming offline text or speech-to-text queries
 * to the appropriate registered AssistantModule.
 */
class CommandDispatcher(
    private val registry: FeatureRegistry = FeatureRegistry
) {
    companion object {
        private const val TAG = "CommandDispatcher"
    }

    /**
     * Dispatches user input to the first active module capable of handling it.
     */
    suspend fun dispatch(context: Context, rawInput: String): CommandResult {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return CommandResult(
                success = false,
                feedbackMessage = "Please say or enter a command.",
                shouldDismissUi = false,
                actionType = CommandActionType.INFORMATIONAL
            )
        }

        val activeModules = registry.getActiveModules()
        for (module in activeModules) {
            try {
                if (module.canHandle(trimmed)) {
                    Log.d(TAG, "Routing '$trimmed' to module: ${module.id}")
                    return module.execute(context, trimmed)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing module: ${module.id}", e)
                return CommandResult(
                    success = false,
                    feedbackMessage = "Error executing ${module.displayName}: ${e.localizedMessage ?: "Unknown error"}",
                    shouldDismissUi = false,
                    actionType = CommandActionType.ERROR
                )
            }
        }

        // Fallback when no registered module matched the input
        return CommandResult(
            success = false,
            feedbackMessage = "No matching command found for \"$trimmed\". Try \"Remind me...\", \"Night\", \"Battery\", \"Note...\", or \"Open [App]\".",
            shouldDismissUi = false,
            actionType = CommandActionType.INFORMATIONAL
        )
    }
}
