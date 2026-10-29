package io.github.sunken1110.tandemplanner.ui.month

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.Animatable as ColorAnimatable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sunken1110.tandemplanner.data.Event
import io.github.sunken1110.tandemplanner.data.EventDraft
import io.github.sunken1110.tandemplanner.model.Season
import io.github.sunken1110.tandemplanner.ui.common.DayCell
import io.github.sunken1110.tandemplanner.ui.common.EventTitleChips
import io.github.sunken1110.tandemplanner.ui.common.MonthGrid
import io.github.sunken1110.tandemplanner.ui.common.WeekdayHeaderRow
import io.github.sunken1110.tandemplanner.ui.common.YearPaint
import io.github.sunken1110.tandemplanner.ui.common.dayNumberColor
import io.github.sunken1110.tandemplanner.ui.common.koreanShort
import io.github.sunken1110.tandemplanner.ui.common.parseStoredTime
import io.github.sunken1110.tandemplanner.ui.common.weekendColor
import io.github.sunken1110.tandemplanner.ui.editor.EventEditorSheet
import io.github.sunken1110.tandemplanner.ui.effects.rememberHaptics
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import java.time.LocalDate
import java.time.YearMonth

/** Spacing between day cells. */
private val CELL_GAP = 6.dp

/** Day cell corner radius. */
private val CELL_CORNER = 10.dp

/** Gap between a cell and its selection ring (the ring "floats" around the cell). */
private val RING_GAP = 2.dp

/** Selection ring thickness. */
private val RING_WIDTH = 2.dp

/** Space kept above and below the day's events when scrolling them into view. */
private val REVEAL_MARGIN = 12.dp

/** Fade-in time of a newly selected day's event list. */
private const val LIST_FADE_MS = 180

/** How long the selection ring takes to turn from the year view's outline color into its own color. */
private const val RING_INTRO_MS = 550

/** Start the color shift slightly after the month starts expanding. */
private const val RING_INTRO_DELAY_MS = 150

/** After the color shift, the ring's glow fades in over this long. */
private const val RING_GLOW_FADE_MS = 400

/** Ring glide duration when moving to another day. */
private const val RING_GLIDE_MS = 420

/** The landing pop starts this long after the glide begins (just before it ends). */
private const val RING_POP_LEAD_MS = 330

/** How much the ring grows at the peak of the landing pop. */
private const val RING_POP_SCALE = 1.055f

/** Moving light: one lap of [SHINE_SWEEP_MS] every [SHINE_CYCLE_MS], starting at [SHINE_START_MS]. */
private const val SHINE_CYCLE_MS = 5600
private const val SHINE_START_MS = 100
private const val SHINE_SWEEP_MS = 1300

/** Grows the ring slightly and settles it back. */
private suspend fun popRing(scale: Animatable<Float, AnimationVector1D>) {
    scale.animateTo(RING_POP_SCALE, tween(130, easing = FastOutSlowInEasing))
    scale.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
}

/**
 * True when the system "remove animations" setting is on (animator duration
 * scale 0), in which case decorative motion is skipped.
 */
@Composable
private fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** Dark season tone used for today's number badge. */
private fun deepTone(base: Color): Color = lerp(base, Color.Black, 0.45f)

