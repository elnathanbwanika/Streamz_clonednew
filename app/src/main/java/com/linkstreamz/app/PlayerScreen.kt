package com.linkstreamz.app

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.PowerManager
import android.view.View
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

sealed interface PlayerTarget {
    val servers: List<StreamServer>
    val selectedServer: StreamServer?
    val title: String
    val subtitle: String
    fun withServer(server: StreamServer): PlayerTarget
}

data class EventTarget(
    val event: Event,
    override val selectedServer: StreamServer? = null,
) : PlayerTarget {
    override val servers: List<StreamServer> = event.servers
    override val title: String = "${event.home.name} vs ${event.away.name}"
    override val subtitle: String = listOf(event.sport, event.league).filter { it.isNotBlank() }.joinToString(" | ")
    override fun withServer(server: StreamServer) = copy(selectedServer = server)
}

data class HighlightTarget(
    val highlight: Highlight,
    override val selectedServer: StreamServer? = null,
) : PlayerTarget {
    override val servers: List<StreamServer> = highlight.servers
    override val title: String = highlight.title
    override val subtitle: String = if (highlight.home != null && highlight.away != null) "${highlight.home.name} vs ${highlight.away.name}" else "Highlight"
    override fun withServer(server: StreamServer) = copy(selectedServer = server)
}

data class CustomTarget(
    val server: StreamServer,
    override val selectedServer: StreamServer? = server,
) : PlayerTarget {
    override val servers: List<StreamServer> = listOf(server)
    override val title: String = "Custom stream"
    override val subtitle: String = "Your stream"
    override fun withServer(server: StreamServer) = copy(server = server, selectedServer = server)
}

// Auto-hide delay for overlay controls (in milliseconds)
private const val CONTROLS_HIDE_DELAY_MS = 5_000L

