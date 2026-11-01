package com.pocketcore.assistant.modules.utility

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.pocketcore.assistant.core.AssistantModule
import com.pocketcore.assistant.core.CommandActionType
import com.pocketcore.assistant.core.CommandResult
import java.util.Locale

class AppLauncherModule : AssistantModule {
    override val id: String = "app_launcher"
    override val displayName: String = "App Launcher"
    override val description: String = "Launch installed apps via offline voice or text (e.g. 'Open Camera', 'Launch Calculator')"
    override val priority: Int = 85

    override fun canHandle(input: String): Boolean {
        val lower = input.trim().lowercase(Locale.ENGLISH)
        return lower.startsWith("open ") || lower.startsWith("launch ")
    }

    override suspend fun execute(context: Context, input: String): CommandResult {
        val trimmed = input.trim()
        val targetAppName = when {
            trimmed.startsWith("open ", ignoreCase = true) -> trimmed.substring(5).trim()
            trimmed.startsWith("launch ", ignoreCase = true) -> trimmed.substring(7).trim()
            else -> ""
        }

        if (targetAppName.isEmpty()) {
            return CommandResult(
                success = false,
                feedbackMessage = "Please specify an app to open (e.g., 'Open Settings' or 'Open Camera').",
                shouldDismissUi = false,
                actionType = CommandActionType.ERROR
            )
        }

        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val installedApps = pm.queryIntentActivities(mainIntent, 0)
        val normalizedTarget = targetAppName.lowercase(Locale.ENGLISH)

        // Exact label match first, then partial match
        val matchedApp = installedApps.firstOrNull {
            val label = it.loadLabel(pm).toString().lowercase(Locale.ENGLISH)
            label == normalizedTarget
        } ?: installedApps.firstOrNull {
            val label = it.loadLabel(pm).toString().lowercase(Locale.ENGLISH)
            label.contains(normalizedTarget) || normalizedTarget.contains(label)
        }

        if (matchedApp != null) {
            val launchIntent = pm.getLaunchIntentForPackage(matchedApp.activityInfo.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return CommandResult(
                    success = true,
                    feedbackMessage = "Opening ${matchedApp.loadLabel(pm)}...",
                    shouldDismissUi = true,
                    actionType = CommandActionType.APP_LAUNCHED
                )
            }
        }

        return CommandResult(
            success = false,
            feedbackMessage = "Could not find an installed app matching \"$targetAppName\".",
            shouldDismissUi = false,
            actionType = CommandActionType.ERROR
        )
    }
}
