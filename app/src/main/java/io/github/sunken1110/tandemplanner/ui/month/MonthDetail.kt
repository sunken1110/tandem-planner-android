package io.github.sunken1110.tandemplanner.ui.month

import io.github.sunken1110.tandemplanner.ui.common.GlyphButton
import androidx.compose.ui.unit.TextUnit
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.Animatable as ColorAnimatable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
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
import io.github.sunken1110.tandemplanner.DEBUG_GLOW_NO_BLUR
import io.github.sunken1110.tandemplanner.data.ChecklistSummary
import io.github.sunken1110.tandemplanner.data.Event
import io.github.sunken1110.tandemplanner.data.EventDraft
import io.github.sunken1110.tandemplanner.model.KoreanHolidays
import io.github.sunken1110.tandemplanner.model.Season
import io.github.sunken1110.tandemplanner.ui.theme.SundayRed
import androidx.compose.ui.text.style.TextOverflow
import io.github.sunken1110.tandemplanner.ui.common.DayCell
import io.github.sunken1110.tandemplanner.ui.common.EventTitleChips
import io.github.sunken1110.tandemplanner.ui.common.MoreBadge
import io.github.sunken1110.tandemplanner.ui.common.SLOT_INSET
import io.github.sunken1110.tandemplanner.ui.common.CELL_SLOTS
import io.github.sunken1110.tandemplanner.ui.common.MonthGrid
import io.github.sunken1110.tandemplanner.ui.common.WeekdayHeaderRow
import io.github.sunken1110.tandemplanner.ui.common.YearPaint
import io.github.sunken1110.tandemplanner.ui.common.dayNumberColor
import io.github.sunken1110.tandemplanner.ui.common.koreanShort
import io.github.sunken1110.tandemplanner.ui.common.parseStoredTime
import io.github.sunken1110.tandemplanner.ui.common.weekendColor
import io.github.sunken1110.tandemplanner.ui.editor.EventEditorSheet
import io.github.sunken1110.tandemplanner.ui.effects.rememberHaptics
import io.github.sunken1110.tandemplanner.ui.effects.rememberReduceMotion
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
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

/** Dark mode: how long the neon glow takes to fade in around the lit center. */
private const val RING_GLOW_FADE_MS = 400

/** Ring glide duration when moving to another day. */
private const val RING_GLIDE_MS = 420

/** The landing pop starts this long after the glide begins (just before it ends). */
private val RING_POP_LEAD = 330.milliseconds

/** How much the ring grows at the peak of the landing pop. */
private const val RING_POP_SCALE = 1.055f

/** Moving light: one lap of [SHINE_SWEEP_MS] every [SHINE_CYCLE_MS], starting at [SHINE_START_MS]. */
private const val SHINE_CYCLE_MS = 5600
// Starts in the cycle's second half (2.9 s): the ring's intro (≤ 0.7 s) has
// finished by then, so even the first lap is seen whole. Starting at 0.1 s made
// the first lap begin before the ring was ready, so only its second half showed.
private const val SHINE_START_MS = 2900
private const val SHINE_SWEEP_MS = 1300

