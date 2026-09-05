package com.safekey.authenticator.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import com.safekey.authenticator.model.VaultFile
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
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Embedded LAN transfer server for sending accounts to a nearby device.
 */
class LanTransferServer(
    private val context: Context,
    private val scope: CoroutineScope
) {

    companion object {
        const val TAG = "LanTransferServer"
        const val SERVICE_TYPE = "_osmium-transfer._tcp."
        const val MAGIC_HEADER = "OSMIUM_TRANSFER_V1"
        const val MAGIC_ACK = "OSMIUM_TRANSFER_ACK"
        const val ERR_TOO_MANY_FAILED_ATTEMPTS = "TOO_MANY_FAILED_ATTEMPTS"

        /** A stalled/rogue peer must not pin a handler coroutine forever. */
        private const val SOCKET_TIMEOUT_MS = 15_000
        /** Failed exchanges allowed per server session (successes don't count). */
        private const val MAX_FAILED_ATTEMPTS = 10
    }

    private var serverSocket: ServerSocket? = null
    private var nsdRegistrationListener: NsdManager.RegistrationListener? = null
    private var serverJob: Job? = null
    private val running = AtomicBoolean(false)
    private val attempts = AtomicInteger(0)

    var localIp: String = ""
        private set
    var port: Int = 0
        private set
    var pairingCode: String = ""
        private set

    fun start(
        vault: VaultFile,
        onClientConnected: () -> Unit,
        onTransferSuccess: (Int) -> Unit,
        onError: (String) -> Unit
    ) {
        stop()

        val ip = getLocalWifiIp()
        if (ip.isBlank()) {
            onError("NO_WIFI")
            return
        }

        localIp = ip
        pairingCode = generatePairingCode()
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
                        break
                    }

                    launch(Dispatchers.IO) {
                        var failed = false
                        try {
                            // A peer that connects and stalls must not pin this
                            // coroutine (and the session's attempt budget) forever.
                            socket.soTimeout = SOCKET_TIMEOUT_MS

                            withContext(Dispatchers.Main) {
                                onClientConnected()
                            }

                            val dis = DataInputStream(socket.getInputStream())
                            val dos = DataOutputStream(socket.getOutputStream())

                            val magic = dis.readUTF()
                            if (magic != MAGIC_HEADER) {
                                failed = true
                                return@launch
                            }

                            val encryptedJson = VaultIO.encrypt(vault, pairingCode.toCharArray())
                            val payload = encryptedJson.toByteArray(Charsets.UTF_8)

                            dos.writeUTF(MAGIC_ACK)
                            dos.writeInt(payload.size)
                            dos.write(payload)
                            dos.flush()

                            withContext(Dispatchers.Main) {
                                onTransferSuccess(vault.accounts.size)
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

    private fun registerNsd(port: Int) {
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        val serviceInfo = NsdServiceInfo().apply {
            serviceType = SERVICE_TYPE
            serviceName = "Osmium-${Build.MODEL.replace(" ", "_")}-$port"
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

        nsdRegistrationListener?.let { listener ->
            try {
                val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
                nsdManager?.unregisterService(listener)
            } catch (_: Exception) {}
            nsdRegistrationListener = null
        }

        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
    }

    private fun generatePairingCode(): String {
        val random = SecureRandom()
        val num = 100_000 + random.nextInt(900_000)
        return num.toString()
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
