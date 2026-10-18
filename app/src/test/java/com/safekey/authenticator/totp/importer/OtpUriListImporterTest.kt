package com.safekey.authenticator.totp.importer

import com.safekey.authenticator.model.Account
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Plain-text otpauth link lists (Ente Auth, Google Auth URI export, scripts). */
class OtpUriListImporterTest {

    private fun fixture(name: String): String {
        val stream = checkNotNull(javaClass.getResourceAsStream(FIXTURE_DIR + name)) { "missing $name" }
        return stream.readBytes().toString(Charsets.UTF_8)
    }

    @Test
    fun `parses every link and skips blank or comment lines`() {
        val rows = OtpUriListImporter.parse(fixture("otpauth_links.txt"))
        assertEquals(3, rows.size)
        assertEquals("GitHub", rows[0].issuer)
        assertEquals("octocat", rows[0].label)
        assertEquals(6, rows[0].digits)
        assertEquals(30, rows[0].period)

        assertEquals(Account.TYPE_HOTP, rows[1].type)
        assertEquals(7L, rows[1].counter)

        assertEquals("Steam", rows[2].issuer)
    }

    @Test
    fun `a file that is only comments is reported as empty`() {
        val error = assertThrows(ImporterException::class.java) {
            OtpUriListImporter.parse("# nothing here\n\n")
        }
        assertEquals(ImporterError.EMPTY, error.error)
    }

    @Test
    fun `malformed links are skipped without failing the file`() {
        val rows = OtpUriListImporter.parse(
            "otpauth://totp/Good:user?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ\n" +
                "otpauth://totp/Bad:user?secret=short\n"
        )
        assertEquals(1, rows.size)
        assertEquals("Good", rows[0].issuer)
    }

    private companion object {
        const val FIXTURE_DIR = "/com/safekey/authenticator/totp/importer/"
    }
}
