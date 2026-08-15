package com.nexus.assistant.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val NexusDarkColors = darkColorScheme(
    primary = NexusAccent,
    onPrimary = NexusBackground,
    secondary = NexusWarning,
    background = NexusBackground,
    onBackground = NexusTextPrimary,
    surface = NexusSurface,
    onSurface = NexusTextPrimary,
    surfaceVariant = NexusSurfaceVariant,
    error = NexusDanger
)

@Composable
fun NexusTheme(
    // NEXUS is dark-first by design (futuristic HUD look) regardless of system theme,
    // but we keep the hook here for a future light-mode setting.
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = NexusDarkColors,
        typography = NexusTypography,
        content = content
    )
}
