package io.github.sunken1110.tandemplanner.ui.year

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import android.content.Context
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.core.content.edit
import kotlin.math.roundToInt
import java.util.Locale
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.AnimationSpec
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import io.github.sunken1110.tandemplanner.ui.effects.rememberHaptics
import io.github.sunken1110.tandemplanner.ui.effects.rememberReduceMotion

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
 * Tapping the count switches between days passed ("310일 지남") and days left
 * ("55일 남음"); the bar follows, and the choice is remembered on this phone.
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

    val context = LocalContext.current
    val haptics = rememberHaptics()
    val reduceMotion = rememberReduceMotion()
    val prefs = remember(context) { context.getSharedPreferences(HEADER_PREFS, Context.MODE_PRIVATE) }
    var showLeft by rememberSaveable { mutableStateOf(prefs.getBoolean(KEY_SHOW_LEFT, false)) }
    // The digits roll only right after a switch. Set in the tap itself, so it is
    // already true in the frame where the numbers change; cleared once they've rolled.
    var rolling by remember { mutableStateOf(false) }
    var switches by remember { mutableIntStateOf(0) }
    val toggle = {
        haptics.tick()
        rolling = !reduceMotion
        switches++
        showLeft = !showLeft
        prefs.edit { putBoolean(KEY_SHOW_LEFT, showLeft) }
    }
    LaunchedEffect(switches) {
        delay(ROLL_WINDOW)
        rolling = false
    }

    // Tapping anywhere on the year, count or bar switches; no ripple, the numbers moving is the feedback.
    Column(
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClickLabel = if (showLeft) "지난 날 보기" else "남은 날 보기",
            onClick = toggle
        )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${paint.year}년", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            ModeDots(showLeft)
        }
        DayCount(
            totalDays = paint.totalDays,
            shownCount = shownCount,
            showLeft = showLeft,
            rolling = rolling
        )
        Spacer(Modifier.height(8.dp))

        GradientProgressBar(paint, showLeft, reduceMotion)
    }
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
 *
 * With [showLeft], the passed part fades back and the days still to come are
 * revealed instead, growing in from the right end, so the bar shows what the
 * number says.
 */
@Composable
private fun GradientProgressBar(paint: YearPaint, showLeft: Boolean, reduceMotion: Boolean) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val leftness = animateFloatAsState(
        targetValue = if (showLeft) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else tween(SWITCH_MS, easing = FastOutSlowInEasing),
        label = "barLeft"
    )
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
                    val l = leftness.value
                    if (filledWidth > 0f) {
                        // Gradient spans the full width; only the elapsed part is revealed.
                        clipRect(right = filledWidth) {
                            drawRoundRect(brush = brush, cornerRadius = radius, alpha = 1f - PASSED_FADE * l)
                        }
                    }
                    if (l > 0f) {
                        // The rest of the year, revealed from the right end inward.
                        val start = size.width - (size.width - filledWidth) * l
                        clipRect(left = start) { drawRoundRect(brush = brush, cornerRadius = radius) }
                    }
                }
            }
    )
}

/** How long the passed/left switch takes. */
private const val SWITCH_MS = 450

/** How long the word ("일 지남" / "일 남음") takes to turn over. */
private const val WORD_MS = 350

/** Quick to start, gentle to settle: the roll feels snappy, then lands softly. */
private val RollEasing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

/** How far the passed part of the bar fades back while showing days left. */
private const val PASSED_FADE = 0.8f

/** After a switch, changes roll for this long. */
private val ROLL_WINDOW = SWITCH_MS.milliseconds

private const val HEADER_PREFS = "year_header"
private const val KEY_SHOW_LEFT = "show_left"

/**
 * "310일 지남 / 365일 (84.9%)", or with [showLeft] "55일 남음 / 365일 (15.1%)".
 * Its own composable, so the count animating during the fill doesn't recompose
 * the rest of the header.
 *
 * Switching rolls the numbers like an odometer ([Odometer]) and turns the word
 * over with them; while the fill counts up at launch the digits just change.
 *
 * @param rolling true right after a switch.
 */
