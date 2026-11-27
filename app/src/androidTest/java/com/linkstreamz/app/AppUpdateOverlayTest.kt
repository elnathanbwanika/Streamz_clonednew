package com.linkstreamz.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppUpdateOverlayTest {
    @get:Rule
    val compose: ComposeTestRule = createComposeRule()

    private val requirement = AppUpdateRequirement(
        config = AppUpdateConfig(
            enabled = true,
            minVersion = "2.0.0",
            apkUrl = "https://example.com/update.apk",
            message = null,
        ),
        installedVersion = "1.0.0",
        minVersion = "2.0.0",
    )

    @Test
    fun popup text and bar change when state changes() {
        var state: AppUpdateDownloadState by mutableStateOf(AppUpdateDownloadState.Connecting())

        compose.setContent {
            AppUpdateOverlayContent(requirement, state, onCloseApp = {})
        }

        compose.onNodeWithText("Connecting...").assertIsDisplayed()

        state = AppUpdateDownloadState.Downloading(
            UpdateProgressSnapshot(
                downloadedBytes = 10L * 1024 * 1024,
                totalBytes = 34L * 1024 * 1024,
                bytesPerSecond = 2L * 1024 * 1024,
                etaSeconds = 12L,
            ),
        )
        compose.onNodeWithText("Downloading update 29%").assertIsDisplayed()
        compose.onNodeWithText("10.0 MB / 34.0 MB").assertIsDisplayed()
        compose.onNodeWithText("2.0 MB/s · 12 s left").assertIsDisplayed()
        assertEquals(10f / 34f, compose.progressOf(UPDATE_PROGRESS_BAR_TAG), 0.01f)

        state = AppUpdateDownloadState.Downloading(
            UpdateProgressSnapshot(
                downloadedBytes = 20L * 1024 * 1024,
                totalBytes = 34L * 1024 * 1024,
                bytesPerSecond = 2L * 1024 * 1024,
                etaSeconds = 7L,
            ),
        )
        compose.onNodeWithText("Downloading update 59%").assertIsDisplayed()
        compose.onNodeWithText("20.0 MB / 34.0 MB").assertIsDisplayed()
        assertEquals(20f / 34f, compose.progressOf(UPDATE_PROGRESS_BAR_TAG), 0.01f)
    }

    private fun ComposeTestRule.progressOf(tag: String): Float {
        mainClock.advanceTimeBy(300L)
        return onNodeWithTag(tag).fetchSemanticsNode()
            .config[SemanticsProperties.ProgressBarRangeInfo].current
    }
}
