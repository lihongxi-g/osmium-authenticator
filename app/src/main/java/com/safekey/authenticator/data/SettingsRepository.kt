package com.safekey.authenticator.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.safekey.authenticator.security.CryptoManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "safekey_settings")

data class AppSettings(
    val themeMode: String = THEME_SYSTEM,
    val dynamicColor: Boolean = true,
    val gateOnOpen: Boolean = true,
    val allowScreenshots: Boolean = false,
    val hideCodes: Boolean = false,
    val timeOffsetSeconds: Int = 0,
    val sortMode: String = SORT_ADDED,
    val destroyMode: String = DESTROY_OFF,
    val failThreshold: Int = 5,
    val pinFailCount: Int = 0,
    val biometricFailCount: Int = 0
) {
    companion object {
        const val THEME_SYSTEM = "system"
        const val SORT_RANDOM = "random"
        const val SORT_ALPHA = "alpha"
        const val SORT_ADDED = "added"
        const val SORT_COPIES = "copies"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"

        const val DESTROY_OFF = "off"
        const val DESTROY_PIN = "destroy_pin"
        const val DESTROY_FAIL_COUNT = "fail_count"
    }
}

class SettingsRepository(
    private val context: Context,
    private val crypto: CryptoManager
) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val GATE_ON_OPEN = booleanPreferencesKey("gate_on_open")
        val ALLOW_SCREENSHOTS = booleanPreferencesKey("allow_screenshots")
        val HIDE_CODES = booleanPreferencesKey("hide_codes")
        val SORT_MODE = stringPreferencesKey("sort_mode")
        val TIME_OFFSET = intPreferencesKey("time_offset_seconds")
        val DESTROY_MODE = stringPreferencesKey("destroy_mode")
        val FAIL_THRESHOLD = intPreferencesKey("fail_threshold")
        val PIN_FAIL_COUNT = intPreferencesKey("pin_fail_count")
        val BIOMETRIC_FAIL_COUNT = intPreferencesKey("biometric_fail_count")
        val WEBDAV_URL = stringPreferencesKey("webdav_url")
        val WEBDAV_USER = stringPreferencesKey("webdav_user")
        val WEBDAV_PASS_IV = stringPreferencesKey("webdav_pass_iv")
        val WEBDAV_PASS_CT = stringPreferencesKey("webdav_pass_ct")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[Keys.THEME_MODE] ?: AppSettings.THEME_SYSTEM,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
            gateOnOpen = prefs[Keys.GATE_ON_OPEN] ?: true,
            allowScreenshots = prefs[Keys.ALLOW_SCREENSHOTS] ?: false,
            hideCodes = prefs[Keys.HIDE_CODES] ?: false,
            sortMode = prefs[Keys.SORT_MODE] ?: AppSettings.SORT_ADDED,
            timeOffsetSeconds = prefs[Keys.TIME_OFFSET] ?: 0,
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

    suspend fun setGateOnOpen(enabled: Boolean) {
        context.dataStore.edit { it[Keys.GATE_ON_OPEN] = enabled }
    }

    suspend fun setAllowScreenshots(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ALLOW_SCREENSHOTS] = enabled }
    }

    suspend fun setHideCodes(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HIDE_CODES] = enabled }
    }

    suspend fun setSortMode(mode: String) {
        context.dataStore.edit { it[Keys.SORT_MODE] = mode }
    }

    suspend fun setTimeOffsetSeconds(offset: Int) {
        context.dataStore.edit { it[Keys.TIME_OFFSET] = offset }
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

    // ------------------------------------------------------ WebDAV backup

    /** The saved WebDAV server, with the password decrypted on read; null when
     *  no server is configured yet. */
    val webDavConfig: Flow<WebDavServerConfig?> = context.dataStore.data.map { prefs ->
        val url = prefs[Keys.WEBDAV_URL]?.takeIf { it.isNotBlank() } ?: return@map null
        val user = prefs[Keys.WEBDAV_USER] ?: ""
        val iv = prefs[Keys.WEBDAV_PASS_IV]
        val ct = prefs[Keys.WEBDAV_PASS_CT]
        val password = if (iv != null && ct != null) {
            try {
                crypto.decrypt(CryptoManager.EncryptedField(iv, ct))
            } catch (e: Exception) {
                // Corrupt password record — fall back to blank rather than crash
                ""
            }
        } else ""
        WebDavServerConfig(baseUrl = url, username = user, password = password)
    }

    /** Persist the server config; the password is encrypted with the
     *  Android Keystore key before it touches disk. */
    suspend fun setWebDavConfig(config: WebDavServerConfig?) {
        context.dataStore.edit { prefs ->
            if (config == null || config.baseUrl.isBlank()) {
                prefs.remove(Keys.WEBDAV_URL)
                prefs.remove(Keys.WEBDAV_USER)
                prefs.remove(Keys.WEBDAV_PASS_IV)
                prefs.remove(Keys.WEBDAV_PASS_CT)
            } else {
                prefs[Keys.WEBDAV_URL] = config.baseUrl.trim()
                prefs[Keys.WEBDAV_USER] = config.username
                if (config.password.isEmpty()) {
                    prefs.remove(Keys.WEBDAV_PASS_IV)
                    prefs.remove(Keys.WEBDAV_PASS_CT)
                } else {
                    val field = crypto.encrypt(config.password)
                    prefs[Keys.WEBDAV_PASS_IV] = field.iv
                    prefs[Keys.WEBDAV_PASS_CT] = field.ciphertext
                }
            }
        }
    }

    suspend fun wipeSettings() {
        context.dataStore.edit { it.clear() }
    }
}
