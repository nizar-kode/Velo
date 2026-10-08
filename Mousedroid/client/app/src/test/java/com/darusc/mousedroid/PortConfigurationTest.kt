package com.darusc.mousedroid

import com.darusc.mousedroid.networking.ConnectionManager
import com.darusc.mousedroid.networking.isValidIpOrIpPort
import com.darusc.mousedroid.networking.parseAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortConfigurationTest {

    @Test
    fun testDefaultPortConstants() {
        assertEquals(48291, ConnectionManager.DEFAULT_WIFI_PORT)
        assertEquals(6969, ConnectionManager.DEFAULT_ADB_PORT)
    }

    @Test
    fun testParseAddressWithPort() {
        val (ip, port) = parseAddress("192.168.0.165:48291")
        assertEquals("192.168.0.165", ip)
        assertEquals(48291, port)
    }

    @Test
    fun testParseAddressWithoutPortUsesDefault() {
        val (ip, port) = parseAddress("192.168.0.165")
        assertEquals("192.168.0.165", ip)
        assertEquals(48291, port)
    }

    @Test
    fun testParseAddressBackwardsCompatibilityWithLegacyPort() {
        val (ip, port) = parseAddress("192.168.0.165:6969")
        assertEquals("192.168.0.165", ip)
        assertEquals(6969, port)
    }

    @Test
    fun testParseAddressCustomDefaultPort() {
        val (ip, port) = parseAddress("10.0.0.5", 55555)
        assertEquals("10.0.0.5", ip)
        assertEquals(55555, port)
    }

    @Test
    fun testParseAddressInvalidPortFallback() {
        val (ip, port) = parseAddress("192.168.0.165:abc")
        assertEquals("192.168.0.165", ip)
        assertEquals(48291, port)
    }

    @Test
    fun testIsValidIpOrIpPort() {
        assertTrue(isValidIpOrIpPort("192.168.0.165"))
        assertTrue(isValidIpOrIpPort("192.168.0.165:48291"))
        assertTrue(isValidIpOrIpPort("192.168.0.165:6969"))
        assertTrue(isValidIpOrIpPort("127.0.0.1:8080"))

        assertFalse(isValidIpOrIpPort("192.168.0.165:99999"))
        assertFalse(isValidIpOrIpPort("192.168.0.165:0"))
        assertFalse(isValidIpOrIpPort("192.168.0.256"))
        assertFalse(isValidIpOrIpPort("not_an_ip"))
        assertFalse(isValidIpOrIpPort(""))
        assertFalse(isValidIpOrIpPort(null))
    }
}
