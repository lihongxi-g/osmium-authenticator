package com.safekey.authenticator.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A QR payload is a pairing credential handed over by a camera: anything on the
 * local network may print one, so every field has to be validated before the app
 * connects to it.
 */
class LanPairingQrTest {

    @Test
    fun roundTripCarriesAddressAndCode() {
        val payload = LanPairingQr.encode("192.168.5.23", 52026, "123456789012")
        assertEquals("OSMIUM1|192.168.5.23|52026|123456789012", payload)
        val parsed = LanPairingQr.parse(payload!!)!!
        assertEquals("192.168.5.23", parsed.host)
        assertEquals(52026, parsed.port)
        assertEquals("123456789012", parsed.code)
    }

    @Test
    fun encodeRefusesAnIncompleteSession() {
        assertNull(LanPairingQr.encode("", 52026, "123456789012"))
        assertNull(LanPairingQr.encode("192.168.5.23", 0, "123456789012"))
        assertNull(LanPairingQr.encode("192.168.5.23", 70000, "123456789012"))
        assertNull(LanPairingQr.encode("192.168.5.23", 52026, "12345"))
        assertNull(LanPairingQr.encode("192.168.5.23", 52026, "12345678901a"))
    }

    @Test
    fun aForeignQrCodeIsRefused() {
        assertNull(LanPairingQr.parse("otpauth://totp/Example?secret=ABC"))
        assertNull(LanPairingQr.parse("https://example.com"))
        assertNull(LanPairingQr.parse("OSMIUM0|192.168.5.23|52026|123456789012"))
        assertNull(LanPairingQr.parse("OSMIUM1|192.168.5.23|52026"))
        assertNull(LanPairingQr.parse("OSMIUM1|192.168.5.23|52026|123456789012|extra"))
    }

    @Test
    fun malformedFieldsAreRefused() {
        assertNull(LanPairingQr.parse("OSMIUM1|192.168.5.23|port|123456789012"))
        assertNull(LanPairingQr.parse("OSMIUM1|192.168.5.23|0|123456789012"))
        assertNull(LanPairingQr.parse("OSMIUM1|192.168.5.23|70000|123456789012"))
        assertNull(LanPairingQr.parse("OSMIUM1|192.168.5.23|52026|12345"))
        assertNull(LanPairingQr.parse("OSMIUM1||52026|123456789012"))
    }

    @Test
    fun aPublicTargetIsRefused() {
        // Someone can print a QR that points anywhere; the send path only talks
        // to the local network, so the payload must not be able to redirect it.
        assertNull(LanPairingQr.parse("OSMIUM1|8.8.8.8|52026|123456789012"))
        assertNull(LanPairingQr.parse("OSMIUM1|example.com|52026|123456789012"))
    }

    @Test
    fun multicastAndBroadcastTargetsAreRefused() {
        assertNull(LanPairingQr.parse("OSMIUM1|224.0.0.1|52026|123456789012"))
        assertNull(LanPairingQr.parse("OSMIUM1|255.255.255.255|52026|123456789012"))
        assertNull(LanPairingQr.parse("OSMIUM1|127.0.0.1|52026|123456789012"))
    }

    @Test
    fun surroundingWhitespaceIsTolerated() {
        val parsed = LanPairingQr.parse("  OSMIUM1| 192.168.5.23 | 52026 | 123456789012 ")
        assertEquals("192.168.5.23", parsed!!.host)
        assertEquals("123456789012", parsed.code)
    }
}
