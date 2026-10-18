package com.safekey.authenticator.totp.importer

import com.safekey.authenticator.model.Account
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * FreeOTP / FreeOTP+ JSON exports, where `secret` is a raw BYTE ARRAY
 * (not Base32) and the issuer lives in `issuerExt`.
 */
class FreeOtpPlusImporterTest {

    private fun fixture(name: String): String {
        val stream = checkNotNull(javaClass.getResourceAsStream(FIXTURE_DIR + name)) { "missing $name" }
        return stream.readBytes().toString(Charsets.UTF_8)
    }

    @Test
    fun `byte-array secret is base32 encoded again`() {
        val rows = FreeOtpPlusImporter.parse(fixture("freeotp_plus.json"))
        assertEquals(2, rows.size)
        // "12345678901234567890" in Base32
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", rows[0].secret)
        assertEquals("GitHub", rows[0].issuer)
        assertEquals("octocat", rows[0].label)
        assertEquals(Account.TYPE_TOTP, rows[0].type)
        assertEquals(30, rows[0].period)
    }

    @Test
    fun `hotp token keeps algorithm digits and counter`() {
        val rows = FreeOtpPlusImporter.parse(fixture("freeotp_plus.json"))
        assertEquals(Account.TYPE_HOTP, rows[1].type)
        assertEquals("SHA256", rows[1].algorithm)
        assertEquals(8, rows[1].digits)
        assertEquals(7L, rows[1].counter)
    }

    @Test
    fun `a string secret is still accepted`() {
        val rows = FreeOtpPlusImporter.parse(
            """{"tokens":[{"type":"TOTP","algo":"SHA1","digits":6,"secret":"GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ","period":30,"issuerExt":"GitHub","label":"octocat"}]}"""
        )
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", rows[0].secret)
    }

    @Test
    fun `no tokens is an empty export`() {
        val error = assertThrows(ImporterException::class.java) { FreeOtpPlusImporter.parse("""{"tokens":[]}""") }
        assertEquals(ImporterError.EMPTY, error.error)
    }

    private companion object {
        const val FIXTURE_DIR = "/com/safekey/authenticator/totp/importer/"
    }
}
