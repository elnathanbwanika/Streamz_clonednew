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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    onBack: () -> Unit,
    previewMode: Boolean = false,
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val server = target.selectedServer ?: target.servers.firstOrNull()
    var currentServer by remember(target, server) { mutableStateOf(server) }
    var controlsVisible by remember { mutableStateOf(true) }
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
                if (!isPlaying && !player.playWhenReady) controlsVisible = true
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
    LaunchedEffect(playing, controlsVisible, locked, settings.hide) {
        if (playing && controlsVisible && !locked) {
            delay(settings.hide * 1000L)
            controlsVisible = false
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
            servers = target.servers,
            currentServer = currentServer,
            menuOpen = serverMenu,
            onBack = onBack,
            onServers = { serverMenu = !serverMenu },
            onSelectServer = { currentServer = it; serverMenu = false },
        )
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(14.dp))
                .pointerInput(locked, duration, settings.skip) {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            if (locked) return@detectTapGestures
                            val left = size.width * .4f
                            val right = size.width * .6f
                            if (offset.x < left) seekBy(-settings.skip)
                            else if (offset.x > right) seekBy(settings.skip)
                            else {
                                if (player.isPlaying) player.pause() else player.play()
                                controlsVisible = true
                            }
                        },
                        onTap = {
                            if (!locked) controlsVisible = !controlsVisible
                        },
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
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black),
            )
            if (controlsVisible && !locked) {
                PlayerOverlay(
                    target = target,
                    playing = playing,
                    muted = muted,
                    position = position,
                    duration = duration,
                    onPlayPause = { if (playing) player.pause() else player.play(); controlsVisible = true },
                    onBack = { seekBy(-settings.skip) },
                    onForward = { seekBy(settings.skip) },
                    onMute = { muted = !muted; player.volume = if (muted) 0f else 1f },
                    onFullscreen = { enterFullscreen(activity) },
                    onPip = {
                        if (Build.VERSION.SDK_INT >= 26) activity?.enterPictureInPictureMode(
                            PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build(),
                        )
                    },
                    onLock = { locked = true; controlsVisible = false },
                    onSeek = { value -> if (duration > 0) player.seekTo(value.toLong()) },
                )
            }
            if (locked) {
                Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { locked = false; controlsVisible = true }, modifier = Modifier.size(44.dp).background(Color.Black.copy(alpha = .68f), CircleShape)) {
                        Icon(Icons.Default.LockOpen, "Unlock", tint = Accent, modifier = Modifier.size(19.dp))
                    }
                    IconButton(onClick = { locked = false; controlsVisible = true }, modifier = Modifier.size(44.dp).background(Color.Black.copy(alpha = .68f), CircleShape)) {
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
        RelatedContent(target, onTarget = { /* related content is supplied by Home on the next selection */ })
    }
}

