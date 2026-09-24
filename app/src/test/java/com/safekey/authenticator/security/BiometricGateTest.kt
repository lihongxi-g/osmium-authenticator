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
}
