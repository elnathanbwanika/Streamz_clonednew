package com.linkstreamz.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateTest {
    @Test
    fun `numeric version comparison`() {
        assertTrue((AppUpdatePolicy.compareVersions("1.10.0", "1.9.2") ?: 0) > 0)
        assertTrue((AppUpdatePolicy.compareVersions("1.9.2", "1.10.0") ?: 0) < 0)
        assertEquals(0, AppUpdatePolicy.compareVersions("1.9", "1.9.0"))
        assertEquals(0, AppUpdatePolicy.compareVersions("2.0", "2.0.0.0"))
    }

    @Test
    fun `installed version ignores suffix`() {
        assertEquals(listOf(3L, 0L, 9L), AppUpdatePolicy.parseInstalledVersion("3.0.9-vpn-autoconfig"))
        assertEquals(listOf(1L, 9L, 2L), AppUpdatePolicy.parseInstalledVersion("1.9.2"))
        assertNull(AppUpdatePolicy.parseInstalledVersion("vpn"))
        assertNull(AppUpdatePolicy.parseInstalledVersion(""))
    }

    @Test
    fun `minimum version must be one to four numeric segments`() {
        assertTrue(AppUpdatePolicy.isValidMinVersion("1"))
        assertTrue(AppUpdatePolicy.isValidMinVersion("1.9.2"))
        assertTrue(AppUpdatePolicy.isValidMinVersion("1.2.3.4"))
        assertFalse(AppUpdatePolicy.isValidMinVersion("1.2.3.4.5"))
        assertFalse(AppUpdatePolicy.isValidMinVersion("1.9.2-beta"))
        assertFalse(AppUpdatePolicy.isValidMinVersion(""))
        assertFalse(AppUpdatePolicy.isValidMinVersion(null))
    }

    @Test
    fun `only https links are accepted`() {
        assertTrue(AppUpdatePolicy.isValidHttpsUrl("https://example.com/update.apk"))
        assertFalse(AppUpdatePolicy.isValidHttpsUrl("http://example.com/update.apk"))
        assertFalse(AppUpdatePolicy.isValidHttpsUrl("ftp://example.com/update.apk"))
        assertFalse(AppUpdatePolicy.isValidHttpsUrl("https://"))
        assertFalse(AppUpdatePolicy.isValidHttpsUrl(null))
    }

    @Test
    fun `enforcement requires enabled valid and newer minimum`() {
        val config = AppUpdateConfig(
            enabled = true,
            minVersion = "3.1.0",
            apkUrl = "https://example.com/update.apk",
            message = null,
        )
        assertTrue(AppUpdatePolicy.shouldEnforce(config, "3.0.9"))
        assertFalse(AppUpdatePolicy.shouldEnforce(config, "3.1.0"))
        assertFalse(AppUpdatePolicy.shouldEnforce(config, "3.2.0"))
        assertFalse(AppUpdatePolicy.shouldEnforce(config.copy(enabled = false), "3.0.9"))
        assertFalse(AppUpdatePolicy.shouldEnforce(config.copy(minVersion = "3.1.0.0.0"), "3.0.9"))
        assertFalse(AppUpdatePolicy.shouldEnforce(config.copy(minVersion = "abc"), "3.0.9"))
        assertFalse(AppUpdatePolicy.shouldEnforce(config.copy(apkUrl = "http://example.com/update.apk"), "3.0.9"))
        assertFalse(AppUpdatePolicy.shouldEnforce(config.copy(apkUrl = null), "3.0.9"))
        assertFalse(AppUpdatePolicy.shouldEnforce(null, "3.0.9"))
        assertFalse(AppUpdatePolicy.shouldEnforce(config, null))
    }

    @Test
    fun `download progress speed and eta`() {
        val progress = AppUpdateMetrics.progress(25L, 100L)
        assertEquals(0.25f, progress, 0.0001f)
        assertEquals(0f, AppUpdateMetrics.progress(0L, 0L), 0.0001f)
        assertEquals(1f, AppUpdateMetrics.progress(100L, 100L), 0.0001f)

        assertEquals(500L, AppUpdateMetrics.speed(1000L, 2000L))
        assertEquals(0L, AppUpdateMetrics.speed(1000L, 0L))
        assertEquals(10L, AppUpdateMetrics.etaSeconds(1000L, 100L))
        assertEquals(-1L, AppUpdateMetrics.etaSeconds(1000L, 0L))
        assertEquals(0L, AppUpdateMetrics.etaSeconds(0L, 100L))

        val snapshot = AppUpdateMetrics.snapshot(25L, 100L, 0L, 1000L)
        assertEquals(25L, snapshot.downloadedBytes)
        assertEquals(100L, snapshot.totalBytes)
        assertEquals(25L, snapshot.bytesPerSecond)
        assertEquals(3L, snapshot.etaSeconds)
        assertEquals(0.25f, snapshot.fraction, 0.0001f)
    }
}
