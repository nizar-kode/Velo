package com.velo.remote.network

import android.os.Build
import android.util.Log
import com.velo.remote.data.model.AuthPayload
import com.velo.remote.data.model.AuthResponsePayload
import com.velo.remote.data.model.DiscoveredServer
import com.velo.remote.data.model.MouseClickPayload
import com.velo.remote.data.model.MouseDownUpPayload
import com.velo.remote.data.model.MouseMovePayload
import com.velo.remote.data.model.MouseScrollPayload
import com.velo.remote.data.model.PairRequestPayload
import com.velo.remote.data.model.PairResponsePayload
import com.velo.remote.data.model.VeloEnvelope
import com.velo.remote.data.storage.CredentialStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    data class Connecting(val server: DiscoveredServer) : ConnectionState()
    data class PairingRequired(val server: DiscoveredServer, val error: String? = null) : ConnectionState()
    data class Connected(val server: DiscoveredServer) : ConnectionState()
    data class Reconnecting(val server: DiscoveredServer, val attempt: Int) : ConnectionState()
}

class VeloWebSocketClient(
    private val credentials: CredentialStorage,
    private val scope: CoroutineScope
) {
    private val TAG = "VeloWS"
    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var currentServer: DiscoveredServer? = null
    private var reconnectJob: Job? = null
    private var shouldAutoReconnect = false

    // Monotonically increasing generation number to reject stale callbacks from closed sockets
    private val activeGeneration = AtomicInteger(0)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    fun connect(server: DiscoveredServer) {
        val generation = activeGeneration.incrementAndGet()
        reconnectJob?.cancel()
        reconnectJob = null

        try {
            webSocket?.close(1000, "Opening new connection")
        } catch (_: Exception) {}
        webSocket = null

        currentServer = server
        shouldAutoReconnect = true
        initiateConnection(server, generation)
    }

    private fun initiateConnection(server: DiscoveredServer, generation: Int) {
        if (generation != activeGeneration.get()) return

        _connectionState.value = ConnectionState.Connecting(server)

        // Velo WebSocket endpoint
        val url = "ws://${server.hostAddress}:${server.port}/velo"
        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (generation != activeGeneration.get()) {
                    webSocket.close(1000, "Superseded connection")
                    return
                }
                Log.d(TAG, "[Gen $generation] WebSocket connected to ${server.hostAddress}:${server.port}")

                val token = credentials.getToken(server.hostAddress)
                if (!token.isNullOrBlank()) {
                    Log.d(TAG, "[Gen $generation] Stored token found for ${server.hostAddress}, sending auth")
                    sendAuth(webSocket, token)
                } else {
                    Log.d(TAG, "[Gen $generation] No stored token for ${server.hostAddress}, pairing required")
                    _connectionState.value = ConnectionState.PairingRequired(server)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (generation != activeGeneration.get()) return
                handleIncomingMessage(text, server)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (generation != activeGeneration.get()) return
                Log.d(TAG, "[Gen $generation] WebSocket closed: $code $reason")
                handleDisconnect(server, generation)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (generation != activeGeneration.get()) return
                Log.w(TAG, "[Gen $generation] WebSocket failure: ${t.message}")
                handleDisconnect(server, generation)
            }
        })
    }

    private fun handleIncomingMessage(text: String, server: DiscoveredServer) {
        try {
            Log.d(TAG, "Incoming message: $text")
            val envelope = json.decodeFromString<VeloEnvelope>(text)
            when (envelope.type) {
                "pair_response" -> {
                    val resp = json.decodeFromJsonElement<PairResponsePayload>(envelope.payload)
                    if (resp.status.equals("success", ignoreCase = true) && !resp.token.isNullOrBlank()) {
                        Log.i(TAG, "Pairing SUCCESS! Saving token and switching to Connected")
                        credentials.saveToken(server.hostAddress, resp.token)
                        reconnectJob?.cancel()
                        reconnectJob = null
                        _connectionState.value = ConnectionState.Connected(server)
                    } else {
                        val errMsg = resp.message.ifBlank { "Pairing rejected by PC. Check the 6-digit PIN." }
                        Log.w(TAG, "Pairing rejected: $errMsg")
                        _connectionState.value = ConnectionState.PairingRequired(server, errMsg)
                    }
                }
                "auth_response" -> {
                    val resp = json.decodeFromJsonElement<AuthResponsePayload>(envelope.payload)
                    if (resp.status.equals("authenticated", ignoreCase = true)) {
                        Log.i(TAG, "Authentication SUCCESS! Switching to Connected")
                        reconnectJob?.cancel()
                        reconnectJob = null
                        _connectionState.value = ConnectionState.Connected(server)
                    } else {
                        Log.w(TAG, "Authentication rejected. Clearing token and requiring pairing")
                        credentials.clearToken(server.hostAddress)
                        val errMsg = resp.message ?: "Session expired. Please enter pairing PIN."
                        _connectionState.value = ConnectionState.PairingRequired(server, errMsg)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling incoming message: ${e.message}", e)
        }
    }

    private fun handleDisconnect(server: DiscoveredServer, generation: Int) {
        if (generation != activeGeneration.get()) return

        val wasConnected = _connectionState.value is ConnectionState.Connected

        if (!wasConnected || !shouldAutoReconnect) {
            if (_connectionState.value !is ConnectionState.PairingRequired) {
                _connectionState.value = ConnectionState.Disconnected
            }
            return
        }

        reconnectJob?.cancel()
        reconnectJob = scope.launch(Dispatchers.IO) {
            var attempt = 1
            val maxAttempts = 5
            while (isActive && shouldAutoReconnect && attempt <= maxAttempts) {
                if (generation != activeGeneration.get()) break
                _connectionState.value = ConnectionState.Reconnecting(server, attempt)
                delay(2000L)
                if (!isActive || generation != activeGeneration.get()) break
                initiateConnection(server, generation)
                delay(3000L)
                if (_connectionState.value is ConnectionState.Connected) {
                    break
                }
                attempt++
            }
            if (_connectionState.value !is ConnectionState.Connected && generation == activeGeneration.get()) {
                _connectionState.value = ConnectionState.Disconnected
            }
        }
    }

    fun submitPairingPin(pin: String): Boolean {
        val server = currentServer ?: return false
        val cleanPin = pin.trim()
        if (cleanPin.length != 6) return false

        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".ifBlank { "Velo Remote Client" }

        val payload = PairRequestPayload(
            deviceId = credentials.deviceId,
            deviceName = deviceName,
            pin = cleanPin
        )

        val envelope = VeloEnvelope(
            version = 1,
            type = "pair_request",
            timestamp = System.currentTimeMillis(),
            payload = json.encodeToJsonElement(payload).jsonObject
        )

        val jsonStr = json.encodeToString(envelope)
        Log.d(TAG, "Submitting pair_request with PIN: $cleanPin")
        val success = sendRaw(jsonStr)

        if (!success) {
            Log.w(TAG, "Failed to send pair_request: socket not open. Re-initiating connection...")
            _connectionState.value = ConnectionState.PairingRequired(server, "Connection lost. Reconnecting to PC...")
            val generation = activeGeneration.incrementAndGet()
            initiateConnection(server, generation)
        }
        return success
    }

    private fun sendAuth(ws: WebSocket, token: String) {
        val payload = AuthPayload(
            deviceId = credentials.deviceId,
            token = token
        )
        val envelope = VeloEnvelope(
            version = 1,
            type = "auth",
            timestamp = System.currentTimeMillis(),
            payload = json.encodeToJsonElement(payload).jsonObject
        )
        val jsonStr = json.encodeToString(envelope)
        Log.d(TAG, "Sending auth payload for device ${credentials.deviceId}")
        ws.send(jsonStr)
    }

    fun sendMouseMove(dx: Double, dy: Double) {
        if (_connectionState.value !is ConnectionState.Connected) return

        val envelope = VeloEnvelope(
            version = 1,
            type = "mouse_move",
            timestamp = System.currentTimeMillis(),
            payload = json.encodeToJsonElement(MouseMovePayload(dx, dy)).jsonObject
        )
        sendRaw(json.encodeToString(envelope))
    }

    fun sendMouseClick(button: String = "left") {
        if (_connectionState.value !is ConnectionState.Connected) return

        val envelope = VeloEnvelope(
            version = 1,
            type = "mouse_click",
            timestamp = System.currentTimeMillis(),
            payload = json.encodeToJsonElement(MouseClickPayload(button)).jsonObject
        )
        sendRaw(json.encodeToString(envelope))
    }

    fun sendMouseDown(button: String = "left") {
        if (_connectionState.value !is ConnectionState.Connected) return

        val envelope = VeloEnvelope(
            version = 1,
            type = "mouse_down",
            timestamp = System.currentTimeMillis(),
            payload = json.encodeToJsonElement(MouseDownUpPayload(button)).jsonObject
        )
        sendRaw(json.encodeToString(envelope))
    }

    fun sendMouseUp(button: String = "left") {
        if (_connectionState.value !is ConnectionState.Connected) return

        val envelope = VeloEnvelope(
            version = 1,
            type = "mouse_up",
            timestamp = System.currentTimeMillis(),
            payload = json.encodeToJsonElement(MouseDownUpPayload(button)).jsonObject
        )
        sendRaw(json.encodeToString(envelope))
    }

    fun sendMouseScroll(dx: Double, dy: Double) {
        if (_connectionState.value !is ConnectionState.Connected) return

        val envelope = VeloEnvelope(
            version = 1,
            type = "mouse_scroll",
            timestamp = System.currentTimeMillis(),
            payload = json.encodeToJsonElement(MouseScrollPayload(dx, dy)).jsonObject
        )
        sendRaw(json.encodeToString(envelope))
    }

    private fun sendRaw(jsonStr: String): Boolean {
        val ws = webSocket ?: return false
        return ws.send(jsonStr)
    }

    fun disconnect(userInitiated: Boolean = true) {
        activeGeneration.incrementAndGet()
        if (userInitiated) {
            shouldAutoReconnect = false
        }
        reconnectJob?.cancel()
        reconnectJob = null
        try {
            webSocket?.close(1000, "User disconnected")
        } catch (_: Exception) {}
        webSocket = null
        _connectionState.value = ConnectionState.Disconnected
    }
}

// Backward-compatibility alias
typealias AirPilotWebSocketClient = VeloWebSocketClient
