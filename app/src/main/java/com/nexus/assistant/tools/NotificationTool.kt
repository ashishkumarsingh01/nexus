package com.nexus.assistant.tools

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.nexus.assistant.security.PermissionLevel

/**
 * Notification access is a special user grant (system Settings), not a
 * normal runtime permission, so this checks it directly instead of via
 * PermissionManager's ContextCompat path. If access isn't granted, NEXUS
 * says so plainly and points to where to grant it - it never pretends to
 * have read notifications it can't see.
 */
class NotificationTool(private val context: Context) : Tool {

    override val name = "notification_tool"
    override val description = "Reads and summarizes currently active notifications, if notification access has been granted."
    override val permissionLevel = PermissionLevel.SENSITIVE

    private fun isAccessGranted(): Boolean {
        val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
        return context.packageName in enabledPackages && NexusNotificationListenerService.isRunning()
    }

    override suspend fun execute(params: Map<String, String>, confirmed: Boolean): ToolResult {
        if (!isAccessGranted()) {
            return ToolResult.Error(
                "NEXUS doesn't have notification access yet. Grant it in Settings → Notification Access to use this."
            )
        }

        val action = params["action"] ?: "list"
        val current = NexusNotificationListenerService.notifications.value

        return when (action) {
            "list" -> {
                if (current.isEmpty()) ToolResult.Success("No active notifications.")
                else ToolResult.Success(
                    current.joinToString("\n") { "• [${it.appLabel}] ${it.title}: ${it.text}" }
                )
            }
            "summarize" -> {
                if (current.isEmpty()) return ToolResult.Success("No active notifications.")
                val byApp = current.groupBy { it.appLabel }
                val summary = byApp.entries.joinToString("\n") { (app, notifs) -> "• $app: ${notifs.size} notification(s)" }
                ToolResult.Success("You have ${current.size} active notification(s):\n$summary")
            }
            else -> ToolResult.Error("Unknown notification_tool action: \"$action\".")
        }
    }
}
