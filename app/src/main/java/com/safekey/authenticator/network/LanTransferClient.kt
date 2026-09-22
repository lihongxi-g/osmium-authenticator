package com.safekey.authenticator.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.safekey.authenticator.model.VaultFile
import com.safekey.authenticator.security.VaultIO
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Collections
import java.util.LinkedList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DiscoveredDevice(
    val name: String,
    val host: String,
    val port: Int
)

/**
 * Client for discovering nearby Osmium transfer servers and fetching vault payloads.
 */
class LanTransferClient(private val context: Context) {

    companion object {
        const val TAG = "LanTransferClient"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var nsdDiscoveryListener: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    
    // Resolve queue to avoid "listener already in use" on older and newer Android NSD stacks
    private val resolveQueue = Collections.synchronizedList(LinkedList<NsdServiceInfo>())
    @Volatile
    private var isResolving = false

    fun startDiscovery(
        onDeviceFound: (DiscoveredDevice) -> Unit,
        onDeviceLost: (String) -> Unit
    ) {
        stopDiscovery()

        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifiManager?.createMulticastLock("osmium_nsd_lock")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Throwable) {
            Log.d(TAG, "MulticastLock error: ${e.message}")
        }

        val nsdManager = try {
            context.getSystemService(Context.NSD_SERVICE) as? NsdManager
        } catch (e: Throwable) {
            Log.d(TAG, "Failed to get NSD_SERVICE: ${e.message}")
            null
        } ?: return

        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.d(TAG, "NSD start discovery failed: $errorCode")
            }
            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.d(TAG, "NSD stop discovery failed: $errorCode")
            }
            override fun onDiscoveryStarted(serviceType: String) {
                Log.d(TAG, "NSD discovery started")
            }
            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "NSD discovery stopped")
            }
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "NSD service found: ${serviceInfo.serviceName}")
                enqueueResolve(nsdManager, serviceInfo, onDeviceFound)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "NSD service lost: ${serviceInfo.serviceName}")
                mainHandler.post {
                    try {
                        onDeviceLost(serviceInfo.serviceName)
                    } catch (_: Throwable) {}
                }
            }
        }
        nsdDiscoveryListener = listener
        try {
            nsdManager.discoverServices(LanTransferServer.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Throwable) {
            Log.d(TAG, "NSD discoverServices exception: ${e.message}")
        }
    }

    private fun enqueueResolve(
        nsdManager: NsdManager,
        serviceInfo: NsdServiceInfo,
        onDeviceFound: (DiscoveredDevice) -> Unit
    ) {
        synchronized(resolveQueue) {
            resolveQueue.add(serviceInfo)
        }
        processNextResolve(nsdManager, onDeviceFound)
    }

    private fun processNextResolve(
        nsdManager: NsdManager,
        onDeviceFound: (DiscoveredDevice) -> Unit
    ) {
        synchronized(resolveQueue) {
            if (isResolving || resolveQueue.isEmpty()) return
            isResolving = true
            val nextService = resolveQueue.removeAt(0)
            
            try {
                nsdManager.resolveService(nextService, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                        Log.d(TAG, "NSD resolve failed for ${serviceInfo?.serviceName}: $errorCode")
                        synchronized(resolveQueue) { isResolving = false }
                        processNextResolve(nsdManager, onDeviceFound)
                    }

                    override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                        try {
                            val host = resolvedInfo.host?.hostAddress
                            val port = resolvedInfo.port
                            val name = resolvedInfo.serviceName
                            if (!host.isNullOrBlank() && port > 0) {
                                mainHandler.post {
                                    onDeviceFound(DiscoveredDevice(name = name, host = host, port = port))
                                }
                            }
                        } catch (e: Throwable) {
                            Log.d(TAG, "Error handling resolved service: ${e.message}")
                        } finally {
                            synchronized(resolveQueue) { isResolving = false }
                            processNextResolve(nsdManager, onDeviceFound)
                        }
                    }
                })
            } catch (e: Throwable) {
                Log.d(TAG, "NSD resolveService exception: ${e.message}")
                isResolving = false
                processNextResolve(nsdManager, onDeviceFound)
            }
        }
    }

    fun stopDiscovery() {
        resolveQueue.clear()
        isResolving = false

        nsdDiscoveryListener?.let { listener ->
            try {
                val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
                nsdManager?.stopServiceDiscovery(listener)
            } catch (_: Throwable) {}
            nsdDiscoveryListener = null
        }
        
        try {
            multicastLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (_: Throwable) {}
        multicastLock = null
    }

    suspend fun sendVault(
        host: String,
        port: Int,
        pairingCode: String,
        vault: VaultFile,
        selfDeviceId: String,
        selfName: String,
        selfThreatCodes: List<String>,
        isBlocked: suspend (ip: String, mac: String?, deviceId: String) -> Boolean
    ): LanSendOutcome = withContext(Dispatchers.IO) {
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(host, port), 8_000)
            socket.soTimeout = 15_000

            val output = DataOutputStream(socket.getOutputStream())
            val input = DataInputStream(socket.getInputStream())

            // 1) hello: version, ephemeral key, nonce, identity, own threat codes
            val sender = LanSession.newEphemeral()
            val clientNonce = LanSession.newNonce()
            LanWire.writeText(output, LanSession.MAGIC)
            LanWire.writeBytes(output, sender.publicKeyBytes)
            LanWire.writeBytes(output, clientNonce)
            LanWire.writeText(output, selfDeviceId)
            LanWire.writeText(output, selfName)
            LanWire.writeText(output, LanWire.codesToText(selfThreatCodes))

            // 2) the receiver either accepts the handshake or refuses outright
            val status = LanWire.readText(input)
            when (status) {
                LanSession.DENY_BLOCKED -> return@withContext LanSendOutcome.RefusedByPeer(
                    LanPeer(host, LanGuard.macFor(host), "", "", emptyList())
                )
                LanSession.ACK -> Unit
                else -> return@withContext LanSendOutcome.Failed("refused: $status")
            }

            // 3) receiver key material, identity, threat codes and its proof
            val receiverPublic = LanWire.readKey(input)
            val serverNonce = LanWire.readKey(input, LanWire.MAX_NONCE_BYTES)
            val receiverId = LanWire.readText(input)
            val receiverName = LanWire.readText(input)
            val receiverThreats = LanWire.codesFromText(LanWire.readText(input))
            val receiverTag = LanWire.readKey(input, LanWire.MAX_TAG_BYTES)

            val candidate = LanPeer(
                ip = host,
                mac = LanGuard.macFor(host),
                deviceId = receiverId,
                name = receiverName,
                threatCodes = receiverThreats
            )
            val transcript = LanSession.transcript(
                senderPublic = sender.publicKeyBytes,
                receiverPublic = receiverPublic,
                clientNonce = clientNonce,
                serverNonce = serverNonce,
                senderThreatCodes = selfThreatCodes,
                receiverThreatCodes = receiverThreats,
                senderDeviceId = selfDeviceId,
                receiverDeviceId = receiverId
            )
            val sessionKey = LanSession.sessionKey(
                privateKey = sender.privateKey,
                peerPublicKeyBytes = receiverPublic,
                pairingCode = pairingCode,
                transcript = transcript
            )

            // The receiver has to prove it knows the same code: a wrong code, a
            // substituted key (interception) or a different device all fail here.
            if (!LanSession.matches(
                    LanSession.receiverTag(sessionKey, transcript), receiverTag
                )
            ) {
                return@withContext LanSendOutcome.ProofFailed
            }

            // 4) this device's own decision: never send to a blocked peer
            if (isBlocked(host, candidate.mac, receiverId)) {
                LanWire.writeText(output, LanSession.DENY_PEER_BLOCKED)
                return@withContext LanSendOutcome.PeerBlocked(candidate)
            }

            // 5) proof of the code, then the sealed vault
            val payload = VaultIO.encodePlain(vault).toByteArray(Charsets.UTF_8)
            LanWire.writeBytes(output, LanSession.senderTag(sessionKey, transcript))
            LanWire.writeBytes(output, LanSession.seal(sessionKey, transcript, payload))

            when (val finalStatus = LanWire.readText(input)) {
                LanSession.ACK -> LanSendOutcome.Delivered(candidate)
                else -> LanSendOutcome.Failed("rejected: $finalStatus")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "LanTransferClient send error: ${e.message}")
            LanSendOutcome.Failed(e.message ?: "error")
        } finally {
            try {
                socket.close()
            } catch (_: Throwable) {
            }
        }
    }
}

/** Result of one send attempt. */
sealed interface LanSendOutcome {

    /** Payload delivered and acknowledged by the receiving device. */
    data class Delivered(val peer: LanPeer) : LanSendOutcome

    /** This device's 24h block list names the receiver, so nothing was sent. */
    data class PeerBlocked(val peer: LanPeer) : LanSendOutcome

    /** The receiver has this device blocked (or refused the handshake). */
    data class RefusedByPeer(val peer: LanPeer) : LanSendOutcome

    /**
     * The receiver could not prove the pairing code for this session: wrong
     * code, or the key exchange was substituted on the way.
     */
    data object ProofFailed : LanSendOutcome

    /** Network or protocol failure. */
    data class Failed(val message: String) : LanSendOutcome
}
