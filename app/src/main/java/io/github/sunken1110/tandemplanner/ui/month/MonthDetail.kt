package io.github.sunken1110.tandemplanner.ui.month

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sunken1110.tandemplanner.data.Event
import io.github.sunken1110.tandemplanner.model.Season
import io.github.sunken1110.tandemplanner.ui.common.DayCell
import io.github.sunken1110.tandemplanner.ui.common.MemberDot
import io.github.sunken1110.tandemplanner.ui.common.MonthGrid
import io.github.sunken1110.tandemplanner.ui.common.WeekdayHeaderRow
import io.github.sunken1110.tandemplanner.ui.common.YearPaint
import io.github.sunken1110.tandemplanner.ui.common.dayNumberColor
import io.github.sunken1110.tandemplanner.ui.common.koreanShort
import io.github.sunken1110.tandemplanner.ui.common.weekendColor
import io.github.sunken1110.tandemplanner.ui.effects.rememberHaptics
import java.time.LocalDate
import java.time.YearMonth

/**
 * Expanded single-month view with a selectable day grid and that day's events.
 *
 * @param sharedScope / animScope enable the expand-from-card transition.
 *        Both may be null when rendered invisibly for JIT warm-up.
 * @param colorOf maps an author UID to that member's accent color.
 * @param nameOf  resolves an event's author display name.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MonthDetail(
    yearMonth: YearMonth,
    paint: YearPaint,
    eventsByDate: Map<LocalDate, List<Event>>,
    sharedScope: SharedTransitionScope?,
    animScope: AnimatedVisibilityScope?,
    colorOf: (String) -> Color,
    nameOf: (Event) -> String,
    onAddEvent: (LocalDate, String, String) -> Unit,
    onDeleteEvent: (Event) -> Unit,
    onBack: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    val haptics = rememberHaptics()
    val season = Season.of(yearMonth.monthValue)
    val daysInMonth = yearMonth.lengthOfMonth()
    val passedInMonth = (1..daysInMonth).count { paint.isPast(yearMonth.atDay(it)) }

    // Default selection: today for the current month, otherwise the 1st.
    var selectedDay by rememberSaveable(yearMonth.monthValue) {
        mutableIntStateOf(if (YearMonth.from(paint.today) == yearMonth) paint.today.dayOfMonth else 1)
    }
    val selectedDate = yearMonth.atDay(selectedDay.coerceIn(1, daysInMonth))
    var showAddDialog by remember { mutableStateOf(false) }

    val sharedModifier = if (sharedScope != null && animScope != null) {
        with(sharedScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "month-${yearMonth.monthValue}"),
                animatedVisibilityScope = animScope
            )
        }
    } else Modifier

    Column(
        Modifier
            .fillMaxSize()
            .then(sharedModifier)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .verticalScroll(rememberScrollState())
            .padding(8.dp)
    ) {
        // ── Navigation ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { haptics.tap(); onBack() }) { Text("‹ 1년 보기") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { haptics.tap(); onPrev() }, enabled = yearMonth.monthValue > 1) {
                Text("‹", fontSize = 20.sp)
            }
            Text("${yearMonth.monthValue}월", fontSize = 20.sp, fontWeight = FontWeight.Medium)
            TextButton(onClick = { haptics.tap(); onNext() }, enabled = yearMonth.monthValue < 12) {
                Text("›", fontSize = 20.sp)
            }
        }
        Text(
            "${season.label} · ${passedInMonth} / ${daysInMonth}일 지남",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp, bottom = 12.dp)
        )

        // ── Day grid ──
        WeekdayHeaderRow(fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        MonthGrid(yearMonth, gap = 6.dp) { date, column ->
            val isPast = paint.isPast(date)
            DayCell(
                date = date,
                paint = paint,
                corner = 10.dp,
                borderWidth = 2.dp,
                isSelected = date.dayOfMonth == selectedDay,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        haptics.tap()
                        selectedDay = date.dayOfMonth
                    }
            ) {
                // Day number, top-left. On filled cells a very faint white badge
                // keeps weekend colors readable.
                Text(
                    "${date.dayOfMonth}",
                    fontSize = 10.sp,
                    lineHeight = 10.sp,
                    fontWeight = if (date == paint.today) FontWeight.Bold else FontWeight.Medium,
                    color = dayNumberColor(column = column, isPast = isPast),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 4.dp, top = 4.dp)
                        .then(
                            if (isPast) Modifier.background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(3.dp))
                            else Modifier
                        )
                        .padding(horizontal = 2.dp)
                )
                // Up to three author-colored dots; the white ring separates them from the cell.
                val dayEvents = eventsByDate[date].orEmpty()
                if (dayEvents.isNotEmpty()) {
                    Row(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        dayEvents.take(3).forEach { ev ->
                            MemberDot(
                                color = colorOf(ev.authorUid),
                                size = 4.dp,
                                modifier = Modifier
                                    .background(Color.White.copy(alpha = 0.9f), CircleShape)
                                    .padding(1.dp)
                            )
                        }
                    }
                }
            }
        }

        // ── Selected day's events ──
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val dow = selectedDate.dayOfWeek
            Text(
                "${selectedDate.monthValue}월 ${selectedDate.dayOfMonth}일 (${dow.koreanShort()})",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = dow.weekendColor() ?: MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 4.dp)
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { haptics.tap(); showAddDialog = true }) { Text("+ 일정 추가") }
        }

        // Timed events first (sorted by time), untimed events last.
        val dayEvents = eventsByDate[selectedDate].orEmpty().sortedBy { it.time.ifBlank { "99:99" } }
        val accent = paint.colorOf(selectedDate)

        Column(Modifier.animateContentSize()) {
            if (dayEvents.isEmpty()) {
                Text(
                    "아직 일정이 없어요. \"+ 일정 추가\"로 적어 보세요.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 16.dp)
                )
            } else {
                dayEvents.forEach { e ->
                    EventRow(
                        event = e,
                        accent = accent,
                        authorName = nameOf(e),
                        authorColor = colorOf(e.authorUid),
                        onDelete = {
                            haptics.reject()
                            onDeleteEvent(e)
                        }
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showAddDialog) {
        AddEventDialog(
            date = selectedDate,
            onDismiss = { showAddDialog = false },
            onSave = { time, title ->
                haptics.confirm()
                onAddEvent(selectedDate, time, title)
                showAddDialog = false
            },
            onInvalid = { haptics.reject() }
        )
    }
}
