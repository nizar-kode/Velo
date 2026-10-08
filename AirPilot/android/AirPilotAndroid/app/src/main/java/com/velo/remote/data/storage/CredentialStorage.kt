package com.velo.remote.data.storage

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

class CredentialStorage(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("velo_auth", Context.MODE_PRIVATE)

    val deviceId: String
        get() {
            var id = prefs.getString("device_id", null)
            if (id == null) {
                id = "velo-" + UUID.randomUUID().toString()
                prefs.edit().putString("device_id", id).apply()
            }
            return id
        }

    fun saveToken(host: String, token: String) {
        prefs.edit().putString("token_$host", token).apply()
    }

    fun getToken(host: String): String? {
        return prefs.getString("token_$host", null)
    }

    fun clearToken(host: String) {
        prefs.edit().remove("token_$host").apply()
    }

    fun clearAll() {
        val devId = deviceId
        prefs.edit().clear().putString("device_id", devId).apply()
    }
}
