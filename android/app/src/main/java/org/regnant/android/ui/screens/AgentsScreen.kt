package org.regnant.android.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.regnant.android.data.AgentItem
import org.regnant.android.data.AgentSessionItem
import org.regnant.android.data.ConnectionStatus
import org.regnant.android.network.RegnantClient
import org.regnant.android.ui.theme.*

@Composable
fun AgentsScreen(
    client: RegnantClient,
    connectionStatus: ConnectionStatus
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var agents by remember { mutableStateOf<List<AgentItem>>(emptyList()) }
    var selectedAgent by remember { mutableStateOf("agy") }
    var workspacePath by remember { mutableStateOf("/home/roonakyadav/Projects/regnant") }
    var prompt by remember { mutableStateOf("") }
    var activeSessionId by remember { mutableStateOf<String?>(null) }
    var streamLogs by remember { mutableStateOf("") }
    var followUpInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    fun refreshAgents() {
        if (connectionStatus != ConnectionStatus.CONNECTED) return
        scope.launch {
            isLoading = true
            val res = client.getAgents()
            if (res.isSuccess) {
                agents = res.getOrDefault(emptyList())
            }
            isLoading = false
        }
    }

    LaunchedEffect(connectionStatus) {
        refreshAgents()
    }

    LaunchedEffect(Unit) {
        client.agentEvents.collectLatest { event ->
            val eventType = event.get("type")?.asString
            if (eventType == "AGENT_OUTPUT") {
                val ev = event.getAsJsonObject("event")
                val text = ev?.get("raw")?.asString ?: ""
                streamLogs += text + "\n"
            } else if (eventType == "AGENT_STATUS") {
                val status = event.get("status")?.asString ?: ""
                streamLogs += "\n[Session Status: $status]\n"
                if (status == "COMPLETED" || status == "FAILED" || status == "CANCELLED") {
                    activeSessionId = null
                }
            } else if (eventType == "AGENT_STARTED") {
                val data = event.getAsJsonObject("data")
                activeSessionId = data?.get("sessionId")?.asString
                streamLogs += "[Agent Session Started: $activeSessionId]\n"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MainBackground)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "AI Agent Control Centre",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = { refreshAgents() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = AccentSage)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Agent Selector Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            agents.forEach { agent ->
                val isSelected = selectedAgent == agent.id
                Surface(
                    onClick = { selectedAgent = agent.id },
                    modifier = Modifier.weight(1f),
                    color = if (isSelected) SurfaceCard else DeepNavy,
                    shape = RoundedCornerShape(8.dp),
                    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, AccentSage) else androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = agent.id.uppercase(),
                            color = if (isSelected) AccentSage else TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (agent.available) "Ready" else "Missing",
                            color = if (agent.available) AccentSage else CoralRed,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Selected Agent Details & Guidance
        val currentAgentObj = agents.find { it.id == selectedAgent }
        if (currentAgentObj != null && !currentAgentObj.available) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, CoralRed),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(text = "Agent Not Installed", color = CoralRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = currentAgentObj.guidance ?: "", color = TextSecondary, fontSize = 11.sp)
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Workspace Path
        OutlinedTextField(
            value = workspacePath,
            onValueChange = { workspacePath = it },
            label = { Text("Workspace Directory", color = TextSecondary) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = AccentSage,
                unfocusedBorderColor = CardBorder
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Prompt Input
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            label = { Text("Agent Instruction Prompt", color = TextSecondary) },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = AccentSage,
                unfocusedBorderColor = CardBorder
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (prompt.isBlank()) {
                        Toast.makeText(context, "Enter a prompt first", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    scope.launch {
                        streamLogs = "Starting agent ${selectedAgent}...\n"
                        val res = client.startAgent(selectedAgent, workspacePath, prompt)
                        if (res.isSuccess) {
                            activeSessionId = res.getOrNull()?.get("sessionId")?.asString
                            Toast.makeText(context, "Agent launched", Toast.LENGTH_SHORT).show()
                        } else {
                            streamLogs += "Error: ${res.exceptionOrNull()?.message}\n"
                            Toast.makeText(context, res.exceptionOrNull()?.message ?: "Failed", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = AccentSage),
                enabled = activeSessionId == null && (currentAgentObj?.available ?: true)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = DeepNavy)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Run Agent", color = DeepNavy, fontWeight = FontWeight.Bold)
            }

            if (activeSessionId != null) {
                Button(
                    onClick = {
                        val sId = activeSessionId ?: return@Button
                        scope.launch {
                            client.cancelAgent(sId)
                            activeSessionId = null
                            streamLogs += "\n[Session Cancelled by User]\n"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralRed)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, tint = TextPrimary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Cancel", color = TextPrimary)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Streaming Output Terminal
        Text("Turn Stream Output", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(DeepNavy, RoundedCornerShape(6.dp))
                .border(1.dp, CardBorder, RoundedCornerShape(6.dp))
                .padding(10.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = if (streamLogs.isBlank()) "Agent output stream will appear here in real time..." else streamLogs,
                color = if (streamLogs.isBlank()) TextSecondary else TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
        }

        if (activeSessionId != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = followUpInput,
                    onValueChange = { followUpInput = it },
                    placeholder = { Text("Reply to agent...", color = TextSecondary, fontSize = 12.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
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
                        if (followUpInput.isNotBlank()) {
                            scope.launch {
                                client.sendAgentInput(sId, followUpInput)
                                streamLogs += "\n> $followUpInput\n"
                                followUpInput = ""
                            }
                        }
                    }
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send", tint = AccentSage)
                }
            }
        }
    }
}
