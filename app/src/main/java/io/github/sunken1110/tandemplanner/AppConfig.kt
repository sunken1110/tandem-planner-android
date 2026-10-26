package io.github.sunken1110.tandemplanner

import java.time.LocalDate

/**
 * App-wide tuning values.
 *
 * Keeping these in one place makes it easy to tweak animation timing or
 * preview a different date without hunting through UI code.
 */

/**
 * Debug override for "today".
 *
 * Set to e.g. `LocalDate.of(2026, 3, 1)` to preview another season's colors,
 * particles and the season-start banner. Must be `null` in release builds.
 */
internal val DEBUG_TODAY: LocalDate? = null

/** Returns the current date, honoring [DEBUG_TODAY] when set. */
internal fun currentDate(): LocalDate = DEBUG_TODAY ?: LocalDate.now()

/** Max characters for a member's display name. */
internal const val MAX_NAME_LENGTH = 12

// ── Year fill animation ──

/** How many "days" a single cell takes to fade from transparent to full color. */
internal const val FADE_DAYS = 14f

/** Total duration of the fill animation that sweeps from Jan 1 to today. */
internal const val FILL_DURATION_MS = 2400

// ── Splash → intro → main screen transition ──

/** Fade-out duration of the system splash screen. */
internal const val SPLASH_FADE_MS = 350L

/** Fade-in duration of the intro image. */
internal const val INTRO_IN_MS = 700

/** How long the intro image stays fully visible. */
internal const val INTRO_HOLD_MS = 900L

/** Cross-fade duration from the intro image to the main screen. */
internal const val INTRO_OUT_MS = 700
