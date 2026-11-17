package org.regnant.android.ui.screens

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.regnant.android.data.ConnectionStatus
import org.regnant.android.data.HostIdentity
import org.regnant.android.ui.theme.*

@Composable
fun DevicesScreen(
    pairedHost: HostIdentity?,
    connectionStatus: ConnectionStatus,
    discoveredHosts: List<HostIdentity>,
    onPairWithHost: (hostIp: String, port: Int, code: String) -> Unit,
    onForgetHost: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pairingCodeInput by remember { mutableStateOf("") }
    var manualIpInput by remember { mutableStateOf(pairedHost?.ip ?: "100.128.163.153") }
    var manualPortInput by remember { mutableStateOf((pairedHost?.port ?: 8765).toString()) }
    var isPairingLoading by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MainBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "PAIRED WORKSTATION",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Leaf,
                letterSpacing = 1.sp
            )
        }

        if (pairedHost != null) {
            item {
                PairedHostCard(
                    host = pairedHost,
                    connectionStatus = connectionStatus,
                    onForget = onForgetHost
                )
            }
        } else {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceCard)
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No workstation paired yet. Enter pairing code below.",
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                }
            }
        }

        item {
            Text(
                text = "PAIR NEW COMPUTER",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Leaf,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceCard)
                    .border(1.dp, SurfaceBorder, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Single-Use 6-Character Code",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Candle
                )

                OutlinedTextField(
                    value = pairingCodeInput,
                    onValueChange = { if (it.length <= 8) pairingCodeInput = it.uppercase() },
                    placeholder = { Text("e.g. RG-7K9P", color = TextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceDark,
                        unfocusedContainerColor = SurfaceDark,
                        focusedBorderColor = Leaf,
                        unfocusedBorderColor = SurfaceBorder,
                        focusedTextColor = Candle,
                        unfocusedTextColor = Candle
                    ),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = manualIpInput,
                        onValueChange = { manualIpInput = it },
                        label = { Text("Host IP / Fallback", color = TextMuted, fontSize = 11.sp) },
                        modifier = Modifier.weight(2f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceDark,
                            unfocusedContainerColor = SurfaceDark,
                            focusedBorderColor = Leaf,
                            unfocusedBorderColor = SurfaceBorder,
                            focusedTextColor = Candle,
                            unfocusedTextColor = Candle
                        ),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = manualPortInput,
                        onValueChange = { manualPortInput = it },
                        label = { Text("Port", color = TextMuted, fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceDark,
                            unfocusedContainerColor = SurfaceDark,
                            focusedBorderColor = Leaf,
                            unfocusedBorderColor = SurfaceBorder,
                            focusedTextColor = Candle,
                            unfocusedTextColor = Candle
                        ),
                        singleLine = true
                    )
                }

                Button(
                    onClick = {
                        val port = manualPortInput.toIntOrNull() ?: 8765
                        if (pairingCodeInput.isNotBlank() && manualIpInput.isNotBlank()) {
                            isPairingLoading = true
                            onPairWithHost(manualIpInput.trim(), port, pairingCodeInput.trim())
                            pairingCodeInput = ""
                            isPairingLoading = false
                        }
                    },
                    enabled = pairingCodeInput.isNotBlank() && manualIpInput.isNotBlank() && !isPairingLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = Coral),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isPairingLoading) "Pairing..." else "Pair Computer",
                        color = Candle,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (discoveredHosts.isNotEmpty()) {
            item {
                Text(
                    text = "DISCOVERED ON LOCAL NETWORK",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Leaf,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(discoveredHosts.size) { idx ->
                val host = discoveredHosts[idx]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceCard)
                        .border(1.dp, SurfaceBorder, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(host.hostName, fontWeight = FontWeight.SemiBold, color = Candle)
                        Text("${host.ip}:${host.port}", fontSize = 12.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                    }

                    Button(
                        onClick = {
                            manualIpInput = host.ip
                            manualPortInput = host.port.toString()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Juniper),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Select", color = Candle, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun PairedHostCard(
    host: HostIdentity,
    connectionStatus: ConnectionStatus,
    onForget: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Column(
        modifier = modifier
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
            Text(
                text = host.hostName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Candle
            )

            Text(
                text = connectionStatus.name,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (connectionStatus == ConnectionStatus.CONNECTED) StatusGreen else Leaf,
                fontFamily = FontFamily.Monospace
            )
        }

        HorizontalDivider(color = SurfaceBorder, thickness = 1.dp)

        InfoRow(label = "Host IP", value = "${host.ip}:${host.port}")
        InfoRow(label = "Host ID", value = host.hostId.take(16) + "...")
        if (host.certFingerprint.isNotBlank()) {
            InfoRow(label = "TLS Fingerprint", value = host.certFingerprint.take(24) + "...")
        }
        InfoRow(label = "Last Seen", value = dateFormat.format(Date(host.lastSeen)))

        Spacer(modifier = Modifier.height(4.dp))

        OutlinedButton(
            onClick = onForget,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusRed),
            border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(StatusRed)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Forget / Revoke Host", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 12.sp, color = TextMuted)
        Text(text = value, fontSize = 12.sp, color = Candle, fontFamily = FontFamily.Monospace)
    }
}
