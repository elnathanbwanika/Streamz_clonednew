package com.linkstreamz.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// ── Status dot colours ────────────────────────────────────────────────────────
private val SC_DOT_GREEN  = Color(0xFF4ADE80)
private val SC_DOT_YELLOW = Color(0xFFFACC15)
private val SC_DOT_RED    = Color(0xFFF87171)

/**
 * Centered modal dialog for selecting a server.
 * Used from the Home screen card tap and the in-player "Switch server" button.
 */
@Composable
fun ServerChooserSheet(target: PlayerTarget, onDismiss: () -> Unit, onPick: (StreamServer) -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.70f))
                .clickable(
                    onClick = onDismiss,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ),
            Alignment.Center,
        ) {
            val selectedServer = target.selectedServer ?: target.servers.firstOrNull()

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .border(1.dp, Color(0xFFF97316).copy(alpha = 0.38f), RoundedCornerShape(22.dp))
                    .drawBehind {
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFFF97316).copy(alpha = 0.09f), Color.Transparent),
                                center = Offset(size.width / 2f, 0f),
                                radius = size.height * 0.7f,
                            )
                        )
                    }
                    .background(Color(0xFF0D0D0D))
                    .padding(top = 20.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
            ) {
                Text(
                    "Choose a server",
                    color = Color(0xFFF3EBE4),
                    fontSize = 16.sp,
                    fontWeight = FontWeight(600),
                    letterSpacing = 0.2.sp,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )

                Spacer(Modifier.height(14.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    target.servers.forEachIndexed { index, server ->
                        ChooserRow(
                            server = server,
                            index = index,
                            isSelected = server == selectedServer,
                            onClick = { onPick(server); onDismiss() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChooserRow(server: StreamServer, index: Int, isSelected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val rowScale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = tween(120),
        label = "serverRowScale",
    )

    val dotColor = when (index) {
        0    -> SC_DOT_GREEN
        1    -> SC_DOT_YELLOW
        else -> SC_DOT_RED
    }
    val rowFill        = if (isSelected) Color(0xFFF97316).copy(alpha = 0.10f) else Color(0xFF131110)
    val rowBorderColor = if (isSelected) Color(0xFFF97316)                     else Color(0xFFF97316).copy(alpha = 0.20f)
    val rowBorderWidth = if (isSelected) 2.dp                                  else 1.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .scale(rowScale)
            .clip(RoundedCornerShape(12.dp))
            .background(rowFill)
            .border(rowBorderWidth, rowBorderColor, RoundedCornerShape(12.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(vertical = 11.dp, horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Dot with glow ring
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(15.dp).background(dotColor.copy(alpha = 0.18f), CircleShape))
                Box(Modifier.size(9.dp).background(dotColor, CircleShape))
            }
            Text(
                server.name,
                color = Color(0xFFF3EBE4),
                fontSize = 14.sp,
                fontWeight = FontWeight(500),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (index == 0) {
                Text(
                    "BEST",
                    color = Color(0xFF1A0A00),
                    fontSize = 9.sp,
                    fontWeight = FontWeight(800),
                    letterSpacing = 0.5.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFFFF8F2E), Color(0xFFF26A0A)),
                                start = Offset(0f, 0f),
                                end = Offset(100f, 100f),
                            )
                        )
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
        }
        Text(
            server.quality ?: "SD",
            color = Color(0xFF8F857C),
            fontSize = 12.sp,
            fontWeight = FontWeight(600),
            letterSpacing = 1.sp,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// In-player dropdown (gear icon → server list). NOT the chooser modal.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Small contextual dropdown list that appears near the gear icon inside the video player.
 * Height adapts to number of available servers (content-driven, no fixed height).
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
        exit  = fadeOut(tween(120)) + slideOutVertically(tween(120)) { it / 4 },
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

@Composable
private fun ServerDropdownRow(
    server: StreamServer,
    index: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val isRecommended = index == 0
    val pingColor = when (index) {
        0    -> SC_DOT_GREEN
        1    -> SC_DOT_YELLOW
        else -> SC_DOT_RED
    }

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
                color = if (isSelected) Accent else Color(0xFFF5EDE6),
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
