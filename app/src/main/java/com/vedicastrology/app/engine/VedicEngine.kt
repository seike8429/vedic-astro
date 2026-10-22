package com.vedicastrology.app.engine

/**
 * Vedic Astrology Calculation Engine (Kotlin — Android)
 * =====================================================
 * Phase 1 foundation. Ported from validated Python prototype (engine.py).
 * Validation: max 0.84 arcmin error vs MOTAA V31.1 on 9 bodies.
 * See ../VALIDATION.md for full report.
 *
 * Swiss Ephemeris Java integration:
 *   Add swisseph.jar (Astrodienst, GPLv2) to app/libs, OR use the
 *   Maven-published port. Ephemeris: use SwissEph with Moshier mode
 *   (no data files needed, ~1 arcsec accuracy — ample for astrology).
 *   Set sidereal mode to Lahiri: swe.setSidMode(SweConst.SE_SIDM_LAHIRI).
 *
 *   Planetary longitude:
 *     val xx = DoubleArray(6)
 *     swe.swe_calc_ut(jdUt, planet, SweConst.SEFLG_SIDEREAL, xx, serr)
 *     // xx[0] = sidereal longitude in degrees
 *   Ascendant (Vehlow Equal):
 *     val cusps = DoubleArray(13); val ascmc = DoubleArray(10)
 *     swe.swe_houses(jdUt, lat, lon, 'V'.code, cusps, ascmc)
 *     // ascmc[0] = Ascendant; cusps[1..12] = house cusps
 *
 * All other math below is pure Kotlin — no library dependency.
 * No PHP anywhere in this project.
 */

import kotlin.math.*

// ---------------------------------------------------------------- constants

data class SignInfo(val en: String, val mm: String, val code: String)

val SIGNS = listOf(
    SignInfo("Aries", "မိဿ", "Ar"), SignInfo("Taurus", "ပြိဿ", "Ta"),
    SignInfo("Gemini", "မေထုန်", "Ge"), SignInfo("Cancer", "ကဋက", "Cn"),
    SignInfo("Leo", "သိဟ်", "Le"), SignInfo("Virgo", "ကန်", "Vi"),
    SignInfo("Libra", "တူ", "Li"), SignInfo("Scorpio", "ဗြိစ္ဆာ", "Sc"),
    SignInfo("Sagittarius", "ဓနု", "Sg"), SignInfo("Capricorn", "မကာရ", "Cp"),
    SignInfo("Aquarius", "ကုမ်", "Aq"), SignInfo("Pisces", "မိန်", "Pi")
)

val RASHI_LORDS = listOf(
    "Mars", "Venus", "Mercury", "Moon", "Sun", "Mercury",
    "Venus", "Mars", "Jupiter", "Saturn", "Saturn", "Jupiter"
)

data class NakshatraInfo(val en: String, val mm: String, val lord: String)

val NAKSHATRAS = listOf(
    NakshatraInfo("Ashwini", "အဿဝဏီ", "Ketu"),
    NakshatraInfo("Bharani", "ဘရဏီ", "Venus"),
    NakshatraInfo("Krittika", "ကြတ္တိကာ", "Sun"),
    NakshatraInfo("Rohini", "ရောဟဏီ", "Moon"),
    NakshatraInfo("Mrigashira", "မိဂသီ", "Mars"),
    NakshatraInfo("Ardra", "အာဒ္ဒါ", "Rahu"),
    NakshatraInfo("Punarvasu", "ပုန္နဝသု", "Jupiter"),
    NakshatraInfo("Pushya", "ပုဿျ", "Saturn"),
    NakshatraInfo("Ashlesha", "အဿလေသ", "Mercury"),
    NakshatraInfo("Magha", "မဃာ", "Ketu"),
    NakshatraInfo("Purva Phalguni", "ပုဗ္ဗာဖလဂုဏီ", "Venus"),
    NakshatraInfo("Uttara Phalguni", "ဥတ္တရာဖလဂုဏီ", "Sun"),
    NakshatraInfo("Hasta", "ဟတ္ထ", "Moon"),
    NakshatraInfo("Chitra", "စိတြ", "Mars"),
    NakshatraInfo("Swati", "သာတိ", "Rahu"),
    NakshatraInfo("Vishakha", "ဝိသာခါ", "Jupiter"),
    NakshatraInfo("Anuradha", "အနုရာဓာ", "Saturn"),
    NakshatraInfo("Jyeshtha", "ဇေဋ္ဌ", "Mercury"),
    NakshatraInfo("Moola", "မူလ", "Ketu"),
    NakshatraInfo("Purva Ashadha", "ပုဗ္ဗာသာဠ်", "Venus"),
    NakshatraInfo("Uttara Ashadha", "ဥတ္တရာသာဠ်", "Sun"),
    NakshatraInfo("Shravana", "သရဝဏ်", "Moon"),
    NakshatraInfo("Dhanishta", "ဓနိဋ္ဌ", "Mars"),
    NakshatraInfo("Shatabhisha", "သတ္တဘိသဇ်", "Rahu"),
    NakshatraInfo("Purva Bhadrapada", "ပုဗ္ဗာဘဒြပိုဒ်", "Jupiter"),
    NakshatraInfo("Uttara Bhadrapada", "ဥတ္တရာဘဒြပိုဒ်", "Saturn"),
    NakshatraInfo("Revati", "ရေဝတီ", "Mercury")
)

