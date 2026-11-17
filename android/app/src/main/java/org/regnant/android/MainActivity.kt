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
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import org.regnant.android.data.CommandRecord
import org.regnant.android.data.ConnectionStatus
import org.regnant.android.data.HostIdentity
import org.regnant.android.service.RegnantForegroundService
import org.regnant.android.ui.screens.ActivityScreen
import org.regnant.android.ui.screens.DevicesScreen
import org.regnant.android.ui.screens.HomeScreen
import org.regnant.android.ui.screens.SettingsScreen
import org.regnant.android.ui.theme.*

enum class AppTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    DEVICES("Devices", Icons.Default.Computer),
    ACTIVITY("Activity", Icons.Default.History),
    SETTINGS("Settings", Icons.Default.Settings)
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

                val connectionStatus by client.connectionStatus.collectAsState()
                val discoveredHosts by nsd.discoveredHosts.collectAsState()

                var pairedHost by remember { mutableStateOf(securityStore.getPairedHost()) }
                var history by remember { mutableStateOf(securityStore.getHistory()) }

                var autoReconnect by remember { mutableStateOf(securityStore.isAutoReconnectEnabled()) }
                var persistentService by remember { mutableStateOf(securityStore.isPersistentServiceEnabled()) }
                var notificationsEnabled by remember { mutableStateOf(securityStore.isNotificationsEnabled()) }

                // Collect incoming command completion events
                LaunchedEffect(Unit) {
                    client.commandEvents.collect {
                        history = securityStore.getHistory()
                    }
                }

                Scaffold(
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
                                    label = { Text(tab.label, fontSize = 11.sp) },
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
                                onNavigateToPairing = { currentTab = AppTab.DEVICES }
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
                                            Toast.makeText(this@MainActivity, "Paired successfully with ${newHost.hostName}!", Toast.LENGTH_SHORT).show()
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

                            AppTab.ACTIVITY -> ActivityScreen(
                                history = history,
                                onClearHistory = {
                                    securityStore.saveHistory(emptyList())
                                    history = emptyList()
                                }
                            )

                            AppTab.SETTINGS -> SettingsScreen(
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
