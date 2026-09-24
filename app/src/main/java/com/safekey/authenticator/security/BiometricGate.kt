package com.safekey.authenticator.security

/**
 * What the device can offer for biometric authentication *right now*.
 *
 * The distinction matters: an enrolled fingerprint that is temporarily
 * unusable (sensor busy, a lockout after too many failed attempts, a pending
 * security update) is NOT the same as a device that has no biometric
 * credential at all. Treating the two as one let the "verify on open" gate
 * fail open — the app unlocked itself whenever the sensor was unavailable
 * (reported by an F-Droid reviewer, 2026-09-24).
 */
enum class BiometricState {
    /** Hardware present, credential enrolled and usable: ask for it. */
    AVAILABLE,

    /**
     * No biometric hardware, or nothing enrolled: there is no biometric
     * credential this device could ever ask for.
     */
    NO_CREDENTIAL,

    /**
     * Hardware and/or enrollment exist, but authentication is impossible at
     * the moment. Must never be treated as authenticated, and must never be
     * collapsed into [NO_CREDENTIAL].
     */
    UNAVAILABLE
}

/** What the "verify on open" gate must do in a given situation. */
enum class GateAction {
    /** No gate: show the app. */
    UNLOCK,

    /** Gate with the biometric prompt. */
    LOCK_BIOMETRIC,

    /** Gate with the app PIN (biometrics unusable/absent, but a PIN exists). */
    REQUIRE_PIN,

    /**
     * Stay locked with no credential available. The lock screen keeps
     * offering the system lock-screen credential and re-checks the sensor, so
     * this clears as soon as biometrics come back.
     */
    STAY_LOCKED
}

/**
 * The single decision point for the enter-app gate.
 *
 * Kept as a pure function so every combination is unit-tested
 * ([BiometricGateTest]) — the fail-open bug lived in an if-chain that no test
 * covered, and this is what replaces it.
 *
 * Rules:
 *  - gate off → unlocked;
 *  - biometrics usable → biometric lock;
 *  - biometrics present but unusable → PIN if one is set, otherwise stay
 *    locked (never unlock);
 *  - no biometric credential at all → PIN if one is set, otherwise unlock,
 *    which is the documented behaviour for devices without any biometrics
 *    (the app only suggests setting a PIN in that case).
 */
fun gateAction(
    gateOnOpen: Boolean,
    biometric: BiometricState,
    hasPin: Boolean
): GateAction = when {
    !gateOnOpen -> GateAction.UNLOCK
    biometric == BiometricState.AVAILABLE -> GateAction.LOCK_BIOMETRIC
    biometric == BiometricState.UNAVAILABLE ->
        if (hasPin) GateAction.REQUIRE_PIN else GateAction.STAY_LOCKED
    hasPin -> GateAction.REQUIRE_PIN
    else -> GateAction.UNLOCK
}
