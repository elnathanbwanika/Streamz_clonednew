package com.linkstreamz.app

/**
 * One-shot gate for the Home feed's initial scroll position.
 *
 * `LazyListState` is saveable, so [androidx.compose.runtime.saveable.rememberSaveableStateHolder]
 * restores the previous run's scroll offset after a cold launch. Consuming this
 * flag on the first Home composition snaps the feed back to the top exactly once
 * per process. Switching tabs later does not re-trigger it, so the user's place
 * is preserved during the session.
 */
internal object HomeLaunchScrollReset {
    @Volatile
    var consumed: Boolean = false
}
