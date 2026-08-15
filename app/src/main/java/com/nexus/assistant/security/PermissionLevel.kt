package com.nexus.assistant.security

/**
 * Every Tool declares one of these. This classification — not the tool's
 * own judgment — decides whether ToolRouter requires an Android runtime
 * permission check, a user confirmation dialog, both, or neither.
 */
enum class PermissionLevel {
    /** No confirmation, no dangerous Android permission. e.g. get time, get battery. */
    SAFE,

    /** Requires a granted Android runtime permission, but no extra confirmation dialog
     *  beyond the OS's own permission prompt. e.g. read notifications, use microphone. */
    SENSITIVE,

    /** Requires both a granted Android permission (if applicable) AND an explicit
     *  in-app confirmation before executing. e.g. delete a file, send a message. */
    DANGEROUS
}
