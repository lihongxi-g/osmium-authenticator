package com.safekey.authenticator.totp.importer

import com.safekey.authenticator.model.Account
import com.safekey.authenticator.model.VaultAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TwoFasImporterTest {

    private fun fixture(name: String): String {
        val stream = checkNotNull(javaClass.getResourceAsStream(FIXTURE_DIR + name)) { "missing $name" }
        return stream.readBytes().toString(Charsets.UTF_8)
    }

    @Test
    fun `parses schemaVersion 1 (no otp parameters - all defaults)`() {
        val rows = TwoFasImporter.parse(fixture("2fas_v1.json"))
        assertEquals(6, rows.size)
        assertEquals(
            VaultAccount(
                issuer = "Deno", label = "Mason",
                secret = "4SJHB4GSD43FZBAI7C2HLRJGPQ",
                algorithm = "SHA1", digits = 6, period = 30
            ),
            rows[0]
        )
        // everything defaults to TOTP / SHA1 / 6 / 30 in schema v1
        rows.forEach { row ->
            assertEquals(Account.TYPE_TOTP, row.type)
            assertEquals("SHA1", row.algorithm)
            assertEquals(6, row.digits)
            assertEquals(30, row.period)
        }
    }

    @Test
    fun `parses schemaVersion 2 (parameters but no tokenType)`() {
        val rows = TwoFasImporter.parse(fixture("2fas_v2.json"))
        assertEquals(4, rows.size)
        assertEquals("SHA512", rows[1].algorithm)
        assertEquals(8, rows[1].digits)
        assertEquals(50, rows[1].period)
        // period omitted for this service → default 30
        assertEquals(30, rows[2].period)
        assertEquals(Account.TYPE_TOTP, rows[2].type)
    }

    @Test
    fun `parses schemaVersion 3 (tokenType with HOTP counters)`() {
        val rows = TwoFasImporter.parse(fixture("2fas_v3.json"))
        assertEquals(6, rows.size)

        val issuu = rows.first { it.label == "James" && it.issuer == "Issuu" }
        assertEquals(Account.TYPE_HOTP, issuu.type)
        assertEquals(1L, issuu.counter)
        assertEquals(6, issuu.digits)

        val wwe = rows.first { it.issuer == "WWE" }
        assertEquals(Account.TYPE_HOTP, wwe.type)
        assertEquals(10300L, wwe.counter)
        assertEquals("SHA512", wwe.algorithm)

        assertEquals(7, rows.first { it.issuer == "SPDX" }.digits)
    }

    @Test
    fun `parses schemaVersion 4 (HOTP and STEAM)`() {
        val rows = TwoFasImporter.parse(fixture("2fas_v4.json"))
        assertEquals(5, rows.size)

        val issuu = rows.first { it.label == "James" }
        assertEquals(Account.TYPE_HOTP, issuu.type)
        assertEquals(1L, issuu.counter)

        val steam = rows.first { it.issuer == "Steam" }
        assertEquals("Sophia", steam.label)
        assertEquals(Account.TYPE_TOTP, steam.type)
        assertEquals("JRZCL47CMXVOQMNPZR2F7J4RGI", steam.secret)
    }

    @Test
    fun `encrypted backups are rejected with a targeted error`() {
        val e = assertThrows(ImporterException::class.java) {
            TwoFasImporter.parse(fixture("2fas_v4_encrypted.json"))
        }
        assertEquals(ImporterError.ENCRYPTED_UNSUPPORTED, e.error)
    }

    @Test
    fun `schema versions above 4 are rejected`() {
        val e = assertThrows(ImporterException::class.java) {
            TwoFasImporter.parse("""{"schemaVersion":9,"services":[]}""")
        }
        assertEquals(ImporterError.VERSION_UNSUPPORTED, e.error)
    }

    @Test
    fun `services without otp blocks are skipped, all-skipped reports empty`() {
        // 2FAS entries that are not OTP tokens (no otp block) carry nothing
        // importable; a file made only of them must report EMPTY, not crash.
        val e = assertThrows(ImporterException::class.java) {
            TwoFasImporter.parse(
                """{"schemaVersion":4,"services":[{"name":"SMS code","secret":"x"},{"name":"Push"}]}"""
            )
        }
        assertEquals(ImporterError.EMPTY, e.error)
    }

    @Test
    fun `wrong shapes are rejected`() {
        val noServices = assertThrows(ImporterException::class.java) {
            TwoFasImporter.parse("""{"schemaVersion":4}""")
        }
        assertEquals(ImporterError.UNRECOGNIZED, noServices.error)

        val notJson = assertThrows(ImporterException::class.java) {
            TwoFasImporter.parse("services data")
        }
        assertEquals(ImporterError.NOT_JSON, notJson.error)

        val corrupt = assertThrows(ImporterException::class.java) {
            TwoFasImporter.parse("""{"schemaVersion":4,"services":[1,2]}""")
        }
        assertEquals(ImporterError.UNRECOGNIZED, corrupt.error)
    }

    @Test
    fun `detect only claims 2fas-shaped json`() {
        assertTrue(TwoFasImporter.detect(fixture("2fas_v1.json")))
        assertTrue(TwoFasImporter.detect(fixture("2fas_v4_encrypted.json")))
        assertFalse(TwoFasImporter.detect(fixture("aegis_plain.json")))
        assertFalse(TwoFasImporter.detect("""{"services":[]}""")) // schemaVersion required
        assertFalse(TwoFasImporter.detect(""))
    }

    companion object {
        private const val FIXTURE_DIR = "/com/safekey/authenticator/totp/importer/"
    }
}
