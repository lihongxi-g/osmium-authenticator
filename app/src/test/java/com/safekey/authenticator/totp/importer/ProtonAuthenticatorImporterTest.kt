package com.safekey.authenticator.totp.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Proton Authenticator plaintext exports (entries[].content.uri). */
class ProtonAuthenticatorImporterTest {

    private fun fixture(name: String): String {
        val stream = checkNotNull(javaClass.getResourceAsStream(FIXTURE_DIR + name)) { "missing $name" }
        return stream.readBytes().toString(Charsets.UTF_8)
    }

    @Test
    fun `plaintext export parses its enrollment links`() {
        val rows = ProtonAuthenticatorImporter.parse(fixture("proton_plain.json"))
        assertEquals(2, rows.size)
        assertEquals("GitHub", rows[0].issuer)
        assertEquals("octocat", rows[0].label)
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", rows[0].secret)
        // a link without issuer gets the entry name as issuer
        assertEquals("Plain entry", rows[1].issuer)
        assertEquals("plainentry", rows[1].label)
    }

    @Test
    fun `password-protected export is reported as encrypted`() {
        val error = assertThrows(ImporterException::class.java) {
            ProtonAuthenticatorImporter.parse(fixture("proton_encrypted.json"))
        }
        assertEquals(ImporterError.ENCRYPTED_UNSUPPORTED, error.error)
    }

    @Test
    fun `a newer encrypted export version is reported as unsupported`() {
        val error = assertThrows(ImporterException::class.java) {
            ProtonAuthenticatorImporter.parse("""{"version":2,"salt":"c2FsdA==","content":"AAAA"}""")
        }
        assertEquals(ImporterError.VERSION_UNSUPPORTED, error.error)
    }

    private companion object {
        const val FIXTURE_DIR = "/com/safekey/authenticator/totp/importer/"
    }
}
