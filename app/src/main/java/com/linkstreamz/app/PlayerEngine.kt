package com.linkstreamz.app

import android.content.Context
import android.net.Uri
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import androidx.media3.exoplayer.DefaultLoadControl

/**
 * Owns a single ExoPlayer instance for the duration of one player screen session.
 * Handles direct playback (with headers), proxied playback (with delay), stall detection,
 * and fallback triggering. Call [release] when the screen is disposed.
 *
 * Supports three playback modes, selected automatically based on the [StreamServer] fields:
 *   • muxed      – single URL carries both video and audio (existing behaviour).
 *   • split      – separate videoUrl + audioUrl; played via MergingMediaSource.
 *   • audio-only – only an audio source; a placeholder is shown in the video surface.
 */

// Stall: how many consecutive 250 ms ticks at the same position triggers fallback.
// 80 × 250 ms = 20 s — generous for HD segments that take time to download.
private const val STALL_TICKS_THRESHOLD = 80

class PlayerEngine(
    private val context: Context,
    private val globalDelaySeconds: Int,
    private val onFallback: () -> Unit,
) {
    /**
     * Creates a DataSource.Factory for ExoPlayer.
     *
     * Only headers configured on the server are applied. Origin/Referer are
     * never synthesised: an IP-bound token usually rejects a request carrying a
     * different Origin than the one that minted it.
     */
    private fun createDataSourceFactory(
        ctx: Context,
        headers: Map<String, String>,
        httpClient: OkHttpClient,
    ): DefaultDataSource.Factory {
        val httpFactory = OkHttpDataSource.Factory(httpClient)
        val requestHeaders = PlayerRequestHeaders.build(headers)
        httpFactory.setDefaultRequestProperties(requestHeaders)
        AppLogger.d("Engine", "DataSourceFactory headers=${requestHeaders.keys}")
        return DefaultDataSource.Factory(ctx, httpFactory)
    }

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs      */ 8_000,   // 8 s min — enough for HD segments
                    /* maxBufferMs      */ 30_000,  // 30 s max — prevents OOM on tablets
                    /* bufferForPlaybackMs           */ 3_000,  // start playing after 3 s ready
                    /* bufferForPlaybackAfterRebufferMs */ 5_000 // 5 s to recover after a stall
                )
                .setPrioritizeTimeOverSizeThresholds(true) // prefer keeping time over byte count
                .build()
        )
        .build()

    init {
        // Disable text tracks (captions/subtitles) by default so they don't show up automatically
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
            .build()

        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                AppLogger.e("Engine", "PlayerError: ${error.errorCodeName}", error)
                var cause: Throwable? = error.cause
                while (cause != null) {
                    AppLogger.e("Engine", "Cause: ${cause.javaClass.simpleName} - ${cause.message}")
                    if (cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                        val host = runCatching { Uri.parse(cause.dataSpec.uri.toString()).host }.getOrNull()
                        AppLogger.e("Engine", "InvalidResponseCode: ${cause.responseCode} host=$host")
                        AppLogger.e("Engine", "  Response headers: ${cause.headerFields.keys}")
                    }
                    cause = cause.cause
                }
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                val stateName = when(playbackState) {
                    1 -> "IDLE"
                    2 -> "BUFFERING"
                    3 -> "READY"
                    4 -> "ENDED"
                    else -> "UNKNOWN"
                }
                AppLogger.d("Engine", "State: $stateName")
            }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                val stack = Thread.currentThread().stackTrace
                    .drop(1).take(8)
                    .joinToString(" < ") { "${it.className.substringAfterLast('.')}.${it.methodName}" }
                AppLogger.d("Engine", "PlayWhenReady: $playWhenReady reason: $reason | $stack")
            }
            override fun onPlaybackSuppressionReasonChanged(reason: Int) {
                AppLogger.d("Engine", "SuppressionReason: $reason")
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                AppLogger.d("Engine", "IsPlaying: $isPlaying")
                // Defer tunnel config swaps while a stream is actively playing.
                WgManager.setPlaybackActive(isPlaying)
            }
        })
    }
    /** True while the proxy is accumulating enough segments to satisfy the delay window. */
    val isSyncing = MutableStateFlow(false)

    // Main.immediate so UI updates are immediate when already on Main.
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var syncingJob: Job? = null
    private var currentProxy: StreamProxyServer? = null

    // Stall detection — polled externally every ~250 ms via checkStall()
    private var lastPosition = 0L
    private var stallCount = 0

    // NOTE: We do NOT add a Player.Listener here.
    // PlayerScreen adds its own engineFallbackListener that calls triggerFallback().
    // Adding one here too would double-fire onFallback() and corrupt fallback state.

    private var loadJob: Job? = null
    /**
     * Canonical key for the currently-loading server.
     * Uses videoUrl (split) or url (muxed/audio-only) so the guard works in all modes.
     */
    private var loadingKey: String? = null

    /** True while a loadStream coroutine is actively running (between setMediaSource and prepare completing). */
    val isLoading: Boolean get() = loadJob?.isActive == true

    /**
     * Returns the canonical identity key for [server], used to detect duplicate loads.
     * Priority: videoUrl (set in split mode) > url (set in muxed mode) > audioUrl (audio-only).
     */
    private fun serverKey(server: StreamServer): String? =
        server.videoUrl ?: server.url ?: server.audioUrl

    /**
     * Loads [server] into the player. Stops any running proxy first.
     * Branches on [serverMode] to select the correct playback pipeline.
     * [isCustom] skips proxy usage (used for the "Play your own link" feature).
     */
    fun loadStream(
        server: StreamServer,
        isCustom: Boolean = false,
        autoplay: Boolean = true,
        bypassTunnel: Boolean = false,
    ) {
        val key = serverKey(server)
        // If we're already loading this exact server, don't cancel and restart — let it finish.
        if (key != null && key == loadingKey && loadJob?.isActive == true) {
            AppLogger.d("Engine", "loadStream: already loading this server, skipping duplicate")
            return
        }

        val testUrl = server.videoUrl ?: server.url ?: server.audioUrl
        if (testUrl != null && (testUrl.startsWith("http://") || testUrl.startsWith("https://"))) {
            val active = ActiveStream(
                label = server.name,
                url = testUrl,
                headers = server.headers,
                bypassTunnel = bypassTunnel,
            )
            StreamDiagnostics.set(active)
            StreamTestRunner.runAutoOnce(context, active)
        } else {
            StreamDiagnostics.set(null)
        }

        loadJob?.cancel()
        loadingKey = key
        loadJob = scope.launch {
            player.playWhenReady = autoplay
            // Tear down previous proxy and reset stall state
            stopProxy()
            stallCount = 0
            lastPosition = 0L

            // Cold start: wait briefly for Android's default network before any
            // client, DNS lookup or data source is created.
            withContext(Dispatchers.IO) {
                NetworkBootstrap.prepareForStream(context, bypassTunnel)
            }
            val dataClient = HttpClients.streamClient(direct = bypassTunnel)
            val mediaClient = dataClient

            val mode = serverMode(server)
            val host = runCatching { Uri.parse(key ?: "").host }.getOrNull().orEmpty()
            AppLogger.d("Engine", "loadStream: mode=$mode host=$host")

            when (mode) {
                // ── MODE: muxed ───────────────────────────────────────────────────
                // Single URL carries both video and audio — existing behaviour.
                "muxed" -> {
                    // Preserve the admin-console URL byte-for-byte. Percent-encoded
                    // spaces/brackets in bucket object names must reach OkHttp as-is.
                    val muxedUrl = DirectFileUrls.exact(server.url!!) // non-null guaranteed by serverMode()
                    val useProxy = if (isCustom) false else server.useProxy
                    val effectiveDelay = server.delaySeconds ?: globalDelaySeconds
                    // Only proxy when explicitly requested by the server config OR a server-specific
                    // delay is set. The global DVR delay must NOT force unrelated streams through
                    // the proxy — those streams may have CDN token auth that the proxy breaks.
                    val serverSpecificDelay = server.delaySeconds ?: 0
                    val path = Uri.parse(muxedUrl).path ?: ""
                    val isM3u8 = path.endsWith(".m3u8", ignoreCase = true) || muxedUrl.contains(".m3u8?")
                    val needsProxy = isM3u8 && !isCustom && (useProxy || serverSpecificDelay > 0)

                    val playUrl: String = if (needsProxy) {
                        // proxy.start() uses runBlocking internally → must run on IO, not Main
                        val proxy = withContext(Dispatchers.IO) {
                            StreamProxyServer(
                                client = mediaClient,
                                sourceUrl = muxedUrl,
                                headers = PlayerRequestHeaders.build(server.headers),
                                delaySeconds = effectiveDelay
                            ).also { it.start() }
                        }
                        currentProxy = proxy

                        // Mirror the proxy's syncing state on the main thread
                        syncingJob = scope.launch {
                            proxy.isSyncing.collect { isSyncing.value = it }
                        }

                        proxy.localMasterUrl
                    } else {
                        isSyncing.value = false
                        muxedUrl
                    }

                    val dataSourceFactory = createDataSourceFactory(context, server.headers, dataClient)
                    // Direct files go through ProgressiveMediaSource explicitly: no
                    // reliance on the file extension or the server's Content-Type.
                    // ExoPlayer will use HTTP Range requests for seeking.
                    val source = if (needsProxy) {
                        hlsSource(playUrl, dataSourceFactory)
                    } else {
                        directSource(playUrl, dataSourceFactory)
                    }

                    AppLogger.d("Engine", "Before setMediaSource (muxed): pwr=${player.playWhenReady} state=${player.playbackState}")
                    player.setMediaSource(source)
                    
                    player.prepare()
                    AppLogger.d("Engine", "After prepare() (muxed): pwr=${player.playWhenReady} state=${player.playbackState}")
                }

                // ── MODE: split ───────────────────────────────────────────────────
                // Separate video URL + separate audio URL — played via MergingMediaSource.
                // ExoPlayer/Media3 handles sample-level synchronization internally.
                // Volume/mute controls act on the player directly (which controls the audio track).
                // No proxy support in split mode (proxy is HLS-specific and muxed-only).
                "split" -> {
                    val videoSrc = server.videoUrl!! // non-null guaranteed by serverMode()
                    val audioSrc = server.audioUrl!! // non-null guaranteed by serverMode()
                    isSyncing.value = false

                    fun buildSource(url: String): androidx.media3.exoplayer.source.MediaSource {
                        val dsf = createDataSourceFactory(context, server.headers, dataClient)
                        return directSource(DirectFileUrls.exact(url), dsf)
                    }

                    val videoSource = buildSource(videoSrc)
                    val audioSource = buildSource(audioSrc)

                    // MergingMediaSource combines the two tracks into a single timeline item.
                    // ExoPlayer renders the video track from videoSource and the audio track
                    // from audioSource, keeping them sample-synced automatically.
                    val mergedSource = MergingMediaSource(videoSource, audioSource)

                    AppLogger.d("Engine", "Split: loading separate video and audio sources")
                    AppLogger.d("Engine", "Before setMediaSource (split): pwr=${player.playWhenReady} state=${player.playbackState}")
                    player.setMediaSource(mergedSource)
                    
                    player.prepare()
                    AppLogger.d("Engine", "After prepare() (split): pwr=${player.playWhenReady} state=${player.playbackState}")
                }

                // ── MODE: audio-only ──────────────────────────────────────────────
                // Only an audio track is available. The PlayerView will show the shutter
                // (black frame / last frame) as a placeholder — no additional UI needed.
                "audio-only" -> {
                    val audioSrc = DirectFileUrls.exact(server.audioUrl!!) // non-null guaranteed by serverMode()
                    isSyncing.value = false

                    val dataSourceFactory = createDataSourceFactory(context, server.headers, dataClient)
                    val source = directSource(audioSrc, dataSourceFactory)

                    AppLogger.d("Engine", "Audio-only: loading audio source")
                    AppLogger.d("Engine", "Before setMediaSource (audio-only): pwr=${player.playWhenReady} state=${player.playbackState}")
                    player.setMediaSource(source)
                    
                    player.prepare()
                    AppLogger.d("Engine", "After prepare() (audio-only): pwr=${player.playWhenReady} state=${player.playbackState}")
                }

                // ── MODE: invalid ─────────────────────────────────────────────────
                // Should never reach here — normalizer filters invalid entries.
                // If it does, trigger fallback so the UI shows the error card.
                else -> {
                    AppLogger.e("Engine", "loadStream: server mode is 'invalid' for key=$key — triggering fallback")
                    withContext(Dispatchers.Main.immediate) { onFallback() }
                }
            }
        }
    }

    /**
     * Call this every ~250 ms from the UI polling loop.
     * Triggers [onFallback] if the player is stuck for ≥ 20 seconds while actively playing.
     *
     * We intentionally skip the check when the player is BUFFERING — the player is doing
     * legitimate work (downloading HD segments) and has not stalled.
     */
    fun checkStall() {
        val state = player.playbackState
        val isActivelyPlaying = state == androidx.media3.common.Player.STATE_READY
            && player.playWhenReady
            && player.isPlaying // only count as stall when we're truly in a playing tick

        if (isActivelyPlaying) {
            val pos = player.currentPosition
            if (pos == lastPosition) {
                stallCount++
                if (stallCount >= STALL_TICKS_THRESHOLD) {
                    stallCount = 0
                    onFallback()
                }
            } else {
                stallCount = 0
            }
            lastPosition = pos
        } else if (state != androidx.media3.common.Player.STATE_BUFFERING) {
            // Reset when idle/ended; don't reset during buffering so a long rebuffer
            // that never recovers will eventually still trigger the fallback on the
            // next READY tick.
            if (state == androidx.media3.common.Player.STATE_IDLE ||
                state == androidx.media3.common.Player.STATE_ENDED) {
                stallCount = 0
            }
        }
    }

    /** Pause on app background. */
    fun pause() {
        val stack = Thread.currentThread().stackTrace.drop(1).take(5)
            .joinToString(" < ") { "${it.className.substringAfterLast('.')}.${it.methodName}" }
        AppLogger.d("Engine", "engine.pause() called! Stack: $stack")
        player.pause()
    }

    /** Resume on app foreground. */
    fun resume() {
        player.play()
    }

    /**
     * Release everything. Must be called when the player screen is disposed.
     *
     * Teardown covers all modes:
     *   • muxed      — proxy stopped, player released.
     *   • split      — MergingMediaSource is discarded automatically by player.release().
     *   • audio-only — player released; no extra resources.
     */
    fun release() {
        AppLogger.d("Engine", "release() called")
        StreamDiagnostics.set(null)
        WgManager.setPlaybackActive(false)
        stopProxy()
        player.release()
        scope.cancel()
    }

    // ── private ─────────────────────────────────────────────────────────────

    /**
     * Progressive source for direct bucket files. This deliberately bypasses
     * Media3's extension/MIME inference: a `.mkv`, an extension-less object, or
     * an `application/octet-stream` response all use the same Range-capable
     * OkHttp data source.
     */
    private fun directSource(
        url: String,
        factory: DefaultDataSource.Factory,
    ): androidx.media3.exoplayer.source.MediaSource =
        ProgressiveMediaSource.Factory(factory)
            .setLoadErrorHandlingPolicy(PlayerRetryPolicy())
            .createMediaSource(androidx.media3.common.MediaItem.fromUri(url))

    /** HLS source for the local delayed-playback proxy only. */
    private fun hlsSource(
        url: String,
        factory: DefaultDataSource.Factory,
    ): androidx.media3.exoplayer.source.MediaSource =
        HlsMediaSource.Factory(factory)
            .setLoadErrorHandlingPolicy(PlayerRetryPolicy())
            .createMediaSource(androidx.media3.common.MediaItem.fromUri(url))

    private fun stopProxy() {
        syncingJob?.cancel()
        syncingJob = null
        currentProxy?.stop()
        currentProxy = null
    }
}
