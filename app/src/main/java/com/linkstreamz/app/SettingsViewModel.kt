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

    /** Clears DataStore; all values revert to SettingsUiState() defaults. */
    fun resetSettings() = viewModelScope.launch { repository.reset() }
}
