package com.safekey.authenticator.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for [LanThreatAnalysis].
 *
 * The module is pure logic, so every behaviour the app relies on is pinned
 * here rather than on a device: the ARP text the kernel really prints (runs
 * of spaces, placeholder rows, IPv6 neighbours), the exact severity of each
 * code, and the boundary values (device-count threshold, blank gateway
 * values, empty inputs) that decide whether a user sees a warning at all.
 */
class LanThreatAnalysisTest {

    /** Short constructor for the many ARP rows these tests build. */
    private fun entry(ip: String, mac: String, iface: String = "wlan0"): LanArpEntry =
        LanArpEntry(ip = ip, mac = mac, iface = iface)

    /** Codes of a threat list, for compact assertions on whole lists. */
    private fun codes(threats: List<LanThreat>): List<String> =
        threats.map { threat -> threat.code }

    // ------------------------------------------------------------ arp table

    @Test
    fun `arp text with header and blank lines yields only device rows`() {
        val text = """
            IP address       HW type     Flags       HW address            Mask     Device
            192.168.1.1      0x1         0x2         aa:bb:cc:dd:ee:ff     *        wlan0

            192.168.1.24     0x1         0x2         11:22:33:44:55:66     *        wlan0
        """.trimIndent()
        val entries = LanThreatAnalysis.arpEntries(text)
        assertEquals(2, entries.size)
        assertEquals(entry("192.168.1.1", "aa:bb:cc:dd:ee:ff"), entries[0])
        assertEquals(entry("192.168.1.24", "11:22:33:44:55:66"), entries[1])
    }

    @Test
    fun `arp parser lowercases the mac and keeps ip and interface`() {
        val text = "10.0.0.7  0x1  0x2  AA:BB:CC:DD:EE:FF  *  eth0"
        val parsed = LanThreatAnalysis.arpEntries(text).single()
        assertEquals("10.0.0.7", parsed.ip)
        assertEquals("aa:bb:cc:dd:ee:ff", parsed.mac)
        assertEquals("eth0", parsed.iface)
    }

    @Test
    fun `arp rows with fewer than six fields are skipped`() {
        val text = "192.168.1.9  0x1  0x2  aa:bb:cc:dd:ee:ff"
        assertTrue(LanThreatAnalysis.arpEntries(text).isEmpty())
    }

    @Test
    fun `ipv6 neighbour rows are skipped`() {
        val text = "fe80::1c2b:3d4e:5f60:7182  0x1  0x2  aa:bb:cc:dd:ee:ff  *  wlan0"
        assertTrue(LanThreatAnalysis.arpEntries(text).isEmpty())
    }

    @Test
    fun `blank and header only arp text yield no entries`() {
        assertTrue(LanThreatAnalysis.arpEntries("").isEmpty())
        assertTrue(
            LanThreatAnalysis.arpEntries(
                "IP address       HW type     Flags       HW address            Mask     Device"
            ).isEmpty()
        )
    }

    @Test
    fun `arp parser keeps an all zero placeholder mac`() {
        val text = "192.168.1.30  0x1  0x0  00:00:00:00:00:00  *  wlan0"
        val parsed = LanThreatAnalysis.arpEntries(text).single()
        assertEquals("00:00:00:00:00:00", parsed.mac)
    }

    // ------------------------------------------------------- usable entries

    @Test
    fun `usable entries drop the all zero mac placeholder`() {
        val entries = listOf(
            entry("192.168.1.30", "00:00:00:00:00:00"),
            entry("192.168.1.1", "aa:bb:cc:dd:ee:ff")
        )
        assertEquals(
            listOf(entry("192.168.1.1", "aa:bb:cc:dd:ee:ff")),
            LanThreatAnalysis.usableArpEntries(entries)
        )
    }

    @Test
    fun `usable entries drop a malformed mac`() {
        val entries = listOf(
            entry("192.168.1.31", "aa:bb:cc"),
            entry("192.168.1.32", "zz:zz:zz:zz:zz:zz"),
            entry("192.168.1.33", "")
        )
        assertTrue(LanThreatAnalysis.usableArpEntries(entries).isEmpty())
    }

    @Test
    fun `usable entries drop a malformed ip`() {
        val entries = listOf(
            entry("192.168.1", "aa:bb:cc:dd:ee:ff"),
            entry("192.168.1.300", "aa:bb:cc:dd:ee:ff"),
            entry("fe80::1", "aa:bb:cc:dd:ee:ff"),
            entry("", "aa:bb:cc:dd:ee:ff")
        )
        assertTrue(LanThreatAnalysis.usableArpEntries(entries).isEmpty())
    }

