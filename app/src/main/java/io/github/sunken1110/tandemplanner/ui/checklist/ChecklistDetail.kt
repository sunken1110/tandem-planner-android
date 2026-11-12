package io.github.sunken1110.tandemplanner.ui.checklist

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import io.github.sunken1110.tandemplanner.data.progress
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import io.github.sunken1110.tandemplanner.data.ChecklistItem
import io.github.sunken1110.tandemplanner.data.ChecklistList
import io.github.sunken1110.tandemplanner.data.MAX_ITEM_TEXT
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import io.github.sunken1110.tandemplanner.data.MAX_LIST_TITLE
import io.github.sunken1110.tandemplanner.data.SharedCalendar
import io.github.sunken1110.tandemplanner.data.SharedChecklists
import io.github.sunken1110.tandemplanner.model.Season
import io.github.sunken1110.tandemplanner.ui.common.colorOf
import io.github.sunken1110.tandemplanner.ui.common.GlyphButton
import io.github.sunken1110.tandemplanner.ui.effects.rememberHaptics
import io.github.sunken1110.tandemplanner.ui.effects.rememberReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.sign
import kotlin.time.Duration.Companion.milliseconds

/** Swiping a row farther than this share of its width deletes it. */
private const val SWIPE_DELETE_FRACTION = 0.35f

/** While dragging near the top or bottom edge, the list scrolls by this much per move (same speed on every screen). */
private val EDGE_SCROLL = 5.dp

/** How high a picked-up row floats. */
private val LIFT_SHADOW = 8.dp

/**
 * The order for an item placed at [index] of [list] (which already shows it
 * there): halfway between its new neighbors, or one step past the only one.
 */
private fun orderAt(list: List<ChecklistItem>, index: Int): Double {
    val prev = list.getOrNull(index - 1)?.order
    val next = list.getOrNull(index + 1)?.order
    return when {
        prev != null && next != null -> (prev + next) / 2
        prev != null -> prev + 1.0
        next != null -> next - 1.0
        else -> list[index].order
    }
}

private const val GESTURE_CANCEL = 0
private const val GESTURE_TAP = 1
private const val GESTURE_SWIPE = 2

private val ROW_SHAPE = RoundedCornerShape(12.dp)

/**
 * One open checklist: progress, the items (open ones first, then checked ones),
 * and a field to add more.
 *
 * - Tap a row: check / uncheck (the box pops, then the row moves to its section).
 * - Swipe a row sideways: delete, with "되돌리기" for a few seconds.
 * - Long-press an open row, then drag: change the order.
 * - Rows the other member just changed glow briefly; "민지 보는 중" shows when
 *   they have the same list open.
 * - Checking the last item (on either phone) runs a short celebration.
 *
 * @param startRenaming the list was just made with a default name: its name opens
 *        for editing, all selected; Enter (or tapping elsewhere) keeps it, and the
 *        cursor moves on to "항목 추가" for the first item.
 */