@Composable
private fun DayCount(totalDays: Int, shownCount: () -> Int, showLeft: Boolean, rolling: Boolean) {
    val passed = shownCount()
    val count = if (showLeft) totalDays - passed else passed
    val percent = String.format(Locale.ROOT, "%.1f", count * 100f / totalDays)
    val big = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Medium)
    val small = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

    // TalkBack reads one sentence: each digit wheel holds all ten digits, which
    // would otherwise be read out one by one.
    Row(
        Modifier.clearAndSetSemantics {
            contentDescription = "${count}일 ${if (showLeft) "남음" else "지남"}, ${totalDays}일 중 ${percent}%"
        },
        verticalAlignment = Alignment.Bottom
    ) {
        // A year has at most 366 days: three places.
        Odometer(count.toString(), places = 3, style = big, rolling = rolling)
        TurnOver(if (showLeft) "일 남음" else "일 지남", up = showLeft, rolling = rolling) { Text(it, style = big) }
        Spacer(Modifier.width(6.dp))
        Row(Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
            Text("/ ${totalDays}일 (", style = small)
            // "100.0" at most: five places.
            Odometer(percent, places = 5, style = small, rolling = rolling)
            Text("%)", style = small)
        }
    }
}

/**
 * [text] with each digit on its own 0–9 wheel, right-aligned in [places]: when
 * a digit changes, its wheel rolls to the new one (passing the digits between,
 * like an odometer); places that empty out (310 → 55) roll to 0 and fold away.
 * Other characters (".") stay put. Without [rolling] the wheels jump.
 */
@Composable
private fun Odometer(text: String, places: Int, style: TextStyle, rolling: Boolean) {
    val padded = text.padStart(places, ' ')
    // Equal-width digits, so the number doesn't wobble while rolling.
    val digitStyle = style.copy(fontFeatureSettings = "tnum")
    Row {
        padded.forEach { c ->
            when {
                c.isDigit() -> DigitWheel(c.digitToInt(), digitStyle, rolling)
                c == ' ' -> DigitWheel(null, digitStyle, rolling)
                else -> Text(c.toString(), style = digitStyle)
            }
        }
    }
}

/**
 * One digit place: the digits 0–9 stacked in a column, showing one at a time
 * through a window one line tall. [digit] null = an unused place, folded to
 * no width. The column's position is read only while laying out, so a rolling
 * wheel doesn't recompose anything.
 */
@Composable
private fun DigitWheel(digit: Int?, style: TextStyle, rolling: Boolean) {
    val spec: AnimationSpec<Float> = if (rolling) tween(SWITCH_MS, easing = RollEasing) else snap()
    val position = animateFloatAsState((digit ?: 0).toFloat(), spec, label = "wheel")
    val shown = animateFloatAsState(if (digit == null) 0f else 1f, spec, label = "place")
    Column(
        Modifier
            .clipToBounds()
            .layout { measurable, constraints ->
                val wheel = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                val line = wheel.height / 10
                val fold = shown.value
                layout((wheel.width * fold).roundToInt(), line) {
                    wheel.placeWithLayer(0, (-position.value * line).roundToInt()) { alpha = fold }
                }
            }
    ) {
        for (d in 0..9) Text(d.toString(), style = style)
    }
}

/**
 * Shows [value]; while [rolling], a change turns over vertically (the new one
 * comes up from below when [up], down from above otherwise), otherwise it
 * just changes.
 */
@Composable
private fun <T> TurnOver(value: T, up: Boolean, rolling: Boolean, content: @Composable (T) -> Unit) {
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            if (!rolling) {
                EnterTransition.None togetherWith ExitTransition.None using null
            } else {
                val spec = tween<IntOffset>(WORD_MS, easing = RollEasing)
                slideInVertically(spec) { if (up) it else -it } togetherWith
                    slideOutVertically(spec) { if (up) -it else it } using
                    SizeTransform(clip = true)
            }
        },
        label = "turnOver"
    ) { content(it) }
}

/** Two small marks by the year: which of "지남" / "남음" is showing (the wide one). */
@Composable
private fun ModeDots(showLeft: Boolean) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(!showLeft, showLeft).forEach { on ->
            val width by animateDpAsState(if (on) 12.dp else 5.dp, tween(250), label = "dot")
            Box(
                Modifier
                    .size(width = width, height = 5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color.copy(alpha = if (on) 0.7f else 0.25f))
            )
        }
    }
}
