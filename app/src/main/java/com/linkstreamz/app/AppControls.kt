package com.linkstreamz.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────────────────────────────────────
// Link Streamz shared control system.
//
// Every value below is copied from the Season selector chips on the series
// player screen, which are the design source of truth:
//   radius 8.dp · height 30.dp (12sp text + 6dp vertical padding × 2)
//   horizontal padding 12.dp · 8.dp gap · 14sp semibold label
//   selected = orange fill / dark text · unselected = dark card fill / light text
// No border in either state. Visible sizes stay compact; the touch target is
// always at least 48dp via minimumInteractiveComponentSize.
// ─────────────────────────────────────────────────────────────────────────────

/** The one corner radius for every button, chip and tab in the app. */
val AppCornerRadius = 8.dp

/** The shared control shape. Change [AppCornerRadius] to restyle everything. */
val AppControlShape: RoundedCornerShape get() = RoundedCornerShape(AppCornerRadius)

object AppControlSpec {
    val ChipHeight: Dp = 30.dp
    val ChipPaddingHorizontal: Dp = 12.dp
    val ChipPaddingVertical: Dp = 6.dp
    val ChipGap: Dp = 8.dp
    val ChipTextSize: TextUnit = 14.sp

    val ButtonHeight: Dp = 40.dp
    val LargeButtonHeight: Dp = 48.dp
    val ButtonTextSize: TextUnit = 14.sp

    val IconButtonSize: Dp = 36.dp
    val IconSize: Dp = 20.dp
    val TouchSize: Dp = 48.dp

    // Dense facets inside the filter popover and the category row. Visual size
    // stays 26dp; the clickable box is 32dp so fat fingers still hit reliably.
    val CompactChipHeight: Dp = 26.dp
    val CompactChipTouchHeight: Dp = 32.dp
    val CompactChipTextSize: TextUnit = 13.sp
    val CompactChipPaddingHorizontal: Dp = 10.dp
    val SegmentedHeight: Dp = 28.dp
    val SegmentedTextSize: TextUnit = 12.5.sp

    val SelectedFill: Color get() = LinkColors.OrangePrimary
    val SelectedText: Color get() = Color.Black
    val UnselectedFill: Color get() = LinkColors.CardBg
    val UnselectedText: Color get() = LinkColors.PrimaryText
    val PressedFill: Color get() = LinkColors.OrangePrimary.copy(alpha = 0.78f)
    val DisabledAlpha: Float = 0.4f
}

/** Common press/disabled treatment shared by all controls. */
@Composable
private fun controlModifier(
    enabled: Boolean,
    onClick: () -> Unit,
    description: String?,
): Pair<Modifier, Boolean> {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val modifier = Modifier
        .minimumInteractiveComponentSize()
        .clickable(
            interactionSource = interaction,
            indication = LocalIndication.current,
            enabled = enabled,
            onClick = onClick,
        )
        .then(
            if (description == null) Modifier else Modifier.semantics {
                contentDescription = description
                role = androidx.compose.ui.semantics.Role.Button
            }
        )
    return modifier to (pressed && enabled)
}

/** The Season-chip style: a compact rounded rectangle, no border. */
@Composable
fun AppChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
    trailingIcon: ImageVector? = null,
) {
    val (click, pressed) = controlModifier(enabled, onClick, contentDescription)
    val fill = if (selected) {
        if (pressed) AppControlSpec.PressedFill else AppControlSpec.SelectedFill
    } else {
        if (pressed) LinkColors.CardBg.copy(alpha = 0.78f) else AppControlSpec.UnselectedFill
    }
    // Outer box owns the 48dp touch target; inner box owns the compact visuals.
    Box(modifier.then(click), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .clip(AppControlShape)
                .background(fill)
                .defaultMinSize(minHeight = AppControlSpec.ChipHeight)
                .padding(
                    horizontal = AppControlSpec.ChipPaddingHorizontal,
                    vertical = AppControlSpec.ChipPaddingVertical,
                )
                .then(if (enabled) Modifier else Modifier.graphicsLayer { alpha = AppControlSpec.DisabledAlpha }),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    color = if (selected) AppControlSpec.SelectedText else AppControlSpec.UnselectedText,
                    fontFamily = BodyFont,
                    fontSize = AppControlSpec.ChipTextSize,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                if (trailingIcon != null && selected) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        trailingIcon,
                        contentDescription = null,
                        tint = AppControlSpec.SelectedText,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
    }
}

/** Alias used for main tabs and sub-tabs — identical Season-chip style. */
@Composable
fun AppTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
) = AppChip(label, selected, onClick, modifier, enabled, contentDescription, null)