@Composable
internal fun ChecklistDetail(
    list: ChecklistList,
    checklists: SharedChecklists,
    calendar: SharedCalendar,
    today: LocalDate,
    season: Season,
    showToast: (SheetToast) -> Unit,
    startRenaming: Boolean = false,
    onBack: () -> Unit,
    onRename: () -> Unit,
    onLinkEvent: () -> Unit,
    onDelete: () -> Unit
) {
    val haptics = rememberHaptics()
    val reduceMotion = rememberReduceMotion()
    val scope = rememberCoroutineScope()
    val all = checklists.itemsOf(list.id)
    val progress = all.progress()
    val hideDone = checklists.isHidingDone(list.id)
    val nameOf: (String) -> String = { calendar.displayName(it, "상대") }
    // Saved, so turning the phone doesn't open the name for editing again.
    var renaming by rememberSaveable { mutableStateOf(startRenaming) }
    val addFocus = remember { FocusRequester() }

    // ── Celebration when a check (here or on the other phone) finishes the list ──
    // Not when opening a finished list, and not when deleting the last open item.
    var shineToken by remember { mutableIntStateOf(0) }
    var burstToken by remember { mutableIntStateOf(0) }
    // Also not when "되돌리기" brings back a checked item (that adds to the total too).
    var lastDone by remember { mutableIntStateOf(progress.done) }
    var lastTotal by remember { mutableIntStateOf(progress.total) }
    LaunchedEffect(progress.done, progress.total) {
        if (progress.complete && progress.done > lastDone && progress.total <= lastTotal) {
            shineToken++
            if (!reduceMotion) burstToken++
            haptics.confirm()
        }
        lastDone = progress.done
        lastTotal = progress.total
    }

    // ── Reordering ──
    // While dragging, the open items are shown from this working copy, rearranged
    // as the row passes others; the new order is saved once on release.
    // Only the order of ids is frozen: the items themselves stay current, so a row
    // the other member checks or deletes mid-drag leaves the list right away.
    val listState = rememberLazyListState()
    val edgeScrollPx = with(LocalDensity.current) { EDGE_SCROLL.toPx() }
    var dragId by remember { mutableStateOf<String?>(null) }
    var workingIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var dragStartTop by remember { mutableFloatStateOf(0f) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    val openItems = all.filter { !it.done }
    val done = all.filter { it.done }

    /**
     * The open items as shown: in the dragged order while dragging. Read fresh from
     * state each time, so the drag callbacks never act on a list from an earlier frame.
     */
    fun currentTodo(): List<ChecklistItem> {
        val open = checklists.itemsOf(list.id).filter { !it.done }
        if (dragId == null) return open
        val byId = open.associateBy { it.id }
        // Items added meanwhile go at the end until the drag is over.
        return workingIds.mapNotNull { byId[it] } + open.filter { it.id !in workingIds }
    }
    val todo = currentTodo()
    // The dragged item was checked or deleted on the other phone: drop the drag.
    val dragLost = dragId != null && openItems.none { it.id == dragId }
    LaunchedEffect(dragLost) {
        if (dragLost) {
            dragId = null
            workingIds = emptyList()
        }
    }

    fun startDrag(item: ChecklistItem) {
        val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == todoKey(item.id) } ?: return
        workingIds = openItems.map { it.id }
        dragStartTop = info.offset.toFloat()
        dragDistance = 0f
        dragId = item.id
        haptics.tap()
    }

    fun dragBy(dy: Float) {
        val id = dragId ?: return
        dragDistance += dy
        val layout = listState.layoutInfo
        val info = layout.visibleItemsInfo.firstOrNull { it.key == todoKey(id) } ?: return
        val top = dragStartTop + dragDistance
        val middle = top + info.size / 2f
        val target = layout.visibleItemsInfo.firstOrNull {
            val key = it.key as? String ?: return@firstOrNull false
            key.startsWith(TODO_PREFIX) && key != info.key && middle >= it.offset && middle <= it.offset + it.size
        }
        if (target != null) {
            val from = workingIds.indexOf(id)
            val to = workingIds.indexOf((target.key as String).removePrefix(TODO_PREFIX))
            if (from >= 0 && to >= 0) {
                workingIds = workingIds.toMutableList().apply { add(to, removeAt(from)) }
                haptics.tick()
            }
        }
        // Near an edge: scroll, so the row can travel past what is on screen. Only
        // while there are open items out of view that way: scrolling further would
        // carry the dragged row's place off the screen and drop the drag.
        fun fullyShown(key: String?): Boolean =
            layout.visibleItemsInfo.firstOrNull { it.key == key }
                ?.let { it.offset >= layout.viewportStartOffset && it.offset + it.size <= layout.viewportEndOffset }
                ?: false
        val shown = currentTodo()
        when {
            top < layout.viewportStartOffset + info.size / 2 &&
                !fullyShown(shown.firstOrNull()?.let { todoKey(it.id) }) -> listState.dispatchRawDelta(-edgeScrollPx)
            top + info.size > layout.viewportEndOffset - info.size / 2 &&
                !fullyShown(shown.lastOrNull()?.let { todoKey(it.id) }) -> listState.dispatchRawDelta(edgeScrollPx)
        }
    }

    fun endDrag() {
        val id = dragId ?: return
        val shown = currentTodo()
        val index = shown.indexOfFirst { it.id == id }
        val item = shown.getOrNull(index)
        val before = checklists.itemsOf(list.id).filter { !it.done }.indexOfFirst { it.id == id }
        if (item != null && index != before) {
            checklists.move(item, orderAt(shown, index))
        }
        dragId = null
        workingIds = emptyList()
    }

    /** Moves an open item one place up (-1) or down (+1): TalkBack's way to reorder. */
    fun moveBy(item: ChecklistItem, step: Int) {
        val open = checklists.itemsOf(list.id).filter { !it.done }
        val from = open.indexOfFirst { it.id == item.id }
        val to = from + step
        if (from < 0 || to !in open.indices) return
        val moved = open.toMutableList().apply { add(to, removeAt(from)) }
        checklists.move(item, orderAt(moved, to))
    }

    fun toggle(item: ChecklistItem, toDone: Boolean) {
        val previousBy = item.doneBy
        // Written a moment later (the box pops first), by the checklists themselves,
        // so it is kept even if this screen goes away meanwhile (e.g. turning the phone).
        checklists.setDoneLater(item, toDone)
        // Unchecking the other member's check: offer to give it back.
        if (!toDone && previousBy != null && !calendar.isMe(previousBy)) {
            val name = nameOf(previousBy)
            showToast(SheetToast("${name}님이 체크한 항목을 해제했어요", "되돌리기") {
                checklists.setDone(item.copy(done = false), true, doneBy = previousBy)
            })
        }
    }

    fun delete(item: ChecklistItem) {
        haptics.reject()
        checklists.deleteItemLater(item)
        showToast(SheetToast("‘${item.text}’ 삭제했어요", "되돌리기") { checklists.undoDelete(item.id) })
    }

    Column(Modifier.fillMaxSize()) {
        // ── Header ──
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlyphButton("‹", "뒤로", onClick = { haptics.tap(); onBack() }, fontSize = 22.sp)
            if (renaming) {
                NameField(
                    initial = list.title,
                    onDone = { name ->
                        renaming = false
                        checklists.renameList(list, name)
                        addFocus.requestFocus()
                    },
                    modifier = Modifier.weight(1f)
                )
            } else {
                Text(
                    list.title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            ViewingNow(checklists, calendar, list.id, reduceMotion, Modifier.padding(start = 6.dp))
            Box {
                var menu by remember { mutableStateOf(false) }
                GlyphButton("⋮", "더보기", onClick = { haptics.tap(); menu = true })
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("이름 바꾸기") }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(
                        text = { Text(if (list.eventId != null) "연결된 일정 바꾸기" else "일정에 연결") },
                        onClick = { menu = false; onLinkEvent() }
                    )
                    DropdownMenuItem(
                        text = { Text("리스트 삭제", color = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; onDelete() }
                    )
                }
            }
        }

        // Above the list, so the celebration's pieces fall over the rows, not behind them.
        Column(
            Modifier
                .zIndex(1f)
                .padding(horizontal = 20.dp)
        ) {
            // ── Linked event ──
            calendar.events.firstOrNull { it.id == list.eventId }?.let { event ->
                Text(
                    linkedEventLabel(event, today),
                    fontSize = 12.sp,
                    color = calendar.colorOf(event.authorUid),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(calendar.colorOf(event.authorUid).copy(alpha = 0.12f))
                        .padding(horizontal = 9.dp, vertical = 3.dp)
                )
                Spacer(Modifier.height(10.dp))
            }

            // ── Progress ──
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    if (progress.complete) "✓ 모두 완료했어요" else "${progress.done} / ${progress.total}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (progress.complete) CompleteGreen else MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.weight(1f))
                if (!progress.complete && progress.total > 0) {
                    Text(
                        "${progress.remaining}개 남음",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Box {
                ChecklistProgressBar(progress.fraction, progress.complete, season, shineToken)
                CelebrationBurst(burstToken, season, Modifier.matchParentSize())
            }
            Spacer(Modifier.height(10.dp))
        }

        // ── Items ──
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
        ) {
            if (all.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "아직 항목이 없어요. 아래에 적어 보세요.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 12.dp)
                    )
                }
            }
            items(todo, key = { todoKey(it.id) }) { item ->
                val dragging = item.id == dragId
                ChecklistRow(
                    item = item,
                    checkColor = calendar.colorOf(calendar.uid.orEmpty()),
                    glowColor = calendar.colorOf(item.lastBy),
                    doneByName = null,
                    highlightAt = checklists.highlights[item.id],
                    reduceMotion = reduceMotion,
                    reorderable = true,
                    dragging = dragging,
                    scope = scope,
                    onToggle = { toggle(item, true) },
                    onDelete = { delete(item) },
                    // Only the moves that can happen: none up for the first, none down for the last.
                    onMoveUp = if (todo.firstOrNull()?.id != item.id) ({ moveBy(item, -1) }) else null,
                    onMoveDown = if (todo.lastOrNull()?.id != item.id) ({ moveBy(item, 1) }) else null,
                    onDragStart = { startDrag(item) },
                    onDrag = ::dragBy,
                    onDragEnd = ::endDrag,
                    modifier = if (dragging) {
                        Modifier
                            .zIndex(1f)
                            .graphicsLayer {
                                val now = listState.layoutInfo.visibleItemsInfo
                                    .firstOrNull { it.key == todoKey(item.id) }?.offset ?: 0
                                translationY = dragStartTop + dragDistance - now
                            }
                    } else Modifier.rowAnimation(this)
                )
            }
            if (done.isNotEmpty()) {
                item(key = "doneHeader") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .rowAnimation(this)
                            .padding(start = 6.dp, top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "완료 ${done.size}개",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            haptics.tap()
                            checklists.setHidingDone(list.id, !hideDone)
                        }) {
                            Text(if (hideDone) "보이기" else "숨기기", fontSize = 12.sp)
                        }
                    }
                }
            }
            if (!hideDone) {
                items(done, key = { "d:${it.id}" }) { item ->
                    val by = item.doneBy
                    ChecklistRow(
                        item = item,
                        checkColor = calendar.colorOf(by ?: calendar.uid.orEmpty()),
                        glowColor = calendar.colorOf(item.lastBy),
                        doneByName = by?.let(nameOf),
                        highlightAt = checklists.highlights[item.id],
                        reduceMotion = reduceMotion,
                        reorderable = false,
                        dragging = false,
                        scope = scope,
                        onToggle = { toggle(item, false) },
                        onDelete = { delete(item) },
                        onMoveUp = null,
                        onMoveDown = null,
                        onDragStart = {},
                        onDrag = {},
                        onDragEnd = {},
                        modifier = Modifier.rowAnimation(this)
                    )
                }
            }
            item(key = "bottom") { Spacer(Modifier.height(12.dp)) }
        }

        // ── Add ──
        DashedInputField(
            placeholder = "항목 추가",
            maxLength = MAX_ITEM_TEXT,
            season = season,
            keepFocus = true,
            focusRequester = addFocus,
            onSubmit = { text ->
                haptics.tap()
                checklists.addItem(list.id, text)
                // Keep the newest item in view.
                scope.launch {
                    delay(120.milliseconds)
                    val count = listState.layoutInfo.totalItemsCount
                    if (count > 0) listState.animateScrollToItem((todo.size).coerceAtMost(count - 1))
                }
            },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp)
        )
    }
}

