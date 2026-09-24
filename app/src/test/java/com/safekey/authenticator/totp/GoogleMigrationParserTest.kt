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
        assertEquals("JJBFGV2ZGNCFARKIKBFTGUCYKA", google.secret)
        assertEquals("user@gmail.com", google.name)
        assertEquals("Google", google.issuer)
        assertEquals("SHA1", google.algorithm)
        assertEquals(6, google.digits)
        assertEquals("totp", google.type)
        assertEquals(0L, google.counter)

        val github = accounts[1]
        assertEquals("JA2FOTZUKRJE4QSFJFIUINKYJZFE4V2EGQ2EGRSOLE", github.secret)
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
        // raw bytes are canonicalized to Base32 text
        assertEquals("JJBFGV2ZGNCFARKIKBFTGUCYKA", accounts[0].secret)
    }

    @Test
    fun `arbitrary raw secret bytes accepted`() {
        // The migration secret field is RAW key bytes (Aegis: getSecret()
        // .toByteArray()) — including bytes outside the printable ASCII
        // range, which the old text-based parsing could never handle.
        val bytes = byteArrayOf(0x21, 0x45, 0x67, 0x09, 0x10, 0x32, 0x54, 0x76, 0x78, 0x0A, 0x3B, 0x4D, 0x5E, 0x6F, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66)
        val uri = buildOneAccountUri(bytes)
        val accounts = GoogleMigrationParser.parse(uri)
        assertEquals(1, accounts.size)
        assertFalse(accounts[0].isUnsupported)
        assertEquals(Base32.encode(bytes).replace("=", ""), accounts[0].secret)
    }

    private fun buildOneAccountUri(secretRaw: ByteArray): String {
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
        var inner = f(1, secretRaw) + f(2, "test".toByteArray()) + f(3, "Google".toByteArray())
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

    // ------------------------------------------------ truncated payloads (bounds)

    private fun varint(n: Long): ByteArray {
        val out = ArrayList<Byte>()
        var v = n
        while (true) {
            if (v < 0x80) {
                out.add(v.toByte())
                break
            }
            out.add(((v and 0x7F) or 0x80).toByte())
            v = v ushr 7
        }
        return out.toByteArray()
    }

    /** Length-delimited field with its declared length left intact. */
    private fun rawField(number: Int, declaredLength: Long, body: ByteArray): ByteArray =
        varint(((number shl 3) or 2).toLong()) + varint(declaredLength) + body

    private fun uri(payload: ByteArray): String =
        "otpauth-migration://offline?data=" +
            java.util.Base64.getEncoder().encodeToString(payload)

    /** Asserts the payload is rejected as invalid — and never crashes with anything else. */
    private fun assertRejected(payload: ByteArray, label: String) {
        try {
            GoogleMigrationParser.parse(uri(payload))
            throw AssertionError("$label: payload was accepted, expected a rejection")
        } catch (e: IllegalArgumentException) {
            // expected: the parser's own "invalid payload" error
        } catch (e: Throwable) {
            throw AssertionError("$label: expected IllegalArgumentException, got $e", e)
        }
    }

    @Test
    fun `truncated outer field is rejected instead of crashing`() {
        // Declares a 13-byte entry but only 5 bytes follow: this used to reach
        // String(bytes, offset, length) and kill the process with
        // StringIndexOutOfBoundsException (F-Droid reviewer report).
        assertRejected(rawField(1, 13, ByteArray(5)), "outer field overruns the payload")
    }

    @Test
    fun `truncated secret inside an entry is rejected instead of crashing`() {
        // A bytes field whose declared length runs past the entry: this used to
        // reach ByteArray.copyOfRange and throw out of the parser.
        val inner = rawField(1, 13, ByteArray(4)) + rawField(2, 13, ByteArray(6))
        assertRejected(rawField(1, inner.size.toLong(), inner), "secret field overruns the entry")
    }

    @Test
    fun `truncated name inside an entry is rejected instead of crashing`() {
        // The reviewer's exact shape: the name field declares 13 bytes with only
        // 4 left in the payload (their trace: length=190; regionStart=183;
        // regionLength=13), which reached String(bytes, offset, length) and
        // killed the process with StringIndexOutOfBoundsException.
        val inner = rawField(2, 13, ByteArray(4))
        assertRejected(rawField(1, inner.size.toLong(), inner), "name field overruns the payload")
    }

    @Test
    fun `truncated varint is rejected`() {
        // Continuation bit set on the last byte: the varint has no terminator.
        assertRejected(byteArrayOf(0x0A, 0x80.toByte()), "unterminated length varint")
        // ...including a varint *inside* an entry, which pre-fix was read as
        // digits = 127 and the payload was imported.
        assertRejected(byteArrayOf(0x0A, 0x02, 0x28, 0xFF.toByte()), "unterminated value varint")
    }

    @Test
    fun `truncated fixed32 after a valid entry is rejected`() {
        // fixed32/fixed64 are unused by this schema but must not be skipped past
        // the end of the payload silently (pre-fix the entry imported anyway).
        val name = "edge".toByteArray()
        val entry = rawField(2, name.size.toLong(), name)
        val payload = rawField(1, entry.size.toLong(), entry) +
            byteArrayOf(0x2D) + byteArrayOf(1, 2, 3)
        assertRejected(payload, "payload ends inside a fixed32 field")
    }

    @Test
    fun `entry length that only just fits still parses`() {
        // Boundary: the declared length ends exactly at the payload's last byte.
        val name = "edge".toByteArray()
        val inner = rawField(1, 12L, ByteArray(12) { 0x11 }) + rawField(2, name.size.toLong(), name)
        val accounts = GoogleMigrationParser.parse(uri(rawField(1, inner.size.toLong(), inner)))
        assertEquals(1, accounts.size)
        assertEquals("edge", accounts[0].name)
    }

    @Test
    fun `lengths beyond the payload are rejected`() {
        // A varint length beyond Int.MAX_VALUE used to truncate to 0 when cast,
        // so the entry parsed as empty and the payload was imported; it must be
        // rejected like any other bad length.
        assertRejected(rawField(1, 0x1_0000_0000L, ByteArray(4)), "length beyond Int range")
    }
}
