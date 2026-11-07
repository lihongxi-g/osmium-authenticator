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
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Receiving side of a LAN transfer (protocol v4).
 *
 * This device hosts the socket and shows the pairing code. A sender has to type
 * that code and prove it for this session (see [LanSession]) before the app
 * accepts anything — the roles are deliberately this way round: the device that
 * accepts data is the one that decides who may send, so a stranger on the same
 * network cannot push data at a device that is merely waiting.
 *
 * Safety rails:
 *  - at most [LanSession.MAX_FAILED_ATTEMPTS] failed exchanges per session, and
 *    only a peer that proved the pairing code can spend that budget,
 *  - every connection has to pass [LanConnectionLimiter] first (bounded
 *    concurrency, one attempt per address per window),
 *  - a peer on the 24h block list is refused before any key material is sent,
 *  - every wire field is length-checked ([LanWire]),
 *  - a stalled peer is dropped after [SOCKET_TIMEOUT_MS],
 *  - the socket is bound to the Wi-Fi address, not to every interface.
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
        const val ERR_DECLINED = "TRANSFER_DECLINED"
        const val ERR_PROTOCOL = "PROTOCOL_MISMATCH"
        const val ERR_PROOF = "PAIRING_PROOF_FAILED"

        /** A stalled/rogue peer must not pin a handler coroutine forever. */
        const val SOCKET_TIMEOUT_MS = 15_000

        /** Failed exchanges allowed per server session (successes don't count). */
        private const val MAX_FAILED_ATTEMPTS = LanSession.MAX_FAILED_ATTEMPTS

        /** Pending connections the OS may queue before new ones are refused. */
        private const val LISTEN_BACKLOG = 4

        /** One "wrong version" notice per this window, however many peers arrive. */
        private const val PROTOCOL_ERROR_DEBOUNCE_MS = 10_000L
    }

    private var serverSocket: ServerSocket? = null
    private var nsdRegistrationListener: NsdManager.RegistrationListener? = null
    private var serverJob: Job? = null
    private val running = AtomicBoolean(false)
    private val attempts = AtomicInteger(0)
    private val activeSockets = ConcurrentHashMap.newKeySet<Socket>()

    /** Rebuilt per session, so a previous session's rejections never carry over. */
    private var limiter = LanConnectionLimiter()

    /** Rate limit for the "wrong version" notice, shared by every handler. */
    private val lastProtocolErrorAt = AtomicLong(0)

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
        confirmPeer: suspend (LanPeer) -> Boolean,
        onListening: (String, Int, String) -> Unit,
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
        limiter = LanConnectionLimiter()
        lastProtocolErrorAt.set(0)

        serverJob = scope.launch(Dispatchers.IO) {
            try {
                // Bound to the Wi-Fi address only: a listener on 0.0.0.0 also
                // accepts connections arriving over a VPN or mobile data, which
                // this feature has no business answering.
                val server = ServerSocket(0, LISTEN_BACKLOG, InetAddress.getByName(localIp))
                serverSocket = server
                port = server.localPort

                withContext(Dispatchers.Main) {
                    registerNsd(port)
                    onListening(localIp, port, pairingCode)
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

                    val peerIp = socket.inetAddress?.hostAddress.orEmpty()
                    if (!limiter.admit(peerIp)) {
                        // Not a pairing-code failure, and deliberately not charged:
                        // a stranger must never be able to end a legitimate session
                        // by reconnecting. The connection is simply dropped.
                        AppLog.d("lan: refused a connection from $peerIp (limit)")
                        runCatching { socket.close() }
                        continue
                    }

                    activeSockets.add(socket)
                    launch(Dispatchers.IO) {
                        // `charge` spends the attempt budget, which exists to stop
                        // pairing-code guessing — so only a peer that proved it is
                        // talking to the right device may spend it.
                        var charge = false
                        var proofVerified = false
                        try {
                            // A peer that connects and stalls must not pin this
                            // coroutine (and the session's attempt budget) forever.
                            socket.soTimeout = SOCKET_TIMEOUT_MS

                            val input = DataInputStream(socket.getInputStream())
                            val output = DataOutputStream(socket.getOutputStream())

                            val hello = when (val greeting = readHello(input)) {
                                is Greeting.Ok -> greeting.hello
                                // Another protocol version is not an attack: say so
                                // once, and leave the attempt budget alone.
                                Greeting.WrongVersion -> {
                                    AppLog.d("lan: peer uses another protocol version")
                                    reportProtocolMismatch(onError)
                                    return@launch
                                }
                                // Junk, a truncated greeting, or a peer that said
                                // nothing at all. The limiter already bounds how
                                // often this can happen; reporting it as a version
                                // mismatch would only spray false errors.
                                Greeting.Malformed -> {
                                    AppLog.d("lan: dropped a connection during the greeting")
                                    return@launch
                                }
                            }

                            // The peer's own claims are checked together with what
                            // this device can actually see (address + ARP entry).
                            val peerMac = LanGuard.macFor(peerIp)
                            if (isBlocked(peerIp, peerMac, hello.deviceId)) {
                                AppLog.d("lan: refused a blocked peer")
                                LanWire.writeText(output, LanSession.DENY_BLOCKED)
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

                            val peer = LanPeer(peerIp, peerMac, hello.deviceId, hello.name, hello.threatCodes)
                            socket.soTimeout = LanTransferConsent.SOCKET_TIMEOUT_MS
                            var prompted = false
                            if (!LanTransferConsent.authorize(
                                    input, output,
                                    LanSession.senderTag(sessionKey, transcript),
                                    LanSession.readyTag(sessionKey, transcript)
                                ) { prompted = true; withContext(Dispatchers.Main) { confirmPeer(peer) } }
                            ) {
                                LanWire.writeText(output, LanSession.DENY)
                                // The dialog is only shown after the peer proved
                                // the code, so "not prompted" means a wrong code
                                // (or a substituted handshake) and stays charged;
                                // a user declining is charged to nothing.
                                charge = !prompted
                                withContext(Dispatchers.Main) {
                                    onError(if (prompted) ERR_DECLINED else ERR_PROOF)
                                }
                                return@launch
                            }
                            // From here on the peer has proven the code, so a
                            // failure is worth charging against the budget.
                            proofVerified = true
                            socket.soTimeout = SOCKET_TIMEOUT_MS
                            withContext(Dispatchers.Main) { onClientConnected() }
                            val sealed = LanWire.readPayload(input)
                            val plain = LanSession.open(sessionKey, transcript, sealed)
                            if (plain == null) {
                                AppLog.d("lan: payload failed to open")
                                LanWire.writeText(output, LanSession.DENY)
                                charge = true
                                return@launch
                            }

                            val vault = try {
                                VaultIO.decodePlain(plain)
                            } catch (e: Exception) {
                                AppLog.d("lan: payload is not a vault: ${e.javaClass.simpleName}")
                                LanWire.writeText(output, LanSession.DENY)
                                charge = true
                                return@launch
                            }

                            LanWire.writeText(output, LanSession.ACK)

                            withContext(Dispatchers.Main) {
                                onVaultReceived(vault, peer)
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // Timeouts and socket errors before the code was proven
                            // are the limiter's business, not the budget's: charging
                            // them would let a stranger end the session with a
                            // handful of junk connections.
                            charge = proofVerified
                            Log.d(TAG, "Transfer handler error: ${e.message}")
                        } finally {
                            activeSockets.remove(socket)
                            // Only a peer that proved the code (and then failed)
                            // consumes the budget, so a port scanner or a stalled
                            // peer cannot kill a working session.
                            if (charge && attempts.incrementAndGet() > MAX_FAILED_ATTEMPTS) {
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
                            limiter.release()
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

    /**
     * The peer's greeting, with "spoke a different version" kept apart from
     * "spoke nonsense": they mean different things to the user, and only one of
     * them is worth interrupting them for.
     */
    private sealed interface Greeting {
        data class Ok(val hello: Hello) : Greeting
        data object WrongVersion : Greeting
        data object Malformed : Greeting
    }

    /** Sender greeting, classified rather than collapsed into null. */
    private fun readHello(input: DataInputStream): Greeting {
        val magic = try {
            LanWire.readText(input)
        } catch (e: Exception) {
            return Greeting.Malformed
        }
        if (magic != LanSession.MAGIC) return Greeting.WrongVersion
        return try {
            val publicKey = LanWire.readKey(input)
            val nonce = LanWire.readKey(input, LanWire.MAX_NONCE_BYTES)
            val deviceId = LanWire.readText(input)
            val name = LanWire.readText(input)
            val threatCodes = LanWire.codesFromText(LanWire.readText(input))
            if (publicKey.isEmpty() || nonce.isEmpty()) {
                Greeting.Malformed
            } else {
                Greeting.Ok(Hello(publicKey, nonce, deviceId, name, threatCodes))
            }
        } catch (e: Exception) {
            Greeting.Malformed
        }
    }

    /**
     * Tells the user their peer runs another protocol version, at most once per
     * [PROTOCOL_ERROR_DEBOUNCE_MS] however many peers arrive — otherwise a
     * stranger can fill the screen with the same false error.
     */
    private suspend fun reportProtocolMismatch(onError: (String) -> Unit) {
        val now = System.currentTimeMillis()
        val previous = lastProtocolErrorAt.get()
        if (now - previous < PROTOCOL_ERROR_DEBOUNCE_MS) return
        if (!lastProtocolErrorAt.compareAndSet(previous, now)) return
        withContext(Dispatchers.Main) { onError(ERR_PROTOCOL) }
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
        activeSockets.forEach { runCatching { it.close() } }
        activeSockets.clear()
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
