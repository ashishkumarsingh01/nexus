package com.nexus.assistant.online

import android.content.Context

/**
 * Master switch for everything network-related beyond the offline core.
 * OFF by default - matches the spec's "offline-first" requirement and
 * "user must be able to disable internet access for NEXUS."
 */
class OnlineSettings(context: Context) {
    private val prefs = context.getSharedPreferences("nexus_online_settings", Context.MODE_PRIVATE)

    var onlineModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_ONLINE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONLINE_ENABLED, value).apply()

    companion object {
        private const val KEY_ONLINE_ENABLED = "online_mode_enabled"
    }
}
