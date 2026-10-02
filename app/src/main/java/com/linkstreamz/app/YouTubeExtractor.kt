package com.linkstreamz.app

// YT-EXTRACT: On-device YouTube stream extraction using NewPipeExtractor v0.26.5 (pure Java).
// No server involvement. URLs are IP-bound and extracted fresh each session.
// Cache: 30-min LRU, max 20 entries, never persisted to disk.
//
// API notes (v0.26.5):
//   - getThumbnails() returns List<Image>, not a String — we grab the first URL.
//   - Exceptions: ReCaptchaException (not ReCaptchaRequiredException), GeographicRestrictionException.
//   - VideoStream.isVideoOnly is a field (also accessible via isVideoOnly()).
//   - MediaFormat has .mimeType field.
//   - AudioStream.getAverageBitrate() (not averageBitrate).

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream

sealed class YtResult {
    data class Muxed(
        val url: String,
        val title: String,
        val durationSeconds: Long,
        val thumbnail: String?
    ) : YtResult()

    data class Split(
        val videoUrl: String,
        val audioUrl: String,
        val title: String,
        val durationSeconds: Long,
        val thumbnail: String?
    ) : YtResult()

    data class Failure(val reason: YtError, val message: String) : YtResult()
}

enum class YtError {
    FATAL, RECAPTCHA, RATE_LIMIT, AGE_RESTRICTED,
    GEO_RESTRICTED, LIVE_STREAM, NO_STREAMS, UNKNOWN
}

data class CachedResult(val result: YtResult, val expiresAt: Long)

object YouTubeExtractor {

    private val cache = object : LinkedHashMap<String, CachedResult>(20, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, CachedResult>) = size > 20
    }

    suspend fun extract(videoId: String): YtResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()

        // YT-EXTRACT: Check in-memory cache before hitting network
        val cached = synchronized(cache) { cache[videoId] }
        if (cached != null && cached.expiresAt > now) {
            return@withContext cached.result
        }

        val url = "https://www.youtube.com/watch?v=$videoId"
        val result: YtResult = try {
            val extractor = ServiceList.YouTube.getStreamExtractor(url)
            extractor.fetchPage()

            // YT-EXTRACT: Reject live streams early
            val streamType = runCatching { extractor.streamType }.getOrNull()
            if (streamType == StreamType.LIVE_STREAM || streamType == StreamType.AUDIO_LIVE_STREAM) {
                return@withContext YtResult.Failure(YtError.LIVE_STREAM, "Live streams are not supported")
            }

            val title = runCatching { extractor.name }.getOrElse { "YouTube Video" }
            val duration = runCatching { extractor.length }.getOrElse { 0L }
            // v0.26.5: getThumbnails() returns List<Image>; grab largest URL
            val thumbnail = runCatching {
                extractor.thumbnails.maxByOrNull { it.height }?.url
            }.getOrNull()

            // YT-EXTRACT: Prefer muxed <= 720p (video + audio in one URL)
            val muxedStreams: List<VideoStream> = runCatching { extractor.videoStreams }.getOrElse { emptyList() }
            val bestMuxed: VideoStream? = muxedStreams
                .filter { !it.isVideoOnly && resolutionInt(it.resolution) <= 720 && it.content.isNotBlank() }
                .maxByOrNull { resolutionInt(it.resolution) }

            if (bestMuxed != null) {
                cacheResult(videoId, YtResult.Muxed(bestMuxed.content, title, duration, thumbnail))
            } else {
                // YT-EXTRACT: Fall back to split stream (separate video + audio URLs)
                val videoOnlyStreams: List<VideoStream> = runCatching { extractor.videoOnlyStreams }.getOrElse { emptyList() }
                val audioStreams: List<AudioStream> = runCatching { extractor.audioStreams }.getOrElse { emptyList() }

                // Prefer MPEG_4 (mp4) video <= 1080p, then anything
                val videoStream: VideoStream? = videoOnlyStreams
                    .filter { resolutionInt(it.resolution) <= 1080 && it.content.isNotBlank() }
                    .sortedByDescending { resolutionInt(it.resolution) }
                    .let { candidates ->
                        candidates.find { it.format?.mimeType?.contains("mp4") == true }
                            ?: candidates.firstOrNull()
                    }

                // Prefer M4A audio, then highest bitrate
                val audioStream: AudioStream? = audioStreams
                    .filter { it.content.isNotBlank() }
                    .let { candidates ->
                        candidates.find { it.format?.mimeType?.contains("m4a") == true }
                            ?: candidates.maxByOrNull { it.averageBitrate }
                    }

                if (videoStream != null && audioStream != null) {
                    cacheResult(videoId, YtResult.Split(videoStream.content, audioStream.content, title, duration, thumbnail))
                } else {
                    YtResult.Failure(YtError.NO_STREAMS, "No playable streams found for $videoId")
                }
            }
        } catch (e: ReCaptchaException) {
            YtResult.Failure(YtError.RECAPTCHA, e.message ?: "ReCaptcha required")
        } catch (e: AgeRestrictedContentException) {
            YtResult.Failure(YtError.AGE_RESTRICTED, e.message ?: "Age restricted")
        } catch (e: GeographicRestrictionException) {
            YtResult.Failure(YtError.GEO_RESTRICTED, e.message ?: "Geo restricted")
        } catch (e: ContentNotAvailableException) {
            val msg = e.message ?: ""
            if (msg.contains("live", ignoreCase = true))
                YtResult.Failure(YtError.LIVE_STREAM, msg)
            else
                YtResult.Failure(YtError.FATAL, msg)
        } catch (e: ExtractionException) {
            YtResult.Failure(YtError.FATAL, e.message ?: "Extraction failed")
        } catch (e: ParsingException) {
            YtResult.Failure(YtError.UNKNOWN, e.message ?: "Parse error")
        } catch (e: java.io.IOException) {
            // Network error — do NOT cache so the user can retry
            return@withContext YtResult.Failure(YtError.UNKNOWN, e.message ?: "Network error")
        } catch (e: Exception) {
            YtResult.Failure(YtError.UNKNOWN, e.message ?: "Unknown error")
        }

        result
    }

    private fun cacheResult(videoId: String, result: YtResult): YtResult {
        val expiry = System.currentTimeMillis() + 30 * 60 * 1000L
        synchronized(cache) { cache[videoId] = CachedResult(result, expiry) }
        return result
    }

    private fun resolutionInt(res: String?): Int =
        res?.replace("p", "")?.replace("P", "")?.toIntOrNull() ?: 0
}
