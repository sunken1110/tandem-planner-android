package io.github.sunken1110.tandemplanner.ui.quickadd

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sunken1110.tandemplanner.ai.EventParser
import io.github.sunken1110.tandemplanner.data.EventDraft
import io.github.sunken1110.tandemplanner.model.Season
import io.github.sunken1110.tandemplanner.ui.common.koreanLabel
import io.github.sunken1110.tandemplanner.ui.common.koreanShort
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/** Wait this long after typing stops before parsing again (keeps future AI calls cheap). */
private const val PREVIEW_DEBOUNCE_MS = 250

/**
 * Floating one-line input: "내일 저녁 7시 민지랑 저녁" → parsed draft → editor sheet.
 *
 * While typing, a small preview row shows how the sentence is understood
 * (date · time · title), so users learn what works before submitting.
 * Parsing goes through [EventParser], so swapping in an AI model needs no UI change.
 *
 * @param leading shown left of the bar (the checklist button).
 */
@Composable
fun QuickAddBar(
    parser: EventParser,
    today: LocalDate,
    onSubmit: (EventDraft) -> Unit,
    modifier: Modifier = Modifier,
    focusRequest: Int = 0,
    leading: (@Composable () -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    // Bumping [focusRequest] (the widget's "+") puts the cursor here and opens the keyboard.
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(focusRequest) {
        if (focusRequest == 0) return@LaunchedEffect
        // Give the screen a moment to settle after launch before taking focus.
        delay(300.milliseconds)
        runCatching { focusRequester.requestFocus() }
        keyboard?.show()
    }
    var text by rememberSaveable { mutableStateOf("") }
    var preview by remember { mutableStateOf<EventDraft?>(null) }

    LaunchedEffect(text) {
        if (text.isBlank()) {
            preview = null
        } else {
            delay(PREVIEW_DEBOUNCE_MS.milliseconds)
            preview = parser.parse(text, today)
        }
    }

    fun submit() {
        if (text.isBlank()) return
        val input = text
        scope.launch {
            onSubmit(parser.parse(input, today))
            if (text == input) text = ""
            focusManager.clearFocus()
        }
    }

    Column(modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = preview != null,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
        ) {
            preview?.let { PreviewRow(it, today) }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(8.dp))
            }
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp,
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    Modifier.padding(start = 18.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Four-season swirl: marks this as the "smart" input.
                    Box(
                        Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Brush.sweepGradient(Season.entries.map { it.color }))
                    )
                    Spacer(Modifier.width(12.dp))
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { submit() }),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        decorationBox = { field ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (text.isEmpty()) {
                                    Text(
                                        "빠르게 추가",
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                field()
                            }
                        }
                    )
                    TextButton(onClick = { submit() }, enabled = text.isNotBlank()) {
                        Text("추가", fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

/** "10월 27일 (화) · 오후 7:00 · 민지랑 저녁" as small pills above the bar. */
@Composable
private fun PreviewRow(draft: EventDraft, today: LocalDate) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val date = draft.date
        // The year is shown only when it isn't this year, so a date that lands in
        // another year is obvious before saving.
        val year = if (date.year != today.year) "${date.year}년 " else ""
        val end = draft.endDate?.let { e ->
            " ~ " + (if (e.monthValue != date.monthValue || e.year != date.year) "${e.monthValue}월 " else "") + "${e.dayOfMonth}일"
        } ?: ""
        PreviewPill("$year${date.monthValue}월 ${date.dayOfMonth}일 (${date.dayOfWeek.koreanShort()})$end")
        PreviewPill(if (draft.endDate != null) "하루 종일" else draft.time?.koreanLabel() ?: "시간 없음")
        Text(
            draft.title,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

@Composable
private fun PreviewPill(label: String) {
    Text(
        label,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}
