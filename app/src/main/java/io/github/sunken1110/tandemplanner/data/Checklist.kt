package io.github.sunken1110.tandemplanner.data

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Longest list title, in characters (also checked by the Firestore rules). */
const val MAX_LIST_TITLE = 40

/** Longest item text, in characters (also checked by the Firestore rules). */
const val MAX_ITEM_TEXT = 100

/**
 * A shared checklist ("장보기", "캠핑 짐", …).
 *
 * @property eventId the event this list belongs to, or null for a standalone list.
 * @property createdAtMs creation time (the server's, estimated until it arrives).
 */
data class ChecklistList(
    val id: String,
    val title: String,
    val createdBy: String,
    val eventId: String?,
    val createdAtMs: Long
)

/**
 * One line of a checklist.
 *
 * @property order sort key within the list. Fractions are allowed, so moving an
 *           item only changes its own number (halfway between its new neighbors).
 * @property doneBy who checked it (account uid), null when not done.
 * @property lastBy who changed it last (account uid); tells the other phone whether
 *           a change was its own.
 */
data class ChecklistItem(
    val id: String,
    val listId: String,
    val text: String,
    val done: Boolean,
    val doneBy: String?,
    val order: Double,
    val createdBy: String,
    val lastBy: String
)

/** How far a list is: [done] of [total] items checked. */
data class ChecklistProgress(val done: Int, val total: Int) {
    val complete: Boolean get() = total > 0 && done == total
    val remaining: Int get() = total - done
    val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total
}

/** Ending of a list made from an event: "제주 여행" → "제주 여행 체크리스트". */
private const val DEFAULT_SUFFIX = " 체크리스트"

/**
 * The name a list made from an event starts with, fitting [MAX_LIST_TITLE]:
 * "제주 여행 체크리스트", or "제주 여행 체크리스트 2" (3, …) when one of [taken]
 * (the event's other lists) already has that name.
 */
fun defaultChecklistTitle(eventTitle: String, taken: Collection<String> = emptyList()): String {
    val used = taken.mapTo(HashSet()) { it.trim() }
    var n = 1
    while (true) {
        val number = if (n == 1) "" else " $n"
        val name = eventTitle.trim().take(MAX_LIST_TITLE - DEFAULT_SUFFIX.length - number.length) + DEFAULT_SUFFIX + number
        if (name !in used) return name
        n++
    }
}

/**
 * How a list is named right under its event, without repeating the event:
 * a name that starts with the whole event title (as a word) shows only the rest
 * ("제주 여행 짐" → "짐", "제주 여행 체크리스트" → "체크리스트"); a name that is just
 * the event title shows "체크리스트"; any other name ("운동 준비물") stays as is.
 */
fun checklistLabel(listTitle: String, eventTitle: String): String {
    val name = listTitle.trim()
    val event = eventTitle.trim()
    if (event.isEmpty() || !name.startsWith(event)) return name
    val rest = name.substring(event.length)
    return when {
        rest.isBlank() -> "체크리스트"
        // Only at a word break: "제주 여행지 맛집" under "제주 여행" stays whole.
        rest.first().isWhitespace() -> rest.trim()
        else -> name
    }
}

/** How far these items are checked. */
fun List<ChecklistItem>.progress(): ChecklistProgress = ChecklistProgress(count { it.done }, size)

/** What an event's day list shows about the checklist linked to it. */
data class ChecklistSummary(val listId: String, val title: String, val progress: ChecklistProgress)

/**
 * A change the *other* member made, as it arrives. Used for the glow on the
 * changed row and for messages like "민지님이 마지막 항목을 체크했어요".
 * Your own changes, and everything already there when a list first loads, are
 * not reported.
 *
 * @property by the other member's account uid.
 */
sealed interface ChecklistChange {
    val listId: String
    val by: String

    data class Checked(override val listId: String, val itemId: String, val text: String, override val by: String) : ChecklistChange
    data class Unchecked(override val listId: String, val itemId: String, val text: String, override val by: String) : ChecklistChange
    data class Added(override val listId: String, val itemId: String, val text: String, override val by: String) : ChecklistChange
    data class Moved(override val listId: String, val itemId: String, override val by: String) : ChecklistChange
    data class Edited(override val listId: String, val itemId: String, override val by: String) : ChecklistChange

