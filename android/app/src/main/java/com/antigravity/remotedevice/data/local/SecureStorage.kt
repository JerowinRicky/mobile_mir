package com.antigravity.remotedevice.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

class SecureStorage(context: Context) {

    companion object {
        private const val PREFS_FILE = "antigravity_secure_prefs"
        private const val KEY_DEVICE_ID = "pref_device_id"
        private const val KEY_DEVICE_NAME = "pref_device_name"
        private const val KEY_DEVICE_TOKEN = "pref_device_token"
        private const val KEY_IS_REGISTERED = "pref_is_registered"
        private const val KEY_IS_PAIRED = "pref_is_paired"
        private const val KEY_SERVER_URL = "pref_server_url"
    }

    private val sharedPreferences: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback for isolated test runners or initialization edge-cases
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
    }

    fun getOrCreateDeviceId(): String {
        var deviceId = sharedPreferences.getString(KEY_DEVICE_ID, null)
        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString()
            sharedPreferences.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        }
        return deviceId
    }

    fun getDeviceName(): String {
        return sharedPreferences.getString(KEY_DEVICE_NAME, android.os.Build.MODEL) ?: android.os.Build.MODEL
    }

    fun setDeviceName(name: String) {
        sharedPreferences.edit().putString(KEY_DEVICE_NAME, name).apply()
    }

    fun getDeviceToken(): String? {
        return sharedPreferences.getString(KEY_DEVICE_TOKEN, null)
    }

    fun setDeviceToken(token: String) {
        sharedPreferences.edit().putString(KEY_DEVICE_TOKEN, token).apply()
    }

    fun isRegistered(): Boolean {
        return sharedPreferences.getBoolean(KEY_IS_REGISTERED, false)
    }

    fun setRegistered(registered: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_IS_REGISTERED, registered).apply()
    }

    fun isPaired(): Boolean {
        return sharedPreferences.getBoolean(KEY_IS_PAIRED, false)
    }

    fun setPaired(paired: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_IS_PAIRED, paired).apply()
    }

    fun getServerUrl(): String {
        return sharedPreferences.getString(KEY_SERVER_URL, "https://api.remotedevice.internal") ?: "https://api.remotedevice.internal"
    }

    fun setServerUrl(url: String) {
        sharedPreferences.edit().putString(KEY_SERVER_URL, url).apply()
    }

    fun clear() {
        sharedPreferences.edit().clear().apply()
    }
}
