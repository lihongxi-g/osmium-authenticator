package com.safekey.authenticator.network

import java.net.InetAddress
import java.net.URL

/** Pure policy shared by foreground WebDAV and scheduled backups. */
object LocalNetworkPolicy {
    fun permissionRequired(sdk: Int, targetSdk: Int): Boolean = sdk >= 37 && targetSdk >= 37

    fun isLocalAddress(address: InetAddress): Boolean {
        if (address.isLoopbackAddress) return false
        val bytes = address.address
        val uniqueLocalV6 = bytes.size == 16 && (bytes[0].toInt() and 0xfe) == 0xfc
        val sharedV4 = bytes.size == 4 && (bytes[0].toInt() and 0xff) == 100 &&
            (bytes[1].toInt() and 0xc0) == 64
        val broadcastV4 = bytes.size == 4 && bytes.all { (it.toInt() and 0xff) == 255 }
        return address.isSiteLocalAddress || address.isLinkLocalAddress ||
            address.isMulticastAddress || uniqueLocalV6 || sharedV4 || broadcastV4
    }

    fun webDavUsesLocalNetwork(
        baseUrl: String,
        resolve: (String) -> Array<InetAddress> = InetAddress::getAllByName
    ): Boolean {
        val host = URL(baseUrl.trim()).host.removeSurrounding("[", "]").trimEnd('.')
        // .local uses mDNS, which is itself permission-protected on Android 17.
        if (host.equals("local", true) || host.endsWith(".local", true)) return true
        return resolve(host).any(::isLocalAddress)
    }
}