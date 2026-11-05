package com.vedicastrology.app.engine.dasha

/**
 * One dasha period at one level (1 = Mahadasha … 5 = Prana).
 *
 * Intervals are half-open: [startEpochMillis, endEpochMillis).
 * At an exact boundary instant the NEW period is active.
 * All instants are UTC epoch millis; conversion to a display
 * timezone happens only in the UI layer.
 *
 * Children are NOT stored here: they are generated on demand by
 * DashaEngine.generateChildren, so a full 120-year five-level tree
 * (9 + 81 + 729 + 6561 + 59049 nodes) is never materialized.
 * This is the lazy-generation option the specification allows.
 */
data class DashaPeriod(
    val level: Int,
    val planet: DashaPlanet,
    val startEpochMillis: Long,
    val endEpochMillis: Long
) {
    val durationMillis: Long get() = endEpochMillis - startEpochMillis

    /** Half-open containment: true for start <= t < end. */
    fun contains(epochMillis: Long): Boolean =
        epochMillis >= startEpochMillis && epochMillis < endEpochMillis
}

/**
 * The birth Mahadasha balance (spec sections 2–3).
 *
 * The Mahadasha is NOT restarted at birth: [mahadashaStartMillis] is
 * the theoretical start, [elapsedYears] before birth, so the
 * Antardasha active at birth is found by walking from that start
 * (it is often not the first Antardasha).
 */
data class BirthDashaBalance(
    /** 0–26, Ashwini = 0. */
    val nakshatraIndex: Int,
    /** Degrees of the Moon inside its birth nakshatra (0–13.333…). */
    val degreesIntoNakshatra: Double,
    /** Fraction of the birth nakshatra already elapsed at birth. */
    val fractionElapsed: Double,
    /** Fraction of the birth nakshatra remaining at birth. */
    val fractionRemaining: Double,
    /** Birth Mahadasha lord (= birth nakshatra lord). */
    val planet: DashaPlanet,
    /** Years of the birth Mahadasha elapsed before birth. */
    val elapsedYears: Double,
    /** Years of the birth Mahadasha remaining at birth. */
    val remainingYears: Double,
    /** Theoretical Mahadasha start (UTC epoch millis, before birth). */
    val mahadashaStartMillis: Long
)
