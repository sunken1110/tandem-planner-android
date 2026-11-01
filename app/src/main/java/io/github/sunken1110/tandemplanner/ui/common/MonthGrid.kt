package io.github.sunken1110.tandemplanner.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import java.time.LocalDate
import java.time.YearMonth

/** Sunday-first weekday labels, with weekend colors. */
@Composable
fun WeekdayHeaderRow(fontSize: TextUnit) {
    Row(Modifier.fillMaxWidth()) {
        WeekdayHeaders.forEachIndexed { column, label ->
            Text(
                label,
                modifier = Modifier.weight(1f),
                fontSize = fontSize,
                textAlign = TextAlign.Center,
                color = weekdayHeaderColor(column)
            )
        }
    }
}

/**
 * Sunday-first grid of square cells for one month.
 * Leading and trailing blanks are left empty; [cell] is called for each real day.
 *
 * @param gap spacing between cells, both horizontally and vertically.
 * @param cell content for a day; `column` is 0 (Sunday) … 6 (Saturday).
 */
@Composable
fun MonthGrid(
    yearMonth: YearMonth,
    gap: Dp,
    cell: @Composable BoxScope.(date: LocalDate, column: Int) -> Unit
) {
    val firstColumn = yearMonth.atDay(1).dayOfWeek.value % 7 // Sunday = 0
    val daysInMonth = yearMonth.lengthOfMonth()
    val rows = (firstColumn + daysInMonth + 6) / 7

    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        for (row in 0 until rows) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                for (column in 0 until 7) {
                    val day = row * 7 + column - firstColumn + 1
                    Box(Modifier.weight(1f).aspectRatio(1f)) {
                        if (day in 1..daysInMonth) cell(yearMonth.atDay(day), column)
                    }
                }
            }
        }
    }
}
