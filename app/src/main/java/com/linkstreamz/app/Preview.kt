package com.linkstreamz.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

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
            settings = SettingsUiState(autoplay = false, startMuted = true),
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
                .background(Color.Black),
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
                onFloatWindow = {},
                onInteraction = {},
                playerBrightness = 100f,
                onPlayerBrightnessChange = {},
            )
        }
    }
}

// ── 12-server scroll preview — confirms the list scrolls and extends past the player ──

@Preview(
    name = "Servers Menu – 12 servers (scroll test)",
    showBackground = true,
    showSystemUi = true,
    widthDp = 390,
    heightDp = 844, // iPhone-like portrait canvas; player sits at the top
    backgroundColor = 0xFF000000,
)
@Composable
fun ServersMenuScrollPreview() {
    val manyServers = (1..12).map { i ->
        StreamServer(
            name = "Server $i",
            url  = "https://example.com/s$i.m3u8",
            quality = when (i % 3) { 0 -> "HD"; 1 -> "SD"; else -> null },
        )
    }
    val state = rememberPlayerControlsState(
        isPlaying    = false,
        servers      = manyServers,
        selectedServerIndex = 2, // Server 3 is active
    )
    // Open the servers menu immediately so the preview shows it
    state.openMenu = OpenMenu.Servers

    LinkStreamzTheme {
        androidx.compose.foundation.layout.Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF000000)),
        ) {
            // Simulate the video player area at the top (dark rectangle)
            androidx.compose.foundation.layout.Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color(0xFF1A1A1A)),
            ) {
                PlayerOverlay(
                    state = state,
                    visible = true,
                    onPlayPause = {},
                    onRewind = {},
                    onForward = {},
                    onSeek = {},
                    onServerSelect = { state.selectedServerIndex = it; state.closeMenus() },
                    onVolumeChange = {},
                    onFullscreenToggle = {},
                onFloatWindow = {},
                    onInteraction = {},
                    playerBrightness = 100f,
                    onPlayerBrightnessChange = {},
                    modifier = Modifier.matchParentSize(),
                )
            }

            // Simulate "More events" content below the player
            androidx.compose.foundation.layout.Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = (390 * 9 / 16).dp + 8.dp, start = 16.dp, end = 16.dp),
            ) {
                repeat(5) { i ->
                    androidx.compose.foundation.layout.Box(
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(vertical = 4.dp)
                            .background(Color(0xFF1C1C1C), androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
                    )
                }
            }
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
    LinkStreamzTheme {
        SettingsScreenContent(
            uiState = SettingsUiState(),
            onToggleAutoplay = {},
            onToggleStartMuted = {},
            onSetSkipLength = {},
            onSetHideControls = {},
            onToggleKeepScreenOn = {},
            onSetStreamDelay = {},
            onRefresh = {},
            onReset = {},
            onCustomStream = { _, _ -> },
            onCustomUrlChange = {},
            onCustomAudioUrlChange = {},
        )
    }
}