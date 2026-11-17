package org.regnant.android.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
import org.regnant.android.ui.components.WorkspaceSelector
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
    var showUnlockDialog by remember { mutableStateOf(false) }

    val workspaceState by client.workspaceState.collectAsState()
    val activeWorkspaceId = workspaceState?.workspaceId ?: 1

    val lockState by client.lockState.collectAsState()
    val isLocked = lockState?.locked == true || lockState?.state == "LOCKED"

    // Lightweight telemetry and lock status polling on home screen
    LaunchedEffect(connectionStatus) {
        if (connectionStatus == ConnectionStatus.CONNECTED) {
            client.getActiveWorkspace()
            client.getLockStatus()
            while (isActive) {
                val res = client.getMetrics()
                if (res.isSuccess) metrics = res.getOrNull()
                client.getLockStatus()
                delay(6000) // Poll every 6s on home screen
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
            // Workspace Selector prominently below StatusHeader
            item {
                WorkspaceSelector(
                    activeWorkspaceId = activeWorkspaceId,
                    onWorkspaceSelected = { slot ->
                        coroutineScope.launch {
                            val res = client.switchWorkspace(slot)
                            if (res.isFailure) {
                                Toast.makeText(
                                    context,
                                    "Failed to switch workspace: ${res.exceptionOrNull()?.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    enabled = isConnected,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Live Mini Hardware Telemetry & Lock Control Banner
            item {
                val m = metrics
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceCard)
                        .border(1.dp, SurfaceBorder, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (m != null) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(onClick = onNavigateToDashboard),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                        }
                    } else {
                        Text(
                            text = if (isConnected) "Telemetry connecting..." else "Offline",
                            fontSize = 11.sp,
                            color = TextMuted,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Dual Laptop Lock & Unlock Controls
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isLocked) {
                            Surface(
                                color = Coral.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, Coral.copy(alpha = 0.6f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = Coral, modifier = Modifier.size(11.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("LOCKED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Coral)
                                }
                            }

                            Button(
                                onClick = { showUnlockDialog = true },
                                enabled = isConnected,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = StatusGreen,
                                    contentColor = MainBackground
                                ),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LockOpen,
                                    contentDescription = null,
                                    tint = MainBackground,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Unlock Laptop",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            OutlinedButton(
                                onClick = { showLockDialog = true },
                                enabled = isConnected,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = SurfaceDark,
                                    contentColor = Coral,
                                    disabledContentColor = TextMuted
                                ),
                                border = BorderStroke(1.dp, if (isConnected) Coral.copy(alpha = 0.6f) else SurfaceBorder),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = if (isConnected) Coral else TextMuted,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Lock Laptop",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isConnected) Candle else TextMuted
                                )
                            }
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
        val laptopName = pairedHost?.hostName ?: "Linux Laptop"
        AlertDialog(
            onDismissRequest = { showLockDialog = false },
            containerColor = SurfaceCard,
            shape = RoundedCornerShape(12.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Coral,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Lock $laptopName?",
                        color = Candle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Your laptop screen will lock immediately.",
                        color = Candle,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "You can unlock your workstation using your Linux login password remotely from Regnant or directly on the computer.",
                        color = TextMuted,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLockDialog = false
                        coroutineScope.launch {
                            val res = client.lockWorkstation()
                            if (res.isSuccess) {
                                Toast.makeText(context, "Laptop locked", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    "Lock failed: ${res.exceptionOrNull()?.message ?: "Unknown error"}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Coral),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Lock", color = Candle, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLockDialog = false }
                ) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }

    if (showUnlockDialog) {
        val laptopName = pairedHost?.hostName ?: "Linux Laptop"
        UnlockPasswordDialog(
            laptopName = laptopName,
            onDismiss = { showUnlockDialog = false },
            onUnlock = { pwd ->
                val res = client.unlockWorkstation(pwd)
                if (res.isSuccess) {
                    val data = res.getOrNull()
                    if (data?.success == true) {
                        Toast.makeText(context, "Workstation unlocked", Toast.LENGTH_SHORT).show()
                        UnlockResultState.Success
                    } else {
                        UnlockResultState.InvalidPassword(data?.message ?: "Invalid password")
                    }
                } else {
                    val err = res.exceptionOrNull()?.message ?: ""
                    if (err.contains("invalid password", ignoreCase = true) || err.contains("401")) {
                        UnlockResultState.InvalidPassword("Authentication failed: invalid password")
                    } else {
                        UnlockResultState.HostUnavailable(err.ifEmpty { "Host connection error" })
                    }
                }
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

@Composable
fun UnlockPasswordDialog(
    laptopName: String,
    onDismiss: () -> Unit,
    onUnlock: suspend (String) -> UnlockResultState
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var unlockState by remember { mutableStateOf<UnlockResultState>(UnlockResultState.Idle) }
    val coroutineScope = rememberCoroutineScope()
    val isUnlocking = unlockState is UnlockResultState.Unlocking

    AlertDialog(
        onDismissRequest = {
            if (!isUnlocking) {
                password = ""
                onDismiss()
            }
        },
        containerColor = SurfaceCard,
        shape = RoundedCornerShape(12.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LockOpen,
                    contentDescription = null,
                    tint = Coral,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Unlock $laptopName",
                    color = Candle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Enter your Linux login password to authenticate and unlock your Omarchy workstation via PAM.",
                    color = TextMuted,
                    fontSize = 13.sp,
                    lineHeight = 17.sp
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        if (unlockState !is UnlockResultState.Idle && unlockState !is UnlockResultState.Unlocking) {
                            unlockState = UnlockResultState.Idle
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Linux Password", color = TextMuted, fontSize = 12.sp) },
                    placeholder = { Text("Enter login password", color = TextMuted.copy(alpha = 0.5f), fontSize = 13.sp) },
                    singleLine = true,
                    enabled = !isUnlocking,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (password.isNotEmpty() && !isUnlocking) {
                                coroutineScope.launch {
                                    val submitted = password
                                    password = ""
                                    unlockState = UnlockResultState.Unlocking
                                    val result = onUnlock(submitted)
                                    unlockState = result
                                    if (result is UnlockResultState.Success) {
                                        delay(800)
                                        onDismiss()
                                    }
                                }
                            }
                        }
                    ),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Candle,
                        unfocusedTextColor = Candle,
                        focusedBorderColor = Coral,
                        unfocusedBorderColor = SurfaceBorder,
                        cursorColor = Coral
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                when (val state = unlockState) {
                    is UnlockResultState.Unlocking -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SurfaceDark, RoundedCornerShape(6.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = Coral,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "UNLOCKING — Verifying PAM credentials...",
                                color = Candle,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    is UnlockResultState.Success -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(StatusGreen.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                .border(1.dp, StatusGreen.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "UNLOCKED — Workstation unlocked",
                                color = StatusGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    is UnlockResultState.InvalidPassword -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(StatusRed.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                .border(1.dp, StatusRed.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = StatusRed, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "INVALID PASSWORD — Authentication failed",
                                color = StatusRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    is UnlockResultState.HostUnavailable -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(StatusAmber.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                .border(1.dp, StatusAmber.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = StatusAmber, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "HOST UNAVAILABLE — ${state.message}",
                                color = StatusAmber,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    else -> {}
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (password.isNotEmpty() && !isUnlocking) {
                        coroutineScope.launch {
                            val submitted = password
                            password = ""
                            unlockState = UnlockResultState.Unlocking
                            val result = onUnlock(submitted)
                            unlockState = result
                            if (result is UnlockResultState.Success) {
                                delay(800)
                                onDismiss()
                            }
                        }
                    }
                },
                enabled = password.isNotEmpty() && !isUnlocking,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Coral,
                    disabledContainerColor = SurfaceDark
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (isUnlocking) "Unlocking..." else "Unlock",
                    color = if (password.isNotEmpty() && !isUnlocking) Candle else TextMuted,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    password = ""
                    onDismiss()
                },
                enabled = !isUnlocking
            ) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
}

sealed class UnlockResultState {
    object Idle : UnlockResultState()
    object Unlocking : UnlockResultState()
    object Success : UnlockResultState()
    data class InvalidPassword(val message: String = "Authentication failed: invalid password") : UnlockResultState()
    data class HostUnavailable(val message: String = "Host service unavailable") : UnlockResultState()
}

