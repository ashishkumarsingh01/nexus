package com.nexus.assistant.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nexus.assistant.agent.AgentPlanner
import com.nexus.assistant.ai.LocalAIEngine
import com.nexus.assistant.ai.ModelManager
import com.nexus.assistant.memory.MemoryRepository
import com.nexus.assistant.memory.MemorySettings
import com.nexus.assistant.tools.ToolRouter

class NexusViewModelFactory(
    private val aiEngine: LocalAIEngine,
    private val modelManager: ModelManager,
    private val memoryRepository: MemoryRepository,
    private val memorySettings: MemorySettings,
    private val toolRouter: ToolRouter,
    private val agentPlanner: AgentPlanner
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return NexusViewModel(aiEngine, modelManager, memoryRepository, memorySettings, toolRouter, agentPlanner) as T
    }
}
