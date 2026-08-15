package com.nexus.assistant.memory

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MemoryEntity::class, ConversationEntity::class],
    version = 1,
    exportSchema = false
)
abstract class MemoryDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
    abstract fun conversationDao(): ConversationDao

    companion object {
        @Volatile private var INSTANCE: MemoryDatabase? = null

        /**
         * This is the ONLY local database NEXUS uses for memory. There is no
         * remote/cloud counterpart, no sync adapter, no backup extraction
         * (see android:allowBackup="false" in the manifest) — the file lives
         * at the standard Room path under the app's private storage and is
         * deleted entirely by "Delete all NEXUS data" in Settings.
         */
        fun getInstance(context: Context): MemoryDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MemoryDatabase::class.java,
                    "nexus_memory.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
