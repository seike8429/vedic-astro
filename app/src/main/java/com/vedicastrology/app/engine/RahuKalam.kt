package com.vedicastrology.app.engine

/**
 * Burmese Rahu Kalam — Sayagyi (ဆရာကြီး) wheel, from the book
 * မြန်မာ့ရိုးရာ သရေခေတ္တရာဟုကာလ (Vol. 2).
 *
 * Pure Kotlin, no Android imports, so the logic stays testable like
 * TransitEffectLogic. Nothing here is the Indian standard Rahu Kalam
 * (fixed weekday time slots): the Burmese wheel is built from the
 * nakshatra Rahu occupies in the NATAL chart.
 *
 * Method (book + author's correction 2026-11-05):
 *  - 28 nakshatras, Ashwini = 1. Abhijit (22, shown as X in the book's
 *    tables) sits between Uttara Ashadha (21) and Shravana (23):
 *    Uttara Ashadha spans 266°40'–276°40' (10°), Abhijit 276°40'–280°00'
 *    (3°20'), every other nakshatra 13°20'.
 *  - The wheel is built from the NATAL chart: the HEAD (ဦးခေါင်း) starts
 *    at the nakshatra Rahu occupies natally, and the rest follow in
 *    order (clockwise): head 7, mouth (ခံတွင်း) 1, chest (ရင်) 7,
 *    belly (ဝမ်း) 6, anus (စအို) 1, hips (အပြီး) 6 = 28.
 *    (Earlier v1.8.15 wrongly used the transiting Rahu and labelled the
 *    last part "legs" (ခြေထောက်) — the author's word corrects both: the
 *    wheel is natal, and Rahu's nakshatra is the head.)
 *  - Tail (ကေတုအမြှီး) = the anus part (စအို), per the author's word.
 *  - The daily reading uses the CURRENT Moon: day 1 = today, day 2 =
 *    tomorrow. General day-2 rule (book, Sayagyi): when the Moon
 *    (day 2) is in a chest / belly / anus nakshatra, work succeeds and
 *    marriage is favourable; in a hips / head / mouth nakshatra, work
 *    meets obstacles and marriage does not hold.
 *
 * Verified against the author's own chart (Rahu natal = Swati, 15):
 * head 15–21, mouth 22 (Abhijit), chest 23–28, 1, belly 2–7,
 * anus 8 (Pushya), hips 9–14. His natal Moon (Shravana, 23) falls in
 * the chest; his Nama nakshatra (Magha, 10) falls in the hips.
 */
object RahuKalam {

    /** Abhijit's span start: 276°40' sidereal. */
    private const val ABHIJIT_START = 276.0 + 40.0 / 60.0

    /**
     * The six body parts of the Sayagyi wheel, in cycle order starting
     * at the mouth. The construction starts at the HEAD (Rahu's natal
     * nakshatra) and runs head -> mouth -> chest -> belly -> anus ->
     * hips -> head.
     */
    enum class WheelPart(val mm: String, val count: Int) {
        MOUTH("ခံတွင်း", 1),
        CHEST("ရင်", 7),
        BELLY("ဝမ်း", 6),
        ANUS("စအို", 1),
        HIPS("အပြီး", 6),
        HEAD("ဦးခေါင်း", 7)
    }

    /** One part of a built wheel: the part plus its nakshatra numbers (1-28). */
    data class WheelPartEntry(val part: WheelPart, val numbers: List<Int>)

    /** A built Sayagyi wheel: Rahu's natal nakshatra (1-28) starts the head. */
    data class SayagyiWheel(val headStart28: Int, val parts: List<WheelPartEntry>) {
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

    /**
     * Build the Sayagyi wheel from Rahu's NATAL sidereal longitude.
     * The head starts at Rahu's nakshatra; the rest follow in order.
     */
    fun sayagyiWheel(natalRahuLon: Double): SayagyiWheel {
        val headStart = nakshatra28(natalRahuLon)
        // Offset of each part's first nakshatra from the head start.
        val offsets = mapOf(
            WheelPart.HEAD to 0,
            WheelPart.MOUTH to 7,
            WheelPart.CHEST to 8,
            WheelPart.BELLY to 15,
            WheelPart.ANUS to 21,
            WheelPart.HIPS to 22
        )
        val parts = WheelPart.entries.map { part ->
            val start = wrap28(headStart + (offsets[part] ?: 0))
            val numbers = (0 until part.count).map { step -> wrap28(start + step) }
            WheelPartEntry(part, numbers)
        }
        return SayagyiWheel(headStart, parts)
    }

    private fun wrap28(n: Int): Int = ((n - 1) % 28 + 28) % 28 + 1

    /**
     * The ten Dasagga (ဒသဂ္ဂ) groups, from the book's GROUP TABLE
     * (nakshatra numbers in the 28-system, Ashwini = 1, Abhijit = 22;
     * the book writes Abhijit as X between 21 and 22, so its 22, 23,
     * 24, 25, 26, 27 are our 23, 24, 25, 26, 27, 28). Used for the
     * group ring of the ZarTarPro-style wheel. Note: the book's note
     * list also gives the variant names ဂူဒ, ပုဗ္ဗ, မော (and ဇီဝိ) —
     * ဂူဒ is associated with nakshatra 24 (the ရာဇေ group); the table
     * names below are the authority unless the author corrects them.
     */
    data class DasaggaGroup(val mm: String, val numbers: List<Int>)

    val DASAGGA_GROUPS = listOf(
        DasaggaGroup("ပူဖိတ", listOf(11, 12, 13)),
        DasaggaGroup("ဖလိတ", listOf(14, 15, 16, 17)),
        DasaggaGroup("နိဖလ", listOf(18, 19, 20)),
        DasaggaGroup("ဇ္ဇိုတ", listOf(21, 22, 23)),
        DasaggaGroup("ရာဇေ", listOf(24)),
        DasaggaGroup("ဌာသ", listOf(25, 26, 27)),
        DasaggaGroup("ထာမသ", listOf(28, 1, 2)),
        DasaggaGroup("ပူ", listOf(3, 4, 5, 6)),
        DasaggaGroup("မတ", listOf(7, 8, 9)),
        DasaggaGroup("ဇီဝ", listOf(10))
    )

    /** Burmese numeral for a number 1-28 (ZarTarPro writes numerals this way). */
    fun burmeseNum(n: Int): String {
        val digits = "၀၁၂၃၄၅၆၇၈၉"
        return n.toString().map { digits[it - '0'] }.joinToString("")
    }

    /** Day-2 general verdict for a Moon part (book's Sayagyi rule). */
    enum class Verdict(val mm: String, val good: Boolean) {
        GOOD("ကောင်း", true),
        OBSTACLE("အခက်အခဲ", false)
    }

    fun day2Verdict(part: WheelPart): Verdict = when (part) {
        WheelPart.CHEST, WheelPart.BELLY, WheelPart.ANUS -> Verdict.GOOD
        WheelPart.HIPS, WheelPart.HEAD, WheelPart.MOUTH -> Verdict.OBSTACLE
    }

    /** Julian Day (UT) for a Unix epoch-millis instant. */
    fun jdFromMillis(millis: Long): Double = 2440587.5 + millis / 86400000.0
}
