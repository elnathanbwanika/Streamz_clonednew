package com.linkstreamz.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun AppUpdateOverlay(
    requirement: AppUpdateRequirement,
    resumeTick: Int,
    onCloseApp: () -> Unit,
) {
    val context = LocalContext.current
    val downloadState by AppUpdateDownloader.state.collectAsStateWithLifecycle()
    var awaitingPermission by remember { mutableStateOf(false) }
    var settingsOpened by remember { mutableStateOf(false) }
    var installerLaunched by remember { mutableStateOf(false) }

    BackHandler { onCloseApp() }

    LaunchedEffect(resumeTick, downloadState) {
        val ready = downloadState as? AppUpdateDownloadState.Ready ?: run {
            if (downloadState is AppUpdateDownloadState.Idle || downloadState is AppUpdateDownloadState.Failed) {
                awaitingPermission = false
                settingsOpened = false
                installerLaunched = false
            }
            return@LaunchedEffect
        }
        if (AppUpdateInstaller.canInstall(context)) {
            if (!installerLaunched) {
                installerLaunched = true
                if (!AppUpdateInstaller.launchInstaller(context, ready.file)) {
                    installerLaunched = false
                    AppUpdateDownloader.markFailed("Could not open the installer. Please try again.")
                }
            } else {
                installerLaunched = false
                AppUpdateDownloader.installationNotCompleted()
            }
        } else {
            if (!awaitingPermission) {
                awaitingPermission = true
                settingsOpened = true
                AppUpdateInstaller.openInstallPermission(context)
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .appBackground()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Image(
                    painter = painterResource(R.mipmap.ic_launcher),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)),
                )
                Text(
                    "Update required",
                    color = LinkColors.PrimaryText,
                    fontFamily = DisplayFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "This version of Link Streamz is no longer supported. Please update to keep using the app.",
                    color = LinkColors.MutedText,
                    fontFamily = BodyFont,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    textAlign = TextAlign.Center,
                )

                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(LinkColors.CardBg)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    VersionFact("Your version", requirement.installedVersion)
                    VersionFact("Minimum required", requirement.minVersion)
                }

                requirement.message?.let { message ->
                    Text(
                        message,
                        color = LinkColors.PrimaryText,
                        fontFamily = BodyFont,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        textAlign = TextAlign.Center,
                    )
                }

                when (val state = downloadState) {
                    AppUpdateDownloadState.Idle -> {
                        UpdateButtons(
                            primary = "Update now",
                            onPrimary = { AppUpdateDownloader.start(context, requirement) },
                            onCloseApp = onCloseApp,
                        )
                    }

                    is AppUpdateDownloadState.Downloading -> {
                        DownloadProgressContent(state.progress)
                        Spacer(Modifier.height(0.dp))
                        AppButton(
                            "Cancel",
                            { AppUpdateDownloader.cancel() },
                            modifier = Modifier.fillMaxWidth(),
                            variant = AppButtonVariant.Secondary,
                            large = true,
                        )
                    }

                    AppUpdateDownloadState.Verifying -> {
                        Text(
                            "Verifying update...",
                            color = LinkColors.PrimaryText,
                            fontFamily = MonoFont,
                            fontSize = 12.sp,
                        )
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(99.dp)),
                            color = LinkColors.OrangePrimary,
                            trackColor = LinkColors.OrangePrimary.copy(alpha = 0.14f),
                        )
                    }

                    is AppUpdateDownloadState.Ready -> {
                        Text(
                            if (awaitingPermission) {
                                "Android needs your permission to install the update."
                            } else {
                                "Opening the installer..."
                            },
                            color = LinkColors.PrimaryText,
                            fontFamily = BodyFont,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                        )
                        UpdateButtons(
                            primary = if (awaitingPermission) "Open settings" else "Install update",
                            onPrimary = {
                                if (AppUpdateInstaller.canInstall(context)) {
                                    if (!AppUpdateInstaller.launchInstaller(context, state.file)) {
                                        AppUpdateDownloader.markFailed("Could not open the installer. Please try again.")
                                    }
                                } else {
                                    AppUpdateInstaller.openInstallPermission(context)
                                }
                            },
                            onCloseApp = onCloseApp,
                        )
                    }

                    is AppUpdateDownloadState.Failed -> {
                        Text(
                            state.message,
                            color = Color(0xFFFF8A80),
                            fontFamily = BodyFont,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            textAlign = TextAlign.Center,
                        )
                        UpdateButtons(
                            primary = "Retry",
                            onPrimary = { AppUpdateDownloader.start(context, requirement) },
                            onCloseApp = onCloseApp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionFact(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = LinkColors.MutedText, fontFamily = BodyFont, fontSize = 13.sp)
        Text(value, color = LinkColors.PrimaryText, fontFamily = MonoFont, fontSize = 12.sp)
    }
}

@Composable
private fun DownloadProgressContent(progress: AppUpdateProgress) {
    val percent = (progress.fraction * 100f).roundToInt().coerceIn(0, 100)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Downloading update", color = LinkColors.PrimaryText, fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text("$percent%", color = LinkColors.OrangePrimary, fontFamily = MonoFont, fontSize = 12.sp)
        }
        LinearProgressIndicator(
            progress = { progress.fraction },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(99.dp)),
            color = LinkColors.OrangePrimary,
            trackColor = LinkColors.OrangePrimary.copy(alpha = 0.14f),
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                if (progress.totalBytes > 0L) {
                    "${formatUpdateBytes(progress.downloadedBytes)} / ${formatUpdateBytes(progress.totalBytes)}"
                } else {
                    formatUpdateBytes(progress.downloadedBytes)
                },
                color = LinkColors.MutedText,
                fontFamily = MonoFont,
                fontSize = 10.sp,
            )
            Text(
                "${formatUpdateSpeed(progress.bytesPerSecond)} · ${formatUpdateEta(progress.etaSeconds)}",
                color = LinkColors.MutedText,
                fontFamily = MonoFont,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun UpdateButtons(primary: String, onPrimary: () -> Unit, onCloseApp: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppButton(
            primary,
            onPrimary,
            modifier = Modifier.fillMaxWidth(),
            large = true,
        )
        AppButton(
            "Close app",
            onCloseApp,
            modifier = Modifier.fillMaxWidth(),
            variant = AppButtonVariant.Text,
            large = true,
        )
    }
}

private fun formatUpdateBytes(bytes: Long): String = when {
    bytes <= 0L -> "0 MB"
    bytes >= 1_073_741_824L -> String.format(Locale.US, "%.1f GB", bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
    else -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
}

private fun formatUpdateSpeed(bytesPerSecond: Long): String =
    if (bytesPerSecond <= 0L) "--/s" else "${formatUpdateBytes(bytesPerSecond)}/s"

private fun formatUpdateEta(seconds: Long): String = when {
    seconds < 0L -> "Estimating time"
    seconds == 0L -> "Almost done"
    seconds < 60L -> "${seconds}s left"
    else -> "${seconds / 60L}m ${seconds % 60L}s left"
}
