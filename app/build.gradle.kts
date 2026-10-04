import java.util.Properties
import java.io.FileInputStream
import java.io.FileOutputStream

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
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

val isBuilding = gradle.startParameter.taskNames.any { it.contains("assemble") || it.contains("bundle") }
if (isBuilding) {
    var major = versionProps["MAJOR"].toString().toInt()
    var minor = versionProps["MINOR"].toString().toInt()
    var patch = versionProps["PATCH"].toString().toInt()
    var build = versionProps["BUILD"].toString().toInt()

    patch++
    if (patch > 9) {
        patch = 0
        minor++
    }
    // Note: Assuming major doesn't auto-increment on minor > 9 unless requested, but we can do it:
    if (minor > 9) {
        minor = 0
        major++
    }

    build++

    versionProps["MAJOR"] = major.toString()
    versionProps["MINOR"] = minor.toString()
    versionProps["PATCH"] = patch.toString()
    versionProps["BUILD"] = build.toString()

    versionProps.store(FileOutputStream(versionPropsFile), "Auto-incremented per build")
}

val vName = "${versionProps["MAJOR"]}.${versionProps["MINOR"]}.${versionProps["PATCH"]}"
val vCode = versionProps["BUILD"].toString().toInt()

android {
    namespace = "com.linkstreamz.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.linkstreamz.app"
        minSdk = 24
        targetSdk = 34
        versionCode = vCode
        versionName = vName
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    // YT-EXTRACT: NewPipeExtractor (pure Java — no Kotlin metadata conflict)
    // Pinned to commit 94159dc971 from open PR #1545 to fix YouTube n-sig extraction (v0.26.5 is broken)
    implementation("com.github.TeamNewPipe:NewPipeExtractor:94159dc971")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.0.4")
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-text")
    implementation("androidx.compose.ui:ui-tooling-preview")
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

    // youtubedl-android removed: added ~50 MB of Python native binaries to the APK
    // and caused crashes when the YoutubeDL engine was not properly initialised.

    // ── Unit tests ────────────────────────────────────────────────────────────
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
