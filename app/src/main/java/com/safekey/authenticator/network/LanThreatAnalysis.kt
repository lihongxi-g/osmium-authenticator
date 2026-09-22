package com.safekey.authenticator.network

/**
 * Local-network threat analysis — pure functions over the ARP table, the
 * Wi-Fi link state and a few device settings, with no Android dependencies,
 * so the whole decision table is unit-tested on the JVM in
 * LanThreatAnalysisTest.
 *
 * Why an offline authenticator cares: the codes it produces are typed into
 * whatever network the phone is on, so the phone's own view of that network
 * is part of its threat model. The module answers three questions from
 * evidence the app can really read — is this ARP table consistent, is the
 * gateway still the one this session started with, is the link itself weak —
 * and turns each hit into a [LanThreat] carrying a stable code. Only the
 * codes travel to a peer device ([summaryCodes]); the details stay local.
 */
internal object LanThreatAnalysis {

    /** Stable wire codes. The peer compares these strings, never the details. */
    private const val CODE_ARP_MAC_CONFLICT = "arp_mac_conflict"
    private const val CODE_ARP_IP_CONFLICT = "arp_ip_conflict"
    private const val CODE_GATEWAY_CHANGED = "gateway_changed"
    private const val CODE_GATEWAY_VIRTUAL = "gateway_virtual"
    private const val CODE_WIFI_OPEN = "wifi_open"
    private const val CODE_WIFI_WEP = "wifi_wep"
    private const val CODE_WIFI_ENTERPRISE = "wifi_enterprise"
    private const val CODE_HTTP_PROXY = "http_proxy"
    private const val CODE_VPN_ACTIVE = "vpn_active"
    private const val CODE_CROWDED_LAN = "crowded_lan"

    /** /proc/net/arp has 6 columns; anything shorter is not a full row. */
    private const val ARP_FIELDS = 6

    /** Field index of the hardware address in a /proc/net/arp row. */
    private const val ARP_MAC_FIELD = 3

    /** Field index of the interface name in a /proc/net/arp row. */
    private const val ARP_IFACE_FIELD = 5

    /**
     * A home LAN is rarely this large; a bigger neighbour table is the
     * signature of a shared/public hotspot rather than a trusted network.
     */
    private const val CROWDED_DEVICE_COUNT = 24

    /**
     * OUIs that a physical router or phone never ships with: hypervisor
     * default adapters (VMware, VirtualBox, Hyper-V, Xen, QEMU/KVM) plus the
     * emulator variant of the VirtualBox block. A gateway on one of these
     * means the traffic is terminated on a computer or VM — a shared
     * uplink, a lab sandbox, or a deliberately inserted relay.
     */
    private val VIRTUAL_OUIS = setOf(
        "005056",
        "000c29",
        "000569",
        "001c14",
        "080027",
        "0a0027",
        "00155d",
        "00163e",
        "525400"
    )

    /**
     * Second hex digit of the first octet values that set the locally
     * administered bit: such an address was chosen by software, not burnt
     * into a card (Android MAC randomisation, virtual access points).
     */
    private val LOCALLY_ADMINISTERED_DIGITS = setOf('2', '6', 'a', 'e')

    /** Full MAC as 12 hex digits, and the bare-OUI form as 6. */
    private const val MAC_HEX_LENGTH = 12
    private const val OUI_HEX_LENGTH = 6

    /** Characters a MAC may consist of; anything else is not a MAC. */
    private val MAC_CHARS = Regex("[0-9a-f:-]+")

    /** /proc/net/arp pads its columns with runs of spaces, not single ones. */
    private val ARP_WHITESPACE = Regex("\\s+")

    private val IPV4_PATTERN = Regex("(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})")

