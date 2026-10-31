package com.safekey.authenticator.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.safekey.authenticator.security.CryptoManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "safekey_settings")

data class AppSettings(
    val themeMode: String = THEME_SYSTEM,
    val dynamicColor: Boolean = true,
    /** Text scale in percent, 80-200; applied on top of the system font scale. */
    val fontScalePercent: Int = FONT_SCALE_PERCENT_DEFAULT,
    /** Master switch: off means Osmium's own default size, slider locked. */
    val fontScaleEnabled: Boolean = false,
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
    /** When a run last *started* (survives a run that dies without a result). */
    val autoBackupLastAttempt: Long = 0L,
    /** Send a notification when an unattended backup finishes. */
    val autoBackupNotify: Boolean = true,
    /**
     * When the schedule was last changed by the user (time / interval / target /
     * switch). A success recorded *before* this cannot absorb the next run: the
     * user just asked for one, and swallowing it silently made "I changed the
     * time and nothing happened" the normal experience.
     */
    val autoBackupConfigChangedAt: Long = 0L,
    /**
     * Ask for identity verification when the auto-backup page is opened from a
     * backup notification. That page can change the backup password, so on a
     * device whose screen is already unlocked a stranger tapping the
     * notification would otherwise own the backups. Not applied while "verify on
     * open" is on — the open gate already verified that same entry.
     */
    val autoBackupNotifyRequireAuth: Boolean = true,
    val autoCheckUpdates: Boolean = false,
    /**
     * Also look at pre-releases (beta / preview builds). Off by default: a
     * preview is a test build and must never arrive uninvited.
     */
    val checkPreviewUpdates: Boolean = false,
    val tagsEnabled: Boolean = true,
    // ---- root hardening / developer mode (2.4.2) ----
    val devModeEnabled: Boolean = false,
    val devDisableRootSecurity: Boolean = false,
    val devPlaintextExport: Boolean = false,

    val devHiddenFeatures: Set<String> = emptySet(),
    // ---- integrity detection logging (2026-09) ----
    val devDetailedLogging: Boolean = false,
    /** User asked not to be reminded about setting a PIN on a device without biometrics. */
    val pinReminderSilenced: Boolean = false
) {
    companion object {
        const val THEME_SYSTEM = "system"
        const val SORT_RANDOM = "random"
        const val SORT_ALPHA = "alpha"
        const val SORT_ADDED = "added"
        const val SORT_COPIES = "copies"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"

        // ---- text size ----
        const val FONT_SCALE_PERCENT_MIN = 80
        const val FONT_SCALE_PERCENT_MAX = 200
        const val FONT_SCALE_PERCENT_STEP = 5
        const val FONT_SCALE_PERCENT_DEFAULT = 100

        /**
         * Ceiling for the combined scale (app percent × system font scale).
         *
         * The PIN keypad is a fixed 72dp circle holding a headlineSmall digit:
         * at 2.0 the 32sp line box is 64dp and still fits, at 2.6 it is 83dp
         * and clips. The keypad also cannot grow sideways — three 72dp keys
         * plus gaps already use 232dp of a 360dp screen — so the text has to
         * stop growing, not the key.
         */
        const val FONT_SCALE_MAX_COMPOSITE = 2.0f

        /**
         * Step ids written by the 2.6.2 test builds. Kept so an install that
         * already picked a step keeps that size after the slider landed.
         */
        fun fontScalePercentOfLegacyStep(id: String): Int = when (id) {
            "small" -> 90
            "large" -> 115
            "xlarge" -> 130
            else -> FONT_SCALE_PERCENT_DEFAULT
        }

        /**
         * Percent actually applied on top of [systemFontScale].
         *
         * The master switch wins: off means Osmium's own default typography
         * (1.0) no matter what the slider says. Values at or below 100% are
         * returned as they are — the user may legitimately want smaller text,
         * and "100%" must stay exactly the system size even on a ROM whose
         * scale is above 200%. Larger values are capped so the combined scale
         * stops at [FONT_SCALE_MAX_COMPOSITE]; past that point they add
         * nothing rather than shrinking the system's own text.
         */
        fun effectiveFontScale(enabled: Boolean, percent: Int, systemFontScale: Float): Float {
            if (!enabled) return 1f
            val step = percent.coerceIn(FONT_SCALE_PERCENT_MIN, FONT_SCALE_PERCENT_MAX) / 100f
            if (step <= 1f) return step
            return maxOf(1f, minOf(step, FONT_SCALE_MAX_COMPOSITE / systemFontScale))
        }

        const val DESTROY_OFF = "off"
        const val DESTROY_PIN = "destroy_pin"
        const val DESTROY_FAIL_COUNT = "fail_count"

        /**
         * Hidden-feature id (developer mode → hide settings entries) for the
         * Android integrity detection entry. Hiding it hides the settings row
         * AND keeps the automatic startup scan off — the user asked for the
         * feature to be gone, not for it to keep running unseen.
         */
        const val HIDDEN_FEATURE_INTEGRITY = "integrity"

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
        /** Legacy step id from the 2.6.2 test builds; read-only fallback. */
        val FONT_SIZE = stringPreferencesKey("font_size")
        val FONT_SCALE_PERCENT = intPreferencesKey("font_scale_percent")
        val FONT_SCALE_ENABLED = booleanPreferencesKey("font_scale_enabled")
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
        val AUTO_BACKUP_LAST_ATTEMPT = longPreferencesKey("auto_backup_last_attempt")
        val AUTO_BACKUP_NOTIFY = booleanPreferencesKey("auto_backup_notify")
        val AUTO_BACKUP_CONFIG_CHANGED = longPreferencesKey("auto_backup_config_changed")
        val AUTO_BACKUP_NOTIFY_AUTH = booleanPreferencesKey("auto_backup_notify_auth")
        val AUTO_CHECK_UPDATES = booleanPreferencesKey("auto_check_updates")
        val CHECK_PREVIEW_UPDATES = booleanPreferencesKey("check_preview_updates")
        val TAGS_ENABLED = booleanPreferencesKey("tags_enabled")
        val DEV_MODE_ENABLED = booleanPreferencesKey("dev_mode_enabled")
        val DEV_DISABLE_ROOT_SECURITY = booleanPreferencesKey("dev_disable_root_security")
        val DEV_PLAINTEXT_EXPORT = booleanPreferencesKey("dev_plaintext_export")

        val DEV_HIDDEN_FEATURES = stringSetPreferencesKey("dev_hidden_features")
        val DEV_DETAILED_LOGGING = booleanPreferencesKey("dev_detailed_logging")
        val PIN_REMINDER_SILENCED = booleanPreferencesKey("pin_reminder_silenced")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[Keys.THEME_MODE] ?: AppSettings.THEME_SYSTEM,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
            fontScalePercent = prefs[Keys.FONT_SCALE_PERCENT]
                ?: prefs[Keys.FONT_SIZE]?.let { AppSettings.fontScalePercentOfLegacyStep(it) }
                ?: AppSettings.FONT_SCALE_PERCENT_DEFAULT,
            fontScaleEnabled = prefs[Keys.FONT_SCALE_ENABLED] ?: false,
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
            autoBackupLastAttempt = prefs[Keys.AUTO_BACKUP_LAST_ATTEMPT] ?: 0L,
            autoBackupNotify = prefs[Keys.AUTO_BACKUP_NOTIFY] ?: true,
            autoBackupConfigChangedAt = prefs[Keys.AUTO_BACKUP_CONFIG_CHANGED] ?: 0L,
            autoBackupNotifyRequireAuth = prefs[Keys.AUTO_BACKUP_NOTIFY_AUTH] ?: true,
            autoCheckUpdates = prefs[Keys.AUTO_CHECK_UPDATES] ?: false,
            checkPreviewUpdates = prefs[Keys.CHECK_PREVIEW_UPDATES] ?: false,
            tagsEnabled = prefs[Keys.TAGS_ENABLED] ?: true,
            devModeEnabled = prefs[Keys.DEV_MODE_ENABLED] ?: false,
            devDisableRootSecurity = prefs[Keys.DEV_DISABLE_ROOT_SECURITY] ?: false,
            devPlaintextExport = prefs[Keys.DEV_PLAINTEXT_EXPORT] ?: false,

            devHiddenFeatures = prefs[Keys.DEV_HIDDEN_FEATURES] ?: emptySet(),
            devDetailedLogging = prefs[Keys.DEV_DETAILED_LOGGING] ?: false,
            pinReminderSilenced = prefs[Keys.PIN_REMINDER_SILENCED] ?: false
        )
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    /** Off restores Osmium's own default typography and locks the slider. */
    suspend fun setFontScaleEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.FONT_SCALE_ENABLED] = enabled }
    }

    /** Out-of-range values are clamped, not stored. */
    suspend fun setFontScalePercent(percent: Int) {
        context.dataStore.edit {
            it[Keys.FONT_SCALE_PERCENT] =
                percent.coerceIn(
                    AppSettings.FONT_SCALE_PERCENT_MIN,
                    AppSettings.FONT_SCALE_PERCENT_MAX
                )
        }
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
        context.dataStore.edit {
            it[Keys.AUTO_BACKUP_ENABLED] = enabled
            it[Keys.AUTO_BACKUP_CONFIG_CHANGED] = System.currentTimeMillis()
        }
    }

    suspend fun setAutoBackupTarget(target: String) {
        context.dataStore.edit {
            it[Keys.AUTO_BACKUP_TARGET] = target
            it[Keys.AUTO_BACKUP_CONFIG_CHANGED] = System.currentTimeMillis()
        }
    }

    suspend fun setAutoBackupIntervalDays(days: Int) {
        context.dataStore.edit {
            it[Keys.AUTO_BACKUP_INTERVAL] = days.coerceIn(1, 365)
            it[Keys.AUTO_BACKUP_CONFIG_CHANGED] = System.currentTimeMillis()
        }
    }

    suspend fun setAutoBackupTime(hour: Int, minute: Int) {
        context.dataStore.edit {
            it[Keys.AUTO_BACKUP_HOUR] = hour.coerceIn(0, 23)
            it[Keys.AUTO_BACKUP_MINUTE] = minute.coerceIn(0, 59)
            it[Keys.AUTO_BACKUP_CONFIG_CHANGED] = System.currentTimeMillis()
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

    suspend fun setAutoBackupNotify(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_BACKUP_NOTIFY] = enabled }
    }

    /** Identity check before the page is opened from a backup notification. */
    suspend fun setAutoBackupNotifyRequireAuth(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_BACKUP_NOTIFY_AUTH] = enabled }
    }

    /** Mark that a run is starting, before any work happens. */
    suspend fun setAutoBackupAttempt(time: Long) {
        context.dataStore.edit { it[Keys.AUTO_BACKUP_LAST_ATTEMPT] = time }
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

    /**
     * Preview channel: also offer pre-releases. Independent of the stable
     * switch — a user may want betas only, or stable only, or both.
     */
    suspend fun setCheckPreviewUpdates(enabled: Boolean) {
        context.dataStore.edit { it[Keys.CHECK_PREVIEW_UPDATES] = enabled }
    }

    suspend fun setTagsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.TAGS_ENABLED] = enabled }
    }

    // -------------------------------------- root hardening / developer mode (2.4.2)

    suspend fun setDevModeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DEV_MODE_ENABLED] = enabled }
    }

    suspend fun setDevDisableRootSecurity(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DEV_DISABLE_ROOT_SECURITY] = enabled }
    }

    suspend fun setDevPlaintextExport(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DEV_PLAINTEXT_EXPORT] = enabled }
    }


    suspend fun setDevHiddenFeatures(ids: Set<String>) {
        context.dataStore.edit { it[Keys.DEV_HIDDEN_FEATURES] = ids }
    }

    suspend fun setPinReminderSilenced(silenced: Boolean) {
        context.dataStore.edit { it[Keys.PIN_REMINDER_SILENCED] = silenced }
    }

    suspend fun setDevDetailedLogging(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DEV_DETAILED_LOGGING] = enabled }
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

    /**
     * Developer-mode maintenance: re-encrypt the stored WebDAV / auto-backup
     * passwords with fresh IVs. The DataStore edit is atomic — on failure
     * nothing is written. Returns the number of fields re-encrypted.
     */
    suspend fun reencryptSecretFields(): Int {
        var touched = 0
        context.dataStore.edit { prefs ->
            for ((ivKey, ctKey) in listOf(
                Keys.WEBDAV_PASS_IV to Keys.WEBDAV_PASS_CT,
                Keys.AUTO_BACKUP_PASS_IV to Keys.AUTO_BACKUP_PASS_CT
            )) {
                val iv = prefs[ivKey] ?: continue
                val ct = prefs[ctKey] ?: continue
                val plain = crypto.decrypt(CryptoManager.EncryptedField(iv, ct))
                val fresh = crypto.encrypt(plain)
                prefs[ivKey] = fresh.iv
                prefs[ctKey] = fresh.ciphertext
                touched++
            }
        }
        return touched
    }

    suspend fun wipeSettings() {
        context.dataStore.edit { it.clear() }
    }
}
