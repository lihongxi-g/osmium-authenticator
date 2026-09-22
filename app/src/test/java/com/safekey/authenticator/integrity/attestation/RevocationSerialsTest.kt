package com.safekey.authenticator.integrity.attestation

import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RevocationSerialsTest {

    @Test
    fun `decimal serials are also stored in their hex form`() {
        // 56% of the shipped snapshot is decimal; the verifier looks up
        // serialNumber.toString(16), so those entries could never match.
        val decimal = "6681152659205225093"
        val set = RevocationSerials.normalize(setOf(decimal))
        assertTrue(set.contains(decimal))
        assertTrue(set.contains(BigInteger(decimal).toString(16)))
    }

    @Test
    fun `zero padded hex matches the unpadded verifier form`() {
        val set = RevocationSerials.normalize(setOf("00ab12cd"))
        assertTrue(set.contains("ab12cd"))
    }

    @Test
    fun `keys are lowercased`() {
        val set = RevocationSerials.normalize(setOf("1F4363F4ACEFDF83AE59202B934CEAD9"))
        assertTrue(set.contains("1f4363f4acefdf83ae59202b934cead9"))
    }

    @Test
    fun `blank entries are dropped`() {
        assertTrue(RevocationSerials.normalize(setOf("", "   ")).isEmpty())
    }

    @Test
    fun `unpaddedHex strips leading zeros only`() {
        assertEquals("ab12", RevocationSerials.unpaddedHex("AB1200"))
        assertEquals("ab12", RevocationSerials.unpaddedHex("0000ab12"))
        assertEquals("0", RevocationSerials.unpaddedHex("0000"))
        assertNull(RevocationSerials.unpaddedHex(""))
        assertNull(RevocationSerials.unpaddedHex("zz"))
    }

    @Test
    fun `decimalToHex ignores non decimal input`() {
        assertEquals("5cb8f0c40ded6f45", RevocationSerials.decimalToHex("6681152659205225093"))
        assertNull(RevocationSerials.decimalToHex("12ab"))
        assertNull(RevocationSerials.decimalToHex(""))
    }
}
