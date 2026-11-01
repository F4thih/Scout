package com.pocketcore.assistant.modules.utility

import android.content.Context
import com.pocketcore.assistant.core.AssistantModule
import com.pocketcore.assistant.core.CommandActionType
import com.pocketcore.assistant.core.CommandResult
import com.pocketcore.assistant.data.PocketCoreDatabase
import com.pocketcore.assistant.data.entity.NoteEntity
import java.util.Locale

class NoteModule : AssistantModule {
    override val id: String = "note"
    override val displayName: String = "Quick Notes"
    override val description: String = "Save offline notes instantly (e.g. 'Note Buy milk and eggs')"
    override val priority: Int = 90

    override fun canHandle(input: String): Boolean {
        val lower = input.trim().lowercase(Locale.ENGLISH)
        return lower.startsWith("note ") || lower.startsWith("take note ") || lower == "note"
    }

    override suspend fun execute(context: Context, input: String): CommandResult {
        val trimmed = input.trim()
        val noteContent = when {
            trimmed.startsWith("take note ", ignoreCase = true) -> trimmed.substring(10).trim()
            trimmed.startsWith("note ", ignoreCase = true) -> trimmed.substring(5).trim()
            else -> ""
        }

        if (noteContent.isEmpty()) {
            return CommandResult(
                success = false,
                feedbackMessage = "Please include text for your note. E.g., 'Note Buy milk'.",
                shouldDismissUi = false,
                actionType = CommandActionType.ERROR
            )
        }

        val db = PocketCoreDatabase.getInstance(context)
        db.noteDao().insertNote(
            NoteEntity.createEncrypted(noteContent)
        )

        return CommandResult(
            success = true,
            feedbackMessage = "Note saved securely: \"$noteContent\"",
            shouldDismissUi = false, // Keep UI open so user sees note was saved and can continue
            actionType = CommandActionType.NOTE_SAVED
        )
    }
}
