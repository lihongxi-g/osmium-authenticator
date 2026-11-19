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
    val password: String = "",
    /**
     * SHA-256 of the DER certificate the user explicitly trusted for this
     * server, as lowercase hex without separators. Empty means "validate with
     * the platform trust anchors", i.e. no pin.
     *
     * A pin only ever applies to this address: the certificate is compared
     * after the TLS handshake and before the request (and its credentials) is
     * written, so a substituted certificate transfers nothing.
     */
    val trustedCertSha256: String = ""
) {
    val isComplete: Boolean get() = baseUrl.isNotBlank()
}
