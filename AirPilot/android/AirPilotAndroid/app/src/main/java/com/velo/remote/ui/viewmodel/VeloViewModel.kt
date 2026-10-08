package com.velo.remote.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.velo.remote.data.model.DiscoveredServer
import com.velo.remote.data.storage.CredentialStorage
import com.velo.remote.network.ConnectionState
import com.velo.remote.network.UdpDiscoveryClient
import com.velo.remote.network.VeloWebSocketClient
import kotlinx.coroutines.flow.StateFlow

class VeloViewModel(application: Application) : AndroidViewModel(application) {
    private val credentials = CredentialStorage(application)
    private val discoveryClient = UdpDiscoveryClient(application)
    private val webSocketClient = VeloWebSocketClient(credentials, viewModelScope)

    val discoveredServers: StateFlow<List<DiscoveredServer>> = discoveryClient.servers
    val connectionState: StateFlow<ConnectionState> = webSocketClient.connectionState

    init {
        discoveryClient.startDiscovery(viewModelScope)
    }

    fun isServerPaired(server: DiscoveredServer): Boolean {
        return !credentials.getToken(server.hostAddress).isNullOrBlank()
    }

    fun connectToServer(server: DiscoveredServer) {
        webSocketClient.connect(server)
    }

    fun submitPin(pin: String) {
        webSocketClient.submitPairingPin(pin)
    }

    fun sendMouseMove(dx: Double, dy: Double) {
        webSocketClient.sendMouseMove(dx, dy)
    }

    fun sendMouseClick(button: String = "left") {
        webSocketClient.sendMouseClick(button)
    }

    fun sendMouseDown(button: String = "left") {
        webSocketClient.sendMouseDown(button)
    }

    fun sendMouseUp(button: String = "left") {
        webSocketClient.sendMouseUp(button)
    }

    fun sendMouseScroll(dx: Double, dy: Double) {
        webSocketClient.sendMouseScroll(dx, dy)
    }

    fun disconnect() {
        webSocketClient.disconnect(userInitiated = true)
    }

    override fun onCleared() {
        super.onCleared()
        discoveryClient.stopDiscovery()
        webSocketClient.disconnect()
    }
}

// Backward-compatibility alias
typealias AirPilotViewModel = VeloViewModel
