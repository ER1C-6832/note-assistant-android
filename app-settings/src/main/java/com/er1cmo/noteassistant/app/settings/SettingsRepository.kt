package com.er1cmo.noteassistant.app.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appSettingsDataStore by preferencesDataStore(name = "note_assistant_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val assistantEnabled = booleanPreferencesKey("assistant_enabled")
        val websocketUrl = stringPreferencesKey("websocket_url")
        val homeBackgroundColor = stringPreferencesKey("home_background_color")
        val tagDrawerBackgroundColor = stringPreferencesKey("tag_drawer_background_color")
    }

    val assistantEnabled: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs ->
        prefs[Keys.assistantEnabled] ?: false
    }

    val websocketUrl: Flow<String> = context.appSettingsDataStore.data.map { prefs ->
        prefs[Keys.websocketUrl] ?: "wss://example.invalid/xiaozhi"
    }

    val homeBackgroundColor: Flow<String> = context.appSettingsDataStore.data.map { prefs ->
        prefs[Keys.homeBackgroundColor] ?: DEFAULT_HOME_BACKGROUND
    }

    val tagDrawerBackgroundColor: Flow<String> = context.appSettingsDataStore.data.map { prefs ->
        prefs[Keys.tagDrawerBackgroundColor] ?: DEFAULT_TAG_DRAWER_BACKGROUND
    }

    suspend fun setAssistantEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { prefs -> prefs[Keys.assistantEnabled] = enabled }
    }

    suspend fun setWebsocketUrl(url: String) {
        context.appSettingsDataStore.edit { prefs -> prefs[Keys.websocketUrl] = url }
    }

    suspend fun setHomeBackgroundColor(hex: String) {
        context.appSettingsDataStore.edit { prefs -> prefs[Keys.homeBackgroundColor] = hex }
    }

    suspend fun setTagDrawerBackgroundColor(hex: String) {
        context.appSettingsDataStore.edit { prefs -> prefs[Keys.tagDrawerBackgroundColor] = hex }
    }

    companion object {
        const val DEFAULT_HOME_BACKGROUND = "#FFFFFF"
        const val DEFAULT_TAG_DRAWER_BACKGROUND = "#FFF3D1"
    }
}