    /** Their check finished the list. */
    data class AllDone(override val listId: String, val title: String, override val by: String) : ChecklistChange
}

/**
 * Shared checklists of the current room, kept in sync live between both phones.
 *
 * Firestore layout (next to the room's events):
 * ```
 * rooms/{code}/lists/{listId}               title, createdBy, createdAt, updatedAt, eventId?
 * rooms/{code}/lists/{listId}/items/{id}    text, done, doneBy?, order, createdBy, createdAt,
 *                                           lastBy, updatedAt
 * rooms/{code}/presence/{uid}               listId ("" = none), at   ("민지 보는 중")
 * ```
 *
 * - Both members may change anything; `lastBy` records who did, so each phone
 *   can tell its own changes from the other member's ([changes], [highlights]).
 * - Checking, moving and deleting update the screen at once (optimistically), then
 *   write; Firestore's offline cache sends the write when a connection is back.
 * - A deleted item first waits a few seconds ([UNDO_WINDOW]) so it can be brought
 *   back with "되돌리기"; only then is it deleted for both.
 * - While a list is open, this phone says so every [HEARTBEAT]; a note younger
 *   than [PRESENCE_FRESH_MS] counts as "보는 중" (a closed app simply stops saying it).
 *
 * Call [start] when the calendar screen appears and [stop] when it goes away.
 */
class SharedChecklists(private val calendar: SharedCalendar, context: Context) {
    private val db = FirebaseFirestore.getInstance()
    private val prefs = context.getSharedPreferences("checklists", Context.MODE_PRIVATE)

    /** All lists, newest first. */
    var lists by mutableStateOf<List<ChecklistList>>(emptyList())
        private set

    /** Items per list id, in [ChecklistItem.order] (including ones waiting to be deleted). */
    private val items = mutableStateMapOf<String, List<ChecklistItem>>()

    /** Items deleted on this phone but still inside their undo window. */
    private val pendingDeletes = mutableStateListOf<String>()
    private val deleteJobs = HashMap<String, Job>()

    /** Checks waiting out [CHECK_WRITE_DELAY] ([setDoneLater]): item id → the job and its write. */
    private val doneJobs = HashMap<String, Pair<Job, () -> Unit>>()

    /**
     * Item id → when ([SystemClock.elapsedRealtime]) the other member last changed
     * it. The row glows briefly when this is recent.
     */
    val highlights = mutableStateMapOf<String, Long>()

    /** Who has which list open: account uid → (list id, server time of the note in ms). */
    private var presence by mutableStateOf<Map<String, Pair<String, Long>>>(emptyMap())

    private val _changes = MutableSharedFlow<ChecklistChange>(extraBufferCapacity = 32)

    /** The other member's changes as they arrive (see [ChecklistChange]). */
    val changes: SharedFlow<ChecklistChange> = _changes

    /** Lists whose "hide done items" switch is on, on this phone only. */
    private val hideDone = mutableStateMapOf<String, Boolean>()

    private var roomCode: String? = null
    private var scope: CoroutineScope? = null
    private var listsReg: ListenerRegistration? = null
    private val itemRegs = HashMap<String, ListenerRegistration>()
    private var presenceReg: ListenerRegistration? = null

    /** Lists whose items have loaded once (later snapshots are real changes). */
    private val loaded = HashSet<String>()

    /** Lists whose items are known to be current with the server (not just the cache). */
    private val live = HashSet<String>()

    private var viewing: String? = null
    private var heartbeat: Job? = null

    private fun room(code: String) = db.collection("rooms").document(code)
    private fun listsOf(code: String) = room(code).collection("lists")
    private fun itemsOf(code: String, listId: String) = listsOf(code).document(listId).collection("items")

    private fun report(message: String) {
        calendar.lastError = message
    }

    // ── Lifecycle ──

