# ── OkHttp / Okio ────────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# ── Ktor (CIO embedded server) ────────────────────────────────────────────────
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { *; }
-dontwarn io.ktor.**

# ── Kotlinx Serialization ─────────────────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.linkstreamz.app.**$$serializer { *; }
-keepclassmembers class com.linkstreamz.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.linkstreamz.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── Kotlinx Coroutines ────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# ── Media3 / ExoPlayer ────────────────────────────────────────────────────────
# Media3 ships with its own consumer ProGuard rules via AAR; no manual rules needed.
-dontwarn androidx.media3.**

# ── gomobile userspace WireGuard (linktunnel.aar) ─────────────────────────────
-keep class linktunnel.** { *; }
-keep class go.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
-dontwarn linktunnel.**
-dontwarn go.**

# ── NewPipeExtractor ──────────────────────────────────────────────────────────
# Its JSON/protobuf models are populated reflectively. R8 renaming or dropping
# them makes the release APK report "no streams" while a debug build works.
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class com.google.gson.** { *; }
-dontwarn org.schabi.newpipe.extractor.**
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn org.slf4j.impl.**
-dontwarn org.mozilla.javascript.**
