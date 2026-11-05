package com.vedicastrology.app.engine

/**
 * Burmese Rahu Kalam — Sayagyi (ဆရာကြီး) wheel, from the book
 * မြန်မာ့ရိုးရာ သရေခေတ္တရာဟုကာလ (Vol. 2).
 *
 * Pure Kotlin, no Android imports, so the logic stays testable like
 * TransitEffectLogic. Nothing here is the Indian standard Rahu Kalam
 * (fixed weekday time slots): the Burmese wheel is built from the
 * nakshatra Rahu currently occupies.
 *
 * Method (book, verified against the worked example):
 *  - 28 nakshatras, Ashwini = 1. Abhijit (22, shown as X in the book's
 *    tables) sits between Uttara Ashadha (21) and Shravana (23):
 *    Uttara Ashadha spans 266°40'–276°40' (10°), Abhijit 276°40'–280°00'
 *    (3°20'), every other nakshatra 13°20'.
 *  - Mouth (ခံတွင်း) = the nakshatra Rahu occupies.
 *  - Counting forward from the mouth, wrapping at 28:
 *    mouth 1, chest (ရင်) 7, belly (ဝမ်း) 6, anus (စအို) 1,
 *    hips (အပြီး) 6, legs (ခြေထောက်) 7 = 28.
 *    Worked example (book): Rahu nakshatra 19 (မူလ/Mula) →
 *    mouth 19; chest 20–26; belly 27, 28, 1–4; anus 5; hips 6–11;
 *    legs 12–18. This implementation reproduces it exactly.
 *  - Tail (ကေတုအမြှီး) = the anus part (စအို), per the author's word.
 *  - General day-2 rule (book, Sayagyi predictions): when the Moon
 *    (day 2) is in a chest / belly / anus nakshatra, work succeeds and
 *    marriage is favourable; in a hips / legs / mouth nakshatra, work
 *    meets obstacles and marriage does not hold.
 *
 * OPEN (author to confirm): the wheel diagram labels the 7-nakshatra
 * part ဦးခေါင်း (head) where the method text and the general rule call
 * it ခြေထောက် (legs). The code uses ခြေထောက်, following the text; if
 * the author confirms ဦးခေါင်း, it is a one-word label change.
 */
object RahuKalam {

    /** Abhijit's span start: 276°40' sidereal. */
    private const val ABHIJIT_START = 276.0 + 40.0 / 60.0

    /** The six body parts of the Sayagyi wheel, in forward order. */
    enum class WheelPart(val mm: String, val count: Int) {
        MOUTH("ခံတွင်း", 1),
        CHEST("ရင်", 7),
        BELLY("ဝမ်း", 6),
        ANUS("စအို", 1),
        HIPS("အပြီး", 6),
        LEGS("ခြေထောက်", 7)
    }

    /** One part of a built wheel: the part plus its nakshatra numbers (1-28). */
    data class WheelPartEntry(val part: WheelPart, val numbers: List<Int>)

    /** A built Sayagyi wheel: Rahu's nakshatra (1-28) is the mouth. */
    data class SayagyiWheel(val mouth28: Int, val parts: List<WheelPartEntry>) {
        /** Which part contains 28-nakshatra number [n28] (1-28). */
        fun partOf(n28: Int): WheelPart =
            parts.first { n28 in it.numbers }.part
    }

    /** 28-system nakshatra number (1-28, Ashwini = 1, Abhijit = 22) for a sidereal longitude. */
    fun nakshatra28(lon: Double): Int {
        val l = norm360(lon)
        val idx27 = (l / NAK_SPAN).toInt().coerceIn(0, 26)
        return when {
            idx27 < 20 -> idx27 + 1
            idx27 == 20 -> if (l < ABHIJIT_START) 21 else 22
            else -> idx27 + 2
        }
    }

    /** Burmese name of a 28-system nakshatra number (1-28). */
    fun name28(n28: Int): String = when (n28) {
        22 -> "အဘိဇိတ်"
        in 1..21 -> NAKSHATRAS[n28 - 1].mm
        in 23..28 -> NAKSHATRAS[n28 - 2].mm
        else -> "?"
    }

    /** Build the Sayagyi wheel from Rahu's sidereal longitude. */
    fun sayagyiWheel(rahuLon: Double): SayagyiWheel {
        val mouth = nakshatra28(rahuLon)
        var next = mouth
        val parts = WheelPart.entries.map { part ->
            val numbers = (0 until part.count).map { step ->
                wrap28(next + step)
            }
            next = wrap28(next + part.count)
            WheelPartEntry(part, numbers)
        }
        return SayagyiWheel(mouth, parts)
    }

    private fun wrap28(n: Int): Int = ((n - 1) % 28 + 28) % 28 + 1

    /** Day-2 general verdict for a Moon part (book's Sayagyi rule). */
    enum class Verdict(val mm: String, val good: Boolean) {
        GOOD("ကောင်း", true),
        OBSTACLE("အခက်အခဲ", false)
    }

    fun day2Verdict(part: WheelPart): Verdict = when (part) {
        WheelPart.CHEST, WheelPart.BELLY, WheelPart.ANUS -> Verdict.GOOD
        WheelPart.HIPS, WheelPart.LEGS, WheelPart.MOUTH -> Verdict.OBSTACLE
    }

    /** Julian Day (UT) for a Unix epoch-millis instant. */
    fun jdFromMillis(millis: Long): Double = 2440587.5 + millis / 86400000.0
}
