import sys
import re

with open("app/src/main/java/com/linkstreamz/app/ServerChooser.kt", "r") as f:
    content = f.read()

# We need to replace ServerChooserSheet
start_sheet = content.find("@Composable\nfun ServerChooserSheet")
if start_sheet == -1:
    print("Could not find ServerChooserSheet")
    sys.exit(1)

# Find end of ServerChooserSheet (before ServerDropdown)
end_sheet = content.find("/**\n * Small contextual dropdown list", start_sheet)
if end_sheet == -1:
    print("Could not find end of ServerChooserSheet")
    sys.exit(1)

new_sheet = """@Composable
fun ServerChooserSheet(target: PlayerTarget, onDismiss: () -> Unit, onPick: (StreamServer) -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        androidx.compose.foundation.layout.Box(
            androidx.compose.ui.Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.70f))
                .clickable(
                    onClick = onDismiss,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ),
            androidx.compose.ui.Alignment.Center,
        ) {
            androidx.compose.foundation.layout.Column(
                androidx.compose.ui.Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 300.dp)
                    .shadow(20.dp, RoundedCornerShape(22.dp), spotColor = Color.Black.copy(alpha = 0.85f), ambientColor = Color.Black.copy(alpha = 0.85f))
                    .clip(RoundedCornerShape(22.dp))
                    .border(1.dp, Color(0xFFF97316).copy(alpha = 0.38f), RoundedCornerShape(22.dp))
                    .background(Color(0xFF0D0D0D))
                    .drawBehind {
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFFF97316).copy(alpha = 0.09f), Color.Transparent),
                                center = Offset(size.width / 2f, 0f),
                                radius = size.height * 0.7f
                            )
                        )
                    }
                    .padding(top = 20.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
            ) {
                Text(
                    "Choose a server",
                    color = Color(0xFFF3EBE4),
                    fontSize = 16.sp,
                    fontWeight = FontWeight(600),
                    letterSpacing = 0.2.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                Spacer(Modifier.height(14.dp))

                val selectedServer = target.selectedServer ?: target.servers.firstOrNull()
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    target.servers.forEachIndexed { index, server ->
                        NewServerRow(
                            server = server,
                            index = index,
                            isSelected = server == selectedServer,
                            onClick = { onPick(server); onDismiss() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NewServerRow(server: StreamServer, index: Int, isSelected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by androidx.compose.foundation.interaction.collectIsPressedAsState(interactionSource)
    val scale by androidx.compose.animation.core.animateFloatAsState(targetValue = if (isPressed) 0.985f else 1f, animationSpec = tween(120))

    val dotColor = when (index) {
        0 -> Color(0xFF10B981)
        1 -> Color(0xFFFBBF24)
        else -> Color(0xFFEF4444)
    }

    val rowFill = if (isSelected) Color(0xFFF97316).copy(alpha = 0.10f) else Color(0xFF131110)
    val rowBorderColor = if (isSelected) Color(0xFFF97316) else Color(0xFFF97316).copy(alpha = 0.20f)
    val rowBorderWidth = if (isSelected) 2.dp else 1.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .androidx.compose.ui.draw.scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(rowFill)
            .border(rowBorderWidth, rowBorderColor, RoundedCornerShape(12.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(vertical = 11.dp, horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
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
                overflow = TextOverflow.Ellipsis
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
                        .background(Brush.linearGradient(
                            colors = listOf(Color(0xFFFF8F2E), Color(0xFFF26A0A)),
                            start = Offset(0f, 0f),
                            end = Offset(100f, 100f)
                        ))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
        Text(
            server.quality ?: "SD",
            color = Color(0xFF8F857C),
            fontSize = 12.sp,
            fontWeight = FontWeight(600),
            letterSpacing = 1.sp
        )
    }
}

"""

content = content[:start_sheet] + new_sheet + content[end_sheet:]

# We also need to remove the old ServerRow.
start_old_row = content.find("@Composable\nprivate fun ServerRow")
if start_old_row != -1:
    end_old_row = content.find("@Composable\nprivate fun ServerDropdownRow", start_old_row)
    if end_old_row != -1:
        content = content[:start_old_row] + content[end_old_row:]

# Make sure imports are added for scale
if "import androidx.compose.ui.draw.scale" not in content:
    content = "import androidx.compose.ui.draw.scale\n" + content

with open("app/src/main/java/com/linkstreamz/app/ServerChooser.kt", "w") as f:
    f.write(content)
print("Updated ServerChooser.kt")
