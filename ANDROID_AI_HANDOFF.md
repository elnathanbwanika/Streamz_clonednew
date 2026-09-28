# Link Streamz Android — AI Handoff

## 1. What this project is

Link Streamz is a native Android sports-streaming UI written in Kotlin with Jetpack Compose. It is a real Android application, not a WebView wrapper and not a browser app.

The visual direction is intentionally close to the supplied Link Streamz HTML reference:

- black background with a soft orange radial glow
- orange gradient accent buttons and live badges
- rounded dark cards with thin translucent borders
- compact monospace metadata
- sports event cards with live, upcoming, finished, and highlight states
- a 16:9 player stage with playback controls
- grouped dark settings panels

The native Kotlin source is the source of truth. The files under `screenshots/` are visual reference renders used to inspect the intended appearance; they are not screenshots from a completed APK because this workspace does not have an Android SDK/JDK runtime.

## 2. Android project facts

- Gradle project name: `LinkStreamz`
- Android application ID: `com.linkstreamz.app`
- Namespace: `com.linkstreamz.app`
- Minimum Android API: 24
- Compile SDK: 35
- Target SDK: 34
- Version: `1.0` / version code `1`
- Main activity: `com.linkstreamz.app.MainActivity`
- Start destination: `home`
- UI toolkit: Jetpack Compose + Material 3
- Playback: AndroidX Media3 ExoPlayer with HLS support
- Local settings: AndroidX DataStore Preferences
- Network: Retrofit + OkHttp + kotlinx.serialization
- Images: Coil Compose

## 3. Important files

### App entry and navigation

- `app/src/main/java/com/linkstreamz/app/MainActivity.kt`
  - Creates the repositories.
  - Applies `LinkStreamzTheme`.
  - Owns Compose navigation between `home`, `player`, and `settings`.
  - Passes the selected `PlayerTarget` into the player screen.

### Screens

- `HomeScreen.kt`
  - Displays the feed.
  - Provides status filters for all, live, upcoming, finished, and highlights.
  - Opens event or highlight items in the player.
  - Opens Settings from the top-right control.

- `PlayerScreen.kt`
  - Uses Media3 ExoPlayer and `PlayerView`.
  - Supports HLS and progressive media URLs.
  - Supports play/pause, seek forward/back, mute, fullscreen, picture-in-picture, server selection, and retry.
  - Accepts `EventTarget`, `HighlightTarget`, or `CustomTarget`.

- `SettingsScreen.kt`
  - Controls autoplay, muted start, skip length, hide-controls timeout, and keep-screen-awake.
  - Accepts a custom HLS or MP4 URL.
  - Provides refresh and reset actions.

### Supporting code

- `Cards.kt` — event cards, highlight cards, team crests, and related content.
- `EventsRepository.kt` — feed loading, polling, JSON normalization, demo data, and feed states.
- `SettingsRepository.kt` — DataStore persistence for playback settings.
- `MatchClock.kt` — status, elapsed-minute, countdown, and relative-time helpers.
- `Theme.kt` — dark Material theme, colors, gradients, typography, and easing.
- `NetworkConfig.kt` — server URL, request headers, and refresh intervals.
- `Preview.kt` — Android Studio Compose previews for Home, Player, and Settings.

## 4. Running it in Android Studio

1. Extract the ZIP.
2. Open the extracted folder in Android Studio as a Gradle project.
3. Install or select:
   - Android SDK platform 35
   - Android SDK build tools compatible with Android Gradle Plugin 8.5.2
   - JDK 17
4. Allow Android Studio to sync Gradle dependencies.
5. Select an emulator or physical Android device running API 24 or newer.
6. Run the `app` configuration.

For static UI inspection, open `Preview.kt` and use the Compose Preview panel. The available previews are:

- `LinkStreamzHomePreview`
- `LinkStreamzPlayerPreview`
- `LinkStreamzSettingsPreview`

The first build may take time while Gradle downloads the Android, Compose, Media3, Retrofit, and Coil dependencies.

## 5. Connecting a real events server

Edit:

```text
app/src/main/java/com/linkstreamz/app/NetworkConfig.kt
```

Example:

```kotlin
const val SERVER_URL = "https://api.example.com"

val REQUEST_HEADERS: Map<String, String> = mapOf(
    // "Authorization" to "Bearer ...",
)
```

