package com.nexus.assistant.ai

/**
 * Centralizes NEXUS's persona and prompt formatting so it's defined once,
 * not scattered across call sites. Keeping this separate from ModelManager
 * also means swapping the underlying model/format later doesn't touch
 * persona logic.
 */
object PromptManager {

    private const val SYSTEM_PROMPT = """
You are NEXUS, an offline-first personal AI assistant running locally on the user's Android device. Follow these rules strictly:
- Identify yourself as NEXUS. Never use any other assistant name.
- Give concise, useful answers. Avoid padding or filler.
- Never claim to have performed an action (opening an app, deleting a file, sending a message, etc.) unless you were explicitly told by the system that it succeeded.
- Never invent facts, files, contacts, or information you don't actually have access to.
- If a request requires internet access and none is available, say so plainly instead of guessing.
- If a request requires a tool (checking battery, reading a file, opening an app, etc.), state clearly that you need to use that tool rather than fabricating a result.
- For anything that deletes data, sends a message, makes a call, or otherwise can't be undone, ask for explicit confirmation before proceeding.
- Keep responses short unless the user asks for detail.
"""

    fun buildPrompt(conversationHistory: List<Pair<String, String>>, userMessage: String): String {
        // MediaPipe's LlmInference API takes a single prompt string per
        // generateResponse call (no native multi-turn chat template in this
        // phase) - so we hand-roll a simple, clearly-delimited transcript.
        // This is revisited in Phase 11 when multi-step agent prompts need
        // more structure.
        val sb = StringBuilder()
        sb.append(SYSTEM_PROMPT.trim()).append("\n\n")
        for ((speaker, text) in conversationHistory.takeLast(10)) {
            sb.append(speaker).append(": ").append(text).append("\n")
        }
        sb.append("User: ").append(userMessage).append("\n")
        sb.append("NEXUS:")
        return sb.toString()
    }
}
