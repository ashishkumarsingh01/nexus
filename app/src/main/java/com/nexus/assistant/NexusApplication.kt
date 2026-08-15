package com.nexus.assistant

import android.app.Application
import com.nexus.assistant.agent.AgentPlanner
import com.nexus.assistant.ai.LocalAIEngine
import com.nexus.assistant.ai.ModelManager
import com.nexus.assistant.memory.MemoryDatabase
import com.nexus.assistant.memory.MemoryRepository
import com.nexus.assistant.memory.MemorySettings
import com.nexus.assistant.online.OnlineSettings
import com.nexus.assistant.online.OnlineTool
import com.nexus.assistant.security.PermissionManager
import com.nexus.assistant.tools.AppTool
import com.nexus.assistant.tools.CommunicationTool
import com.nexus.assistant.tools.DeviceTool
import com.nexus.assistant.tools.FileAccessSettings
import com.nexus.assistant.tools.FileTool
import com.nexus.assistant.tools.NotificationTool
import com.nexus.assistant.tools.ToolRouter
import com.nexus.assistant.voice.VoiceSettings

/**
 * Holds app-wide singletons that must outlive any single Activity/ViewModel,
 * so nothing about the assistant depends on UI lifecycle. Deliberately not
 * using a DI framework (Hilt/Koin) — the object graph is small enough that
 * a manual composition root here is simpler to audit for a privacy-focused
 * app, and keeps the dependency list lean for a mobile build.
 *
 * All 12 phases' singletons are composed here. Nothing in this file makes
 * network calls, and nothing here uploads any locally-stored data anywhere.
 */
class NexusApplication : Application() {

    lateinit var modelManager: ModelManager
        private set
    lateinit var aiEngine: LocalAIEngine
        private set
    lateinit var memoryRepository: MemoryRepository
        private set
    lateinit var memorySettings: MemorySettings
        private set
    lateinit var permissionManager: PermissionManager
        private set
    lateinit var toolRouter: ToolRouter
        private set
    lateinit var agentPlanner: AgentPlanner
        private set
    lateinit var fileAccessSettings: FileAccessSettings
        private set
    lateinit var voiceSettings: VoiceSettings
        private set
    lateinit var onlineSettings: OnlineSettings
        private set

    override fun onCreate() {
        super.onCreate()

        modelManager = ModelManager(this)
        aiEngine = LocalAIEngine(modelManager)
        memoryRepository = MemoryRepository(MemoryDatabase.getInstance(this))
        memorySettings = MemorySettings(this)
        permissionManager = PermissionManager(this)
        fileAccessSettings = FileAccessSettings(this)
        voiceSettings = VoiceSettings(this)
        onlineSettings = OnlineSettings(this)

        toolRouter = ToolRouter(permissionManager).apply {
            registerAll(
                DeviceTool(this@NexusApplication),
                FileTool(this@NexusApplication, fileAccessSettings),
                AppTool(this@NexusApplication),
                NotificationTool(this@NexusApplication),
                CommunicationTool(this@NexusApplication, permissionManager),
                // Always registered, but internally refuses to do anything
                // unless OnlineSettings.onlineModeEnabled is explicitly on -
                // keeps this genuinely optional and independent of the
                // offline core rather than needing conditional wiring.
                OnlineTool(this@NexusApplication, onlineSettings)
            )
        }

        agentPlanner = AgentPlanner(toolRouter, aiEngine)

        // No local model is loaded automatically. NEXUS only loads a model
        // once one is installed (see Settings -> Import Model), and the UI
        // reflects real status (ModelStatus.NOT_LOADED / UNAVAILABLE) rather
        // than pretending readiness.
    }
}
