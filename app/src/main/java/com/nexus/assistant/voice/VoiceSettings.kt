package com.nexus.assistant.voice

import android.content.Context

class VoiceSettings(context: Context) {
    private val prefs = context.getSharedPreferences("nexus_voice_settings", Context.MODE_PRIVATE)

    /** Tap-to-talk mic. On by default - each use still requires an explicit tap. */
    var microphoneEnabled: Boolean
        get() = prefs.getBoolean(KEY_MIC_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_MIC_ENABLED, value).apply()

    /** Continuous always-listening wake word. OFF by default - this is a
     *  meaningfully bigger privacy/battery commitment than tap-to-talk, so
     *  it's opt-in rather than opt-out. */
    var wakeWordEnabled: Boolean
        get() = prefs.getBoolean(KEY_WAKE_WORD_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_WAKE_WORD_ENABLED, value).apply()

    var ttsEnabled: Boolean
        get() = prefs.getBoolean(KEY_TTS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_TTS_ENABLED, value).apply()

    companion object {
        private const val KEY_MIC_ENABLED = "microphone_enabled"
        private const val KEY_WAKE_WORD_ENABLED = "wake_word_enabled"
        private const val KEY_TTS_ENABLED = "tts_enabled"
    }
}
