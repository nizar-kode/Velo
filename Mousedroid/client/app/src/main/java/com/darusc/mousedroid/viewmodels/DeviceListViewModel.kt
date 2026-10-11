package com.darusc.mousedroid.viewmodels

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.darusc.mousedroid.getDeviceDetails
import com.darusc.mousedroid.networking.Connection
import com.darusc.mousedroid.networking.ConnectionManager
import com.darusc.mousedroid.networking.parseAddress

/**
 * @param devices The list of bluetooth devices
 * @param sharedPreferences Shared preferences for saved WIFI devices
 */
class DeviceListViewModel(
    private val mode: Connection.Mode,
    private val devices: List<Pair<String, String>>?,
    private val sharedPreferences: SharedPreferences?
): BaseViewModel<DeviceListViewModel.State, DeviceListViewModel.Event>(State(emptyList())) {

    private var currentBtDevices: List<Pair<String, String>>? = devices
    sealed class Event: BaseViewModel.Event()
    data class State(val devices: List<Pair<String, String>>): BaseViewModel.State()

    private val connectionManager = ConnectionManager.getInstance()

    class Factory: ViewModelProvider.Factory {

        private val devices: List<Pair<String, String>>?
        private val sharedPreferences: SharedPreferences?

        /**
         * Create the viewmodel corresponding for bluetooth mode.
         * @param devices The list of paired bluetooth devices
         */
        @SuppressLint("MissingPermission")
        constructor(devices: Set<BluetoothDevice>) {
            this.devices = devices.map {
                Pair(it.name?: "Unknown", it.address)
            }
            this.sharedPreferences = null
        }

        /**
         * Create the viewmodel corresponding for wifi mode
         * @param sharedPreferences The shared preferences containing the stored WIFI devices
         */
        constructor(sharedPreferences: SharedPreferences?) {
            this.devices = null
            this.sharedPreferences = sharedPreferences
        }

        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if(modelClass.isAssignableFrom(DeviceListViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return if(this.devices != null) {
                    DeviceListViewModel(Connection.Mode.BLUETOOTH, devices, null) as T
                } else {
                    DeviceListViewModel(Connection.Mode.WIFI, null, sharedPreferences) as T
                }
            }
            throw IllegalArgumentException("Unknown viewmodel class")
        }
    }

    private var wifiDiscoveryManager: com.darusc.mousedroid.networking.WifiDiscoveryManager? = null

    init {
        updateState()
    }

    fun startAutoDiscovery(context: Context) {
        if(mode != Connection.Mode.WIFI) return
        wifiDiscoveryManager = com.darusc.mousedroid.networking.WifiDiscoveryManager(context) { name, address ->
            add(name, address)
        }
        wifiDiscoveryManager?.startDiscovery()
    }

    fun stopAutoDiscovery() {
        wifiDiscoveryManager?.stopDiscovery()
        wifiDiscoveryManager = null
    }

    fun add(name: String, address: String) {
        sharedPreferences?.edit { putString(name, address) }
        updateState()
    }

    fun remove(name: String) {
        sharedPreferences?.edit { remove(name) }
        updateState()
    }

    fun getDefaultWifiPort(): Int {
        return sharedPreferences?.getInt("DEFAULT_WIFI_PORT", ConnectionManager.DEFAULT_WIFI_PORT) ?: ConnectionManager.DEFAULT_WIFI_PORT
    }

    fun setDefaultWifiPort(port: Int) {
        sharedPreferences?.edit { putInt("DEFAULT_WIFI_PORT", port) }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun onDeviceClick(context: Context, name: String, address: String) {
        if(mode == Connection.Mode.WIFI) {
            val details = getDeviceDetails(context, Connection.Mode.WIFI)
            val defaultPort = getDefaultWifiPort()
            val (ip, port) = parseAddress(address, defaultPort)
            connectionManager.connectWIFI(ip, port, details)
        } else {
            connectionManager.connectBluetooth(address)
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshBluetoothDevices() {
        if (mode == Connection.Mode.BLUETOOTH) {
            val paired = com.darusc.mousedroid.networking.bluetooth.BluetoothAdapterWrapper.getInstance()?.pairedDevices ?: emptySet()
            currentBtDevices = paired.map {
                Pair(it.name ?: "Unknown", it.address)
            }
            updateState()
        }
    }

    private fun updateState() {
        if(mode == Connection.Mode.BLUETOOTH) {
            setState(State(currentBtDevices ?: emptyList()))
        } else {
            val wifiDevices = mutableListOf<Pair<String, String>>()
            sharedPreferences?.all?.let {
                for((name, value) in it) {
                    if (value is String) {
                        wifiDevices.add(Pair(name, value))
                    }
                }
            }
            setState(State(wifiDevices))
        }
    }
}