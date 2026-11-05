package com.vedicastrology.app.engine.dasha

/**
 * Year-length convention for converting dasha years to time.
 *
 * One convention must be used consistently for the Mahadasha balance
 * AND all subperiods of one generated timeline — never mixed.
 *
 * - SAVANA_360: traditional 360-day year.
 * - SOLAR_365_2425: Gregorian mean year (the spec's worked example).
 * - JULIAN_365_25: 365.25-day year — the convention the app's existing
 *   Mahadasha list (VedicEngine.vimshottari) already uses, so it is the
 *   default for consistency with the timeline the user already sees.
 *   (The spec requires 360 and 365.2425 "at minimum"; the Julian year
 *   is kept as a third option for historical compatibility.)
 */
enum class DashaYearConvention(
    val daysPerYear: Double,
    /** Short label shown in the Dasha UI. */
    val label: String
) {
    SAVANA_360(360.0, "Savana · 360 ရက်/နှစ်"),
    JULIAN_365_25(365.25, "Julian · 365.25 ရက်/နှစ်"),
    SOLAR_365_2425(365.2425, "Solar · 365.2425 ရက်/နှစ်");

    companion object {
        /** Default: matches the existing in-app Mahadasha timeline. */
        val DEFAULT: DashaYearConvention = JULIAN_365_25
    }

    /** Milliseconds in one dasha year under this convention. */
    fun millisPerYear(): Double = daysPerYear * 86_400_000.0
}
