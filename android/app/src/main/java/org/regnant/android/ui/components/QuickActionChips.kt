package org.regnant.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.regnant.android.ui.theme.*

@Composable
fun QuickActionChips(
    enabled: Boolean,
    onActionClick: (String) -> Unit,
    onNavigateToProjects: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "QUICK ACTIONS",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Leaf,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionCard(
                title = "Terminal",
                icon = Icons.Default.Terminal,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = { onActionClick("open terminal") }
            )
            ActionCard(
                title = "Brave",
                icon = Icons.Default.Language,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = { onActionClick("open brave") }
            )
            ActionCard(
                title = "Files",
                icon = Icons.Default.Folder,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = { onActionClick("open file manager") }
            )
            ActionCard(
                title = "Projects",
                icon = Icons.Default.Code,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onClick = onNavigateToProjects
            )
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) Juniper else Juniper.copy(alpha = 0.5f))
            .border(1.dp, SurfaceBorder, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (enabled) Candle else TextMuted,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) Candle else TextMuted
            )
        }
    }
}
