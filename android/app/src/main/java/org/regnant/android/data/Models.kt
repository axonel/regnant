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

// --- v1.2 Developer Workstation Control Centre Models ---

data class AgentItem(
    val id: String = "",
    val name: String = "",
    val available: Boolean = false,
    val version: String? = null,
    val path: String? = null,
    val guidance: String? = null
)

data class AgentSessionItem(
    val id: String = "",
    val agent_type: String = "",
    val workspace_path: String = "",
    val prompt: String = "",
    val status: String = "",
    val pid: Int? = null,
    val created_at: Long = 0L,
    val completed_at: Long? = null
)

data class TerminalSessionItem(
    val id: String = "",
    val pid: Int = 0,
    val cwd: String = "",
    val title: String = "Terminal",
    val cols: Int = 80,
    val rows: Int = 24,
    val createdAt: Long = 0L
)

data class GitStatusItem(
    val branch: String = "HEAD",
    val tracking: String? = null,
    val ahead: Int = 0,
    val behind: Int = 0,
    val isClean: Boolean = true,
    val staged: List<Map<String, String>> = emptyList(),
    val unstaged: List<Map<String, String>> = emptyList(),
    val untracked: List<String> = emptyList()
)

data class GitBranchItem(
    val name: String = "",
    val upstream: String? = null,
    val current: Boolean = false
)

data class GitCommitItem(
    val hash: String = "",
    val shortHash: String = "",
    val author: String = "",
    val email: String = "",
    val timestamp: Long = 0L,
    val subject: String = ""
)

data class GitHubPRItem(
    val number: Int = 0,
    val title: String = "",
    val state: String = "OPEN",
    val isDraft: Boolean = false,
    val headRefName: String = "",
    val baseRefName: String = "",
    val updatedAt: String? = null,
    val url: String? = null
)

data class GitHubRunItem(
    val databaseId: Long = 0L,
    val name: String = "",
    val headBranch: String = "",
    val headSha: String = "",
    val status: String = "completed",
    val conclusion: String? = null,
    val event: String? = null,
    val createdAt: String? = null,
    val url: String? = null
)

data class HyprWindowItem(
    val address: String = "",
    val title: String = "",
    val className: String = "",
    val workspaceId: Int = 1,
    val workspaceName: String = "1",
    val pid: Int = 0,
    val visible: Boolean = true
)

data class HyprWorkspaceItem(
    val id: Int = 1,
    val name: String = "1",
    val monitor: String = "",
    val windowsCount: Int = 0
)

data class BrowserTabItem(
    val id: Int = 0,
    val windowId: Int = 0,
    val title: String = "",
    val url: String = "",
    val active: Boolean = false,
    val favIconUrl: String? = null
)

data class AudioState(
    val volume: Int = 50,
    val muted: Boolean = false,
    val sink: String = "Default Audio Sink"
)

data class MediaState(
    val available: Boolean = false,
    val player: String? = null,
    val playbackStatus: String = "Stopped",
    val title: String = "",
    val artist: String = ""
)

data class DownloadFileItem(
    val name: String = "",
    val path: String = "",
    val size: Long = 0L,
    val mtime: Double = 0.0,
    val isPartial: Boolean = false
)

data class SearchItem(
    val id: String = "",
    val type: String = "file",
    val title: String = "",
    val path: String = "",
    val keywords: String? = null
)

data class AutomationRuleItem(
    val id: String = "",
    val name: String = "",
    val trigger_type: String = "",
    val action_type: String = "",
    val enabled: Int = 1
)

data class ScheduleItem(
    val id: String = "",
    val name: String = "",
    val schedule_type: String = "INTERVAL",
    val schedule_expr: String = "3600",
    val action_type: String = "",
    val enabled: Int = 1,
    val next_run_at: Long = 0L
)

data class NetworkInterfaceItem(
    val name: String = "",
    val mac: String = "",
    val type: String = "wifi",
    val wowlan: Boolean = false
)

data class WolStatusItem(
    val interfaces: List<NetworkInterfaceItem> = emptyList(),
    val primaryMac: String? = null
)

// --- v1.2.1 Workspace Switcher & Laptop Lock Models ---

data class WorkspaceState(
    val workspaceId: Int = 1,
    val workspaceName: String = "1",
    val monitorName: String = "default",
    val activeSlot: Int = 1,
    val timestamp: Long = 0L
)

data class LockState(
    val locked: Boolean? = false,
    val state: String = "UNLOCKED",
    val timestamp: Long = 0L
)

