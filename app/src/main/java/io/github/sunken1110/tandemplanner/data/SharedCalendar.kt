package io.github.sunken1110.tandemplanner.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.edit
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.sunken1110.tandemplanner.MAX_EVENT_TITLE
import io.github.sunken1110.tandemplanner.currentDate
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDate
import kotlin.random.asKotlinRandom
import kotlin.time.Duration.Companion.seconds
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Awaits a Play Services [Task] from a coroutine (a cancelled task cancels the wait). */
private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}

/**
 * Shared two-person calendar backed by Firebase.
 *
 * Firestore layout:
 * ```
 * rooms/{code}               members: [uid, uid], names: {uid: name}, createdAt,
 *                            recovery: {uid: sha256(secret)}, formerUids: {oldUid: newUid}
 * rooms/{code}/events/{id}   date, time, title, authorUid, authorName, createdAt, …
 * rooms/{code}/claims/{uid}  seat, secret   (short-lived, during a seat recovery)
 * ```
 *
 * - Sign-in is anonymous; the 6-character room code is what links two devices.
 * - Reinstalling the app creates a new anonymous account. Each member therefore
 *   keeps a personal recovery code ("1-ABCD-EFGH-JKLM": seat + secret); with it a
 *   new account can take over the old seat ([recoverSeat]). `formerUids` maps old
 *   accounts to their successors, so events written before reinstalling still
 *   count as the same person (color, name, author rights) — see [currentUid].
 * - Firestore keeps an on-device cache, so reads and writes keep working offline
 *   and sync automatically once the connection returns.
 * - All public state is Compose state, so the UI recomposes on change.
 */
