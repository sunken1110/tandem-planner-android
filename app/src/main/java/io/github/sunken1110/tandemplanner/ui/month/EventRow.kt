package io.github.sunken1110.tandemplanner.ui.month

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sunken1110.tandemplanner.data.ChecklistSummary
import io.github.sunken1110.tandemplanner.data.Event
import io.github.sunken1110.tandemplanner.data.EventSnapshot
import io.github.sunken1110.tandemplanner.ui.checklist.CompleteGreen
import io.github.sunken1110.tandemplanner.ui.common.koreanShort
import io.github.sunken1110.tandemplanner.ui.common.displayTime
import io.github.sunken1110.tandemplanner.ui.common.periodLabel
import java.time.LocalDate
import java.time.temporal.ChronoUnit

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
 * A linked checklist shows as a small chip under the title ("☑ 여행 짐 3/8"),
 * which opens the list.
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
    checklist: ChecklistSummary? = null,
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
            if (checklist != null) {
                Spacer(Modifier.height(5.dp))
                ChecklistChip(checklist, onClick = { onChecklistClick(checklist.listId) })
            }
        }
        TextButton(onClick = onDelete) { Text("삭제") }
    }
}

/** "☑ 여행 짐 3/8", or "✓ 여행 짐 · 모두 완료" in green once everything is checked. */
@Composable
private fun ChecklistChip(summary: ChecklistSummary, onClick: () -> Unit) {
    val complete = summary.progress.complete
    val tint = if (complete) CompleteGreen else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        if (complete) "✓ ${summary.title} · 모두 완료"
        else "☑ ${summary.title} ${summary.progress.done}/${summary.progress.total}",
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = tint,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 3.dp)
    )
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