@Composable
fun PlayerScreen(
    target: PlayerTarget,
    settings: PlaybackSettings,
    repository: EventsRepository? = null,
    onBack: () -> Unit,
    onTarget: (PlayerTarget) -> Unit = {},
    previewMode: Boolean = false,
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val server = target.selectedServer ?: target.servers.firstOrNull()
    var currentServer by remember(target, server) { mutableStateOf(server) }
    var locked by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var muted by remember(settings.muted) { mutableStateOf(settings.muted) }
    // Volume tracks player.volume [0f..1f]; starts at 0 if muted, else 1.
    var playerVolume by remember(settings.muted) { mutableFloatStateOf(if (settings.muted) 0f else 1f) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(C.TIME_UNSET) }
    var error by remember { mutableStateOf<String?>(null) }
    var serverMenu by remember { mutableStateOf(false) }
    var seekMessage by remember { mutableStateOf<String?>(null) }

    // Auto-hide controls state
    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteractionMs by remember { mutableStateOf(System.currentTimeMillis()) }

    // Overlay controls state (hoisted; shared between PlayerOverlay and player callbacks)
    val controlsState = rememberPlayerControlsState(
        isPlaying = playing,
        position = position,
        duration = duration,
        volume = playerVolume,
        servers = target.servers,
        selectedServerIndex = target.servers.indexOf(server).coerceAtLeast(0),
    )

    val player = remember { ExoPlayer.Builder(context).build() }
    val wakeLock = remember {
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "LinkStreamz:Playback",
        )
    }

    /** Resets the auto-hide timer — call whenever user interacts with the player. */
    fun touchControls() {
        controlsVisible = true
        lastInteractionMs = System.currentTimeMillis()
    }

    fun seekBy(deltaSeconds: Int, side: String? = null) {
        val seekable = duration != C.TIME_UNSET && duration > 0
        if (!seekable) {
            Toast.makeText(context, "Seeking isn't available on a live stream", Toast.LENGTH_SHORT).show()
            return
        }
        player.seekTo((player.currentPosition + deltaSeconds * 1000L).coerceIn(0L, duration))
        seekMessage = if (deltaSeconds < 0) "−${-deltaSeconds}s" else "+${deltaSeconds}s"
        touchControls()
    }

    fun loadStream(stream: StreamServer?) {
        if (stream == null) return
        error = null
        val mediaItem = MediaItem.fromUri(stream.url)
        val source = if (stream.url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)) {
            HlsMediaSource.Factory(DefaultHttpDataSource.Factory()).createMediaSource(mediaItem)
        } else {
            ProgressiveMediaSource.Factory(DefaultDataSource.Factory(context)).createMediaSource(mediaItem)
        }
        player.setMediaSource(source)
        player.prepare()
        player.volume = if (muted) 0f else 1f
        player.playWhenReady = settings.autoplay
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }
            override fun onPlayerError(playbackException: PlaybackException) {
                error = if (target.servers.size > 1) "This server isn't responding. Try again or switch to another server." else "Check your connection, then try again."
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(currentServer?.url, previewMode) {
        if (!previewMode) loadStream(currentServer)
    }
    LaunchedEffect(Unit) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration
            delay(250)
        }
    }
    LaunchedEffect(playing, settings.awake) {
        if (playing && settings.awake && !wakeLock.isHeld) wakeLock.acquire(settings.hide * 60_000L)
        else if (wakeLock.isHeld) wakeLock.release()
    }
    DisposableEffect(Unit) {
        onDispose { if (wakeLock.isHeld) wakeLock.release() }
    }

    // Auto-hide timer: poll every second and hide controls after CONTROLS_HIDE_DELAY_MS of inactivity
    LaunchedEffect(lastInteractionMs) {
        delay(CONTROLS_HIDE_DELAY_MS)
        controlsVisible = false
    }

    // Keep the hoisted controls state mirror in sync with reactive player state
    LaunchedEffect(playing)       { controlsState.isPlaying = playing }
    LaunchedEffect(position)      { controlsState.position  = position }
    LaunchedEffect(duration)      { controlsState.duration  = duration }
    LaunchedEffect(playerVolume)  { controlsState.volume    = playerVolume }
    LaunchedEffect(target.servers) {
        controlsState.servers = target.servers
        controlsState.selectedServerIndex =
            target.servers.indexOf(currentServer).coerceAtLeast(0)
    }
    LaunchedEffect(currentServer) {
        controlsState.selectedServerIndex =
            target.servers.indexOf(currentServer).coerceAtLeast(0)
    }

    val exoPlayer = player
    Column(Modifier.fillMaxSize().background(Bg)) {
        PlayerBar(
            target = target,
            onBack = onBack,
        )

        // ── Video player with overlaid controls ───────────────────────────
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
                .pointerInput(locked, duration, settings.skip) {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            if (locked) return@detectTapGestures
                            val left = size.width * .4f
                            val right = size.width * .6f
                            if (offset.x < left) seekBy(-settings.skip)
                            else if (offset.x > right) seekBy(settings.skip)
                        },
                        onTap = {
                            if (!locked) {
                                touchControls()
                            }
                        }
                    )
                },
        ) {
            // 1. Video Surface
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        useController = false
                        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        this.player = exoPlayer
                        setShutterBackgroundColor(android.graphics.Color.BLACK)

                        clipToOutline = true
                        outlineProvider = object : android.view.ViewOutlineProvider() {
                            override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                                val radius = 16f * view.resources.displayMetrics.density
                                outline.setRoundRect(0, 0, view.width, view.height, radius)
                            }
                        }
                    }
                },
                update = { it.player = exoPlayer },
                modifier = Modifier.fillMaxSize(),
            )

            // 2. Overlay Controls — new unified layout
            PlayerOverlay(
                state = controlsState,
                visible = controlsVisible || error != null,
                onPlayPause = {
                    if (playing) player.pause() else player.play()
                },
                onRewind = { seekBy(-settings.skip) },
                onForward = { seekBy(settings.skip) },
                onSeek = { newValue ->
                    if (duration > 0) player.seekTo(newValue.toLong())
                },
                onServerSelect = { idx ->
                    val picked = target.servers.getOrNull(idx) ?: return@PlayerOverlay
                    currentServer = picked
                },
                onVolumeChange = { v ->
                    playerVolume = v
                    player.volume = v
                    muted = v == 0f
                },
                onFullscreenToggle = {
                    if (controlsState.isFullscreen) {
                        // Exit fullscreen – restore portrait
                        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        @Suppress("DEPRECATION")
                        activity?.window?.decorView?.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
                    } else {
                        enterFullscreen(activity)
                    }
                },
                onInteraction = ::touchControls,
                modifier = Modifier.matchParentSize(),
            )


            // Seek-feedback bubble
            seekMessage?.let { message ->
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = .65f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 15.dp, vertical = 9.dp)
                ) {
                    Text(message, color = Text, fontFamily = MonoFont, fontSize = 12.sp)
                }
                LaunchedEffect(message) { delay(450); seekMessage = null }
            }

            // Error overlay
            if (error != null) {
                Column(
                    Modifier.matchParentSize().background(Color.Black.copy(alpha = .9f)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("STREAM UNAVAILABLE", color = Text, fontFamily = DisplayFont, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Text(error!!, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(10.dp, 11.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { error = null; loadStream(currentServer) }) { Text("Try again") }
                        if (target.servers.size > 1) Button(onClick = { serverMenu = true }) { Text("Switch server") }
                    }
                }
            }

        }

        // ── Related Content (below video) ─────────────────────────────────
        RelatedContent(repository, target, onTarget)
    }

    // Main server-chooser modal (from error overlay / home screen)
    if (serverMenu) {
        ServerChooserSheet(
            target = target,
            onDismiss = { serverMenu = false },
            onPick = { currentServer = it; serverMenu = false }
        )
    }
}

