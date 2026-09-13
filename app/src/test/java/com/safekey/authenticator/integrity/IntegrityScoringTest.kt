package com.safekey.authenticator.integrity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntegrityScoringTest {

    private fun check(id: String, severity: IntegritySeverity, hit: Boolean) =
        IntegrityCheck(id, severity, hit)

    @Test
    fun `all misses score clean`() {
        val checks = listOf(
            check("manager_packages", IntegritySeverity.FAIL, false),
            check("selinux_state", IntegritySeverity.WARN, false),
            check("boot_props", IntegritySeverity.INFO, false)
        )
        assertEquals(IntegrityLevel.CLEAN, IntegrityScoring.levelOf(checks))
    }

    @Test
    fun `hard evidence scores compromised`() {
        val checks = listOf(
            check("manager_packages", IntegritySeverity.FAIL, true),
            check("boot_props", IntegritySeverity.INFO, true)
        )
        assertEquals(IntegrityLevel.COMPROMISED, IntegrityScoring.levelOf(checks))
    }

    @Test
    fun `weak evidence scores suspicious`() {
        val checks = listOf(
            check("manager_packages", IntegritySeverity.FAIL, false),
            check("selinux_state", IntegritySeverity.WARN, true)
        )
        assertEquals(IntegrityLevel.SUSPICIOUS, IntegrityScoring.levelOf(checks))
    }

    @Test
    fun `warn beats boot signals`() {
        val checks = listOf(
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
    fun `fail beats everything`() {
        val checks = listOf(
            check("su_runtime", IntegritySeverity.FAIL, true),
            check("selinux_state", IntegritySeverity.WARN, true),
            check("boot_props", IntegritySeverity.INFO, true)
        )
        assertEquals(IntegrityLevel.COMPROMISED, IntegrityScoring.levelOf(checks))
    }

    @Test
    fun `report compromised flag follows the level`() {
        val compromised = IntegrityReport(
            0L,
            listOf(check("manager_packages", IntegritySeverity.FAIL, true)),
            IntegrityLevel.COMPROMISED
        )
        assertTrue(compromised.compromised)

        val suspicious = IntegrityReport(
            0L,
            listOf(check("selinux_state", IntegritySeverity.WARN, true)),
            IntegrityLevel.SUSPICIOUS
        )
        assertFalse(suspicious.compromised)
    }

    @Test
    fun `hits are ordered strongest first`() {
        val report = IntegrityReport(
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
}
