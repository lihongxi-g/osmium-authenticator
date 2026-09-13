package com.safekey.authenticator.integrity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntegrityScoringTest {

    private fun check(id: String, severity: IntegritySeverity, hit: Boolean) =
        IntegrityCheck(id, severity, hit)

    private fun attestation(severity: IntegritySeverity, hit: Boolean = true) =
        check("attestation", severity, hit)

    @Test
    fun `a silent scan without hardware proof is unknown, not clean`() {
        val checks =
            listOf(
                check("manager_packages", IntegritySeverity.FAIL, false),
                check("selinux_state", IntegritySeverity.WARN, false),
                check("attestation", IntegritySeverity.INFO, false)
            )
        assertEquals(IntegrityLevel.UNKNOWN, IntegrityScoring.levelOf(checks))
    }

    @Test
    fun `verified attestation alone scores clean`() {
        val checks =
            listOf(
                check("manager_packages", IntegritySeverity.FAIL, false),
                attestation(IntegritySeverity.PASS)
            )
        assertEquals(IntegrityLevel.CLEAN, IntegrityScoring.levelOf(checks))
    }

    @Test
    fun `hard evidence scores compromised`() {
        val checks =
            listOf(
                check("manager_packages", IntegritySeverity.FAIL, true),
                check("boot_props", IntegritySeverity.INFO, true)
            )
        assertEquals(IntegrityLevel.COMPROMISED, IntegrityScoring.levelOf(checks))
    }

    @Test
    fun `weak evidence scores suspicious`() {
        val checks =
            listOf(
                check("manager_packages", IntegritySeverity.FAIL, false),
                check("selinux_state", IntegritySeverity.WARN, true)
            )
        assertEquals(IntegrityLevel.SUSPICIOUS, IntegrityScoring.levelOf(checks))
    }

    @Test
    fun `warn beats boot signals`() {
        val checks =
            listOf(
                check("selinux_state", IntegritySeverity.WARN, true),
                check("boot_props", IntegritySeverity.INFO, true)
            )
        assertEquals(IntegrityLevel.SUSPICIOUS, IntegrityScoring.levelOf(checks))
    }

    @Test
    fun `boot signals alone score unverified`() {
        assertEquals(
            IntegrityLevel.UNVERIFIED,
            IntegrityScoring.levelOf(listOf(check("boot_props", IntegritySeverity.INFO, true)))
        )
        assertEquals(
            IntegrityLevel.UNVERIFIED,
            IntegrityScoring.levelOf(listOf(check("build_tags", IntegritySeverity.INFO, true)))
        )
    }

    @Test
    fun `attestation unlock signal scores unverified`() {
        assertEquals(
            IntegrityLevel.UNVERIFIED,
            IntegrityScoring.levelOf(listOf(attestation(IntegritySeverity.INFO)))
        )
    }

    @Test
    fun `attestation contradiction scores compromised`() {
        assertEquals(
            IntegrityLevel.COMPROMISED,
            IntegrityScoring.levelOf(listOf(attestation(IntegritySeverity.FAIL)))
        )
    }

    @Test
    fun `fail beats everything`() {
        val checks =
            listOf(
                check("su_runtime", IntegritySeverity.FAIL, true),
                check("selinux_state", IntegritySeverity.WARN, true),
                check("boot_props", IntegritySeverity.INFO, true)
            )
        assertEquals(IntegrityLevel.COMPROMISED, IntegrityScoring.levelOf(checks))
    }

    @Test
    fun `report compromised flag follows the level`() {
        val compromised =
            IntegrityReport(
                0L,
                listOf(check("manager_packages", IntegritySeverity.FAIL, true)),
                IntegrityLevel.COMPROMISED
            )
        assertTrue(compromised.compromised)

        val suspicious =
            IntegrityReport(
                0L,
                listOf(check("selinux_state", IntegritySeverity.WARN, true)),
                IntegrityLevel.SUSPICIOUS
            )
        assertFalse(suspicious.compromised)
    }

    @Test
    fun `hits are ordered strongest first`() {
        val report =
            IntegrityReport(
                0L,
                listOf(
                    check("boot_props", IntegritySeverity.INFO, true),
                    check("selinux_state", IntegritySeverity.WARN, true),
                    check("manager_packages", IntegritySeverity.FAIL, true),
                    check("su_binaries", IntegritySeverity.FAIL, false)
                ),
                IntegrityLevel.COMPROMISED
            )
        assertEquals(
            listOf("manager_packages", "selinux_state", "boot_props"),
            report.hits.map { it.id }
        )
    }

    // ------------------------------------------- hardware-authority override

    @Test
    fun `hardware verification demotes weak signals and keeps the score clean`() {
        val checks =
            listOf(
                attestation(IntegritySeverity.PASS),
                check("selinux_state", IntegritySeverity.WARN, true),
                check("file_cross", IntegritySeverity.WARN, true)
            )
        val scored = IntegrityScoring.applyHardwareOverride(checks)
        assertEquals(IntegritySeverity.INFO, scored.first { it.id == "file_cross" }.severity)
        assertEquals(IntegrityLevel.CLEAN, IntegrityScoring.levelOf(scored))
    }

    @Test
    fun `without hardware proof weak signals still score suspicious`() {
        val checks = listOf(check("file_cross", IntegritySeverity.WARN, true))
        val scored = IntegrityScoring.applyHardwareOverride(checks)
        assertEquals(IntegritySeverity.WARN, scored.first { it.id == "file_cross" }.severity)
        assertEquals(IntegrityLevel.SUSPICIOUS, IntegrityScoring.levelOf(scored))
    }

    @Test
    fun `hard evidence is never demoted by hardware verification`() {
        val checks =
            listOf(
                attestation(IntegritySeverity.PASS),
                check("su_runtime", IntegritySeverity.FAIL, true)
            )
        val scored = IntegrityScoring.applyHardwareOverride(checks)
        assertEquals(IntegritySeverity.FAIL, scored.first { it.id == "su_runtime" }.severity)
        assertEquals(IntegrityLevel.COMPROMISED, IntegrityScoring.levelOf(scored))
    }
}
