package com.linkstreamz.app

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay

// ─────────────────────────────────────────────────────────────────────────────
// Home featured carousel
//
// GUI-only: it renders the feeds Home already loaded and never fetches. All
// timing constants live in HomeCarousel (pure, unit-tested).
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The featured carousel.
 *
 * - 0 slides: renders nothing (the caller's `item` disappears entirely).
 * - 1 slide: renders the card alone, with no pager, no dots and no timer.
 * - 2+: pager with dots, auto-sliding while [visible] and the app is foregrounded.
 *
 * Auto-sliding is disabled when system animations are off or when touch
 * exploration is on, and pauses for RESUME_AFTER_TOUCH_MS after a drag.
 */
@Composable
fun HomeCarousel(
    slides: List<HomeCarouselSlide>,
    visible: Boolean,
    onOpenSportsTab: (SportsTab) -> Unit,
    onPlayEvent: (Event) -> Unit,
    onOpenLimited: () -> Unit,
    onWatchMovie: (Movie) -> Unit,
    onDownloadMovie: (Movie) -> Unit,
    onOpenMovieDetails: (Movie) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (slides.isEmpty()) return

    if (slides.size == 1) {
        val only = slides.first()
        HomeCarouselCard(
            slide = only,
            modifier = modifier.padding(horizontal = 16.dp),
            onClick = { only.open(onOpenSportsTab, onOpenLimited, onOpenMovieDetails) },
            onPlay = { only.play(onPlayEvent, onWatchMovie) },
            onDownload = onDownloadMovie,
        )
        return
    }

    val pagerState = rememberPagerState(pageCount = { slides.size })

    val context = LocalContext.current
    val animatorScale = remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(1f)
    }
    val animationsOff = animatorScale == 0f
    val touchExplorationOn = LocalAccessibilityManager.current?.isEnabled == true

    var inForeground by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> inForeground = true
                Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_PAUSE -> inForeground = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        inForeground = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // While paused this holds a future timestamp; Long.MAX_VALUE means "held by
    // the user's finger". The loop below re-reads it every tick, so a touch takes
    // effect immediately instead of after the current delay.
    var pausedUntilMs by remember { mutableLongStateOf(0L) }
    val autoSlide = visible && inForeground && !animationsOff && !touchExplorationOn

    LaunchedEffect(autoSlide, slides.size) {
        if (!autoSlide) return@LaunchedEffect
        while (true) {
            val wait = pausedUntilMs - System.currentTimeMillis()
            if (wait > 0L) {
                delay(wait)
                continue
            }
            delay(HomeCarousel.AUTO_SLIDE_MS)
            if (pausedUntilMs > System.currentTimeMillis()) continue
            val next = (pagerState.currentPage + 1) % slides.size
            pagerState.animateScrollToPage(
                page = next,
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = HomeCarousel.SLIDE_ANIM_MS,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing,
                ),
            )
        }
    }

    val resume = {
        pausedUntilMs = System.currentTimeMillis() + HomeCarousel.RESUME_AFTER_TOUCH_MS
        Unit
    }

    Column(modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 12.dp,
            key = { index -> slides[index].key },
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(slides.size) {
                    // Observe drags only: pausing must never steal the gesture
                    // from the pager's own swipe or from the card's tap.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        pausedUntilMs = Long.MAX_VALUE
                        var dragged = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.pressed) {
                                if (change.positionChange().getDistance() > 0f) dragged = true
                            } else {
                                break
                            }
                        }
                        if (dragged) resume()
                    }
                },
        ) { page ->
            val slide = slides[page]
            HomeCarouselCard(
                slide = slide,
                onClick = { slide.open(onOpenSportsTab, onOpenLimited, onOpenMovieDetails) },
                onPlay = { slide.play(onPlayEvent, onWatchMovie) },
                onDownload = onDownloadMovie,
            )
        }

        if (slides.size > 1) {
            HomeCarouselDots(
                count = slides.size,
                selected = pagerState.currentPage,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
            )
        }
    }
}

/** Small muted dots; the active one is orange. */
@Composable
private fun HomeCarouselDots(count: Int, selected: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.semantics {
            contentDescription = "Slide ${selected + 1} of $count"
        },
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            Box(
                Modifier
                    .size(if (index == selected) 6.dp else 5.dp)
                    .clip(CircleShape)
                    .background(if (index == selected) LsColor.Accent else LsColor.TextDim.copy(alpha = 0.45f)),
            )
        }
    }
}

/** Tap target for the whole card. */
private fun HomeCarouselSlide.open(
    onOpenSportsTab: (SportsTab) -> Unit,
    onOpenLimited: () -> Unit,
    onOpenMovieDetails: (Movie) -> Unit,
) {
    when (this) {
        is HomeCarouselSlide.Sports ->
            onOpenSportsTab(if (isLive) SportsTab.LIVE else SportsTab.UPCOMING)
        is HomeCarouselSlide.Limited -> onOpenLimited()
        is HomeCarouselSlide.NewMovie -> onOpenMovieDetails(movie.toMovie())
    }
}

/** The slide's primary button, if it has one. */
private fun HomeCarouselSlide.play(
    onPlayEvent: (Event) -> Unit,
    onWatchMovie: (Movie) -> Unit,
) {
    when (this) {
        is HomeCarouselSlide.Sports -> onPlayEvent(event)
        is HomeCarouselSlide.Limited -> onWatchMovie(movie.toMovie())
        is HomeCarouselSlide.NewMovie -> onWatchMovie(movie.toMovie())
    }
}
