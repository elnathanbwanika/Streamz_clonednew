package com.linkstreamz.app

/**
 * Maps a Media3 playback error to a short, themed, URL-free message.
 *
 * The player overlay is already styled by [PlayerVideoContent] and already has a
 * Retry button; this object only decides WHAT that overlay says. Raw exception
 * messages are never used because they contain signed bucket URLs.
 */
object PlaybackErrorMessage {
    private const val ERROR_CODE_IO_UNSPECIFIED = 1000
    private const val ERROR_CODE_IO_NETWORK_CONNECTION_FAILED = 1001
    private const val ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT = 1002
    private const val ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE = 1003
    private const val ERROR_CODE_IO_BAD_HTTP_STATUS = 1004
    private const val ERROR_CODE_IO_FILE_NOT_FOUND = 1005
    private const val ERROR_CODE_IO_NO_PERMISSION = 1006
    private const val ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE = 1008

    private const val ERROR_CODE_PARSING_CONTAINER_MALFORMED = 2001
    private const val ERROR_CODE_PARSING_MANIFEST_MALFORMED = 2002
    private const val ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED = 2003
    private const val ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED = 2004

    private const val ERROR_CODE_DECODER_INIT_FAILED = 4001
    private const val ERROR_CODE_DECODER_QUERY_FAILED = 4002
    private const val ERROR_CODE_DECODING_FAILED = 4003
    private const val ERROR_CODE_DECODING_FORMAT_UNSUPPORTED = 4004

    /** Codec/container problems are device-wide; retrying the same file won't help. */
    fun isDeviceFormatError(errorCode: Int): Boolean = errorCode in setOf(
        ERROR_CODE_DECODER_INIT_FAILED,
        ERROR_CODE_DECODER_QUERY_FAILED,
        ERROR_CODE_DECODING_FAILED,
        ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
    )

    fun forCode(errorCode: Int, httpCode: Int = 0): String = when {
        isDeviceFormatError(errorCode) ->
            "This video's format isn't supported on this device. Try another server or update the app."

        errorCode in setOf(
            ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        ) -> "This video file is damaged. Try another server or retry."

        errorCode == ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE ->
            "This video doesn't support seeking from that position. Try again."

        httpCode == 403 ->
            "Stream not available in your location. Try either connecting VPN or using proxy in settings"

        httpCode == 410 -> "Link expired (410). Please reload the app."
        httpCode == 404 -> "This video has been removed. Try another server."
        httpCode in 500..599 -> "Server error ($httpCode). Try again later."

        errorCode in setOf(
            ERROR_CODE_IO_UNSPECIFIED,
            ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            ERROR_CODE_IO_BAD_HTTP_STATUS,
        ) -> "Network issue. Check your connection and try again."

        errorCode == ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE ->
            "The server returned an unexpected file type. Try another server."

        errorCode == ERROR_CODE_IO_FILE_NOT_FOUND || errorCode == ERROR_CODE_IO_NO_PERMISSION ->
            "This video couldn't be opened. Try another server."

        httpCode != 0 -> "This video couldn't be played (HTTP $httpCode). Try again."

        else -> "This video couldn't be played. Try again or switch server."
    }
}
