package com.nexus.assistant.ui

enum class Sender { USER, NEXUS, SYSTEM }

data class ChatMessage(
    val id: Long = System.nanoTime(),
    val sender: Sender,
    val text: String,
    val timestampMillis: Long = System.currentTimeMillis()
)
