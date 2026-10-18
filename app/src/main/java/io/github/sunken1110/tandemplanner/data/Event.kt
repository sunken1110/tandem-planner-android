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
 */
data class Event(
    val id: String,
    val date: LocalDate,
    val time: String,
    val title: String,
    val authorUid: String = "",
    val authorName: String = ""
)
