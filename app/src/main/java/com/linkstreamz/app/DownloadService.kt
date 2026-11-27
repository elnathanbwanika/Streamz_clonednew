package com.linkstreamz.app

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

class DownloadService : Service() {

    companion object {
        const val ACTION_START = "com.linkstreamz.app.download.START"
        const val ACTION_PAUSE = "com.linkstreamz.app.download.PAUSE"
        const val ACTION_CANCEL = "com.linkstreamz.app.download.CANCEL"
        const val EXTRA_ID = "download_id"

        private const val NOTIFICATION_ID = 7101
        private const val MAX_CONCURRENT = 2
        private const val MAX_RETRIES = 5
        private const val BUFFER_BYTES = 64 * 1024
        private const val PROGRESS_INTERVAL_MS = 900L
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tasks = ConcurrentHashMap<String, DownloadTask>()
    private val scheduleLock = Any()
    private val client: OkHttpClient by lazy {
        HttpClients.newBuilder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .followRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile private var foregroundStarted = false

    @Volatile private var stopping = false

    override fun onCreate() {
        super.onCreate()
        DownloadStore.init(this)
        DownloadStore.recoverInterrupted()
        DownloadManager.init(this)
        NotificationChannels.ensure(this)
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "LinkStreamz:Downloads",
        ).apply { setReferenceCounted(false) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopping = false
        startForegroundNow()

        when (intent?.action) {
            ACTION_PAUSE -> {
                val id = intent.getStringExtra(EXTRA_ID)
                if (id != null) {
                    val task = tasks[id]
                    if (task != null) task.requestPause()
                    else DownloadStore.updateStatus(id, DownloadStatus.PAUSED)
                    DownloadManager.refresh()
                }
            }
            ACTION_CANCEL -> {
                val id = intent.getStringExtra(EXTRA_ID)
                if (id != null) {
                    val task = tasks[id]
                    if (task != null) {
                        task.requestCancel()
                    } else {
                        DownloadStore.get(id)?.let { item ->
                            DownloadManager.deleteFiles(this, item)
                            DownloadStore.delete(id)
                        }
                        DownloadManager.refresh()
                    }
                }
            }
            ACTION_START -> {
                val id = intent.getStringExtra(EXTRA_ID)
                if (id != null) {
                    val existing = tasks[id]
                    if (existing != null) existing.resumeRequested()
                    else {
                        DownloadStore.get(id)?.let { item ->
                            if (item.status == DownloadStatus.PAUSED || item.status == DownloadStatus.FAILED) {
                                DownloadStore.updateStatus(id, DownloadStatus.QUEUED)
                                DownloadManager.refresh()
                            }
                            startTask(item)
                        }
                    }
                }
            }
            null -> Unit
        }

        schedule()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        tasks.values.forEach { it.requestStop() }
        releaseWakeLock()
        super.onDestroy()
    }

    private fun schedule() {
        scope.launch {
            synchronized(scheduleLock) {
                if (stopping) return@launch
                val queued = DownloadStore.list().filter { it.status == DownloadStatus.QUEUED }
                for (item in queued) {
                    if (tasks.size >= MAX_CONCURRENT) break
                    if (tasks.containsKey(item.id)) continue
                    startTask(item)
                }
                val hasWork = tasks.isNotEmpty() ||
                    DownloadStore.list().any { it.status == DownloadStatus.QUEUED }
                if (!hasWork) {
                    stopping = true
                    releaseWakeLock()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    private fun startTask(item: DownloadItem) {
        if (tasks.containsKey(item.id)) return
        DownloadStore.updateStatus(item.id, DownloadStatus.DOWNLOADING)
        ensureWakeLock()
        val task = DownloadTask(item)
        tasks[item.id] = task
        scope.launch {
            try {
                task.run()
            } catch (e: Exception) {
                AppLogger.e("DownloadService", "Unhandled download failure for ${item.id}", e)
                DownloadStore.updateStatus(item.id, DownloadStatus.FAILED, UserFacingError.message(e))
                DownloadManager.refresh()
            } finally {
                tasks.remove(item.id)
                DownloadManager.refresh()
                schedule()
            }
        }
    }

    private fun startForegroundNow() {
        if (foregroundStarted) return
        val notification = buildSummaryNotification()
        val type = if (Build.VERSION.SDK_INT >= 29) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
            foregroundStarted = true
        }.onFailure { AppLogger.e("DownloadService", "startForeground failed", it) }
    }

    private fun updateSummary() {
        if (!foregroundStarted) return
        runCatching {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildSummaryNotification())
        }
    }

    private fun buildSummaryNotification(): Notification {
        val items = DownloadStore.list()
        val active = items.filter {
            it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING
        }
        val paused = items.filter { it.status == DownloadStatus.PAUSED }
        val total = active.sumOf { it.totalBytes.coerceAtLeast(0L) }
        val done = active.sumOf { it.downloadedBytes.coerceAtLeast(0L) }
        val indeterminate = active.isEmpty() || total <= 0L || done > total
        val percent = if (total > 0L) ((done * 100L) / total).toInt().coerceIn(0, 100) else 0

        val title = when {
            active.size == 1 -> "Downloading ${active.first().title}"
            active.size > 1 -> "Downloading ${active.size} items"
            paused.isNotEmpty() -> "Downloads paused"
            else -> "Preparing download"
        }
        val speed = if (active.size == 1) {
            tasks[active.first().id]?.currentSpeed() ?: active.first().speedBytesPerSecond
        } else {
            active.sumOf { tasks[it.id]?.currentSpeed() ?: it.speedBytesPerSecond }
        }
        val eta = if (active.size == 1) {
            tasks[active.first().id]?.currentEta() ?: active.first().etaSeconds
        } else {
            val remaining = (total - done).coerceAtLeast(0L)
            if (speed > 0L) (remaining + speed - 1L) / speed else -1L
        }
        val text = when {
            active.isNotEmpty() && speed > 0L && total > 0L ->
                "${formatBytes(done)} of ${formatBytes(total)} · ${formatTransferSpeed(speed)} · ${formatTransferEta(eta)}"
            active.isNotEmpty() -> "${formatBytes(done)} of ${formatBytes(total)}"
            paused.isNotEmpty() -> "${paused.size} download(s) waiting to resume"
            else -> "Starting..."
        }

        val open = PendingIntent.getActivity(
            this,
            7102,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("notification_target", "downloads")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val action = when {
            active.size == 1 -> intentFor(ACTION_PAUSE, active.first().id) to "Pause"
            active.isEmpty() && paused.size == 1 -> intentFor(ACTION_START, paused.first().id) to "Resume"
            active.isEmpty() && paused.size > 1 -> intentFor(ACTION_START, paused.first().id) to "Resume"
            else -> null
        }

        return NotificationCompat.Builder(this, NotificationChannels.DOWNLOADS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setProgress(100, percent, indeterminate)
            .applyAction(action)
            .build()
    }

    private fun intentFor(action: String, id: String): Intent =
        Intent(this, DownloadService::class.java).apply {
            this.action = action
            putExtra(EXTRA_ID, id)
        }

    private fun NotificationCompat.Builder.applyAction(
        action: Pair<Intent, String>?,
    ): NotificationCompat.Builder {
        if (action != null) {
            addAction(
                0,
                action.second,
                PendingIntent.getService(
                    this@DownloadService,
                    7103,
                    action.first,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
        return this
    }

    private fun ensureWakeLock() {
        wakeLock?.let { lock ->
            if (!lock.isHeld) runCatching { lock.acquire(30 * 60 * 1000L) }
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock ->
            if (lock.isHeld) runCatching { lock.release() }
        }
    }

    private inner class DownloadTask(private val item: DownloadItem) {
        val id: String = item.id

        @Volatile private var pauseRequested = false
        @Volatile private var cancelRequested = false
        @Volatile private var stopRequested = false
        private val calls: MutableSet<Call> = Collections.synchronizedSet(mutableSetOf())
        private val downloaded = AtomicLong(item.downloadedBytes.coerceAtLeast(0L))
        private var currentUri: Uri? = item.fileUri?.let(Uri::parse)
        private var totalBytes = item.totalBytes
        private var currentSegments: List<DownloadSegment>? = null
        private val speedEstimator = TransferSpeedEstimator()

        /** Validated but byte-for-byte unchanged: percent escapes stay intact. */
        private val sourceUrl: String by lazy { DirectFileUrls.exact(item.videoUrl) }

        @Volatile private var speedBytesPerSecond: Long = 0L
        @Volatile private var etaSeconds: Long = -1L

        fun requestPause() {
            pauseRequested = true
            abortCalls()
        }

        fun requestCancel() {
            cancelRequested = true
            abortCalls()
        }

        fun requestStop() {
            stopRequested = true
            abortCalls()
        }

        fun resumeRequested() {
            pauseRequested = false
            cancelRequested = false
            stopRequested = false
        }

        fun currentSpeed(): Long = speedBytesPerSecond

        fun currentEta(): Long = etaSeconds

        private fun abortCalls() {
            synchronized(calls) { calls.toList() }.forEach { runCatching { it.cancel() } }
        }

        suspend fun run() {
            var handle: ChannelHandle? = null
            try {
                val uri = currentUri ?: DownloadManager.createDestination(this@DownloadService, item).also {
                    currentUri = it
                    DownloadStore.updateFile(
                        id,
                        it.toString(),
                        item.totalBytes,
                        item.fileName,
                        item.relativePath,
                        item.mimeType,
                    )
                }
                handle = openHandle(uri)
                val probe = probe()
                if (probe.totalBytes > 0L) {
                    totalBytes = probe.totalBytes
                    DownloadStore.updateFile(
                        id,
                        uri.toString(),
                        totalBytes,
                        item.fileName,
                        item.relativePath,
                        item.mimeType,
                    )
                }

                if (probe.rangeSupported && probe.totalBytes > 0L) {
                    try {
                        runSegmented(handle)
                    } catch (_: RangeUnsupported) {
                        runSingle(handle)
                    }
                } else {
                    runSingle(handle)
                }

                checkInterrupted()
                handle.channel.force(true)
                if (totalBytes > 0L && downloaded.get() != totalBytes) {
                    if (downloaded.get() > totalBytes) handle.channel.truncate(totalBytes)
                    else throw IOException("Downloaded size did not match the file size")
                }
                handle.close()
                handle = null

                DownloadManager.finishDestination(this@DownloadService, uri)
                totalBytes = downloaded.get()
                DownloadStore.updateProgress(id, totalBytes, totalBytes)
                DownloadStore.updateStatus(id, DownloadStatus.COMPLETED)
                DownloadManager.refresh()
                notifyComplete(item.title)
            } catch (_: DownloadInterrupted) {
                handle?.let { persist() }
                handle?.close()
                when {
                    cancelRequested -> deleteThisDownload()
                    pauseRequested -> DownloadStore.updateStatus(id, DownloadStatus.PAUSED)
                    stopRequested -> DownloadStore.updateStatus(id, DownloadStatus.QUEUED)
                    else -> DownloadStore.updateStatus(id, DownloadStatus.PAUSED)
                }
                DownloadManager.refresh()
            } catch (_: CancellationException) {
                handle?.let { persist() }
                handle?.close()
                DownloadStore.updateStatus(id, DownloadStatus.PAUSED)
                DownloadManager.refresh()
            } catch (e: Exception) {
                handle?.let { persist() }
                handle?.close()
                when {
                    cancelRequested -> deleteThisDownload()
                    pauseRequested -> DownloadStore.updateStatus(id, DownloadStatus.PAUSED)
                    stopRequested -> DownloadStore.updateStatus(id, DownloadStatus.QUEUED)
                    else -> {
                        AppLogger.e("DownloadService", "Download failed: ${item.title}", e)
                        DownloadStore.updateStatus(
                            id,
                            DownloadStatus.FAILED,
                            UserFacingError.message(e),
                        )
                    }
                }
                DownloadManager.refresh()
            } finally {
                handle?.close()
            }
        }

        private fun deleteThisDownload() {
            DownloadManager.deleteFiles(
                this@DownloadService,
                item.copy(fileUri = currentUri?.toString()),
            )
            DownloadStore.delete(id)
            DownloadManager.refresh()
        }

        private fun openHandle(uri: Uri): ChannelHandle {
            if (uri.scheme == "content") {
                val pfd: ParcelFileDescriptor = contentResolver.openFileDescriptor(uri, "rw")
                    ?: throw IOException("Could not open the download file")
                val stream = FileOutputStream(pfd.fileDescriptor)
                return ChannelHandle(stream.channel) {
                    runCatching { pfd.close() }
                }
            }
            val path = uri.path ?: throw IOException("Invalid download path")
            val raf = RandomAccessFile(File(path), "rw")
            return ChannelHandle(raf.channel) {
                runCatching { raf.close() }
            }
        }

        private fun checkInterrupted() {
            if (pauseRequested || cancelRequested || stopRequested) throw DownloadInterrupted()
        }

        private suspend fun probe(): Probe {
            var attempt = 0
            var last: Exception? = null
            while (attempt < 4) {
                checkInterrupted()
                val call = client.newCall(
                    Request.Builder()
                        .url(sourceUrl)
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "*/*")
                        .header("Range", "bytes=0-0")
                        .get()
                        .build()
                )
                calls.add(call)
                try {
                    call.execute().use { response ->
                        if (response.code == 206) {
                            val total = totalFromContentRange(response.header("Content-Range"))
                            if (total != null && total > 0L) return Probe(total, true)
                        } else if (response.code == 200) {
                            val length = response.body?.contentLength() ?: -1L
                            return Probe(length.coerceAtLeast(0L), false)
                        } else if (response.code == 416) {
                            val total = totalFromContentRange(response.header("Content-Range"))
                            if (total != null && total > 0L) return Probe(total, true)
                        }
                        throw IOException("Server returned HTTP ${response.code}")
                    }
                } catch (e: Exception) {
                    checkInterrupted()
                    last = e
                    attempt++
                    if (attempt >= 4) break
                    delay((500L shl attempt).coerceAtMost(8_000L))
                } finally {
                    calls.remove(call)
                }
            }
            throw last ?: IOException("Could not reach the download server")
        }

        private fun totalFromContentRange(value: String?): Long? {
            val raw = value?.trim() ?: return null
            return raw.substringAfterLast('/', "").toLongOrNull()?.takeIf { it > 0L }
        }

        suspend fun runSegmented(handle: ChannelHandle) {
            val total = totalBytes
            if (total <= 0L) throw IOException("Unknown download size")
            val planned = SegmentPlanner.plan(total, SegmentPlanner.adaptiveConnections(total))
            val stored = DownloadStore.segments(id)
            val resumable = stored.isNotEmpty() &&
                stored.size == planned.size &&
                stored.all { seg ->
                    seg.index in planned.indices &&
                        seg.start == planned[seg.index].start &&
                        seg.end == planned[seg.index].end
                } &&
                stored.sumOf { it.length } == total
            val segments = if (resumable) SegmentPlanner.mergeCompleted(stored, planned) else planned

            currentSegments = segments
            if (!resumable) handle.channel.truncate(0L)
            downloaded.set(SegmentPlanner.totalDownloaded(snapshot(segments)))
            speedEstimator.reset()
            DownloadStore.saveSegments(id, snapshot(segments))
            DownloadStore.updateProgress(id, downloaded.get(), total)
            DownloadManager.refresh()
            updateSummary()

            if (segments.all { it.isComplete }) {
                DownloadStore.updateProgress(id, total, total)
                return
            }

            coroutineScope {
                val monitor = launch {
                    while (isActive) {
                        delay(PROGRESS_INTERVAL_MS)
                        publish()
                    }
                }
                val jobs = segments.map { seg ->
                    launch { downloadSegment(seg, handle.channel) }
                }
                try {
                    jobs.joinAll()
                } catch (e: Exception) {
                    jobs.forEach { it.cancel() }
                    abortCalls()
                    throw e
                } finally {
                    monitor.cancelAndJoin()
                    publish()
                }
            }

            checkInterrupted()
            if (segments.any { !it.isComplete }) throw IOException("Download did not finish")
            downloaded.set(total)
            handle.channel.force(true)
            val size = handle.channel.size()
            if (size < total) throw IOException("Saved file is smaller than expected")
            if (size > total) handle.channel.truncate(total)
        }

        private suspend fun downloadSegment(seg: DownloadSegment, channel: FileChannel) {
            var attempt = 0
            while (!seg.isComplete) {
                checkInterrupted()
                val rangeStart = seg.nextByte
                val call = client.newCall(
                    Request.Builder()
                        .url(sourceUrl)
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "*/*")
                        .header("Range", "bytes=$rangeStart-${seg.end}")
                        .get()
                        .build()
                )
                calls.add(call)
                try {
                    call.execute().use { response ->
                        checkInterrupted()
                        if (response.code == 200) throw RangeUnsupported()
                        if (response.code == 416) {
                            if (seg.isComplete) return
                            throw IOException("Server rejected the requested range")
                        }
                        if (response.code != 206) {
                            throw IOException("Server returned HTTP ${response.code}")
                        }
                        val body = response.body ?: throw IOException("Empty response body")
                        body.byteStream().use { input ->
                            var position = seg.nextByte
                            val buffer = ByteArray(BUFFER_BYTES)
                            while (true) {
                                checkInterrupted()
                                val read = input.read(buffer)
                                if (read < 0) break
                                if (read == 0) continue
                                channel.write(ByteBuffer.wrap(buffer, 0, read), position)
                                position += read
                                synchronized(seg) {
                                    seg.downloaded =
                                        (seg.downloaded + read).coerceAtMost(seg.length)
                                }
                            }
                        }
                        if (!seg.isComplete) {
                            throw IOException("Connection closed before the segment finished")
                        }
                    }
                } catch (e: Exception) {
                    checkInterrupted()
                    if (e is RangeUnsupported) throw e
                    attempt++
                    if (attempt >= MAX_RETRIES) {
                        throw IOException("Download failed after $MAX_RETRIES attempts", e)
                    }
                    delay((500L shl min(attempt, 5)).coerceAtMost(10_000L))
                } finally {
                    calls.remove(call)
                }
            }
        }

        suspend fun runSingle(handle: ChannelHandle) {
            currentSegments = null
            coroutineScope {
                val monitor = launch {
                    while (isActive) {
                        delay(PROGRESS_INTERVAL_MS)
                        publish()
                    }
                }
                try {
                    var attempt = 0
                    while (true) {
                        checkInterrupted()
                        try {
                            handle.channel.truncate(0L)
                            handle.channel.position(0L)
                            downloaded.set(0L)
                            speedEstimator.reset()
                            DownloadStore.updateProgress(id, 0L, totalBytes)
                            updateSummary()

                            val call = client.newCall(
                                Request.Builder()
                                    .url(sourceUrl)
                                    .header("User-Agent", USER_AGENT)
                                    .header("Accept", "*/*")
                                    .get()
                                    .build()
                            )
                            calls.add(call)
                            try {
                                call.execute().use { response ->
                                    checkInterrupted()
                                    if (response.code != 200) {
                                        throw IOException("Server returned HTTP ${response.code}")
                                    }
                                    val body = response.body
                                        ?: throw IOException("Empty response body")
                                    val expected = body.contentLength()
                                    var position = 0L
                                    body.byteStream().use { input ->
                                        val buffer = ByteArray(BUFFER_BYTES)
                                        while (true) {
                                            checkInterrupted()
                                            val read = input.read(buffer)
                                            if (read < 0) break
                                            if (read == 0) continue
                                            handle.channel.write(ByteBuffer.wrap(buffer, 0, read), position)
                                            position += read
                                            downloaded.set(position)
                                        }
                                    }
                                    if (expected > 0L && position != expected) {
                                        throw IOException("Download ended early")
                                    }
                                    totalBytes = if (totalBytes > 0L) totalBytes else position
                                    downloaded.set(position)
                                }
                            } finally {
                                calls.remove(call)
                            }
                            break
                        } catch (e: Exception) {
                            checkInterrupted()
                            attempt++
                            if (attempt >= 3) {
                                throw IOException("Download failed after $attempt attempts", e)
                            }
                            delay((1_000L shl attempt).coerceAtMost(8_000L))
                        }
                    }
                } finally {
                    monitor.cancelAndJoin()
                    publish()
                }
            }
        }

        private fun snapshot(segments: List<DownloadSegment>): List<DownloadSegment> =
            segments.map { seg ->
                synchronized(seg) {
                    seg.copy(downloaded = seg.downloaded.coerceIn(0L, seg.length))
                }
            }

        private fun publish() {
            val segments = currentSegments
            val done = if (segments != null) {
                val copy = snapshot(segments)
                DownloadStore.saveSegments(id, copy)
                SegmentPlanner.totalDownloaded(copy)
            } else {
                downloaded.get()
            }
            val estimate = speedEstimator.update(done, System.currentTimeMillis())
            if (estimate > 0L) speedBytesPerSecond = estimate
            etaSeconds = speedEstimator.etaSeconds(done, totalBytes, speedBytesPerSecond)
            DownloadStore.updateProgress(id, done, totalBytes)
            DownloadStore.updateSpeed(id, speedBytesPerSecond, etaSeconds)
            DownloadManager.refresh()
            updateSummary()
        }

        private fun persist() {
            val segments = currentSegments
            if (segments != null) {
                val copy = snapshot(segments)
                DownloadStore.saveSegments(id, copy)
                DownloadStore.updateProgress(id, SegmentPlanner.totalDownloaded(copy), totalBytes)
            } else {
                DownloadStore.updateProgress(id, downloaded.get(), totalBytes)
            }
        }
    }

    private fun notifyComplete(title: String) {
        runCatching {
            NotificationChannels.ensure(this)
            val open = PendingIntent.getActivity(
                this,
                title.hashCode(),
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("notification_target", "downloads")
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(this, NotificationChannels.DOWNLOADS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Download complete")
                .setContentText(title)
                .setContentIntent(open)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            NotificationManagerCompat.from(this).notify(title.hashCode(), notification)
        }
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes <= 0L -> "0 MB"
        bytes >= 1L shl 30 -> String.format(Locale.US, "%.1f GB", bytes / 1_073_741_824.0)
        else -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
    }

    private class ChannelHandle(
        val channel: FileChannel,
        private val closer: () -> Unit,
    ) {
        private var closed = false

        fun close() {
            if (closed) return
            closed = true
            runCatching { closer() }
        }
    }

    private data class Probe(val totalBytes: Long, val rangeSupported: Boolean)

    private class DownloadInterrupted : Exception()

    private class RangeUnsupported : IOException("Range not supported")
}
