package com.nexus.assistant.tools

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.StatFs
import android.os.Environment
import com.nexus.assistant.security.PermissionLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * All operations here are SAFE tier: no dangerous Android permission, no
 * user confirmation needed, matching the spec's SAFE classification for
 * "get battery / get storage / get time / etc."
 *
 * One Tool instance intentionally exposes several related actions via the
 * `action` param, rather than one Tool class per getter — keeps ToolRouter's
 * registry from needing a dozen near-identical single-purpose classes.
 */
class DeviceTool(private val context: Context) : Tool {

    override val name = "device_tool"
    override val description = "Reads device status: battery, storage, time, date, volume, network, device info."
    override val permissionLevel = PermissionLevel.SAFE

    override suspend fun execute(params: Map<String, String>, confirmed: Boolean): ToolResult {
        val action = params["action"] ?: return ToolResult.Error("device_tool called without an 'action' parameter.")
        return try {
            when (action) {
                "battery_status" -> getBatteryStatus()
                "storage_info" -> getStorageInfo()
                "device_info" -> getDeviceInfo()
                "current_time" -> getCurrentTime()
                "current_date" -> getCurrentDate()
                "volume_info" -> getVolumeInfo()
                "network_status" -> getNetworkStatus()
                "available_memory" -> getAvailableMemory()
                else -> ToolResult.Error("Unknown device_tool action: \"$action\".")
            }
        } catch (e: Exception) {
            ToolResult.Error("Couldn't read device status: ${e.message ?: "unknown error"}")
        }
    }

    private fun getBatteryStatus(): ToolResult {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return ToolResult.Error("Battery status is not available right now.")
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return ToolResult.Error("Battery level could not be read.")
        val pct = (level * 100) / scale

        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        return ToolResult.Success(
            message = "$pct% battery${if (isCharging) ", charging" else ""}",
            data = "$pct"
        )
    }

    private fun getStorageInfo(): ToolResult {
        val stat = StatFs(Environment.getDataDirectory().path)
        val totalBytes = stat.totalBytes
        val freeBytes = stat.availableBytes
        val totalGb = totalBytes / (1024.0 * 1024 * 1024)
        val freeGb = freeBytes / (1024.0 * 1024 * 1024)
        return ToolResult.Success(
            message = "%.1f GB free of %.1f GB".format(freeGb, totalGb)
        )
    }

    private fun getDeviceInfo(): ToolResult {
        val info = "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        return ToolResult.Success(message = info)
    }

    private fun getCurrentTime(): ToolResult {
        val fmt = SimpleDateFormat("h:mm a", Locale.getDefault())
        return ToolResult.Success(message = fmt.format(Date()))
    }

    private fun getCurrentDate(): ToolResult {
        val fmt = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
        return ToolResult.Success(message = fmt.format(Date()))
    }

    private fun getVolumeInfo(): ToolResult {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val pct = if (max > 0) (current * 100) / max else 0
        return ToolResult.Success(message = "Media volume: $pct%")
    }

    private fun getNetworkStatus(): ToolResult {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        val connected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val type = when {
            caps == null -> "none"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile data"
            else -> "other"
        }
        return ToolResult.Success(
            message = if (connected) "Connected via $type" else "No network connection",
            data = if (connected) "online" else "offline"
        )
    }

    private fun getAvailableMemory(): ToolResult {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val availMb = memInfo.availMem / (1024 * 1024)
        val totalMb = memInfo.totalMem / (1024 * 1024)
        return ToolResult.Success(message = "$availMb MB free of $totalMb MB RAM")
    }
}
