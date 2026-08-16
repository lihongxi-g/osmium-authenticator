package com.safekey.authenticator.data

/**
 * User-configured WebDAV backup server (local network).
 *
 * [password] is held in memory as plaintext only during a session; it is
 * persisted encrypted with the Android Keystore key (see SettingsRepository).
 */
data class WebDavServerConfig(
    val baseUrl: String = "",
    val username: String = "",
    val password: String = ""
) {
    val isComplete: Boolean get() = baseUrl.isNotBlank()
}
