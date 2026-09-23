package com.safekey.authenticator.integrity.attestation

import com.safekey.authenticator.integrity.IntegritySeverity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttestationMappingTest {

    private fun info(
        level: String = "tee",
        locked: Boolean = true,
        hashMatch: Boolean? = null,
    ) = AttestationInfo(level, locked, hashMatch)

    private fun mainCheck(outcome: AttestationOutcome) =
        AttestationMapping.toChecks(outcome).first { it.id == AttestationMapping.CHECK_ID }

    @Test
    fun `verified boot maps to a passing check`() {
        val checks = AttestationMapping.toChecks(AttestationOutcome.Verified(info()))
        assertEquals(1, checks.size)
        val main = checks.single()
        assertEquals(IntegritySeverity.PASS, main.severity)
        assertTrue(main.hit)
        assertTrue(main.detail.contains("tee"))
        assertTrue(main.detail.contains("verified boot"))
    }

    @Test
    fun `boot hash mismatch adds a failing companion check`() {
        val checks =
            AttestationMapping.toChecks(AttestationOutcome.Verified(info(hashMatch = false)))
        assertEquals(2, checks.size)
        val hash = checks.first { it.id == AttestationMapping.BOOT_HASH_CHECK_ID }
        assertEquals(IntegritySeverity.FAIL, hash.severity)
        assertTrue(hash.hit)
    }

    @Test
    fun `matching boot hash adds no companion check`() {
        val checks =
            AttestationMapping.toChecks(AttestationOutcome.Verified(info(hashMatch = true)))
        assertEquals(1, checks.size)
        assertTrue(checks.single().detail.contains("boot hash match"))
    }

    @Test
    fun `unlocked bootloader is informational`() {
        val main = mainCheck(AttestationOutcome.BootUnverified(info(locked = false)))
        assertEquals(IntegritySeverity.INFO, main.severity)
        assertTrue(main.hit)
    }

    @Test
    fun `self signed boot state is informational`() {
        val main = mainCheck(AttestationOutcome.BootSelfSigned(info()))
        assertEquals(IntegritySeverity.INFO, main.severity)
    }

    @Test
    fun `failed boot state is weak evidence`() {
        val main = mainCheck(AttestationOutcome.BootFailed(info()))
        assertEquals(IntegritySeverity.WARN, main.severity)
    }

    @Test
    fun `challenge mismatch fails the check`() {
        val main = mainCheck(AttestationOutcome.ChallengeMismatch)
        assertEquals(IntegritySeverity.FAIL, main.severity)
        assertTrue(main.hit)
    }

    @Test
    fun `rejected chain is weak evidence, not a verdict`() {
        // An anchor mismatch only says "not in the pinned set" — it happens on
        // non-GMS devices with vendor roots, so it must not score as hard
        // evidence (which forced the root hardening on unrooted devices).
        val main = mainCheck(AttestationOutcome.ChainRejected("no matching trust anchor"))
        assertEquals(IntegritySeverity.WARN, main.severity)
        assertTrue(main.hit)
    }

    @Test
    fun `unreadable chain is weak evidence`() {
        val main = mainCheck(AttestationOutcome.ChainUnreadable("unknown tag number"))
        assertEquals(IntegritySeverity.WARN, main.severity)
    }

    @Test
    fun `constraint violation is weak evidence`() {
        val main = mainCheck(AttestationOutcome.ConstraintFailed("Origin"))
        assertEquals(IntegritySeverity.WARN, main.severity)
    }

    @Test
    fun `software probe failure and unsupported outcomes are neutral misses`() {
        val outcomes =
            listOf<AttestationOutcome>(
                AttestationOutcome.SoftwareOnly("software level"),
                AttestationOutcome.ProbeError("KeystoreException"),
                AttestationOutcome.Unsupported,
            )
        for (outcome in outcomes) {
            val checks = AttestationMapping.toChecks(outcome)
            assertEquals(1, checks.size)
            assertEquals(IntegritySeverity.INFO, checks.single().severity)
            assertFalse(checks.single().hit)
        }
    }

    // ---------------- boot hash normalization ----------------

    @Test
    fun `hex property passes through lowercased`() {
        val hex = "AB".repeat(32)
        assertEquals("ab".repeat(32), BootHash.propToHex(hex))
    }

    @Test
    fun `base64 property decodes to hex`() {
        val bytes = ByteArray(32) { it.toByte() }
        val b64 = java.util.Base64.getEncoder().encodeToString(bytes)
        assertEquals(BootHash.toHex(bytes), BootHash.propToHex(b64))
    }

    @Test
    fun `invalid property values normalize to null`() {
        assertNull(BootHash.propToHex(null))
        assertNull(BootHash.propToHex(""))
        assertNull(BootHash.propToHex("not-a-hash"))
        assertNull(BootHash.propToHex(java.util.Base64.getEncoder().encodeToString(ByteArray(16))))
    }

    @Test
    fun `compare returns null when either side is unavailable`() {
        assertNull(BootHash.compare(null, "ab".repeat(32)))
        assertNull(BootHash.compare("ab".repeat(32), null))
        assertNull(BootHash.compare("garbage", "ab".repeat(32)))
    }

    @Test
    fun `compare normalizes before matching`() {
        val hex = "cd".repeat(32)
        assertEquals(true, BootHash.compare(hex, hex.uppercase()))
    }

    @Test
    fun `compare detects a real mismatch`() {
        assertEquals(false, BootHash.compare("ab".repeat(32), "cd".repeat(32)))
    }
}
