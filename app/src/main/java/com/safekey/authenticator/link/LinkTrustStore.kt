package com.safekey.authenticator.link

import android.content.Context

/** Stores only remote public-key fingerprints; no account or secret data. */
class LinkTrustStore(context: Context) {
    private val prefs = context.getSharedPreferences("osmium_link_trust", Context.MODE_PRIVATE)
    fun isTrusted(fingerprint: String): Boolean = fingerprints().contains(fingerprint)
    fun setTrusted(fingerprint: String, trusted: Boolean) {
        val next = fingerprints().toMutableSet().apply { if (trusted) add(fingerprint) else remove(fingerprint) }
        prefs.edit().putStringSet("fingerprints", next).apply()
    }
    fun all(): Set<String> = fingerprints()
    private fun fingerprints(): Set<String> = prefs.getStringSet("fingerprints", emptySet()).orEmpty()
}
