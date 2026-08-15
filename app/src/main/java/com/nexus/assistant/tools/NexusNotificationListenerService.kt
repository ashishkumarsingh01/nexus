package com.nexus.assistant.tools

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NexusNotification(
    val appLabel: String,
    val title: String,
    val text: String,
    val postTimeMillis: Long
)

/**
 * Only holds data in memory, for the current process lifetime. Nothing here
 * is written to disk or sent anywhere - NotificationTool reads this cache,
 * nothing else does.
 */
class NexusNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        refreshCache()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (instance === this) instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        refreshCache()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        refreshCache()
    }

    private fun refreshCache() {
        val list = try {
            activeNotifications?.mapNotNull { sbn -> toNexusNotification(sbn) } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        _notifications.value = list
    }

    private fun toNexusNotification(sbn: StatusBarNotification): NexusNotification? {
        val extras = sbn.notification?.extras ?: return null
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return null
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val appLabel = try {
            val pm = packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            sbn.packageName
        }
        return NexusNotification(appLabel = appLabel, title = title, text = text, postTimeMillis = sbn.postTime)
    }

    companion object {
        private var instance: NexusNotificationListenerService? = null

        private val _notifications = MutableStateFlow<List<NexusNotification>>(emptyList())
        val notifications: StateFlow<List<NexusNotification>> = _notifications.asStateFlow()

        fun isRunning(): Boolean = instance != null
    }
}
