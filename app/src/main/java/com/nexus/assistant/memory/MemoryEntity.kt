package com.nexus.assistant.memory

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single piece of long-term memory. Rows here are created ONLY in response
 * to an explicit user instruction ("remember that…"), never automatically
 * from ordinary conversation. This satisfies the requirement that NEXUS
 * "must not automatically store everything permanently."
 */
@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val source: String = "user_explicit" // reserved for future sources, always explicit for now
)
