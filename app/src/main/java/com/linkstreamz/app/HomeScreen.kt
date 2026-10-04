package com.linkstreamz.app

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.runtime.mutableLongStateOf

private enum class HomeFilter(val label: String) {
    ALL("All"), LIVE("Live"), UPCOMING("Upcoming"), FINISHED("Finished"), HIGHLIGHTS("Highlights")
}

@Composable
fun HomeScreen(
    repository: EventsRepository,
    onSettings: () -> Unit,
    onPlayer: (PlayerTarget) -> Unit,
) {
    val state by repository.state.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var filter by remember { mutableStateOf(HomeFilter.ALL) }
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    var dialogTarget by remember { mutableStateOf<PlayerTarget?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        while (true) {
            delay(TICKER_MS)
            nowMs = System.currentTimeMillis()
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


    val feed = (state as? FeedState.Ready)?.feed
    val counts = remember(feed, nowMs) {
        val events = feed?.events.orEmpty()
        mapOf(
            HomeFilter.ALL to events.size,
            HomeFilter.LIVE to events.count { effectiveStatus(it.startMs, it.status, nowMs) == "live" },
            HomeFilter.UPCOMING to events.count { effectiveStatus(it.startMs, it.status, nowMs) == "upcoming" },
            HomeFilter.FINISHED to events.count { effectiveStatus(it.startMs, it.status, nowMs) == "finished" },
            HomeFilter.HIGHLIGHTS to (feed?.highlights?.size ?: 0),
        )
    }

    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(Modifier.fillMaxSize().appBackground()) {
            HomeHeader(onSettings)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 20.dp, bottom = 16.dp),
            ) {
                items(HomeFilter.entries) { item ->
                    FilterChip(item, counts[item] ?: 0, filter == item) { filter = item; }
                }
            }
            // FEED: Always show the loading shimmer for at least 3 seconds on first open.
            // After 3s: show content if ready, or error if still failing.
            var minDelayPassed by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(3000L)
                minDelayPassed = true
            }

            // FEED: Grace period — don't flash "Can't reach server" instantly on first load.
            // Only show ErrorState after the error has persisted for at least 3 seconds.
            var errorSinceMs by remember { mutableLongStateOf(0L) }
            var showError by remember { mutableStateOf(false) }
            LaunchedEffect(state) {
                if (state is FeedState.Error) {
                    if (errorSinceMs == 0L) errorSinceMs = System.currentTimeMillis()
                    val elapsed = System.currentTimeMillis() - errorSinceMs
                    if (elapsed < 3000L) {
                        delay(3000L - elapsed)
                    }
                    // Only flip to error if we're still in Error state (not recovered)
                    if (state is FeedState.Error) showError = true
                } else {
                    errorSinceMs = 0L
                    showError = false
                }
            }

            when (val current = state) {
                FeedState.Loading -> LoadingList()
                FeedState.Error -> if (minDelayPassed && showError) ErrorState { scope.launch { repository.load() } } else LoadingList()
                is FeedState.Ready -> if (!minDelayPassed) LoadingList() else {
                    val items = when (filter) {
                        HomeFilter.HIGHLIGHTS -> current.feed.highlights.map { it as Any }
                        else -> current.feed.events.filter {
                            filter == HomeFilter.ALL || effectiveStatus(it.startMs, it.status, nowMs) == filter.name.lowercase()
                        }.map { it as Any }
                    }
                    if (items.isEmpty()) {
                        EmptyState(if (filter == HomeFilter.HIGHLIGHTS) "No highlights yet." else "No events here yet.")
                    } else {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 44.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(items, key = { item -> if (item is Event) "event-${item.id}" else "highlight-${(item as Highlight).id}" }) { item ->
                                when (item) {
                                    is Event -> EventCard(item, nowMs, {
                                        selectTarget(EventTarget(item), onPlayer, { dialogTarget = it }, context)
                                    })
                                    is Highlight -> HighlightCard(item, {
                                        selectTarget(HighlightTarget(item), onPlayer, { dialogTarget = it }, context)
                                    })
                                }
                            }
                        }
                    }
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
private fun HomeHeader(onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(38.dp).shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(13.dp),
                    ambientColor = LinkColors.OrangePrimary.copy(alpha = 0.35f),
                    spotColor = LinkColors.OrangePrimary.copy(alpha = 0.35f)
                ).background(
                    Brush.verticalGradient(listOf(LinkColors.OrangeLight, LinkColors.OrangePrimary)), 
                    RoundedCornerShape(13.dp)
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(18.dp))
            }
            Column {
                Text("LINK STREAMZ", color = LinkColors.PrimaryText, fontFamily = DisplayFont, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                Text("Your Home For Streams", color = LinkColors.MutedText, fontFamily = MonoFont, fontSize = 9.5.sp, modifier = Modifier.padding(top = 5.dp), letterSpacing = .4.sp)
            }
        }
        IconButton(
            onClick = onSettings, 
            modifier = Modifier.size(38.dp)
                .background(LinkColors.CardBg, RoundedCornerShape(12.dp))
                .border(1.dp, LinkColors.OrangePrimary.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
        ) {
            Icon(Icons.Default.Settings, "Settings", tint = LinkColors.OrangePrimary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun FilterChip(filter: HomeFilter, count: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .shadow(
                elevation = if (selected) 8.dp else 0.dp,
                shape = RoundedCornerShape(999.dp),
                ambientColor = if (selected) LinkColors.OrangePrimary.copy(alpha = 0.35f) else Color.Transparent,
                spotColor = if (selected) LinkColors.OrangePrimary.copy(alpha = 0.35f) else Color.Transparent
            )
            .background(
                brush = if (selected) Brush.verticalGradient(listOf(LinkColors.OrangeLight, LinkColors.OrangePrimary))
                        else Brush.verticalGradient(listOf(Color(0xFF0A0A0A), Color(0xFF0A0A0A))),
                shape = RoundedCornerShape(999.dp)
            )
            .border(
                width = 1.dp,
                color = if (selected) LinkColors.OrangePrimary else LinkColors.OrangePrimary.copy(alpha = 0.28f),
                shape = RoundedCornerShape(999.dp)
            )
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(horizontal = 15.dp, vertical = 9.dp), 
            verticalAlignment = Alignment.CenterVertically, 
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            if (filter == HomeFilter.HIGHLIGHTS) {
                Icon(
                    Icons.Default.PlayArrow, 
                    null, 
                    tint = if (selected) Color.Black else Color(0xFFB39A86), 
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = filter.label, 
                color = if (selected) Color.Black else Color(0xFFB39A86), 
                fontSize = 13.sp, 
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
            Text(
                text = count.toString(), 
                color = if (selected) Color.Black.copy(alpha = 0.7f) else Color(0xFF7A6656), 
                fontFamily = MonoFont, 
                fontSize = 11.sp
            )
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 44.dp),
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
                modifier = Modifier.padding(top = 18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LinkColors.OrangePrimary,
                    contentColor = Color.Black
                )
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

