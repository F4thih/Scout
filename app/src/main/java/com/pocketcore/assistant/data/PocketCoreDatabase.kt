package com.pocketcore.assistant.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.pocketcore.assistant.data.dao.CommandLogDao
import com.pocketcore.assistant.data.dao.NoteDao
import com.pocketcore.assistant.data.dao.ReminderDao
import com.pocketcore.assistant.data.entity.CommandLogEntity
import com.pocketcore.assistant.data.entity.NoteEntity
import com.pocketcore.assistant.data.entity.ReminderEntity

@Database(
    entities = [
        NoteEntity::class,
        ReminderEntity::class,
        CommandLogEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class PocketCoreDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun reminderDao(): ReminderDao
    abstract fun commandLogDao(): CommandLogDao

    companion object {
        @Volatile
        private var INSTANCE: PocketCoreDatabase? = null

        fun getInstance(context: Context): PocketCoreDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PocketCoreDatabase::class.java,
                    "pocket_core.db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
