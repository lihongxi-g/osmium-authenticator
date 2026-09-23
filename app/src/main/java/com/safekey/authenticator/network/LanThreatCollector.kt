package com.safekey.authenticator.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import com.safekey.authenticator.security.AppLog
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/** Outcome of one silent local-network scan. */
data class LanThreatReport(
    val threats: List<LanThreat>,
    val gatewayMac: String?,
    val deviceCount: Int,
    val ssid: String?
) {
    val worst: LanThreatSeverity? get() = LanThreatAnalysis.worst(threats)

    /** Codes only — this is what travels to the peer device. */
    val codes: List<String> get() = LanThreatAnalysis.summaryCodes(threats)

    val hasThreats: Boolean get() = threats.isNotEmpty()
}

/**
 * Silent local-network inspection for the transfer feature.
 *
 * Only what an untrusted app can actually observe is used — anything that
 * cannot be read stays out of the report instead of being guessed, so a device
 * that hides information from the app produces a quiet scan, never a false
 * accusation. Collected signals:
 *
 *  - the Wi-Fi link's security type (open/WEP networks are readable by anyone),
 *  - an HTTP proxy configured on the active network (traffic can be redirected),
 *  - an active VPN (the local path is not what it appears to be),
 *  - this device's ARP table: duplicate MAC/IP pairs (a classic spoofing sign),
 *  - the gateway's MAC, remembered across the session (a change mid-session is
 *    the strongest local indication of an interception attempt).
 *
 * The ARP table lists only peers this device has talked to, so an attacker that
 * never exchanged a packet with this device is invisible here — the transfer
 * protocol therefore does not rely on detection alone.
 */
internal object LanThreatCollector {

    private const val ARP_PATH = "/proc/net/arp"

    /** Usable ARP entries of this device; empty when the table is not readable. */
    fun arpTable(): List<LanArpEntry> = runCatching {
        LanThreatAnalysis.usableArpEntries(LanThreatAnalysis.arpEntries(readQuietly(ARP_PATH)))
    }.getOrDefault(emptyList())

    fun scan(context: Context): LanThreatReport {
        val entries = arpTable()

        val ssid = ssid(context)
        val gatewayIp = gatewayIp(context)
        val gatewayMac = entries.firstOrNull { it.ip == gatewayIp }?.mac

        val threats = mutableListOf<LanThreat>()
        threats += LanThreatAnalysis.spoofingThreats(entries)
        LanThreatAnalysis.gatewayChangeThreat(LanGuard.gatewayBaseline(ssid), gatewayMac)
            ?.let { threats += it }
        LanThreatAnalysis.gatewayVirtualThreat(gatewayMac)?.let { threats += it }
        threats += LanThreatAnalysis.linkThreats(
            security = wifiSecurity(context),
            vpnActive = vpnActive(context),
            proxyHost = proxyHost(context),
            isP2p = false,
            arpDeviceCount = entries.size
        )

        val report = LanThreatReport(
            threats = LanThreatAnalysis.sorted(threats),
            gatewayMac = gatewayMac,
            deviceCount = entries.size,
            ssid = ssid
        )
        LanGuard.rememberGateway(ssid, gatewayMac)
        return report
    }

    // -------------------------------------------------------------- probing

    private fun readQuietly(path: String): String = try {
        val file = File(path)
        if (file.exists() && file.canRead()) file.readText() else ""
    } catch (_: Exception) {
        ""
    }

    private fun ssid(context: Context): String? = try {
        @Suppress("DEPRECATION")
        val info = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
            ?.connectionInfo
        info?.ssid?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
    } catch (_: Exception) {
        null
    }

    private fun wifiSecurity(context: Context): LanLinkSecurity {
        return try {
            val wifiManager =
                context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                    ?: return LanLinkSecurity.UNKNOWN
            @Suppress("DEPRECATION")
            val info = wifiManager.connectionInfo ?: return LanLinkSecurity.UNKNOWN
            if (Build.VERSION.SDK_INT >= 31) {
                when (info.currentSecurityType) {
                    0 -> LanLinkSecurity.OPEN                    // SECURITY_TYPE_OPEN
                    1 -> LanLinkSecurity.WEP                     // SECURITY_TYPE_WEP
                    2 -> LanLinkSecurity.WPA2                    // SECURITY_TYPE_PSK
                    3 -> LanLinkSecurity.ENTERPRISE              // SECURITY_TYPE_EAP
                    4 -> LanLinkSecurity.WPA3                    // SECURITY_TYPE_SAE
                    5 -> LanLinkSecurity.UNKNOWN                 // SECURITY_TYPE_OWE (encrypted)
                    else -> LanLinkSecurity.UNKNOWN
                }
            } else {
                securityFromScanResult(wifiManager)
            }
        } catch (_: Exception) {
            LanLinkSecurity.UNKNOWN
        }
    }

