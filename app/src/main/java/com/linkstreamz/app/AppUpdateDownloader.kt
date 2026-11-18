package com.linkstreamz.app

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min

data class AppUpdateProgress(
    val downloadedBytes: Long,
    val totalBytes: Long,
    val bytesPerSecond: Long,
    val etaSeconds: Long,
) {
    val fraction: Float get() = AppUpdateMetrics.progress(downloadedBytes, totalBytes)
}

object AppUpdateMetrics {
    fun progress(downloadedBytes: Long, totalBytes: Long): Float =
        if (totalBytes > 0L) {
            (downloadedBytes.toDouble() / totalBytes.toDouble()).coerceIn(0.0, 1.0).toFloat()
        } else {
            0f
        }

    fun speed(bytes: Long, elapsedMs: Long): Long =
        if (bytes <= 0L || elapsedMs <= 0L) 0L else (bytes * 1000L) / elapsedMs

    fun etaSeconds(remainingBytes: Long, bytesPerSecond: Long): Long = when {
        remainingBytes <= 0L -> 0L
        bytesPerSecond <= 0L -> -1L
        else -> (remainingBytes + bytesPerSecond - 1L) / bytesPerSecond
    }

    fun snapshot(
        downloadedBytes: Long,
        totalBytes: Long,
        sessionStartBytes: Long,
        elapsedMs: Long,
    ): AppUpdateProgress {
        val safeTotal = totalBytes.coerceAtLeast(0L)
        val done = downloadedBytes.coerceIn(0L, if (safeTotal > 0L) safeTotal else Long.MAX_VALUE)
        val remaining = (safeTotal - done).coerceAtLeast(0L)
        val speed = speed((done - sessionStartBytes).coerceAtLeast(0L), elapsedMs)
        return AppUpdateProgress(
            downloadedBytes = done,
            totalBytes = safeTotal,
            bytesPerSecond = speed,
            etaSeconds = if (safeTotal > 0L) etaSeconds(remaining, speed) else -1L,
        )
    }
}

sealed interface AppUpdateDownloadState {
    data object Idle : AppUpdateDownloadState
    data class Downloading(val progress: AppUpdateProgress) : AppUpdateDownloadState
    data object Verifying : AppUpdateDownloadState
    data class Ready(val file: File) : AppUpdateDownloadState
    data class Failed(val message: String) : AppUpdateDownloadState
}

object AppUpdateDownloader {
    private const val DIR_NAME = "app_update"
    private const val PART_NAME = "link-streamz-update.apk.part"
    private const val APK_NAME = "link-streamz-update.apk"
    private const val MANIFEST_NAME = "link-streamz-update.json"
    private const val BUFFER_BYTES = 64 * 1024
    private const val PROGRESS_INTERVAL_MS = 500L
    private const val MAX_RETRIES = 5
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<AppUpdateDownloadState>(AppUpdateDownloadState.Idle)
    val state: StateFlow<AppUpdateDownloadState> = _state.asStateFlow()

    private val generation = AtomicInteger(0)
    private val calls = Collections.synchronizedSet(mutableSetOf<Call>())
    @Volatile private var job: Job? = null
    @Volatile private var appContext: Context? = null
    @Volatile private var cancelRequested = false
    @Volatile private var activeSegments: List<SegmentState> = emptyList()

