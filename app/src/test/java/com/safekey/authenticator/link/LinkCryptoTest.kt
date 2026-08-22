package com.safekey.authenticator.link

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class LinkCryptoTest {
    @Test
    fun `ephemeral keys derive the same session key`() {
        val a = LinkCrypto.generateKeyPair()
        val b = LinkCrypto.generateKeyPair()
        val context = "osmium-link-v1".toByteArray()
        assertArrayEquals(LinkCrypto.deriveKey(a.private, b.public, context), LinkCrypto.deriveKey(b.private, a.public, context))
    }

    @Test
    fun `authenticated encryption round trips`() {
        val a = LinkCrypto.generateKeyPair()
        val b = LinkCrypto.generateKeyPair()
        val key = LinkCrypto.deriveKey(a.private, b.public, "osmium-link-v1".toByteArray())
        val plaintext = "account metadata only".toByteArray()
        val encoded = LinkCrypto.encrypt(key, plaintext, "metadata".toByteArray())
        assertEquals(String(plaintext), String(LinkCrypto.decrypt(key, encoded, "metadata".toByteArray())))
    }
}
