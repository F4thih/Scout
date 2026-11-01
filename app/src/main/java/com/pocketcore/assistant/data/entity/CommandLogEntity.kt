package com.pocketcore.assistant.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores execution logs for offline audit and command history.
 */
@Entity(tableName = "command_logs")
data class CommandLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val rawInput: String,
    val feedbackMessage: String,
    val success: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
