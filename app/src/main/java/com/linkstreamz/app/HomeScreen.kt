package com.linkstreamz.app

import androidx.compose.foundation.layout.BoxWithConstraints

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior

import android.widget.Toast
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.mutableLongStateOf

@Composable
fun HomeScreen(
    repository: EventsRepository,
    moviesRepo: MoviesRepository,
    channelsRepo: ChannelsRepository,
    settings: SettingsUiState,
    section: MainSection,
    onSectionChange: (MainSection) -> Unit,
    onOpenLimited: () -> Unit,
    focusLimited: Boolean,
    onFocusConsumed: () -> Unit,
    onSettings: () -> Unit,
    onDownloads: () -> Unit = {},
    onPlayer: (PlayerTarget) -> Unit,
) {
    val state by repository.state.collectAsState()
    val mediaState  by moviesRepo.state.collectAsState()
    val channelState by channelsRepo.state.collectAsState()
    val downloads by DownloadManager.items.collectAsState()

    // Derive live lists; empty until fetched from server
    val movies   = (mediaState  as? MediaFeedState.Ready)?.movies   ?: emptyList()
    val series   = (mediaState  as? MediaFeedState.Ready)?.series   ?: emptyList()
    val channels = (channelState as? ChannelFeedState.Ready)?.channels ?: emptyList()
    val events   = (state as? FeedState.Ready)?.feed?.events ?: emptyList()

    val context = LocalContext.current
    var sportsTab by rememberSaveable { mutableStateOf(SportsTab.LIVE) }
    var dialogTarget by remember { mutableStateOf<PlayerTarget?>(null) }
    var pendingDownload by remember { mutableStateOf<Movie?>(null) }
    var mobileConfirmDownload by remember { mutableStateOf<Movie?>(null) }
    var showDownloadRationale by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun enqueueDownload(movie: Movie) {
        when (val result = DownloadManager.downloadMovie(context, movie)) {
            is EnqueueResult.Started -> Toast.makeText(context, "Download started", Toast.LENGTH_SHORT).show()
            is EnqueueResult.AlreadyExists -> Toast.makeText(context, "Already in Downloads", Toast.LENGTH_SHORT).show()
            is EnqueueResult.Rejected -> Toast.makeText(context, result.reason, Toast.LENGTH_LONG).show()
        }
    }

    val downloadPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val movie = pendingDownload
        pendingDownload = null
        val storageDenied = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            grants[Manifest.permission.WRITE_EXTERNAL_STORAGE] == false
        if (storageDenied) {
            Toast.makeText(context, "Storage permission is needed to save downloads", Toast.LENGTH_LONG).show()
        } else if (movie != null) {
            enqueueDownload(movie)
        }
    }

    fun requestDownloadPermissions(movie: Movie) {
        val missing = buildList {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
            ) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (missing.isEmpty()) {
            enqueueDownload(movie)
        } else {
            pendingDownload = movie
            showDownloadRationale = true
        }
    }

    fun requestDownload(movie: Movie) {
        if (DownloadConditions.hasLowStorage(context)) {
            Toast.makeText(context, DownloadConditions.LOW_STORAGE_MESSAGE, Toast.LENGTH_LONG).show()
            return
        }
        if (DownloadConditions.isMetered(context)) {
            mobileConfirmDownload = movie
        } else {
            requestDownloadPermissions(movie)
        }
    }

    // FEED: Reload when the app returns to the foreground. If a previous Ready
    // state exists and the new fetch fails, the repository keeps old data (silent
    // mode) and we show a toast per the spec.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val hadData = state is FeedState.Ready
                scope.launch {
                    repository.load(silent = hadData)
                    // FEED: After a silent load, if we still have Ready data the
                    // repository kept old content because the refresh failed — toast.
                    if (hadData && state !is FeedState.Ready) {
                        Toast.makeText(context, "Couldn't refresh. Showing last data.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var errorSinceMs by remember { mutableLongStateOf(0L) }
    var showError by remember { mutableStateOf(false) }
    LaunchedEffect(state) {
        if (state is FeedState.Error) {
            if (errorSinceMs == 0L) errorSinceMs = System.currentTimeMillis()
            val elapsed = System.currentTimeMillis() - errorSinceMs
            if (elapsed < 3000L) {
                delay(3000L - elapsed)
            }
            if (state is FeedState.Error) showError = true
        } else {
            errorSinceMs = 0L
            showError = false
        }
    }

    BackHandler(enabled = section != MainSection.HOME) {
        onSectionChange(MainSection.HOME)
    }

    val playChannel: (Channel) -> Unit = { onPlayer(ChannelTarget(it)) }
    val playSeriesOrMovie: (Any) -> Unit = { item ->
        if (item is Movie) onPlayer(MovieTarget(item))
        if (item is Series && item.seasons.isNotEmpty() && item.seasons.first().episodes.isNotEmpty()) {
            val season = item.seasons.first()
            val ep = season.episodes.first()
            onPlayer(EpisodeTarget(item, season, ep))
        }
    }

    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(Modifier.fillMaxSize().appBackground()) {
            HomeHeader(onSettings, onDownloads)
            MainSectionTabs(selected = section, onSelect = onSectionChange)

            Box(Modifier.weight(1f)) {
                // Compose only the active tab. Keeping hidden tabs composed
                // (the earlier alpha(0f) approach) left their nested lazy lists
                // and long-lived LaunchedEffect/produceState loops running in a
                // zero-size viewport, which froze the tab when you returned.
                // We now dispose on switch, but saveableStateHolder preserves
                // each tab's rememberSaveable state (scroll offset, filters)
                // so a return is instant without stale live state.
                val keepAlive = rememberSaveableStateHolder()
                keepAlive.SaveableStateProvider(section.name) {
                    SectionContent(
                        entry = section,
                        movies = movies,
                        series = series,
                        channels = channels,
                        events = events,
                        downloads = downloads,
                        state = state,
                        mediaState = mediaState,
                        channelState = channelState,
                        sportsTab = sportsTab,
                        showError = showError,
                        focusLimited = focusLimited,
                        onFocusConsumed = onFocusConsumed,
                        onSectionChange = onSectionChange,
                        onSportsTabChange = { sportsTab = it },
                        onRetry = { scope.launch { repository.load() } },
                        onDownload = ::requestDownload,
                        onOpenDownloads = onDownloads,
                        onWatchChannel = playChannel,
                        onPlayer = onPlayer,
                        onDialogTarget = { dialogTarget = it },
                    )
                }
            }
        }
    }

    dialogTarget?.let { target ->
        ServerChooserSheet(
            target = target,
            onDismiss = { dialogTarget = null },
            onPick = { server -> dialogTarget = null; onPlayer(target.withServer(server)) },
        )
    }

    mobileConfirmDownload?.let { movie ->
        AlertDialog(
            onDismissRequest = { mobileConfirmDownload = null },
            containerColor = LsColor.Card,
            title = { Text("Download over mobile data?", color = LsColor.Text, fontFamily = BodyFont, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = { Text(DownloadConditions.MOBILE_DATA_MESSAGE, color = LinkColors.MutedText) },
            confirmButton = {
                TextButton(onClick = {
                    mobileConfirmDownload = null
                    requestDownloadPermissions(movie)
                }) { Text("Download", color = LinkColors.OrangePrimary) }
            },
            dismissButton = {
                TextButton(onClick = { mobileConfirmDownload = null }) {
                    Text("Cancel", color = LinkColors.MutedText)
                }
            },
        )
    }

    if (showDownloadRationale) {
        AlertDialog(
            onDismissRequest = { showDownloadRationale = false; pendingDownload = null },
            containerColor = LsColor.Card,
            title = { Text("Allow download permissions", color = LsColor.Text, fontFamily = BodyFont, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                Text(
                    "Notifications show download progress. On Android 9 and older, storage access saves the file to your Downloads folder.",
                    color = LinkColors.MutedText,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDownloadRationale = false
                    val missing = buildList {
                        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                        ) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    if (missing.isEmpty()) {
                        pendingDownload?.let { enqueueDownload(it) }
                        pendingDownload = null
                    } else {
                        downloadPermissionLauncher.launch(missing.toTypedArray())
                    }
                }) { Text("Continue", color = LinkColors.OrangePrimary) }
            },
            dismissButton = {
                TextButton(onClick = { showDownloadRationale = false; pendingDownload = null }) {
                    Text("Not now", color = LinkColors.MutedText)
                }
            },
        )
    }
}

@Composable
private fun SectionContent(
    entry: MainSection,
    movies: List<Movie>,
    series: List<Series>,
    channels: List<Channel>,
    events: List<Event>,
    downloads: List<DownloadItem>,
    state: FeedState,
    mediaState: MediaFeedState,
    channelState: ChannelFeedState,
    sportsTab: SportsTab,
    showError: Boolean,
    focusLimited: Boolean,
    onFocusConsumed: () -> Unit,
    onSectionChange: (MainSection) -> Unit,
    onSportsTabChange: (SportsTab) -> Unit,
    onRetry: () -> Unit,
    onDownload: (Movie) -> Unit,
    onOpenDownloads: () -> Unit,
    onWatchChannel: (Channel) -> Unit,
    onPlayer: (PlayerTarget) -> Unit,
    onDialogTarget: (PlayerTarget) -> Unit,
) {
    val context = LocalContext.current
    when (entry) {
        MainSection.HOME -> HomeSummary(
            movies = movies,
            channels = channels,
            events = events,
            downloads = downloads,
            onOpenLimited = { onSectionChange(MainSection.MOVIES) },
            onDownload = onDownload,
            onOpenDownloads = onOpenDownloads,
            onOpenMovies = { onSectionChange(MainSection.MOVIES) },
            onOpenChannels = { onSectionChange(MainSection.CHANNELS) },
            onOpenSports = { onSectionChange(MainSection.SPORTS) },
            onWatchChannel = onWatchChannel,
            onPlayer = onPlayer,
            selectTarget = { target -> selectTarget(target, onPlayer, onDialogTarget, context) },
        )

        MainSection.SPORTS -> SportsContent(
            state = state,
            minDelayPassed = true,
            showError = showError,
            selectedTab = sportsTab,
            onSelectTab = onSportsTabChange,
            onRetry = onRetry,
            onEvent = { selectTarget(EventTarget(it), onPlayer, onDialogTarget, context) },
            onHighlight = { selectTarget(HighlightTarget(it), onPlayer, onDialogTarget, context) },
        )

        MainSection.MOVIES -> {
            if (mediaState is MediaFeedState.Loading && movies.isEmpty()) {
                LoadingList()
            } else {
                MoviesScreen(
                    movies = movies,
                    series = series,
                    onWatch = { item ->
                        if (item is Movie) onPlayer(MovieTarget(item))
                        if (item is Series && item.seasons.isNotEmpty() && item.seasons.first().episodes.isNotEmpty()) {
                            val season = item.seasons.first()
                            onPlayer(EpisodeTarget(item, season, season.episodes.first()))
                        }
                    },
                    onDownload = onDownload,
                    focusLimited = focusLimited,
                    onFocusConsumed = onFocusConsumed,
                )
            }
        }

        MainSection.CHANNELS -> {
            if (channelState is ChannelFeedState.Loading && channels.isEmpty()) {
                LoadingList()
            } else {
                ChannelsScreen(channels = channels, onWatch = onWatchChannel)
            }
        }
    }
}

@Composable
private fun MainSectionTabs(selected: MainSection, onSelect: (MainSection) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MainSection.entries.forEach { item ->
            val isSelected = selected == item
            Box(
                Modifier.weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(LsShape.Pill)
                    .background(if (isSelected) LsColor.Accent else LsColor.Surface)
                    .clickable { onSelect(item) }
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    item.label,
                    color = if (isSelected) LsColor.OnAccent else LsColor.TextDim,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun HomeSummary(
    movies: List<Movie>,
    channels: List<Channel>,
    events: List<Event>,
    downloads: List<DownloadItem>,
    onOpenLimited: () -> Unit,
    onDownload: (Movie) -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenMovies: () -> Unit,
    onOpenChannels: () -> Unit,
    onOpenSports: () -> Unit,
    onWatchChannel: (Channel) -> Unit,
    onPlayer: (PlayerTarget) -> Unit,
    selectTarget: (PlayerTarget) -> Unit,
) {
    val homeMovies = remember(movies) { movies.filter { it.translated }.ifEmpty { movies } }
    val newlyAdded = remember(homeMovies) { homeMovies.takeLast(10).reversed() }
    val trending = remember(homeMovies) { homeMovies.take(10) }
    val bannerMovie = remember(movies) { TimeLimitedBannerSelector.select(movies, ServerClock.now()) }
    val bannerDownloadSupport = remember(bannerMovie) {
        bannerMovie?.let { DownloadSupport.canDownload(it, ServerClock.now()) } ?: false
    }
    val newestLimited = remember(movies) { TimeLimitedBannerSelector.newest(movies, ServerClock.now()) }
    val homeSportsEvents = remember(events) {
        events.sortedByDescending { it.startMs ?: Long.MIN_VALUE }
    }
    val recentDownloads = remember(downloads) {
        val weekAgo = ServerClock.now() - 7L * 24L * 60L * 60L * 1000L
        downloads.filter {
            it.isActive || (it.status == DownloadStatus.COMPLETED && it.updatedAt >= weekAgo)
        }.take(3)
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        bannerMovie?.let { movie ->
            item(key = "home-limited") {
                HomeTimeLimitedBanner(
                    movie = movie,
                    downloadSupport = bannerDownloadSupport,
                    onOpenMovies = onOpenLimited,
                    onWatch = { selectTarget(MovieTarget(movie)) },
                    onDownload = { onDownload(movie) },
                )
            }
        }

        if (recentDownloads.isNotEmpty()) {
            item(key = "home-downloads") {
                HomeDownloadsSummary(
                    items = recentDownloads,
                    onClick = onOpenDownloads,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        if (newlyAdded.isNotEmpty()) {
            item(key = "home-newly") {
                HomeRailHeader(title = "Newly Added", onViewAll = onOpenMovies)
                HomeMovieRail(
                    movies = newlyAdded,
                    onWatch = { item -> onPlayer(MovieTarget(item)) },
                )
            }
        }

        if (newestLimited.isNotEmpty()) {
            item(key = "home-limited-rail") {
                HomeRailHeader(title = "Limited Time", onViewAll = onOpenLimited)
                HomeMovieRail(
                    movies = newestLimited,
                    onWatch = { item -> onPlayer(MovieTarget(item)) },
                )
            }
        }

        if (homeSportsEvents.isNotEmpty()) {
            item(key = "home-sports-rail") {
                HomeRailHeader(title = "Sports Events", onViewAll = onOpenSports)
                HomeSportsRail(
                    events = homeSportsEvents,
                    onWatch = { event -> selectTarget(EventTarget(event)) },
                )
            }
        }

        if (trending.isNotEmpty()) {
            item(key = "home-trending") {
                HomeRailHeader(title = "Trending", onViewAll = onOpenMovies)
                HomeMovieRail(
                    movies = trending,
                    onWatch = { item -> onPlayer(MovieTarget(item)) },
                )
            }
        }

        if (channels.isNotEmpty()) {
            item(key = "home-channels") {
                HomeRailHeader(title = "Channels", onViewAll = onOpenChannels)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(channels.take(10), key = { it.id }) { channel ->
                        Box(Modifier.width(112.dp)) {
                            ChannelCard(channel = channel, onClick = { onWatchChannel(channel) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeRailHeader(title: String, onViewAll: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            title,
            color = LsColor.Text,
            fontFamily = DisplayFont,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
        )
        Box(
            Modifier.heightIn(min = 48.dp).clickable(onClick = onViewAll).padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "View all",
                color = LsColor.Accent,
                fontFamily = MonoFont,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun HomeMovieRail(movies: List<Movie>, onWatch: (Movie) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(movies, key = { it.id }) { movie ->
            MediaCard(item = movie, onClick = { onWatch(movie) })
        }
    }
}

@Composable
private fun HomeSportsRail(
    events: List<Event>,
    onWatch: (Event) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val pageWidth = (maxWidth - 16.dp - 16.dp - 12.dp).coerceAtLeast(220.dp)
        val state = rememberLazyListState()
        LazyRow(
            state = state,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            flingBehavior = rememberSnapFlingBehavior(state),
        ) {
            items(events, key = { it.id }) { event ->
                Box(Modifier.width(pageWidth)) {
                    EventCard(event = event) { onWatch(event) }
                }
            }
        }
    }
}

@Composable
private fun HomeDownloadsSummary(
    items: List<DownloadItem>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = items.count { it.isActive }
    val title = if (active > 0) "$active download${if (active == 1) "" else "s"} in progress" else "Recently downloaded"
    val subtitle = buildString {
        append(items.first().title)
        if (items.size > 1) append(" +${items.size - 1} more")
    }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(LinkColors.CardBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(LinkColors.OrangePrimary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Download, null, tint = LinkColors.OrangePrimary, modifier = Modifier.size(19.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = LinkColors.PrimaryText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = LinkColors.MutedText, fontFamily = MonoFont, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("Open", color = LinkColors.OrangePrimary, fontFamily = MonoFont, fontSize = 11.sp)
    }
}

@Composable
private fun SportsContent(
    state: FeedState,
    minDelayPassed: Boolean,
    showError: Boolean,
    selectedTab: SportsTab,
    onSelectTab: (SportsTab) -> Unit,
    onRetry: () -> Unit,
    onEvent: (Event) -> Unit,
    onHighlight: (Highlight) -> Unit,
) {
    val events = (state as? FeedState.Ready)?.feed?.events.orEmpty()
    val highlights = (state as? FeedState.Ready)?.feed?.highlights.orEmpty()
    // Counts only need minute-level accuracy; a 10 s tick would rebuild the tab
    // row (and every card below it) for no visible change.
    val countsNowMs by produceState(ServerClock.now(), events, highlights) {
        while (true) {
            delay(60_000L)
            value = ServerClock.now()
        }
    }
    val counts = remember(events, highlights, countsNowMs) {
        mapOf(
            SportsTab.LIVE to events.count { effectiveStatus(it.startMs, it.status, countsNowMs) == "live" },
            SportsTab.UPCOMING to events.count { effectiveStatus(it.startMs, it.status, countsNowMs) == "upcoming" },
            SportsTab.FINISHED to events.count { effectiveStatus(it.startMs, it.status, countsNowMs) == "finished" },
            SportsTab.HIGHLIGHTS to highlights.size,
        )
    }

    Column(Modifier.fillMaxSize()) {
        SportsSubTabs(selected = selectedTab, counts = counts, onSelect = onSelectTab)
        Box(Modifier.weight(1f)) {
            when (state) {
                FeedState.Loading -> LoadingList()
                FeedState.Error -> if (minDelayPassed && showError) ErrorState(onRetry) else LoadingList()
                is FeedState.Ready -> {
                    if (!minDelayPassed) {
                        LoadingList()
                    } else {
                        val list: List<Any> = when (selectedTab) {
                            SportsTab.HIGHLIGHTS -> highlights.map { it as Any }
                            else -> events.filter {
                                effectiveStatus(it.startMs, it.status, ServerClock.now()) == selectedTab.name.lowercase()
                            }.map { it as Any }
                        }
                        if (list.isEmpty()) {
                            EmptyState(if (selectedTab == SportsTab.HIGHLIGHTS) "No highlights yet." else "No events here yet.")
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                items(
                                    list,
                                    key = { item ->
                                        if (item is Event) "event-${item.id}" else "highlight-${(item as Highlight).id}"
                                    },
                                ) { item ->
                                    when (item) {
                                        is Event -> EventCard(item) { onEvent(item) }
                                        is Highlight -> HighlightCard(item) { onHighlight(item) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SportsSubTabs(
    selected: SportsTab,
    counts: Map<SportsTab, Int>,
    onSelect: (SportsTab) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        items(SportsTab.entries, key = { it.name }) { tab ->
            val isSelected = selected == tab
            Column(
                Modifier.heightIn(min = 48.dp).clickable { onSelect(tab) }.padding(top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "${tab.label} ${counts[tab] ?: 0}",
                    color = if (isSelected) LinkColors.OrangePrimary else LinkColors.MutedText,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .height(2.dp)
                        .width(if (isSelected) 28.dp else 0.dp)
                        .background(LinkColors.OrangePrimary, RoundedCornerShape(99.dp))
                )
            }
        }
    }
}

@Composable
private fun HomeTimeLimitedBanner(
    movie: Movie,
    downloadSupport: Boolean,
    onOpenMovies: () -> Unit,
    onWatch: () -> Unit,
    onDownload: () -> Unit,
) {
    val tl = movie.timeLimited ?: return
    val now = ServerClock.now()
    val state = movie.effectiveTimeLimitedState(now) ?: return
    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(LsShape.Hero)
            .background(LsColor.HeroBase)
            .drawBehind {
                // Soft large orange circle, top-right, 16 % opacity
                drawCircle(
                    color = LsColor.Accent.copy(alpha = 0.16f),
                    radius = size.width * 0.55f,
                    center = Offset(size.width, 0f),
                )
            }
            .clickable(onClick = onOpenMovies)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.background(LinkColors.OrangePrimary, LsShape.Pill)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        when (state) {
                            TimeLimitedState.LIVE -> "LIMITED TIME"
                            TimeLimitedState.UPCOMING -> "COMING SOON"
                            TimeLimitedState.ENDED -> "ENDED"
                        },
                        color = LsColor.OnAccent, fontFamily = MonoFont, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    when (state) {
                        TimeLimitedState.LIVE -> timeLimitedRemainingText(tl, now)
                        TimeLimitedState.UPCOMING -> "Starts in ${timeLimitedStartCountdown(tl, now)}"
                        TimeLimitedState.ENDED -> "Window ended"
                    },
                    color = LinkColors.OrangeLight, fontFamily = MonoFont, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(movie.title, color = LsColor.Text, fontWeight = FontWeight.Bold, fontSize = 24.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "Available ${formatEatDateTime(tl.startAtMs)} EAT for ${formatDuration(tl.durationHours)}",
                color = LsColor.TextDim,
                fontFamily = MonoFont,
                fontSize = 10.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.End) {
            Button(
                onClick = onWatch,
                colors = ButtonDefaults.buttonColors(containerColor = LinkColors.OrangePrimary),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
                shape = LsShape.Button,
                modifier = Modifier.height(LsSpace.Button).widthIn(min = 120.dp),
            ) {
                Text(
                    if (state == TimeLimitedState.LIVE) "Watch" else "Details",
                    color = LsColor.OnAccent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (downloadSupport) {
                IconButton(
                    onClick = onDownload,
                    modifier = Modifier
                        .size(LsSpace.Button)
                        .clip(LsShape.Button)
                        .background(LsColor.FillSubtle)
                ) {
                    Icon(Icons.Default.Download, "Download", tint = LinkColors.OrangePrimary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

private fun selectTarget(
    target: PlayerTarget,
    onPlayer: (PlayerTarget) -> Unit,
    onDialog: (PlayerTarget) -> Unit,
    context: android.content.Context,
) {
    when {
        target.servers.isEmpty() -> Toast.makeText(context, if (target is EventTarget && effectiveStatus(target.event.startMs, target.event.status) == "upcoming") "This event hasn't started yet" else "No streams available yet", Toast.LENGTH_SHORT).show()
        target.servers.size == 1 -> onPlayer(target.withServer(target.servers.first()))
        else -> onDialog(target)
    }
}

@Composable
private fun HomeHeader(onSettings: () -> Unit, onDownloads: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(LsColor.Accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = LsColor.OnAccent, modifier = Modifier.size(24.dp))
            }
            Column {
                Text("LINK STREAMZ", color = LsColor.Text, fontFamily = DisplayFont, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.96.sp)
                Text("Your Home For Streams", color = LsColor.TextDim, fontFamily = MonoFont, fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp), letterSpacing = .4.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onDownloads,
                modifier = Modifier.size(44.dp).clip(CircleShape).background(LsColor.Card),
            ) {
                Icon(Icons.Default.Download, "Downloads", tint = LsColor.Accent, modifier = Modifier.size(20.dp))
            }
            IconButton(
                onClick = onSettings,
                modifier = Modifier.size(44.dp).clip(CircleShape).background(LsColor.Card),
            ) {
                Icon(Icons.Default.Settings, "Settings", tint = LsColor.Accent, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun LoadingList() {
    val shimmer = rememberInfiniteTransition(label = "shimmer")
    val shimmerX by shimmer.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_x"
    )

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(5) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(
                        Brush.horizontalGradient(
                            colorStops = arrayOf(
                                (shimmerX - 0.3f).coerceIn(0f, 1f) to LinkColors.CardBg,
                                shimmerX.coerceIn(0f, 1f) to Color(0xFF2A2015),
                                (shimmerX + 0.3f).coerceIn(0f, 1f) to LinkColors.CardBg,
                            )
                        ),
                        RoundedCornerShape(18.dp)
                    )
            )
        }
    }
}

@Composable
private fun ErrorState(onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Can't reach the server", color = LinkColors.PrimaryText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("Check your connection and try again.", color = LinkColors.MutedText, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            Button(
                onClick = onRetry,
                modifier = Modifier.padding(top = 18.dp).heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LinkColors.OrangePrimary,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        Text(message, color = LinkColors.MutedText, fontSize = 13.sp)
    }
}
