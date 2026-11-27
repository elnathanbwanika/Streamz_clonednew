package com.linkstreamz.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackErrorMessageTest {
    @Test
    fun unsupportedCodecGetsClearDeviceFriendlyMessage() {
        val message = PlaybackErrorMessage.forCode(4004)

        assertTrue(message.contains("format isn't supported"))
        assertFalse(message.contains("http"))
        assertFalse(message.contains("://"))
    }

    @Test
    fun deviceFormatErrorsAreDetectedForImmediateFeedback() {
        assertTrue(PlaybackErrorMessage.isDeviceFormatError(4004))
        assertTrue(PlaybackErrorMessage.isDeviceFormatError(2003))
        assertFalse(PlaybackErrorMessage.isDeviceFormatError(1001))
        assertFalse(PlaybackErrorMessage.isDeviceFormatError(0))
    }

    @Test
    fun networkAndHttpErrorsStayActionable() {
        assertTrue(PlaybackErrorMessage.forCode(1001).contains("Network issue"))
        assertTrue(PlaybackErrorMessage.forCode(1002).contains("Network issue"))
        assertTrue(PlaybackErrorMessage.forCode(0, 403).contains("VPN"))
        assertTrue(PlaybackErrorMessage.forCode(0, 404).contains("removed"))
        assertTrue(PlaybackErrorMessage.forCode(0, 503).contains("503"))
    }

    @Test
    fun messagesNeverContainUrls() {
        val samples = listOf(
            PlaybackErrorMessage.forCode(4001),
            PlaybackErrorMessage.forCode(2001),
            PlaybackErrorMessage.forCode(1000),
            PlaybackErrorMessage.forCode(0, 500),
            PlaybackErrorMessage.forCode(0),
        )

        samples.forEach { message ->
            assertFalse(message.contains("://"))
            assertFalse(message.contains("bucket"))
            assertFalse(message.contains("%20"))
        }
    }
}
