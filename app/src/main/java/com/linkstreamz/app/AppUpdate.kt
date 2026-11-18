package com.linkstreamz.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.net.URI

data class AppUpdateConfig(
    val enabled: Boolean,
    val minVersion: String?,
    val apkUrl: String?,
    val message: String?,
)

data class AppUpdateRequirement(
    val config: AppUpdateConfig,
    val installedVersion: String,
    val minVersion: String,
) {
    val apkUrl: String get() = config.apkUrl?.trim().orEmpty()
    val message: String? get() = config.message?.trim()?.takeIf { it.isNotEmpty() }
}

object AppUpdateParser {
    fun parse(element: JsonElement?): AppUpdateConfig? {
        val obj = element as? JsonObject ?: return null
        val enabled = (obj["enabled"] as? JsonPrimitive)?.booleanOrNull ?: return null
        return AppUpdateConfig(
            enabled = enabled,
            minVersion = obj.stringOrNull("minVersion"),
            apkUrl = obj.stringOrNull("apkUrl"),
            message = obj.stringOrNull("message"),
        )
    }

    private fun JsonObject.stringOrNull(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
}

object AppUpdatePolicy {
    private val MIN_VERSION = Regex("""^\d+(?:\.\d+){0,3}$""")
    private val LEADING_VERSION = Regex("""^(\d+(?:\.\d+){0,3})""")

    fun isValidMinVersion(raw: String?): Boolean {
        val value = raw?.trim() ?: return false
        if (!MIN_VERSION.matches(value)) return false
        return parseNumeric(value) != null
    }

    fun parseInstalledVersion(raw: String?): List<Long>? {
        val value = raw?.trim() ?: return null
        val numeric = LEADING_VERSION.find(value)?.groupValues?.getOrNull(1) ?: return null
        return parseNumeric(numeric)
    }

    fun compareVersions(left: String?, right: String?): Int? {
        val a = parseInstalledVersion(left) ?: return null
        val b = parseInstalledVersion(right) ?: return null
        val count = maxOf(a.size, b.size)
        for (i in 0 until count) {
            val av = a.getOrElse(i) { 0L }
            val bv = b.getOrElse(i) { 0L }
            if (av != bv) return av.compareTo(bv)
        }
        return 0
    }

    fun isValidHttpsUrl(raw: String?): Boolean {
        val value = raw?.trim() ?: return false
        if (value.isEmpty()) return false
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
    }

    fun shouldEnforce(config: AppUpdateConfig?, installedVersion: String?): Boolean {
        if (config == null || !config.enabled) return false
        if (!isValidMinVersion(config.minVersion)) return false
        if (!isValidHttpsUrl(config.apkUrl)) return false
        val comparison = compareVersions(installedVersion, config.minVersion) ?: return false
        return comparison < 0
    }

    private fun parseNumeric(value: String): List<Long>? =
        value.split('.').map { it.toLongOrNull() ?: return null }.takeIf { it.size in 1..4 }
}

object AppUpdateStore {
    private const val PREFS = "app_update"
    private const val KEY_SAVED = "saved"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_MIN_VERSION = "minVersion"
    private const val KEY_APK_URL = "apkUrl"
    private const val KEY_MESSAGE = "message"

    private val lock = Any()
    private val _config = MutableStateFlow<AppUpdateConfig?>(null)
    private val _required = MutableStateFlow<AppUpdateRequirement?>(null)

    val required: StateFlow<AppUpdateRequirement?> = _required.asStateFlow()

    @Volatile private var appContext: Context? = null
    @Volatile private var installedVersion: String = ""

    fun init(context: Context, versionName: String) {
        synchronized(lock) {
            installedVersion = versionName
            if (appContext == null) {
                val app = context.applicationContext
                appContext = app
                val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                if (prefs.getBoolean(KEY_SAVED, false)) {
                    _config.value = AppUpdateConfig(
                        enabled = prefs.getBoolean(KEY_ENABLED, false),
                        minVersion = prefs.getString(KEY_MIN_VERSION, null),
                        apkUrl = prefs.getString(KEY_APK_URL, null),
                        message = prefs.getString(KEY_MESSAGE, null),
                    )
                }
            }
            recomputeLocked()
        }
    }

    fun ingest(element: JsonElement?) {
        val parsed = AppUpdateParser.parse(element) ?: return
        synchronized(lock) {
            _config.value = parsed
            appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.apply {
                putBoolean(KEY_SAVED, true)
                putBoolean(KEY_ENABLED, parsed.enabled)
                putString(KEY_MIN_VERSION, parsed.minVersion)
                putString(KEY_APK_URL, parsed.apkUrl)
                putString(KEY_MESSAGE, parsed.message)
                apply()
            }
            recomputeLocked()
        }
    }

    fun refreshRequirement() {
        synchronized(lock) { recomputeLocked() }
    }

    private fun recomputeLocked() {
        val config = _config.value
        val installed = installedVersion
        _required.value = if (AppUpdatePolicy.shouldEnforce(config, installed) && config != null) {
            AppUpdateRequirement(
                config = config,
                installedVersion = installed,
                minVersion = config.minVersion.orEmpty().trim(),
            )
        } else {
            null
        }
    }
}
