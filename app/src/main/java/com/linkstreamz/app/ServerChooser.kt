package com.linkstreamz.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// ── Server-chooser frame constants (Task 1) ───────────────────────────────────
private val SC_BG               = Color(0xFF111111)
private val SC_BORDER           = Color(0xFF2E1F14)
private val SC_ROW_BG           = Color(0xFF171717)
private val SC_ROW_BORDER       = Color(0xFF262626)
private val SC_REC_BG           = Color(0xFF1D140D)
private val SC_REC_BORDER       = Color(0xFFFF6600)
private val SC_TEXT             = Color(0xFFF5EDE6)
private val SC_MUTED            = Color(0xFFC9B8AA)
private val SC_DOT_GREEN        = Color(0xFF4ADE80)
private val SC_DOT_YELLOW       = Color(0xFFFACC15)
private val SC_DOT_RED          = Color(0xFFF87171)
private val SC_BEST_BG          = Color(0xFFFF6600)
private val SC_BEST_TEXT        = Color(0xFF1A0A00)

/**
 * Centered modal dialog for selecting a server.
 * Used from the Home screen card tap and the in-player "Switch server" button.
 * Compact 210dp wide card with dark-orange branding (Task 1).
 */
@Composable
fun ServerChooserSheet(target: PlayerTarget, onDismiss: () -> Unit, onPick: (StreamServer) -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // Scrim — 60% black, tap to dismiss
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.60f))
                .clickable(
                    onClick = onDismiss,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ),
            contentAlignment = Alignment.Center,
        ) {
            // Modal card with enter/exit animation
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(180)) + scaleIn(tween(180, easing = androidx.compose.animation.core.EaseOut), initialScale = 0.96f),
                exit  = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.96f),
            ) {
                Column(
                    Modifier
                        .widthIn(max = 420.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(SC_BG)
                        // stop taps from falling through to the scrim
                        .clickable(
                            enabled = false,
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = {},
                        )
                        .padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 8.dp),
                ) {
                    // Header — sentence case, no subtitle
                    Text(
                        "Choose a server",
                        color = SC_TEXT,
                        fontFamily = DisplayFont,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.26.sp,
                        modifier = Modifier.padding(horizontal = 1.dp, vertical = 0.dp),
                    )

                    Spacer(Modifier.height(7.dp))

                    // Row list — scrollable when more than ~5 servers
                    Column(
                        Modifier
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        target.servers.forEachIndexed { index, server ->
                            ServerRow(
                                server   = server,
                                index    = index,
                                onClick  = { onPick(server) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Small contextual dropdown list that appears near the gear icon inside the video player.
 * Its height adapts to the number of available servers (no fixed height — content-driven).
 * NOT changed by Task 1 (this is the in-player dropdown, not the chooser modal).
 */
@Composable
fun ServerDropdown(
    target: PlayerTarget,
    visible: Boolean,
    onDismiss: () -> Unit,
    onPick: (StreamServer) -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(150)) + slideInVertically(tween(150)) { it / 4 },
        exit = fadeOut(tween(120)) + slideOutVertically(tween(120)) { it / 4 },
    ) {
        Column(
            Modifier
                .background(Color(0xFF1A1A1A), RoundedCornerShape(14.dp))
                .padding(vertical = 8.dp, horizontal = 4.dp)
                .widthIn(min = 200.dp, max = 280.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                "SELECT SERVER",
                color = Muted,
                fontFamily = MonoFont,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
            target.servers.forEachIndexed { index, server ->
                ServerDropdownRow(
                    server = server,
                    index = index,
                    isSelected = server == target.selectedServer,
                    onClick = { onPick(server); onDismiss() },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Shared row components
// ---------------------------------------------------------------------------

@Composable
private fun ServerRow(server: StreamServer, index: Int, onClick: () -> Unit) {
    val isRecommended = index == 0
    val pingColor = pingColorForIndex(index)
    val rowBg     = if (isRecommended) SC_REC_BG     else SC_ROW_BG
    val rowBorder = if (isRecommended) SC_REC_BORDER else SC_ROW_BORDER
    val borderW   = if (isRecommended) 1.5.dp         else 1.dp

    // The visual row is 30dp tall; keep it wrapped in a Box but with less vertical padding
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 34.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(rowBg)
                .border(borderW, rowBorder, RoundedCornerShape(9.dp))
                .padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            // Status dot
            Box(Modifier.size(6.dp).background(pingColor, CircleShape))

            // Server name + optional BEST chip
            Row(
                Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    server.name,
                    color = SC_TEXT,
                    fontSize = 12.sp,
                    fontWeight = if (isRecommended) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isRecommended) {
                    Text(
                        "BEST",
                        color = SC_BEST_TEXT,
                        fontFamily = MonoFont,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.4.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(SC_BEST_BG)
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }

            // Quality tag — plain text, no box
            server.quality?.let {
                Text(it, color = SC_MUTED, fontFamily = MonoFont, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun ServerDropdownRow(
    server: StreamServer,
    index: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val isRecommended = index == 0
    val pingColor = pingColorForIndex(index)

    Row(
        Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) Accent.copy(alpha = 0.12f) else Color.Transparent,
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(8.dp).background(pingColor, CircleShape))

        Column(Modifier.weight(1f)) {
            Text(
                server.name,
                color = if (isSelected) Accent else Text,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isRecommended) {
                    Text(
                        "RECOMMENDED",
                        color = Accent,
                        fontFamily = MonoFont,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                server.quality?.let {
                    Text(it, color = Muted, fontFamily = MonoFont, fontSize = 8.sp)
                }
            }
        }

        if (isSelected) {
            Box(Modifier.size(6.dp).background(Accent, CircleShape))
        }
    }
}

private fun pingColorForIndex(index: Int): Color = when (index) {
    0    -> SC_DOT_GREEN
    1    -> SC_DOT_YELLOW
    else -> SC_DOT_RED
}
