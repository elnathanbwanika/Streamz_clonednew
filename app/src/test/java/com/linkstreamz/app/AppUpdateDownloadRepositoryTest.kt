package com.linkstreamz.app

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AppUpdateDownloadRepositoryTest {
    @Test
    fun `state survives screen recreation and keeps updating existing collectors`() = runTest {
        AppUpdateDownloadRepository.clear()
        val first = UpdateProgressSnapshot(25_000_000L, 35_861_299L, 2_000_000L, 5L)

        // First popup instance subscribes while the download is running.
        AppUpdateDownloadRepository.publish(AppUpdateDownloadState.Downloading(first))
        val firstScreen = mutableListOf<AppUpdateDownloadState>()
        val firstJob = launch {
            AppUpdateDownloadRepository.state.take(2).collect { firstScreen.add(it) }
        }
        runCurrent()
        assertEquals(first, (firstScreen.first() as AppUpdateDownloadState.Downloading).progress)

        // Rotation/recreation disposes the first collector and creates a new
        // screen. A StateFlow replays the current progress immediately.
        AppUpdateDownloadRepository.publish(
            AppUpdateDownloadState.Downloading(first.copy(downloadedBytes = 30_000_000L)),
        )
        val secondScreen = mutableListOf<AppUpdateDownloadState>()
        val secondJob = launch {
            AppUpdateDownloadRepository.state.take(1).collect { secondScreen.add(it) }
        }
        runCurrent()
        assertEquals(
            30_000_000L,
            (secondScreen.first() as AppUpdateDownloadState.Downloading).progress.downloadedBytes,
        )

        // Leaving the app does not stop the engine; the original collector
        // resumes with the newer value when the screen is restored.
        AppUpdateDownloadRepository.publish(
            AppUpdateDownloadState.Downloading(first.copy(downloadedBytes = 34_000_000L)),
        )
        runCurrent()
        assertEquals(
            34_000_000L,
            (firstScreen.last() as AppUpdateDownloadState.Downloading).progress.downloadedBytes,
        )

        firstJob.cancel()
        secondJob.cancel()
        AppUpdateDownloadRepository.clear()
    }
}
