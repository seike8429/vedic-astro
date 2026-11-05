package com.vedicastrology.app.engine

/**
 * Burmese Rahu Kalam — Sayadaw (သယောဒယ) wheel, from the book
 * မြန်မာ့ရိုးရာ သယောဒယ ရာဟုကာလ (Vol. 2).
 *
 * Pure Kotlin, no Android imports, so the logic stays testable like
 * TransitEffectLogic. Nothing here is the Indian standard Rahu Kalam
 * (fixed weekday time slots): the Burmese wheel is built from the
 * nakshatra Rahu occupies in the NATAL chart.
 *
 * Method (book + author's corrections 2026-11-05):
 *  - 28 nakshatras, Ashwini = 1. Abhijit (22, shown as X in the book's
 *    tables) sits between Uttara Ashadha (21) and Shravana (23):
 *    Uttara Ashadha spans 266°40'–276°40' (10°), Abhijit 276°40'–280°00'
 *    (3°20'), every other nakshatra 13°20'.
 *  - The wheel is anchored at the MOUTH (ဇီဝ): the mouth IS the
 *    nakshatra Rahu occupies, and the anus (ဂူဒ) IS the nakshatra Ketu
 *    occupies — the author's rule ("ကေတုရှိတဲ့နေရာက ဂူဒ၊
 *    ရာဟုရှိတဲ့နေရာက ဇီဝ"). From the mouth the parts follow in order
 *    (clockwise): mouth (ခံတွင်း) 1, chest (ရင်) 7, belly (ဝမ်း) 6,
 *    anus (စအို) 1, tail (အမြှီး) 6, head (ဦးခေါင်း) 7 = 28.
 *    The wheel is built from the NATAL chart and from the CURRENT
 *    (transit) chart alike — each has its own Rahu, hence its own
 *    mouth and anus. (Earlier versions wrongly anchored the wheel at
 *    the head with the mouth at offset 7 — the author's word corrects
 *    this: Rahu's nakshatra IS the mouth.)
 *  - The daily reading uses the CURRENT Moon: day 1 = today, day 2 =
 *    tomorrow. General day-2 rule (book, Sayadaw): when the Moon
 *    (day 2) is in a chest / belly / anus nakshatra, work succeeds and
 *    marriage is favourable; in a tail / head / mouth nakshatra, work
 *    meets obstacles and marriage does not hold.
 *
 * Verified against the author's own chart (Rahu natal = Swati, 15):
 * mouth 15 (Swati, ဇီဝ), chest 16–22, belly 23–28,1, anus 2
 * (Bharani, ဂူဒ = Ketu's nakshatra — the anus is anchored to Ketu,
 * not to a fixed offset 14; Abhijit makes the count 15 here),
 * tail 3–8, head 9–14. His natal Moon (Shravana, 23) falls in the
 * belly; his Nama nakshatra (Magha, 10) falls in the head.
 */
object RahuKalam {

    /** Abhijit's span start: 276°40' sidereal. */
    private const val ABHIJIT_START = 276.0 + 40.0 / 60.0

    /**
     * The six body parts of the Sayadaw wheel, in cycle order starting
     * at the mouth. The wheel is anchored at the MOUTH: the mouth
     * (ဇီဝ) IS the nakshatra Rahu occupies, and the anus (ဂူဒ) IS the
     * nakshatra Ketu occupies — the author's rule 2026-11-05
     * ("ကေတုရှိတဲ့နေရာက ဂူဒ၊ ရာဟုရှိတဲ့နေရာက ဇီဝ"). Rahu and Ketu
     * are always 180° apart, so the mouth and the anus sit exactly
     * 180° apart. From the mouth the parts run mouth -> chest ->
     * belly -> anus -> tail -> head -> mouth.
     */
    enum class WheelPart(val mm: String, val count: Int) {
        MOUTH("ခံတွင်း", 1),
        CHEST("ရင်", 7),
        BELLY("ဝမ်း", 6),
        ANUS("စအို", 1),
        TAIL("အမြှီး", 6),
        HEAD("ဦးခေါင်း", 7)
    }

    /** One part of a built wheel: the part plus its nakshatra numbers (1-28). */
    data class WheelPartEntry(val part: WheelPart, val numbers: List<Int>)