@Composable
private fun PlayerBar(
    target: PlayerTarget,
    onBack: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(14.dp, 14.dp, 16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconButton(onClick = onBack, modifier = Modifier.size(38.dp).background(Color.White.copy(alpha = .04f), RoundedCornerShape(12.dp))) {
            Icon(Icons.Default.ArrowBack, "Back", tint = Text, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(target.title.uppercase(), color = Text, fontFamily = DisplayFont, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(target.subtitle.uppercase(), color = Muted, fontFamily = MonoFont, fontSize = 11.sp, letterSpacing = .5.sp, modifier = Modifier.padding(top = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}



/**
 * Related content section shown below the video player.
 */
@Composable
private fun RelatedContent(repository: EventsRepository?, target: PlayerTarget, onTarget: (PlayerTarget) -> Unit) {
    val state by (repository?.state ?: kotlinx.coroutines.flow.MutableStateFlow(FeedState.Loading)).collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        // Section header
        val sectionTitle = if (target is HighlightTarget) "More Highlights" else "More Events"
        Text(
            sectionTitle.uppercase(),
            color = Muted,
            fontFamily = DisplayFont,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 0.8.sp,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .fillMaxWidth(),
        )
        HorizontalDivider(color = Line, modifier = Modifier.padding(horizontal = 16.dp))

        when (state) {
            is FeedState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent)
                }
            }
            is FeedState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Failed to load content", color = Muted)
                }
            }
            is FeedState.Ready -> {
                val feed = (state as FeedState.Ready).feed

                if (target is HighlightTarget) {
                    // Highlight player → show other highlights as vertical list
                    val highlights = feed.highlights.filter { it.id != target.highlight.id }
                    if (highlights.isEmpty()) {
                        EmptyContentState(Icons.Default.Movie, "No other highlights available")
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 64.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(highlights) { highlight ->
                                HighlightCard(
                                    highlight = highlight,
                                    onClick = { onTarget(HighlightTarget(highlight)) },
                                )
                            }
                        }
                    }
                } else {
                    // Live/VOD event player → show other live events as vertical list, NO highlights tab
                    val liveEvents = feed.events.filter {
                        effectiveStatus(it.startMs, it.status) == "live" &&
                            (target !is EventTarget || it.id != target.event.id)
                    }
                    if (liveEvents.isEmpty()) {
                        EmptyContentState(Icons.Default.SportsBasketball, "No other live events right now")
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 64.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            val nowMs = System.currentTimeMillis()
                            items(liveEvents) { event ->
                                EventCard(
                                    event = event,
                                    nowMs = nowMs,
                                    onClick = { onTarget(EventTarget(event)) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyContentState(icon: androidx.compose.ui.graphics.vector.ImageVector, message: String) {
    Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = Muted.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text(message, color = Muted, fontSize = 14.sp)
    }
}


private fun enterFullscreen(activity: Activity?) {
    activity ?: return
    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    activity.window.decorView.systemUiVisibility = (
        View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is android.content.ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}