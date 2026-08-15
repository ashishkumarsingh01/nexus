package com.nexus.assistant.tools

import android.util.Log
import com.nexus.assistant.security.PermissionLevel
import com.nexus.assistant.security.PermissionManager

private const val TAG = "NexusToolRouter"

/**
 * Central dispatcher. Responsibilities (per spec):
 *  1. Receive the AI's requested action (tool name + params)
 *  2. Identify the correct tool
 *  3. Validate parameters
 *  4. Check permissions
 *  5. Ask for confirmation if necessary
 *  6. Execute the tool
 *  7. Return the result to the AI
 *  8. Handle errors
 *
 * Nothing outside this class ever calls Tool.execute() directly.
 */
class ToolRouter(private val permissionManager: PermissionManager) {

    private val tools = mutableMapOf<String, Tool>()

    fun register(tool: Tool) {
        tools[tool.name] = tool
    }

    fun registerAll(vararg toolsToAdd: Tool) {
        toolsToAdd.forEach { register(it) }
    }

    fun availableTools(): List<Tool> = tools.values.toList()

    /**
     * Runs [toolName] with [params]. Set [confirmed] = true only when the
     * user has explicitly confirmed a prior NeedsConfirmation result for
     * this exact call — the UI layer is responsible for that, never the AI.
     */
    suspend fun route(toolName: String, params: Map<String, String> = emptyMap(), confirmed: Boolean = false): ToolResult {
        val tool = tools[toolName]
            ?: return ToolResult.Error("Unknown tool: \"$toolName\". This action isn't supported.")

        // Permission check (SENSITIVE and DANGEROUS tiers may require Android permissions).
        if (tool.permissionLevel != PermissionLevel.SAFE && tool.requiredAndroidPermissions.isNotEmpty()) {
            val missing = permissionManager.missing(tool.requiredAndroidPermissions)
            if (missing.isNotEmpty()) {
                return ToolResult.PermissionRequired(
                    androidPermissions = missing,
                    message = "NEXUS needs permission to do this. Please grant it to continue."
                )
            }
        }

        // Confirmation check (DANGEROUS tier only) — Tool.execute(confirmed=false)
        // is expected to return NeedsConfirmation rather than act, by convention.
        return try {
            tool.execute(params, confirmed)
        } catch (e: Exception) {
            Log.e(TAG, "Tool '$toolName' execution failed", e)
            ToolResult.Error("\"$toolName\" failed: ${e.message ?: "unknown error"}. Nothing was changed.")
        }
    }
}
