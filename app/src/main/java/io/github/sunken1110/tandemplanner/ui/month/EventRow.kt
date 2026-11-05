package io.github.sunken1110.tandemplanner.ui.month

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.github.sunken1110.tandemplanner.data.ChecklistSummary
import io.github.sunken1110.tandemplanner.data.Event
import io.github.sunken1110.tandemplanner.data.checklistLabel
import io.github.sunken1110.tandemplanner.data.EventSnapshot
import io.github.sunken1110.tandemplanner.ui.checklist.CompleteGreen
import io.github.sunken1110.tandemplanner.ui.common.koreanShort
import io.github.sunken1110.tandemplanner.ui.common.displayTime
import io.github.sunken1110.tandemplanner.ui.common.periodLabel
import io.github.sunken1110.tandemplanner.ui.effects.rememberHaptics
import io.github.sunken1110.tandemplanner.ui.effects.rememberReduceMotion
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max

/**
 * One event in the day list: a season-colored stripe on the left, an author chip,
 * time ("하루 종일" when none), title and a delete button.
 * Tapping the row (anywhere but the delete button) opens it for editing.
 *
 * If the other member edited it, a small note says who and what the author
 * originally wrote, e.g. "민지가 수정함 · 원래 오후 7:00 ‘저녁 약속’".
 *
 * A multi-day event shows its period and which of its days [day] is instead of a
 * time, e.g. "11월 3일 ~ 6일 · 2/4일째".
 *
 * Each linked checklist shows as a small chip under the title ("☑ 짐 3/8"),
 * which opens that list.
 *
 * @param day the day whose list this row is in.
 * @param editorName display name of the member who edited it, used only when
 *        [Event.isEditedByOther] is true.
 */
