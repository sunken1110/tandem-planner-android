package io.github.sunken1110.tandemplanner.ui.calendar

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sunken1110.tandemplanner.DEBUG_TODAY
import io.github.sunken1110.tandemplanner.FADE_DAYS
import io.github.sunken1110.tandemplanner.FILL_DURATION_MS
import io.github.sunken1110.tandemplanner.ai.EventParser
import io.github.sunken1110.tandemplanner.ai.RuleBasedEventParser
import io.github.sunken1110.tandemplanner.currentDate
import io.github.sunken1110.tandemplanner.data.Event
import io.github.sunken1110.tandemplanner.data.EventDraft
import io.github.sunken1110.tandemplanner.data.groupByDay
import io.github.sunken1110.tandemplanner.widget.WidgetUpdater
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import io.github.sunken1110.tandemplanner.data.nextUpcoming
import java.time.LocalTime
import kotlin.time.Duration.Companion.seconds
import io.github.sunken1110.tandemplanner.data.ChecklistChange
import io.github.sunken1110.tandemplanner.data.SharedCalendar
import io.github.sunken1110.tandemplanner.data.SharedChecklists
import io.github.sunken1110.tandemplanner.data.defaultChecklistTitle
import io.github.sunken1110.tandemplanner.ui.checklist.ChecklistButton
import io.github.sunken1110.tandemplanner.ui.checklist.ChecklistSheet
import io.github.sunken1110.tandemplanner.ui.checklist.allDoneMessage
import io.github.sunken1110.tandemplanner.model.Season
import io.github.sunken1110.tandemplanner.model.dayColor
import io.github.sunken1110.tandemplanner.model.isSeasonFirstDay
import io.github.sunken1110.tandemplanner.ui.common.YearPaint
import io.github.sunken1110.tandemplanner.ui.common.colorOf
import io.github.sunken1110.tandemplanner.ui.common.toStoredTime
import io.github.sunken1110.tandemplanner.ui.effects.SeasonParticles
import io.github.sunken1110.tandemplanner.ui.effects.rememberHaptics
import io.github.sunken1110.tandemplanner.ui.editor.EventEditorSheet
import io.github.sunken1110.tandemplanner.ui.month.MonthDetail
import io.github.sunken1110.tandemplanner.ui.quickadd.QuickAddBar
import io.github.sunken1110.tandemplanner.ui.share.ShareDialog
import io.github.sunken1110.tandemplanner.ui.share.SharedBar
import io.github.sunken1110.tandemplanner.ui.year.SeasonBanner
import io.github.sunken1110.tandemplanner.ui.year.YearGrid
import io.github.sunken1110.tandemplanner.ui.year.YearHeader
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.time.Duration.Companion.milliseconds

/** Space kept free at the bottom for the floating quick-add bar. */
private val QUICK_ADD_CLEARANCE = 64.dp

/** How long an error message stays on screen. */
private const val ERROR_VISIBLE_MS = 6_000

/**
 * Opacity of the hidden month-view warm-up: far too faint to see, but above 0 so
 * the renderer really draws it (and so prepares its GPU shaders).
 */
private const val WARM_UP_ALPHA = 0.004f

/** How long the warm-up stays: the ring's intro plus its glow and moving light. */
private const val WARM_UP_MS = 1200

/**
 * Main screen once the user has joined a shared calendar.
 *
 * Owns screen-level state (today, fill animation, selected month, dialogs)
 * and switches between the year overview and the expanded month view.
 *
 * @param startFill the year fill animation starts only when this becomes true
 *        (i.e. after the intro overlay is gone).
 */
