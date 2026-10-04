package com.linkstreamz.app

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.PowerManager
import android.view.View
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

// ── Shared OkHttp client (one per process, connection pooling) ─────────────────
private val sharedOkHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .retryOnConnectionFailure(true)
        .build()
}

// ── Sort servers for automatic fallback ───────────────────────────────────────
// index 0 is already marked "recommended" in the UI; keep that order.
// Within the remaining servers preserve original order.
fun sortServersForFallback(servers: List<StreamServer>): List<StreamServer> = servers

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
private const val CONTROLS_HIDE_DELAY_MS = 3_000L

@Composable
fun PlayerScreen(
    target: PlayerTarget,
    settings: SettingsUiState,
    repository: EventsRepository? = null,
    onBack: () -> Unit,
    onTarget: (PlayerTarget) -> Unit = {},
    previewMode: Boolean = false,
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val server = target.selectedServer ?: target.servers.firstOrNull()
    var currentServer by remember(target, server) { mutableStateOf(server) }

    // Fallback tracking — ordered list, index into it, whether user picked manually
    val fallbackOrder = remember(target.servers) { sortServersForFallback(target.servers) }
    var fallbackIndex by remember { mutableIntStateOf(0) }
    var userPickedManually by remember { mutableStateOf(false) }
    var allServersFailed by remember { mutableStateOf(false) }

    // Per-server retry: how many times we've retried the current server before switching
    var retryCount by remember { mutableIntStateOf(0) }

    // NOTE: lock state lives in controlsState.isLocked (initialized below at rememberPlayerControlsState)
    var playing by remember { mutableStateOf(false) }
    var muted by remember(settings.startMuted) { mutableStateOf(settings.startMuted) }
    var playerVolume by remember(settings.startMuted) { mutableFloatStateOf(if (settings.startMuted) 0f else 1f) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(C.TIME_UNSET) }
    var error by remember { mutableStateOf<String?>(null) }
    var serverMenu by remember { mutableStateOf(false) }
    var seekMessage by remember { mutableStateOf<String?>(null) }
    var isExtracting by remember { mutableStateOf(false) }
    // YT-EXTRACT: Set by loadStream to signal LaunchedEffect to run extraction
    var ytExtractId by remember { mutableStateOf<String?>(null) }
    var ytExtractStream by remember { mutableStateOf<StreamServer?>(null) }
    // Player-local brightness: 100f = no dim overlay; lower = darker player only, not the whole app
    var playerBrightness by remember { mutableFloatStateOf(100f) }

    // Auto-hide controls state
    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteractionMs by remember { mutableStateOf(System.currentTimeMillis()) }

    // ── PlayerEngine (owns ExoPlayer + proxy) ────────────────────────────────
    val engine = remember {
        PlayerEngine(
            context = context,
            baseClient = sharedOkHttpClient,
            globalDelaySeconds = settings.streamDelaySeconds,
        ) {
            // onFallback — runs on main thread via engine's scope
        }
    }

    // Collect engine syncing state
    val isSyncing by engine.isSyncing.collectAsStateWithLifecycle()

    // ── Fallback logic ───────────────────────────────────────────────────────
    /** How many times to retry the same server before moving to the next. */
    val MAX_SERVER_RETRIES = 3

    // Incremented each time we want to schedule a same-server retry.
    // LaunchedEffect(retryTrigger) below watches this and does the delayed reload.
    var retryTrigger by remember { mutableIntStateOf(0) }

    fun triggerFallback(httpCode: Int = 0) {
        if (allServersFailed) return

        // Don't blindly retry client errors:
        // 403/410 → immediate fallback to next server (don't waste retries)
        // 404     → immediate fallback (stream gone)
        // 5xx/0   → retry with backoff, then fallback
        val skipRetries = httpCode == 403 || httpCode == 404 || httpCode == 410

        if (!skipRetries && retryCount < MAX_SERVER_RETRIES) {
            retryCount++
            val label = currentServer?.name?.ifBlank { "current server" } ?: "current server"
            AppLogger.d("Engine", "Retrying $label (attempt $retryCount/$MAX_SERVER_RETRIES, code=$httpCode)")
            // Signal the LaunchedEffect below to schedule the reload after a short delay
            retryTrigger++
            return
        }

        // Retries exhausted — advance to the next server
        retryCount = 0
        val nextIndex = fallbackOrder.indexOfFirst { s ->
            s != currentServer && fallbackOrder.indexOf(s) > fallbackOrder.indexOf(currentServer)
        }
        if (nextIndex == -1 || fallbackIndex >= fallbackOrder.size - 1) {
            allServersFailed = true
            error = when (httpCode) {
                403 -> "Access denied (403). The stream may be geo-blocked."
                404 -> "Stream not found (404). The event may have ended."
                410 -> "Link expired (410). Please reload the app."
                in 500..599 -> "Server error ($httpCode). Try again later."
                else -> "All servers failed. Please try again later."
            }
            return
        }
        fallbackIndex++
        val next = fallbackOrder.getOrNull(fallbackIndex) ?: run {
            allServersFailed = true
            error = "All servers failed. Please try again later."
            return
        }
        // Show toast (redacted — only show server name/index, never URL)
        val label = next.name.ifBlank { "Server ${fallbackIndex + 1}" }
        Toast.makeText(context, "Switched to $label", Toast.LENGTH_SHORT).show()
        currentServer = next
        userPickedManually = false
    }

    // Re-create engine with updated fallback when currentServer changes
    // (engine is remembered once; we wire the callback via a ref)
    val fallbackRef = rememberUpdatedState(::triggerFallback)

    // ── Wire PlayerEngine's onFallback via a side-channel ───────────────────
    // Because we can't pass a lambda into `remember { }` that references
    // mutable state, we install the listener post-creation.
    val engineFallbackListener = remember {
        object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(err: androidx.media3.common.PlaybackException) {
                // Extract HTTP response code if available for smarter retry decisions
                var httpCode = 0
                var cause: Throwable? = err.cause
                while (cause != null) {
                    if (cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                        httpCode = cause.responseCode
                        break
                    }
                    cause = cause.cause
                }
                fallbackRef.value.invoke(httpCode)
            }
        }
    }

    DisposableEffect(engine) {
        engine.player.addListener(engineFallbackListener)
        onDispose {
            engine.player.removeListener(engineFallbackListener)
            engine.release()
        }
    }

    var resumeOnForeground by remember { mutableStateOf(false) }

    // ── Background pause ─────────────────────────────────────────────────────
    // Use the Activity lifecycle owner (not LocalLifecycleOwner which gives the
    // NavBackStackEntry lifecycle). The NavBackStackEntry lifecycle fires ON_STOP
    // during nav transitions, which was pausing the player immediately on load.
    val activityLifecycleOwner = remember(activity) {
        activity as? androidx.lifecycle.LifecycleOwner
    }
    DisposableEffect(activityLifecycleOwner) {
        val owner = activityLifecycleOwner ?: return@DisposableEffect onDispose {}
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    if (!FloatingOverlayManager.isFloating.value) {
                        resumeOnForeground = engine.player.playWhenReady
                        engine.pause()
                    }
                }
                Lifecycle.Event.ON_START -> {
                    if (resumeOnForeground) {
                        val state = engine.player.playbackState
                        if (state == androidx.media3.common.Player.STATE_IDLE ||
                            state == androidx.media3.common.Player.STATE_ENDED) {
                            // Player died while backgrounded — reload the current server
                            AppLogger.d("Engine", "ON_START: player in IDLE/ENDED, reloading")
                            currentServer?.let { engine.loadStream(it, userPickedManually, true) }
                        } else {
                            engine.resume()
                        }
                    }
                }
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    // ── WakeLock ─────────────────────────────────────────────────────────────
    val wakeLock = remember {
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "LinkStreamz:Playback",
        )
    }
    DisposableEffect(Unit) {
        onDispose { if (wakeLock.isHeld) wakeLock.release() }
    }

    // ── Overlay controls state ───────────────────────────────────────────────
    val controlsState = rememberPlayerControlsState(
        isPlaying = playing,
        position = position,
        duration = duration,
        volume = playerVolume,
        servers = target.servers,
        selectedServerIndex = target.servers.indexOf(server).coerceAtLeast(0),
    )

    fun touchControls() {
        controlsVisible = true
        lastInteractionMs = System.currentTimeMillis()
    }

    fun seekBy(deltaSeconds: Int) {
        val seekable = duration != C.TIME_UNSET && duration > 0
        if (!seekable) {
            Toast.makeText(context, "Seeking isn't available on a live stream", Toast.LENGTH_SHORT).show()
            return
        }
        engine.player.seekTo((engine.player.currentPosition + deltaSeconds * 1000L).coerceIn(0L, duration))
        seekMessage = if (deltaSeconds < 0) "−${-deltaSeconds}s" else "+${deltaSeconds}s"
        touchControls()
    }

    fun loadStream(stream: StreamServer?) {
        if (stream == null) return
        error = null
        allServersFailed = false
        val isCustom = target is CustomTarget

        // YT-EXTRACT: Detect YouTube URLs at load time — branch to async extraction
        val streamUrl = stream.url
        if (streamUrl != null && YouTubeUrl.isYouTubeUrl(streamUrl)) {
            val videoId = YouTubeUrl.youtubeVideoId(streamUrl)
            if (videoId == null) {
                error = "Invalid YouTube URL"
                return
            }
            // Signal the LaunchedEffect to begin extraction
            ytExtractStream = stream
            ytExtractId = videoId
        } else {
            // YT-EXTRACT: Normal (non-YouTube) playback path — unchanged
            ytExtractId = null
            ytExtractStream = null
            isExtracting = false
            engine.loadStream(stream, isCustom, settings.autoplay)
            engine.player.volume = if (muted) 0f else 1f
        }
    }

    // ── Player event listener ────────────────────────────────────────────────
    DisposableEffect(engine.player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                // If playWhenReady just became false but isPlaying hasn't updated yet,
                // force playing=false so the UI immediately shows the play button.
                if (!playWhenReady) playing = false
            }
        }
        engine.player.addListener(listener)
        onDispose { engine.player.removeListener(listener) }
    }

    // ── Effects ──────────────────────────────────────────────────────────────
    LaunchedEffect(currentServer, previewMode) {
        if (!previewMode) loadStream(currentServer)
    }

    // YT-EXTRACT: Async extraction — runs when ytExtractId changes (set by loadStream).
    // This LaunchedEffect is inside the composable scope so scope/launch are available.
    // Cancels automatically if ytExtractId changes (user switches video) or composable leaves.
    LaunchedEffect(ytExtractId) {
        val videoId = ytExtractId ?: return@LaunchedEffect
        val stream = ytExtractStream ?: return@LaunchedEffect
        val isCustom = target is CustomTarget

        isExtracting = true
        error = null

        val result = YouTubeExtractor.extract(videoId)

        // Guard: if server changed while extracting, discard this result
        if (ytExtractId != videoId) return@LaunchedEffect

        isExtracting = false

        when (result) {
            is YtResult.Muxed -> {
                // YT-EXTRACT: muxed — single URL carries video+audio, maps to server.url
                val ephemeral = stream.copy(url = result.url, videoUrl = null, audioUrl = null)
                engine.loadStream(ephemeral, isCustom, settings.autoplay)
                engine.player.volume = if (muted) 0f else 1f
            }
            is YtResult.Split -> {
                // YT-EXTRACT: split — separate video+audio URLs, maps to server.videoUrl+audioUrl
                val ephemeral = stream.copy(url = null, videoUrl = result.videoUrl, audioUrl = result.audioUrl)
                engine.loadStream(ephemeral, isCustom, settings.autoplay)
                engine.player.volume = if (muted) 0f else 1f
            }
            is YtResult.Failure -> {
                error = when (result.reason) {
                    YtError.FATAL -> "YouTube could not load this video. It may be unavailable or restricted."
                    YtError.RECAPTCHA -> "YouTube requires a ReCaptcha solve. Please try again."
                    YtError.RATE_LIMIT -> "YouTube is rate-limiting. Please try again in a few minutes."
                    YtError.AGE_RESTRICTED -> "This video has an age restriction and cannot be played here."
                    YtError.GEO_RESTRICTED -> "This video is not available in your region."
                    YtError.LIVE_STREAM -> "Live YouTube streams are not supported in this app."
                    YtError.NO_STREAMS -> "Could not load this video. Try another server."
                    YtError.UNKNOWN -> "Could not load this video. Try another server."
                }
                // Try next server before showing error card
                if (target.servers.size > 1 && target.servers.indexOf(stream) < target.servers.lastIndex) {
                    error = null
                    triggerFallback()
                }
            }
        }
    }

    // ── Same-server retry (triggered by triggerFallback when retries remain) ─
    LaunchedEffect(retryTrigger) {
        if (retryTrigger == 0) return@LaunchedEffect // skip initial composition
        delay(2_000L) // brief pause lets transient network errors clear
        loadStream(currentServer)
    }

    LaunchedEffect(Unit) {
        while (true) {
            position = engine.player.currentPosition.coerceAtLeast(0L)
            duration = engine.player.duration
            engine.checkStall()
            delay(250)
        }
    }

    LaunchedEffect(playing, settings.keepScreenOn) {
        if (playing && settings.keepScreenOn && !wakeLock.isHeld)
            wakeLock.acquire(settings.hideControlsSeconds * 60_000L)
        else if (!playing && wakeLock.isHeld)
            wakeLock.release()
    }

    // Auto-hide timer
    LaunchedEffect(lastInteractionMs, controlsState.openMenu) {
        while (controlsState.openMenu != OpenMenu.None) {
            delay(500)
        }
        delay(CONTROLS_HIDE_DELAY_MS)
        controlsVisible = false
    }

    // Keep controls state mirror in sync
    LaunchedEffect(playing)      { controlsState.isPlaying = playing }
    LaunchedEffect(position)     { controlsState.position  = position }
    LaunchedEffect(duration)     { controlsState.duration  = duration }
    LaunchedEffect(playerVolume) { controlsState.volume    = playerVolume }
    LaunchedEffect(target.servers) {
        controlsState.servers = target.servers
        controlsState.selectedServerIndex =
            target.servers.indexOf(currentServer).coerceAtLeast(0)
    }
    LaunchedEffect(currentServer) {
        controlsState.selectedServerIndex =
            target.servers.indexOf(currentServer).coerceAtLeast(0)
    }

    val exoPlayer = engine.player
    val isFs = controlsState.isFullscreen

    Column(
        Modifier
            .fillMaxSize()
            .background(LinkColors.ScreenBg)
            .drawBehind {
                val r = size.width * 1.5f
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.00f to LinkColors.OrangePrimary.copy(alpha = 0.12f),
                            0.70f to Color.Transparent,
                        ),
                        center = Offset(size.width / 2f, 0f),
                        radius = r,
                    ),
                    radius = r,
                    center = Offset(size.width / 2f, 0f),
                )
            }
    ) {
        if (!isFs) {
            PlayerBar(
                target = target,
                onBack = onBack,
            )
        }

        // ── Video player with overlaid controls ───────────────────────────
        Box(
            Modifier
                .fillMaxWidth()
                .then(if (isFs) Modifier.weight(1f) else Modifier.padding(horizontal = 12.dp).aspectRatio(16f / 9f))
                .then(if (isFs) Modifier else Modifier.shadow(
                    elevation = 24.dp,
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = LinkColors.OrangePrimary.copy(alpha = 0.10f),
                    spotColor = LinkColors.OrangePrimary.copy(alpha = 0.10f)
                ))
                .background(Color.Black, RoundedCornerShape(if (isFs) 0.dp else 16.dp))
                .then(if (isFs) Modifier else Modifier.border(1.dp, LinkColors.OrangePrimary.copy(alpha = 0.30f), RoundedCornerShape(16.dp)))
                .clip(RoundedCornerShape(if (isFs) 0.dp else 16.dp))
                .pointerInput(controlsState.isLocked, duration, settings.skipLength) {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            if (controlsState.isLocked) return@detectTapGestures
                            val left = size.width * .4f
                            val right = size.width * .6f
                            if (offset.x < left) seekBy(-settings.skipLength)
                            else if (offset.x > right) seekBy(settings.skipLength)
                        },
                        onTap = {
                            if (controlsState.isLocked) {
                                // Single tap = instant unlock, overlay comes back immediately
                                controlsState.isLocked = false
                                touchControls()
                            } else {
                                touchControls()
                            }
                        }
                    )
                },
        ) {
            // 1. Video Surface or Float Placeholder
            val isFloating by FloatingOverlayManager.isFloating
            
            if (isFloating) {
                Box(Modifier.fillMaxSize().clickable { 
                    FloatingOverlayManager.hide() 
                }, contentAlignment = Alignment.Center) {
                    EmptyContentState(Icons.Default.PictureInPictureAlt, "Playing in a floating window")
                }
            } else {
                AndroidView(
                    factory = { context ->
                        PlayerView(context).apply {
                            useController = false
                            resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                            this.player = exoPlayer
                            setShutterBackgroundColor(android.graphics.Color.BLACK)
                            // !! Critical: disable PlayerView's built-in lifecycle observer
                            // that auto-pauses the player on Activity onPause. Without this,
                            // PlayerView internally calls player.pause() whenever it detects
                            // a lifecycle pause event, fighting with our own playback control.
                            keepScreenOn = true
    
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
            }

            // 1.2. Player Dimmer — applies brightness by drawing a black overlay
            // brightness range is 5f..100f. 100f = 0 alpha, 5f = ~95% alpha
            val dimAlpha = 1f - (playerBrightness / 100f).coerceIn(0f, 1f)
            if (dimAlpha > 0f) {
                Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = dimAlpha)))
            }

            // 1.5. Scrim
            androidx.compose.animation.AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(600)),
                modifier = Modifier.matchParentSize(),
            ) {
                Box(
                    Modifier.fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.00f to Color.Black.copy(alpha = 0.28f),
                                0.32f to Color.Transparent,
                                0.55f to Color.Transparent,
                                1.00f to Color.Black.copy(alpha = 0.55f)
                            )
                        )
                )
            }

            // 1.7. Syncing / buffering / extracting overlay — fades out when playback starts
            androidx.compose.animation.AnimatedVisibility(
                visible = isExtracting || (isSyncing && !playing),
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(600)),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(
                            color = LinkColors.OrangePrimary,
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 2.5.dp,
                        )
                        val delayVal = settings.streamDelaySeconds
                        if (isExtracting) {
                            Text(
                                "Extracting YouTube stream...",
                                color = LinkColors.MutedText,
                                fontSize = 12.sp,
                                fontFamily = MonoFont,
                            )
                        } else if (delayVal > 0) {
                            Text(
                                "Syncing to stay ${delayVal}s behind live...",
                                color = LinkColors.MutedText,
                                fontSize = 12.sp,
                                fontFamily = MonoFont,
                            )
                        }
                    }
                }
            }

            // 2. Overlay Controls
            PlayerOverlay(
                state = controlsState,
                visible = controlsVisible || error != null,
                onPlayPause = {
                    val state = engine.player.playbackState
                    val pwr = engine.player.playWhenReady
                    AppLogger.d("Engine", "UI Play/Pause clicked. playing=$playing pwr=$pwr state=$state loading=${engine.isLoading}")
                    when {
                        playing -> {
                            // Actively playing — pause
                            engine.player.pause()
                        }
                        pwr && state == androidx.media3.common.Player.STATE_BUFFERING -> {
                            // Buffering toward playback — pause
                            engine.player.pause()
                        }
                        state == androidx.media3.common.Player.STATE_READY -> {
                            // Ready but paused — resume
                            engine.player.play()
                        }
                        engine.isLoading -> {
                            // Load coroutine in flight — just ensure we want to play when ready
                            AppLogger.d("Engine", "Load in flight, ensuring playWhenReady=true")
                            engine.player.playWhenReady = true
                        }
                        state == androidx.media3.common.Player.STATE_IDLE ||
                        state == androidx.media3.common.Player.STATE_ENDED -> {
                            // Player dead and no load in flight — restart
                            AppLogger.d("Engine", "Restarting stream (state=$state)")
                            loadStream(currentServer)
                        }
                        else -> engine.player.play()
                    }
                },
                onRewind = { seekBy(-settings.skipLength) },
                onForward = { seekBy(settings.skipLength) },
                onSeek = { newValue ->
                    if (duration > 0) engine.player.seekTo(newValue.toLong())
                },
                onServerSelect = { idx ->
                    val picked = target.servers.getOrNull(idx) ?: return@PlayerOverlay
                    userPickedManually = true
                    retryCount = 0  // fresh retries on the newly picked server
                    currentServer = picked
                },
                onVolumeChange = { v ->
                    playerVolume = v
                    engine.player.volume = v
                    muted = v == 0f
                },
                onFullscreenToggle = {
                    if (controlsState.isFullscreen) {
                        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        @Suppress("DEPRECATION")
                        activity?.window?.decorView?.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
                    } else {
                        enterFullscreen(activity)
                    }
                },
                onFloatWindow = {
                    FloatingOverlayManager.show(
                        context = context,
                        engine = engine,
                        onClose = { engine.player.stop() },
                        onExpand = { 
                            FloatingOverlayManager.hide() 
                            val intent = android.content.Intent(context, MainActivity::class.java).apply {
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                            }
                            context.startActivity(intent)
                        }
                    )
                },
                onInteraction = ::touchControls,
                playerBrightness = playerBrightness,
                onPlayerBrightnessChange = { v -> playerBrightness = v },
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
                    Text(message, color = LinkColors.PrimaryText, fontFamily = MonoFont, fontSize = 12.sp)
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
                    Text("STREAM UNAVAILABLE", color = LinkColors.PrimaryText, fontFamily = DisplayFont, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Text(error!!, color = LinkColors.MutedText, fontSize = 13.sp, modifier = Modifier.padding(10.dp, 11.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            error = null
                            allServersFailed = false
                            fallbackIndex = 0
                            retryCount = 0
                            userPickedManually = false
                            loadStream(currentServer)
                        }) { Text("Try again") }
                        if (target.servers.size > 1) Button(onClick = { serverMenu = true }) { Text("Switch server") }
                    }
                }
            }


        }
        
        // ── Related Content (below video) ─────────────────────────────────
        if (!isFs) {
            RelatedContent(repository, target, onTarget)
        }
    }

    // Main server-chooser modal (from error overlay / home screen)
    if (serverMenu) {
        ServerChooserSheet(
            target = target,
            onDismiss = { serverMenu = false },
            onPick = {
                userPickedManually = true
                retryCount = 0  // fresh retries on the newly picked server
                currentServer = it
                serverMenu = false
            }
        )
    }
}

