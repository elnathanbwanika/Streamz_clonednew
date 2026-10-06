package com.linkstreamz.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val CATEGORY_ORDER = listOf("General", "News", "Entertainment", "Music", "Sports", "Kids")
private val CAT_ROWS = listOf(
    "local"  to "Local Channels",
    "Sports" to "Sports",
    "News"   to "News",
    "Entertainment" to "Entertainment",
    "Kids"   to "Kids & Family",
    "Music"  to "Music",
    "intl"   to "International"
)
private val LIVE_RED = Color(0xFFFF3B30)

@Composable
fun ChannelsScreen(repository: ChannelsRepository, onWatch: (Channel) -> Unit) {
    val allChannels = repository.channels
    var searchQuery by remember { mutableStateOf("") }
    var localFilter by remember { mutableStateOf(true) }   // true = Local, false = Abroad
    var catFilter   by remember { mutableStateOf<String?>(null) }

    val filtered = remember(searchQuery, localFilter, catFilter, allChannels) {
        allChannels
            .let { list -> if (searchQuery.length >= 2) list.filter {
                it.title.contains(searchQuery, true) || it.program.contains(searchQuery, true) || it.category.contains(searchQuery, true)
            } else list }
            .let { list -> if (catFilter != null) list.filter { it.category == catFilter } else list }
    }

    // Build rows according to prototype spec
    val localChannels = allChannels.filter { it.country == "Uganda" }
    val isSearch = searchQuery.length >= 2

    LazyColumn(
        contentPadding = PaddingValues(bottom = 80.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // ── Search bar ─────────────────────────────────────────────────────
        item {
            Box(
                Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(LinkColors.CardBg, RoundedCornerShape(8.dp))
                    .border(1.dp, LinkColors.OrangePrimary.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, null, tint = LinkColors.MutedText, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) Text("Search channels or categories", color = LinkColors.MutedText, fontSize = 13.sp)
                        BasicTextField(
                            value = searchQuery, onValueChange = { searchQuery = it },
                            textStyle = TextStyle(color = LinkColors.PrimaryText, fontSize = 13.sp, fontFamily = BodyFont),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // ── Local / Abroad segmented control ──────────────────────────────
        if (!isSearch) {
            item {
                Row(
                    Modifier.fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0A0A0A))
                        .border(1.dp, LinkColors.OrangePrimary.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
                ) {
                    listOf(true to "Local", false to "Abroad").forEach { (local, label) ->
                        val selected = localFilter == local
                        Box(
                            Modifier.weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) LinkColors.OrangePrimary else Color.Transparent)
                                .clickable { localFilter = local; catFilter = null }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, color = if (selected) Color.Black else LinkColors.MutedText, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            // ── Category chips ─────────────────────────────────────────────
            item {
                val scopedCategories = allChannels
                    .filter { if (localFilter) it.country == "Uganda" else it.country != "Uganda" }
                    .map { it.category }.distinct().sortedBy { CATEGORY_ORDER.indexOf(it).takeIf { i -> i >= 0 } ?: 99 }

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { CategoryChip("All", catFilter == null) { catFilter = null } }
                    items(scopedCategories) { cat -> CategoryChip(cat, catFilter == cat) { catFilter = if (catFilter == cat) null else cat } }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        // ── Search results ─────────────────────────────────────────────────
        if (isSearch) {
            item {
                Text("${filtered.size} results for \"$searchQuery\"", color = LinkColors.MutedText, fontFamily = MonoFont, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            item {
                ChannelRow(channels = filtered, onWatch = onWatch)
            }
            return@LazyColumn
        }

        // ── Category-filtered results ──────────────────────────────────────
        if (catFilter != null) {
            val catItems = allChannels.filter {
                (if (localFilter) it.country == "Uganda" else it.country != "Uganda") && it.category == catFilter
            }
            item { SectionHeader("${catFilter} (${catItems.size})") }
            item { ChannelRow(channels = catItems, onWatch = onWatch) }
            return@LazyColumn
        }

        // ── Home rows per section ──────────────────────────────────────────
        if (localFilter) {
            // Local: General/Entertainment local, Sports, Kids, News local
            item {
                SectionHeader("Local Channels", showLiveDot = true)
                ChannelRow(channels = localChannels.take(8), onWatch = onWatch)
                Spacer(Modifier.height(24.dp))
            }
            val localSports = allChannels.filter { it.category == "Sports" && it.country == "Uganda" }
            if (localSports.isNotEmpty()) item {
                SectionHeader("Sports", showLiveDot = true)
                ChannelRow(channels = localSports, onWatch = onWatch)
                Spacer(Modifier.height(24.dp))
            }
            val localNews = allChannels.filter { it.category == "News" && it.country == "Uganda" }
            if (localNews.isNotEmpty()) item {
                SectionHeader("News", showLiveDot = true)
                ChannelRow(channels = localNews, onWatch = onWatch)
                Spacer(Modifier.height(24.dp))
            }
            val localEnt = allChannels.filter { it.category == "Entertainment" && it.country == "Uganda" }
            if (localEnt.isNotEmpty()) item {
                SectionHeader("Entertainment")
                ChannelRow(channels = localEnt, onWatch = onWatch)
                Spacer(Modifier.height(24.dp))
            }
        } else {
            // Abroad rows
            listOf(
                "Sports" to "Sports",
                "News" to "News",
                "Entertainment" to "Entertainment",
                "Kids" to "Kids & Family",
                "Music" to "Music",
                "General" to "International"
            ).forEach { (cat, label) ->
                val chs = allChannels.filter { it.country != "Uganda" && it.category == cat }
                if (chs.isNotEmpty()) item {
                    SectionHeader(label, showLiveDot = cat == "Sports" || cat == "News")
                    ChannelRow(channels = chs, onWatch = onWatch)
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, showLiveDot: Boolean = false) {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (showLiveDot) {
            Box(Modifier.size(7.dp).background(LIVE_RED, CircleShape))
        }
        Text(title, color = LinkColors.PrimaryText, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) LinkColors.OrangePrimary else Color(0xFF0A0A0A))
            .border(1.dp, LinkColors.OrangePrimary.copy(alpha = if (selected) 1f else 0.28f), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(label, color = if (selected) Color.Black else LinkColors.MutedText, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun ChannelRow(channels: List<Channel>, onWatch: (Channel) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(channels) { ch -> ChannelCard(channel = ch, onClick = { onWatch(ch) }) }
    }
}

@Composable
fun ChannelCard(channel: Channel, onClick: () -> Unit) {
    val c1 = remember(channel.color1) { parseHex(channel.color1) }
    val c2 = remember(channel.color2) { parseHex(channel.color2) }

    Box(
        modifier = Modifier
            .width(130.dp)
            .height(130.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.radialGradient(listOf(c1, c2)))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        // Initials badge top-center
        Box(
            Modifier.align(Alignment.TopCenter)
                .size(42.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text(channel.initials, color = Color.White, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        Column(Modifier.align(Alignment.BottomStart)) {
            Text(channel.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(5.dp).background(LIVE_RED, CircleShape))
                Text("LIVE", color = LIVE_RED, fontFamily = MonoFont, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Text(channel.program, color = Color.White.copy(alpha = 0.65f), fontFamily = MonoFont, fontSize = 10.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}

private fun parseHex(hex: String): Color {
    return try {
        val h = hex.removePrefix("#")
        val v = if (h.length == 6) "FF$h" else h
        Color(v.toLong(16))
    } catch (e: Exception) {
        Color(0xFF1A1A2E)
    }
}
