package com.pocketcore.assistant.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.pocketcore.assistant.data.security.SecurityHelper

/**
 * Stores local encrypted note items.
 * The content is encrypted using AES-256-GCM backed by Android KeyStore.
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Transparently decrypts the note content for UI rendering.
     */
    val displayContent: String
        get() = SecurityHelper.decrypt(content)

    companion object {
        /**
         * Creates a new NoteEntity with its content encrypted.
         */
        fun createEncrypted(content: String): NoteEntity {
            return NoteEntity(content = SecurityHelper.encrypt(content))
        }
    }
}
