package com.linkstreamz.app

// ─────────────────────────────────────────────────────────────────────────────
// SettingsViewModel.kt
//
// Exposes a single StateFlow<SettingsUiState> consumed by SettingsScreen and
// PlayerScreen.  Every write goes through SettingsRepository → DataStore.
// ─────────────────────────────────────────────────────────────────────────────

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ── UI state ──────────────────────────────────────────────────────────────────

/**
 * Single source of truth for every persisted setting.
 *
 * Defaults match the spec:
 *   autoplay            = true
 *   startMuted          = false
 *   skipLength          = 5 s   (5 / 10 / 15 / 30)
 *   hideControlsSeconds = 5 s   (3 / 5 / 8 / 12)
 *   keepScreenOn        = true
 */
data class SettingsUiState(
    val autoplay: Boolean            = true,
    val startMuted: Boolean          = false,
    val skipLength: Int              = 5,
    val hideControlsSeconds: Int     = 5,
    val keepScreenOn: Boolean        = true,
    val streamDelaySeconds: Int      = 20,
    val customUrl: String            = "",
    val customAudioUrl: String       = "",
    val customProxyUrl: String       = "",
    // Optional stream identity overrides for the custom player.
    // When non-blank these are applied to every request for that stream.
    val customReferer: String        = "",
    val customOrigin: String         = "",
    // Raw "Key: Value" lines, one per line.  Parsed at play time.
    val customHeaders: String        = "",
    // ── Global WireGuard / SOCKS5 proxy ──────────────────────────────────────
    // When vpnHost is non-blank ALL app traffic (streams, API, token minting)
    // is routed through this SOCKS5 (default) or HTTP proxy endpoint.
    val vpnHost: String              = "",
    val vpnPort: String              = "1080",
    val vpnUsername: String          = "",
    val vpnPassword: String          = "",
    val vpnUseHttp: Boolean          = false,   // false = SOCKS5, true = HTTP proxy
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    /**
     * Observed by both SettingsScreen (for UI) and PlayerScreen (for playback
     * behaviour: autoplay, startMuted, skipLength, hideControlsSeconds, keepScreenOn).
     */
    val uiState: StateFlow<SettingsUiState> = repository.settings.stateIn(
        scope          = viewModelScope,
        started        = SharingStarted.WhileSubscribed(5_000),
        initialValue   = SettingsUiState(),
    )

    fun setAutoplay(value: Boolean)           = viewModelScope.launch {
        repository.update { it.copy(autoplay = value) }
    }
    fun setStartMuted(value: Boolean)         = viewModelScope.launch {
        repository.update { it.copy(startMuted = value) }
    }
    fun setSkipLength(value: Int)             = viewModelScope.launch {
        repository.update { it.copy(skipLength = value) }
    }
    fun setHideControlsSeconds(value: Int)    = viewModelScope.launch {
        repository.update { it.copy(hideControlsSeconds = value) }
    }
    fun setKeepScreenOn(value: Boolean)       = viewModelScope.launch {
        repository.update { it.copy(keepScreenOn = value) }
    }
    fun setStreamDelaySeconds(value: Int)     = viewModelScope.launch {
        repository.update { it.copy(streamDelaySeconds = value) }
    }
    fun setCustomUrl(value: String)           = viewModelScope.launch {
        repository.update { it.copy(customUrl = value) }
    }
    fun setCustomAudioUrl(value: String)      = viewModelScope.launch {
        repository.update { it.copy(customAudioUrl = value) }
    }
    fun setCustomProxyUrl(value: String)      = viewModelScope.launch {
        repository.update { it.copy(customProxyUrl = value) }
    }
    fun setCustomReferer(value: String)       = viewModelScope.launch {
        repository.update { it.copy(customReferer = value) }
    }
    fun setCustomOrigin(value: String)        = viewModelScope.launch {
        repository.update { it.copy(customOrigin = value) }
    }
    fun setCustomHeaders(value: String)       = viewModelScope.launch {
        repository.update { it.copy(customHeaders = value) }
    }

    // ── VPN / Global proxy setters ────────────────────────────────────────────
    fun setVpnHost(value: String)             = viewModelScope.launch {
        repository.update { it.copy(vpnHost = value) }
    }
    fun setVpnPort(value: String)             = viewModelScope.launch {
        repository.update { it.copy(vpnPort = value) }
    }
    fun setVpnUsername(value: String)         = viewModelScope.launch {
        repository.update { it.copy(vpnUsername = value) }
    }
    fun setVpnPassword(value: String)         = viewModelScope.launch {
        repository.update { it.copy(vpnPassword = value) }
    }
    fun setVpnUseHttp(value: Boolean)         = viewModelScope.launch {
        repository.update { it.copy(vpnUseHttp = value) }
    }

    /**
     * Persists the VPN proxy settings AND immediately applies them to [GlobalProxy].
     * The caller should pass the current [SettingsUiState] snapshot.
     * Note: OkHttpClient instances already built (PlayerEngine, EventsRepository) pick up
     * the new proxy on the NEXT app launch. WebView proxy is updated immediately.
     */
    fun applyVpnProxy(state: SettingsUiState) = viewModelScope.launch {
        repository.applyVpnProxy(state)
    }

    /** Clears DataStore; all values revert to SettingsUiState() defaults. */
    fun resetSettings() = viewModelScope.launch { repository.reset() }
}
