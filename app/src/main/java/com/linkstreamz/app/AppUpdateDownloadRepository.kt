package com.linkstreamz.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** The single process-wide state for the mandatory app update download. */
sealed interface AppUpdateDownloadState {
    data object Idle : AppUpdateDownloadState
    data class Connecting(
        val totalBytes: Long? = null,
        val message: String? = null,
    ) : AppUpdateDownloadState
    data class Downloading(val progress: UpdateProgressSnapshot) : AppUpdateDownloadState
    data class Completed(val file: File, val progress: UpdateProgressSnapshot) : AppUpdateDownloadState
    data class Failed(val message: String) : AppUpdateDownloadState
}

/**
 * Singleton state holder. The download engine is the only writer; the popup,
 * the foreground notification and the install hand-off only observe it.
 * Because it is process-scoped and backed by a replaying StateFlow, a screen
 * recreation or a background/foreground round trip immediately sees the
 * current progress without re-subscribing to the engine.
 */
object AppUpdateDownloadRepository {
    private val _state = MutableStateFlow<AppUpdateDownloadState>(AppUpdateDownloadState.Idle)
    val state: StateFlow<AppUpdateDownloadState> = _state.asStateFlow()

    @Volatile private var cancelHandler: (() -> Unit)? = null

    fun setCancelHandler(handler: () -> Unit) {
        cancelHandler = handler
    }

    fun publish(next: AppUpdateDownloadState) {
        _state.value = next
        log(next)
    }

    fun clear() {
        publish(AppUpdateDownloadState.Idle)
    }

    fun requestCancel() {
        cancelHandler?.invoke() ?: clear()
    }

    fun current(): AppUpdateDownloadState = _state.value

    private fun log(state: AppUpdateDownloadState) {
        when (state) {
            AppUpdateDownloadState.Idle ->
                AppLogger.d("UpdateProgress", "update state: idle")
            is AppUpdateDownloadState.Connecting ->
                AppLogger.d(
                    "UpdateProgress",
                    "update state: connecting total=${state.totalBytes ?: -1L}",
                )
            is AppUpdateDownloadState.Downloading ->
                AppLogger.d(
                    "UpdateProgress",
                    "update state: downloading ${state.progress.downloadedBytes}/" +
                        "${state.progress.totalBytes ?: -1L} speed=${state.progress.bytesPerSecond}",
                )
            is AppUpdateDownloadState.Completed ->
                AppLogger.d("UpdateProgress", "update state: completed 100%")
            is AppUpdateDownloadState.Failed ->
                AppLogger.d("UpdateProgress", "update state: failed ${state.message}")
        }
    }
}
