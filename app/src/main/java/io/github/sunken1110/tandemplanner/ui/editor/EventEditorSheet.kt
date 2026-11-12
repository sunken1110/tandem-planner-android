package io.github.sunken1110.tandemplanner.ui.editor

import io.github.sunken1110.tandemplanner.ui.common.koreanDayLabel
import io.github.sunken1110.tandemplanner.MAX_EVENT_TITLE
import io.github.sunken1110.tandemplanner.data.takeWhole
import io.github.sunken1110.tandemplanner.ui.common.GlyphButton
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sunken1110.tandemplanner.ai.TimeRecall
import io.github.sunken1110.tandemplanner.ai.TimeSuggestion
import io.github.sunken1110.tandemplanner.data.ChecklistSummary
import io.github.sunken1110.tandemplanner.data.Event
import io.github.sunken1110.tandemplanner.data.EventDraft
import io.github.sunken1110.tandemplanner.currentDate
import io.github.sunken1110.tandemplanner.data.MAX_EVENT_DAYS
import io.github.sunken1110.tandemplanner.data.checklistLabel
import io.github.sunken1110.tandemplanner.model.KoreanHolidays
import io.github.sunken1110.tandemplanner.ui.theme.SundayRed
import androidx.compose.ui.graphics.Color
import io.github.sunken1110.tandemplanner.ui.checklist.ChecklistIcon
import io.github.sunken1110.tandemplanner.ui.checklist.CompleteGreen
import io.github.sunken1110.tandemplanner.ui.common.koreanLabel
import io.github.sunken1110.tandemplanner.ui.common.weekendColor
import io.github.sunken1110.tandemplanner.ui.effects.rememberHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import kotlin.time.Duration.Companion.milliseconds

/** Wait this long after typing stops before looking up past events, so the hint doesn't flicker. */
private val RECALL_DEBOUNCE = 300.milliseconds

/** Default time when the user first turns a time on: the next full hour. */
private fun nextFullHour(): LocalTime = LocalTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0)

/**
 * "10월 30일 (금)", or "9월 25일 (금) 추석" on a public holiday. The year is added
 * when it isn't this year ("2027년 1월 5일 (화)"), so it is clear before saving.
 */
private fun LocalDate.koreanLabel(): String =
    (if (year != currentDate().year) "${year}년 " else "") +
        koreanDayLabel() + (KoreanHolidays.of(this)?.let { " ${it.short}" } ?: "")

/** Red for public holidays and Sundays, sky blue for Saturdays, null otherwise. */
private fun LocalDate.labelColor(): Color? =
    if (KoreanHolidays.of(this) != null) SundayRed else dayOfWeek.weekendColor()

