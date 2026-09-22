package com.safekey.authenticator.ui.screens

import android.content.Context

/**
 * Texts for the LAN transfer security UI (risk notices, 24h block list).
 *
 * These strings live here instead of res/values-* on purpose: the feature is
 * new and its wording is still moving, so it follows the same English /
 * Simplified Chinese scheme the developer-only screens already use — other
 * locales fall back to English instead of showing a half-translated dialog.
 * Everything else on the transfer screen still comes from the translated
 * resources.
 */
class LanStrings private constructor(
    val riskTitle: String,
    val riskLocalHeader: String,
    val riskPeerHeader: String,
    val riskNone: String,
    val riskContinue: String,
    val blockAction: String,
    val blockDone: String,
    val blockedByMe: String,
    val blockedByPeer: String,
    val unblockAction: String,
    val unblocked: String,
    val blockedTapHint: String,
    val sendCodeHint: String,
    val receiveCodeHint: String,
    val verifying: String,
    val riskCodes: Map<String, String>
) {

    /** Human text for one threat code (peer codes included). */
    fun threat(code: String): String = riskCodes[code] ?: code

    companion object {

        fun forContext(context: Context): LanStrings {
            val language = context.resources.configuration.locales[0].language
            return if (language == "zh") chinese() else english()
        }

        private fun english() = LanStrings(
            riskTitle = "Local network risk",
            riskLocalHeader = "This device sees:",
            riskPeerHeader = "The other device sees:",
            riskNone = "No risk detected on this network.",
            riskContinue = "Continue anyway",
            blockAction = "Refuse this device for 24 hours",
            blockDone = "Device blocked for 24 hours",
            blockedByMe = "Blocked by you (24h)",
            blockedByPeer = "You have been blocked by the other device — transfer not possible",
            unblockAction = "Remove the block",
            unblocked = "Block removed",
            blockedTapHint = "Tap to review this block",
            sendCodeHint = "Enter the 12-digit code shown on the receiving device",
            receiveCodeHint = "Show this code to the sending device",
            verifying = "Verifying…",
            riskCodes = mapOf(
                "wifi_open" to "The Wi-Fi network is open — anyone nearby can read its traffic.",
                "wifi_wep" to "The Wi-Fi network uses WEP, which is broken.",
                "wifi_enterprise" to "An enterprise Wi-Fi network is in use; its operator can inspect traffic.",
                "http_proxy" to "An HTTP proxy is configured for this network — traffic can be redirected.",
                "vpn_active" to "A VPN is active; the local network path is not what it appears to be.",
                "crowded_lan" to "Many devices share this network.",
                "gateway_changed" to "The gateway address changed during this session — a sign of interception.",
                "gateway_virtual" to "The gateway looks like a virtual machine or a hotspot relay.",
                "arp_mac_conflict" to "One hardware address claims several IP addresses (ARP conflict).",
                "arp_ip_conflict" to "One IP address answers with several hardware addresses (ARP spoofing)."
            )
        )

        private fun chinese() = LanStrings(
            riskTitle = "局域网存在风险",
            riskLocalHeader = "本机检测到：",
            riskPeerHeader = "对方设备检测到：",
            riskNone = "未在当前网络发现风险。",
            riskContinue = "仍然继续",
            blockAction = "24 小时内拒绝该设备",
            blockDone = "已拉黑该设备 24 小时",
            blockedByMe = "已被你拉黑（24 小时）",
            blockedByPeer = "你已被对方拉黑，不可传输",
            unblockAction = "解除拉黑",
            unblocked = "已解除拉黑",
            blockedTapHint = "点击查看该拉黑记录",
            sendCodeHint = "输入接收方设备上显示的 12 位验证码",
            receiveCodeHint = "把此验证码出示给发送方设备",
            verifying = "正在验证…",
            riskCodes = mapOf(
                "wifi_open" to "当前 Wi-Fi 为开放网络，附近任何人都能读取流量。",
                "wifi_wep" to "当前 Wi-Fi 使用已失效的 WEP 加密。",
                "wifi_enterprise" to "当前为企业级 Wi-Fi，网络管理员可以查看流量。",
                "http_proxy" to "该网络配置了 HTTP 代理，流量可能被转发。",
                "vpn_active" to "检测到 VPN，局域网路径与显示不符。",
                "crowded_lan" to "该网络上的设备数量较多。",
                "gateway_changed" to "本次会话中网关地址发生变化，存在被中间人接管的迹象。",
                "gateway_virtual" to "网关看起来是虚拟机或热点中转。",
                "arp_mac_conflict" to "同一个硬件地址声称拥有多个 IP（ARP 冲突）。",
                "arp_ip_conflict" to "同一个 IP 回应了多个硬件地址（疑似 ARP 欺骗）。"
            )
        )
    }
}