    @Suppress("DEPRECATION")
    private fun securityFromScanResult(wifiManager: WifiManager): LanLinkSecurity {
        val ssid = wifiManager.connectionInfo?.ssid ?: return LanLinkSecurity.UNKNOWN
        val capabilities = wifiManager.scanResults
            ?.firstOrNull { it.SSID == ssid }
            ?.capabilities
            ?: return LanLinkSecurity.UNKNOWN
        val upper = capabilities.uppercase()
        return when {
            upper.contains("WPA3") -> LanLinkSecurity.WPA3
            upper.contains("WPA2") -> LanLinkSecurity.WPA2
            upper.contains("WPA") -> LanLinkSecurity.WPA
            upper.contains("WEP") -> LanLinkSecurity.WEP
            upper.contains("ESS") -> LanLinkSecurity.OPEN
            else -> LanLinkSecurity.UNKNOWN
        }
    }

    private fun proxyHost(context: Context): String? {
        return try {
            activeLink(context)?.httpProxy?.host
        } catch (_: Exception) {
            null
        }
    }

    private fun vpnActive(context: Context): Boolean = try {
        val connectivity = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val capabilities = connectivity?.activeNetwork?.let { connectivity.getNetworkCapabilities(it) }
        capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    } catch (_: Exception) {
        false
    }

    /** Default gateway address of the active network, when it exposes one. */
    private fun gatewayIp(context: Context): String? = try {
        activeLink(context)?.routes
            ?.firstOrNull { it.isDefaultRoute }
            ?.gateway
            ?.hostAddress
    } catch (_: Exception) {
        null
    }

    private fun activeLink(context: Context): android.net.LinkProperties? {
        return try {
            val connectivity = context.applicationContext
                .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            connectivity?.activeNetwork?.let { connectivity.getLinkProperties(it) }
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Process-wide LAN guard: caches the newest scan result and remembers the
 * gateway MAC per SSID so a change during the session can be spotted.
 */
object LanGuard {

    private val _report = MutableStateFlow<LanThreatReport?>(null)
    val report: StateFlow<LanThreatReport?> = _report

    @Volatile private var baselineSsid: String? = null
    @Volatile private var baselineGatewayMac: String? = null

    /** Gateway MAC seen first on this SSID, or null when unknown. */
    fun gatewayBaseline(ssid: String?): String? =
        if (ssid != null && ssid == baselineSsid) baselineGatewayMac else null

    /** Records the gateway MAC the first time it is seen for an SSID. */
    fun rememberGateway(ssid: String?, mac: String?) {
        if (mac.isNullOrBlank()) return
        if (ssid != baselineSsid) {
            // Different network: re-baseline instead of reporting a bogus change.
            baselineSsid = ssid
            baselineGatewayMac = mac
        }
        if (baselineGatewayMac == null) baselineGatewayMac = mac
    }

    /** Hardware address of a peer, when this device's ARP table exposes it. */
    fun macFor(ip: String): String? = try {
        LanThreatCollector.arpTable().firstOrNull { it.ip == ip }?.mac?.takeIf { mac ->
            mac.isNotBlank() && mac != "00:00:00:00:00:00"
        }
    } catch (_: Exception) {
        null
    }

    /** Fresh scan on the IO dispatcher; failures leave the previous report. */
    suspend fun refresh(context: Context): LanThreatReport? = withContext(Dispatchers.IO) {
        try {
            val next = LanThreatCollector.scan(context.applicationContext)
            _report.value = next
            next
        } catch (e: Exception) {
            AppLog.d("lan threat scan failed: ${e.javaClass.simpleName}")
            _report.value
        }
    }

    fun clear() {
        _report.value = null
        baselineSsid = null
        baselineGatewayMac = null
    }
}
