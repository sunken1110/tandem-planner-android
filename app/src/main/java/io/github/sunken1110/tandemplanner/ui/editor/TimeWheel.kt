package io.github.sunken1110.tandemplanner.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sunken1110.tandemplanner.ui.common.WheelPicker
import io.github.sunken1110.tandemplanner.ui.effects.rememberHaptics
import java.time.LocalTime

/** Minute wheel step. */
private const val MINUTE_STEP = 5

/**
 * Hour wheel labels for all 24 hours of the day: 12, 1, …, 11 (오전), 12, 1, …, 11 (오후).
 * The wheel's index is the 24-hour value, so scrolling simply walks through
 * the day and crosses between 오전 and 오후 at 12 on its own.
 */
private val Hours = (0 until 24).map { h -> (h % 12).let { if (it == 0) 12 else it }.toString() }

/** Minute labels for a given step: "00", "05", … for 5; "00", "01", … for 1. */
private fun minuteLabels(step: Int): List<String> = (0 until 60 step step).map { "%02d".format(it) }

private val Minutes5 = minuteLabels(MINUTE_STEP)
private val Minutes1 = minuteLabels(1)

private val Periods = listOf("오전", "오후")

/**
 * Time picker: 오전/오후, hour and minute wheels (5-minute steps), editing a
 * 24-hour [LocalTime].
 *
 * - The 오전/오후 wheel can be scrolled, or tapped: tapping either row (including
 *   the selected one) switches to the other, like a toggle. Switching keeps the
 *   hour on the clock face (3 → 3), so the hour wheel doesn't visibly move.
 * - The hour wheel is cyclic and covers the whole day, so scrolling past 오전 11
 *   continues to 오후 12, 1, … and the 오전/오후 wheel follows along.
 * - Rows in every wheel can also be tapped to select them.
 * - Minutes move in 5-minute steps. If the time arrives off that grid (e.g. 10:07
 *   from quick-add, an older event or a history suggestion), the minute wheel
 *   switches to 1-minute steps for this sheet, so it shows the real value instead
 *   of silently rounding it.
 *
 * Changes are reported as transforms applied to the caller's latest value, so
 * two controls changing in the same frame never overwrite each other.
 */
@Composable
fun TimeWheel(
    time: LocalTime,
    onTimeChange: (transform: (LocalTime) -> LocalTime) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberHaptics()
    // 5-minute steps, switching to 1-minute steps (for the rest of this sheet) as
    // soon as an off-grid minute arrives, e.g. from an applied history suggestion.
    // It never switches back, so the wheel doesn't change under the user's finger.
    // (A plain holder rather than state: it is only derived from `time` during
    // composition, and writing state there would trigger an extra recomposition.)
    val stepHolder = remember { intArrayOf(MINUTE_STEP) }
    if (time.minute % stepHolder[0] != 0) stepHolder[0] = 1
    val minuteStep = stepHolder[0]
    val minutes = if (minuteStep == 1) Minutes1 else Minutes5
    val minuteIndex = (time.minute / minuteStep).coerceIn(0, minutes.lastIndex)

    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WheelPicker(
            items = Periods,
            selectedIndex = if (time.hour >= 12) 1 else 0,
            onSelectedIndexChange = { p -> onTimeChange { t -> t.withHour(t.hour % 12 + if (p == 1) 12 else 0) } },
            // Tapping the selected 오전/오후 flips it.
            onCenterTap = {
                haptics.tap()
                onTimeChange { t -> t.withHour((t.hour + 12) % 24) }
            },
            modifier = Modifier.width(72.dp)
        )
        WheelPicker(
            items = Hours,
            selectedIndex = time.hour,
            onSelectedIndexChange = { h -> onTimeChange { t -> t.withHour(h) } },
            cyclic = true,
            modifier = Modifier.width(64.dp)
        )
        Text(":", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // Keyed on the step: a new item list needs a fresh wheel (its positions differ).
        key(minuteStep) {
        WheelPicker(
            items = minutes,
            selectedIndex = minuteIndex,
            onSelectedIndexChange = { m -> onTimeChange { t -> t.withMinute(m * minuteStep) } },
            cyclic = true,
            modifier = Modifier.width(64.dp)
        )
        }
    }
}
