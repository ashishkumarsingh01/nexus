package com.nexus.assistant.ui

import android.content.Intent
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
import androidx.documentfile.provider.DocumentFile
import com.nexus.assistant.tools.FileAccessSettings
import com.nexus.assistant.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileAccessScreen(fileAccessSettings: FileAccessSettings, onBack: () -> Unit) {
    val context = LocalContext.current
    var grantedUri by remember { mutableStateOf(fileAccessSettings.grantedTreeUri) }
    val grantedFolderName = remember(grantedUri) {
        grantedUri?.let { DocumentFile.fromTreeUri(context, it)?.name }
    }

    val pickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            fileAccessSettings.grantedTreeUri = uri
            grantedUri = uri
        }
    }

    Scaffold(
        containerColor = NexusBackground,
        topBar = {
            TopAppBar(
                title = { Text("File Access", color = NexusTextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = NexusTextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NexusBackground)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            Text(
                "NEXUS can only see files inside one folder you choose — never your whole device storage. " +
                    "This is Android's standard, sandboxed file-access mechanism (Storage Access Framework).",
                color = NexusTextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(16.dp))

            Surface(color = NexusSurface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = grantedFolderName?.let { "Granted folder: $it" } ?: "No folder granted yet",
                        color = NexusTextPrimary
                    )
                    Spacer(Modifier.height(12.dp))
                    Row {
                        Button(
                            onClick = { pickerLauncher.launch(null) },
                            colors = ButtonDefaults.buttonColors(containerColor = NexusAccentDim)
                        ) {
                            Text(if (grantedUri == null) "Grant Folder Access" else "Change Folder")
                        }
                        if (grantedUri != null) {
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(onClick = {
                                fileAccessSettings.grantedTreeUri = null
                                grantedUri = null
                            }) {
                                Text("Revoke", color = NexusDanger)
                            }
                        }
                    }
                }
            }
        }
    }
}