    /**
     * Parses /proc/net/arp text. Header row and malformed rows are skipped.
     *
     * The kernel prints this file column-aligned with runs of spaces, so
     * splitting on single spaces would yield empty fields; IPv6 neighbours
     * are skipped because those rows carry no MAC this analysis can use.
     * MACs are lowercased here once, so that no other function in this
     * module has to care about the casing a device happened to print.
     */
    fun arpEntries(text: String): List<LanArpEntry> {
        val out = mutableListOf<LanArpEntry>()
        for (line in text.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            if (trimmed.startsWith("IP address", ignoreCase = true)) continue
            val fields = trimmed.split(ARP_WHITESPACE)
            if (fields.size < ARP_FIELDS) continue
            if (fields[0].contains(':')) continue
            val entry = LanArpEntry(
                ip = fields[0],
                mac = fields[ARP_MAC_FIELD].lowercase(),
                iface = fields[ARP_IFACE_FIELD]
            )
            out += entry
        }
        return out
    }

    /**
     * Entries whose MAC is all zeros or whose address is malformed are
     * dropped.
     *
     * A row is written even before the kernel has resolved an address
     * (00:00:00:00:00:00), and comparing those placeholder rows would make
     * every device on the LAN look like part of a conflict. Entries are kept
     * otherwise, including ones not seen in the ARP table at all.
     */
    fun usableArpEntries(entries: List<LanArpEntry>): List<LanArpEntry> =
        entries.filter { entry ->
            isCompleteMac(entry.mac) && !isZeroMac(entry.mac) && isUsableIpv4(entry.ip)
        }

    /**
     * Duplicate MAC on several IPs, or one IP seen with several MACs.
     *
     * This is what gratuitous-ARP based spoofing looks like from a non-root
     * app: an attacker answers for a MAC that already belongs to an address,
     * or claims several addresses with one card. Only usable rows are
     * compared — placeholder rows would otherwise manufacture conflicts. The
     * MAC conflict is HIGH (a second card is wearing an existing identity),
     * the IP conflict MEDIUM (one card answering for many addresses is odd,
     * but gateways and hotspots do it legitimately).
     *
     * Group order follows first appearance, so the result is deterministic
     * for a given ARP table.
     */
    fun spoofingThreats(entries: List<LanArpEntry>): List<LanThreat> {
        val ipsByMac = LinkedHashMap<String, LinkedHashSet<String>>()
        val macsByIp = LinkedHashMap<String, LinkedHashSet<String>>()
        for (entry in usableArpEntries(entries)) {
            val mac = entry.mac.lowercase()
            ipsByMac.getOrPut(mac) { LinkedHashSet() }.add(entry.ip)
            macsByIp.getOrPut(entry.ip) { LinkedHashSet() }.add(mac)
        }
        val threats = mutableListOf<LanThreat>()
        for ((mac, ips) in ipsByMac) {
            if (ips.size < 2) continue
            val listed = ips.sorted().joinToString(", ")
            threats += LanThreat(
                code = CODE_ARP_MAC_CONFLICT,
                severity = LanThreatSeverity.HIGH,
                detail = "MAC $mac answers for ${ips.size} IPs: $listed"
            )
        }
        for ((ip, macs) in macsByIp) {
            if (macs.size < 2) continue
            val listed = macs.sorted().joinToString(", ")
            threats += LanThreat(
                code = CODE_ARP_IP_CONFLICT,
                severity = LanThreatSeverity.MEDIUM,
                detail = "IP $ip is claimed by ${macs.size} MACs: $listed"
            )
        }
        return threats
    }

    /**
     * Known virtual/randomized OUIs (VMware/VirtualBox/Hyper-V/Xen/QEMU,
     * locally administered bit).
     *
     * Two families are checked because they catch different things: the OUI
     * block catches ordinary hypervisor adapters, and the locally
     * administered bit catches addresses that software chose rather than a
     * manufacturer (Android randomisation, virtual APs). Case-insensitive;
     * text that is not a MAC, or a bare OUI, is judged on its OUI only —
     * a half address says nothing about who picked it.
     */
    fun isVirtualMac(mac: String): Boolean {
        val plain = normalizeMac(mac) ?: return false
        if (VIRTUAL_OUIS.any { oui -> plain.startsWith(oui) }) return true
        return plain.length == MAC_HEX_LENGTH && plain[1] in LOCALLY_ADMINISTERED_DIGITS
    }

