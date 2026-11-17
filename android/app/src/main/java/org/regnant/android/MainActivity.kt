package org.regnant.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import org.regnant.android.data.CommandRecord
import org.regnant.android.data.ConnectionStatus
import org.regnant.android.data.HostIdentity
import org.regnant.android.service.RegnantForegroundService
import org.regnant.android.ui.screens.*
import org.regnant.android.ui.theme.*

enum class AppTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    PROJECTS("Projects", Icons.Default.Code),
    STATUS("Status", Icons.Default.Dashboard),
    FILES("Files", Icons.Default.Folder),
    DEVICES("Devices", Icons.Default.Computer)
}

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Audio and Notification permissions results
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestRequiredPermissions()

        val app = application as RegnantApp
        val securityStore = app.securityStore
        val client = app.client
        val nsd = app.nsdDiscovery

        setContent {
            RegnantTheme {
                val coroutineScope = rememberCoroutineScope()
                var currentTab by remember { mutableStateOf(AppTab.HOME) }
                var showSettingsModal by remember { mutableStateOf(false) }
                var showActivityModal by remember { mutableStateOf(false) }

                val connectionStatus by client.connectionStatus.collectAsState()
                val discoveredHosts by nsd.discoveredHosts.collectAsState()

                var pairedHost by remember { mutableStateOf(securityStore.getPairedHost()) }
                var history by remember { mutableStateOf(securityStore.getHistory()) }

                var autoReconnect by remember { mutableStateOf(securityStore.isAutoReconnectEnabled()) }
                var persistentService by remember { mutableStateOf(securityStore.isPersistentServiceEnabled()) }
                var notificationsEnabled by remember { mutableStateOf(securityStore.isNotificationsEnabled()) }

                // Automatically trigger NSD discovery when viewing app
                LaunchedEffect(Unit) {
                    nsd.startDiscovery { host ->
                        val currentPaired = securityStore.getPairedHost()
                        if (currentPaired != null && host.hostId == currentPaired.hostId &&
                            (host.ip != currentPaired.ip || host.port != currentPaired.port)
                        ) {
                            securityStore.updateHostIpAndPort(host.ip, host.port)
                            pairedHost = securityStore.getPairedHost()
                            client.connect()
                        }
                    }

                    client.commandEvents.collect {
                        history = securityStore.getHistory()
                    }
                }

                Scaffold(
                    topBar = {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SurfaceDark)
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "REGNANT",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Candle,
                                    letterSpacing = 2.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .background(Coral, shape = androidx.compose.foundation.shape.CircleShape)
                                        .size(6.dp)
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(onClick = { showActivityModal = true }) {
                                    Icon(Icons.Default.History, contentDescription = "Activity Log", tint = TextMuted)
                                }
                                IconButton(onClick = { showSettingsModal = true }) {
                                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextMuted)
                                }
                            }
                        }
                    },
                    bottomBar = {
                        NavigationBar(
                            containerColor = SurfaceDark,
                            contentColor = Candle
                        ) {
                            AppTab.entries.forEach { tab ->
                                NavigationBarItem(
                                    selected = currentTab == tab,
                                    onClick = { currentTab = tab },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = { Text(tab.label, fontSize = 10.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Candle,
                                        selectedTextColor = Candle,
                                        indicatorColor = Coral,
                                        unselectedIconColor = TextMuted,
                                        unselectedTextColor = TextMuted
                                    )
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        when (currentTab) {
                            AppTab.HOME -> HomeScreen(
                                client = client,
                                pairedHost = pairedHost,
                                connectionStatus = connectionStatus,
                                recentCommands = history,
                                onSendCommand = { cmd ->
                                    coroutineScope.launch {
                                        val res = client.sendCommand(rawCommand = cmd)
                                        if (res.isSuccess) {
                                            history = securityStore.getHistory()
                                        } else {
                                            Toast.makeText(this@MainActivity, res.exceptionOrNull()?.message ?: "Command failed", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onNavigateToPairing = { currentTab = AppTab.DEVICES },
                                onNavigateToProjects = { currentTab = AppTab.PROJECTS },
                                onNavigateToDashboard = { currentTab = AppTab.STATUS }
                            )

                            AppTab.PROJECTS -> ProjectsScreen(
                                client = client,
                                connectionStatus = connectionStatus
                            )

                            AppTab.STATUS -> DashboardScreen(
                                client = client,
                                connectionStatus = connectionStatus
                            )

                            AppTab.FILES -> FilesScreen(
                                client = client,
                                connectionStatus = connectionStatus
                            )

                            AppTab.DEVICES -> DevicesScreen(
                                pairedHost = pairedHost,
                                connectionStatus = connectionStatus,
                                discoveredHosts = discoveredHosts,
                                onPairWithHost = { hostIp, port, code ->
                                    coroutineScope.launch {
                                        val res = client.pair(hostIp, port, code)
                                        if (res.isSuccess) {
                                            val pairData = res.getOrThrow()
                                            val newHost = HostIdentity(
                                                hostId = pairData.hostId ?: "host-uuid",
                                                hostName = pairData.hostName ?: "Omarchy Laptop",
                                                ip = hostIp,
                                                port = port,
                                                certFingerprint = pairData.certFingerprint ?: ""
                                            )
                                            securityStore.savePairedHost(newHost, pairData.token ?: "")
                                            pairedHost = newHost
                                            Toast.makeText(this@MainActivity, "Paired successfully over Wi-Fi with ${newHost.hostName}!", Toast.LENGTH_SHORT).show()
                                            client.connect()
                                            currentTab = AppTab.HOME
                                        } else {
                                            Toast.makeText(this@MainActivity, "Pairing error: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                onForgetHost = {
                                    client.disconnect()
                                    securityStore.clearPairedHost()
                                    pairedHost = null
                                    Toast.makeText(this@MainActivity, "Host forgotten", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }

                    // Activity Modal Dialog
                    if (showActivityModal) {
                        AlertDialog(
                            onDismissRequest = { showActivityModal = false },
                            containerColor = MainBackground,
                            title = {
                                Text("Recent Activity & Audit Log", color = Candle, fontWeight = FontWeight.Bold)
                            },
                            text = {
                                Box(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                                    ActivityScreen(
                                        history = history,
                                        onClearHistory = {
                                            securityStore.saveHistory(emptyList())
                                            history = emptyList()
                                        }
                                    )
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showActivityModal = false }) {
                                    Text("Close", color = Candle)
                                }
                            }
                        )
                    }

                    // Settings Modal Dialog
                    if (showSettingsModal) {
                        AlertDialog(
                            onDismissRequest = { showSettingsModal = false },
                            containerColor = MainBackground,
                            title = {
                                Text("Regnant Preferences", color = Candle, fontWeight = FontWeight.Bold)
                            },
                            text = {
                                Box(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                                    SettingsScreen(
                                        autoReconnect = autoReconnect,
                                        onAutoReconnectChanged = {
                                            autoReconnect = it
                                            securityStore.setAutoReconnectEnabled(it)
                                        },
                                        persistentService = persistentService,
                                        onPersistentServiceChanged = {
                                            persistentService = it
                                            securityStore.setPersistentServiceEnabled(it)
                                            if (it) {
                                                RegnantForegroundService.start(this@MainActivity)
                                            } else {
                                                RegnantForegroundService.stop(this@MainActivity)
                                            }
                                        },
                                        notificationsEnabled = notificationsEnabled,
                                        onNotificationsEnabledChanged = {
                                            notificationsEnabled = it
                                            securityStore.setNotificationsEnabled(it)
                                        }
                                    )
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showSettingsModal = false }) {
                                    Text("Done", color = Candle)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }
}
