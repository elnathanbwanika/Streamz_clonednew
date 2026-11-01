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
import androidx.compose.foundation.lazy.LazyRow
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
    // Base client — proxy is applied via GlobalProxy (WireGuard SOCKS5 server).
    // The per-stream customProxyUrl in Settings was the old approach; GlobalProxy
    // supersedes it and routes ALL traffic (streaming + API + token minting).
    GlobalProxy.buildClient(connectTimeoutSec = 30, readTimeoutSec = 30)
        .newBuilder()
        .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
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

data class ChannelTarget(
    val channel: Channel,
    override val selectedServer: StreamServer? = null,
) : PlayerTarget {
    override val servers: List<StreamServer> = listOf(
        StreamServer(
            name        = "Auto",
            url         = channel.videoUrl,
            useTokenAuth = StreamConfig.requiresTokenAuth(channel.videoUrl),
        )
    )
    override val title: String = channel.title
    override val subtitle: String = listOfNotNull(channel.category, channel.country).joinToString(" | ")
    override fun withServer(server: StreamServer) = copy(selectedServer = server)
}

data class MovieTarget(
    val movie: Movie,
    override val selectedServer: StreamServer? = null,
) : PlayerTarget {
    override val servers: List<StreamServer> = listOf(StreamServer("Auto", movie.videoUrl))
    override val title: String = movie.title
    override val subtitle: String = listOfNotNull(movie.genre, movie.year.toString(), movie.vj).joinToString(" | ")
    override fun withServer(server: StreamServer) = copy(selectedServer = server)
}

data class EpisodeTarget(
    val series: Series,
    val season: Season,
    val episode: Episode,
    override val selectedServer: StreamServer? = null,
) : PlayerTarget {
    override val servers: List<StreamServer> = listOf(StreamServer("Auto", episode.videoUrl))
    override val title: String = series.title
    override val subtitle: String = "S${season.number} E${episode.n} | ${episode.title}"
    override fun withServer(server: StreamServer) = copy(selectedServer = server)
}

// Auto-hide delay for overlay controls (in milliseconds)
private const val CONTROLS_HIDE_DELAY_MS = 3_000L

