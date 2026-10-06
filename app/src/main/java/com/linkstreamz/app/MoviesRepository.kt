package com.linkstreamz.app

data class Episode(
    val n: Int,
    val title: String,
    val thumbUrl: String,
    val videoUrl: String,
    val duration: String
)

data class Season(
    val number: Int,
    val episodes: List<Episode>
)

data class Movie(
    val id: String,
    val title: String,
    val genre: String,
    val year: Int,
    val vj: String?,
    val translated: Boolean,
    val thumbUrl: String,
    val videoUrl: String,
    val duration: String
)

data class Series(
    val id: String,
    val title: String,
    val genre: String,
    val year: Int,
    val vj: String?,
    val translated: Boolean,
    val thumbUrl: String,
    val seasons: List<Season>
)

class MoviesRepository {
    val movies = listOf(
        Movie("mv-1", "The Last Stand of the Fallen King", "ACTION", 2025, "VJ Emmy", true, "https://picsum.photos/seed/m1/400/250", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4", "9:56"),
        Movie("mv-2", "Nightfall Over Nairobi", "THRILLER", 2024, "VJ Emmy", true, "https://picsum.photos/seed/m2/400/250", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4", "10:53"),
        Movie("mv-3", "Rivers of Dust", "WESTERN", 2021, "VJ Emmy", true, "https://picsum.photos/seed/m7/400/250", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackOnStreetAndDirt.mp4", "9:54"),
        Movie("mv-4", "Broken Compass", "ADVENTURE", 2024, "VJ Ulio", true, "https://picsum.photos/seed/m5/400/250", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/VolkswagenGTIReview.mp4", "9:45"),
        Movie("mv-5", "The Long Road Home", "DRAMA", 2023, "VJ Ulio", true, "https://picsum.photos/seed/m6/400/250", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4", "9:29"),
        Movie("mv-6", "The Quiet Sea", "DRAMA", 2021, "VJ Marley", true, "https://picsum.photos/seed/m11/400/250", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WhatCarCanYouGetForAGrand.mp4", "9:27"),
        Movie("mv-7", "Savanna Run", "ADVENTURE", 2024, "VJ Marley", true, "https://picsum.photos/seed/m12/400/250", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4", "12:14"),
        Movie("mv-8", "The Last Harvest", "DRAMA", 2020, "VJ Ulio", true, "https://picsum.photos/seed/m13/400/250", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4", "14:48"),
        Movie("mv-9", "Midnight Express", "THRILLER", 2022, "VJ Emmy", true, "https://picsum.photos/seed/m14/400/250", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/VolkswagenGTIReview.mp4", "9:45"),
        Movie("mv-10", "Golden Hour", "DRAMA", 2023, null, false, "https://picsum.photos/seed/m3/400/250", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4", "12:14"),
        Movie("mv-11", "Blackout", "ACTION", 2025, null, false, "https://picsum.photos/seed/m9/400/250", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4", "14:48"),
        Movie("mv-12", "Iron Tides", "ACTION", 2024, null, false, "https://picsum.photos/seed/m15/400/250", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4", "9:56"),
    )

    val series = listOf(
        Series("sr-1", "Silent Witness", "MYSTERY", 2024, "VJ Emmy", true, "https://picsum.photos/seed/sr1/600/340", listOf(
            Season(1, listOf(
                Episode(1, "The Beginning", "https://picsum.photos/seed/e111/200/120", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackOnStreetAndDirt.mp4", "9:54"),
                Episode(2, "Shadows", "https://picsum.photos/seed/e112/200/120", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/VolkswagenGTIReview.mp4", "9:45"),
                Episode(3, "The Bridge", "https://picsum.photos/seed/e113/200/120", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4", "9:29"),
                Episode(4, "Cold Trail", "https://picsum.photos/seed/e114/200/120", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WhatCarCanYouGetForAGrand.mp4", "9:27"),
            )),
            Season(2, listOf(
                Episode(1, "Homecoming", "https://picsum.photos/seed/e121/200/120", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4", "12:14"),
                Episode(2, "The Letter", "https://picsum.photos/seed/e122/200/120", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4", "14:48"),
                Episode(3, "Broken Glass", "https://picsum.photos/seed/e123/200/120", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4", "9:56"),
            )),
        )),
        Series("sr-2", "Steel Horizon", "SCI-FI", 2023, "VJ Ulio", true, "https://picsum.photos/seed/sr2/600/340", listOf(
            Season(1, listOf(
                Episode(1, "Ignition", "https://picsum.photos/seed/e211/200/120", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4", "9:29"),
                Episode(2, "Orbit", "https://picsum.photos/seed/e212/200/120", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WhatCarCanYouGetForAGrand.mp4", "9:27"),
                Episode(3, "The Signal", "https://picsum.photos/seed/e213/200/120", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4", "12:14"),
            )),
            Season(2, listOf(
                Episode(1, "Decay", "https://picsum.photos/seed/e221/200/120", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4", "14:48"),
                Episode(2, "Re-entry", "https://picsum.photos/seed/e222/200/120", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4", "9:56"),
            )),
        )),
        Series("sr-3", "Echoes of Tomorrow", "SCI-FI", 2024, "VJ Marley", true, "https://picsum.photos/seed/sr3/600/340", listOf(
            Season(1, listOf(
                Episode(1, "Yesterday", "https://picsum.photos/seed/e311/200/120", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4", "12:14"),
                Episode(2, "Tomorrow", "https://picsum.photos/seed/e312/200/120", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4", "14:48"),
                Episode(3, "Paradox", "https://picsum.photos/seed/e313/200/120", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4", "9:56"),
            )),
        )),
        Series("sr-4", "Crimson Signal", "THRILLER", 2023, null, false, "https://picsum.photos/seed/sr4/600/340", listOf(
            Season(1, listOf(
                Episode(1, "Red Static", "https://picsum.photos/seed/e411/200/120", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4", "9:56"),
                Episode(2, "Dead Air", "https://picsum.photos/seed/e412/200/120", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4", "10:53"),
                Episode(3, "Interference", "https://picsum.photos/seed/e413/200/120", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackOnStreetAndDirt.mp4", "9:54"),
            )),
        )),
        Series("sr-5", "Iron Coast", "ACTION", 2022, null, false, "https://picsum.photos/seed/sr5/600/340", listOf(
            Season(1, listOf(
                Episode(1, "Landfall", "https://picsum.photos/seed/e511/200/120", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackOnStreetAndDirt.mp4", "9:54"),
                Episode(2, "The Wall", "https://picsum.photos/seed/e512/200/120", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/VolkswagenGTIReview.mp4", "9:45"),
                Episode(3, "Undertow", "https://picsum.photos/seed/e513/200/120", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4", "9:29"),
            )),
        )),
    )
}
