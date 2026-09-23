package com.safekey.authenticator.integrity.attestation

import com.safekey.authenticator.integrity.IntegrityCheck
import com.safekey.authenticator.integrity.IntegritySeverity

/**
 * K3 (Android Key Attestation) outcome model and its mapping onto engine
 * checks. This file is pure logic — the AndroidKeyStore / JCA work lives in
 * [AttestationProbe], so every rule here is unit-tested on the JVM.
 *
 * Check ids produced:
 *  - "attestation": the main hardware-proof verdict.
 *  - "attestation_boot_hash": only fires when the runtime vbmeta digest
 *    contradicts the hash carried inside the attestation chain.
 */
internal sealed interface AttestationOutcome {

    /** This device/run cannot produce a hardware-backed attestation chain. */
    data object Unsupported : AttestationOutcome

    /** Chain verified against Google roots; boot state VERIFIED, device locked. */
    data class Verified(val info: AttestationInfo) : AttestationOutcome

    /** Chain verified; boot state UNVERIFIED (bootloader unlocked). */
    data class BootUnverified(val info: AttestationInfo) : AttestationOutcome

    /** Chain verified; boot state SELF_SIGNED (custom keys). */
    data class BootSelfSigned(val info: AttestationInfo) : AttestationOutcome

    /** Chain verified; boot state FAILED. */
    data class BootFailed(val info: AttestationInfo) : AttestationOutcome

    /** Software security level or software root: no hardware proof. */
    data class SoftwareOnly(val detail: String) : AttestationOutcome

    /** The attested challenge does not match the one we sent (replay / hook). */
    data object ChallengeMismatch : AttestationOutcome

    /** Chain does not validate to a Google trust anchor. */
    data class ChainRejected(val detail: String) : AttestationOutcome

    /** Chain present but unreadable (parse / ASN.1 / tag errors). */
    data class ChainUnreadable(val detail: String) : AttestationOutcome

    /** A structural constraint failed (origin, root of trust, ...). */
    data class ConstraintFailed(val label: String) : AttestationOutcome

    /** The probe itself failed (keystore error, unexpected exception). */
    data class ProbeError(val detail: String) : AttestationOutcome
}

/** Values captured from a successfully validated chain. */
internal data class AttestationInfo(
    /** "strongbox" | "tee" */
    val securityLevel: String,
    val deviceLocked: Boolean,
    /** true/false when the runtime vbmeta digest was comparable, else null. */
    val bootHashMatch: Boolean?,
)

/** Pure outcome -> checks mapping (unit-tested; no Android dependencies). */
internal object AttestationMapping {

    const val CHECK_ID = "attestation"
    const val BOOT_HASH_CHECK_ID = "attestation_boot_hash"

    fun toChecks(outcome: AttestationOutcome): List<IntegrityCheck> = when (outcome) {
        is AttestationOutcome.Verified ->
            chainChecks(IntegritySeverity.PASS, "verified boot", outcome.info)
        is AttestationOutcome.BootUnverified ->
            chainChecks(IntegritySeverity.INFO, "boot state unverified (bootloader unlocked)", outcome.info)
        is AttestationOutcome.BootSelfSigned ->
            chainChecks(IntegritySeverity.INFO, "boot state self-signed", outcome.info)
        is AttestationOutcome.BootFailed ->
            chainChecks(IntegritySeverity.WARN, "boot state FAILED", outcome.info)
        is AttestationOutcome.SoftwareOnly ->
            listOf(
                IntegrityCheck(
                    CHECK_ID, IntegritySeverity.INFO, false,
                    "software attestation only (no hardware proof)" + suffix(outcome.detail)
                )
            )
        AttestationOutcome.ChallengeMismatch ->
            listOf(
                IntegrityCheck(
                    CHECK_ID, IntegritySeverity.FAIL, true,
                    "attested challenge mismatch (replayed or faked chain)"
                )
            )
        is AttestationOutcome.ChainRejected ->
            // Weak evidence, not a verdict: the only fact observed is that the
            // chain does not validate against the pinned anchors — true on
            // non-GMS devices with vendor roots, on emulators, or after Google
            // rotates a root key. Scoring this FAIL made unrooted devices
            // "COMPROMISED" and forced the root hardening on them. Hard evidence
            // stays reserved for contradictions that positively indicate
            // tampering (challenge mismatch, boot-hash mismatch).
            listOf(
                IntegrityCheck(
                    CHECK_ID, IntegritySeverity.WARN, true,
                    "chain not validated against the pinned anchors: " +
                        outcome.detail.take(160)
                )
            )
        is AttestationOutcome.ChainUnreadable ->
            listOf(
                IntegrityCheck(
                    CHECK_ID, IntegritySeverity.WARN, true,
                    "chain unreadable: " + outcome.detail.take(180)
                )
            )
        is AttestationOutcome.ConstraintFailed ->
            listOf(
                IntegrityCheck(
                    CHECK_ID, IntegritySeverity.WARN, true,
                    "constraint violated: " + outcome.label.take(120)
                )
            )
        is AttestationOutcome.ProbeError ->
            listOf(
                IntegrityCheck(
                    CHECK_ID, IntegritySeverity.INFO, false,
                    "probe unavailable (" + outcome.detail.take(120) + ")"
                )
            )
        AttestationOutcome.Unsupported ->
            listOf(
                IntegrityCheck(
                    CHECK_ID, IntegritySeverity.INFO, false,
                    "no hardware attestation on this device"
                )
            )
    }

    private fun chainChecks(
        severity: IntegritySeverity,
        label: String,
        info: AttestationInfo,
    ): List<IntegrityCheck> {
        val detail = buildString {
            append(info.securityLevel)
            append(" · ")
            append(label)
            if (!info.deviceLocked) append(" · device not locked")
            when (info.bootHashMatch) {
                true -> append(" · boot hash match")
                false -> append(" · boot hash MISMATCH")
                null -> Unit
            }
        }
        val main = IntegrityCheck(CHECK_ID, severity, true, detail)
        val hashCheck =
            if (info.bootHashMatch == false) {
                IntegrityCheck(
                    BOOT_HASH_CHECK_ID, IntegritySeverity.FAIL, true,
                    "runtime vbmeta digest differs from the attested boot hash"
                )
            } else {
                null
            }
        return listOfNotNull(main, hashCheck)
    }

    private fun suffix(detail: String): String =
        if (detail.isBlank()) "" else ": " + detail.take(120)
}
