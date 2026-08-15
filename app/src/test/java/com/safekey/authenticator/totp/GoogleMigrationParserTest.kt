package com.safekey.authenticator.totp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vectors hand-encoded per the community-standard MigrationPayload proto
 * (same schema Aegis uses) with an independent Python encoder.
 */
class GoogleMigrationParserTest {

    private val twoAccountUri =
        "otpauth-migration://offline?data=CjIKEEpCU1dZM0RQRUhQSzNQWFASDnVzZXJAZ21haWwuY29tGgZHb29nbGUgASgBMAI4AAoyChpINFdPNFRSTkJFSVFENVhOSk5XRDQ0Q0ZOWRIEb2N0bxoGR2l0SHViIAIoAjABOAUQARgCKAA="

    @Test
    fun `recognizes migration uris`() {
        assertTrue(GoogleMigrationParser.isMigrationUri(twoAccountUri))
        assertFalse(GoogleMigrationParser.isMigrationUri("otpauth://totp/X:y?secret=A"))
        assertFalse(GoogleMigrationParser.isMigrationUri("random"))
    }

    @Test
    fun `parses totp and hotp accounts`() {
        val accounts = GoogleMigrationParser.parse(twoAccountUri)
        assertEquals(2, accounts.size)

        val google = accounts[0]
        assertEquals("JBSWY3DPEHPK3PXP", google.secret)
        assertEquals("user@gmail.com", google.name)
        assertEquals("Google", google.issuer)
        assertEquals("SHA1", google.algorithm)
        assertEquals(6, google.digits)
        assertEquals("totp", google.type)
        assertEquals(0L, google.counter)

        val github = accounts[1]
        assertEquals("H4WO4TRNBEIQD5XNJNWD44CFNY", github.secret)
        assertEquals("octo", github.name)
        assertEquals("GitHub", github.issuer)
        assertEquals("SHA256", github.algorithm)
        assertEquals(8, github.digits)
        assertEquals("hotp", github.type)
        assertEquals(5L, github.counter)
    }

    @Test
    fun `rejects malformed payloads`() {
        for (bad in listOf(
            "otpauth-migration://offline",              // no data
            "otpauth-migration://offline?data=!!!",     // not base64
            "otpauth-migration://offline?data=AAAA",    // decodes to empty/garbage fields
        )) {
            var threw = false
            try {
                GoogleMigrationParser.parse(bad)
            } catch (e: IllegalArgumentException) {
                threw = true
            }
            assertTrue("expected failure for: $bad", threw)
        }
    }

    @Test
    fun `urlsafe base64 accepted`() {
        // same payload with URL-safe base64 and no padding
        val data = "CjIKEEpCU1dZM0RQRUhQSzNQWFASDnVzZXJAZ21haWwuY29tGgZHb29nbGUgASgBMAI4AAoyChpINFdPNFRSTkJFSVFENVhOSk5XRDQ0Q0ZOWRIEb2N0bxoGR2l0SHViIAIoAjABOAUQARgCKAA"
        val uri = "otpauth-migration://offline?data=" + data.replace("+", "-").replace("/", "_")
        val accounts = GoogleMigrationParser.parse(uri)
        assertEquals(2, accounts.size)
        assertEquals("Google", accounts[0].issuer)
    }

    @Test
    fun `algorithm zero defaults to sha1`() {
        // Many Google accounts omit the algorithm field (enum 0), which
        // semantically means SHA1 — never MD5.
        fun v(n: Long): ByteArray {
            val out = mutableListOf<Byte>()
            var x = n
            while (true) {
                val b = (x and 0x7F).toInt()
                x = x ushr 7
                if (x != 0L) out.add((b or 0x80).toByte()) else { out.add(b.toByte()); break }
            }
            return out.toByteArray()
        }
        fun f(num: Int, payload: ByteArray): ByteArray = v((num shl 3 or 2).toLong()) + v(payload.size.toLong()) + payload
        fun fv(num: Int, value: Long): ByteArray = v((num shl 3).toLong()) + v(value)
        var inner = f(1, "JBSWY3DPEHPK3PXP".toByteArray()) + f(2, "alice".toByteArray()) + f(3, "Google".toByteArray())
        inner += fv(4, 0) + fv(5, 1) + fv(6, 2) // algorithm = 0 (unspecified)
        val payload = f(1, inner) + fv(2, 1) + fv(3, 1) + fv(5, 0)
        val data = java.util.Base64.getEncoder().encodeToString(payload)
        val accounts = GoogleMigrationParser.parse("otpauth-migration://offline?data=$data")
        assertEquals(1, accounts.size)
        assertEquals("SHA1", accounts[0].algorithm)
        assertFalse(accounts[0].isUnsupported)
    }

    @Test
    fun `base64 secret accepted`() {
        // Some Google accounts store the secret as base64 (e.g. containing
        // chars outside the base32 alphabet). Encode a valid 20-byte secret.
        val bytes = "12345678901234567890".toByteArray(Charsets.US_ASCII)
        val b64 = java.util.Base64.getEncoder().encodeToString(bytes)
        val uri = buildOneAccountUri(b64)
        val accounts = GoogleMigrationParser.parse(uri)
        assertEquals(1, accounts.size)
        assertFalse(accounts[0].isUnsupported)
        // canonicalized to base32 of the same bytes
        assertEquals(Base32.encode(bytes).replace("=", ""), accounts[0].secret)
    }

    private fun buildOneAccountUri(secretB64: String): String {
        // hand-encode: field1(bytes secret), field2(name), field3(issuer), field4(alg=1), field5(digits=1), field6(type=2)
        fun v(n: Long): ByteArray {
            val out = mutableListOf<Byte>()
            var x = n
            while (true) {
                val b = (x and 0x7F).toInt()
                x = x ushr 7
                if (x != 0L) out.add((b or 0x80).toByte()) else { out.add(b.toByte()); break }
            }
            return out.toByteArray()
        }
        fun f(num: Int, payload: ByteArray): ByteArray = v((num shl 3 or 2).toLong()) + v(payload.size.toLong()) + payload
        fun fv(num: Int, value: Long): ByteArray = v((num shl 3).toLong()) + v(value)
        var inner = f(1, secretB64.toByteArray()) + f(2, "test".toByteArray()) + f(3, "Google".toByteArray())
        inner += fv(4, 1) + fv(5, 1) + fv(6, 2)
        val payload = f(1, inner) + fv(2, 1) + fv(3, 1) + fv(5, 0)
        val data = java.util.Base64.getEncoder().encodeToString(payload)
        return "otpauth-migration://offline?data=$data"
    }

    @Test
    fun `percent encoded payload accepted`() {
        // Google QR payloads may percent-encode the base64 body (%3D etc.)
        val data = "CjIKEEpCU1dZM0RQRUhQSzNQWFASDnVzZXJAZ21haWwuY29tGgZHb29nbGUgASgBMAI4AAoyChpINFdPNFRSTkJFSVFENVhOSk5XRDQ0Q0ZOWRIEb2N0bxoGR2l0SHViIAIoAjABOAUQARgCKAA%3D"
        val uri = "otpauth-migration://offline?data=$data"
        val accounts = GoogleMigrationParser.parse(uri)
        assertEquals(2, accounts.size)
        assertEquals("Google", accounts[0].issuer)
        assertEquals("GitHub", accounts[1].issuer)
    }
}
