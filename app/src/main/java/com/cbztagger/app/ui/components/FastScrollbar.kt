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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

data class FastScrollIndicator(
    val pageNumber: Int,
    val totalPages: Int,
    val centerYPx: Float,
)

internal object FastScrollbarMath {
    fun computeThumbHeight(
        trackHeightPx: Float,
        verticalPaddingPx: Float,
        minThumbPx: Float,
        viewportRatio: Float,
    ): Float {
        val effectiveTrack = (trackHeightPx - verticalPaddingPx * 2f).coerceAtLeast(1f)
        val maxThumb = (effectiveTrack * 0.65f).coerceAtLeast(1f)
        val safeMin = minThumbPx.coerceAtMost(maxThumb)
        return (effectiveTrack * viewportRatio.coerceIn(0.08f, 1f)).coerceIn(safeMin, maxThumb)
    }

    fun pointerYToFraction(
        pointerY: Float,
        trackHeightPx: Float,
        verticalPaddingPx: Float,
        thumbHeightPx: Float,
    ): Float {
        val effectiveTrack = (trackHeightPx - verticalPaddingPx * 2f).coerceAtLeast(1f)
        val usableTrack = (effectiveTrack - thumbHeightPx).coerceAtLeast(1f)
        return ((pointerY - verticalPaddingPx - thumbHeightPx / 2f) / usableTrack).coerceIn(0f, 1f)
    }

