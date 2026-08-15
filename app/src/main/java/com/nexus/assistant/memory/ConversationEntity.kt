package com.nexus.assistant.memory

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single conversational turn. Distinct from MemoryEntity: this is
 * ordinary chat history, not a fact the user asked NEXUS to remember.
 * Persisting this table at all is opt-in (see MemorySettings.persistConversations) —
 * by default conversation history lives only in the ViewModel's in-memory
 * state for the current app session and is gone when the app is killed.
 */
@Entity(tableName = "conversation_history")
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String, // "USER" or "NEXUS"
    val text: String,
    val timestampMillis: Long = System.currentTimeMillis()
)
