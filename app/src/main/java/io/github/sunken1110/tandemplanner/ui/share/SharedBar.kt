package io.github.sunken1110.tandemplanner.ui.share

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sunken1110.tandemplanner.data.SharedCalendar
import io.github.sunken1110.tandemplanner.ui.common.colorOf
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/** Sync status must persist this long before it is shown, to avoid a flash on app start. */
private const val STATUS_DEBOUNCE_MS = 2_000

/**
 * Top bar of the calendar: members (color dot + name) on the left and a sync
 * status pill on the right. Tapping the members chip opens the share dialog.
 */
@Composable
fun SharedBar(calendar: SharedCalendar, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val members = calendar.members
            if (members.isEmpty()) {
                Text("공동 달력", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            } else {
                members.forEachIndexed { i, uid ->
                    if (i > 0) Text("  ·  ", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(calendar.colorOf(uid))
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        calendar.displayName(uid, "") + if (uid == calendar.uid) " (나)" else "",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (members.size < 2) {
                    Text("  ·  초대하기", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(Modifier.weight(1f))

        var showStatus by remember { mutableStateOf(false) }
        val busy = calendar.offline || calendar.pendingWrites
        LaunchedEffect(busy) {
            if (busy) {
                delay(STATUS_DEBOUNCE_MS.milliseconds)
                showStatus = true
            } else {
                showStatus = false
            }
        }
        AnimatedVisibility(visible = showStatus) {
            Text(
                if (calendar.offline) "오프라인 · 연결되면 자동 저장" else "저장 중…",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
    }
}
