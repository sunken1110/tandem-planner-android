package io.github.sunken1110.tandemplanner.ui.calendar

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import io.github.sunken1110.tandemplanner.currentDate
import io.github.sunken1110.tandemplanner.data.Event
import io.github.sunken1110.tandemplanner.data.SharedCalendar
import io.github.sunken1110.tandemplanner.model.Season
import io.github.sunken1110.tandemplanner.model.dayColor
import io.github.sunken1110.tandemplanner.model.isSeasonFirstDay
import io.github.sunken1110.tandemplanner.ui.common.colorOf
import io.github.sunken1110.tandemplanner.ui.effects.SeasonParticles
import io.github.sunken1110.tandemplanner.ui.effects.rememberHaptics
import io.github.sunken1110.tandemplanner.ui.month.MonthDetail
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

/** How long an error message stays on screen. */
private const val ERROR_VISIBLE_MS = 6_000

/**
 * Main screen once the user has joined a shared calendar.
 *
 * Owns screen-level state (today, fill animation, selected month, dialogs)
 * and switches between the year overview and the expanded month view.
 *
 * @param startFill the year fill animation starts only when this becomes true
 *        (i.e. after the intro overlay is gone).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun CalendarScreen(calendar: SharedCalendar, startFill: Boolean = true) {
    // "Today", refreshed automatically at midnight (unless a debug date is set).
    var today by remember { mutableStateOf(currentDate()) }
    if (DEBUG_TODAY == null) {
        LaunchedEffect(today) {
            val untilMidnight = ChronoUnit.MILLIS.between(LocalDateTime.now(), today.plusDays(1).atStartOfDay())
            delay(untilMidnight.coerceAtLeast(1_000L).milliseconds)
            today = LocalDate.now()
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
    val shownCount by remember(passedDays) { derivedStateOf { fill.value.toInt().coerceAtMost(passedDays) } }

    // ── Shared data ──
    // Listen to the server while this screen is visible; stop when it leaves.
    DisposableEffect(calendar.roomCode) {
        val listeners = calendar.startListening()
        onDispose { listeners.forEach { it.remove() } }
    }
    val eventsByDate by remember { derivedStateOf { calendar.events.groupBy { it.date } } }
    val memberColor: (String) -> Color = { calendar.colorOf(it) }
    val nameOf: (Event) -> String = { calendar.displayName(it.authorUid, it.authorName) }

    // Open the share dialog once right after creating a calendar.
    var showShare by remember { mutableStateOf(calendar.justCreated) }
    LaunchedEffect(Unit) { calendar.justCreated = false }

    // Offer to migrate events saved by the old local-only version.
    var localToMigrate by remember { mutableStateOf(calendar.pendingLocalEvents()) }

    // null = year overview, 1..12 = expanded month.
    var selectedMonth by rememberSaveable { mutableStateOf<Int?>(null) }
    BackHandler(enabled = selectedMonth != null) { selectedMonth = null }

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

        // ── JIT warm-up ──
        // The month view's code is compiled on first use, which caused a one-time
        // stutter. After the fill animation, render it invisibly for a few frames
        // so the first real tap is smooth.
        val fillDone by remember(passedDays) {
            derivedStateOf { fill.value >= passedDays + FADE_DAYS - 0.5f }
        }
        var prewarmed by remember { mutableStateOf(false) }
        if (fillDone && !prewarmed && selectedMonth == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0f }
            ) {
                MonthDetail(
                    yearMonth = YearMonth.from(today),
                    today = today,
                    passedDays = passedDays,
                    fillProvider = fillProvider,
                    yearColors = yearColors,
                    eventsByDate = eventsByDate,
                    sharedScope = null,
                    animScope = null,
                    colorOf = memberColor,
                    nameOf = nameOf,
                    onAddEvent = { _, _, _ -> },
                    onDeleteEvent = {},
                    onBack = {},
                    onPrev = {},
                    onNext = {}
                )
            }
            LaunchedEffect(Unit) {
                repeat(3) { withFrameNanos { } }
                prewarmed = true
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 14.dp, vertical = 16.dp)
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

            YearHeader(today, season, shownCount, passedDays, totalDays, yearColors, fillProvider)
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
                            year, today, passedDays, fillProvider, yearColors, eventsByDate,
                            sharedScope, animScope,
                            onMonthClick = {
                                haptics.tap()
                                selectedMonth = it
                            }
                        )
                    } else {
                        MonthDetail(
                            yearMonth = YearMonth.of(year, month),
                            today = today,
                            passedDays = passedDays,
                            fillProvider = fillProvider,
                            yearColors = yearColors,
                            eventsByDate = eventsByDate,
                            sharedScope = sharedScope,
                            animScope = animScope,
                            colorOf = memberColor,
                            nameOf = nameOf,
                            onAddEvent = { date, time, title -> calendar.addEvent(date, time, title) },
                            onDeleteEvent = { event -> calendar.deleteEvent(event) },
                            onBack = { selectedMonth = null },
                            onPrev = { if (month > 1) selectedMonth = month - 1 },
                            onNext = { if (month < 12) selectedMonth = month + 1 }
                        )
                    }
                }
            }
        }

        // ── Error toast ──
        val error = calendar.lastError
        AnimatedVisibility(
            visible = error != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
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
    }

    if (showShare) {
        ShareDialog(calendar = calendar, onDismiss = { showShare = false })
    }

    if (localToMigrate.isNotEmpty() && !showShare) {
        MigrationDialog(
            count = localToMigrate.size,
            onMigrate = {
                haptics.confirm()
                calendar.migrateLocalEvents(upload = true)
                localToMigrate = emptyList()
            },
            onSkip = {
                calendar.migrateLocalEvents(upload = false)
                localToMigrate = emptyList()
            }
        )
    }
}

/** One-time prompt to move events from the old on-device storage into the shared calendar. */
@Composable
private fun MigrationDialog(count: Int, onMigrate: () -> Unit, onSkip: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("이 폰에 저장된 일정이 있어요") },
        text = {
            Text("예전 버전에서 이 폰에만 저장한 일정 ${count}개가 있어요. 공동 달력으로 옮길까요? 옮기면 함께 쓰는 사람도 볼 수 있어요.")
        },
        confirmButton = { TextButton(onClick = onMigrate) { Text("옮기기") } },
        dismissButton = { TextButton(onClick = onSkip) { Text("옮기지 않기") } }
    )
}
