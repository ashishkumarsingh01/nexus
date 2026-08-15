package com.nexus.assistant.memory

import android.content.Context

/**
 * Everything here defaults to the more private option. The user has to
 * actively opt in to persistent conversation logging; long-term "remember
 * that…" memories are unaffected by this setting since those are always
 * explicit per-item, one at a time.
 */
class MemorySettings(context: Context) {
    private val prefs = context.getSharedPreferences("nexus_memory_settings", Context.MODE_PRIVATE)

    var persistConversations: Boolean
        get() = prefs.getBoolean(KEY_PERSIST_CONVERSATIONS, false)
        set(value) = prefs.edit().putBoolean(KEY_PERSIST_CONVERSATIONS, value).apply()

    var maxConversationHistory: Int
        get() = prefs.getInt(KEY_MAX_HISTORY, 200)
        set(value) = prefs.edit().putInt(KEY_MAX_HISTORY, value).apply()

    companion object {
        private const val KEY_PERSIST_CONVERSATIONS = "persist_conversations"
        private const val KEY_MAX_HISTORY = "max_conversation_history"
    }
}