class SharedCalendar(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = context.getSharedPreferences("shared_calendar", Context.MODE_PRIVATE)
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    var uid by mutableStateOf(auth.currentUser?.uid)
        private set
    var roomCode by mutableStateOf(prefs.getString(KEY_ROOM, null))
        private set
    var myName by mutableStateOf(prefs.getString(KEY_NAME, "") ?: "")
        private set

    val events = mutableStateListOf<Event>()
    var members by mutableStateOf<List<String>>(emptyList())
        private set
    private var names by mutableStateOf<Map<String, String>>(emptyMap())

    /** Old account → the account that took over its seat (after reinstalling). */
    private var formerUids by mutableStateOf<Map<String, String>>(emptyMap())

    /** Members that have a recovery code registered on the server. */
    private var recoveryOwners by mutableStateOf<Set<String>>(emptySet())

    /** This member's recovery code, as shown to them (kept only on this phone). */
    var recoveryCode by mutableStateOf(prefs.getString(KEY_RECOVERY, null))
        private set

    /** True when this member has a recovery code that the server also knows. */
    val hasRecoveryCode: Boolean get() = recoveryCode != null && uid in recoveryOwners

    /** Set right after recovering a seat, so the UI can show the new recovery code. */
    var justRecovered by mutableStateOf(false)

    /** True while the UI is showing cached data because the server is unreachable. */
    var offline by mutableStateOf(false)
        private set

    /** True while local changes are waiting to be uploaded. */
    var pendingWrites by mutableStateOf(false)
        private set

    /** Last user-facing error message, or null. The UI clears it after showing. */
    var lastError by mutableStateOf<String?>(null)

    /** Set right after creating a room so the UI can open the share dialog once. */
    var justCreated by mutableStateOf(false)

    private fun reportError(message: String, cause: Exception? = null) {
        lastError = if (cause == null) message else "$message (${cause.message})"
    }

    /** Waits for a Firestore write to finish, with a timeout. Returns false and reports an error if it doesn't. */
    private suspend fun <T> Task<T>.awaitOnline(): Boolean {
        val done = withTimeoutOrNull(NETWORK_TIMEOUT) { awaitResult(); true } != null
        if (!done) reportError(MSG_OFFLINE)
        return done
    }

    private fun room(code: String) = db.collection("rooms").document(code)

    /**
     * True when the phone has a working internet connection.
     *
     * Checked before creating or joining a room: Firestore queues writes made
     * offline and sends them later. So even after creating or joining a room
     * "timed out", it could still happen behind the user's back (an orphan room,
     * or a seat taken in a room whose code was never saved). Defaults to true if
     * the check itself fails.
     */
    private fun isOnline(): Boolean = runCatching {
        val cm = appContext.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    }.getOrDefault(true)

    /** A string field, or null when it is missing or has another type. */
    private fun DocumentSnapshot.str(field: String): String? = get(field) as? String

    // ─────────────────────────────────────────────
    // Auth
    // ─────────────────────────────────────────────

    private suspend fun ensureSignedIn(): Boolean {
        auth.currentUser?.let { uid = it.uid; return true }
        return try {
            val result = withTimeoutOrNull(NETWORK_TIMEOUT) { auth.signInAnonymously().awaitResult() }
            uid = result?.user?.uid
            if (result == null) reportError(MSG_OFFLINE)
            uid != null
        } catch (e: Exception) {
            lastError = "로그인하지 못했어요. Firebase 콘솔에서 '익명' 로그인이 켜져 있는지 확인해 주세요.\n(${e.message})"
            false
        }
    }

    // ─────────────────────────────────────────────
    // Rooms
    // ─────────────────────────────────────────────

    /** Creates a new room with the current user as its only member. */
    suspend fun createRoom(name: String): Boolean {
        if (!isOnline()) {
            reportError(MSG_OFFLINE)
            return false
        }
        if (!ensureSignedIn()) return false
        val me = uid ?: return false
        repeat(CREATE_ATTEMPTS) {
            val code = randomCode()
            val secret = newRecoverySecret()
            val data = mapOf(
                "members" to listOf(me),
                "names" to mapOf(me to name),
                "recovery" to mapOf(me to recoveryHash(secret)),
                "createdAt" to FieldValue.serverTimestamp()
            )
            try {
                if (!room(code).set(data).awaitOnline()) return false
                saveRoom(code, name)
                saveRecoveryCode(seat = 0, secret = secret)
                justCreated = true
                return true
            } catch (e: FirebaseFirestoreException) {
                // PERMISSION_DENIED here means the code is already taken → try another one.
                if (e.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    reportError(MSG_CREATE_FAILED, e)
                    return false
                }
            } catch (e: Exception) {
                reportError(MSG_CREATE_FAILED, e)
                return false
            }
        }
        reportError("$MSG_CREATE_FAILED 잠시 후 다시 시도해 주세요.")
        return false
    }

    /** Joins an existing room by its code. Security rules cap rooms at two members. */
    suspend fun joinRoom(rawCode: String, name: String): Boolean {
        val code = rawCode.trim().uppercase()
        if (code.length != CODE_LENGTH) {
            reportError("코드 ${CODE_LENGTH}자리를 정확히 입력해 주세요.")
            return false
        }
        if (!isOnline()) {
            reportError(MSG_OFFLINE)
            return false
        }
        if (!ensureSignedIn()) return false
        val me = uid ?: return false
        val secret = newRecoverySecret()
        return try {
            val update = mapOf(
                "members" to FieldValue.arrayUnion(me),
                "names.$me" to name,
                "recovery.$me" to recoveryHash(secret)
            )
            val ok = room(code).update(update).awaitOnline()
            if (ok) {
                saveRoom(code, name)
                // Usually seat 1; read it to be sure (e.g. rejoining with the same account).
                val seat = runCatching {
                    withTimeoutOrNull(NETWORK_TIMEOUT) { room(code).get(Source.SERVER).awaitResult() }
                        ?.let { (it.get("members") as? List<*>)?.indexOf(me) }
                }.getOrNull()?.takeIf { it >= 0 } ?: 1
                saveRecoveryCode(seat, secret)
            }
            ok
        } catch (e: FirebaseFirestoreException) {
            when (e.code) {
                FirebaseFirestoreException.Code.NOT_FOUND -> reportError("이 코드로 만든 달력이 없어요. 코드를 다시 확인해 주세요.")
                // The rules also deny a join to a code that doesn't exist, so this
                // can mean either case.
                FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                    reportError("참여하지 못했어요. 코드를 다시 확인해 주세요. 이미 두 명이 쓰고 있는 달력일 수도 있어요.")
                else -> reportError(MSG_JOIN_FAILED, e)
            }
            false
        } catch (e: Exception) {
            reportError(MSG_JOIN_FAILED, e)
            false
        }
    }

    private fun saveRoom(code: String, name: String) {
        prefs.edit {
            putString(KEY_ROOM, code)
            putString(KEY_NAME, name)
        }
        roomCode = code
        myName = name
        lastError = null
    }

    // ─────────────────────────────────────────────
    // Recovery codes
    // ─────────────────────────────────────────────

    /**
     * Creates (or replaces) this member's recovery code, e.g. for members who joined
     * before recovery codes existed, or to retire a code that may have leaked.
     * Only its hash goes to the server; the code itself is kept on this phone.
     */
    suspend fun createRecoveryCode(): Boolean {
        val code = roomCode ?: return false
        val me = uid ?: return false
        val seat = members.indexOf(me).takeIf { it >= 0 } ?: return false
        if (!isOnline()) {
            reportError(MSG_OFFLINE)
            return false
        }
        val secret = newRecoverySecret()
        return try {
            room(code).update("recovery.$me", recoveryHash(secret)).awaitOnline()
                .also { ok -> if (ok) saveRecoveryCode(seat, secret) }
        } catch (e: Exception) {
            reportError("복구 코드를 만들지 못했어요.", e)
            false
        }
    }

    /**
     * Takes back a seat after reinstalling, with the room code and that seat's
     * recovery code. Steps (each checked by the security rules):
     * 1. Prove the code: write it to `claims/{me}`; the rules only accept it if it
     *    matches the seat's stored hash.
     * 2. With that proof, read the room and replace the old account in that seat
     *    with this one: its name moves over, the old recovery hash is replaced by a
     *    new one (the used code stops working), and `formerUids` records old → new.
     * 3. Delete the claim.
     */
    suspend fun recoverSeat(rawCode: String, rawRecovery: String): Boolean {
        val code = rawCode.trim().uppercase()
        if (code.length != CODE_LENGTH) {
            reportError("코드 ${CODE_LENGTH}자리를 정확히 입력해 주세요.")
            return false
        }
        val parsed = parseRecoveryCode(rawRecovery) ?: run {
            reportError("복구 코드를 정확히 입력해 주세요. (예: 1-ABCD-EFGH-JKMN)")
            return false
        }
        val (seat, secret) = parsed
        if (!isOnline()) {
            reportError(MSG_OFFLINE)
            return false
        }
        if (!ensureSignedIn()) return false
        val me = uid ?: return false
        val claim = room(code).collection("claims").document(me)

        // 1. Prove the code.
        try {
            if (!claim.set(mapOf("seat" to seat, "secret" to secret)).awaitOnline()) return false
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                reportError("자리를 되찾지 못했어요. 방 코드와 복구 코드를 확인해 주세요.")
            } else {
                reportError(MSG_RECOVER_FAILED, e)
            }
            return false
        } catch (e: Exception) {
            reportError(MSG_RECOVER_FAILED, e)
            return false
        }

        return try {
            // 2. Take over the seat.
            val snap = withTimeoutOrNull(NETWORK_TIMEOUT) { room(code).get(Source.SERVER).awaitResult() }
                ?: run { reportError(MSG_OFFLINE); return false }
            val roomMembers = (snap.get("members") as? List<*>).orEmpty().filterIsInstance<String>()
            val old = roomMembers.getOrNull(seat) ?: run { reportError(MSG_RECOVER_FAILED); return false }
            val roomNames = (snap.get("names") as? Map<*, *>).orEmpty()
            val name = (roomNames[old] as? String)?.takeIf { it.isNotBlank() }
                ?: myName.takeIf { it.isNotBlank() } ?: "나"
            if (old != me) {
                val newSecret = newRecoverySecret()
                val newMembers = roomMembers.toMutableList().also { it[seat] = me }
                val updates = mapOf(
                    "members" to newMembers,
                    "names.$old" to FieldValue.delete(),
                    "names.$me" to name,
                    "recovery.$old" to FieldValue.delete(),
                    "recovery.$me" to recoveryHash(newSecret),
                    "formerUids.$old" to me
                )
                if (!room(code).update(updates).awaitOnline()) return false
                saveRecoveryCode(seat, newSecret)
            }
            saveRoom(code, name)
            justRecovered = true
            true
        } catch (e: Exception) {
            reportError(MSG_RECOVER_FAILED, e)
            false
        } finally {
            // 3. The claim has done its job (or failed); remove it either way.
            claim.delete()
        }
    }

    private fun saveRecoveryCode(seat: Int, secret: String) {
        val display = formatRecoveryCode(seat, secret)
        prefs.edit { putString(KEY_RECOVERY, display) }
        recoveryCode = display
        // Saving has succeeded; don't wait for the room snapshot to say so.
        uid?.let { recoveryOwners = recoveryOwners + it }
    }

    // ─────────────────────────────────────────────
    // Real-time sync
    // ─────────────────────────────────────────────

    /**
     * Starts listening to the room document and its events.
     * The caller must call [ListenerRegistration.remove] on each result when done.
     */
    fun startListening(): List<ListenerRegistration> {
        val code = roomCode ?: return emptyList()

        val roomListener = room(code).addSnapshotListener { snap, err ->
            if (err != null) return@addSnapshotListener reportError("달력 정보를 불러오지 못했어요.", err)
            if (snap == null || !snap.exists()) return@addSnapshotListener
            applyRoom(snap)
        }

        val eventsListener = room(code).collection("events")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snap, err ->
                if (err != null) return@addSnapshotListener reportError("일정을 불러오지 못했어요.", err)
                if (snap == null) return@addSnapshotListener
                offline = snap.metadata.isFromCache
                pendingWrites = snap.metadata.hasPendingWrites()
                // A metadata-only update (e.g. a saved change reaching the server) changes no
                // events; skip rebuilding the list, which would regroup and re-sort
                // everything and recompose every day cell.
                if (snap.documentChanges.isEmpty() && events.isNotEmpty()) return@addSnapshotListener
                val list = snap.documents.mapNotNull(::parseEvent)
                events.clear()
                events.addAll(list)
            }

        return listOf(roomListener, eventsListener)
    }

    /**
     * Reads the room and its events once, without listening. Used in the background
     * (the home-screen widget's hourly refresh), where no screen keeps listeners open.
     * Only events that can still matter to the widget are read: those starting on or
     * after [MAX_EVENT_DAYS] days ago (a trip that started earlier is already over),
     * so an hourly refresh doesn't re-read the whole history.
     * Returns false when there is no room or sign-in, or nothing could be read in time
     * (offline, Firestore answers from its cache instead).
     */
    suspend fun refreshOnce(): Boolean {
        val code = roomCode ?: return false
        auth.currentUser ?: return false
        return try {
            val roomSnap = withTimeoutOrNull(NETWORK_TIMEOUT) { room(code).get().awaitResult() }
                ?: return false
            if (!roomSnap.exists()) return false
            applyRoom(roomSnap)
            val eventsSnap = withTimeoutOrNull(NETWORK_TIMEOUT) {
                room(code).collection("events")
                    .whereGreaterThanOrEqualTo("date", currentDate().minusDays(MAX_EVENT_DAYS.toLong()).toString())
                    .get()
                    .awaitResult()
            } ?: return false
            val list = eventsSnap.documents.mapNotNull(::parseEvent)
            events.clear()
            events.addAll(list)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Takes the members, names and recovery info from a room snapshot. */
    private fun applyRoom(snap: DocumentSnapshot) {
        // Read defensively: a value of an unexpected type is skipped, never a crash.
        members = (snap.get("members") as? List<*>).orEmpty().filterIsInstance<String>()
        names = (snap.get("names") as? Map<*, *>).orEmpty().entries
            .mapNotNull { (k, v) -> if (k is String && v is String) k to v else null }
            .toMap()
        formerUids = (snap.get("formerUids") as? Map<*, *>).orEmpty().entries
            .mapNotNull { (k, v) -> if (k is String && v is String) k to v else null }
            .toMap()
        recoveryOwners = (snap.get("recovery") as? Map<*, *>).orEmpty().keys
            .filterIsInstance<String>().toSet()
    }

    /**
     * One event document → [Event], or null if it has no valid date.
     *
     * Missing fields fall back to defaults and unknown fields are ignored, so mixed
     * app versions can share the same data. Fields are read with `str()` rather than
     * getString(), which throws on a non-string value: one malformed document must
     * not crash the app on every launch.
     */
    private fun parseEvent(d: DocumentSnapshot): Event? {
        val date = d.str("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        return Event(
            id = d.id,
            date = date,
            time = d.str("time").orEmpty(),
            title = d.str("title").orEmpty(),
            authorUid = d.str("authorUid").orEmpty(),
            authorName = d.str("authorName").orEmpty(),
            editedByUid = d.str("editedByUid"),
            editedByName = d.str("editedByName"),
            original = (d.get("original") as? Map<*, *>)?.let { m ->
                val originalDate = (m["date"] as? String)
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                originalDate?.let {
                    EventSnapshot(
                        it,
                        m["time"] as? String ?: "",
                        m["title"] as? String ?: "",
                        (m["endDate"] as? String)?.parseEndDate(after = it)
                    )
                }
            },
            endDate = d.str("endDate")?.parseEndDate(after = date)
        )
    }

    // ─────────────────────────────────────────────
    // Events
    // ─────────────────────────────────────────────
    // Writes are fire-and-forget: Firestore applies them locally right away
    // (even offline) and uploads them when possible.

    /**
     * Adds an event. With [endDate] (after [date]) it is a multi-day event, which is
     * always all-day, so [time] is ignored then.
     */
    fun addEvent(date: LocalDate, time: String, title: String, endDate: LocalDate? = null) {
        val code = roomCode ?: return
        val me = uid ?: return
        val end = endDate?.takeIf { it.isAfter(date) }
        val data = mutableMapOf(
            "date" to date.toString(),
            "time" to if (end != null) "" else time,
            "title" to title.fitTitle(),
            "authorUid" to me,
            "authorName" to myName,
            "createdAt" to FieldValue.serverTimestamp()
        )
        // Only multi-day events have the field, so one-day events look exactly as before.
        if (end != null) data["endDate"] = end.toString()
        room(code).collection("events").document()
            .set(data)
            .addOnFailureListener { reportError("일정을 저장하지 못했어요.", it) }
    }

    /**
     * Changes an event's date, time and title. The author never changes, so the
     * event keeps the author's color.
     *
     * Edit history (all fields are additive; older app versions ignore them):
     * - The other member edits → `editedByUid`/`editedByName` record who, and
     *   `original` stores the author's version. It is written only on the first
     *   such edit, so repeated edits still point back to what the author wrote.
     * - That member changes it back to exactly the original → the marks are removed.
     * - The author edits → their new version is the one that counts, so the marks
     *   are removed too.
     */
    fun updateEvent(event: Event, date: LocalDate, time: String, title: String, endDate: LocalDate? = null) {
        val code = roomCode ?: return
        val me = uid ?: return
        val end = endDate?.takeIf { it.isAfter(date) }
        val storedTime = if (end != null) "" else time
        val name = title.fitTitle()
        val next = EventSnapshot(date, storedTime, name, end)
        val updates = mutableMapOf(
            "date" to date.toString(),
            "time" to storedTime,
            "title" to name,
            // Becoming a one-day event removes the field instead of storing a null.
            "endDate" to (end?.toString() ?: FieldValue.delete()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        val original = event.original ?: event.snapshot()
        val clearMarks = isMyEvent(event.authorUid) || next == original
        if (clearMarks) {
            updates["original"] = FieldValue.delete()
            updates["editedByUid"] = FieldValue.delete()
            updates["editedByName"] = FieldValue.delete()
        } else {
            updates["original"] = buildMap {
                put("date", original.date.toString())
                put("time", original.time)
                put("title", original.title)
                // Same shape as the event itself: `endDate` only for a multi-day original.
                original.endDate?.let { put("endDate", it.toString()) }
            }
            updates["editedByUid"] = me
            updates["editedByName"] = myName
        }
        room(code).collection("events").document(event.id)
            .update(updates)
            .addOnFailureListener { reportError("일정을 수정하지 못했어요.", it) }
    }

    fun deleteEvent(event: Event) {
        val code = roomCode ?: return
        room(code).collection("events").document(event.id)
            .delete()
            .addOnFailureListener { reportError("일정을 지우지 못했어요.", it) }
    }

    // ─────────────────────────────────────────────
    // Members
    // ─────────────────────────────────────────────

    fun rename(newName: String) {
        val code = roomCode ?: return
        val me = uid ?: return
        prefs.edit { putString(KEY_NAME, newName) }
        myName = newName
        room(code).update("names.$me", newName)
            .addOnFailureListener { reportError("이름을 바꾸지 못했어요.", it) }
    }

    /**
     * The account currently in [accountUid]'s seat: the account itself, or the one
     * that took it over after reinstalling (following any chain of recoveries).
     */
    fun currentUid(accountUid: String): String {
        var current = accountUid
        repeat(MAX_ALIAS_HOPS) { current = formerUids[current] ?: return current }
        return current
    }

    /** True when [accountUid] is this member (now or before reinstalling). */
    fun isMe(accountUid: String): Boolean = uid != null && currentUid(accountUid) == uid

    /**
     * Whether the security rules treat this member as the author of an event by
     * [authorUid]. Same as [isMe], but the rules only follow [RULES_AUTHOR_HOPS]
     * recoveries, so this must not follow more (or an edit would be refused).
     */
    private fun isMyEvent(authorUid: String): Boolean {
        val me = uid ?: return false
        var current = authorUid
        repeat(RULES_AUTHOR_HOPS + 1) {
            if (current == me) return true
            current = formerUids[current] ?: return false
        }
        return false
    }

    /** Current display name of a member, falling back to the name stored on the event. */
    fun displayName(memberUid: String, fallback: String): String =
        names[currentUid(memberUid)]?.takeIf { it.isNotBlank() } ?: fallback.ifBlank { "알 수 없음" }

    /** 0 for the room creator, 1 for the second member, -1 if unknown. */
    fun memberIndex(memberUid: String): Int = members.indexOf(currentUid(memberUid))

    /** A stored `endDate`, accepted only when it is a valid day after [after]. */
    private fun String.parseEndDate(after: LocalDate): LocalDate? =
        runCatching { LocalDate.parse(this) }.getOrNull()?.takeIf { it.isAfter(after) }

    /** A title the security rules accept: trimmed, at most [MAX_EVENT_TITLE] characters. */
    private fun String.fitTitle(): String = trim().takeWhole(MAX_EVENT_TITLE)

    /** A new room code. Knowing it is enough to join a room with one member, so it's unguessable. */
    private fun randomCode(): String =
        (1..CODE_LENGTH).map { CODE_CHARS.random(secureRandom) }.joinToString("")

    /** A new random recovery secret (12 characters from [CODE_CHARS], ~59 bits). */
    private fun newRecoverySecret(): String =
        (1..RECOVERY_SECRET_LENGTH).map { CODE_CHARS.random(secureRandom) }.joinToString("")

    /** SHA-256 of the secret, stored as bytes (what the security rules compare against). */
    private fun recoveryHash(secret: String): Blob =
        Blob.fromBytes(MessageDigest.getInstance("SHA-256").digest(secret.toByteArray(Charsets.UTF_8)))

    /** "1-ABCD-EFGH-JKMN": seat number (1 or 2), then the secret in groups of four. */
    private fun formatRecoveryCode(seat: Int, secret: String): String =
        "${seat + 1}-" + secret.chunked(4).joinToString("-")

    /** Parses what the user typed (spaces, hyphens and case are ignored) into seat + secret. */
    private fun parseRecoveryCode(raw: String): Pair<Int, String>? {
        val clean = raw.uppercase().filter { it.isLetterOrDigit() }
        if (clean.length != RECOVERY_SECRET_LENGTH + 1) return null
        val seat = when (clean[0]) { '1' -> 0; '2' -> 1; else -> return null }
        val secret = clean.substring(1)
        if (secret.any { it !in CODE_CHARS }) return null
        return seat to secret
    }

    companion object {
        const val CODE_LENGTH = 6

        /** Excludes look-alike characters (0/O, 1/I/L). */
        private const val CODE_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        private val NETWORK_TIMEOUT = 15.seconds
        private const val CREATE_ATTEMPTS = 3

        private const val KEY_ROOM = "room"
        private const val KEY_NAME = "name"
        private const val KEY_RECOVERY = "recovery"

        private const val RECOVERY_SECRET_LENGTH = 12

        /** Recoveries followed when resolving an old account (more than enough). */
        private const val MAX_ALIAS_HOPS = 8

        /** Recoveries the security rules follow for event authorship (firestore.rules `isAuthor`). */
        private const val RULES_AUTHOR_HOPS = 2

        private val secureRandom = SecureRandom().asKotlinRandom()

        private const val MSG_OFFLINE = "인터넷 연결을 확인해 주세요."
        private const val MSG_CREATE_FAILED = "달력을 만들지 못했어요."
        private const val MSG_JOIN_FAILED = "참여하지 못했어요."
        private const val MSG_RECOVER_FAILED = "자리를 되찾지 못했어요."
    }
}
