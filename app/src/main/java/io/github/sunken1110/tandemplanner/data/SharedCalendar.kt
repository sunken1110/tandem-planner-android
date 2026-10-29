package io.github.sunken1110.tandemplanner.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Awaits a Play Services [Task] from a coroutine. */
private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
}

/**
 * Shared two-person calendar backed by Firebase.
 *
 * Firestore layout:
 * ```
 * rooms/{code}               members: [uid, uid], names: {uid: name}, createdAt
 * rooms/{code}/events/{id}   date, time, title, authorUid, authorName, createdAt
 * ```
 *
 * - Sign-in is anonymous; the 6-character room code is what links two devices.
 * - Firestore keeps an on-device cache, so reads and writes keep working offline
 *   and sync automatically once the connection returns.
 * - All public state is Compose state, so the UI recomposes on change.
 */
class SharedCalendar(context: Context) {
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

    /** Awaits a write with a timeout. Returns false and reports an error if it doesn't finish. */
    private suspend fun <T> Task<T>.awaitOnline(): Boolean {
        val done = withTimeoutOrNull(NETWORK_TIMEOUT_MS) { awaitResult(); true } != null
        if (!done) reportError(MSG_OFFLINE)
        return done
    }

    private fun room(code: String) = db.collection("rooms").document(code)

    // ─────────────────────────────────────────────
    // Auth
    // ─────────────────────────────────────────────

    private suspend fun ensureSignedIn(): Boolean {
        auth.currentUser?.let { uid = it.uid; return true }
        return try {
            val result = withTimeoutOrNull(NETWORK_TIMEOUT_MS) { auth.signInAnonymously().awaitResult() }
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
        if (!ensureSignedIn()) return false
        val me = uid ?: return false
        repeat(CREATE_ATTEMPTS) {
            val code = randomCode()
            val data = mapOf(
                "members" to listOf(me),
                "names" to mapOf(me to name),
                "createdAt" to FieldValue.serverTimestamp()
            )
            try {
                if (!room(code).set(data).awaitOnline()) return false
                saveRoom(code, name)
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
        if (!ensureSignedIn()) return false
        val me = uid ?: return false
        return try {
            val update = mapOf("members" to FieldValue.arrayUnion(me), "names.$me" to name)
            room(code).update(update).awaitOnline().also { ok -> if (ok) saveRoom(code, name) }
        } catch (e: FirebaseFirestoreException) {
            when (e.code) {
                FirebaseFirestoreException.Code.NOT_FOUND -> reportError("이 코드로 만든 달력이 없어요. 코드를 다시 확인해 주세요.")
                FirebaseFirestoreException.Code.PERMISSION_DENIED -> reportError("이미 두 명이 쓰고 있는 달력이에요.")
                else -> reportError(MSG_JOIN_FAILED, e)
            }
            false
        } catch (e: Exception) {
            reportError(MSG_JOIN_FAILED, e)
            false
        }
    }

    private fun saveRoom(code: String, name: String) {
        prefs.edit().putString(KEY_ROOM, code).putString(KEY_NAME, name).apply()
        roomCode = code
        myName = name
        lastError = null
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
            @Suppress("UNCHECKED_CAST")
            members = (snap.get("members") as? List<String>).orEmpty()
            @Suppress("UNCHECKED_CAST")
            names = (snap.get("names") as? Map<String, String>).orEmpty()
        }

        val eventsListener = room(code).collection("events")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snap, err ->
                if (err != null) return@addSnapshotListener reportError("일정을 불러오지 못했어요.", err)
                if (snap == null) return@addSnapshotListener
                offline = snap.metadata.isFromCache
                pendingWrites = snap.metadata.hasPendingWrites()
                // Missing fields fall back to defaults and unknown fields are ignored,
                // so mixed app versions can share the same data.
                val list = snap.documents.mapNotNull { d ->
                    val date = d.getString("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                        ?: return@mapNotNull null
                    Event(
                        id = d.id,
                        date = date,
                        time = d.getString("time").orEmpty(),
                        title = d.getString("title").orEmpty(),
                        authorUid = d.getString("authorUid").orEmpty(),
                        authorName = d.getString("authorName").orEmpty(),
                        editedByUid = d.getString("editedByUid"),
                        editedByName = d.getString("editedByName"),
                        original = (d.get("original") as? Map<*, *>)?.let { m ->
                            val originalDate = (m["date"] as? String)
                                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                            originalDate?.let {
                                EventSnapshot(it, m["time"] as? String ?: "", m["title"] as? String ?: "")
                            }
                        }
                    )
                }
                events.clear()
                events.addAll(list)
            }

        return listOf(roomListener, eventsListener)
    }

    // ─────────────────────────────────────────────
    // Events
    // ─────────────────────────────────────────────
    // Writes are fire-and-forget: Firestore applies them locally right away
    // (even offline) and uploads them when possible.

    fun addEvent(date: LocalDate, time: String, title: String) {
        val code = roomCode ?: return
        val me = uid ?: return
        room(code).collection("events").document()
            .set(
                mapOf(
                    "date" to date.toString(),
                    "time" to time,
                    "title" to title,
                    "authorUid" to me,
                    "authorName" to myName,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
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
    fun updateEvent(event: Event, date: LocalDate, time: String, title: String) {
        val code = roomCode ?: return
        val me = uid ?: return
        val next = EventSnapshot(date, time, title)
        val updates = mutableMapOf<String, Any>(
            "date" to date.toString(),
            "time" to time,
            "title" to title,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        val original = event.original ?: event.snapshot()
        val clearMarks = me == event.authorUid || next == original
        if (clearMarks) {
            updates["original"] = FieldValue.delete()
            updates["editedByUid"] = FieldValue.delete()
            updates["editedByName"] = FieldValue.delete()
        } else {
            updates["original"] = mapOf(
                "date" to original.date.toString(),
                "time" to original.time,
                "title" to original.title
            )
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
        prefs.edit().putString(KEY_NAME, newName).apply()
        myName = newName
        room(code).update("names.$me", newName)
            .addOnFailureListener { reportError("이름을 바꾸지 못했어요.", it) }
    }

    /** Current display name of a member, falling back to the name stored on the event. */
    fun displayName(memberUid: String, fallback: String): String =
        names[memberUid]?.takeIf { it.isNotBlank() } ?: fallback.ifBlank { "알 수 없음" }

    /** 0 for the room creator, 1 for the second member, -1 if unknown. */
    fun memberIndex(memberUid: String): Int = members.indexOf(memberUid)

    private fun randomCode(): String =
        (1..CODE_LENGTH).map { CODE_CHARS.random() }.joinToString("")

    companion object {
        const val CODE_LENGTH = 6

        /** Excludes look-alike characters (0/O, 1/I/L). */
        private const val CODE_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        private const val NETWORK_TIMEOUT_MS = 15_000L
        private const val CREATE_ATTEMPTS = 3

        private const val KEY_ROOM = "room"
        private const val KEY_NAME = "name"

        private const val MSG_OFFLINE = "인터넷 연결을 확인해 주세요."
        private const val MSG_CREATE_FAILED = "달력을 만들지 못했어요."
        private const val MSG_JOIN_FAILED = "참여하지 못했어요."
    }
}