/**
 * The list's name as a text field, focused with the whole name selected, so
 * typing replaces it. Done on Enter or when the field loses focus; an emptied
 * name keeps the old one.
 */
@Composable
private fun NameField(initial: String, onDone: (String) -> Unit, modifier: Modifier = Modifier) {
    var value by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length)))
    }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var focusedOnce by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    fun finish() {
        if (finished) return
        finished = true
        onDone(value.text.trim().ifEmpty { initial })
    }
    // After the sheet has slid in, so the keyboard doesn't fight its animation.
    LaunchedEffect(Unit) {
        delay(NAME_FOCUS_DELAY)
        focus.requestFocus()
        keyboard?.show()
    }
    val accent = MaterialTheme.colorScheme.primary
    BasicTextField(
        value = value,
        onValueChange = { if (it.text.length <= MAX_LIST_TITLE) value = it },
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        ),
        cursorBrush = SolidColor(accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        // Handling Done here keeps the keyboard up for the item field that comes next.
        keyboardActions = KeyboardActions(onDone = { finish() }),
        modifier = modifier
            .focusRequester(focus)
            .onFocusChanged {
                if (it.isFocused) focusedOnce = true else if (focusedOnce) finish()
            }
            .drawBehind {
                val y = size.height - 1.dp.toPx()
                drawLine(accent, Offset(0f, y), Offset(size.width, y), 2.dp.toPx())
            }
            .padding(vertical = 4.dp)
    )
}