@Composable
private fun PlayerBar(
    target: PlayerTarget,
    onBack: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(14.dp, 14.dp, 16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconButton(onClick = onBack, modifier = Modifier.size(38.dp).background(LinkColors.CardBg, RoundedCornerShape(12.dp)).border(1.dp, LinkColors.OrangePrimary.copy(alpha = 0.35f), RoundedCornerShape(12.dp))) {
            Icon(Icons.Default.ArrowBack, "Back", tint = LinkColors.OrangePrimary, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(target.title.uppercase(), color = LinkColors.PrimaryText, fontFamily = DisplayFont, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(target.subtitle.uppercase(), color = LinkColors.MutedText, fontFamily = MonoFont, fontSize = 11.sp, letterSpacing = .5.sp, modifier = Modifier.padding(top = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            color = LinkColors.MutedText,
            fontFamily = DisplayFont,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 0.8.sp,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .fillMaxWidth(),
        )
        HorizontalDivider(color = LinkColors.LineAlpha, modifier = Modifier.padding(horizontal = 16.dp))

        when (state) {
            is FeedState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = LinkColors.OrangePrimary)
                }
            }
            is FeedState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Failed to load content", color = LinkColors.MutedText)
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
        Icon(icon, null, tint = LinkColors.MutedText.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text(message, color = LinkColors.MutedText, fontSize = 14.sp)
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

