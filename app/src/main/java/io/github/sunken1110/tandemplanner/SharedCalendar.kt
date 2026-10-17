package io.github.sunken1110.tandemplanner

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import java.time.LocalDate
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// ─────────────────────────────────────────────
// 일정 한 건
// authorUid / authorName: 누가 쓴 일정인지
// ─────────────────────────────────────────────
data class Event(
    val id: String,
    val date: LocalDate,
    val time: String,
    val title: String,
    val authorUid: String = "",
    val authorName: String = ""
)

// ─────────────────────────────────────────────
// 예전 버전에서 폰에만 저장하던 일정 (공동 달력으로 옮길 때만 사용)
// ─────────────────────────────────────────────
class LocalEventStore(context: Context) {
    private val prefs = context.getSharedPreferences("events", Context.MODE_PRIVATE)

    fun load(): List<Event> = runCatching {
        val arr = JSONArray(prefs.getString("list", "[]"))
        (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val o = arr.getJSONObject(i)
                Event(
                    id = o.opt("id").toString(),
                    date = LocalDate.parse(o.getString("date")),
                    time = o.optString("time", ""),
                    title = o.getString("title")
                )
            }.getOrNull()
        }
    }.getOrDefault(emptyList())

    fun clear() {
        prefs.edit().remove("list").apply()
    }
}

/** 사람별 색 (먼저 들어온 사람: 보라, 두 번째: 장미색) */
val MemberColors = listOf(Color(0xFF7C5CFA), Color(0xFFE64980))
val UnknownMemberColor = Color(0xFF8A8A8A)

/** 구글 Task를 코루틴에서 기다리기 위한 도우미 */
private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
}

// ─────────────────────────────────────────────
// 공동 달력 저장소 (Firebase)
//
// 서버 구조
//   rooms/{코드}                → members: [uid, uid], names: {uid: 이름}
//   rooms/{코드}/events/{id}    → date, time, title, authorUid, authorName
//
// 인터넷이 끊겨도 Firestore가 폰에 사본을 두고 동작하며,
// 다시 연결되면 자동으로 서버와 맞춰진다.
// ─────────────────────────────────────────────
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

    /** 서버와 연결이 안 돼서 폰에 저장된 사본을 보여 주는 중인지 */
    var offline by mutableStateOf(false)
        private set
    /** 아직 서버에 올라가지 못한 변경이 있는지 */
    var pendingWrites by mutableStateOf(false)
        private set
    var lastError by mutableStateOf<String?>(null)

    /** 방금 달력을 만들었으면 true → 코드 공유 창을 한 번 띄운다 */
    var justCreated by mutableStateOf(false)

    // ── 로그인 (익명) ──
    suspend fun ensureSignedIn(): Boolean {
        auth.currentUser?.let { uid = it.uid; return true }
        return try {
            val result = withTimeoutOrNull(15_000) { auth.signInAnonymously().awaitResult() }
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

    // ── 새 달력 만들기 ──
    suspend fun createRoom(name: String): Boolean {
        if (!ensureSignedIn()) return false
        val me = uid ?: return false
        repeat(3) {
            val code = randomCode()
            val data = mapOf(
                "members" to listOf(me),
                "names" to mapOf(me to name),
                "createdAt" to FieldValue.serverTimestamp()
            )
            try {
                // 성공하면 true, 15초 안에 끝나지 않으면 null
                val ok = withTimeoutOrNull(15_000) {
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
                // 아주 드물게 같은 코드가 이미 있으면 다른 코드로 다시 시도
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

    // ── 코드로 참여하기 ──
    suspend fun joinRoom(rawCode: String, name: String): Boolean {
        val code = rawCode.trim().uppercase()
        if (code.length != 6) {
            lastError = "코드 6자리를 정확히 입력해 주세요."
            return false
        }
        if (!ensureSignedIn()) return false
        val me = uid ?: return false
        return try {
            val ok = withTimeoutOrNull(15_000) {
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

    // ── 실시간 듣기: 달력 정보 + 일정 목록 ──
    // 화면이 닫힐 때 remove()로 꼭 해제해야 한다.
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

    // ── 일정 추가 / 삭제 ──
    // 서버 응답을 기다리지 않는다. 오프라인이어도 화면에는 바로 반영되고,
    // 연결되면 Firestore가 알아서 서버로 올린다.
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

    // ── 내 이름 바꾸기 ──
    fun rename(newName: String) {
        val code = roomCode ?: return
        val me = uid ?: return
        prefs.edit().putString("name", newName).apply()
        myName = newName
        db.collection("rooms").document(code).update("names.$me", newName)
            .addOnFailureListener { lastError = "이름을 바꾸지 못했어요. (${it.message})" }
    }

    // ── 예전에 폰에만 저장한 일정 옮기기 ──
    fun pendingLocalEvents(): List<Event> =
        if (prefs.getBoolean("migrated", false)) emptyList() else localStore.load()

    fun migrateLocalEvents(upload: Boolean) {
        if (upload) {
            localStore.load().forEach { addEvent(it.date, it.time, it.title) }
            localStore.clear()
        }
        prefs.edit().putBoolean("migrated", true).apply()
    }

    // ── 표시용 도우미 ──
    fun displayName(authorUid: String, fallback: String): String =
        names[authorUid]?.takeIf { it.isNotBlank() } ?: fallback.ifBlank { "알 수 없음" }

    fun colorOf(authorUid: String): Color {
        val i = members.indexOf(authorUid)
        return if (i in MemberColors.indices) MemberColors[i] else UnknownMemberColor
    }

    private fun randomCode(): String {
        // 헷갈리는 글자(0/O, 1/I/L)는 뺐다
        val chars = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }
}