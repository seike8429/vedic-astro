package com.vedicastrology.app.data

import com.vedicastrology.app.engine.norm360

/**
 * Myanmar (Burmese) calendar date from sidereal Sun/Moon longitudes.
 *
 * Method (astronomical, validated 2026-10-23 against MOTAA V31.1):
 * - Year: increments at Thingyan (~Apr 17). myear = wyear - 638 on/after,
 *   wyear - 639 before.
 * - Month: the lunar month is NAMED by the sidereal sign of the Sun at the
 *   full moon belonging to that lunar month. monthIndex = (signAtFull + 1) % 12
 *   with 0 = Tagu. (Sun in Virgo at full moon -> Thadingyut, etc.)
 * - Day: tithi = elongation/12. Waxing (လဆန်း) tithi 1-15, waning (လဆုတ်) 16-30.
 * - Weekday: from the proleptic Gregorian date.
 *
 * Validation:
 * - 2025-10-09 -> 1387, Thadingyut, waning 3, Thursday — EXACT match to MOTAA.
 * - 2025-10-07 -> Thadingyut waxing 15 (full moon).
 * - 2025-04-17 -> 1387 Tagu (New Year day).
 * - 2026-04-13 -> 1387 Tagu (Thingyan eve, old year).
 *
 * Limitation: intercalary (watat) second-Waso years are not specially
 * labelled; the month name follows the astronomical rule. Refine in Phase 3
 * with the user's book if needed.
 */

data class MyanmarDate(
    val myear: Int,
    val monthIndex: Int,   // 0=Tagu .. 11=Tabaung
    val monthMm: String,
    val phaseMm: String,   // လဆန်း / လဆုတ်
    val day: Int,          // 1-15 within fortnight
    val weekdayMm: String
)

val MYANMAR_MONTHS = listOf(
    "တန်ခူး", "ကဆုန်", "နယုန်", "ဝါဆို", "ဝါခေါင်", "တော်သလင်း",
    "သီတင်းကျွတ်", "တန်ဆောင်မုန်း", "နတ်တော်", "ပြာသို", "တပို့တွဲ", "တပေါင်း"
)

val MYANMAR_WEEKDAYS = listOf(
    "တနင်္ဂနွေ", "တနင်္လာ", "အင်္ဂါ", "ဗုဒ္ဓဟူး", "ကြာသပတေး", "သောကြာ", "စနေ"
)

object MyanmarCalendar {

    fun fromWestern(
        y: Int, m: Int, d: Int,
        sunLon: Double, moonLon: Double
    ): MyanmarDate {
        val myear = if (m > 4 || (m == 4 && d >= 17)) y - 638 else y - 639

        // Month: sign of Sun at the full moon of this lunar month.
        val elong = norm360(moonLon - sunLon)
        val sunAtFull = if (elong <= 180.0) {
            sunLon + (180.0 - elong) / 12.2
        } else {
            sunLon - (elong - 180.0) / 12.2
        }
        val signAtFull = (norm360(sunAtFull) / 30.0).toInt().coerceIn(0, 11)
        val monthIndex = (signAtFull + 1) % 12

        // Day: tithi within fortnight.
        val tithi = elong / 12.0
        val (phaseMm, day) = if (tithi < 15.0) {
            "လဆန်း" to (tithi.toInt() + 1)
        } else {
            "လဆုတ်" to ((tithi - 15.0).toInt() + 1)
        }

        // Weekday via Julian Day Number, anchored to a known date.
        val jdn = gregorianToJdn(y, m, d)
        val weekdayMm = MYANMAR_WEEKDAYS[weekdayIndexFromJdn(jdn)]

        return MyanmarDate(myear, monthIndex, MYANMAR_MONTHS[monthIndex], phaseMm, day, weekdayMm)
    }

    /** 0=Sunday..6=Saturday index into MYANMAR_WEEKDAYS. */
    private fun weekdayIndexFromJdn(jdn: Long): Int {
        // JDN 2461337 = 2026-10-23 = Friday (index 5). Verified by computation.
        val anchorJdn = 2461337L
        val anchorIdx = 5
        return (((anchorIdx + (jdn - anchorJdn)) % 7) + 7).toInt() % 7
    }

    private fun gregorianToJdn(y: Int, m: Int, d: Int): Long {
        val a = (14 - m) / 12
        val yy = y + 4800 - a
        val mm = m + 12 * a - 3
        return d + (153 * mm + 2) / 5 + 365L * yy + yy / 4 - yy / 100 + yy / 400 - 32045
    }

    /** One-line display, e.g. "၁၃၈၈ တန်ဆောင်မုန်း လဆန်း ၁၂ သောကြာ". */
    fun format(md: MyanmarDate): String =
        "${toMyanmarDigits(md.myear)} ${md.monthMm} ${md.phaseMm} ${toMyanmarDigits(md.day)} ${md.weekdayMm}"

    private fun toMyanmarDigits(n: Int): String =
        n.toString().map { c ->
            if (c in '0'..'9') ('၀' + (c - '0')) else c
        }.joinToString("")
}
