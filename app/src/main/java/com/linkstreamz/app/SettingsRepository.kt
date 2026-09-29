package com.linkstreamz.app

// ─────────────────────────────────────────────────────────────────────────────
// SettingsRepository.kt
//
// DataStore (Preferences) persistence layer for all settings.
// The custom stream URL is intentionally NOT stored here.
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
            )
            val next = transform(current)
            prefs[Keys.autoplay]            = next.autoplay
            prefs[Keys.startMuted]          = next.startMuted
            prefs[Keys.skipLength]          = next.skipLength
            prefs[Keys.hideControlsSeconds] = next.hideControlsSeconds
            prefs[Keys.keepScreenOn]        = next.keepScreenOn
            prefs[Keys.streamDelaySeconds]  = next.streamDelaySeconds
        }
    }

    // ── Reset ─────────────────────────────────────────────────────────────────

    /** Clears all stored preferences; defaults from [SettingsUiState] take effect. */
    suspend fun reset() = context.settingsDataStore.edit { it.clear() }
}
