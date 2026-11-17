package org.regnant.android.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.regnant.android.AppTab
import org.regnant.android.data.CommandRecord
import org.regnant.android.data.ConnectionStatus
import org.regnant.android.data.HostIdentity
import org.regnant.android.data.HostMetrics
import org.regnant.android.network.RegnantClient
import org.regnant.android.ui.components.QuickActionChips
import org.regnant.android.ui.components.StatusHeader
import org.regnant.android.ui.components.VoiceInputButton
import org.regnant.android.ui.theme.*

@Composable
fun HomeScreen(
    client: RegnantClient,
    pairedHost: HostIdentity?,
    connectionStatus: ConnectionStatus,
    recentCommands: List<CommandRecord>,
    onSendCommand: (String) -> Unit,
    onNavigateToPairing: () -> Unit,
    onNavigateToProjects: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    onNavigateToTab: (AppTab) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var commandText by remember { mutableStateOf("") }
    val isConnected = connectionStatus == ConnectionStatus.CONNECTED

    var metrics by remember { mutableStateOf<HostMetrics?>(null) }
    var showLockDialog by remember { mutableStateOf(false) }

    // Lightweight telemetry polling on home screen
    LaunchedEffect(connectionStatus) {
        if (connectionStatus == ConnectionStatus.CONNECTED) {
            while (isActive) {
                val res = client.getMetrics()
                if (res.isSuccess) metrics = res.getOrNull()
                delay(8000) // Poll every 8s on home screen
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MainBackground)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            StatusHeader(
                hostName = pairedHost?.hostName,
                connectionStatus = connectionStatus
            )
        }

        if (pairedHost == null) {
            item {
                UnpairedCard(onNavigateToPairing = onNavigateToPairing)
            }
        } else {
            // Live Mini Hardware Telemetry Banner
            if (isConnected && metrics != null) {
                item {
                    val m = metrics!!
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceCard)
                            .border(1.dp, SurfaceBorder, RoundedCornerShape(8.dp))
                            .clickable(onClick = onNavigateToDashboard)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (m.battery.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                                contentDescription = null,
                                tint = if (m.battery.percent > 20) StatusGreen else StatusRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("${m.battery.percent}%", fontSize = 11.sp, color = Candle, fontFamily = FontFamily.Monospace)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Memory, contentDescription = null, tint = Coral, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("CPU ${m.cpuPercent}%", fontSize = 11.sp, color = Candle, fontFamily = FontFamily.Monospace)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DeveloperBoard, contentDescription = null, tint = Leaf, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("RAM ${m.memory.percent}%", fontSize = 11.sp, color = Candle, fontFamily = FontFamily.Monospace)
                        }

                        IconButton(
                            onClick = { showLockDialog = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = "Lock", tint = Coral, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            item {
                QuickActionChips(
                    enabled = isConnected,
                    onActionClick = { onSendCommand(it) },
                    onNavigateToProjects = onNavigateToProjects
                )
            }

            item {
                ControlMatrixSection(
                    enabled = isConnected,
                    onNavigateToTab = onNavigateToTab
                )
            }

            item {
                CommandInputSection(
                    commandText = commandText,
                    onTextChange = { commandText = it },
                    enabled = isConnected,
                    onSend = {
                        if (commandText.isNotBlank()) {
                            onSendCommand(commandText)
                            commandText = ""
                        }
                    }
                )
            }

            item {
                Text(
                    text = "RECENT ACTIVITY",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Leaf,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (recentCommands.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceCard)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No recent commands executed",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                items(recentCommands.take(10)) { cmd ->
                    ActivityItem(cmd)
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showLockDialog) {
        AlertDialog(
            onDismissRequest = { showLockDialog = false },
            containerColor = SurfaceCard,
            title = { Text("Lock Workstation?", color = Candle, fontWeight = FontWeight.Bold) },
            text = { Text("Lock your Linux desktop session immediately.", color = TextMuted) },
            confirmButton = {
                Button(
                    onClick = {
                        showLockDialog = false
                        coroutineScope.launch {
                            val res = client.lockWorkstation()
                            if (res.isSuccess) Toast.makeText(context, "Workstation locked!", Toast.LENGTH_SHORT).show()
                            else Toast.makeText(context, "Lock failed", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Coral)
                ) { Text("Lock", color = Candle, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showLockDialog = false }) { Text("Cancel", color = TextMuted) }
            }
        )
    }
}

@Composable
private fun UnpairedCard(
    onNavigateToPairing: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceCard)
            .border(1.dp, Juniper, RoundedCornerShape(12.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Link,
            contentDescription = null,
            tint = Leaf,
            modifier = Modifier.size(36.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Connect Your Linux Computer",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Candle
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Open Regnant on your Omarchy Linux laptop to view your single-use 6-character pairing code.",
            fontSize = 13.sp,
            color = TextMuted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 18.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onNavigateToPairing,
            colors = ButtonDefaults.buttonColors(containerColor = Coral),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Enter Pairing Code",
                color = Candle,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun CommandInputSection(
    commandText: String,
    onTextChange: (String) -> Unit,
    enabled: Boolean,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "COMMAND INPUT",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Leaf,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = commandText,
                onValueChange = onTextChange,
                enabled = enabled,
                placeholder = {
                    Text(
                        text = if (enabled) "e.g. open terminal" else "Waiting for connection...",
                        color = TextMuted,
                        fontSize = 14.sp
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp)),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceCard,
                    unfocusedContainerColor = SurfaceCard,
                    disabledContainerColor = SurfaceDark,
                    focusedBorderColor = Leaf,
                    unfocusedBorderColor = SurfaceBorder,
                    focusedTextColor = Candle,
                    unfocusedTextColor = Candle
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(onSend = { onSend() })
            )

            VoiceInputButton(
                enabled = enabled,
                onResult = { recognized ->
                    onTextChange(recognized)
                }
            )

            IconButton(
                onClick = onSend,
                enabled = enabled && commandText.isNotBlank(),
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (enabled && commandText.isNotBlank()) Coral else Juniper)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send Command",
                    tint = Candle,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun ActivityItem(
    cmd: CommandRecord,
    modifier: Modifier = Modifier
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceCard)
            .border(1.dp, SurfaceBorder, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = cmd.rawCommand,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Candle
            )
            if (cmd.message.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = cmd.message,
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(horizontalAlignment = Alignment.End) {
            val (icon, tint) = when (cmd.status) {
                "COMPLETED" -> Icons.Default.CheckCircle to StatusGreen
                "FAILED", "REJECTED" -> Icons.Default.Error to StatusRed
                else -> Icons.Default.HourglassEmpty to Leaf
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = cmd.status,
                    tint = tint,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = cmd.status,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = tint,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = timeFormat.format(Date(cmd.timestamp)),
                fontSize = 10.sp,
                color = TextMuted,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun ControlMatrixSection(
    enabled: Boolean,
    onNavigateToTab: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "WORKSTATION CONTROL CENTRE",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Leaf,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        val items = listOf(
            Triple(AppTab.AGENTS, "AI Agents", "Claude, Codex & Agy"),
            Triple(AppTab.TERMINAL, "Terminal", "Interactive PTY shell"),
            Triple(AppTab.GITHUB, "Git & GitHub", "Branches, PRs & CI"),
            Triple(AppTab.PEEK, "Desktop Peek", "Live Wayland stream"),
            Triple(AppTab.MEDIA, "Sound & Media", "PipeWire & MPRIS"),
            Triple(AppTab.SEARCH, "Universal Search", "FTS5 files & apps"),
            Triple(AppTab.DOWNLOADS, "Downloads", "Recent & active"),
            Triple(AppTab.AUTOMATIONS, "Automations", "Triggers & cron"),
            Triple(AppTab.WOL, "Wake-on-LAN", "Magic packet sender")
        )

        for (chunk in items.chunked(3)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for ((tab, title, desc) in chunk) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceCard)
                            .border(1.dp, SurfaceBorder, RoundedCornerShape(8.dp))
                            .clickable(enabled = enabled) { onNavigateToTab(tab) }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = title,
                                tint = if (enabled) Coral else TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = title,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (enabled) Candle else TextMuted,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = desc,
                                fontSize = 8.sp,
                                color = TextMuted,
                                maxLines = 1,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

