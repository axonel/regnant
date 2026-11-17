package org.regnant.android.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

class SecurityStore(context: Context) {
  private val prefs: SharedPreferences
  private val gson = Gson()

  init {
    val masterKey = MasterKey.Builder(context)
      .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
      .build()

    prefs = EncryptedSharedPreferences.create(
      context,
      "regnant_secure_prefs",
      masterKey,
      EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
      EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
  }

  fun getDeviceId(): String {
    var id = prefs.getString("device_id", null)
    if (id == null) {
      id = "android-" + UUID.randomUUID().toString()
      prefs.edit().putString("device_id", id).apply()
    }
    return id
  }

  fun getDeviceName(): String {
    val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
    return "$manufacturer ${Build.MODEL}"
  }

  fun savePairedHost(host: HostIdentity, token: String) {
    prefs.edit()
      .putString("host_id", host.hostId)
      .putString("host_name", host.hostName)
      .putString("host_ip", host.ip)
      .putInt("host_port", host.port)
      .putString("host_fingerprint", host.certFingerprint)
      .putString("host_token", token)
      .putLong("host_last_seen", System.currentTimeMillis())
      .apply()
  }

  fun getPairedHost(): HostIdentity? {
    val hostId = prefs.getString("host_id", null) ?: return null
    val hostName = prefs.getString("host_name", "Regnant Host") ?: "Regnant Host"
    val ip = prefs.getString("host_ip", "127.0.0.1") ?: "127.0.0.1"
    val port = prefs.getInt("host_port", 53317)
    val fingerprint = prefs.getString("host_fingerprint", "") ?: ""
    val lastSeen = prefs.getLong("host_last_seen", 0)

    return HostIdentity(
      hostId = hostId,
      hostName = hostName,
      ip = ip,
      port = port,
      certFingerprint = fingerprint,
      lastSeen = lastSeen
    )
  }

  fun updateHostIpAndPort(ip: String, port: Int) {
    prefs.edit()
      .putString("host_ip", ip)
      .putInt("host_port", port)
      .apply()
  }

  fun getAuthToken(): String? {
    return prefs.getString("host_token", null)
  }

  fun getCertificateFingerprint(): String? {
    return prefs.getString("host_fingerprint", null)
  }

  fun clearPairedHost() {
    prefs.edit()
      .remove("host_id")
      .remove("host_name")
      .remove("host_ip")
      .remove("host_port")
      .remove("host_fingerprint")
      .remove("host_token")
      .remove("host_last_seen")
      .apply()
  }

  fun isAutoReconnectEnabled(): Boolean {
    return prefs.getBoolean("auto_reconnect", true)
  }

  fun setAutoReconnectEnabled(enabled: Boolean) {
    prefs.edit().putBoolean("auto_reconnect", enabled).apply()
  }

  fun isPersistentServiceEnabled(): Boolean {
    return prefs.getBoolean("persistent_service", false)
  }

  fun setPersistentServiceEnabled(enabled: Boolean) {
    prefs.edit().putBoolean("persistent_service", enabled).apply()
  }

  fun isNotificationsEnabled(): Boolean {
    return prefs.getBoolean("notifications_enabled", true)
  }

  fun setNotificationsEnabled(enabled: Boolean) {
    prefs.edit().putBoolean("notifications_enabled", enabled).apply()
  }

  fun saveHistory(history: List<CommandRecord>) {
    val json = gson.toJson(history.take(50))
    prefs.edit().putString("command_history", json).apply()
  }

  fun getHistory(): List<CommandRecord> {
    val json = prefs.getString("command_history", null) ?: return emptyList()
    return try {
      val type = object : TypeToken<List<CommandRecord>>() {}.type
      gson.fromJson(json, type) ?: emptyList()
    } catch (e: Exception) {
      emptyList()
    }
  }
}
