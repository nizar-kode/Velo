package com.velo.remote

import com.velo.remote.data.model.DiscoveryBeacon
import com.velo.remote.data.model.MouseMovePayload
import com.velo.remote.data.model.PairRequestPayload
import com.velo.remote.data.model.PairResponsePayload
import com.velo.remote.data.model.VeloEnvelope
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ProtocolSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testMouseMoveSerialization() {
        val move = MouseMovePayload(dx = 12.0, dy = -4.0)
        val envelope = VeloEnvelope(
            version = 1,
            type = "mouse_move",
            timestamp = 1727188800000L,
            payload = json.encodeToJsonElement(move).jsonObject
        )

        val serialized = json.encodeToString(envelope)
        val deserialized = json.decodeFromString<VeloEnvelope>(serialized)

        assertEquals(1, deserialized.version)
        assertEquals("mouse_move", deserialized.type)
        assertEquals(1727188800000L, deserialized.timestamp)
        assertNotNull(deserialized.payload)
    }

    @Test
    fun testDiscoveryBeaconParsing() {
        val rawJson = """
            {
                "service": "Velo",
                "version": 1,
                "computerName": "Laptop-Nizar",
                "os": "Windows 11",
                "port": 51821,
                "status": "Available",
                "instanceId": "test-uuid-123"
            }
        """.trimIndent()

        val beacon = json.decodeFromString<DiscoveryBeacon>(rawJson)
        assertEquals("Velo", beacon.service)
        assertEquals(1, beacon.version)
        assertEquals("Laptop-Nizar", beacon.computerName)
        assertEquals("Windows 11", beacon.os)
        assertEquals(51821, beacon.port)
        assertEquals("Available", beacon.status)
    }

    @Test
    fun testPairRequestPayload() {
        val pairReq = PairRequestPayload(
            deviceId = "velo-001",
            deviceName = "Samsung Galaxy A34",
            pin = "482913"
        )
        val serialized = json.encodeToString(pairReq)
        val deserialized = json.decodeFromString<PairRequestPayload>(serialized)

        assertEquals("velo-001", deserialized.deviceId)
        assertEquals("Samsung Galaxy A34", deserialized.deviceName)
        assertEquals("482913", deserialized.pin)
    }

    @Test
    fun testPairResponseParsing() {
        val serverJson = """
            {
                "version": 1,
                "type": "pair_response",
                "timestamp": 1727188800000,
                "payload": {
                    "status": "success",
                    "token": "3e861f808a74d71fe04c811e120474cf81e584abb88d5de4a6da0634594f1f22",
                    "message": "Device paired successfully"
                }
            }
        """.trimIndent()

        val envelope = json.decodeFromString<VeloEnvelope>(serverJson)
        assertEquals("pair_response", envelope.type)
        val resp = kotlinx.serialization.json.Json.decodeFromJsonElement(
            PairResponsePayload.serializer(),
            envelope.payload
        )
        assertEquals("success", resp.status)
        assertEquals("3e861f808a74d71fe04c811e120474cf81e584abb88d5de4a6da0634594f1f22", resp.token)
        assertEquals("Device paired successfully", resp.message)
    }
}
