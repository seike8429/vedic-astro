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

    /** Start longitude (degrees) of a 28-system nakshatra number (1-28). */
    fun nakshatraStart(n28: Int): Double = when (n28) {
        22 -> ABHIJIT_START
        in 1..21 -> (n28 - 1) * NAK_SPAN
        in 23..28 -> (n28 - 2) * NAK_SPAN
        else -> 0.0
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
     * The ten Dasagga (ဒသဂ္ဂ) groups, read directly from the book's
     * group table (his photo, upload 185): the book writes Abhijit as
     * X between 21 and 22, so its 22, 23, 24, 25, 26, 27 are our 23,
     * 24, 25, 26, 27, 28. The table reads: 11-13 ပူဖိတ, 14-17 ဖလိတ,
     * 18-20 နိဖလ, 21,X,22 ဇ္ဇိုတ, 23 ဂူဒ, 24-26 ရာဇေ, 27,1,2 ထာမသ,
     * 3-6 ပူ, 7-9 မတ, 10 ဇီဝ. There is NO ဌာသ group — that was a
     * misread of ထာမသ in an earlier session; the table is the
     * authority. Used for the group ring and the groups table.
     */
    data class DasaggaGroup(val mm: String, val numbers: List<Int>)

    val DASAGGA_GROUPS = listOf(
        DasaggaGroup("ပူဖိတ", listOf(11, 12, 13)),
        DasaggaGroup("ဖလိတ", listOf(14, 15, 16, 17)),
        DasaggaGroup("နိဖလ", listOf(18, 19, 20)),
        DasaggaGroup("ဇ္ဇိုတ", listOf(21, 22, 23)),
        DasaggaGroup("ဂူဒ", listOf(24)),
        DasaggaGroup("ရာဇေ", listOf(25, 26, 27)),
        DasaggaGroup("ထာမသ", listOf(28, 1, 2)),
        DasaggaGroup("ပူ", listOf(3, 4, 5, 6)),
        DasaggaGroup("မတ", listOf(7, 8, 9)),
        DasaggaGroup("ဇီဝ", listOf(10))
    )

    /**
     * Pada (သရ) syllables per nakshatra (28-system; index = n28 - 1).
     * The standard 108 pada syllables, transliterated into Burmese.
     * Magha's မ/မိ/မု/မေ is confirmed by the author; the rest are
     * the standard forms — the author can correct any spelling that
     * differs from his book. Abhijit (22) spans a single pada, so it
     * has no pada syllable of its own ("—").
     */
    val PADA_SYLLABLES = listOf(
        listOf("ကု", "ကေ", "ကော", "လာ"),      // 1 Ashwini
        listOf("လိ", "လု", "လေ", "လော"),      // 2 Bharani
        listOf("အ", "ဣ", "ဥ", "ဧ"),           // 3 Krittika
        listOf("အို", "ဝ", "ဝိ", "ဝု"),        // 4 Rohini
        listOf("ဝေ", "ဝေါ", "က", "ကိ"),        // 5 Mrigashira
        listOf("ကု", "ဃ", "င", "ဆ"),          // 6 Ardra
        listOf("ကေ", "ကော", "ဟ", "ဟိ"),       // 7 Punarvasu
        listOf("ဟု", "ဟေ", "ဟော", "ဒ"),       // 8 Pushya
        listOf("ဒိ", "ဒု", "ဒေ", "ဒေါ"),       // 9 Ashlesha
        listOf("မ", "မိ", "မု", "မေ"),         // 10 Magha (confirmed)
        listOf("မော", "တ", "တိ", "တု"),       // 11 Purva Phalguni
        listOf("တေ", "တော", "ပ", "ပိ"),       // 12 Uttara Phalguni
        listOf("ပု", "ရှ", "ဏ", "ထ"),          // 13 Hasta
        listOf("ပေ", "ပေါ", "ရ", "ရိ"),        // 14 Chitra
        listOf("ရု", "ရေ", "ရော", "တ"),       // 15 Swati
        listOf("တိ", "တု", "တေ", "တော"),      // 16 Vishakha
        listOf("န", "နိ", "နု", "နေ"),          // 17 Anuradha
        listOf("နော", "ယ", "ယိ", "ယု"),        // 18 Jyeshtha
        listOf("ယေ", "ယော", "ဘ", "ဘိ"),       // 19 Moola
        listOf("ဘု", "ဓ", "ဖ", "ဓ"),           // 20 Purva Ashadha
        listOf("ဘေ", "ဘော", "ဇ", "ဇိ"),       // 21 Uttara Ashadha
        listOf("—"),                            // 22 Abhijit (single pada)
        listOf("ဇု", "ဇေ", "ဇော", "ဃ"),       // 23 Shravana
        listOf("ဂ", "ဂိ", "ဂု", "ဂေ"),         // 24 Dhanishta
        listOf("ဂေါ", "သ", "သိ", "သု"),       // 25 Shatabhisha
        listOf("သေ", "သော", "ဒ", "ဒိ"),       // 26 Purva Bhadrapada
        listOf("ဒု", "ထ", "ဈ", "ဏ"),          // 27 Uttara Bhadrapada
        listOf("ဒေ", "ဒေါ", "ဆ", "ဆိ")        // 28 Revati
    )

    /**
     * (nakshatra28, pada) for a sidereal longitude. Each pada spans
     * 3°20' (NAK_SPAN / 4). Uttara Ashadha (21) is only 10° wide in
     * the 28-system, so it has 3 padas; Abhijit (22) is a single pada.
     */
    fun padaOf(lon: Double): Pair<Int, Int> {
        val n28 = nakshatra28(lon)
        val within = ((lon - nakshatraStart(n28)) % 360.0 + 360.0) % 360.0
        val pada = (within / (NAK_SPAN / 4.0)).toInt() + 1
        val maxPada = when (n28) {
            22 -> 1
            21 -> 3
            else -> 4
        }
        return n28 to minOf(pada, maxPada)
    }

    /** The သရ (pada syllable) for a sidereal longitude. */
    fun syllableOf(lon: Double): String {
        val (n28, pada) = padaOf(lon)
        val list = PADA_SYLLABLES[n28 - 1]
        return if (list.size == 1) list[0] else list[pada - 1]
    }

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
