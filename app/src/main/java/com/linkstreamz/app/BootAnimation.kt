[Earlier content for app/src/main/java/com/linkstreamz/app/BootAnimation.kt (2137 chars, already written to disk). The module surface below may not show every signature you need — re-read with read_file before editing this file again, OR before importing/calling anything from it in another file. Do not recreate it from scratch and do not guess a function's parameters from memory.
Module surface:
import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/** Timing for the welcome screen, kept together so it can be tuned in one place. */
object BootSpec {
    /** Never let the welcome screen hold the app hostage if content is slow. */
    const val MAX_VISIBLE_MS = 2_600L
    /** Minimum time on screen so it does not flash on a warm launch. */
    const val MIN_VISIBLE_MS = 700L
    const val EXIT_MS = 260
}

private fun reduceMotion(context: Context): Boolean =
    runCatching {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }.getOrDefault(false)

/**
 * Full-screen welcome animation shown while the first content load is in
 * flight. Purely decorative: it never gates navigation and is dismissed by
 * the host after a hard timeout. All motion is transform/opacity only.
 */
@Composable
fun LinkEcosystemWelcome() {
    val context = LocalContext.current
    val reduced = remember(context) { reduceMotion(context) }

    val enter by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(if (reduced) 0 else 320, easing = FastOutSlowInEasing),
        label = "welcome-enter",
    )

    val infinite = rememberInfiniteTransition(label = "welcome-orbit")
    val orbit by infinite.animateFloat(
        initialValue = 0f,
        targetValue = if (reduced) 0f else 360f,
        animationSpec = if (reduced) {
            // A long, static repeat avoids a zero-duration busy loop.
            infiniteRepeatable(tween(10_000), RepeatMode.Restart)
        } else {
            infiniteRepeatable(tween(2_800, easing = FastOutSlowInEasing), RepeatMode.Restart)
        },
        label = "welcome-orbit-angle",
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.92f,
        targetValue = if (reduced) 1f else 1.08f,
        animationSpec = if (reduced) {
            infiniteRepeatable(tween(10_000), RepeatMode.Restart)
        } else {
            infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse)
        },
        label = "welcome-pulse",
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(LsColor.Bg)
            .alpha(enter),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(
                Modifier
                    .size(112.dp)
                    .scale(pulse)
                    .drawBehind {
                        val radius = size.minDimension / 2f
                        val center = Offset(radius, radius)
                        drawCircle(
                            brush = Brush.sweepGradient(
                                0f to LsColor.Accent,
                                0.5f to LsColor.AccentDim,
                                1f to LsColor.Accent,
                            ),
                            radius = radius,
                            center = center,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                        )
                        val angle = Math.toRadians(orbit.toDouble())
                        repeat(3) { i ->
                            val a = angle + i * (Math.PI * 2 / 3)
                            val d = radius * 0.72f
                            drawCircle(
                                color = LsColor.Accent,
                                radius = 4.dp.toPx(),
                                center = Offset(
                                    center.x + (cos(a) * d).toFloat(),
                                    center.y + (sin(a) * d).toFloat(),
                                ),
                            )
                        }
                    },
            )
            Text(
                "LINK STREAMZ",
                color = LsColor.Text,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                letterSpacing = 2.sp,
                textAlign = TextAlign.Center,
            )
            Text(
                "Link Ecosystem",
                color = LsColor.Accent,
                fontSize = 12.sp,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}