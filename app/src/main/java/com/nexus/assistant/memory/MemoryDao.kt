package com.nexus.assistant.memory

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {

    @Query("SELECT * FROM memories ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories ORDER BY createdAtMillis DESC")
    suspend fun getAll(): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE content LIKE '%' || :query || '%' ORDER BY createdAtMillis DESC")
    suspend fun search(query: String): List<MemoryEntity>

    @Insert
    suspend fun insert(memory: MemoryEntity): Long

    @Update
    suspend fun update(memory: MemoryEntity)

    @Delete
    suspend fun delete(memory: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM memories")
    suspend fun deleteAll()
}
