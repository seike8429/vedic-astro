package com.vedicastrology.app.engine.dasha

import kotlin.math.floor

/**
 * Vimshottari Dasha engine — five nested levels, instant precision.
 *
 * This module is deliberately separate from the existing chart code:
 * it does not touch D1/D9, the Vehlow house system, or the Strength
 * algorithm, and it does not modify VedicEngine.vimshottari (the
 * existing Mahadasha list shown on the chart screen). It only reads
 * the birth Moon's sidereal longitude and the birth instant, both of
 * which the existing chart computation already produces.
 *
 * Conventions (all per the specification):
 * - Moon longitude is SIDEREAL (Lahiri, as computed by the app's
 *   ephemeris); never tropical. Normalized to [0, 360).
 * - 27 nakshatras of 13°20′ each; half-open boundaries — the exact
 *   beginning of a nakshatra belongs to the new nakshatra.
 * - A parent period divides into 9 children starting with the parent
 *   planet, then cyclically; child duration = parent × years / 120.
 * - Boundaries are computed from the cumulative fraction of the
 *   parent's span (no incremental drift); the final child's end is
 *   forced to equal the parent's end exactly.
 * - All instants are UTC epoch millis; display-timezone conversion
 *   happens only in the UI.
 */
object DashaEngine {

    /** Total Vimshottari cycle in years. */
    const val TOTAL_YEARS = 120

    /** One nakshatra span in degrees: 360 / 27 = 13°20′. */
    const val NAK_SPAN_DEG = 360.0 / 27.0

    /** Deepest level: 1 Mahadasha … 5 Prana. */
    const val MAX_LEVEL = 5

    private const val MILLIS_PER_DAY = 86_400_000.0

    fun norm360(x: Double): Double = ((x % 360.0) + 360.0) % 360.0

    /**
     * Nakshatra of a sidereal longitude.
     * @return Triple(index 0–26, degrees into nakshatra, fraction 0–1).
     * Exact nakshatra beginnings belong to the new nakshatra. Because
     * 13°20′ is not exactly representable in binary, a longitude that
     * is mathematically on a boundary can scale to k ± 1e-13; values
     * within 1e-9 nakshatra (~0.05 arcsec) of an integer are snapped
     * to that boundary instead of being flipped by float noise.
     */
    fun nakshatraOf(moonLon: Double): Triple<Int, Double, Double> {
        val lon = norm360(moonLon)
        val scaled = lon / NAK_SPAN_DEG
        val nearest = kotlin.math.round(scaled)
        return if (kotlin.math.abs(scaled - nearest) < 1e-9) {
            val idx = nearest.toInt().coerceIn(0, 26)
            Triple(idx, 0.0, 0.0)
        } else {
            val idx = floor(scaled).toInt().coerceIn(0, 26)
            val into = lon - idx * NAK_SPAN_DEG
            Triple(idx, into, into / NAK_SPAN_DEG)
        }
    }

    /**
     * Birth Mahadasha balance from the birth Moon longitude.
     * The theoretical Mahadasha start is birth minus the elapsed part.
     */
    fun birthBalance(
        moonLon: Double,
        birthEpochMillis: Long,
        convention: DashaYearConvention
    ): BirthDashaBalance {
        val (idx, into, fraction) = nakshatraOf(moonLon)
        val planet = DashaPlanet.nakshatraLord(idx)
        val elapsedYears = planet.years * fraction
        val remainingYears = planet.years * (1.0 - fraction)
        val startMillis = birthEpochMillis -
            (elapsedYears * convention.millisPerYear()).toLong()
        return BirthDashaBalance(
            nakshatraIndex = idx,
            degreesIntoNakshatra = into,
            fractionElapsed = fraction,
            fractionRemaining = 1.0 - fraction,
            planet = planet,
            elapsedYears = elapsedYears,
            remainingYears = remainingYears,
            mahadashaStartMillis = startMillis
        )
    }

    /** The 9 child planets of a parent, starting with the parent itself. */
    fun childSequence(parent: DashaPlanet): List<DashaPlanet> {
        val start = parent.ordinal
        return (0 until 9).map { DashaPlanet.fromCycleIndex(start + it) }
    }

    /**
     * The 9 children tiling [parent] exactly.
     * Each boundary is computed from the parent's start (cumulative
     * fraction), never by successive addition; the last child's end
     * is forced to the parent's end.
     */
    fun generateChildren(
        parent: DashaPeriod,
        convention: DashaYearConvention
    ): List<DashaPeriod> {
        require(parent.level < MAX_LEVEL) {
            "Level ${parent.level} has no children (max $MAX_LEVEL)"
        }
        val span = parent.durationMillis.toDouble()
        val children = childSequence(parent.planet)
        var cumYears = 0
        val out = ArrayList<DashaPeriod>(9)
        for (i in children.indices) {
            val child = children[i]
            val start = parent.startEpochMillis +
                (span * cumYears / TOTAL_YEARS).toLong()
            cumYears += child.years
            val end = if (i == children.lastIndex) parent.endEpochMillis
            else parent.startEpochMillis +
                (span * cumYears / TOTAL_YEARS).toLong()
            out.add(DashaPeriod(parent.level + 1, child, start, end))
        }
        return out
    }

    /**
     * Level-1 (Mahadasha) periods from the theoretical birth-MD start,
     * cycling the 9-lord sequence, until [untilMillis] is covered.
     * The first period is the FULL birth Mahadasha (birth falls inside
     * it); the balance section reports the remaining part instead.
     */
    fun mahadashas(
        balance: BirthDashaBalance,
        convention: DashaYearConvention,
        untilMillis: Long
    ): List<DashaPeriod> {
        val out = ArrayList<DashaPeriod>()
        var start = balance.mahadashaStartMillis
        var idx = balance.planet.ordinal
        var guard = 0
        while (start <= untilMillis && guard < 90) {
            val planet = DashaPlanet.fromCycleIndex(idx)
            val end = start +
                (planet.years * convention.millisPerYear()).toLong()
            out.add(DashaPeriod(1, planet, start, end))
            start = end
            idx++
            guard++
        }
        return out
    }

    /**
     * The complete five-level active path at [queryMillis].
     * Returns empty if the query predates the theoretical MD start.
     * Drills down only the active branch at each level (lazy).
     */
    fun activePath(
        moonLon: Double,
        birthEpochMillis: Long,
        queryMillis: Long,
        convention: DashaYearConvention
    ): List<DashaPeriod> {
        val balance = birthBalance(moonLon, birthEpochMillis, convention)
        if (queryMillis < balance.mahadashaStartMillis) return emptyList()
        val mds = mahadashas(balance, convention, queryMillis)
        var current = mds.firstOrNull { it.contains(queryMillis) } ?: return emptyList()
        val path = ArrayList<DashaPeriod>(MAX_LEVEL)
        path.add(current)
        while (current.level < MAX_LEVEL) {
            val child = generateChildren(current, convention)
                .firstOrNull { it.contains(queryMillis) } ?: break
            path.add(child)
            current = child
        }
        return path
    }

    /** Julian Day (UT) → UTC epoch millis. JD 2440587.5 = 1970-01-01. */
    fun julianDayToMillis(jdUt: Double): Long =
        ((jdUt - 2440587.5) * MILLIS_PER_DAY).toLong()
}
