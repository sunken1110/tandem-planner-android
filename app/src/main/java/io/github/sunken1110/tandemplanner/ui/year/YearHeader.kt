package io.github.sunken1110.tandemplanner.ui.year

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sunken1110.tandemplanner.model.Season
import io.github.sunken1110.tandemplanner.model.seasonEnd
import io.github.sunken1110.tandemplanner.ui.common.YearPaint
import io.github.sunken1110.tandemplanner.ui.theme.OnSeasonColor
import java.time.temporal.ChronoUnit

/** Banner shown on the first day of a season (Mar 1, Jun 1, Sep 1, Dec 1). */
@Composable
fun SeasonBanner(season: Season, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.horizontalGradient(season.colors))
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("오늘부터 ${season.label}", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = OnSeasonColor)
            Text(season.greeting, fontSize = 13.sp, color = OnSeasonColor)
        }
        TextButton(onClick = onClose) { Text("닫기", color = OnSeasonColor) }
    }
}

/**
 * Top summary: days passed this year, a gradient progress bar,
 * days left in the current season, and the season legend.
 *
 * @param shownCount day count currently displayed (follows the fill animation).
 */
@Composable
fun YearHeader(paint: YearPaint, shownCount: Int) {
    val today = paint.today
    val season = Season.of(today.monthValue)

    Text("${paint.year}년", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(verticalAlignment = Alignment.Bottom) {
        Text("${shownCount}일 지남", fontSize = 28.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(6.dp))
        Text(
            "/ ${paint.totalDays}일 (${"%.1f".format(shownCount * 100f / paint.totalDays)}%)",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )
    }
    Spacer(Modifier.height(8.dp))

    GradientProgressBar(paint)
    Spacer(Modifier.height(10.dp))

    val daysLeft = ChronoUnit.DAYS.between(today, seasonEnd(today))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(season.colors))
                .padding(horizontal = 10.dp, vertical = 3.dp)
        ) {
            Text(season.label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = OnSeasonColor)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            if (daysLeft == 0L) "오늘이 ${season.label}의 마지막 날이에요"
            else "${season.label}이 ${daysLeft}일 남았어요",
            fontSize = 13.sp
        )
    }
    Spacer(Modifier.height(10.dp))

    // Legend: one gradient swatch per season.
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Season.entries.forEach {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(width = 18.dp, height = 10.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Brush.horizontalGradient(it.colors))
                )
                Spacer(Modifier.width(4.dp))
                Text(it.label, fontSize = 12.sp)
            }
        }
    }
}

/**
 * Year progress bar whose fill is the whole year's color flow,
 * revealed up to the current fill position. Animation is read in the draw phase only.
 */
@Composable
private fun GradientProgressBar(paint: YearPaint) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val total = paint.totalDays
    // Sample every 4th day as a gradient stop; plenty for an 8dp bar.
    val stops = remember(paint.dayColors) {
        (0 until total step 4).map { i -> (i / (total - 1f)) to paint.dayColors[i] }.toTypedArray()
    }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
    ) {
        val radius = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(color = trackColor, cornerRadius = radius)
        val progress = paint.fill().coerceAtMost(paint.passedDays.toFloat())
        val filledWidth = size.width * progress / total
        if (filledWidth > 0f) {
            // Gradient spans the full width; only the elapsed part is revealed.
            clipRect(right = filledWidth) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(*stops, startX = 0f, endX = size.width),
                    cornerRadius = radius
                )
            }
        }
    }
}