@Composable
fun EventRow(
    event: Event,
    day: LocalDate,
    accent: Color,
    authorName: String,
    authorColor: Color,
    editorName: String?,
    checklists: List<ChecklistSummary> = emptyList(),
    onChecklistClick: (listId: String) -> Unit = {},
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(5.dp)
                .fillMaxHeight()
                .background(Brush.verticalGradient(listOf(lerp(accent, Color.White, 0.4f), accent)))
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    authorName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = authorColor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(authorColor.copy(alpha = 0.14f))
                        .padding(horizontal = 7.dp, vertical = 1.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    when {
                        event.isMultiDay -> {
                            val nth = ChronoUnit.DAYS.between(event.date, day) + 1
                            "${rangeLabel(event.date, event.lastDate)} · $nth/${event.dayCount}일째"
                        }
                        event.time.isBlank() -> "하루 종일"
                        else -> displayTime(event.time)
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(event.title, fontSize = 15.sp)
            val original = event.original
            if (event.isEditedByOther && original != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    editNote(editorName ?: event.editedByName.orEmpty(), event, original),
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (checklists.isNotEmpty()) {
                Spacer(Modifier.height(5.dp))
                ChecklistChips(checklists, event.title, onChecklistClick)
            }
        }
        TextButton(onClick = onDelete) { Text("삭제") }
    }
}

/** Space between checklist chips, across and between lines. */
private val CHIP_GAP = 4.dp

/**
 * One chip per linked list, always on a single line, so event rows keep their
 * height: as many as fit, then "+N" for the rest; if not even one fits beside
 * "+N", the first one's name is shortened. Tapping "+N" opens a small menu of all
 * the event's lists ([MoreChip]). A chip is never wider than the line, and only
 * its name is shortened ("…"), never its progress.
 */
@Composable
private fun ChecklistChips(summaries: List<ChecklistSummary>, eventTitle: String, onClick: (listId: String) -> Unit) {
    val count = summaries.size
    // A plain Layout (not SubcomposeLayout), because the event row asks for its
    // content's height first (IntrinsicSize.Min), which SubcomposeLayout can't answer.
    // Everything that might be shown is emitted up front, in this order:
    //   [0, count)          one chip per list
    //   count               the first chip again, to be measured narrower if needed
    //   count + n           "+n", for n in 1 until count
    Layout(content = {
        summaries.forEach { summary ->
            ChecklistChip(summary, eventTitle, onClick = { onClick(summary.listId) })
        }
        if (count > 0) {
            ChecklistChip(summaries[0], eventTitle, onClick = { onClick(summaries[0].listId) })
        }
        for (hidden in 1 until count) {
            MoreChip(hidden, summaries, eventTitle, onOpen = onClick)
        }
    }) { measurables, constraints ->
        val maxWidth = constraints.maxWidth
        val gap = CHIP_GAP.roundToPx()
        val loose = Constraints(maxWidth = maxWidth)

        // Each child may be measured only once, so "+n" results are kept.
        val pluses = HashMap<Int, Placeable>()
        fun plus(hidden: Int): Placeable = pluses.getOrPut(hidden) { measurables[count + hidden].measure(loose) }

        fun rowWidth(items: List<Placeable>): Int = items.sumOf { it.width } + gap * (items.size - 1).coerceAtLeast(0)

        val chips = (0 until count).map { measurables[it].measure(loose) }

        // Which chips to show, in order (the last one may be "+n").
        val shown: List<Placeable> = when {
            rowWidth(chips) <= maxWidth -> chips
            else -> {
                var pick: List<Placeable>? = null
                for (fit in count - 1 downTo 1) {
                    val more = plus(count - fit)
                    if (rowWidth(chips.take(fit)) + gap + more.width <= maxWidth) {
                        pick = chips.take(fit) + more
                        break
                    }
                }
                // Not even one chip fits beside "+n": shorten the first one's name.
                pick ?: run {
                    val more = plus(count - 1)
                    val narrow = measurables[count].measure(
                        Constraints(maxWidth = (maxWidth - gap - more.width).coerceAtLeast(0))
                    )
                    listOf(narrow, more)
                }
            }
        }

        // Left to right, starting a new line when the next one doesn't fit.
        var x = 0
        var y = 0
        var lineHeight = 0
        var usedWidth = 0
        val positions = shown.map { item ->
            if (x > 0 && x + item.width > maxWidth) {
                y += lineHeight + gap
                x = 0
                lineHeight = 0
            }
            val at = x to y
            x += item.width + gap
            usedWidth = max(usedWidth, x - gap)
            lineHeight = max(lineHeight, item.height)
            at
        }
        layout(
            usedWidth.coerceIn(constraints.minWidth, constraints.maxWidth),
            (y + lineHeight).coerceIn(constraints.minHeight, constraints.maxHeight)
        ) {
            shown.forEachIndexed { index, item -> item.place(positions[index].first, positions[index].second) }
        }
    }
}

/** How long the "+N" menu takes to appear, and to go away. */
private const val MENU_IN_MS = 180
private const val MENU_OUT_MS = 120

/**
 * "+2": the chips that didn't fit. Tapping it opens a small menu over the day
 * list (nothing below moves) with every list of the event: full names, progress
 * and a thin bar. Picking one opens it; tapping outside or "back" closes the menu.
 *
 * The menu grows out of the chip and fades in, and fades out a little faster
 * when closing (only fading when the system's animations are off).
 */
@Composable
private fun MoreChip(
    hidden: Int,
    summaries: List<ChecklistSummary>,
    eventTitle: String,
    onOpen: (listId: String) -> Unit
) {
    val haptics = rememberHaptics()
    val reduceMotion = rememberReduceMotion()
    // Stays composed while the closing animation runs.
    val menu = remember { MutableTransitionState(false) }
    Box {
        Text(
            "+$hidden",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))
                .clickable(onClickLabel = "체크리스트 모두 보기") {
                    haptics.tap()
                    menu.targetState = true
                }
                .padding(horizontal = 9.dp, vertical = 3.dp)
        )
        if (menu.currentState || menu.targetState) {
            val gap = with(LocalDensity.current) { 4.dp.roundToPx() }
            val margin = with(LocalDensity.current) { 8.dp.roundToPx() }
            Popup(
                popupPositionProvider = remember(gap, margin) { BelowAnchor(gap, margin) },
                onDismissRequest = { menu.targetState = false },
                properties = PopupProperties(focusable = true)
            ) {
                val origin = TransformOrigin(0f, 0f)
                AnimatedVisibility(
                    visibleState = menu,
                    enter = if (reduceMotion) fadeIn(tween(MENU_IN_MS)) else {
                        fadeIn(tween(MENU_IN_MS)) +
                            scaleIn(tween(MENU_IN_MS, easing = FastOutSlowInEasing), initialScale = 0.9f, transformOrigin = origin)
                    },
                    exit = if (reduceMotion) fadeOut(tween(MENU_OUT_MS)) else {
                        fadeOut(tween(MENU_OUT_MS)) +
                            scaleOut(tween(MENU_OUT_MS), targetScale = 0.96f, transformOrigin = origin)
                    }
                ) {
                    ChecklistMenu(summaries, eventTitle, onPick = { listId ->
                        menu.targetState = false
                        onOpen(listId)
                    })
                }
            }
        }
    }
}

