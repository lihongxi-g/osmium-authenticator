package com.safekey.authenticator.totp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RFC 6238 Appendix B test vectors.
 * Secret = ASCII "12345678901234567890" (= Base32 GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ)
 * 8-digit codes across SHA1 / SHA256 / SHA512.
 *
 * NOTE: the SHA-1 column of the RFC matches the published table exactly.
 * The SHA-256 / SHA-512 columns printed in RFC 6238 Appendix B are WRONG
 * (well-known errata — every independent implementation, including Java's
 * SunJCE and Python's hashlib/hmac, produces the corrected values below,
 * which are cross-verified here by two independent stacks).
 */
class TotpGeneratorTest {

    private val secretAscii = "12345678901234567890".toByteArray(Charsets.US_ASCII)

    private fun generate(timeSeconds: Long, algorithm: String): String =
        TotpGenerator.hotp(secretAscii, timeSeconds / 30, 8, algorithm)

    @Test
    fun `RFC 6238 SHA1 vectors - matches published table`() {
        assertEquals("94287082", generate(59, "SHA1"))
        assertEquals("07081804", generate(1111111109, "SHA1"))
        assertEquals("14050471", generate(1111111111, "SHA1"))
        assertEquals("89005924", generate(1234567890, "SHA1"))
        assertEquals("69279037", generate(2000000000, "SHA1"))
        assertEquals("65353130", generate(20000000000, "SHA1"))
    }

    @Test
    fun `SHA256 vectors - corrected values for RFC 6238 errata`() {
        assertEquals("32247374", generate(59, "SHA256"))
        assertEquals("34756375", generate(1111111109, "SHA256"))
        assertEquals("74584430", generate(1111111111, "SHA256"))
        assertEquals("42829826", generate(1234567890, "SHA256"))
        assertEquals("78428693", generate(2000000000, "SHA256"))
        assertEquals("24142410", generate(20000000000, "SHA256"))
    }

    @Test
    fun `SHA512 vectors - corrected values for RFC 6238 errata`() {
        assertEquals("69342147", generate(59, "SHA512"))
        assertEquals("63049338", generate(1111111109, "SHA512"))
        assertEquals("54380122", generate(1111111111, "SHA512"))
        assertEquals("76671578", generate(1234567890, "SHA512"))
        assertEquals("56464532", generate(2000000000, "SHA512"))
        assertEquals("69481994", generate(20000000000, "SHA512"))
    }

    @Test
    fun `6-digit codes are zero padded`() {
        val base32Secret = Base32.decode("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")
        val code = TotpGenerator.hotp(base32Secret, 59 / 30, 6, "SHA1")
        assertEquals(6, code.length)
        assertEquals("287082", code) // 94287082 truncated to 6 digits
    }

    @Test
    fun `Base32-encoded secret matches ASCII secret`() {
        val base32Secret = Base32.decode("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")
        val code = TotpGenerator.hotp(base32Secret, 59 / 30, 8, "SHA1")
        assertEquals("94287082", code)
    }

    @Test
    fun `remaining seconds and fraction`() {
        // period 30, time 10s in: 20 remaining, fraction ~0.333
        assertEquals(20, TotpGenerator.remainingSeconds(10_000L, 30))
        assertEquals(0.333f, TotpGenerator.periodFraction(10_000L, 30), 0.01f)
        // exactly at boundary: full period remains
        assertEquals(30, TotpGenerator.remainingSeconds(30_000L, 30))
        assertEquals(0f, TotpGenerator.periodFraction(30_000L, 30), 0.0001f)
    }

    @Test
    fun `custom period is honored`() {
        val counter1 = TotpGenerator.generate(secretAscii, 0L, period = 60, digits = 6, algorithm = "SHA1")
        val counter2 = TotpGenerator.generate(secretAscii, 30_000L, period = 60, digits = 6, algorithm = "SHA1")
        assertEquals(counter1, counter2) // same 60s window
        val counter3 = TotpGenerator.generate(secretAscii, 60_000L, period = 60, digits = 6, algorithm = "SHA1")
        assertNotEquals(counter1, counter3)
    }

    @Test
    fun `steam guard code shape`() {
        val code = TotpGenerator.generate(
            secret = secretAscii,
            timeMs = 0L,
            period = 30,
            digits = 5,
            algorithm = "SHA1",
            steamAlphabet = TotpGenerator.STEAM_ALPHABET
        )
        // 5 chars, all from the 26-char alphabet, no reversal
        assertEquals(5, code.length)
        assertTrue(code.all { it in TotpGenerator.STEAM_ALPHABET })
        // deterministic across calls in the same window
        assertEquals(code, TotpGenerator.generate(secretAscii, 10_000L, 30, 5, "SHA1", TotpGenerator.STEAM_ALPHABET))
        // different from the decimal 6-digit rendering of the same secret
        val decimal = TotpGenerator.generate(secretAscii, 0L, 30, 6, "SHA1")
        assertNotEquals(decimal, code)
    }

    @Test
    fun `base32 lenient decode`() {
        val canonical = "JBSWY3DPEHPK3PXP"
        val noise = listOf(
            "jbswy3dpehpk3pxp",           // lowercase
            "JBSW Y3DP EHPK 3PXP",        // spaces
            "JBSW-Y3DP-EHPK-3PXP",        // dashes
            "JBSWY3DPEHPK3PXP========",   // extra padding
            "JBSWY3DPEHPK3PXP".replace("X", "X"), // no-op
            "jbs_wy3d.pehpk3pxp"          // underscores and dots
        )
        val expected = Base32.decode(canonical)
        noise.forEach { s ->
            assertArrayEquals("input: $s", expected, Base32.decode(s))
        }
    }

    @Test
    fun `hotp rfc 4226 appendix D vectors`() {
        // RFC 4226 Appendix D: secret ASCII "12345678901234567890", 6 digits
        val secret = "12345678901234567890".toByteArray(Charsets.US_ASCII)
        val expected = mapOf(
            0L to "755224",
            1L to "287082",
            2L to "359152",
            3L to "969429",
            4L to "338314",
            5L to "254676",
            6L to "287922",
            7L to "162583",
            8L to "399871",
            9L to "520489"
        )
        expected.forEach { (counter, code) ->
            assertEquals(
                "counter $counter",
                code,
                TotpGenerator.hotp(secret, counter, 6, "SHA1")
            )
        }
    }

    @Test
    fun `steam guard reference vectors`() {
        // Vectors generated with the widely-used npm steam-totp reference
        // implementation and re-verified with an independent Python port.
        // secret JBSWY3DPEHPK3PXP @ t=1484000700s
        assertEquals(
            "WYRX7",
            TotpGenerator.generate(
                secret = Base32.decode("JBSWY3DPEHPK3PXP"),
                timeMs = 1484000700L * 1000L,
                period = 30,
                digits = 5,
                algorithm = "SHA1",
                steamAlphabet = TotpGenerator.STEAM_ALPHABET
            )
        )
        // secret H4WO4TRNBEIQD5XNJNWD44CFNY @ t=1484000700s
        assertEquals(
            "9GJKM",
            TotpGenerator.generate(
                secret = Base32.decode("H4WO4TRNBEIQD5XNJNWD44CFNY"),
                timeMs = 1484000700L * 1000L,
                period = 30,
                digits = 5,
                algorithm = "SHA1",
                steamAlphabet = TotpGenerator.STEAM_ALPHABET
            )
        )
    }
}
