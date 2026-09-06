package com.safekey.authenticator.totp.importer

import com.safekey.authenticator.model.VaultAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LastPassImporterTest {

    private fun fixture(name: String): String {
        val stream = checkNotNull(javaClass.getResourceAsStream(FIXTURE_DIR + name)) { "missing $name" }
        return stream.readBytes().toString(Charsets.UTF_8)
    }

    @Test
    fun `parses accounts and Other Accounts arrays`() {
        val rows = LastPassImporter.parse(fixture("lastpass_accounts.json"))
        assertEquals(3, rows.size)
        assertEquals(
            VaultAccount(
                issuer = "GitHub", label = "octocat",
                secret = "JBSWY3DPEHPK3PXP",
                algorithm = "SHA1", digits = 6, period = 30
            ),
            rows[0]
        )
        // originalIssuerName wins over the edited issuerName
        assertEquals("Cloudflare", rows[1].issuer)
        assertEquals("user@example.com", rows[1].label)
        assertEquals("SHA256", rows[1].algorithm)
        assertEquals(8, rows[1].digits)
        // from "Other Accounts"
        assertEquals("Twitter", rows[2].issuer)
        assertEquals("@alice", rows[2].label)
    }

    @Test
    fun `optional otp parameters default to SHA1 6 30`() {
        val rows = LastPassImporter.parse(
            """{"accounts":[{"accountID":"a","issuerName":"Example","userName":"bob","secret":"JBSWY3DPEHPK3PXP"}]}"""
        )
        assertEquals(1, rows.size)
        assertEquals("Example", rows[0].issuer)
        assertEquals("bob", rows[0].label)
        assertEquals("SHA1", rows[0].algorithm)
        assertEquals(6, rows[0].digits)
        assertEquals(30, rows[0].period)
    }

    @Test
    fun `issuer falls back to issuerName when originalIssuerName is absent`() {
        val rows = LastPassImporter.parse(
            """{"accounts":[{"issuerName":"GitHub","userName":"octocat","secret":"JBSWY3DPEHPK3PXP"}]}"""
        )
        assertEquals("GitHub", rows[0].issuer)
    }

    @Test
    fun `wrong shapes are rejected`() {
        val noArrays = assertThrows(ImporterException::class.java) {
            LastPassImporter.parse("""{"foo":1}""")
        }
        assertEquals(ImporterError.UNRECOGNIZED, noArrays.error)

        val rootArray = assertThrows(ImporterException::class.java) {
            LastPassImporter.parse("""[{"issuerName":"x"}]""")
        }
        assertEquals(ImporterError.UNRECOGNIZED, rootArray.error)

        val empty = assertThrows(ImporterException::class.java) {
            LastPassImporter.parse("""{"accounts":[],"Other Accounts":[]}""")
        }
        assertEquals(ImporterError.EMPTY, empty.error)

        val notJson = assertThrows(ImporterException::class.java) {
            LastPassImporter.parse("accounts export")
        }
        assertEquals(ImporterError.NOT_JSON, notJson.error)
    }

    @Test
    fun `detect only claims lastpass-shaped json`() {
        assertTrue(LastPassImporter.detect(fixture("lastpass_accounts.json")))
        assertTrue(LastPassImporter.detect("""{"accounts":[]}"""))
        assertTrue(LastPassImporter.detect("""{"Other Accounts":[]}"""))
        assertFalse(LastPassImporter.detect(fixture("aegis_plain.json")))
        assertFalse(LastPassImporter.detect(fixture("2fas_v4.json")))
        assertFalse(LastPassImporter.detect(fixture("raivo_sample.json")))
        assertFalse(LastPassImporter.detect(""))
    }

    companion object {
        private const val FIXTURE_DIR = "/com/safekey/authenticator/totp/importer/"
    }
}
