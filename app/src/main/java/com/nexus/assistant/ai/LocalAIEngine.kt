package com.nexus.assistant.ai

import com.nexus.assistant.core.ModelStatus
import kotlinx.coroutines.flow.StateFlow

sealed class AIResponse {
    data class Success(val text: String) : AIResponse()
    data class Unavailable(val reason: String) : AIResponse()
}

/**
 * PHASE 2/3: plain conversational engine. No tool-calling or intent
 * detection yet — that's layered on top in Phase 5 (Tool system) via
 * ToolRouter, which will sit between this class and the UI rather than
 * inside it, keeping "talk to the model" and "decide to use a tool"
 * separate responsibilities.
 */
class LocalAIEngine(private val modelManager: ModelManager) {

    val modelStatus: StateFlow<ModelStatus> = modelManager.status

    private val history = mutableListOf<Pair<String, String>>() // (speaker, text)

    suspend fun ensureModelLoaded(): Boolean {
        if (modelManager.status.value == ModelStatus.READY) return true
        return modelManager.loadModel().isSuccess
    }

    suspend fun respond(userMessage: String): AIResponse {
        if (!modelManager.isModelAvailable()) {
            return AIResponse.Unavailable(
                "I don't have a local AI model installed yet. Import a .task model file in Settings to enable this."
            )
        }

        if (modelManager.status.value != ModelStatus.READY) {
            val loaded = ensureModelLoaded()
            if (!loaded) {
                val err = modelManager.lastError.value
                return AIResponse.Unavailable(err?.userMessage ?: "The local AI model isn't available right now.")
            }
        }

        val prompt = PromptManager.buildPrompt(history, userMessage)
        val result = modelManager.generate(prompt)

        return result.fold(
            onSuccess = { text ->
                history.add("User" to userMessage)
                history.add("NEXUS" to text)
                AIResponse.Success(text.trim())
            },
            onFailure = { e ->
                AIResponse.Unavailable("Inference failed: ${e.message ?: "unknown error"}. Try again, or check Settings.")
            }
        )
    }

    fun clearHistory() {
        history.clear()
    }

    /**
     * One-off generation for internal use (e.g. AgentPlanner summarization)
     * that does NOT touch the main conversation history - keeps agent
     * sub-tasks from polluting what the user sees as "the conversation".
     */
    suspend fun generateStandalone(prompt: String): AIResponse {
        if (!modelManager.isModelAvailable()) {
            return AIResponse.Unavailable("No local AI model installed yet.")
        }
        if (modelManager.status.value != ModelStatus.READY && !ensureModelLoaded()) {
            return AIResponse.Unavailable(modelManager.lastError.value?.userMessage ?: "Model unavailable.")
        }
        return modelManager.generate(prompt).fold(
            onSuccess = { AIResponse.Success(it.trim()) },
            onFailure = { AIResponse.Unavailable("Generation failed: ${it.message ?: "unknown error"}") }
        )
    }
}
