package io.github.sunken1110.tandemplanner.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sunken1110.tandemplanner.data.Event

/** The year view's bar shows at most this many segments; more events still draw three. */
const val MAX_BAR_SEGMENTS = 3

/**
 * Draws a thin bar along the bottom edge, split into one segment per color
 * (left to right). Used on the tiny year-view cells, where a dot reads as a smudge.
 *
 * Drawn directly in the draw phase rather than as child composables, because
 * the year view has ~365 cells.
 *
 * @param colors segment colors in display order; empty draws nothing.
 */
fun Modifier.eventBar(
    colors: List<Color>,
    height: Dp = 2.dp,
    inset: Dp = 2.dp,
    gap: Dp = 1.dp
): Modifier = if (colors.isEmpty()) this else drawBehind {
    val h = height.toPx()
    val side = inset.toPx()
    val g = gap.toPx()
    val total = size.width - side * 2
    val n = colors.size
    val segment = (total - g * (n - 1)) / n
    val top = size.height - side - h
    val radius = CornerRadius(h / 2)
    colors.forEachIndexed { i, color ->
        drawRoundRect(
            color = color,
            topLeft = Offset(side + i * (segment + g), top),
            size = Size(segment, h),
            cornerRadius = radius
        )
    }
}

/**
 * The year-view bar's segment colors, wrapped so Compose compares them by value:
 * a day only recomposes when its colors actually change.
 */
@Immutable
data class BarSegments(val colors: List<Color>)

/** Segment colors for the year-view bar: authors' colors in display order, at most three. */
fun List<Event>.barColors(colorOf: (String) -> Color): List<Color> =
    take(MAX_BAR_SEGMENTS).map { colorOf(it.authorUid) }

/** The month view previews at most this many titles per day. */
const val MAX_PREVIEW_TITLES = 2

/**
 * Which events a month-view cell previews, from a day's time-sorted events:
 * - both members have events → each member's first event;
 * - only one member does     → that member's first two events.
 * The result keeps time order.
 *
 * Members are told apart by [colorOf] (one color per seat) rather than by raw
 * account id, so events written before and after a reinstall count as the same
 * person.
 */
fun List<Event>.previewEvents(colorOf: (String) -> Color): List<Event> {
    val firstPerAuthor = distinctBy { colorOf(it.authorUid) }
    val picked = if (firstPerAuthor.size >= MAX_PREVIEW_TITLES) firstPerAuthor else this
    return picked.take(MAX_PREVIEW_TITLES)
}

/**
 * Month-view cell preview: up to two titles (see [previewEvents]) as small
 * chips in their authors' colors, plus "+N" for the rest. Titles are clipped,
 * not ellipsized, since only two or three characters fit.
 *
 * @param events the day's events, already sorted by time.
 */
@Composable
fun EventTitleChips(
    events: List<Event>,
    colorOf: (String) -> Color,
    modifier: Modifier = Modifier
) {
    if (events.isEmpty()) return
    val shown = events.previewEvents(colorOf)
    val extra = events.size - shown.size

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        shown.forEachIndexed { i, event ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TitleChip(event.title, colorOf(event.authorUid), Modifier.weight(1f, fill = false))
                // "+N" sits on the last row.
                if (extra > 0 && i == shown.lastIndex) {
                    Text(
                        "+$extra",
                        fontSize = 8.sp,
                        lineHeight = 9.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(3.dp))
                            .padding(horizontal = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TitleChip(title: String, author: Color, modifier: Modifier = Modifier) {
    // Opaque light tint so the chip stays readable on any season color.
    val background = lerp(Color.White, author, 0.18f)
    val text = lerp(author, Color.Black, 0.35f)
    Text(
        title,
        fontSize = 9.sp,
        lineHeight = 10.sp,
        fontWeight = FontWeight.Medium,
        color = text,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        modifier = modifier
            .background(background, RoundedCornerShape(3.dp))
            .padding(horizontal = 2.dp, vertical = 0.5.dp)
    )
}
