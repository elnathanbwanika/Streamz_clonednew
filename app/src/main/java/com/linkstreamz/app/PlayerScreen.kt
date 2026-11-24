package com.linkstreamz.app

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.Manifest
import android.os.Build
import android.os.PowerManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.style.TextAlign
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
    override val subtitle: String = listOf(event.sport, event.league)
        .filter { it.isNotBlank() }
        .joinToString(" | ")
        .let { base ->
            val minute = MatchClock.eventLabel(event)
            if (minute != null && MatchClock.status(event) == "live") "$base · $minute" else base
        }
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
            name = "Auto",
            url  = channel.videoUrl,
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
    override val subtitle: String = movie.vj?.takeIf { it.isNotBlank() } ?: "Link Streamz"
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

data class DownloadedTarget(
    val localUri: String,
    override val title: String,
    override val subtitle: String,
    val tvBadge: TvBadge? = null,
    /** The download behind this playback; lets related content show siblings. */
    val sourceItem: DownloadItem? = null,
    override val selectedServer: StreamServer? = StreamServer("On device", url = localUri),
) : PlayerTarget {
    override val servers: List<StreamServer> = listOf(StreamServer("On device", url = localUri))
    override fun withServer(server: StreamServer) = copy(selectedServer = server)

    companion object {
        /** Build a target directly from a saved download. */
        fun from(item: DownloadItem): DownloadedTarget {
            val uri = item.fileUri.orEmpty()
            return DownloadedTarget(
                localUri = uri,
                title = item.title,
                subtitle = if (item.kind == "episode") {
                    listOfNotNull(
                        item.seriesTitle,
                        item.season?.let { "S$it" },
                        item.episode?.let { "E$it" },
                    ).joinToString(" · ")
                } else {
                    "Downloaded movie"
                },
                tvBadge = item.tvBadge,
                sourceItem = item,
            )
        }
    }
}

private val PlayerTarget.playerTvBadge: TvBadgePlacement?
    get() = when (this) {
        is EventTarget -> event.tvBadge?.player
        is HighlightTarget -> highlight.tvBadge?.player
        is ChannelTarget -> channel.tvBadge?.player
        is MovieTarget -> movie.tvBadge?.player
        is EpisodeTarget -> episode.tvBadge?.player ?: series.tvBadge?.player
        is CustomTarget -> null
        is DownloadedTarget -> tvBadge?.player
    }

private sealed interface PendingDownload {
    data class SingleMovie(val movie: Movie) : PendingDownload
    data class SingleEpisode(val target: EpisodeTarget) : PendingDownload
    data class WholeSeason(val target: EpisodeTarget) : PendingDownload
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
    onOpenDownloads: () -> Unit = {},
    previewMode: Boolean = false,
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val episodeDownloadTarget = target as? EpisodeTarget
    val movieDownloadTarget = target as? MovieTarget
    val downloads by DownloadManager.items.collectAsState()
    val currentDownload = remember(downloads, target) {
        when {
            movieDownloadTarget != null ->
                downloads.firstOrNull { it.movieId == movieDownloadTarget.movie.id }
            episodeDownloadTarget != null ->
                downloads.firstOrNull {
                    it.movieId == "ep-${episodeDownloadTarget.series.id}-" +
                        "${episodeDownloadTarget.season.number}-${episodeDownloadTarget.episode.n}"
                }
            else -> null
        }
    }
    var pendingDownload by remember { mutableStateOf<PendingDownload?>(null) }
    var mobileConfirmDownload by remember { mutableStateOf<PendingDownload?>(null) }
    var showDownloadRationale by remember { mutableStateOf(false) }