    /** Starts listening to the room's lists (and each list's items). */
    fun start() {
        stop()
        val code = calendar.roomCode ?: return
        // Another room (joined or recovered elsewhere): forget the old one's lists.
        if (code != roomCode) {
            lists = emptyList()
            items.clear()
            pendingDeletes.clear()
            highlights.clear()
            presence = emptyMap()
        }
        roomCode = code
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        listsReg = listsOf(code).addSnapshotListener { snap, err ->
            if (err != null) return@addSnapshotListener report("체크리스트를 불러오지 못했어요.")
            if (snap == null) return@addSnapshotListener
            val parsed = snap.documents.mapNotNull(::parseList)
                .sortedWith(compareByDescending<ChecklistList> { it.createdAtMs }.thenBy { it.id })
            lists = parsed
            val ids = parsed.mapTo(HashSet()) { it.id }
            // Follow each list's items; stop following deleted lists.
            (itemRegs.keys - ids).forEach { id ->
                itemRegs.remove(id)?.remove()
                items.remove(id)
                loaded.remove(id)
                live.remove(id)
            }
            (ids - itemRegs.keys).forEach { id -> itemRegs[id] = listenItems(code, id) }
        }
    }

    /** Stops listening. Checks and deletes still waiting are carried out now. */
    fun stop() {
        // Before the scope goes: turning the phone right after a check must not lose it.
        doneJobs.keys.toList().forEach(::commitDone)
        deleteJobs.keys.toList().forEach(::commitDelete)
        if (viewing != null) writePresence("")
        viewing = null
        heartbeat?.cancel()
        heartbeat = null
        scope?.cancel()
        scope = null
        listsReg?.remove()
        listsReg = null
        itemRegs.values.forEach { it.remove() }
        itemRegs.clear()
        presenceReg?.remove()
        presenceReg = null
        loaded.clear()
        live.clear()
        // The lists and items stay: stop() also runs when the screen is rebuilt
        // (turning the phone), and start() picks up again with what's shown.
    }

    private fun listenItems(code: String, listId: String): ListenerRegistration =
        itemsOf(code, listId).addSnapshotListener(MetadataChanges.INCLUDE) { snap, err ->
            if (err != null) return@addSnapshotListener
            if (snap == null) return@addSnapshotListener
            val wasLive = listId in live
            if (snap.metadata.isFromCache) live.remove(listId) else live.add(listId)
            // A metadata-only update (a write reaching the server, going online) changes no items.
            if (snap.documentChanges.isEmpty() && listId in loaded) return@addSnapshotListener

            val before = items[listId].orEmpty().associateBy { it.id }
            val list = snap.documents.mapNotNull { parseItem(listId, it) }
                .sortedWith(compareBy<ChecklistItem> { it.order }.thenBy { it.id })
            items[listId] = list
            // Deletions from this phone have arrived: nothing left to hide.
            snap.documentChanges
                .filter { it.type == DocumentChange.Type.REMOVED }
                .forEach { pendingDeletes.remove(it.document.id) }
            // The first load and catching up after being offline are not "news".
            val firstLoad = loaded.add(listId)
            if (firstLoad || !wasLive) return@addSnapshotListener

            var finishedBy: String? = null
            for (c in snap.documentChanges) {
                if (c.document.metadata.hasPendingWrites()) continue // our own write
                val item = parseItem(listId, c.document) ?: continue
                if (calendar.isMe(item.lastBy)) continue
                val old = before[item.id]
                val change = when (c.type) {
                    DocumentChange.Type.ADDED ->
                        if (old == null) ChecklistChange.Added(listId, item.id, item.text, item.lastBy) else null
                    DocumentChange.Type.MODIFIED -> when {
                        old == null -> null
                        !old.done && item.done -> {
                            finishedBy = item.lastBy
                            ChecklistChange.Checked(listId, item.id, item.text, item.lastBy)
                        }
                        old.done && !item.done -> ChecklistChange.Unchecked(listId, item.id, item.text, item.lastBy)
                        old.order != item.order -> ChecklistChange.Moved(listId, item.id, item.lastBy)
                        old.text != item.text -> ChecklistChange.Edited(listId, item.id, item.lastBy)
                        else -> null
                    }
                    DocumentChange.Type.REMOVED -> null
                }
                if (change != null) {
                    val now = SystemClock.elapsedRealtime()
                    // Old glows are over; forget them so the map doesn't grow all session.
                    highlights.entries.removeAll { now - it.value > HIGHLIGHT_MS }
                    highlights[item.id] = now
                    _changes.tryEmit(change)
                }
            }
            // Their check was the last one missing.
            val by = finishedBy
            if (by != null && list.isNotEmpty() && list.all { it.done } && before.values.any { !it.done }) {
                val title = lists.firstOrNull { it.id == listId }?.title.orEmpty()
                _changes.tryEmit(ChecklistChange.AllDone(listId, title, by))
            }
        }