Do not commit real credentials into source control. Use an Android build-time or secure runtime configuration for private credentials.

The app requests:

```text
GET {SERVER_URL}/events
GET {SERVER_URL}/highlights
```

Both responses may be either a top-level array or an object containing one of:

```text
events, highlights, data, items, results
```

The normalizer accepts common field aliases. The relevant accepted aliases include:

| Concept | Accepted fields |
| --- | --- |
| Event ID | `id`, `_id`, `eventId` |
| Sport | `sport`, `category` |
| League | `league`, `competition`, `tournament` |
| Status | `status`, `state` |
| Start time | `start`, `startTime`, `kickoff`, `startsAt`, `date` |
| Home team | `home`, `homeTeam`, `team1` |
| Away team | `away`, `awayTeam`, `team2` |
| Stream list | `servers`, `streams`, `links`, `sources` |
| Single stream | `stream`, `url`, `src`, `link` |
| Stream URL | `url`, `src`, `link`, `stream`, `m3u8` |
| Team logo | `logo`, `logoUrl`, `badge`, `image`, `icon` |

Dates can be epoch seconds, epoch milliseconds, or ISO-8601 strings. Status values are normalized to `live`, `upcoming`, or `finished`.

The app polls every 60 seconds while active. The UI ticker updates match timing every 10 seconds.

Until `SERVER_URL` is changed from the placeholder value, the repository uses its built-in demo feed and demo HLS URLs. Replace those before treating the app as a production streaming client.

## 6. Settings and playback behavior

Settings are stored locally in a DataStore named `linkstream_settings`.

Defaults:

```text
autoplay = true
muted = false
skip = 10 seconds
hide controls = 5 seconds
keep screen awake = true
```

The custom stream field accepts an HLS `.m3u8` or progressive MP4 URL. Custom URLs are passed directly to the player and are not saved by the settings repository.

The Android manifest includes:

- `INTERNET` for feed and stream access
- `WAKE_LOCK` for the keep-screen-awake setting

## 7. Design source of truth

The primary design tokens are in `Theme.kt`:

```text
background: #000000
secondary background: #0D0D0D
card: #141414
raised card: #191919
primary text: #F5F5F5
muted text: #8A8A8A
accent: #FF6600
accent highlight: #FF8533
```

Do not replace the native Compose implementation with HTML or a WebView. If visual changes are needed, update the Compose composables and theme tokens directly.

The current font declarations use platform fallbacks because the original prompt did not include TTF files. For exact font metrics, add Inter, JetBrains Mono, and Syncopate files under `app/src/main/res/font/` and update `DisplayFont` and `MonoFont` in `Theme.kt`.

## 8. AI instructions for future work

When modifying this project:

1. Keep the implementation native Kotlin + Jetpack Compose.
2. Preserve the three existing destinations unless a new screen is explicitly requested.
3. Keep network parsing isolated in `EventsRepository.kt`; do not put API calls inside screen composables.
4. Keep persisted preferences isolated in `SettingsRepository.kt`.
5. Use `PlayerTarget` for navigation into playback instead of passing raw URLs through unrelated UI components.
6. Preserve demo mode so Compose previews remain usable without a live server.
7. Add or update a Compose `@Preview` when changing a visible screen.
8. Keep real credentials out of `NetworkConfig.kt` and source control.
9. Run `./gradlew :app:assembleDebug` after configuring a valid Android SDK/JDK.
10. If a build or runtime error appears, fix the Kotlin/Gradle issue rather than introducing a browser fallback.

## 9. Verification checklist

- Gradle sync succeeds.
- `:app:assembleDebug` succeeds.
- Home opens with demo or server-backed content.
- Each status filter changes the visible feed.
- Selecting an event opens Player.
- Server selection changes the selected stream.
- Playback controls work with a valid HLS/MP4 URL.
- Settings persist after closing and reopening the app.
- Custom stream opens Player.
- Refresh reloads the feed.
- Reset restores the default settings.
- Home, Player, and Settings Compose previews render.

## 10. Known limitations at handoff

- This workspace did not contain an Android SDK or JDK, so native Gradle compilation and APK installation were not run here.
- The source uses platform font fallbacks until the exact TTF files are supplied.
- The placeholder server URL must be replaced for real events and streams.
- Demo HLS URLs are included only to keep the UI and preview data usable; they are not a production content source.