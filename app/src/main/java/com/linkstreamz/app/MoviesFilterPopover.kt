package com.linkstreamz.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PopoverSurface = Color(0xFF161616)
private val PopoverHairline = Color(0xFF303030)
private val PopoverShape = RoundedCornerShape(14.dp)

/**
 * Compact filter card anchored under the Movies filter button. It is neither a
 * bottom sheet nor a page: an outside-click scrim dismisses it while the
 * toolbar stays fully interactive. The draft state lives here and is only
 * published through [onApply], so the toolbar badge/chips reflect applied
 * filters, not half-finished edits.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoviesFilterPopover(
    state: MoviesFilterState,
    movies: List<Movie>,
    series: List<Series>,
    onDismiss: () -> Unit,
    onApply: (MoviesFilterState) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember(state) { mutableStateOf(state) }
    BackHandler(onBack = onDismiss)

    val configuration = LocalConfiguration.current
    val isWide = configuration.screenWidthDp >= 600 ||
        configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val cardWidth = if (isWide) 240.dp else 208.dp
    val maxBodyHeight = (configuration.screenHeightDp.dp * 0.7f) - 16.dp

    val scale by animateFloatAsState(1f, tween(150), label = "filter-popover-scale")
    val alpha by animateFloatAsState(1f, tween(150), label = "filter-popover-alpha")

    val genres = remember(movies, series) { availableGenres(movies, series) }
    val years = remember(movies, series) { availableYears(movies, series) }
    val vjs = remember(movies, series) { availableVJs(movies, series) }
    val count = remember(draft, movies, series) { resultCount(movies, series, draft) }
    val resultLabel = if (draft.contentType == MediaContentType.MOVIES) "movies" else "series"

    Box(
        Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    ) {
        Column(
            modifier
                .align(Alignment.TopEnd)
                .padding(end = 16.dp, top = 4.dp)
                .width(cardWidth)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                    transformOrigin = TransformOrigin(0.9f, 0f)
                }
                .shadow(8.dp, PopoverShape)
                .clip(PopoverShape)
                .background(PopoverSurface)
                .border(1.dp, PopoverHairline, PopoverShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(10.dp),
        ) {
            // Pointer aligned with the filter button on the right edge.
            Box(Modifier.fillMaxWidth().heightIn(min = 12.dp)) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 14.dp)
                        .size(12.dp)
                        .rotate(45f)
                        .background(PopoverSurface),
                )
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxBodyHeight)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AppSegmentedToggle(
                    options = MediaContentType.entries.toList(),
                    selected = draft.contentType,
                    label = { it.label },
                    onSelect = { draft = draft.copy(contentType = it) },
                )

                AppSegmentedToggle(
                    options = TranslatedFilter.entries.toList(),
                    selected = draft.translatedFilter,
                    label = { it.label },
                    onSelect = { language ->
                        // VJs only exist on translated titles — drop a stale VJ
                        // so it cannot silently empty the results.
                        draft = if (language == TranslatedFilter.NON_TRANSLATED) {
                            draft.copy(translatedFilter = language, vjFilter = null)
                        } else {
                            draft.copy(translatedFilter = language)
                        }
                    },
                )

                if (genres.isNotEmpty()) {
                    PopoverLabel("GENRE")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        genres.forEach { genre ->
                            val selected = genre in draft.genres
                            AppCompactChip(
                                label = genre,
                                selected = selected,
                                onClick = {
                                    draft = draft.copy(
                                        genres = if (selected) draft.genres - genre else draft.genres + genre
                                    )
                                },
                                contentDescription = "$genre genre",
                                trailingIcon = Icons.Default.Check,
                            )
                        }
                    }
                }

                if (years.isNotEmpty()) {
                    PopoverLabel("YEAR")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        AppCompactChip(
                            label = "Any",
                            selected = draft.years.isEmpty(),
                            onClick = { draft = draft.copy(years = emptySet()) },
                            contentDescription = "Any year",
                        )
                        years.forEach { year ->
                            AppCompactChip(
                                label = year.toString(),
                                selected = draft.years == setOf(year),
                                onClick = { draft = draft.copy(years = setOf(year)) },
                                contentDescription = "Year $year",
                            )
                        }
                    }
                }

                if (isVjSectionVisible(draft) && vjs.isNotEmpty()) {
                    PopoverLabel("VJ")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        AppCompactChip(
                            label = "All",
                            selected = draft.vjFilter == null,
                            onClick = { draft = draft.copy(vjFilter = null) },
                            contentDescription = "All VJs",
                        )
                        vjs.forEach { vj ->
                            AppCompactChip(
                                label = vj,
                                selected = draft.vjFilter == vj,
                                onClick = { draft = draft.copy(vjFilter = vj) },
                                contentDescription = "VJ $vj",
                            )
                        }
                    }
                }

                Spacer(Modifier.height(2.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AppButton(
                        label = "Reset",
                        onClick = { draft = MoviesFilterState() },
                        variant = AppButtonVariant.Secondary,
                        contentDescription = "Reset filters",
                        modifier = Modifier.weight(0.8f),
                    )
                    AppButton(
                        label = "Show $count $resultLabel",
                        onClick = { onApply(draft) },
                        contentDescription = "Show $count $resultLabel",
                        modifier = Modifier.weight(1.6f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PopoverLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = LsColor.TextDim,
        fontFamily = MonoFont,
        fontSize = 9.5.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        maxLines = 1,
    )
}