    /** A built Sayadaw wheel: Rahu's nakshatra (1-28) is the mouth (ဇီဝ). */
    data class SayagyiWheel(val mouthStart28: Int, val parts: List<WheelPartEntry>) {
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
     * Build the Sayadaw wheel from Rahu's sidereal longitude.
     * The MOUTH (ဇီဝ) is the nakshatra Rahu occupies; the ANUS (ဂူဒ)
     * is the nakshatra Ketu occupies — anchored to Ketu, NOT to a
     * fixed offset 14. Ketu is always exactly 180° from Rahu, but
     * because Abhijit is short (3°20') the count from mouth to Ketu
     * can be 14 or 15: the author's natal chart (mouth Swati 15,
     * Ketu Bharani 2) is offset 15, so a fixed offset 14 wrongly put
     * the anus at Ashwini 1 and Ketu in the tail (fixed 2026-11-06).
     * CHEST is always 7 (offsets 1-7) and TAIL always 6 (first 6
     * after the anus); BELLY (offsets 8..d-1) and HEAD (offsets
     * d+7..27) absorb the ±1, where d is the anus offset (14 or 15).
     */
    fun sayagyiWheel(natalRahuLon: Double): SayagyiWheel {
        val mouthStart = nakshatra28(natalRahuLon)
        val ketuN28 = nakshatra28(natalRahuLon + 180.0)
        val d = (ketuN28 - mouthStart + 28) % 28  // anus offset: 14 or 15
        val parts = listOf(
            WheelPartEntry(WheelPart.MOUTH, listOf(mouthStart)),
            WheelPartEntry(WheelPart.CHEST, (1..7).map { wrap28(mouthStart + it) }),
            WheelPartEntry(WheelPart.BELLY, (8 until d).map { wrap28(mouthStart + it) }),
            WheelPartEntry(WheelPart.ANUS, listOf(ketuN28)),
            WheelPartEntry(WheelPart.TAIL, (1..6).map { wrap28(ketuN28 + it) }),
            WheelPartEntry(WheelPart.HEAD, ((d + 7) until 28).map { wrap28(mouthStart + it) })
        )
        return SayagyiWheel(mouthStart, parts)
    }

    private fun wrap28(n: Int): Int = ((n - 1) % 28 + 28) % 28 + 1

    /**
     * The ten Dasagga (ဒသဂ္ဂ) groups. They are NOT fixed nakshatras —
     * they are subdivisions of the Sayadaw parts, defined by offsets
     * from the MOUTH (Rahu's nakshatra), so they shift with the wheel
     * and always coincide with the parts (the author's rule 2026-11-05,
     * ထပ်တူကျ): the mouth (offset 0) IS the ဇီဝ group (one nakshatra),
     * the anus (offset 14) IS the ဂူဒ group (one nakshatra), and the
     * mouth and anus sit exactly 180° apart. The book's group table
     * (his photo, upload 185) shows these groups for the reference
     * Rahu = Magha (10): 11-13 ပုပ္ဖိတ, 14-17 ဖလိတ, 18-20 နိဖ္ဖလ,
     * 21,X,22 ဇဋိတ, 23 ဂူဒ, 24-26 ရာဇဿ, 27,1,2 ထမဿ, 3-6 ဝုဒ္ဓ,
     * 7-9 မတ, 10 ဇီဝ (the book writes Abhijit as X between 21 and 22,
     * so its 22, 23, 24, 25, 26, 27 are our 23, 24, 25, 26, 27, 28).
     * There is NO ဌာသ group — that was a misread of ထာမသ in an
     * earlier session; the table is the authority. Spellings corrected
     * by the author 2026-11-05 (ပူဖိတ→ပုပ္ဖိတ, နိဖလ→နိဖ္ဖလ,
     * ဇ္ဇိုတ→ဇဋိတ, ရာဇေ→ရာဇဿ, ထာမသ→ထမဿ, ပူ→ဝုဒ္ဓ).
     * Terminology (author): ဇီဝ = the mouth (ခံတွင်း) = Rahu's
     * nakshatra; ဂူဒ = the anus (စအို) = Ketu's nakshatra.
     * Part -> groups: MOUTH = ဇီဝ; CHEST = ပူဖိတ + ဖလိတ;
     * BELLY = နိဖလ + ဇဋိတ; ANUS = ဂူဒ; TAIL = ရာဇဿ + ထမဿ;
     * HEAD = ဝုဒ္ဓ + မတ.
     */
    data class DasaggaGroup(val mm: String, val numbers: List<Int>)