val VIMSHOTTARI: List<Pair<String, Int>> = listOf(
    "Ketu" to 7, "Venus" to 20, "Sun" to 6, "Moon" to 10,
    "Mars" to 7, "Rahu" to 18, "Jupiter" to 16, "Saturn" to 19,
    "Mercury" to 17
)
val VIM_YEARS = VIMSHOTTARI.toMap()

const val NAK_SPAN = 360.0 / 27.0
const val PADA_SPAN = NAK_SPAN / 4.0
const val NAV_SPAN = 30.0 / 9.0

// ---------------------------------------------------------------- data

/** One row of the planetary positions table (both chart styles consume this). */
data class PlanetRow(
    val planet: String,
    val longitude: Double,       // sidereal, 0-360
    val dms: String,             // "DD:MM:SS.s" within sign
    val sign: String, val signMm: String,
    val nakshatra: String, val nakshatraMm: String,
    val pada: Int,               // 1-4
    val rl: String,              // Rashi lord
    val nl: String,              // Nakshatra lord
    val sl: String,              // KP Sub lord
    val navamsa: String,         // D-9 sign (English)
    val navamsaMm: String,
    val bhava: Int               // 1-12 (Vehlow Equal)
)

data class DashaPeriod(
    val lord: String,
    val startY: Int, val startM: Int, val startD: Int,
    val endY: Int, val endM: Int, val endD: Int,
    val years: Double
)

// ---------------------------------------------------------------- pure math

fun norm360(x: Double): Double = ((x % 360.0) + 360.0) % 360.0

fun formatDms(degInSign: Double): String {
    val d = degInSign.toInt()
    val m = ((degInSign - d) * 60).toInt()
    val s = (degInSign - d - m / 60.0) * 3600
    return "%02d:%02d:%04.1f".format(d, m, s)
}

/** Returns Triple(nakshatraIndex 0-26, pada 1-4, degreesIntoNakshatra). */
fun nakshatraOf(lon: Double): Triple<Int, Int, Double> {
    val l = norm360(lon)
    var idx = (l / NAK_SPAN).toInt().coerceIn(0, 26)
    val into = l - idx * NAK_SPAN
    val pada = ((into / PADA_SPAN).toInt() + 1).coerceIn(1, 4)
    return Triple(idx, pada, into)
}

/** KP sub lord: nakshatra partitioned in Vimshottari order from nakshatra lord. */
fun kpSubLord(lon: Double): String {
    val (nakIdx, _, intoNak) = nakshatraOf(lon)
    val seqLord = NAKSHATRAS[nakIdx].lord
    val order = VIMSHOTTARI.map { it.first }
    val start = order.indexOf(seqLord)
    val seq = order.drop(start) + order.take(start)
    var pos = intoNak
    for (lord in seq) {
        val span = NAK_SPAN * (VIM_YEARS[lord] ?: 0) / 120.0
        if (pos < span) return lord
        pos -= span
    }
    return seq.last()
}

/** Navamsa (D-9) sign index 0-11. */
fun navamsaOf(lon: Double): Int {
    val l = norm360(lon)
    val s = (l / 30).toInt()
    val pada = ((l % 30) / NAV_SPAN).toInt().coerceIn(0, 8)
    return when (s % 3) {
        0 -> s + pada          // movable: from same sign
        1 -> s + 8 + pada      // fixed: from 9th
        else -> s + 4 + pada   // dual: from 5th
    } % 12
}

