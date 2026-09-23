package com.safekey.authenticator.integrity.attestation

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.android.keyattestation.verifier.ChallengeChecker
import com.android.keyattestation.verifier.ConstraintConfig
import com.android.keyattestation.verifier.GoogleTrustAnchors
import com.android.keyattestation.verifier.IgnoredConstraint
import com.android.keyattestation.verifier.InstantSource
import com.android.keyattestation.verifier.KeyAttestationReason
import com.android.keyattestation.verifier.KeyDescription
import com.android.keyattestation.verifier.LogHook
import com.android.keyattestation.verifier.RootOfTrust
import com.android.keyattestation.verifier.SecurityLevel
import com.android.keyattestation.verifier.VerificationResult
import com.android.keyattestation.verifier.VerifiedBootState
import com.android.keyattestation.verifier.Verifier
import com.android.keyattestation.verifier.VerifyRequestLog
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.protobuf.ByteString
import com.safekey.authenticator.integrity.IntegrityCheck
import com.safekey.authenticator.integrity.IntegrityProbes
import com.safekey.authenticator.security.AppLog
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import kotlinx.coroutines.CancellationException
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.util.Arrays

/**
 * K3 probe: generates an ephemeral AndroidKeyStore key with an attestation
 * challenge, verifies the returned certificate chain against the vendored
 * Google verifier (see keyattestation/NOTICE) and maps the outcome onto
 * engine checks (see [AttestationMapping]).
 *
 * Fully offline: the revocation set comes from the bundled snapshot via
 * [RevocationData]; nothing here touches the network. Never throws — any
 * failure degrades to a neutral miss. The probe key is deleted after every
 * run so no long-lived key material accumulates in the keystore.
 */
internal object AttestationProbe {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    /** Prefix for the per-run probe key alias. */
    private const val KEY_ALIAS_PREFIX = "safekey_integrity_probe_"
    private const val CHALLENGE_BYTES = 32
    private const val BOOT_HASH_PROP = "ro.boot.vbmeta.digest"

    fun probe(context: Context): List<IntegrityCheck> =
        AttestationMapping.toChecks(run(context))

    private fun run(context: Context): AttestationOutcome {
        val challenge = ByteArray(CHALLENGE_BYTES).also { SecureRandom().nextBytes(it) }
        // One alias per run: with a single fixed alias two overlapping scans
        // (the auto-backup worker scans directly while a foreground refresh can
        // be running) delete each other's key between generateKeyPair() and
        // getCertificateChain(), which silently dropped the whole K3 layer.
        val alias = KEY_ALIAS_PREFIX + java.util.UUID.randomUUID().toString()
        return try {
            val chain = generateChain(alias, challenge)
            if (chain == null) {
                AttestationOutcome.Unsupported
            } else {
                verify(context, chain, challenge)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Contract: never throws. Errors (class-loading failures inside the
            // vendored verifier, StackOverflowError while parsing ASN.1) have to
            // degrade to a neutral miss instead of killing the whole scan.
            AppLog.detection("attestation probe error: ${e.javaClass.simpleName}")
            AttestationOutcome.ProbeError(e.javaClass.simpleName)
        } finally {
            deleteKeyQuietly(alias)
        }
    }

    /** Fresh key + certificate chain, or null when this device cannot attest. */
    private fun generateChain(alias: String, challenge: ByteArray): List<X509Certificate>? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(alias)) {
            runCatching { keyStore.deleteEntry(alias) }
        }
        val generator =
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
        generator.initialize(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setAttestationChallenge(challenge)
                .setUserAuthenticationRequired(false)
                .build()
        )
        generator.generateKeyPair()
        val raw = keyStore.getCertificateChain(alias) ?: return null
        return raw.filterIsInstance<X509Certificate>().takeIf { it.isNotEmpty() }
    }

