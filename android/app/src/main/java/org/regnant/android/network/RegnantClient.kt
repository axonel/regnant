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

    private val _terminalDataEvents = MutableSharedFlow<Pair<String, String>>()
    val terminalDataEvents: SharedFlow<Pair<String, String>> = _terminalDataEvents.asSharedFlow()

    private val _agentEvents = MutableSharedFlow<JsonObject>()
    val agentEvents: SharedFlow<JsonObject> = _agentEvents.asSharedFlow()

    private val _workspaceState = MutableStateFlow<WorkspaceState?>(null)
    val workspaceState: StateFlow<WorkspaceState?> = _workspaceState.asStateFlow()

    private val _lockState = MutableStateFlow<LockState?>(null)
    val lockState: StateFlow<LockState?> = _lockState.asStateFlow()

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
                scope.launch {
                    getActiveWorkspace()
                    getLockStatus()
                }
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
            } else if (type == "TERM_DATA") {
                val sId = json.get("sessionId")?.asString ?: ""
                val chunk = json.get("data")?.asString ?: ""
                scope.launch { _terminalDataEvents.emit(Pair(sId, chunk)) }
            } else if (type == "AGENT_OUTPUT" || type == "AGENT_STATUS" || type == "AGENT_STARTED" || type == "AGENT_ERROR") {
                scope.launch { _agentEvents.emit(json) }
            } else if (type == "WORKSPACE_STATE") {
                try {
                    val ws = gson.fromJson(json, WorkspaceState::class.java)
                    _workspaceState.value = ws
                } catch (e: Exception) {
                    Log.w(tag, "Failed to parse WORKSPACE_STATE", e)
                }
            } else if (type == "LOCK_STATE") {
                try {
                    val ls = gson.fromJson(json, LockState::class.java)
                    _lockState.value = ls
                } catch (e: Exception) {
                    Log.w(tag, "Failed to parse LOCK_STATE", e)
                }
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
        if (res.isSuccess) {
            _lockState.value = LockState(locked = true, state = "LOCKED", timestamp = System.currentTimeMillis())
            Result.success(true)
        } else {
            Result.failure(res.exceptionOrNull() ?: Exception("Lock failed"))
        }
    }

    suspend fun getLockStatus(): Result<LockState> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val res = executeAuthRequest("/api/v1/power/lock-status", "GET", null, LockState::class.java)
        if (res.isSuccess) {
            val state = res.getOrNull()
            if (state != null) _lockState.value = state
            res
        } else {
            Result.failure(res.exceptionOrNull() ?: Exception("Failed to get lock status"))
        }
    }

    suspend fun getActiveWorkspace(): Result<WorkspaceState> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val res = executeAuthRequest("/api/v1/hypr/active-workspace", "GET", null, WorkspaceState::class.java)
        if (res.isSuccess) {
            val state = res.getOrNull()
            if (state != null) _workspaceState.value = state
            res
        } else {
            Result.failure(res.exceptionOrNull() ?: Exception("Failed to get active workspace"))
        }
    }

    suspend fun switchWorkspace(slot: Int, monitorName: String? = null): Result<WorkspaceState> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        if (slot < 1 || slot > 5) return@withContext Result.failure(IllegalArgumentException("Workspace slot must be 1..5"))
        val payload = JsonObject().apply {
            addProperty("workspaceId", slot)
            if (monitorName != null) addProperty("monitorName", monitorName)
        }
        val res = executeAuthRequest("/api/v1/hypr/workspace", "POST", payload.toString(), WorkspaceState::class.java)
        if (res.isSuccess) {
            val state = res.getOrNull()
            if (state != null) _workspaceState.value = state
            res
        } else {
            val msg = JsonObject().apply {
                addProperty("type", "SWITCH_WORKSPACE")
                addProperty("workspaceId", slot)
                if (monitorName != null) addProperty("monitorName", monitorName)
            }
            sendWebSocketMessage(msg)
            Result.failure(res.exceptionOrNull() ?: Exception("Failed to switch workspace"))
        }
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

    // --- v1.2 API Extensions ---

    fun executeAuthRequestBytes(path: String): Result<ByteArray> {
        val host = securityStore.getPairedHost() ?: return Result.failure(Exception("No paired host"))
        val token = securityStore.getAuthToken() ?: return Result.failure(Exception("No auth token"))
        val fingerprint = securityStore.getCertificateFingerprint()
        val client = buildPinnedHttpClient(fingerprint, host.ip)

        val url = "https://workstation.regnant.local:${host.port}$path"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        return try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val bytes = response.body?.bytes() ?: ByteArray(0)
                Result.success(bytes)
            } else {
                Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun sendWebSocketMessage(json: JsonObject): Boolean {
        val ws = activeWebSocket ?: return false
        return ws.send(json.toString())
    }

    // Agents
    suspend fun getAgents(): Result<List<AgentItem>> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val res = executeAuthRequest("/api/v1/agents", "GET", null, JsonObject::class.java)
        if (res.isSuccess) {
            val arr = res.getOrNull()?.getAsJsonArray("agents") ?: return@withContext Result.success(emptyList())
            val list = mutableListOf<AgentItem>()
            arr.forEach { list.add(gson.fromJson(it, AgentItem::class.java)) }
            Result.success(list)
        } else {
            Result.failure(res.exceptionOrNull() ?: Exception("Failed to get agents"))
        }
    }

    suspend fun startAgent(agentId: String, workspacePath: String, prompt: String): Result<JsonObject> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val payload = JsonObject().apply {
            addProperty("agentId", agentId)
            addProperty("workspacePath", workspacePath)
            addProperty("prompt", prompt)
        }
        executeAuthRequest("/api/v1/agents/start", "POST", payload.toString(), JsonObject::class.java)
    }

    suspend fun sendAgentInput(sessionId: String, input: String): Result<Boolean> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val payload = JsonObject().apply {
            addProperty("sessionId", sessionId)
            addProperty("input", input)
        }
        val res = executeAuthRequest("/api/v1/agents/input", "POST", payload.toString(), JsonObject::class.java)
        if (res.isSuccess) Result.success(true) else Result.failure(res.exceptionOrNull() ?: Exception("Failed to send input"))
    }

    suspend fun cancelAgent(sessionId: String): Result<Boolean> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val payload = JsonObject().apply { addProperty("sessionId", sessionId) }
        val res = executeAuthRequest("/api/v1/agents/cancel", "POST", payload.toString(), JsonObject::class.java)
        if (res.isSuccess) Result.success(true) else Result.failure(res.exceptionOrNull() ?: Exception("Failed to cancel agent"))
    }

    suspend fun getAgentSessions(): Result<List<AgentSessionItem>> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val res = executeAuthRequest("/api/v1/agents/sessions", "GET", null, JsonObject::class.java)
        if (res.isSuccess) {
            val arr = res.getOrNull()?.getAsJsonArray("sessions") ?: return@withContext Result.success(emptyList())
            val list = mutableListOf<AgentSessionItem>()
            arr.forEach { list.add(gson.fromJson(it, AgentSessionItem::class.java)) }
            Result.success(list)
        } else {
            Result.failure(res.exceptionOrNull() ?: Exception("Failed to get sessions"))
        }
    }

    // Terminal
    suspend fun createTerminal(cwd: String?, cols: Int = 80, rows: Int = 24, title: String = "Terminal"): Result<TerminalSessionItem> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val payload = JsonObject().apply {
            if (cwd != null) addProperty("cwd", cwd)
            addProperty("cols", cols)
            addProperty("rows", rows)
            addProperty("title", title)
        }
        executeAuthRequest("/api/v1/terminal/create", "POST", payload.toString(), TerminalSessionItem::class.java)
    }

    suspend fun listTerminals(): Result<List<TerminalSessionItem>> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val res = executeAuthRequest("/api/v1/terminal/list", "GET", null, JsonObject::class.java)
        if (res.isSuccess) {
            val arr = res.getOrNull()?.getAsJsonArray("sessions") ?: return@withContext Result.success(emptyList())
            val list = mutableListOf<TerminalSessionItem>()
            arr.forEach { list.add(gson.fromJson(it, TerminalSessionItem::class.java)) }
            Result.success(list)
        } else {
            Result.failure(res.exceptionOrNull() ?: Exception("Failed to list terminals"))
        }
    }

    suspend fun getTerminalBuffer(sessionId: String): Result<String> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val res = executeAuthRequest("/api/v1/terminal/buffer?sessionId=$sessionId", "GET", null, JsonObject::class.java)
        if (res.isSuccess) {
            Result.success(res.getOrNull()?.get("buffer")?.asString ?: "")
        } else {
            Result.failure(res.exceptionOrNull() ?: Exception("Failed to get terminal buffer"))
        }
    }

    suspend fun closeTerminal(sessionId: String): Result<Boolean> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val payload = JsonObject().apply { addProperty("sessionId", sessionId) }
        val res = executeAuthRequest("/api/v1/terminal/close", "POST", payload.toString(), JsonObject::class.java)
        if (res.isSuccess) Result.success(true) else Result.failure(res.exceptionOrNull() ?: Exception("Failed to close terminal"))
    }

    fun sendTerminalInput(sessionId: String, data: String): Boolean {
        val msg = JsonObject().apply {
            addProperty("type", "TERM_INPUT")
            addProperty("sessionId", sessionId)
            addProperty("data", data)
        }
        val sent = sendWebSocketMessage(msg)
        if (!sent) {
            CoroutineScope(Dispatchers.IO).launch {
                executeAuthRequest("/api/v1/terminal/input", "POST", msg.toString(), JsonObject::class.java)
            }
        }
        return true
    }

    fun resizeTerminal(sessionId: String, cols: Int, rows: Int): Boolean {
        val msg = JsonObject().apply {
            addProperty("type", "TERM_RESIZE")
            addProperty("sessionId", sessionId)
            addProperty("cols", cols)
            addProperty("rows", rows)
        }
        return sendWebSocketMessage(msg)
    }

    // Git & GitHub
    suspend fun getGitStatus(path: String? = null): Result<GitStatusItem> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val query = if (path != null) "?path=" + java.net.URLEncoder.encode(path, "UTF-8") else ""
        executeAuthRequest("/api/v1/git/status$query", "GET", null, GitStatusItem::class.java)
    }

    suspend fun getGitBranches(path: String? = null): Result<List<GitBranchItem>> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val query = if (path != null) "?path=" + java.net.URLEncoder.encode(path, "UTF-8") else ""
        val res = executeAuthRequest("/api/v1/git/branches$query", "GET", null, JsonObject::class.java)
        if (res.isSuccess) {
            val arr = res.getOrNull()?.getAsJsonArray("branches") ?: return@withContext Result.success(emptyList())
            val list = mutableListOf<GitBranchItem>()
            arr.forEach { list.add(gson.fromJson(it, GitBranchItem::class.java)) }
            Result.success(list)
        } else {
            Result.failure(res.exceptionOrNull() ?: Exception("Failed to get git branches"))
        }
    }

    suspend fun getGitCommits(path: String? = null, limit: Int = 20): Result<List<GitCommitItem>> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val p = if (path != null) "&path=" + java.net.URLEncoder.encode(path, "UTF-8") else ""
        val res = executeAuthRequest("/api/v1/git/commits?limit=$limit$p", "GET", null, JsonObject::class.java)
        if (res.isSuccess) {
            val arr = res.getOrNull()?.getAsJsonArray("commits") ?: return@withContext Result.success(emptyList())
            val list = mutableListOf<GitCommitItem>()
            arr.forEach { list.add(gson.fromJson(it, GitCommitItem::class.java)) }
            Result.success(list)
        } else {
            Result.failure(res.exceptionOrNull() ?: Exception("Failed to get git commits"))
        }
    }

    suspend fun getGitDiff(path: String? = null, sha: String? = null): Result<String> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        var query = if (path != null) "?path=" + java.net.URLEncoder.encode(path, "UTF-8") else "?"
        if (sha != null) query += "&sha=$sha"
        val res = executeAuthRequest("/api/v1/git/diff$query", "GET", null, JsonObject::class.java)
        if (res.isSuccess) {
            Result.success(res.getOrNull()?.get("diff")?.asString ?: "")
        } else {
            Result.failure(res.exceptionOrNull() ?: Exception("Failed to get git diff"))
        }
    }

    suspend fun getGitHubAuth(): Result<JsonObject> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        executeAuthRequest("/api/v1/github/auth", "GET", null, JsonObject::class.java)
    }

    suspend fun getGitHubPRs(path: String? = null, limit: Int = 15): Result<List<GitHubPRItem>> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val p = if (path != null) "&path=" + java.net.URLEncoder.encode(path, "UTF-8") else ""
        val res = executeAuthRequest("/api/v1/github/prs?limit=$limit$p", "GET", null, JsonObject::class.java)
        if (res.isSuccess) {
            val arr = res.getOrNull()?.getAsJsonArray("pullRequests") ?: return@withContext Result.success(emptyList())
            val list = mutableListOf<GitHubPRItem>()
            arr.forEach { list.add(gson.fromJson(it, GitHubPRItem::class.java)) }
            Result.success(list)
        } else {
            Result.failure(res.exceptionOrNull() ?: Exception("Failed to get GitHub PRs"))
        }
    }

    suspend fun getGitHubRuns(path: String? = null, limit: Int = 15): Result<List<GitHubRunItem>> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val p = if (path != null) "&path=" + java.net.URLEncoder.encode(path, "UTF-8") else ""
        val res = executeAuthRequest("/api/v1/github/runs?limit=$limit$p", "GET", null, JsonObject::class.java)
        if (res.isSuccess) {
            val arr = res.getOrNull()?.getAsJsonArray("runs") ?: return@withContext Result.success(emptyList())
            val list = mutableListOf<GitHubRunItem>()
            arr.forEach { list.add(gson.fromJson(it, GitHubRunItem::class.java)) }
            Result.success(list)
        } else {
            Result.failure(res.exceptionOrNull() ?: Exception("Failed to get GitHub runs"))
        }
    }

    // Hyprland
    suspend fun getHyprWindows(): Result<List<HyprWindowItem>> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val res = executeAuthRequest("/api/v1/hypr/windows", "GET", null, JsonObject::class.java)
        if (res.isSuccess) {
            val arr = res.getOrNull()?.getAsJsonArray("windows") ?: return@withContext Result.success(emptyList())
            val list = mutableListOf<HyprWindowItem>()
            arr.forEach { list.add(gson.fromJson(it, HyprWindowItem::class.java)) }
            Result.success(list)
        } else {
            Result.failure(res.exceptionOrNull() ?: Exception("Failed to get Hyprland windows"))
        }
    }

    suspend fun getHyprWorkspaces(): Result<List<HyprWorkspaceItem>> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val res = executeAuthRequest("/api/v1/hypr/workspaces", "GET", null, JsonObject::class.java)
        if (res.isSuccess) {
            val arr = res.getOrNull()?.getAsJsonArray("workspaces") ?: return@withContext Result.success(emptyList())
            val list = mutableListOf<HyprWorkspaceItem>()
            arr.forEach { list.add(gson.fromJson(it, HyprWorkspaceItem::class.java)) }
            Result.success(list)
        } else {
            Result.failure(res.exceptionOrNull() ?: Exception("Failed to get Hyprland workspaces"))
        }
    }

    suspend fun dispatchHypr(action: String, address: String? = null, workspaceId: Int? = null): Result<Boolean> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val payload = JsonObject().apply {
            addProperty("action", action)
            if (address != null) addProperty("address", address)
            if (workspaceId != null) addProperty("workspaceId", workspaceId)
        }
        val res = executeAuthRequest("/api/v1/hypr/dispatch", "POST", payload.toString(), JsonObject::class.java)
        if (res.isSuccess) Result.success(true) else Result.failure(res.exceptionOrNull() ?: Exception("Dispatch failed"))
    }

    // Browser
    suspend fun getBrowserTabs(): Result<List<BrowserTabItem>> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val res = executeAuthRequest("/api/v1/browser/tabs", "GET", null, JsonObject::class.java)
        if (res.isSuccess) {
            val arr = res.getOrNull()?.getAsJsonArray("tabs") ?: return@withContext Result.success(emptyList())
            val list = mutableListOf<BrowserTabItem>()
            arr.forEach { list.add(gson.fromJson(it, BrowserTabItem::class.java)) }
            Result.success(list)
        } else {
            Result.failure(res.exceptionOrNull() ?: Exception("Failed to get browser tabs"))
        }
    }

    suspend fun activateBrowserTab(tabId: Int, windowId: Int? = null): Result<Boolean> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val payload = JsonObject().apply {
            addProperty("tabId", tabId)
            if (windowId != null) addProperty("windowId", windowId)
        }
        val res = executeAuthRequest("/api/v1/browser/activate", "POST", payload.toString(), JsonObject::class.java)
        if (res.isSuccess) Result.success(true) else Result.failure(res.exceptionOrNull() ?: Exception("Activate failed"))
    }

    suspend fun closeBrowserTab(tabId: Int): Result<Boolean> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val payload = JsonObject().apply { addProperty("tabId", tabId) }
        val res = executeAuthRequest("/api/v1/browser/close", "POST", payload.toString(), JsonObject::class.java)
        if (res.isSuccess) Result.success(true) else Result.failure(res.exceptionOrNull() ?: Exception("Close tab failed"))
    }

    suspend fun openBrowserTab(url: String): Result<Boolean> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val payload = JsonObject().apply { addProperty("url", url) }
        val res = executeAuthRequest("/api/v1/browser/open", "POST", payload.toString(), JsonObject::class.java)
        if (res.isSuccess) Result.success(true) else Result.failure(res.exceptionOrNull() ?: Exception("Open tab failed"))
    }

    suspend fun inspectBrowserPage(): Result<JsonObject> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        executeAuthRequest("/api/v1/browser/inspect", "GET", null, JsonObject::class.java)
    }

    // Desktop Peek
    suspend fun getDesktopPeekJpeg(quality: Int = 70): Result<ByteArray> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        executeAuthRequestBytes("/api/v1/desktop/peek?q=$quality")
    }

    // Audio & Media
    suspend fun getAudioState(): Result<AudioState> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        executeAuthRequest("/api/v1/audio", "GET", null, AudioState::class.java)
    }

    suspend fun setAudioVolume(percent: Int): Result<AudioState> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val payload = JsonObject().apply { addProperty("percent", percent) }
        executeAuthRequest("/api/v1/audio/volume", "POST", payload.toString(), AudioState::class.java)
    }

    suspend fun toggleAudioMute(): Result<AudioState> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        executeAuthRequest("/api/v1/audio/mute", "POST", "{}", AudioState::class.java)
    }

    suspend fun getMediaState(): Result<MediaState> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        executeAuthRequest("/api/v1/media", "GET", null, MediaState::class.java)
    }

    suspend fun controlMedia(action: String): Result<Boolean> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val payload = JsonObject().apply { addProperty("action", action) }
        val res = executeAuthRequest("/api/v1/media/control", "POST", payload.toString(), JsonObject::class.java)
        if (res.isSuccess) Result.success(true) else Result.failure(res.exceptionOrNull() ?: Exception("Media control failed"))
    }

    // Downloads
    suspend fun getDownloads(limit: Int = 20): Result<List<DownloadFileItem>> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val res = executeAuthRequest("/api/v1/downloads?limit=$limit", "GET", null, JsonObject::class.java)
        if (res.isSuccess) {
            val arr = res.getOrNull()?.getAsJsonArray("files") ?: return@withContext Result.success(emptyList())
            val list = mutableListOf<DownloadFileItem>()
            arr.forEach { list.add(gson.fromJson(it, DownloadFileItem::class.java)) }
            Result.success(list)
        } else {
            Result.failure(res.exceptionOrNull() ?: Exception("Failed to get downloads"))
        }
    }

    // Search
    suspend fun searchLaptop(query: String, limit: Int = 25): Result<List<SearchItem>> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val q = java.net.URLEncoder.encode(query, "UTF-8")
        val res = executeAuthRequest("/api/v1/search?q=$q&limit=$limit", "GET", null, JsonObject::class.java)
        if (res.isSuccess) {
            val arr = res.getOrNull()?.getAsJsonArray("results") ?: return@withContext Result.success(emptyList())
            val list = mutableListOf<SearchItem>()
            arr.forEach { list.add(gson.fromJson(it, SearchItem::class.java)) }
            Result.success(list)
        } else {
            Result.failure(res.exceptionOrNull() ?: Exception("Failed to search laptop"))
        }
    }

    suspend fun reindexLaptop(): Result<Boolean> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val res = executeAuthRequest("/api/v1/search/reindex", "POST", "{}", JsonObject::class.java)
        if (res.isSuccess) Result.success(true) else Result.failure(res.exceptionOrNull() ?: Exception("Reindex failed"))
    }

    // Automations & Schedules
    suspend fun getAutomations(): Result<List<AutomationRuleItem>> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val res = executeAuthRequest("/api/v1/automations", "GET", null, JsonObject::class.java)
        if (res.isSuccess) {
            val arr = res.getOrNull()?.getAsJsonArray("rules") ?: return@withContext Result.success(emptyList())
            val list = mutableListOf<AutomationRuleItem>()
            arr.forEach { list.add(gson.fromJson(it, AutomationRuleItem::class.java)) }
            Result.success(list)
        } else {
            Result.failure(res.exceptionOrNull() ?: Exception("Failed to get automations"))
        }
    }

    suspend fun saveAutomation(rule: AutomationRuleItem): Result<AutomationRuleItem> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val payload = gson.toJson(rule)
        executeAuthRequest("/api/v1/automations/save", "POST", payload, AutomationRuleItem::class.java)
    }

    suspend fun deleteAutomation(id: String): Result<Boolean> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val payload = JsonObject().apply { addProperty("id", id) }
        val res = executeAuthRequest("/api/v1/automations/delete", "POST", payload.toString(), JsonObject::class.java)
        if (res.isSuccess) Result.success(true) else Result.failure(res.exceptionOrNull() ?: Exception("Delete automation failed"))
    }

    suspend fun getSchedules(): Result<List<ScheduleItem>> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val res = executeAuthRequest("/api/v1/schedules", "GET", null, JsonObject::class.java)
        if (res.isSuccess) {
            val arr = res.getOrNull()?.getAsJsonArray("schedules") ?: return@withContext Result.success(emptyList())
            val list = mutableListOf<ScheduleItem>()
            arr.forEach { list.add(gson.fromJson(it, ScheduleItem::class.java)) }
            Result.success(list)
        } else {
            Result.failure(res.exceptionOrNull() ?: Exception("Failed to get schedules"))
        }
    }

    suspend fun saveSchedule(schedule: ScheduleItem): Result<ScheduleItem> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val payload = gson.toJson(schedule)
        executeAuthRequest("/api/v1/schedules/save", "POST", payload, ScheduleItem::class.java)
    }

    suspend fun deleteSchedule(id: String): Result<Boolean> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val payload = JsonObject().apply { addProperty("id", id) }
        val res = executeAuthRequest("/api/v1/schedules/delete", "POST", payload.toString(), JsonObject::class.java)
        if (res.isSuccess) Result.success(true) else Result.failure(res.exceptionOrNull() ?: Exception("Delete schedule failed"))
    }

    // Wake-on-LAN
    suspend fun getWolStatus(): Result<WolStatusItem> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        executeAuthRequest("/api/v1/wol/status", "GET", null, WolStatusItem::class.java)
    }

    suspend fun wakeWorkstation(mac: String): Result<Boolean> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val payload = JsonObject().apply { addProperty("mac", mac) }
        val res = executeAuthRequest("/api/v1/wol/wake", "POST", payload.toString(), JsonObject::class.java)
        if (res.isSuccess) Result.success(true) else Result.failure(res.exceptionOrNull() ?: Exception("Wake workstation failed"))
    }
}
