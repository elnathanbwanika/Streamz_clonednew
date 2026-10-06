import re

with open('app/src/main/java/com/linkstreamz/app/HomeScreen.kt', 'r') as f:
    home_kt = f.read()

# 1. Update Enum
home_kt = home_kt.replace(
    'ALL("All"), LIVE("Live"), UPCOMING("Upcoming"), FINISHED("Finished"), HIGHLIGHTS("Highlights")',
    'ALL("All"), LIVE("Live"), UPCOMING("Upcoming"), FINISHED("Finished"), HIGHLIGHTS("Highlights"), MOVIES("Movies"), CHANNELS("Channels")'
)

# 2. Update HomeScreen parameters
home_kt = home_kt.replace(
    """fun HomeScreen(
    repository: EventsRepository,
    onSettings: () -> Unit,
    onPlayer: (PlayerTarget) -> Unit,
)""",
    """fun HomeScreen(
    repository: EventsRepository,
    moviesRepo: MoviesRepository,
    channelsRepo: ChannelsRepository,
    onSettings: () -> Unit,
    onPlayer: (PlayerTarget) -> Unit,
)"""
)

# 3. Update counts
counts_block_old = """HomeFilter.HIGHLIGHTS to (feed?.highlights?.size ?: 0),"""
counts_block_new = """HomeFilter.HIGHLIGHTS to (feed?.highlights?.size ?: 0),
            HomeFilter.MOVIES to (moviesRepo.movies.size + moviesRepo.series.size),
            HomeFilter.CHANNELS to channelsRepo.channels.size,"""
home_kt = home_kt.replace(counts_block_old, counts_block_new)

with open('app/src/main/java/com/linkstreamz/app/HomeScreen.kt', 'w') as f:
    f.write(home_kt)

