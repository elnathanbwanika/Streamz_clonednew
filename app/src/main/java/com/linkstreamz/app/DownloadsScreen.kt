[Earlier content for app/src/main/java/com/linkstreamz/app/DownloadsScreen.kt (1379 chars, already written to disk). The module surface below may not show every signature you need — re-read with read_file before editing this file again, OR before importing/calling anything from it in another file. Do not recreate it from scratch and do not guess a function's parameters from memory.
Module surface:
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onPlay: (DownloadItem) -> Unit,
) {
    val context = LocalContext.current
    val items by DownloadManager.items.collectAsState()
    var confirmDelete by remember { mutableStateOf<DownloadItem?>(null) }
    val groups = remember(items) { groupDownloads(items) }

    Column(Modifier.fillMaxSize().background(LinkColors.ScreenBg)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(LinkColors.CardBg)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Text("←", color = LinkColors.OrangePrimary, fontSize = 18.sp) }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Downloads", color = LinkColors.PrimaryText, fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(
                    if (items.isEmpty()) "Saved to your device" else "${items.size} item${if (items.size == 1) "" else "s"}",
                    color = LinkColors.MutedText, fontFamily = MonoFont, fontSize = 10.sp,
                )
            }
        }

        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Download, null, tint = LinkColors.MutedText, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("No downloads yet", color = LinkColors.PrimaryText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Downloaded movies and episodes appear here.", color = LinkColors.MutedText, fontSize = 12.sp)
                }
            }
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            groups.forEach { group ->
                item(key = "header:${group.key}") { DownloadSectionHeader(group) }
                items(group.items, key = { "row:${it.id}" }) { item ->
                    DownloadRow(
                        item = item,
                        onOpen = { onPlay(item) },
                        onPause = { DownloadManager.pause(context, item.id) },
                        onResume = { DownloadManager.resume(context, item.id) },
                        onDelete = { confirmDelete = item },
                    )
                }
            }
        }
    }

    confirmDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = LsColor.Card,
            title = { Text("Remove download?", color = LinkColors.PrimaryText) },
            text = {
                Text(
                    if (item.isActive) "The download will be cancelled and its partial file removed."
                    else "The saved file will be deleted from this device.",
                    color = LinkColors.MutedText,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    DownloadManager.remove(context, item)
                    confirmDelete = null
                }) { Text("Remove", color = LinkColors.OrangePrimary) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel", color = LinkColors.MutedText) }
            },
        )
    }
}

@Composable
private fun DownloadSectionHeader(group: DownloadGroup) {
    Column(Modifier.fillMaxWidth().padding(top = if (group.key == "movies") 4.dp else 12.dp)) {
        Text(
            group.title,
            color = LinkColors.PrimaryText,
            fontFamily = DisplayFont,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!group.subtitle.isNullOrBlank()) {
            Text(
                group.subtitle,
                color = LinkColors.MutedText,
                fontFamily = MonoFont,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun DownloadRow(
    item: DownloadItem,
    onOpen: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(LsShape.Card)
            .background(LsColor.Card)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(64.dp).height(84.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Color(0xFF16110E)),
        ) {
            AsyncImage(
                model = item.thumbUrl,
                contentDescription = null,
                imageLoader = AppImages.direct(LocalContext.current),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            TvBadgeOverlay(
                placement = item.tvBadge?.card,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, color = LinkColors.PrimaryText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val subtitle = when {
                item.kind == "episode" ->
                    buildList {
                        if (item.season != null && item.season > 0) add("Season ${item.season}")
                        if (item.episode != null) add("Episode ${item.episode}")
                        if (!item.episodeTitle.isNullOrBlank()) add(item.episodeTitle)
                    }.joinToString(" · ").ifBlank { item.seriesTitle ?: item.title }
                else -> "Movie"
            }
            Text(subtitle, color = LinkColors.MutedText, fontFamily = MonoFont, fontSize = 9.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            if (item.status == DownloadStatus.COMPLETED) {
                Text("${formatBytes(item.downloadedBytes)} · Downloaded", color = LinkColors.OrangePrimary, fontFamily = MonoFont, fontSize = 9.5.sp)
            } else {
                LinearProgressIndicator(
                    progress = { item.progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(99.dp)),
                    color = LinkColors.OrangePrimary,
                    trackColor = LinkColors.OrangePrimary.copy(alpha = 0.14f),
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    statusText(item),
                    color = if (item.status == DownloadStatus.FAILED) Color(0xFFE57373) else LinkColors.MutedText,
                    fontFamily = MonoFont,
                    fontSize = 9.5.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when {
                item.status == DownloadStatus.COMPLETED && item.fileUri != null -> RowAction(Icons.Default.PlayArrow, "Play", onOpen)
                item.isActive -> RowAction(Icons.Default.Pause, "Pause", onPause)
                else -> RowAction(Icons.Default.Refresh, "Resume", onResume)
            }
            RowAction(Icons.Default.Delete, "Remove", onDelete)
        }
    }
}

@Composable
private fun RowAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(32.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(LinkColors.OrangePrimary.copy(alpha = 0.12f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = LinkColors.OrangePrimary, modifier = Modifier.size(16.dp))
    }
}

private fun statusText(item: DownloadItem): String = when (item.status) {
    DownloadStatus.QUEUED -> "Queued · ${formatBytes(item.downloadedBytes)} of ${formatBytes(item.totalBytes)}"
    DownloadStatus.DOWNLOADING -> buildString {
        append("${(item.progress * 100).toInt()}% · ${formatBytes(item.downloadedBytes)} of ${formatBytes(item.totalBytes)}")
        if (item.speedBytesPerSecond > 0L) {
            append(" · ${formatTransferSpeed(item.speedBytesPerSecond)}")
            if (item.etaSeconds >= 0L) append(" · ${formatTransferEta(item.etaSeconds)}")
        }
    }
    DownloadStatus.PAUSED -> "Paused · ${formatBytes(item.downloadedBytes)} of ${formatBytes(item.totalBytes)}"
    DownloadStatus.FAILED -> UserFacingError.fromStored(item.error)
    DownloadStatus.COMPLETED -> "${formatBytes(item.downloadedBytes)} · Downloaded"
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) String.format(java.util.Locale.US, "%.2f GB", mb / 1024) else String.format(java.util.Locale.US, "%.1f MB", mb)
}