@Composable
fun PlayerScreen(
    target: PlayerTarget,
    settings: SettingsUiState,
    repository: EventsRepository? = null,
    moviesRepo: MoviesRepository? = null,
    channelsRepo: ChannelsRepository? = null,
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
    var isBuffering by remember { mutableStateOf(false) }
    // YT-EXTRACT: Set by loadStream to signal LaunchedEffect to run extraction
    var ytExtractId by remember { mutableStateOf<String?>(null) }
    var ytExtractStream by remember { mutableStateOf<StreamServer?>(null) }
    // Player-local brightness: 100f = no overlay (neutral); lower = darker, higher = brighter (white wash).
    // Range 5f..300f. Default 100f (neutral — no overlay).
    var playerBrightness by remember { mutableFloatStateOf(100f) }

    // Auto-hide controls state
    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteractionMs by remember { mutableStateOf(System.currentTimeMillis()) }

    // ── PlayerEngine (owns ExoPlayer + HLS proxy) ────────────────────────────
    // GlobalProxy already applied the WireGuard SOCKS5 proxy to sharedOkHttpClient.
    val baseClient = sharedOkHttpClient

    val engine = remember {
        PlayerEngine(
            context = context,
            baseClient = baseClient,
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
            AppLogger.e("Engine", "All servers failed. Last HTTP code: $httpCode")
            error = when (httpCode) {
                403 -> "Stream not available in your location. Try either connecting VPN or using proxy in settings"
                404 -> "This event has ended"
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
                if (isPlaying) isBuffering = false
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == androidx.media3.common.Player.STATE_BUFFERING
                if (playbackState == androidx.media3.common.Player.STATE_READY ||
                    playbackState == androidx.media3.common.Player.STATE_IDLE ||
                    playbackState == androidx.media3.common.Player.STATE_ENDED) {
                    isBuffering = false
                }
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
            wakeLock.acquire(4 * 60 * 60 * 1000L) // 4-hour safety cap for playback
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

    // Keep the singleton in sync so MainActivity can hide the Logs FAB
    androidx.compose.runtime.SideEffect {
        PlayerFullscreenState.isFullscreen.value = isFs
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { PlayerFullscreenState.isFullscreen.value = false }
    }

    // The player content (video + overlays) is extracted so it can be rendered in two contexts:
    // portrait (inside the framed Column) and fullscreen (as a root-level overlay Box).
    //
    // We use a Box at the top level so fullscreen can be an absolute overlay that escapes the Column.
    Box(Modifier.fillMaxSize()) {
        // ── Portrait layout (always rendered; hidden behind fullscreen overlay when isFs) ──
        if (!isFs) {
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
                PlayerBar(target = target, onBack = onBack)

                // Portrait frame: 8dp outer, 1.5dp orange border, 3dp dark gap, 3.5dp inner
                val outerShape = RoundedCornerShape(LinkDimens.PlayerOuterRadius)
                val innerShape = RoundedCornerShape(LinkDimens.PlayerInnerRadius)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .aspectRatio(16f / 9f)
                        .clip(outerShape)
                        .border(LinkDimens.PlayerBorderWidth, LinkColors.OrangePrimary, outerShape)
                        .background(LinkDimens.PlayerGapColor)
                        .padding(LinkDimens.PlayerGap),
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black, innerShape)
                            .clip(innerShape)
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
                                            controlsState.isLocked = false
                                            touchControls()
                                        } else {
                                            touchControls()
                                        }
                                    }
                                )
                            },
                    ) {
                        PlayerVideoContent(
                            exoPlayer = exoPlayer,
                            isFloating = FloatingOverlayManager.isFloating.value,
                            onHideFloat = { FloatingOverlayManager.hide() },
                            playerBrightness = playerBrightness,
                            controlsVisible = controlsVisible,
                            isExtracting = isExtracting,
                            isBuffering = isBuffering,
                            isSyncing = isSyncing,
                            playing = playing,
                            settings = settings,
                            seekMessage = seekMessage,
                            error = error,
                            controlsState = controlsState,
                            onPlayPause = {
                                val state = engine.player.playbackState
                                val pwr = engine.player.playWhenReady
                                AppLogger.d("Engine", "UI Play/Pause clicked. playing=$playing pwr=$pwr state=$state loading=${engine.isLoading}")
                                when {
                                    playing -> engine.player.pause()
                                    pwr && state == androidx.media3.common.Player.STATE_BUFFERING -> engine.player.pause()
                                    state == androidx.media3.common.Player.STATE_READY -> engine.player.play()
                                    engine.isLoading -> { AppLogger.d("Engine", "Load in flight, ensuring playWhenReady=true"); engine.player.playWhenReady = true }
                                    state == androidx.media3.common.Player.STATE_IDLE || state == androidx.media3.common.Player.STATE_ENDED -> { AppLogger.d("Engine", "Restarting stream (state=$state)"); loadStream(currentServer) }
                                    else -> engine.player.play()
                                }
                            },
                            onRewind = { seekBy(-settings.skipLength) },
                            onForward = { seekBy(settings.skipLength) },
                            onSeek = { if (duration > 0) engine.player.seekTo(it.toLong()) },
                            onServerSelect = { idx ->
                                val picked = target.servers.getOrNull(idx) ?: return@PlayerVideoContent
                                userPickedManually = true
                                retryCount = 0
                                currentServer = picked
                            },
                            onVolumeChange = { v ->
                                playerVolume = v
                                engine.player.volume = v
                                muted = v == 0f
                            },
                            onFullscreenToggle = { enterFullscreen(activity) },
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
                            onPlayerBrightnessChange = { v -> playerBrightness = v },
                            onRetry = {
                                error = null
                                allServersFailed = false
                                fallbackIndex = 0
                                retryCount = 0
                                userPickedManually = false
                                loadStream(currentServer)
                            },
                            onSwitchServer = { serverMenu = true },
                            hasMultipleServers = target.servers.size > 1,
                            isFullscreen = false,
                        )
                    }
                }

                // ── Related Content (below video) ─────────────────────────────────
                RelatedContent(repository, moviesRepo, channelsRepo, target, onTarget)
            }
        }

        // ── Fullscreen overlay: pure black, edge-to-edge, no frame ──────────
        if (isFs) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
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
                                    controlsState.isLocked = false
                                    touchControls()
                                } else {
                                    touchControls()
                                }
                            }
                        )
                    },
            ) {
                PlayerVideoContent(
                    exoPlayer = exoPlayer,
                    isFloating = FloatingOverlayManager.isFloating.value,
                    onHideFloat = { FloatingOverlayManager.hide() },
                    playerBrightness = playerBrightness,
                    controlsVisible = controlsVisible,
                    isExtracting = isExtracting,
                    isBuffering = isBuffering,
                    isSyncing = isSyncing,
                    playing = playing,
                    settings = settings,
                    seekMessage = seekMessage,
                    error = error,
                    controlsState = controlsState,
                    onPlayPause = {
                        val state = engine.player.playbackState
                        val pwr = engine.player.playWhenReady
                        AppLogger.d("Engine", "UI Play/Pause clicked. playing=$playing pwr=$pwr state=$state loading=${engine.isLoading}")
                        when {
                            playing -> engine.player.pause()
                            pwr && state == androidx.media3.common.Player.STATE_BUFFERING -> engine.player.pause()
                            state == androidx.media3.common.Player.STATE_READY -> engine.player.play()
                            engine.isLoading -> { AppLogger.d("Engine", "Load in flight, ensuring playWhenReady=true"); engine.player.playWhenReady = true }
                            state == androidx.media3.common.Player.STATE_IDLE || state == androidx.media3.common.Player.STATE_ENDED -> { AppLogger.d("Engine", "Restarting stream (state=$state)"); loadStream(currentServer) }
                            else -> engine.player.play()
                        }
                    },
                    onRewind = { seekBy(-settings.skipLength) },
                    onForward = { seekBy(settings.skipLength) },
                    onSeek = { if (duration > 0) engine.player.seekTo(it.toLong()) },
                    onServerSelect = { idx ->
                        val picked = target.servers.getOrNull(idx) ?: return@PlayerVideoContent
                        userPickedManually = true
                        retryCount = 0
                        currentServer = picked
                    },
                    onVolumeChange = { v ->
                        playerVolume = v
                        engine.player.volume = v
                        muted = v == 0f
                    },
                    onFullscreenToggle = { exitFullscreen(activity) },
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
                    onPlayerBrightnessChange = { v -> playerBrightness = v },
                    onRetry = {
                        error = null
                        allServersFailed = false
                        fallbackIndex = 0
                        retryCount = 0
                        userPickedManually = false
                        loadStream(currentServer)
                    },
                    onSwitchServer = { serverMenu = true },
                    hasMultipleServers = target.servers.size > 1,
                    isFullscreen = true,
                )
            }
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



