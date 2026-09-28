package com.linkstreamz.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview

@Preview(
    name = "Link Streamz Home",
    showBackground = true,
    showSystemUi = true,
    widthDp = 440,
    heightDp = 900,
)
@Composable
fun LinkStreamzHomePreview() {
    LinkStreamzTheme {
        HomeScreen(
            repository = remember { EventsRepository(preview = true) },
            onSettings = {},
            onPlayer = {},
        )
    }
}

@Preview(
    name = "Link Streamz Player",
    showBackground = true,
    showSystemUi = true,
    widthDp = 440,
    heightDp = 900,
)
@Composable
fun LinkStreamzPlayerPreview() {
    val servers = listOf(
        StreamServer("Server 1", "https://example.com/preview.m3u8", "HD"),
        StreamServer("Server 2", "https://example.com/preview2.m3u8", "SD"),
        StreamServer("Server 3", "https://example.com/preview3.m3u8"),
    )
    val event = Event(
        id = "preview",
        sport = "Football",
        league = "MLS",
        status = "live",
        startMs = System.currentTimeMillis() - 67 * 60_000L,
        at = "",
        home = Team("Columbus", "b1", "CC"),
        away = Team("Inter Miami", "b2", "IM"),
        servers = servers,
    )
    LinkStreamzTheme {
        PlayerScreen(
            target = EventTarget(event, servers.first()),
            settings = PlaybackSettings(autoplay = false, muted = true),
            onBack = {},
            previewMode = true,
        )
    }
}

// ── Isolated overlay preview (no real player needed) ──────────────────────────

@Preview(
    name = "Player Overlay Controls",
    showBackground = true,
    widthDp = 360,
    heightDp = 203, // 16:9 of 360dp
    backgroundColor = 0xFF000000,
)
@Composable
fun PlayerOverlayPreview() {
    val sampleServers = listOf(
        StreamServer("Server 1", "https://example.com/s1.m3u8", "HD"),
        StreamServer("Server 2", "https://example.com/s2.m3u8", "SD"),
        StreamServer("Server 3", "https://example.com/s3.m3u8"),
    )
    val state = rememberPlayerControlsState(
        isPlaying = true,
        position = 2_430_000L,   // 40:30
        duration  = 5_400_000L,  // 90:00
        volume    = 0.8f,
        isFullscreen = false,
        servers   = sampleServers,
        selectedServerIndex = 0,
    )
    LinkStreamzTheme {
        androidx.compose.foundation.layout.Box(
            Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black),
        ) {
            PlayerOverlay(
                state = state,
                visible = true,
                onPlayPause = {},
                onRewind = {},
                onForward = {},
                onSeek = {},
                onServerSelect = {},
                onVolumeChange = {},
                onFullscreenToggle = {},
                onInteraction = {},
            )
        }
    }
}


@Preview(
    name = "Link Streamz Settings",
    showBackground = true,
    showSystemUi = true,
    widthDp = 440,
    heightDp = 900,
)
@Composable
fun LinkStreamzSettingsPreview() {
    val context = LocalContext.current
    LinkStreamzTheme {
        SettingsScreen(
            settings = PlaybackSettings(),
            settingsRepository = remember { SettingsRepository(context) },
            scope = rememberCoroutineScope(),
            onBack = {},
            onRefresh = {},
            onCustomStream = {},
        )
    }
}