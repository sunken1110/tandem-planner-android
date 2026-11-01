package io.github.sunken1110.tandemplanner.ai

import io.github.sunken1110.tandemplanner.data.EventDraft
import java.time.LocalDate

/**
 * Turns a free-form sentence such as "내일 저녁 7시 민지랑 저녁" into an [EventDraft].
 *
 * This is the seam for AI: the quick-add bar only depends on this interface, so
 * the current [RuleBasedEventParser] can later be swapped for an on-device model
 * (e.g. Gemini Nano) or a cloud LLM (e.g. via Firebase AI Logic) without UI changes.
 *
 * Implementations should be best-effort: always return a draft (falling back to
 * [today] and the original text as the title) rather than failing, because the
 * user confirms and corrects the result in the editor sheet anyway.
 */
fun interface EventParser {
    suspend fun parse(text: String, today: LocalDate): EventDraft
}
