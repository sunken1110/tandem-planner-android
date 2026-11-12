package io.github.sunken1110.tandemplanner.ui.checklist

import io.github.sunken1110.tandemplanner.ui.common.koreanDayLabel
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.sp
import io.github.sunken1110.tandemplanner.data.ChecklistChange
import io.github.sunken1110.tandemplanner.data.ChecklistList
import io.github.sunken1110.tandemplanner.data.Event
import io.github.sunken1110.tandemplanner.data.MAX_LIST_TITLE
import io.github.sunken1110.tandemplanner.data.SharedCalendar
import io.github.sunken1110.tandemplanner.data.takeWhole
import io.github.sunken1110.tandemplanner.data.SharedChecklists
import io.github.sunken1110.tandemplanner.data.sortedByTime
import io.github.sunken1110.tandemplanner.model.KoreanHolidays
import io.github.sunken1110.tandemplanner.model.Season
import io.github.sunken1110.tandemplanner.ui.common.colorOf
import io.github.sunken1110.tandemplanner.ui.common.ddayText
import io.github.sunken1110.tandemplanner.ui.common.displayTime
import io.github.sunken1110.tandemplanner.ui.common.GlyphButton
import io.github.sunken1110.tandemplanner.ui.common.periodLabel
import io.github.sunken1110.tandemplanner.ui.common.WeekdayHeaders
import io.github.sunken1110.tandemplanner.ui.common.weekdayHeaderColor
import io.github.sunken1110.tandemplanner.ui.common.weekendColor
import io.github.sunken1110.tandemplanner.ui.effects.rememberHaptics
import io.github.sunken1110.tandemplanner.ui.effects.rememberReduceMotion
import io.github.sunken1110.tandemplanner.ui.theme.SundayRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import kotlin.time.Duration.Companion.milliseconds

/**
 * How long a message at the bottom of the sheet stays: gone a little before a
 * deleted item's undo window ends, so "되돌리기" never shows when it can't work.
 */
private val TOAST_VISIBLE = SharedChecklists.UNDO_WINDOW - 500.milliseconds

