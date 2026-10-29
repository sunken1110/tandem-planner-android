package io.github.sunken1110.tandemplanner.data

import java.time.LocalDate

/**
 * A single calendar event.
 *
 * Stored in Firestore at `rooms/{code}/events/{id}`.
 * New fields must only ever be *added* (never renamed or retyped) so that
 * older app versions keep working while both users update.
 *
 * @property authorUid  Firebase UID of the member who created the event.
 * @property authorName Display name at the time of writing (fallback if the
 *                      member's current name is unavailable).
 * @property editedByUid  Member (other than the author) who last edited the event, or null.
 * @property editedByName That member's name at the time of the edit.
 * @property original     The author's version from before the other member's first
 *                        edit. Later edits by that member keep it as is, so it always
 *                        shows what the author originally wrote.
 */
data class Event(
    val id: String,
    val date: LocalDate,
    val time: String,
    val title: String,
    val authorUid: String,
    val authorName: String,
    val editedByUid: String? = null,
    val editedByName: String? = null,
    val original: EventSnapshot? = null
) {
    /** True when the other member changed this event and the author hasn't taken it over since. */
    val isEditedByOther: Boolean get() = editedByUid != null && original != null

    /** The editable part of this event. */
    fun snapshot(): EventSnapshot = EventSnapshot(date, time, title)
}

/**
 * The editable part of an event: date, stored time ("HH:mm" or blank) and title.
 * Stored in Firestore as the `original` map of an event.
 */
data class EventSnapshot(val date: LocalDate, val time: String, val title: String)

/**
 * Events in display order: all-day events (no time) first, since they apply to
 * the whole day, then timed events by time.
 * Stored times are zero-padded "HH:mm", so text order is time order, and a
 * blank time sorts before any of them. The sort is stable, so all-day events
 * keep their original order.
 */
fun List<Event>.sortedByTime(): List<Event> = sortedBy { it.time.trim() }
