package com.safekey.authenticator.totp.importer

import com.safekey.authenticator.model.Account
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Stratum / Authenticator Pro exports: CAPITALIZED keys, numeric type and
 * algorithm enums (Type 1=HOTP 2=TOTP 4=Steam; Algorithm 0=SHA1 1=SHA256
 * 2=SHA512).
 */
class StratumImporterTest {

    private fun fixture(name: String): String {
        val stream = checkNotNull(javaClass.getResourceAsStream(FIXTURE_DIR + name)) { "missing $name" }
        return stream.readBytes().toString(Charsets.UTF_8)
    }

    @Test
    fun `maps numeric type and algorithm enums`() {
        val rows = StratumImporter.parse(fixture("stratum.json"))
        assertEquals(5, rows.size)

        assertEquals("GitHub", rows[0].issuer)
        assertEquals("octocat", rows[0].label)
        assertEquals(Account.TYPE_TOTP, rows[0].type)
        assertEquals("SHA1", rows[0].algorithm)

        assertEquals(Account.TYPE_HOTP, rows[1].type)
        assertEquals("SHA256", rows[1].algorithm)
        assertEquals(12L, rows[1].counter)

        // Type 4 = Steam -> Osmium's Steam handling
        assertEquals("Steam", rows[2].issuer)
        assertEquals(Account.TYPE_TOTP, rows[2].type)
        assertNull(ImportSupport.issue(rows[2]))
    }

    @Test
    fun `unknown type and md5 algorithm are flagged not guessed`() {
        val rows = StratumImporter.parse(fixture("stratum.json"))
        assertEquals(EntryIssue.UNSUPPORTED_TYPE, ImportSupport.issue(rows[3]))
        assertEquals(EntryIssue.UNSUPPORTED_ALGORITHM, ImportSupport.issue(rows[4]))
    }

    private companion object {
        const val FIXTURE_DIR = "/com/safekey/authenticator/totp/importer/"
    }
}
