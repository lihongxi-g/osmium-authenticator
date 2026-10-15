package com.safekey.authenticator.network

import java.net.InetAddress
import org.junit.Assert.*
import org.junit.Test

class LocalNetworkPolicyTest {
    @Test fun permissionStartsAtApi37AndTarget37() {
        assertFalse(LocalNetworkPolicy.permissionRequired(36, 37))
        assertFalse(LocalNetworkPolicy.permissionRequired(37, 36))
        assertTrue(LocalNetworkPolicy.permissionRequired(37, 37))
        assertTrue(LocalNetworkPolicy.permissionRequired(38, 37))
    }

    @Test fun localAndPublicAddressesAreDistinguishedWithoutDns() {
        for (address in listOf("192.168.1.4", "10.0.0.1", "172.16.0.2", "169.254.1.2", "100.64.0.1", "100.127.255.254", "255.255.255.255", "fd12::1", "fe80::1", "224.0.0.251", "ff02::fb")) {
            assertTrue(address, LocalNetworkPolicy.isLocalAddress(InetAddress.getByName(address)))
        }
        for (address in listOf("8.8.8.8", "1.1.1.1", "100.63.255.254", "100.128.0.1", "172.32.0.1", "2606:4700:4700::1111", "127.0.0.1", "::1")) {
            assertFalse(address, LocalNetworkPolicy.isLocalAddress(InetAddress.getByName(address)))
        }
    }

    @Test fun dotLocalDoesNotResolveBeforePermission() {
        assertTrue(LocalNetworkPolicy.webDavUsesLocalNetwork("https://nas.local:5006/dav") {
            error("mDNS must not run before permission")
        })
    }

    @Test fun publicAndSplitDnsServersUseTheirResolvedAddresses() {
        assertFalse(LocalNetworkPolicy.webDavUsesLocalNetwork("https://dav.example.com/") {
            arrayOf(InetAddress.getByName("8.8.8.8"))
        })
        assertTrue(LocalNetworkPolicy.webDavUsesLocalNetwork("https://nas.example.com/") {
            arrayOf(InetAddress.getByName("8.8.8.8"), InetAddress.getByName("192.168.1.3"))
        })
    }
}