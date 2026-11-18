package com.linkstreamz.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

enum class DownloadStatus { QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED }

data class DownloadItem(
    val id: String,
    val movieId: String,
    val title: String,
    val kind: String,
    val seriesTitle: String?,
    val season: Int?,
    val episode: Int?,
    val episodeTitle: String?,
    val tvBadge: TvBadge?,
    val thumbUrl: String,
    val videoUrl: String,
    val fileUri: String?,
    val fileName: String,
    val relativePath: String,
    val mimeType: String,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val status: DownloadStatus,
    val error: String?,
    val speedBytesPerSecond: Long = 0L,
    val etaSeconds: Long = -1L,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val progress: Float
        get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    val isActive: Boolean get() = status == DownloadStatus.QUEUED || status == DownloadStatus.DOWNLOADING
}

class DownloadDatabase(context: Context) : SQLiteOpenHelper(context, "link_streamz_downloads.db", null, 3) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE downloads (
                id TEXT PRIMARY KEY,
                movie_id TEXT NOT NULL,
                title TEXT NOT NULL,
                kind TEXT NOT NULL,
                series_title TEXT,
                season INTEGER,
                episode INTEGER,
                episode_title TEXT,
                tv_badge_card TEXT,
                tv_badge_player TEXT,
                thumb_url TEXT NOT NULL,
                video_url TEXT NOT NULL,
                file_uri TEXT,
                file_name TEXT NOT NULL,
                relative_path TEXT NOT NULL,
                mime_type TEXT NOT NULL,
                total_bytes INTEGER NOT NULL DEFAULT 0,
                downloaded_bytes INTEGER NOT NULL DEFAULT 0,
                status TEXT NOT NULL,
                error TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                speed_bytes_per_second INTEGER NOT NULL DEFAULT 0,
                eta_seconds INTEGER NOT NULL DEFAULT -1
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE segments (
                download_id TEXT NOT NULL,
                idx INTEGER NOT NULL,
                start_byte INTEGER NOT NULL,
                end_byte INTEGER NOT NULL,
                downloaded INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (download_id, idx)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_downloads_status ON downloads(status)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE downloads ADD COLUMN tv_badge_card TEXT")
            db.execSQL("ALTER TABLE downloads ADD COLUMN tv_badge_player TEXT")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE downloads ADD COLUMN speed_bytes_per_second INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE downloads ADD COLUMN eta_seconds INTEGER NOT NULL DEFAULT -1")
        }
    }
}

object DownloadStore {
    private val lock = Any()
    @Volatile private var db: DownloadDatabase? = null

    fun init(context: Context) {
        if (db == null) synchronized(lock) {
            if (db == null) db = DownloadDatabase(context.applicationContext)
        }
    }

    private fun database(): DownloadDatabase =
        db ?: throw IllegalStateException("DownloadStore.init() not called")

