package com.nexus.assistant.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Uses Android's built-in TextToSpeech engine, which runs fully on-device
 * once its voice data is installed (standard on virtually all Android
 * phones via Settings -> Text-to-speech). No network call, no third-party
 * service, no cost.
 */
class TextToSpeechManager(context: Context) {

    private var tts: TextToSpeech? = null
    private var isReady = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            isReady = status == TextToSpeech.SUCCESS
            if (isReady) {
                tts?.language = Locale.getDefault()
            }
        }
    }

    fun speak(text: String) {
        if (!isReady) return // fail silently rather than crash; voice output is a nice-to-have, not core
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nexus_utterance")
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
