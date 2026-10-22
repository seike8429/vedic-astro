package com.vedicastrology.app.engine

/**
 * Orchestrates a full chart computation:
 *   birth/input date-time + place -> SwissEph -> VedicEngine.buildTable()
 *
 * The validated VedicEngine does all Vedic math (nakshatra/pada/navamsa/
 * bhava/RL-NL-SL/dasha). This class only supplies it with sidereal longitudes,
 * ascendant and cusps from the ephemeris. No Vedic math is duplicated here.
 */

/** User-supplied birth (or transit) data. */
data class BirthData(
    val year: Int, val month: Int, val day: Int,
    val hour: Int, val minute: Int,
    /** UTC offset in hours, e.g. 6.5 for Myanmar. */
    val tzOffset: Double,
    val latitude: Double,
    val longitude: Double,
    val placeName: String = ""
)

/** One planet row plus display helpers (retrograde comes from the ephemeris). */
data class ChartPlanet(
    val row: PlanetRow,
    val retrograde: Boolean
)

/** Full result consumed by both chart styles + table + dasha. */
data class ChartResult(
    val planets: List<ChartPlanet>,   // Lagna first, then Sun..Ketu
    val dashas: List<DashaPeriod>,
    val ascendant: Double,
    val julianDay: Double,
    val useTrueRahu: Boolean
)

object ChartCalculator {

    /**
     * Compute the chart for the given date/time/place.
     * For dasha, pass the natal BirthData separately when computing transits.
     */
    fun compute(
        data: BirthData,
        natalData: BirthData = data,
        useTrueRahu: Boolean = true
    ): ChartResult {
        val hourUtc = data.hour + data.minute / 60.0 - data.tzOffset
        val jd = Ephemeris.julianDay(data.year, data.month, data.day, hourUtc)

        val longitudes = mutableMapOf<String, Double>()
        val retro = mutableMapOf<String, Boolean>()
        for (planet in VedicEngine.PLANET_ORDER) {
            val (lon, isRetro) = Ephemeris.planetPosition(jd, planet, useTrueRahu)
            longitudes[planet] = lon
            retro[planet] = isRetro
        }

        val (asc, cusps) = Ephemeris.houses(jd, data.latitude, data.longitude)

        val rows = VedicEngine.buildTable(longitudes, asc, cusps)
        val planets = rows.map { row ->
            ChartPlanet(row, retro[row.planet] ?: false)
        }

        // Dasha is always rooted at the NATAL Moon.
        val natalJd = Ephemeris.julianDay(
            natalData.year, natalData.month, natalData.day,
            natalData.hour + natalData.minute / 60.0 - natalData.tzOffset
        )
        // Natal Moon (sidereal Lahiri) roots the Vimshottari sequence.
        val (natalMoon, _) = Ephemeris.planetPosition(natalJd, "Moon", useTrueRahu)
        val dashas = VedicEngine.vimshottari(
            natalMoon, natalData.year, natalData.month, natalData.day
        )

        return ChartResult(planets, dashas, asc, jd, useTrueRahu)
    }
}
