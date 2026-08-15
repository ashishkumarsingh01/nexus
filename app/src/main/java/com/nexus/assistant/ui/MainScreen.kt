package com.nexus.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexus.assistant.core.ModelStatus
import com.nexus.assistant.core.NetworkStatus
import com.nexus.assistant.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: NexusViewModel, onOpenSettings: () -> Unit, onMicClick: () -> Unit, isListening: Boolean) {
    val state = viewModel.state
    val messages = viewModel.messages
    val listState = rememberLazyListState()
    val pending = viewModel.pendingConfirmation

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(
        containerColor = NexusBackground,
        topBar = { NexusHeader(state.modelStatus, state.networkStatus, onOpenSettings) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            state.currentActivity?.let { activity ->
                ToolActivityChip(activity)
                Spacer(Modifier.height(8.dp))
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    ChatBubble(message)
                }
            }

            InputBar(
                text = viewModel.inputText,
                onTextChange = viewModel::onInputChanged,
                onSend = viewModel::onSend,
                onMicClick = onMicClick,
                isListening = isListening
            )
            Spacer(Modifier.height(12.dp))
        }
    }

    if (pending != null) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelPendingAction() },
            title = { Text("Confirm action") },
            text = { Text(pending.prompt) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmPendingAction() }) {
                    Text("Confirm", color = NexusDanger)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelPendingAction() }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun NexusHeader(modelStatus: ModelStatus, networkStatus: NetworkStatus, onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NexusBackground)
            .padding(top = 20.dp, bottom = 12.dp, start = 20.dp, end = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "NEXUS",
                style = MaterialTheme.typography.headlineLarge,
                color = NexusTextPrimary
            )
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = NexusTextSecondary)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill(networkStatus)
            Spacer(Modifier.width(8.dp))
            ModelStatusPill(modelStatus)
        }
    }
}

@Composable
private fun StatusPill(networkStatus: NetworkStatus) {
    val (color, label) = when (networkStatus) {
        NetworkStatus.ONLINE -> NexusAccent to "ONLINE"
        NetworkStatus.OFFLINE -> NexusWarning to "OFFLINE"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun ModelStatusPill(modelStatus: ModelStatus) {
    val (color, label) = when (modelStatus) {
        ModelStatus.READY -> NexusAccent to "MODEL READY"
        ModelStatus.LOADING -> NexusWarning to "LOADING MODEL"
        ModelStatus.NOT_LOADED -> NexusTextSecondary to "NO MODEL LOADED"
        ModelStatus.UNAVAILABLE -> NexusDanger to "MODEL UNAVAILABLE"
    }
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun ToolActivityChip(activity: String) {
    Surface(
        color = NexusSurfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = activity,
            style = MaterialTheme.typography.bodyMedium,
            color = NexusTextSecondary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val isUser = message.sender == Sender.USER
    val isSystem = message.sender == Sender.SYSTEM

    val bubbleColor = when {
        isSystem -> Color.Transparent
        isUser -> NexusBubbleUser
        else -> NexusBubbleNexus
    }
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                text = message.text,
                color = if (isSystem) NexusTextSecondary else NexusTextPrimary,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSystem) FontWeight.Light else FontWeight.Normal,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun InputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onMicClick: () -> Unit,
    isListening: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        IconButton(
            onClick = onMicClick,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (isListening) NexusDanger.copy(alpha = 0.25f) else NexusSurfaceVariant)
        ) {
            Icon(
                Icons.Filled.Mic,
                contentDescription = if (isListening) "Listening…" else "Voice input",
                tint = if (isListening) NexusDanger else NexusTextSecondary
            )
        }

        Spacer(Modifier.width(10.dp))

        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Type a message…", color = NexusTextSecondary) },
            shape = RoundedCornerShape(20.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = NexusSurface,
                unfocusedContainerColor = NexusSurface,
                focusedTextColor = NexusTextPrimary,
                unfocusedTextColor = NexusTextPrimary,
                focusedBorderColor = NexusAccentDim,
                unfocusedBorderColor = NexusSurfaceVariant
            ),
            singleLine = true
        )

        Spacer(Modifier.width(8.dp))

        IconButton(
            onClick = onSend,
            enabled = text.isNotBlank(),
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (text.isNotBlank()) NexusAccentDim else NexusSurfaceVariant)
        ) {
            Icon(Icons.Filled.Send, contentDescription = "Send", tint = NexusTextPrimary)
        }
    }
}
