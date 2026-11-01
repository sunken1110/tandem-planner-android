package io.github.sunken1110.tandemplanner.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

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
 * @property endDate      Last day of a multi-day event (vacation, trip), after [date];
 *                        null for a one-day event. Multi-day events are always all-day
 *                        ([time] is blank). Older app versions ignore this field and show
 *                        the event on its first day only.
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
    val original: EventSnapshot? = null,
    val endDate: LocalDate? = null
) {
    /** True for an event that spans more than one day. */
    val isMultiDay: Boolean get() = endDate != null

    /** The event's last day ([date] for a one-day event). */
    val lastDate: LocalDate get() = endDate ?: date

    /** Number of days the event covers (1 for a one-day event). */
    val dayCount: Int get() = (ChronoUnit.DAYS.between(date, lastDate) + 1).toInt()

    /** True when the event takes place on [day]. */
    fun covers(day: LocalDate): Boolean = !day.isBefore(date) && !day.isAfter(lastDate)

    /** True when the other member changed this event and the author hasn't taken it over since. */
    val isEditedByOther: Boolean get() = editedByUid != null && original != null

    /** The editable part of this event. */
    fun snapshot(): EventSnapshot = EventSnapshot(date, time, title, endDate)
}

/**
 * The editable part of an event: date, stored time ("HH:mm" or blank), title and,
 * for a multi-day event, its last day. Stored in Firestore as the `original` map
 * of an event (with `endDate` only when there is one).
 */
data class EventSnapshot(
    val date: LocalDate,
    val time: String,
    val title: String,
    val endDate: LocalDate? = null
)

/**
 * Events still to come, in the order the D-day card and the widget list them:
 * - multi-day events under way today come first;
 * - then by day from today on (all-day before timed on the same day, as in the
 *   day lists), then by time;
 * - today's timed events whose time ([now]) has passed are left out.
 */
fun Collection<Event>.upcoming(today: LocalDate, now: LocalTime): List<Event> {
    val nowStored = "%02d:%02d".format(now.hour, now.minute)
    return filter { e ->
        when {
            e.lastDate.isBefore(today) -> false
            e.date == today && !e.isMultiDay && e.time.isNotBlank() ->
                // Free-form legacy times ("저녁쯤") can't be compared; keep them for today.
                e.time.length != 5 || e.time[2] != ':' || e.time >= nowStored
            else -> true
        }
    }.sortedWith(
        compareBy<Event>({ maxOf(it.date, today) }, { !it.isMultiDay }, { it.time.trim() }, { it.id })
    )
}

/** The next event to look forward to (see [upcoming]), or null when nothing is coming up. */
fun Collection<Event>.nextUpcoming(today: LocalDate, now: LocalTime): Event? = upcoming(today, now).firstOrNull()

/** Longest multi-day event the editor allows, in days. */
const val MAX_EVENT_DAYS = 60

/**
 * Events in display order: multi-day events first (earlier start, then longer
 * first), then all-day events (no time), since they apply to the whole day, then
 * timed events by time.
 * Stored times are zero-padded "HH:mm", so text order is time order, and a
 * blank time sorts before any of them. The sort is stable, so all-day events
 * keep their original order.
 */
fun List<Event>.sortedByTime(): List<Event> = sortedWith(
    compareBy<Event>({ !it.isMultiDay }, { if (it.isMultiDay) it.date else null }, { -it.dayCount }, { it.time.trim() })
)

/**
 * Events grouped by every day they cover: a multi-day event appears under each
 * of its days (at most [MAX_EVENT_DAYS], in case of bad data). Each day's list is
 * in display order ([sortedByTime]).
 */
fun List<Event>.groupByDay(): Map<LocalDate, List<Event>> {
    val byDay = HashMap<LocalDate, MutableList<Event>>()
    for (event in this) {
        var day = event.date
        repeat(event.dayCount.coerceIn(1, MAX_EVENT_DAYS)) {
            byDay.getOrPut(day) { mutableListOf() }.add(event)
            day = day.plusDays(1)
        }
    }
    return byDay.mapValues { (_, list) -> list.sortedByTime() }
}
