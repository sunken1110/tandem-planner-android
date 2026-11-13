package io.github.sunken1110.tandemplanner.ui.year

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sunken1110.tandemplanner.ui.common.GlyphButton
import io.github.sunken1110.tandemplanner.ui.effects.rememberHaptics

/**
 * "‹ 2026년 ›" above the year grid: steps between the years the calendar can
 * show (last year to next year). While another year is shown, a "올해로" button
 * on the right jumps back; it sits apart from the arrows so they never move.
 *
 * @param years the years that can be shown, in order.
 */
@Composable
fun YearSwitcher(
    year: Int,
    thisYear: Int,
    years: IntRange,
    onYearChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberHaptics()
    // Equal-weight sides keep the arrows centered, and the button gets its own
    // side, so the two can't overlap even with a large system font.
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlyphButton(
                "‹", "이전 해",
                onClick = { haptics.tap(); onYearChange(year - 1) },
                enabled = year - 1 in years
            )
            Text(
                "${year}년",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(min = 64.dp)
                    .semantics { heading() }
            )
            GlyphButton(
                "›", "다음 해",
                onClick = { haptics.tap(); onYearChange(year + 1) },
                enabled = year + 1 in years
            )
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            // The plain (non-RowScope) version: this sits in a Box inside the Row.
            androidx.compose.animation.AnimatedVisibility(visible = year != thisYear, enter = fadeIn(), exit = fadeOut()) {
                OutlinedButton(
                    onClick = { haptics.tap(); onYearChange(thisYear) },
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.heightIn(min = 30.dp)
                ) {
                    Text("올해로", fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
}
