package org.regnant.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.regnant.android.data.CommandRecord
import org.regnant.android.data.ConnectionStatus
import org.regnant.android.data.HostIdentity
import org.regnant.android.ui.components.QuickActionChips
import org.regnant.android.ui.components.StatusHeader
import org.regnant.android.ui.components.VoiceInputButton
import org.regnant.android.ui.theme.*

@Composable
fun HomeScreen(
    pairedHost: HostIdentity?,
    connectionStatus: ConnectionStatus,
    recentCommands: List<CommandRecord>,
    onSendCommand: (String) -> Unit,
    onNavigateToPairing: () -> Unit,
    modifier: Modifier = Modifier
) {
    var commandText by remember { mutableStateOf("") }
    val isConnected = connectionStatus == ConnectionStatus.CONNECTED

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
            item {
                QuickActionChips(
                    enabled = isConnected,
                    onActionClick = { onSendCommand(it) }
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
