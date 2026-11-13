package io.github.sunken1110.tandemplanner.ui.common

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import io.github.sunken1110.tandemplanner.FADE_DAYS
import io.github.sunken1110.tandemplanner.model.dayColor
import java.time.LocalDate

/**
 * Everything a day cell needs to paint itself for one year (the one shown,
 * which may be last or next year), bundled so it can be passed down the UI
 * tree as a single parameter.
 *
 * @property today     the current date; days up to and including it are "passed".
 * @property year      the year being painted; [dayColors] holds its days.
 * @property dayColors color of each day of [year], index = dayOfYear - 1.
 * @property fill      fill animation progress in "days" of this year. Read it only in the draw phase.
 */
@Immutable
class YearPaint(
    val today: LocalDate,
    val year: Int,
    val dayColors: List<Color>,
    val fill: () -> Float
) {
    /** Days passed in the current year (today's day of the year). */
    val passedDays: Int get() = today.dayOfYear
    val totalDays: Int get() = dayColors.size

    /** The day's season color; days outside [year] are computed on the spot. */
    fun colorOf(date: LocalDate): Color =
        if (date.year == year) dayColors[date.dayOfYear - 1] else dayColor(date)

    fun isPast(date: LocalDate): Boolean = !date.isAfter(today)

    /**
     * How far the day is colored in: 0 = not reached yet, 1 = fully colored.
     * Days of the current year follow the fill animation; earlier years are
     * fully colored. Reads [fill], so call it only in the draw phase.
     */
    fun fillProgress(date: LocalDate): Float = when {
        date.isAfter(today) -> 0f
        date.year < today.year -> 1f
        else -> ((fill() - (date.dayOfYear - 1)) / FADE_DAYS).coerceIn(0f, 1f)
    }
}
