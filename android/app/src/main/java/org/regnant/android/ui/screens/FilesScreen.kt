package org.regnant.android.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.regnant.android.data.ConnectionStatus
import org.regnant.android.data.FileItem
import org.regnant.android.data.FileReadResponse
import org.regnant.android.network.RegnantClient
import org.regnant.android.ui.theme.*

@Composable
fun FilesScreen(
    client: RegnantClient,
    connectionStatus: ConnectionStatus,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var currentPath by remember { mutableStateOf<String?>(null) }
    var parentPath by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var isRoot by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }

    var previewFile by remember { mutableStateOf<FileReadResponse?>(null) }

    fun loadPath(path: String?) {
        if (connectionStatus != ConnectionStatus.CONNECTED) return
        coroutineScope.launch {
            isLoading = true
            val res = client.listFiles(path)
            if (res.isSuccess) {
                val data = res.getOrThrow()
                currentPath = if (data.isRoot) null else data.currentPath
                parentPath = data.parentPath
                items = data.items
                isRoot = data.isRoot
            } else {
                Toast.makeText(context, "File browse error: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
            }
            isLoading = false
        }
    }

    LaunchedEffect(connectionStatus) {
        if (connectionStatus == ConnectionStatus.CONNECTED) {
            loadPath(null)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MainBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "WORKSTATION FILES",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Leaf,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = if (isRoot) "Approved Directory Roots" else (currentPath ?: "Browsing"),
                        fontSize = 11.sp,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!isRoot) {
                        IconButton(onClick = { loadPath(parentPath) }) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "Up", tint = Candle)
                        }
                    }
                    IconButton(
                        onClick = { loadPath(currentPath) },
                        enabled = connectionStatus == ConnectionStatus.CONNECTED
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Candle)
                    }
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
                        text = "Connect to workstation to browse approved project and download directories",
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                }
            }
        } else if (items.isEmpty() && !isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceCard)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "Directory is empty", color = TextMuted, fontSize = 13.sp)
                }
            }
        } else {
            items(items) { item ->
                FileRowItem(
                    item = item,
                    onClick = {
                        if (item.isDirectory) {
                            loadPath(item.path)
                        } else {
                            coroutineScope.launch {
                                val res = client.readFile(item.path)
                                if (res.isSuccess) {
                                    previewFile = res.getOrThrow()
                                } else {
                                    Toast.makeText(context, "Read error: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                )
            }
        }
    }

    // Preview File Modal
    if (previewFile != null) {
        val f = previewFile!!
        AlertDialog(
            onDismissRequest = { previewFile = null },
            containerColor = SurfaceCard,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Description, contentDescription = null, tint = Leaf, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(f.fileName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Candle)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Size: ${f.size} bytes • Path: ${f.path}", fontSize = 11.sp, color = TextMuted, fontFamily = FontFamily.Monospace)

                    if (f.isText && f.content != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(SurfaceDark)
                                .padding(10.dp)
                        ) {
                            LazyColumn {
                                item {
                                    Text(
                                        text = f.content,
                                        color = Candle,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    } else {
                        Text("Binary file preview is not displayed.", color = TextMuted, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { previewFile = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Juniper)
                ) {
                    Text("Close", color = Candle)
                }
            }
        )
    }
}

@Composable
private fun FileRowItem(
    item: FileItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceCard)
            .border(1.dp, SurfaceBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                contentDescription = null,
                tint = if (item.isDirectory) Coral else Leaf,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = item.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Candle
                )
                if (!item.isDirectory && item.size > 0) {
                    val sizeStr = if (item.size > 1024 * 1024) "${item.size / (1024 * 1024)} MB" else "${item.size / 1024} KB"
                    Text(text = sizeStr, fontSize = 11.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                }
            }
        }

        if (item.isDirectory) {
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
        }
    }
}