    // ── Reading ──

    /** A list's items, without ones deleted here and waiting for "되돌리기". */
    fun itemsOf(listId: String): List<ChecklistItem> =
        items[listId].orEmpty().filter { it.id !in pendingDeletes }

    fun progressOf(listId: String): ChecklistProgress = itemsOf(listId).progress()

    /**
     * Lists still to finish, for the badge on the checklist button: lists with at
     * least one unchecked item. Empty lists don't count (nothing to do), nor do
     * lists of events that are over (an old trip's leftovers would keep the badge
     * up forever); those still show in the list of checklists.
     */
    fun openListCount(today: LocalDate): Int {
        val ended = calendar.events.filter { it.lastDate.isBefore(today) }.mapTo(HashSet()) { it.id }
        return lists.count { list ->
            val progress = progressOf(list.id)
            progress.total > 0 && !progress.complete && list.eventId !in ended
        }
    }

    /**
     * Event id → its checklists, oldest first. Worked out once per change of the
     * lists or items, not once per event row that asks ([summariesFor]).
     */
    private val summariesByEvent: Map<String, List<ChecklistSummary>> by derivedStateOf {
        lists.filter { it.eventId != null }
            .sortedWith(compareBy<ChecklistList> { it.createdAtMs }.thenBy { it.id })
            .groupBy({ it.eventId!! }) { ChecklistSummary(it.id, it.title, progressOf(it.id)) }
    }

    /** The checklists linked to [eventId], oldest first, for the event's row in the day list. */
    fun summariesFor(eventId: String): List<ChecklistSummary> = summariesByEvent[eventId].orEmpty()

    /** Other members who have [listId] open right now ([nowMs] = wall clock). */
    fun viewersOf(listId: String, nowMs: Long): List<String> =
        presence.filter { (uid, note) ->
            !calendar.isMe(uid) && note.first == listId && nowMs - note.second < PRESENCE_FRESH_MS
        }.keys.toList()

    // Read only: writing the cache here would change state while the screen is being drawn.
    fun isHidingDone(listId: String): Boolean =
        hideDone[listId] ?: prefs.getBoolean("hide_$listId", false)

    fun setHidingDone(listId: String, hide: Boolean) {
        hideDone[listId] = hide
        prefs.edit { putBoolean("hide_$listId", hide) }
    }

    // ── Presence ──

    /** Follows who is looking at which list; on while the checklist sheet is open. */
    fun watchPresence(on: Boolean) {
        presenceReg?.remove()
        presenceReg = null
        if (!on) return
        val code = roomCode ?: return
        presenceReg = room(code).collection("presence").addSnapshotListener { snap, _ ->
            if (snap == null) return@addSnapshotListener
            presence = snap.documents.associate { d ->
                d.id to ((d.getString("listId") ?: "") to (d.getTimestamp("at")?.toDate()?.time ?: 0L))
            }
        }
    }

    /** Tells the other phone which list is open here (null = none). */
    fun setViewing(listId: String?) {
        if (viewing == listId) return
        viewing = listId
        heartbeat?.cancel()
        heartbeat = null
        if (listId == null) {
            // A list was open (otherwise the check above returned): say it's closed.
            writePresence("")
            return
        }
        heartbeat = scope?.launch {
            while (isActive) {
                writePresence(listId)
                delay(HEARTBEAT)
            }
        }
    }

