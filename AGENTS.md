# Project notes

## Build
- Native Android app (Kotlin + Compose), module `app/` at repo root (not `android/app/`).
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
- `app/build.gradle.kts` auto-increments `app/version.properties` on every `assemble`/`bundle` run.
- NewPipeExtractor is pinned to a JitPack commit (`com.github.TeamNewPipe:NewPipeExtractor:94159dc971`); JitPack repo is in `settings.gradle.kts`.
- Local sandbox has NO git binary and NO `.git` dir — commits/pushes must go through the git tools or the user's phone.
