package io.github.sunken1110.tandemplanner.ui.common

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import java.time.LocalDate

/**
 * Everything a day cell needs to paint itself for the current year,
 * bundled so it can be passed down the UI tree as a single parameter.
 *
 * @property today     the current date; days up to and including it are "passed".
 * @property dayColors color of each day of the year, index = dayOfYear - 1.
 * @property fill      fill animation progress in "days". Read it only in the draw phase.
 */
@Immutable
class YearPaint(
    val today: LocalDate,
    val dayColors: List<Color>,
    val fill: () -> Float
) {
    val year: Int get() = today.year
    val passedDays: Int get() = today.dayOfYear
    val totalDays: Int get() = dayColors.size

    fun colorOf(date: LocalDate): Color = dayColors[date.dayOfYear - 1]
    fun isPast(date: LocalDate): Boolean = date.dayOfYear <= passedDays
}
