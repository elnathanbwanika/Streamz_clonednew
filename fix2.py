import sys

with open("app/src/main/java/com/linkstreamz/app/PlayerScreen.kt", "r") as f:
    content = f.read()

# Fix targets
old_targets = """data class ChannelTarget(
    val channel: Channel,
    override val selectedServer: StreamServer? = null,
) : PlayerTarget {
    override val servers: List<StreamServer> = channel.servers
    override val title: String = channel.name
    override val subtitle: String = listOfNotNull(channel.category, channel.country).joinToString(" | ")
    override fun withServer(server: StreamServer) = copy(selectedServer = server)
}

data class MovieTarget(
    val movie: Movie,
    override val selectedServer: StreamServer? = null,
) : PlayerTarget {
    override val servers: List<StreamServer> = movie.servers
    override val title: String = movie.title
    override val subtitle: String = listOfNotNull(movie.genre, movie.year.toString(), movie.vj).joinToString(" | ")
    override fun withServer(server: StreamServer) = copy(selectedServer = server)
}

data class EpisodeTarget(
    val series: Series,
    val season: Season,
    val episode: Episode,
    override val selectedServer: StreamServer? = null,
) : PlayerTarget {
    override val servers: List<StreamServer> = episode.servers
    override val title: String = series.title
    override val subtitle: String = "S${season.number} E${episode.n} | ${episode.title}"
    override fun withServer(server: StreamServer) = copy(selectedServer = server)
}"""

new_targets = """data class ChannelTarget(
    val channel: Channel,
    override val selectedServer: StreamServer? = null,
) : PlayerTarget {
    override val servers: List<StreamServer> = listOf(StreamServer("Auto", channel.videoUrl))
    override val title: String = channel.title
    override val subtitle: String = listOfNotNull(channel.category, channel.country).joinToString(" | ")
    override fun withServer(server: StreamServer) = copy(selectedServer = server)
}

data class MovieTarget(
    val movie: Movie,
    override val selectedServer: StreamServer? = null,
) : PlayerTarget {
    override val servers: List<StreamServer> = listOf(StreamServer("Auto", movie.videoUrl))
    override val title: String = movie.title
    override val subtitle: String = listOfNotNull(movie.genre, movie.year.toString(), movie.vj).joinToString(" | ")
    override fun withServer(server: StreamServer) = copy(selectedServer = server)
}

data class EpisodeTarget(
    val series: Series,
    val season: Season,
    val episode: Episode,
    override val selectedServer: StreamServer? = null,
) : PlayerTarget {
    override val servers: List<StreamServer> = listOf(StreamServer("Auto", episode.videoUrl))
    override val title: String = series.title
    override val subtitle: String = "S${season.number} E${episode.n} | ${episode.title}"
    override fun withServer(server: StreamServer) = copy(selectedServer = server)
}"""

if old_targets in content:
    content = content.replace(old_targets, new_targets)
else:
    print("WARNING: Could not find old targets to replace")

# Fix Modifier.padding
old_padding = "Modifier.padding(horizontal = 16.dp, top = 14.dp, bottom = 8.dp)"
new_padding = "Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp)"
if old_padding in content:
    content = content.replace(old_padding, new_padding)
else:
    print("WARNING: Could not find padding to replace")

# Fix channel.name -> channel.title
if "channel.name" in content:
    # We already replaced it in targets, but in RelatedContent we have it too
    # wait, in RelatedContent: name = it.name
    # Let's fix that
    content = content.replace("name = it.name,", "name = it.title,")

# Remove extra brace at end of EmptyContentState
# Finding:
# }
# }
# 
# 
# private fun enterFullscreen(activity: Activity?) {
old_brace = """}
}


private fun enterFullscreen"""
new_brace = """}


private fun enterFullscreen"""

if old_brace in content:
    content = content.replace(old_brace, new_brace)
else:
    print("WARNING: Could not find extra brace")

with open("app/src/main/java/com/linkstreamz/app/PlayerScreen.kt", "w") as f:
    f.write(content)

print("Applied fixes to PlayerScreen.kt")
