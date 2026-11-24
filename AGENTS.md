# Project notes

## Build
- Native Android app (Kotlin + Compose), module `app/` at repo root (not `android/app/`). No Capacitor/Cordova/RN wrapper.
- AGP 8.5.2, Gradle wrapper 8.9, Kotlin 2.0.21, compileSdk 36 / targetSdk 34 / minSdk 24, applicationId `com.linkstreamz.app`.
- Requires JDK 17+ (AGP 8.x). Release build command: `./gradlew assembleRelease --no-daemon`.
- JDK 25 is TOO NEW (Gradle 8.9/AGP 8.5.2 fail with `What went wrong: 25.0.4.1`). Use 17 or 21. CI pins 17 via setup-java; local Codespace defaults to 25 → set `JAVA_HOME` to a 17/21 JDK for local builds.
- APK lands at `app/build/outputs/apk/release/`.

## Installing the APK
- **Always install the raw `.apk`**, never the Actions artifact ZIP (installing a renamed ZIP gives "package appears to be invalid/corrupt"). CI publishes a direct APK at `https://github.com/elnathanbwanika/Streamz_clonednew/releases/download/latest/LinkStreamz-<version>.apk`.
- If an older build is already installed with a different signing key, uninstall it first — Android rejects an update with a different signature and reports it as a corrupt/invalid package.
- CI verifies every APK with `aapt2 dump badging` (manifest/resource parse), `zipalign -c -P 16` (warning) and `apksigner verify --verbose`. A passing run means the uploaded bytes are a valid signed package; a failure after download means the file was truncated or the ZIP was installed.

## CI (GitHub Actions)
- Workflow: `.github/workflows/build-apk.yml` (builds a **signed release** APK, uploads artifact + failure reports).
- **Versioning:** `app/version.properties` is the single source of truth. CI increments it (`PATCH` rolls 0..9 then `MINOR+1`; `MINOR` rolls 0..9 then `MAJOR+1`; `BUILD` = Android `versionCode`, always +1), passes the values to Gradle as `-PversionMajor/-PversionMinor/-PversionPatch/-PversionCodeOverride`, then commits the file back with `[skip ci]`. A local build never bumps — it reuses the committed values — so dev builds can't consume a version number and collide with CI.
- **Release signing secrets** (repo → Settings → Secrets → Actions): `RELEASE_KEYSTORE_BASE64` (the `.jks`, `base64 -w0`), `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`. The keystore is decoded to `app/release-key.jks` at build time and is gitignored; it must never be committed. Alias is `release` (PKCS12, `release-key.jks` produced by `keytool -genkeypair`).
- `gradle.properties` must NOT contain `org.gradle.java.home` (it pointed to a local sdkman path and breaks the runner). Keep `org.gradle.jvmargs=-Xmx3g` to avoid OOM.
- `android-actions/setup-android@v3` FAILS on the runner (tries to install legacy `tools` package → "Failed to find package 'tools'"). Use the preinstalled SDK: set `ANDROID_HOME=/usr/local/lib/android/sdk` and run `yes | sdkmanager --licenses`.

## Shared controls
- `AppControls.kt` is the single design system: `AppChip`/`AppTab`, `AppButton` (Primary/Secondary/Text), `AppIconButton`, `AppControlShape`. All use one radius constant `AppCornerRadius` (8.dp, from the Season chip). Never add ad-hoc `Button`, `TextButton`, pill/circle chips or outlined buttons in screens — extend the shared components instead. Standard touch targets are 48dp via `minimumInteractiveComponentSize`.
- Dense variants: `AppCompactChip` (26dp visual / 32dp touch, orange when selected, TalkBack state) and `AppSegmentedToggle<T>` (28dp, two options). `InlineSearchRow` is the shared collapsing search toolbar: scope chips or title when collapsed, focused field + "x" when expanded, optional `trailing` slot keeps the Movies filter button visible; Back or focus loss while empty collapses it. Both Channels and Movies use it — don't build another search field.
- `InlineSearchRow` must compose the field directly and animate only its alpha (200ms). Do NOT wrap it in `AnimatedContent`: the delayed composition made `requestFocus()` fire before the field attached, so the keyboard opened while the field instantly collapsed. Its focus-loss handler also waits for a real focus event (`focusSeen`) before collapsing.

## Movies filter
- `MoviesFilter.kt` owns the model, pure helpers (`matchesMoviesFilter`, `filterCatalogue`, `resultCount`, `activeFilterChips`, `removeActiveFilter`, `isVjSectionVisible`, facet builders) and `MoviesFilterStateSaver`. `MoviesScreen.kt` owns the **applied** state via `rememberSaveable`; `MoviesFilterPopover.kt` owns a draft and publishes it only through `onApply`. The bottom sheet (`MoviesFilterSheet`) was deleted — never reintroduce it. Keep all counts/chips/badges derived from these helpers so they cannot drift from the catalogue.

