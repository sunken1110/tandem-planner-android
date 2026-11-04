package io.github.sunken1110.tandemplanner.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import java.time.LocalDate
import java.time.temporal.ChronoUnit

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
 * account id, so events written before and after reinstalling count as the same
 * person.
 */
fun List<Event>.previewEvents(colorOf: (String) -> Color): List<Event> {
    val firstPerAuthor = distinctBy { colorOf(it.authorUid) }
    val picked = if (firstPerAuthor.size >= MAX_PREVIEW_TITLES) firstPerAuthor else this
    return picked.take(MAX_PREVIEW_TITLES)
}

/** Rows a month-view cell has for event marks: band lanes and title chips share them. */
const val CELL_SLOTS = 2

/** Height of one row (a title chip or a multi-day band) in a month-view cell. */
val SLOT_HEIGHT = 11.dp

/** Vertical space between the rows. */
val SLOT_GAP = 1.dp

/** Distance of the rows from the cell's sides and bottom edge. */
val SLOT_INSET = 3.dp

/**
 * Top of row [slot] (0 = upper) inside a cell of height [cellHeight]. The rows sit
 * at the bottom of the cell, so this is where both the title chips and the
 * multi-day bands are drawn.
 */
fun slotTop(slot: Int, cellHeight: Dp): Dp =
    cellHeight - SLOT_INSET - SLOT_HEIGHT * (CELL_SLOTS - slot) - SLOT_GAP * (CELL_SLOTS - 1 - slot)

/** Light, opaque tint of an author's color, readable on any season color. */
fun chipBackground(author: Color, strength: Float = 0.18f): Color = lerp(Color.White, author, strength)

/** Text color on [chipBackground]. */
fun chipText(author: Color): Color = lerp(author, Color.Black, 0.35f)

/**
 * Month-view cell preview of one-day events: titles as small chips in their
 * authors' colors (see [previewEvents]), plus "+N" for the rest.
 *
 * The cell has [CELL_SLOTS] rows at its bottom. Rows taken by multi-day bands
 * ([blockedSlots]; the bands themselves are drawn over the whole grid) are left
 * empty, and the chips fill the free rows, lowest first, so a single chip sits at
 * the bottom as before. Titles are clipped, not ellipsized, since only two or
 * three characters fit.
 *
 * @param events the day's one-day events, already sorted by time.
 * @param hiddenCount further events not drawn at all (multi-day events that got
 *        no row), added to the "+N" count.
 * @param modifier should place this at the cell's bottom, inset by [SLOT_INSET].
 */
@Composable
fun EventTitleChips(
    events: List<Event>,
    colorOf: (String) -> Color,
    modifier: Modifier = Modifier,
    blockedSlots: Set<Int> = emptySet(),
    hiddenCount: Int = 0
) {
    val freeSlots = (0 until CELL_SLOTS).filter { it !in blockedSlots }
    val shown = events.previewEvents(colorOf).take(freeSlots.size.coerceAtMost(MAX_PREVIEW_TITLES))
    val extra = events.size - shown.size + hiddenCount
    if (shown.isEmpty() && (extra == 0 || freeSlots.isEmpty())) return
    // Chips take the lowest free rows, in time order; "+N" alone takes the lowest free row.
    val chipSlots = freeSlots.takeLast(shown.size.coerceAtLeast(1))

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SLOT_GAP)) {
        for (slot in 0 until CELL_SLOTS) {
            val index = chipSlots.indexOf(slot)
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(SLOT_HEIGHT),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (index < 0) return@Row
                shown.getOrNull(index)?.let { event ->
                    TitleChip(event.title, colorOf(event.authorUid), Modifier.weight(1f, fill = false))
                }
                // "+N" sits on the last chip's row.
                if (extra > 0 && index == chipSlots.lastIndex) MoreBadge(extra)
            }
        }
    }
}

/** "+N" for events a cell has no room for. */
@Composable
fun MoreBadge(count: Int, modifier: Modifier = Modifier) {
    Text(
        "+$count",
        fontSize = 8.sp,
        lineHeight = 9.sp,
        fontWeight = FontWeight.Medium,
        color = Color.White,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(3.dp))
            .padding(horizontal = 2.dp)
    )
}

@Composable
private fun TitleChip(title: String, author: Color, modifier: Modifier = Modifier) {
    Text(
        title,
        fontSize = 9.sp,
        lineHeight = 10.sp,
        fontWeight = FontWeight.Medium,
        color = chipText(author),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        modifier = modifier
            .background(chipBackground(author), RoundedCornerShape(3.dp))
            .padding(horizontal = 2.dp, vertical = 0.5.dp)
    )
}