/** Bhava number 1-12 given 12 Vehlow Equal cusps (cusp[i] = start of house i+1). */
fun bhavaOf(lon: Double, cusps: DoubleArray): Int {
    val l = norm360(lon)
    for (i in 0 until 12) {
        val start = norm360(cusps[i])
        val end = norm360(cusps[(i + 1) % 12])
        val inside = if (start < end) l >= start && l < end
                     else l >= start || l < end
        if (inside) return i + 1
    }
    return 12
}

// ---------------------------------------------------------------- engine

/**
 * Assembles the full chart from computed longitudes.
 * Callers supply sidereal longitudes (from SwissEph) + ascendant + cusps.
 */
object VedicEngine {

    val PLANET_ORDER = listOf(
        "Sun", "Moon", "Mars", "Mercury", "Jupiter",
        "Venus", "Saturn", "Rahu", "Ketu"
    )

    fun buildTable(
        longitudes: Map<String, Double>,  // sidereal lon per planet
        ascendant: Double,
        cusps: DoubleArray                // 12 Vehlow Equal cusps
    ): List<PlanetRow> {
        val rows = mutableListOf<PlanetRow>()
        val bodies = listOf("Lagna" to ascendant) +
                PLANET_ORDER.map { it to (longitudes[it] ?: 0.0) }
        for ((name, lon) in bodies) {
            val l = norm360(lon)
            val s = (l / 30).toInt()
            val (nakIdx, pada, _) = nakshatraOf(l)
            val nak = NAKSHATRAS[nakIdx]
            val nav = navamsaOf(l)
            rows.add(PlanetRow(
                planet = name,
                longitude = l,
                dms = formatDms(l % 30),
                sign = SIGNS[s].en, signMm = SIGNS[s].mm,
                nakshatra = nak.en, nakshatraMm = nak.mm,
                pada = pada,
                rl = RASHI_LORDS[s],
                nl = nak.lord,
                sl = kpSubLord(l),
                navamsa = SIGNS[nav].en, navamsaMm = SIGNS[nav].mm,
                bhava = bhavaOf(l, cusps)
            ))
        }
        return rows
    }

    /**
     * Vimshottari Mahadasha sequence from birth.
     * @param moonLon sidereal Moon longitude at birth
     * @param birthY/M/D birth date (local); balance computed from Moon's nakshatra
     * Returns periods covering 120 years.
     */
    fun vimshottari(
        moonLon: Double, birthY: Int, birthM: Int, birthD: Int
    ): List<DashaPeriod> {
        val (nakIdx, _, intoNak) = nakshatraOf(moonLon)
        val lord0 = NAKSHATRAS[nakIdx].lord
        val fracLeft = 1.0 - intoNak / NAK_SPAN
        val order = VIMSHOTTARI.map { it.first }
        val start = order.indexOf(lord0)
        val seq = order.drop(start) + order.take(start)

        val result = mutableListOf<DashaPeriod>()
        var y = birthY; var m = birthM; var d = birthD
        var first = true
        var totalYears = 0.0
        var ci = 0
        while (totalYears < 120.0) {
            val lord = seq[ci % seq.size]
            val yrs = if (first) (VIM_YEARS[lord] ?: 0) * fracLeft
                      else (VIM_YEARS[lord] ?: 0).toDouble()
            first = false
            // advance date by yrs (365.25 d/yr)
            val totalDays = (yrs * 365.25).toLong()
            val (ey, em, ed) = addDays(y, m, d, totalDays)
            result.add(DashaPeriod(lord, y, m, d, ey, em, ed, yrs))
            y = ey; m = em; d = ed
            totalYears += yrs
            ci++
        }
        return result
    }

    // Simple Gregorian date arithmetic (no external dep)
    private fun isLeap(y: Int) = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0

    private fun dim(y: Int, m: Int) = when (m) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        else -> if (isLeap(y)) 29 else 28
    }

    private fun addDays(y: Int, m: Int, d: Int, days: Long): Triple<Int, Int, Int> {
        var yy = y; var mm = m; var dd = d; var left = days
        while (left > 0) {
            val inMonth = (dim(yy, mm) - dd).toLong()
            if (left <= inMonth) { dd += left.toInt(); left = 0 }
            else { left -= (inMonth + 1); dd = 1; mm++; if (mm > 12) { mm = 1; yy++ } }
        }
        return Triple(yy, mm, dd)
    }
}
