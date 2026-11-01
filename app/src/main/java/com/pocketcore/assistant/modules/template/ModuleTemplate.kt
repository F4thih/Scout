package com.pocketcore.assistant.modules.template

import android.content.Context
import com.pocketcore.assistant.core.AssistantModule
import com.pocketcore.assistant.core.CommandActionType
import com.pocketcore.assistant.core.CommandResult
import com.pocketcore.assistant.core.FeatureRegistry
import java.util.Locale

/**
 * ============================================================================
 * POCKETCORE ASSISTANT: 5-MINUTE NEW MODULE TEMPLATE
 * ============================================================================
 * This template demonstrates how simple it is to add a new offline feature,
 * hardware action, custom tool, or offline math parser.
 *
 * HOW TO ADD YOUR MODULE:
 * 1. Duplicate or adapt this file into your feature package (e.g. `modules/calc/CalculatorModule.kt`).
 * 2. Implement [canHandle] with your keywords or regex pattern.
 * 3. Implement [execute] with your offline logic.
 * 4. Register your module in [PocketCoreApp.kt] with one line:
 *       `FeatureRegistry.registerModule(MyNewModule())`
 * 5. Done! The module is automatically supported in offline voice, text,
 *    and the Jetpack Compose Settings screen.
 * ============================================================================
 */
class ModuleTemplate : AssistantModule {

    /**
     * Unique identifier for module tracking and toggling in Settings.
     */
    override val id: String = "template_module"

    /**
     * User-facing display title.
     */
    override val displayName: String = "Template Custom Tool"

    /**
     * Description of what this module does.
     */
    override val description: String = "An example template demonstrating 5-minute offline feature expansion."

    /**
     * Routing priority. Higher numbers are checked first (e.g. 100 for exact commands).
     */
    override val priority: Int = 50

    /**
     * Check if the incoming user query (voice or text) belongs to this module.
     */
    override fun canHandle(input: String): Boolean {
        val lower = input.trim().lowercase(Locale.ENGLISH)
        return lower.startsWith("echo ") || lower == "ping"
    }

    /**
     * Execute offline logic and return user feedback.
     */
    override suspend fun execute(context: Context, input: String): CommandResult {
        val trimmed = input.trim()

        if (trimmed.equals("ping", ignoreCase = true)) {
            return CommandResult(
                success = true,
                feedbackMessage = "Pong! PocketCore local engine is healthy.",
                shouldDismissUi = false,
                actionType = CommandActionType.INFORMATIONAL
            )
        }

        val echoContent = if (trimmed.startsWith("echo ", ignoreCase = true)) {
            trimmed.substring(5).trim()
        } else trimmed

        return CommandResult(
            success = true,
            feedbackMessage = "Echo: $echoContent",
            shouldDismissUi = false,
            actionType = CommandActionType.INFORMATIONAL
        )
    }
}
