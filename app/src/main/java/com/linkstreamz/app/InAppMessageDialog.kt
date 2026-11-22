package com.linkstreamz.app

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

private const val MOTION_MS = 200

/** Renders whatever message the controller exposes, with enter/exit motion. */
@Composable
fun InAppMessageHost(
    controller: InAppMessageController,
    onAction: (InAppMessageAction) -> Unit,
) {
    val message = controller.current
    var rendered by remember { mutableStateOf<InAppMessage?>(null) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        if (message != null) {
            rendered = message
            visible = true
        } else if (rendered != null) {
            visible = false
            delay(MOTION_MS.toLong())
            rendered = null
        }
    }

    val shown = rendered ?: return
    InAppMessageDialog(
        message = shown,
        visible = visible,
        onDismiss = { controller.dismiss() },
        onAction = { action ->
            controller.dismiss()
            onAction(action)
        },
    )
}

@Composable
private fun InAppMessageDialog(
    message: InAppMessage,
    visible: Boolean,
    onDismiss: () -> Unit,
    onAction: (InAppMessageAction) -> Unit,
) {
    val context = LocalContext.current
    val requiresTap = message.style == InAppMessageStyle.WARN

    // Warn messages need an explicit button tap; everything else can be closed
    // with Back, an outside tap or the Close button.
    Dialog(
        onDismissRequest = { if (!requiresTap) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !requiresTap,
            dismissOnClickOutside = !requiresTap,
            usePlatformDefaultWidth = false,
        ),
    ) {
        // Draw our own 70% scrim; disable the platform dim so they don't stack.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        LaunchedEffect(Unit) { window?.setDimAmount(0f) }

        val reduceMotion = remember {
            runCatching {
                Settings.Global.getFloat(
                    context.contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f,
                ) == 0f
            }.getOrDefault(false)
        }
        val enter: EnterTransition
        val exit: ExitTransition
        if (reduceMotion) {
            enter = EnterTransition.None
            exit = ExitTransition.None
        } else {
            val spec = tween<Float>(MOTION_MS)
            enter = fadeIn(animationSpec = spec) + scaleIn(initialScale = 0.94f, animationSpec = spec)
            exit = fadeOut(animationSpec = spec) + scaleOut(targetScale = 0.94f, animationSpec = spec)
        }

        AnimatedVisibility(visible = visible, enter = enter, exit = exit) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xB3000000))
                    .clickable { if (!requiresTap) onDismiss() },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth()
                        .widthIn(max = 420.dp)
                        .heightIn(max = 620.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(LsColor.Card)
                        .clickable { /* consume: taps inside must not dismiss */ }
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(LsSpace.Item),
                ) {
                    var imageFailed by remember(message.id) { mutableStateOf(false) }
                    val image = message.image
                    if (!image.isNullOrBlank() && !imageFailed) {
                        AsyncImage(
                            model = image,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            onError = { imageFailed = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(LsColor.Surface),
                        )
                    }

                    val tag = when (message.style) {
                        InAppMessageStyle.INFO -> "INFO"
                        InAppMessageStyle.ANNOUNCE -> "ANNOUNCEMENT"
                        InAppMessageStyle.PROMO -> "PROMO"
                        InAppMessageStyle.WARN -> "IMPORTANT"
                    }
                    val tagColor = if (message.style == InAppMessageStyle.WARN) {
                        LsColor.Danger
                    } else {
                        LsColor.Accent
                    }
                    Text(
                        text = tag,
                        color = tagColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )

                    val focusRequester = remember { FocusRequester() }
                    LaunchedEffect(message.id) { runCatching { focusRequester.requestFocus() } }
                    Text(
                        text = message.title,
                        color = LsColor.Text,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .focusRequester(focusRequester)
                            .focusable()
                            .semantics(mergeDescendants = true) {
                                contentDescription = "$tag: ${message.title}"
                            },
                    )

                    Text(
                        text = message.body,
                        color = LsColor.TextDim,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Start,
                    )

                    if (message.buttonLabel != null) {
                        Button(
                            onClick = { onAction(message.action) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = LsSpace.Touch),
                            shape = LsShape.Button,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LsColor.Accent,
                                contentColor = LsColor.OnAccent,
                            ),
                        ) {
                            Text(message.buttonLabel, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        if (!requiresTap) {
                            TextButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = LsSpace.Touch),
                            ) {
                                Text("Close", color = LsColor.TextDim, fontSize = 14.sp)
                            }
                        }
                    } else {
                        Button(
                            onClick = { onAction(InAppMessageAction.None) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = LsSpace.Touch),
                            shape = LsShape.Button,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LsColor.Accent,
                                contentColor = LsColor.OnAccent,
                            ),
                        ) {
                            Text("Got it", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

/** Opens an https link in the browser. Invalid URLs are ignored silently. */
fun openMessageLink(context: android.content.Context, url: String?) {
    if (url == null || !url.startsWith("https://", ignoreCase = true)) return
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
