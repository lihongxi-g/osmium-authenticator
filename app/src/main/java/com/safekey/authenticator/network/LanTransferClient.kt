package com.safekey.authenticator.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import com.safekey.authenticator.model.VaultFile
import com.safekey.authenticator.security.VaultFormatException
import com.safekey.authenticator.security.VaultIO
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
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

    private var nsdDiscoveryListener: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    fun startDiscovery(
        onDeviceFound: (DiscoveredDevice) -> Unit,
        onDeviceLost: (String) -> Unit
    ) {
        stopDiscovery()

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        multicastLock = wifiManager?.createMulticastLock("osmium_nsd_lock")?.apply {
            setReferenceCounted(true)
            acquire()
        }

        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return

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
                try {
                    nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                            Log.d(TAG, "NSD resolve failed: $errorCode")
                        }

                        override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                            val host = resolvedInfo.host?.hostAddress ?: return
                            val port = resolvedInfo.port
                            val name = resolvedInfo.serviceName
                            onDeviceFound(DiscoveredDevice(name = name, host = host, port = port))
                        }
                    })
                } catch (e: Exception) {
                    Log.d(TAG, "NSD resolve exception: ${e.message}")
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "NSD service lost: ${serviceInfo.serviceName}")
                onDeviceLost(serviceInfo.serviceName)
            }
        }
        nsdDiscoveryListener = listener
        try {
            nsdManager.discoverServices(LanTransferServer.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.d(TAG, "NSD discoverServices exception: ${e.message}")
        }
    }

    fun stopDiscovery() {
        nsdDiscoveryListener?.let { listener ->
            try {
                val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
                nsdManager?.stopServiceDiscovery(listener)
            } catch (_: Exception) {}
            nsdDiscoveryListener = null
        }
        multicastLock?.let {
            if (it.isHeld) it.release()
        }
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
            try { socket.close() } catch (_: Exception) {}
        }
    }
}