/** The "+N" menu's card: "제주 여행 · 리스트 3개", then one row per list. */
@Composable
private fun ChecklistMenu(summaries: List<ChecklistSummary>, eventTitle: String, onPick: (listId: String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp,
        modifier = Modifier.width(260.dp)
    ) {
        Column(
            Modifier
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState())
                .padding(6.dp)
        ) {
            Text(
                "${eventTitle.trim()} · 리스트 ${summaries.size}개",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 2.dp)
            )
            summaries.forEach { summary ->
                val complete = summary.progress.complete
                val tint = if (complete) CompleteGreen else MaterialTheme.colorScheme.primary
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onPick(summary.listId) }
                        .padding(horizontal = 10.dp, vertical = 9.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            (if (complete) "✓ " else "☑ ") + checklistLabel(summary.title, eventTitle),
                            fontSize = 14.sp,
                            color = if (complete) CompleteGreen else MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (complete) "모두 완료" else "${summary.progress.done}/${summary.progress.total}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (complete) CompleteGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(summary.progress.fraction)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(50))
                                .background(tint)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Puts a popup just below its anchor, starting at the anchor's left edge, moved
 * in to stay [margin] inside the screen; above the anchor when there's no room below.
 */
private class BelowAnchor(private val gap: Int, private val margin: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val x = anchorBounds.left
            .coerceAtMost(windowSize.width - margin - popupContentSize.width)
            .coerceAtLeast(margin)
        val below = anchorBounds.bottom + gap
        val y = if (below + popupContentSize.height <= windowSize.height - margin) below
        else (anchorBounds.top - gap - popupContentSize.height).coerceAtLeast(margin)
        return IntOffset(x, y)
    }
}

/**
 * "☑ 짐 3/8", or "✓ 짐 · 모두 완료" in green once everything is checked. The
 * event's own title is left out of the name ([checklistLabel]), since the chip
 * sits right under it. When space is short only the name is shortened.
 */
@Composable
private fun ChecklistChip(summary: ChecklistSummary, eventTitle: String, onClick: () -> Unit) {
    val complete = summary.progress.complete
    val tint = if (complete) CompleteGreen else MaterialTheme.colorScheme.onSurfaceVariant
    val name = checklistLabel(summary.title, eventTitle)
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            (if (complete) "✓ " else "☑ ") + name,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Text(
            if (complete) " · 모두 완료" else " ${summary.progress.done}/${summary.progress.total}",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = tint,
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * Asks before deleting an event, since a deleted event is gone for both members
 * and can't be restored. Shows which event it is (date, time, title), and who
 * wrote it when it isn't the current member's own.
 *
 * @param authorName shown only when [isMine] is false.
 */
@Composable
fun DeleteEventDialog(
    event: Event,
    authorName: String,
    isMine: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val date = if (event.isMultiDay) {
        rangeLabel(event.date, event.lastDate, weekdays = true)
    } else {
        "${event.date.monthValue}월 ${event.date.dayOfMonth}일 (${event.date.dayOfWeek.koreanShort()})"
    }
    val time = when {
        event.isMultiDay -> "${event.dayCount}일"
        event.time.isBlank() -> "하루 종일"
        else -> displayTime(event.time)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("일정을 삭제할까요?") },
        text = {
            Column {
                Text("$date · $time", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(2.dp))
                Text(event.title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(12.dp))
                Text(
                    (if (isMine) "" else "${authorName.ifBlank { "상대" }}님이 쓴 일정이에요. ") +
                        (if (event.isMultiDay) "${event.dayCount}일 전체가 한꺼번에 삭제돼요. " else "") +
                        "삭제하면 두 사람 모두에게서 사라지고 되돌릴 수 없어요.",
                    fontSize = 14.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("삭제", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}

/**
 * "민지가 수정함 · 원래 10월 30일 (금) · 오후 7:00 · ‘저녁 약속’", listing only the
 * parts that differ from the current version.
 */
private fun editNote(editor: String, current: Event, original: EventSnapshot): String {
    val changes = buildList {
        if (original.date != current.date || original.endDate != current.endDate) {
            val end = original.endDate
            add(
                if (end != null) rangeLabel(original.date, end)
                else "${original.date.monthValue}월 ${original.date.dayOfMonth}일 (${original.date.dayOfWeek.koreanShort()})"
            )
        }
        if (original.time != current.time) {
            add(if (original.time.isBlank()) "하루 종일" else displayTime(original.time))
        }
        if (original.title != current.title) add("‘${original.title}’")
    }
    val name = editor.ifBlank { "상대" }
    val summary = changes.ifEmpty { listOf("‘${original.title}’") }.joinToString(" · ")
    return "${name}${subjectParticle(name)} 수정함 · 원래 $summary"
}

/**
 * A period such as "11월 3일 ~ 6일", or "10월 30일 ~ 11월 2일" across months
 * (with weekdays: "11월 3일 (화) ~ 6일 (금)").
 */
fun rangeLabel(from: LocalDate, to: LocalDate, weekdays: Boolean = false): String =
    periodLabel(from, to, weekdays)

/** Korean subject particle: "이" after a final consonant ("지훈이"), else "가" ("민지가"). */
private fun subjectParticle(word: String): String {
    val last = word.lastOrNull() ?: return "가"
    if (last !in '가'..'힣') return "가"
    return if ((last - '가') % 28 != 0) "이" else "가"
}
