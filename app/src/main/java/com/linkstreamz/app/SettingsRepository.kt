package com.linkstreamz.app

// ─────────────────────────────────────────────────────────────────────────────
// SettingsRepository.kt
//
// DataStore (Preferences) persistence layer for all settings.
// ─────────────────────────────────────────────────────────────────────────────

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// One DataStore instance per app process, scoped to the application context.
private val Context.settingsDataStore by preferencesDataStore(name = "linkstream_settings")

class SettingsRepository(private val context: Context) {

    // ── Preference keys ───────────────────────────────────────────────────────

    private object Keys {
        val autoplay            = booleanPreferencesKey("autoplay")
        val startMuted          = booleanPreferencesKey("startMuted")
        val skipLength          = intPreferencesKey("skipLength")
        val hideControlsSeconds = intPreferencesKey("hideControlsSeconds")
        val keepScreenOn        = booleanPreferencesKey("keepScreenOn")
        val streamDelaySeconds  = intPreferencesKey("streamDelaySeconds")
        val customUrl           = androidx.datastore.preferences.core.stringPreferencesKey("customUrl")
        val customAudioUrl      = androidx.datastore.preferences.core.stringPreferencesKey("customAudioUrl")
        val customProxyUrl      = androidx.datastore.preferences.core.stringPreferencesKey("customProxyUrl")
        val customReferer       = androidx.datastore.preferences.core.stringPreferencesKey("customReferer")
        val customOrigin        = androidx.datastore.preferences.core.stringPreferencesKey("customOrigin")
        val customHeaders       = androidx.datastore.preferences.core.stringPreferencesKey("customHeaders")
        // Global VPN / SOCKS5 proxy
        val vpnHost             = androidx.datastore.preferences.core.stringPreferencesKey("vpnHost")
        val vpnPort             = androidx.datastore.preferences.core.stringPreferencesKey("vpnPort")
        val vpnUsername         = androidx.datastore.preferences.core.stringPreferencesKey("vpnUsername")
        val vpnPassword         = androidx.datastore.preferences.core.stringPreferencesKey("vpnPassword")
        val vpnUseHttp          = booleanPreferencesKey("vpnUseHttp")
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /** Emits a new [SettingsUiState] whenever any preference changes. */
    val settings: Flow<SettingsUiState> = context.settingsDataStore.data.map { prefs ->
        SettingsUiState(
            autoplay            = prefs[Keys.autoplay]            ?: true,
            startMuted          = prefs[Keys.startMuted]          ?: false,
            skipLength          = prefs[Keys.skipLength]          ?: 5,
            hideControlsSeconds = prefs[Keys.hideControlsSeconds] ?: 5,
            keepScreenOn        = prefs[Keys.keepScreenOn]        ?: true,
            streamDelaySeconds  = prefs[Keys.streamDelaySeconds]  ?: 20,
            customUrl           = prefs[Keys.customUrl]           ?: "",
            customAudioUrl      = prefs[Keys.customAudioUrl]      ?: "",
            customProxyUrl      = prefs[Keys.customProxyUrl]      ?: "",
            customReferer       = prefs[Keys.customReferer]       ?: "",
            customOrigin        = prefs[Keys.customOrigin]        ?: "",
            customHeaders       = prefs[Keys.customHeaders]       ?: "",
            vpnHost             = prefs[Keys.vpnHost]             ?: "",
            vpnPort             = prefs[Keys.vpnPort]             ?: "1080",
            vpnUsername         = prefs[Keys.vpnUsername]         ?: "",
            vpnPassword         = prefs[Keys.vpnPassword]         ?: "",
            vpnUseHttp          = prefs[Keys.vpnUseHttp]          ?: false,
        )
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    /**
     * Applies [transform] to the current state and persists the result.
     * Called by every `SettingsViewModel.setXxx` function.
     */
    suspend fun update(transform: (SettingsUiState) -> SettingsUiState) {
        context.settingsDataStore.edit { prefs ->
            val current = SettingsUiState(
                autoplay            = prefs[Keys.autoplay]            ?: true,
                startMuted          = prefs[Keys.startMuted]          ?: false,
                skipLength          = prefs[Keys.skipLength]          ?: 5,
                hideControlsSeconds = prefs[Keys.hideControlsSeconds] ?: 5,
                keepScreenOn        = prefs[Keys.keepScreenOn]        ?: true,
                streamDelaySeconds  = prefs[Keys.streamDelaySeconds]  ?: 20,
                customUrl           = prefs[Keys.customUrl]           ?: "",
                customAudioUrl      = prefs[Keys.customAudioUrl]      ?: "",
                customProxyUrl      = prefs[Keys.customProxyUrl]      ?: "",
                customReferer       = prefs[Keys.customReferer]       ?: "",
                customOrigin        = prefs[Keys.customOrigin]        ?: "",
                customHeaders       = prefs[Keys.customHeaders]       ?: "",
                vpnHost             = prefs[Keys.vpnHost]             ?: "",
                vpnPort             = prefs[Keys.vpnPort]             ?: "1080",
                vpnUsername         = prefs[Keys.vpnUsername]         ?: "",
                vpnPassword         = prefs[Keys.vpnPassword]         ?: "",
                vpnUseHttp          = prefs[Keys.vpnUseHttp]          ?: false,
            )
            val next = transform(current)
            prefs[Keys.autoplay]            = next.autoplay
            prefs[Keys.startMuted]          = next.startMuted
            prefs[Keys.skipLength]          = next.skipLength
            prefs[Keys.hideControlsSeconds] = next.hideControlsSeconds
            prefs[Keys.keepScreenOn]        = next.keepScreenOn
            prefs[Keys.streamDelaySeconds]  = next.streamDelaySeconds
            prefs[Keys.customUrl]           = next.customUrl
            prefs[Keys.customAudioUrl]      = next.customAudioUrl
            prefs[Keys.customProxyUrl]      = next.customProxyUrl
            prefs[Keys.customReferer]       = next.customReferer
            prefs[Keys.customOrigin]        = next.customOrigin
            prefs[Keys.customHeaders]       = next.customHeaders
            prefs[Keys.vpnHost]             = next.vpnHost
            prefs[Keys.vpnPort]             = next.vpnPort
            prefs[Keys.vpnUsername]         = next.vpnUsername
            prefs[Keys.vpnPassword]         = next.vpnPassword
            prefs[Keys.vpnUseHttp]          = next.vpnUseHttp
        }
    }

    // ── Reset ─────────────────────────────────────────────────────────────────

    /** Clears all stored preferences; defaults from [SettingsUiState] take effect. */
    suspend fun reset() = context.settingsDataStore.edit { it.clear() }

    /**
     * Applies the VPN proxy config from [state] into [GlobalProxy] and persists it.
     * Call this whenever the user taps "Apply" in the VPN settings section.
     * The new proxy takes effect for all OkHttpClient instances on the NEXT app launch
     * (clients already built won't change mid-session), except for WebView which is
     * reconfigured immediately via ProxyController if on Android 10+.
     */
    fun applyVpnProxy(state: SettingsUiState) {
        val host = state.vpnHost.trim()
        val port = state.vpnPort.trim().toIntOrNull() ?: 1080
        val proxyConfig = if (host.isNotBlank()) {
            AppProxyConfig(
                host     = host,
                port     = port,
                username = state.vpnUsername.trim(),
                password = state.vpnPassword.trim(),
                type     = if (state.vpnUseHttp) java.net.Proxy.Type.HTTP else java.net.Proxy.Type.SOCKS,
            )
        } else null
        GlobalProxy.save(context, proxyConfig)
    }
}
