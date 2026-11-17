package org.regnant.android.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import org.regnant.android.data.*

class RegnantClient(
    private val context: Context,
    private val securityStore: SecurityStore
) {
    private val tag = "RegnantClient"
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _commandEvents = MutableSharedFlow<CommandRecord>()
    val commandEvents: SharedFlow<CommandRecord> = _commandEvents.asSharedFlow()

    private val _metricsEvents = MutableStateFlow<HostMetrics?>(null)
    val metricsEvents: StateFlow<HostMetrics?> = _metricsEvents.asStateFlow()

    private var activeWebSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var isManuallyDisconnected = false

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Resolves workstation.regnant.local to the target IP, appending the Wi-Fi scope interface (e.g. %wlan0)
     * when the target IP is an IPv6 link-local address. This bypasses RFC 3986 percent-encoding URL restrictions
     * while allowing Java sockets to send link-local packets out the correct interface.
     */
    private class RegnantDns(
        private val targetIp: String,
        private val ifaceName: String
    ) : okhttp3.Dns {
        override fun lookup(hostname: String): List<java.net.InetAddress> {
            if (hostname == "workstation.regnant.local" || hostname == "node.regnant.local") {
                val clean = targetIp.substringBefore("%")
                return if (clean.contains(":")) {
                    try {
                        listOf(java.net.Inet6Address.getByName("$clean%$ifaceName"))
                    } catch (e: Exception) {
                        listOf(java.net.InetAddress.getByName(clean))
                    }
                } else {
                    listOf(java.net.InetAddress.getByName(clean))
                }
            }
            return okhttp3.Dns.SYSTEM.lookup(hostname)
        }
    }

    /**
     * Builds an OkHttpClient configured for TLS pinning against the host certificate fingerprint
     * and binds sockets/DNS to the active Wi-Fi interface.
     */
    private fun buildPinnedHttpClient(pinnedFingerprint: String?, targetHostIp: String? = null): OkHttpClient {
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}

            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                if (chain == null || chain.isEmpty()) {
                    throw java.security.cert.CertificateException("Empty certificate chain")
                }

                val cert = chain[0]
                val computedFp = computeFingerprint(cert)

                if (pinnedFingerprint != null && pinnedFingerprint.isNotBlank()) {
                    val normalizedPinned = pinnedFingerprint.replace(":", "").uppercase()
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

        val builder = OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true } // Pinned via SHA-256 certificate fingerprint
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)

        var wifiIface = "wlan0"
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val wifiNetwork = cm?.allNetworks?.firstOrNull { network ->
                val caps = cm.getNetworkCapabilities(network)
                caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            }
            if (wifiNetwork != null) {
                cm.bindProcessToNetwork(wifiNetwork)
                val lp = cm.getLinkProperties(wifiNetwork)
                if (lp?.interfaceName != null) {
                    wifiIface = lp.interfaceName!!
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Could not bind process to Wi-Fi: ${e.message}")
        }

        if (targetHostIp != null) {
            builder.dns(RegnantDns(targetHostIp, wifiIface))
        }

        return builder.build()
    }

    fun computeFingerprint(cert: X509Certificate): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(cert.encoded)
        return digest.joinToString(":") { "%02X".format(it) }
    }

    /**
     * First-time pairing: Submits entered 6-character code over TLS.
     */
    suspend fun pair(hostIp: String, port: Int, pairingCode: String): Result<PairingResponse> {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                Log.d(tag, "Attempting pair with $hostIp:$port code=$pairingCode")
                val client = buildPinnedHttpClient(null, hostIp) // Unpinned for initial pairing
                val url = "https://workstation.regnant.local:$port/api/v1/pair"

                val body = PairingRequest(
                    deviceId = securityStore.getDeviceId(),
                    deviceName = securityStore.getDeviceName(),
                    pairingCode = pairingCode.trim().uppercase()
                )

                val request = Request.Builder()
                    .url(url)
                    .post(gson.toJson(body).toRequestBody(jsonMediaType))
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""
                Log.d(tag, "Pair response code=${response.code}: $responseBody")

                if (response.isSuccessful) {
                    val pairResponse = gson.fromJson(responseBody, PairingResponse::class.java)
                    Result.success(pairResponse)
                } else {
                    val errorJson = try { gson.fromJson(responseBody, JsonObject::class.java) } catch (e: Exception) { null }
                    val reason = errorJson?.get("reason")?.asString ?: "Pairing failed: HTTP ${response.code}"
                    Result.failure(Exception(reason))
                }
            } catch (e: Exception) {
                Log.e(tag, "Pair exception: ${e.message}", e)
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

        val client = buildPinnedHttpClient(fingerprint, host.ip)
        val wsUrl = "wss://workstation.regnant.local:${host.port}/ws?token=$token"

        val request = Request.Builder().url(wsUrl).build()

        activeWebSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(tag, "WebSocket connected successfully to ${host.hostName} over Wi-Fi")
                _connectionStatus.value = ConnectionStatus.CONNECTED
                reconnectJob?.cancel()
                reconnectJob = null
                startHeartbeat(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
                stopHeartbeat()
                if (!isManuallyDisconnected && securityStore.isAutoReconnectEnabled()) {
                    scheduleReconnect()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(tag, "WebSocket failure: ${t.message}")
                _connectionStatus.value = ConnectionStatus.ERROR
                stopHeartbeat()
                if (!isManuallyDisconnected && securityStore.isAutoReconnectEnabled()) {
                    scheduleReconnect()
                }
            }
        })
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
        heartbeatJob = scope.launch {
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
        reconnectJob = scope.launch {
            var delayMs = 1500L
            while (isActive && _connectionStatus.value != ConnectionStatus.CONNECTED && !isManuallyDisconnected) {
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
                val record = CommandRecord(
                    requestId = data.get("requestId")?.asString ?: "",
                    rawCommand = data.get("rawCommand")?.asString ?: "",
                    action = data.get("action")?.asString ?: "",
                    target = data.get("target")?.asString ?: "",
                    status = data.get("status")?.asString ?: "UNKNOWN",
                    message = data.get("message")?.asString ?: data.get("error")?.asString ?: "",
                    timestamp = data.get("executedAt")?.asLong ?: System.currentTimeMillis()
                )
                scope.launch { _commandEvents.emit(record) }
            } else if (type == "METRICS_UPDATE") {
                val data = json.getAsJsonObject("data") ?: return
                val metrics = gson.fromJson(data, HostMetrics::class.java)
                _metricsEvents.value = metrics
            }
        } catch (e: Exception) {
            Log.e(tag, "Error parsing incoming WS message: ${e.message}")
        }
    }

    // --- General REST Request Helper ---
    private fun <T> executeAuthRequest(
        path: String,
        method: String = "GET",
        bodyJson: String? = null,
        responseClass: Class<T>
    ): Result<T> {
        val host = securityStore.getPairedHost() ?: return Result.failure(Exception("No paired host"))
        val token = securityStore.getAuthToken() ?: return Result.failure(Exception("No auth token"))
        val fingerprint = securityStore.getCertificateFingerprint()

        try {
            val client = buildPinnedHttpClient(fingerprint, host.ip)
            val url = "https://workstation.regnant.local:${host.port}$path"

            val reqBuilder = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")

            if (method == "POST") {
                val reqBody = (bodyJson ?: "{}").toRequestBody(jsonMediaType)
                reqBuilder.post(reqBody)
            } else {
                reqBuilder.get()
            }

            val response = client.newCall(reqBuilder.build()).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val parsed = gson.fromJson(responseBody, responseClass)
                return Result.success(parsed)
            } else {
                val errorJson = try { gson.fromJson(responseBody, JsonObject::class.java) } catch (e: Exception) { null }
                val errorMsg = errorJson?.get("error")?.asString ?: "Request failed: HTTP ${response.code}"
                return Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    // --- Command Submission ---
    suspend fun sendCommand(rawCommand: String? = null, action: String? = null, target: String? = null): Result<CommandRecord> {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            val host = securityStore.getPairedHost() ?: return@withContext Result.failure(Exception("No paired host"))
            val token = securityStore.getAuthToken() ?: return@withContext Result.failure(Exception("No auth token"))
            val fingerprint = securityStore.getCertificateFingerprint()

            val requestId = java.util.UUID.randomUUID().toString()
            val submission = CommandSubmission(
                requestId = requestId,
                rawCommand = rawCommand,
                action = action,
                target = target
            )

            // Try active WebSocket first
            if (activeWebSocket != null && _connectionStatus.value == ConnectionStatus.CONNECTED) {
                val wsPayload = JsonObject()
                wsPayload.addProperty("type", "COMMAND")
                wsPayload.add("data", gson.toJsonTree(submission))
                if (activeWebSocket!!.send(wsPayload.toString())) {
                    return@withContext Result.success(
                        CommandRecord(
                            requestId = requestId,
                            rawCommand = rawCommand ?: "$action $target",
                            action = action ?: "unknown",
                            target = target ?: "unknown",
                            status = "RUNNING",
                            message = "Sent via WebSocket...",
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
            }

            // Fallback to REST HTTPS
            try {
                val client = buildPinnedHttpClient(fingerprint, host.ip)
                val url = "https://workstation.regnant.local:${host.port}/api/v1/commands"

                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $token")
                    .post(gson.toJson(submission).toRequestBody(jsonMediaType))
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""
                val json = gson.fromJson(responseBody, JsonObject::class.java)
                val status = json.get("status")?.asString ?: "UNKNOWN"
                val message = json.get("message")?.asString ?: json.get("reason")?.asString ?: json.get("error")?.asString ?: ""

                val record = CommandRecord(
                    requestId = requestId,
                    rawCommand = rawCommand ?: "$action $target",
                    action = action ?: json.get("action")?.asString ?: "",
                    target = target ?: json.get("target")?.asString ?: "",
                    status = status,
                    message = message,
                    timestamp = System.currentTimeMillis()
                )

                if (response.isSuccessful) Result.success(record) else Result.failure(Exception(message))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // --- Feature APIs ---

    suspend fun getMetrics(): Result<HostMetrics> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val res = executeAuthRequest("/api/v1/metrics", "GET", null, HostMetrics::class.java)
        if (res.isSuccess) _metricsEvents.value = res.getOrNull()
        res
    }

    suspend fun getProjects(): Result<ProjectsResponse> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        executeAuthRequest("/api/v1/projects", "GET", null, ProjectsResponse::class.java)
    }

    suspend fun launchProject(projectId: String, ide: String? = null): Result<String> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val payload = JsonObject().apply {
            addProperty("id", projectId)
            if (ide != null) addProperty("ide", ide)
        }
        val res = executeAuthRequest("/api/v1/projects/launch", "POST", payload.toString(), JsonObject::class.java)
        if (res.isSuccess) {
            Result.success(res.getOrNull()?.get("message")?.asString ?: "Project launched")
        } else {
            Result.failure(res.exceptionOrNull() ?: Exception("Launch failed"))
        }
    }

    suspend fun registerProject(name: String, path: String, ide: String = "auto"): Result<ProjectItem> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val payload = JsonObject().apply {
            addProperty("name", name)
            addProperty("path", path)
            addProperty("ide", ide)
        }
        executeAuthRequest("/api/v1/projects/register", "POST", payload.toString(), ProjectItem::class.java)
    }

    suspend fun listFiles(path: String? = null): Result<FileListResponse> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val query = if (path != null) "?path=" + java.net.URLEncoder.encode(path, "UTF-8") else ""
        executeAuthRequest("/api/v1/files$query", "GET", null, FileListResponse::class.java)
    }

    suspend fun readFile(path: String): Result<FileReadResponse> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val query = "?path=" + java.net.URLEncoder.encode(path, "UTF-8")
        executeAuthRequest("/api/v1/files/read$query", "GET", null, FileReadResponse::class.java)
    }

    suspend fun readClipboard(): Result<String> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val res = executeAuthRequest("/api/v1/clipboard", "GET", null, JsonObject::class.java)
        if (res.isSuccess) {
            Result.success(res.getOrNull()?.get("text")?.asString ?: "")
        } else {
            Result.failure(res.exceptionOrNull() ?: Exception("Clipboard read failed"))
        }
    }

    suspend fun writeClipboard(text: String): Result<Boolean> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val payload = JsonObject().apply { addProperty("text", text) }
        val res = executeAuthRequest("/api/v1/clipboard", "POST", payload.toString(), JsonObject::class.java)
        if (res.isSuccess) Result.success(true) else Result.failure(res.exceptionOrNull() ?: Exception("Write failed"))
    }

    suspend fun getRunningApps(): Result<List<RunningApp>> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val res = executeAuthRequest("/api/v1/apps", "GET", null, JsonObject::class.java)
        if (res.isSuccess) {
            val appsArray = res.getOrNull()?.getAsJsonArray("apps") ?: return@withContext Result.success(emptyList())
            val list = mutableListOf<RunningApp>()
            appsArray.forEach { elem ->
                list.add(gson.fromJson(elem, RunningApp::class.java))
            }
            Result.success(list)
        } else {
            Result.failure(res.exceptionOrNull() ?: Exception("Query apps failed"))
        }
    }

    suspend fun closeApp(address: String): Result<Boolean> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val payload = JsonObject().apply { addProperty("address", address) }
        val res = executeAuthRequest("/api/v1/apps/close", "POST", payload.toString(), JsonObject::class.java)
        if (res.isSuccess) Result.success(true) else Result.failure(res.exceptionOrNull() ?: Exception("Close failed"))
    }

    suspend fun lockWorkstation(): Result<Boolean> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val res = executeAuthRequest("/api/v1/power/lock", "POST", "{}", JsonObject::class.java)
        if (res.isSuccess) Result.success(true) else Result.failure(res.exceptionOrNull() ?: Exception("Lock failed"))
    }

    suspend fun suspendWorkstation(): Result<Boolean> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val res = executeAuthRequest("/api/v1/power/suspend", "POST", "{}", JsonObject::class.java)
        if (res.isSuccess) Result.success(true) else Result.failure(res.exceptionOrNull() ?: Exception("Suspend failed"))
    }

    suspend fun getRoutines(): Result<List<RoutineItem>> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val res = executeAuthRequest("/api/v1/routines", "GET", null, JsonObject::class.java)
        if (res.isSuccess) {
            val arr = res.getOrNull()?.getAsJsonArray("routines") ?: return@withContext Result.success(emptyList())
            val list = mutableListOf<RoutineItem>()
            arr.forEach { list.add(gson.fromJson(it, RoutineItem::class.java)) }
            Result.success(list)
        } else {
            Result.failure(res.exceptionOrNull() ?: Exception("Routines list failed"))
        }
    }

    suspend fun executeRoutine(routineId: String): Result<RoutineResult> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val payload = JsonObject().apply { addProperty("routineId", routineId) }
        executeAuthRequest("/api/v1/routines/execute", "POST", payload.toString(), RoutineResult::class.java)
    }

    suspend fun openUrl(url: String): Result<Boolean> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val payload = JsonObject().apply { addProperty("url", url) }
        val res = executeAuthRequest("/api/v1/web/open", "POST", payload.toString(), JsonObject::class.java)
        if (res.isSuccess) Result.success(true) else Result.failure(res.exceptionOrNull() ?: Exception("Open URL failed"))
    }

    suspend fun searchWeb(query: String): Result<Boolean> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val payload = JsonObject().apply { addProperty("query", query) }
        val res = executeAuthRequest("/api/v1/web/search", "POST", payload.toString(), JsonObject::class.java)
        if (res.isSuccess) Result.success(true) else Result.failure(res.exceptionOrNull() ?: Exception("Search failed"))
    }
}
