import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

val versionPropsFile = file("version.properties")
val versionProps = Properties()
if (versionPropsFile.exists()) {
    versionProps.load(FileInputStream(versionPropsFile))
} else {
    versionProps["MAJOR"] = "1"
    versionProps["MINOR"] = "0"
    versionProps["PATCH"] = "8"
    versionProps["BUILD"] = "0"
}

// Version source of truth: app/version.properties, committed to git.
//
// CI (GitHub Actions) is the ONLY place that increments it: the workflow bumps
// the file, commits it back, and passes the new values in via -P. That keeps a
// single monotonic counter across every cloud build, which a local bump could
// never do (two clones would race to the same number).
//
// Locally we must NOT bump, or a dev build would silently consume a version
// number and the next CI build would collide with it. So a local build reuses
// the committed values verbatim and only warns.
val ciMajor = (findProperty("versionMajor") as String?)?.toIntOrNull()
val ciMinor = (findProperty("versionMinor") as String?)?.toIntOrNull()
val ciPatch = (findProperty("versionPatch") as String?)?.toIntOrNull()
val ciCode = (findProperty("versionCodeOverride") as String?)?.toIntOrNull()

val isCiVersion = ciMajor != null && ciMinor != null && ciPatch != null && ciCode != null

if (!isCiVersion) {
    logger.lifecycle(
        "LinkStreamz: using committed version ${versionProps["MAJOR"]}.${versionProps["MINOR"]}.${versionProps["PATCH"]} " +
            "(build ${versionProps["BUILD"]}). CI increments this; local builds do not."
    )
}

val vMajor = ciMajor ?: versionProps["MAJOR"].toString().toInt()
val vMinor = ciMinor ?: versionProps["MINOR"].toString().toInt()
val vPatch = ciPatch ?: versionProps["PATCH"].toString().toInt()
val vName = "$vMajor.$vMinor.$vPatch"
val vCode = ciCode ?: versionProps["BUILD"].toString().toInt()

android {
    namespace = "com.linkstreamz.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.linkstreamz.app"
        minSdk = 24
        targetSdk = 34
        versionCode = vCode
        versionName = vName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // Release signing. The keystore is NEVER committed: CI decodes the
            // RELEASE_KEYSTORE_BASE64 secret into app/release-key.jks and
            // passes the passwords as env vars. Locally, drop the .jks next to
            // this file and export the same vars (or use ~/.gradle/gradle.properties).
            //
            // Without a keystore the release build stays unsigned rather than
            // failing, so a plain `assembleRelease` on a fresh clone still works.
            val keystoreFile = file("release-key.jks")
            val storePass = System.getenv("RELEASE_KEYSTORE_PASSWORD")
            val keyAlias = System.getenv("RELEASE_KEY_ALIAS")
            val keyPass = System.getenv("RELEASE_KEY_PASSWORD")

            if (keystoreFile.exists() && !storePass.isNullOrBlank()) {
                signingConfig = signingConfigs.create("release").apply {
                    storeFile = keystoreFile
                    storePassword = storePass
                    this.keyAlias = keyAlias ?: "release"
                    this.keyPassword = keyPass ?: storePass
                }
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    // Stream extraction (pure Java — no Kotlin metadata conflict).
    // v0.27.1 is the first stable release containing the merged n-sig fixes
    // (#1545 and #1556). The old pinned PR commit is obsolete — keep this on a
    // released tag so CI gets a reproducible JitPack build.
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.27.1")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.0.4")
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    debugImplementation(composeBom)
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-text")
    implementation("androidx.compose.ui:ui-tooling-preview")
    // Preview.kt / SettingsScreen.kt use the @Preview annotation, which lives in ui-tooling.
    // Debug-only so the release APK stays lean (release uses R8 minification).
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.5.1")
    implementation("io.ktor:ktor-server-core:2.3.11")
    implementation("io.ktor:ktor-server-cio:2.3.11")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("androidx.media3:media3-datasource:1.5.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-messaging-ktx")
    // Userspace WireGuard (wireguard-go + netstack) built with gomobile.
    // Runs entirely in-process — no system VPN service, no TUN, no key icon.
    // Rebuild: ./scripts/build-tunnel-aar.sh
    implementation(files("libs/linktunnel.aar"))
    


    // youtubedl-android removed: added ~50 MB of Python native binaries to the APK
    // and caused crashes when the YoutubeDL engine was not properly initialised.

    // ── Unit tests ────────────────────────────────────────────────────────────
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")

    // ── Instrumented UI tests ───────────────────────────────────────────────
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
