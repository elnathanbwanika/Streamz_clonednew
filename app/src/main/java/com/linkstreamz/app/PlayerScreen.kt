package com.linkstreamz.app

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.PowerManager
import android.util.Rational
import android.view.View
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
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
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(C.TIME_UNSET) }
    var error by remember { mutableStateOf<String?>(null) }
    var serverMenu by remember { mutableStateOf(false) }
    var seekMessage by remember { mutableStateOf<String?>(null) }
    val player = remember { ExoPlayer.Builder(context).build() }
    val wakeLock = remember {
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "LinkStreamz:Playback",
        )
    }

    fun seekBy(deltaSeconds: Int, side: String? = null) {
        val seekable = duration != C.TIME_UNSET && duration > 0
        if (!seekable) {
            Toast.makeText(context, "Seeking isn't available on a live stream", Toast.LENGTH_SHORT).show()
            return
        }
        player.seekTo((player.currentPosition + deltaSeconds * 1000L).coerceIn(0L, duration))
        seekMessage = if (deltaSeconds < 0) "−${-deltaSeconds}s" else "+${deltaSeconds}s"
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

    val exoPlayer = player
    Column(Modifier.fillMaxSize().background(Bg)) {
        PlayerBar(
            target = target,
            onBack = onBack,
        )
        
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)
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
                                if (player.isPlaying) player.pause() else player.play()
                            }
                        }
                    )
                },
        ) {
            AndroidView(
                factory = {
                    PlayerView(it).apply {
                        useController = false
                        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        this.player = exoPlayer
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                update = { it.player = exoPlayer },
                modifier = Modifier.fillMaxSize(),
            )
            
            if (locked) {
                Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { locked = false }, modifier = Modifier.size(44.dp).background(Color.Black.copy(alpha = .68f), CircleShape)) {
                        Icon(Icons.Default.LockOpen, "Unlock", tint = Accent, modifier = Modifier.size(19.dp))
                    }
                }
            }
            
            seekMessage?.let { message ->
                Box(Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = .65f), RoundedCornerShape(999.dp)).padding(horizontal = 15.dp, vertical = 9.dp)) {
                    Text(message, color = Text, fontFamily = MonoFont, fontSize = 12.sp)
                }
                LaunchedEffect(message) { delay(450); seekMessage = null }
            }
            if (error != null) {
                Column(Modifier.matchParentSize().background(Color.Black.copy(alpha = .9f)), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("STREAM UNAVAILABLE", color = Text, fontFamily = DisplayFont, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Text(error!!, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(10.dp, 11.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { error = null; loadStream(currentServer) }) { Text("Try again") }
                        if (target.servers.size > 1) Button(onClick = { serverMenu = true }) { Text("Switch server") }
                    }
                }
            }
        }
        
        // Essential Controls Below Video
        ControlBar(
            target = target,
            playing = playing,
            muted = muted,
            position = position,
            duration = duration,
            onPlayPause = { if (playing) player.pause() else player.play() },
            onBack = { seekBy(-settings.skip) },
            onForward = { seekBy(settings.skip) },
            onMute = { muted = !muted; player.volume = if (muted) 0f else 1f },
            onFullscreen = { enterFullscreen(activity) },
            onServers = { serverMenu = true },
            onSeek = { value -> if (duration > 0) player.seekTo(value.toLong()) },
        )
        
        // Related Content Tabs
        RelatedContent(repository, target, onTarget)
    }
    
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

@Composable
private fun ControlBar(
    target: PlayerTarget,
    playing: Boolean,
    muted: Boolean,
    position: Long,
    duration: Long,
    onPlayPause: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onMute: () -> Unit,
    onFullscreen: () -> Unit,
    onServers: () -> Unit,
    onSeek: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(formatPlayerTime(position), color = Text, fontFamily = MonoFont, fontSize = 11.5.sp)
            Slider(
                value = if (duration > 0) position.toFloat().coerceIn(0f, duration.toFloat()) else 0f,
                onValueChange = onSeek,
                valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                modifier = Modifier.weight(1f),
            )
            Text(if (duration > 0) formatPlayerTime(duration) else "LIVE", color = Text, fontFamily = MonoFont, fontSize = 11.5.sp)
        }
        
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPlayPause, modifier = Modifier.size(56.dp).background(Brush.linearGradient(listOf(Accent2, Accent)), CircleShape)) {
                    Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play", tint = Color.Black, modifier = Modifier.size(26.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoundPlayerButton(Icons.Default.SkipPrevious, "Back", onBack)
                    RoundPlayerButton(Icons.Default.SkipNext, "Forward", onForward)
                }
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RoundPlayerButton(if (muted) Icons.Default.MusicOff else Icons.Default.MusicNote, "Mute", onMute)
                if (target.servers.size > 1) {
                    RoundPlayerButton(Icons.Default.Settings, "Servers", onServers)
                }
                RoundPlayerButton(Icons.Default.Fullscreen, "Fullscreen", onFullscreen)
            }
        }
    }
}

@Composable
private fun RoundPlayerButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(42.dp).background(Color.White.copy(alpha = .08f), CircleShape)) {
        Icon(icon, description, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun RelatedContent(repository: EventsRepository?, target: PlayerTarget, onTarget: (PlayerTarget) -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Also Live Now", "Highlights")
    
    val state by (repository?.state ?: kotlinx.coroutines.flow.MutableStateFlow(FeedState.Loading)).collectAsStateWithLifecycle()
    
    Column(Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = Accent,
            divider = { HorizontalDivider(color = Line) }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, color = if (selectedTab == index) Accent else Muted) }
                )
            }
        }
        
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
                if (selectedTab == 0) {
                    val liveEvents = feed.events.filter { effectiveStatus(it.startMs, it.status) == "live" && (target !is EventTarget || it.id != target.event.id) }
                    if (liveEvents.isEmpty()) {
                        EmptyContentState(Icons.Default.SportsBasketball, "No other live games right now")
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(liveEvents) { event ->
                                EventCardSmall(event) {
                                    onTarget(EventTarget(event))
                                }
                            }
                        }
                    }
                } else {
                    val highlights = feed.highlights.filter { target !is HighlightTarget || it.id != target.highlight.id }
                    if (highlights.isEmpty()) {
                        EmptyContentState(Icons.Default.Movie, "No highlights available")
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(highlights) { highlight ->
                                HighlightCardSmall(highlight) {
                                    onTarget(HighlightTarget(highlight))
                                }
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

@Composable
private fun EventCardSmall(event: Event, onClick: () -> Unit) {
    Column(
        Modifier.width(200.dp).background(Color(0xFF151515), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("● LIVE", color = Accent, fontFamily = MonoFont, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(event.league, color = Muted, fontFamily = MonoFont, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(event.home.name, color = Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(event.away.name, color = Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun HighlightCardSmall(highlight: Highlight, onClick: () -> Unit) {
    Column(
        Modifier.width(200.dp).background(Color(0xFF151515), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(12.dp)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f/9f).background(Color(0xFF222222), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.PlayArrow, null, tint = Color.White.copy(alpha = 0.5f))
            Text(highlight.duration, color = Color.White, fontFamily = MonoFont, fontSize = 10.sp, modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).background(Color.Black.copy(alpha=0.7f), RoundedCornerShape(4.dp)).padding(horizontal=4.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(highlight.title, color = Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private fun formatPlayerTime(milliseconds: Long): String {
    val seconds = (milliseconds / 1000).coerceAtLeast(0)
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remaining = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, remaining) else "%02d:%02d".format(minutes, remaining)
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
