package com.safekey.authenticator.security

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The gate decision table. Every combination must be spelled out: the
 * fail-open report (F-Droid reviewer, 2026-09-24) was a case that no test
 * covered — "biometrics temporarily unusable + no app PIN" unlocked the app.
 */
class BiometricGateTest {

    private fun action(state: BiometricState, hasPin: Boolean, gate: Boolean = true) =
        gateAction(gateOnOpen = gate, biometric = state, hasPin = hasPin)

    @Test
    fun gateOff_neverLocks() {
        assertEquals(GateAction.UNLOCK, action(BiometricState.AVAILABLE, hasPin = true, gate = false))
        assertEquals(GateAction.UNLOCK, action(BiometricState.UNAVAILABLE, hasPin = true, gate = false))
        assertEquals(GateAction.UNLOCK, action(BiometricState.NO_CREDENTIAL, hasPin = true, gate = false))
    }

    @Test
    fun usableBiometrics_alwaysUseTheBiometricLock() {
        assertEquals(GateAction.LOCK_BIOMETRIC, action(BiometricState.AVAILABLE, hasPin = false))
        assertEquals(GateAction.LOCK_BIOMETRIC, action(BiometricState.AVAILABLE, hasPin = true))
    }

    @Test
    fun temporarilyUnavailableBiometrics_withPin_fallBackToThePin() {
        assertEquals(GateAction.REQUIRE_PIN, action(BiometricState.UNAVAILABLE, hasPin = true))
    }

    @Test
    fun temporarilyUnavailableBiometrics_withoutPin_stayLocked() {
        // The regression: this used to return UNLOCK, so a device with an
        // enrolled-but-busy sensor (or a temporary lockout) showed the account
        // list and live codes without any authentication.
        assertEquals(GateAction.STAY_LOCKED, action(BiometricState.UNAVAILABLE, hasPin = false))
    }

    @Test
    fun noBiometricCredential_withPin_askForThePin() {
        assertEquals(GateAction.REQUIRE_PIN, action(BiometricState.NO_CREDENTIAL, hasPin = true))
    }

    @Test
    fun noBiometricCredential_withoutPin_unlocks() {
        // Documented behaviour for devices without biometrics: the app only
        // suggests setting a PIN instead of blocking entry.
        assertEquals(GateAction.UNLOCK, action(BiometricState.NO_CREDENTIAL, hasPin = false))
    }

    @Test
    fun onlyAUsableCredentialEverUnlocksTheApp() {
        val unlocked = BiometricState.entries.flatMap { state ->
            listOf(false, true).map { pin -> state to pin }
        }.filter { (state, pin) -> action(state, pin) == GateAction.UNLOCK }
        assertEquals(
            setOf(
                BiometricState.NO_CREDENTIAL to false
            ),
            unlocked.toSet()
        )
    }

    // ------------------------------------------------------- code -> state

    /**
     * The other half of the fail-open bug: the activity used to decide
     * "no biometrics" from a single boolean. Only no-hardware/no-enrollment may
     * mean there is no credential — every other code means "cannot use it right
     * now" and must keep the gate closed.
     */
    @Test
    fun onlyNoHardwareOrNoEnrollmentMeansNoCredential() {
        assertEquals(
            BiometricState.NO_CREDENTIAL,
            classifyBiometricCode(BIOMETRIC_ERROR_NONE_ENROLLED /* 11 */)
        )
        assertEquals(
            BiometricState.NO_CREDENTIAL,
            classifyBiometricCode(BIOMETRIC_ERROR_NO_HARDWARE /* 12 */)
        )
    }

    @Test
    fun usableBiometrics_mapToAvailable() {
        assertEquals(BiometricState.AVAILABLE, classifyBiometricCode(BIOMETRIC_SUCCESS /* 0 */))
    }

    @Test
    fun everyOtherFailureMapsToUnavailable() {
        // BIOMETRIC_ERROR_HW_UNAVAILABLE (1) is what a busy sensor and a
        // temporary lockout report — the exact state the reviewer reproduced.
        assertEquals(BiometricState.UNAVAILABLE, classifyBiometricCode(BIOMETRIC_ERROR_HW_UNAVAILABLE))
        // pending security update, status unknown, unsupported
        assertEquals(BiometricState.UNAVAILABLE, classifyBiometricCode(BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED))
        assertEquals(BiometricState.UNAVAILABLE, classifyBiometricCode(BIOMETRIC_ERROR_STATUS_UNKNOWN))
        assertEquals(BiometricState.UNAVAILABLE, classifyBiometricCode(BIOMETRIC_ERROR_UNSUPPORTED))
    }

    @Test
    fun everyCredentialLikeFailureKeepsTheGateClosed() {
        // Cross-check the two halves: a code that maps to UNAVAILABLE must never
        // produce UNLOCK, with or without a PIN.
        val unavailableCodes = listOf(
            BIOMETRIC_ERROR_HW_UNAVAILABLE,
            BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED,
            BIOMETRIC_ERROR_STATUS_UNKNOWN,
            BIOMETRIC_ERROR_UNSUPPORTED
        )
        unavailableCodes.forEach { code ->
            val state = classifyBiometricCode(code)
            assertEquals(BiometricState.UNAVAILABLE, state)
            assertEquals(GateAction.STAY_LOCKED, action(state, hasPin = false))
        }
    }

    private companion object {
        // androidx.biometric values, spelled out so the mapping is pinned to the
        // real numbers rather than to whatever the library constant becomes.
        const val BIOMETRIC_SUCCESS = 0
        const val BIOMETRIC_ERROR_HW_UNAVAILABLE = 1
        const val BIOMETRIC_ERROR_NONE_ENROLLED = 11
        const val BIOMETRIC_ERROR_NO_HARDWARE = 12
        const val BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED = 15
        const val BIOMETRIC_ERROR_STATUS_UNKNOWN = -1
        const val BIOMETRIC_ERROR_UNSUPPORTED = -2
    }
}
