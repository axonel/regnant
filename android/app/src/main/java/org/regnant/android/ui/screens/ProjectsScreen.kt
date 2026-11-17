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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.regnant.android.data.ConnectionStatus
import org.regnant.android.data.IdeItem
import org.regnant.android.data.ProjectItem
import org.regnant.android.network.RegnantClient
import org.regnant.android.ui.theme.*

@Composable
fun ProjectsScreen(
    client: RegnantClient,
    connectionStatus: ConnectionStatus,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var projects by remember { mutableStateOf<List<ProjectItem>>(emptyList()) }
    var supportedIdes by remember { mutableStateOf<List<IdeItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var showRegisterDialog by remember { mutableStateOf(false) }

    fun refreshProjects() {
        if (connectionStatus != ConnectionStatus.CONNECTED) return
        coroutineScope.launch {
            isLoading = true
            val res = client.getProjects()
            if (res.isSuccess) {
                val data = res.getOrThrow()
                projects = data.projects
                supportedIdes = data.supportedIdes
            } else {
                Toast.makeText(context, "Failed to load projects: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
            }
            isLoading = false
        }
    }

    LaunchedEffect(connectionStatus) {
        if (connectionStatus == ConnectionStatus.CONNECTED) {
            refreshProjects()
        }
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
                        text = "PROJECT WORKSPACES",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Leaf,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Remote Workstation IDE Launcher",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { refreshProjects() },
                        enabled = connectionStatus == ConnectionStatus.CONNECTED
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Candle)
                    }

                    Button(
                        onClick = { showRegisterDialog = true },
                        enabled = connectionStatus == ConnectionStatus.CONNECTED,
                        colors = ButtonDefaults.buttonColors(containerColor = Coral),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = Candle)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Candle)
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
                        text = "Connect to your Linux workstation to manage and launch projects",
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                }
            }
        } else if (projects.isEmpty() && !isLoading) {
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
                        text = "No projects registered. Tap 'Add' to register ~/Projects.",
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            items(projects) { project ->
                ProjectCard(
                    project = project,
                    supportedIdes = supportedIdes,
                    onLaunch = { ide ->
                        coroutineScope.launch {
                            val res = client.launchProject(project.id, ide)
                            if (res.isSuccess) {
                                Toast.makeText(context, res.getOrThrow(), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Launch failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
            }
        }
    }

    if (showRegisterDialog) {
        RegisterProjectDialog(
            onDismiss = { showRegisterDialog = false },
            onConfirm = { name, path, ide ->
                coroutineScope.launch {
                    val res = client.registerProject(name, path, ide)
                    if (res.isSuccess) {
                        Toast.makeText(context, "Registered ${res.getOrThrow().name}!", Toast.LENGTH_SHORT).show()
                        refreshProjects()
                    } else {
                        Toast.makeText(context, "Registration error: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                    showRegisterDialog = false
                }
            }
        )
    }
}

@Composable
private fun ProjectCard(
    project: ProjectItem,
    supportedIdes: List<IdeItem>,
    onLaunch: (String) -> Unit
) {
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
                Icon(
                    imageVector = Icons.Default.FolderSpecial,
                    contentDescription = null,
                    tint = Coral,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = project.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Candle
                )
            }

            if (!project.exists) {
                Text(
                    text = "NOT FOUND",
                    fontSize = 10.sp,
                    color = StatusRed,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Text(
            text = project.path,
            fontSize = 12.sp,
            color = TextMuted,
            fontFamily = FontFamily.Monospace
        )

        HorizontalDivider(color = SurfaceBorder, thickness = 1.dp)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onLaunch("auto") },
                colors = ButtonDefaults.buttonColors(containerColor = Juniper),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp), tint = Candle)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Launch", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Candle)
            }

            // Quick Code launcher
            OutlinedButton(
                onClick = { onLaunch("code") },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Candle),
                border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(SurfaceBorder)),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                Text("VS Code", fontSize = 12.sp, color = Candle)
            }

            // Quick Terminal launcher
            OutlinedButton(
                onClick = { onLaunch("terminal") },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Candle),
                border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(SurfaceBorder)),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                Text("Terminal", fontSize = 12.sp, color = Candle)
            }
        }
    }
}

@Composable
private fun RegisterProjectDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, path: String, ide: String) -> Unit
) {
    var nameInput by remember { mutableStateOf("") }
    var pathInput by remember { mutableStateOf("~/Projects/") }
    var ideInput by remember { mutableStateOf("auto") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        title = {
            Text("Register New Workspace", fontWeight = FontWeight.Bold, color = Candle, fontSize = 16.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Project Name (Optional)", color = TextMuted) },
                    placeholder = { Text("e.g. My App", color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Candle, unfocusedTextColor = Candle,
                        focusedBorderColor = Leaf, unfocusedBorderColor = SurfaceBorder
                    )
                )

                OutlinedTextField(
                    value = pathInput,
                    onValueChange = { pathInput = it },
                    label = { Text("Workstation Directory Path", color = TextMuted) },
                    placeholder = { Text("e.g. ~/Projects/my-app", color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Candle, unfocusedTextColor = Candle,
                        focusedBorderColor = Leaf, unfocusedBorderColor = SurfaceBorder
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pathInput.isNotBlank()) {
                        onConfirm(nameInput.trim(), pathInput.trim(), ideInput)
                    }
                },
                enabled = pathInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Coral)
            ) {
                Text("Register", color = Candle, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
}
