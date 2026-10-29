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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent

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

// ── Channel card layout tuning ────────────────────────────────────────────────
private val CHANNEL_CARD_SIZE = 130.dp
private val CHANNEL_CARD_RADIUS = 14.dp
private val CHANNEL_CARD_INSET = 6.dp        // gap from card edge to thumbnail/caption
private val CHANNEL_THUMB_HEIGHT_FRACTION = 0.80f  // share of card height for the thumbnail
private val CHANNEL_THUMB_RADIUS = 12.dp
private val CHANNEL_THUMB_PADDING = 10.dp    // inner padding so the logo never touches the edge
private val CHANNEL_CARD_BORDER = 0.5.dp

@Composable
fun ChannelsScreen(channels: List<Channel>, onWatch: (Channel) -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    val allChannels = channels
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
        Text(title, color = LinkColors.PrimaryText, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
    val logoUrl = channel.logo?.trim().orEmpty()
    val hasLogo = logoUrl.isNotBlank()
    val thumbBacking = Color.White

    Box(
        modifier = Modifier
            .width(CHANNEL_CARD_SIZE)
            .height(CHANNEL_CARD_SIZE)
            .clip(RoundedCornerShape(CHANNEL_CARD_RADIUS))
            .background(LinkColors.CardBg)
            .border(CHANNEL_CARD_BORDER, LinkColors.LineAlpha, RoundedCornerShape(CHANNEL_CARD_RADIUS))
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.fillMaxSize()) {

            // ── Thumbnail area ────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(CHANNEL_THUMB_HEIGHT_FRACTION)
                    .padding(start = CHANNEL_CARD_INSET, top = CHANNEL_CARD_INSET, end = CHANNEL_CARD_INSET)
                    .clip(RoundedCornerShape(CHANNEL_THUMB_RADIUS))
                    .background(thumbBacking),
                contentAlignment = Alignment.Center
            ) {
                if (hasLogo) {
                    // Supports absolute http(s) URLs, base64 data URIs, and paths
                    // relative to the content server; falls back to initials on error.
                    val modelData = remember(logoUrl) {
                        when {
                            logoUrl.startsWith("data:image") -> try {
                                android.util.Base64.decode(logoUrl.substringAfter(","), android.util.Base64.DEFAULT)
                            } catch (e: Exception) {
                                logoUrl
                            }
                            logoUrl.startsWith("http://") || logoUrl.startsWith("https://") -> logoUrl
                            else -> "$CONTENT_BASE_URL/${logoUrl.trimStart('/')}"
                        }
                    }
                    SubcomposeAsyncImage(
                        model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                            .data(modelData)
                            .crossfade(true)
                            .build(),
                        contentDescription = channel.title,
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(CHANNEL_THUMB_PADDING)
                    ) {
                        when (painter.state) {
                            is coil.compose.AsyncImagePainter.State.Success -> SubcomposeAsyncImageContent()
                            else -> ChannelInitials(channel.initials)
                        }
                    }
                } else {
                    ChannelInitials(channel.initials)
                }
            }

            // ── Caption strip ─────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f - CHANNEL_THUMB_HEIGHT_FRACTION)
                    .padding(horizontal = CHANNEL_CARD_INSET, vertical = CHANNEL_CARD_INSET),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    channel.title,
                    modifier = Modifier.weight(1f),
                    color = LinkColors.PrimaryText,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(5.dp).background(LIVE_RED, CircleShape))
                    Text("LIVE", color = LIVE_RED, fontFamily = MonoFont, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

@Composable
private fun ChannelInitials(initials: String) {
    Text(
        initials,
        color = LinkColors.CardBg,
        fontFamily = DisplayFont,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp
    )
}
