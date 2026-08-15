package com.safekey.authenticator.totp

import com.safekey.authenticator.model.Account
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OtpUriParserTest {

    private val baseSecret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"

    @Test
    fun `parses a standard google-style uri`() {
        val p = OtpUriParser.parse(
            "otpauth://totp/Google:user%40gmail.com?secret=$baseSecret&issuer=Google"
        )
        assertEquals("Google", p.issuer)
        assertEquals("user@gmail.com", p.label)
        assertEquals(baseSecret, p.secret)
        assertEquals("SHA1", p.algorithm)
        assertEquals(6, p.digits)
        assertEquals(30, p.period)
    }

    @Test
    fun `issuer falls back to label prefix`() {
        val p = OtpUriParser.parse("otpauth://totp/GitHub:octocat?secret=$baseSecret")
        assertEquals("GitHub", p.issuer)
        assertEquals("octocat", p.label)
    }

    @Test
    fun `no issuer and no prefix`() {
        val p = OtpUriParser.parse("otpauth://totp/myaccount?secret=$baseSecret")
        assertEquals("", p.issuer)
        assertEquals("myaccount", p.label)
    }

    @Test
    fun `uppercase scheme and host are accepted`() {
        val p = OtpUriParser.parse("OTPAUTH://TOTP/Issuer:acc?secret=$baseSecret")
        assertEquals("Issuer", p.issuer)
    }

    @Test
    fun `sha256 sha512 and digits`() {
        val p = OtpUriParser.parse(
            "otpauth://totp/Test?secret=$baseSecret&algorithm=SHA256&digits=8&period=60"
        )
        assertEquals("SHA256", p.algorithm)
        assertEquals(8, p.digits)
        assertEquals(60, p.period)
        val p2 = OtpUriParser.parse("otpauth://totp/Test?secret=$baseSecret&algorithm=sha512")
        assertEquals("SHA512", p2.algorithm)
    }

    @Test
    fun `lowercase secret is normalized`() {
        val p = OtpUriParser.parse("otpauth://totp/A:B?secret=${baseSecret.lowercase()}")
        assertEquals(baseSecret, p.secret)
    }

    @Test
    fun `missing secret throws`() {
        expectThrow("otpauth://totp/A:B")
    }

    @Test
    fun `invalid base32 secret throws`() {
        expectThrow("otpauth://totp/A:B?secret=NOT!VALID!")
    }

    @Test
    fun `too short secret throws`() {
        // "MY" = 1 byte
        expectThrow("otpauth://totp/A:B?secret=MY")
    }

    @Test
    fun `unsupported algorithm throws`() {
        expectThrow("otpauth://totp/A:B?secret=$baseSecret&algorithm=MD5")
    }

    @Test
    fun `unsupported digits throws`() {
        expectThrow("otpauth://totp/A:B?secret=$baseSecret&digits=7")
    }

    @Test
    fun `invalid period throws`() {
        expectThrow("otpauth://totp/A:B?secret=$baseSecret&period=0")
        expectThrow("otpauth://totp/A:B?secret=$baseSecret&period=9999")
        expectThrow("otpauth://totp/A:B?secret=$baseSecret&period=abc")
    }

    @Test
    fun `hotp uri parses with counter`() {
        val p = OtpUriParser.parse("otpauth://hotp/A:B?secret=$baseSecret&counter=7")
        assertEquals(Account.TYPE_HOTP, p.type)
        assertEquals(7L, p.counter)
        assertEquals("B", p.label)
        // counter defaults to 0 when omitted
        val p2 = OtpUriParser.parse("otpauth://hotp/A:B?secret=$baseSecret")
        assertEquals(0L, p2.counter)
        // negative counter rejected
        expectThrow("otpauth://hotp/A:B?secret=$baseSecret&counter=-1")
    }

    @Test
    fun `non otpauth uri is rejected`() {
        expectThrow("https://example.com")
        expectThrow("random text")
        expectThrow("")
    }

    private fun expectThrow(uri: String) {
        var threw = false
        try {
            OtpUriParser.parse(uri)
        } catch (e: IllegalArgumentException) {
            threw = true
            assertTrue(e.message?.isNotBlank() == true)
        }
        assertTrue("expected parse failure for: $uri", threw)
    }
}
