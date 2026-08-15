package com.nexus.assistant.tools

import android.content.Context
import android.net.Uri

/**
 * NEXUS only ever accesses files inside a folder tree the user explicitly
 * picked via Android's document tree picker (ACTION_OPEN_DOCUMENT_TREE).
 * There is no broad storage permission and no access outside this tree.
 */
class FileAccessSettings(context: Context) {
    private val prefs = context.getSharedPreferences("nexus_file_access", Context.MODE_PRIVATE)

    var grantedTreeUri: Uri?
        get() = prefs.getString(KEY_TREE_URI, null)?.let { Uri.parse(it) }
        set(value) {
            prefs.edit().putString(KEY_TREE_URI, value?.toString()).apply()
        }

    companion object {
        private const val KEY_TREE_URI = "granted_tree_uri"
    }
}
