package com.velo.remote.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class VeloEnvelope(
    val version: Int = 1,
    val type: String,
    val timestamp: Long = System.currentTimeMillis(),
    val payload: JsonObject
)

// Backward-compatibility alias
typealias AirPilotEnvelope = VeloEnvelope

@Serializable
data class DiscoveryBeacon(
    val service: String = "Velo",
    val version: Int = 1,
    val computerName: String = "",
    val os: String = "Windows 11",
    val port: Int = 51821,
    val status: String = "Available",
    val instanceId: String = ""
)

data class DiscoveredServer(
    val computerName: String,
    val hostAddress: String,
    val port: Int = 51821,
    val os: String = "Windows",
    val status: String = "Available",
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)

@Serializable
data class PairRequestPayload(
    val deviceId: String,
    val deviceName: String,
    val pin: String
)

@Serializable
data class PairResponsePayload(
    val status: String, // "success" | "rejected"
    val token: String? = null,
    val message: String = ""
)

@Serializable
data class AuthPayload(
    val deviceId: String,
    val token: String
)

@Serializable
data class AuthResponsePayload(
    val status: String, // "authenticated" | "unauthorized"
    val serverTime: Long = 0,
    val message: String? = null
)

@Serializable
data class MouseMovePayload(
    val dx: Double,
    val dy: Double
)

@Serializable
data class MouseClickPayload(
    val button: String = "left"
)

@Serializable
data class MouseDownUpPayload(
    val button: String = "left"
)

@Serializable
data class MouseScrollPayload(
    val dx: Double,
    val dy: Double
)
