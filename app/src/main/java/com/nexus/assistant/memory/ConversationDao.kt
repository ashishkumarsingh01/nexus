package com.nexus.assistant.memory

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ConversationDao {

    @Insert
    suspend fun insert(message: ConversationEntity): Long

    @Query("SELECT * FROM conversation_history ORDER BY timestampMillis DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<ConversationEntity>

    @Query("DELETE FROM conversation_history")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM conversation_history")
    suspend fun count(): Int

    @Query("""
        DELETE FROM conversation_history
        WHERE id NOT IN (
            SELECT id FROM conversation_history ORDER BY timestampMillis DESC LIMIT :keep
        )
    """)
    suspend fun trimToMostRecent(keep: Int)
}