    fun list(): List<DownloadItem> = synchronized(lock) {
        database().readableDatabase.query(
            "downloads", null, null, null, null, null, "created_at DESC"
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(readItem(c))
            }
        }
    }

    fun get(id: String): DownloadItem? = synchronized(lock) {
        database().readableDatabase.query(
            "downloads", null, "id = ?", arrayOf(id), null, null, null
        ).use { c -> if (c.moveToFirst()) readItem(c) else null }
    }

    fun getByMovieId(movieId: String): DownloadItem? = synchronized(lock) {
        database().readableDatabase.query(
            "downloads", null, "movie_id = ?", arrayOf(movieId), null, null, null
        ).use { c -> if (c.moveToFirst()) readItem(c) else null }
    }

    fun insert(item: DownloadItem): Boolean = synchronized(lock) {
        val values = ContentValues().apply {
            put("id", item.id)
            put("movie_id", item.movieId)
            put("title", item.title)
            put("kind", item.kind)
            put("series_title", item.seriesTitle)
            put("season", item.season)
            put("episode", item.episode)
            put("episode_title", item.episodeTitle)
            put("tv_badge_card", item.tvBadge?.card?.storageValue())
            put("tv_badge_player", item.tvBadge?.player?.storageValue())
            put("thumb_url", item.thumbUrl)
            put("video_url", item.videoUrl)
            put("file_uri", item.fileUri)
            put("file_name", item.fileName)
            put("relative_path", item.relativePath)
            put("mime_type", item.mimeType)
            put("total_bytes", item.totalBytes)
            put("downloaded_bytes", item.downloadedBytes)
            put("status", item.status.name)
            put("error", item.error)
            put("created_at", item.createdAt)
            put("updated_at", item.updatedAt)
        }
        database().writableDatabase.insertWithOnConflict("downloads", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L
    }

    fun updateStatus(id: String, status: DownloadStatus, error: String? = null) = synchronized(lock) {
        val values = ContentValues().apply {
            put("status", status.name)
            if (status != DownloadStatus.DOWNLOADING) {
                put("speed_bytes_per_second", 0L)
                put("eta_seconds", -1L)
            }
            put("error", error)
            put("updated_at", System.currentTimeMillis())
        }
        database().writableDatabase.update("downloads", values, "id = ?", arrayOf(id))
    }

    fun updateProgress(id: String, downloaded: Long, total: Long) = synchronized(lock) {
        val values = ContentValues().apply {
            put("downloaded_bytes", downloaded.coerceAtLeast(0))
            if (total > 0) put("total_bytes", total)
            put("updated_at", System.currentTimeMillis())
        }
        database().writableDatabase.update("downloads", values, "id = ?", arrayOf(id))
    }

    fun updateSpeed(id: String, speedBytesPerSecond: Long, etaSeconds: Long) = synchronized(lock) {
        val values = ContentValues().apply {
            put("speed_bytes_per_second", speedBytesPerSecond.coerceAtLeast(0L))
            put("eta_seconds", etaSeconds)
        }
        database().writableDatabase.update("downloads", values, "id = ?", arrayOf(id))
    }

    fun updateFile(
        id: String,
        uri: String,
        total: Long,
        fileName: String,
        relativePath: String,
        mimeType: String,
    ) = synchronized(lock) {
        val values = ContentValues().apply {
            put("file_uri", uri)
            put("total_bytes", total)
            put("file_name", fileName)
            put("relative_path", relativePath)
            put("mime_type", mimeType)
            put("updated_at", System.currentTimeMillis())
        }
        database().writableDatabase.update("downloads", values, "id = ?", arrayOf(id))
    }

    fun delete(id: String) = synchronized(lock) {
        database().writableDatabase.delete("segments", "download_id = ?", arrayOf(id))
        database().writableDatabase.delete("downloads", "id = ?", arrayOf(id))
    }

    fun recoverInterrupted(): Int = synchronized(lock) {
        val values = ContentValues().apply {
            put("status", DownloadStatus.QUEUED.name)
            put("updated_at", System.currentTimeMillis())
        }
        database().writableDatabase.update(
            "downloads", values, "status = ?", arrayOf(DownloadStatus.DOWNLOADING.name)
        )
    }

    fun segments(downloadId: String): List<DownloadSegment> = synchronized(lock) {
        database().readableDatabase.query(
            "segments", null, "download_id = ?", arrayOf(downloadId), null, null, "idx ASC"
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        DownloadSegment(
                            index = c.getInt(c.getColumnIndexOrThrow("idx")),
                            start = c.getLong(c.getColumnIndexOrThrow("start_byte")),
                            end = c.getLong(c.getColumnIndexOrThrow("end_byte")),
                            downloaded = c.getLong(c.getColumnIndexOrThrow("downloaded")),
                        )
                    )
                }
            }
        }
    }

    fun saveSegments(downloadId: String, segments: List<DownloadSegment>) = synchronized(lock) {
        val sqlDb = database().writableDatabase
        sqlDb.beginTransaction()
        try {
            sqlDb.delete("segments", "download_id = ?", arrayOf(downloadId))
            segments.forEach { seg ->
                val values = ContentValues().apply {
                    put("download_id", downloadId)
                    put("idx", seg.index)
                    put("start_byte", seg.start)
                    put("end_byte", seg.end)
                    put("downloaded", seg.downloaded.coerceIn(0L, seg.length))
                }
                sqlDb.insertWithOnConflict("segments", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            sqlDb.setTransactionSuccessful()
        } finally {
            sqlDb.endTransaction()
        }
    }

    private fun readItem(c: android.database.Cursor): DownloadItem = DownloadItem(
        id = c.getString(c.getColumnIndexOrThrow("id")),
        movieId = c.getString(c.getColumnIndexOrThrow("movie_id")),
        title = c.getString(c.getColumnIndexOrThrow("title")),
        kind = c.getString(c.getColumnIndexOrThrow("kind")),
        seriesTitle = c.getString(c.getColumnIndexOrThrow("series_title")),
        season = c.getInt(c.getColumnIndexOrThrow("season")).takeIf { !c.isNull(c.getColumnIndexOrThrow("season")) },
        episode = c.getInt(c.getColumnIndexOrThrow("episode")).takeIf { !c.isNull(c.getColumnIndexOrThrow("episode")) },
        episodeTitle = c.getString(c.getColumnIndexOrThrow("episode_title")),
        tvBadge = TvBadge(
            card = parseStoredPlacement(c.getString(c.getColumnIndexOrThrow("tv_badge_card"))),
            player = parseStoredPlacement(c.getString(c.getColumnIndexOrThrow("tv_badge_player"))),
        ).takeIf { it.card != null || it.player != null },
        thumbUrl = c.getString(c.getColumnIndexOrThrow("thumb_url")),
        videoUrl = c.getString(c.getColumnIndexOrThrow("video_url")),
        fileUri = c.getString(c.getColumnIndexOrThrow("file_uri")),
        fileName = c.getString(c.getColumnIndexOrThrow("file_name")),
        relativePath = c.getString(c.getColumnIndexOrThrow("relative_path")),
        mimeType = c.getString(c.getColumnIndexOrThrow("mime_type")),
        totalBytes = c.getLong(c.getColumnIndexOrThrow("total_bytes")),
        downloadedBytes = c.getLong(c.getColumnIndexOrThrow("downloaded_bytes")),
        status = runCatching {
            DownloadStatus.valueOf(c.getString(c.getColumnIndexOrThrow("status")))
        }.getOrDefault(DownloadStatus.FAILED),
        error = c.getString(c.getColumnIndexOrThrow("error")),
        speedBytesPerSecond = c.getLong(c.getColumnIndexOrThrow("speed_bytes_per_second")),
        etaSeconds = c.getLong(c.getColumnIndexOrThrow("eta_seconds")),
        createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
        updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at")),
    )
}