/**
 * The shared checklists, in a bottom sheet: first all lists (the hub), then one
 * list at a time ([ChecklistDetail]).
 *
 * @param initialListId open this list right away (from an event's checklist chip,
 *        its editor, or a message). Going back then closes the sheet, back to the
 *        calendar the list was opened from, instead of showing all lists.
 * @param renameOnOpen the list was just made with a default name: open with its
 *        name being edited, all selected, so typing replaces it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistSheet(
    checklists: SharedChecklists,
    calendar: SharedCalendar,
    today: LocalDate,
    initialListId: String?,
    renameOnOpen: Boolean = false,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val season = Season.of(today.monthValue)
    var openId by rememberSaveable { mutableStateOf(initialListId) }
    val open = openId?.let { id -> checklists.lists.firstOrNull { it.id == id } }
    // Opened straight into a list: "back" means back to where it was opened from.
    val direct = initialListId != null

    /** Slides the sheet away, then closes it. */
    fun close() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    fun back() {
        if (direct) close() else openId = null
    }

    // The open list was deleted (here or on the other phone): leave it.
    // Waits a moment, since a list created just now takes a few ms to appear.
    LaunchedEffect(openId, open == null) {
        if (openId != null && open == null) {
            delay(1500.milliseconds)
            back()
        }
    }

    // Who is looking at what: only while this sheet is open.
    DisposableEffect(Unit) {
        checklists.watchPresence(true)
        onDispose {
            checklists.watchPresence(false)
            checklists.setViewing(null)
        }
    }
    // "보는 중" only while the app is on screen: going to the home screen (or
    // locking the phone) stops saying so, coming back resumes it.
    LifecycleStartEffect(open?.id) {
        checklists.setViewing(open?.id)
        onStopOrDispose { checklists.setViewing(null) }
    }

    // ── Messages ──
    var toast by remember { mutableStateOf<SheetToast?>(null) }
    LaunchedEffect(toast?.token) {
        if (toast != null) {
            delay(TOAST_VISIBLE)
            toast = null
        }
    }
    LaunchedEffect(checklists) {
        checklists.changes.collect { change ->
            if (change is ChecklistChange.AllDone) {
                toast = SheetToast(allDoneMessage(calendar.displayName(change.by, "상대"), change.title))
            }
        }
    }

    // ── Dialogs ──
    var renaming by remember { mutableStateOf<ChecklistList?>(null) }
    var linking by remember { mutableStateOf<ChecklistList?>(null) }
    var deleting by remember { mutableStateOf<ChecklistList?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        // When opened straight into a list, the sheet's own back handling closes it.
        BackHandler(enabled = openId != null && !direct) { openId = null }
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .imePadding()
        ) {
            AnimatedContent(
                targetState = open?.id,
                transitionSpec = {
                    if (targetState != null) {
                        (slideInHorizontally { it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 4 } + fadeOut())
                    } else {
                        (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { it / 4 } + fadeOut())
                    }
                },
                label = "checklistPage"
            ) { id ->
                val list = id?.let { listId -> checklists.lists.firstOrNull { it.id == listId } }
                if (list == null && direct) {
                    // A list made a moment ago, not arrived yet: show nothing rather than all lists.
                    Box(Modifier.fillMaxSize())
                } else if (list == null) {
                    ChecklistHub(
                        checklists = checklists,
                        calendar = calendar,
                        today = today,
                        season = season,
                        onOpen = { openId = it },
                        onCreate = { title -> checklists.createList(title)?.let { openId = it } }
                    )
                } else {
                    ChecklistDetail(
                        list = list,
                        checklists = checklists,
                        calendar = calendar,
                        today = today,
                        season = season,
                        showToast = { toast = it },
                        startRenaming = renameOnOpen && list.id == initialListId,
                        onBack = ::back,
                        onRename = { renaming = list },
                        onLinkEvent = { linking = list },
                        onDelete = { deleting = list }
                    )
                }
            }

            // A new message replaces the old one with a quick fade; the last one
            // keeps showing while it fades out.
            AnimatedContent(
                targetState = toast,
                contentKey = { it?.token },
                transitionSpec = {
                    (fadeIn() + slideInVertically { it / 2 }) togetherWith (fadeOut() + slideOutVertically { it / 2 })
                },
                contentAlignment = Alignment.BottomCenter,
                label = "sheetToast",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = 74.dp)
            ) { shown ->
                if (shown != null) SheetToastView(shown, onDismiss = { toast = null })
            }
        }
    }

    renaming?.let { list ->
        RenameDialog(
            current = list.title,
            onConfirm = { name ->
                checklists.renameList(list, name)
                renaming = null
            },
            onDismiss = { renaming = null }
        )
    }

    linking?.let { list ->
        LinkEventDialog(
            calendar = calendar,
            today = today,
            linkedId = list.eventId,
            onPick = { eventId ->
                checklists.linkEvent(list, eventId)
                linking = null
            },
            onDismiss = { linking = null }
        )
    }

    deleting?.let { list ->
        val count = checklists.itemsOf(list.id).size
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("리스트를 삭제할까요?") },
            text = {
                val quoted = "‘${list.title}’"
                Text(
                    (if (count > 0) "$quoted${particle(list.title, "과", "와")} 항목 ${count}개가"
                    else "$quoted${particle(list.title, "이", "가")}") +
                        " 두 사람 모두에게서 사라지고 되돌릴 수 없어요."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    checklists.deleteList(list)
                    deleting = null
                    back()
                }) { Text("삭제", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("취소") } }
        )
    }
}

/**
 * The Korean particle that fits after [word]: [afterConsonant] when its last
 * syllable ends in a consonant ("짐" → "과"), else [afterVowel] ("장보기" → "와").
 * Words not ending in Hangul get [afterVowel].
 */
