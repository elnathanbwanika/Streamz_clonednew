package com.linkstreamz.app

// Bundled sample data shown until the first successful server fetch.

internal val SAMPLE_MOVIES = listOf(
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

internal val SAMPLE_SERIES = listOf(
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

internal val SAMPLE_CHANNELS = listOf(
    Channel("ch-1", "NBS TV", "General", "Uganda", "NBS", "#7a3fff", "#3f1f8a", "News at 9", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"),
    Channel("ch-2", "Bukedde TV", "General", "Uganda", "BKD", "#1a6dff", "#003bb0", "Agataliiko Nfuufu", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerMeltdowns.mp4"),
    Channel("ch-3", "UBC TV", "General", "Uganda", "UBC", "#16a34a", "#065f1f", "National News", "https://media.w3.org/2010/05/bunny/trailer.mp4"),
    Channel("ch-4", "NTV Uganda", "News", "Uganda", "NTV", "#dc2626", "#7f1d1d", "The Market", "https://test-videos.co.uk/vids/jellyfish/mp4/h264/720/Jellyfish_720_10s_1MB.mp4"),
    Channel("ch-5", "Spark TV", "Entertainment", "Uganda", "SPK", "#ec4899", "#831843", "The Junction", "https://samplelib.com/lib/preview/mp4/sample-10s.mp4"),
    Channel("ch-6", "BBS TV", "General", "Uganda", "BBS", "#eab308", "#78350f", "Amasengejje", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4"),
    Channel("ch-7", "Salt TV", "Entertainment", "Uganda", "SLT", "#14b8a6", "#0f766e", "Music Live", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WhatCarCanYouGetForAGrand.mp4"),
    Channel("ch-8", "Urban TV", "Music", "Uganda", "URB", "#f97316", "#7c2d12", "The Countdown", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4"),
    Channel("ch-9", "SuperSport Football", "Sports", "South Africa", "SSF", "#0ea5e9", "#075985", "Champions League", "https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4"),
    Channel("ch-10", "SuperSport Cricket", "Sports", "South Africa", "SSC", "#16a34a", "#14532d", "Test Cricket Day 2", "https://media.w3.org/2010/05/bunny/movie.mp4"),
    Channel("ch-11", "SuperSport Premier", "Sports", "South Africa", "SSP", "#8b5cf6", "#4c1d95", "Manchester Derby", "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/720/Big_Buck_Bunny_720_10s_1MB.mp4"),
    Channel("ch-12", "BeIN Sports 1", "Sports", "Qatar", "BS1", "#e11d48", "#7f1d1d", "La Liga", "https://filesamples.com/samples/video/mp4/sample_640x360.mp4"),
    Channel("ch-13", "BeIN Sports 2", "Sports", "Qatar", "BS2", "#e11d48", "#7f1d1d", "Serie A", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackOnStreetAndDirt.mp4"),
    Channel("ch-14", "Sky Sports F1", "Sports", "UK", "SKF", "#dc2626", "#991b1b", "Race Weekend", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4"),
    Channel("ch-15", "ESPN", "Sports", "USA", "ESP", "#dc2626", "#450a0a", "NBA Tonight", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4"),
    Channel("ch-16", "Eurosport 1", "Sports", "France", "EU1", "#0ea5e9", "#0c4a6e", "Tour de France", "https://www.w3schools.com/html/mov_bbb.mp4"),
    Channel("ch-17", "BBC World News", "News", "UK", "BBC", "#dc2626", "#7f1d1d", "World News", "https://media.w3.org/2010/05/video/movie_300.mp4"),
    Channel("ch-18", "CNN International", "News", "USA", "CNN", "#dc2626", "#450a0a", "Amanpour", "https://test-videos.co.uk/vids/sintel/mp4/h264/720/Sintel_720_10s_1MB.mp4"),
    Channel("ch-19", "Al Jazeera", "News", "Qatar", "AJZ", "#eab308", "#78350f", "The Stream", "https://www.learningcontainer.com/wp-content/uploads/2020/05/sample-mp4-file.mp4"),
    Channel("ch-20", "Sky News", "News", "UK", "SKY", "#dc2626", "#7f1d1d", "Sky News Tonight", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/VolkswagenGTIReview.mp4"),
    Channel("ch-21", "France 24", "News", "France", "F24", "#0ea5e9", "#0c4a6e", "The Debate", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4"),
    Channel("ch-22", "DW News", "News", "Germany", "DW", "#1e40af", "#1e1b4b", "DW News", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4"),
    Channel("ch-23", "Africanews", "News", "France", "AFN", "#7c3aed", "#4c1d95", "Journal", "https://media.w3.org/2010/05/sintel/trailer.mp4"),
    Channel("ch-24", "CNBC Africa", "News", "Kenya", "CNB", "#0ea5e9", "#075985", "Power Lunch", "https://vjs.zencdn.net/v/oceans.mp4"),
    Channel("ch-25", "MTV", "Entertainment", "USA", "MTV", "#ec4899", "#831843", "Top 20 Video Countdown", "https://samplelib.com/lib/preview/mp4/sample-5s.mp4"),
    Channel("ch-26", "Comedy Central", "Entertainment", "USA", "CC", "#f59e0b", "#78350f", "Stand-Up Showcase", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"),
    Channel("ch-27", "E! Entertainment", "Entertainment", "USA", "E!", "#a855f7", "#4c1d95", "Red Carpet Live", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4"),
    Channel("ch-28", "TNT", "Entertainment", "USA", "TNT", "#dc2626", "#7f1d1d", "Movie Marathon", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"),
    Channel("ch-29", "USA Network", "Entertainment", "USA", "USA", "#0ea5e9", "#0c4a6e", "Suits", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerMeltdowns.mp4"),
    Channel("ch-30", "Bravo", "Entertainment", "USA", "BRV", "#a855f7", "#581c87", "Top Chef", "https://media.w3.org/2010/05/bunny/trailer.mp4"),
    Channel("ch-31", "Cartoon Network", "Kids", "USA", "CN", "#000000", "#1f2937", "Ben 10", "https://test-videos.co.uk/vids/jellyfish/mp4/h264/720/Jellyfish_720_10s_1MB.mp4"),
    Channel("ch-32", "Nickelodeon", "Kids", "USA", "NIC", "#f97316", "#7c2d12", "SpongeBob", "https://samplelib.com/lib/preview/mp4/sample-10s.mp4"),
    Channel("ch-33", "Disney Channel", "Kids", "USA", "DIS", "#1e40af", "#312e81", "Mickey Mouse Clubhouse", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4"),
    Channel("ch-34", "Boomerang", "Kids", "USA", "BMG", "#f59e0b", "#78350f", "Tom & Jerry", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WhatCarCanYouGetForAGrand.mp4"),
    Channel("ch-35", "CBeebies", "Kids", "UK", "CBB", "#0ea5e9", "#0c4a6e", "Bluey", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4"),
    Channel("ch-36", "PBS Kids", "Kids", "USA", "PBS", "#16a34a", "#065f1f", "Daniel Tiger", "https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4"),
    Channel("ch-37", "MTV Base", "Music", "UK", "MB", "#ec4899", "#831843", "The Official Chart", "https://media.w3.org/2010/05/bunny/movie.mp4"),
    Channel("ch-38", "Trace TV", "Music", "France", "TRC", "#7c3aed", "#4c1d95", "Afrobeats Top 10", "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/720/Big_Buck_Bunny_720_10s_1MB.mp4"),
    Channel("ch-39", "BET", "Music", "USA", "BET", "#dc2626", "#7f1d1d", "106 & Park", "https://filesamples.com/samples/video/mp4/sample_640x360.mp4"),
    Channel("ch-40", "VH1", "Music", "USA", "VH1", "#0ea5e9", "#075985", "Behind the Music", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackOnStreetAndDirt.mp4"),
    Channel("ch-41", "Zee TV", "Entertainment", "India", "ZEE", "#a855f7", "#4c1d95", "Kumkum Bhagya", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4"),
    Channel("ch-42", "Star Plus", "Entertainment", "India", "STR", "#0ea5e9", "#075985", "Anupamaa", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4"),
    Channel("ch-43", "Colors TV", "Entertainment", "India", "CLR", "#f97316", "#7c2d12", "Bigg Boss", "https://www.w3schools.com/html/mov_bbb.mp4"),
    Channel("ch-44", "CCTV-4", "News", "China", "CCT", "#dc2626", "#7f1d1d", "News Hour", "https://media.w3.org/2010/05/video/movie_300.mp4"),
    Channel("ch-45", "NHK World", "News", "Japan", "NHK", "#1e40af", "#1e1b4b", "NHK News 7", "https://test-videos.co.uk/vids/sintel/mp4/h264/720/Sintel_720_10s_1MB.mp4"),
    Channel("ch-46", "Arirang TV", "Entertainment", "Korea", "ARI", "#ec4899", "#831843", "K-Pop Star", "https://www.learningcontainer.com/wp-content/uploads/2020/05/sample-mp4-file.mp4"),
    Channel("ch-47", "RT News", "News", "Russia", "RT", "#16a34a", "#065f1f", "RT News", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/VolkswagenGTIReview.mp4"),
    Channel("ch-48", "TV5 Monde", "General", "France", "TV5", "#0ea5e9", "#0c4a6e", "Journal", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4"),
)
