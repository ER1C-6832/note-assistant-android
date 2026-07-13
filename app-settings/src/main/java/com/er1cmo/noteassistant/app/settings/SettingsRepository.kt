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
        val assistantTextPanelEnabled = booleanPreferencesKey("assistant_text_panel_enabled") // legacy
        val assistantConversationTextEnabled = booleanPreferencesKey("assistant_conversation_text_enabled")
        val assistantTextInputEnabled = booleanPreferencesKey("assistant_text_input_enabled")

        val assistantOtaUrl = stringPreferencesKey("assistant_ota_url")
        val assistantAuthorizationUrl = stringPreferencesKey("assistant_authorization_url")
        val assistantWebsocketToken = stringPreferencesKey("assistant_websocket_token")
        val assistantActivationVersion = stringPreferencesKey("assistant_activation_version")
        val assistantActivationStatus = booleanPreferencesKey("assistant_activation_status")
        val assistantActivationCode = stringPreferencesKey("assistant_activation_code")
        val assistantActivationChallenge = stringPreferencesKey("assistant_activation_challenge")
        val assistantActivationMessage = stringPreferencesKey("assistant_activation_message")
        val assistantClientId = stringPreferencesKey("assistant_client_id")
        val assistantDeviceId = stringPreferencesKey("assistant_device_id")
        val assistantSerialNumber = stringPreferencesKey("assistant_serial_number")
        val assistantHmacKey = stringPreferencesKey("assistant_hmac_key")
        val assistantLastJson = stringPreferencesKey("assistant_last_json")
    }

    val assistantEnabled: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs ->
        prefs[Keys.assistantEnabled] ?: false
    }

    val websocketUrl: Flow<String> = context.appSettingsDataStore.data.map { prefs ->
        prefs[Keys.websocketUrl] ?: DEFAULT_ASSISTANT_WEBSOCKET_URL
    }

    val assistantWebsocketToken: Flow<String> = context.appSettingsDataStore.data.map { prefs ->
        prefs[Keys.assistantWebsocketToken].orEmpty()
    }

    val assistantOtaUrl: Flow<String> = context.appSettingsDataStore.data.map { prefs ->
        prefs[Keys.assistantOtaUrl] ?: DEFAULT_ASSISTANT_OTA_URL
    }

    val assistantAuthorizationUrl: Flow<String> = context.appSettingsDataStore.data.map { prefs ->
        prefs[Keys.assistantAuthorizationUrl] ?: DEFAULT_ASSISTANT_AUTHORIZATION_URL
    }

    val assistantActivationVersion: Flow<String> = context.appSettingsDataStore.data.map { prefs ->
        prefs[Keys.assistantActivationVersion] ?: DEFAULT_ASSISTANT_ACTIVATION_VERSION
    }

    val assistantActivationStatus: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs ->
        prefs[Keys.assistantActivationStatus] ?: false
    }

    val assistantActivationCode: Flow<String> = context.appSettingsDataStore.data.map { prefs ->
        prefs[Keys.assistantActivationCode].orEmpty()
    }

    val assistantActivationMessage: Flow<String> = context.appSettingsDataStore.data.map { prefs ->
        prefs[Keys.assistantActivationMessage].orEmpty()
    }

    val assistantClientId: Flow<String> = context.appSettingsDataStore.data.map { prefs ->
        prefs[Keys.assistantClientId].orEmpty()
    }

    val assistantDeviceId: Flow<String> = context.appSettingsDataStore.data.map { prefs ->
        prefs[Keys.assistantDeviceId].orEmpty()
    }

    val assistantSerialNumber: Flow<String> = context.appSettingsDataStore.data.map { prefs ->
        prefs[Keys.assistantSerialNumber].orEmpty()
    }

    val assistantHmacKey: Flow<String> = context.appSettingsDataStore.data.map { prefs ->
        prefs[Keys.assistantHmacKey].orEmpty()
    }

    val assistantLastJson: Flow<String> = context.appSettingsDataStore.data.map { prefs ->
        prefs[Keys.assistantLastJson].orEmpty()
    }

    val homeBackgroundColor: Flow<String> = context.appSettingsDataStore.data.map { prefs ->
        prefs[Keys.homeBackgroundColor] ?: DEFAULT_HOME_BACKGROUND
    }

    val tagDrawerBackgroundColor: Flow<String> = context.appSettingsDataStore.data.map { prefs ->
        prefs[Keys.tagDrawerBackgroundColor] ?: DEFAULT_TAG_DRAWER_BACKGROUND
    }

    val assistantTextPanelEnabled: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs ->
        prefs[Keys.assistantTextPanelEnabled] ?: false
    }

    // Phase5 product transcript/input split: transcript is visible by default;
    // text input remains an explicit opt-in surface.
    val assistantConversationTextEnabled: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs ->
        prefs[Keys.assistantConversationTextEnabled] ?: true
    }

    val assistantTextInputEnabled: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs ->
        prefs[Keys.assistantTextInputEnabled] ?: false
    }

    suspend fun setAssistantEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { prefs -> prefs[Keys.assistantEnabled] = enabled }
    }

    suspend fun setWebsocketUrl(url: String) {
        context.appSettingsDataStore.edit { prefs -> prefs[Keys.websocketUrl] = url.ifBlank { DEFAULT_ASSISTANT_WEBSOCKET_URL } }
    }

    suspend fun setAssistantOtaUrl(url: String) {
        context.appSettingsDataStore.edit { prefs -> prefs[Keys.assistantOtaUrl] = url.ifBlank { DEFAULT_ASSISTANT_OTA_URL } }
    }

    suspend fun setAssistantAuthorizationUrl(url: String) {
        context.appSettingsDataStore.edit { prefs -> prefs[Keys.assistantAuthorizationUrl] = url.ifBlank { DEFAULT_ASSISTANT_AUTHORIZATION_URL } }
    }

    suspend fun setAssistantActivationVersion(value: String) {
        context.appSettingsDataStore.edit { prefs -> prefs[Keys.assistantActivationVersion] = value.ifBlank { DEFAULT_ASSISTANT_ACTIVATION_VERSION } }
    }

    suspend fun saveAssistantNetworkSettings(
        otaUrl: String,
        authorizationUrl: String,
        websocketUrl: String,
        websocketToken: String,
        activationVersion: String,
    ) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.assistantOtaUrl] = otaUrl.ifBlank { DEFAULT_ASSISTANT_OTA_URL }
            prefs[Keys.assistantAuthorizationUrl] = authorizationUrl.ifBlank { DEFAULT_ASSISTANT_AUTHORIZATION_URL }
            prefs[Keys.websocketUrl] = websocketUrl.ifBlank { DEFAULT_ASSISTANT_WEBSOCKET_URL }
            if (websocketToken.isBlank()) {
                prefs.remove(Keys.assistantWebsocketToken)
            } else {
                prefs[Keys.assistantWebsocketToken] = websocketToken.trim()
            }
            prefs[Keys.assistantActivationVersion] = activationVersion.ifBlank { DEFAULT_ASSISTANT_ACTIVATION_VERSION }
        }
    }

    suspend fun saveAssistantIdentity(
        clientId: String,
        deviceId: String,
        serialNumber: String,
        hmacKey: String,
    ) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.assistantClientId] = clientId
            prefs[Keys.assistantDeviceId] = deviceId
            prefs[Keys.assistantSerialNumber] = serialNumber
            prefs[Keys.assistantHmacKey] = hmacKey
        }
    }

    suspend fun clearAssistantIdentityAndActivation() {
        context.appSettingsDataStore.edit { prefs ->
            prefs.remove(Keys.assistantClientId)
            prefs.remove(Keys.assistantDeviceId)
            prefs.remove(Keys.assistantSerialNumber)
            prefs.remove(Keys.assistantHmacKey)
            prefs[Keys.assistantActivationStatus] = false
            prefs.remove(Keys.assistantActivationCode)
            prefs.remove(Keys.assistantActivationChallenge)
            prefs.remove(Keys.assistantActivationMessage)
            prefs.remove(Keys.assistantWebsocketToken)
            prefs.remove(Keys.assistantLastJson)
            prefs.remove(Keys.websocketUrl)
        }
    }

    suspend fun saveAssistantWebSocketConfig(url: String, token: String) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.websocketUrl] = url.ifBlank { DEFAULT_ASSISTANT_WEBSOCKET_URL }
            if (token.isBlank()) {
                prefs.remove(Keys.assistantWebsocketToken)
            } else {
                prefs[Keys.assistantWebsocketToken] = token
            }
        }
    }

    suspend fun setAssistantActivationStatus(activated: Boolean) {
        context.appSettingsDataStore.edit { prefs -> prefs[Keys.assistantActivationStatus] = activated }
    }

    suspend fun saveAssistantActivationData(code: String, challenge: String, message: String) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[Keys.assistantActivationCode] = code
            prefs[Keys.assistantActivationChallenge] = challenge
            prefs[Keys.assistantActivationMessage] = message
        }
    }

    suspend fun clearAssistantActivationData() {
        context.appSettingsDataStore.edit { prefs ->
            prefs.remove(Keys.assistantActivationCode)
            prefs.remove(Keys.assistantActivationChallenge)
            prefs.remove(Keys.assistantActivationMessage)
        }
    }

    suspend fun saveAssistantLastJson(value: String) {
        context.appSettingsDataStore.edit { prefs -> prefs[Keys.assistantLastJson] = value }
    }

    suspend fun setHomeBackgroundColor(hex: String) {
        context.appSettingsDataStore.edit { prefs -> prefs[Keys.homeBackgroundColor] = hex }
    }

    suspend fun setTagDrawerBackgroundColor(hex: String) {
        context.appSettingsDataStore.edit { prefs -> prefs[Keys.tagDrawerBackgroundColor] = hex }
    }

    suspend fun setAssistantTextPanelEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { prefs -> prefs[Keys.assistantTextPanelEnabled] = enabled }
    }

    suspend fun setAssistantConversationTextEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { prefs -> prefs[Keys.assistantConversationTextEnabled] = enabled }
    }

    suspend fun setAssistantTextInputEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { prefs -> prefs[Keys.assistantTextInputEnabled] = enabled }
    }

    companion object {
        const val DEFAULT_HOME_BACKGROUND = "#FFFFFF"
        const val DEFAULT_TAG_DRAWER_BACKGROUND = "#FFF3D1"
        const val DEFAULT_ASSISTANT_OTA_URL = "https://api.tenclass.net/xiaozhi/ota/"
        const val DEFAULT_ASSISTANT_AUTHORIZATION_URL = "https://xiaozhi.me/"
        const val DEFAULT_ASSISTANT_WEBSOCKET_URL = "wss://example.invalid/xiaozhi"
        const val DEFAULT_ASSISTANT_ACTIVATION_VERSION = "v2"
    }
}
