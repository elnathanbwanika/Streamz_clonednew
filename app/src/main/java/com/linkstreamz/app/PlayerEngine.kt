package com.linkstreamz.app

import android.content.Context
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
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

/**
 * Owns a single ExoPlayer instance for the duration of one player screen session.
 * Handles direct playback (with headers), proxied playback (with delay), stall detection,
 * and fallback triggering. Call [release] when the screen is disposed.
 */
class PlayerEngine(
    private val context: Context,
    private val client: OkHttpClient,
    private val globalDelaySeconds: Int,
    private val onFallback: () -> Unit
) {
    val player: ExoPlayer = ExoPlayer.Builder(context).build()

    init {
        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                AppLogger.e("Engine", "PlayerError: ${error.errorCodeName}", error)
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
     * Loads [server] into the player. Stops any running proxy first.
     * [isCustom] skips proxy usage (used for the "Play your own link" feature).
     */
    fun loadStream(server: StreamServer, isCustom: Boolean = false, autoplay: Boolean = true) {
        loadJob?.cancel()
        loadJob = scope.launch {
            // Tear down previous proxy and reset stall state
            stopProxy()
            stallCount = 0
            lastPosition = 0L

            val useProxy = if (isCustom) false else server.useProxy
            val effectiveDelay = server.delaySeconds ?: globalDelaySeconds

            val isMp4 = server.url.substringBefore('?').endsWith(".mp4", ignoreCase = true)
            val needsProxy = !isMp4 && (useProxy || effectiveDelay > 0)

            val playUrl: String = if (needsProxy) {
                // proxy.start() uses runBlocking internally → must run on IO, not Main
                val proxy = withContext(Dispatchers.IO) {
                    StreamProxyServer(
                        client = client,
                        sourceUrl = server.url,
                        headers = server.headers,
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
                server.url
            }

            val dataSourceFactory = when {
                currentProxy != null -> {
                    // Proxy handles headers; player just talks to localhost
                    DefaultHttpDataSource.Factory()
                }
                server.headers.isNotEmpty() -> {
                    OkHttpDataSource.Factory(client)
                        .setDefaultRequestProperties(server.headers)
                }
                else -> DefaultHttpDataSource.Factory()
            }

            val mediaItem = androidx.media3.common.MediaItem.fromUri(playUrl)
            val source = if (playUrl.contains(".m3u8", ignoreCase = true) || currentProxy != null) {
                AppLogger.d("Engine", "Using HlsMediaSource for $playUrl")
                HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
            } else {
                AppLogger.d("Engine", "Using ProgressiveMediaSource for $playUrl")
                ProgressiveMediaSource.Factory(
                    DefaultDataSource.Factory(context, dataSourceFactory)
                ).createMediaSource(mediaItem)
            }

            AppLogger.d("Engine", "Before setMediaSource: pwr=${player.playWhenReady} state=${player.playbackState}")
            player.setMediaSource(source)
            AppLogger.d("Engine", "After setMediaSource: pwr=${player.playWhenReady} state=${player.playbackState}")
            player.playWhenReady = autoplay
            AppLogger.d("Engine", "After setPlayWhenReady($autoplay): pwr=${player.playWhenReady} state=${player.playbackState}")
            player.prepare()
            AppLogger.d("Engine", "After prepare(): pwr=${player.playWhenReady} state=${player.playbackState}")

        }
    }

    /**
     * Call this every ~250 ms from the UI polling loop.
     * Triggers [onFallback] if the player is stuck for ≥ 12 seconds while it should be playing.
     */
    fun checkStall() {
        if (player.playbackState == androidx.media3.common.Player.STATE_READY && player.playWhenReady) {
            val pos = player.currentPosition
            if (pos == lastPosition) {
                stallCount++
                if (stallCount >= 48) { // 48 × 250 ms = 12 s
                    stallCount = 0
                    onFallback()
                }
            } else {
                stallCount = 0
            }
            lastPosition = pos
        } else {
            stallCount = 0
        }
    }

    /** Pause on app background. */
    fun pause() {
        player.pause()
    }

    /** Resume on app foreground. */
    fun resume() {
        player.play()
    }

    /** Release everything. Must be called when the player screen is disposed. */
    fun release() { AppLogger.d("Engine", "release() called");
        stopProxy()
        player.release()
        scope.cancel()
    }

    // ── private ─────────────────────────────────────────────────────────────

    private fun stopProxy() {
        syncingJob?.cancel()
        syncingJob = null
        currentProxy?.stop()
        currentProxy = null
    }
}
