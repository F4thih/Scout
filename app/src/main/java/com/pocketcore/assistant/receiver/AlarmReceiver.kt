package com.pocketcore.assistant.receiver

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.pocketcore.assistant.data.PocketCoreDatabase
import com.pocketcore.assistant.data.entity.ReminderEntity
import com.pocketcore.assistant.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TRIGGER_REMINDER = "com.pocketcore.assistant.ACTION_TRIGGER_REMINDER"
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_TASK_DESCRIPTION = "extra_task_description"
        const val CHANNEL_ID = "scout_reminders"
        const val CHANNEL_NAME = "Scout Reminders"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val reminderId = intent?.getLongExtra(EXTRA_REMINDER_ID, -1L) ?: -1L
        val task = intent?.getStringExtra(EXTRA_TASK_DESCRIPTION) ?: "Reminder alert"

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Offline reminders and alarms triggered by Scout"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            reminderId.toInt(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Scout Reminder")
            .setContentText(task)
            .setStyle(NotificationCompat.BigTextStyle().bigText(task))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notifId = if (reminderId > 0) reminderId.toInt() else System.currentTimeMillis().toInt()
        notificationManager.notify(notifId, notification)

        // Reschedule if recurring or mark completed
        if (reminderId > 0) {
            CoroutineScope(Dispatchers.IO).launch {
                val db = PocketCoreDatabase.getInstance(context)
                val allReminders = db.reminderDao().getAllRemindersList()
                val current = allReminders.firstOrNull { it.id == reminderId }
                if (current != null) {
                    if (current.repeatInterval != "NONE") {
                        val nextTrigger = computeNextTrigger(current.triggerTimeMillis, current.repeatInterval)
                        val updated = current.copy(triggerTimeMillis = nextTrigger)
                        db.reminderDao().updateReminder(updated)

                        // Reschedule with AlarmManager
                        scheduleAlarm(context, updated.id, updated.displayDescription, nextTrigger)
                    } else {
                        val updated = current.copy(isCompleted = true)
                        db.reminderDao().updateReminder(updated)
                    }
                }
            }
        }
    }

    private fun computeNextTrigger(currentEpochMillis: Long, repeatInterval: String): Long {
        val zone = ZoneId.systemDefault()
        val currentDt = LocalDateTime.ofInstant(Instant.ofEpochMilli(currentEpochMillis), zone)
        val now = LocalDateTime.now(zone)

        var nextDt = currentDt
        when (repeatInterval.uppercase()) {
            "DAILY" -> {
                while (!nextDt.isAfter(now)) {
                    nextDt = nextDt.plusDays(1)
                }
            }
            "WEEKDAYS" -> {
                do {
                    nextDt = nextDt.plusDays(1)
                } while (!nextDt.isAfter(now) || nextDt.dayOfWeek == DayOfWeek.SATURDAY || nextDt.dayOfWeek == DayOfWeek.SUNDAY)
            }
            "WEEKLY" -> {
                while (!nextDt.isAfter(now)) {
                    nextDt = nextDt.plusWeeks(1)
                }
            }
            "MONTHLY" -> {
                while (!nextDt.isAfter(now)) {
                    nextDt = nextDt.plusMonths(1)
                }
            }
            else -> {
                nextDt = nextDt.plusDays(1)
            }
        }

        return nextDt.atZone(zone).toInstant().toEpochMilli()
    }

    private fun scheduleAlarm(context: Context, reminderId: Long, task: String, triggerMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_TRIGGER_REMINDER
            putExtra(EXTRA_REMINDER_ID, reminderId)
            putExtra(EXTRA_TASK_DESCRIPTION, task)
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
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            }
        } catch (e: Exception) {
            // Log/ignore on permission error
        }
    }
}