/** The name field waits this long for the sheet to finish sliding in. */
private val NAME_FOCUS_DELAY = 350.milliseconds

private const val TODO_PREFIX = "t:"
private fun todoKey(id: String) = TODO_PREFIX + id

/** Rows fade in and out (moving between sections) and glide to new places. */
private fun Modifier.rowAnimation(scope: LazyItemScope): Modifier = with(scope) {
    animateItem(
        fadeInSpec = tween(260),
        placementSpec = spring(stiffness = Spring.StiffnessMediumLow),
        fadeOutSpec = tween(180)
    )
}

/**
 * One item: a round check box, the text, and (for checked items) who checked it.
 * Gestures are told apart by hand, so a tap, a sideways swipe, a long press and
 * a normal list scroll don't trip over each other.
 *
 * @param scope the list's scope, for work that must finish even if this row
 *        leaves the screen meanwhile (the end of a swipe, which then deletes):
 *        the row's own scope would be cancelled with it. The delayed check write
 *        lives longer still, in [SharedChecklists.setDoneLater].
 * @param onMoveUp / [onMoveDown] TalkBack's way to reorder (null for checked items).
 */
@Composable
private fun ChecklistRow(
    item: ChecklistItem,
    checkColor: Color,
    glowColor: Color,
    doneByName: String?,
    highlightAt: Long?,
    reduceMotion: Boolean,
    reorderable: Boolean,
    dragging: Boolean,
    scope: CoroutineScope,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberHaptics()
    val rowScope = rememberCoroutineScope()

    // The box shows the new state at once; the write follows after the pop.
    var shownDone by remember(item.id, item.done) { mutableStateOf(item.done) }
    val pop = remember { Animatable(1f) }
    val check by animateFloatAsState(if (shownDone) 1f else 0f, tween(180), label = "check")

    fun tap() {
        if (shownDone != item.done) return // already on its way
        shownDone = !item.done
        haptics.tick()
        if (!reduceMotion) {
            rowScope.launch {
                pop.animateTo(1.28f, tween(90))
                pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 600f))
            }
        }
        // The write waits for the pop in SharedChecklists.setDoneLater.
        onToggle()
    }

    // ── The other member's change: a light sweeps across the row ──
    val glow = remember { Animatable(-1f) }
    LaunchedEffect(highlightAt) {
        val at = highlightAt ?: return@LaunchedEffect
        if (SystemClock.elapsedRealtime() - at > SharedChecklists.HIGHLIGHT_MS) return@LaunchedEffect
        glow.snapTo(0f)
        glow.animateTo(1f, tween(1200))
        glow.snapTo(-1f)
    }

    // ── Swipe to delete ──
    var swipeX by remember { mutableFloatStateOf(0f) }
    var widthPx by remember { mutableIntStateOf(0) }

    val currentTap by rememberUpdatedState(::tap)
    val currentDelete by rememberUpdatedState(onDelete)
    val currentMoveUp by rememberUpdatedState(onMoveUp)
    val currentMoveDown by rememberUpdatedState(onMoveDown)
    val currentDragStart by rememberUpdatedState(onDragStart)
    val currentDrag by rememberUpdatedState(onDrag)
    val currentDragEnd by rememberUpdatedState(onDragEnd)

    val lift by animateFloatAsState(if (dragging) 1f else 0f, tween(150), label = "lift")
    val surface = MaterialTheme.colorScheme.surface

    Box(
        modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .onSizeChanged { widthPx = it.width }
    ) {
        // Revealed behind the row while swiping.
        if (swipeX != 0f) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(ROW_SHAPE)
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 18.dp),
                contentAlignment = if (swipeX > 0) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Text("삭제", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                // Lift and shadow are read here, while drawing, so picking a row up
                // doesn't recompose it every frame.
                .graphicsLayer {
                    translationX = swipeX
                    scaleX = 1f + 0.02f * lift
                    scaleY = 1f + 0.02f * lift
                    shadowElevation = LIFT_SHADOW.toPx() * lift
                    shape = ROW_SHAPE
                    clip = false
                }
                .clip(ROW_SHAPE)
                .background(surface.copy(alpha = if (dragging) 1f else 0.9f))
                .drawBehind {
                    val g = glow.value
                    if (g < 0f) return@drawBehind
                    drawRect(glowColor.copy(alpha = 0.12f * (1f - g)))
                    if (!reduceMotion) {
                        val x = -size.width * 0.4f + g * size.width * 1.8f
                        drawRect(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, glowColor.copy(alpha = 0.32f), Color.Transparent),
                                startX = x - size.width * 0.3f,
                                endX = x + size.width * 0.3f
                            )
                        )
                    }
                }
                .pointerInput(item.id, reorderable) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val kind = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            awaitGestureKind(down, viewConfiguration.touchSlop)
                        }
                        when (kind) {
                            GESTURE_TAP -> currentTap()
                            GESTURE_SWIPE -> {
                                horizontalDrag(down.id) { change ->
                                    swipeX += change.positionChange().x
                                    change.consume()
                                }
                                val limit = widthPx * SWIPE_DELETE_FRACTION
                                val target = if (abs(swipeX) > limit) sign(swipeX) * widthPx else 0f
                                scope.launch {
                                    animate(swipeX, target, animationSpec = tween(180)) { v, _ -> swipeX = v }
                                    if (target != 0f) currentDelete()
                                }
                            }
                            null -> {
                                // Long press: pick the row up (open items only).
                                if (!reorderable) return@awaitEachGesture
                                currentDragStart()
                                try {
                                    drag(down.id) { change: PointerInputChange ->
                                        currentDrag(change.positionChange().y)
                                        change.consume()
                                    }
                                } finally {
                                    currentDragEnd()
                                }
                            }
                            else -> Unit // a scroll, or someone else took the touch
                        }
                    }
                }
                .semantics(mergeDescendants = true) {
                    role = Role.Checkbox
                    stateDescription = if (shownDone) "완료" else "미완료"
                    onClick(label = if (shownDone) "체크 해제" else "체크") { currentTap(); true }
                    customActions = buildList {
                        add(CustomAccessibilityAction("삭제") { currentDelete(); true })
                        currentMoveUp?.let { up -> add(CustomAccessibilityAction("위로 이동") { up(); true }) }
                        currentMoveDown?.let { down -> add(CustomAccessibilityAction("아래로 이동") { down(); true }) }
                    }
                }
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CheckCircle(
                progress = check,
                color = checkColor,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        scaleX = pop.value
                        scaleY = pop.value
                    }
            )
            Spacer(Modifier.width(12.dp))
            Text(
                item.text,
                fontSize = 15.sp,
                color = if (shownDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (shownDone) TextDecoration.LineThrough else null,
                modifier = Modifier.weight(1f)
            )
            if (doneByName != null) {
                Spacer(Modifier.width(8.dp))
                Text(doneByName, fontSize = 11.sp, color = checkColor, maxLines = 1)
            }
            if (reorderable) {
                Spacer(Modifier.width(8.dp))
                Grip(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
            }
        }
    }
}

