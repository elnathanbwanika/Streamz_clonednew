package com.linkstreamz.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

sealed class EnqueueResult {
    data class Started(val item: DownloadItem) : EnqueueResult()
    data class AlreadyExists(val item: DownloadItem) : EnqueueResult()
    data class Rejected(val reason: String) : EnqueueResult()
}

object DownloadManager {
    private val _items = MutableStateFlow<List<DownloadItem>>(emptyList())
    val items: StateFlow<List<DownloadItem>> = _items.asStateFlow()

    @Volatile private var appContext: Context? = null

    fun init(context: Context) {
        val app = context.applicationContext
        if (appContext == null) {
            appContext = app
            DownloadStore.init(app)
            DownloadStore.recoverInterrupted()
        }
        refresh()
    }

    fun refresh() {
        runCatching { _items.value = DownloadStore.list() }
    }

    fun get(id: String): DownloadItem? = runCatching { DownloadStore.get(id) }.getOrNull()

    fun findByMovieId(movieId: String): DownloadItem? =
        runCatching { DownloadStore.getByMovieId(movieId) }.getOrNull()

    fun downloadMovie(context: Context, movie: Movie): EnqueueResult {
        init(context)
        val source = DownloadSupport.downloadUrl(movie)
        if (!DownloadSupport.canDownload(movie) || source == null) {
            return EnqueueResult.Rejected("Download not available for this title")
        }
        val existing = DownloadStore.getByMovieId(movie.id)
        if (existing != null) {
            if (existing.status == DownloadStatus.FAILED || existing.status == DownloadStatus.PAUSED) {
                resume(context, existing.id)
            } else if (existing.isActive) {
                startService(context, DownloadService.ACTION_START)
            }
            return EnqueueResult.AlreadyExists(existing)
        }
        if (Build.VERSION.SDK_INT <= 28 &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return EnqueueResult.Rejected("Storage permission is needed to save downloads")
        }

        val extension = DownloadSupport.extensionFor(source)
        val fileName = FileNames.singleMovie(movie.title, extension)
        val relativePath = "Link Streamz/Single Movies"
        val mime = mimeFor(extension)
        val now = System.currentTimeMillis()
        val item = DownloadItem(
            id = UUID.randomUUID().toString(),
            movieId = movie.id,
            title = movie.title,
            kind = "movie",
            seriesTitle = null,
            season = null,
            episode = null,
            episodeTitle = null,
            tvBadge = movie.tvBadge,
            thumbUrl = movie.thumbUrl,
            videoUrl = source,
            fileUri = null,
            fileName = fileName,
            relativePath = relativePath,
            mimeType = mime,
            totalBytes = 0L,
            downloadedBytes = 0L,
            status = DownloadStatus.QUEUED,
            error = null,
            createdAt = now,
            updatedAt = now,
        )
        if (!DownloadStore.insert(item)) {
            val raced = DownloadStore.getByMovieId(movie.id)
            return if (raced != null) EnqueueResult.AlreadyExists(raced)
            else EnqueueResult.Rejected("Could not create the download")
        }
        refresh()
        startService(context, DownloadService.ACTION_START)
        return EnqueueResult.Started(item)
    }

    fun downloadEpisode(
        context: Context,
        series: Series,
        season: Season,
        episode: Episode,
    ): EnqueueResult {
        init(context)
        val source = DownloadSupport.downloadUrl(series, episode)
        if (!DownloadSupport.canDownloadEpisode(series, episode) || source == null) {
            return EnqueueResult.Rejected("Download not available for this episode")
        }
        val key = "ep-${series.id}-${season.number}-${episode.n}"
        val existing = DownloadStore.getByMovieId(key)
        if (existing != null) {
            if (existing.status == DownloadStatus.FAILED || existing.status == DownloadStatus.PAUSED) {
                resume(context, existing.id)
            } else if (existing.isActive) {
                startService(context, DownloadService.ACTION_START)
            }
            return EnqueueResult.AlreadyExists(existing)
        }
        if (Build.VERSION.SDK_INT <= 28 &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return EnqueueResult.Rejected("Storage permission is needed to save downloads")
        }

        val extension = DownloadSupport.extensionFor(source)
        val fileName = FileNames.episode(series.title, season.number, episode.n, episode.title, extension)
        val relativePath = "Link Streamz/Series/${FileNames.sanitize(series.title)}"
        val mime = mimeFor(extension)
        val now = System.currentTimeMillis()
        val item = DownloadItem(
            id = UUID.randomUUID().toString(),
            movieId = key,
            title = series.title,
            kind = "episode",
            seriesTitle = series.title,
            season = season.number,
            episode = episode.n,
            episodeTitle = episode.title,
            tvBadge = TvBadge(
                card = episode.tvBadge?.card ?: series.tvBadge?.card,
                player = episode.tvBadge?.player ?: series.tvBadge?.player,
            ).takeIf { it.card != null || it.player != null },
            thumbUrl = episode.thumbUrl.ifBlank { series.thumbUrl },
            videoUrl = source,
            fileUri = null,
            fileName = fileName,
            relativePath = relativePath,
            mimeType = mime,
            totalBytes = 0L,
            downloadedBytes = 0L,
            status = DownloadStatus.QUEUED,
            error = null,
            createdAt = now,
            updatedAt = now,
        )
        if (!DownloadStore.insert(item)) {
            val raced = DownloadStore.getByMovieId(key)
            return if (raced != null) EnqueueResult.AlreadyExists(raced)
            else EnqueueResult.Rejected("Could not create the download")
        }
        refresh()
        startService(context, DownloadService.ACTION_START)
        return EnqueueResult.Started(item)
    }

