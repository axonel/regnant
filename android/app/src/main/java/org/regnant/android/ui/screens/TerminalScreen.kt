package org.regnant.android.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.regnant.android.data.ConnectionStatus
import org.regnant.android.data.TerminalSessionItem
import org.regnant.android.network.RegnantClient
import org.regnant.android.ui.theme.*

@Composable
fun TerminalScreen(
    client: RegnantClient,
    connectionStatus: ConnectionStatus
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sessions by remember { mutableStateOf<List<TerminalSessionItem>>(emptyList()) }
    var activeSessionId by remember { mutableStateOf<String?>(null) }
    var terminalOutput by remember { mutableStateOf("") }
    var commandLineInput by remember { mutableStateOf("") }
    val verticalScrollState = rememberScrollState()

    fun refreshSessions() {
        if (connectionStatus != ConnectionStatus.CONNECTED) return
        scope.launch {
            val res = client.listTerminals()
            if (res.isSuccess) {
                sessions = res.getOrDefault(emptyList())
                if (activeSessionId == null && sessions.isNotEmpty()) {
                    activeSessionId = sessions[0].id
                    // load buffer
                    val bufRes = client.getTerminalBuffer(sessions[0].id)
                    terminalOutput = bufRes.getOrDefault("")
                }
            }
        }
    }

    LaunchedEffect(connectionStatus) {
        refreshSessions()
    }

    // Listen for WebSocket terminal streaming output
    LaunchedEffect(Unit) {
        client.terminalDataEvents.collectLatest { (sId, chunk) ->
            if (activeSessionId == null || activeSessionId == sId) {
                terminalOutput += chunk
            }
        }
    }

    // Auto-scroll to bottom on output change
    LaunchedEffect(terminalOutput) {
        verticalScrollState.animateScrollTo(verticalScrollState.maxValue)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MainBackground)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Remote Terminal (PTY)",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Row {
                IconButton(onClick = {
                    scope.launch {
                        val res = client.createTerminal(cwd = null, title = "Bash ${sessions.size + 1}")
                        if (res.isSuccess) {
                            val newSess = res.getOrThrow()
                            activeSessionId = newSess.id
                            terminalOutput = ""
                            refreshSessions()
                            Toast.makeText(context, "New shell session created", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) {
                    Icon(Icons.Default.Add, contentDescription = "New Terminal", tint = AccentSage)
                }
                IconButton(onClick = { refreshSessions() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = TextSecondary)
                }
            }
        }

        // Sessions Tab Row
        if (sessions.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sessions) { s ->
                    val isSelected = s.id == activeSessionId
                    Surface(
                        onClick = {
                            activeSessionId = s.id
                            scope.launch {
                                val buf = client.getTerminalBuffer(s.id).getOrDefault("")
                                terminalOutput = buf
                            }
                        },
                        color = if (isSelected) SurfaceCard else DeepNavy,
                        shape = RoundedCornerShape(6.dp),
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, AccentSage) else androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "${s.title} (PID ${s.pid})",
                                color = if (isSelected) AccentSage else TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = TextSecondary,
                                modifier = Modifier
                                    .size(14.dp)
                                    .padding(1.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Terminal Output Screen
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(DeepNavy, RoundedCornerShape(6.dp))
                .border(1.dp, CardBorder, RoundedCornerShape(6.dp))
                .padding(8.dp)
                .verticalScroll(verticalScrollState)
        ) {
            Text(
                text = if (terminalOutput.isBlank()) "Terminal output will appear here. Tap below to type commands." else terminalOutput,
                color = if (terminalOutput.isBlank()) TextSecondary else TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Virtual Auxiliary Keys Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            fun sendKey(key: String) {
                val sId = activeSessionId ?: return
                client.sendTerminalInput(sId, key)
            }

            KeyChip("TAB") { sendKey("\t") }
            KeyChip("ESC") { sendKey("\u001b") }
            KeyChip("CTRL+C") { sendKey("\u0003") }
            KeyChip("CTRL+D") { sendKey("\u0004") }
            KeyChip("▲") { sendKey("\u001b[A") }
            KeyChip("▼") { sendKey("\u001b[B") }
            KeyChip("◄") { sendKey("\u001b[D") }
            KeyChip("►") { sendKey("\u001b[C") }
            KeyChip("CLEAR") { sendKey("clear\n") }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Interactive Input Line
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = commandLineInput,
                onValueChange = { commandLineInput = it },
                placeholder = { Text("Enter command / text...", color = TextSecondary, fontSize = 12.sp) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    val sId = activeSessionId ?: return@KeyboardActions
                    if (commandLineInput.isNotEmpty()) {
                        client.sendTerminalInput(sId, commandLineInput + "\n")
                        commandLineInput = ""
                    }
                }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = AccentSage,
                    unfocusedBorderColor = CardBorder
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    val sId = activeSessionId ?: return@IconButton
                    if (commandLineInput.isNotEmpty()) {
                        client.sendTerminalInput(sId, commandLineInput + "\n")
                        commandLineInput = ""
                    }
                }
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = AccentSage)
            }
        }
    }
}

@Composable
fun KeyChip(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = SurfaceCard,
        shape = RoundedCornerShape(4.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
    ) {
        Text(
            text = label,
            color = TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}
