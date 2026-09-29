package com.linkstreamz.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Centered modal dialog for selecting a server.
 * Used from the Home screen card tap and the in-player "Switch server" button.
 * Renders with a dark semi-transparent background behind the card.
 */
@Composable
fun ServerChooserSheet(target: PlayerTarget, onDismiss: () -> Unit, onPick: (StreamServer) -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.82f))
                .clickable(onClick = onDismiss, indication = null, interactionSource = remember { MutableInteractionSource() }),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .fillMaxWidth(0.88f)
                    .background(Color(0xFF161616), RoundedCornerShape(20.dp))
                    .clickable(enabled = false, indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = {})
                    .padding(24.dp)
            ) {
                Text(
                    "CHOOSE A SERVER",
                    color = LinkColors.PrimaryText,
                    fontFamily = DisplayFont,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                )
                Text(
                    target.subtitle,
                    color = LinkColors.MutedText,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
                )

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    target.servers.forEachIndexed { index, server ->
                        ServerRow(
                            server = server,
                            index = index,
                            onClick = { onPick(server) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Small contextual dropdown list that appears near the gear icon inside the video player.
 * Its height adapts to the number of available servers (no fixed height — content-driven).
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

    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF222222), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(10.dp).background(pingColor, CircleShape))

        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(server.name, color = Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                if (isRecommended) {
                    Text(
                        "RECOMMENDED",
                        color = Accent,
                        fontFamily = MonoFont,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(Accent.copy(alpha = 0.18f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                server.quality?.let {
                    Text(
                        it,
                        color = Muted,
                        fontFamily = MonoFont,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
            }
        }

        Icon(Icons.Default.ChevronRight, null, tint = Muted, modifier = Modifier.size(18.dp))
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
    0 -> Color(0xFF4CAF50)  // green — best
    1 -> Color(0xFFFFC107)  // amber
    else -> Color(0xFFF44336)  // red
}
