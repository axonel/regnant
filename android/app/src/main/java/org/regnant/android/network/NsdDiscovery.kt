package org.regnant.android.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.regnant.android.data.HostIdentity

class NsdDiscovery(private val context: Context) {
    private val tag = "NsdDiscovery"
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager

    private val _discoveredHosts = MutableStateFlow<List<HostIdentity>>(emptyList())
    val discoveredHosts: StateFlow<List<HostIdentity>> = _discoveredHosts.asStateFlow()

    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var isDiscovering = false

    fun startDiscovery(onHostFound: ((HostIdentity) -> Unit)? = null) {
        if (isDiscovering || nsdManager == null) return

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e(tag, "Start discovery failed: $errorCode")
                isDiscovering = false
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e(tag, "Stop discovery failed: $errorCode")
                isDiscovering = false
            }

            override fun onDiscoveryStarted(serviceType: String?) {
                Log.d(tag, "Service discovery started for $serviceType")
                isDiscovering = true
            }

            override fun onDiscoveryStopped(serviceType: String?) {
                Log.d(tag, "Service discovery stopped")
                isDiscovering = false
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                Log.d(tag, "Service found: ${serviceInfo?.serviceName}")
                if (serviceInfo == null) return

                // Resolve service details
                resolveService(serviceInfo, onHostFound)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
                Log.d(tag, "Service lost: ${serviceInfo?.serviceName}")
                val name = serviceInfo?.serviceName ?: return
                _discoveredHosts.value = _discoveredHosts.value.filter { it.hostName != name }
            }
        }

        try {
            nsdManager.discoverServices("_regnant._tcp", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(tag, "Exception during discoverServices: ${e.message}")
        }
    }

    private fun resolveService(serviceInfo: NsdServiceInfo, onHostFound: ((HostIdentity) -> Unit)?) {
        val resolver = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                Log.e(tag, "Resolve failed for ${serviceInfo?.serviceName}: $errorCode")
            }

            override fun onServiceResolved(resolvedInfo: NsdServiceInfo?) {
                if (resolvedInfo == null) return
                val host = resolvedInfo.host ?: return
                // Only use IPv4 addresses for reliable local Wi-Fi routing
                if (host !is java.net.Inet4Address) return
                val ip = host.hostAddress ?: return
                val port = resolvedInfo.port

                val attributes = resolvedInfo.attributes
                val hostId = attributes["hostId"]?.let { String(it) } ?: "unknown-host-id"
                val name = attributes["name"]?.let { String(it) } ?: resolvedInfo.serviceName ?: "Omarchy Laptop"
                val fp = attributes["fp"]?.let { String(it) } ?: ""

                val identity = HostIdentity(
                    hostId = hostId,
                    hostName = name,
                    ip = ip,
                    port = port,
                    certFingerprint = fp,
                    lastSeen = System.currentTimeMillis()
                )

                val current = _discoveredHosts.value.toMutableList()
                current.removeAll { it.hostId == hostId || it.ip == ip }
                current.add(identity)
                _discoveredHosts.value = current

                onHostFound?.invoke(identity)
            }
        }

        try {
            nsdManager?.resolveService(serviceInfo, resolver)
        } catch (e: Exception) {
            Log.e(tag, "Exception resolving service: ${e.message}")
        }
    }

    fun stopDiscovery() {
        if (!isDiscovering || nsdManager == null || discoveryListener == null) return
        try {
            nsdManager.stopServiceDiscovery(discoveryListener)
        } catch (e: Exception) {
            Log.e(tag, "Error stopping discovery: ${e.message}")
        } finally {
            discoveryListener = null
            isDiscovering = false
        }
    }
}
