package io.github.sunken1110.tandemplanner.ui.year

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sunken1110.tandemplanner.data.Event
import io.github.sunken1110.tandemplanner.ui.common.DayCell
import io.github.sunken1110.tandemplanner.ui.common.WeekdayHeaders
import io.github.sunken1110.tandemplanner.ui.common.weekdayHeaderColor
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
    year: Int,
    today: LocalDate,
    passedDays: Int,
    fillProvider: () -> Float,
    yearColors: List<Color>,
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
                yearMonth = YearMonth.of(year, month),
                today = today,
                passedDays = passedDays,
                fillProvider = fillProvider,
                yearColors = yearColors,
                eventsByDate = eventsByDate,
                modifier = sharedModifier,
                onClick = { onMonthClick(month) }
            )
        }
    }
}

/** A compact month: weekday header plus tiny day cells. Days with events get a center dot. */
@Composable
fun MonthCard(
    yearMonth: YearMonth,
    today: LocalDate,
    passedDays: Int,
    fillProvider: () -> Float,
    yearColors: List<Color>,
    eventsByDate: Map<LocalDate, List<Event>>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val firstDayOffset = yearMonth.atDay(1).dayOfWeek.value % 7 // Sunday = 0
    val daysInMonth = yearMonth.lengthOfMonth()
    val rows = (firstDayOffset + daysInMonth + 6) / 7

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .clickable(onClick = onClick)
            .padding(6.dp)
    ) {
        Text("${yearMonth.monthValue}월", fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))

        Row(Modifier.fillMaxWidth()) {
            WeekdayHeaders.forEachIndexed { i, w ->
                Text(
                    w,
                    modifier = Modifier.weight(1f),
                    fontSize = 7.sp,
                    textAlign = TextAlign.Center,
                    color = weekdayHeaderColor(i)
                )
            }
        }

        for (r in 0 until rows) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                for (c in 0 until 7) {
                    val day = r * 7 + c - firstDayOffset + 1
                    Box(Modifier.weight(1f).aspectRatio(1f)) {
                        if (day in 1..daysInMonth) {
                            val date = yearMonth.atDay(day)
                            val doy = date.dayOfYear
                            DayCell(
                                base = yearColors[doy - 1],
                                dayOfYear = doy,
                                passedDays = passedDays,
                                fillProvider = fillProvider,
                                isToday = date == today,
                                corner = 3.dp,
                                borderWidth = 1.5.dp
                            ) {
                                if (eventsByDate[date]?.isNotEmpty() == true) {
                                    Box(
                                        Modifier
                                            .align(Alignment.Center)
                                            .size(3.dp)
                                            .clip(CircleShape)
                                            .background(OnSeasonColor.copy(alpha = 0.75f))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
