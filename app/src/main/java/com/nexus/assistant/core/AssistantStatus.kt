package com.nexus.assistant.core

/**
 * State of the local AI model. Populated for real by ModelManager in Phase 2.
 * The UI must always reflect this truthfully — NEXUS never pretends to be
 * ready when it is not (see project requirement: "never claim an action
 * succeeded when it did not").
 */
enum class ModelStatus {
    NOT_LOADED,       // no model file present / not yet loaded into memory
    LOADING,
    READY,
    UNAVAILABLE        // load failed, corrupted, insufficient memory, etc.
}

/**
 * Network reachability only. This does NOT mean NEXUS is using the network —
 * online mode is a separate, user-controlled toggle (Phase 12). It's shown in
 * the UI purely as an indicator.
 */
enum class NetworkStatus {
    ONLINE,
    OFFLINE
}

data class AssistantState(
    val modelStatus: ModelStatus = ModelStatus.NOT_LOADED,
    val networkStatus: NetworkStatus = NetworkStatus.OFFLINE,
    val onlineModeEnabled: Boolean = false,
    val currentActivity: String? = null // e.g. "Searching local files…" shown as a tool-activity chip
)
