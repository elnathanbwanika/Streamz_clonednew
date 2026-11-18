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
- `local.properties` is gitignored (machine-specific `sdk.dir`); the workflow relies on the runner's preinstalled SDK via `ANDROID_HOME=/usr/local/lib/android/sdk`.
- `app/build.gradle.kts` auto-increments `app/version.properties` on every `assemble`/`bundle` run; it also reads it at configuration time, so the file must be committed (it is).
- NewPipeExtractor is pinned to a JitPack commit (`com.github.TeamNewPipe:NewPipeExtractor:94159dc971`); JitPack repo is in `settings.gradle.kts`. First CI run may be slow or 403 if JitPack has not built that commit yet — re-run, then check `https://jitpack.io/com/github/TeamNewPipe/NewPipeExtractor/94159dc971/`.
- `@Preview` annotations need `androidx.compose.ui:ui-tooling`; it must be `debugImplementation(...)` (was missing, added) or `:app:compileDebugKotlin` fails with unresolved reference.
- `app/libs/linktunnel.aar` (15 MB, gomobile WireGuard) is committed on purpose and un-ignored in `.gitignore`; it is a `files(...)` dependency, so it must stay in the repo. Rebuild: `scripts/build-tunnel-aar.sh` (needs Go + gomobile + NDK).
- `app/google-services.json` is committed; the `com.google.gms.google-services` plugin is applied, so a missing/mismatched file fails the build at `processDebugGoogleServices` (package_name must be `com.linkstreamz.app`).
- No git binary and no `.git` dir in the local sandbox — commits/pushes go through the git tools or the user's phone.

## Getting the APK
- Actions → "Build debug APK" → latest green run → Artifacts → `app-debug-apk` → download on phone → install (allow "install unknown apps" for the browser/Files app).
- Artifacts are kept 14 days; red runs still upload `build-reports`.