sealed interface RelatedCardModel {
    val key: String
    
    data class Event(
        val event: com.linkstreamz.app.Event,
        val nowMs: Long,
        val onClick: () -> Unit
    ) : RelatedCardModel {
        override val key = "event_${event.id}"
    }
    
    data class Highlight(
        val highlight: com.linkstreamz.app.Highlight,
        val onClick: () -> Unit
    ) : RelatedCardModel {
        override val key = "hl_${highlight.id}"
    }
    
    data class SingleItem(
        override val key: String,
        val categoryLine: String,
        val imageUrl: String?,
        val initials: String?,
        val name: String,
        val statusText: String?,
        val isLive: Boolean,
        val onClick: () -> Unit
    ) : RelatedCardModel
}

@Composable
private fun RelatedContent(
    repository: EventsRepository?,
    moviesRepo: MoviesRepository?,
    channelsRepo: ChannelsRepository?,
    target: PlayerTarget,
    onTarget: (PlayerTarget) -> Unit
) {
    val eventsState by (repository?.state ?: kotlinx.coroutines.flow.MutableStateFlow(FeedState.Loading)).collectAsStateWithLifecycle()
    val movies = moviesRepo?.movies ?: emptyList()
    val series = moviesRepo?.series ?: emptyList()
    val channels = channelsRepo?.channels ?: emptyList()

    Column(Modifier.fillMaxSize()) {
        if (target is EpisodeTarget) {
            var selectedSeason by remember(target.series) { mutableStateOf(target.season) }
            Text("EPISODES", color = LinkColors.MutedText, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.8.sp, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(target.series.seasons) { season ->
                    val isSelected = season == selectedSeason
                    Box(
                        Modifier.clip(RoundedCornerShape(8.dp)).background(if (isSelected) LinkColors.OrangePrimary else LinkColors.CardBg).clickable { selectedSeason = season }.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Season ${season.number}", color = if (isSelected) Color.Black else LinkColors.PrimaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            selectedSeason.episodes.forEach { ep ->
                val isPlaying = ep == target.episode
                Row(
                    Modifier.fillMaxWidth().background(if (isPlaying) LinkColors.OrangePrimary.copy(alpha = 0.1f) else Color.Transparent).clickable { onTarget(EpisodeTarget(target.series, selectedSeason, ep)) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${ep.n}. ${ep.title}", color = if (isPlaying) LinkColors.OrangePrimary else LinkColors.PrimaryText, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    if (isPlaying) {
                        Text("Playing", color = LinkColors.OrangePrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.border(1.dp, LinkColors.OrangePrimary, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp))
                    } else {
                        Text(ep.duration, color = LinkColors.MutedText, fontSize = 11.sp, fontFamily = MonoFont)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        val sectionTitle = when(target) {
            is HighlightTarget -> "More Highlights"
            is EventTarget -> "More Events"
            is ChannelTarget -> "More Channels"
            is MovieTarget -> "More Movies"
            is EpisodeTarget -> "More Series"
            is CustomTarget -> "Related"
        }

        Text(
            sectionTitle.uppercase(),
            color = LinkColors.MutedText,
            fontFamily = DisplayFont,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp).fillMaxWidth(),
        )
        HorizontalDivider(color = LinkColors.LineAlpha, modifier = Modifier.padding(horizontal = 16.dp))

        if (target is EventTarget || target is HighlightTarget) {
            if (eventsState is FeedState.Loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = LinkColors.OrangePrimary) }
                return@Column
            }
            if (eventsState is FeedState.Error) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Failed to load content", color = LinkColors.MutedText) }
                return@Column
            }
        }

        val cards: List<RelatedCardModel> = when (target) {
            is HighlightTarget -> {
                val feed = (eventsState as? FeedState.Ready)?.feed ?: return@Column
                feed.highlights.filter { it.id != target.highlight.id }.map { RelatedCardModel.Highlight(it) { onTarget(HighlightTarget(it)) } }
            }
            is EventTarget -> {
                val feed = (eventsState as? FeedState.Ready)?.feed ?: return@Column
                val now = System.currentTimeMillis()
                feed.events.filter { effectiveStatus(it.startMs, it.status) == "live" && it.id != target.event.id }.map { RelatedCardModel.Event(it, now) { onTarget(EventTarget(it)) } }
            }
            is ChannelTarget -> {
                channels.filter { it.id != target.channel.id }.sortedBy { it.category != target.channel.category }.map {
                    RelatedCardModel.SingleItem(
                        key = it.id,
                        categoryLine = listOfNotNull(it.category, it.country).joinToString(" | "),
                        imageUrl = it.logo,
                        initials = it.initials,
                        name = it.title,
                        statusText = "LIVE",
                        isLive = true,
                        onClick = { onTarget(ChannelTarget(it)) }
                    )
                }
            }
            is MovieTarget -> {
                movies.filter { it.id != target.movie.id }.sortedBy { it.translated != target.movie.translated }.map {
                    RelatedCardModel.SingleItem(
                        key = it.id,
                        categoryLine = listOfNotNull(it.genre, it.year.toString()).joinToString(" | "),
                        imageUrl = it.thumbUrl,
                        initials = null,
                        name = it.title,
                        statusText = it.duration,
                        isLive = false,
                        onClick = { onTarget(MovieTarget(it)) }
                    )
                }
            }
            is EpisodeTarget -> {
                series.filter { it.id != target.series.id }.mapNotNull {
                    val ep = it.seasons.firstOrNull()?.episodes?.firstOrNull() ?: return@mapNotNull null
                    RelatedCardModel.SingleItem(
                        key = it.id,
                        categoryLine = listOfNotNull(it.genre, it.year.toString()).joinToString(" | "),
                        imageUrl = it.thumbUrl,
                        initials = null,
                        name = it.title,
                        statusText = null,
                        isLive = false,
                        onClick = { onTarget(EpisodeTarget(it, it.seasons.first(), ep)) }
                    )
                }
            }
            is CustomTarget -> emptyList()
        }

        if (cards.isEmpty()) {
            EmptyContentState(Icons.Default.Movie, "No other content available")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 64.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(cards, key = { it.key }) { card ->
                    when (card) {
                        is RelatedCardModel.Event -> EventCard(event = card.event, nowMs = card.nowMs, onClick = card.onClick)
                        is RelatedCardModel.Highlight -> HighlightCard(highlight = card.highlight, onClick = card.onClick)
                        is RelatedCardModel.SingleItem -> SingleItemCard(
                            categoryLine = card.categoryLine,
                            team = Team(name = card.name, crestClass = "", initials = card.initials ?: "", logo = card.imageUrl),
                            statusText = card.statusText,
                            isLive = card.isLive,
                            onClick = card.onClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun EmptyContentState(icon: androidx.compose.ui.graphics.vector.ImageVector, message: String) {
    Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = LinkColors.MutedText.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text(message, color = LinkColors.MutedText, fontSize = 14.sp)
    }
}



private fun enterFullscreen(activity: Activity?) {
    activity ?: return
    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    // Edge-to-edge + immersive using the modern WindowInsetsController API
    androidx.core.view.WindowCompat.setDecorFitsSystemWindows(activity.window, false)
    val controller = androidx.core.view.WindowInsetsControllerCompat(activity.window, activity.window.decorView)
    controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
    controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
        activity.window.attributes = activity.window.attributes.also { attrs ->
            attrs.layoutInDisplayCutoutMode =
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R)
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                else
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }
}

private fun exitFullscreen(activity: Activity?) {
    activity ?: return
    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    androidx.core.view.WindowCompat.setDecorFitsSystemWindows(activity.window, true)
    val controller = androidx.core.view.WindowInsetsControllerCompat(activity.window, activity.window.decorView)
    controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
        activity.window.attributes = activity.window.attributes.also { attrs ->
            attrs.layoutInDisplayCutoutMode =
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
        }
    }
}
