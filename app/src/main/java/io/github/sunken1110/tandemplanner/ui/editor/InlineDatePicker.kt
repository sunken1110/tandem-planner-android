package io.github.sunken1110.tandemplanner.ui.editor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sunken1110.tandemplanner.currentDate
import io.github.sunken1110.tandemplanner.model.KoreanHolidays
import io.github.sunken1110.tandemplanner.ui.common.GlyphButton
import io.github.sunken1110.tandemplanner.ui.common.WeekdayHeaders
import io.github.sunken1110.tandemplanner.ui.common.koreanDayLabel
import io.github.sunken1110.tandemplanner.ui.common.weekdayHeaderColor
import io.github.sunken1110.tandemplanner.ui.common.weekendColor
import io.github.sunken1110.tandemplanner.ui.effects.rememberHaptics
import io.github.sunken1110.tandemplanner.ui.effects.rememberReduceMotion
import io.github.sunken1110.tandemplanner.ui.theme.SundayRed
import java.time.LocalDate
import java.time.YearMonth

/**
 * Small month grid shown inline in [EventEditorSheet] when the date is tapped,
 * so an event can be moved to any day, not only one day at a time.
 *
 * Sunday-first, six rows. Public holidays and Sundays are red, Saturdays sky
 * blue, like the rest of the app. Today has a ring, the selected day a filled circle.
 *
 * @param minDate / [maxDate] days outside this range are dimmed and can't be picked
 *        (used for the last day of a multi-day event).
 */
@Composable
fun InlineDatePicker(
    selected: LocalDate,
    onPick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null
) {
    val haptics = rememberHaptics()
    val reduceMotion = rememberReduceMotion()
    var month by remember { mutableStateOf(YearMonth.from(selected)) }
    val minMonth = minDate?.let { YearMonth.from(it) }
    val maxMonth = maxDate?.let { YearMonth.from(it) }

    Column(modifier) {
        // ── Month header ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlyphButton(
                "‹",
                "이전 달",
                onClick = { haptics.tap(); month = month.minusMonths(1) },
                enabled = minMonth == null || month.isAfter(minMonth),
                fontSize = 18.sp
            )
            Text(
                "${month.year}년 ${month.monthValue}월",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            GlyphButton(
                "›",
                "다음 달",
                onClick = { haptics.tap(); month = month.plusMonths(1) },
                enabled = maxMonth == null || month.isBefore(maxMonth),
                fontSize = 18.sp
            )
        }

        // ── Weekday headers ──
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            WeekdayHeaders.forEachIndexed { column, name ->
                Text(
                    name,
                    fontSize = 12.sp,
                    color = weekdayHeaderColor(column),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clearAndSetSemantics {}
                )
            }
        }

        // ── Day grid (slides sideways when the month changes) ──
        AnimatedContent(
            targetState = month,
            transitionSpec = {
                if (reduceMotion) {
                    ContentTransform(fadeIn(), fadeOut())
                } else {
                    val forward = targetState.isAfter(initialState)
                    (slideInHorizontally { w -> if (forward) w / 3 else -w / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { w -> if (forward) -w / 3 else w / 3 } + fadeOut())
                }
            },
            label = "inlineDatePickerMonth"
        ) { shown ->
            MonthGrid(
                month = shown,
                selected = selected,
                minDate = minDate,
                maxDate = maxDate,
                onPick = { haptics.tap(); onPick(it) }
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    selected: LocalDate,
    minDate: LocalDate?,
    maxDate: LocalDate?,
    onPick: (LocalDate) -> Unit
) {
    val today = currentDate()
    val first = month.atDay(1)
    // Sunday-first: Sunday (7) → 0 leading cells, Monday (1) → 1, ...
    val gridStart = first.minusDays((first.dayOfWeek.value % 7).toLong())
    val selectedColor = MaterialTheme.colorScheme.primary
    val ringColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(Modifier.fillMaxWidth()) {
        repeat(6) { week ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { column ->
                    val day = gridStart.plusDays((week * 7 + column).toLong())
                    val inMonth = YearMonth.from(day) == month
                    val allowed = (minDate == null || !day.isBefore(minDate)) &&
                        (maxDate == null || !day.isAfter(maxDate))
                    val isSelected = day == selected
                    val base: Color = when {
                        isSelected -> MaterialTheme.colorScheme.onPrimary
                        KoreanHolidays.of(day) != null -> SundayRed
                        else -> day.dayOfWeek.weekendColor() ?: MaterialTheme.colorScheme.onSurface
                    }
                    val textColor = when {
                        isSelected -> base
                        !allowed -> base.copy(alpha = 0.25f)
                        !inMonth -> base.copy(alpha = 0.4f)
                        else -> base
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(38.dp)
                            .semantics {
                                contentDescription = day.koreanDayLabel()
                                this.selected = isSelected
                            }
                            .clickable(enabled = allowed) { onPick(day) },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .then(
                                    when {
                                        isSelected -> Modifier.background(selectedColor)
                                        day == today -> Modifier.border(1.dp, ringColor, CircleShape)
                                        else -> Modifier
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                day.dayOfMonth.toString(),
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                color = textColor
                            )
                        }
                    }
                }
            }
        }
    }
}
