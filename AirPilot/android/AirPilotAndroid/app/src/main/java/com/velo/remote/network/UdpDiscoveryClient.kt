package com.velo.remote.network

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.velo.remote.data.model.DiscoveredServer
import com.velo.remote.data.model.DiscoveryBeacon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

class UdpDiscoveryClient(private val context: Context) {
    private val TAG = "VeloDiscovery"
    private val DISCOVERY_PORT = 51820
    private val json = Json { ignoreUnknownKeys = true }

    private val _servers = MutableStateFlow<List<DiscoveredServer>>(emptyList())
    val servers: StateFlow<List<DiscoveredServer>> = _servers.asStateFlow()

    private var multicastLock: WifiManager.MulticastLock? = null
    private var discoveryJob: Job? = null
    private var socket: DatagramSocket? = null

    fun startDiscovery(scope: CoroutineScope) {
        if (discoveryJob?.isActive == true) return

        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifiManager.createMulticastLock("velo_udp_lock").apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire MulticastLock: ${e.message}")
        }

        discoveryJob = scope.launch(Dispatchers.IO) {
            try {
                socket = DatagramSocket().apply {
                    broadcast = true
                    soTimeout = 2000
                }

                // Launch receiver loop
                launch {
                    val buffer = ByteArray(4096)
                    val packet = DatagramPacket(buffer, buffer.size)

                    while (isActive) {
                        try {
                            socket?.receive(packet)
                            val text = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                            val packetSenderIp = packet.address?.hostAddress ?: ""
                            parseAndAddServer(text, packetSenderIp)
                        } catch (e: SocketTimeoutException) {
                            // Expected timeout, loop again
                        } catch (e: Exception) {
                            if (!isActive) break
                            Log.w(TAG, "Receive error: ${e.message}")
                            delay(500)
                        }
                    }
                }

                // Launch sender loop (broadcast discovery probes every 2.5s)
                while (isActive) {
                    try {
                        val broadcastAddr = InetAddress.getByName("255.255.255.255")

                        // 1. Broadcast standard Velo protocol probe
                        val veloProbeBytes = "VELO_PROBE".toByteArray(Charsets.UTF_8)
                        val veloPacket = DatagramPacket(veloProbeBytes, veloProbeBytes.size, broadcastAddr, DISCOVERY_PORT)
                        socket?.send(veloPacket)

                        // 2. Broadcast JSON fallback probe
                        val queryMsg = """{"service":"Velo","type":"discover_query","version":1}"""
                        val queryBytes = queryMsg.toByteArray(Charsets.UTF_8)
                        val queryPacket = DatagramPacket(queryBytes, queryBytes.size, broadcastAddr, DISCOVERY_PORT)
                        socket?.send(queryPacket)

                        // Prune dead servers (not seen in > 8 seconds)
                        val now = System.currentTimeMillis()
                        _servers.value = _servers.value.filter { now - it.lastSeenTimestamp < 8000 }
                    } catch (e: Exception) {
                        Log.w(TAG, "Broadcast error: ${e.message}")
                    }
                    delay(2500)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Discovery setup error: ${e.message}")
            }
        }
    }

    private fun parseAndAddServer(text: String, packetSenderIp: String) {
        // Option A: Velo string beacon format: VELO_BEACON|<HOSTNAME>|<IP>|51821
        if (text.startsWith("VELO_BEACON|")) {
            val parts = text.split("|")
            if (parts.size >= 4) {
                val compName = parts[1].ifBlank { "Windows PC" }
                val reportedIp = parts[2].trim()
                val targetIp = if (reportedIp.isNotBlank() && reportedIp != "127.0.0.1" && reportedIp != "0.0.0.0") {
                    reportedIp
                } else {
                    packetSenderIp
                }
                val port = parts[3].toIntOrNull() ?: 51821

                val server = DiscoveredServer(
                    computerName = compName,
                    hostAddress = targetIp,
                    port = port,
                    os = "Windows 11",
                    status = "Available",
                    lastSeenTimestamp = System.currentTimeMillis()
                )
                upsertServer(server)
                return
            }
        }

        // Option B: JSON beacon format
        try {
            val beacon = json.decodeFromString<DiscoveryBeacon>(text)
            if (beacon.service.equals("Velo", ignoreCase = true) || beacon.service.equals("AirPilot", ignoreCase = true)) {
                val server = DiscoveredServer(
                    computerName = beacon.computerName.ifBlank { "Windows PC" },
                    hostAddress = packetSenderIp,
                    port = beacon.port,
                    os = beacon.os,
                    status = beacon.status,
                    lastSeenTimestamp = System.currentTimeMillis()
                )
                upsertServer(server)
            }
        } catch (e: Exception) {
            // Unrecognized packet, ignore
        }
    }

    private fun upsertServer(server: DiscoveredServer) {
        val current = _servers.value.toMutableList()
        val index = current.indexOfFirst { it.hostAddress == server.hostAddress }
        if (index >= 0) {
            current[index] = server
        } else {
            current.add(server)
        }
        _servers.value = current
    }

    fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
        try {
            socket?.close()
        } catch (e: Exception) {}
        socket = null

        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (e: Exception) {}
        multicastLock = null
    }
}
