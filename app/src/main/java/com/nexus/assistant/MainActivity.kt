package com.nexus.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

    private var speechRecognizer: NexusSpeechRecognizer? = null
    private var ttsManager: TextToSpeechManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as NexusApplication

        // Defensive creation of optional managers.
        speechRecognizer = try {
            NexusSpeechRecognizer(this)
        } catch (e: Throwable) {
            Log.e("MainActivity", "Failed to create SpeechRecognizer", e)
            null
        }
        ttsManager = try {
            TextToSpeechManager(this)
        } catch (e: Throwable) {
            Log.e("MainActivity", "Failed to create TextToSpeech", e)
            null
        }

        setContent {
            NexusTheme {
                var screen by remember { mutableStateOf(Screen.CHAT) }
                var isListening by remember { mutableStateOf(false) }
                var micPermissionDenied by remember { mutableStateOf(false) }

                // Only create the NexusViewModel if core app components initialized successfully.
                val canCreateViewModel = app.aiEngine != null && app.modelManager != null && app.memoryRepository != null && app.toolRouter != null && app.agentPlanner != null

                val viewModel: NexusViewModel? = if (canCreateViewModel) {
                    viewModel<NexusViewModel>(
                        factory = NexusViewModelFactory(
                            app.aiEngine!!, app.modelManager!!, app.memoryRepository!!,
                            app.memorySettings!!, app.toolRouter!!, app.agentPlanner!!
                        )
                    )
                } else null

                // Reflect real connectivity, not a guess.
                LaunchedEffect(Unit) {
                    viewModel?.setNetworkStatus(currentNetworkStatus())
                }

                // Speak NEXUS's replies aloud if TTS is enabled - the last
                // NEXUS message is watched and spoken once, not re-spoken
                // on every recomposition.
                var lastSpokenId by remember { mutableStateOf<Long?>(null) }
                LaunchedEffect(viewModel?.messages?.size ?: 0) {
                    if (app.voiceSettings?.ttsEnabled != true) return@LaunchedEffect
                    val last = viewModel?.messages?.lastOrNull()
                    if (last != null && last.sender == com.nexus.assistant.ui.Sender.NEXUS && last.id != lastSpokenId) {
                        lastSpokenId = last.id
                        ttsManager?.speak(last.text)
                    }
                }

                val micPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) {
                        isListening = true
                        speechRecognizer?.startListening(
                            onResult = { text -> isListening = false; viewModel?.onVoiceResult(text) },
                            onError = { isListening = false },
                            onListeningStateChanged = { listening -> isListening = listening }
                        )
                    } else {
                        micPermissionDenied = true
                    }
                }

                fun onMicClick() {
                    if (app.voiceSettings?.microphoneEnabled != true) return
                    val hasPermission = ContextCompat.checkSelfPermission(
                        this@MainActivity, Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED

                    if (hasPermission) {
                        isListening = true
                        speechRecognizer?.startListening(
                            onResult = { text -> isListening = false; viewModel?.onVoiceResult(text) },
                            onError = { isListening = false },
                            onListeningStateChanged = { listening -> isListening = listening }
                        )
                    } else {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }

                if (viewModel == null) {
                    // Core initialization failed — show a simple, non-crashing fallback UI.
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("NEXUS failed to initialize some core components.")
                        Spacer(modifier = Modifier.padding(8.dp))
                        Button(onClick = { screen = Screen.SETTINGS }) {
                            Text("Open Settings")
                        }
                    }
                } else {
                    when (screen) {
                        Screen.CHAT -> MainScreen(
                            viewModel = viewModel,
                            onOpenSettings = { screen = Screen.SETTINGS },
                            onMicClick = { onMicClick() },
                            isListening = isListening
                        )
                        Screen.SETTINGS -> SettingsScreen(
                            modelManager = app.modelManager!!,
                            onBack = { screen = Screen.CHAT },
                            onOpenMemory = { screen = Screen.MEMORY },
                            onOpenFileAccess = { screen = Screen.FILE_ACCESS },
                            onOpenPermissions = { screen = Screen.PERMISSIONS },
                            voiceSettings = app.voiceSettings!!,
                            onlineSettings = app.onlineSettings!!
                        )
                        Screen.MEMORY -> MemoryScreen(
                            memoryRepository = app.memoryRepository!!,
                            memorySettings = app.memorySettings!!,
                            onBack = { screen = Screen.SETTINGS }
                        )
                        Screen.FILE_ACCESS -> FileAccessScreen(
                            fileAccessSettings = app.fileAccessSettings!!,
                            onBack = { screen = Screen.SETTINGS }
                        )
                        Screen.PERMISSIONS -> PermissionScreen(
                            onBack = { screen = Screen.SETTINGS }
                        )
                    }
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
        speechRecognizer?.stopListening()
        ttsManager?.shutdown()
    }
}
