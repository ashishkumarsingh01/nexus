package com.nexus.assistant.tools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.SmsManager
import com.nexus.assistant.security.PermissionLevel
import com.nexus.assistant.security.PermissionManager

/**
 * Every action here is user-facing and irreversible in some way (a sent
 * message can't be unsent, a call can't be un-made), so every action
 * requires BOTH the relevant Android permission AND explicit in-app
 * confirmation - never just one or the other.
 */
class CommunicationTool(
    private val context: Context,
    private val permissionManager: PermissionManager
) : Tool {

    override val name = "communication_tool"
    override val description = "Looks up contacts, and sends messages or places calls after explicit confirmation."
    override val permissionLevel = PermissionLevel.DANGEROUS

    override suspend fun execute(params: Map<String, String>, confirmed: Boolean): ToolResult {
        val action = params["action"] ?: return ToolResult.Error("communication_tool called without an 'action' parameter.")
        return when (action) {
            "find_contact" -> findContact(params["name"].orEmpty())
            "send_message" -> sendMessage(params["name"].orEmpty(), params["message"].orEmpty(), confirmed)
            "make_call" -> makeCall(params["name"].orEmpty(), confirmed)
            else -> ToolResult.Error("Unknown communication_tool action: \"$action\".")
        }
    }

    private fun findContact(name: String): ToolResult {
        if (name.isBlank()) return ToolResult.Error("No contact name given.")
        if (!permissionManager.isGranted(Manifest.permission.READ_CONTACTS)) {
            return ToolResult.PermissionRequired(
                listOf(Manifest.permission.READ_CONTACTS),
                "NEXUS needs contacts permission to look up \"$name\"."
            )
        }
        val number = lookupNumber(name) ?: return ToolResult.Error("No contact found matching \"$name\".")
        return ToolResult.Success("Found $name: $number", data = number)
    }

    private fun sendMessage(name: String, message: String, confirmed: Boolean): ToolResult {
        if (name.isBlank() || message.isBlank()) return ToolResult.Error("Need both a contact name and a message.")

        if (!permissionManager.allGranted(listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.SEND_SMS))) {
            return ToolResult.PermissionRequired(
                listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.SEND_SMS),
                "NEXUS needs contacts and SMS permission to message $name."
            )
        }

        val number = lookupNumber(name) ?: return ToolResult.Error("No contact found matching \"$name\".")

        if (!confirmed) {
            return ToolResult.NeedsConfirmation(
                "I've prepared the message for $name: \"$message\". Do you want me to send it?"
            )
        }

        return try {
            val smsManager = context.getSystemService(SmsManager::class.java)
            smsManager.sendTextMessage(number, null, message, null, null)
            ToolResult.Success("Message sent to $name.")
        } catch (e: Exception) {
            ToolResult.Error("Failed to send the message to $name: ${e.message ?: "unknown error"}. Nothing was sent.")
        }
    }

    private fun makeCall(name: String, confirmed: Boolean): ToolResult {
        if (name.isBlank()) return ToolResult.Error("No contact name given.")

        if (!permissionManager.allGranted(listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE))) {
            return ToolResult.PermissionRequired(
                listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE),
                "NEXUS needs contacts and phone permission to call $name."
            )
        }

        val number = lookupNumber(name) ?: return ToolResult.Error("No contact found matching \"$name\".")

        if (!confirmed) {
            return ToolResult.NeedsConfirmation("This will call $name ($number). Do you want me to continue?")
        }

        return try {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolResult.Success("Calling $name.")
        } catch (e: Exception) {
            ToolResult.Error("Failed to place the call to $name: ${e.message ?: "unknown error"}.")
        }
    }

    private fun lookupNumber(name: String): String? {
        val resolver = context.contentResolver
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$name%")

        resolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (idx >= 0) return cursor.getString(idx)
            }
        }
        return null
    }
}