// ─────────────────────────────────────────────
// D-day badge (the year view's card and the home-screen widget)
// ─────────────────────────────────────────────

/**
 * How strongly a D-day badge is colored, by how close the event is. Each step
 * mixes more of the author's color into the badge (`tint`); [NOW] is fully filled.
 */
enum class DdayStep(val tint: Float) {
    /** D-8 and further. */
    FAR(0.10f),
    /** D-7 … D-2. */
    NEAR(0.22f),
    /** D-1. */
    TOMORROW(0.42f),
    /** D-DAY, or a multi-day event under way. */
    NOW(1f);

    companion object {
        fun of(daysUntil: Long, ongoing: Boolean): DdayStep = when {
            ongoing || daysUntil <= 0L -> NOW
            daysUntil == 1L -> TOMORROW
            daysUntil <= 7L -> NEAR
            else -> FAR
        }
    }
}

/** Dark-mode base the badge tint is mixed into (a near-black with a hint of violet). */
private val DarkBadgeBase = Color(0xFF1F1D24)

/**
 * Badge background and text colors for an author's color at a [step].
 * - Light mode: white mixed with the author's color; text is the author's color, darkened.
 * - Dark mode: a dark base mixed with the author's color (a little more, so the
 *   steps stay apart on dark); text is the author's color, lightened.
 * - [DdayStep.NOW]: the author's color itself with white text, in both modes.
 */
fun ddayBadgeColors(author: Color, step: DdayStep, dark: Boolean): Pair<Color, Color> = when {
    step == DdayStep.NOW -> author to Color.White
    dark -> lerp(DarkBadgeBase, author, step.tint + 0.08f) to lerp(author, Color.White, 0.55f)
    else -> lerp(Color.White, author, step.tint) to lerp(author, Color.Black, 0.35f)
}

/** "D-12", "D-DAY" or "진행 중". */
fun ddayText(event: Event, today: LocalDate): String {
    val daysUntil = ChronoUnit.DAYS.between(today, event.date)
    return when {
        event.isMultiDay && daysUntil < 0 -> "진행 중"
        daysUntil <= 0L -> "D-DAY"
        else -> "D-$daysUntil"
    }
}

/** The badge step for an event as seen on [today]. */
fun ddayStepOf(event: Event, today: LocalDate): DdayStep {
    val daysUntil = ChronoUnit.DAYS.between(today, event.date)
    return DdayStep.of(daysUntil, ongoing = event.isMultiDay && daysUntil < 0)
}

/**
 * When an event is, for the D-day card: "오늘 · 오후 7:00", "내일 · 하루 종일",
 * "11월 3일 (화) · 오후 2:00", "11월 3일 (화) ~ 6일 (금) · 4일", or for a trip
 * under way "11월 3일 ~ 6일 · 2/4일째".
 */
fun eventWhenText(event: Event, today: LocalDate): String {
    val daysUntil = ChronoUnit.DAYS.between(today, event.date)
    return when {
        event.isMultiDay && daysUntil < 0 -> {
            val nth = ChronoUnit.DAYS.between(event.date, today) + 1
            "${periodLabel(event.date, event.lastDate)} · $nth/${event.dayCount}일째"
        }
        event.isMultiDay -> periodLabel(event.date, event.lastDate, weekdays = true) + " · ${event.dayCount}일"
        else -> {
            val day = when (daysUntil) {
                0L -> "오늘"
                1L -> "내일"
                else -> "${event.date.monthValue}월 ${event.date.dayOfMonth}일 (${event.date.dayOfWeek.koreanShort()})"
            }
            day + " · " + if (event.time.isBlank()) "하루 종일" else displayTime(event.time)
        }
    }
}

/**
 * A period such as "11월 3일 ~ 6일", or "10월 30일 ~ 11월 2일" across months
 * (with weekdays: "11월 3일 (화) ~ 6일 (금)").
 */
fun periodLabel(from: LocalDate, to: LocalDate, weekdays: Boolean = false): String {
    fun dow(d: LocalDate) = if (weekdays) " (${d.dayOfWeek.koreanShort()})" else ""
    val end = if (to.year == from.year && to.monthValue == from.monthValue) "${to.dayOfMonth}일"
    else "${to.monthValue}월 ${to.dayOfMonth}일"
    return "${from.monthValue}월 ${from.dayOfMonth}일${dow(from)} ~ $end${dow(to)}"
}
