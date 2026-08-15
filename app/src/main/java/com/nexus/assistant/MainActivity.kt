package com.nexus.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexus.assistant.core.NetworkStatus
import com.nexus.assistant.ui.FileAccessScreen
import com.nexus.assistant.ui.MainScreen
import com.nexus.assistant.ui.MemoryScreen
import com.nexus.assistant.ui.NexusViewModelFactory
import com.nexus.assistant.ui.NexusViewModel
import com.nexus.assistant.ui.PermissionScreen
import com.nexus.assistant.ui.SettingsScreen
import com.nexus.assistant.ui.theme.NexusTheme
import com.nexus.assistant.voice.NexusSpeechRecognizer
import com.nexus.assistant.voice.TextToSpeechManager

private enum class Screen { CHAT, SETTINGS, MEMORY, FILE_ACCESS, PERMISSIONS }

class MainActivity : ComponentActivity() {

    private lateinit var speechRecognizer: NexusSpeechRecognizer
    private lateinit var ttsManager: TextToSpeechManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as NexusApplication
        speechRecognizer = NexusSpeechRecognizer(this)
        ttsManager = TextToSpeechManager(this)

        setContent {
            NexusTheme {
                var screen by remember { mutableStateOf(Screen.CHAT) }
                var isListening by remember { mutableStateOf(false) }
                var micPermissionDenied by remember { mutableStateOf(false) }

                val viewModel = viewModel<NexusViewModel>(
                    factory = NexusViewModelFactory(
                        app.aiEngine, app.modelManager, app.memoryRepository,
                        app.memorySettings, app.toolRouter, app.agentPlanner
                    )
                )

                // Reflect real connectivity, not a guess.
                LaunchedEffect(Unit) {
                    viewModel.setNetworkStatus(currentNetworkStatus())
                }

                // Speak NEXUS's replies aloud if TTS is enabled - the last
                // NEXUS message is watched and spoken once, not re-spoken
                // on every recomposition.
                var lastSpokenId by remember { mutableStateOf<Long?>(null) }
                LaunchedEffect(viewModel.messages.size) {
                    if (!app.voiceSettings.ttsEnabled) return@LaunchedEffect
                    val last = viewModel.messages.lastOrNull()
                    if (last != null && last.sender == com.nexus.assistant.ui.Sender.NEXUS && last.id != lastSpokenId) {
                        lastSpokenId = last.id
                        ttsManager.speak(last.text)
                    }
                }

                val micPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) {
                        isListening = true
                        speechRecognizer.startListening(
                            onResult = { text -> isListening = false; viewModel.onVoiceResult(text) },
                            onError = { isListening = false },
                            onListeningStateChanged = { listening -> isListening = listening }
                        )
                    } else {
                        micPermissionDenied = true
                    }
                }

                fun onMicClick() {
                    if (!app.voiceSettings.microphoneEnabled) return
                    val hasPermission = ContextCompat.checkSelfPermission(
                        this@MainActivity, Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED

                    if (hasPermission) {
                        isListening = true
                        speechRecognizer.startListening(
                            onResult = { text -> isListening = false; viewModel.onVoiceResult(text) },
                            onError = { isListening = false },
                            onListeningStateChanged = { listening -> isListening = listening }
                        )
                    } else {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }

                when (screen) {
                    Screen.CHAT -> MainScreen(
                        viewModel = viewModel,
                        onOpenSettings = { screen = Screen.SETTINGS },
                        onMicClick = { onMicClick() },
                        isListening = isListening
                    )
                    Screen.SETTINGS -> SettingsScreen(
                        modelManager = app.modelManager,
                        onBack = { screen = Screen.CHAT },
                        onOpenMemory = { screen = Screen.MEMORY },
                        onOpenFileAccess = { screen = Screen.FILE_ACCESS },
                        onOpenPermissions = { screen = Screen.PERMISSIONS },
                        voiceSettings = app.voiceSettings,
                        onlineSettings = app.onlineSettings
                    )
                    Screen.MEMORY -> MemoryScreen(
                        memoryRepository = app.memoryRepository,
                        memorySettings = app.memorySettings,
                        onBack = { screen = Screen.SETTINGS }
                    )
                    Screen.FILE_ACCESS -> FileAccessScreen(
                        fileAccessSettings = app.fileAccessSettings,
                        onBack = { screen = Screen.SETTINGS }
                    )
                    Screen.PERMISSIONS -> PermissionScreen(
                        onBack = { screen = Screen.SETTINGS }
                    )
                }
            }
        }
    }

    private fun currentNetworkStatus(): NetworkStatus {
        val cm = getSystemService(android.net.ConnectivityManager::class.java)
        val network = cm?.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        val connected = caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        return if (connected) NetworkStatus.ONLINE else NetworkStatus.OFFLINE
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.stopListening()
        ttsManager.shutdown()
    }
}
