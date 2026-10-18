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
    private val localStore = LocalEventStore(context)

    var uid by mutableStateOf(auth.currentUser?.uid)
        private set
    var roomCode by mutableStateOf(prefs.getString("room", null))
        private set
    var myName by mutableStateOf(prefs.getString("name", "") ?: "")
        private set

    val events = mutableStateListOf<Event>()
    var members by mutableStateOf<List<String>>(emptyList())
        private set
    var names by mutableStateOf<Map<String, String>>(emptyMap())
        private set

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

    // ─────────────────────────────────────────────
    // Auth
    // ─────────────────────────────────────────────

    suspend fun ensureSignedIn(): Boolean {
        auth.currentUser?.let { uid = it.uid; return true }
        return try {
            val result = withTimeoutOrNull(NETWORK_TIMEOUT_MS) { auth.signInAnonymously().awaitResult() }
            if (result == null) {
                lastError = "인터넷 연결을 확인해 주세요."
                false
            } else {
                uid = result.user?.uid
                uid != null
            }
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
                // `true` on success, `null` on timeout (set() itself returns Void).
                val ok = withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
                    db.collection("rooms").document(code).set(data).awaitResult()
                    true
                }
                if (ok == null) {
                    lastError = "인터넷 연결을 확인해 주세요."
                    return false
                }
                saveRoom(code, name)
                justCreated = true
                return true
            } catch (e: FirebaseFirestoreException) {
                // PERMISSION_DENIED here almost always means the code already exists → retry.
                if (e.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    lastError = "달력을 만들지 못했어요. (${e.message})"
                    return false
                }
            } catch (e: Exception) {
                lastError = "달력을 만들지 못했어요. (${e.message})"
                return false
            }
        }
        lastError = "달력을 만들지 못했어요. 잠시 후 다시 시도해 주세요."
        return false
    }

    /** Joins an existing room by its 6-character code. Security rules cap rooms at two members. */
    suspend fun joinRoom(rawCode: String, name: String): Boolean {
        val code = rawCode.trim().uppercase()
        if (code.length != CODE_LENGTH) {
            lastError = "코드 6자리를 정확히 입력해 주세요."
            return false
        }
        if (!ensureSignedIn()) return false
        val me = uid ?: return false
        return try {
            val ok = withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
                db.collection("rooms").document(code).update(
                    mapOf(
                        "members" to FieldValue.arrayUnion(me),
                        "names.$me" to name
                    )
                ).awaitResult()
                true
            }
            if (ok == null) {
                lastError = "인터넷 연결을 확인해 주세요."
                false
            } else {
                saveRoom(code, name)
                true
            }
        } catch (e: FirebaseFirestoreException) {
            lastError = when (e.code) {
                FirebaseFirestoreException.Code.NOT_FOUND -> "이 코드로 만든 달력이 없어요. 코드를 다시 확인해 주세요."
                FirebaseFirestoreException.Code.PERMISSION_DENIED -> "이미 두 명이 쓰고 있는 달력이에요."
                else -> "참여하지 못했어요. (${e.message})"
            }
            false
        } catch (e: Exception) {
            lastError = "참여하지 못했어요. (${e.message})"
            false
        }
    }

    private fun saveRoom(code: String, name: String) {
        prefs.edit().putString("room", code).putString("name", name).apply()
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
        val room = db.collection("rooms").document(code)

        val roomListener = room.addSnapshotListener { snap, err ->
            if (err != null) {
                lastError = "달력 정보를 불러오지 못했어요. (${err.message})"
                return@addSnapshotListener
            }
            if (snap != null && snap.exists()) {
                @Suppress("UNCHECKED_CAST")
                members = (snap.get("members") as? List<String>).orEmpty()
                @Suppress("UNCHECKED_CAST")
                names = (snap.get("names") as? Map<String, String>).orEmpty()
            }
        }

        val eventsListener = room.collection("events")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snap, err ->
                if (err != null) {
                    lastError = "일정을 불러오지 못했어요. (${err.message})"
                    return@addSnapshotListener
                }
                if (snap == null) return@addSnapshotListener
                offline = snap.metadata.isFromCache
                pendingWrites = snap.metadata.hasPendingWrites()
                // Missing fields fall back to defaults and unknown fields are ignored,
                // so mixed app versions can share the same data.
                val list = snap.documents.mapNotNull { d ->
                    val date = d.getString("date")?.let {
                        runCatching { LocalDate.parse(it) }.getOrNull()
                    } ?: return@mapNotNull null
                    Event(
                        id = d.id,
                        date = date,
                        time = d.getString("time").orEmpty(),
                        title = d.getString("title").orEmpty(),
                        authorUid = d.getString("authorUid").orEmpty(),
                        authorName = d.getString("authorName").orEmpty()
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
        db.collection("rooms").document(code).collection("events").document()
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
            .addOnFailureListener { lastError = "일정을 저장하지 못했어요. (${it.message})" }
    }

    fun deleteEvent(event: Event) {
        val code = roomCode ?: return
        db.collection("rooms").document(code).collection("events").document(event.id)
            .delete()
            .addOnFailureListener { lastError = "일정을 지우지 못했어요. (${it.message})" }
    }

    // ─────────────────────────────────────────────
    // Members
    // ─────────────────────────────────────────────

    fun rename(newName: String) {
        val code = roomCode ?: return
        val me = uid ?: return
        prefs.edit().putString("name", newName).apply()
        myName = newName
        db.collection("rooms").document(code).update("names.$me", newName)
            .addOnFailureListener { lastError = "이름을 바꾸지 못했어요. (${it.message})" }
    }

    /** Current display name of a member, falling back to the name stored on the event. */
    fun displayName(authorUid: String, fallback: String): String =
        names[authorUid]?.takeIf { it.isNotBlank() } ?: fallback.ifBlank { "알 수 없음" }

    /** 0 for the room creator, 1 for the second member, -1 if unknown. */
    fun memberIndex(uid: String): Int = members.indexOf(uid)

    // ─────────────────────────────────────────────
    // Migration from on-device storage
    // ─────────────────────────────────────────────

    /** Events from the old local-only version that have not been migrated yet. */
    fun pendingLocalEvents(): List<Event> =
        if (prefs.getBoolean("migrated", false)) emptyList() else localStore.load()

    /** Uploads (or discards) old local events, then never asks again. */
    fun migrateLocalEvents(upload: Boolean) {
        if (upload) {
            localStore.load().forEach { addEvent(it.date, it.time, it.title) }
            localStore.clear()
        }
        prefs.edit().putBoolean("migrated", true).apply()
    }

    private fun randomCode(): String =
        (1..CODE_LENGTH).map { CODE_CHARS.random() }.joinToString("")

    private companion object {
        const val CODE_LENGTH = 6
        /** Excludes look-alike characters (0/O, 1/I/L). */
        const val CODE_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        const val NETWORK_TIMEOUT_MS = 15_000L
        const val CREATE_ATTEMPTS = 3
    }
}
