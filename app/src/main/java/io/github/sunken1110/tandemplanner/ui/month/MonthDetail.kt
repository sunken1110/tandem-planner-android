package io.github.sunken1110.tandemplanner.ui.month

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sunken1110.tandemplanner.data.Event
import io.github.sunken1110.tandemplanner.model.Season
import io.github.sunken1110.tandemplanner.ui.common.DayCell
import io.github.sunken1110.tandemplanner.ui.common.WeekdayHeaders
import io.github.sunken1110.tandemplanner.ui.common.dayNumberColor
import io.github.sunken1110.tandemplanner.ui.common.koreanShort
import io.github.sunken1110.tandemplanner.ui.common.weekdayHeaderColor
import io.github.sunken1110.tandemplanner.ui.effects.rememberHaptics
import io.github.sunken1110.tandemplanner.ui.theme.SaturdaySky
import io.github.sunken1110.tandemplanner.ui.theme.SundayRed
import java.time.DayOfWeek
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
    today: LocalDate,
    passedDays: Int,
    fillProvider: () -> Float,
    yearColors: List<Color>,
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
    val firstDayOffset = yearMonth.atDay(1).dayOfWeek.value % 7 // Sunday = 0
    val daysInMonth = yearMonth.lengthOfMonth()
    val rows = (firstDayOffset + daysInMonth + 6) / 7
    val passedInMonth = (1..daysInMonth).count { yearMonth.atDay(it).dayOfYear <= passedDays }

    // Default selection: today for the current month, otherwise the 1st.
    var selectedDay by rememberSaveable(yearMonth.monthValue) {
        mutableIntStateOf(if (YearMonth.from(today) == yearMonth) today.dayOfMonth else 1)
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

        // ── Weekday header ──
        Row(Modifier.fillMaxWidth()) {
            WeekdayHeaders.forEachIndexed { i, w ->
                Text(
                    w,
                    modifier = Modifier.weight(1f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = weekdayHeaderColor(i)
                )
            }
        }
        Spacer(Modifier.height(6.dp))

        // ── Day grid ──
        for (r in 0 until rows) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (c in 0 until 7) {
                    val day = r * 7 + c - firstDayOffset + 1
                    Box(Modifier.weight(1f).aspectRatio(1f)) {
                        if (day in 1..daysInMonth) {
                            val date = yearMonth.atDay(day)
                            val doy = date.dayOfYear
                            val isPast = doy <= passedDays
                            val isToday = date == today
                            val dayEvents = eventsByDate[date].orEmpty()

                            DayCell(
                                base = yearColors[doy - 1],
                                dayOfYear = doy,
                                passedDays = passedDays,
                                fillProvider = fillProvider,
                                isToday = isToday,
                                isSelected = day == selectedDay,
                                corner = 10.dp,
                                borderWidth = 2.dp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        haptics.tap()
                                        selectedDay = day
                                    }
                            ) {
                                // Day number, top-left. On filled cells a very faint
                                // white badge keeps weekend colors readable.
                                Box(
                                    Modifier
                                        .align(Alignment.TopStart)
                                        .padding(start = 4.dp, top = 4.dp)
                                        .then(
                                            if (isPast) Modifier.background(
                                                Color.White.copy(alpha = 0.3f),
                                                RoundedCornerShape(3.dp)
                                            ) else Modifier
                                        )
                                        .padding(horizontal = 2.dp)
                                ) {
                                    Text(
                                        "$day",
                                        fontSize = 10.sp,
                                        lineHeight = 10.sp,
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                                        color = dayNumberColor(column = c, isPast = isPast)
                                    )
                                }
                                // Up to three author-colored dots; the white ring
                                // separates them from the cell color.
                                if (dayEvents.isNotEmpty()) {
                                    Row(
                                        Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 5.dp),
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        dayEvents.take(3).forEach { ev ->
                                            Box(
                                                Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.White.copy(alpha = 0.9f))
                                                    .padding(1.dp)
                                                    .clip(CircleShape)
                                                    .background(colorOf(ev.authorUid))
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

        // ── Selected day's events ──
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val dow = selectedDate.dayOfWeek
            Text(
                "${selectedDate.monthValue}월 ${selectedDate.dayOfMonth}일 (${dow.koreanShort()})",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = when (dow) {
                    DayOfWeek.SUNDAY -> SundayRed
                    DayOfWeek.SATURDAY -> SaturdaySky
                    else -> MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.padding(start = 4.dp)
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { haptics.tap(); showAddDialog = true }) { Text("+ 일정 추가") }
        }

        // Timed events first (sorted by time), untimed events last.
        val dayEvents = eventsByDate[selectedDate].orEmpty().sortedBy { it.time.ifBlank { "99:99" } }
        val accent = yearColors[selectedDate.dayOfYear - 1]

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
