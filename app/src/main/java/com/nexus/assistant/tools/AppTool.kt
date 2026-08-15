package com.nexus.assistant.tools

import android.content.Context
import android.content.Intent
import com.nexus.assistant.security.PermissionLevel

/**
 * Deliberately a curated allow-list, not an open-ended "launch any app by
 * name" tool. Per spec: "Start with safe application launching before
 * implementing more advanced interaction." Expanding this list means adding
 * both an entry here AND a matching <queries> entry in AndroidManifest.xml —
 * a package can't be checked or launched otherwise on Android 11+.
 */
class AppTool(private val context: Context) : Tool {

    override val name = "app_tool"
    override val description = "Launches a known, approved application by name."
    override val permissionLevel = PermissionLevel.SAFE

    private val knownApps = mapOf(
        "youtube" to "com.google.android.youtube",
        "chrome" to "com.android.chrome",
        "whatsapp" to "com.whatsapp",
        "gmail" to "com.google.android.gm",
        "maps" to "com.google.android.apps.maps",
        "google maps" to "com.google.android.apps.maps",
        "spotify" to "com.spotify.music",
        "camera" to "com.android.camera",
        "settings" to "com.android.settings",
        "phone" to "com.android.dialer",
        "messages" to "com.google.android.apps.messaging",
        "calendar" to "com.google.android.calendar",
        "photos" to "com.google.android.apps.photos",
        "play store" to "com.android.vending"
    )

    override suspend fun execute(params: Map<String, String>, confirmed: Boolean): ToolResult {
        val action = params["action"] ?: return ToolResult.Error("app_tool called without an 'action' parameter.")
        if (action != "open") return ToolResult.Error("Unknown app_tool action: \"$action\".")

        val requestedName = params["appName"]?.trim()?.lowercase()
            ?: return ToolResult.Error("No app name given.")

        // 1. Identify the requested application.
        val packageName = knownApps[requestedName]
        if (packageName == null) {
            return ToolResult.Error("I don't recognize an app called \"$requestedName\". NEXUS can currently open: ${knownApps.keys.joinToString(", ")}.")
        }

        // 2. Verify that the application exists (is actually installed).
        val launchIntent = try {
            context.packageManager.getLaunchIntentForPackage(packageName)
        } catch (e: Exception) {
            null
        }

        if (launchIntent == null) {
            return ToolResult.Error("\"$requestedName\" doesn't appear to be installed on this device, so I can't open it.")
        }

        // 3. No extra confirmation needed - launching an approved app is SAFE tier.
        // 4. Launch it.
        return try {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            // 5. Report the actual result - we only reach here if startActivity didn't throw.
            ToolResult.Success("Opened $requestedName.")
        } catch (e: Exception) {
            ToolResult.Error("Tried to open \"$requestedName\" but it failed: ${e.message ?: "unknown error"}")
        }
    }
}