/**
 * Waits for the first movement of a touch and says what it is: a tap (lifted
 * in place), a sideways swipe, or a cancel (a vertical move, which the list
 * scrolls, or a touch someone else consumed). Runs until one of those happens;
 * the caller's timeout turns "still holding" into a long press.
 */
private suspend fun AwaitPointerEventScope.awaitGestureKind(
    down: PointerInputChange,
    slop: Float
): Int {
    var total = Offset.Zero
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == down.id } ?: return GESTURE_CANCEL
        if (change.changedToUpIgnoreConsumed()) {
            if (change.isConsumed) return GESTURE_CANCEL
            change.consume()
            return GESTURE_TAP
        }
        if (change.isConsumed) return GESTURE_CANCEL
        total += change.positionChange()
        if (abs(total.x) > slop && abs(total.x) > abs(total.y) * 1.2f) return GESTURE_SWIPE
        if (abs(total.y) > slop) return GESTURE_CANCEL
    }
}

/** Round check box: an outline when open; filled in [color] with a drawn-in check when done. */
@Composable
private fun CheckCircle(progress: Float, color: Color, modifier: Modifier = Modifier) {
    val outline = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    Canvas(modifier) {
        val r = size.minDimension / 2
        val stroke = 1.8.dp.toPx()
        if (progress < 1f) {
            drawCircle(outline.copy(alpha = outline.alpha * (1f - progress)), r - stroke / 2, style = Stroke(stroke))
        }
        if (progress > 0f) {
            drawCircle(color.copy(alpha = progress), r * (0.6f + 0.4f * progress))
            // The check is drawn in from its left end.
            val path = Path().apply {
                moveTo(size.width * 0.28f, size.height * 0.52f)
                lineTo(size.width * 0.44f, size.height * 0.67f)
                lineTo(size.width * 0.73f, size.height * 0.36f)
            }
            val measure = PathMeasure().apply { setPath(path, false) }
            val partial = Path()
            measure.getSegment(0f, measure.length * progress, partial, true)
            drawPath(partial, Color.White, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** Six faint dots: "this row can be moved" (long-press it). */
@Composable
private fun Grip(color: Color) {
    Canvas(Modifier.size(width = 8.dp, height = 14.dp)) {
        val r = 1.3.dp.toPx()
        for (col in 0..1) for (row in 0..2) {
            drawCircle(color, r, Offset(size.width * (0.25f + 0.5f * col), size.height * (0.17f + 0.33f * row)))
        }
    }
}
