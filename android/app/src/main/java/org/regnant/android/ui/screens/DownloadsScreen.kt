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
import org.regnant.android.data.ConnectionStatus
import org.regnant.android.data.DownloadFileItem
import org.regnant.android.network.RegnantClient
import org.regnant.android.ui.theme.*

@Composable
fun DownloadsScreen(
    client: RegnantClient,
    connectionStatus: ConnectionStatus
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf<List<DownloadFileItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    fun refreshDownloads() {
        if (connectionStatus != ConnectionStatus.CONNECTED) return
        scope.launch {
            isLoading = true
            val res = client.getDownloads(25)
            if (res.isSuccess) {
                files = res.getOrDefault(emptyList())
            }
            isLoading = false
        }
    }

    LaunchedEffect(connectionStatus) {
        refreshDownloads()
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
            Column {
                Text(
                    text = "Download Manager",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${files.size} recent workstation files",
                    color = AccentSage,
                    fontSize = 12.sp
                )
            }
            IconButton(onClick = { refreshDownloads() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = AccentSage)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = AccentSage)
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (files.isEmpty() && !isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No downloads found in ~/Downloads", color = TextSecondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(files) { file ->
                    Surface(
                        color = SurfaceCard,
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (file.isPartial) CoralRed else CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (file.isPartial) Icons.Default.DownloadForOffline else Icons.Default.InsertDriveFile,
                                contentDescription = null,
                                tint = if (file.isPartial) CoralRed else AccentSage,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(file.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${file.size / 1024} KB ${if (file.isPartial) "• In progress" else "• Ready"}",
                                    color = if (file.isPartial) CoralRed else TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    client.sendCommand(action = "open_path", target = "downloads")
                                    Toast.makeText(context, "Revealed in file manager", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Icon(Icons.Default.FolderOpen, contentDescription = "Reveal", tint = TextSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}
