package com.pocketcore.assistant.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.pocketcore.assistant.data.security.SecurityHelper

/**
 * Stores scheduled reminder metadata locally with hardware-backed encryption.
 * [repeatInterval] can be:
 * - "NONE" (one-time reminder)
 * - "DAILY" (every day at same time)
 * - "WEEKLY" (every week on this day)
 * - "WEEKDAYS" (Monday to Friday)
 * - "MONTHLY" (every month on this date)
 */
@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val taskDescription: String,
    val triggerTimeMillis: Long,
    val isCompleted: Boolean = false,
    val repeatInterval: String = "NONE",
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * Transparently decrypts the task description for display.
     */
    val displayDescription: String
        get() = SecurityHelper.decrypt(taskDescription)

    companion object {
        fun createEncrypted(
            taskDescription: String,
            triggerTimeMillis: Long,
            repeatInterval: String = "NONE",
            isCompleted: Boolean = false
        ): ReminderEntity {
            return ReminderEntity(
                taskDescription = SecurityHelper.encrypt(taskDescription),
                triggerTimeMillis = triggerTimeMillis,
                repeatInterval = repeatInterval,
                isCompleted = isCompleted
            )
        }
    }
}
