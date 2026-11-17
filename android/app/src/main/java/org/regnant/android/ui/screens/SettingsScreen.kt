package org.regnant.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.regnant.android.ui.theme.*

@Composable
fun SettingsScreen(
    autoReconnect: Boolean,
    onAutoReconnectChanged: (Boolean) -> Unit,
    persistentService: Boolean,
    onPersistentServiceChanged: (Boolean) -> Unit,
    notificationsEnabled: Boolean,
    onNotificationsEnabledChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MainBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "CONNECTION PREFERENCES",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Leaf,
                letterSpacing = 1.sp
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ToggleRow(
                    title = "Automatic Reconnection",
                    description = "Automatically reconnect when discovered on local Wi-Fi",
                    checked = autoReconnect,
                    onCheckedChange = onAutoReconnectChanged
                )

                HorizontalDivider(color = SurfaceBorder, thickness = 1.dp)

                ToggleRow(
                    title = "Persistent Foreground Service",
                    description = "Maintains active connection notification in Android system tray",
                    checked = persistentService,
                    onCheckedChange = onPersistentServiceChanged
                )

                HorizontalDivider(color = SurfaceBorder, thickness = 1.dp)

                ToggleRow(
                    title = "Command Notifications",
                    description = "Display notifications when commands finish or fail",
                    checked = notificationsEnabled,
                    onCheckedChange = onNotificationsEnabledChanged
                )
            }
        }

        item {
            Text(
                text = "ABOUT REGNANT",
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Regnant Mobile Control", fontWeight = FontWeight.Bold, color = Candle, fontSize = 16.sp)
                Text("Version 1.0.0 (Production Architecture)", fontSize = 12.sp, color = Leaf)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Personal mobile control plane for Omarchy Linux workstation.\n" +
                            "• Zero runtime cloud AI services\n" +
                            "• Zero public relay servers\n" +
                            "• Pinned TLS 1.3 local cryptography\n" +
                            "• Strict non-shell deterministic execution",
                    fontSize = 12.sp,
                    color = TextMuted,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, fontWeight = FontWeight.SemiBold, color = Candle, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = description, color = TextMuted, fontSize = 12.sp, lineHeight = 16.sp)
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Candle,
                checkedTrackColor = Coral,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = Juniper
            )
        )
    }
}
