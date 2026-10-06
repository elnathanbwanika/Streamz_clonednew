import json

with open('movies.json') as f: movies = json.load(f)
with open('series.json') as f: series = json.load(f)
with open('channels.json') as f: channels = json.load(f)

# generate ChannelsRepository
channels_kt = """package com.linkstreamz.app

data class Channel(
    val id: String,
    val title: String,
    val category: String,
    val country: String,
    val initials: String,
    val color1: String,
    val color2: String,
    val program: String,
    val videoUrl: String
)

class ChannelsRepository {
    val channels = listOf(
"""
for c in channels:
    channels_kt += f'        Channel("{c["id"]}", "{c["title"]}", "{c["cat"]}", "{c["country"]}", "{c["initials"]}", "{c["c1"]}", "{c["c2"]}", "{c["program"]}", "{c["video"]}"),\n'
channels_kt += """    )
}
"""

with open('app/src/main/java/com/linkstreamz/app/ChannelsRepository.kt', 'w') as f:
    f.write(channels_kt)

movies_kt = """package com.linkstreamz.app

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
"""

for m in movies:
    vj = f'"{m["vj"]}"' if m.get("vj") else "null"
    translated = "true" if m.get("translated") else "false"
    movies_kt += f'        Movie("{m["id"]}", "{m["title"]}", "{m["genre"]}", {m["year"]}, {vj}, {translated}, "{m["thumb"]}", "{m["video"]}", "{m["dur"]}"),\n'
movies_kt += """    )

    val series = listOf(
"""

for s in series:
    vj = f'"{s["vj"]}"' if s.get("vj") else "null"
    translated = "true" if s.get("translated") else "false"
    movies_kt += f'        Series("{s["id"]}", "{s["title"]}", "{s["genre"]}", {s["year"]}, {vj}, {translated}, "{s["thumb"]}", listOf(\n'
    for se in s["seasons"]:
        movies_kt += f'            Season({se["number"]}, listOf(\n'
        for ep in se["episodes"]:
            movies_kt += f'                Episode({ep["n"]}, "{ep["title"]}", "{ep["thumb"]}", "{ep["video"]}", "{ep["dur"]}"),\n'
        movies_kt += '            )),\n'
    movies_kt += '        )),\n'
movies_kt += """    )
}
"""

with open('app/src/main/java/com/linkstreamz/app/MoviesRepository.kt', 'w') as f:
    f.write(movies_kt)