@Composable
private fun PlayerBar(
    target: PlayerTarget,
    servers: List<StreamServer>,
    currentServer: StreamServer?,
    menuOpen: Boolean,
    onBack: () -> Unit,
    onServers: () -> Unit,
    onSelectServer: (StreamServer) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(14.dp, 14.dp, 16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconButton(onClick = onBack, modifier = Modifier.size(38.dp).background(Color.White.copy(alpha = .04f), RoundedCornerShape(12.dp))) {
            Icon(Icons.Default.ArrowBack, "Back", tint = Text, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(target.title.uppercase(), color = Text, fontFamily = DisplayFont, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(target.subtitle.uppercase(), color = Muted, fontFamily = MonoFont, fontSize = 10.sp, letterSpacing = .5.sp, modifier = Modifier.padding(top = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (servers.size > 1) {
            Box {
                IconButton(onClick = onServers, modifier = Modifier.size(38.dp).background(Color.White.copy(alpha = .04f), RoundedCornerShape(12.dp))) {
                    Icon(Icons.Default.Settings, "Servers", tint = Text, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = onServers,
                    offset = DpOffset(0.dp, 10.dp),
                    modifier = Modifier.width(220.dp).background(Brush.verticalGradient(listOf(Color(0xFF1A1A1A), Color(0xFF111111)))),
                ) {
                    Text("CHOOSE SERVER", color = Muted, fontFamily = MonoFont, fontSize = 9.5.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
                    servers.forEachIndexed { index, value ->
                        DropdownMenuItem(
                            text = { Text("${index + 1}. ${value.name}", color = if (value == currentServer) Accent else Text, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold) },
                            onClick = { onSelectServer(value) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerOverlay(
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
    onPip: () -> Unit,
    onLock: () -> Unit,
    onSeek: (Float) -> Unit,
) {
    Column(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .7f), Color.Transparent, Color.Black.copy(alpha = .82f))).clip(RoundedCornerShape(14.dp)), verticalArrangement = Arrangement.SpaceBetween) {
        Row(Modifier.fillMaxWidth().padding(12.dp, 12.dp, 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (target) {
                    is EventTarget -> if (effectiveStatus(target.event.startMs, target.event.status) == "live") "● LIVE" else "REPLAY"
                    is HighlightTarget -> "HIGHLIGHT"
                    is CustomTarget -> "● LIVE"
                },
                color = Color.Black,
                fontFamily = MonoFont,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.background(Brush.linearGradient(listOf(Accent2, Accent)), RoundedCornerShape(8.dp)).padding(horizontal = 11.dp, vertical = 5.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                RoundPlayerButton(Icons.Default.Lock, "Lock", onLock)
                RoundPlayerButton(Icons.Default.PictureInPictureAlt, "PiP", onPip)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            RoundPlayerButton(Icons.Default.SkipPrevious, "Back", onBack, 42.dp)
            Spacer(Modifier.width(16.dp))
            IconButton(onClick = onPlayPause, modifier = Modifier.size(64.dp).background(Brush.linearGradient(listOf(Accent2, Accent)), CircleShape)) {
                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play", tint = Color.Black, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(16.dp))
            RoundPlayerButton(Icons.Default.SkipNext, "Forward", onForward, 42.dp)
        }
        Row(Modifier.fillMaxWidth().padding(12.dp, 12.dp, 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(formatPlayerTime(position), color = Color(0xFFE8E8E8), fontFamily = MonoFont, fontSize = 11.5.sp)
            Slider(
                value = if (duration > 0) position.toFloat().coerceIn(0f, duration.toFloat()) else 0f,
                onValueChange = onSeek,
                valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                modifier = Modifier.weight(1f),
            )
            Text(if (duration > 0) formatPlayerTime(duration) else "LIVE", color = Color(0xFFE8E8E8), fontFamily = MonoFont, fontSize = 11.5.sp)
            RoundPlayerButton(if (muted) Icons.Default.MusicOff else Icons.Default.MusicNote, "Mute", onMute)
            RoundPlayerButton(Icons.Default.Fullscreen, "Fullscreen", onFullscreen)
        }
    }
}

@Composable
private fun RoundPlayerButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit, size: androidx.compose.ui.unit.Dp = 42.dp) {
    IconButton(onClick = onClick, modifier = Modifier.size(size)) {
        Icon(icon, description, tint = Color.White, modifier = Modifier.size(if (size == 42.dp) 24.dp else 24.dp))
    }
}

@Composable
private fun RelatedContent(target: PlayerTarget, onTarget: (PlayerTarget) -> Unit) {
    val label = when (target) {
        is HighlightTarget -> "MORE HIGHLIGHTS"
        else -> "ALSO LIVE NOW"
    }
    Column(Modifier.fillMaxSize().padding(bottom = 28.dp)) {
        Row(Modifier.fillMaxWidth().padding(20.dp, 20.dp, 20.dp, 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Muted, fontFamily = MonoFont, fontSize = 11.sp, letterSpacing = 1.sp)
        }
        Text("Choose another stream from Home to continue watching.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp))
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