package com.linkstreamz.app

import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectFileUrlTest {
    private val bucketUrl =
        "https://host.example/bucket/movies/Hope%20(2026)%20%5BVJ%20Ulio%5D.mkv"

    @Test
    fun percentEncodedBucketUrlIsPassedThroughUnchanged() {
        val exact = DirectFileUrls.exact(bucketUrl)

        assertEquals(bucketUrl, exact)
        assertTrue(DirectFileUrls.isSafePassthrough(bucketUrl))
        assertEquals(
            "/bucket/movies/Hope%20(2026)%20%5BVJ%20Ulio%5D.mkv",
            DirectFileUrls.encodedPath(exact),
        )
    }

    @Test
    fun downloadedLocalUriIsNeverHttpValidated() {
        val contentUri = "content://com.android.providers.downloads.documents/document/42"
        val fileUri = "file:///storage/emulated/0/Movies/Hope%20(2026)%20%5BVJ%20Ulio%5D.mkv"

        assertEquals(contentUri, DirectFileUrls.forPlayback(contentUri))
        assertEquals(fileUri, DirectFileUrls.forPlayback(fileUri))
        assertEquals(bucketUrl, DirectFileUrls.forPlayback(bucketUrl))

        // Downloads stay strict; only the playback path is permissive.
        assertTrue(
            runCatching { DirectFileUrls.exact(contentUri) }
                .exceptionOrNull() is IllegalArgumentException,
        )
    }

    @Test
    fun okHttpRequestKeepsEncodedSpacesBracketsAndParentheses() {
        val request = Request.Builder()
            .url(DirectFileUrls.exact(bucketUrl))
            .header("Range", "bytes=0-0")
            .build()

        assertEquals(
            "/bucket/movies/Hope%20(2026)%20%5BVJ%20Ulio%5D.mkv",
            request.url.encodedPath,
        )
        assertEquals("bytes=0-0", request.header("Range"))
        assertFalse(request.url.encodedPath.contains("%2520"))
        assertFalse(request.url.encodedPath.contains("%255B"))
    }

    @Test
    fun longMp4NameAndQueryStringArePreserved() {
        val longName = "A".repeat(180) + "%20%5BFinal%5D.mp4"
        val url = "https://host.example/bucket/movies/$longName?X-Amz-Signature=abc"

        assertEquals(url, DirectFileUrls.exact(url))
        assertEquals(
            "/bucket/movies/$longName",
            DirectFileUrls.encodedPath(url),
        )
        assertEquals("mp4", DownloadSupport.extensionFor(url))
    }

    @Test
    fun directFileIsRecognisedForDownloadAndNamedSafely() {
        assertTrue(DownloadSupport.canDownloadUrl(bucketUrl))
        assertEquals("mkv", DownloadSupport.extensionFor(bucketUrl))

        val fileName = FileNames.singleMovie("Hope (2026) [VJ Ulio]", "mkv")
        assertEquals("Hope (2026) [VJ Ulio].mkv", fileName)
        assertTrue(fileName.length <= 124)
    }

    @Test
    fun parallelRangesCoverWholeFileAndResumeFromSavedSegments() {
        val total = 34_217_472L
        val planned = SegmentPlanner.plan(total, requestedConnections = 6)

        assertTrue(planned.size >= 4)
        assertEquals(0L, planned.first().start)
        assertEquals(total - 1L, planned.last().end)
        assertEquals(total, planned.sumOf { it.length })

        val saved = planned.mapIndexed { index, seg ->
            seg.copy(downloaded = if (index == 0) seg.length / 2 else seg.length)
        }
        val resumed = SegmentPlanner.mergeCompleted(saved, planned)

        assertEquals(SegmentPlanner.totalDownloaded(saved), SegmentPlanner.totalDownloaded(resumed))
        assertEquals(
            "bytes=${planned.first().nextByte}-${planned.first().end}",
            SegmentPlanner.rangeHeader(resumed.first()),
        )
    }
}
