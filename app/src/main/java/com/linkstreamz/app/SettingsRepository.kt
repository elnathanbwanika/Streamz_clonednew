package com.linkstreamz.app

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore("linkstream_settings")

data class PlaybackSettings(
    val autoplay: Boolean = true,
    val muted: Boolean = false,
    val skip: Int = 10,
    val hide: Int = 5,
    val awake: Boolean = true,
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val autoplay = booleanPreferencesKey("autoplay")
        val muted = booleanPreferencesKey("muted")
        val skip = intPreferencesKey("skip")
        val hide = intPreferencesKey("hide")
        val awake = booleanPreferencesKey("awake")
    }

    val settings: Flow<PlaybackSettings> = context.settingsDataStore.data.map { prefs ->
        PlaybackSettings(
            autoplay = prefs[Keys.autoplay] ?: true,
            muted = prefs[Keys.muted] ?: false,
            skip = prefs[Keys.skip] ?: 10,
            hide = prefs[Keys.hide] ?: 5,
            awake = prefs[Keys.awake] ?: true,
        )
    }

    suspend fun update(transform: (PlaybackSettings) -> PlaybackSettings) {
        context.settingsDataStore.edit { prefs ->
            val current = PlaybackSettings(
                prefs[Keys.autoplay] ?: true,
                prefs[Keys.muted] ?: false,
                prefs[Keys.skip] ?: 10,
                prefs[Keys.hide] ?: 5,
                prefs[Keys.awake] ?: true,
            )
            val next = transform(current)
            prefs[Keys.autoplay] = next.autoplay
            prefs[Keys.muted] = next.muted
            prefs[Keys.skip] = next.skip
            prefs[Keys.hide] = next.hide
            prefs[Keys.awake] = next.awake
        }
    }

    suspend fun reset() = context.settingsDataStore.edit { it.clear() }
}