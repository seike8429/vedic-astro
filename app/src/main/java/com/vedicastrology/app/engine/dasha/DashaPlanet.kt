package com.vedicastrology.app.engine.dasha

/**
 * Vimshottari Dasha planets in fixed sequence order.
 *
 * The order is the cycle itself: after Mercury the sequence repeats
 * from Ketu. Total = 120 years. Burmese names follow the app's
 * existing usage (see PlanetNames); Ketu/Rahu use ကေတု/ရာဟု.
 */
enum class DashaPlanet(
    val years: Int,
    /** Burmese name used in the Dasha UI. */
    val mm: String,
    /** Short English code (matches the app's existing planet codes). */
    val code: String
) {
    KETU(7, "ကေတု", "Ke"),
    VENUS(20, "သောကြာ", "Ve"),
    SUN(6, "နေ", "Su"),
    MOON(10, "လ", "Mo"),
    MARS(7, "အင်္ဂါ", "Ma"),
    RAHU(18, "ရာဟု", "Ra"),
    JUPITER(16, "ဂုရု", "Ju"),
    SATURN(19, "စနေ", "Sa"),
    MERCURY(17, "ဗုဒ္ဓဟူး", "Me");

    companion object {
        /** Cyclic lookup: index 0 = Ketu, 9 wraps back to Ketu. */
        fun fromCycleIndex(index: Int): DashaPlanet {
            val i = ((index % 9) + 9) % 9
            return entries[i]
        }

        /** Nakshatra ruler: nakshatra 0 (Ashwini) = Ketu, then cyclic. */
        fun nakshatraLord(nakshatraIndex: Int): DashaPlanet =
            fromCycleIndex(nakshatraIndex)
    }
}
