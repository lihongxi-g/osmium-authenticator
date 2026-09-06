package com.safekey.authenticator.totp.importer

import com.safekey.authenticator.model.Account
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AegisImporterTest {

    private fun fixture(name: String): String {
        val stream = checkNotNull(javaClass.getResourceAsStream(FIXTURE_DIR + name)) { "missing $name" }
        return stream.readBytes().toString(Charsets.UTF_8)
    }

    @Test
    fun `parses the real plaintext Aegis fixture`() {
        val rows = AegisImporter.parse(fixture("aegis_plain.json"))
        assertEquals(7, rows.size)

        // totp with all defaults spelled out
        assertEquals(
            com.safekey.authenticator.model.VaultAccount(
                issuer = "Deno", label = "Mason",
                secret = "4SJHB4GSD43FZBAI7C2HLRJGPQ",
                algorithm = "SHA1", digits = 6, period = 30
            ),
            rows[0]
        )
        // non-default SHA256 + digits 7 (unsupported by Osmium, surfaced later)
        assertEquals("SPDX", rows[1].issuer)
        assertEquals("SHA256", rows[1].algorithm)
        assertEquals(7, rows[1].digits)
        assertEquals(20, rows[1].period)
        // hotp with counter
        assertEquals(Account.TYPE_HOTP, rows[4].type)
        assertEquals("Air Canada", rows[4].issuer)
        assertEquals(50L, rows[4].counter)
        assertEquals(10300L, rows[5].counter)
        assertEquals("SHA512", rows[5].algorithm)
    }

    @Test
    fun `steam entries are remapped to issuer Steam`() {
        val rows = AegisImporter.parse(fixture("aegis_plain.json"))
        val steam = rows.last()
        assertEquals("Steam", steam.issuer)
        assertEquals("Sophia", steam.label)
        assertEquals(Account.TYPE_TOTP, steam.type)
        assertEquals("JRZCL47CMXVOQMNPZR2F7J4RGI", steam.secret)
        assertEquals(5, steam.digits)
    }

    @Test
    fun `encrypted exports are rejected with a targeted error`() {
        val e = assertThrows(ImporterException::class.java) {
            AegisImporter.parse(fixture("aegis_encrypted.json"))
        }
        assertEquals(ImporterError.ENCRYPTED_UNSUPPORTED, e.error)
    }

    @Test
    fun `non-json and foreign json are rejected`() {
        val notJson = assertThrows(ImporterException::class.java) { AegisImporter.parse("not json at all") }
        assertEquals(ImporterError.NOT_JSON, notJson.error)

        val foreign = assertThrows(ImporterException::class.java) {
            AegisImporter.parse("""{"schemaVersion":4,"services":[]}""")
        }
        assertEquals(ImporterError.UNRECOGNIZED, foreign.error)

        // db present but not an object → encrypted-style failure
        val weird = assertThrows(ImporterException::class.java) {
            AegisImporter.parse("""{"version":1,"header":{},"db":"c2VjcmV0"}""")
        }
        assertEquals(ImporterError.ENCRYPTED_UNSUPPORTED, weird.error)
    }

    @Test
    fun `empty vault reports empty`() {
        val e = assertThrows(ImporterException::class.java) {
            AegisImporter.parse(
                """{"version":1,"header":{"slots":null,"params":null},"db":{"version":1,"entries":[]}}"""
            )
        }
        assertEquals(ImporterError.EMPTY, e.error)
    }

    @Test
    fun `detect only claims aegis-shaped json`() {
        assertTrue(AegisImporter.detect(fixture("aegis_plain.json")))
        assertTrue(AegisImporter.detect(fixture("aegis_encrypted.json")))
        assertFalse(AegisImporter.detect(fixture("2fas_v4.json")))
        assertFalse(AegisImporter.detect(fixture("raivo_sample.json")))
        assertFalse(AegisImporter.detect("""{"db":{"entries":[]}}"""))
        assertFalse(AegisImporter.detect(""))
    }

    companion object {
        private const val FIXTURE_DIR = "/com/safekey/authenticator/totp/importer/"
    }
}