    fun fractionToThumbOffsetY(
        fraction: Float,
        trackHeightPx: Float,
        verticalPaddingPx: Float,
        thumbHeightPx: Float,
    ): Float {
        val effectiveTrack = (trackHeightPx - verticalPaddingPx * 2f).coerceAtLeast(1f)
        val usableTrack = (effectiveTrack - thumbHeightPx).coerceAtLeast(1f)
        return verticalPaddingPx + fraction.coerceIn(0f, 1f) * usableTrack
    }
}

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
    var scrollRequestSeq by remember { mutableIntStateOf(0) }
    var trackHeightPx by remember { mutableIntStateOf(0) }

    val canScroll by remember(listState) {
        derivedStateOf { listState.canScrollForward || listState.canScrollBackward }
    }
    if (totalItems <= 1 || (!canScroll && !isDragging)) {
        return
    }

    val density = LocalDensity.current
    val verticalPaddingPx = with(density) { 8.dp.toPx() }
    val minThumbPx = with(density) { 44.dp.toPx() }
    val onIndicatorChangeState = rememberUpdatedState(onIndicatorChange)

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
                0f
            }
            val estimatedTotalHeight = if (avgItemHeight > 0f) {
                (totalItems * avgItemHeight).coerceAtLeast(viewportHeight)
            } else {
                viewportHeight
            }
            val maxScrollPx = (estimatedTotalHeight - viewportHeight).coerceAtLeast(1f)
            val currentScrollPx = listState.firstVisibleItemIndex * avgItemHeight +
                listState.firstVisibleItemScrollOffset
            val scrollFraction = when {
                !listState.canScrollForward -> 1f
                listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 -> 0f
                avgItemHeight > 0f -> (currentScrollPx / maxScrollPx).coerceIn(0f, 1f)
                else -> (listState.firstVisibleItemIndex.toFloat() / (totalItems - 1).coerceAtLeast(1))
                    .coerceIn(0f, 1f)
            }
            val viewportRatio = (viewportHeight / estimatedTotalHeight).coerceIn(0.08f, 1f)
            ScrollMetrics(
                scrollFraction = scrollFraction,
                viewportRatio = viewportRatio,
            )
        }
    }
    val viewportRatioState = rememberUpdatedState(metrics.viewportRatio)

    // Sequentially drive LazyListState from drag/tap requests so coroutines never fight MutatorMutex.
    LaunchedEffect(listState, totalItems) {
        if (totalItems <= 1) return@LaunchedEffect
        snapshotFlow { scrollRequestSeq to dragFraction }.collectLatest { (seq, fraction) ->
            if (seq <= 0) return@collectLatest
            when {
                fraction <= 0.001f -> listState.scrollToItem(0, 0)
                fraction >= 0.999f -> listState.scrollToItem(totalItems - 1, 0)
                else -> {
                    val layoutInfo = listState.layoutInfo
                    val visible = layoutInfo.visibleItemsInfo
                    val viewportH = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset)
                        .coerceAtLeast(1)
                        .toFloat()
                    val avgH = if (visible.isNotEmpty()) {
                        (visible.sumOf { it.size }.toFloat() / visible.size + layoutInfo.mainAxisItemSpacing)
                            .coerceAtLeast(1f)
                    } else {
                        0f
                    }
                    if (avgH > 0f) {
                        val maxScroll = (totalItems * avgH - viewportH).coerceAtLeast(0f)
                        val targetScroll = fraction * maxScroll
                        val targetIndex = (targetScroll / avgH).toInt().coerceIn(0, totalItems - 1)
                        val targetOffset = (targetScroll - targetIndex * avgH)
                            .roundToInt()
                            .coerceAtLeast(0)
                        listState.scrollToItem(targetIndex, targetOffset)
                    } else {
                        val targetIndex = (fraction * (totalItems - 1))
                            .roundToInt()
                            .coerceIn(0, totalItems - 1)
                        listState.scrollToItem(targetIndex, 0)
                    }
                }
            }
        }
    }

    val thumbHeightPx = FastScrollbarMath.computeThumbHeight(
        trackHeightPx = trackHeightPx.toFloat(),
        verticalPaddingPx = verticalPaddingPx,
        minThumbPx = minThumbPx,
        viewportRatio = metrics.viewportRatio,
    )
    val activeFraction = if (isDragging) dragFraction else metrics.scrollFraction
    val thumbOffsetYPx = FastScrollbarMath.fractionToThumbOffsetY(
        fraction = activeFraction,
        trackHeightPx = trackHeightPx.toFloat(),
        verticalPaddingPx = verticalPaddingPx,
        thumbHeightPx = thumbHeightPx,
    )

    Box(
        modifier = modifier
            .width(28.dp)
            .fillMaxHeight()
            .onSizeChanged { trackHeightPx = it.height }
            .pointerInput(totalItems) {
                val padPx = 8.dp.toPx()
                val minThumb = 44.dp.toPx()

                fun applyPointerY(pointerY: Float) {
                    val heightPx = size.height.toFloat()
                    if (heightPx <= 0f || totalItems <= 1) return
                    val currentThumbHeight = FastScrollbarMath.computeThumbHeight(
                        trackHeightPx = heightPx,
                        verticalPaddingPx = padPx,
                        minThumbPx = minThumb,
                        viewportRatio = viewportRatioState.value,
                    )
                    val newFraction = FastScrollbarMath.pointerYToFraction(
                        pointerY = pointerY,
                        trackHeightPx = heightPx,
                        verticalPaddingPx = padPx,
                        thumbHeightPx = currentThumbHeight,
                    )
                    dragFraction = newFraction
                    scrollRequestSeq += 1
                    val pageNumber = (newFraction * (totalItems - 1))
                        .roundToInt()
                        .coerceIn(0, totalItems - 1) + 1
                    val centerY = FastScrollbarMath.fractionToThumbOffsetY(
                        fraction = newFraction,
                        trackHeightPx = heightPx,
                        verticalPaddingPx = padPx,
                        thumbHeightPx = currentThumbHeight,
                    ) + currentThumbHeight / 2f
                    onIndicatorChangeState.value(
                        FastScrollIndicator(
                            pageNumber = pageNumber,
                            totalPages = totalItems,
                            centerYPx = centerY,
                        ),
                    )
                }

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    isDragging = true
                    applyPointerY(down.position.y)
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
                            applyPointerY(change.position.y)
                        }
                    } finally {
                        isDragging = false
                        onIndicatorChangeState.value(null)
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

        // Draggable thumb (render after track measurement)
        if (trackHeightPx > 0) {
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
}

private data class ScrollMetrics(
    val scrollFraction: Float,
    val viewportRatio: Float,
)
