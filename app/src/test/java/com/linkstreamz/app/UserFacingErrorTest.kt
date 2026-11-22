[Earlier content for app/src/test/java/com/linkstreamz/app/UserFacingErrorTest.kt (1420 chars, already written to disk). The module surface below may not show every signature you need — re-read with read_file before editing this file again, OR before importing/calling anything from it in another file. Do not recreate it from scratch and do not guess a function's parameters from memory.
Module surface:
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException

class UserFacingErrorTest {
    private val network = "Network issue. Check your connection and try again."

    @Test fun unknownHostShowsOnlyNetworkAdvice() {
        val raw = "java.net.UnknownHostException: Unable to resolve host \"storage.example.com\": No address associated with hostname https://bucket.example.com/secret/path?sig=abc"
        val shown = UserFacingError.message(UnknownHostException(raw))
        assertEquals(network, shown)
        assertFalse(shown.contains("bucket", true))
        assertFalse(shown.contains("example.com", true))
        assertFalse(shown.contains("sig", true))
    }

    @Test fun storedLegacyRawErrorIsSanitized() {
        val raw = "failed to connect to /https://cdn.example.com/hls/movie/abc123.m3u8?token=SECRET"
        val shown = UserFacingError.fromStored(raw)
        assertEquals(network, shown)
        assertFalse(shown.contains("http", true))
        assertFalse(shown.contains("SECRET", true))
    }

    @Test fun genericExceptionNeverLeaksMessage() {
        val shown = UserFacingError.message(IllegalStateException("https://bucket/path/file.mp4"))
        assertEquals("Something went wrong. Please try again.", shown)
    }

    @Test fun noSpaceIsActionable() {
        assertEquals(
            "Not enough storage space. Free up space and try again.",
            UserFacingError.message(IOException("write failed: ENOSPC (no space left on device)")),
        )
    }

    @Test fun nullAndBlankAreSafe() {
        assertEquals("Something went wrong. Please try again.", UserFacingError.message(null))
        assertEquals("Download failed. Please try again.", UserFacingError.fromStored(null))
        assertEquals("Download failed. Please try again.", UserFacingError.fromStored("  "))
    }

    @Test fun noUrlOrHostSurvivesAnyInput() {
        val raw = "https://cdn.example.com/a/b?token=SECRET&sig=XYZ java.net.SocketTimeoutException: timeout"
        val shown = UserFacingError.fromStored(raw)
        assertTrue(shown.length < 80)
        assertFalse(shown.contains("cdn", true))
        assertFalse(shown.contains("SECRET", true))
    }
}