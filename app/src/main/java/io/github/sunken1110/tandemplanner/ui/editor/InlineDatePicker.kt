package io.github.sunken1110.tandemplanner.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sunken1110.tandemplanner.currentDate
import io.github.sunken1110.tandemplanner.ui.common.MiniMonthPicker
import io.github.sunken1110.tandemplanner.ui.common.koreanDayLabel
import io.github.sunken1110.tandemplanner.ui.common.miniDayNumberColor
import io.github.sunken1110.tandemplanner.ui.effects.rememberHaptics
import java.time.LocalDate
import java.time.YearMonth

/**
 * Small month grid shown inline in [EventEditorSheet] when the date is tapped,
 * so an event can be moved to any day, not only one day at a time.
 *
 * Built on [MiniMonthPicker]: six rows with the neighboring months' days dimmed,
 * sliding between months. Today has a ring, the selected day a filled circle.
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
    val today = currentDate()
    var month by remember { mutableStateOf(YearMonth.from(selected)) }
    // Follow the selection when it moves into another month from outside the grid
    // (the sheet's ‹ › stepper), so the selected day never drops out of view.
    LaunchedEffect(selected) { month = YearMonth.from(selected) }
    val minMonth = minDate?.let { YearMonth.from(it) }
    val maxMonth = maxDate?.let { YearMonth.from(it) }

    MiniMonthPicker(
        month = month,
        onMonthChange = { month = it },
        modifier = modifier,
        canGoBack = minMonth == null || month.isAfter(minMonth),
        canGoForward = maxMonth == null || month.isBefore(maxMonth)
    ) { day, _, inMonth ->
        val allowed = (minDate == null || !day.isBefore(minDate)) &&
            (maxDate == null || !day.isAfter(maxDate))
        InlineDayCell(
            day = day,
            inMonth = inMonth,
            allowed = allowed,
            isToday = day == today,
            isSelected = day == selected,
            onClick = { haptics.tap(); onPick(day) }
        )
    }
}

/** A day in [InlineDatePicker]: its number, a ring for today, a filled circle when selected. */
@Composable
private fun InlineDayCell(
    day: LocalDate,
    inMonth: Boolean,
    allowed: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val base = miniDayNumberColor(day, isSelected)
    val textColor = when {
        isSelected -> base
        !allowed -> base.copy(alpha = 0.25f)
        !inMonth -> base.copy(alpha = 0.4f)
        else -> base
    }
    val selectedColor = MaterialTheme.colorScheme.primary
    val ringColor = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier
            .fillMaxSize()
            .semantics {
                contentDescription = day.koreanDayLabel()
                this.selected = isSelected
            }
            .clickable(enabled = allowed, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .then(
                    when {
                        isSelected -> Modifier.background(selectedColor)
                        isToday -> Modifier.border(1.dp, ringColor, CircleShape)
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
