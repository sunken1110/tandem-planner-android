package io.github.sunken1110.tandemplanner.ui.year

import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import io.github.sunken1110.tandemplanner.ui.common.BarSegments
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sunken1110.tandemplanner.data.Event
import io.github.sunken1110.tandemplanner.ui.common.DayCell
import io.github.sunken1110.tandemplanner.ui.common.barColors
import io.github.sunken1110.tandemplanner.ui.common.eventBar
import io.github.sunken1110.tandemplanner.ui.common.MonthGrid
import io.github.sunken1110.tandemplanner.ui.common.WeekdayHeaderRow
import io.github.sunken1110.tandemplanner.ui.common.YearPaint
import java.time.LocalDate
import java.time.YearMonth

/**
 * Whole-year overview of [YearPaint.year]: 12 mini months in a 3-column grid.
 * Each card shares its bounds with [io.github.sunken1110.tandemplanner.ui.month.MonthDetail]
 * so tapping it expands the card in place.
 *
 * @param state hoisted by the caller so the scroll position survives a trip to
 *        the month view (the grid itself is disposed meanwhile).
 */
@Composable
fun YearGrid(
    paint: YearPaint,
    eventsByDate: Map<LocalDate, List<Event>>,
    colorOf: (String) -> Color,
    state: LazyGridState,
    sharedScope: SharedTransitionScope,
    animScope: AnimatedVisibilityScope,
    onMonthClick: (Int) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = state,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items((1..12).toList()) { month ->
            val yearMonth = YearMonth.of(paint.year, month)
            val sharedModifier = with(sharedScope) {
                Modifier.sharedBounds(
                    sharedContentState = rememberSharedContentState(key = monthBoundsKey(yearMonth)),
                    animatedVisibilityScope = animScope
                )
            }
            MonthCard(
                yearMonth = yearMonth,
                paint = paint,
                eventsByDate = eventsByDate,
                colorOf = colorOf,
                modifier = sharedModifier,
                onClick = { onMonthClick(month) }
            )
        }
    }
}

/**
 * Shared-bounds key linking a year-grid card to its expanded month view.
 * Includes the year, so the same month of two years never pairs up.
 */
fun monthBoundsKey(yearMonth: YearMonth): String = "month-$yearMonth"

/**
 * A compact month: weekday header plus tiny day cells. Days with events get a
 * thin bottom bar, one segment per event (up to three) in the author's color.
 */
@Composable
private fun MonthCard(
    yearMonth: YearMonth,
    paint: YearPaint,
    eventsByDate: Map<LocalDate, List<Event>>,
    colorOf: (String) -> Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .clickable(onClick = onClick)
            // Read as "11월", not as the month name followed by every weekday letter.
            // Another year's card says its year too ("2027년 1월").
            .clearAndSetSemantics {
                contentDescription =
                    (if (yearMonth.year != paint.today.year) "${yearMonth.year}년 " else "") + "${yearMonth.monthValue}월"
                role = Role.Button
                onClick { onClick(); true }
            }
            .padding(6.dp)
    ) {
        Text("${yearMonth.monthValue}월", fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        WeekdayHeaderRow(fontSize = 7.sp)
        Spacer(Modifier.height(2.dp))

        MonthGrid(yearMonth, gap = 2.dp) { date, _ ->
            YearDayCell(
                year = date.year,
                dayOfYear = date.dayOfYear,
                paint = paint,
                bar = eventsByDate[date]?.let { BarSegments(it.barColors(colorOf)) }
            )
        }
    }
}

/**
 * One tiny year-view day. Takes only stable, value-compared parameters (ints and
 * an @Immutable bar), so when events change only the days whose bar changed
 * recompose, instead of all ~365. (A LocalDate parameter would be a new object
 * on every pass and defeat that.)
 */
@Composable
private fun YearDayCell(year: Int, dayOfYear: Int, paint: YearPaint, bar: BarSegments?) {
    val date = remember(year, dayOfYear) { LocalDate.ofYearDay(year, dayOfYear) }
    DayCell(date = date, paint = paint, corner = 3.dp, borderWidth = 1.5.dp) {
        if (bar != null) Box(Modifier.matchParentSize().eventBar(bar.colors))
    }
}
