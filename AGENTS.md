# Project notes

## Build
- Native Android app (Kotlin + Compose), module `app/` at repo root (not `android/app/`). No Capacitor/Cordova/RN wrapper.
- AGP 8.5.2, Gradle wrapper 8.9, Kotlin 2.0.21, compileSdk 36 / targetSdk 34 / minSdk 24, applicationId `com.linkstreamz.app`.
- Requires JDK 17+ (AGP 8.x). Build command: `./gradlew assembleDebug --no-daemon`.
- JDK 25 is TOO NEW (Gradle 8.9/AGP 8.5.2 fail with `What went wrong: 25.0.4.1`). Use 17 or 21. CI pins 17 via setup-java; local Codespace defaults to 25 → set `JAVA_HOME` to a 17/21 JDK for local builds.
- APK lands at `app/build/outputs/apk/debug/`.

## CI (GitHub Actions)
- Workflow: `.github/workflows/build-apk.yml` (builds debug APK, uploads artifact + failure reports).
- `gradle.properties` must NOT contain `org.gradle.java.home` (it pointed to a local sdkman path and breaks the runner). Keep `org.gradle.jvmargs=-Xmx3g` to avoid OOM.
- `android-actions/setup-android@v3` FAILS on the runner (tries to install legacy `tools` package → "Failed to find package 'tools'"). Use the preinstalled SDK: set `ANDROID_HOME=/usr/local/lib/android/sdk` and run `yes | sdkmanager --licenses`.

## Pitfalls
- **"There was a problem parsing the package" on install = the APK is not debug-signed.** `buildTypes` only declares `release` (no `debug` block, no `signingConfig`), and the release type has no signing config either. A build that is `minifyEnabled`/not-debuggable ships unsigned and Android refuses it at parse time. The APK that installs must come from `./gradlew assembleDebug` (or `assembleRelease` once a signing config is added) — never from a `release`-flavoured task that is not signed.
- `local.properties` is gitignored (machine-specific `sdk.dir`); the workflow relies on the runner's preinstalled SDK via `ANDROID_HOME=/usr/local/lib/android/sdk`.
- `app/build.gradle.kts` auto-increments `app/version.properties` on every `assemble`/`bundle` run; it also reads it at configuration time, so the file must be committed (it is).
- NewPipeExtractor is pinned to a JitPack commit (`com.github.TeamNewPipe:NewPipeExtractor:94159dc971`); JitPack repo is in `settings.gradle.kts`. First CI run may be slow or 403 if JitPack has not built that commit yet — re-run, then check `https://jitpack.io/com/github/TeamNewPipe/NewPipeExtractor/94159dc971/`.
- `@Preview` annotations need `androidx.compose.ui:ui-tooling`; it must be `debugImplementation(...)` (was missing, added) or `:app:compileDebugKotlin` fails with unresolved reference.
- `app/libs/linktunnel.aar` (15 MB, gomobile WireGuard) is committed on purpose and un-ignored in `.gitignore`; it is a `files(...)` dependency, so it must stay in the repo. Rebuild: `scripts/build-tunnel-aar.sh` (needs Go + gomobile + NDK).
- `app/google-services.json` is committed; the `com.google.gms.google-services` plugin is applied, so a missing/mismatched file fails the build at `processDebugGoogleServices` (package_name must be `com.linkstreamz.app`).
- No git binary and no `.git` dir in the local sandbox — commits/pushes go through the git tools or the user's phone.

## UI / design tokens
- Single token layer: `app/src/main/java/com/linkstreamz/app/LinkTokens.kt` — `LsColor`, `LsSpace`, `LsShape`, `LsType` + text helpers (`LsSectionHeading`, `LsBadge`, `LsMeta`, `LsDialogBody`).
- Palette: bg `#000000`, surface `#0D0D0D`, card `#141414`, accent `#FF6600`, accent-dim `#2A1608`, hero base `#170B04`, text `#F5F5F5`, secondary `#9A9A9A`, on-accent `#000000`.
- Radii: chip/pill fully round, button 16, card 22, hero 28, poster 18, dialog/sheet 28. **No outlines or strokes** on cards/buttons/chips — separate by tone only.
- Fonts are bundled in `res/font/` (inter_regular, jetbrains_mono, syncopate_bold/regular). No font downloads needed.
- The older palette (`LinkColors`/`LinkDimens` in `SettingsScreen.kt`, `DisplayFont`/`BodyFont`/`MonoFont` in `Theme.kt`) is still present and used by unrestyled screens — do not delete it, both live side by side.
- `LinkColors.ScreenBg` is `#000000` and `LinkColors.CardBg` is `#0F0F0F`; `LsColor.Card` is `#141414`. Don't mix them inside one component or you get a visible seam.
- Restyling is presentational only: never touch click handlers, view ids, labels, or data classes. TV badge placement/size logic (`TvBadge.kt`, `TvBadgeView.kt`) is explicitly out of scope.

## Performance rules (learned the hard way — don't regress these)
- **Never pass a clock `Long` down into list items.** `nowMs` as a parameter invalidates every card on each tick. Read `ServerClock.now()` inside the leaf composable, or give the composable a defaulted param (`nowMs: Long = ServerClock.now()`) and precompute any *derived* value (e.g. `downloadSupport: Boolean`) at the call site. `HomeScreen`/`MoviesScreen` tickers were removed for exactly this.
- **Main tabs: compose only the active tab, but preserve scroll state.** `HomeScreen` renders just `section` inside `rememberSaveableStateHolder().SaveableStateProvider(section.name)` — a `when` in `SectionContent`. Keeping *hidden* tabs composed (the earlier `alpha(0f)` approach) left their nested lazy lists and long-lived `produceState`/`LaunchedEffect` loops running in a zero-size viewport, which froze a tab when you returned to it. The saveable holder preserves scroll offset/filters across dispose, so a return is instant without stale live state.
- **One infinite animation per grid, not per card.** `ChannelCard` takes `liveAlpha: Float` from a single `rememberInfiniteTransition` in `ChannelsScreen`; its default `1f` is for callers outside the grid.
- No fake loading delays; show `Ready` data immediately.

## Getting the APK
- Actions → "Build debug APK" → latest green run → Artifacts → `app-debug-apk` → download on phone → install (allow "install unknown apps" for the browser/Files app).
- Artifacts are kept 14 days; red runs still upload `build-reports`.
