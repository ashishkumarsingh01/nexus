package com.nexus.assistant.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.nexus.assistant.ui.theme.*

private data class PermissionRow(
    val label: String,
    val androidPermission: String?,
    val tier: String,
    val note: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    val rows = remember {
        listOf(
            PermissionRow("Microphone (voice input)", Manifest.permission.RECORD_AUDIO, "SENSITIVE", "For tap-to-talk and wake word."),
            PermissionRow("Contacts", Manifest.permission.READ_CONTACTS, "SENSITIVE", "To look up who you're messaging or calling."),
            PermissionRow("Send SMS", Manifest.permission.SEND_SMS, "DANGEROUS", "Only used after you confirm a message NEXUS drafted."),
            PermissionRow("Phone calls", Manifest.permission.CALL_PHONE, "DANGEROUS", "Only used after you confirm placing a call."),
            PermissionRow("Notification access", null, "SENSITIVE", "Granted separately in system Settings, not here.")
        )
    }

    var refreshTick by remember { mutableStateOf(0) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refreshTick++ }

    Scaffold(
        containerColor = NexusBackground,
        topBar = {
            TopAppBar(
                title = { Text("Permissions", color = NexusTextPrimary) },
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
            modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize()
        ) {
            Text(
                "SAFE actions (battery, time, storage, opening approved apps) never need a permission prompt. " +
                    "SENSITIVE and DANGEROUS actions below need explicit grants, and DANGEROUS actions always ask you to confirm too, every time.",
                color = NexusTextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(16.dp))

            rows.forEach { row ->
                key(row.label, refreshTick) {
                    val granted = row.androidPermission?.let {
                        ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    }
                    Surface(
                        color = NexusSurface,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(row.label, color = NexusTextPrimary, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    row.tier,
                                    color = if (row.tier == "DANGEROUS") NexusDanger else NexusWarning,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Text(row.note, color = NexusTextSecondary, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(8.dp))
                            when {
                                row.androidPermission == null -> {
                                    TextButton(onClick = {
                                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                    }) { Text("Open system Settings") }
                                }
                                granted == true -> {
                                    Text("Granted", color = NexusAccent, style = MaterialTheme.typography.labelSmall)
                                }
                                else -> {
                                    Button(
                                        onClick = { permissionLauncher.launch(row.androidPermission) },
                                        colors = ButtonDefaults.buttonColors(containerColor = NexusAccentDim)
                                    ) { Text("Grant") }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            TextButton(onClick = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            }) {
                Text("Open full app permission settings")
            }
        }
    }
}