## Home spotlight
- The "Recently downloaded" summary strip on Home was removed. Its slot (right after the carousel) now holds `HomeEventSpotlightCard` from `HomeEventSpotlight.kt`: one event card at a time, rotating every `EventSpotlight.ROTATE_MS` (5s) with a Crossfade. Users can also swipe horizontally (`SWIPE_THRESHOLD_PX` = 40f); a manual swipe restarts the auto-rotate timer. `EventSpotlight.rank()` is the pure ordering helper (live → upcoming → finished, dedup by id, cap 8) — keep ranking logic there, not in the composable. The separate "Sports Events" rail was removed from Home; the Sports tab itself still has its full event list.

## Home channel marquee
- The bottom "Channels" rail was removed from Home. `HomeChannelMarquee.kt` now renders a label-free, endlessly scrolling strip of channel **names only** (no thumbnails) as the first item in the Home feed, directly below the main tabs and above the carousel. Tapping a name plays that channel. All channels are shown (dedup by id only — never cap the list); loop duration scales with count via `ChannelMarqueeSpec.MS_PER_CHANNEL` (1.8s per channel, min `MIN_LOOP_MS` 12s) so scroll speed stays constant. The two-strip + `wrapContentWidth(unbounded = true)` pattern is what makes the loop seamless — don't constrain the scrolling Row to the parent width. The Channels tab still has the full grid with artwork.

## Home initial scroll
- The Home feed must open with the channel marquee visible at the very top. `LazyListState` is saveable and `rememberSaveableStateHolder` restores a stale offset across runs, so `HomeSummary` uses `homeScroll.scrollToItem(0)` gated on `channels.isNotEmpty()` (the marquee item must exist first — resetting before channels load lands on the carousel and hides the marquee). `HomeLaunchScrollReset.kt` is the one-shot process gate. Don't remove the `channels` wait or switch to `rememberSaveable` for `homeScroll`.

## Brand mark
- The L-with-dot logo is defined once: `AppLogo.kt` (`AppLogoMark` composable + `AppLogo.drawGlyph`) for Compose, mirrored by `res/drawable/ic_app_logo.xml`, `ic_launcher_foreground.xml` + `mipmap-anydpi-v26/ic_launcher.xml`/`ic_launcher_round.xml` (adaptive launcher, referenced as `@mipmap/ic_launcher` in the manifest), and `ic_notification.xml` (white small-icon silhouette). All use a 48x48 grid: black rounded-cap L at (16,13)→(16,33)→(32,33), dot at (32,16), orange #FF6600 tile. Keep them in sync — never draw an ad-hoc brand icon again. Used on the welcome screen and Home header.

## Player / PiP / images
- Downloads UI is hierarchical: Movies/Series tabs at root → series card → season card → episode rows. `DownloadGroups.kt` supplies pure helpers (`downloadMovies`, `downloadSeries`, `downloadSeasons`, `downloadEpisodes`); each page renders one flat list so LazyColumn keys never collide. `BackHandler` steps back through the in-screen stack before leaving the screen.
- Related content for local playback: `DownloadedTarget.from(item)` carries the source `DownloadItem`; `DownloadedRelatedContent` in `PlayerScreen.kt` then shows other completed movie downloads, or all downloaded episodes of the same series with season chips. Keep building downloaded targets through the factory so `sourceItem` is never lost.
- PiP aspect ratio must come from Media3 `VideoSize.pixelWidthHeightRatio`, not raw width/height: anamorphic movie files otherwise produce a stretched floating window. `PipAspectRatio.display()` is the single pure helper; `AppImages.loader()` is the single shared Coil loader (retry + prefetch), do not create per-screen loaders.