    private val client: OkHttpClient by lazy {
        HttpClients.newBuilder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun onAppStart(context: Context, requirement: AppUpdateRequirement?) {
        init(context)
        val ctx = context.applicationContext
        val dir = updateDir(ctx)
        if (requirement == null) {
            cancel()
            clearEverything(dir)
            return
        }
        val finalFile = File(dir, APK_NAME)
        if (finalFile.exists()) {
            if (finalFile.length() > 0L && verifyApk(ctx, finalFile, requirement)) {
                _state.value = AppUpdateDownloadState.Ready(finalFile)
            } else {
                finalFile.delete()
            }
        }
    }

    fun start(context: Context, requirement: AppUpdateRequirement) {
        if (job?.isActive == true) return
        init(context)
        val gen = generation.incrementAndGet()
        cancelRequested = false
        _state.value = AppUpdateDownloadState.Downloading(AppUpdateProgress(0L, 0L, 0L, -1L))
        job = scope.launch {
            try {
                download(context.applicationContext, requirement)
            } catch (_: CancellationException) {
                if (gen == generation.get()) _state.value = AppUpdateDownloadState.Idle
            } catch (e: Exception) {
                if (e is BadUpdateFile) clearPartial(context)
                if (gen == generation.get()) {
                    _state.value = AppUpdateDownloadState.Failed(friendlyError(e))
                }
            } finally {
                if (gen == generation.get()) job = null
            }
        }
    }

    fun cancel() {
        generation.incrementAndGet()
        cancelRequested = true
        job?.cancel()
        job = null
        abortCalls()
        _state.value = AppUpdateDownloadState.Idle
    }

    fun markFailed(message: String) {
        _state.value = AppUpdateDownloadState.Failed(message)
    }

    fun installationNotCompleted() {
        val current = _state.value
        if (current is AppUpdateDownloadState.Ready) {
            _state.value = AppUpdateDownloadState.Failed("Installation was not completed. Please try again.")
        }
    }

    private suspend fun download(context: Context, requirement: AppUpdateRequirement) {
        val dir = updateDir(context)
        val part = File(dir, PART_NAME)
        val finalFile = File(dir, APK_NAME)
        val manifestFile = File(dir, MANIFEST_NAME)

        if (finalFile.exists()) {
            if (finalFile.length() > 0L && verifyApk(context, finalFile, requirement)) {
                _state.value = AppUpdateDownloadState.Ready(finalFile)
                return
            }
            finalFile.delete()
        }

        _state.value = AppUpdateDownloadState.Downloading(AppUpdateProgress(0L, 0L, 0L, -1L))
        val probe = probe(requirement.apkUrl)
        if (probe.totalBytes <= 0L) throw BadUpdateFile()
        val total = probe.totalBytes
        val sessionStart = if (probe.rangeSupported) resumePosition(part, manifestFile, total, requirement.apkUrl) else 0L
        if (!probe.rangeSupported) {
            part.delete()
            manifestFile.delete()
        }

        RandomAccessFile(part, "rw").use { it.setLength(total) }
        val downloaded = AtomicLong(sessionStart)
        val startedAt = System.currentTimeMillis()
        val monitor = scope.launch {
            while (isActive && !cancelRequested) {
                delay(PROGRESS_INTERVAL_MS)
                if (!isActive || cancelRequested) break
                publish(downloaded.get(), total, sessionStart, startedAt)
                if (probe.rangeSupported && activeSegments.isNotEmpty()) {
                    saveManifest(manifestFile, total, requirement.apkUrl, part, activeSegments.map { it.done.get() })
                }
            }
        }
        try {
            if (probe.rangeSupported) {
                try {
                    downloadSegmented(requirement.apkUrl, total, part, manifestFile, downloaded)
                } catch (_: RangeUnsupported) {
                    abortCalls()
                    activeSegments = emptyList()
                    RandomAccessFile(part, "rw").use { it.setLength(0L) }
                    manifestFile.delete()
                    downloaded.set(0L)
                    downloadSingle(requirement.apkUrl, total, part, downloaded)
                }
            } else {
                downloadSingle(requirement.apkUrl, total, part, downloaded)
            }
            monitor.cancel()
            publish(downloaded.get(), total, sessionStart, startedAt)
            if (part.length() != total) throw BadUpdateFile()
            _state.value = AppUpdateDownloadState.Verifying
            finalFile.delete()
            if (!part.renameTo(finalFile)) throw IOException("Could not finish the update file")
            manifestFile.delete()
            if (!verifyApk(context, finalFile, requirement)) {
                finalFile.delete()
                throw BadUpdateFile()
            }
            _state.value = AppUpdateDownloadState.Ready(finalFile)
        } finally {
            monitor.cancel()
            activeSegments = emptyList()
            if (part.exists() && finalFile.exists()) part.delete()
        }
    }

    private suspend fun downloadSegmented(
        url: String,
        total: Long,
        part: File,
        manifestFile: File,
        downloaded: AtomicLong,
    ) {
        val planned = SegmentPlanner.plan(total, SegmentPlanner.adaptiveConnections(total))
        val existing = readManifest(manifestFile, total, url, planned)
        val segments = planned.mapIndexed { index, seg ->
            SegmentState(seg.index, seg.start, seg.end, AtomicLong(existing.getOrElse(index) { 0L }.coerceIn(0L, seg.length)))
        }
        activeSegments = segments
        downloaded.set(segments.sumOf { it.done.get() })
        saveManifest(manifestFile, total, url, part, segments.map { it.done.get() })

        if (segments.all { it.done.get() == it.length }) return

        RandomAccessFile(part, "rw").use { raf ->
            val channel = raf.channel
            coroutineScope {
                val jobs = segments.map { seg ->
                    launch { downloadSegment(url, seg, channel) }
                }
                jobs.joinAll()
            }
        }
        if (segments.any { it.done.get() != it.length }) throw IOException("Download did not finish")
        downloaded.set(total)
        saveManifest(manifestFile, total, url, part, segments.map { it.done.get() })
    }

    private suspend fun downloadSegment(url: String, seg: SegmentState, channel: java.nio.channels.FileChannel) {
        var attempt = 0
        while (seg.done.get() < seg.length) {
            val startAt = seg.done.get()
            val call = client.newCall(
                Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "*/*")
                    .header("Range", "bytes=${seg.start + startAt}-${seg.end}")
                    .get()
                    .build()
            )
            calls.add(call)
            try {
                call.execute().use { response ->
                    if (response.code == 200) throw RangeUnsupported()
                    if (response.code != 206) throw IOException("Update server returned HTTP ${response.code}")
                    val body = response.body ?: throw IOException("Empty update response")
                    body.byteStream().use { input ->
                        val buffer = ByteArray(BUFFER_BYTES)
                        var position = seg.start + startAt
                        while (seg.done.get() < seg.length) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            if (read == 0) continue
                            val toWrite = min(read, (seg.length - seg.done.get()).toInt())
                            var offset = 0
                            while (offset < toWrite) {
                                val wrote = channel.write(
                                    java.nio.ByteBuffer.wrap(buffer, offset, toWrite - offset),
                                    position + offset,
                                )
                                if (wrote <= 0) throw IOException("Could not write the update file")
                                offset += wrote
                            }
                            seg.done.addAndGet(toWrite.toLong())
                            position += toWrite
                        }
                    }
                }
                if (seg.done.get() < seg.length) throw IOException("Update connection closed early")
                return
            } catch (e: RangeUnsupported) {
                throw e
            } catch (e: Exception) {
                if (cancelRequested) throw CancellationException()
                attempt++
                if (attempt >= MAX_RETRIES) throw e
                delay((400L shl attempt).coerceAtMost(6_000L))
            } finally {
                calls.remove(call)
            }
        }
    }

    private suspend fun downloadSingle(
        url: String,
        expectedTotal: Long,
        part: File,
        downloaded: AtomicLong,
    ) {
        RandomAccessFile(part, "rw").use { it.setLength(0L) }
        downloaded.set(0L)
        var attempt = 0
        while (true) {
            val call = client.newCall(
                Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "*/*")
                    .get()
                    .build()
            )
            calls.add(call)
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Update server returned HTTP ${response.code}")
                    val body = response.body ?: throw IOException("Empty update response")
                    val total = body.contentLength().takeIf { it > 0L } ?: expectedTotal
                    body.byteStream().use { input ->
                        RandomAccessFile(part, "rw").use { raf ->
                            raf.setLength(0L)
                            val buffer = ByteArray(BUFFER_BYTES)
                            var written = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                if (read == 0) continue
                                val remaining = if (total > 0L) total - written else Long.MAX_VALUE
                                if (remaining <= 0L) break
                                val toWrite = min(read.toLong(), remaining).toInt()
                                raf.write(buffer, 0, toWrite)
                                written += toWrite
                                downloaded.set(written)
                                if (total > 0L && written >= total) break
                            }
                        }
                    }
                }
                return
            } catch (e: Exception) {
                if (cancelRequested) throw CancellationException()
                attempt++
                if (attempt >= MAX_RETRIES) throw e
                delay((500L shl attempt).coerceAtMost(8_000L))
            } finally {
                calls.remove(call)
            }
        }
    }

    private suspend fun probe(url: String): Probe {
        var attempt = 0
        var last: Exception? = null
        while (attempt < 4) {
            val call = client.newCall(
                Request.Builder()
                    .url(url)
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
                        if (length > 0L) return Probe(length, false)
                    } else if (response.code == 416) {
                        val total = totalFromContentRange(response.header("Content-Range"))
                        if (total != null && total > 0L) return Probe(total, true)
                    }
                    throw IOException("Update server returned HTTP ${response.code}")
                }
            } catch (e: Exception) {
                last = e
                attempt++
                if (attempt >= 4) break
                delay((500L shl attempt).coerceAtMost(8_000L))
            } finally {
                calls.remove(call)
            }
        }
        throw last ?: IOException("Could not reach the update server")
    }

    private fun publish(downloaded: Long, total: Long, sessionStart: Long, startedAt: Long) {
        _state.value = AppUpdateDownloadState.Downloading(
            AppUpdateMetrics.snapshot(
                downloadedBytes = downloaded,
                totalBytes = total,
                sessionStartBytes = sessionStart,
                elapsedMs = System.currentTimeMillis() - startedAt,
            )
        )
    }

    private fun resumePosition(part: File, manifestFile: File, total: Long, url: String): Long {
        if (!part.exists() || part.length() != total) return 0L
        val planned = SegmentPlanner.plan(total, SegmentPlanner.adaptiveConnections(total))
        val saved = readManifest(manifestFile, total, url, planned)
        return saved.sumOf { it }.coerceIn(0L, total)
    }

    private fun readManifest(file: File, total: Long, url: String, planned: List<DownloadSegment>): List<Long> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val obj = JSONObject(file.readText())
            if (obj.optLong("total") != total) return emptyList()
            if (obj.optInt("urlHash") != url.hashCode()) return emptyList()
            val array = obj.optJSONArray("segments") ?: return emptyList()
            if (array.length() != planned.size) return emptyList()
            (0 until array.length()).map { index ->
                array.optLong(index, 0L).coerceIn(0L, planned[index].length)
            }
        }.getOrDefault(emptyList())
    }

    private fun saveManifest(file: File, total: Long, url: String, part: File, segments: List<Long>) {
        if (!part.exists()) return
        synchronized(this) {
        runCatching {
            val array = JSONArray()
            segments.forEach { array.put(it) }
            file.writeText(
                JSONObject()
                    .put("total", total)
                    .put("urlHash", url.hashCode())
                    .put("segments", array)
                    .toString()
            )
        }
        }
    }

    private fun verifyApk(context: Context, file: File, requirement: AppUpdateRequirement): Boolean {
        if (!file.exists() || file.length() <= 0L) return false
        val info = runCatching {
            context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
        }.getOrNull() ?: return false
        if (info.packageName != context.packageName) return false
        val comparison = AppUpdatePolicy.compareVersions(info.versionName, requirement.minVersion) ?: return false
        return comparison >= 0
    }

    private fun updateDir(context: Context): File =
        File(context.filesDir, DIR_NAME).also { if (!it.exists()) it.mkdirs() }

    private fun clearEverything(dir: File) {
        runCatching {
            dir.listFiles()?.forEach { it.delete() }
        }
    }

    private fun clearPartial(context: Context) {
        val dir = updateDir(context)
        runCatching { File(dir, PART_NAME).delete() }
        runCatching { File(dir, MANIFEST_NAME).delete() }
    }

    private fun abortCalls() {
        val snapshot = synchronized(calls) { calls.toList() }
        snapshot.forEach { runCatching { it.cancel() } }
        calls.clear()
    }

    private fun totalFromContentRange(value: String?): Long? {
        val raw = value?.trim() ?: return null
        return raw.substringAfterLast('/', "").toLongOrNull()?.takeIf { it > 0L }
    }

    private fun friendlyError(error: Exception): String = when (error) {
        is BadUpdateFile -> "The update file is damaged. Please try again."
        is UnknownHostException, is ConnectException, is SocketTimeoutException ->
            "No internet connection. Check your connection and try again."
        is IOException -> {
            val msg = error.message.orEmpty()
            if (msg.contains("ENOSPC", true) || msg.contains("no space", true)) {
                "Not enough storage space to download the update."
            } else {
                "The update could not be downloaded. Please try again."
            }
        }
        else -> "The update could not be downloaded. Please try again."
    }

    private data class Probe(val totalBytes: Long, val rangeSupported: Boolean)
    private data class SegmentState(val index: Int, val start: Long, val end: Long, val done: AtomicLong) {
        val length: Long get() = end - start + 1L
    }
    private class RangeUnsupported : IOException("Server does not support byte ranges")
    private class BadUpdateFile : IOException("Bad update file")
}
