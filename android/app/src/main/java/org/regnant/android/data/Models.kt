package org.regnant.android.data

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR
}

data class HostIdentity(
    val hostId: String,
    val hostName: String,
    val ip: String,
    val port: Int,
    val certFingerprint: String,
    val lastSeen: Long = System.currentTimeMillis()
)

data class CommandRecord(
    val requestId: String,
    val rawCommand: String,
    val action: String,
    val target: String,
    val status: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class PairingRequest(
    val deviceId: String,
    val deviceName: String,
    val pairingCode: String
)

data class PairingResponse(
    val status: String,
    val token: String?,
    val hostId: String?,
    val hostName: String?,
    val certFingerprint: String?,
    val reason: String?
)

data class CommandSubmission(
    val requestId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val rawCommand: String? = null,
    val action: String? = null,
    val target: String? = null
)
