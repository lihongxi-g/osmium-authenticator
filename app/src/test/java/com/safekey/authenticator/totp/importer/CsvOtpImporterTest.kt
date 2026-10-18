package com.safekey.authenticator.totp.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Generic CSV exports carrying otpauth links (KeePassXC, 1Password, Dashlane…). */
class CsvOtpImporterTest {

    private fun fixture(name: String): String {
        val stream = checkNotNull(javaClass.getResourceAsStream(FIXTURE_DIR + name)) { "missing $name" }
        return stream.readBytes().toString(Charsets.UTF_8)
    }

    @Test
    fun `keePassXC export is read from the TOTP column`() {
        val rows = CsvOtpImporter.parse(fixture("keepassxc.csv"))
        assertEquals(2, rows.size)
        assertEquals("GitHub", rows[0].issuer)
        assertEquals("octocat", rows[0].label)
        // second row has no Title in the link and no issuer in the URI: the
        // Title column is empty, so the link's own label survives
        assertEquals("", rows[1].issuer)
        assertEquals("bareuser", rows[1].label)
    }

    @Test
    fun `blank title column falls back to the link`() {
        val csv = "Group,Title,Username,TOTP\r\n" +
            "Osmium,ServiceName,user,otpauth://totp/x?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ\r\n"
        val rows = CsvOtpImporter.parse(csv)
        // the Title column fills the missing issuer, the link keeps its label
        assertEquals("ServiceName", rows[0].issuer)
        assertEquals("x", rows[0].label)
    }

    @Test
    fun `a csv without any link is an empty export`() {
        val error = assertThrows(ImporterException::class.java) {
            CsvOtpImporter.parse("Group,Title,Username\r\nOsmium,Nothing,user\r\n")
        }
        assertEquals(ImporterError.EMPTY, error.error)
    }

    private companion object {
        const val FIXTURE_DIR = "/com/safekey/authenticator/totp/importer/"
    }
}
