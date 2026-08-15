package com.nexus.assistant.tools

sealed class ToolResult {
    /** Tool ran and this is the real outcome — never fabricated. */
    data class Success(val message: String, val data: String? = null) : ToolResult()

    /** Tool ran but failed. Always surfaced to the user honestly, never hidden. */
    data class Error(val message: String) : ToolResult()

    /** DANGEROUS-tier tool needs explicit user confirmation before it will execute. */
    data class NeedsConfirmation(val prompt: String) : ToolResult()

    /** SENSITIVE/DANGEROUS-tier tool needs an Android runtime permission that isn't granted yet. */
    data class PermissionRequired(val androidPermissions: List<String>, val message: String) : ToolResult()
}