    /** The ten Dasagga groups for a built [wheel]. They are subdivisions
     * of the wheel's parts, so they follow the anus anchor: ဇီဝ IS the
     * mouth, ဂူဒ IS the anus (Ketu's nakshatra). CHEST splits 3+4
     * (ပုပ္ဖိတ+ဖလိတ), BELLY 3+rest (နိဖ္ဖလ+ဇဋိတ), TAIL 3+3
     * (ရာဇဿ+ထမဿ), HEAD 4+rest (ဝုဒ္ဓ+မတ). When the anus offset
     * is 15 (Abhijit shift), BELLY has 7 so ဇဋိတ has 4, and HEAD has
     * 6 so မတ has 2 — the standard 3+3 is the offset-14 case. */
    fun dasaggaGroups(wheel: SayagyiWheel): List<DasaggaGroup> {
        val byPart = wheel.parts.associate { it.part to it.numbers }
        val chest = byPart[WheelPart.CHEST] ?: emptyList()
        val belly = byPart[WheelPart.BELLY] ?: emptyList()
        val tail = byPart[WheelPart.TAIL] ?: emptyList()
        val head = byPart[WheelPart.HEAD] ?: emptyList()
        return listOf(
            DasaggaGroup("ဇီဝ", byPart[WheelPart.MOUTH] ?: emptyList()),
            DasaggaGroup("ပုပ္ဖိတ", chest.take(3)),
            DasaggaGroup("ဖလိတ", chest.drop(3)),
            DasaggaGroup("နိဖ္ဖလ", belly.take(3)),
            DasaggaGroup("ဇဋိတ", belly.drop(3)),
            DasaggaGroup("ဂူဒ", byPart[WheelPart.ANUS] ?: emptyList()),
            DasaggaGroup("ရာဇဿ", tail.take(3)),
            DasaggaGroup("ထမဿ", tail.drop(3)),
            DasaggaGroup("ဝုဒ္ဓ", head.take(4)),
            DasaggaGroup("မတ", head.drop(4))
        )
    }

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
    /** Number of padas in a 28-system nakshatra: 1 for Abhijit (22), 3 for Uttara Ashadha (21), else 4. */
    fun padaCount(n28: Int): Int = when (n28) {
        22 -> 1
        21 -> 3
        else -> 4
    }

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

    /** Burmese numeral for a number 1-28. */
    fun burmeseNum(n: Int): String {
        val digits = "၀၁၂၃၄၅၆၇၈၉"
        return n.toString().map { digits[it - '0'] }.joinToString("")
    }

    /**
     * Display number for a 28-system nakshatra (author's rule 2026-11-06):
     * Abhijit (22) has NO number — it is just "အဘိဇိတ်"; the numbers run
     * 1–27 only, so nakshatras after Abhijit show n28−1 (Shravana 23 -> 22,
     * Revati 28 -> 27). Returns null for Abhijit. The internal 1–28
     * numbering is unchanged — this is display only.
     */
    fun displayNum(n28: Int): Int? = when {
        n28 == 22 -> null
        n28 > 22 -> n28 - 1
        else -> n28
    }

    /** Display label for a nakshatra number: "အဘိဇိတ်" for Abhijit (no number), else the Burmese numeral (1–27). */
    fun displayNumLabel(n28: Int): String {
        val d = displayNum(n28)
        return if (d == null) "အဘိဇိတ်" else burmeseNum(d)
    }

    /** Day-2 general verdict for a Moon part (book's Sayadaw rule). */
    enum class Verdict(val mm: String, val good: Boolean) {
        GOOD("ကောင်း", true),
        OBSTACLE("အခက်အခဲ", false)
    }

    fun day2Verdict(part: WheelPart): Verdict = when (part) {
        WheelPart.CHEST, WheelPart.BELLY, WheelPart.ANUS -> Verdict.GOOD
        WheelPart.TAIL, WheelPart.HEAD, WheelPart.MOUTH -> Verdict.OBSTACLE
    }

    /** Julian Day (UT) for a Unix epoch-millis instant. */
    fun jdFromMillis(millis: Long): Double = 2440587.5 + millis / 86400000.0
}
