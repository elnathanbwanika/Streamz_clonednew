// CHANGED: Replaced placeholder SERVER_URL / REQUEST_HEADERS with the real
// GitHub Pages content feed URL. All demo/example constants removed.
// FEED: This is the single source-of-truth for the remote content endpoint.
package com.linkstreamz.app

// ── Public content repo (GitHub Pages) ───────────────────────────────────────
// FEED: Base URL for the public content repository on GitHub Pages.
const val CONTENT_BASE_URL = "https://raw.githubusercontent.com/tracerdasu/link-streamz-public/main"

// FEED: Full URL of the single JSON file that drives the entire app.
const val CONTENT_JSON_URL = "$CONTENT_BASE_URL/content.json"

// ── Private admin repo (GitHub Contents API) ─────────────────────────────────
// The admin console writes VPN config files to this private GitHub repository.
// Access requires a fine-grained GitHub personal access token with:
//   • Contents: Read-only
//   • Scoped to this repository only
//
// ⚠ REPLACE the placeholder values below with your actual repo details.
//   ADMIN_REPO_OWNER  — GitHub user or org that owns the private repo
//   ADMIN_REPO_NAME   — name of the private repo (not the public one)
//   ADMIN_GITHUB_TOKEN — a fine-grained PAT (starts with "github_pat_" or "ghp_")
//
// The token is baked into the APK. Use a read-only, repo-scoped token so a
// compromised APK cannot modify or delete any data.
const val ADMIN_REPO_OWNER  = "elnathanbwanika"          // ← your GitHub username/org
const val ADMIN_REPO_NAME   = "streamz-admin"             // ← REPLACE with your private repo name
const val ADMIN_GITHUB_TOKEN = "REPLACE_WITH_GITHUB_PAT" // ← REPLACE with your fine-grained PAT

// GitHub Contents API base — do not change.
const val GITHUB_API_BASE = "https://api.github.com"

// ── Timing constants ─────────────────────────────────────────────────────────
// Polling interval: reload the feed every 60 seconds while the home screen is visible.
const val REFRESH_MS = 60_000L

// Ticker interval for live match clocks on the home screen.
const val TICKER_MS = 10_000L

// Assumed total match duration (used by the live-clock heuristic).
const val MATCH_MINUTES = 100

// Fetch timeout in seconds. If no response arrives within this window the
// loader treats the request as a network error.
const val FETCH_TIMEOUT_SECONDS = 15L