package com.safekey.authenticator.totp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Base32Test {

    @Test
    fun `RFC 4648 vectors`() {
        assertArrayEquals("".toByteArray(), Base32.decode(""))
        assertArrayEquals("f".toByteArray(), Base32.decode("MY======"))
        assertArrayEquals("fo".toByteArray(), Base32.decode("MZXQ===="))
        assertArrayEquals("foo".toByteArray(), Base32.decode("MZXW6==="))
        assertArrayEquals("foob".toByteArray(), Base32.decode("MZXW6YQ="))
        assertArrayEquals("fooba".toByteArray(), Base32.decode("MZXW6YTB"))
        assertArrayEquals("foobar".toByteArray(), Base32.decode("MZXW6YTBOI======"))
    }

    @Test
    fun `encode matches RFC 4648`() {
        assertEquals("MY======", Base32.encode("f".toByteArray()))
        assertEquals("MZXW6===", Base32.encode("foo".toByteArray()))
        assertEquals("MZXW6YTBOI======", Base32.encode("foobar".toByteArray()))
    }

    @Test
    fun `lenient input - lowercase spaces dashes no padding`() {
        val expected = "foobar".toByteArray()
        assertArrayEquals(expected, Base32.decode("mzxw6ytboi"))
        assertArrayEquals(expected, Base32.decode("MZXW 6YTB OI"))
        assertArrayEquals(expected, Base32.decode("MZXW-6YTB-OI"))
        assertArrayEquals(expected, Base32.decode("MZXW6YTBOI======"))
    }

    @Test
    fun `round trip preserves bytes`() {
        val bytes = ByteArray(256) { it.toByte() }
        val encoded = Base32.encode(bytes)
        assertArrayEquals(bytes, Base32.decode(encoded))
    }

    @Test
    fun `invalid characters throw`() {
        assertFalse(Base32.isValid("MZXW!YTBOI"))
        assertFalse(Base32.isValid("MZXW1YTBOI"))
        assertFalse(Base32.isValid("MZXW8YTBOI"))
        assertFalse(Base32.isValid(""))
        try {
            Base32.decode("MZXW!YTBOI")
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `validity check`() {
        assertTrue(Base32.isValid("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"))
        assertTrue(Base32.isValid("jbswy3dpehpk3pxp"))
    }
}
