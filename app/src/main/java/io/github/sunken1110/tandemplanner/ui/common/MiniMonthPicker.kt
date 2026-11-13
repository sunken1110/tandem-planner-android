package io.github.sunken1110.tandemplanner.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sunken1110.tandemplanner.model.KoreanHolidays
import io.github.sunken1110.tandemplanner.ui.effects.rememberHaptics
import io.github.sunken1110.tandemplanner.ui.effects.rememberReduceMotion
import io.github.sunken1110.tandemplanner.ui.theme.SundayRed
import java.time.LocalDate
import java.time.YearMonth

/** Height of one day cell in a [MiniMonthPicker]. */
val MiniPickerCellHeight = 38.dp

/**
 * Small Sunday-first month calendar used to pick a day: a "‹ 2026년 11월 ›" header,
 * weekday labels and the day grid. Shared by the event editor's date picker and
 * the checklist's "일정에 연결" dialog; each draws its own day cells via [dayCell].
 *
 * The caller owns [month]; the arrows tap a haptic and call [onMonthChange].
 *
 * @param canGoBack / [canGoForward] enable the ‹ › arrows (e.g. to stop at an allowed range).
 * @param arrowFontSize size of the ‹ › glyphs.
 * @param weekdayFontSize size of the "일 월 화 …" labels.
 * @param weekdayPadding space around the weekday row.
 * @param sixWeeks true: always six rows, with the neighboring months' days filling the gaps
 *        (`inMonth = false`). false: only as many rows as the month needs, gaps left blank.
 * @param animateMonthChange slide the grid sideways (or fade, with reduce motion) when the month changes.
 * @param dayCell content of one day, drawn in a full-width-share box [MiniPickerCellHeight] tall;
 *        `column` is 0 (Sunday) … 6 (Saturday).
 */
@Composable
fun MiniMonthPicker(
    month: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
    canGoBack: Boolean = true,
    canGoForward: Boolean = true,
    arrowFontSize: TextUnit = 18.sp,
    weekdayFontSize: TextUnit = 12.sp,
    weekdayPadding: PaddingValues = PaddingValues(vertical = 2.dp),
    sixWeeks: Boolean = true,
    animateMonthChange: Boolean = true,
    dayCell: @Composable BoxScope.(day: LocalDate, column: Int, inMonth: Boolean) -> Unit
) {
    val haptics = rememberHaptics()
    val reduceMotion = rememberReduceMotion()

    Column(modifier) {
        // ── Month header ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlyphButton(
                "‹",
                "이전 달",
                onClick = { haptics.tap(); onMonthChange(month.minusMonths(1)) },
                enabled = canGoBack,
                fontSize = arrowFontSize
            )
            Text(
                "${month.year}년 ${month.monthValue}월",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                // TalkBack reads the new month after "이전 달" / "다음 달".
                modifier = Modifier
                    .weight(1f)
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
            GlyphButton(
                "›",
                "다음 달",
                onClick = { haptics.tap(); onMonthChange(month.plusMonths(1)) },
                enabled = canGoForward,
                fontSize = arrowFontSize
            )
        }

        // ── Weekday headers (the day cells already say their weekday to TalkBack) ──
        Row(Modifier.fillMaxWidth().padding(weekdayPadding)) {
            WeekdayHeaders.forEachIndexed { column, name ->
                Text(
                    name,
                    fontSize = weekdayFontSize,
                    color = weekdayHeaderColor(column),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).clearAndSetSemantics {}
                )
            }
        }

        // ── Day grid ──
        if (animateMonthChange) {
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
                label = "miniMonthPickerMonth"
            ) { shown ->
                MiniMonthDays(shown, sixWeeks, dayCell)
            }
        } else {
            MiniMonthDays(month, sixWeeks, dayCell)
        }
    }
}

/** The day rows of a [MiniMonthPicker]. */
@Composable
private fun MiniMonthDays(
    month: YearMonth,
    sixWeeks: Boolean,
    dayCell: @Composable BoxScope.(day: LocalDate, column: Int, inMonth: Boolean) -> Unit
) {
    val first = month.atDay(1)
    val lead = first.dayOfWeek.value % 7 // Sunday-first: Sunday (7) → 0 leading cells, Monday (1) → 1, ...
    val gridStart = first.minusDays(lead.toLong())
    val weeks = if (sixWeeks) 6 else (lead + month.lengthOfMonth() + 6) / 7

    Column(Modifier.fillMaxWidth()) {
        repeat(weeks) { week ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { column ->
                    val day = gridStart.plusDays((week * 7 + column).toLong())
                    val inMonth = YearMonth.from(day) == month
                    Box(
                        Modifier.weight(1f).height(MiniPickerCellHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        if (inMonth || sixWeeks) dayCell(day, column, inMonth)
                    }
                }
            }
        }
    }
}

/**
 * Day-number color in a [MiniMonthPicker]: on the filled selection circle, else
 * public holidays and Sundays red, Saturdays sky blue, other days the normal text color.
 */
@Composable
fun miniDayNumberColor(day: LocalDate, isSelected: Boolean): Color = when {
    isSelected -> MaterialTheme.colorScheme.onPrimary
    KoreanHolidays.of(day) != null -> SundayRed
    else -> day.dayOfWeek.weekendColor() ?: MaterialTheme.colorScheme.onSurface
}