    private fun writePresence(listId: String) {
        val code = roomCode ?: return
        val me = calendar.uid ?: return
        room(code).collection("presence").document(me)
            .set(mapOf("listId" to listId, "at" to FieldValue.serverTimestamp()))
    }

    // ── Lists ──

    /** Creates a list and returns its id (usable at once, even offline). */
    fun createList(title: String, eventId: String? = null): String? {
        val code = roomCode ?: return null
        val me = calendar.uid ?: return null
        val name = title.trim().take(MAX_LIST_TITLE).ifEmpty { return null }
        val ref = listsOf(code).document()
        val data = mutableMapOf(
            "title" to name,
            "createdBy" to me,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (eventId != null) data["eventId"] = eventId
        ref.set(data).addOnFailureListener { report("리스트를 만들지 못했어요.") }
        return ref.id
    }

    fun renameList(list: ChecklistList, title: String) {
        val code = roomCode ?: return
        val name = title.trim().take(MAX_LIST_TITLE).ifEmpty { return }
        if (name == list.title) return
        listsOf(code).document(list.id)
            .update(mapOf("title" to name, "updatedAt" to FieldValue.serverTimestamp()))
            .addOnFailureListener { report("이름을 바꾸지 못했어요.") }
    }

    /** Links the list to an event, or unlinks it ([eventId] = null). */
    fun linkEvent(list: ChecklistList, eventId: String?) {
        val code = roomCode ?: return
        listsOf(code).document(list.id)
            .update(mapOf("eventId" to (eventId ?: FieldValue.delete()), "updatedAt" to FieldValue.serverTimestamp()))
            .addOnFailureListener { report("일정을 연결하지 못했어요.") }
    }

    /** Deletes a list and all its items, for both members. */
    fun deleteList(list: ChecklistList) {
        val code = roomCode ?: return
        // One request per item: the rules look up the room for each write, and a
        // single batch may only do a few such lookups.
        items[list.id].orEmpty().forEach { item ->
            deleteJobs.remove(item.id)?.cancel()
            pendingDeletes.remove(item.id)
            itemsOf(code, list.id).document(item.id).delete()
        }
        listsOf(code).document(list.id).delete()
            .addOnFailureListener { report("리스트를 삭제하지 못했어요.") }
        prefs.edit { remove("hide_${list.id}") }
    }

    // ── Items ──

    /** Adds an item at the end of the list. */
    fun addItem(listId: String, text: String) {
        val code = roomCode ?: return
        val me = calendar.uid ?: return
        val value = text.trim().take(MAX_ITEM_TEXT).ifEmpty { return }
        // A tiny random part keeps two items added at the same moment on both phones
        // from getting exactly the same number (which would make them unmovable
        // relative to each other).
        val order = (items[listId].orEmpty().maxOfOrNull { it.order } ?: 0.0) + 1.0 + Random.nextDouble(0.0, 0.001)
        itemsOf(code, listId).document().set(
            mapOf(
                "text" to value,
                "done" to false,
                "order" to order,
                "createdBy" to me,
                "createdAt" to FieldValue.serverTimestamp(),
                "lastBy" to me,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).addOnFailureListener { report("항목을 추가하지 못했어요.") }
    }

    /**
     * Checks or unchecks an item.
     *
     * @param doneBy who it counts as checked by; normally this phone's member, but
     *        "되돌리기" after unchecking the other member's item gives it back to them.
     */
    fun setDone(item: ChecklistItem, done: Boolean, doneBy: String? = calendar.uid) {
        val code = roomCode ?: return
        val me = calendar.uid ?: return
        val by = if (done) doneBy ?: me else null
        patch(item.listId, item.id) { it.copy(done = done, doneBy = by, lastBy = me) }
        itemsOf(code, item.listId).document(item.id).update(
            mapOf(
                "done" to done,
                "doneBy" to (by ?: FieldValue.delete()),
                "lastBy" to me,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).addOnFailureListener { report("체크를 저장하지 못했어요.") }
    }

    /**
     * [setDone] after [CHECK_WRITE_DELAY], so the check box can pop before the row
     * moves. Runs here, not in the screen's scope: the write survives the row, the
     * list or the whole screen going away meanwhile ([stop] carries it out at once).
     */
    fun setDoneLater(item: ChecklistItem, done: Boolean) {
        doneJobs.remove(item.id)?.first?.cancel()
        val write = { setDone(item, done) }
        val job = scope?.launch {
            delay(CHECK_WRITE_DELAY)
            commitDone(item.id)
        } ?: return write()
        doneJobs[item.id] = job to write
    }

    private fun commitDone(itemId: String) {
        val (job, write) = doneJobs.remove(itemId) ?: return
        job.cancel()
        write()
    }

    /** Moves an item to a new place ([order] between its new neighbors). */
    fun move(item: ChecklistItem, order: Double) {
        val code = roomCode ?: return
        val me = calendar.uid ?: return
        if (order == item.order) return
        patch(item.listId, item.id) { it.copy(order = order, lastBy = me) }
        itemsOf(code, item.listId).document(item.id).update(
            mapOf("order" to order, "lastBy" to me, "updatedAt" to FieldValue.serverTimestamp())
        ).addOnFailureListener { report("순서를 저장하지 못했어요.") }
    }

    /** Hides the item now and deletes it for both after [UNDO_WINDOW], unless [undoDelete] comes first. */
    fun deleteItemLater(item: ChecklistItem) {
        if (item.id in pendingDeletes) return
        pendingDeletes += item.id
        deleteJobs[item.id] = scope?.launch {
            delay(UNDO_WINDOW)
            commitDelete(item.id)
        } ?: return commitDelete(item.id)
    }

    fun undoDelete(itemId: String) {
        deleteJobs.remove(itemId)?.cancel()
        pendingDeletes.remove(itemId)
    }

    private fun commitDelete(itemId: String) {
        deleteJobs.remove(itemId)?.cancel()
        val code = roomCode
        val listId = items.entries.firstOrNull { (_, list) -> list.any { it.id == itemId } }?.key
        if (code != null && listId != null) {
            itemsOf(code, listId).document(itemId).delete()
                .addOnFailureListener {
                    pendingDeletes.remove(itemId)
                    report("항목을 삭제하지 못했어요.")
                }
        }
        // Stays hidden until the deletion arrives back from Firestore.
        if (listId == null) pendingDeletes.remove(itemId)
    }

    /** Applies a change to the shown items at once, before Firestore confirms it. */
    private fun patch(listId: String, itemId: String, change: (ChecklistItem) -> ChecklistItem) {
        val list = items[listId] ?: return
        items[listId] = list.map { if (it.id == itemId) change(it) else it }
            .sortedWith(compareBy<ChecklistItem> { it.order }.thenBy { it.id })
    }

    // ── Parsing ──

    private fun parseList(d: DocumentSnapshot): ChecklistList? {
        val title = d.getString("title") ?: return null
        return ChecklistList(
            id = d.id,
            title = title,
            createdBy = d.getString("createdBy").orEmpty(),
            eventId = d.getString("eventId"),
            createdAtMs = d.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?.toDate()?.time ?: 0L
        )
    }

    private fun parseItem(listId: String, d: DocumentSnapshot): ChecklistItem? {
        val text = d.getString("text") ?: return null
        return ChecklistItem(
            id = d.id,
            listId = listId,
            text = text,
            done = d.getBoolean("done") ?: false,
            doneBy = d.getString("doneBy"),
            order = (d.get("order") as? Number)?.toDouble() ?: 0.0,
            createdBy = d.getString("createdBy").orEmpty(),
            lastBy = d.getString("lastBy").orEmpty()
        )
    }

    companion object {
        /** How long a deleted item can still be brought back. */
        val UNDO_WINDOW = 4.seconds

        /** A check is written this long after the tap: the box pops first, then the row moves. */
        val CHECK_WRITE_DELAY = 200.milliseconds

        /** How often an open list tells the other phone "보는 중". */
        private val HEARTBEAT = 20.seconds

        /** A "보는 중" note older than this is stale (the app was closed or went offline). */
        private const val PRESENCE_FRESH_MS = 60_000L

        /** How long the glow on a row the other member changed lasts. */
        const val HIGHLIGHT_MS = 2_500L
    }
}
