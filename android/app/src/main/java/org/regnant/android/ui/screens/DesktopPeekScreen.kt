package org.regnant.android.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.regnant.android.data.ConnectionStatus
import org.regnant.android.network.RegnantClient
import org.regnant.android.ui.theme.*

@Composable
fun DesktopPeekScreen(
    client: RegnantClient,
    connectionStatus: ConnectionStatus
) {
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var frameSizeKb by remember { mutableStateOf(0) }
    var lastUpdated by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var quality by remember { mutableStateOf(70) }

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 4f)
        offset += offsetChange
    }

    fun captureFrame() {
        if (connectionStatus != ConnectionStatus.CONNECTED) return
        scope.launch {
            isLoading = true
            val res = client.getDesktopPeekJpeg(quality)
            if (res.isSuccess) {
                val bytes = res.getOrThrow()
                frameSizeKb = bytes.size / 1024
                bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                lastUpdated = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            }
            isLoading = false
        }
    }

    LaunchedEffect(connectionStatus) {
        captureFrame()
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
                    text = "Live Desktop Peek",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (lastUpdated.isNotEmpty()) "Captured at $lastUpdated ($frameSizeKb KB)" else "Wayland Screen Capture",
                    color = AccentSage,
                    fontSize = 12.sp
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        quality = if (quality == 50) 80 else 50
                        captureFrame()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(text = if (quality == 50) "50% (Fast)" else "80% (HD)", color = TextPrimary, fontSize = 11.sp)
                }
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(onClick = { captureFrame() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh Snapshot", tint = AccentSage)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(DeepNavy, RoundedCornerShape(8.dp))
                .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                .clipToBounds(),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = "Desktop Peek Preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                        .transformable(state = transformState)
                )
            } else if (isLoading) {
                CircularProgressIndicator(color = AccentSage)
            } else {
                Text("Tap refresh to capture workstation display", color = TextSecondary, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Pinch to zoom (1x - 4x) | Pan to inspect",
                color = TextSecondary,
                fontSize = 11.sp
            )
            Button(
                onClick = {
                    scale = 1f
                    offset = Offset.Zero
                },
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceCard),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("Reset Zoom", color = TextPrimary, fontSize = 11.sp)
            }
        }
    }
}
