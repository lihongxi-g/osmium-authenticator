package com.safekey.authenticator.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {

    @Test
    fun `hash and verify round trip`() {
        val h = PinHasher.hashPin("123456")
        assertTrue(PinHasher.verify("123456", h.salt, h.hash))
    }

    @Test
    fun `wrong pin fails`() {
        val h = PinHasher.hashPin("123456")
        assertFalse(PinHasher.verify("654321", h.salt, h.hash))
        assertFalse(PinHasher.verify("12345", h.salt, h.hash))
        assertFalse(PinHasher.verify("", h.salt, h.hash))
    }

    @Test
    fun `same pin gets different salt each time`() {
        val h1 = PinHasher.hashPin("123456")
        val h2 = PinHasher.hashPin("123456")
        assertTrue(h1.salt != h2.salt)
        assertTrue(h1.hash != h2.hash)
        // each verifies against its own salt
        assertTrue(PinHasher.verify("123456", h1.salt, h1.hash))
        assertTrue(PinHasher.verify("123456", h2.salt, h2.hash))
    }

    @Test
    fun `cross salt verification fails`() {
        val h1 = PinHasher.hashPin("123456")
        val h2 = PinHasher.hashPin("123456")
        // h2's hash was derived with h2's salt — verifying against h1's salt must fail
        assertFalse(PinHasher.verify("123456", h1.salt, h2.hash))
    }

    @Test
    fun `short and long pins work`() {
        val h = PinHasher.hashPin("1234")
        assertTrue(PinHasher.verify("1234", h.salt, h.hash))
        val h2 = PinHasher.hashPin("1234567890123456")
        assertTrue(PinHasher.verify("1234567890123456", h2.salt, h2.hash))
    }
}
