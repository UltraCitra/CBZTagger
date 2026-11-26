package com.cbztagger.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class FastScrollIndicator(
    val pageNumber: Int,
    val totalPages: Int,
    val centerYPx: Float,
)

/**
 * Browser-style vertical fast-scroll slider rail on the right edge of a [LazyListState].
 * Supports both direct tap-to-jump and continuous vertical dragging without top/bottom buttons.
 */
@Composable
fun LazyListFastScrollbar(
    listState: LazyListState,
    totalItems: Int,
    onIndicatorChange: (FastScrollIndicator?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var trackHeightPx by remember { mutableIntStateOf(0) }

    val canScroll by remember(listState) {
        derivedStateOf { listState.canScrollForward || listState.canScrollBackward }
    }
    if (totalItems <= 1 || (!canScroll && !isDragging)) {
        return
    }

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val verticalPaddingPx = with(density) { 8.dp.toPx() }
    val minThumbPx = with(density) { 44.dp.toPx() }

    val metrics by remember(listState, totalItems) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            val viewportHeight = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset)
                .coerceAtLeast(1)
                .toFloat()
            val avgItemHeight = if (visibleItems.isNotEmpty()) {
                val sum = visibleItems.sumOf { it.size }.toFloat()
                val spacing = layoutInfo.mainAxisItemSpacing.toFloat()
                (sum / visibleItems.size + spacing).coerceAtLeast(1f)
            } else {
                1f
            }
            val estimatedTotalHeight = (totalItems * avgItemHeight).coerceAtLeast(viewportHeight)
            val maxScrollPx = (estimatedTotalHeight - viewportHeight).coerceAtLeast(1f)
            val currentScrollPx = listState.firstVisibleItemIndex * avgItemHeight +
                listState.firstVisibleItemScrollOffset
            val scrollFraction = when {
                !listState.canScrollForward -> 1f
                listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 -> 0f
                else -> (currentScrollPx / maxScrollPx).coerceIn(0f, 1f)
            }
            val viewportRatio = (viewportHeight / estimatedTotalHeight).coerceIn(0.08f, 1f)
            ScrollMetrics(
                avgItemHeight = avgItemHeight,
                maxScrollPx = maxScrollPx,
                scrollFraction = scrollFraction,
                viewportRatio = viewportRatio,
            )
        }
    }

    val effectiveTrackPx = (trackHeightPx - verticalPaddingPx * 2f).coerceAtLeast(1f)
    val thumbHeightPx = (effectiveTrackPx * metrics.viewportRatio)
        .coerceIn(minThumbPx.coerceAtMost(effectiveTrackPx), effectiveTrackPx * 0.65f)
    val usableTrackPx = (effectiveTrackPx - thumbHeightPx).coerceAtLeast(1f)
    val activeFraction = if (isDragging) dragFraction else metrics.scrollFraction
    val thumbOffsetYPx = verticalPaddingPx + activeFraction * usableTrackPx

    fun scrollToPointerY(pointerY: Float) {
        if (trackHeightPx <= 0 || totalItems <= 1) return
        val newFraction = ((pointerY - verticalPaddingPx - thumbHeightPx / 2f) / usableTrackPx)
            .coerceIn(0f, 1f)
        dragFraction = newFraction
        val pageNumber = (newFraction * (totalItems - 1)).roundToInt().coerceIn(0, totalItems - 1) + 1
        val centerY = verticalPaddingPx + newFraction * usableTrackPx + thumbHeightPx / 2f
        onIndicatorChange(
            FastScrollIndicator(
                pageNumber = pageNumber,
                totalPages = totalItems,
                centerYPx = centerY,
            ),
        )
        scope.launch {
            when {
                newFraction <= 0.001f -> listState.scrollToItem(0, 0)
                newFraction >= 0.999f -> listState.scrollToItem(totalItems - 1, 0)
                else -> {
                    val targetScrollPx = newFraction * metrics.maxScrollPx
                    val targetIndex = (targetScrollPx / metrics.avgItemHeight)
                        .toInt()
                        .coerceIn(0, totalItems - 1)
                    val targetOffset = (targetScrollPx - targetIndex * metrics.avgItemHeight)
                        .roundToInt()
                        .coerceAtLeast(0)
                    listState.scrollToItem(targetIndex, targetOffset)
                }
            }
        }
    }

    Box(
        modifier = modifier
            .width(24.dp)
            .fillMaxHeight()
            .onSizeChanged { trackHeightPx = it.height }
            .pointerInput(totalItems) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    isDragging = true
                    scrollToPointerY(down.position.y)
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: event.changes.firstOrNull()
                                ?: break
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            change.consume()
                            scrollToPointerY(change.position.y)
                        }
                    } finally {
                        isDragging = false
                        onIndicatorChange(null)
                    }
                }
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        // Subtle browser-like vertical scrollbar track
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 8.dp)
                .width(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        )

        // Draggable thumb
        val thumbHeightDp = with(density) { thumbHeightPx.toDp() }
        val thumbWidthDp = if (isDragging) 8.dp else 6.dp
        val thumbColor = if (isDragging) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, thumbOffsetYPx.roundToInt()) },
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                modifier = Modifier
                    .width(thumbWidthDp)
                    .height(thumbHeightDp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(thumbColor),
            )
        }
    }
}

private data class ScrollMetrics(
    val avgItemHeight: Float,
    val maxScrollPx: Float,
    val scrollFraction: Float,
    val viewportRatio: Float,
)
