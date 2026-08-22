package com.safekey.authenticator.link

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/** LAN-only discovery lifecycle. The caller starts it only while Link is visible. */
@RequiresApi(Build.VERSION_CODES.JELLY_BEAN)
class LinkDiscovery(context: Context) {
    companion object { const val SERVICE_TYPE = "_osmium-link._tcp." }

    private val nsd = context.getSystemService(NsdManager::class.java)
    private val _peers = MutableStateFlow<List<NsdServiceInfo>>(emptyList())
    val peers: StateFlow<List<NsdServiceInfo>> = _peers
    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private var listening = false

    fun start(deviceName: String, servicePort: Int) {
        listening = true
        val info = NsdServiceInfo().apply {
            serviceName = "Osmium-$deviceName-${UUID.randomUUID().toString().take(8)}"
            serviceType = SERVICE_TYPE
            port = servicePort
        }
        registration = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, registration)
        discovery = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType == SERVICE_TYPE) nsd.resolveService(serviceInfo, resolver)
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) { _peers.value = _peers.value.filterNot { it.serviceName == serviceInfo.serviceName } }
        }
        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discovery)
    }

    private val resolver = object : NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            _peers.value = (_peers.value.filterNot { it.serviceName == serviceInfo.serviceName } + serviceInfo)
        }
    }

    fun stop() {
        discovery?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        registration?.let { runCatching { nsd.unregisterService(it) } }
        discovery = null; registration = null; _peers.value = emptyList()
    }
}
