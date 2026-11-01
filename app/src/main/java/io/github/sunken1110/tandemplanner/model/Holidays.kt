package io.github.sunken1110.tandemplanner.model

import java.time.LocalDate

/**
 * A Korean public holiday.
 *
 * @property name  full name, e.g. "대체공휴일 (삼일절)", shown in day lists and the editor.
 * @property short a few characters for the small month-view cells, e.g. "대체휴일".
 */
data class Holiday(val name: String, val short: String)

/**
 * South Korea's public holidays (공휴일), including substitute holidays (대체공휴일)
 * and election days, bundled with the app so no server or network is needed.
 *
 * Lunar holidays (설날, 부처님오신날, 추석) move every year and substitute holidays
 * depend on weekends, so the dates are listed per year rather than computed.
 * Add the next year's list once the government announces it (usually in the
 * summer before): see [COVERED_YEARS].
 *
 * Sources: 한국천문연구원 월력요항, and the 2026 additions of 노동절 (May 1) and
 * 제헌절 (Jul 17) as public holidays.
 */
object KoreanHolidays {

    /** Years with a holiday list. Other years simply show no holidays. */
    val COVERED_YEARS = 2026..2027

    private val byDate: Map<LocalDate, Holiday> = buildMap {
        fun add(y: Int, m: Int, d: Int, name: String, short: String = name) =
            put(LocalDate.of(y, m, d), Holiday(name, short))
        fun sub(y: Int, m: Int, d: Int, of: String) = add(y, m, d, "대체공휴일 ($of)", "대체휴일")

        // ── 2026 ──
        add(2026, 1, 1, "신정")
        add(2026, 2, 16, "설날 연휴", "설 연휴")
        add(2026, 2, 17, "설날")
        add(2026, 2, 18, "설날 연휴", "설 연휴")
        add(2026, 3, 1, "삼일절")
        sub(2026, 3, 2, "삼일절")
        add(2026, 5, 1, "노동절")
        add(2026, 5, 5, "어린이날")
        add(2026, 5, 24, "부처님오신날", "부처님")
        sub(2026, 5, 25, "부처님오신날")
        add(2026, 6, 3, "전국동시지방선거", "선거일")
        add(2026, 6, 6, "현충일")
        add(2026, 7, 17, "제헌절")
        add(2026, 8, 15, "광복절")
        sub(2026, 8, 17, "광복절")
        add(2026, 9, 24, "추석 연휴", "추석")
        add(2026, 9, 25, "추석")
        add(2026, 9, 26, "추석 연휴", "추석")
        add(2026, 10, 3, "개천절")
        sub(2026, 10, 5, "개천절")
        add(2026, 10, 9, "한글날")
        add(2026, 12, 25, "성탄절")

        // ── 2027 ──
        add(2027, 1, 1, "신정")
        add(2027, 2, 6, "설날 연휴", "설 연휴")
        add(2027, 2, 7, "설날")
        add(2027, 2, 8, "설날 연휴", "설 연휴")
        sub(2027, 2, 9, "설날")
        add(2027, 3, 1, "삼일절")
        add(2027, 5, 1, "노동절")
        sub(2027, 5, 3, "노동절")
        add(2027, 5, 5, "어린이날")
        add(2027, 5, 13, "부처님오신날", "부처님")
        add(2027, 6, 6, "현충일")
        add(2027, 7, 17, "제헌절")
        sub(2027, 7, 19, "제헌절")
        add(2027, 8, 15, "광복절")
        sub(2027, 8, 16, "광복절")
        add(2027, 9, 14, "추석 연휴", "추석")
        add(2027, 9, 15, "추석")
        add(2027, 9, 16, "추석 연휴", "추석")
        add(2027, 10, 3, "개천절")
        sub(2027, 10, 4, "개천절")
        add(2027, 10, 9, "한글날")
        sub(2027, 10, 11, "한글날")
        add(2027, 12, 25, "성탄절")
        sub(2027, 12, 27, "성탄절")
    }

    /** The holiday on [date], or null. */
    fun of(date: LocalDate): Holiday? = byDate[date]
}
