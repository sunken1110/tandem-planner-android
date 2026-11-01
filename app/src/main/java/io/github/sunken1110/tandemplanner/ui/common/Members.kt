package io.github.sunken1110.tandemplanner.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import io.github.sunken1110.tandemplanner.data.SharedCalendar
import io.github.sunken1110.tandemplanner.ui.theme.MemberColors
import io.github.sunken1110.tandemplanner.ui.theme.UnknownMemberColor

/** Accent color for a member, based on join order. */
fun SharedCalendar.colorOf(memberUid: String): Color =
    MemberColors.getOrElse(memberIndex(memberUid)) { UnknownMemberColor }

/** Display name with a "(나)" suffix for the current user. */
fun SharedCalendar.memberLabel(memberUid: String): String =
    displayName(memberUid, "") + if (isMe(memberUid)) " (나)" else ""

/** Small circle in a member's color. Pass a [modifier] to add a ring or alignment. */
@Composable
fun MemberDot(color: Color, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
    )
}
