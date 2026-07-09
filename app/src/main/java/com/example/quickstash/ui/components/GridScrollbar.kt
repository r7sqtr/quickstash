package com.example.quickstash.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun GridScrollbar(
    gridState: LazyGridState,
    modifier: Modifier = Modifier,
) {
    val totalItems = gridState.layoutInfo.totalItemsCount
    val visibleItems = gridState.layoutInfo.visibleItemsInfo.size
    if (totalItems == 0 || visibleItems >= totalItems) return

    var dragging by remember { mutableStateOf(false) }
    var recentlyScrolled by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    LaunchedEffect(gridState.isScrollInProgress) {
        if (gridState.isScrollInProgress) {
            recentlyScrolled = true
        } else {
            delay(1500)
            recentlyScrolled = false
        }
    }

    val visible = recentlyScrolled || dragging
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = if (visible) 100 else 400),
        label = "scrollbarAlpha",
    )

    val thumbWidth = if (dragging) 14.dp else 8.dp

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(36.dp)
            .graphicsLayer { this.alpha = alpha },
    ) {
        val trackHeightPx = constraints.maxHeight.toFloat()
        val thumbFraction = (visibleItems.toFloat() / totalItems).coerceIn(0.08f, 1f)
        val thumbHeightPx = trackHeightPx * thumbFraction
        val scrollableItems = (totalItems - visibleItems).coerceAtLeast(1)
        val progress =
            (gridState.firstVisibleItemIndex.toFloat() / scrollableItems).coerceIn(0f, 1f)
        val offsetYPx = (trackHeightPx - thumbHeightPx) * progress
        val thumbHeightDp = with(density) { thumbHeightPx.toDp() }

        val currentThumbTop = rememberUpdatedState(offsetYPx)
        val currentThumbHeight = rememberUpdatedState(thumbHeightPx)
        val currentTrackHeight = rememberUpdatedState(trackHeightPx)
        val currentTotal = rememberUpdatedState(totalItems)
        val currentVisible = rememberUpdatedState(visibleItems)
        val slopDp = with(density) { THUMB_GRAB_SLOP.toDp() }

        Box(modifier = Modifier.fillMaxHeight().width(36.dp)) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(0, offsetYPx.roundToInt()) }
                    .align(Alignment.TopEnd)
                    .padding(end = 3.dp)
                    .width(thumbWidth)
                    .height(thumbHeightDp)
                    .background(
                        Color.White.copy(alpha = if (dragging) 0.95f else 0.7f),
                        RoundedCornerShape(50),
                    ),
            )
            if (visible) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset(0, (offsetYPx - THUMB_GRAB_SLOP).roundToInt()) }
                        .width(36.dp)
                        .height(thumbHeightDp + slopDp * 2)
                        .pointerInput(Unit) {
                            var trackY = 0f
                            detectVerticalDragGestures(
                                onDragStart = {
                                    dragging = true
                                    trackY = currentThumbTop.value
                                },
                                onDragEnd = { dragging = false },
                                onDragCancel = { dragging = false },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    trackY += dragAmount
                                    val denominator =
                                        (currentTrackHeight.value - currentThumbHeight.value)
                                            .coerceAtLeast(1f)
                                    val fraction = (trackY / denominator).coerceIn(0f, 1f)
                                    val target =
                                        (fraction * (currentTotal.value - currentVisible.value))
                                            .roundToInt()
                                            .coerceIn(0, currentTotal.value - 1)
                                    scope.launch { gridState.scrollToItem(target) }
                                },
                            )
                        },
                )
            }
        }
    }
}

private const val THUMB_GRAB_SLOP = 24f
