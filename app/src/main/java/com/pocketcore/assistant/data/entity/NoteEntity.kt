package com.pocketcore.assistant.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores local offline notes.
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
