package org.regnant.android.network

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.regnant.android.data.CommandRecord
import org.regnant.android.data.CommandSubmission
import org.regnant.android.data.ConnectionStatus
import org.regnant.android.data.PairingRequest
import org.regnant.android.data.PairingResponse
import org.regnant.android.data.SecurityStore

class RegnantClient(private val securityStore: SecurityStore) {
  private val tag = "RegnantClient"
  private val gson = Gson()
  private val scope = CoroutineScope(Dispatchers.IO + Job())

  private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
  val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

  private val _commandEvents = MutableSharedFlow<CommandRecord>()
  val commandEvents: SharedFlow<CommandRecord> = _commandEvents.asSharedFlow()

  private var activeWebSocket: WebSocket? = null
  private var heartbeatJob: Job? = null
  private var reconnectJob: Job? = null
  private var isManuallyDisconnected = false

  private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

  /**
   * Builds an OkHttpClient configured for TLS pinning against the host's
   * certificate fingerprint.
   */
  private fun buildPinnedHttpClient(pinnedFingerprint: String?): OkHttpClient {
    val trustManager =
      object : X509TrustManager {
        override fun checkClientTrusted(
          chain: Array<out X509Certificate>?,
          authType: String?,
        ) {}

        override fun checkServerTrusted(
          chain: Array<out X509Certificate>?,
          authType: String?,
        ) {
          if (chain == null || chain.isEmpty()) {
            throw java.security.cert.CertificateException(
              "Empty certificate chain"
            )
          }

          val cert = chain[0]
          val computedFp = computeFingerprint(cert)

          if (pinnedFingerprint != null && pinnedFingerprint.isNotBlank()) {
            val normalizedPinned =
              pinnedFingerprint.replace(":", "").uppercase()
            val normalizedComputed = computedFp.replace(":", "").uppercase()
            if (normalizedPinned != normalizedComputed) {
              throw java.security.cert.CertificateException(
                "Certificate fingerprint mismatch! Expected $pinnedFingerprint but got $computedFp"
              )
            }
          }
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
      }

    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(null, arrayOf<TrustManager>(trustManager), null)

    return OkHttpClient.Builder()
      .sslSocketFactory(sslContext.socketFactory, trustManager)
      .hostnameVerifier { _, _ -> true } // Pinned by certificate SHA-256 fingerprint
      .connectTimeout(8, TimeUnit.SECONDS)
      .readTimeout(15, TimeUnit.SECONDS)
      .writeTimeout(15, TimeUnit.SECONDS)
      .build()
  }

  fun computeFingerprint(cert: X509Certificate): String {
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(cert.encoded)
    return digest.joinToString(":") { "%02X".format(it) }
  }

  private fun formatHostAddress(raw: String): String {
    val clean = raw.substringBefore("%")
    return if (clean.contains(":") && !clean.startsWith("[")) "[$clean]" else clean
  }

  /**
   * First-time pairing call: Submits entered 6-character code over TLS.
   */
  suspend fun pair(
    hostIp: String,
    port: Int,
    pairingCode: String,
  ): Result<PairingResponse> {
    return kotlinx.coroutines.withContext(Dispatchers.IO) {
      try {
        val client = buildPinnedHttpClient(null) // Unpinned for initial pairing
        val formattedHost = formatHostAddress(hostIp)
        val url = "https://$formattedHost:$port/api/v1/pair"

        val body =
          PairingRequest(
            deviceId = securityStore.getDeviceId(),
            deviceName = securityStore.getDeviceName(),
            pairingCode = pairingCode.trim().uppercase(),
          )

        val request =
          Request.Builder()
            .url(url)
            .post(gson.toJson(body).toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""

        if (response.isSuccessful) {
          val pairResponse =
            gson.fromJson(responseBody, PairingResponse::class.java)
          Result.success(pairResponse)
        } else {
          val errorJson =
            try {
              gson.fromJson(responseBody, JsonObject::class.java)
            } catch (e: Exception) {
              null
            }
          val reason =
            errorJson?.get("reason")?.asString
              ?: "Pairing failed: HTTP ${response.code}"
          Result.failure(Exception(reason))
        }
      } catch (e: Exception) {
        Result.failure(e)
      }
    }
  }

  /** Connects persistent WebSocket to paired host. */
  fun connect() {
    isManuallyDisconnected = false
    val host = securityStore.getPairedHost() ?: return
    val token = securityStore.getAuthToken() ?: return
    val fingerprint = securityStore.getCertificateFingerprint()

    if (_connectionStatus.value == ConnectionStatus.CONNECTED) return

    activeWebSocket?.cancel()
    activeWebSocket = null
    _connectionStatus.value = ConnectionStatus.CONNECTING

    val client = buildPinnedHttpClient(fingerprint)
    val formattedHost = formatHostAddress(host.ip)
    val wsUrl = "wss://$formattedHost:${host.port}/ws?token=$token"

    val request = Request.Builder().url(wsUrl).build()

    activeWebSocket =
      client.newWebSocket(
        request,
        object : WebSocketListener() {
          override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(tag, "WebSocket connected successfully to ${host.hostName}")
            _connectionStatus.value = ConnectionStatus.CONNECTED
            reconnectJob?.cancel()
            reconnectJob = null
            startHeartbeat(webSocket)
          }

          override fun onMessage(webSocket: WebSocket, text: String) {
            handleIncomingMessage(text)
          }

          override fun onClosing(
            webSocket: WebSocket,
            code: Int,
            reason: String,
          ) {
            Log.d(tag, "WebSocket closing: $code $reason")
          }

          override fun onClosed(
            webSocket: WebSocket,
            code: Int,
            reason: String,
          ) {
            Log.d(tag, "WebSocket closed: $code $reason")
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
            stopHeartbeat()
            if (!isManuallyDisconnected && securityStore.isAutoReconnectEnabled()) {
              scheduleReconnect()
            }
          }

          override fun onFailure(
            webSocket: WebSocket,
            t: Throwable,
            response: Response?,
          ) {
            Log.e(tag, "WebSocket failure: ${t.message}")
            _connectionStatus.value = ConnectionStatus.ERROR
            stopHeartbeat()
            if (!isManuallyDisconnected && securityStore.isAutoReconnectEnabled()) {
              scheduleReconnect()
            }
          }
        },
      )
  }

  fun disconnect() {
    isManuallyDisconnected = true
    reconnectJob?.cancel()
    reconnectJob = null
    stopHeartbeat()
    activeWebSocket?.close(1000, "User initiated disconnect")
    activeWebSocket = null
    _connectionStatus.value = ConnectionStatus.DISCONNECTED
  }

  private fun startHeartbeat(webSocket: WebSocket) {
    heartbeatJob?.cancel()
    heartbeatJob =
      scope.launch {
        while (isActive) {
          delay(15000)
          val ping = JsonObject()
          ping.addProperty("type", "PING")
          webSocket.send(ping.toString())
        }
      }
  }

  private fun stopHeartbeat() {
    heartbeatJob?.cancel()
    heartbeatJob = null
  }

  private fun scheduleReconnect() {
    if (reconnectJob?.isActive == true) return
    reconnectJob =
      scope.launch {
        var delayMs = 1500L
        while (
          isActive &&
            _connectionStatus.value != ConnectionStatus.CONNECTED &&
            !isManuallyDisconnected
        ) {
          _connectionStatus.value = ConnectionStatus.RECONNECTING
          delay(delayMs)
          connect()
          delayMs = (delayMs * 2).coerceAtMost(5000L)
        }
      }
  }

  private fun handleIncomingMessage(text: String) {
    try {
      val json = gson.fromJson(text, JsonObject::class.java)
      val type = json.get("type")?.asString ?: return

      if (type == "COMMAND_STATUS") {
        val data = json.getAsJsonObject("data") ?: return
        val record =
          CommandRecord(
            requestId = data.get("requestId")?.asString ?: "",
            rawCommand = data.get("rawCommand")?.asString ?: "",
            action = data.get("action")?.asString ?: "",
            target = data.get("target")?.asString ?: "",
            status = data.get("status")?.asString ?: "UNKNOWN",
            message =
              data.get("message")?.asString
                ?: data.get("error")?.asString
                ?: "",
            timestamp =
              data.get("executedAt")?.asLong ?: System.currentTimeMillis(),
          )
        scope.launch { _commandEvents.emit(record) }
      }
    } catch (e: Exception) {
      Log.e(tag, "Error parsing incoming WS message: ${e.message}")
    }
  }

  /**
   * Submits a command either over active WebSocket or falls back to REST HTTPS.
   */
  suspend fun sendCommand(
    rawCommand: String? = null,
    action: String? = null,
    target: String? = null,
  ): Result<CommandRecord> {
    return kotlinx.coroutines.withContext(Dispatchers.IO) {
      val host =
        securityStore.getPairedHost()
          ?: return@withContext Result.failure(Exception("No paired host"))
      val token =
        securityStore.getAuthToken()
          ?: return@withContext Result.failure(Exception("No auth token"))
      val fingerprint = securityStore.getCertificateFingerprint()

      val requestId = java.util.UUID.randomUUID().toString()
      val submission =
        CommandSubmission(
          requestId = requestId,
          rawCommand = rawCommand,
          action = action,
          target = target,
        )

      // If WebSocket is connected, send over WS
      if (
        activeWebSocket != null &&
          _connectionStatus.value == ConnectionStatus.CONNECTED
      ) {
        val wsPayload = JsonObject()
        wsPayload.addProperty("type", "COMMAND")
        wsPayload.add("data", gson.toJsonTree(submission))
        val sent = activeWebSocket!!.send(wsPayload.toString())
        if (sent) {
          return@withContext Result.success(
            CommandRecord(
              requestId = requestId,
              rawCommand = rawCommand ?: "$action $target",
              action = action ?: "unknown",
              target = target ?: "unknown",
              status = "RUNNING",
              message = "Sent via WebSocket...",
              timestamp = System.currentTimeMillis(),
            )
          )
        }
      }

      // Otherwise send via REST HTTPS endpoint
      try {
        val client = buildPinnedHttpClient(fingerprint)
        val formattedHost = formatHostAddress(host.ip)
        val url = "https://$formattedHost:${host.port}/api/v1/commands"

        val request =
          Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .post(gson.toJson(submission).toRequestBody(jsonMediaType))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""

        val json = gson.fromJson(responseBody, JsonObject::class.java)
        val status = json.get("status")?.asString ?: "UNKNOWN"
        val message =
          json.get("message")?.asString
            ?: json.get("reason")?.asString
            ?: json.get("error")?.asString
            ?: ""

        val record =
          CommandRecord(
            requestId = requestId,
            rawCommand = rawCommand ?: "$action $target",
            action = action ?: json.get("action")?.asString ?: "",
            target = target ?: json.get("target")?.asString ?: "",
            status = status,
            message = message,
            timestamp = System.currentTimeMillis(),
          )

        if (response.isSuccessful) {
          Result.success(record)
        } else {
          Result.failure(Exception(message.ifEmpty { "HTTP ${response.code}" }))
        }
      } catch (e: Exception) {
        Result.failure(e)
      }
    }
  }
}
