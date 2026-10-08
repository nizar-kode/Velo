package com.darusc.mousedroid.networking

import android.Manifest
import android.content.Context
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.annotation.RequiresPermission

fun hasUsbConnection(context: Context): Boolean {
    val intent = context.registerReceiver(
        null,
        IntentFilter("android.hardware.usb.action.USB_STATE")
    )
    return intent?.getBooleanExtra("connected", false) == true
}

@RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
fun hasWifiConnection(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
}

/**
 * Parses raw address string into IP address and Port.
 * Supports "192.168.0.165" -> ("192.168.0.165", defaultPort)
 * and "192.168.0.165:48291" -> ("192.168.0.165", 48291).
 */
fun parseAddress(rawAddress: String, defaultPort: Int = ConnectionManager.DEFAULT_WIFI_PORT): Pair<String, Int> {
    val trimmed = rawAddress.trim()
    if (trimmed.contains(":")) {
        val parts = trimmed.split(":")
        val ip = parts[0].trim()
        val port = parts.getOrNull(1)?.trim()?.toIntOrNull()
        if (port != null && port in 1..65535) {
            return Pair(ip, port)
        }
        return Pair(ip, defaultPort)
    }
    return Pair(trimmed, defaultPort)
}

/**
 * Validates if the given string is a valid IPv4 address or IPv4:port string.
 */
fun isValidIpOrIpPort(input: String?): Boolean {
    if (input.isNullOrBlank()) return false
    val trimmed = input.trim()
    val parts = trimmed.split(":")
    if (parts.size > 2) return false
    val ipRegex = Regex("^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\$")
    if (!parts[0].matches(ipRegex)) return false
    if (parts.size == 2) {
        val port = parts[1].toIntOrNull() ?: return false
        return port in 1..65535
    }
    return true
}