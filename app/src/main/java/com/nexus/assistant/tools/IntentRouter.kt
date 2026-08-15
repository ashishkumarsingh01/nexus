package com.nexus.assistant.tools

/**
 * PHASE 5/6 intent detection: keyword-based, not a learned classifier or
 * model-driven function call. This is an intentional, documented
 * simplification — MediaPipe's on-device LlmInference API (Phase 2) is
 * plain text-in/text-out with no native structured tool-calling support, so
 * routing "what's my battery?" to DeviceTool.battery_status is done with
 * pattern matching here rather than asking the model to decide. This keeps
 * tool invocation deterministic and auditable, at the cost of only
 * recognizing phrasings we've explicitly listed.
 *
 * Extended in later phases (FileTool, AppTool, ...) by adding more rules,
 * not by changing this mechanism.
 */
object IntentRouter {

    data class ToolIntent(val toolName: String, val params: Map<String, String>)

    private data class Rule(val keywords: List<String>, val toolName: String, val action: String)

    private val rules = listOf(
        Rule(listOf("battery"), "device_tool", "battery_status"),
        Rule(listOf("storage", "disk space", "how much space"), "device_tool", "storage_info"),
        Rule(listOf("what time", "current time", "time is it"), "device_tool", "current_time"),
        Rule(listOf("what date", "today's date", "what day is it", "what's the date"), "device_tool", "current_date"),
        Rule(listOf("device info", "what phone", "device information", "what device"), "device_tool", "device_info"),
        Rule(listOf("volume"), "device_tool", "volume_info"),
        Rule(listOf("network status", "am i online", "internet connection", "connected to"), "device_tool", "network_status"),
        Rule(listOf("available memory", "how much ram", "free memory"), "device_tool", "available_memory")
    )

    // File and app actions need an extracted argument (a file name, a query,
    // an app name), so they use regex capture groups rather than plain
    // keyword containment.
    private val findFilesPattern = Regex("""find (?:my )?(.+?)(?: files?| notes?)?$""", RegexOption.IGNORE_CASE)
    private val deleteFilePattern = Regex("""delete (.+)$""", RegexOption.IGNORE_CASE)
    private val openAppPattern = Regex("""open (.+)$""", RegexOption.IGNORE_CASE)
    private val launchAppPattern = Regex("""launch (.+)$""", RegexOption.IGNORE_CASE)
    private val weatherPattern = Regex("""weather (?:in|for) (.+)$""", RegexOption.IGNORE_CASE)

    fun detect(rawText: String): ToolIntent? {
        val text = rawText.lowercase().trim()

        deleteFilePattern.find(text)?.let {
            return ToolIntent("file_tool", mapOf("action" to "delete", "name" to it.groupValues[1].trim()))
        }
        openAppPattern.find(text)?.let {
            return ToolIntent("app_tool", mapOf("action" to "open", "appName" to it.groupValues[1].trim()))
        }
        launchAppPattern.find(text)?.let {
            return ToolIntent("app_tool", mapOf("action" to "open", "appName" to it.groupValues[1].trim()))
        }
        weatherPattern.find(text)?.let {
            return ToolIntent("online_tool", mapOf("action" to "weather", "city" to it.groupValues[1].trim()))
        }
        for (rule in rules) {
            if (rule.keywords.any { text.contains(it) }) {
                return ToolIntent(rule.toolName, mapOf("action" to rule.action))
            }
        }
        findFilesPattern.find(text)?.let {
            val query = it.groupValues[1].trim()
            if (query.isNotEmpty()) return ToolIntent("file_tool", mapOf("action" to "search", "query" to query))
        }
        return null
    }
}
