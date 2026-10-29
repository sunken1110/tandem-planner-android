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
import io.github.sunken1110.tandemplanner.data.Event
import io.github.sunken1110.tandemplanner.data.EventSnapshot
import io.github.sunken1110.tandemplanner.ui.common.koreanShort
import io.github.sunken1110.tandemplanner.ui.common.displayTime

/**
 * One event in the day list: a season-colored side bar, an author chip,
 * time ("하루 종일" when none), title and a delete button.
 * Tapping the row (anywhere but the delete button) opens it for editing.
 *
 * If the other member edited it, a small note says who and what the author
 * originally wrote, e.g. "민지가 수정함 · 원래 오후 7:00 ‘저녁 약속’".
 *
 * @param editorName display name of the member who edited it, used only when
 *        [Event.isEditedByOther] is true.
 */
@Composable
fun EventRow(
    event: Event,
    accent: Color,
    authorName: String,
    authorColor: Color,
    editorName: String?,
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
                    if (event.time.isBlank()) "하루 종일" else displayTime(event.time),
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
        }
        TextButton(onClick = onDelete) { Text("삭제") }
    }
}

/**
 * "민지가 수정함 · 원래 10월 30일 (금) · 오후 7:00 · ‘저녁 약속’", listing only the
 * parts that differ from the current version.
 */
private fun editNote(editor: String, current: Event, original: EventSnapshot): String {
    val changes = buildList {
        if (original.date != current.date) {
            add("${original.date.monthValue}월 ${original.date.dayOfMonth}일 (${original.date.dayOfWeek.koreanShort()})")
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

/** Korean subject particle: "이" after a final consonant ("지훈이"), else "가" ("민지가"). */
private fun subjectParticle(word: String): String {
    val last = word.lastOrNull() ?: return "가"
    if (last !in '가'..'힣') return "가"
    return if ((last - '가') % 28 != 0) "이" else "가"
}
