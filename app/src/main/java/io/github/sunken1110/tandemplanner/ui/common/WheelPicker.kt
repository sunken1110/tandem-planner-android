package io.github.sunken1110.tandemplanner.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sunken1110.tandemplanner.ui.effects.rememberHaptics
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * A cyclic wheel repeats its items this many times, starting in the middle, so
 * it can be scrolled "forever" in both directions in practice.
 */
private const val CYCLIC_REPEAT = 1000

/**
 * Keeps leftover scroll inside the wheel so reaching its end doesn't drag the
 * surrounding bottom sheet.
 */
private object ConsumeRemainingScroll : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource) = available
    override suspend fun onPostFling(consumed: Velocity, available: Velocity) = available
}

/**
 * iOS-style scrolling wheel that snaps to one item, with a haptic tick per step.
 *
 * The centered item is the selection. Scrolling reports changes live through
 * [onSelectedIndexChange]; tapping a visible item scrolls it to the center;
 * changing [selectedIndex] from outside (e.g. a toggle) moves the wheel to it.
 *
 * @param cyclic when true the items repeat endlessly (… 11, 12, 1, 2 …).
 *        When an outside change lands on an item with the same label as the
 *        current one (e.g. 오전 3 → 오후 3 on a 24-step hour wheel), the wheel
 *        jumps there invisibly instead of scrolling through every item.
 * @param visibleCount number of rows shown; should be odd so one row is centered.
 * @param onCenterTap called when the already-centered row is tapped. Other rows
 *        are always brought to the center on tap. Useful for a two-item wheel
 *        that should also work as a toggle.
 */
@Composable
fun WheelPicker(
    items: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 40.dp,
    visibleCount: Int = 5,
    cyclic: Boolean = false,
    onCenterTap: (() -> Unit)? = null
) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val onChange by rememberUpdatedState(onSelectedIndexChange)
    val count = items.size
    val total = if (cyclic) count * CYCLIC_REPEAT else count
    val padding = visibleCount / 2
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = if (cyclic) (CYCLIC_REPEAT / 2) * count + selectedIndex else selectedIndex
    )
    val itemPx = with(LocalDensity.current) { itemHeight.toPx() }

    // `padding` blank rows sit above the items, so the first visible row index
    // equals the (absolute) index of the item in the center.
    val centeredAbs by remember {
        derivedStateOf {
            val extra = if (listState.firstVisibleItemScrollOffset > itemPx / 2) 1 else 0
            (listState.firstVisibleItemIndex + extra).coerceIn(0, total - 1)
        }
    }

    // True while the wheel moves because of an outside selection change.
    var programmatic by remember { mutableStateOf(false) }

    LaunchedEffect(centeredAbs) {
        val logical = centeredAbs % count
        if (!programmatic && logical != selectedIndex) {
            haptics.tick()
            onChange(logical)
        }
    }

    // Safety net: whenever the wheel comes to rest, make sure the caller has the
    // value it shows. Covers a programmatic move interrupted by a touch that then
    // settles without changing the centered row (no change event would fire).
    val latestSelected by rememberUpdatedState(selectedIndex)
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling && !programmatic) {
                val logical = centeredAbs % count
                if (logical != latestSelected) onChange(logical)
            }
        }
    }

    LaunchedEffect(selectedIndex) {
        // Wait out user-driven scrolling; a running programmatic move is simply replaced.
        snapshotFlow { listState.isScrollInProgress && !programmatic }.first { !it }
        val current = centeredAbs
        if (current % count == selectedIndex) return@LaunchedEffect
        val target = if (cyclic) nearestAbsolute(current, selectedIndex, count, total) else selectedIndex
        programmatic = true
        try {
            if (items[target % count] == items[current % count]) {
                listState.scrollToItem(target) // same label: jump without visible motion
            } else {
                listState.animateScrollToItem(target)
            }
        } finally {
            programmatic = false
        }
    }

    val noRipple = remember { MutableInteractionSource() }

    Box(modifier.requiredHeight(itemHeight * visibleCount), contentAlignment = Alignment.Center) {
        // Highlight band behind the centered row.
        Box(
            Modifier
                .fillMaxWidth()
                .requiredHeight(itemHeight)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
        )
        LazyColumn(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                // Fixed height even if the parent is squeezed (e.g. by the keyboard);
                // otherwise the centered row would drift away from the highlight band.
                .requiredHeight(itemHeight * visibleCount)
                .nestedScroll(ConsumeRemainingScroll)
                // Fade rows out toward the top and bottom edges.
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.35f to Color.Black,
                            0.65f to Color.Black,
                            1f to Color.Transparent
                        ),
                        blendMode = BlendMode.DstIn
                    )
                }
        ) {
            items(padding) { Box(Modifier.requiredHeight(itemHeight)) }
            items(total) { row ->
                val isCenter = row == centeredAbs
                Box(
                    Modifier
                        .fillMaxWidth()
                        .requiredHeight(itemHeight)
                        // Tap a row to bring it to the center (or toggle, for the centered row).
                        .clickable(interactionSource = noRipple, indication = null) {
                            if (row == centeredAbs && onCenterTap != null) onCenterTap()
                            else scope.launch { listState.animateScrollToItem(row) }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        items[row % count],
                        fontSize = if (isCenter) 20.sp else 18.sp,
                        fontWeight = if (isCenter) FontWeight.Medium else FontWeight.Normal,
                        color = if (isCenter) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(padding) { Box(Modifier.requiredHeight(itemHeight)) }
        }
    }
}

/** The absolute index showing [logical] that is closest to [current] on a cyclic wheel. */
private fun nearestAbsolute(current: Int, logical: Int, count: Int, total: Int): Int {
    val base = current - current % count + logical
    return listOf(base - count, base, base + count)
        .filter { it in 0 until total }
        .minBy { abs(it - current) }
}
