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

// --- Expanded Feature Models ---

data class MemoryMetrics(
    val totalMb: Int = 0,
    val usedMb: Int = 0,
    val availableMb: Int = 0,
    val percent: Float = 0f
)

data class BatteryMetrics(
    val present: Boolean = false,
    val percent: Int = 100,
    val status: String = "AC",
    val isCharging: Boolean = false
)

data class DiskMetrics(
    val path: String = "",
    val totalGb: Float = 0f,
    val usedGb: Float = 0f,
    val freeGb: Float = 0f,
    val percent: Float = 0f
)

data class HostMetrics(
    val timestamp: Long = 0,
    val cpuPercent: Float = 0f,
    val memory: MemoryMetrics = MemoryMetrics(),
    val battery: BatteryMetrics = BatteryMetrics(),
    val disk: DiskMetrics = DiskMetrics(),
    val uptimeSeconds: Long = 0,
    val hostname: String = ""
)

data class IdeItem(
    val id: String,
    val name: String,
    val exec: String
)

data class ProjectItem(
    val id: String,
    val name: String,
    val path: String,
    val ide: String = "auto",
    val exists: Boolean = true
)

data class ProjectsResponse(
    val projects: List<ProjectItem> = emptyList(),
    val supportedIdes: List<IdeItem> = emptyList()
)

data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0,
    val modified: Double = 0.0,
    val isRootCategory: Boolean = false
)

data class FileListResponse(
    val currentPath: String = "",
    val isRoot: Boolean = false,
    val parentPath: String? = null,
    val items: List<FileItem> = emptyList()
)

data class FileReadResponse(
    val path: String,
    val fileName: String,
    val size: Long,
    val isText: Boolean,
    val content: String? = null
)

data class RunningApp(
    val address: String,
    val title: String,
    val appClass: String,
    val pid: Int,
    val workspaceName: String? = null
)

data class RoutineStep(
    val action: String,
    val target: String,
    val label: String? = null,
    val optional: Boolean = false
)

data class RoutineItem(
    val id: String,
    val name: String,
    val description: String? = null,
    val steps: List<RoutineStep> = emptyList()
)

data class RoutineResult(
    val success: Boolean,
    val routineId: String,
    val routineName: String,
    val stepsCompleted: Int,
    val totalSteps: Int,
    val stepResults: List<Map<String, Any>> = emptyList()
)
