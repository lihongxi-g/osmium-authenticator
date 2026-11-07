package com.safekey.authenticator.network

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The send path must refuse anything that could carry the vault off the LAN. */
class LanSendTargetTest {

    private fun local(host: String) = LanSendTarget.isLocalPeer(host)

    @Test
    fun privateRangesAreAccepted() {
        assertTrue(local("192.168.1.5"))
        assertTrue(local("10.0.0.7"))
        assertTrue(local("172.16.5.4"))
        assertTrue(local("172.31.255.254"))
        assertTrue(local("100.64.0.9"))          // carrier-grade NAT
        assertTrue(local("169.254.10.10"))       // link-local, cannot route off-link
        assertTrue(local("fd00::1"))             // IPv6 unique-local
        assertTrue(local("fe80::1"))             // IPv6 link-local
    }

    @Test
    fun publicAddressesAreRefused() {
        assertFalse(local("8.8.8.8"))
        assertFalse(local("172.32.0.1"))         // just outside 172.16/12
        assertFalse(local("1.1.1.1"))
        assertFalse(local("2001:4860:4860::8888"))
    }

    @Test
    fun addressesThatAreNotAPeerAtAllAreRefused() {
        assertFalse(local("127.0.0.1"))          // loopback
        assertFalse(local("0.0.0.0"))            // unspecified
        assertFalse(local("224.0.0.1"))          // multicast
        assertFalse(local("239.255.255.250"))    // SSDP multicast
        assertFalse(local("255.255.255.255"))    // broadcast
        assertFalse(local("::1"))
    }

    @Test
    fun bracketAndPortArtifactsAreTolerated() {
        assertEquals(true, LanSendTarget.literalIsLocalUnicast("[192.168.1.5]"))
        assertEquals(true, LanSendTarget.literalIsLocalUnicast("192.168.1.5."))
        assertEquals(true, LanSendTarget.literalIsLocalUnicast(" 192.168.1.5 "))
    }

    @Test
    fun aNonLiteralHostIsLeftToTheIoPath() {
        assertNull("host names are resolved off the main thread", LanSendTarget.literalIsLocalUnicast("myphone.local"))
        assertNull(LanSendTarget.literalIsLocalUnicast(""))
        assertFalse("an unresolvable name is refused", LanSendTarget.isLocalPeer("no-such-host.invalid"))
    }

    @Test
    fun multicastIsRefusedWhereLocalNetworkPolicyAcceptsIt() {
        // LocalNetworkPolicy counts multicast as "local" (it answers a permission
        // question); the send path must not.
        val multicast = InetAddress.getByName("239.255.255.250")
        assertTrue(LocalNetworkPolicy.isLocalAddress(multicast))
        assertFalse(LanSendTarget.isLocalUnicast(multicast))
    }
}
