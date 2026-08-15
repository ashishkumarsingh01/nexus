package com.nexus.assistant

import android.app.Application
import android.util.Log
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
 * Application composition root. Initialization is defensive: risky/long-running
 * components are wrapped in try/catch and left null on failure so the app can
 * start and surface a graceful UI instead of crashing at cold start.
 */
class NexusApplication : Application() {

    // Core components may fail to initialize on some devices (native libs,
    // missing files, DB issues). Keep them nullable and recover gracefully.
    var modelManager: ModelManager? = null
        private set
    var aiEngine: LocalAIEngine? = null
        private set
    var memoryRepository: MemoryRepository? = null
        private set
    var memorySettings: MemorySettings? = null
        private set
    lateinit var permissionManager: PermissionManager
        private set
    var toolRouter: ToolRouter? = null
        private set
    var agentPlanner: AgentPlanner? = null
        private set
    var fileAccessSettings: FileAccessSettings? = null
        private set
    var voiceSettings: VoiceSettings? = null
        private set
    var onlineSettings: OnlineSettings? = null
        private set

    override fun onCreate() {
        super.onCreate()

        // Initialize components that are unlikely to fail first.
        try {
            permissionManager = PermissionManager(this)
        } catch (e: Throwable) {
            Log.e("NexusApplication", "PermissionManager init failed", e)
            // permissionManager is critical for ToolRouter; if this fails the
            // rest of tool wiring will be skipped.
        }

        // Model & AI engine (may rely on native libs or files)
        try {
            modelManager = ModelManager(this)
            aiEngine = LocalAIEngine(modelManager!!)
        } catch (e: Throwable) {
            Log.e("NexusApplication", "Model/AI initialization failed", e)
            modelManager = null
            aiEngine = null
        }

        // Memory DB/repository
        try {
            memoryRepository = MemoryRepository(MemoryDatabase.getInstance(this))
        } catch (e: Throwable) {
            Log.e("NexusApplication", "Memory repository init failed", e)
            memoryRepository = null
        }

        try {
            memorySettings = MemorySettings(this)
        } catch (e: Throwable) {
            Log.e("NexusApplication", "MemorySettings init failed", e)
            memorySettings = null
        }

        try {
            fileAccessSettings = FileAccessSettings(this)
        } catch (e: Throwable) {
            Log.e("NexusApplication", "FileAccessSettings init failed", e)
            fileAccessSettings = null
        }

        try {
            voiceSettings = VoiceSettings(this)
        } catch (e: Throwable) {
            Log.e("NexusApplication", "VoiceSettings init failed", e)
            voiceSettings = null
        }

        try {
            onlineSettings = OnlineSettings(this)
        } catch (e: Throwable) {
            Log.e("NexusApplication", "OnlineSettings init failed", e)
            onlineSettings = null
        }

        // Tool router and tools - depends on permissionManager; make this tolerant
        try {
            // Ensure permissionManager is initialized (constructed) before using
            if (this::permissionManager.isInitialized) {
                toolRouter = ToolRouter(permissionManager).apply {
                    try {
                        registerAll(
                            DeviceTool(this@NexusApplication),
                            FileTool(this@NexusApplication, fileAccessSettings ?: FileAccessSettings(this@NexusApplication)),
                            AppTool(this@NexusApplication),
                            NotificationTool(this@NexusApplication),
                            CommunicationTool(this@NexusApplication, permissionManager),
                            // OnlineTool respects onlineSettings internally; it's safe to add even if onlineSettings is null
                            OnlineTool(this@NexusApplication, onlineSettings ?: OnlineSettings(this@NexusApplication))
                        )
                    } catch (e: Throwable) {
                        Log.e("NexusApplication", "One or more tools failed to register", e)
                    }
                }
            } else {
                Log.e("NexusApplication", "PermissionManager not available; skipping tool wiring")
            }
        } catch (e: Throwable) {
            Log.e("NexusApplication", "ToolRouter initialization failed", e)
            toolRouter = null
        }

        // AgentPlanner depends on toolRouter and aiEngine
        try {
            agentPlanner = if (toolRouter != null && aiEngine != null) {
                AgentPlanner(toolRouter!!, aiEngine!!)
            } else null
        } catch (e: Throwable) {
            Log.e("NexusApplication", "AgentPlanner init failed", e)
            agentPlanner = null
        }

        // Note: no automatic model loading here. Models are loaded explicitly via UI actions.
    }
}
