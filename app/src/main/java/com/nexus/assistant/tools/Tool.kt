package com.nexus.assistant.tools

import com.nexus.assistant.security.PermissionLevel

/**
 * The AI never calls Android APIs directly (per spec). It only ever
 * produces a tool name + params; ToolRouter resolves that to one of these
 * and enforces permissions/confirmation before Execute() runs.
 */
interface Tool {
    val name: String
    val description: String
    val permissionLevel: PermissionLevel

    /** Android runtime permissions this tool needs, if any. Empty for SAFE tools. */
    val requiredAndroidPermissions: List<String> get() = emptyList()

    /**
     * Executes the tool. [confirmed] is only meaningful for DANGEROUS tools —
     * ToolRouter will not set it true unless the user actually confirmed via
     * the UI, so a Tool implementation can trust it.
     */
    suspend fun execute(params: Map<String, String> = emptyMap(), confirmed: Boolean = false): ToolResult
}