/**
 * Bottom sheet for creating an event.
 *
 * Used both by the month view's "+ 일정 추가" (empty draft) and by the quick-add
 * bar (draft pre-filled by a parser), so parsed results can be checked and
 * corrected in the same place before saving.
 *
 * Also used to edit an existing event (pre-filled draft, [saveLabel] "수정").
 *
 * "하루" / "여러 날" switches between a one-day event and a multi-day one (a trip,
 * a vacation). A multi-day event is all-day on each of its days, so the time
 * controls are hidden while it is selected; the time chosen before is kept and
 * comes back when switching back to "하루".
 *
 * @param history past events used to suggest a time for similar titles (see [TimeRecall]).
 * @param saveLabel text on the confirm button.
 * @param checklists the checklists already linked to the event being edited.
 * @param onChecklist shows checklist rows (existing events only): one per linked
 *        list, which opens it (its id), and "체크리스트 만들기" (id null). It gets the
 *        edited version when something was changed (to save first), or null.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditorSheet(
    initial: EventDraft,
    history: List<Event>,
    onDismiss: () -> Unit,
    onSave: (EventDraft) -> Unit,
    saveLabel: String = "저장",
    checklists: List<ChecklistSummary> = emptyList(),
    onChecklist: ((edited: EventDraft?, listId: String?) -> Unit)? = null
) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var date by remember { mutableStateOf(initial.date) }
    // Last day of a multi-day event; null = one day.
    var endDate by remember { mutableStateOf(initial.endDate?.takeIf { it.isAfter(initial.date) }) }
    // Remembered length, so switching "여러 날" off and on again keeps it.
    var lastSpanDays by remember { mutableLongStateOf(endDate?.let { ChronoUnit.DAYS.between(initial.date, it) } ?: 1L) }
    LaunchedEffect(endDate, date) { endDate?.let { lastSpanDays = ChronoUnit.DAYS.between(date, it) } }
    val multiDay = endDate != null
    /** Moves the start day, keeping the event's length. */
    fun shiftStart(days: Long) {
        date = date.plusDays(days)
        endDate = endDate?.plusDays(days)
    }
    var time by remember { mutableStateOf(initial.time) }
    // A legacy free-form time ("저녁쯤"), kept until the user picks a time themselves.
    var rawTime by remember { mutableStateOf(initial.rawTime) }
    fun pickTime(t: LocalTime?) {
        time = t
        rawTime = null
    }
    var title by remember { mutableStateOf(initial.title) }
    var showError by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    // Last chosen time, kept so the wheel doesn't jump while it collapses after "시간 없음".
    var lastTime by remember { mutableStateOf(initial.time ?: nextFullHour()) }
    LaunchedEffect(time) { time?.let { lastTime = it } }

    // Time recalled from past events with a similar title, looked up once typing pauses.
    var suggestion by remember { mutableStateOf<TimeSuggestion?>(null) }
    var lastHint by remember { mutableStateOf<TimeSuggestion?>(null) }
    LaunchedEffect(title, history) {
        delay(RECALL_DEBOUNCE)
        suggestion = TimeRecall.suggest(title, history)
    }

    /** What the sheet would save right now. */
    fun currentDraft(): EventDraft = if (multiDay) {
        EventDraft(date, null, title.trim(), endDate = endDate)
    } else {
        EventDraft(date, time, title.trim(), rawTime = if (time == null) rawTime else null)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                // Scroll instead of squeezing the content when the keyboard takes space.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            // ── Date (stepper, so parsed dates can be nudged) ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlyphButton("‹", "하루 전", onClick = { haptics.tap(); shiftStart(-1) })
                Text(
                    if (multiDay) "${date.koreanLabel()} 부터" else date.koreanLabel(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = date.labelColor() ?: MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                GlyphButton("›", "하루 뒤", onClick = { haptics.tap(); shiftStart(1) })
            }

            // ── Last day (multi-day events only) ──
            AnimatedVisibility(
                visible = multiDay,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                val end = endDate ?: date.plusDays(lastSpanDays)
                val days = ChronoUnit.DAYS.between(date, end) + 1
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlyphButton(
                        "‹",
                        "마지막 날 하루 앞으로",
                        onClick = { haptics.tap(); endDate = end.minusDays(1) },
                        enabled = days > 2
                    )
                    Text(
                        "${end.koreanLabel()} 까지 · ${days}일",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = end.labelColor() ?: MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    GlyphButton(
                        "›",
                        "마지막 날 하루 뒤로",
                        onClick = { haptics.tap(); endDate = end.plusDays(1) },
                        enabled = days < MAX_EVENT_DAYS
                    )
                }
            }

            // ── One day / several days ──
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = !multiDay,
                    onClick = { haptics.tap(); endDate = null },
                    label = { Text("하루") }
                )
                FilterChip(
                    selected = multiDay,
                    onClick = {
                        haptics.tap()
                        if (!multiDay) endDate = date.plusDays(lastSpanDays.coerceIn(1L, MAX_EVENT_DAYS - 1L))
                    },
                    label = { Text("여러 날") }
                )
            }
            Spacer(Modifier.height(8.dp))

            // ── Title ──
            OutlinedTextField(
                value = title,
                // At most what the server accepts (longer titles would be rejected).
                onValueChange = { title = it.takeWhole(MAX_EVENT_TITLE); showError = false },
                label = { Text("일정 내용") },
                singleLine = true,
                isError = showError,
                supportingText = if (showError) {
                    { Text("일정 내용을 입력해 주세요") }
                } else null,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            // ── Time (one-day events only; multi-day events are all-day) ──
            AnimatedVisibility(
                visible = !multiDay,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
            Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("시간", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(
                    time?.koreanLabel() ?: rawTime ?: "시간 없음",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (time != null || rawTime != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    // Neither chip is selected while a legacy free-form time is kept.
                    selected = time == null && rawTime == null,
                    onClick = { haptics.tap(); pickTime(null) },
                    label = { Text("시간 없음") }
                )
                FilterChip(
                    selected = time != null,
                    onClick = { haptics.tap(); if (time == null) pickTime(lastTime) },
                    label = { Text("시간 지정") }
                )
            }

            // History-based hint; hidden once its time is already chosen.
            val hint = suggestion?.takeIf { it.time != time }
            SideEffect { if (hint != null) lastHint = hint }
            AnimatedVisibility(
                visible = hint != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                // While animating out, keep showing the hint that was there.
                (hint ?: lastHint)?.let { s ->
                    RecallHint(s, onApply = { haptics.tap(); pickTime(s.time) })
                }
            }

            // The wheel appears whenever a time is set.
            AnimatedVisibility(
                visible = time != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                TimeWheel(
                    time = time ?: lastTime,
                    // Ignore changes from a wheel still settling while it collapses after "시간 없음".
                    onTimeChange = { transform -> time?.let { pickTime(transform(it)) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )
            }
            }
            }
            AnimatedVisibility(
                visible = multiDay,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Text(
                    "여러 날 일정은 매일 하루 종일로 표시돼요",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ── Checklist (existing events only) ──
            if (onChecklist != null) {
                fun go(listId: String?) {
                    if (saving) return
                    haptics.tap()
                    saving = true
                    // Unsaved edits are saved on the way, so nothing typed is lost.
                    val edited = currentDraft().takeIf { title.isNotBlank() && it != normalized(initial) }
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onChecklist(edited, listId) }
                }
                Spacer(Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    checklists.forEach { summary ->
                        ChecklistRow(summary, initial.title, onClick = { go(summary.listId) })
                    }
                    ChecklistRow(null, initial.title, onClick = { go(null) })
                }
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    if (saving) return@Button
                    if (title.isBlank()) {
                        showError = true
                        haptics.reject()
                        return@Button
                    }
                    haptics.confirm()
                    saving = true
                    val draft = currentDraft()
                    // Let the sheet slide away before handing the result back.
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onSave(draft) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text(saveLabel, fontSize = 16.sp) }
        }
    }
}

/** [draft] in the shape [EventEditorSheet] saves, so "was anything changed?" is a plain comparison. */
private fun normalized(draft: EventDraft): EventDraft = if (draft.endDate != null) {
    EventDraft(draft.date, null, draft.title.trim(), endDate = draft.endDate)
} else {
    EventDraft(draft.date, draft.time, draft.title.trim(), rawTime = if (draft.time == null) draft.rawTime else null)
}

/**
 * "체크리스트 만들기", or the linked list with its progress ("체크리스트 3/8 · 열기"),
 * named without repeating the event's title ([checklistLabel]).
 */
@Composable
private fun ChecklistRow(summary: ChecklistSummary?, eventTitle: String, onClick: () -> Unit) {
    val complete = summary?.progress?.complete == true
    val name = summary?.let { checklistLabel(it.title, eventTitle) }
    val tint = if (complete) CompleteGreen else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChecklistIcon(color = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            when {
                summary == null -> "체크리스트 만들기"
                complete -> "$name · 모두 완료"
                else -> "$name ${summary.progress.done}/${summary.progress.total}"
            },
            fontSize = 15.sp,
            color = if (complete) CompleteGreen else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            if (summary == null) "›" else "열기",
            fontSize = if (summary == null) 18.sp else 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (summary == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * Small "과거 이력 기반 추천" line with a tappable time:
 * `과거 이력 기반 추천 · '필라테스' 3회    [오후 8:00 적용]`
 */
@Composable
private fun RecallHint(suggestion: TimeSuggestion, onApply: () -> Unit) {
    val basis = suggestion.basis.let { if (it.length > 12) it.take(12) + "…" else it }
    val times = if (suggestion.count > 1) " ${suggestion.count}회" else ""
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
            .clickable(onClick = onApply)
            .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "과거 이력 기반 추천 · '$basis'$times",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onApply) {
            Text("${suggestion.time.koreanLabel()} 적용", fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}
