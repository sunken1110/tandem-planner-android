package io.github.sunken1110.tandemplanner.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.github.sunken1110.tandemplanner.data.SharedCalendar
import io.github.sunken1110.tandemplanner.ui.theme.MemberColors
import io.github.sunken1110.tandemplanner.ui.theme.OnSeasonColor
import io.github.sunken1110.tandemplanner.ui.theme.SaturdaySky
import io.github.sunken1110.tandemplanner.ui.theme.SundayRed
import io.github.sunken1110.tandemplanner.ui.theme.UnknownMemberColor
import java.time.DayOfWeek

/** Column headers for a Sunday-first calendar grid. */
val WeekdayHeaders = listOf("일", "월", "화", "수", "목", "금", "토")

/** Short Korean weekday name, e.g. "월" for Monday. */
fun DayOfWeek.koreanShort(): String = listOf("월", "화", "수", "목", "금", "토", "일")[value - 1]

/** Header color for a Sunday-first grid column (0 = Sunday … 6 = Saturday). */
@Composable
fun weekdayHeaderColor(column: Int): Color = when (column) {
    0 -> SundayRed
    6 -> SaturdaySky
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * Day-number color in a Sunday-first grid.
 * Weekends always keep their color; weekdays switch to dark text on filled cells.
 */
@Composable
fun dayNumberColor(column: Int, isPast: Boolean): Color = when (column) {
    0 -> SundayRed
    6 -> SaturdaySky
    else -> if (isPast) OnSeasonColor else MaterialTheme.colorScheme.onSurface
}

/** Accent color for a member, based on join order. */
fun SharedCalendar.colorOf(uid: String): Color =
    MemberColors.getOrElse(memberIndex(uid)) { UnknownMemberColor }