    @Test
    fun `usable entries keep valid rows and accept an uppercase mac`() {
        val entries = listOf(
            entry("192.168.1.1", "AA:BB:CC:DD:EE:FF"),
            entry("192.168.1.2", "11:22:33:44:55:66")
        )
        assertEquals(entries, LanThreatAnalysis.usableArpEntries(entries))
    }

    @Test
    fun `usable entries of an empty list are empty`() {
        assertTrue(LanThreatAnalysis.usableArpEntries(emptyList()).isEmpty())
    }

    // ---------------------------------------------------------- spoofing

    @Test
    fun `same mac on two ips is a high mac conflict`() {
        val entries = listOf(
            entry("192.168.1.1", "aa:bb:cc:dd:ee:ff"),
            entry("192.168.1.50", "aa:bb:cc:dd:ee:ff")
        )
        val threat = LanThreatAnalysis.spoofingThreats(entries).single()
        assertEquals("arp_mac_conflict", threat.code)
        assertEquals(LanThreatSeverity.HIGH, threat.severity)
    }

    @Test
    fun `mac conflict detail lists the ips sorted and deduplicated`() {
        val entries = listOf(
            entry("10.0.0.7", "aa:bb:cc:dd:ee:ff"),
            entry("10.0.0.2", "aa:bb:cc:dd:ee:ff"),
            entry("10.0.0.7", "aa:bb:cc:dd:ee:ff")
        )
        val threat = LanThreatAnalysis.spoofingThreats(entries).single()
        assertTrue(threat.detail.contains("10.0.0.2, 10.0.0.7"))
        assertTrue(threat.detail.contains("2 IPs"))
    }

    @Test
    fun `same ip claimed by two macs is a medium ip conflict`() {
        val entries = listOf(
            entry("10.0.0.1", "aa:bb:cc:dd:ee:ff"),
            entry("10.0.0.1", "11:22:33:44:55:66")
        )
        val threat = LanThreatAnalysis.spoofingThreats(entries).single()
        assertEquals("arp_ip_conflict", threat.code)
        assertEquals(LanThreatSeverity.MEDIUM, threat.severity)
    }

    @Test
    fun `ip conflict detail lists the macs sorted`() {
        val entries = listOf(
            entry("10.0.0.1", "aa:bb:cc:dd:ee:ff"),
            entry("10.0.0.1", "11:22:33:44:55:66")
        )
        val threat = LanThreatAnalysis.spoofingThreats(entries).single()
        assertTrue(threat.detail.contains("11:22:33:44:55:66, aa:bb:cc:dd:ee:ff"))
    }

    @Test
    fun `unique mac ip pairs produce no threats`() {
        val entries = listOf(
            entry("192.168.1.1", "aa:bb:cc:dd:ee:ff"),
            entry("192.168.1.2", "11:22:33:44:55:66")
        )
        assertTrue(LanThreatAnalysis.spoofingThreats(entries).isEmpty())
    }

    @Test
    fun `repeated identical rows are not a conflict`() {
        val entries = listOf(
            entry("192.168.1.5", "aa:bb:cc:dd:ee:ff"),
            entry("192.168.1.5", "aa:bb:cc:dd:ee:ff")
        )
        assertTrue(LanThreatAnalysis.spoofingThreats(entries).isEmpty())
    }

    @Test
    fun `placeholder macs do not produce spoofing threats`() {
        val entries = listOf(
            entry("192.168.1.30", "00:00:00:00:00:00"),
            entry("192.168.1.31", "00:00:00:00:00:00")
        )
        assertTrue(LanThreatAnalysis.spoofingThreats(entries).isEmpty())
    }

    @Test
    fun `empty arp table produces no spoofing threats`() {
        assertTrue(LanThreatAnalysis.spoofingThreats(emptyList()).isEmpty())
    }

    @Test
    fun `spoofing reports mac conflicts before ip conflicts`() {
        val entries = listOf(
            entry("10.0.0.1", "aa:bb:cc:dd:ee:ff"),
            entry("10.0.0.1", "11:22:33:44:55:66"),
            entry("10.0.0.2", "11:22:33:44:55:66"),
            entry("10.0.0.3", "11:22:33:44:55:66")
        )
        assertEquals(
            listOf("arp_mac_conflict", "arp_ip_conflict"),
            codes(LanThreatAnalysis.spoofingThreats(entries))
        )
    }

