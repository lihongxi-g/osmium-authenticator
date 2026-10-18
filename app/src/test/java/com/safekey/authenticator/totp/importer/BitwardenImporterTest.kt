package com.safekey.authenticator.totp.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Bitwarden vault exports. Password-only and note items must be skipped, and a
 * missing `login`/`totp` on one item must never abort the whole file (Aegis
 * falls through to its CSV branch there and loses every TOTP).
 */
class BitwardenImporterTest {

    private fun fixture(name: String): String {
        val stream = checkNotNull(javaClass.getResourceAsStream(FIXTURE_DIR + name)) { "missing $name" }
        return stream.readBytes().toString(Charsets.UTF_8)
    }

    @Test
    fun `json export imports only items that carry a totp`() {
        val rows = BitwardenImporter.parse(fixture("bitwarden.json"))
        assertEquals(2, rows.size)
        assertEquals("GitHub", rows[0].issuer)
        assertEquals("octocat", rows[0].label)
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", rows[0].secret)
        // steam://SECRET links become Steam accounts
        assertEquals("Steam", rows[1].issuer)
    }

    @Test
    fun `csv export reads the login_totp column`() {
        val rows = BitwardenImporter.parse(fixture("bitwarden.csv"))
        assertEquals(1, rows.size)
        assertEquals("GitHub", rows[0].issuer)
        assertEquals("octocat", rows[0].label)
    }

    @Test
    fun `a vault without any totp is reported as empty`() {
        val error = assertThrows(ImporterException::class.java) {
            BitwardenImporter.parse("""{"encrypted":false,"items":[{"name":"x","login":{"username":"u"}}]}""")
        }
        assertEquals(ImporterError.EMPTY, error.error)
    }

    private companion object {
        const val FIXTURE_DIR = "/com/safekey/authenticator/totp/importer/"
    }
}
