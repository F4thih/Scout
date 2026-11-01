package com.pocketcore.assistant.modules.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.pocketcore.assistant.core.AssistantModule
import com.pocketcore.assistant.core.CommandActionType
import com.pocketcore.assistant.core.CommandResult
import com.pocketcore.assistant.data.PocketCoreDatabase
import com.pocketcore.assistant.data.entity.ReminderEntity
import com.pocketcore.assistant.receiver.AlarmReceiver
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.regex.Pattern

class ReminderModule : AssistantModule {
    override val id: String = "reminder"
    override val displayName: String = "Offline Reminders"
    override val description: String = "Schedule, query, list, or delete reminders via natural speech or text"
    override val priority: Int = 100

    private val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH)
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("MMM dd, hh:mm a", Locale.ENGLISH)

    override fun canHandle(input: String): Boolean {
        val lower = input.trim().lowercase(Locale.ENGLISH)
        return lower.startsWith("remind") ||
                lower.startsWith("what are my reminders") ||
                lower.startsWith("show reminders") ||
                lower.startsWith("list reminders") ||
                lower.startsWith("any reminders") ||
                lower.startsWith("check reminders") ||
                lower.startsWith("check reminder") ||
                lower.startsWith("do i have any reminder") ||
                lower.startsWith("do i have a reminder") ||
                lower.startsWith("delete reminder") ||
                lower.startsWith("remove reminder") ||
                lower.startsWith("cancel reminder") ||
                lower == "reminders" ||
                lower == "my reminders"
    }

    override suspend fun execute(context: Context, input: String): CommandResult {
        val trimmed = input.trim()
        val lower = trimmed.lowercase(Locale.ENGLISH)

        // 1. DELETE / REMOVE / CANCEL REMINDER
        if (lower.startsWith("delete reminder") || lower.startsWith("remove reminder") || lower.startsWith("cancel reminder")) {
            return handleDeleteReminder(context, trimmed)
        }

        // 2. CHECK SPECIFIC TIME / CHECK DAY / LIST REMINDERS
        if (isQueryIntent(lower)) {
            return handleQueryReminders(context, trimmed)
        }

        // 3. SCHEDULE NEW REMINDER (Default creation path)
        return handleCreateReminder(context, trimmed)
    }

    private fun isQueryIntent(lower: String): Boolean {
        return lower.startsWith("what are my reminders") ||
                lower.startsWith("show reminders") ||
                lower.startsWith("list reminders") ||
                lower.startsWith("any reminders") ||
                lower.startsWith("check reminder") ||
                lower.startsWith("do i have") ||
                lower == "reminders" ||
                lower == "my reminders" ||
                lower.startsWith("reminders for ") ||
                lower.startsWith("reminders today") ||
                lower.startsWith("reminders tomorrow")
    }

    private suspend fun handleCreateReminder(context: Context, input: String): CommandResult {
        val parsed = ReminderParser.parse(input)
            ?: return CommandResult(
                success = false,
                feedbackMessage = "Couldn't understand the reminder format. Try: 'Remind me to [task] at [time]' or 'in [X] minutes'.",
                shouldDismissUi = false,
                actionType = CommandActionType.ERROR
            )

        val triggerEpochMillis = parsed.triggerDateTime
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        if (triggerEpochMillis <= System.currentTimeMillis()) {
            return CommandResult(
                success = false,
                feedbackMessage = "The specified time is already in the past. Please choose a future time.",
                shouldDismissUi = false,
                actionType = CommandActionType.ERROR
            )
        }

        // Save into local Room database
        val db = PocketCoreDatabase.getInstance(context)
        val reminderId = db.reminderDao().insertReminder(
            ReminderEntity(
                taskDescription = parsed.task,
                triggerTimeMillis = triggerEpochMillis
            )
        )

        // Schedule exact alarm with AlarmManager
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TRIGGER_REMINDER
            putExtra(AlarmReceiver.EXTRA_REMINDER_ID, reminderId)
            putExtra(AlarmReceiver.EXTRA_TASK_DESCRIPTION, parsed.task)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerEpochMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerEpochMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerEpochMillis,
                    pendingIntent
                )
            }

            val formattedTime = parsed.triggerDateTime.format(dateTimeFormatter)
            return CommandResult(
                success = true,
                feedbackMessage = "Reminder scheduled: \"${parsed.task}\" for $formattedTime",
                shouldDismissUi = true,
                actionType = CommandActionType.REMINDER_SCHEDULED
            )
        } catch (e: SecurityException) {
            return CommandResult(
                success = false,
                feedbackMessage = "Permission required: Exact alarms permission is needed to set precise alarms.",
                shouldDismissUi = false,
                actionType = CommandActionType.ERROR
            )
        } catch (e: Exception) {
            return CommandResult(
                success = false,
                feedbackMessage = "Failed to set alarm: ${e.message}",
                shouldDismissUi = false,
                actionType = CommandActionType.ERROR
            )
        }
    }

    private suspend fun handleQueryReminders(context: Context, input: String): CommandResult {
        val lower = input.lowercase(Locale.ENGLISH)
        val db = PocketCoreDatabase.getInstance(context)
        val now = LocalDateTime.now()
        val zone = ZoneId.systemDefault()

        // Case A: Query for a specific time: e.g. "is there any reminder at 9pm?" or "check reminder at 5:00"
        val timeRegex = Pattern.compile("(?:at\\s+)(\\d{1,2}(?::\\d{2})?\\s*(?:am|pm)?)\\b", Pattern.CASE_INSENSITIVE)
        val timeMatcher = timeRegex.matcher(input)
        if (timeMatcher.find()) {
            val rawTime = timeMatcher.group(1) ?: ""
            val parsedTime = parseTimeStr(rawTime)
            if (parsedTime != null) {
                val targetToday = LocalDateTime.of(now.toLocalDate(), parsedTime)
                val windowStart = targetToday.minusMinutes(15).atZone(zone).toInstant().toEpochMilli()
                val windowEnd = targetToday.plusMinutes(15).atZone(zone).toInstant().toEpochMilli()

                val matches = db.reminderDao().getRemindersBetween(windowStart, windowEnd)
                return if (matches.isNotEmpty()) {
                    val desc = matches.joinToString(", ") { "\"${it.taskDescription}\" at ${formatEpoch(it.triggerTimeMillis)}" }
                    CommandResult(
                        success = true,
                        feedbackMessage = "Yes, you have: $desc",
                        shouldDismissUi = false,
                        actionType = CommandActionType.INFORMATIONAL
                    )
                } else {
                    CommandResult(
                        success = true,
                        feedbackMessage = "You have no reminders scheduled around $rawTime.",
                        shouldDismissUi = false,
                        actionType = CommandActionType.INFORMATIONAL
                    )
                }
            }
        }

        // Case B: Query for "today"
        if (lower.contains("today")) {
            val startOfDay = now.toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
            val endOfDay = now.toLocalDate().atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli()

            val remindersToday = db.reminderDao().getRemindersBetween(startOfDay, endOfDay)
            return if (remindersToday.isNotEmpty()) {
                val formatted = remindersToday.mapIndexed { idx, it ->
                    "${idx + 1}. \"${it.taskDescription}\" at ${formatEpoch(it.triggerTimeMillis)}"
                }.joinToString("\n")
                CommandResult(
                    success = true,
                    feedbackMessage = "Here are your reminders for today:\n$formatted",
                    shouldDismissUi = false,
                    actionType = CommandActionType.INFORMATIONAL
                )
            } else {
                CommandResult(
                    success = true,
                    feedbackMessage = "You have no reminders scheduled for today.",
                    shouldDismissUi = false,
                    actionType = CommandActionType.INFORMATIONAL
                )
            }
        }

        // Case C: Query for "tomorrow"
        if (lower.contains("tomorrow") || lower.contains("tmrw")) {
            val tmrw = now.toLocalDate().plusDays(1)
            val startOfDay = tmrw.atStartOfDay(zone).toInstant().toEpochMilli()
            val endOfDay = tmrw.atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli()

            val remindersTmrw = db.reminderDao().getRemindersBetween(startOfDay, endOfDay)
            return if (remindersTmrw.isNotEmpty()) {
                val formatted = remindersTmrw.mapIndexed { idx, it ->
                    "${idx + 1}. \"${it.taskDescription}\" at ${formatEpoch(it.triggerTimeMillis)}"
                }.joinToString("\n")
                CommandResult(
                    success = true,
                    feedbackMessage = "Here are your reminders for tomorrow:\n$formatted",
                    shouldDismissUi = false,
                    actionType = CommandActionType.INFORMATIONAL
                )
            } else {
                CommandResult(
                    success = true,
                    feedbackMessage = "You have no reminders scheduled for tomorrow.",
                    shouldDismissUi = false,
                    actionType = CommandActionType.INFORMATIONAL
                )
            }
        }

        // Case D: General query / List all active upcoming reminders
        val allReminders = db.reminderDao().getAllRemindersList()
            .filter { it.triggerTimeMillis >= System.currentTimeMillis() }

        if (allReminders.isEmpty()) {
            return CommandResult(
                success = true,
                feedbackMessage = "You have no upcoming reminders scheduled.",
                shouldDismissUi = false,
                actionType = CommandActionType.INFORMATIONAL
            )
        }

        val listStr = allReminders.take(5).mapIndexed { idx, it ->
            "${idx + 1}. \"${it.taskDescription}\" on ${formatEpochFull(it.triggerTimeMillis)}"
        }.joinToString("\n")

        val countInfo = if (allReminders.size > 5) "\n(and ${allReminders.size - 5} more)" else ""

        return CommandResult(
            success = true,
            feedbackMessage = "You have ${allReminders.size} upcoming reminder(s):\n$listStr$countInfo",
            shouldDismissUi = false,
            actionType = CommandActionType.INFORMATIONAL
        )
    }

    private suspend fun handleDeleteReminder(context: Context, input: String): CommandResult {
        // e.g. "delete reminder buy groceries", "remove reminder Mom", or "delete reminder 1"
        val query = input.replaceFirst(Regex("^(?:delete|remove|cancel)\\s+reminder\\s*", RegexOption.IGNORE_CASE), "").trim()
        val db = PocketCoreDatabase.getInstance(context)
        val all = db.reminderDao().getAllRemindersList()

        if (all.isEmpty()) {
            return CommandResult(
                success = false,
                feedbackMessage = "There are no reminders to remove.",
                shouldDismissUi = false,
                actionType = CommandActionType.INFORMATIONAL
            )
        }

        // Match by index number (e.g. "delete reminder 1")
        val index = query.toIntOrNull()
        val targetReminder = if (index != null && index in 1..all.size) {
            all[index - 1]
        } else if (query.isNotBlank()) {
            // Match by task description substring
            all.firstOrNull { it.taskDescription.contains(query, ignoreCase = true) }
        } else {
            // Default to the next upcoming reminder
            all.firstOrNull()
        }

        if (targetReminder == null) {
            return CommandResult(
                success = false,
                feedbackMessage = "Could not find a reminder matching \"$query\".",
                shouldDismissUi = false,
                actionType = CommandActionType.ERROR
            )
        }

        // Cancel AlarmManager intent
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TRIGGER_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            targetReminder.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)

        // Delete from SQLite database
        db.reminderDao().deleteReminder(targetReminder)

        return CommandResult(
            success = true,
            feedbackMessage = "Removed reminder: \"${targetReminder.taskDescription}\".",
            shouldDismissUi = true,
            actionType = CommandActionType.INFORMATIONAL
        )
    }

    private fun formatEpoch(millis: Long): String {
        return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), ZoneId.systemDefault())
            .format(timeFormatter)
    }

    private fun formatEpochFull(millis: Long): String {
        return LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), ZoneId.systemDefault())
            .format(dateTimeFormatter)
    }

    private fun parseTimeStr(raw: String): LocalTime? {
        val cleaned = raw.trim().lowercase(Locale.ENGLISH).replace(" ", "")
        val amPmPattern = Pattern.compile("(\\d{1,2})(?::(\\d{2}))?(am|pm)")
        val matcher = amPmPattern.matcher(cleaned)
        if (matcher.matches()) {
            var hour = matcher.group(1)?.toIntOrNull() ?: return null
            val minute = matcher.group(2)?.toIntOrNull() ?: 0
            val meridian = matcher.group(3)
            if (meridian == "pm" && hour < 12) hour += 12
            else if (meridian == "am" && hour == 12) hour = 0
            return if (hour in 0..23 && minute in 0..59) LocalTime.of(hour, minute) else null
        }
        val colonPattern = Pattern.compile("(\\d{1,2}):(\\d{2})")
        val colonMatcher = colonPattern.matcher(cleaned)
        if (colonMatcher.matches()) {
            val hour = colonMatcher.group(1)?.toIntOrNull() ?: return null
            val minute = colonMatcher.group(2)?.toIntOrNull() ?: return null
            return if (hour in 0..23 && minute in 0..59) LocalTime.of(hour, minute) else null
        }
        return null
    }
}
