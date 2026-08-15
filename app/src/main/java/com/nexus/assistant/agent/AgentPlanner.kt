package com.nexus.assistant.agent

import com.nexus.assistant.ai.AIResponse
import com.nexus.assistant.ai.LocalAIEngine
import com.nexus.assistant.tools.ToolResult
import com.nexus.assistant.tools.ToolRouter

/**
 * PHASE 11: multi-step task execution. Deliberately narrow in this phase —
 * one concrete workflow (find notes -> summarize -> save summary), matching
 * the spec's worked example exactly, rather than a general-purpose planner.
 * A general planner would need the model to reliably emit structured plans,
 * which isn't something MediaPipe's plain-text LlmInference API (Phase 2)
 * supports without a lot of unverified prompt-engineering risk. This
 * concrete version is honest about what it can do and recovers per-step
 * rather than failing the whole task silently.
 */
class AgentPlanner(
    private val toolRouter: ToolRouter,
    private val aiEngine: LocalAIEngine
) {
    private val summarizeTaskPattern = Regex(
        """find (?:my )?(.+?) (?:notes?|files?) and (?:create|make|write) a? ?(?:revision )?summary""",
        RegexOption.IGNORE_CASE
    )
    private val summarizeShortPattern = Regex(
        """summarize (?:my )?(.+?) notes?""",
        RegexOption.IGNORE_CASE
    )

    fun canHandle(text: String): Boolean {
        val t = text.trim()
        return summarizeTaskPattern.containsMatchIn(t) || summarizeShortPattern.containsMatchIn(t)
    }

    /**
     * Runs the "find notes -> summarize -> save" workflow. [onStatus] is
     * called with short status strings for the UI's tool-activity chip
     * ("Searching local files…", etc.) — never internal reasoning, just
     * what's actually happening.
     */
    suspend fun run(text: String, onStatus: (String) -> Unit): String {
        val match = summarizeTaskPattern.find(text) ?: summarizeShortPattern.find(text)
        val topic = match?.groupValues?.get(1)?.trim()
        if (topic.isNullOrEmpty()) {
            return "I understood you want a summary, but couldn't tell what topic. Try: \"Find my Java notes and create a revision summary.\""
        }

        // Step 1: search
        onStatus("Searching local files…")
        val searchResult = toolRouter.route("file_tool", mapOf("action" to "search", "query" to topic))
        if (searchResult !is ToolResult.Success) {
            return "I couldn't search for \"$topic\" notes: ${(searchResult as? ToolResult.Error)?.message ?: "unknown error"}"
        }
        if (searchResult.message.startsWith("No files found")) {
            return "I looked for files matching \"$topic\" but didn't find any. Nothing was created."
        }

        // Step 2: identify relevant files (parse the "• name" lines back out, cap at 3)
        val fileNames = searchResult.message.lines()
            .filter { it.trim().startsWith("•") }
            .map { it.trim().removePrefix("•").trim() }
            .take(3)

        if (fileNames.isEmpty()) {
            return "I found a match for \"$topic\" but couldn't parse the file list. Nothing was created."
        }

        // Step 3: read authorized content, tolerating individual failures
        onStatus("Reading files…")
        val contents = mutableListOf<String>()
        val failedFiles = mutableListOf<String>()
        for (fileName in fileNames) {
            when (val readResult = toolRouter.route("file_tool", mapOf("action" to "read", "name" to fileName))) {
                is ToolResult.Success -> readResult.data?.let { contents.add("--- $fileName ---\n$it") }
                else -> failedFiles.add(fileName)
            }
        }

        if (contents.isEmpty()) {
            return "I found files matching \"$topic\" but couldn't read any of them (${failedFiles.joinToString(", ")}). Nothing was created."
        }

        // Step 4/5: summarize via the local model
        onStatus("Generating summary…")
        val combined = contents.joinToString("\n\n").take(6000) // keep prompt bounded
        val prompt = "Summarize the following notes into a concise revision summary with key points as bullets:\n\n$combined"
        val summaryResponse = aiEngine.generateStandalone(prompt)
        val summaryText = when (summaryResponse) {
            is AIResponse.Success -> summaryResponse.text
            is AIResponse.Unavailable -> return "I read the notes but couldn't generate a summary: ${summaryResponse.reason}"
        }

        // Step 6: save the summary as a new local file
        onStatus("Creating summary file…")
        val summaryFileName = "${topic.replace(" ", "_")}_revision_summary.txt"
        val createResult = toolRouter.route(
            "file_tool",
            mapOf("action" to "create", "name" to summaryFileName, "content" to summaryText)
        )

        // Step 7: report the real outcome
        val failNote = if (failedFiles.isNotEmpty()) " (couldn't read: ${failedFiles.joinToString(", ")})" else ""
        return when (createResult) {
            is ToolResult.Success -> "Done. I summarized ${contents.size} file(s)$failNote and saved it as \"$summaryFileName\".\n\n$summaryText"
            else -> "I generated the summary but couldn't save it as a file: ${(createResult as? ToolResult.Error)?.message}. Here it is anyway:\n\n$summaryText"
        }
    }
}
