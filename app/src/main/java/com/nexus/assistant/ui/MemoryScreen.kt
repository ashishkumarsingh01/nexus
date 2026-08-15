package com.nexus.assistant.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexus.assistant.memory.MemoryEntity
import com.nexus.assistant.memory.MemoryRepository
import com.nexus.assistant.memory.MemorySettings
import com.nexus.assistant.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(
    memoryRepository: MemoryRepository,
    memorySettings: MemorySettings,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val memories by memoryRepository.observeMemories().collectAsState(initial = emptyList())
    var persistConversations by remember { mutableStateOf(memorySettings.persistConversations) }
    var confirmDeleteAll by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = NexusBackground,
        topBar = {
            TopAppBar(
                title = { Text("Memory", color = NexusTextPrimary) },
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Save conversation history", color = NexusTextPrimary, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Off by default. When on, chats are kept locally so NEXUS has more context next time.",
                        color = NexusTextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Switch(
                    checked = persistConversations,
                    onCheckedChange = {
                        persistConversations = it
                        memorySettings.persistConversations = it
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = NexusAccentDim)
                )
            }

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Remembered facts (${memories.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = NexusTextSecondary
                )
                if (memories.isNotEmpty()) {
                    TextButton(onClick = { confirmDeleteAll = true }) {
                        Text("Delete all", color = NexusDanger)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (memories.isEmpty()) {
                Text(
                    "Nothing stored yet. Try saying \"Remember that…\" in chat.",
                    color = NexusTextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(memories, key = { it.id }) { memory ->
                        MemoryRow(memory) {
                            scope.launch { memoryRepository.deleteMemory(memory) }
                        }
                    }
                }
            }
        }
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Delete all memories?") },
            text = { Text("This permanently removes everything NEXUS remembers. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { memoryRepository.deleteAllMemories() }
                    confirmDeleteAll = false
                }) { Text("Delete all", color = NexusDanger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun MemoryRow(memory: MemoryEntity, onDelete: () -> Unit) {
    Surface(color = NexusSurface, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(memory.content, color = NexusTextPrimary, modifier = Modifier.weight(1f))
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = NexusTextSecondary)
            }
        }
    }
}
