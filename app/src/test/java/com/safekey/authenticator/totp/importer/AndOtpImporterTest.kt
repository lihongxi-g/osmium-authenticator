package com.safekey.authenticator.totp.importer

import com.safekey.authenticator.model.Account
import com.safekey.authenticator.model.VaultAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * andOTP plaintext backups. The fixture is a real export (kept in the repo since
 * the first andOTP parser, restored for the widened migration set).
 */
class AndOtpImporterTest {

    private fun fixture(name: String): String {
        val stream = checkNotNull(javaClass.getResourceAsStream(FIXTURE_DIR + name)) { "missing $name" }
        return stream.readBytes().toString(Charsets.UTF_8)
    }

    @Test
    fun `parses every entry of the real andOTP fixture`() {
        val rows = AndOtpImporter.parse(fixture("andotp_plain.json"))
        assertEquals(7, rows.size)
        assertEquals(
            VaultAccount(
                issuer = "Deno", label = "Mason",
                secret = "4SJHB4GSD43FZBAI7C2HLRJGPQ",
                algorithm = "SHA1", digits = 6, period = 30
            ),
            rows[0]
        )
        // unsupported-by-Osmium rows are still parsed, then flagged by ImportSupport
        assertEquals("SPDX", rows[1].issuer)
        assertEquals("SHA256", rows[1].algorithm)
        assertEquals(7, rows[1].digits)
        assertEquals(20, rows[1].period)
        assertEquals(EntryIssue.UNSUPPORTED_DIGITS, ImportSupport.issue(rows[1]))

        // HOTP keeps its counter, which the merge logic must never rewind
        assertEquals(Account.TYPE_HOTP, rows[3].type)
        assertEquals(1L, rows[3].counter)
        assertEquals(50L, rows[4].counter)
        assertEquals(10300L, rows[5].counter)

        // STEAM entries become TOTP rows with issuer Steam
        assertEquals("Steam", rows[6].issuer)
        assertEquals(Account.TYPE_TOTP, rows[6].type)
        assertNull(ImportSupport.issue(rows[6]))
    }

    @Test
    fun `padding in the andOTP secret is normalized away`() {
        val rows = AndOtpImporter.parse(fixture("andotp_plain.json"))
        assertTrue(rows.none { "=" in it.secret })
    }

    @Test
    fun `old backups without issuer split the label on the dash`() {
        val rows = AndOtpImporter.parse(
            """[{"type":"TOTP","secret":"GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ","label":"GitHub - octocat","digits":6,"period":30,"algorithm":"SHA1","counter":0}]"""
        )
        assertEquals("GitHub", rows[0].issuer)
        assertEquals("octocat", rows[0].label)
    }

    @Test
    fun `an empty array is reported as an empty export`() {
        val error = assertThrows(ImporterException::class.java) { AndOtpImporter.parse("[]") }
        assertEquals(ImporterError.EMPTY, error.error)
    }

    private companion object {
        const val FIXTURE_DIR = "/com/safekey/authenticator/totp/importer/"
    }
}
