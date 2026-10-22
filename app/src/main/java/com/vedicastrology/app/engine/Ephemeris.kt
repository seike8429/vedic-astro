package com.vedicastrology.app.engine

import swisseph.SweConst
import swisseph.SweDate
import swisseph.SwissEph

/**
 * Swiss Ephemeris bridge (Thomas Mack Java port 2.01.00, vendored in app/libs).
 *
 * - Sid mode: Lahiri (SE_SIDM_LAHIRI), set once at init.
 * - Ephemeris: Moshier analytical (SEFLG_MOSEPH) — no data files, ~1 arcsec
 *   accuracy, minimal APK size. Well within the 1-arcminute validation tolerance.
 * - All calls are synchronized: SwissEph instances are not thread-safe.
 *
 * API verified against the vendored JAR:
 *   SwissEph.swe_calc_ut / swe_houses / swe_set_sid_mode / swe_close
 *   SweDate.swe_julday, SweConst.SE_HSYS_VEHLOW etc.
 */
object Ephemeris {

    /** Planet key -> SwissEph planet id. Rahu handled via TRUE/MEAN_NODE. */
    private val PLANET_IDS = mapOf(
        "Sun" to SweConst.SE_SUN,
        "Moon" to SweConst.SE_MOON,
        "Mars" to SweConst.SE_MARS,
        "Mercury" to SweConst.SE_MERCURY,
        "Jupiter" to SweConst.SE_JUPITER,
        "Venus" to SweConst.SE_VENUS,
        "Saturn" to SweConst.SE_SATURN
    )

    private val swe: SwissEph by lazy {
        SwissEph().also {
            // Lahiri ayanamsa for all sidereal results.
            it.swe_set_sid_mode(SweConst.SE_SIDM_LAHIRI, 0.0, 0.0)
        }
    }

    private val FLAGS = SweConst.SEFLG_SIDEREAL or SweConst.SEFLG_MOSEPH or SweConst.SEFLG_SPEED

    /** Julian Day (UT) for a calendar date/time. hourUtc may be fractional. */
    @Synchronized
    fun julianDay(year: Int, month: Int, day: Int, hourUtc: Double): Double {
        return SweDate(year, month, day, hourUtc).julDay
    }

    /**
     * Sidereal longitude (degrees, 0-360) for one planet at jdUt.
     * @return Pair(longitude, retrograde)
     */
    @Synchronized
    fun planetPosition(jdUt: Double, planetKey: String, useTrueRahu: Boolean): Pair<Double, Boolean> {
        val xx = DoubleArray(6)
        val serr = StringBuffer()
        if (planetKey == "Rahu") {
            val nodeId = if (useTrueRahu) SweConst.SE_TRUE_NODE else SweConst.SE_MEAN_NODE
            swe.swe_calc_ut(jdUt, nodeId, FLAGS, xx, serr)
            // Nodes always move retrograde on average; flag from speed.
            return norm360(xx[0]) to (xx[3] < 0)
        }
        if (planetKey == "Ketu") {
            val nodeId = if (useTrueRahu) SweConst.SE_TRUE_NODE else SweConst.SE_MEAN_NODE
            swe.swe_calc_ut(jdUt, nodeId, FLAGS, xx, serr)
            return norm360(xx[0] + 180.0) to (xx[3] < 0)
        }
        val id = PLANET_IDS[planetKey] ?: error("Unknown planet $planetKey")
        swe.swe_calc_ut(jdUt, id, FLAGS, xx, serr)
        return norm360(xx[0]) to (xx[3] < 0)
    }

    /**
     * Ascendant + Vehlow Equal house cusps.
     * @return Pair(ascendantLon, cusps[12]) where cusps[i] = start of house i+1.
     */
    @Synchronized
    fun houses(jdUt: Double, lat: Double, lon: Double): Pair<Double, DoubleArray> {
        val cusps = DoubleArray(13)
        val ascmc = DoubleArray(10)
        val serr = StringBuffer()
        val rc = swe.swe_houses(
            jdUt,
            SweConst.SEFLG_SIDEREAL,
            lat, lon,
            SweConst.SE_HSYS_VEHLOW,
            cusps, ascmc
        )
        if (rc < 0) error("swe_houses failed: $serr")
        // swe_houses returns cusps[1..12]; index 0 unused. ascmc[0] = Ascendant.
        return norm360(ascmc[0]) to DoubleArray(12) { i -> norm360(cusps[i + 1]) }
    }

    fun close() = swe.swe_close()
}
