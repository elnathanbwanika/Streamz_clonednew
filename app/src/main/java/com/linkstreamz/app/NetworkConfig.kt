// CHANGED: Replaced placeholder SERVER_URL / REQUEST_HEADERS with the real
// GitHub Pages content feed URL. All demo/example constants removed.
// FEED: This is the single source-of-truth for the remote content endpoint.
package com.linkstreamz.app

// FEED: Base URL for the public content repository on GitHub Pages.
const val CONTENT_BASE_URL = "https://raw.githubusercontent.com/tracerdasu/link-streamz-public/main"

// FEED: Full URL of the single JSON file that drives the entire app.
const val CONTENT_JSON_URL = "$CONTENT_BASE_URL/content.json"

// Polling interval: reload the feed every 60 seconds while the home screen is visible.
const val REFRESH_MS = 60_000L

// Ticker interval for live match clocks on the home screen.
const val TICKER_MS = 10_000L

// Assumed total match duration (used by the live-clock heuristic).
const val MATCH_MINUTES = 100

// Fetch timeout in seconds. If no response arrives within this window the
// loader treats the request as a network error.
const val FETCH_TIMEOUT_SECONDS = 15L