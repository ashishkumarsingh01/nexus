package com.nexus.assistant.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexus.assistant.ai.ModelManager
import com.nexus.assistant.core.ModelStatus
import com.nexus.assistant.memory.MemoryDatabase
import com.nexus.assistant.tools.FileAccessSettings
import com.nexus.assistant.ui.theme.*
import com.nexus.assistant.voice.VoiceSettings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modelManager: ModelManager,
    onBack: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenFileAccess: () -> Unit,
    onOpenPermissions: () -> Unit,
    voiceSettings: VoiceSettings,
    onlineSettings: com.nexus.assistant.online.OnlineSettings
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val status by modelManager.status.collectAsState()
    var installedModelName by remember { mutableStateOf(modelManager.getInstalledModel()?.fileName) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var isBusy by remember { mutableStateOf(false) }
    var micEnabled by remember { mutableStateOf(voiceSettings.microphoneEnabled) }
    var wakeWordEnabled by remember { mutableStateOf(voiceSettings.wakeWordEnabled) }
    var ttsEnabled by remember { mutableStateOf(voiceSettings.ttsEnabled) }
    var confirmDeleteAllData by remember { mutableStateOf(false) }
    var onlineModeEnabled by remember { mutableStateOf(onlineSettings.onlineModeEnabled) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isBusy = true
        errorText = null
        scope.launch {
            val result = modelManager.importModel(uri)
            result.onSuccess { info ->
                installedModelName = info.fileName
                modelManager.loadModel() // attempt immediate load; UI reflects real outcome via status flow
            }.onFailure { e ->
                errorText = e.message ?: "Import failed."
            }
            isBusy = false
        }
    }

    Scaffold(
        containerColor = NexusBackground,
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = NexusTextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = NexusTextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NexusBackground)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Text("Local AI Model", style = MaterialTheme.typography.labelSmall, color = NexusTextSecondary)
            Spacer(Modifier.height(8.dp))

            Surface(color = NexusSurface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = installedModelName ?: "No model installed",
                        color = NexusTextPrimary,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = statusLabel(status),
                        color = statusColor(status),
                        style = MaterialTheme.typography.labelSmall
                    )

                    Spacer(Modifier.height(12.dp))

                    Row {
                        Button(
                            onClick = { importLauncher.launch(arrayOf("*/*")) },
                            enabled = !isBusy,
                            colors = ButtonDefaults.buttonColors(containerColor = NexusAccentDim)
                        ) {
                            Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (installedModelName == null) "Import Model" else "Replace Model")
                        }

                        if (installedModelName != null) {
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = {
                                    isBusy = true
                                    scope.launch {
                                        modelManager.deleteModel()
                                        installedModelName = null
                                        isBusy = false
                                    }
                                },
                                enabled = !isBusy
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Remove")
                            }
                        }
                    }

                    if (isBusy) {
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = NexusAccent)
                    }

                    errorText?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = NexusDanger, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "NEXUS expects a MediaPipe-format .task LLM file (e.g. a converted Gemma 2B or Phi-2 model). " +
                    "Download one to your device first (any browser or file manager), then import it here. " +
                    "NEXUS never downloads model files on its own.",
                style = MaterialTheme.typography.bodyMedium,
                color = NexusTextSecondary
            )

            Spacer(Modifier.height(24.dp))
            Text("Memory", style = MaterialTheme.typography.labelSmall, color = NexusTextSecondary)
            Spacer(Modifier.height(8.dp))
            SettingsNavRow("View & manage remembered facts", onOpenMemory)

            Spacer(Modifier.height(24.dp))
            Text("Files", style = MaterialTheme.typography.labelSmall, color = NexusTextSecondary)
            Spacer(Modifier.height(8.dp))
            SettingsNavRow("Manage file access folder", onOpenFileAccess)

            Spacer(Modifier.height(24.dp))
            Text("Permissions", style = MaterialTheme.typography.labelSmall, color = NexusTextSecondary)
            Spacer(Modifier.height(8.dp))
            SettingsNavRow("View & grant permissions", onOpenPermissions)

            Spacer(Modifier.height(24.dp))
            Text("Voice", style = MaterialTheme.typography.labelSmall, color = NexusTextSecondary)
            Spacer(Modifier.height(8.dp))
            Surface(color = NexusSurface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    SettingsToggleRow("Microphone (tap-to-talk)", micEnabled) {
                        micEnabled = it; voiceSettings.microphoneEnabled = it
                    }
                    Spacer(Modifier.height(10.dp))
                    SettingsToggleRow("Wake word (\"Nexus\", always-listening)", wakeWordEnabled) {
                        wakeWordEnabled = it; voiceSettings.wakeWordEnabled = it
                    }
                    Spacer(Modifier.height(10.dp))
                    SettingsToggleRow("Speak responses aloud", ttsEnabled) {
                        ttsEnabled = it; voiceSettings.ttsEnabled = it
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("Online Mode", style = MaterialTheme.typography.labelSmall, color = NexusTextSecondary)
            Spacer(Modifier.height(8.dp))
            Surface(color = NexusSurface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    SettingsToggleRow("Allow internet use (weather lookups)", onlineModeEnabled) {
                        onlineModeEnabled = it; onlineSettings.onlineModeEnabled = it
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Off by default. NEXUS works fully offline either way — this only adds optional " +
                            "internet-dependent features on top, never required for the core assistant.",
                        color = NexusTextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("Privacy & Data", style = MaterialTheme.typography.labelSmall, color = NexusTextSecondary)
            Spacer(Modifier.height(8.dp))
            Text(
                "NEXUS never sends your files, messages, contacts, notifications, memories, or voice recordings " +
                    "to any external server. Everything above runs and stays on this device.",
                color = NexusTextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { confirmDeleteAllData = true },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NexusDanger)
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Delete all NEXUS data")
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDeleteAllData) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAllData = false },
            title = { Text("Delete all NEXUS data?") },
            text = {
                Text(
                    "This permanently deletes all memories, conversation history, the imported AI model, and " +
                        "resets all settings. This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        MemoryDatabase.getInstance(context).clearAllTables()
                        modelManager.deleteModel()
                        installedModelName = null
                        context.getSharedPreferences("nexus_memory_settings", android.content.Context.MODE_PRIVATE).edit().clear().apply()
                        context.getSharedPreferences("nexus_voice_settings", android.content.Context.MODE_PRIVATE).edit().clear().apply()
                        context.getSharedPreferences("nexus_file_access", android.content.Context.MODE_PRIVATE).edit().clear().apply()
                        confirmDeleteAllData = false
                    }
                }) { Text("Delete everything", color = NexusDanger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAllData = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingsNavRow(label: String, onClick: () -> Unit) {
    Surface(color = NexusSurface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = NexusTextPrimary)
            Text("›", color = NexusTextSecondary)
        }
    }
}

@Composable
private fun SettingsToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(label, color = NexusTextPrimary, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedTrackColor = NexusAccentDim))
    }
}

private fun statusLabel(status: ModelStatus): String = when (status) {
    ModelStatus.READY -> "Ready"
    ModelStatus.LOADING -> "Loading…"
    ModelStatus.NOT_LOADED -> "Not loaded"
    ModelStatus.UNAVAILABLE -> "Unavailable"
}

@Composable
private fun statusColor(status: ModelStatus) = when (status) {
    ModelStatus.READY -> NexusAccent
    ModelStatus.LOADING -> NexusWarning
    ModelStatus.NOT_LOADED -> NexusTextSecondary
    ModelStatus.UNAVAILABLE -> NexusDanger
}
