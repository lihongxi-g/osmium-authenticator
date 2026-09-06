package com.safekey.authenticator.totp.importer

import com.safekey.authenticator.model.Account
import com.safekey.authenticator.model.VaultAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AndOtpImporterTest {

    private fun fixture(name: String): String {
        val stream = checkNotNull(javaClass.getResourceAsStream(FIXTURE_DIR + name)) { "missing $name" }
        return stream.readBytes().toString(Charsets.UTF_8)
    }

    @Test
    fun `parses the real plaintext andOTP fixture`() {
        val rows = AndOtpImporter.parse(fixture("andotp_plain.json"))
        assertEquals(7, rows.size)

        // padding "======" must be stripped from the secret
        assertEquals(
            VaultAccount(
                issuer = "Deno", label = "Mason",
                secret = "4SJHB4GSD43FZBAI7C2HLRJGPQ",
                algorithm = "SHA1", digits = 6, period = 30
            ),
            rows[0]
        )
        assertEquals("SHA256", rows[1].algorithm)
        assertEquals(7, rows[1].digits)
        assertEquals(20, rows[1].period)

        val issuu = rows.first { it.label == "James" && it.issuer == "Issuu" }
        assertEquals(Account.TYPE_HOTP, issuu.type)
        assertEquals(1L, issuu.counter)
        assertEquals(10300L, rows.first { it.issuer == "WWE" }.counter)
        assertEquals("SHA512", rows.first { it.issuer == "WWE" }.algorithm)
    }

    @Test
    fun `steam entries are remapped to issuer Steam`() {
        val rows = AndOtpImporter.parse(fixture("andotp_plain.json"))
        val steam = rows.last()
        assertEquals("Steam", steam.issuer)
        assertEquals("Sophia", steam.label)
        assertEquals(Account.TYPE_TOTP, steam.type)
        assertEquals("JRZCL47CMXVOQMNPZR2F7J4RGI", steam.secret)
        assertEquals(5, steam.digits)
        assertEquals(30, steam.period) // absent in the file → default
    }

    @Test
    fun `legacy labels split issuer - name when there is no issuer key`() {
        val rows = AndOtpImporter.parse(
            """[{"type":"TOTP","secret":"JBSWY3DPEHPK3PXP","label":"GitHub - octocat","digits":6,"period":30,"algorithm":"SHA1"}]"""
        )
        assertEquals(1, rows.size)
        assertEquals("GitHub", rows[0].issuer)
        assertEquals("octocat", rows[0].label)
    }

    @Test
    fun `encrypted binary backups fail as not json and are not detected`() {
        // andOTP password/OpenPGP backups are binary (PBKDF2 iterations +
        // salt + AES-GCM), never JSON — they must fall through detection.
        val binary = "\u0001\u0000\u0000\u0001salt_here_and_more_bytes\u0000\u0001ciphertext"
        assertFalse(AndOtpImporter.detect(binary))
        val e = assertThrows(ImporterException::class.java) { AndOtpImporter.parse(binary) }
        assertEquals(ImporterError.NOT_JSON, e.error)
    }

    @Test
    fun `wrong shapes and empty files are rejected`() {
        val notArray = assertThrows(ImporterException::class.java) {
            AndOtpImporter.parse("""{"entries":[]}""")
        }
        assertEquals(ImporterError.UNRECOGNIZED, notArray.error)

        val empty = assertThrows(ImporterException::class.java) { AndOtpImporter.parse("[]") }
        assertEquals(ImporterError.EMPTY, empty.error)

        val corrupt = assertThrows(ImporterException::class.java) {
            AndOtpImporter.parse("""["just","strings"]""")
        }
        assertEquals(ImporterError.UNRECOGNIZED, corrupt.error)
    }

    @Test
    fun `detect only claims andotp-shaped json`() {
        assertTrue(AndOtpImporter.detect(fixture("andotp_plain.json")))
        assertTrue(AndOtpImporter.detect("[]"))
        assertFalse(AndOtpImporter.detect(fixture("aegis_plain.json")))
        assertFalse(AndOtpImporter.detect(fixture("raivo_sample.json")))
        assertFalse(AndOtpImporter.detect(""))
    }

    companion object {
        private const val FIXTURE_DIR = "/com/safekey/authenticator/totp/importer/"
    }
}
