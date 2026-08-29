package com.safekey.authenticator.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.safekey.authenticator.model.VaultFile
import com.safekey.authenticator.security.VaultFormatException
import com.safekey.authenticator.security.VaultIO
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Collections
import java.util.LinkedList
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

    suspend fun fetchVault(
        host: String,
        port: Int,
        pairingCode: String
    ): Result<VaultFile> = withContext(Dispatchers.IO) {
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(host, port), 8_000)
            socket.soTimeout = 15_000

            val dos = DataOutputStream(socket.getOutputStream())
            val dis = DataInputStream(socket.getInputStream())

            // Send magic header
            dos.writeUTF(LanTransferServer.MAGIC_HEADER)
            dos.flush()

            // Read ACK
            val ack = dis.readUTF()
            if (ack != LanTransferServer.MAGIC_ACK) {
                return@withContext Result.failure(Exception("Incompatible protocol response: $ack"))
            }

            val length = dis.readInt()
            if (length <= 0 || length > VaultIO.MAX_PAYLOAD_BYTES) {
                return@withContext Result.failure(Exception("Invalid payload size: $length"))
            }

            val payloadBytes = ByteArray(length)
            dis.readFully(payloadBytes)

            // Decrypt with pairing code
            val vault = try {
                VaultIO.decrypt(payloadBytes, pairingCode.toCharArray())
            } catch (e: VaultFormatException) {
                return@withContext Result.failure(e)
            }

            Result.success(vault)
        } catch (e: Exception) {
            Log.d(TAG, "LanTransferClient fetch error: ${e.message}")
            Result.failure(e)
        } finally {
            try { socket.close() } catch (_: Throwable) {}
        }
    }
}
