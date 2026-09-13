package com.safekey.authenticator.integrity

/**
 * Device-integrity detection models (2026-09 layered engine).
 *
 * Layers: K1 = local static probes (this release), K2 = cross-source
 * consistency checks and K3 = Android Key Attestation arrive in later
 * slices. Probes never throw; a probe that cannot run is reported as a miss.
 */

/**
 * Weight class of a check — what it means when it fires.
 * [PASS] is reserved for checks that positively confirm a property (K3 era).
 */
enum class IntegritySeverity { PASS, INFO, WARN, FAIL }

/**
 * Overall device-integrity level; the strongest signal wins:
 *
 *  - [COMPROMISED]: hard root evidence fired (FAIL-class check). Drives the
 *    same forced-security behaviour 2.4.2 applied to "rooted".
 *  - [SUSPICIOUS]: weak indicators fired (WARN-class check) — reminder only.
 *  - [UNVERIFIED]: boot/build signals show a modified system but no root
 *    evidence (boot state not green/locked, test-keys, userdebug).
 *  - [CLEAN]: hardware attestation verified (boot state verified) and no
 *    other signals fired.
 *  - [UNKNOWN]: nothing fired and no hardware proof is available (no
 *    attestation support, or the probe could not run) — neutral.
 */
enum class IntegrityLevel { CLEAN, UNVERIFIED, SUSPICIOUS, COMPROMISED, UNKNOWN }

/** Result of one probe. [hit] = the finding fired. */
data class IntegrityCheck(
    val id: String,
    val severity: IntegritySeverity,
    val hit: Boolean,
    val detail: String = ""
)

/** Full scan result; [level] is derived from [checks] by the scoring rules. */
data class IntegrityReport(
    val checkedAt: Long,
    val checks: List<IntegrityCheck>,
    val level: IntegrityLevel
) {
    /** True when hard root evidence fired — the root-hardening trigger. */
    val compromised: Boolean get() = level == IntegrityLevel.COMPROMISED

    /** Fired checks, strongest first (reports, logs). */
    val hits: List<IntegrityCheck>
        get() = checks.filter { it.hit }.sortedByDescending { weight(it.severity) }

    private fun weight(severity: IntegritySeverity): Int = when (severity) {
        IntegritySeverity.FAIL -> 3
        IntegritySeverity.WARN -> 2
        IntegritySeverity.INFO -> 1
        IntegritySeverity.PASS -> 0
    }
}
