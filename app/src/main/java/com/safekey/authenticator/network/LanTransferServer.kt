package com.safekey.authenticator.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import com.safekey.authenticator.model.VaultFile
import com.safekey.authenticator.security.AppLog
import com.safekey.authenticator.security.VaultIO
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Receiving side of a LAN transfer (protocol v3).
 *
 * This device hosts the socket and shows the pairing code. A sender has to type
 * that code and prove it for this session (see [LanSession]) before the app
 * accepts anything — the roles are deliberately this way round: the device that
 * accepts data is the one that decides who may send, so a stranger on the same
 * network cannot push data at a device that is merely waiting.
 *
 * Safety rails:
 *  - at most [LanSession.MAX_FAILED_ATTEMPTS] failed exchanges per session,
 *  - a peer on the 24h block list is refused before any key material is sent,
 *  - every wire field is length-checked ([LanWire]),
 *  - a stalled peer is dropped after [SOCKET_TIMEOUT_MS].
 */
class LanTransferServer(
    private val context: Context,
    private val scope: CoroutineScope
) {

    companion object {
        const val TAG = "LanTransferServer"
        const val SERVICE_TYPE = "_osmium-transfer._tcp."
        const val ERR_TOO_MANY_FAILED_ATTEMPTS = "TOO_MANY_FAILED_ATTEMPTS"
        const val ERR_NO_WIFI = "NO_WIFI"

        /** A stalled/rogue peer must not pin a handler coroutine forever. */
        const val SOCKET_TIMEOUT_MS = 15_000

        /** Failed exchanges allowed per server session (successes don't count). */
        private const val MAX_FAILED_ATTEMPTS = LanSession.MAX_FAILED_ATTEMPTS
    }

    private var serverSocket: ServerSocket? = null
    private var nsdRegistrationListener: NsdManager.RegistrationListener? = null
    private var serverJob: Job? = null
    private val running = AtomicBoolean(false)
    private val attempts = AtomicInteger(0)

    var localIp: String = ""
        private set

    @Volatile
    var port: Int = 0
        private set

    @Volatile
    var pairingCode: String = ""
        private set

    fun start(
        selfDeviceId: String,
        selfName: String,
        selfThreatCodes: List<String>,
        isBlocked: suspend (ip: String, mac: String?, deviceId: String) -> Boolean,
        onClientConnected: () -> Unit,
        onVaultReceived: (VaultFile, LanPeer) -> Unit,
        onError: (String) -> Unit
    ) {
        stop()

        val ip = getLocalWifiIp()
        if (ip.isBlank()) {
            onError(ERR_NO_WIFI)
            return
        }

        localIp = ip
        pairingCode = LanSession.newPairingCode()
        running.set(true)
        attempts.set(0)

        serverJob = scope.launch(Dispatchers.IO) {
            try {
                val server = ServerSocket(0)
                serverSocket = server
                port = server.localPort

                withContext(Dispatchers.Main) {
                    registerNsd(port)
                }

                while (running.get()) {
                    val socket = try {
                        server.accept()
                    } catch (e: Exception) {
                        // stop()/the attempt limit closes the socket to break
                        // this loop — anything else is a real failure.
                        if (running.get()) {
                            withContext(Dispatchers.Main) {
                                onError(e.message ?: "Server error")
                            }
                        }
                        break
                    }

                    launch(Dispatchers.IO) {
                        var failed = false
                        try {
                            // A peer that connects and stalls must not pin this
                            // coroutine (and the session's attempt budget) forever.
                            socket.soTimeout = SOCKET_TIMEOUT_MS

                            val input = DataInputStream(socket.getInputStream())
                            val output = DataOutputStream(socket.getOutputStream())
                            val peerIp = socket.inetAddress?.hostAddress.orEmpty()

                            val hello = readHello(input)
                            if (hello == null) {
                                failed = true
                                return@launch
                            }

                            withContext(Dispatchers.Main) {
                                onClientConnected()
                            }

                            // The peer's own claims are checked together with what
                            // this device can actually see (address + ARP entry).
                            val peerMac = LanGuard.macFor(peerIp)
                            if (isBlocked(peerIp, peerMac, hello.deviceId)) {
                                AppLog.d("lan: refused a blocked peer")
                                LanWire.writeText(output, LanSession.DENY_BLOCKED)
                                failed = true
                                return@launch
                            }

                            val receiver = LanSession.newEphemeral()
                            val serverNonce = LanSession.newNonce()
                            val transcript = LanSession.transcript(
                                senderPublic = hello.publicKey,
                                receiverPublic = receiver.publicKeyBytes,
                                clientNonce = hello.nonce,
                                serverNonce = serverNonce,
                                senderThreatCodes = hello.threatCodes,
                                receiverThreatCodes = selfThreatCodes,
                                senderDeviceId = hello.deviceId,
                                receiverDeviceId = selfDeviceId
                            )
                            val sessionKey = LanSession.sessionKey(
                                privateKey = receiver.privateKey,
                                peerPublicKeyBytes = hello.publicKey,
                                pairingCode = pairingCode,
                                transcript = transcript
                            )

                            LanWire.writeText(output, LanSession.ACK)
                            LanWire.writeBytes(output, receiver.publicKeyBytes)
                            LanWire.writeBytes(output, serverNonce)
                            LanWire.writeText(output, selfDeviceId)
                            LanWire.writeText(output, selfName)
                            LanWire.writeText(output, LanWire.codesToText(selfThreatCodes))
                            LanWire.writeBytes(
                                output, LanSession.receiverTag(sessionKey, transcript)
                            )

                            // Proof that the sender knows the code for THIS session.
                            val senderTag = LanWire.readKey(input, LanWire.MAX_TAG_BYTES)
                            if (!LanSession.matches(
                                    LanSession.senderTag(sessionKey, transcript), senderTag
                                )
                            ) {
                                AppLog.d("lan: sender proof rejected")
                                LanWire.writeText(output, LanSession.DENY)
                                failed = true
                                return@launch
                            }

                            val sealed = LanWire.readPayload(input)
                            val plain = LanSession.open(sessionKey, transcript, sealed)
                            if (plain == null) {
                                AppLog.d("lan: payload failed to open")
                                LanWire.writeText(output, LanSession.DENY)
                                failed = true
                                return@launch
                            }

                            val vault = try {
                                VaultIO.decodePlain(plain)
                            } catch (e: Exception) {
                                AppLog.d("lan: payload is not a vault: ${e.javaClass.simpleName}")
                                LanWire.writeText(output, LanSession.DENY)
                                failed = true
                                return@launch
                            }

                            LanWire.writeText(output, LanSession.ACK)
                            val peer = LanPeer(
                                ip = peerIp,
                                mac = peerMac,
                                deviceId = hello.deviceId,
                                name = hello.name,
                                threatCodes = hello.threatCodes
                            )
                            withContext(Dispatchers.Main) {
                                onVaultReceived(vault, peer)
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // Read timeout, protocol error or socket error — any
                            // failed exchange counts toward the attempt limit.
                            failed = true
                            Log.d(TAG, "Transfer handler error: ${e.message}")
                        } finally {
                            // Only failed exchanges consume the budget, so a
                            // port scanner or stalled peer cannot kill a
                            // working session; successful transfers never do.
                            if (failed && attempts.incrementAndGet() > MAX_FAILED_ATTEMPTS) {
                                running.set(false)
                                runCatching {
                                    withContext(Dispatchers.Main) {
                                        onError(ERR_TOO_MANY_FAILED_ATTEMPTS)
                                    }
                                }
                                runCatching { serverSocket?.close() }
                                // Drop the mDNS advertisement too: leaving it
                                // published would keep sending peers to a dead
                                // port for the rest of the session.
                                unregisterNsd()
                            }
                            runCatching { socket.close() }
                        }
                    }
                }
            } catch (e: Exception) {
                if (running.get()) {
                    withContext(Dispatchers.Main) {
                        onError(e.message ?: "Server error")
                    }
                }
            }
        }
    }

    /** Sender hello, or null when the peer speaks a different protocol version. */
    private fun readHello(input: DataInputStream): Hello? {
        val magic = try {
            LanWire.readText(input)
        } catch (e: Exception) {
            return null
        }
        if (magic != LanSession.MAGIC) {
            AppLog.d("lan: peer uses another protocol version")
            return null
        }
        return try {
            val publicKey = LanWire.readKey(input)
            val nonce = LanWire.readKey(input, LanWire.MAX_NONCE_BYTES)
            val deviceId = LanWire.readText(input)
            val name = LanWire.readText(input)
            val threatCodes = LanWire.codesFromText(LanWire.readText(input))
            if (publicKey.isEmpty() || nonce.isEmpty()) {
                null
            } else {
                Hello(publicKey, nonce, deviceId, name, threatCodes)
            }
        } catch (e: Exception) {
            null
        }
    }

    private data class Hello(
        val publicKey: ByteArray,
        val nonce: ByteArray,
        val deviceId: String,
        val name: String,
        val threatCodes: List<String>
    )

    private fun registerNsd(port: Int) {
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        val serviceInfo = NsdServiceInfo().apply {
            serviceType = SERVICE_TYPE
            serviceName = "Osmium-Receive-${Build.MODEL.replace(" ", "_")}-$port"
            setPort(port)
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo?) {
                Log.d(TAG, "NSD registered: ${serviceInfo?.serviceName}")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                Log.d(TAG, "NSD registration failed: $errorCode")
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo?) {
                Log.d(TAG, "NSD unregistered")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                Log.d(TAG, "NSD unregistration failed: $errorCode")
            }
        }
        nsdRegistrationListener = listener
        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.d(TAG, "NSD register exception: ${e.message}")
        }
    }

    fun stop() {
        running.set(false)
        serverJob?.cancel()
        serverJob = null
        unregisterNsd()
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
    }

    /** Withdraws the mDNS advertisement; safe to call more than once. */
    private fun unregisterNsd() {
        nsdRegistrationListener?.let { listener ->
            try {
                val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
                nsdManager?.unregisterService(listener)
            } catch (_: Exception) {
            }
            nsdRegistrationListener = null
        }
    }

    private fun getLocalWifiIp(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return ""
            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback || iface.isVirtual) continue
                val name = iface.name.lowercase()
                if (!name.contains("wlan") && !name.contains("p2p") && !name.contains("ap") && !name.contains("eth") && !name.contains("rndis")) {
                    continue
                }
                for (addr in iface.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                        val host = addr.hostAddress ?: ""
                        if (host.isNotBlank() && host != "127.0.0.1") {
                            return host
                        }
                    }
                }
            }
            val allInterfaces = NetworkInterface.getNetworkInterfaces() ?: return ""
            for (iface in allInterfaces) {
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in iface.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                        val host = addr.hostAddress ?: ""
                        if (host.isNotBlank() && (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172."))) {
                            return host
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "getLocalWifiIp failed: ${e.message}")
        }
        return ""
    }
}