    data class SeasonDownloadResult(
        val started: Int = 0,
        val alreadyExists: Int = 0,
        val rejected: Int = 0,
    )

    fun downloadSeason(context: Context, series: Series, season: Season): SeasonDownloadResult {
        var started = 0
        var existing = 0
        var rejected = 0
        season.episodes.forEach { episode ->
            when (downloadEpisode(context, series, season, episode)) {
                is EnqueueResult.Started -> started++
                is EnqueueResult.AlreadyExists -> existing++
                is EnqueueResult.Rejected -> rejected++
            }
        }
        return SeasonDownloadResult(started, existing, rejected)
    }

    fun pause(context: Context, id: String) {
        DownloadStore.updateStatus(id, DownloadStatus.PAUSED)
        refresh()
        startService(context, DownloadService.ACTION_PAUSE, id)
    }

    fun resume(context: Context, id: String) {
        DownloadStore.updateStatus(id, DownloadStatus.QUEUED, null)
        refresh()
        startService(context, DownloadService.ACTION_START)
    }

    fun remove(context: Context, item: DownloadItem) {
        if (item.isActive) {
            startService(context, DownloadService.ACTION_CANCEL, item.id)
            return
        }
        deleteFiles(context, item)
        DownloadStore.delete(item.id)
        refresh()
    }

    fun retry(context: Context, id: String) = resume(context, id)

    fun hasPending(): Boolean {
        val context = appContext ?: return false
        return runCatching {
            DownloadStore.init(context)
            DownloadStore.list().any {
                it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING
            }
        }.getOrDefault(false)
    }

    fun resumePendingIfNeeded(context: Context) {
        init(context)
        if (DownloadStore.list().any { it.status == DownloadStatus.QUEUED }) {
            runCatching { startService(context, DownloadService.ACTION_START) }
        }
    }

    internal fun deleteFiles(context: Context, item: DownloadItem) {
        runCatching {
            val uri = item.fileUri?.let(Uri::parse)
            if (uri != null && uri.scheme == "content") {
                context.contentResolver.delete(uri, null, null)
            } else if (uri != null && uri.scheme == "file") {
                uri.path?.let { File(it).delete() }
            } else if (Build.VERSION.SDK_INT <= 28) {
                legacyRoot(item.relativePath)?.let { File(it, item.fileName).delete() }
            }
        }
    }

    internal fun createDestination(context: Context, item: DownloadItem): Uri {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = android.content.ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, item.fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, item.mimeType)
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/" + item.relativePath
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            return context.contentResolver.insert(collection, values)
                ?: throw IllegalStateException("Could not create download file")
        }
        val root = legacyRoot(item.relativePath)
            ?: throw IllegalStateException("Storage is not available")
        if (!root.exists() && !root.mkdirs()) throw IllegalStateException("Could not create download folder")
        val file = File(root, item.fileName)
        if (!file.exists() && !file.createNewFile()) throw IllegalStateException("Could not create download file")
        return Uri.fromFile(file)
    }

    internal fun finishDestination(context: Context, uri: Uri) {
        if (Build.VERSION.SDK_INT >= 29 && uri.scheme == "content") {
            runCatching {
                val values = android.content.ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                context.contentResolver.update(uri, values, null, null)
            }
        } else if (uri.scheme == "file") {
            runCatching {
                uri.path?.let {
                    android.media.MediaScannerConnection.scanFile(context, arrayOf(it), null, null)
                }
            }
        }
    }

    private fun legacyRoot(relativePath: String): File? {
        @Suppress("DEPRECATION")
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return File(downloads, relativePath)
    }

    private fun mimeFor(extension: String): String = when (extension.lowercase()) {
        "mp4", "m4v" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        "avi" -> "video/x-msvideo"
        "ts" -> "video/mp2t"
        "mpg", "mpeg" -> "video/mpeg"
        else -> "application/octet-stream"
    }

    internal fun startService(context: Context, action: String, id: String? = null) {
        val intent = Intent(context, DownloadService::class.java).apply {
            this.action = action
            if (id != null) putExtra(DownloadService.EXTRA_ID, id)
        }
        runCatching { ContextCompat.startForegroundService(context, intent) }
    }
}
