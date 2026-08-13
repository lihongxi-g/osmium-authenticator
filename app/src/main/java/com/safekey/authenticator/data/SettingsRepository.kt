package com.safekey.authenticator.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "safekey_settings")

data class AppSettings(
    val themeMode: String = THEME_SYSTEM,
    val dynamicColor: Boolean = true,
    val hapticIntensity: Int = 80,
    val biometricLock: Boolean = false,
    val clipboardClearSeconds: Int = 30,
    val pinVerifyMode: String = PIN_VERIFY_OFF,
    val pinFixedHour: Int = 9,
    val pinFixedMinute: Int = 0,
    val destroyMode: String = DESTROY_OFF,
    val failThreshold: Int = 5,
    val lastPinVerifiedDay: Long = 0L,
    val pinFailCount: Int = 0,
    val biometricFailCount: Int = 0
) {
    companion object {
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"

        const val PIN_VERIFY_OFF = "off"
        const val PIN_VERIFY_RANDOM = "random"
        const val PIN_VERIFY_DAILY = "daily"

        const val DESTROY_OFF = "off"
        const val DESTROY_PIN = "destroy_pin"
        const val DESTROY_FAIL_COUNT = "fail_count"
    }
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val HAPTIC_INTENSITY = intPreferencesKey("haptic_intensity")
        val BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock")
        val CLIPBOARD_CLEAR = intPreferencesKey("clipboard_clear_seconds")
        val PIN_VERIFY_MODE = stringPreferencesKey("pin_verify_mode")
        val PIN_FIXED_HOUR = intPreferencesKey("pin_fixed_hour")
        val PIN_FIXED_MINUTE = intPreferencesKey("pin_fixed_minute")
        val DESTROY_MODE = stringPreferencesKey("destroy_mode")
        val FAIL_THRESHOLD = intPreferencesKey("fail_threshold")
        val LAST_PIN_VERIFIED_DAY = longPreferencesKey("last_pin_verified_day")
        val PIN_FAIL_COUNT = intPreferencesKey("pin_fail_count")
        val BIOMETRIC_FAIL_COUNT = intPreferencesKey("biometric_fail_count")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[Keys.THEME_MODE] ?: AppSettings.THEME_SYSTEM,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
            hapticIntensity = prefs[Keys.HAPTIC_INTENSITY] ?: 80,
            biometricLock = prefs[Keys.BIOMETRIC_LOCK] ?: false,
            clipboardClearSeconds = prefs[Keys.CLIPBOARD_CLEAR] ?: 30,
            pinVerifyMode = prefs[Keys.PIN_VERIFY_MODE] ?: AppSettings.PIN_VERIFY_OFF,
            pinFixedHour = prefs[Keys.PIN_FIXED_HOUR] ?: 9,
            pinFixedMinute = prefs[Keys.PIN_FIXED_MINUTE] ?: 0,
            destroyMode = prefs[Keys.DESTROY_MODE] ?: AppSettings.DESTROY_OFF,
            failThreshold = prefs[Keys.FAIL_THRESHOLD] ?: 5,
            lastPinVerifiedDay = prefs[Keys.LAST_PIN_VERIFIED_DAY] ?: 0L,
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

    suspend fun setHapticIntensity(percent: Int) {
        context.dataStore.edit { it[Keys.HAPTIC_INTENSITY] = percent }
    }

    suspend fun setBiometricLock(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BIOMETRIC_LOCK] = enabled }
    }

    suspend fun setClipboardClearSeconds(seconds: Int) {
        context.dataStore.edit { it[Keys.CLIPBOARD_CLEAR] = seconds }
    }

    suspend fun setPinVerifyMode(mode: String) {
        context.dataStore.edit { it[Keys.PIN_VERIFY_MODE] = mode }
    }

    suspend fun setPinFixedTime(hour: Int, minute: Int) {
        context.dataStore.edit {
            it[Keys.PIN_FIXED_HOUR] = hour
            it[Keys.PIN_FIXED_MINUTE] = minute
        }
    }

    suspend fun setDestroyMode(mode: String) {
        context.dataStore.edit { it[Keys.DESTROY_MODE] = mode }
    }

    suspend fun setFailThreshold(threshold: Int) {
        context.dataStore.edit { it[Keys.FAIL_THRESHOLD] = threshold }
    }

    suspend fun setLastPinVerifiedDay(day: Long) {
        context.dataStore.edit { it[Keys.LAST_PIN_VERIFIED_DAY] = day }
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
