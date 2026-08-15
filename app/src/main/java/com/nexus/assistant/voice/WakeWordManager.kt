package com.nexus.assistant.voice

/**
 * PHASE 9: "lightweight and local" per spec. This implementation reuses
 * NexusSpeechRecognizer in a restart loop, listening for short utterances
 * and checking whether they contain "nexus" — rather than bundling a
 * dedicated always-on wake-word engine (e.g. Porcupine), which is a
 * separate native SDK with its own licensing to evaluate and isn't free for
 * commercial use at scale. This keeps the whole project free and avoids
 * adding an unverified native dependency, at the cost of being less
 * battery-efficient than a purpose-built wake-word model (each cycle is a
 * real speech-recognition pass, not a tiny always-on keyword spotter).
 *
 * Off by default (see VoiceSettings.wakeWordEnabled) given that tradeoff.
 */
class WakeWordManager(
    private val recognizer: NexusSpeechRecognizer,
    private val wakeWord: String = "nexus"
) {
    private var isRunning = false

    fun start(onWakeWordDetected: () -> Unit, onError: (String) -> Unit) {
        if (isRunning) return
        isRunning = true
        listenCycle(onWakeWordDetected, onError)
    }

    fun stop() {
        isRunning = false
        recognizer.stopListening()
    }

    private fun listenCycle(onWakeWordDetected: () -> Unit, onError: (String) -> Unit) {
        if (!isRunning) return
        recognizer.startListening(
            onResult = { text ->
                if (text.contains(wakeWord, ignoreCase = true)) {
                    onWakeWordDetected()
                    // Caller is expected to stop() the wake-word loop while
                    // handling the resulting command, then restart it.
                } else if (isRunning) {
                    listenCycle(onWakeWordDetected, onError) // keep listening
                }
            },
            onError = { _ ->
                // Timeouts/no-match are expected constantly in a listening
                // loop - just restart quietly rather than surfacing every
                // cycle as a user-facing error.
                if (isRunning) listenCycle(onWakeWordDetected, onError)
            }
        )
    }
}