private fun particle(word: String, afterConsonant: String, afterVowel: String): String {
    val last = word.trim().lastOrNull() ?: return afterVowel
    if (last !in '가'..'힣') return afterVowel
    return if ((last - '가') % 28 != 0) afterConsonant else afterVowel
}

/** "민지님이 마지막 항목을 체크했어요 · ‘장보기’ 모두 완료" */
fun allDoneMessage(name: String, title: String): String =
    "${name}님이 마지막 항목을 체크했어요 · ‘$title’ 모두 완료"

/** All lists, newest first, with a field to start a new one. */
@Composable
private fun ChecklistHub(
    checklists: SharedChecklists,
    calendar: SharedCalendar,
    today: LocalDate,
    season: Season,
    onOpen: (String) -> Unit,
    onCreate: (String) -> Unit
) {
    val haptics = rememberHaptics()
    val reduceMotion = rememberReduceMotion()
    // Looked up once, not once per list card.
    val eventsById = calendar.events.associateBy { it.id }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Text("체크리스트", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(
            "둘이 같이 보고, 같이 체크해요",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))
        DashedInputField(
            placeholder = "새 리스트 이름 (예: 장보기, 캠핑 짐)",
            maxLength = MAX_LIST_TITLE,
            season = season,
            keepFocus = false,
            onSubmit = { title ->
                haptics.tap()
                onCreate(title)
            }
        )
        Spacer(Modifier.height(12.dp))

        val lists = checklists.lists
        if (lists.isEmpty()) {
            Text(
                "아직 리스트가 없어요.\n장보기, 여행 짐, 집안일처럼 둘이 같이 챙길 것을 적어 보세요.",
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp)
            )
        }
        LazyColumn(Modifier.weight(1f)) {
            items(lists, key = { it.id }) { list ->
                val progress = checklists.progressOf(list.id)
                val event = list.eventId?.let(eventsById::get)
                Surface(
                    onClick = {
                        haptics.tap()
                        onOpen(list.id)
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .animateItem()
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                list.title,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            ViewingNow(checklists, calendar, list.id, reduceMotion, Modifier.padding(end = 8.dp))
                            Text(
                                when {
                                    progress.complete -> "✓ 완료"
                                    progress.total == 0 -> "비어 있음"
                                    else -> "${progress.done}/${progress.total}"
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (progress.complete) CompleteGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (event != null) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                linkedEventLabel(event, today),
                                fontSize = 12.sp,
                                color = calendar.colorOf(event.authorUid),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (progress.total > 0) {
                            Spacer(Modifier.height(8.dp))
                            ChecklistProgressBar(progress.fraction, progress.complete, season, 0, height = 4.dp)
                        }
                    }
                }
            }
            item(key = "bottom") { Spacer(Modifier.height(16.dp)) }
        }
    }
}

/**
 * "11월 9일 (월) · 하와이 여행 · D-5", or for a multi-day event
 * "11월 16일 (월) ~ 20일 (금) · 제주 여행 · D-12" ("지난 일정" once over).
 */
internal fun linkedEventLabel(event: Event, today: LocalDate): String {
    val date = if (event.isMultiDay) periodLabel(event.date, event.lastDate, weekdays = true)
    else event.date.koreanDayLabel()
    val whenText = if (event.lastDate.isBefore(today)) "지난 일정" else ddayText(event, today)
    return "$date · ${event.title} · $whenText"
}

/**
 * A one-line input with a dashed outline (solid while typing) and a round ↑ button.
 *
 * @param keepFocus true to stay in the field after each entry, for adding several
 *        items in a row; false to let go of it (a new list opens right after).
 * @param focusRequester lets the caller put the cursor here.
 */
