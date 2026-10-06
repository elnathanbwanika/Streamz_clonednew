import re

with open('app/src/main/java/com/linkstreamz/app/HomeScreen.kt', 'r') as f:
    home_kt = f.read()

# Add settings to HomeScreen params
home_kt = home_kt.replace(
    """    moviesRepo: MoviesRepository,
    channelsRepo: ChannelsRepository,
    onSettings: () -> Unit,""",
    """    moviesRepo: MoviesRepository,
    channelsRepo: ChannelsRepository,
    settings: SettingsUiState,
    onSettings: () -> Unit,"""
)

# Add var watchItem
state_block = """    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    var dialogTarget by remember { mutableStateOf<PlayerTarget?>(null) }"""
new_state_block = """    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    var dialogTarget by remember { mutableStateOf<PlayerTarget?>(null) }
    var watchItem by remember { mutableStateOf<Any?>(null) }"""
home_kt = home_kt.replace(state_block, new_state_block)

# Show WatchScreen if watchItem is not null
box_start = """    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {"""
box_new = """    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        if (watchItem != null) {
            WatchScreen(item = watchItem!!, settings = settings, onBack = { watchItem = null })
            return@Box
        }"""
home_kt = home_kt.replace(box_start, box_new)

# Update onWatch calls
home_kt = home_kt.replace(
    """onWatch = { item ->
                    // TODO: open WatchScreen
                    val url = if (item is Movie) item.videoUrl else if (item is Series) item.seasons[0].episodes[0].videoUrl else ""
                    onPlayer(CustomTarget(StreamServer("Movies", url = url)))
                }""",
    """onWatch = { item -> watchItem = item }"""
)
home_kt = home_kt.replace(
    """onWatch = { channel ->
                    onPlayer(CustomTarget(StreamServer("Channels", url = channel.videoUrl)))
                }""",
    """onWatch = { channel -> watchItem = channel }"""
)

with open('app/src/main/java/com/linkstreamz/app/HomeScreen.kt', 'w') as f:
    f.write(home_kt)

