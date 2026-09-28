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
    val event = Event(
        id = "preview",
        sport = "Football",
        league = "MLS",
        status = "live",
        startMs = System.currentTimeMillis() - 67 * 60_000L,
        at = "",
        home = Team("Columbus", "b1", "CC"),
        away = Team("Inter Miami", "b2", "IM"),
        servers = listOf(StreamServer("Server 1", "https://example.com/preview.m3u8", "HD")),
    )
    LinkStreamzTheme {
        PlayerScreen(
            target = EventTarget(event, event.servers.first()),
            settings = PlaybackSettings(autoplay = false, muted = true),
            onBack = {},
            previewMode = true,
        )
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