@Composable
internal fun DashedInputField(
    placeholder: String,
    maxLength: Int,
    season: Season,
    keepFocus: Boolean,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    var text by rememberSaveable { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    val accent = lerp(season.colors[2], Color.Black, 0.3f)
    val idle = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val focusManager = LocalFocusManager.current

    fun submit() {
        val value = text.trim()
        if (value.isEmpty()) return
        onSubmit(value)
        text = ""
        if (!keepFocus) focusManager.clearFocus()
    }

    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .drawBehind {
                val width = 1.5.dp.toPx()
                drawRoundRect(
                    color = if (focused) accent else idle,
                    topLeft = Offset(width / 2, width / 2),
                    size = Size(size.width - width, size.height - width),
                    cornerRadius = CornerRadius(14.dp.toPx()),
                    style = Stroke(
                        width = width,
                        pathEffect = if (focused) null else PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))
                    )
                )
            }
            .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("+", fontSize = 18.sp, color = if (focused) accent else idle)
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = text,
            onValueChange = { text = it.takeWhole(maxLength) },
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            // Handling Send here keeps the keyboard open (no default "done" action).
            keyboardActions = KeyboardActions(onSend = { submit() }),
            modifier = Modifier
                .weight(1f)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { field ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) {
                        Text(
                            placeholder,
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
        val enabled = text.isNotBlank()
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (enabled) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                .clickable(enabled = enabled, onClickLabel = "추가", role = Role.Button) { submit() }
                .semantics { contentDescription = "추가" },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "↑",
                modifier = Modifier.clearAndSetSemantics {},
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RenameDialog(current: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("리스트 이름") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.takeWhole(MAX_LIST_TITLE) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onConfirm(name) })
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

/**
 * Picks the event a list belongs to, from a small month calendar: days with
 * events carry their authors' color dots; tapping a day lists its events
 * (a multi-day event shows on each of its days). Tapping one selects it and
 * paints its days on the calendar as a block; "연결" then links it.
 * Opens at the linked event's day, or today. Past events can be picked too.
 */
@Composable
private fun LinkEventDialog(
    calendar: SharedCalendar,
    today: LocalDate,
    linkedId: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = rememberHaptics()
    val events = calendar.events.toList()
    val start = events.firstOrNull { it.id == linkedId }?.date ?: today
    var month by remember { mutableStateOf(YearMonth.from(start)) }
    var selected by remember { mutableStateOf(start) }
    var picked by remember { mutableStateOf(linkedId) }
    var pickError by remember { mutableStateOf(false) }
    val pickedEvent = events.firstOrNull { it.id == picked }
    val byDay = remember(events, month) {
        (1..month.lengthOfMonth()).associate { d ->
            val day = month.atDay(d)
            day to events.filter { it.covers(day) }.sortedByTime()
        }
    }

    /** Shows another month, selecting today there, else its first day with events. */
    fun showMonth(target: YearMonth) {
        haptics.tap()
        month = target
        selected = today.takeIf { YearMonth.from(it) == target }
            ?: (1..target.lengthOfMonth()).map { target.atDay(it) }.firstOrNull { day -> events.any { it.covers(day) } }
            ?: target.atDay(1)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("어떤 일정의 리스트인가요?") },
        text = {
            Column {
                // ── Month ──
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlyphButton("‹", "이전 달", onClick = { showMonth(month.minusMonths(1)) })
                    Text(
                        "${month.year}년 ${month.monthValue}월",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    GlyphButton("›", "다음 달", onClick = { showMonth(month.plusMonths(1)) })
                }
                Row {
                    WeekdayHeaders.forEachIndexed { column, label ->
                        Text(
                            label,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            color = weekdayHeaderColor(column),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))

                // ── Days ──
                val lead = month.atDay(1).dayOfWeek.value % 7 // Sunday = 0
                val cells = lead + month.lengthOfMonth()
                for (week in 0 until (cells + 6) / 7) {
                    Row {
                        for (column in 0..6) {
                            val n = week * 7 + column - lead + 1
                            if (n !in 1..month.lengthOfMonth()) {
                                Spacer(Modifier.weight(1f).height(PICKER_CELL))
                                continue
                            }
                            val day = month.atDay(n)
                            DayCellSmall(
                                day = day,
                                band = pickedEvent?.takeIf { it.covers(day) }?.let { e ->
                                    PickedBand(
                                        color = calendar.colorOf(e.authorUid),
                                        // Rounded at the event's ends, and squared where a week row breaks it.
                                        roundStart = day == e.date || column == 0 || n == 1,
                                        roundEnd = day == e.lastDate || column == 6 || n == month.lengthOfMonth()
                                    )
                                },
                                isToday = day == today,
                                isSelected = day == selected,
                                dots = byDay[day].orEmpty().map { calendar.colorOf(it.authorUid) }.distinct().take(3),
                                onClick = {
                                    haptics.tap()
                                    selected = day
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // ── The selected day's events ──
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    selected.koreanDayLabel(),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                )
                val dayEvents = if (YearMonth.from(selected) == month) byDay[selected].orEmpty() else emptyList()
                Column(
                    Modifier
                        .heightIn(min = 56.dp, max = 200.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (dayEvents.isEmpty()) {
                        Text(
                            "이날은 일정이 없어요",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp)
                        )
                    }
                    dayEvents.forEach { event ->
                        val isPicked = event.id == picked
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isPicked) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
                                .clickable {
                                    haptics.tick()
                                    picked = event.id
                                    pickError = false
                                }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(calendar.colorOf(event.authorUid))
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(event.title, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    when {
                                        event.isMultiDay ->
                                            periodLabel(event.date, event.lastDate, weekdays = true) + " · ${event.dayCount}일"
                                        event.time.isBlank() -> "하루 종일"
                                        else -> displayTime(event.time)
                                    },
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (isPicked) Text("✓", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                if (pickError) {
                    Text(
                        "일정을 먼저 골라 주세요",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val id = picked
                when {
                    id == null -> {
                        haptics.reject()
                        pickError = true
                    }
                    id == linkedId -> onDismiss() // nothing changed
                    else -> {
                        haptics.confirm()
                        onPick(id)
                    }
                }
            }) { Text("연결") }
        },
        dismissButton = {
            Row {
                if (linkedId != null) {
                    TextButton(onClick = { onPick(null) }) { Text("연결 해제") }
                }
                TextButton(onClick = onDismiss) { Text("취소") }
            }
        }
    )
}

/** The picked event's days on the picker's calendar, and where its block ends round off. */
private data class PickedBand(val color: Color, val roundStart: Boolean, val roundEnd: Boolean)

/** Height of a day in the picker's calendar. */
private val PICKER_CELL = 38.dp

/**
 * A day in the picker: its number (holidays red), up to three authors' dots, and
 * behind the number a piece of the picked event's block when it covers this day.
 */
@Composable
private fun DayCellSmall(
    day: LocalDate,
    band: PickedBand?,
    isToday: Boolean,
    isSelected: Boolean,
    dots: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val numberColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        KoreanHolidays.of(day) != null -> SundayRed
        else -> day.dayOfWeek.weekendColor() ?: MaterialTheme.colorScheme.onSurface
    }
    // Not clipped: a block runs edge to edge into the neighboring days.
    Column(
        modifier
            .height(PICKER_CELL)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .padding(top = 3.dp)
                .fillMaxWidth()
                .height(24.dp),
            contentAlignment = Alignment.Center
        ) {
            if (band != null) {
                val r = 12.dp
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(start = if (band.roundStart) 3.dp else 0.dp, end = if (band.roundEnd) 3.dp else 0.dp)
                        .background(
                            band.color.copy(alpha = 0.28f),
                            RoundedCornerShape(
                                topStart = if (band.roundStart) r else 0.dp,
                                bottomStart = if (band.roundStart) r else 0.dp,
                                topEnd = if (band.roundEnd) r else 0.dp,
                                bottomEnd = if (band.roundEnd) r else 0.dp
                            )
                        )
                )
            }
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    day.dayOfMonth.toString(),
                    fontSize = 13.sp,
                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isToday && !isSelected) MaterialTheme.colorScheme.primary else numberColor
                )
            }
        }
        Row(
            Modifier.padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            dots.forEach { color ->
                Box(
                    Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(color)
                )
            }
        }
    }
}
