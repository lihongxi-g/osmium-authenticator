package com.safekey.authenticator.network

import java.net.InetAddress

/**
 * Where a transfer may be sent.
 *
 * The payload is encrypted either way, so an off-network target is not a
 * plaintext leak — but it does break the feature's promise ("nothing leaves the
 * local network") and hands an attacker a captured sample to work on at leisure.
 * The send path therefore refuses anything that is not a unicast address of a
 * local network.
 *
 * This is deliberately **not** [LocalNetworkPolicy.isLocalAddress]: that one
 * answers "does this host need the local-network permission", so it counts
 * multicast and broadcast as local. Here multicast, broadcast, loopback and the
 * unspecified address are all refused.
 */
internal object LanSendTarget {

    private val IPV4_LITERAL = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

    /** True when [address] is a unicast address that cannot leave the local network. */
    fun isLocalUnicast(address: InetAddress): Boolean {
        if (address.isLoopbackAddress) return false
        if (address.isAnyLocalAddress) return false
        if (address.isMulticastAddress) return false
        val bytes = address.address
        // 255.255.255.255 — a broadcast would spray the payload at the whole LAN.
        if (bytes.size == 4 && bytes.all { (it.toInt() and 0xff) == 255 }) return false
        val uniqueLocalV6 = bytes.size == 16 && (bytes[0].toInt() and 0xfe) == 0xfc
        val sharedV4 = bytes.size == 4 && (bytes[0].toInt() and 0xff) == 100 &&
            (bytes[1].toInt() and 0xc0) == 64
        return address.isSiteLocalAddress || address.isLinkLocalAddress ||
            uniqueLocalV6 || sharedV4
    }

    /**
     * Literal-only check: no name resolution, so it is safe to call on the main
     * thread for an immediate UI error. Null means "not an IP literal" — the
     * caller then has to check on an IO thread via [isLocalPeer].
     */
    fun literalIsLocalUnicast(host: String): Boolean? {
        val trimmed = host.trim().removeSurrounding("[", "]").trimEnd('.')
        if (trimmed.isEmpty()) return null
        val literal = trimmed.contains(':') || IPV4_LITERAL.matches(trimmed)
        if (!literal) return null
        val address = runCatching { InetAddress.getByName(trimmed) }.getOrNull() ?: return false
        return isLocalUnicast(address)
    }

    /**
     * Full check for the send path (runs on IO). A host name is accepted only
     * when *every* address it resolves to is local, and an unresolvable host is
     * refused.
     */
    fun isLocalPeer(host: String): Boolean {
        literalIsLocalUnicast(host)?.let { return it }
        val addresses = runCatching { InetAddress.getAllByName(host) }.getOrNull().orEmpty()
        if (addresses.isEmpty()) return false
        return addresses.all(::isLocalUnicast)
    }
}