    // ------------------------------------------------------- virtual macs

    @Test
    fun `hypervisor ouis are virtual`() {
        val ouis = listOf(
            "00:50:56", "00:0c:29", "00:05:69", "00:1c:14", "08:00:27",
            "0a:00:27", "00:15:5d", "00:16:3e", "52:54:00"
        )
        for (oui in ouis) {
            assertTrue(oui, LanThreatAnalysis.isVirtualMac("$oui:aa:bb:cc"))
        }
    }

    @Test
    fun `locally administered macs are virtual`() {
        assertTrue(LanThreatAnalysis.isVirtualMac("02:11:22:33:44:55"))
        assertTrue(LanThreatAnalysis.isVirtualMac("06:11:22:33:44:55"))
        assertTrue(LanThreatAnalysis.isVirtualMac("0a:11:22:33:44:55"))
        assertTrue(LanThreatAnalysis.isVirtualMac("0e:11:22:33:44:55"))
    }

    @Test
    fun `ordinary macs are not virtual`() {
        assertFalse(LanThreatAnalysis.isVirtualMac("5c:e9:1e:aa:bb:cc"))
        assertFalse(LanThreatAnalysis.isVirtualMac("00:1a:2b:3c:4d:5e"))
    }

    @Test
    fun `virtual mac check is case insensitive`() {
        assertTrue(LanThreatAnalysis.isVirtualMac("0A:00:27:AB:CD:EF"))
        assertTrue(LanThreatAnalysis.isVirtualMac("0a0027aabbcc"))
        assertFalse(LanThreatAnalysis.isVirtualMac("5C:E9:1E:AA:BB:CC"))
    }

    @Test
    fun `garbage text is not a virtual mac`() {
        assertFalse(LanThreatAnalysis.isVirtualMac(""))
        assertFalse(LanThreatAnalysis.isVirtualMac("   "))
        assertFalse(LanThreatAnalysis.isVirtualMac("not a mac at all"))
        assertFalse(LanThreatAnalysis.isVirtualMac("00:00:00:00:00:00"))
    }

    // --------------------------------------------------------- gateway mac

    @Test
    fun `missing gateway mac values produce no threat`() {
        assertNull(LanThreatAnalysis.gatewayChangeThreat(null, "aa:bb:cc:dd:ee:ff"))
        assertNull(LanThreatAnalysis.gatewayChangeThreat("aa:bb:cc:dd:ee:ff", null))
        assertNull(LanThreatAnalysis.gatewayChangeThreat(null, null))
    }

    @Test
    fun `blank gateway mac values produce no threat`() {
        assertNull(LanThreatAnalysis.gatewayChangeThreat("", ""))
        assertNull(LanThreatAnalysis.gatewayChangeThreat("  ", "  "))
    }

    @Test
    fun `unchanged gateway mac produces no threat`() {
        assertNull(
            LanThreatAnalysis.gatewayChangeThreat("aa:bb:cc:dd:ee:ff", "aa:bb:cc:dd:ee:ff")
        )
        assertNull(
            LanThreatAnalysis.gatewayChangeThreat("AA:BB:CC:DD:EE:FF", "aa:bb:cc:dd:ee:ff")
        )
    }

    @Test
    fun `changed gateway mac is a high severity threat`() {
        val threat = LanThreatAnalysis.gatewayChangeThreat(
            "aa:bb:cc:dd:ee:ff",
            "11:22:33:44:55:66"
        )
        assertNotNull(threat)
        assertEquals("gateway_changed", threat!!.code)
        assertEquals(LanThreatSeverity.HIGH, threat.severity)
        assertTrue(threat.detail.contains("aa:bb:cc:dd:ee:ff"))
        assertTrue(threat.detail.contains("11:22:33:44:55:66"))
    }

    @Test
    fun `virtual gateway mac is a medium threat`() {
        val threat = LanThreatAnalysis.gatewayVirtualThreat("52:54:00:aa:bb:cc")
        assertNotNull(threat)
        assertEquals("gateway_virtual", threat!!.code)
        assertEquals(LanThreatSeverity.MEDIUM, threat.severity)
    }

    @Test
    fun `physical or blank gateway mac is not flagged`() {
        assertNull(LanThreatAnalysis.gatewayVirtualThreat(null))
        assertNull(LanThreatAnalysis.gatewayVirtualThreat(""))
        assertNull(LanThreatAnalysis.gatewayVirtualThreat("   "))
        assertNull(LanThreatAnalysis.gatewayVirtualThreat("5c:e9:1e:aa:bb:cc"))
    }

