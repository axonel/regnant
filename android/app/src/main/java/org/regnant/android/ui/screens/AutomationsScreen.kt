package org.regnant.android.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.regnant.android.data.AutomationRuleItem
import org.regnant.android.data.ConnectionStatus
import org.regnant.android.data.ScheduleItem
import org.regnant.android.network.RegnantClient
import org.regnant.android.ui.theme.*

@Composable
fun AutomationsScreen(
    client: RegnantClient,
    connectionStatus: ConnectionStatus
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedSection by remember { mutableStateOf(0) } // 0 = Rules, 1 = Schedules
    var rules by remember { mutableStateOf<List<AutomationRuleItem>>(emptyList()) }
    var schedules by remember { mutableStateOf<List<ScheduleItem>>(emptyList()) }
    var showAddRuleModal by remember { mutableStateOf(false) }

    fun refreshAll() {
        if (connectionStatus != ConnectionStatus.CONNECTED) return
        scope.launch {
            val rRes = client.getAutomations()
            if (rRes.isSuccess) rules = rRes.getOrDefault(emptyList())

            val sRes = client.getSchedules()
            if (sRes.isSuccess) schedules = sRes.getOrDefault(emptyList())
        }
    }

    LaunchedEffect(connectionStatus) {
        refreshAll()
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
                text = "Automations & Schedules",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Row {
                IconButton(onClick = { showAddRuleModal = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Rule", tint = AccentSage)
                }
                IconButton(onClick = { refreshAll() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = TextSecondary)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Toggle Tabs
        TabRow(
            selectedTabIndex = selectedSection,
            containerColor = SurfaceCard,
            contentColor = AccentSage
        ) {
            Tab(
                selected = selectedSection == 0,
                onClick = { selectedSection = 0 },
                text = { Text("Event Rules (${rules.size})", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedSection == 1,
                onClick = { selectedSection = 1 },
                text = { Text("Scheduled Tasks (${schedules.size})", fontWeight = FontWeight.Bold) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (selectedSection == 0) {
            if (rules.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No event automation rules configured. Tap + to add one.", color = TextSecondary)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(rules) { r ->
                        Surface(
                            color = SurfaceCard,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(r.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("When: ${r.trigger_type} -> Then: ${r.action_type}", color = AccentSage, fontSize = 11.sp)
                                }
                                IconButton(onClick = {
                                    scope.launch {
                                        client.deleteAutomation(r.id)
                                        refreshAll()
                                        Toast.makeText(context, "Rule deleted", Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = CoralRed)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            if (schedules.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No scheduled tasks configured. Tap + to add one.", color = TextSecondary)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(schedules) { s ->
                        Surface(
                            color = SurfaceCard,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(s.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Interval: ${s.schedule_expr}s -> Action: ${s.action_type}", color = AccentSage, fontSize = 11.sp)
                                }
                                IconButton(onClick = {
                                    scope.launch {
                                        client.deleteSchedule(s.id)
                                        refreshAll()
                                        Toast.makeText(context, "Schedule deleted", Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = CoralRed)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showAddRuleModal) {
            var ruleName by remember { mutableStateOf("Mute On Connect") }
            var trigger by remember { mutableStateOf("DEVICE_CONNECTED") }
            var action by remember { mutableStateOf("MUTE_AUDIO") }

            AlertDialog(
                onDismissRequest = { showAddRuleModal = false },
                containerColor = SurfaceCard,
                title = { Text("New Event Rule", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = ruleName,
                            onValueChange = { ruleName = it },
                            label = { Text("Rule Name", color = TextSecondary) },
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
                        Text("Trigger Event: $trigger", color = AccentSage, fontSize = 12.sp)
                        Text("Action: $action", color = AccentSage, fontSize = 12.sp)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                client.saveAutomation(
                                    AutomationRuleItem(
                                        id = "rule-${System.currentTimeMillis()}",
                                        name = ruleName,
                                        trigger_type = trigger,
                                        action_type = action,
                                        enabled = 1
                                    )
                                )
                                showAddRuleModal = false
                                refreshAll()
                                Toast.makeText(context, "Rule saved", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentSage)
                    ) {
                        Text("Save Rule", color = DeepNavy)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddRuleModal = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
            )
        }
    }
}
