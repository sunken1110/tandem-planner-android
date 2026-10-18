package io.github.sunken1110.tandemplanner.ui.theme

import androidx.compose.ui.graphics.Color

/** Text color drawn on top of filled (season-colored) cells, banners and chips. */
val OnSeasonColor = Color(0xFF2B2B2B)

/** Sunday labels and day numbers. */
val SundayRed = Color(0xFFE5484D)

/** Saturday labels and day numbers (sky blue). */
val SaturdaySky = Color(0xFF1E9BE8)

/** Per-member accent colors: index 0 = room creator, index 1 = second member. */
val MemberColors = listOf(Color(0xFF7C5CFA), Color(0xFFE64980))

/** Fallback for authors who are no longer (or not yet) known members. */
val UnknownMemberColor = Color(0xFF8A8A8A)