    // --------------------------------------------------------- link state

    @Test
    fun `open wifi is a high severity threat`() {
        val threats = LanThreatAnalysis.linkThreats(
            security = LanLinkSecurity.OPEN,
            vpnActive = false,
            proxyHost = null,
            isP2p = false,
            arpDeviceCount = 3
        )
        val threat = threats.single()
        assertEquals("wifi_open", threat.code)
        assertEquals(LanThreatSeverity.HIGH, threat.severity)
    }

    @Test
    fun `wep wifi is a high severity threat`() {
        val threat = LanThreatAnalysis.linkThreats(
            security = LanLinkSecurity.WEP,
            vpnActive = false,
            proxyHost = null,
            isP2p = false,
            arpDeviceCount = 3
        ).single()
        assertEquals("wifi_wep", threat.code)
        assertEquals(LanThreatSeverity.HIGH, threat.severity)
    }

    @Test
    fun `enterprise wifi is a medium threat`() {
        val threat = LanThreatAnalysis.linkThreats(
            security = LanLinkSecurity.ENTERPRISE,
            vpnActive = false,
            proxyHost = null,
            isP2p = false,
            arpDeviceCount = 3
        ).single()
        assertEquals("wifi_enterprise", threat.code)
        assertEquals(LanThreatSeverity.MEDIUM, threat.severity)
    }

    @Test
    fun `wpa wpa2 wpa3 and unknown links alone produce no threat`() {
        for (security in listOf(
            LanLinkSecurity.WPA,
            LanLinkSecurity.WPA2,
            LanLinkSecurity.WPA3,
            LanLinkSecurity.UNKNOWN
        )) {
            val threats = LanThreatAnalysis.linkThreats(
                security = security,
                vpnActive = false,
                proxyHost = null,
                isP2p = false,
                arpDeviceCount = 3
            )
            assertTrue(security.name, threats.isEmpty())
        }
    }

    @Test
    fun `http proxy host is a medium threat`() {
        val threat = LanThreatAnalysis.linkThreats(
            security = LanLinkSecurity.WPA2,
            vpnActive = false,
            proxyHost = "proxy.example.net",
            isP2p = false,
            arpDeviceCount = 3
        ).single()
        assertEquals("http_proxy", threat.code)
        assertEquals(LanThreatSeverity.MEDIUM, threat.severity)
        assertTrue(threat.detail.contains("proxy.example.net"))
    }

    @Test
    fun `blank proxy host produces no threat`() {
        for (host in listOf(null, "", "   ")) {
            val threats = LanThreatAnalysis.linkThreats(
                security = LanLinkSecurity.WPA2,
                vpnActive = false,
                proxyHost = host,
                isP2p = false,
                arpDeviceCount = 3
            )
            assertTrue(threats.isEmpty())
        }
    }

    @Test
    fun `active vpn is a low threat`() {
        val threat = LanThreatAnalysis.linkThreats(
            security = LanLinkSecurity.WPA2,
            vpnActive = true,
            proxyHost = null,
            isP2p = false,
            arpDeviceCount = 3
        ).single()
        assertEquals("vpn_active", threat.code)
        assertEquals(LanThreatSeverity.LOW, threat.severity)
    }

    @Test
    fun `crowded arp table is a low threat`() {
        val threat = LanThreatAnalysis.linkThreats(
            security = LanLinkSecurity.WPA2,
            vpnActive = false,
            proxyHost = null,
            isP2p = false,
            arpDeviceCount = 25
        ).single()
        assertEquals("crowded_lan", threat.code)
        assertEquals(LanThreatSeverity.LOW, threat.severity)
    }

    @Test
    fun `arp table at the threshold produces no crowded threat`() {
        for (count in listOf(0, 1, 24)) {
            val threats = LanThreatAnalysis.linkThreats(
                security = LanLinkSecurity.WPA2,
                vpnActive = false,
                proxyHost = null,
                isP2p = false,
                arpDeviceCount = count
            )
            assertTrue(threats.isEmpty())
        }
    }

    @Test
    fun `p2p flag currently produces no threat`() {
        val threats = LanThreatAnalysis.linkThreats(
            security = LanLinkSecurity.WPA2,
            vpnActive = false,
            proxyHost = null,
            isP2p = true,
            arpDeviceCount = 3
        )
        assertTrue(threats.isEmpty())
    }

