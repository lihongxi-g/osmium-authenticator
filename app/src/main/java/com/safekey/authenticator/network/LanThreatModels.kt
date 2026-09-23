package com.safekey.authenticator.network

/**
 * Severity of one local-network risk signal.
 *
 * Deliberately coarse: a LAN scan produces weak evidence (ARP tables and
 * link properties can be spoofed, or simply unusual), and a finer scale
 * would promise precision the data cannot carry. HIGH is the level the user
 * is asked to act on, MEDIUM is context worth showing, LOW is informational.
 */
enum class LanThreatSeverity { LOW, MEDIUM, HIGH }

/**
 * Wi-Fi link security as far as the app can observe it.
 *
 * These are *observations* of what the platform reports, not guarantees: a
 * hostile access point can advertise WPA3 and still relay the traffic. The
 * values exist so that clearly broken links (OPEN, WEP) can be named, and so
 * that an unreadable link (UNKNOWN) is never silently treated as a safe one.
 */
enum class LanLinkSecurity { OPEN, WEP, WPA, WPA2, WPA3, ENTERPRISE, UNKNOWN }

/**
 * One ARP table row of this device.
 *
 * [mac] is expected in lowercase (see LanThreatAnalysis.arpEntries) so that
 * every comparison downstream — spoofing detection, gateway memory, virtual
 * OUI checks — can be a plain string comparison instead of a case dance.
 */
data class LanArpEntry(val ip: String, val mac: String, val iface: String)

/**
 * A detected risk. [code] is a stable identifier (transmitted to the peer),
 * [detail] is local-only.
 *
 * The split is a privacy boundary as much as a structure: the peer device
 * learns *which kinds* of risk were seen, never the MAC addresses, hosts or
 * interface names that produced them.
 */
data class LanThreat(
    val code: String,
    val severity: LanThreatSeverity,
    val detail: String = ""
)
