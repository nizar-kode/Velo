package com.darusc.mousedroid.networking

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException

class WifiDiscoveryManager(
    private val context: Context,
    private val onDeviceFound: (name: String, address: String) -> Unit
) {
    companion object {
        const val DISCOVERY_PORT = 48292
        private const val TAG = "WifiDiscovery"
    }

    private var job: Job? = null
    private var socket: DatagramSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    private fun getBroadcastAddresses(): List<InetAddress> {
        val targets = mutableListOf<InetAddress>()
        try {
            targets.add(InetAddress.getByName("255.255.255.255"))
        } catch (e: Exception) {
            Log.e(TAG, "Global broadcast address error: ${e.message}")
        }

        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val dhcp = wifiManager?.dhcpInfo
            if (dhcp != null && dhcp.ipAddress != 0 && dhcp.netmask != 0) {
                val broadcast = (dhcp.ipAddress and dhcp.netmask) or dhcp.netmask.inv()
                val quads = ByteArray(4)
                for (k in 0..3) {
                    quads[k] = ((broadcast shr (k * 8)) and 0xFF).toByte()
                }
                val subnetBroadcast = InetAddress.getByAddress(quads)
                if (!targets.contains(subnetBroadcast)) {
                    targets.add(subnetBroadcast)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Subnet broadcast calc error: ${e.message}")
        }

        return targets
    }

    fun startDiscovery() {
        if (job?.isActive == true) return

        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifiManager?.createMulticastLock("mousedroid_discovery")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire multicast lock: ${e.message}")
        }

        job = CoroutineScope(Dispatchers.IO).launch {
            try {
                socket = try {
                    DatagramSocket(null).apply {
                        reuseAddress = true
                        bind(InetSocketAddress(DISCOVERY_PORT))
                        broadcast = true
                        soTimeout = 2000
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Port $DISCOVERY_PORT bind failed, using ephemeral port: ${e.message}")
                    DatagramSocket().apply {
                        broadcast = true
                        soTimeout = 2000
                    }
                }

                // Send discovery probe packets periodically
                launch {
                    val probeData = "MOUSEDROID_PROBE".toByteArray()
                    while (isActive) {
                        try {
                            val targets = getBroadcastAddresses()
                            for (target in targets) {
                                val probePacket = DatagramPacket(probeData, probeData.size, target, DISCOVERY_PORT)
                                socket?.send(probePacket)
                            }
                            Log.d(TAG, "Sent discovery probes to: ${targets.joinToString { it.hostAddress }}")
                        } catch (e: Exception) {
                            Log.e(TAG, "Probe send error: ${e.message}")
                        }
                        delay(2000)
                    }
                }

                // Listen for incoming beacon responses or broadcasts
                val buffer = ByteArray(1024)
                while (isActive) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket?.receive(packet)

                        val message = String(packet.data, 0, packet.length).trim()
                        Log.d(TAG, "Received packet from ${packet.address.hostAddress}: $message")

                        if (message.startsWith("MOUSEDROID_BEACON|")) {
                            val parts = message.split("|")
                            if (parts.size >= 4) {
                                val name = parts[1]
                                val ip = parts[2].ifEmpty { packet.address.hostAddress }
                                val port = parts[3]
                                val address = "$ip:$port"

                                withContext(Dispatchers.Main) {
                                    onDeviceFound(name, address)
                                }
                            }
                        }
                    } catch (e: SocketTimeoutException) {
                        // Timeout hit, continue listening
                    } catch (e: Exception) {
                        if (!isActive) break
                        Log.e(TAG, "Receive error: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Discovery error: ${e.message}")
            } finally {
                socket?.close()
                socket = null
            }
        }
    }

    fun stopDiscovery() {
        job?.cancel()
        job = null
        socket?.close()
        socket = null
        try {
            multicastLock?.release()
        } catch (e: Exception) {
            // Ignore
        }
        multicastLock = null
    }
}
