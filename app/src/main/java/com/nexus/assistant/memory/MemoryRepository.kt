package com.nexus.assistant.memory

import kotlinx.coroutines.flow.Flow

sealed class MemoryCommandResult {
    data class Remembered(val confirmation: String) : MemoryCommandResult()
    data class Forgotten(val confirmation: String) : MemoryCommandResult()
    data class Recalled(val text: String) : MemoryCommandResult()
    object NotAMemoryCommand : MemoryCommandResult()
}

/**
 * Sits between the AI/chat layer and the Room DAOs. This is deliberately
 * simple pattern matching, not a general intent classifier — real intent
 * routing for tools arrives in Phase 5 (ToolRouter). This exists now because
 * "remember that I'm working on a Java project" -> "I'll remember that." is
 * a named example in the spec and shouldn't depend on Phase 5 being done.
 */
class MemoryRepository(private val db: MemoryDatabase) {

    private val memoryDao = db.memoryDao()
    private val conversationDao = db.conversationDao()

    fun observeMemories(): Flow<List<MemoryEntity>> = memoryDao.observeAll()

    suspend fun getAllMemories(): List<MemoryEntity> = memoryDao.getAll()

    suspend fun searchMemories(query: String): List<MemoryEntity> = memoryDao.search(query)

    suspend fun addMemory(content: String): MemoryEntity {
        val entity = MemoryEntity(content = content)
        val id = memoryDao.insert(entity)
        return entity.copy(id = id)
    }

    suspend fun updateMemory(memory: MemoryEntity) {
        memoryDao.update(memory.copy(updatedAtMillis = System.currentTimeMillis()))
    }

    suspend fun deleteMemory(memory: MemoryEntity) = memoryDao.delete(memory)

    suspend fun deleteMemoryById(id: Long) = memoryDao.deleteById(id)

    suspend fun deleteAllMemories() = memoryDao.deleteAll()

    suspend fun logConversationTurn(sender: String, text: String, settings: MemorySettings) {
        if (!settings.persistConversations) return
        conversationDao.insert(ConversationEntity(sender = sender, text = text))
        // Enforce the configured cap so this table can't grow without bound —
        // "not everything permanently" applies to conversation logging too.
        conversationDao.trimToMostRecent(settings.maxConversationHistory)
    }

    suspend fun clearConversationHistory() = conversationDao.clearAll()

    suspend fun deleteEverything() {
        memoryDao.deleteAll()
        conversationDao.clearAll()
    }

    private val rememberPatterns = listOf(
        Regex("""^remember that (.+)$""", RegexOption.IGNORE_CASE),
        Regex("""^remember (.+)$""", RegexOption.IGNORE_CASE),
        Regex("""^please remember (.+)$""", RegexOption.IGNORE_CASE),
        Regex("""^don'?t forget (.+)$""", RegexOption.IGNORE_CASE)
    )

    private val forgetPatterns = listOf(
        Regex("""^forget that (.+)$""", RegexOption.IGNORE_CASE),
        Regex("""^forget (.+)$""", RegexOption.IGNORE_CASE)
    )

    private val recallPatterns = listOf(
        Regex("""^what do you remember about (.+)$""", RegexOption.IGNORE_CASE),
        Regex("""^what do you remember\??$""", RegexOption.IGNORE_CASE)
    )

    /**
     * Checks a raw user message against explicit memory commands. Returns
     * NotAMemoryCommand for everything else, so the caller falls through to
     * the normal AI conversation path.
     */
    suspend fun tryHandleCommand(rawText: String): MemoryCommandResult {
        val text = rawText.trim()

        for (pattern in rememberPatterns) {
            val match = pattern.find(text) ?: continue
            val content = match.groupValues[1].trim().trimEnd('.', '!')
            if (content.isEmpty()) continue
            addMemory(content)
            return MemoryCommandResult.Remembered("I'll remember that.")
        }

        for (pattern in forgetPatterns) {
            val match = pattern.find(text) ?: continue
            val target = match.groupValues[1].trim().trimEnd('.', '!')
            if (target.isEmpty()) continue
            val matches = searchMemories(target)
            if (matches.isEmpty()) {
                return MemoryCommandResult.Forgotten("I didn't have anything stored matching \"$target\".")
            }
            matches.forEach { deleteMemory(it) }
            return MemoryCommandResult.Forgotten("Done — I've deleted ${matches.size} matching memor${if (matches.size == 1) "y" else "ies"}.")
        }

        for (pattern in recallPatterns) {
            val match = pattern.find(text) ?: continue
            val topic = match.groupValues.getOrNull(1)?.trim()?.trimEnd('.', '!')
            val results = if (topic.isNullOrEmpty()) getAllMemories() else searchMemories(topic)
            val reply = if (results.isEmpty()) {
                if (topic.isNullOrEmpty()) "I don't have anything stored in memory yet."
                else "I don't have anything stored about \"$topic\"."
            } else {
                results.joinToString("\n") { "• ${it.content}" }
            }
            return MemoryCommandResult.Recalled(reply)
        }

        return MemoryCommandResult.NotAMemoryCommand
    }
}
