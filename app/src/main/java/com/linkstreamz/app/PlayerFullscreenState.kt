package com.linkstreamz.app

import androidx.compose.runtime.mutableStateOf

/**
 * Simple singleton that bridges fullscreen state from PlayerScreen to MainActivity.
 * PlayerScreen writes isFullscreen here; MainActivity reads it to hide the Logs FAB.
 */
object PlayerFullscreenState {
    val isFullscreen = mutableStateOf(false)
}
