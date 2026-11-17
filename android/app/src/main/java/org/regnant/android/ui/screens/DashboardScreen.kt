package org.regnant.android.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.regnant.android.data.ConnectionStatus
import org.regnant.android.data.HostMetrics
import org.regnant.android.network.RegnantClient
import org.regnant.android.ui.theme.*

@Composable
fun DashboardScreen(
    client: RegnantClient,
    connectionStatus: ConnectionStatus,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    var metrics by remember { mutableStateOf<HostMetrics?>(null) }
    var isPolling by remember { mutableStateOf(false) }

    // Dialog confirmations
    var showLockConfirm by remember { mutableStateOf(false) }
    var showSuspendConfirm by remember { mutableStateOf(false) }
    var clipboardTextDialog by remember { mutableStateOf<String?>(null) }

    // Efficient polling only while screen is active and connected
    LaunchedEffect(connectionStatus) {
        if (connectionStatus == ConnectionStatus.CONNECTED) {
            isPolling = true
            while (isActive && isPolling) {
                val res = client.getMetrics()
                if (res.isSuccess) {
                    metrics = res.getOrNull()
                }
                delay(3000) // Poll every 3 seconds while viewing dashboard
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { isPolling = false }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MainBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "WORKSTATION STATUS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Leaf,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = if (metrics != null) "${metrics?.hostname} • Live Hardware Telemetry" else "Waiting for telemetry...",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }

                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            val res = client.getMetrics()
                            if (res.isSuccess) metrics = res.getOrNull()
                        }
                    },
                    enabled = connectionStatus == ConnectionStatus.CONNECTED
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Candle)
                }
            }
        }

        if (connectionStatus != ConnectionStatus.CONNECTED) {
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
                        text = "Connect to workstation to view live CPU, RAM, battery, and storage metrics",
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                }
            }
        } else if (metrics == null) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceCard)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Coral, modifier = Modifier.size(28.dp))
                }
            }
        } else {
            val m = metrics!!

            // Battery & Power Card
            item {
                MetricCard(
                    title = "BATTERY & POWER",
                    value = "${m.battery.percent}%",
                    subtitle = "${m.battery.status} ${if (m.battery.isCharging) "⚡ Charging" else ""}",
                    icon = if (m.battery.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                    progress = m.battery.percent / 100f,
                    progressColor = if (m.battery.percent > 20) StatusGreen else StatusRed
                )
            }

            // CPU Usage Card
            item {
                MetricCard(
                    title = "CPU UTILIZATION",
                    value = "${m.cpuPercent}%",
                    subtitle = "Linux kernel scheduler (/proc/stat)",
                    icon = Icons.Default.Memory,
                    progress = m.cpuPercent / 100f,
                    progressColor = if (m.cpuPercent > 80f) StatusRed else Coral
                )
            }

            // Memory Usage Card
            item {
                MetricCard(
                    title = "RAM CONSUMPTION",
                    value = "${m.memory.usedMb} MB / ${m.memory.totalMb} MB",
                    subtitle = "${m.memory.percent}% used • ${m.memory.availableMb} MB available",
                    icon = Icons.Default.DeveloperBoard,
                    progress = m.memory.percent / 100f,
                    progressColor = if (m.memory.percent > 85f) StatusRed else Juniper
                )
            }

            // Disk Usage Card
            item {
                MetricCard(
                    title = "HOME STORAGE",
                    value = "${m.disk.freeGb} GB Free / ${m.disk.totalGb} GB",
                    subtitle = "${m.disk.percent}% allocated on ${m.disk.path}",
                    icon = Icons.Default.Storage,
                    progress = m.disk.percent / 100f,
                    progressColor = if (m.disk.percent > 90f) StatusRed else Leaf
                )
            }

            // Uptime Banner
            item {
                val hours = m.uptimeSeconds / 3600
                val mins = (m.uptimeSeconds % 3600) / 60
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceCard)
                        .border(1.dp, SurfaceBorder, RoundedCornerShape(8.dp))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccessTime, contentDescription = null, tint = Leaf, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("System Uptime", color = Candle, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text("${hours}h ${mins}m", color = Candle, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            // Quick Hardware & Control Action Bar
            item {
                Text(
                    text = "HARDWARE & SECURITY CONTROLS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Leaf,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Lock Session Button
                    Button(
                        onClick = { showLockConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Coral),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp), tint = Candle)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Lock Screen", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Candle)
                    }

                    // Suspend Button
                    OutlinedButton(
                        onClick = { showSuspendConfirm = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Candle),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(SurfaceBorder)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Bedtime, contentDescription = null, modifier = Modifier.size(16.dp), tint = Candle)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sleep", fontSize = 12.sp, color = Candle)
                    }
                }
            }

            // Manual Clipboard Sync Card
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceCard)
                        .border(1.dp, SurfaceBorder, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, tint = Leaf, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Workstation Clipboard", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Candle)
                        }
                    }

                    Text(
                        text = "Explicit user-initiated clipboard sharing. Zero background polling.",
                        fontSize = 11.sp,
                        color = TextMuted
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    val res = client.readClipboard()
                                    if (res.isSuccess) {
                                        val text = res.getOrThrow()
                                        if (text.isNotBlank()) {
                                            clipboardManager.setText(AnnotatedString(text))
                                            clipboardTextDialog = text
                                            Toast.makeText(context, "Copied laptop clipboard to phone!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Workstation clipboard is empty", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "Error: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Juniper),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Fetch from Laptop", fontSize = 11.sp, color = Candle)
                        }

                        OutlinedButton(
                            onClick = {
                                val phoneClip = clipboardManager.getText()?.text ?: ""
                                if (phoneClip.isNotBlank()) {
                                    coroutineScope.launch {
                                        val res = client.writeClipboard(phoneClip)
                                        if (res.isSuccess) {
                                            Toast.makeText(context, "Sent phone clipboard to laptop!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Error: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } else {
                                    Toast.makeText(context, "Phone clipboard is empty", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Candle),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(SurfaceBorder)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Send to Laptop", fontSize = 11.sp, color = Candle)
                        }
                    }
                }
            }
        }
    }

    // Lock Dialog
    if (showLockConfirm) {
        AlertDialog(
            onDismissRequest = { showLockConfirm = false },
            containerColor = SurfaceCard,
            title = { Text("Lock Workstation Session?", color = Candle, fontWeight = FontWeight.Bold) },
            text = { Text("This will trigger loginctl to lock your Omarchy desktop session immediately.", color = TextMuted) },
            confirmButton = {
                Button(
                    onClick = {
                        showLockConfirm = false
                        coroutineScope.launch {
                            val res = client.lockWorkstation()
                            if (res.isSuccess) Toast.makeText(context, "Workstation locked!", Toast.LENGTH_SHORT).show()
                            else Toast.makeText(context, "Lock failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Coral)
                ) { Text("Lock Now", color = Candle, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showLockConfirm = false }) { Text("Cancel", color = TextMuted) }
            }
        )
    }

    // Suspend Dialog
    if (showSuspendConfirm) {
        AlertDialog(
            onDismissRequest = { showSuspendConfirm = false },
            containerColor = SurfaceCard,
            title = { Text("Put Workstation to Sleep?", color = Candle, fontWeight = FontWeight.Bold) },
            text = { Text("This will trigger systemctl suspend. The connection will disconnect until the laptop wakes.", color = TextMuted) },
            confirmButton = {
                Button(
                    onClick = {
                        showSuspendConfirm = false
                        coroutineScope.launch {
                            val res = client.suspendWorkstation()
                            if (res.isSuccess) Toast.makeText(context, "Workstation suspended", Toast.LENGTH_SHORT).show()
                            else Toast.makeText(context, "Suspend failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Coral)
                ) { Text("Suspend", color = Candle, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showSuspendConfirm = false }) { Text("Cancel", color = TextMuted) }
            }
        )
    }

    // Clipboard View Dialog
    if (clipboardTextDialog != null) {
        AlertDialog(
            onDismissRequest = { clipboardTextDialog = null },
            containerColor = SurfaceCard,
            title = { Text("Workstation Clipboard Content", color = Candle, fontWeight = FontWeight.Bold) },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(SurfaceDark)
                        .padding(12.dp)
                ) {
                    Text(
                        text = clipboardTextDialog ?: "",
                        color = Candle,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        maxLines = 10
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { clipboardTextDialog = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Juniper)
                ) { Text("Done", color = Candle) }
            }
        )
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    progress: Float,
    progressColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceCard)
            .border(1.dp, SurfaceBorder, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = Leaf, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Leaf, letterSpacing = 0.5.sp)
            }
            Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Candle, fontFamily = FontFamily.Monospace)
        }

        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = progressColor,
            trackColor = SurfaceDark
        )

        Text(text = subtitle, fontSize = 11.sp, color = TextMuted)
    }
}
