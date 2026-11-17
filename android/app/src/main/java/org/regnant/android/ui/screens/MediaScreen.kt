package org.regnant.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.regnant.android.data.AudioState
import org.regnant.android.data.ConnectionStatus
import org.regnant.android.data.MediaState
import org.regnant.android.network.RegnantClient
import org.regnant.android.ui.theme.*

@Composable
fun MediaScreen(
    client: RegnantClient,
    connectionStatus: ConnectionStatus
) {
    val scope = rememberCoroutineScope()
    var audioState by remember { mutableStateOf(AudioState()) }
    var mediaState by remember { mutableStateOf(MediaState()) }
    var sliderValue by remember { mutableFloatStateOf(50f) }

    fun refreshAll() {
        if (connectionStatus != ConnectionStatus.CONNECTED) return
        scope.launch {
            val aRes = client.getAudioState()
            if (aRes.isSuccess) {
                audioState = aRes.getOrThrow()
                sliderValue = audioState.volume.toFloat()
            }
            val mRes = client.getMediaState()
            if (mRes.isSuccess) {
                mediaState = mRes.getOrThrow()
            }
        }
    }

    LaunchedEffect(connectionStatus) {
        refreshAll()
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
                text = "Audio & Media Controls",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = { refreshAll() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = AccentSage)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // PipeWire Volume Card
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("PipeWire Audio Sink", color = TextSecondary, fontSize = 12.sp)
                        Text(audioState.sink, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                val res = client.toggleAudioMute()
                                if (res.isSuccess) audioState = res.getOrThrow()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (audioState.muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = "Mute Toggle",
                            tint = if (audioState.muted) CoralRed else AccentSage
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Volume", color = TextSecondary, fontSize = 12.sp)
                    Text("${sliderValue.toInt()}%", color = AccentSage, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    onValueChangeFinished = {
                        scope.launch {
                            val res = client.setAudioVolume(sliderValue.toInt())
                            if (res.isSuccess) audioState = res.getOrThrow()
                        }
                    },
                    valueRange = 0f..150f,
                    colors = SliderDefaults.colors(
                        thumbColor = AccentSage,
                        activeTrackColor = AccentSage,
                        inactiveTrackColor = DeepNavy
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // MPRIS Media Controls Card
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("MPRIS Media Player", color = TextSecondary, fontSize = 12.sp)
                    Text(
                        text = if (mediaState.available) mediaState.playbackStatus else "No Active Player",
                        color = if (mediaState.playbackStatus == "Playing") AccentSage else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (mediaState.title.isNotEmpty()) mediaState.title else "Nothing playing",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                if (mediaState.artist.isNotEmpty()) {
                    Text(
                        text = mediaState.artist,
                        color = AccentSage,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Transport Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            scope.launch {
                                client.controlMedia("previous")
                                refreshAll()
                            }
                        },
                        enabled = mediaState.available
                    ) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = TextPrimary)
                    }

                    FilledIconButton(
                        onClick = {
                            scope.launch {
                                client.controlMedia("play_pause")
                                refreshAll()
                            }
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = AccentSage),
                        enabled = mediaState.available
                    ) {
                        Icon(
                            imageVector = if (mediaState.playbackStatus == "Playing") Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = DeepNavy
                        )
                    }

                    IconButton(
                        onClick = {
                            scope.launch {
                                client.controlMedia("next")
                                refreshAll()
                            }
                        },
                        enabled = mediaState.available
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = TextPrimary)
                    }

                    IconButton(
                        onClick = {
                            scope.launch {
                                client.controlMedia("stop")
                                refreshAll()
                            }
                        },
                        enabled = mediaState.available
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop", tint = TextPrimary)
                    }
                }
            }
        }
    }
}