    fun enqueueDownload(request: PendingDownload) {
        when (request) {
            is PendingDownload.SingleMovie -> when (val result = DownloadManager.downloadMovie(context, request.movie)) {
                is EnqueueResult.Started -> Toast.makeText(context, "Download started", Toast.LENGTH_SHORT).show()
                is EnqueueResult.AlreadyExists -> Toast.makeText(context, "Already in Downloads", Toast.LENGTH_SHORT).show()
                is EnqueueResult.Rejected -> Toast.makeText(context, result.reason, Toast.LENGTH_LONG).show()
            }
            is PendingDownload.SingleEpisode -> when (val result = DownloadManager.downloadEpisode(
                context,
                request.target.series,
                request.target.season,
                request.target.episode,
            )) {
                is EnqueueResult.Started -> Toast.makeText(context, "Download started", Toast.LENGTH_SHORT).show()
                is EnqueueResult.AlreadyExists -> Toast.makeText(context, "Already in Downloads", Toast.LENGTH_SHORT).show()
                is EnqueueResult.Rejected -> Toast.makeText(context, result.reason, Toast.LENGTH_LONG).show()
            }
            is PendingDownload.WholeSeason -> {
                val result = DownloadManager.downloadSeason(context, request.target.series, request.target.season)
                val message = when {
                    result.started > 0 -> "Added ${result.started} episode${if (result.started == 1) "" else "s"} to Downloads"
                    result.alreadyExists > 0 -> "Episodes already in Downloads"
                    else -> "No episodes available to download"
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    val downloadPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val request = pendingDownload
        pendingDownload = null
        val storageDenied = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            grants[Manifest.permission.WRITE_EXTERNAL_STORAGE] == false
        if (storageDenied) {
            Toast.makeText(context, "Storage permission is needed to save downloads", Toast.LENGTH_LONG).show()
        } else if (request != null) {
            enqueueDownload(request)
        }
    }

    fun requestDownloadPermissions(request: PendingDownload) {
        val missing = buildList {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
            ) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (missing.isEmpty()) {
            enqueueDownload(request)
        } else {
            pendingDownload = request
            showDownloadRationale = true
        }
    }

    fun requestDownload(request: PendingDownload) {
        if (DownloadConditions.hasLowStorage(context)) {
            Toast.makeText(context, DownloadConditions.LOW_STORAGE_MESSAGE, Toast.LENGTH_LONG).show()
            return
        }
        if (DownloadConditions.isMetered(context)) {
            mobileConfirmDownload = request
        } else {
            requestDownloadPermissions(request)
        }
    }

    // Time-limited movies: block playback outside the availability window and
    // stop playback when the window closes while watching.
    val timeLimited = (target as? MovieTarget)?.movie?.timeLimited
    var timeLimitedState by remember(timeLimited) {
        mutableStateOf(timeLimited?.state(ServerClock.now()))
    }
    LaunchedEffect(timeLimited) {
        while (timeLimited != null) {
            timeLimitedState = timeLimited.state(ServerClock.now())
            delay(1_000L)
        }
    }
    if (timeLimited != null && timeLimitedState != TimeLimitedState.LIVE) {
        TimeLimitedBlockScreen(
            title = target.title,
            state = timeLimitedState ?: TimeLimitedState.ENDED,
            timeLimited = timeLimited,
            onBack = onBack,
        )
        return
    }

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
    // Set by loadStream to signal LaunchedEffect to run extraction. The attempt
    // counter is part of the effect key: retries keep the same video id, so a
    // key based on the id alone would never re-extract after a failure.
    var ytExtractId by remember { mutableStateOf<String?>(null) }
    var ytExtractStream by remember { mutableStateOf<StreamServer?>(null) }
    var ytExtractAttempt by remember { mutableIntStateOf(0) }

    // ── Playback resume (everything except live TV / live events) ─────────────
    // The key comes from PlaybackResume.keyFor so the resume policy lives in one
    // place: movies, episodes, highlights, downloads and custom streams resume;
    // live channels and live events return null and never touch the store.
    val resumeKeyForTarget = remember(target) { PlaybackResume.keyFor(target) }
    val currentResumeKey by rememberUpdatedState(resumeKeyForTarget)
    val bypassTunnel = ContentRouting.bypassTunnel(target)
    val itemTvBadge = remember(target) { target.playerTvBadge }
    val playerTvBadge = if (currentServer?.showTvBadge == false) null else itemTvBadge
    var resumePositionMs by remember { mutableLongStateOf(0L) }
    // True once we've applied the saved seek — prevents seeking again on reconnect/retry.
    var hasAppliedResume by remember { mutableStateOf(false) }
    // The key whose position was loaded into resumePositionMs (not "movie" only).
    var loadedResumeKey by remember { mutableStateOf<String?>(null) }
    // Throttle counter so we save position only every ~2 s (8 × 250 ms) not every tick.
    var resumeSaveTick by remember { mutableIntStateOf(0) }
    // Player-local brightness: 100f = no overlay (neutral); lower = darker, higher = brighter (white wash).
    // Range 5f..300f. Default 100f (neutral — no overlay).
    var playerBrightness by remember { mutableFloatStateOf(100f) }

    // Auto-hide controls state
    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteractionMs by remember { mutableStateOf(System.currentTimeMillis()) }

    // ── PlayerEngine (owns ExoPlayer + HLS proxy) ────────────────────────────
    val engine = remember {
        PlayerEngine(
            context = context,
            globalDelaySeconds = settings.streamDelaySeconds,
        ) {
            // onFallback — runs on main thread via engine's scope
        }
    }

    // Collect engine syncing state
    val isSyncing by engine.isSyncing.collectAsStateWithLifecycle()
    val updateRequired by AppUpdateStore.required.collectAsStateWithLifecycle()
    LaunchedEffect(updateRequired) {
        if (updateRequired != null) engine.pause()
    }

    // ── Picture-in-Picture ───────────────────────────────────────────────────
    // The activity owns the platform PiP session; the player instance is the
    // same one used on screen, so expanding back never rebuffers.
    val pipHost = activity as? MainActivity
    // Read inside a snapshot-observed scope: `by` on the State makes Compose
    // subscribe, so entering/leaving PiP actually recomposes this screen.
    val isInPipState by (pipHost?.isInPipState ?: remember { mutableStateOf(false) })
    val isPipTransitioning by (pipHost?.isPipTransitioningState ?: remember { mutableStateOf(false) })
    // Single decision point, shared with the unit tests: the layout mode depends
    // ONLY on the two PiP flags. Playback state, lifecycle events, touches,
    // controller visibility and window resizes cannot reach this call at all.
    val isInPip =
        PipLayoutSpec.layoutMode(
            isInPictureInPictureMode = isInPipState,
            isTransitioningToPip = isPipTransitioning,
        ) == PipLayoutMode.VIDEO_ONLY

    // Per-target PiP identity. Keyed ONLY on the target: pausing from the PiP
    // window must never tear this down, because detachPip() would drop the
    // controller and (historically) clear the PiP flag, letting the normal page
    // reappear inside the floating window.
    DisposableEffect(pipHost, target) {
        pipHost?.attachPip(engine.player)
        pipHost?.setPipLive(PipActions.isLiveTarget(target))
        pipHost?.setPipCloseHandler { engine.player.pause() }
        pipHost?.setPipTitle(
            title = target.title,
            subtitle = target.subtitle.ifBlank { "Link Streamz" },
        )
        onDispose { pipHost?.detachPip() }
    }

    // Playback-dependent PiP chrome. These only push params; they never change
    // the layout mode, which is derived from the platform PiP state alone.
    LaunchedEffect(pipHost, playing) {
        pipHost?.setPipPlaying(playing)
        if (playing) pipHost?.enableAutoEnter() else pipHost?.disableAutoEnter()
    }

    // Re-derive the layout mode from the platform flag when this screen mounts,
    // so a pause/buffer/error can never leave the normal page showing in PiP.
    // The lifecycle callbacks keep it in sync afterwards.
    LaunchedEffect(pipHost) {
        pipHost?.refreshPipState("player-composed")
    }

    LaunchedEffect(isInPip) {
        if (isInPip) controlsVisible = false
    }


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

    // ── Resume persistence ────────────────────────────────────────────────────
    // One place that writes the position, so every teardown path (leaving the
    // screen, switching targets, backgrounding, the 2s tick) saves the same way
    // and for the same content. Live targets have no key and are skipped.
    fun persistResumePosition() {
        val key = loadedResumeKey ?: return
        if (key != currentResumeKey) return
        PlaybackResume.save(
            context,
            key,
            engine.player.currentPosition.coerceAtLeast(0L),
            engine.player.duration,
        )
    }

    DisposableEffect(engine) {
        engine.player.addListener(engineFallbackListener)
        onDispose {
            persistResumePosition()
            engine.player.removeListener(engineFallbackListener)
            engine.release()
        }
    }

    DisposableEffect(resumeKeyForTarget) {
        onDispose { persistResumePosition() }
    }

    var resumeOnForeground by remember { mutableStateOf(false) }

    // ── Background pause ─────────────────────────────────────────────────────
    // Use the Activity lifecycle owner (not LocalLifecycleOwner which gives the
    // NavBackStackEntry lifecycle). The NavBackStackEntry lifecycle fires ON_STOP
    // during nav transitions, which was pausing the player immediately on load.
    val activityLifecycleOwner = remember(activity) {
        activity as? androidx.lifecycle.LifecycleOwner
    }
    DisposableEffect(activityLifecycleOwner, bypassTunnel) {
        val owner = activityLifecycleOwner ?: return@DisposableEffect onDispose {}
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    persistResumePosition()
                    // In PiP the activity is stopped but still visible and
                    // controllable from the floating window. Pausing here would
                    // fight the PiP play/pause action (and resume() on ON_START
                    // would undo it), so leave playback to the user.
                    if (!FloatingOverlayManager.isFloating.value && pipHost?.isInPipNow != true) {
                        resumeOnForeground = engine.player.playWhenReady
                        engine.pause()
                    } else {
                        // Entering/leaving PiP must never arm an auto-resume:
                        // otherwise expanding would restart playback that the
                        // user deliberately paused from the floating window.
                        resumeOnForeground = false
                    }
                }
                Lifecycle.Event.ON_START -> {
                    // Never resume on our own while in PiP: the user may have
                    // deliberately paused from the floating window.
                    if (resumeOnForeground && pipHost?.isInPipNow != true) {
                        val state = engine.player.playbackState
                        if (state == androidx.media3.common.Player.STATE_IDLE ||
                            state == androidx.media3.common.Player.STATE_ENDED) {
                            // Player died while backgrounded — reload the current server
                            AppLogger.d("Engine", "ON_START: player in IDLE/ENDED, reloading")
                            currentServer?.let { engine.loadStream(it, userPickedManually, true, bypassTunnel) }
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
        // In PiP the system draws its own overlay and handles touches; tapping
        // the window must never bring the app's controls (or any chrome) back.
        if (isInPip) return
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

        // Resume lookup: keyed by the target's resume key, so episodes, highlights,
        // downloads and custom streams resume too. Live targets have a null key.
        val resumeKey = currentResumeKey
        if (resumeKey != null) {
            if (loadedResumeKey != resumeKey) {
                loadedResumeKey = resumeKey
                hasAppliedResume = false
                resumePositionMs = PlaybackResume.get(context, resumeKey)
            }
        } else {
            loadedResumeKey = null
            hasAppliedResume = false
            resumePositionMs = 0L
        }

        // YT-EXTRACT: Detect YouTube URLs at load time — branch to async extraction
        val streamUrl = stream.url
        if (streamUrl != null && YouTubeUrl.isYouTubeUrl(streamUrl)) {
            val videoId = YouTubeUrl.youtubeVideoId(streamUrl)
            if (videoId == null) {
                error = "This link could not be opened."
                return
            }
            // Signal the LaunchedEffect to begin extraction
            ytExtractStream = stream
            ytExtractId = videoId
            ytExtractAttempt++
        } else {
            // YT-EXTRACT: Normal (non-YouTube) playback path — unchanged
            ytExtractId = null
            ytExtractStream = null
            isExtracting = false
            engine.loadStream(stream, isCustom, settings.autoplay, bypassTunnel)
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
                // ── Resume: seek to saved position on first STATE_READY ────────
                if (playbackState == androidx.media3.common.Player.STATE_READY &&
                    currentResumeKey != null && loadedResumeKey == currentResumeKey &&
                    !hasAppliedResume) {
                    hasAppliedResume = true
                    if (resumePositionMs > 0L) engine.player.seekTo(resumePositionMs)
                }
                // ── Resume: clear saved position when the video finishes ───────
                if (playbackState == androidx.media3.common.Player.STATE_ENDED && currentResumeKey != null) {
                    PlaybackResume.markFinished(context, currentResumeKey!!)
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
    LaunchedEffect(currentServer, previewMode, bypassTunnel) {
        if (!previewMode) loadStream(currentServer)
    }

    // YT-EXTRACT: Async extraction — runs when ytExtractId changes (set by loadStream).
    // This LaunchedEffect is inside the composable scope so scope/launch are available.
    // Cancels automatically if ytExtractId changes (user switches video) or composable leaves.
    LaunchedEffect(ytExtractId, ytExtractAttempt, bypassTunnel) {
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
                engine.loadStream(ephemeral, isCustom, settings.autoplay, bypassTunnel)
                engine.player.volume = if (muted) 0f else 1f
            }
            is YtResult.Split -> {
                // YT-EXTRACT: split — separate video+audio URLs, maps to server.videoUrl+audioUrl
                val ephemeral = stream.copy(url = null, videoUrl = result.videoUrl, audioUrl = result.audioUrl)
                engine.loadStream(ephemeral, isCustom, settings.autoplay, bypassTunnel)
                engine.player.volume = if (muted) 0f else 1f
            }
            is YtResult.Failure -> {
                // Generic codes let support identify the failure stage without
                // exposing any provider name in the player UI.
                error = when (result.reason) {
                    YtError.FATAL -> "This video could not be loaded. Please try again. (E1)"
                    YtError.UNKNOWN -> "This video could not be loaded. Please try again. (E2)"
                    YtError.RECAPTCHA -> "This video could not be loaded. Please try again. (E3)"
                    YtError.RATE_LIMIT -> "Too many requests right now. Please try again in a few minutes. (E4)"
                    YtError.AGE_RESTRICTED -> "This video isn't available here. (E5)"
                    YtError.GEO_RESTRICTED -> "This video isn't available in your region. (E6)"
                    YtError.LIVE_STREAM -> "Live streams aren't supported. (E7)"
                    YtError.NO_STREAMS -> "Could not load this video. Try another server. (E8)"
                }
                AppLogger.e("Playback", "Resolve failed: ${result.reason} — ${result.message}")
                // Try next server before showing error card
                if (target.servers.size > 1 && target.servers.indexOf(stream) < target.servers.lastIndex) {
                    error = null
                    triggerFallback()
                }
            }
        }
    }

    // ── Same-server retry (triggered by triggerFallback when retries remain) ─
    LaunchedEffect(retryTrigger, bypassTunnel) {
        if (retryTrigger == 0) return@LaunchedEffect // skip initial composition
        delay(2_000L) // brief pause lets transient network errors clear
        loadStream(currentServer)
    }

    LaunchedEffect(Unit) {
        while (true) {
            position = engine.player.currentPosition.coerceAtLeast(0L)
            duration = engine.player.duration
            engine.checkStall()
            // ── Resume: persist position every ~2 s while a video is open ──
            // Only after the initial seek has been applied, so the tick cannot
            // overwrite the saved position with 0 before we jump to it.
            if (currentResumeKey != null && loadedResumeKey == currentResumeKey && hasAppliedResume) {
                resumeSaveTick++
                if (resumeSaveTick >= 8) {
                    resumeSaveTick = 0
                    PlaybackResume.save(context, currentResumeKey!!, position, duration)
                }
            }
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
        // ── PiP: video only ───────────────────────────────────────────────────
        // While the activity is in picture-in-picture the whole window is tiny, so
        // NOTHING except the video surface may be composed: no header/back/download
        // bar, no rounded frame or orange border, no padding, no gradients, no
        // "More movies" list. The window aspect ratio is set from the real video
        // size and the surface uses FIT, so the complete frame is visible with no
        // cropping and no letterboxing.
        if (isInPip) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            ) {
                PlayerVideoContent(
                    exoPlayer = exoPlayer,
                    isFloating = false,
                    onHideFloat = {},
                    playerBrightness = playerBrightness,
                    controlsVisible = false,
                    isExtracting = isExtracting,
                    isBuffering = isBuffering,
                    isSyncing = isSyncing,
                    playing = playing,
                    settings = settings,
                    seekMessage = null,
                    error = error,
                    controlsState = controlsState,
                    onPlayPause = { engine.player.play() },
                    onRewind = {},
                    onForward = {},
                    onSeek = {},
                    onServerSelect = {},
                    onVolumeChange = {},
                    onFullscreenToggle = {},
                    onFloatWindow = {},
                    onInteraction = {},
                    onHideControls = {},
                    onPlayerBrightnessChange = {},
                    onRetry = { error = null },
                    onSwitchServer = {},
                    hasMultipleServers = false,
                    isFullscreen = false,
                    tvBadge = null,
                    isInPip = true,
                    onVideoSizeChanged = { w, h -> pipHost?.setPipVideoSize(w, h) },
                    onVideoAspectRatio = { ratio -> pipHost?.setPipVideoAspectRatio(ratio) },
                )
            }
        } else if (!isFs) {
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
                PlayerBar(
                    target = target,
                    onBack = onBack,
                    download = currentDownload,
                    onDownload = when {
                        movieDownloadTarget != null &&
                            DownloadSupport.canDownload(movieDownloadTarget.movie) ->
                            ({ requestDownload(PendingDownload.SingleMovie(movieDownloadTarget.movie)) })
                        episodeDownloadTarget != null &&
                            DownloadSupport.canDownloadEpisode(episodeDownloadTarget.series, episodeDownloadTarget.episode) ->
                            ({ requestDownload(PendingDownload.SingleEpisode(episodeDownloadTarget)) })
                        else -> null
                    },
                    onPlayDownloaded = currentDownload?.fileUri
                        ?.takeIf { it.isNotBlank() && currentDownload.status == DownloadStatus.COMPLETED }
                        ?.let {
                            {
                                onTarget(DownloadedTarget.from(currentDownload))
                            }
                        },
                    onOpenDownloads = onOpenDownloads,
                    onDownloadSeason = if (episodeDownloadTarget != null &&
                        episodeDownloadTarget.season.episodes.any {
                            DownloadSupport.canDownloadEpisode(episodeDownloadTarget.series, it)
                        }
                    ) {
                        ({ requestDownload(PendingDownload.WholeSeason(episodeDownloadTarget)) })
                    } else null,
                )

                // Portrait frame: 8dp outer, 1.5dp orange border, 3dp dark gap, 3.5dp inner
                val outerShape = RoundedCornerShape(LinkDimens.PlayerOuterRadius)
                val innerShape = RoundedCornerShape(LinkDimens.PlayerInnerRadius)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .aspectRatio(16f / 9f)
                        .clip(outerShape)
                        .border(LinkDimens.PlayerBorderWidth, LinkColors.LineAlpha, outerShape)
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
                            onFloatWindow = { pipHost?.enterPip() },
                            onInteraction = ::touchControls,
                            onHideControls = { controlsVisible = false },
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
                            tvBadge = playerTvBadge,
                            isInPip = isInPip,
                            onVideoSizeChanged = { w, h -> pipHost?.setPipVideoSize(w, h) },
                    onVideoAspectRatio = { ratio -> pipHost?.setPipVideoAspectRatio(ratio) },
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
                    onFloatWindow = { pipHost?.enterPip() },
                    onInteraction = ::touchControls,
                    onHideControls = { controlsVisible = false },
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
                    tvBadge = playerTvBadge,
                    isInPip = isInPip,
                    onVideoSizeChanged = { w, h -> pipHost?.setPipVideoSize(w, h) },
                    onVideoAspectRatio = { ratio -> pipHost?.setPipVideoAspectRatio(ratio) },
                )
            }
        }
    }

    mobileConfirmDownload?.let { request ->
        AlertDialog(
            onDismissRequest = { mobileConfirmDownload = null },
            containerColor = LinkColors.CardBg,
            title = { Text("Download over mobile data?", color = LinkColors.PrimaryText) },
            text = { Text(DownloadConditions.MOBILE_DATA_MESSAGE, color = LinkColors.MutedText) },
            confirmButton = {
                AppButton("Download", {
                    mobileConfirmDownload = null
                    requestDownloadPermissions(request)
                })
            },
            dismissButton = {
                AppButton("Cancel", { mobileConfirmDownload = null }, variant = AppButtonVariant.Secondary)
            },
        )
    }

    if (showDownloadRationale) {
        AlertDialog(
            onDismissRequest = { showDownloadRationale = false; pendingDownload = null },
            containerColor = LinkColors.CardBg,
            title = { Text("Allow download permissions", color = LinkColors.PrimaryText) },
            text = {
                Text(
                    "Notifications show download progress. On Android 9 and older, storage access saves the file to your Downloads folder.",
                    color = LinkColors.MutedText,
                )
            },
            confirmButton = {
                AppButton("Continue", {
                    showDownloadRationale = false
                    val missing = buildList {
                        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                        ) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    if (missing.isEmpty()) {
                        pendingDownload?.let { enqueueDownload(it) }
                        pendingDownload = null
                    } else {
                        downloadPermissionLauncher.launch(missing.toTypedArray())
                    }
                })
            },
            dismissButton = {
                AppButton(
                    "Not now",
                    { showDownloadRationale = false; pendingDownload = null },
                    variant = AppButtonVariant.Text,
                )
            },
        )
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
    download: DownloadItem? = null,
    onDownload: (() -> Unit)? = null,
    onPlayDownloaded: (() -> Unit)? = null,
    onOpenDownloads: (() -> Unit)? = null,
    onDownloadSeason: (() -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth().padding(14.dp, 14.dp, 16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AppIconButton(Icons.Default.ArrowBack, "Back", onBack)
        Column(Modifier.weight(1f)) {
            Text(target.title.uppercase(), color = LinkColors.PrimaryText, fontFamily = DisplayFont, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(target.subtitle.uppercase(), color = LinkColors.MutedText, fontFamily = MonoFont, fontSize = 11.sp, letterSpacing = .5.sp, modifier = Modifier.padding(top = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        when {
            download?.status == DownloadStatus.COMPLETED && onPlayDownloaded != null ->
                PlayerBarAction(Icons.Default.PlayCircle, "Play downloaded", onPlayDownloaded)
            download != null && download.isActive -> {
                Box(
                    Modifier.size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(LinkColors.CardBg)
                        .clickable { onOpenDownloads?.invoke() },
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        progress = { download.progress },
                        modifier = Modifier.size(20.dp),
                        color = LinkColors.OrangePrimary,
                        trackColor = LinkColors.OrangePrimary.copy(alpha = 0.18f),
                        strokeWidth = 2.dp,
                    )
                }
            }
            download != null &&
                (download.status == DownloadStatus.PAUSED || download.status == DownloadStatus.FAILED) &&
                onDownload != null ->
                PlayerBarAction(Icons.Default.Refresh, "Resume download", onDownload)
            onDownload != null ->
                PlayerBarAction(Icons.Default.Download, "Download", onDownload)
        }
        if (onDownloadSeason != null) {
            PlayerBarAction(Icons.Default.DownloadForOffline, "Download season", onDownloadSeason)
        }
    }
}

@Composable
private fun PlayerBarAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) = AppIconButton(icon, description, onClick)



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
    val relatedImageLoader = if (ContentRouting.bypassTunnel(target)) {
        AppImages.direct(LocalContext.current)
    } else {
        null
    }

    Column(Modifier.fillMaxSize()) {
        if (target is DownloadedTarget) {
            DownloadedRelatedContent(target, onTarget)
            return@Column
        }

        if (target is EpisodeTarget) {
            var selectedSeason by remember(target.series) { mutableStateOf(target.season) }
            Text("EPISODES", color = LinkColors.MutedText, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.8.sp, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(target.series.seasons) { season ->
                    AppChip(
                        label = "Season ${season.number}",
                        selected = season == selectedSeason,
                        onClick = { selectedSeason = season },
                        contentDescription = "Season ${season.number}",
                    )
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
            is DownloadedTarget -> "On this device"
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
                feed.events.filter { MatchClock.status(it) == "live" && it.id != target.event.id }.map { RelatedCardModel.Event(it, now) { onTarget(EventTarget(it)) } }
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
            is DownloadedTarget -> emptyList()
        }

        if (cards.isEmpty()) {
            EmptyContentState(Icons.Default.Movie, "No other content available")
        } else if (target is MovieTarget) {
            // ── Movies: 3-column portrait-card grid (same style as ViewAllScreen) ──
            val movieItems = movies.filter { it.id != target.movie.id }
                .sortedBy { it.translated != target.movie.translated }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 64.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(movieItems, key = { it.id }) { movie ->
                    MediaCard(item = movie, onClick = { onTarget(MovieTarget(movie)) }, fullWidth = true)
                }
            }
        } else if (target is ChannelTarget) {
            // ── Channels: 4-column grid with same ChannelCard structure ──
            val relatedChannels = channels.filter { it.id != target.channel.id }
                .sortedBy { it.category != target.channel.category }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 76.dp),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 64.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(relatedChannels, key = { it.id }) { ch ->
                    ChannelCard(channel = ch, onClick = { onTarget(ChannelTarget(ch)) })
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 64.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(cards, key = { it.key }) { card ->
                    when (card) {
                        is RelatedCardModel.Event     -> EventCard(event = card.event, nowMs = card.nowMs, onClick = card.onClick)
                        is RelatedCardModel.Highlight -> HighlightCard(highlight = card.highlight, onClick = card.onClick)
                        is RelatedCardModel.SingleItem -> SingleItemCard(
                            categoryLine = card.categoryLine,
                            team = Team(name = card.name, crestClass = "", initials = card.initials ?: "", logo = card.imageUrl),
                            statusText = card.statusText,
                            isLive = card.isLive,
                            onClick = card.onClick,
                            imageLoader = relatedImageLoader,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Related content for locally saved playback.
 *
 * Movies get the other completed downloads as cards. Episodes get every
 * downloaded episode of the same series, with season chips for quick picking.
 * Tapping a card switches the player to that saved file without leaving the
 * player screen.
 */
@Composable
private fun DownloadedRelatedContent(
    target: DownloadedTarget,
    onTarget: (PlayerTarget) -> Unit,
) {
    val downloads by DownloadManager.items.collectAsStateWithLifecycle()
    val source = target.sourceItem
    val ready = remember(downloads) {
        downloads.filter { it.status == DownloadStatus.COMPLETED && !it.fileUri.isNullOrBlank() }
    }
    val imageLoader = AppImages.direct(LocalContext.current)

    if (source == null || source.kind != "episode") {
        val movies = ready.filter { it.kind != "episode" && it.id != source?.id }
        Text(
            "MORE DOWNLOADED MOVIES",
            color = LinkColors.MutedText,
            fontFamily = DisplayFont,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
        )
        HorizontalDivider(color = LinkColors.LineAlpha, modifier = Modifier.padding(horizontal = 16.dp))
        if (movies.isEmpty()) {
            EmptyContentState(Icons.Default.Movie, "No other downloaded movies")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 64.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(movies, key = { it.id }) { item ->
                    SingleItemCard(
                        categoryLine = "DOWNLOADED",
                        team = Team(name = item.title, crestClass = "", initials = "", logo = item.thumbUrl),
                        statusText = "On this device",
                        isLive = false,
                        onClick = { onTarget(DownloadedTarget.from(item)) },
                        imageLoader = imageLoader,
                    )
                }
            }
        }
        return
    }

    val seriesTitle = source.seriesTitle ?: source.title
    val episodes = remember(ready, seriesTitle) {
        ready.filter { it.kind == "episode" && (it.seriesTitle ?: it.title) == seriesTitle }
            .sortedWith(compareBy({ it.season ?: 0 }, { it.episode ?: 0 }))
    }
    val seasons = remember(episodes) { episodes.mapNotNull { it.season }.distinct().sorted() }
    var selectedSeason by remember(seriesTitle, source.season) {
        mutableStateOf(source.season ?: seasons.firstOrNull())
    }

    Text(
        "EPISODES · $seriesTitle".uppercase(),
        color = LinkColors.MutedText,
        fontFamily = DisplayFont,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(AppControlSpec.ChipGap),
    ) {
        items(seasons, key = { it }) { season ->
            AppChip(
                label = "Season $season",
                selected = season == selectedSeason,
                onClick = { selectedSeason = season },
                contentDescription = "Season $season",
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    val visible = episodes.filter { selectedSeason == null || it.season == selectedSeason }
    if (visible.isEmpty()) {
        EmptyContentState(Icons.Default.Tv, "No other downloaded episodes")
    } else {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 64.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(visible, key = { it.id }) { item ->
                val isPlaying = item.id == source.id
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (isPlaying) LinkColors.OrangePrimary.copy(alpha = 0.1f) else Color.Transparent)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !isPlaying) { onTarget(DownloadedTarget.from(item)) }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "E${item.episode ?: "-"} · ${item.episodeTitle ?: item.title}",
                            color = if (isPlaying) LinkColors.OrangePrimary else LinkColors.PrimaryText,
                            fontSize = 13.sp,
                            fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (item.season != null) {
                            Text(
                                "Season ${item.season}",
                                color = LinkColors.MutedText,
                                fontSize = 10.sp,
                                fontFamily = MonoFont,
                            )
                        }
                    }
                    if (isPlaying) {
                        Text(
                            "Playing",
                            color = LinkColors.OrangePrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.border(1.dp, LinkColors.OrangePrimary, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    } else {
                        Icon(Icons.Default.PlayArrow, null, tint = LinkColors.OrangePrimary, modifier = Modifier.size(18.dp))
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

/**
 * Placeholder shown behind the floating PiP window.
 *
 * Dark surface, orange accent, a small PiP glyph and centred text — no outline
 * or border, so it reads as a calm backdrop rather than a card.
 */
@Composable
internal fun FloatingWindowPlaceholder(onTap: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(LinkColors.ScreenBg)
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = Icons.Default.PictureInPictureAlt,
                contentDescription = null,
                tint = LinkColors.OrangePrimary,
                modifier = Modifier.size(44.dp),
            )
            Text(
                text = "Playing in a floating window",
                color = LinkColors.PrimaryText,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Tap to return to the player",
                color = LinkColors.MutedText,
                fontFamily = MonoFont,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )
        }
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

@Composable
private fun TimeLimitedBlockScreen(
    title: String,
    state: TimeLimitedState,
    timeLimited: TimeLimited,
    onBack: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize().background(LinkColors.ScreenBg).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Lock, null, tint = LinkColors.OrangePrimary, modifier = Modifier.size(42.dp))
            Spacer(Modifier.height(16.dp))
            Text(title, color = LinkColors.PrimaryText, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(10.dp))
            val message = when (state) {
                TimeLimitedState.UPCOMING -> "Not started yet. Available in ${timeLimitedStartCountdown(timeLimited)}."
                TimeLimitedState.LIVE -> "Available now."
                TimeLimitedState.ENDED -> "This movie's availability window has ended."
            }
            Text(message, color = LinkColors.MutedText, fontFamily = BodyFont, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                "Available ${formatEatDateTime(timeLimited.startAtMs)} EAT for ${formatDuration(timeLimited.durationHours)}",
                color = LinkColors.MutedText,
                fontFamily = MonoFont,
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(22.dp))
            AppButton("Go back", onBack)
        }
    }
}
