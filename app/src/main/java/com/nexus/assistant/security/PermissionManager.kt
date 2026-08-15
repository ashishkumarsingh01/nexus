package com.nexus.assistant.security

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Every tool call in NEXUS must go through this before touching Android
 * APIs. This class only answers "is X granted right now" — it never itself
 * shows the OS permission dialog (that requires an Activity), and it never
 * lets a tool bypass a check because the tool "seemed safe".
 */
class PermissionManager(private val context: Context) {

    fun isGranted(androidPermission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, androidPermission) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun allGranted(androidPermissions: List<String>): Boolean {
        if (androidPermissions.isEmpty()) return true
        return androidPermissions.all { isGranted(it) }
    }

    fun missing(androidPermissions: List<String>): List<String> {
        return androidPermissions.filterNot { isGranted(it) }
    }
}