@OptIn(FlowPreview::class)
@Composable
fun CalendarScreen(
    calendar: SharedCalendar,
    startFill: Boolean = true,
    launchRequest: LaunchRequest? = null,
    onLaunchHandled: () -> Unit = {}
) {
    // "Today", refreshed automatically at midnight (unless a debug date is set).
    var today by remember { mutableStateOf(currentDate()) }
    // The midnight timer below pauses while the phone is in deep sleep, so also
    // re-check whenever the app comes back to the foreground.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { today = currentDate() }
    if (DEBUG_TODAY == null) {
        LaunchedEffect(today) {
            val untilMidnight = ChronoUnit.MILLIS.between(LocalDateTime.now(), today.plusDays(1).atStartOfDay())
            delay(untilMidnight.coerceAtLeast(1_000L).milliseconds)
            today = currentDate()
        }
    }

    val year = today.year
    val totalDays = today.lengthOfYear()
    val passedDays = today.dayOfYear
    val season = Season.of(today.monthValue)
    val dark = isSystemInDarkTheme()
    val haptics = rememberHaptics()

    // Precompute every day's color once per year instead of on every frame.
    val yearColors = remember(year) { List(totalDays) { dayColor(LocalDate.ofYearDay(year, it + 1)) } }

    // ── Year fill animation ──
    // `fill` counts how many days have been "painted" (fractional). Cells read it
    // only in the draw phase via fillProvider, so animating it never recomposes.
    val fill = remember { Animatable(0f) }
    LaunchedEffect(passedDays, startFill) {
        if (!startFill) return@LaunchedEffect
        fill.animateTo(
            targetValue = passedDays + FADE_DAYS,
            animationSpec = tween(FILL_DURATION_MS, easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f))
        )
    }
    val fillProvider = remember { { fill.value } }
    val paint = remember(today, yearColors) { YearPaint(today, yearColors, fillProvider) }
    val shownCount by remember(passedDays) { derivedStateOf { fill.value.toInt().coerceAtMost(passedDays) } }

    // ── Shared data ──
    // Listen to the server while this screen is visible; stop when it leaves.
    DisposableEffect(calendar.roomCode) {
        val listeners = calendar.startListening()
        onDispose { listeners.forEach { it.remove() } }
    }
    // Each day's events are sorted once here, so every view shows them in time order.
    // A multi-day event is listed under every day it covers.
    val eventsByDate by remember {
        derivedStateOf { calendar.events.toList().groupByDay() }
    }
    // The D-day card's event. The clock is re-read every minute, so today's timed
    // events drop off the card once their time has passed.
    var minuteNow by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay((60 - LocalTime.now().second).seconds)
            minuteNow = LocalTime.now()
        }
    }
    val nextEvent by remember(today, minuteNow) {
        derivedStateOf { calendar.events.toList().nextUpcoming(today, minuteNow) }
    }
    val memberColor: (String) -> Color = { calendar.colorOf(it) }
    val nameOf: (Event) -> String = { calendar.displayName(it.authorUid, it.authorName) }

    // Open the share dialog once right after creating a calendar.
    // Opened once right after creating a room (to share the code) or recovering a
    // seat (to show the new recovery code, since the used one no longer works).
    var showShare by remember { mutableStateOf(calendar.justCreated || calendar.justRecovered) }
    LaunchedEffect(Unit) {
        calendar.justCreated = false
        calendar.justRecovered = false
    }

    // null = year overview, 1..12 = expanded month.
    var selectedMonth by rememberSaveable { mutableStateOf<Int?>(null) }

    // Year grid scroll position, kept here because the grid is disposed while a month is open.
    val yearGridState = rememberLazyGridState()

    /**
     * Back to the year view. The card-shrink animation needs the month's card to
     * be on screen (the lazy grid only creates visible cards), so scroll it into
     * view first; the request is applied on the grid's very first layout.
     */
    fun backToYear() {
        val month = selectedMonth ?: return
        val index = month - 1
        val info = yearGridState.layoutInfo
        val card = info.visibleItemsInfo.firstOrNull { it.index == index }
        val fullyVisible = card != null &&
            card.offset.y >= info.viewportStartOffset &&
            card.offset.y + card.size.height <= info.viewportEndOffset
        if (!fullyVisible) yearGridState.requestScrollToItem(index)
        selectedMonth = null
    }

    BackHandler(enabled = selectedMonth != null) { backToYear() }

    // Quick-add: the parsed draft waiting for confirmation, and the date to
    // highlight after saving it.
    val parser: EventParser = remember { RuleBasedEventParser }
    var quickDraft by remember { mutableStateOf<EventDraft?>(null) }
    var focusDate by remember { mutableStateOf<LocalDate?>(null) }
    // Bumped on every quick-add save, so saving to the same date twice still re-selects it.
    var focusToken by remember { mutableIntStateOf(0) }
    // Forget a quick-add focus date once the user returns to the year view.
    LaunchedEffect(selectedMonth) { if (selectedMonth == null) focusDate = null }
    // ── Home-screen widget ──
    // Keep the widget's copy of the events current (shortly after changes settle).
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        snapshotFlow { calendar.events.toList() to calendar.members }
            .debounce(800.milliseconds)
            .collect { runCatching { WidgetUpdater.publish(context, calendar) } }
    }
    // ── Shared checklists ──
    val checklists = remember(calendar) { SharedChecklists(calendar, context) }
    DisposableEffect(calendar.roomCode) {
        checklists.start()
        onDispose { checklists.stop() }
    }
    var checklistOpen by rememberSaveable { mutableStateOf(false) }
    // The list to open directly (an event's checklist chip), or null for all lists.
    var checklistTarget by rememberSaveable { mutableStateOf<String?>(null) }
    // The target was just made with a default name, to be edited right away.
    var checklistRenameNew by rememberSaveable { mutableStateOf(false) }
    fun openChecklist(listId: String?, renameNew: Boolean = false) {
        checklistTarget = listId
        checklistRenameNew = renameNew
        checklistOpen = true
    }
    // The other member finished a list while the sheet is closed: say so here.
    var notice by remember { mutableStateOf<ChecklistChange.AllDone?>(null) }
    LaunchedEffect(checklists) {
        checklists.changes.collect { change ->
            if (change is ChecklistChange.AllDone && !checklistOpen) notice = change
        }
    }

    // A widget tap: open the requested day (this year only), or focus quick add.
    var quickAddFocus by remember { mutableIntStateOf(0) }
    LaunchedEffect(launchRequest?.token) {
        val request = launchRequest ?: return@LaunchedEffect
        request.date?.takeIf { it.year == year }?.let { day ->
            focusDate = day
            focusToken++
            selectedMonth = day.monthValue
        }
        if (request.quickAdd) quickAddFocus++
        onLaunchHandled()
    }

    val saveDraft: (EventDraft) -> Unit = { d ->
        calendar.addEvent(d.date, d.storedTime { it.toStoredTime() }, d.title, d.endDate)
    }

    val bgTint by animateColorAsState(
        targetValue = dayColor(today).copy(alpha = if (dark) 0.08f else 0.12f),
        animationSpec = tween(800),
        label = "seasonBackground"
    )

    // Season-start banner; once closed it stays hidden for the rest of that day.
    var bannerClosedOn by rememberSaveable { mutableStateOf<String?>(null) }
    val showBanner = isSeasonFirstDay(today) && bannerClosedOn != today.toString()

    Box(
        Modifier
            .fillMaxSize()
            .background(bgTint)
    ) {
        // Background particles follow the expanded month's season, or today's season
        // in the year view. Same season → no visible change.
        val particleSeason = selectedMonth?.let { Season.of(it) } ?: season
        Crossfade(targetState = particleSeason, animationSpec = tween(700), label = "particles") { s ->
            SeasonParticles(season = s, dark = dark)
        }

        // ── Warm-up ──
        // The first time the month view is shown, its code is compiled and the GPU
        // builds its shaders (blurred glow, gradients, text), which made the first
        // open stutter. After the year fill (a natural "loading screen"), the month
        // view is drawn behind everything for about a second, so all of that is done
        // before the first real tap.
        // It must actually be drawn: a layer with alpha 0 is skipped by the renderer
        // (only the code would warm up, not the GPU), so it uses a tiny alpha instead.
        // It runs long enough for the ring's intro, glow and moving light to render.
        val fillDone by remember(passedDays) {
            derivedStateOf { fill.value >= passedDays + FADE_DAYS - 0.5f }
        }
        var prewarmed by remember { mutableStateOf(false) }
        if (fillDone && !prewarmed && selectedMonth == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = WARM_UP_ALPHA }
            ) {
                MonthDetail(
                    yearMonth = YearMonth.from(today),
                    paint = paint,
                    eventsByDate = eventsByDate,
                    sharedScope = null,
                    animScope = null,
                    colorOf = memberColor,
                    nameOf = nameOf,
                    onAddEvent = {},
                    onUpdateEvent = { _, _ -> },
                    onDeleteEvent = {},
                    onBack = {},
                    onPrev = {},
                    onNext = {},
                    warmUp = true
                )
            }
            LaunchedEffect(Unit) {
                delay(WARM_UP_MS.milliseconds)
                prewarmed = true
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 14.dp, vertical = 16.dp)
                .padding(bottom = QUICK_ADD_CLEARANCE)
        ) {
            AnimatedVisibility(
                visible = showBanner,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                SeasonBanner(season = season, onClose = {
                    haptics.tap()
                    bannerClosedOn = today.toString()
                })
            }

            SharedBar(
                calendar = calendar,
                onClick = {
                    haptics.tap()
                    showShare = true
                }
            )
            Spacer(Modifier.height(10.dp))

            // Passed as a lambda so only the header recomposes while the count animates.
            YearHeader(
                paint = paint,
                shownCount = { shownCount },
                next = nextEvent,
                nameOf = nameOf,
                colorOf = memberColor,
                // Open the event's day (today, for a trip under way) in the month view.
                onNextClick = { event ->
                    val day = maxOf(event.date, today)
                    if (day.year == year) {
                        haptics.tap()
                        focusDate = day
                        focusToken++
                        selectedMonth = day.monthValue
                    }
                }
            )
            Spacer(Modifier.height(14.dp))

            // Year ↔ month: the tapped card expands in place via shared bounds.
            SharedTransitionLayout(Modifier.fillMaxSize()) {
                val sharedScope = this
                AnimatedContent(
                    targetState = selectedMonth,
                    transitionSpec = {
                        val from = initialState
                        val to = targetState
                        when {
                            // Size change is handled by sharedBounds; only fade here.
                            from == null || to == null ->
                                fadeIn(tween(350)) togetherWith fadeOut(tween(250))
                            // Month → next month slides in from the right, previous from the left.
                            to > from ->
                                slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                            else ->
                                slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                        }
                    },
                    label = "monthView"
                ) { month ->
                    val animScope = this
                    if (month == null) {
                        YearGrid(
                            paint = paint,
                            eventsByDate = eventsByDate,
                            colorOf = memberColor,
                            state = yearGridState,
                            sharedScope = sharedScope,
                            animScope = animScope,
                            onMonthClick = {
                                haptics.tap()
                                selectedMonth = it
                            }
                        )
                    } else {
                        MonthDetail(
                            yearMonth = YearMonth.of(year, month),
                            paint = paint,
                            eventsByDate = eventsByDate,
                            sharedScope = sharedScope,
                            animScope = animScope,
                            colorOf = memberColor,
                            nameOf = nameOf,
                            editorNameOf = { e -> e.editedByUid?.let { calendar.displayName(it, e.editedByName.orEmpty()) } },
                            isMine = { e -> calendar.isMe(e.authorUid) },
                            checklistsFor = { e -> checklists.summariesFor(e.id) },
                            onOpenChecklist = { openChecklist(it) },
                            // The event's list, or a new one named after the event.
                            onEventChecklist = { event, title, listId ->
                                if (listId != null) {
                                    openChecklist(listId)
                                } else {
                                    // A new list named after the event (numbered if the event
                                    // already has one by that name); its name opens for editing.
                                    val taken = checklists.summariesFor(event.id).map { it.title }
                                    checklists.createList(defaultChecklistTitle(title, taken), eventId = event.id)
                                        ?.let { openChecklist(it, renameNew = true) }
                                }
                            },
                            initialSelectedDate = focusDate,
                            focusToken = focusToken,
                            onAddEvent = saveDraft,
                            onUpdateEvent = { event, d ->
                                calendar.updateEvent(event, d.date, d.storedTime { it.toStoredTime() }, d.title, d.endDate)
                            },
                            onDeleteEvent = { event -> calendar.deleteEvent(event) },
                            onBack = { backToYear() },
                            // A requested day (D-day card, widget, quick add) is shown once:
                            // after moving to another month, coming back opens normally,
                            // at the top with the whole grid in view.
                            onPrev = { if (month > 1) { focusDate = null; selectedMonth = month - 1 } },
                            onNext = { if (month < 12) { focusDate = null; selectedMonth = month + 1 } }
                        )
                    }
                }
            }
        }

        // ── Quick-add bar ──
        QuickAddBar(
            parser = parser,
            today = today,
            onSubmit = { quickDraft = it },
            focusRequest = quickAddFocus,
            leading = {
                ChecklistButton(
                    remaining = checklists.remainingCount,
                    season = season,
                    onClick = {
                        haptics.tap()
                        openChecklist(null)
                    }
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        )

        // ── Error toast ──
        val error = calendar.lastError
        AnimatedVisibility(
            visible = error != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = QUICK_ADD_CLEARANCE + 8.dp)
        ) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    error.orEmpty(),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f, fill = false)
                )
                TextButton(onClick = { calendar.lastError = null }) { Text("닫기") }
            }
        }
        LaunchedEffect(error) {
            if (error != null) {
                delay(ERROR_VISIBLE_MS.milliseconds)
                calendar.lastError = null
            }
        }

        // ── "민지님이 마지막 항목을 체크했어요" (while the checklists are closed) ──
        val shownNotice = notice
        AnimatedVisibility(
            visible = shownNotice != null && error == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = QUICK_ADD_CLEARANCE + 8.dp)
        ) {
            var last by remember { mutableStateOf(shownNotice) }
            LaunchedEffect(shownNotice) { if (shownNotice != null) last = shownNotice }
            val n = shownNotice ?: last
            Row(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.inverseSurface)
                    .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    n?.let { allDoneMessage(calendar.displayName(it.by, "상대"), it.title) }.orEmpty(),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.weight(1f, fill = false)
                )
                TextButton(onClick = {
                    n?.let { openChecklist(it.listId) }
                    notice = null
                }) { Text("보기", color = MaterialTheme.colorScheme.inversePrimary) }
            }
        }
        LaunchedEffect(shownNotice) {
            if (shownNotice != null) {
                delay(ERROR_VISIBLE_MS.milliseconds)
                notice = null
            }
        }
    }

    if (checklistOpen) {
        ChecklistSheet(
            checklists = checklists,
            calendar = calendar,
            today = today,
            initialListId = checklistTarget,
            renameOnOpen = checklistRenameNew,
            onDismiss = {
                checklistOpen = false
                checklistTarget = null
                checklistRenameNew = false
            }
        )
    }

    if (showShare) {
        ShareDialog(calendar = calendar, onDismiss = { showShare = false })
    }

    quickDraft?.let { draft ->
        key(draft) {
            EventEditorSheet(
            initial = draft,
            history = calendar.events,
            onDismiss = { quickDraft = null },
            onSave = { saved ->
                saveDraft(saved)
                quickDraft = null
                // Jump to the event's month so the new dot is visible.
                if (saved.date.year == year) {
                    focusDate = saved.date
                    focusToken++
                    selectedMonth = saved.date.monthValue
                }
            }
        )
        }
    }
}


/**
 * A request to open the calendar somewhere specific, from a home-screen widget tap.
 *
 * @property date  the day to show in the month view (this year only), or null.
 * @property quickAdd true to focus the quick-add bar.
 * @property token unique per tap, so tapping the same day twice still works.
 */
data class LaunchRequest(val date: LocalDate?, val quickAdd: Boolean, val token: Long)
