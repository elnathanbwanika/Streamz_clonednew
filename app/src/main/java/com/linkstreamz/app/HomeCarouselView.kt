package com.linkstreamz.app

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
// Home featured carousel.
//
// GUI only: it renders [slides] and never fetches anything. The slide list is
// built from the already-loaded feeds by HomeCarousel and passed in.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The featured carousel.
 *
 * Auto-slides every [HomeCarousel.AUTO_SLIDE_MS] with a
 * [HomeCarousel.SLIDE_ANIM_MS] ease-in-out slide, looping endlessly. The timer
 * runs only while [visible] and the app is resumed, and is cancelled outright
 * (the effect is keyed on it) whenever the user is touching the carousel, the
 * app is backgrounded, system animations are off, or talkback is on.
 *
 * A single slide renders as a plain card with no pager, dots or timer; an empty
 * list renders nothing at all.
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
        // One slide: no pager, no dots, no auto-slide - just the card.
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

    val count = slides.size
    // An "infinite" pager gives a seamless endless loop: advancing past the last
    // slide simply walks onto the next lap instead of rewinding to the first.
    val pagerState = rememberPagerState(
        initialPage = remember(count) { startPageFor(count) },
        pageCount = { Int.MAX_VALUE },
    )

    val context = LocalContext.current
    // Animations off (developer option / battery saver) => manual swipe only.
    val animationsOff = remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(1f) == 0f
    }
    // Touch exploration (TalkBack) => manual swipe only.
    val talkBackOn = LocalAccessibilityManager.current?.isTouchExplorationEnabled == true

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

    // `holding` is a key of the timer effect, so a finger down cancels the timer
    // immediately rather than leaving a coroutine sleeping in a long delay.
    var holding by remember { mutableStateOf(false) }
    var pausedUntilMs by remember { mutableLongStateOf(0L) }

    val autoSlide = visible && inForeground && !animationsOff && !talkBackOn

    LaunchedEffect(autoSlide, count, holding) {
        if (!autoSlide || holding) return@LaunchedEffect
        while (true) {
            val remainingPause = pausedUntilMs - System.currentTimeMillis()
            if (remainingPause > 0L) {
                // Just released: wait out the pause, then slide straight away.
                delay(remainingPause)
            } else {
                delay(HomeCarousel.AUTO_SLIDE_MS)
            }
            if (holding) continue
            pagerState.animateScrollToPage(
                page = pagerState.currentPage + 1,
                animationSpec = tween(
                    durationMillis = HomeCarousel.SLIDE_ANIM_MS,
                    easing = FastOutSlowInEasing,
                ),
            )
        }
    }

    // ── Keep the visible slide across data refreshes ─────────────────────────
    // `anchorKey` is the slide currently on screen. When the list is rebuilt we
    // move the pager back onto that same slide (in place, so nothing jumps) and
    // only fall back to staying put if the slide no longer exists.
    val anchorKey = remember { mutableStateOf(slides.first().key) }
    val latestSlides by rememberUpdatedState(slides)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            anchorKey.value = latestSlides[page % latestSlides.size].key
        }
    }
    val slideKeys = slides.map { it.key }
    LaunchedEffect(slideKeys) {
        val index = slideKeys.indexOf(anchorKey.value)
        if (index >= 0) {
            val page = pageForIndex(pagerState.currentPage, index, slides.size)
            if (page != pagerState.currentPage) pagerState.scrollToPage(page)
        }
    }

    Column(modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 12.dp,
            // Compose the neighbouring page too, so Coil has the next slide's
            // images loading before the swipe and the slide never flashes.
            beyondViewportPageCount = 1,
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(count) {
                    // Observe the touch only; nothing is consumed, so the pager's
                    // own swipe and the card's tap still work normally.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        holding = true
                        // Wait until every finger is up.
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.none { it.pressed }) break
                        }
                        // Let go: resume auto-sliding RESUME_AFTER_TOUCH_MS later.
                        pausedUntilMs =
                            System.currentTimeMillis() + HomeCarousel.RESUME_AFTER_TOUCH_MS
                        holding = false
                    }
                },
        ) { page ->
            val slide = slides[page % count]
            HomeCarouselCard(
                slide = slide,
                onClick = { slide.open(onOpenSportsTab, onOpenLimited, onOpenMovieDetails) },
                onPlay = { slide.play(onPlayEvent, onWatchMovie) },
                onDownload = onDownloadMovie,
            )
        }

        HomeCarouselDots(
            count = count,
            selected = pagerState.currentPage % count,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
        )
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
                    .background(
                        if (index == selected) LsColor.Accent
                        else LsColor.TextDim.copy(alpha = 0.45f)
                    ),
            )
        }
    }
}

/** A page in the middle of the "infinite" range that maps to slide 0. */
private fun startPageFor(count: Int): Int {
    val middle = Int.MAX_VALUE / 2
    return middle - (middle % count)
}

/** The nearest page at or after the current lap that shows slide [index]. */
private fun pageForIndex(currentPage: Int, index: Int, count: Int): Int =
    currentPage - (currentPage % count) + index

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