## Pitfalls
- **"There was a problem parsing the package" on install = the APK is unsigned.** Release builds are signed only when `app/release-key.jks` exists **and** `RELEASE_KEYSTORE_PASSWORD` is set; otherwise Gradle produces an unsigned release APK on purpose rather than failing. If you ever see this on a CI artifact, the keystore secret is missing — the workflow fails early with an explicit error in that case.
- `local.properties` is gitignored (machine-specific `sdk.dir`); the workflow relies on the runner's preinstalled SDK via `ANDROID_HOME=/usr/local/lib/android/sdk`.
- `app/version.properties` is read at configuration time and written back by CI, so it must stay committed. Don't hand-edit it to "reset" a version — `versionCode` must only ever increase or Play/Android will reject the update.
- `release` has `isMinifyEnabled = true` (R8). `app/proguard-rules.pro` keeps OkHttp, Ktor, kotlinx.serialization, Media3 and the `linktunnel`/`go` gomobile classes — a release-only crash that doesn't reproduce in debug usually means a missing keep rule there.
- Stream resolution uses `com.github.TeamNewPipe:NewPipeExtractor:v0.27.1` (JitPack repo in `settings.gradle.kts`). v0.27.0 was the first stable release with the merged n-sig fixes (#1545/#1556); the old pinned PR commit `94159dc971` is obsolete. When YouTube breaks extraction again, bump this tag first — it is the only place the extractor version is declared.
- `NewPipeDownloader` must let NewPipe's headers override downloader defaults. Adding our own User-Agent alongside NewPipe's sends two UA lines and YouTube returns a player response with no streams. Normalise header keys to lowercase in a plain `LinkedHashMap` — `java.util.LinkedHashMap` has NO comparator constructor (`String.CASE_INSENSITIVE_ORDER` fails to compile), and destructuring `(key, value)` in `forEach` over a Java `Map` is ambiguous in Kotlin. Use `for (entry in map.entries)`. OkHttp `Headers` is an `Iterable<Pair>`, not a Map — iterate `for (pair in response.headers)`. `proguard-rules.pro` keeps `org.schabi.newpipe.**` — without it R8 can break reflective model parsing in release builds only.
- Extraction retries in `PlayerScreen` are keyed on `ytExtractAttempt`, not just the video id: retrying the same server keeps the same id, so an id-only key silently skips re-extraction.
- The player must never show extraction/provider branding: the overlay says only "Loading..." (`PlayerVideoContent.kt`) and failures use generic messages in `PlayerScreen.kt`. Keep provider names and raw exception text out of the UI.
- `@Preview` annotations need `androidx.compose.ui:ui-tooling`; it must be `debugImplementation(...)` (was missing, added) or `:app:compileDebugKotlin` fails with unresolved reference.
- `app/libs/linktunnel.aar` (15 MB, gomobile WireGuard) is committed on purpose and un-ignored in `.gitignore`; it is a `files(...)` dependency, so it must stay in the repo. Rebuild: `scripts/build-tunnel-aar.sh` (needs Go + gomobile + NDK).
- `app/google-services.json` is committed; the `com.google.gms.google-services` plugin is applied, so a missing/mismatched file fails the build at `processDebugGoogleServices` (package_name must be `com.linkstreamz.app`).
- **Never show raw exception messages in the UI.** OkHttp errors contain signed bucket URLs/tokens. Use `UserFacingError.message(e)` when persisting failures and `UserFacingError.fromStored(...)` when rendering stored errors. `LogRedactor` is for logs only, not UI.
- No git binary and no `.git` dir in the local sandbox — commits/pushes go through the git tools or the user's phone.
- **Compose's `LocalAccessibilityManager` does not expose `isTouchExplorationEnabled`** on Compose BOM 2024.12.01 (only `isEnabled`) — `:app:compileReleaseKotlin` fails with `Unresolved reference 'isTouchExplorationEnabled'`. To detect TalkBack, get the framework manager from `LocalContext`: `context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager` and read/listen on `isTouchExplorationEnabled` / `TouchExplorationStateChangeListener`.
- **After writing a Kotlin file in pieces, verify line 1 is `package com.linkstreamz.app` and that no `[Earlier content ...]` / `Module surface:` wrapper text leaked into the file.** Both produce `compileReleaseKotlin` failures that look like unrelated unresolved references. Check with `find app/src -name '*.kt' -exec grep -lF -e '[Earlier' -e 'Module surface:' {} \;`.

## UI / design tokens
- Single token layer: `app/src/main/java/com/linkstreamz/app/LinkTokens.kt` — `LsColor`, `LsSpace`, `LsShape`, `LsType` + text helpers (`LsSectionHeading`, `LsBadge`, `LsMeta`, `LsDialogBody`).
- Palette: bg `#000000`, surface `#0D0D0D`, card `#141414`, accent `#FF6600`, accent-dim `#2A1608`, hero base `#170B04`, text `#F5F5F5`, secondary `#9A9A9A`, on-accent `#000000`.
- Radii: chip/pill fully round, button 16, card 22, hero 28, poster 18, dialog/sheet 28. **No outlines or strokes** on cards/buttons/chips — separate by tone only.
- Fonts are bundled in `res/font/` (inter_regular, jetbrains_mono, syncopate_bold/regular). No font downloads needed.
- The older palette (`LinkColors`/`LinkDimens` in `SettingsScreen.kt`, `DisplayFont`/`BodyFont`/`MonoFont` in `Theme.kt`) is still present and used by unrestyled screens — do not delete it, both live side by side.
- `LinkColors.ScreenBg` is `#000000` and `LinkColors.CardBg` is `#0F0F0F`; `LsColor.Card` is `#141414`. Don't mix them inside one component or you get a visible seam.
- Restyling is presentational only: never touch click handlers, view ids, labels, or data classes. TV badge placement/size logic (`TvBadge.kt`, `TvBadgeView.kt`) is explicitly out of scope.

## Picture-in-Picture (player)
- Uses the **platform PiP API only** (`enterPictureInPictureMode`). Never `SYSTEM_ALERT_WINDOW` / "display over other apps" — the old `FloatingOverlayManager` overlay window is what produced the app-drawn curved-arrow/X/pause/zoom chrome and the black bars; it is no longer used by the PiP button (the file is left in place, unreferenced by `onFloatWindow`).
- `PipSpec.kt` is framework-free (ratio clamp + action list) so it unit-tests on the JVM without Robolectric. `PipController.kt` is the Android plumbing. `MainActivity` owns the single session; `PlayerScreen` attaches the *same* `engine.player`, so expand never rebuffers.
- Aspect ratio comes from the real video size via `Player.Listener.onVideoSizeChanged`, clamped to Android's `[1/2.39, 2.39]`; `RESIZE_MODE_FIT` + `setSeamlessResizeEnabled(false)`.
- Actions: movies/series = rewind 10 / play-pause / forward 10; live (event, channel, highlight) = play-pause only. `ACTION_CLOSE` must stay in the receiver's `IntentFilter` or the X button silently does nothing.
- While in PiP **no app UI** is drawn (controller, scrim, loading, badge, seek bubble, error card) — only the video. The system's own expand/close overlay is the only chrome and cannot be restyled.
- **The PiP window is the whole activity shrunk** — anything `PlayerScreen` composes shows up in it. `PlayerScreen` therefore switches to a dedicated video-only branch (`if (isInPip)`) that renders *only* `PlayerVideoContent` on black; the `PlayerBar` header (back/title/download), the rounded frame + orange border + 12dp padding, the radial gradient and `RelatedContent` live in the `else` branch. Never add chrome outside that branch, and never rely on `PlayerVideoContent`'s internal guards alone — that was the original bug (header/frame/list were siblings of the video, not children).
- Entry timing: `onPictureInPictureUiStateChanged` (`isTransitioningToPip`, API 33) flips the state **before** the window shrinks; pre-13 `onUserLeaveHint` enters PiP directly; `onPictureInPictureModeChanged` is the final authority in both directions. Read `isInPip` with `by` from `isInPipState`, not `.value`, or Compose will not recompose.
- `MainActivity.isInPipState` is a Compose `State` mirror; `isInPipNow` reads the platform flag live. `PipLayoutSpec.layoutMode()` is the single decision point and depends on the PiP flags **only**.
- Keep the PiP **transition** flag separate from the mirror: during `isTransitioningToPip`, `isInPictureInPictureMode` is still `false`, so `syncPipState()` alone cannot publish the early switch. `pipTransitioningState` carries it and `isInPipNow` ORs both.
- **Never key a `DisposableEffect`/`LaunchedEffect` on playback state when its `onDispose` tears down PiP.** The regression: `DisposableEffect(pipHost, target, playing)` + `detachPip()` clearing the flag meant tapping **pause in the floating window** restarted the effect, dropped the controller and reverted to the normal page *inside* the window (`onPictureInPictureModeChanged` never fires again to fix it). Keys are now `(pipHost, target)`; playback-dependent PiP chrome goes in its own `LaunchedEffect(pipHost, playing)`. `detachPip()` must never clear the PiP flag.
- **Background-pause must be PiP-aware.** The lifecycle observer paused the player on `ON_STOP` and resumed on `ON_START`; in PiP that fought the window's play/pause button. Both branches now check `!pipHost?.isInPipNow`.
- `onStop` must not `unregister()` the action receiver while `isInPictureInPictureMode`, or the window's buttons go dead. `register()`/`unregister()` are idempotent guards.
- The action receiver uses `PendingIntent.getBroadcast` + `FLAG_IMMUTABLE` and only touches the player, then `refreshActions(trigger)`; it never starts the activity or re-delivers an intent.
- `PipLayoutSpec` in `PipSpec.kt` is the framework-free source of truth for "video only", covered by `PipLayoutSpecTest` (every `PlaybackState` × `LifecycleEvent` must stay `VIDEO_ONLY`) — change the policy there, not only in the composable.
- The source-rect hint must be `getGlobalVisibleRect` (window coords), not `getLocationOnScreen` (screen coords — off by the status-bar inset).

## Performance rules (learned the hard way — don't regress these)
- **Never pass a clock `Long` down into list items.** `nowMs` as a parameter invalidates every card on each tick. Read `ServerClock.now()` inside the leaf composable, or give the composable a defaulted param (`nowMs: Long = ServerClock.now()`) and precompute any *derived* value (e.g. `downloadSupport: Boolean`) at the call site. `HomeScreen`/`MoviesScreen` tickers were removed for exactly this.
- **Main tabs: compose only the active tab, but preserve scroll state.** `HomeScreen` renders just `section` inside `rememberSaveableStateHolder().SaveableStateProvider(section.name)` — a `when` in `SectionContent`. Keeping *hidden* tabs composed (the earlier `alpha(0f)` approach) left their nested lazy lists and long-lived `produceState`/`LaunchedEffect` loops running in a zero-size viewport, which froze a tab when you returned to it. The saveable holder preserves scroll offset/filters across dispose, so a return is instant without stale live state.
- **One infinite animation per grid, not per card.** `ChannelCard` takes `liveAlpha: Float` from a single `rememberInfiniteTransition` in `ChannelsScreen`; its default `1f` is for callers outside the grid.
- No fake loading delays; show `Ready` data immediately.

## Home featured carousel
- The slide list is built by `HomeCarousel.build(movies, events, nowMs)` in `HomeCarousel.kt` — pure, no Compose/Android, covered by `HomeCarouselTest` (round-robin order, 10-slide cap, de-dup, ended/finished excluded, empty kinds hidden, single & empty cases). Change ordering/filter rules there, not in the composable.
- `HomeCarouselView.kt` is the pager; it uses an effectively infinite page range with modulo indexing so "loop endlessly" never rewinds to the first slide. `HomeCarouselCard.kt` holds the four layouts behind one fixed-height frame (`CarouselCardHeight`) — keep one height and radius for all slides or the pager resizes, i.e. the rail jumps.
- **Ended time-limited movies are dead content.** Feed Home/Movies only `movies.visibleCatalogue()`; a downloaded copy stays reachable from Downloads (that screen lists the download store, not the catalogue), so no special-casing is needed anywhere else.
- The auto-slide timer runs only while the Home tab is composed, the app is resumed, system animations are on and touch exploration is off. It is keyed on `holding`, so a touch *cancels* it instead of leaving a coroutine parked in a long `delay`.
- The carousel is the only sports content that belongs on Home (the older Sports rail predates it).
- Cards must not receive a clock `Long` — each slide runs its own `produceState` tick (1 s only under an hour left, else 60 s), which follows the performance rule above.

## In-app messages (server-controlled popups)
- `InAppMessage.kt` is the pure core (model, tolerant `content.json` parser, EAT day helper, eligibility, frequency rules, queue selection) — covered by `InAppMessageTest`. Change ordering/cap/frequency rules there, not in the composable.
- `EventsRepository.parseFeed` calls `InAppMessageStore.ingest(root)`; the store keeps the last good `messages` array in SharedPreferences for offline launches and per-id seen markers. `MainActivity` initialises it in `onCreate`, and `InAppMessageController` exposes one message at a time with a 3-per-session cap.
- Never show over update/permissions/player/PiP: `messagesBlocked` in `MainActivity` feeds `controller.setBlocked`. The controller is `remember`ed, so "always" resets on a new process but not on a foreground return.

## Team badges / match rows
- `TeamRow.kt` is the single source of truth for match rows: fixed 36dp badge slots outside weighted name slots, 60dp status column, 14→11sp autosize, 2-line ellipsis. Badge size must never depend on name length.
- Root cause of shrinking badges: `TeamSide` put `Crest` inside a `Modifier.weight(1f)` Row, so the badge shared the weighted width with the name. Do not reintroduce weights or wrap-content on badges.

## Getting the APK
- Actions → "Build release APK" → latest green run → Artifacts → `app-release-<versionName>` (e.g. `app-release-3.1.6`) → download on phone → install (allow "install unknown apps" for the browser/Files app).
- Artifacts are kept 30 days; red runs still upload `build-reports`.
- Every successful run bumps `versionCode` and commits `app/version.properties` back, so installs always go forward. Uninstalling the old build is only needed if you ever switch signing keys — with a stable key, installs upgrade in place.