/**
 * Expanded single-month view with a selectable day grid and that day's events.
 *
 * Markers:
 * - Today: the day number sits in a dark, season-toned circle (plus the cell's shimmer).
 * - Selected day: a ring floating 2dp outside the cell (see [SelectionRingPainter]):
 *   a deep season-colored ring with a soft glow in light mode, a neon tube in
 *   dark mode. It glides to the next cell when another day is tapped and lands
 *   with a small pop; every few seconds a light runs around it while its glow
 *   swells. On open its color shifts from the year view's "today" outline, then
 *   the glow fades in and the motion starts. Motion is skipped when the system
 *   "remove animations" setting is on.
 *
 * Tapping a day swaps the list below instantly (with a quick fade) and then
 * scrolls once so the whole list is visible. The list area never shrinks while
 * the month is open, so switching to a shorter day can't yank the page upward.
 *
 * @param sharedScope / animScope enable the expand-from-card transition.
 *        Both may be null when rendered invisibly for JIT warm-up.
 * @param initialSelectedDate day to select on open (e.g. an event just added via quick-add).
 * @param colorOf maps an author UID to that member's accent color.
 * @param nameOf  resolves an event's author display name.
 * @param editorNameOf resolves the display name of the member who edited an event.
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
fun MonthDetail(
    yearMonth: YearMonth,
    paint: YearPaint,
    eventsByDate: Map<LocalDate, List<Event>>,
    sharedScope: SharedTransitionScope?,
    animScope: AnimatedVisibilityScope?,
    initialSelectedDate: LocalDate? = null,
    colorOf: (String) -> Color,
    nameOf: (Event) -> String,
    editorNameOf: (Event) -> String? = { it.editedByName },
    onAddEvent: (EventDraft) -> Unit,
    onUpdateEvent: (Event, EventDraft) -> Unit,
    onDeleteEvent: (Event) -> Unit,
    onBack: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    val season = Season.of(yearMonth.monthValue)
    val daysInMonth = yearMonth.lengthOfMonth()
    val passedInMonth = (1..daysInMonth).count { paint.isPast(yearMonth.atDay(it)) }

    // Initial selection: the requested date, else today in the current month, else the 1st.
    var selectedDay by rememberSaveable(yearMonth.monthValue, initialSelectedDate) {
        val focus = initialSelectedDate?.takeIf { YearMonth.from(it) == yearMonth }
            ?: paint.today.takeIf { YearMonth.from(it) == yearMonth }
        mutableIntStateOf(focus?.dayOfMonth ?: 1)
    }
    val selectedDate = yearMonth.atDay(selectedDay.coerceIn(1, daysInMonth))
    var showAddDialog by remember { mutableStateOf(false) }
    // The event being edited in the sheet, or null.
    var editingEvent by remember { mutableStateOf<Event?>(null) }

    // ── Auto-scroll so the selected day's events are fully visible ──
    // Bumped on each day tap (and on open when a specific date was requested);
    // 0 means "don't scroll", so simply opening a month keeps the grid in view.
    var revealRequest by remember(yearMonth.monthValue, initialSelectedDate) {
        mutableIntStateOf(if (initialSelectedDate?.let { YearMonth.from(it) } == yearMonth) 1 else 0)
    }
    val revealRequester = remember { BringIntoViewRequester() }
    // Natural size of the section (header + list), and the tallest it has been this month.
    var sectionSize by remember { mutableStateOf(IntSize.Zero) }
    var sectionMinHeightPx by remember(yearMonth.monthValue) { mutableIntStateOf(0) }
    val revealMargin = with(density) { REVEAL_MARGIN.toPx() }

    LaunchedEffect(revealRequest) {
        if (revealRequest == 0) return@LaunchedEffect
        // Let the new day's list be composed and measured first (two frames is enough),
        // so we scroll exactly once, straight to the final position.
        repeat(2) { withFrameNanos { } }
        // On open the section may not be laid out yet; wait a few more frames for it.
        var frames = 0
        while (sectionSize.height == 0 && frames++ < 10) withFrameNanos { }
        revealRequester.bringIntoView(
            Rect(0f, -revealMargin, sectionSize.width.toFloat(), sectionSize.height + revealMargin)
        )
    }

    // ── Selection ring: one ring for the whole grid that glides between cells ──
    val firstColumn = yearMonth.atDay(1).dayOfWeek.value % 7 // Sunday = 0
    var gridWidthPx by remember { mutableIntStateOf(0) }
    val gapPx = with(density) { CELL_GAP.toPx() }
    val cellPx = if (gridWidthPx > 0) (gridWidthPx - gapPx * 6) / 7f else 0f
    val slot = firstColumn + selectedDate.dayOfMonth - 1
    val ringTarget = Offset((slot % 7) * (cellPx + gapPx), (slot / 7) * (cellPx + gapPx))
    val ringPos = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var ringPlaced by remember { mutableStateOf(false) }
    // Ring style and colors: solid in light mode, neon in dark mode.
    //
    // Opening sequence, so the year → month handoff reads as one continuous change:
    // 1. The ring starts in the year view's "today" outline color (the theme's text
    //    color: dark in light mode, light in dark mode) and shifts to its own color.
    //    Both ends have similar brightness, so it looks like color seeping in.
    // 2. Only then does the glow fade in (a glow tinted by the start color would
    //    look like a smudge).
    // 3. Only then do the moving light and breathing start, so they don't overlap
    //    with the zoom.
    val ringStyle = ringStyleFor(isSystemInDarkTheme())
    val ringColorsTarget = ringColorsOf(Season.of(selectedDate.monthValue), ringStyle)
    val introColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
    val ringCore = remember { ColorAnimatable(introColor) }
    val ringGlow = remember { ColorAnimatable(introColor) }
    val ringGlowLevel = remember { Animatable(0f) }
    var ringIntroDone by remember { mutableStateOf(false) }
    var ringEffectsReady by remember { mutableStateOf(false) }
    LaunchedEffect(ringColorsTarget) {
        if (!ringIntroDone) {
            ringIntroDone = true
            val spec = tween<Color>(RING_INTRO_MS, delayMillis = RING_INTRO_DELAY_MS)
            launch { ringGlow.animateTo(ringColorsTarget.glow, spec) }
            ringCore.animateTo(ringColorsTarget.core, spec)
            ringGlowLevel.animateTo(1f, tween(RING_GLOW_FADE_MS))
            ringEffectsReady = true
        } else {
            // Later changes (a day in the next season, or a theme switch) just cross-fade.
            launch { ringGlow.animateTo(ringColorsTarget.glow, tween(300)) }
            ringCore.animateTo(ringColorsTarget.core, tween(300))
        }
    }
    val ringPainter = remember { SelectionRingPainter() }

    // "Landing" pop: the ring grows slightly and settles when it arrives.
    val ringScale = remember { Animatable(1f) }
    val reduceMotion = rememberReduceMotion()

    // Re-runs on a new target and on every tap (revealRequest), so tapping the
    // already-selected day still gives a small pop as feedback.
    LaunchedEffect(ringTarget, cellPx, revealRequest) {
        if (cellPx <= 0f) return@LaunchedEffect
        val firstPlacement = !ringPlaced
        if (firstPlacement || reduceMotion) {
            ringPos.snapTo(ringTarget) // first placement: no fly-in from the corner
            ringPlaced = true
        } else {
            // Start the pop just before the glide ends, so both read as one motion.
            launch {
                delay(RING_POP_LEAD_MS.milliseconds)
                popRing(ringScale)
            }
            ringPos.animateTo(ringTarget, tween(RING_GLIDE_MS, easing = FastOutSlowInEasing))
            return@LaunchedEffect
        }
        if (!reduceMotion) popRing(ringScale)
    }

    // Occasional light around the ring (one lap, then rest), with the glow
    // swelling during the lap. The 5.6 s cycle is twice the today-shimmer cycle
    // (2.8 s), and the lap runs while the shimmer is resting, so the two effects
    // never glow at the same time.
    val shine = if (reduceMotion) null else rememberInfiniteTransition(label = "ringShine").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = SHINE_CYCLE_MS
                0f at SHINE_START_MS using LinearEasing
                1f at SHINE_START_MS + SHINE_SWEEP_MS
                1f at SHINE_CYCLE_MS
            },
            RepeatMode.Restart
        ),
        label = "ringShineProgress"
    )

    val sharedModifier = if (sharedScope != null && animScope != null) {
        with(sharedScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "month-${yearMonth.monthValue}"),
                animatedVisibilityScope = animScope
            )
        }
    } else Modifier

    Column(
        Modifier
            .fillMaxSize()
            .then(sharedModifier)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .verticalScroll(rememberScrollState())
            .padding(8.dp)
    ) {
        // ── Navigation ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { haptics.tap(); onBack() }) { Text("‹ 1년 보기") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { haptics.tap(); onPrev() }, enabled = yearMonth.monthValue > 1) {
                Text("‹", fontSize = 20.sp)
            }
            Text("${yearMonth.monthValue}월", fontSize = 20.sp, fontWeight = FontWeight.Medium)
            TextButton(onClick = { haptics.tap(); onNext() }, enabled = yearMonth.monthValue < 12) {
                Text("›", fontSize = 20.sp)
            }
        }
        Text(
            "${season.label} · ${passedInMonth} / ${daysInMonth}일 지남",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp, bottom = 12.dp)
        )

        // ── Day grid (with the selection ring drawn on top) ──
        WeekdayHeaderRow(fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .onSizeChanged { gridWidthPx = it.width }
                .drawWithContent {
                    drawContent()
                    if (ringPlaced && cellPx > 0f) {
                        val out = RING_GAP.toPx() + RING_WIDTH.toPx() / 2
                        val half = (cellPx / 2 + out) * ringScale.value
                        val center = ringPos.value + Offset(cellPx / 2, cellPx / 2)
                        val topLeft = center - Offset(half, half)
                        val ringSize = Size(half * 2, half * 2)
                        val corner = CornerRadius((CELL_CORNER.toPx() + out) * ringScale.value)
                        ringPainter.draw(
                            scope = this,
                            topLeft = topLeft,
                            size = ringSize,
                            corner = corner,
                            width = RING_WIDTH.toPx(),
                            colors = RingColors(core = ringCore.value, glow = ringGlow.value),
                            style = ringStyle,
                            lap = if (ringEffectsReady) shine?.value else null,
                            glowLevel = ringGlowLevel.value
                        )
                    }
                }
        ) {
            MonthGrid(yearMonth, gap = CELL_GAP) { date, column ->
                val isPast = paint.isPast(date)
                val isToday = date == paint.today
                DayCell(
                    date = date,
                    paint = paint,
                    corner = CELL_CORNER,
                    borderWidth = 2.dp,
                    showTodayOutline = false, // the number badge marks today here
                    modifier = Modifier
                        .clip(RoundedCornerShape(CELL_CORNER))
                        .clickable {
                            haptics.tap()
                            selectedDay = date.dayOfMonth
                            revealRequest++
                        }
                ) {
                    DayNumber(
                        day = date.dayOfMonth,
                        color = dayNumberColor(column = column, isPast = isPast),
                        isPast = isPast,
                        todayBadge = if (isToday) deepTone(paint.colorOf(date)) else null,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 3.dp, top = 3.dp)
                    )
                    // Up to two titles in their authors' colors, plus "+N" for the rest.
                    EventTitleChips(
                        events = eventsByDate[date].orEmpty(),
                        colorOf = colorOf,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = 3.dp, end = 3.dp, bottom = 3.dp)
                    )
                }
            }
        }

        // ── Selected day's events ──
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .bringIntoViewRequester(revealRequester)
                // Never shrink while this month is open: a shorter list leaves space
                // below instead of pulling the page up.
                .heightIn(min = with(density) { sectionMinHeightPx.toDp() })
        ) {
            Column(
                Modifier.onSizeChanged {
                    sectionSize = it
                    if (it.height > sectionMinHeightPx) sectionMinHeightPx = it.height
                }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val dow = selectedDate.dayOfWeek
                    Text(
                        "${selectedDate.monthValue}월 ${selectedDate.dayOfMonth}일 (${dow.koreanShort()})",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = dow.weekendColor() ?: MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { haptics.tap(); showAddDialog = true }) { Text("+ 일정 추가") }
                }

                // Already sorted: all-day events first, then timed events by time.
                val dayEvents = eventsByDate[selectedDate].orEmpty()
                val accent = paint.colorOf(selectedDate)

                // A new day's list replaces the old one at once (so the final size is
                // known immediately) and fades in quickly.
                key(selectedDate) {
                    val fade = remember { Animatable(0f) }
                    LaunchedEffect(Unit) { fade.animateTo(1f, tween(LIST_FADE_MS)) }
                    Column(Modifier.graphicsLayer { alpha = fade.value }) {
                        if (dayEvents.isEmpty()) {
                            Text(
                                "아직 일정이 없어요. \"+ 일정 추가\"로 적어 보세요.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 16.dp)
                            )
                        } else {
                            dayEvents.forEach { e ->
                                EventRow(
                                    event = e,
                                    accent = accent,
                                    authorName = nameOf(e),
                                    authorColor = colorOf(e.authorUid),
                                    editorName = editorNameOf(e),
                                    onClick = {
                                        haptics.tap()
                                        editingEvent = e
                                    },
                                    onDelete = {
                                        haptics.reject()
                                        onDeleteEvent(e)
                                    }
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        EventEditorSheet(
            initial = EventDraft(date = selectedDate, time = null, title = ""),
            history = remember(eventsByDate) { eventsByDate.values.flatten() },
            onDismiss = { showAddDialog = false },
            onSave = { draft ->
                onAddEvent(draft)
                showAddDialog = false
            }
        )
    }

    editingEvent?.let { event ->
        key(event.id) {
            EventEditorSheet(
                initial = EventDraft(date = event.date, time = parseStoredTime(event.time), title = event.title),
                // Leave the event itself out, so it doesn't "suggest" its own old time.
                history = remember(eventsByDate, event.id) {
                    eventsByDate.values.flatten().filter { it.id != event.id }
                },
                onDismiss = { editingEvent = null },
                onSave = { draft ->
                    onUpdateEvent(event, draft)
                    editingEvent = null
                },
                saveLabel = "수정"
            )
        }
    }
}

/**
 * Day number in the cell's top-left corner.
 * - Today: white number in a dark, season-toned circle.
 * - Past days: a very faint white badge keeps weekend colors readable on filled cells.
 */
@Composable
private fun DayNumber(
    day: Int,
    color: Color,
    isPast: Boolean,
    todayBadge: Color?,
    modifier: Modifier = Modifier,
    badgeSize: Dp = 16.dp
) {
    if (todayBadge != null) {
        Box(
            modifier
                .size(badgeSize)
                .background(todayBadge, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("$day", fontSize = 10.sp, lineHeight = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    } else {
        Text(
            "$day",
            fontSize = 10.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.Medium,
            color = color,
            modifier = modifier
                .padding(start = 1.dp, top = 1.dp)
                .then(
                    if (isPast) Modifier.background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(3.dp))
                    else Modifier
                )
                .padding(horizontal = 2.dp)
        )
    }
}
