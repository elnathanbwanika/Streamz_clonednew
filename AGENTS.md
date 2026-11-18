# Project notes

## Build
- Native Android app (Kotlin + Compose), module `app/` at repo root (not `android/app/`).
- AGP 8.5.2, Gradle wrapper 8.9, Kotlin 2.0.21, compileSdk 36 / targetSdk 34 / minSdk 24, applicationId `com.linkstreamz.app`.
- Requires JDK 17+ (AGP 8.x). Build command: `./gradlew assembleDebug --no-daemon`.
- APK lands at `app/build/outputs/apk/debug/`.

## CI (GitHub Actions)
- Workflow: `.github/workflows/build-apk.yml` (builds debug APK, uploads artifact + failure reports).
- `gradle.properties` must NOT contain `org.gradle.java.home` (it pointed to a local sdkman path and breaks the runner). Keep `org.gradle.jvmargs=-Xmx3g` to avoid OOM.

## Pitfalls
- `local.properties` is gitignored (machine-specific `sdk.dir`); the workflow relies on `ANDROID_HOME` from `android-actions/setup-android@v3`.
- `app/build.gradle.kts` auto-increments `app/version.properties` on every `assemble`/`bundle` run.
- NewPipeExtractor is pinned to a JitPack commit (`com.github.TeamNewPipe:NewPipeExtractor:94159dc971`); JitPack repo is in `settings.gradle.kts`.
- Local sandbox has NO git binary and NO `.git` dir — commits/pushes must go through the git tools or the user's phone.
