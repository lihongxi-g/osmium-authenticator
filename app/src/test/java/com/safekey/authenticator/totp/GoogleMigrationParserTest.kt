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
