package com.example.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dlPreferencesDataStore by preferencesDataStore(name = "dl_chat_appearance")

data class AppearancePrefs(
    val themeMode: String = "DARK",
    val accentColorHex: String = "#00E5FF",
    val bubbleStyle: String = "MODERN",
    val fontScale: Float = 1.0f,
    val wallpaperId: String = "nebula",
    val reducedMotion: Boolean = false,
    val messageNotifications: Boolean = true,
    val groupNotifications: Boolean = true,
    val callNotifications: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val wifiOnlyMedia: Boolean = false,
    val compactDensity: Boolean = false,
    val appLanguage: String = "en"
)

class PreferencesRepository(private val context: Context) {
    private val keyTheme = stringPreferencesKey("theme_mode")
    private val keyAccent = stringPreferencesKey("accent_hex")
    private val keyBubble = stringPreferencesKey("bubble_style")
    private val keyFontScale = doublePreferencesKey("font_scale")
    private val keyWallpaper = stringPreferencesKey("wallpaper_id")

    private val keyReducedMotion = booleanPreferencesKey("reduced_motion")
    private val keyMessageNotifications = booleanPreferencesKey("message_notifications")
    private val keyGroupNotifications = booleanPreferencesKey("group_notifications")
    private val keyCallNotifications = booleanPreferencesKey("call_notifications")
    private val keyVibrationEnabled = booleanPreferencesKey("vibration_enabled")
    private val keyWifiOnlyMedia = booleanPreferencesKey("wifi_only_media")
    private val keyCompactDensity = booleanPreferencesKey("compact_density")
    private val keyAppLanguage = stringPreferencesKey("app_language")

    val appearanceFlow: Flow<AppearancePrefs> = context.dlPreferencesDataStore.data.map { prefs ->
        AppearancePrefs(
            themeMode = prefs[keyTheme] ?: "DARK",
            accentColorHex = prefs[keyAccent] ?: "#00E5FF",
            bubbleStyle = prefs[keyBubble] ?: "MODERN",
            fontScale = (prefs[keyFontScale] ?: 1.0).toFloat(),
            wallpaperId = prefs[keyWallpaper] ?: "nebula",
            reducedMotion = prefs[keyReducedMotion] ?: false,
            messageNotifications = prefs[keyMessageNotifications] ?: true,
            groupNotifications = prefs[keyGroupNotifications] ?: true,
            callNotifications = prefs[keyCallNotifications] ?: true,
            vibrationEnabled = prefs[keyVibrationEnabled] ?: true,
            wifiOnlyMedia = prefs[keyWifiOnlyMedia] ?: false,
            compactDensity = prefs[keyCompactDensity] ?: false,
            appLanguage = prefs[keyAppLanguage] ?: "en"
        )
    }

    /**
     * Persists remote-synced appearance attributes without clobbering device-local V4/V5 UI toggles
     * such as language, reduced motion, or granular notification/media preferences.
     */
    suspend fun saveAppearance(prefs: AppearancePrefs) {
        context.dlPreferencesDataStore.edit { store ->
            store[keyTheme] = prefs.themeMode
            store[keyAccent] = prefs.accentColorHex
            store[keyBubble] = prefs.bubbleStyle
            store[keyFontScale] = prefs.fontScale.toDouble()
            store[keyWallpaper] = prefs.wallpaperId
        }
    }

    suspend fun saveLocalUiPreferences(
        reducedMotion: Boolean? = null,
        messageNotifications: Boolean? = null,
        groupNotifications: Boolean? = null,
        callNotifications: Boolean? = null,
        vibrationEnabled: Boolean? = null,
        wifiOnlyMedia: Boolean? = null,
        compactDensity: Boolean? = null,
        appLanguage: String? = null
    ) {
        context.dlPreferencesDataStore.edit { store ->
            reducedMotion?.let { store[keyReducedMotion] = it }
            messageNotifications?.let { store[keyMessageNotifications] = it }
            groupNotifications?.let { store[keyGroupNotifications] = it }
            callNotifications?.let { store[keyCallNotifications] = it }
            vibrationEnabled?.let { store[keyVibrationEnabled] = it }
            wifiOnlyMedia?.let { store[keyWifiOnlyMedia] = it }
            compactDensity?.let { store[keyCompactDensity] = it }
            appLanguage?.let { store[keyAppLanguage] = it }
        }
    }
}
