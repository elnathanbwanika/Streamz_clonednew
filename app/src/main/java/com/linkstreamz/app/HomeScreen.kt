package com.linkstreamz.app

import android.widget.Toast
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
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
            when (val current = state) {
                FeedState.Loading -> LoadingList()
                FeedState.Error -> ErrorState { scope.launch { repository.load() } }
                is FeedState.Ready -> {
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
        ServerChooserDialog(
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
                Modifier.size(38.dp).shadow(8.dp, RoundedCornerShape(13.dp))
                    .background(Brush.linearGradient(listOf(Accent2, Accent)), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(18.dp))
            }
            Column {
                Text("LINK STREAMZ", color = Text, fontFamily = DisplayFont, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                Text("WATCH EVERY MATCH LIVE", color = Muted, fontFamily = MonoFont, fontSize = 9.5.sp, modifier = Modifier.padding(top = 5.dp), letterSpacing = .4.sp)
            }
        }
        IconButton(onClick = onSettings, modifier = Modifier.size(38.dp).background(Color.White.copy(alpha = .04f), RoundedCornerShape(12.dp))) {
            Icon(Icons.Default.Settings, "Settings", tint = Text, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun FilterChip(filter: HomeFilter, count: Int, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = if (selected) Accent else Color.White.copy(alpha = .02f),
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, Line),
        shadowElevation = if (selected) 8.dp else 0.dp,
    ) {
        Row(Modifier.padding(horizontal = 15.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            if (filter == HomeFilter.HIGHLIGHTS) Icon(Icons.Default.PlayArrow, null, tint = if (selected) Color.Black else Muted, modifier = Modifier.size(14.dp))
            Text(filter.label, color = if (selected) Color.Black else Muted, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
            Text(count.toString(), color = if (selected) Color.Black else Muted.copy(alpha = .7f), fontFamily = MonoFont, fontSize = 11.sp)
        }
    }
}

@Composable
private fun LoadingList() {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 44.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(3) {
            Box(Modifier.fillMaxWidth().size(96.dp).background(Brush.horizontalGradient(listOf(Color(0xFF101010), Color(0xFF1B1B1B), Color(0xFF101010))), RoundedCornerShape(18.dp)))
        }
    }
}

@Composable
private fun ErrorState(onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Can't reach the server", color = Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("Check your connection and try again.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            Button(onClick = onRetry, modifier = Modifier.padding(top = 18.dp)) { Text("Retry") }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        Text(message, color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun ServerChooserDialog(target: PlayerTarget, onDismiss: () -> Unit, onPick: (StreamServer) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF151515),
        title = {
            Column {
                Text("CHOOSE A SERVER", color = Text, fontFamily = DisplayFont, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Text(target.subtitle, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                target.servers.forEachIndexed { index, server ->
                    Row(
                        Modifier.fillMaxWidth().background(Color(0xFF0A0A0A), RoundedCornerShape(14.dp)).clickable { onPick(server) }.padding(13.dp, 13.dp, 15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(32.dp).background(AccentSoft, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                            Text("${index + 1}", color = Accent, fontFamily = MonoFont, fontWeight = FontWeight.Bold)
                        }
                        Text(server.name, color = Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        server.quality?.let { Text(it, color = Accent, fontFamily = MonoFont, fontSize = 10.5.sp, modifier = Modifier.background(AccentSoft, RoundedCornerShape(8.dp)).padding(4.dp, 4.dp)) }
                        Icon(Icons.Default.ChevronRight, null, tint = Muted, modifier = Modifier.size(16.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close", color = Accent) } },
    )
}