/** Same shape as AppChip, in three variants. No outline in any state. */
@Composable
fun AppButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: AppButtonVariant = AppButtonVariant.Primary,
    large: Boolean = false,
    enabled: Boolean = true,
    contentDescription: String? = null,
    leadingIcon: ImageVector? = null,
) {
    val (click, pressed) = controlModifier(enabled, onClick, contentDescription)
    val fill = when (variant) {
        AppButtonVariant.Primary -> if (pressed) AppControlSpec.PressedFill else AppControlSpec.SelectedFill
        AppButtonVariant.Secondary -> if (pressed) AppControlSpec.UnselectedFill.copy(alpha = 0.78f) else AppControlSpec.UnselectedFill
        AppButtonVariant.Text -> Color.Transparent
    }
    val textColor = when (variant) {
        AppButtonVariant.Primary -> AppControlSpec.SelectedText
        AppButtonVariant.Secondary, AppButtonVariant.Text -> AppControlSpec.UnselectedText
    }
    Box(modifier.then(click), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .clip(AppControlShape)
                .background(fill)
                .defaultMinSize(
                    minHeight = if (large) AppControlSpec.LargeButtonHeight else AppControlSpec.ButtonHeight,
                )
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .then(if (enabled) Modifier else Modifier.graphicsLayer { alpha = AppControlSpec.DisabledAlpha }),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leadingIcon != null) {
                    Icon(
                        leadingIcon,
                        contentDescription = null,
                        tint = textColor,
                        modifier = Modifier.size(AppControlSpec.IconSize),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = label,
                    color = textColor,
                    fontFamily = BodyFont,
                    fontSize = AppControlSpec.ButtonTextSize,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

enum class AppButtonVariant { Primary, Secondary, Text }

/** 36dp rounded-square icon button with a 20dp icon and a 48dp touch target. */
@Composable
fun AppIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = AppControlSpec.SelectedFill,
    enabled: Boolean = true,
) {
    val (click, pressed) = controlModifier(enabled, onClick, contentDescription)
    Box(modifier.then(click), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .clip(AppControlShape)
                .background(if (pressed) AppControlSpec.UnselectedFill.copy(alpha = 0.78f) else AppControlSpec.UnselectedFill)
                .size(AppControlSpec.IconButtonSize)
                .then(if (enabled) Modifier else Modifier.graphicsLayer { alpha = AppControlSpec.DisabledAlpha }),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(AppControlSpec.IconSize))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Dense variants — same Season-chip language, smaller visual footprint
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 26dp-tall chip with a 32dp touch target. No outline; orange fill when
 * selected. Announces its selected state to TalkBack via [stateDescription].
 */
@Composable
fun AppCompactChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    trailingIcon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fill = if (selected) {
        if (pressed) AppControlSpec.PressedFill else AppControlSpec.SelectedFill
    } else {
        if (pressed) LinkColors.CardBg.copy(alpha = 0.78f) else AppControlSpec.UnselectedFill
    }
    Box(
        modifier
            .defaultMinSize(minHeight = AppControlSpec.CompactChipTouchHeight)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {
                role = androidx.compose.ui.semantics.Role.Button
                this.contentDescription = contentDescription ?: label
                this.stateDescription = if (selected) "Selected" else "Not selected"
            }
            .then(if (enabled) Modifier else Modifier.graphicsLayer { alpha = AppControlSpec.DisabledAlpha }),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .clip(AppControlShape)
                .background(fill)
                .height(AppControlSpec.CompactChipHeight)
                .padding(horizontal = AppControlSpec.CompactChipPaddingHorizontal),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    color = if (selected) AppControlSpec.SelectedText else AppControlSpec.UnselectedText,
                    fontFamily = BodyFont,
                    fontSize = AppControlSpec.CompactChipTextSize,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                if (trailingIcon != null && selected) {
                    Spacer(Modifier.width(3.dp))
                    Icon(
                        trailingIcon,
                        contentDescription = null,
                        tint = AppControlSpec.SelectedText,
                        modifier = Modifier.size(11.dp),
                    )
                }
            }
        }
    }
}

/** Two-option segmented toggle, 28dp tall, built from the shared radius. */
@Composable
fun <T> AppSegmentedToggle(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(AppControlSpec.SegmentedHeight)
            .clip(AppControlShape)
            .background(AppControlSpec.UnselectedFill)
            .padding(2.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(AppControlShape)
                    .background(if (isSelected) AppControlSpec.SelectedFill else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = LocalIndication.current,
                        onClick = { onSelect(option) },
                    )
                    .semantics(mergeDescendants = true) {
                        role = androidx.compose.ui.semantics.Role.Button
                        contentDescription = label(option)
                        stateDescription = if (isSelected) "Selected" else "Not selected"
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    color = if (isSelected) AppControlSpec.SelectedText else AppControlSpec.UnselectedText,
                    fontFamily = BodyFont,
                    fontSize = AppControlSpec.SegmentedTextSize,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Shared inline search toolbar. Collapsed: [collapsedLeading] (scope chips or
 * the screen title) plus a magnifier. Expanded: a focused field plus an "x",
 * with [trailing] (the Movies filter button) kept visible in both states.
 *
 * Back, the "x", or losing focus while empty collapses the field.
 */
@Composable
fun InlineSearchRow(
    expanded: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    collapsedLeading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    BackHandler(enabled = expanded, onCollapse)
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(expanded) {
        if (expanded) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    AnimatedContent(
        targetState = expanded,
        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
        label = "inline-search",
    ) { isExpanded ->
        Row(
            modifier
                .fillMaxWidth()
                .heightIn(min = AppControlSpec.TouchSize),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            if (isExpanded) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(AppControlSpec.TouchSize - 4.dp)
                        .clip(AppControlShape)
                        .background(AppControlSpec.UnselectedFill)
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = LinkColors.MutedText,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.weight(1f)) {
                            if (query.isEmpty()) {
                                Text(
                                    placeholder,
                                    color = LinkColors.MutedText,
                                    fontFamily = BodyFont,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                )
                            }
                            BasicTextField(
                                value = query,
                                onValueChange = onQueryChange,
                                textStyle = TextStyle(
                                    color = LinkColors.PrimaryText,
                                    fontFamily = BodyFont,
                                    fontSize = 13.sp,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                                    .onFocusChanged { state ->
                                        if (!state.isFocused && query.isEmpty()) onCollapse()
                                    },
                            )
                        }
                    }
                }
                if (trailing != null) trailing()
                AppIconButton(Icons.Default.Close, "Close search", onCollapse)
            } else {
                if (collapsedLeading != null) collapsedLeading()
                Spacer(Modifier.weight(1f))
                AppIconButton(Icons.Default.Search, "Search", onExpand)
                if (trailing != null) trailing()
            }
        }
    }
}
