package org.regnant.android.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.regnant.android.data.ConnectionStatus
import org.regnant.android.data.NetworkInterfaceItem
import org.regnant.android.network.RegnantClient
import org.regnant.android.ui.theme.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

@Composable
fun WolScreen(
    client: RegnantClient,
    connectionStatus: ConnectionStatus
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var interfaces by remember { mutableStateOf<List<NetworkInterfaceItem>>(emptyList()) }
    var targetMac by remember { mutableStateOf("28:a0:6b:45:90:9d") }
    var statusMessage by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }

    fun refreshWol() {
        if (connectionStatus != ConnectionStatus.CONNECTED) return
        scope.launch {
            val res = client.getWolStatus()
            if (res.isSuccess) {
                val data = res.getOrThrow()
                interfaces = data.interfaces
                if (!data.primaryMac.isNullOrEmpty()) {
                    targetMac = data.primaryMac
                }
            }
        }
    }

    LaunchedEffect(connectionStatus) {
        refreshWol()
    }

    suspend fun sendUdpMagicPacket(mac: String) = withContext(Dispatchers.IO) {
        val cleanMac = mac.replace(":", "").replace("-", "")
        if (cleanMac.length != 12) {
            throw IllegalArgumentException("Invalid MAC length")
        }

        val macBytes = ByteArray(6)
        for (i in 0 until 6) {
            macBytes[i] = cleanMac.substring(i * 2, (i * 2) + 2).toInt(16).toByte()
        }

        val packetData = ByteArray(6 + 16 * 6)
        for (i in 0 until 6) packetData[i] = 0xFF.toByte()
        for (i in 0 until 16) {
            System.arraycopy(macBytes, 0, packetData, 6 + i * 6, 6)
        }

        val socket = DatagramSocket()
        socket.broadcast = true
        val broadcastAddr = InetAddress.getByName("255.255.255.255")
        val packet = DatagramPacket(packetData, packetData.size, broadcastAddr, 9)
        socket.send(packet)
        socket.close()
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
                text = "Wake-on-LAN (WoWLAN)",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = { refreshWol() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = AccentSage)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Target MAC Card
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Target Workstation MAC Address", color = TextSecondary, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = targetMac,
                    onValueChange = { targetMac = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AccentSage,
                        unfocusedBorderColor = CardBorder
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        scope.launch {
                            isSending = true
                            try {
                                sendUdpMagicPacket(targetMac)
                                statusMessage = "Magic packet broadcasted to 255.255.255.255:9"
                                Toast.makeText(context, "Magic packet broadcasted!", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                statusMessage = "Error: ${e.message}"
                                Toast.makeText(context, "Send error: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                            isSending = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentSage),
                    enabled = !isSending && targetMac.isNotBlank()
                ) {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = DeepNavy)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Broadcast Magic Packet", color = DeepNavy, fontWeight = FontWeight.Bold)
                }

                if (statusMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(statusMessage, color = AccentSage, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text("Detected Network Adapters", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        if (interfaces.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (connectionStatus == ConnectionStatus.CONNECTED) "No active physical interfaces detected" else "Connect to host to view interfaces",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(interfaces) { iface ->
                    Surface(
                        onClick = { targetMac = iface.mac },
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
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (iface.type == "wifi") Icons.Default.Wifi else Icons.Default.SettingsEthernet,
                                        contentDescription = null,
                                        tint = AccentSage,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(iface.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(iface.mac, color = AccentSage, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(iface.type.uppercase(), color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                if (iface.wowlan) {
                                    Text("WoWLAN Ready", color = AccentSage, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
