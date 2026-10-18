package io.github.sunken1110.tandemplanner.data

import android.content.Context
import org.json.JSONArray
import java.time.LocalDate

/**
 * Read-only access to events saved on-device by pre-sharing versions of the app.
 *
 * Used once to offer migrating those events into the shared calendar.
 * Malformed entries are skipped instead of crashing.
 */
class LocalEventStore(context: Context) {
    private val prefs = context.getSharedPreferences("events", Context.MODE_PRIVATE)

    fun load(): List<Event> = runCatching {
        val arr = JSONArray(prefs.getString("list", null) ?: "[]")
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
