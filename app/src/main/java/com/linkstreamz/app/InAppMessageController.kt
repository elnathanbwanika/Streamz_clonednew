package com.linkstreamz.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Owns the in-app message queue for one app process.
 *
 * At most one message is exposed at a time. Messages are picked newest first,
 * skipped when already shown this session or filtered by frequency, and capped
 * at three per session. While a higher-priority overlay (update, permissions,
 * full-screen video or PiP) is up, nothing is shown; an interrupted message is
 * returned to the queue rather than being marked seen.
 */
class InAppMessageController(
    private val store: InAppMessageStore = InAppMessageStore,
    private val now: () -> Long = { ServerClock.now() },
    private val shownThisSession: MutableSet<Long> = mutableSetOf(),
) {
    private var gatesOpen = false

    /** The message to render, or null when nothing should be shown. */
    var current by mutableStateOf<InAppMessage?>(null)
        private set

    /** Called once when the app composes, using the cached list if offline. */
    fun onAppStart() = check()

    /** Called on every ON_RESUME and after each successful content refresh. */
    fun onForeground() = check()

    fun onContentChanged() = check()

    /** True when a blocking overlay is up. */
    fun setBlocked(blocked: Boolean) {
        gatesOpen = !blocked
        if (blocked) {
            // Put an interrupted message back in the queue; it was not dismissed.
            current?.let { shownThisSession -= it.id }
            current = null
        } else {
            check()
        }
    }

    private fun check() {
        if (!gatesOpen || current != null) return
        val next = InAppMessageQueue.next(
            messages = store.candidates(),
            seen = store,
            nowMs = now(),
            shownThisSession = shownThisSession,
        ) ?: return
        shownThisSession += next.id
        current = next
    }

    /** Both dismissal and a button tap mark the message seen, then show the next. */
    fun dismiss() {
        val message = current ?: return
        store.markSeen(message, now())
        current = null
        check()
    }
}
