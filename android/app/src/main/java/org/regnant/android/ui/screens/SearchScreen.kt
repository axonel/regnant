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
import org.regnant.android.data.SearchItem
import org.regnant.android.network.RegnantClient
import org.regnant.android.ui.theme.*

@Composable
fun SearchScreen(
    client: RegnantClient,
    connectionStatus: ConnectionStatus
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchItem>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    fun doSearch(q: String) {
        if (connectionStatus != ConnectionStatus.CONNECTED || q.isBlank()) {
            results = emptyList()
            return
        }
        scope.launch {
            isSearching = true
            val res = client.searchLaptop(q)
            if (res.isSuccess) {
                results = res.getOrDefault(emptyList())
            }
            isSearching = false
        }
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
                text = "Universal Laptop Search",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = {
                    scope.launch {
                        val res = client.reindexLaptop()
                        if (res.isSuccess) {
                            Toast.makeText(context, "Search index updated", Toast.LENGTH_SHORT).show()
                            if (searchQuery.isNotEmpty()) doSearch(searchQuery)
                        }
                    }
                }
            ) {
                Icon(Icons.Default.Sync, contentDescription = "Reindex", tint = AccentSage)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                doSearch(it)
            },
            placeholder = { Text("Search projects, apps, and files...", color = TextSecondary) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AccentSage) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = {
                        searchQuery = ""
                        results = emptyList()
                    }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextSecondary)
                    }
                }
            },
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

        if (isSearching) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = AccentSage)
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (results.isEmpty() && searchQuery.isNotEmpty() && !isSearching) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No matching items found", color = TextSecondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(results) { item ->
                    Surface(
                        onClick = {
                            scope.launch {
                                if (item.type == "project") {
                                    val res = client.launchProject(item.id)
                                    if (res.isSuccess) {
                                        Toast.makeText(context, "Launched ${item.title}", Toast.LENGTH_SHORT).show()
                                    }
                                } else if (item.type == "app") {
                                    client.sendCommand(action = "launch_app", target = item.path)
                                    Toast.makeText(context, "Launching ${item.title}", Toast.LENGTH_SHORT).show()
                                } else {
                                    client.openUrl("file://${item.path}")
                                    Toast.makeText(context, "Opening ${item.title}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        color = SurfaceCard,
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val icon = when (item.type) {
                                "project" -> Icons.Default.Code
                                "app" -> Icons.Default.Apps
                                "folder" -> Icons.Default.Folder
                                else -> Icons.Default.Description
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = AccentSage,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(item.path, color = TextSecondary, fontSize = 11.sp, maxLines = 1)
                            }
                            Text(
                                text = item.type.uppercase(),
                                color = TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