    private fun verify(
        context: Context,
        chain: List<X509Certificate>,
        challenge: ByteArray,
    ): AttestationOutcome {
        val capture = RootOfTrustCapture()
        val result =
            VerifierHolder.get(context).verify(
                chain,
                ChallengeMatchChecker(challenge),
                capture,
            )
        return when (result) {
            is VerificationResult.Success -> fromSuccess(result, capture)
            VerificationResult.ChallengeMismatch -> AttestationOutcome.ChallengeMismatch
            is VerificationResult.PathValidationFailure -> {
                val message = result.cause.message.orEmpty()
                if (message.contains("software root")) {
                    AttestationOutcome.SoftwareOnly("chain terminates in a software root")
                } else {
                    AttestationOutcome.ChainRejected(message.ifEmpty { "no matching trust anchor" })
                }
            }
            is VerificationResult.ChainParsingFailure ->
                AttestationOutcome.ChainUnreadable(
                    result.cause.message ?: result.cause.javaClass.simpleName
                )
            is VerificationResult.ExtensionParsingFailure -> {
                val reason = result.cause.reason
                if (reason == KeyAttestationReason.TARGET_MISSING_ATTESTATION_EXTENSION) {
                    AttestationOutcome.Unsupported
                } else {
                    AttestationOutcome.ChainUnreadable(reason?.name ?: result.cause.message)
                }
            }
            is VerificationResult.ConstraintViolation ->
                AttestationOutcome.ConstraintFailed(result.constraintLabel)
            VerificationResult.SoftwareAttestationUnsupported ->
                AttestationOutcome.SoftwareOnly("software attestation")
        }
    }

    private fun fromSuccess(
        result: VerificationResult.Success,
        capture: RootOfTrustCapture,
    ): AttestationOutcome {
        if (result.securityLevel == SecurityLevel.SOFTWARE) {
            return AttestationOutcome.SoftwareOnly("key security level is software")
        }
        val level =
            when (result.securityLevel) {
                SecurityLevel.STRONG_BOX -> "strongbox"
                SecurityLevel.TRUSTED_ENVIRONMENT -> "tee"
                SecurityLevel.SOFTWARE -> "software"
            }
        val attestedHash =
            capture.rootOfTrust?.verifiedBootHash?.toByteArray()?.let { BootHash.toHex(it) }
        val runtimeProp = runCatching { IntegrityProbes.bootProp(BOOT_HASH_PROP) }.getOrNull()
        val info =
            AttestationInfo(
                securityLevel = level,
                deviceLocked = result.deviceLocked,
                bootHashMatch = BootHash.compare(attestedHash, runtimeProp),
            )
        return when (result.verifiedBootState) {
            VerifiedBootState.VERIFIED ->
                if (result.deviceLocked) {
                    AttestationOutcome.Verified(info)
                } else {
                    AttestationOutcome.BootUnverified(info)
                }
            VerifiedBootState.UNVERIFIED -> AttestationOutcome.BootUnverified(info)
            VerifiedBootState.SELF_SIGNED -> AttestationOutcome.BootSelfSigned(info)
            VerifiedBootState.FAILED -> AttestationOutcome.BootFailed(info)
        }
    }

    private fun deleteKeyQuietly(alias: String) {
        runCatching {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(alias)
        }
    }

    /** The boot hash is not part of the public result; capture it via the log hook. */
    private class RootOfTrustCapture : LogHook {
        @Volatile var rootOfTrust: RootOfTrust? = null

        override fun createRequestLog(): VerifyRequestLog =
            object : VerifyRequestLog {
                override fun logKeyDescription(keyDescription: KeyDescription) {
                    rootOfTrust = keyDescription.hardwareEnforced.rootOfTrust
                }
            }
    }

    /** Exact comparison against the challenge generated for this run. */
    private class ChallengeMatchChecker(private val expected: ByteArray) : ChallengeChecker {
        override fun checkChallenge(challenge: ByteString): ListenableFuture<Boolean> =
            Futures.immediateFuture(Arrays.equals(challenge.toByteArray(), expected))
    }

    /** One verifier per process; anchors parse once and stay cached. */
    private object VerifierHolder {
        @Volatile private var instance: Verifier? = null

        fun get(context: Context): Verifier {
            instance?.let { return it }
            synchronized(this) {
                instance?.let { return it }
                val appContext = context.applicationContext
                val built =
                    Verifier(
                        GoogleTrustAnchors,
                        { RevocationData.current(appContext) },
                        InstantSource { Instant.now() },
                        // Software chains are classified by our own mapping
                        // instead of failing verification on a constraint.
                        ConstraintConfig(
                            allowSoftwareRoot = false,
                            securityLevel = IgnoredConstraint,
                        ),
                    )
                instance = built
                return built
            }
        }
    }
}
