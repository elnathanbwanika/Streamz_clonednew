# Link Streamz Android app

This directory is a native Kotlin + Jetpack Compose port of the supplied Link Streamz HTML app. It does not use a WebView.

For a full Android Studio and AI handoff guide, read [`ANDROID_AI_HANDOFF.md`](ANDROID_AI_HANDOFF.md) before making changes.

## Project layout

- `PORT_CHECKLIST.md` — the CSS-to-Compose fidelity checklist required before implementation.
- `app/src/main/java/com/linkstreamz/app/MainActivity.kt` — Compose navigation for Home, Player, and Settings.
- `HomeScreen.kt` / `PlayerScreen.kt` / `SettingsScreen.kt` — the three app destinations.
- `Cards.kt` — event, highlight, crest, and related-content composables.
- `MatchClock.kt` — pure status/minute/countdown helpers.
- `EventsRepository.kt` — Retrofit/OkHttp/kotlinx-serialization feed loading, normalization, polling, and demo fallback.
- `SettingsRepository.kt` — DataStore preferences for playback settings.
- `Theme.kt` — CSS token equivalents, gradients, typography, and easing.
- `Preview.kt` — native Android Studio Compose previews for Home, Player, and Settings.
- `NetworkConfig.kt` — edit `SERVER_URL` and `REQUEST_HEADERS` for the live server.

## Live server contract

Edit `app/src/main/java/com/linkstreamz/app/NetworkConfig.kt`:

```kotlin
const val SERVER_URL = "https://your-server.example.com"
val REQUEST_HEADERS: Map<String, String> = emptyMap()
```

The app calls `/events` and `/highlights` on launch and every 60 seconds while active. Until `SERVER_URL` is changed, the app uses the HTML prompt's demo feed and public demo HLS stream.

## Build

Open the repository root in Android Studio with an installed Android SDK and run the `app` configuration. The project targets SDK 34 and supports Android API 24+.

This environment does not include the Android SDK/JDK, so Gradle compilation could not be run in-session. The project files are structured for Android Gradle Plugin 8.5.2, Kotlin 2.0.21, and the Compose compiler plugin.

## Font fidelity

The source theme exposes `DisplayFont` and `MonoFont` in one place. The current source uses Compose platform fallbacks with explicit `// APPROXIMATION` markers because the uploaded prompt did not include the required TTF binaries. Add Inter, JetBrains Mono, and Syncopate TTF files under `app/src/main/res/font/`, then change those two declarations in `Theme.kt` to `FontFamily(Font(R.font.<file>))` to make the type metrics exact.