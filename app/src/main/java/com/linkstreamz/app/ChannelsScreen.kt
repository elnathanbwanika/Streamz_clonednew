package com.linkstreamz.app

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.Coil
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest

private val CATEGORY_ORDER = listOf("General", "News", "Entertainment", "Music", "Sports", "Kids")

private val CARD_BG       = LsColor.Card
private val LIVE_RED      = LsColor.Accent
private val THUMB_BG      = LsColor.AccentDim
private val ORANGE_ACCENT = LsColor.Accent
private val TEXT_NAME     = LsColor.Text

@Composable
fun ChannelsScreen(channels: List<Channel>, onWatch: (Channel) -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    val allChannels = channels
    var localFilter by remember { mutableStateOf(true) }   // true = Local, false = Abroad
    var catFilter by remember { mutableStateOf<String?>(null) }

    val scoped = remember(allChannels, localFilter) {
        allChannels.filter { if (localFilter) it.country == "Uganda" else it.country != "Uganda" }
    }
    val scopedCategories = remember(scoped) {
        scoped.map { it.category }.distinct()
            .sortedBy { CATEGORY_ORDER.indexOf(it).takeIf { i -> i >= 0 } ?: 99 }
    }

    // Search still scans the whole catalogue, exactly as before.
    val filtered = remember(searchQuery, allChannels, catFilter) {
        allChannels
            .let { list -> if (searchQuery.length >= 2) list.filter {
                it.title.contains(searchQuery, true) || it.program.contains(searchQuery, true) || it.category.contains(searchQuery, true)
            } else list }
            .let { list -> if (catFilter != null) list.filter { it.category == catFilter } else list }
    }

    val localChannels = allChannels.filter { it.country == "Uganda" }
    val isSearch = searchQuery.length >= 2

    fun collapseSearch() {
        searchQuery = ""
        searchOpen = false
    }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        // ── One toolbar row: scope chips left, search right ────────────────
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            InlineSearchRow(
                expanded = searchOpen,
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onExpand = { searchOpen = true },
                onCollapse = { collapseSearch() },
                placeholder = "Search channels or categories",
                collapsedLeading = {
                    Row(horizontalArrangement = Arrangement.spacedBy(AppControlSpec.ChipGap)) {
                        AppChip(
                            label = "Local",
                            selected = localFilter,
                            onClick = { localFilter = true; catFilter = null },
                            contentDescription = "Local channels",
                        )
                        AppChip(
                            label = "Abroad",
                            selected = !localFilter,
                            onClick = { localFilter = false; catFilter = null },
                            contentDescription = "Abroad channels",
                        )
                    }
                },
            )
        }

        // ── One compact, horizontally scrolling category row ───────────────
        if (!isSearch) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { CategoryChip("All", catFilter == null) { catFilter = null } }
                items(scopedCategories) { cat ->
                    CategoryChip(cat, catFilter == cat) {
                        catFilter = if (catFilter == cat) null else cat
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        // One pulse clock for the whole grid — read by every ChannelCard.
        val livePulse = rememberInfiniteTransition(label = "channels_live_pulse")
        val liveAlpha by livePulse.animateFloat(
            initialValue = 1f,
            targetValue = 0.4f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "channels_live_alpha"
        )

        // ── Three-column grid with 8dp gaps ───────────────────────────────
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
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
                    ChannelCard(channel = ch, liveAlpha = liveAlpha, onClick = { onWatch(ch) })
                }
            } else if (catFilter != null) {
                val catItems = allChannels.filter {
                    (if (localFilter) it.country == "Uganda" else it.country != "Uganda") && it.category == catFilter
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader(title = catFilter!!, count = catItems.size)
                }
                items(catItems, key = { it.id }) { ch ->
                    ChannelCard(channel = ch, liveAlpha = liveAlpha, onClick = { onWatch(ch) })
                }
            } else if (localFilter) {
                // Local rows: General/Entertainment local, Sports, Kids, News local
                val localRow = localChannels.take(8)
                if (localRow.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(title = "Local Channels", count = localRow.size)
                    }
                    items(localRow, key = { "local_${it.id}" }) { ch ->
                        ChannelCard(channel = ch, liveAlpha = liveAlpha, onClick = { onWatch(ch) })
                    }
                }
                val localSports = allChannels.filter { it.category == "Sports" && it.country == "Uganda" }
                if (localSports.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(title = "Sports", count = localSports.size)
                    }
                    items(localSports, key = { "sports_${it.id}" }) { ch ->
                        ChannelCard(channel = ch, liveAlpha = liveAlpha, onClick = { onWatch(ch) })
                    }
                }
                val localNews = allChannels.filter { it.category == "News" && it.country == "Uganda" }
                if (localNews.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(title = "News", count = localNews.size)
                    }
                    items(localNews, key = { "news_${it.id}" }) { ch ->
                        ChannelCard(channel = ch, liveAlpha = liveAlpha, onClick = { onWatch(ch) })
                    }
                }
                val localEnt = allChannels.filter { it.category == "Entertainment" && it.country == "Uganda" }
                if (localEnt.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(title = "Entertainment", count = localEnt.size)
                    }
                    items(localEnt, key = { "ent_${it.id}" }) { ch ->
                        ChannelCard(channel = ch, liveAlpha = liveAlpha, onClick = { onWatch(ch) })
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
                            ChannelCard(channel = ch, liveAlpha = liveAlpha, onClick = { onWatch(ch) })
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
            .padding(top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            color = LinkColors.PrimaryText,
            fontFamily = DisplayFont,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            maxLines = 1,
        )
        Text(
            text = count.toString(),
            color = LinkColors.OrangePrimary,
            fontFamily = MonoFont,
            fontSize = 11.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    AppCompactChip(label, selected, onClick, contentDescription = label)
}

@Composable
fun ChannelCard(channel: Channel, onClick: () -> Unit, liveAlpha: Float = 1f) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = tween(100),
        label = "channel_card_scale"
    )
    val isLive = channel.videoUrl.isNotBlank()
    val logoUrl = channel.logo?.trim().orEmpty()
    val hasLogo = logoUrl.isNotBlank()

    val context = LocalContext.current
    val imageLoader = if (ContentRouting.isLocalChannel(channel)) {
        AppImages.direct(context)
    } else {
        Coil.imageLoader(context)
    }

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

    val imageRequest = remember(modelData) {
        ImageRequest.Builder(context)
            .data(modelData)
            .crossfade(true)
            .build()
    }

    val cardShape = RoundedCornerShape(12.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(cardShape)
            .background(CARD_BG)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        // ── Top section (thumbnail): fixed height 76dp, full width flush ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .background(THUMB_BG),
            contentAlignment = Alignment.Center
        ) {
            if (hasLogo) {
                SubcomposeAsyncImage(
                    model = imageRequest,
                    contentDescription = channel.title,
                    imageLoader = imageLoader,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                ) {
                    val imgPainter = painter
                    when (imgPainter.state) {
                        is AsyncImagePainter.State.Success -> {
                            androidx.compose.foundation.Image(
                                painter = imgPainter,
                                contentDescription = channel.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        else -> {
                            ChannelFallback(channel)
                        }
                    }
                }
            } else {
                ChannelFallback(channel)
            }

            // ── Live indicator dot ─────────────────────────────────────────
            // 10dp red dot (#FF3B30) with 1.5dp border in card background color (#1A120C)
            if (isLive) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 6.dp, end = 6.dp)
                        .size(13.dp)
                        .background(CARD_BG, CircleShape)
                        .padding(1.5.dp)
                        .background(LIVE_RED.copy(alpha = liveAlpha), CircleShape)
                )
            }

            TvBadgeOverlay(
                placement = channel.tvBadge?.card,
                modifier = Modifier.matchParentSize(),
            )
        }

        // ── Bottom section (label): directly under thumbnail ───────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CARD_BG)
                .padding(horizontal = 4.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
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
private fun ChannelFallback(channel: Channel, modifier: Modifier = Modifier) {
    val initialsText = remember(channel.initials, channel.title) {
        val raw = channel.initials.trim()
        if (raw.isNotBlank()) raw
        else channel.title.split(" ").mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString("").ifBlank { "?" }
    }
    val fontSize = if (initialsText.length >= 3) 17.sp else 22.sp

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(THUMB_BG),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initialsText.uppercase(),
            color = ORANGE_ACCENT,
            fontFamily = DisplayFont,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize,
            textAlign = TextAlign.Center
        )
    }
}
