package com.safekey.authenticator.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.safekey.authenticator.security.CryptoManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
    val biometricFailCount: Int = 0,
    val autoBackupEnabled: Boolean = false,
    val autoBackupTarget: String = AUTO_BACKUP_TARGET_LOCAL,
    val autoBackupIntervalDays: Int = 1,
    val autoBackupHour: Int = 3,
    val autoBackupMinute: Int = 0,
    val autoBackupPasswordSet: Boolean = false,
    val autoBackupKeepCount: Int = 5,
    val autoBackupLastTime: Long = 0L,
    val autoBackupLastError: String = "",
    val autoCheckUpdates: Boolean = true,
    val tagsEnabled: Boolean = true
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

        const val AUTO_BACKUP_TARGET_WEBDAV = "webdav"
        const val AUTO_BACKUP_TARGET_LOCAL = "local"

        /** How many auto-backups to keep per target; older ones are pruned. */
        const val AUTO_BACKUP_KEEP_DEFAULT = 5
        const val AUTO_BACKUP_KEEP_MAX = 10
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
        val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        val AUTO_BACKUP_TARGET = stringPreferencesKey("auto_backup_target")
        val AUTO_BACKUP_INTERVAL = intPreferencesKey("auto_backup_interval_days")
        val AUTO_BACKUP_HOUR = intPreferencesKey("auto_backup_hour")
        val AUTO_BACKUP_MINUTE = intPreferencesKey("auto_backup_minute")
        val AUTO_BACKUP_PASS_IV = stringPreferencesKey("auto_backup_pass_iv")
        val AUTO_BACKUP_PASS_CT = stringPreferencesKey("auto_backup_pass_ct")
        val AUTO_BACKUP_KEEP = intPreferencesKey("auto_backup_keep_count")
        val AUTO_BACKUP_LAST_TIME = longPreferencesKey("auto_backup_last_time")
        val AUTO_BACKUP_LAST_ERROR = stringPreferencesKey("auto_backup_last_error")
        val AUTO_CHECK_UPDATES = booleanPreferencesKey("auto_check_updates")
        val TAGS_ENABLED = booleanPreferencesKey("tags_enabled")
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
            biometricFailCount = prefs[Keys.BIOMETRIC_FAIL_COUNT] ?: 0,
            autoBackupEnabled = prefs[Keys.AUTO_BACKUP_ENABLED] ?: false,
            autoBackupTarget = prefs[Keys.AUTO_BACKUP_TARGET] ?: AppSettings.AUTO_BACKUP_TARGET_LOCAL,
            autoBackupIntervalDays = prefs[Keys.AUTO_BACKUP_INTERVAL] ?: 1,
            autoBackupHour = prefs[Keys.AUTO_BACKUP_HOUR] ?: 3,
            autoBackupMinute = prefs[Keys.AUTO_BACKUP_MINUTE] ?: 0,
            autoBackupPasswordSet = prefs[Keys.AUTO_BACKUP_PASS_IV] != null,
            autoBackupKeepCount = prefs[Keys.AUTO_BACKUP_KEEP] ?: AppSettings.AUTO_BACKUP_KEEP_DEFAULT,
            autoBackupLastTime = prefs[Keys.AUTO_BACKUP_LAST_TIME] ?: 0L,
            autoBackupLastError = prefs[Keys.AUTO_BACKUP_LAST_ERROR] ?: "",
            autoCheckUpdates = prefs[Keys.AUTO_CHECK_UPDATES] ?: true,
            tagsEnabled = prefs[Keys.TAGS_ENABLED] ?: true
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

    // ------------------------------------------------------- auto backup

    suspend fun setAutoBackupEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_BACKUP_ENABLED] = enabled }
    }

    suspend fun setAutoBackupTarget(target: String) {
        context.dataStore.edit { it[Keys.AUTO_BACKUP_TARGET] = target }
    }

    suspend fun setAutoBackupIntervalDays(days: Int) {
        context.dataStore.edit { it[Keys.AUTO_BACKUP_INTERVAL] = days.coerceIn(1, 365) }
    }

    suspend fun setAutoBackupTime(hour: Int, minute: Int) {
        context.dataStore.edit {
            it[Keys.AUTO_BACKUP_HOUR] = hour.coerceIn(0, 23)
            it[Keys.AUTO_BACKUP_MINUTE] = minute.coerceIn(0, 59)
        }
    }

    /**
     * Store the export password used by unattended scheduled backups,
     * encrypted with the Android Keystore key — same storage as the WebDAV
     * server password. Without this the scheduled worker has no password
     * to encrypt the backup file with.
     */
    suspend fun setAutoBackupPassword(password: String) {
        context.dataStore.edit { prefs ->
            if (password.isEmpty()) {
                prefs.remove(Keys.AUTO_BACKUP_PASS_IV)
                prefs.remove(Keys.AUTO_BACKUP_PASS_CT)
            } else {
                val field = crypto.encrypt(password)
                prefs[Keys.AUTO_BACKUP_PASS_IV] = field.iv
                prefs[Keys.AUTO_BACKUP_PASS_CT] = field.ciphertext
            }
        }
    }

    /** Decrypt the stored auto-backup password; null when none is set. */
    suspend fun getAutoBackupPassword(): String? {
        val prefs = context.dataStore.data.first()
        val iv = prefs[Keys.AUTO_BACKUP_PASS_IV] ?: return null
        val ct = prefs[Keys.AUTO_BACKUP_PASS_CT] ?: return null
        return try {
            crypto.decrypt(CryptoManager.EncryptedField(iv, ct))
        } catch (e: Exception) {
            // Corrupt password record — fall back to null rather than crash
            null
        }
    }

    /** Result of the last auto-backup run: empty error = success. */
    suspend fun setAutoBackupResult(error: String, time: Long) {
        context.dataStore.edit {
            it[Keys.AUTO_BACKUP_LAST_TIME] = time
            it[Keys.AUTO_BACKUP_LAST_ERROR] = error
        }
    }

    suspend fun setAutoBackupKeepCount(count: Int) {
        context.dataStore.edit {
            it[Keys.AUTO_BACKUP_KEEP] = count.coerceIn(1, AppSettings.AUTO_BACKUP_KEEP_MAX)
        }
    }

    // ------------------------------------------------------ update checks

    suspend fun setAutoCheckUpdates(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_CHECK_UPDATES] = enabled }
    }

    suspend fun setTagsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.TAGS_ENABLED] = enabled }
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
