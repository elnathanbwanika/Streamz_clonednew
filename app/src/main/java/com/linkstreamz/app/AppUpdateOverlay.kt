package com.linkstreamz.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

internal const val UPDATE_PROGRESS_BAR_TAG = "update_progress_bar"

@Composable
fun AppUpdateOverlay(
    requirement: AppUpdateRequirement,
    resumeTick: Int,
    onCloseApp: () -> Unit,
) {
    val context = LocalContext.current
    val downloadState by AppUpdateDownloadRepository.state.collectAsStateWithLifecycle()

    LaunchedEffect(downloadState, resumeTick) {
        val percent = when (val state = downloadState) {
            is AppUpdateDownloadState.Downloading -> state.progress.percent
            is AppUpdateDownloadState.Completed -> 100
            else -> -1
        }
        AppLogger.d("UpdateProgress", "update ui progress: $percent%")
        AppUpdateCompletionController.ensureHandled(context, downloadState)
    }

    AppUpdateOverlayContent(requirement, downloadState, onCloseApp)
}

/** Pure UI body, separated so a Compose test can drive it with injected state. */
@Composable
internal fun AppUpdateOverlayContent(
    requirement: AppUpdateRequirement,
    downloadState: AppUpdateDownloadState,
    onCloseApp: () -> Unit,
) {
    val context = LocalContext.current

    BackHandler { onCloseApp() }

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

                safeAdminMessage(requirement.message)?.let { message ->
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

                    is AppUpdateDownloadState.Connecting -> {
                        ConnectingContent(state.message ?: "Connecting...")
                        CancelButton()
                    }

                    is AppUpdateDownloadState.Downloading -> {
                        if (state.progress.downloadedBytes <= 0L) {
                            ConnectingContent("Connecting...")
                        } else {
                            DownloadProgressContent(state.progress)
                        }
                        CancelButton()
                    }

                    is AppUpdateDownloadState.Completed -> {
                        CompletedContent(
                            canInstall = AppUpdateInstaller.canInstall(context),
                            onInstall = {
                                if (!AppUpdateCompletionController.launchFromUi(context, state.file)) {
                                    AppUpdateDownloader.markFailed(
                                        "Could not open the installer. Please try again.",
                                    )
                                }
                            },
                            onSettings = { AppUpdateInstaller.openInstallPermission(context) },
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
private fun ConnectingContent(message: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            message,
            color = LinkColors.PrimaryText,
            fontFamily = BodyFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
        )
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(99.dp)).testTag(UPDATE_PROGRESS_BAR_TAG),
            color = LinkColors.OrangePrimary,
            trackColor = LinkColors.OrangePrimary.copy(alpha = 0.14f),
        )
    }
}

@Composable
internal fun DownloadProgressContent(progress: UpdateProgressSnapshot) {
    val fraction = progress.fraction
    val animatedFraction by animateFloatAsState(
        targetValue = fraction ?: 0f,
        animationSpec = tween(durationMillis = 250, easing = LinearEasing),
        label = "updateProgress",
    )
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (fraction == null) "Downloading update" else "Downloading update ${progress.percent}%",
                color = LinkColors.PrimaryText,
                fontFamily = BodyFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
            if (fraction != null) {
                Text(
                    "${progress.percent}%",
                    color = LinkColors.OrangePrimary,
                    fontFamily = MonoFont,
                    fontSize = 12.sp,
                )
            }
        }
        if (fraction == null) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(99.dp)).testTag(UPDATE_PROGRESS_BAR_TAG),
                color = LinkColors.OrangePrimary,
                trackColor = LinkColors.OrangePrimary.copy(alpha = 0.14f),
            )
        } else {
            LinearProgressIndicator(
                progress = { animatedFraction.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(99.dp)).testTag(UPDATE_PROGRESS_BAR_TAG),
                color = LinkColors.OrangePrimary,
                trackColor = LinkColors.OrangePrimary.copy(alpha = 0.14f),
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                AppUpdateProgressText.size(progress),
                color = LinkColors.MutedText,
                fontFamily = MonoFont,
                fontSize = 10.sp,
            )
            Text(
                AppUpdateProgressText.detail(progress),
                color = LinkColors.MutedText,
                fontFamily = MonoFont,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun CompletedContent(
    canInstall: Boolean,
    onInstall: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (canInstall) "Download complete. Preparing the installer..." else "Android needs your permission to install the update.",
            color = LinkColors.PrimaryText,
            fontFamily = BodyFont,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
        LinearProgressIndicator(
            progress = { 1f },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(99.dp)).testTag(UPDATE_PROGRESS_BAR_TAG),
            color = LinkColors.OrangePrimary,
            trackColor = LinkColors.OrangePrimary.copy(alpha = 0.14f),
        )
        AppButton(
            if (canInstall) "Install update" else "Open settings",
            if (canInstall) onInstall else onSettings,
            modifier = Modifier.fillMaxWidth(),
            large = true,
        )
    }
}

@Composable
private fun CancelButton() {
    AppButton(
        "Cancel",
        { AppUpdateDownloadRepository.requestCancel() },
        modifier = Modifier.fillMaxWidth(),
        variant = AppButtonVariant.Secondary,
        large = true,
    )
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

/** Admin messages are display text only; never surface a download URL. */
private fun safeAdminMessage(raw: String?): String? =
    raw?.trim()?.takeIf { it.isNotEmpty() }
        ?.replace(Regex("""https?://\S+"""), "")
        ?.trim()?.takeIf { it.isNotEmpty() }
