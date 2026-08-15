package com.nexus.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Wraps Android's built-in SpeechRecognizer rather than bundling a separate
 * offline STT engine. EXTRA_PREFER_OFFLINE requests on-device recognition
 * where the device/OS supports it (most modern Android phones with Google
 * Speech Services do); this is the pragmatic free/offline-capable choice
 * documented back in Phase 8 planning, not a guarantee every device has a
 * fully offline recognizer available.
 */
class NexusSpeechRecognizer(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening(onResult: (String) -> Unit, onError: (String) -> Unit, onListeningStateChanged: (Boolean) -> Unit = {}) {
        if (!isAvailable()) {
            onError("Speech recognition isn't available on this device.")
            return
        }

        stopListening() // ensure no duplicate session

        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { onListeningStateChanged(true) }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { onListeningStateChanged(false) }

                override fun onError(error: Int) {
                    onListeningStateChanged(false)
                    onError(describeError(error))
                }

                override fun onResults(results: Bundle) {
                    onListeningStateChanged(false)
                    val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()
                    if (text.isNullOrBlank()) onError("Didn't catch that.") else onResult(text)
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        recognizer?.startListening(intent)
    }

    fun stopListening() {
        recognizer?.stopListening()
        recognizer?.destroy()
        recognizer = null
    }

    private fun describeError(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that — try again."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Speech recognition needs network access on this device and none is available right now."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
        SpeechRecognizer.ERROR_AUDIO -> "Microphone error."
        else -> "Speech recognition failed (code $error)."
    }
}
