package com.pocketcore.assistant.data.backup

import android.content.Context
import android.net.Uri
import com.pocketcore.assistant.data.PocketCoreDatabase
import com.pocketcore.assistant.data.entity.NoteEntity
import com.pocketcore.assistant.data.entity.ReminderEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * Air-gapped JSON Backup & Restore utility.
 * Exports and imports user Notes & Reminders using standard Android Storage Access Framework (SAF).
 * Completely local, zero internet, zero cloud dependencies.
 */
object BackupHelper {

    suspend fun exportDataToJson(context: Context): String = withContext(Dispatchers.IO) {
        val db = PocketCoreDatabase.getInstance(context)
        val notes = db.noteDao().getAllNotes().first()
        val reminders = db.reminderDao().getAllRemindersList()

        val rootJson = JSONObject()
        rootJson.put("version", 1)
        rootJson.put("appName", "Scout")
        rootJson.put("exportedAt", System.currentTimeMillis())

        // Export Notes (storing plaintext displayContent so it can be restored on any device or version)
        val notesArray = JSONArray()
        for (note in notes) {
            val noteObj = JSONObject()
            noteObj.put("content", note.displayContent)
            noteObj.put("timestamp", note.timestamp)
            notesArray.put(noteObj)
        }
        rootJson.put("notes", notesArray)

        // Export Reminders
        val remindersArray = JSONArray()
        for (rem in reminders) {
            val remObj = JSONObject()
            remObj.put("task", rem.displayDescription)
            remObj.put("triggerTimeMillis", rem.triggerTimeMillis)
            remObj.put("repeatInterval", rem.repeatInterval)
            remObj.put("isCompleted", rem.isCompleted)
            remindersArray.put(remObj)
        }
        rootJson.put("reminders", remindersArray)

        rootJson.toString(2)
    }

    suspend fun writeExportToUri(context: Context, uri: Uri, jsonString: String): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(jsonString)
                    writer.flush()
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun importDataFromUri(context: Context, uri: Uri): Result<Pair<Int, Int>> = withContext(Dispatchers.IO) {
        try {
            val stringBuilder = StringBuilder()
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                    var line: String? = reader.readLine()
                    while (line != null) {
                        stringBuilder.append(line)
                        line = reader.readLine()
                    }
                }
            }

            val rootJson = JSONObject(stringBuilder.toString())
            val db = PocketCoreDatabase.getInstance(context)

            var importedNotes = 0
            if (rootJson.has("notes")) {
                val notesArray = rootJson.getJSONArray("notes")
                for (i in 0 until notesArray.length()) {
                    val noteObj = notesArray.getJSONObject(i)
                    val content = noteObj.getString("content")
                    val timestamp = noteObj.optLong("timestamp", System.currentTimeMillis())
                    db.noteDao().insertNote(
                        NoteEntity.createEncrypted(content).copy(timestamp = timestamp)
                    )
                    importedNotes++
                }
            }

            var importedReminders = 0
            if (rootJson.has("reminders")) {
                val remindersArray = rootJson.getJSONArray("reminders")
                for (i in 0 until remindersArray.length()) {
                    val remObj = remindersArray.getJSONObject(i)
                    val task = remObj.getString("task")
                    val triggerTimeMillis = remObj.getLong("triggerTimeMillis")
                    val repeatInterval = remObj.optString("repeatInterval", "NONE")
                    val isCompleted = remObj.optBoolean("isCompleted", false)

                    db.reminderDao().insertReminder(
                        ReminderEntity.createEncrypted(
                            taskDescription = task,
                            triggerTimeMillis = triggerTimeMillis,
                            repeatInterval = repeatInterval,
                            isCompleted = isCompleted
                        )
                    )
                    importedReminders++
                }
            }

            Result.success(Pair(importedNotes, importedReminders))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
