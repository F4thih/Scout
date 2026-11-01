package com.pocketcore.assistant.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.pocketcore.assistant.data.security.SecurityHelper

/**
 * Stores encrypted logs of previous command inputs and feedback messages.
 */
@Entity(tableName = "command_logs")
data class CommandLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val rawInput: String,
    val feedbackMessage: String,
    val success: Boolean,
    val timestamp: Long = System.currentTimeMillis()
) {
    val displayInput: String
        get() = SecurityHelper.decrypt(rawInput)

    val displayFeedback: String
        get() = SecurityHelper.decrypt(feedbackMessage)

    companion object {
        fun createEncrypted(
            rawInput: String,
            feedbackMessage: String,
            success: Boolean
        ): CommandLogEntity {
            return CommandLogEntity(
                rawInput = SecurityHelper.encrypt(rawInput),
                feedbackMessage = SecurityHelper.encrypt(feedbackMessage),
                success = success
            )
        }
    }
}
