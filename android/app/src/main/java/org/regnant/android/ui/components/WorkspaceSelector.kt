package org.regnant.android.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.regnant.android.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun WorkspaceSelector(
    activeWorkspaceId: Int,
    enabled: Boolean,
    onWorkspaceSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    monitorName: String? = null
) {
    val density = LocalDensity.current
    var totalWidthPx by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var dragSlot by remember { mutableIntStateOf(activeWorkspaceId) }

    // Check if authoritative workspace is within displayed range 1..5
    val isStandardRange = activeWorkspaceId in 1..5
    val currentSlot = if (isDragging) dragSlot else if (isStandardRange) activeWorkspaceId else -1

    // Target index 0..4 for the sliding indicator pill (or offscreen/invisible if outside 1..5)
    val targetIndex = if (currentSlot in 1..5) (currentSlot - 1).toFloat() else -1f

    val animatedIndex by animateFloatAsState(
        targetValue = if (targetIndex >= 0f) targetIndex else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "workspaceIndicator"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceCard)
            .border(1.dp, SurfaceBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        // Header Row: Label & Status
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "WORKSPACES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = Leaf,
                    fontFamily = FontFamily.Monospace
                )
                if (monitorName != null && monitorName.isNotBlank() && monitorName != "default") {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "• $monitorName",
                        fontSize = 10.sp,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            if (!enabled) {
                Text(
                    text = "OFFLINE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace
                )
            } else if (!isStandardRange) {
                Text(
                    text = "ACTIVE: WS $activeWorkspaceId",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Coral,
                    fontFamily = FontFamily.Monospace
                )
            } else {
                Text(
                    text = "SLOT $activeWorkspaceId",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Candle,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Interactive Workspace Track
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceDark)
                .border(1.dp, Juniper, RoundedCornerShape(8.dp))
                .onGloballyPositioned { layoutCoordinates ->
                    totalWidthPx = layoutCoordinates.size.width.toFloat()
                }
                .pointerInput(enabled, activeWorkspaceId) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downX = down.position.x
                        var isDrag = false
                        val currentWidth = if (totalWidthPx > 0f) totalWidthPx else size.width.toFloat()
                        val slotWidth = if (currentWidth > 0f) currentWidth / 5f else 1f

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.changedToUp()) {
                                change.consume()
                                if (isDrag) {
                                    isDragging = false
                                    if (dragSlot in 1..5) {
                                        onWorkspaceSelected(dragSlot)
                                    }
                                } else {
                                    val slot = ((change.position.x / slotWidth).toInt() + 1).coerceIn(1, 5)
                                    onWorkspaceSelected(slot)
                                }
                                break
                            } else {
                                val dx = kotlin.math.abs(change.position.x - downX)
                                if (dx > 20f) {
                                    if (!isDrag) {
                                        isDrag = true
                                        isDragging = true
                                    }
                                    change.consume()
                                    val tentative = ((change.position.x / slotWidth).toInt() + 1).coerceIn(1, 5)
                                    dragSlot = tentative
                                }
                            }
                        }
                    }
                }
        ) {
            val trackWidth = maxWidth
            val slotWidth = trackWidth / 5

            // Animated Sliding Highlight Pill
            if (isStandardRange || isDragging) {
                val indicatorOffset = slotWidth * animatedIndex
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .width(slotWidth)
                        .fillMaxHeight()
                        .padding(3.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (enabled) Juniper else Juniper.copy(alpha = 0.4f))
                        .border(
                            1.dp,
                            if (enabled) Leaf else SurfaceBorder,
                            RoundedCornerShape(6.dp)
                        )
                )
            }

            // 5 Workspace Slots Row
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (slot in 1..5) {
                    val isSlotActive = (isStandardRange && slot == activeWorkspaceId) || (isDragging && slot == dragSlot)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .semantics {
                                contentDescription = "Workspace $slot${if (isSlotActive) ", Active" else ""}"
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "$slot",
                                fontSize = if (isSlotActive) 16.sp else 14.sp,
                                fontWeight = if (isSlotActive) FontWeight.Bold else FontWeight.Medium,
                                color = when {
                                    !enabled -> TextMuted.copy(alpha = 0.5f)
                                    isSlotActive -> Candle
                                    else -> TextMuted
                                },
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.Center
                            )

                            // Subtle dash between items (except after item 5)
                            if (slot < 5) {
                                Text(
                                    text = " —",
                                    fontSize = 11.sp,
                                    color = SurfaceBorder,
                                    modifier = Modifier.padding(start = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