    /**
     * Gateway MAC changed since it was first seen in this session.
     *
     * The gateway MAC is the one address an attacker on the same LAN must
     * impersonate to read this device's traffic, so a change *within one
     * session* is the cheapest strong signal available. A change is
     * legitimate across sessions (router swap, reconnect, AP randomisation),
     * which is why the caller passes what it remembered rather than the
     * analysis guessing. Unknown values are a miss, never a change.
     */
    fun gatewayChangeThreat(previous: String?, current: String?): LanThreat? {
        val before = previous?.trim()
        val after = current?.trim()
        if (before == null || after == null) return null
        if (before.isEmpty() && after.isEmpty()) return null
        if (before.equals(after, ignoreCase = true)) return null
        return LanThreat(
            code = CODE_GATEWAY_CHANGED,
            severity = LanThreatSeverity.HIGH,
            detail = "gateway MAC changed: ${before.ifEmpty { "(unknown)" }} -> " +
                after.ifEmpty { "(unknown)" }
        )
    }

    /**
     * Gateway MAC that looks like a virtual machine/hotspot share.
     *
     * If the router this device routes through is a hypervisor adapter, the
     * traffic is handled by a computer sharing its uplink — a laptop
     * hotspot, a VM sandbox, or a relay someone placed on purpose. MEDIUM,
     * not HIGH: captive portals and lab setups look exactly the same, so
     * this is a hint the user can confirm, not a verdict.
     */
    fun gatewayVirtualThreat(current: String?): LanThreat? {
        val mac = current?.trim().orEmpty()
        if (mac.isEmpty()) return null
        if (!isVirtualMac(mac)) return null
        return LanThreat(
            code = CODE_GATEWAY_VIRTUAL,
            severity = LanThreatSeverity.MEDIUM,
            detail = "gateway MAC $mac belongs to a virtual/randomized adapter"
        )
    }

    /**
     * Link properties (open/WEP/enterprise Wi-Fi, proxy, VPN, crowded
     * network).
     *
     * These describe the connection the user chose, which is why they are
     * reported next to — not mixed into — the ARP findings: the fix is
     * usually a setting, not a cleanup. OPEN and WEP are HIGH because a
     * passive listener on such a link reads every login in clear; only a
     * much weaker signal (a proxy the user may not have set, a VPN, a large
     * neighbour table) is MEDIUM or LOW. [isP2p] is kept for future use — a
     * Wi-Fi Direct transfer of this app's own data is not a risk today — and
     * [arpDeviceCount] only flags unusually large tables, the hallmark of a
     * public hotspot.
     */
    @Suppress("UNUSED_PARAMETER")
    fun linkThreats(
        security: LanLinkSecurity,
        vpnActive: Boolean,
        proxyHost: String?,
        isP2p: Boolean,
        arpDeviceCount: Int
    ): List<LanThreat> {
        val threats = mutableListOf<LanThreat>()
        when (security) {
            LanLinkSecurity.OPEN -> threats += LanThreat(
                code = CODE_WIFI_OPEN,
                severity = LanThreatSeverity.HIGH,
                detail = "Wi-Fi is open, traffic is readable by anyone in range"
            )
            LanLinkSecurity.WEP -> threats += LanThreat(
                code = CODE_WIFI_WEP,
                severity = LanThreatSeverity.HIGH,
                detail = "Wi-Fi uses WEP, which is broken and crackable in minutes"
            )
            LanLinkSecurity.ENTERPRISE -> threats += LanThreat(
                code = CODE_WIFI_ENTERPRISE,
                severity = LanThreatSeverity.MEDIUM,
                detail = "Wi-Fi uses WPA-Enterprise: trust the profile, not the SSID"
            )
            LanLinkSecurity.WPA,
            LanLinkSecurity.WPA2,
            LanLinkSecurity.WPA3,
            LanLinkSecurity.UNKNOWN -> Unit
        }
        val proxy = proxyHost?.trim().orEmpty()
        if (proxy.isNotEmpty()) {
            threats += LanThreat(
                code = CODE_HTTP_PROXY,
                severity = LanThreatSeverity.MEDIUM,
                detail = "HTTP proxy configured: $proxy"
            )
        }
        if (vpnActive) {
            threats += LanThreat(
                code = CODE_VPN_ACTIVE,
                severity = LanThreatSeverity.LOW,
                detail = "VPN is active, traffic leaves through a tunnel"
            )
        }
        if (arpDeviceCount > CROWDED_DEVICE_COUNT) {
            threats += LanThreat(
                code = CODE_CROWDED_LAN,
                severity = LanThreatSeverity.LOW,
                detail = "$arpDeviceCount neighbours in the ARP table (public network?)"
            )
        }
        return threats
    }