/** Grows the ring slightly and settles it back. */
private suspend fun popRing(scale: Animatable<Float, AnimationVector1D>) {
    scale.animateTo(RING_POP_SCALE, tween(130, easing = FastOutSlowInEasing))
    scale.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
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
 * @param focusToken changes on every new focus request, so the same date can be
 *        re-selected (and scrolled to) again.
 * @param colorOf maps an author UID to that member's accent color.
 * @param nameOf  resolves an event's author display name.
 * @param warmUp  true for the hidden pre-render after launch: the moving light is
 *        shown mid-lap from the start, so its drawing code and GPU shaders get
 *        prepared too (normally the first lap only starts after 2.9 s).
 * @param editorNameOf resolves the display name of the member who edited an event.
 * @param checklistsFor the checklists linked to an event, shown as chips under it.
 * @param onOpenChecklist opens a checklist (tapping that chip).
 * @param onEventChecklist from the event editor's checklist rows: opens one of the
 *        event's checklists (its id), or with no id makes a new one named after the
 *        event (the title it is given) and opens it.
 */
@Composable
fun MonthDetail(
    yearMonth: YearMonth,
    paint: YearPaint,
    eventsByDate: Map<LocalDate, List<Event>>,
    sharedScope: SharedTransitionScope?,
    animScope: AnimatedVisibilityScope?,
    initialSelectedDate: LocalDate? = null,
    focusToken: Int = 0,
    colorOf: (String) -> Color,
    nameOf: (Event) -> String,
    editorNameOf: (Event) -> String? = { it.editedByName },
    isMine: (Event) -> Boolean = { true },
    checklistsFor: (Event) -> List<ChecklistSummary> = { emptyList() },
    onOpenChecklist: (listId: String) -> Unit = {},
    onEventChecklist: ((event: Event, title: String, listId: String?) -> Unit)? = null,
    onAddEvent: (EventDraft) -> Unit,
    /** [openedFrom] is the day whose list the editor was opened from. */
    onUpdateEvent: (event: Event, draft: EventDraft, openedFrom: LocalDate) -> Unit,
    onDeleteEvent: (Event) -> Unit,
    onBack: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    warmUp: Boolean = false
) {
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    val daysInMonth = yearMonth.lengthOfMonth()

    // Initial selection: the requested date, else today in the current month, else the 1st.
    var selectedDay by rememberSaveable(yearMonth.monthValue, initialSelectedDate, focusToken) {
        val focus = initialSelectedDate?.takeIf { YearMonth.from(it) == yearMonth }
            ?: paint.today.takeIf { YearMonth.from(it) == yearMonth }
        mutableIntStateOf(focus?.dayOfMonth ?: 1)
    }
    val selectedDate = yearMonth.atDay(selectedDay.coerceIn(1, daysInMonth))
    var showAddDialog by remember { mutableStateOf(false) }
    // The event being edited in the sheet, or null.
    var editingEvent by remember { mutableStateOf<Event?>(null) }
    // The day the edited event was opened from; captured on tap so a later
    // selection change (e.g. a widget request) can't alter it.
    var editingFrom by remember { mutableStateOf<LocalDate?>(null) }
    // The event whose delete button was tapped, waiting for confirmation.
    var deletingEvent by remember { mutableStateOf<Event?>(null) }
    // Every event once (a multi-day event is listed under each of its days).
    val allEvents = remember(eventsByDate) { eventsByDate.values.flatten().distinctBy { it.id } }
    // Rows of this month's multi-day bands; recomputed only when the events change.
    val bandLayout = remember(yearMonth, allEvents) { layoutBands(yearMonth, allEvents) }

    // ── Auto-scroll so the selected day's events are fully visible ──
    // Bumped on each day tap (and on open when a specific date was requested);
    // 0 means "don't scroll", so simply opening a month keeps the grid in view.
    var revealRequest by remember(yearMonth.monthValue, initialSelectedDate, focusToken) {
        mutableIntStateOf(if (initialSelectedDate?.let { YearMonth.from(it) } == yearMonth) 1 else 0)
    }
    val revealRequester = remember { BringIntoViewRequester() }
    // Natural size of the section (header + list), and the tallest it has been this month.
    var sectionSize by remember { mutableStateOf(IntSize.Zero) }
    var sectionMinHeightPx by remember(yearMonth.monthValue) { mutableIntStateOf(0) }
    val revealMargin = with(density) { REVEAL_MARGIN.toPx() }

    // Keyed on the token too: a new focus request restarts the counter at 1,
    // which may equal the previous value.
    LaunchedEffect(revealRequest, focusToken) {
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
            if (ringStyle == RingStyle.NEON) {
                // Neon is lit from the start: the white center is there as the month
                // zooms in (the year view's outline is already light in dark mode),
                // and only the colored glow fades in around it.
                ringGlow.snapTo(ringColorsTarget.glow)
                ringCore.snapTo(ringColorsTarget.core)
                ringGlowLevel.animateTo(1f, tween(RING_GLOW_FADE_MS, delayMillis = RING_INTRO_DELAY_MS))
            } else {
                // Solid: the year view's dark outline color seeps into the season color,
                // and the glow rises with it — same start, same duration, same curve —
                // so the ring and its light come on together. The glow takes its final
                // color right away (it starts fully transparent, so nothing dark shows);
                // only its opacity animates, which keeps the blurred layers from
                // re-rendering every frame.
                ringGlow.snapTo(ringColorsTarget.glow)
                val spec = tween<Float>(RING_INTRO_MS, delayMillis = RING_INTRO_DELAY_MS)
                launch { ringGlowLevel.animateTo(1f, spec) }
                ringCore.animateTo(ringColorsTarget.core, tween(RING_INTRO_MS, delayMillis = RING_INTRO_DELAY_MS))
            }
            ringEffectsReady = true
        } else {
            // Later changes (a theme switch while the month is open) just cross-fade.
            // If that interrupted the intro, finish what it would have done, so the
            // glow and the moving light don't stay off.
            launch { ringGlowLevel.animateTo(1f, tween(300)) }
            launch { ringGlow.animateTo(ringColorsTarget.glow, tween(300)) }
            ringCore.animateTo(ringColorsTarget.core, tween(300))
            ringEffectsReady = true
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
                delay(RING_POP_LEAD)
                popRing(ringScale)
            }
            ringPos.animateTo(ringTarget, tween(RING_GLIDE_MS, easing = FastOutSlowInEasing))
            return@LaunchedEffect
        }
        if (!reduceMotion) popRing(ringScale)
    }

    // Occasional light around the ring (one lap, then rest), with the glow
    // swelling during the lap. The 5.6 s cycle is twice the today-shimmer cycle
    // (2.8 s; it glows at 1.5–2.8 s of each), and the lap runs at 2.9–4.2 s, while
    // the shimmer is resting, so the two effects never glow at the same time.
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

    // Only the part below the navigation bar scrolls, so "‹ 2026년" and "‹ 11월 ›"
    // stay in view while the day's events are scrolled into view.
    val scrollState = rememberScrollState()
    // A thin line under the bar once content has scrolled beneath it.
    val scrolled by remember { derivedStateOf { scrollState.value > 0 } }
    val dividerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)

    Column(
        Modifier
            .fillMaxSize()
            .then(sharedModifier)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
    ) {
        // ── Navigation (fixed) ──
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp)
                .drawBehind {
                    if (scrolled) {
                        val y = size.height + 4.dp.toPx() - 0.5.dp.toPx()
                        drawLine(dividerColor, Offset(-8.dp.toPx(), y), Offset(size.width + 8.dp.toPx(), y), 0.5.dp.toPx())
                    }
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            // The button's own text size (Unspecified), as before; read without the "‹".
            GlyphButton(
                "‹ ${yearMonth.year}년",
                "${yearMonth.year}년 달력으로",
                onClick = { haptics.tap(); onBack() },
                fontSize = TextUnit.Unspecified
            )
            Spacer(Modifier.weight(1f))
            GlyphButton("‹", "이전 달", onClick = { haptics.tap(); onPrev() }, enabled = yearMonth.monthValue > 1)
            Text("${yearMonth.monthValue}월", fontSize = 20.sp, fontWeight = FontWeight.Medium)
            GlyphButton("›", "다음 달", onClick = { haptics.tap(); onNext() }, enabled = yearMonth.monthValue < 12)
        }

        // ── Everything below scrolls ──
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp)
        ) {

        // ── Day grid, with the selection ring's glow and the ring on top ──
        // Three layers, so an animating ring never re-draws the ~35 cells:
        // 1. the cells, 2. the glow (one layer per glow step), 3. the ring.
        WeekdayHeaderRow(fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))

        // Ring geometry in the grid's coordinates; called from draw code only, so
        // the animated values are read in the draw phase.
        fun ringGeometry(density: Density): RingGeometry? = with(density) {
            if (!ringPlaced || cellPx <= 0f) return null
            val out = RING_GAP.toPx() + RING_WIDTH.toPx() / 2
            val half = (cellPx / 2 + out) * ringScale.value
            val center = ringPos.value + Offset(cellPx / 2, cellPx / 2)
            RingGeometry(
                topLeft = center - Offset(half, half),
                size = Size(half * 2, half * 2),
                corner = CornerRadius((CELL_CORNER.toPx() + out) * ringScale.value),
                width = RING_WIDTH.toPx()
            )
        }
        fun ringLap(): Float? = when {
            warmUp -> 0.5f // mid-lap: draws the moving light and the breathing at once
            ringEffectsReady -> shine?.value
            else -> null
        }

        // Glow layer geometry (see below): the ring sits at a fixed spot inside each layer.
        val glowBleedPx = with(density) { GLOW_BLEED.toPx() }
        val ringOutPx = with(density) { RING_GAP.toPx() + RING_WIDTH.toPx() / 2 }
        val ringSidePx = cellPx + ringOutPx * 2
        val ringCornerPx = with(density) { CELL_CORNER.toPx() } + ringOutPx
        val glowSideDp = with(density) { (ringSidePx + glowBleedPx * 2).toDp() }

        // Moves a glow layer onto the ring and applies the landing pop (the layer's
        // default transform origin, its center, is the ring's center). Hidden until
        // the ring has been placed. Called from graphicsLayer blocks only, so the
        // animated values never cause a redraw.
        fun GraphicsLayerScope.followRing(): Boolean {
            if (!ringPlaced || cellPx <= 0f) {
                alpha = 0f
                return false
            }
            translationX = ringPos.value.x - ringOutPx - glowBleedPx
            translationY = ringPos.value.y - ringOutPx - glowBleedPx
            scaleX = ringScale.value
            scaleY = ringScale.value
            return true
        }

        Box(Modifier.onSizeChanged { gridWidthPx = it.width }) {
            Box(Modifier.graphicsLayer()) {
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
                        // Public holidays: a red number, like Sundays, and a short name beside it.
                        val holiday = KoreanHolidays.of(date)
                        Row(
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(start = 3.dp, top = 3.dp, end = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DayNumber(
                                day = date.dayOfMonth,
                                color = if (holiday != null) SundayRed else dayNumberColor(column = column, isPast = isPast),
                                isPast = isPast,
                                todayBadge = if (isToday) deepTone(paint.colorOf(date)) else null
                            )
                            if (holiday != null) {
                                Text(
                                    holiday.short,
                                    fontSize = 7.sp,
                                    lineHeight = 8.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = SundayRed,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Clip,
                                    modifier = Modifier
                                        .padding(start = 1.dp, top = 1.dp)
                                        .then(
                                            // Same faint backing as the day number on filled (past) cells.
                                            if (isPast) Modifier.background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(3.dp))
                                            else Modifier
                                        )
                                        .padding(horizontal = 1.dp)
                                )
                            }
                        }
                        // One-day events as titles in their authors' colors, in the rows
                        // the multi-day bands (drawn over the grid) leave free, plus "+N".
                        val blocked = bandLayout.blockedSlots(date)
                        val hidden = bandLayout.hiddenCount(date)
                        val singles = eventsByDate[date].orEmpty().filter { !it.isMultiDay }
                        EventTitleChips(
                            events = singles,
                            colorOf = colorOf,
                            blockedSlots = blocked,
                            hiddenCount = hidden,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(start = SLOT_INSET, end = SLOT_INSET, bottom = SLOT_INSET)
                        )
                        // Every row is taken by bands: show the count by the day number instead.
                        val overflow = singles.size + hidden
                        if (blocked.size >= CELL_SLOTS && overflow > 0) {
                            MoreBadge(
                                overflow,
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 4.dp, end = 3.dp)
                            )
                        }
                    }
                }
                // Multi-day events as continuous bands over the cells (part of the grid layer).
                MonthBands(
                    yearMonth = yearMonth,
                    layout = bandLayout,
                    cellPx = cellPx,
                    gapPx = gapPx,
                    colorOf = colorOf,
                    modifier = Modifier.matchParentSize()
                )
            }

            // Glow layers are only as big as the ring plus room for the blur, with the
            // ring's stroke drawn at a fixed spot inside them. Gliding and the landing
            // pop move and scale the whole layer (graphicsLayer translation/scale), so
            // the blurred light is rendered once and reused instead of being re-blurred
            // on every frame of a move. Only a season or theme change redraws it.
            if (ringPainter.gpuGlow) {
                // GPU glow (Android 12+): each layer is a sharp stroke blurred on the GPU.
                ringPainter.gpuGlowLayers(ringStyle).forEach { layer ->
                    key(layer) {
                        val blur = remember(layer, density) {
                            if (DEBUG_GLOW_NO_BLUR) null
                            else layer.blurPx(density).let { BlurEffect(it, it, TileMode.Decal) }
                        }
                        Box(
                            Modifier
                                .size(glowSideDp)
                                .graphicsLayer {
                                    if (!followRing()) return@graphicsLayer
                                    renderEffect = blur
                                    alpha = layer.alphaAt(ringPulse(ringLap()), ringGlowLevel.value)
                                }
                                .drawBehind {
                                    ringPainter.drawGlowSource(
                                        scope = this,
                                        topLeft = Offset(glowBleedPx, glowBleedPx),
                                        size = Size(ringSidePx, ringSidePx),
                                        corner = CornerRadius(ringCornerPx),
                                        ringWidth = RING_WIDTH.toPx(),
                                        glow = ringGlow.value,
                                        layer = layer
                                    )
                                }
                        )
                    }
                }
            } else {
                // Blur-mask glow (USE_GPU_GLOW off, for comparison): one layer with a fixed blur, so
                // the blurred shape is rasterized once; breathing uses alpha and scale.
                Box(
                    Modifier
                        .size(glowSideDp)
                        .graphicsLayer {
                            if (!followRing()) return@graphicsLayer
                            val pulse = ringPulse(ringLap())
                            alpha = ringPainter.outerGlowAlpha(ringStyle, pulse, ringGlowLevel.value)
                            val scale = ringScale.value * ringPainter.outerGlowScale(pulse)
                            scaleX = scale
                            scaleY = scale
                        }
                        .drawBehind {
                            ringPainter.drawOuterGlow(
                                scope = this,
                                topLeft = Offset(glowBleedPx, glowBleedPx),
                                size = Size(ringSidePx, ringSidePx),
                                corner = CornerRadius(ringCornerPx),
                                width = RING_WIDTH.toPx(),
                                glow = ringGlow.value,
                                style = ringStyle
                            )
                        }
                )
            }

            // The ring itself.
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer()
                    .drawBehind {
                        val g = ringGeometry(this) ?: return@drawBehind
                        ringPainter.draw(
                            scope = this,
                            topLeft = g.topLeft,
                            size = g.size,
                            corner = g.corner,
                            width = g.width,
                            colors = RingColors(core = ringCore.value, glow = ringGlow.value),
                            style = ringStyle,
                            lap = ringLap(),
                            glowLevel = ringGlowLevel.value
                        )
                    }
            )
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
                    val holiday = KoreanHolidays.of(selectedDate)
                    Text(
                        "${selectedDate.monthValue}월 ${selectedDate.dayOfMonth}일 (${dow.koreanShort()})" +
                            (holiday?.let { " · ${it.name}" } ?: ""),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (holiday != null) SundayRed else dow.weekendColor() ?: MaterialTheme.colorScheme.onSurface,
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
                                    day = selectedDate,
                                    accent = accent,
                                    authorName = nameOf(e),
                                    authorColor = colorOf(e.authorUid),
                                    editorName = editorNameOf(e),
                                    checklists = checklistsFor(e),
                                    onChecklistClick = { id ->
                                        haptics.tap()
                                        onOpenChecklist(id)
                                    },
                                    onClick = {
                                        haptics.tap()
                                        editingFrom = selectedDate
                                        editingEvent = e
                                    },
                                    onDelete = {
                                        haptics.tap()
                                        deletingEvent = e
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
    }

    if (showAddDialog) {
        EventEditorSheet(
            initial = EventDraft(date = selectedDate, time = null, title = ""),
            history = allEvents,
            onDismiss = { showAddDialog = false },
            onSave = { draft ->
                onAddEvent(draft)
                showAddDialog = false
            }
        )
    }

    editingEvent?.let { event ->
        val openedFrom = editingFrom ?: selectedDate
        key(event.id) {
            EventEditorSheet(
                initial = parseStoredTime(event.time).let { parsed ->
                    EventDraft(
                        date = event.date,
                        time = parsed,
                        title = event.title,
                        // Keep a free-form legacy time ("저녁쯤") unless the user changes it.
                        rawTime = event.time.takeIf { parsed == null && it.isNotBlank() },
                        endDate = event.endDate
                    )
                },
                // Leave the event itself out, so it doesn't "suggest" its own old time.
                history = remember(allEvents, event.id) { allEvents.filter { it.id != event.id } },
                onDismiss = { editingEvent = null },
                onSave = { draft ->
                    onUpdateEvent(event, draft, openedFrom)
                    editingEvent = null
                },
                saveLabel = "수정",
                checklists = checklistsFor(event),
                onChecklist = onEventChecklist?.let { open ->
                    { edited, listId ->
                        edited?.let { onUpdateEvent(event, it, openedFrom) }
                        editingEvent = null
                        open(event, edited?.title ?: event.title, listId)
                    }
                }
            )
        }
    }

    deletingEvent?.let { event ->
        DeleteEventDialog(
            event = event,
            authorName = nameOf(event),
            isMine = isMine(event),
            onConfirm = {
                haptics.reject()
                onDeleteEvent(event)
                deletingEvent = null
            },
            onDismiss = { deletingEvent = null }
        )
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

/** Where the selection ring is drawn, in the grid's coordinates. */
private class RingGeometry(
    val topLeft: Offset,
    val size: Size,
    val corner: CornerRadius,
    val width: Float
)

/** Room around the ring inside each glow layer, so even the wide neon aura isn't clipped at the layer edge. */
private val GLOW_BLEED = 40.dp
