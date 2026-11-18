package com.linkstreamz.app

// ─────────────────────────────────────────────────────────────────────────────
// Movies & Channels domain models
// ─────────────────────────────────────────────────────────────────────────────

data class Movie(
    val id: String,
    val title: String,
    val genre: String,
    val year: Int,
    val vj: String?,
    val translated: Boolean,
    val thumbUrl: String,
    val videoUrl: String,
    val duration: String,
    val tvBadge: TvBadge? = null,
    val timeLimited: TimeLimited? = null,
    val allowDownload: Boolean = true,
    val servers: List<StreamServer> = emptyList(),
)

data class Episode(
    val n: Int,
    val title: String,
    val thumbUrl: String,
    val videoUrl: String,
    val duration: String,
    val tvBadge: TvBadge? = null,
    val allowDownload: Boolean = true,
    val servers: List<StreamServer> = emptyList(),
)

data class Season(
    val number: Int,
    val episodes: List<Episode>,
)

data class Series(
    val id: String,
    val title: String,
    val genre: String,
    val year: Int,
    val vj: String?,
    val translated: Boolean,
    val thumbUrl: String,
    val seasons: List<Season>,
    val tvBadge: TvBadge? = null,
    val allowDownload: Boolean = true,
    val servers: List<StreamServer> = emptyList(),
)

data class Channel(
    val id: String,
    val title: String,
    val category: String,
    val country: String,
    val initials: String,
    val logo: String? = null,
    val color1: String,
    val color2: String,
    val program: String,
    val videoUrl: String,
    val tvBadge: TvBadge? = null,
)
