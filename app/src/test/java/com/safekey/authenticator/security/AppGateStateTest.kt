package com.safekey.authenticator.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppGateStateTest {
    @Test
    fun beforeFirstDecisionNeitherGateNorAccountsAreReady() {
        val starting = AppGateState()
        assertFalse(starting.ready)
        assertFalse(starting.isOpen)
        assertFalse(starting.copy(locked = false, pinRequired = false).isOpen)
    }

    @Test
    fun disabledVerificationMovesStraightFromWaitingToOpen() {
        for (biometric in BiometricState.entries) {
            for (hasPin in listOf(false, true)) {
                val resolved = AppGateState().applyDecision(
                    gateAction(gateOnOpen = false, biometric = biometric, hasPin = hasPin)
                )
                assertTrue(resolved.ready)
                assertTrue(resolved.isOpen)
            }
        }
    }

    @Test
    fun enabledVerificationWithBiometricsKeepsTheGateClosedEvenWhenTheSensorIsBusy() {
        for (biometric in listOf(BiometricState.AVAILABLE, BiometricState.UNAVAILABLE)) {
            val resolved = AppGateState().applyDecision(
                gateAction(gateOnOpen = true, biometric = biometric, hasPin = false)
            )
            assertTrue(resolved.ready)
            assertTrue(resolved.locked)
            assertFalse(resolved.pinRequired)
            assertFalse(resolved.isOpen)
        }
    }

    @Test
    fun enabledVerificationWithPinFallbackKeepsTheGateClosed() {
        val resolved = AppGateState().applyDecision(
            gateAction(gateOnOpen = true, biometric = BiometricState.UNAVAILABLE, hasPin = true)
        )
        assertTrue(resolved.ready)
        assertTrue(resolved.pinRequired)
        assertFalse(resolved.isOpen)
        assertTrue(resolved.applyDecision(GateAction.UNLOCK).isOpen)
    }

    @Test
    fun aLaterIntegrityOverrideCanLockAnInitiallyOpenApp() {
        val open = AppGateState().applyDecision(GateAction.UNLOCK)
        val restricted = open.applyDecision(
            gateAction(gateOnOpen = true, biometric = BiometricState.AVAILABLE, hasPin = false)
        )
        assertTrue(restricted.ready)
        assertTrue(restricted.locked)
        assertFalse(restricted.isOpen)
    }
}