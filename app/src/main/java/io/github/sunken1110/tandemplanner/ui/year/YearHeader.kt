package io.github.sunken1110.tandemplanner.ui.year

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sunken1110.tandemplanner.data.Event
import io.github.sunken1110.tandemplanner.model.Season
import io.github.sunken1110.tandemplanner.model.seasonEnd
import io.github.sunken1110.tandemplanner.ui.common.YearPaint
import io.github.sunken1110.tandemplanner.ui.common.DdayStep
import io.github.sunken1110.tandemplanner.ui.common.ddayBadgeColors
import io.github.sunken1110.tandemplanner.ui.common.ddayStepOf
import io.github.sunken1110.tandemplanner.ui.common.ddayText
import io.github.sunken1110.tandemplanner.ui.common.eventWhenText
import androidx.compose.foundation.isSystemInDarkTheme
import java.time.LocalDate
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
 * Top summary: days passed this year, a gradient progress bar, and below it the
 * D-day card of the next event ([NextEventCard]). With nothing coming up, the
 * days left in the current season are shown instead.
 *
 * @param shownCount day count currently displayed (follows the fill animation).
 *        A lambda, so the animating value is read here and doesn't recompose the caller.
 * @param next the next event to look forward to (see `nextUpcoming`), or null.
 */
@Composable
fun YearHeader(
    paint: YearPaint,
    shownCount: () -> Int,
    next: Event? = null,
    nameOf: (Event) -> String = { it.authorName },
    colorOf: (String) -> Color = { Color.Gray },
    onNextClick: (Event) -> Unit = {}
) {
    val today = paint.today
    val season = Season.of(today.monthValue)

    Text("${paint.year}년", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    DayCount(totalDays = paint.totalDays, shownCount = shownCount)
    Spacer(Modifier.height(8.dp))

    GradientProgressBar(paint)
    Spacer(Modifier.height(10.dp))

    // Cross-fades when the next event changes (a new event, or one passing).
    AnimatedContent(
        targetState = next,
        contentKey = { it?.id },
        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
        label = "nextEvent"
    ) { event ->
        if (event != null) {
            NextEventCard(
                event = event,
                today = today,
                author = nameOf(event),
                color = colorOf(event.authorUid),
                onClick = { onNextClick(event) }
            )
        } else {
            SeasonLine(today, season)
        }
    }
}

/** "가을 · 가을이 29일 남았어요", shown when no event is coming up. */
@Composable
private fun SeasonLine(today: LocalDate, season: Season) {
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
}

/**
 * D-day card for the next event: a bar in the author's color, the title, when
 * it is and who wrote it, and a D-day badge on the right.
 *
 * - Upcoming:  "D-12", tinted with the author's color, more strongly as the day
 *              comes closer (D-8+ faint, D-7…D-2 light, D-1 medium; see [DdayStep]).
 * - Today:     "D-DAY", filled with the author's color.
 * - Multi-day event under way: "진행 중", filled, with "2/4일째" in the details.
 *
 * Tapping it opens the event's day in the month view.
 */
@Composable
private fun NextEventCard(
    event: Event,
    today: LocalDate,
    author: String,
    color: Color,
    onClick: () -> Unit
) {
    val ongoing = event.isMultiDay && event.date.isBefore(today)
    val whenText = eventWhenText(event, today)

    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(color)
        )
        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
        ) {
            Text(
                if (ongoing) "진행 중인 일정" else "다음 일정",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                event.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "$whenText · $author",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        DdayBadge(
            text = ddayText(event, today),
            color = color,
            step = ddayStepOf(event, today),
            modifier = Modifier.padding(end = 12.dp)
        )
    }
}

/**
 * Pill-shaped D-day label, more strongly colored as the day comes closer
 * (see [DdayStep]): D-8+ faint, D-7…D-2 light, D-1 medium, D-DAY / 진행 중 filled.
 */
@Composable
private fun DdayBadge(text: String, color: Color, step: DdayStep, modifier: Modifier = Modifier) {
    val (background, content) = ddayBadgeColors(color, step, dark = isSystemInDarkTheme())
    Text(
        text,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        color = content,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    )
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
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            // Brush and radius are built once per size; only the clip moves per frame.
            .drawWithCache {
                val radius = CornerRadius(size.height / 2, size.height / 2)
                val brush = Brush.horizontalGradient(*stops, startX = 0f, endX = size.width)
                onDrawBehind {
                    drawRoundRect(color = trackColor, cornerRadius = radius)
                    val progress = paint.fill().coerceAtMost(paint.passedDays.toFloat())
                    val filledWidth = size.width * progress / total
                    if (filledWidth > 0f) {
                        // Gradient spans the full width; only the elapsed part is revealed.
                        clipRect(right = filledWidth) { drawRoundRect(brush = brush, cornerRadius = radius) }
                    }
                }
            }
    )
}

/**
 * "N일 지남 / 365일 (x%)". Its own composable, so the count animating during the
 * year fill recomposes only this row, not the whole header.
 */
@Composable
private fun DayCount(totalDays: Int, shownCount: () -> Int) {
    val count = shownCount()
    Row(verticalAlignment = Alignment.Bottom) {
        Text("${count}일 지남", fontSize = 28.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(6.dp))
        Text(
            "/ ${totalDays}일 (${"%.1f".format(count * 100f / totalDays)}%)",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )
    }
}
