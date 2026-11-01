package com.pocketcore.assistant.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores scheduled reminder metadata locally.
 */
@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val taskDescription: String,
    val triggerTimeMillis: Long,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
