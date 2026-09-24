package com.safekey.authenticator.security

import androidx.biometric.BiometricManager

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

/**
 * Maps `BiometricManager.canAuthenticate()` to [BiometricState].
 *
 * This is the other half of the fail-open bug and lives here — not in the
 * activity — so it can be unit-tested: only "no hardware" and "nothing
 * enrolled" mean the device has no biometric credential. Every other failure
 * (sensor busy, temporary lockout, pending security update, unknown) means the
 * credential exists and simply cannot be used right now.
 *
 * The constants are inlined `static final int`s, so this stays a pure function
 * with no Android runtime dependency.
 */
fun classifyBiometricCode(code: Int): BiometricState = when (code) {
    BiometricManager.BIOMETRIC_SUCCESS -> BiometricState.AVAILABLE
    BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED,
    BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricState.NO_CREDENTIAL
    else -> BiometricState.UNAVAILABLE
}
