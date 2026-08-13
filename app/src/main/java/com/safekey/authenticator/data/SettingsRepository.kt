package com.safekey.authenticator.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "safekey_settings")

data class AppSettings(
    val themeMode: String = THEME_SYSTEM,
    val dynamicColor: Boolean = true,
    val clipboardClearSeconds: Int = 30,
    val destroyMode: String = DESTROY_OFF,
    val failThreshold: Int = 5,
    val pinFailCount: Int = 0,
    val biometricFailCount: Int = 0
) {
    companion object {
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"

        const val DESTROY_OFF = "off"
        const val DESTROY_PIN = "destroy_pin"
        const val DESTROY_FAIL_COUNT = "fail_count"
    }
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val CLIPBOARD_CLEAR = intPreferencesKey("clipboard_clear_seconds")
        val DESTROY_MODE = stringPreferencesKey("destroy_mode")
        val FAIL_THRESHOLD = intPreferencesKey("fail_threshold")
        val PIN_FAIL_COUNT = intPreferencesKey("pin_fail_count")
        val BIOMETRIC_FAIL_COUNT = intPreferencesKey("biometric_fail_count")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[Keys.THEME_MODE] ?: AppSettings.THEME_SYSTEM,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
            clipboardClearSeconds = prefs[Keys.CLIPBOARD_CLEAR] ?: 30,
            destroyMode = prefs[Keys.DESTROY_MODE] ?: AppSettings.DESTROY_OFF,
            failThreshold = prefs[Keys.FAIL_THRESHOLD] ?: 5,
            pinFailCount = prefs[Keys.PIN_FAIL_COUNT] ?: 0,
            biometricFailCount = prefs[Keys.BIOMETRIC_FAIL_COUNT] ?: 0
        )
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setClipboardClearSeconds(seconds: Int) {
        context.dataStore.edit { it[Keys.CLIPBOARD_CLEAR] = seconds }
    }

    suspend fun setDestroyMode(mode: String) {
        context.dataStore.edit { it[Keys.DESTROY_MODE] = mode }
    }

    suspend fun setFailThreshold(threshold: Int) {
        context.dataStore.edit { it[Keys.FAIL_THRESHOLD] = threshold }
    }

    suspend fun setPinFailCount(count: Int) {
        context.dataStore.edit { it[Keys.PIN_FAIL_COUNT] = count }
    }

    suspend fun setBiometricFailCount(count: Int) {
        context.dataStore.edit { it[Keys.BIOMETRIC_FAIL_COUNT] = count }
    }

    suspend fun wipeSettings() {
        context.dataStore.edit { it.clear() }
    }
}
