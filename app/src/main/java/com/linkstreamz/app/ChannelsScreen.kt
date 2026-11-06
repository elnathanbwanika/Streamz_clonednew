package com.linkstreamz.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest

private val CATEGORY_ORDER = listOf("General", "News", "Entertainment", "Music", "Sports", "Kids")

private val CARD_BG            = Color(0xFF100C09)
private val CARD_BORDER_REST   = Color(0xFF2E1C10)
private val CARD_BORDER_PRESS  = Color(0xFFFF6600)
private val LIVE_RED           = Color(0xFFFF3B30)
private val FALLBACK_CIRCLE_BG = Color(0xFF1A120C)
private val ORANGE_ACCENT      = Color(0xFFFF6600)
private val TEXT_NAME          = Color(0xFFF2EBE5)
private val PILL_BG            = Color(0xFF1A120C)
private val PILL_BORDER        = Color(0xFF3A2415)
private val PILL_TEXT          = Color(0xFFC9B8AA)

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

    val localChannels = allChannels.filter { it.country == "Uganda" }
    val isSearch = searchQuery.length >= 2

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Search bar (fixed outside scroll area) ─────────────────────────
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

        // ── Local / Abroad segmented control & category chips (outside scroll area)
        if (!isSearch) {
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
            Spacer(Modifier.height(4.dp))
        }

        // ── Vertical 4-column Grid ─────────────────────────────────────────
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 76.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 90.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (isSearch) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "${filtered.size} results for \"$searchQuery\"",
                        color = LinkColors.MutedText,
                        fontFamily = MonoFont,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                    )
                }
                items(filtered, key = { it.id }) { ch ->
                    ChannelCard(channel = ch, onClick = { onWatch(ch) })
                }
            } else if (catFilter != null) {
                val catItems = allChannels.filter {
                    (if (localFilter) it.country == "Uganda" else it.country != "Uganda") && it.category == catFilter
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader(title = catFilter!!, count = catItems.size)
                }
                items(catItems, key = { it.id }) { ch ->
                    ChannelCard(channel = ch, onClick = { onWatch(ch) })
                }
            } else if (localFilter) {
                // Local rows: General/Entertainment local, Sports, Kids, News local
                val localRow = localChannels.take(8)
                if (localRow.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(title = "Local Channels", count = localRow.size)
                    }
                    items(localRow, key = { "local_${it.id}" }) { ch ->
                        ChannelCard(channel = ch, onClick = { onWatch(ch) })
                    }
                }
                val localSports = allChannels.filter { it.category == "Sports" && it.country == "Uganda" }
                if (localSports.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(title = "Sports", count = localSports.size)
                    }
                    items(localSports, key = { "sports_${it.id}" }) { ch ->
                        ChannelCard(channel = ch, onClick = { onWatch(ch) })
                    }
                }
                val localNews = allChannels.filter { it.category == "News" && it.country == "Uganda" }
                if (localNews.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(title = "News", count = localNews.size)
                    }
                    items(localNews, key = { "news_${it.id}" }) { ch ->
                        ChannelCard(channel = ch, onClick = { onWatch(ch) })
                    }
                }
                val localEnt = allChannels.filter { it.category == "Entertainment" && it.country == "Uganda" }
                if (localEnt.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(title = "Entertainment", count = localEnt.size)
                    }
                    items(localEnt, key = { "ent_${it.id}" }) { ch ->
                        ChannelCard(channel = ch, onClick = { onWatch(ch) })
                    }
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
                    if (chs.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SectionHeader(title = label, count = chs.size)
                        }
                        items(chs, key = { "abroad_${cat}_${it.id}" }) { ch ->
                            ChannelCard(channel = ch, onClick = { onWatch(ch) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier
                    .size(width = 3.dp, height = 15.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(ORANGE_ACCENT)
            )
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(PILL_BG)
                .border(0.5.dp, PILL_BORDER, RoundedCornerShape(999.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = count.toString(),
                color = PILL_TEXT,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
            )
        }
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
fun ChannelCard(channel: Channel, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = tween(100),
        label = "channel_card_scale"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isPressed) CARD_BORDER_PRESS else CARD_BORDER_REST,
        animationSpec = tween(100),
        label = "channel_card_border"
    )

    // Soft pulse for live dot: alpha 1.0 to 0.4 and back over 1.6s
    val infiniteTransition = rememberInfiniteTransition(label = "live_pulse")
    val liveAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "live_alpha"
    )

    val isLive = channel.videoUrl.isNotBlank()
    val logoUrl = channel.logo?.trim().orEmpty()
    val hasLogo = logoUrl.isNotBlank()

    val context = LocalContext.current
    val density = LocalDensity.current
    val targetPx = with(density) { 54.dp.roundToPx() } * 2 // 2x for full resolution

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

    val imageRequest = remember(modelData, targetPx) {
        ImageRequest.Builder(context)
            .data(modelData)
            .size(targetPx, targetPx)
            .crossfade(true)
            .build()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .background(CARD_BG)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(start = 3.dp, end = 3.dp, top = 8.dp, bottom = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // ── Logo with live dot overlay ─────────────────────────────────
            Box(
                modifier = Modifier.size(54.dp),
                contentAlignment = Alignment.Center
            ) {
                if (hasLogo) {
                    SubcomposeAsyncImage(
                        model = imageRequest,
                        contentDescription = channel.title,
                        modifier = Modifier.size(54.dp)
                    ) {
                        val imgPainter = painter
                        when (imgPainter.state) {
                            is AsyncImagePainter.State.Success -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(Color.White)
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.foundation.Image(
                                        painter = imgPainter,
                                        contentDescription = channel.title,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            else -> {
                                ChannelFallbackCircle(channel.initials)
                            }
                        }
                    }
                } else {
                    ChannelFallbackCircle(channel.initials)
                }

                // ── Live indicator dot ─────────────────────────────────────
                // 9dp red dot (#FF3B30) at top-right corner with 2dp ring in card background (#100C09)
                if (isLive) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(13.dp)
                            .background(CARD_BG, CircleShape)
                            .padding(2.dp)
                            .background(LIVE_RED.copy(alpha = liveAlpha), CircleShape)
                    )
                }
            }

            // ── Channel name ───────────────────────────────────────────────
            Spacer(Modifier.height(7.dp))
            Text(
                text = channel.title,
                color = TEXT_NAME,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ChannelFallbackCircle(initials: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(FALLBACK_CIRCLE_BG),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials.take(2).uppercase(),
            color = ORANGE_ACCENT,
            fontFamily = DisplayFont,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )
    }
}
