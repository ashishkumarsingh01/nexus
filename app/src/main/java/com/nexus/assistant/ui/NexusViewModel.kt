package com.nexus.assistant.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.assistant.agent.AgentPlanner
import com.nexus.assistant.ai.AIResponse
import com.nexus.assistant.ai.LocalAIEngine
import com.nexus.assistant.ai.ModelManager
import com.nexus.assistant.core.AssistantState
import com.nexus.assistant.core.ModelStatus
import com.nexus.assistant.core.NetworkStatus
import com.nexus.assistant.memory.MemoryCommandResult
import com.nexus.assistant.memory.MemoryRepository
import com.nexus.assistant.memory.MemorySettings
import com.nexus.assistant.tools.IntentRouter
import com.nexus.assistant.tools.ToolResult
import com.nexus.assistant.tools.ToolRouter
import kotlinx.coroutines.launch

/**
 * PHASE 5/6/7/10/11 additions on top of Phase 4: user text is checked, in
 * order, against:
 *   1. Explicit memory commands       (deterministic, no model/tool call)
 *   2. Multi-step agent triggers      (AgentPlanner - Phase 11)
 *   3. Single-tool intents            (IntentRouter -> ToolRouter)
 *   4. Otherwise, the local AI model
 *
 * A tool call that comes back NeedsConfirmation or PermissionRequired is
 * surfaced to the user as a message + a pending-action the UI can act on;
 * NEXUS never re-executes a DANGEROUS tool on its own.
 */
class NexusViewModel(
    private val aiEngine: LocalAIEngine,
    private val modelManager: ModelManager,
    private val memoryRepository: MemoryRepository,
    private val memorySettings: MemorySettings,
    private val toolRouter: ToolRouter,
    private val agentPlanner: AgentPlanner
) : ViewModel() {

    var state by mutableStateOf(AssistantState())
        private set

    var messages by mutableStateOf(
        listOf(
            ChatMessage(
                sender = Sender.SYSTEM,
                text = "NEXUS ready. Import a local model in Settings if you haven't yet."
            )
        )
    )
        private set

    var inputText by mutableStateOf("")
        private set

    var isThinking by mutableStateOf(false)
        private set

    /** Set when a DANGEROUS tool call is awaiting explicit user confirmation. */
    var pendingConfirmation by mutableStateOf<PendingAction?>(null)
        private set

    data class PendingAction(val toolName: String, val params: Map<String, String>, val prompt: String)

    init {
        viewModelScope.launch {
            modelManager.status.collect { status -> state = state.copy(modelStatus = status) }
        }
        refreshModelAvailability()
    }

    fun refreshModelAvailability() {
        val status = if (modelManager.isModelAvailable()) {
            if (modelManager.status.value == ModelStatus.READY) ModelStatus.READY else ModelStatus.NOT_LOADED
        } else ModelStatus.NOT_LOADED
        state = state.copy(modelStatus = status)
    }

    fun onInputChanged(text: String) { inputText = text }

    fun onSend() {
        val trimmed = inputText.trim()
        if (trimmed.isEmpty() || isThinking) return
        messages = messages + ChatMessage(sender = Sender.USER, text = trimmed)
        inputText = ""
        isThinking = true

        viewModelScope.launch {
            memoryRepository.logConversationTurn("USER", trimmed, memorySettings)

            // 1. Memory commands
            when (val memResult = memoryRepository.tryHandleCommand(trimmed)) {
                is MemoryCommandResult.Remembered -> return@launch finish(memResult.confirmation)
                is MemoryCommandResult.Forgotten -> return@launch finish(memResult.confirmation)
                is MemoryCommandResult.Recalled -> return@launch finish(memResult.text)
                MemoryCommandResult.NotAMemoryCommand -> {}
            }

            // 2. Multi-step agent tasks
            if (agentPlanner.canHandle(trimmed)) {
                state = state.copy(currentActivity = "Planning task…")
                agentPlanner.run(trimmed) { statusUpdate -> state = state.copy(currentActivity = statusUpdate) }
                    .let { result -> return@launch finish(result) }
            }

            // 3. Single-tool intents
            IntentRouter.detect(trimmed)?.let { intent ->
                state = state.copy(currentActivity = "Checking ${intent.toolName.replace('_', ' ')}…")
                val result = toolRouter.route(intent.toolName, intent.params)
                return@launch handleToolResult(intent.toolName, intent.params, result)
            }

            // 4. Fall through to the AI model
            state = state.copy(currentActivity = "Thinking…")
            when (val response = aiEngine.respond(trimmed)) {
                is AIResponse.Success -> finish(response.text)
                is AIResponse.Unavailable -> {
                    messages = messages + ChatMessage(sender = Sender.SYSTEM, text = response.reason)
                    isThinking = false
                    state = state.copy(currentActivity = null)
                }
            }
        }
    }

    private suspend fun handleToolResult(toolName: String, params: Map<String, String>, result: ToolResult) {
        when (result) {
            is ToolResult.Success -> finish(result.message)
            is ToolResult.Error -> finish(result.message)
            is ToolResult.NeedsConfirmation -> {
                pendingConfirmation = PendingAction(toolName, params, result.prompt)
                finish(result.prompt)
            }
            is ToolResult.PermissionRequired -> finish(result.message)
        }
    }

    /** Called by the UI when the user taps "Confirm" on a pending DANGEROUS action. */
    fun confirmPendingAction() {
        val pending = pendingConfirmation ?: return
        pendingConfirmation = null
        isThinking = true
        viewModelScope.launch {
            val result = toolRouter.route(pending.toolName, pending.params, confirmed = true)
            handleToolResult(pending.toolName, pending.params, result)
        }
    }

    fun cancelPendingAction() {
        pendingConfirmation = null
        messages = messages + ChatMessage(sender = Sender.SYSTEM, text = "Okay, I won't do that.")
    }

    private suspend fun finish(text: String) {
        messages = messages + ChatMessage(sender = Sender.NEXUS, text = text)
        memoryRepository.logConversationTurn("NEXUS", text, memorySettings)
        isThinking = false
        state = state.copy(currentActivity = null)
    }

    fun setNetworkStatus(status: NetworkStatus) { state = state.copy(networkStatus = status) }

    /** Called by voice pipeline (Phase 8) to inject recognized speech as if typed. */
    fun onVoiceResult(text: String) {
        inputText = text
        onSend()
    }
}
