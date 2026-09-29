package com.linkstreamz.app

import android.content.Context
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
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

    /** True while the proxy is accumulating enough segments to satisfy the delay window. */
    val isSyncing = MutableStateFlow(false)

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var syncingJob: Job? = null
    private var currentProxy: StreamProxyServer? = null

    // Stall detection — polled externally every ~250 ms via checkStall()
    private var lastPosition = 0L
    private var stallCount = 0

    init {
        player.addListener(object : Player.Listener {
            override fun onPlayerError(playbackException: PlaybackException) {
                // Do not log the exception message as it may contain URL details
                onFallback()
            }
        })
    }

    private var loadJob: Job? = null

    /**
     * Loads [server] into the player. Stops any running proxy first.
     * [isCustom] forces the proxy path (used for the "Play your own link" feature).
     */
    fun loadStream(server: StreamServer, isCustom: Boolean = false) {
        loadJob?.cancel()
        loadJob = scope.launch {
            // Tear down previous proxy and reset stall state
            stopProxy()
            stallCount = 0
            lastPosition = 0L
            isSyncing.value = true

            val resolvedServer = kotlinx.coroutines.withContext(Dispatchers.IO) {
                resolveYoutubeDlUrl(server)
            }

            isSyncing.value = false

        val useProxy = if (isCustom) false else resolvedServer.useProxy
        val effectiveDelay = resolvedServer.delaySeconds ?: globalDelaySeconds

        val isMp4 = resolvedServer.url.substringBefore('?').endsWith(".mp4", ignoreCase = true)
        val needsProxy = !isMp4 && (useProxy || effectiveDelay > 0)

        val playUrl: String = if (needsProxy) {
            val proxy = StreamProxyServer(
                client = client,
                sourceUrl = resolvedServer.url,
                headers = resolvedServer.headers,
                delaySeconds = effectiveDelay
            )
            proxy.start()
            currentProxy = proxy

            // Mirror the proxy's syncing state on the main thread
            syncingJob = scope.launch {
                proxy.isSyncing.collect { isSyncing.value = it }
            }

            proxy.localMasterUrl
        } else {
            resolvedServer.url
        }

        val dataSourceFactory = when {
            currentProxy != null -> {
                // Proxy handles headers; player just talks to localhost
                DefaultHttpDataSource.Factory()
            }
            resolvedServer.headers.isNotEmpty() -> {
                OkHttpDataSource.Factory(client)
                    .setDefaultRequestProperties(resolvedServer.headers)
            }
            else -> DefaultHttpDataSource.Factory()
        }

        val mediaItem = androidx.media3.common.MediaItem.fromUri(playUrl)
        val source = if (playUrl.contains(".m3u8", ignoreCase = true) || currentProxy != null) {
            HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
        } else {
            ProgressiveMediaSource.Factory(
                DefaultDataSource.Factory(context, dataSourceFactory)
            ).createMediaSource(mediaItem)
        }

        player.setMediaSource(source)
        player.prepare()
        }
    }

    private fun resolveYoutubeDlUrl(server: StreamServer): StreamServer {
        return try {
            val url = server.url
            val isDirectStream = url.contains(".m3u8") || url.contains(".mp4") || url.contains(".mkv")
            if (!isDirectStream) {
                val request = com.yausername.youtubedl_android.YoutubeDLRequest(url)
                // Get the best video and audio stream combined, or just best if combined isn't available
                request.addOption("-f", "best")
                val info = com.yausername.youtubedl_android.YoutubeDL.getInstance().getInfo(request)
                val extractedUrl = info.url ?: url
                server.copy(url = extractedUrl)
            } else {
                server
            }
        } catch (e: Exception) {
            e.printStackTrace()
            server
        }
    }

    /**
     * Call this every ~250 ms from the UI polling loop.
     * Triggers [onFallback] if the player is stuck for ≥ 12 seconds while it should be playing.
     */
    fun checkStall() {
        if (player.playbackState == Player.STATE_READY && player.playWhenReady) {
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
    fun release() {
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
