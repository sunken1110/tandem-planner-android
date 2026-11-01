package io.github.sunken1110.tandemplanner.ui.month

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sunken1110.tandemplanner.data.Event
import io.github.sunken1110.tandemplanner.ui.common.CELL_SLOTS
import io.github.sunken1110.tandemplanner.ui.common.SLOT_HEIGHT
import io.github.sunken1110.tandemplanner.ui.common.SLOT_INSET
import io.github.sunken1110.tandemplanner.ui.common.chipBackground
import io.github.sunken1110.tandemplanner.ui.common.chipText
import io.github.sunken1110.tandemplanner.ui.common.slotTop
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt

/** Band tint: a little stronger than a title chip, so a band reads as one long bar. */
private const val BAND_TINT = 0.28f

/** Rounding at a band's real start and end (a band continuing to another week stays square). */
private val BAND_CORNER = 3.dp

/**
 * Where each multi-day event of a month is drawn: its row ("lane") in the cells,
 * 0 = upper. Events without a lane didn't fit (more overlapping events than
 * [CELL_SLOTS]) and are only counted in the cells' "+N".
 */
@Immutable
data class BandLayout(val events: List<Event>, val lanes: Map<String, Int>) {
    /** Lanes taken by bands on [day]. */
    fun blockedSlots(day: LocalDate): Set<Int> =
        events.mapNotNullTo(HashSet()) { e -> if (e.covers(day)) lanes[e.id] else null }

    /** Multi-day events on [day] that have no band. */
    fun hiddenCount(day: LocalDate): Int = events.count { it.covers(day) && it.id !in lanes }

    companion object {
        val Empty = BandLayout(emptyList(), emptyMap())
    }
}

/**
 * Gives each multi-day event of [yearMonth] a lane for the whole month, so a band
 * stays at the same height from week to week. Earlier events, then longer ones,
 * choose first; each takes the upper-most lane that is free on all of its days.
 *
 * @param events all events (only multi-day ones touching the month are used).
 */
fun layoutBands(yearMonth: YearMonth, events: Collection<Event>): BandLayout {
    val first = yearMonth.atDay(1)
    val last = yearMonth.atEndOfMonth()
    val inMonth = events
        .filter { it.isMultiDay && !it.lastDate.isBefore(first) && !it.date.isAfter(last) }
        .distinctBy { it.id }
        .sortedWith(compareBy<Event>({ it.date }, { -it.dayCount }, { it.id }))
    val taken = List(CELL_SLOTS) { mutableListOf<Event>() }
    val lanes = HashMap<String, Int>()
    for (event in inMonth) {
        val lane = taken.indexOfFirst { others ->
            others.none { !it.lastDate.isBefore(event.date) && !event.lastDate.isBefore(it.date) }
        }
        if (lane >= 0) {
            taken[lane] += event
            lanes[event.id] = lane
        }
    }
    return BandLayout(inMonth, lanes)
}

/** One week row's piece of a band. */
private data class BandSegment(
    val event: Event,
    val lane: Int,
    val row: Int,
    val startColumn: Int,
    val endColumn: Int,
    /** The event really starts in this piece (rounded left end), rather than continuing. */
    val startsHere: Boolean,
    /** The event really ends in this piece (rounded right end). */
    val endsHere: Boolean
)

/** Splits each band into one piece per week row of the month grid. */
private fun segmentsOf(yearMonth: YearMonth, layout: BandLayout): List<BandSegment> {
    val firstColumn = yearMonth.atDay(1).dayOfWeek.value % 7 // Sunday = 0
    val first = yearMonth.atDay(1)
    val last = yearMonth.atEndOfMonth()
    return layout.events.flatMap { event ->
        val lane = layout.lanes[event.id] ?: return@flatMap emptyList()
        val from = maxOf(event.date, first)
        val to = minOf(event.lastDate, last)
        val fromSlot = firstColumn + from.dayOfMonth - 1
        val toSlot = firstColumn + to.dayOfMonth - 1
        (fromSlot / 7..toSlot / 7).map { row ->
            val start = maxOf(fromSlot, row * 7)
            val end = minOf(toSlot, row * 7 + 6)
            BandSegment(
                event = event,
                lane = lane,
                row = row,
                startColumn = start % 7,
                endColumn = end % 7,
                startsHere = start == fromSlot && from == event.date,
                endsHere = end == toSlot && to == event.lastDate
            )
        }
    }
}

/**
 * Multi-day events drawn as continuous bands across the month grid, like a wall
 * calendar: one bar from the first day to the last, split at the end of each
 * week and continued on the next row. The title is shown at the start of each
 * row's piece. Ends that continue into another week (or month) are square, real
 * start and end days are rounded.
 *
 * Meant to be placed over the grid ([cellPx] and [gapPx] are the grid's cell size
 * and spacing). It draws only; taps go through to the day cells below.
 */
@Composable
fun MonthBands(
    yearMonth: YearMonth,
    layout: BandLayout,
    cellPx: Float,
    gapPx: Float,
    colorOf: (String) -> Color,
    modifier: Modifier = Modifier
) {
    if (cellPx <= 0f || layout.lanes.isEmpty()) return
    val density = LocalDensity.current
    val insetPx = with(density) { SLOT_INSET.toPx() }
    val cellDp = with(density) { cellPx.toDp() }
    val segments = segmentsOf(yearMonth, layout)

    Box(modifier) {
        segments.forEach { s ->
            val step = cellPx + gapPx
            val left = s.startColumn * step + if (s.startsHere) insetPx else 0f
            val right = s.endColumn * step + cellPx - if (s.endsHere) insetPx else 0f
            val top = s.row * step + with(density) { slotTop(s.lane, cellDp).toPx() }
            val author = colorOf(s.event.authorUid)
            val shape = RoundedCornerShape(
                topStart = if (s.startsHere) BAND_CORNER else 0.dp,
                bottomStart = if (s.startsHere) BAND_CORNER else 0.dp,
                topEnd = if (s.endsHere) BAND_CORNER else 0.dp,
                bottomEnd = if (s.endsHere) BAND_CORNER else 0.dp
            )
            Box(
                Modifier
                    .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                    .size(width = with(density) { (right - left).toDp() }, height = SLOT_HEIGHT)
                    .background(chipBackground(author, BAND_TINT), shape)
                    .padding(horizontal = 3.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    s.event.title,
                    fontSize = 9.sp,
                    lineHeight = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = chipText(author),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