    @Test
    fun `all weak link properties produce one threat each`() {
        val threats = LanThreatAnalysis.linkThreats(
            security = LanLinkSecurity.OPEN,
            vpnActive = true,
            proxyHost = "10.0.0.1",
            isP2p = true,
            arpDeviceCount = 40
        )
        assertEquals(
            listOf("wifi_open", "http_proxy", "vpn_active", "crowded_lan"),
            codes(threats)
        )
    }

    // --------------------------------------------- ordering and hand-off

    @Test
    fun `sorted orders high then medium then low`() {
        val threats = listOf(
            LanThreat("vpn_active", LanThreatSeverity.LOW),
            LanThreat("arp_ip_conflict", LanThreatSeverity.MEDIUM),
            LanThreat("wifi_open", LanThreatSeverity.HIGH),
            LanThreat("crowded_lan", LanThreatSeverity.LOW)
        )
        assertEquals(
            listOf("wifi_open", "arp_ip_conflict", "vpn_active", "crowded_lan"),
            codes(LanThreatAnalysis.sorted(threats))
        )
    }

    @Test
    fun `sorted keeps input order for equal severities`() {
        val threats = listOf(
            LanThreat("wifi_wep", LanThreatSeverity.HIGH),
            LanThreat("gateway_changed", LanThreatSeverity.HIGH),
            LanThreat("arp_mac_conflict", LanThreatSeverity.HIGH)
        )
        assertEquals(
            listOf("wifi_wep", "gateway_changed", "arp_mac_conflict"),
            codes(LanThreatAnalysis.sorted(threats))
        )
    }

    @Test
    fun `sorted of an empty list is empty`() {
        assertTrue(LanThreatAnalysis.sorted(emptyList()).isEmpty())
    }

    @Test
    fun `worst returns the highest severity present`() {
        val threats = listOf(
            LanThreat("vpn_active", LanThreatSeverity.LOW),
            LanThreat("gateway_changed", LanThreatSeverity.HIGH),
            LanThreat("http_proxy", LanThreatSeverity.MEDIUM)
        )
        assertEquals(LanThreatSeverity.HIGH, LanThreatAnalysis.worst(threats))
    }

    @Test
    fun `worst is null for an empty list`() {
        assertNull(LanThreatAnalysis.worst(emptyList()))
    }

    @Test
    fun `summary codes keep input order and drop duplicates`() {
        val threats = listOf(
            LanThreat("wifi_open", LanThreatSeverity.HIGH),
            LanThreat("vpn_active", LanThreatSeverity.LOW),
            LanThreat("wifi_open", LanThreatSeverity.HIGH),
            LanThreat("vpn_active", LanThreatSeverity.LOW)
        )
        assertEquals(
            listOf("wifi_open", "vpn_active"),
            LanThreatAnalysis.summaryCodes(threats)
        )
    }

    @Test
    fun `summary codes of an empty list are empty`() {
        assertTrue(LanThreatAnalysis.summaryCodes(emptyList()).isEmpty())
    }

    @Test
    fun `a real arp table flows through the whole analysis`() {
        // One spoofed gateway (same MAC on two IPs) plus a crowded hotspot
        // link: the pipeline must end with the worst signal HIGH and hand the
        // peer nothing but the stable codes, not the addresses behind them.
        val text = """
            IP address       HW type     Flags       HW address            Mask     Device
            192.168.1.1      0x1         0x2         aa:bb:cc:dd:ee:ff     *        wlan0
            192.168.1.99     0x1         0x2         AA:BB:CC:DD:EE:FF     *        wlan0
            192.168.1.30     0x1         0x0         00:00:00:00:00:00     *        wlan0
        """.trimIndent()
        val parsed = LanThreatAnalysis.arpEntries(text)
        assertEquals(3, parsed.size)
        val spoofing = LanThreatAnalysis.spoofingThreats(parsed)
        assertEquals(listOf("arp_mac_conflict"), codes(spoofing))
        val link = LanThreatAnalysis.linkThreats(
            security = LanLinkSecurity.OPEN,
            vpnActive = false,
            proxyHost = null,
            isP2p = false,
            arpDeviceCount = 40
        )
        val all = LanThreatAnalysis.sorted(spoofing + link)
        assertEquals(LanThreatSeverity.HIGH, LanThreatAnalysis.worst(all))
        assertEquals(
            listOf("arp_mac_conflict", "wifi_open", "crowded_lan"),
            LanThreatAnalysis.summaryCodes(all)
        )
        assertTrue(all.any { threat -> threat.detail.contains("192.168.1.99") })
    }
}
