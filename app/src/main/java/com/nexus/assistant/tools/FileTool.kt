package com.nexus.assistant.tools

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.nexus.assistant.security.PermissionLevel

/**
 * All paths are relative to the user-granted folder tree (see
 * FileAccessSettings). "old_notes.pdf" means "a file named old_notes.pdf
 * somewhere under the granted tree", found by recursive search, not an
 * absolute filesystem path — NEXUS has no access outside that tree.
 */
class FileTool(
    private val context: Context,
    private val accessSettings: FileAccessSettings
) : Tool {

    override val name = "file_tool"
    override val description = "Search, list, read, create, rename, copy, move, and delete files within the folder NEXUS was granted access to."
    // Base tier is SENSITIVE (reading arbitrary file contents); the delete
    // action specifically escalates to DANGEROUS and requires confirmation,
    // enforced below rather than via a single fixed permissionLevel, since
    // this Tool bundles multiple actions of different risk.
    override val permissionLevel = PermissionLevel.SENSITIVE

    private fun rootDoc(): DocumentFile? {
        val uri = accessSettings.grantedTreeUri ?: return null
        return DocumentFile.fromTreeUri(context, uri)
    }

    override suspend fun execute(params: Map<String, String>, confirmed: Boolean): ToolResult {
        val root = rootDoc()
            ?: return ToolResult.Error("NEXUS hasn't been granted access to a folder yet. Grant one in Settings → File Access.")

        val action = params["action"] ?: return ToolResult.Error("file_tool called without an 'action' parameter.")

        return try {
            when (action) {
                "search" -> search(root, params["query"].orEmpty())
                "list" -> list(root, params["path"])
                "read" -> read(root, params["name"].orEmpty())
                "create" -> create(root, params["name"].orEmpty(), params["content"].orEmpty())
                "rename" -> rename(root, params["name"].orEmpty(), params["newName"].orEmpty())
                "copy" -> copy(root, params["name"].orEmpty(), params["newName"].orEmpty())
                "move" -> move(root, params["name"].orEmpty(), params["destFolder"].orEmpty())
                "delete" -> delete(root, params["name"].orEmpty(), confirmed)
                else -> ToolResult.Error("Unknown file_tool action: \"$action\".")
            }
        } catch (e: Exception) {
            ToolResult.Error("File operation failed: ${e.message ?: "unknown error"}")
        }
    }

    private fun findByName(root: DocumentFile, name: String): DocumentFile? {
        fun walk(dir: DocumentFile): DocumentFile? {
            for (child in dir.listFiles()) {
                if (child.isDirectory) {
                    walk(child)?.let { return it }
                } else if (child.name.equals(name, ignoreCase = true)) {
                    return child
                }
            }
            return null
        }
        return walk(root)
    }

    private fun search(root: DocumentFile, query: String): ToolResult {
        if (query.isBlank()) return ToolResult.Error("No search term given.")
        val matches = mutableListOf<String>()
        fun walk(dir: DocumentFile) {
            for (child in dir.listFiles()) {
                if (child.isDirectory) walk(child)
                else if (child.name?.contains(query, ignoreCase = true) == true) matches.add(child.name ?: "?")
            }
        }
        walk(root)
        return if (matches.isEmpty()) {
            ToolResult.Success("No files found matching \"$query\".")
        } else {
            ToolResult.Success("Found ${matches.size} file(s):\n" + matches.joinToString("\n") { "• $it" })
        }
    }

    private fun list(root: DocumentFile, path: String?): ToolResult {
        val target = if (path.isNullOrBlank()) root else findFolder(root, path) ?: return ToolResult.Error("Folder \"$path\" not found.")
        val entries = target.listFiles().map { (if (it.isDirectory) "📁 " else "📄 ") + it.name }
        return if (entries.isEmpty()) ToolResult.Success("This folder is empty.")
        else ToolResult.Success(entries.joinToString("\n"))
    }

    private fun findFolder(root: DocumentFile, path: String): DocumentFile? {
        var current = root
        for (segment in path.split("/").filter { it.isNotBlank() }) {
            current = current.listFiles().firstOrNull { it.isDirectory && it.name == segment } ?: return null
        }
        return current
    }

    private fun read(root: DocumentFile, name: String): ToolResult {
        val file = findByName(root, name) ?: return ToolResult.Error("File \"$name\" not found in the granted folder.")
        val text = context.contentResolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() }
            ?: return ToolResult.Error("Couldn't open \"$name\".")
        val truncated = if (text.length > 4000) text.take(4000) + "\n…(truncated)" else text
        return ToolResult.Success(message = "Read \"$name\" (${text.length} chars).", data = truncated)
    }

    private fun create(root: DocumentFile, name: String, content: String): ToolResult {
        if (name.isBlank()) return ToolResult.Error("No file name given.")
        if (root.findFile(name) != null) return ToolResult.Error("A file named \"$name\" already exists.")
        val newFile = root.createFile("text/plain", name) ?: return ToolResult.Error("Couldn't create \"$name\".")
        context.contentResolver.openOutputStream(newFile.uri)?.use { it.write(content.toByteArray()) }
        return ToolResult.Success("Created \"$name\".")
    }

    private fun rename(root: DocumentFile, name: String, newName: String): ToolResult {
        val file = findByName(root, name) ?: return ToolResult.Error("File \"$name\" not found.")
        return if (file.renameTo(newName)) ToolResult.Success("Renamed \"$name\" to \"$newName\".")
        else ToolResult.Error("Couldn't rename \"$name\".")
    }

    private fun copy(root: DocumentFile, name: String, newName: String): ToolResult {
        val file = findByName(root, name) ?: return ToolResult.Error("File \"$name\" not found.")
        val mime = file.type ?: "application/octet-stream"
        val copyName = newName.ifBlank { "copy_of_$name" }
        val newFile = root.createFile(mime, copyName) ?: return ToolResult.Error("Couldn't create copy.")
        context.contentResolver.openInputStream(file.uri)?.use { input ->
            context.contentResolver.openOutputStream(newFile.uri)?.use { output -> input.copyTo(output) }
        }
        return ToolResult.Success("Copied \"$name\" to \"$copyName\".")
    }

    private fun move(root: DocumentFile, name: String, destFolder: String): ToolResult {
        // DocumentFile has no direct cross-directory move on all providers;
        // implemented as copy-to-destination + delete-original.
        val file = findByName(root, name) ?: return ToolResult.Error("File \"$name\" not found.")
        val dest = findFolder(root, destFolder) ?: return ToolResult.Error("Destination folder \"$destFolder\" not found.")
        val mime = file.type ?: "application/octet-stream"
        val newFile = dest.createFile(mime, name) ?: return ToolResult.Error("Couldn't move \"$name\".")
        context.contentResolver.openInputStream(file.uri)?.use { input ->
            context.contentResolver.openOutputStream(newFile.uri)?.use { output -> input.copyTo(output) }
        }
        file.delete()
        return ToolResult.Success("Moved \"$name\" to \"$destFolder\".")
    }

    private fun delete(root: DocumentFile, name: String, confirmed: Boolean): ToolResult {
        if (name.isBlank()) return ToolResult.Error("No file name given.")
        val file = findByName(root, name) ?: return ToolResult.Error("File \"$name\" not found.")
        if (!confirmed) {
            return ToolResult.NeedsConfirmation("This will permanently delete \"$name\". Do you want me to continue?")
        }
        return if (file.delete()) ToolResult.Success("Deleted \"$name\".")
        else ToolResult.Error("Couldn't delete \"$name\".")
    }
}
