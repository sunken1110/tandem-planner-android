package io.github.sunken1110.tandemplanner.ui.common

import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Event times are stored as zero-padded "HH:mm" strings (e.g. "09:30", "18:00")
 * so they sort correctly as text and stay readable in Firestore.
 */
private val StorageFormat = DateTimeFormatter.ofPattern("HH:mm")

/** Formats a time for storage, e.g. 18:30 → "18:30". */
fun LocalTime.toStoredTime(): String = format(StorageFormat)

/**
 * Lenient reading format: also accepts single-digit hours ("9:30"), which early
 * app versions stored when times were typed by hand.
 */
private val ReadFormat = DateTimeFormatter.ofPattern("H:mm")

/** Parses a stored time ("18:30" or "9:30"), or returns null for empty / free-form values. */
fun parseStoredTime(stored: String): LocalTime? =
    runCatching { LocalTime.parse(stored.trim(), ReadFormat) }.getOrNull()

/** Korean 12-hour label, e.g. 09:00 → "오전 9:00", 18:30 → "오후 6:30", 12:00 → "오후 12:00". */
fun LocalTime.koreanLabel(): String {
    val period = if (hour < 12) "오전" else "오후"
    val hour12 = (hour % 12).let { if (it == 0) 12 else it }
    return "%s %d:%02d".format(period, hour12, minute)
}

/** Display label for a stored time. Legacy free-form values are shown as-is. */
fun displayTime(stored: String): String = parseStoredTime(stored)?.koreanLabel() ?: stored
