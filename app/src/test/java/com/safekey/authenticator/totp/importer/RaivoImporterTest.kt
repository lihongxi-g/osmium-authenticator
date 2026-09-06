package com.safekey.authenticator.totp.importer

import com.safekey.authenticator.model.Account
import com.safekey.authenticator.model.VaultAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RaivoImporterTest {

    private fun fixture(name: String): String {
        val stream = checkNotNull(javaClass.getResourceAsStream(FIXTURE_DIR + name)) { "missing $name" }
        return stream.readBytes().toString(Charsets.UTF_8)
    }

    @Test
    fun `parses the legacy array export with string-typed fields`() {
        val rows = RaivoImporter.parse(fixture("raivo_sample.json"))
        assertEquals(3, rows.size)
        assertEquals(
            VaultAccount(
                issuer = "Google.com", label = "google@gmail.com",
                secret = "JBSWY3DPEHPK3PXP",
                algorithm = "SHA1", digits = 6, period = 30
            ),
            rows[0]
        )
        // timer "20" (string!) → period 20
        assertEquals(20, rows[1].period)
        assertEquals("SHA256", rows[1].algorithm)
        assertEquals(8, rows[1].digits)
        // kind "HOTP" + counter "1" (string) → hotp
        assertEquals(Account.TYPE_HOTP, rows[2].type)
        assertEquals(1L, rows[2].counter)
    }

    @Test
    fun `accepts a single-object export`() {
        val rows = RaivoImporter.parse(fixture("raivo_single_object.json"))
        assertEquals(1, rows.size)
        assertEquals("Airbnb", rows[0].issuer)
        assertEquals("user@example.com", rows[0].label)
        assertEquals("SHA512", rows[0].algorithm)
        assertEquals(50, rows[0].period)
        assertEquals(8, rows[0].digits)
    }

    @Test
    fun `missing kind defaults to TOTP, missing timer defaults to 30`() {
        val rows = RaivoImporter.parse(
            """[{"account":"alice","secret":"JBSWY3DPEHPK3PXP","issuer":"GitHub"}]"""
        )
        assertEquals(1, rows.size)
        assertEquals(Account.TYPE_TOTP, rows[0].type)
        assertEquals(30, rows[0].period)
        assertEquals("SHA1", rows[0].algorithm)
        assertEquals(6, rows[0].digits)
    }

    @Test
    fun `wrong shapes are rejected`() {
        val notArray = assertThrows(ImporterException::class.java) { RaivoImporter.parse("42") }
        assertEquals(ImporterError.UNRECOGNIZED, notArray.error)

        val emptyObj = assertThrows(ImporterException::class.java) { RaivoImporter.parse("{}") }
        assertEquals(ImporterError.EMPTY, emptyObj.error)

        val emptyArray = assertThrows(ImporterException::class.java) { RaivoImporter.parse("[]") }
        assertEquals(ImporterError.EMPTY, emptyArray.error)

        val notJson = assertThrows(ImporterException::class.java) { RaivoImporter.parse("raivo backup") }
        assertEquals(ImporterError.NOT_JSON, notJson.error)
    }

    @Test
    fun `detect only claims raivo-shaped json`() {
        assertTrue(RaivoImporter.detect(fixture("raivo_sample.json")))
        assertTrue(RaivoImporter.detect(fixture("raivo_single_object.json")))
        assertFalse(RaivoImporter.detect(fixture("aegis_plain.json")))
        assertFalse(RaivoImporter.detect(fixture("2fas_v4.json")))
        assertFalse(RaivoImporter.detect(""))
        // "kind" alone is not enough — account and secret must be there too
        assertFalse(RaivoImporter.detect("""{"kind":"TOTP"}"""))
    }

    companion object {
        private const val FIXTURE_DIR = "/com/safekey/authenticator/totp/importer/"
    }
}
