package io.github.sunken1110.tandemplanner.ui.year

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sunken1110.tandemplanner.data.Event
import io.github.sunken1110.tandemplanner.ui.common.DayCell
import io.github.sunken1110.tandemplanner.ui.common.MemberDot
import io.github.sunken1110.tandemplanner.ui.common.MonthGrid
import io.github.sunken1110.tandemplanner.ui.common.WeekdayHeaderRow
import io.github.sunken1110.tandemplanner.ui.common.YearPaint
import io.github.sunken1110.tandemplanner.ui.theme.OnSeasonColor
import java.time.LocalDate
import java.time.YearMonth

/**
 * Whole-year overview: 12 mini months in a 3-column grid.
 * Each card shares its bounds with [io.github.sunken1110.tandemplanner.ui.month.MonthDetail]
 * so tapping it expands the card in place.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun YearGrid(
    paint: YearPaint,
    eventsByDate: Map<LocalDate, List<Event>>,
    sharedScope: SharedTransitionScope,
    animScope: AnimatedVisibilityScope,
    onMonthClick: (Int) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items((1..12).toList()) { month ->
            val sharedModifier = with(sharedScope) {
                Modifier.sharedBounds(
                    sharedContentState = rememberSharedContentState(key = "month-$month"),
                    animatedVisibilityScope = animScope
                )
            }
            MonthCard(
                yearMonth = YearMonth.of(paint.year, month),
                paint = paint,
                eventsByDate = eventsByDate,
                modifier = sharedModifier,
                onClick = { onMonthClick(month) }
            )
        }
    }
}

/** A compact month: weekday header plus tiny day cells. Days with events get a center dot. */
@Composable
private fun MonthCard(
    yearMonth: YearMonth,
    paint: YearPaint,
    eventsByDate: Map<LocalDate, List<Event>>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .clickable(onClick = onClick)
            .padding(6.dp)
    ) {
        Text("${yearMonth.monthValue}월", fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        WeekdayHeaderRow(fontSize = 7.sp)
        Spacer(Modifier.height(2.dp))

        MonthGrid(yearMonth, gap = 2.dp) { date, _ ->
            DayCell(date = date, paint = paint, corner = 3.dp, borderWidth = 1.5.dp) {
                if (eventsByDate[date]?.isNotEmpty() == true) {
                    MemberDot(
                        color = OnSeasonColor.copy(alpha = 0.75f),
                        size = 3.dp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}