    /**
     * All threats, HIGH first, stable order for equal severities.
     *
     * The UI and the peer payload both walk this list, so the order has to
     * be explainable to the user (most urgent first) and reproducible across
     * runs. Kotlin's sortedByDescending is a stable sort, so threats of the
     * same severity keep the order of the detectors that produced them.
     */
    fun sorted(threats: List<LanThreat>): List<LanThreat> =
        threats.sortedByDescending { severityRank(it.severity) }

    /**
     * Highest severity present, or null when the list is empty.
     *
     * Callers want one headline ("how bad is this network") without walking
     * the individual codes. Null must stay distinguishable from LOW: an
     * empty list means nothing was observed, which is a miss, not a verdict.
     */
    fun worst(threats: List<LanThreat>): LanThreatSeverity? =
        threats.maxByOrNull { severityRank(it.severity) }?.severity

    /**
     * Codes only, deduplicated, for transmitting to the peer device.
     *
     * The peer only needs to know which risks were seen — the MAC addresses,
     * hosts and interfaces in [LanThreat.detail] never leave this device —
     * so this is the narrow payload the protocol is allowed to carry. Input
     * order is preserved and each code appears at most once, which keeps the
     * payload deterministic for a given threat list.
     */
    fun summaryCodes(threats: List<LanThreat>): List<String> =
        threats.map { threat -> threat.code }.distinct()

    /** Severity ordering for [sorted] and [worst]; higher means worse. */
    private fun severityRank(severity: LanThreatSeverity): Int = when (severity) {
        LanThreatSeverity.HIGH -> 3
        LanThreatSeverity.MEDIUM -> 2
        LanThreatSeverity.LOW -> 1
    }

    /**
     * Lowercase hex form of [mac] without separators, or null when the text is
     * not MAC-shaped at all (hex digits and separators only, nothing else).
     * Both the full form (aa:bb:cc:dd:ee:ff, or 12 plain hex digits) and a
     * bare OUI are normalised here and callers decide which length they
     * require; rejecting foreign text keeps a stray string from being read as
     * a locally administered address by accident.
     */
    private fun normalizeMac(mac: String): String? {
        val trimmed = mac.trim().lowercase()
        if (!MAC_CHARS.matches(trimmed)) return null
        val plain = trimmed.replace(":", "").replace("-", "")
        val complete = plain.length == MAC_HEX_LENGTH || plain.length == OUI_HEX_LENGTH
        return if (complete) plain else null
    }

    /**
     * True for a complete 6-octet MAC. An ARP row must carry a full address —
     * a bare OUI is not a device — and the check is case-insensitive so a row
     * the kernel happened to print in upper case still counts.
     */
    private fun isCompleteMac(mac: String): Boolean =
        normalizeMac(mac)?.length == MAC_HEX_LENGTH

    /** True for the 00:00:00:00:00:00 placeholder the kernel prints early on. */
    private fun isZeroMac(mac: String): Boolean =
        normalizeMac(mac)?.all { digit -> digit == '0' } == true

    /** True for a dotted quad with each octet inside 0..255. */
    private fun isUsableIpv4(ip: String): Boolean {
        if (!IPV4_PATTERN.matches(ip)) return false
        return ip.split('.').all { octet -> octet.toInt() in 0..255 }
    }
}
