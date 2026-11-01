package io.github.sunken1110.tandemplanner.data

import java.time.LocalDate
import java.time.LocalTime

/**
 * An event that is being created or confirmed, before it is saved.
 *
 * Produced by the add-event button (empty title, chosen date), by the
 * quick-add bar's parser, or from an existing event being edited, then shown
 * in the editor sheet for confirmation.
 *
 * @property rawTime a stored time that isn't a clock time, such as "저녁쯤" typed
 *           in an early app version. It is kept as is unless the user changes the
 *           time, so editing only the title never erases it. Null for new events.
 */
data class EventDraft(
    val date: LocalDate,
    val time: LocalTime?,
    val title: String,
    val rawTime: String? = null
) {
    /** The value to store in Firestore: "HH:mm", the untouched [rawTime], or blank. */
    fun storedTime(toStored: (LocalTime) -> String): String = time?.let(toStored) ?: rawTime.orEmpty()
}
