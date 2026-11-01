package io.github.sunken1110.tandemplanner.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.github.sunken1110.tandemplanner.data.Event
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** An event as the widget needs it: the event, its author's name and color. */
data class WidgetItem(val event: Event, val author: String, val color: Color)

/**
 * What the widget shows, saved by the app (or the background refresh).
 *
 * @property linked false when this phone hasn't joined a shared calendar yet.
 */
data class WidgetState(val linked: Boolean, val items: List<WidgetItem>)

/**
 * The widget's own copy of the upcoming events, kept in a small preferences file.
 *
 * A home-screen widget doesn't run like an app: it is drawn on request, and must
 * not wait for the network while drawing. So the app writes a snapshot here
 * whenever its events change, and the widget only reads it. The widget decides
 * "today", D-days and what has passed at the moment it is drawn, so the saved
 * snapshot stays valid for days.
 */
object WidgetStore {
    private const val PREFS = "widget"
    private const val KEY_LINKED = "linked"
    private const val KEY_ITEMS = "items"

    /** Events kept for the widget, from yesterday on; more than it can ever show. */
    private const val MAX_ITEMS = 40

    fun save(context: Context, linked: Boolean, items: List<WidgetItem>, today: LocalDate) {
        val kept = items
            .filter { !it.event.lastDate.isBefore(today.minusDays(1)) }
            .sortedBy { it.event.date }
            .take(MAX_ITEMS)
        val json = JSONArray()
        kept.forEach { item ->
            val e = item.event
            json.put(
                JSONObject()
                    .put("id", e.id)
                    .put("date", e.date.toString())
                    .put("end", e.endDate?.toString() ?: "")
                    .put("time", e.time)
                    .put("title", e.title)
                    .put("author", item.author)
                    .put("color", item.color.toArgb())
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_LINKED, linked)
            .putString(KEY_ITEMS, json.toString())
            .apply()
    }

    /** The saved snapshot; anything unreadable is skipped rather than failing the widget. */
    fun load(context: Context): WidgetState {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val items = runCatching {
            val json = JSONArray(prefs.getString(KEY_ITEMS, "[]"))
            (0 until json.length()).mapNotNull { i ->
                val o = json.optJSONObject(i) ?: return@mapNotNull null
                val date = runCatching { LocalDate.parse(o.optString("date")) }.getOrNull() ?: return@mapNotNull null
                val end = o.optString("end").takeIf { it.isNotEmpty() }
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    ?.takeIf { it.isAfter(date) }
                WidgetItem(
                    event = Event(
                        id = o.optString("id"),
                        date = date,
                        time = o.optString("time"),
                        title = o.optString("title"),
                        authorUid = "",
                        authorName = o.optString("author"),
                        endDate = end
                    ),
                    author = o.optString("author"),
                    color = Color(o.optInt("color", Color.Gray.toArgb()))
                )
            }
        }.getOrDefault(emptyList())
        return WidgetState(linked = prefs.getBoolean(KEY_LINKED, false), items = items)
    }
}
