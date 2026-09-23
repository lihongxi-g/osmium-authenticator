package com.safekey.authenticator.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/**
 * Anti-repackaging: compares the APK signing certificate SHA-256 against the
 * official fingerprint baked in at build time. A re-signed (tampered) APK
 * fails immediately and the app refuses to run.
 *
 * Zero network, zero servers — works offline by design.
 */
object IntegrityCheck {

    /** Official signing certificate SHA-256 (from keytool, uppercase, no colons). */
    private const val OFFICIAL_SHA256 =
        "B65BB0131CAA22C45D99EA4E2C3E99B3980EAE0DC5647190F41A2878E6D88412"

    /** Outcome of the signature comparison (see [signatureVerdict]). */
    enum class SignatureVerdict { MATCH, MISMATCH, UNAVAILABLE }

    /**
     * App-start gate: fails closed — an unreadable signature blocks the app
     * rather than showing an unverified build.
     */
    fun isTampered(context: Context): Boolean {
        return try {
            val cert = signingCertificate(context) ?: return true
            val digest = MessageDigest.getInstance("SHA-256").digest(cert)
            val hex = digest.joinToString("") { "%02X".format(it) }
            hex != OFFICIAL_SHA256
        } catch (_: Exception) {
            true
        }
    }

    /**
     * Same comparison, but distinguishes "could not look" from "looked and it
     * differs". Integrity reports must not call a transient PackageManager or
     * provider failure hard tamper evidence — the start-up gate above already
     * refuses to run a mismatching build.
     */
    fun signatureVerdict(context: Context): SignatureVerdict {
        return try {
            val cert = signingCertificate(context) ?: return SignatureVerdict.UNAVAILABLE
            val digest = MessageDigest.getInstance("SHA-256").digest(cert)
            val hex = digest.joinToString("") { "%02X".format(it) }
            if (hex == OFFICIAL_SHA256) SignatureVerdict.MATCH else SignatureVerdict.MISMATCH
        } catch (_: Exception) {
            SignatureVerdict.UNAVAILABLE
        }
    }

    private fun signingCertificate(context: Context): ByteArray? {
        val pm = context.packageManager
        val packageName = context.packageName
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val signers = info.signingInfo?.apkContentsSigners
            signers?.firstOrNull()?.toByteArray()
        } else {
            @Suppress("DEPRECATION")
            val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            @Suppress("DEPRECATION")
            info.signatures?.firstOrNull()?.toByteArray()
        }
    }
}
