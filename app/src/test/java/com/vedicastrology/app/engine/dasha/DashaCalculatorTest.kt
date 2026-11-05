package com.vedicastrology.app.engine.dasha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JUnit mirror of the standalone harness (~/workspace/build/vedic-astro/
 * dasha/DashaHarness.kt), which is the suite actually executed in this
 * offline environment (JUnit is not in the local mirror, so this file
 * is for CI / future runs, not executed here).
 *
 * Covers specification section 9: nakshatra mapping, boundaries,
 * balance, child sequences, exact tiling, boundary queries, the
 * Saturn/Venus/Jupiter duration, and all year conventions.
 */
class DashaCalculatorTest {

    private val solar = DashaYearConvention.SOLAR_365_2425
    private val julian = DashaYearConvention.JULIAN_365_25
    private val savana = DashaYearConvention.SAVANA_360
    private val birth = 1_000_000_000_000L

    @Test
    fun all27NakshatrasMapToCorrectRulers() {
        val lords = listOf(
            DashaPlanet.KETU, DashaPlanet.VENUS, DashaPlanet.SUN,
            DashaPlanet.MOON, DashaPlanet.MARS, DashaPlanet.RAHU,
            DashaPlanet.JUPITER, DashaPlanet.SATURN, DashaPlanet.MERCURY
        )
        for (i in 0 until 27) {
            val lon = (i + 0.5) * DashaEngine.NAK_SPAN_DEG
            val (idx, _, _) = DashaEngine.nakshatraOf(lon)
            assertEquals(i, idx)
            assertEquals(lords[i % 9], DashaPlanet.nakshatraLord(idx))
        }
    }

    @Test
    fun exactNakshatraBoundariesBelongToNewNakshatra() {
        for (k in 0 until 27) {
            val (idx, into, _) = DashaEngine.nakshatraOf(k * DashaEngine.NAK_SPAN_DEG)
            assertEquals(k, idx)
            assertEquals(0.0, into, 0.0)
        }
        val (idx359, _, _) = DashaEngine.nakshatraOf(359.999999)
        assertEquals(26, idx359)
    }

    @Test
    fun birthAtNakshatraStartKeepsFullYears() {
        val b = DashaEngine.birthBalance(5 * DashaEngine.NAK_SPAN_DEG, birth, solar)
        assertEquals(DashaPlanet.RAHU, b.planet)
        assertEquals(0.0, b.fractionElapsed, 0.0)
        assertEquals(18.0, b.remainingYears, 1e-9)
        assertEquals(birth, b.mahadashaStartMillis)
    }

    @Test
    fun birthHalfwayGivesHalfBalance() {
        val b = DashaEngine.birthBalance(2.5 * DashaEngine.NAK_SPAN_DEG, birth, solar)
        assertEquals(DashaPlanet.SUN, b.planet)
        assertEquals(3.0, b.remainingYears, 1e-6)
        assertEquals(3.0, b.elapsedYears, 1e-6)
    }

    @Test
    fun childSequencesAreCyclicFromParent() {
        assertEquals(
            listOf(
                DashaPlanet.SATURN, DashaPlanet.MERCURY, DashaPlanet.KETU,
                DashaPlanet.VENUS, DashaPlanet.SUN, DashaPlanet.MOON,
                DashaPlanet.MARS, DashaPlanet.RAHU, DashaPlanet.JUPITER
            ),
            DashaEngine.childSequence(DashaPlanet.SATURN)
        )
        assertEquals(
            listOf(
                DashaPlanet.VENUS, DashaPlanet.SUN, DashaPlanet.MOON,
                DashaPlanet.MARS, DashaPlanet.RAHU, DashaPlanet.JUPITER,
                DashaPlanet.SATURN, DashaPlanet.MERCURY, DashaPlanet.KETU
            ),
            DashaEngine.childSequence(DashaPlanet.VENUS)
        )
        for (p in DashaPlanet.entries) {
            assertEquals(p, DashaEngine.childSequence(p)[0])
        }
    }

    @Test
    fun childrenTileParentExactlyWithoutGaps() {
        val md = DashaPeriod(
            1, DashaPlanet.SATURN, 0L,
            (19 * solar.millisPerYear()).toLong()
        )
        val kids = DashaEngine.generateChildren(md, solar)
        assertEquals(md.durationMillis, kids.sumOf { it.durationMillis })
        assertEquals(md.startEpochMillis, kids[0].startEpochMillis)
        assertEquals(md.endEpochMillis, kids[8].endEpochMillis)
        for (i in 0 until 8) {
            assertEquals(kids[i].endEpochMillis, kids[i + 1].startEpochMillis)
        }
    }

    @Test
    fun exactBoundaryQueryReturnsNewPeriod() {
        val md = DashaPeriod(
            1, DashaPlanet.SATURN, 0L,
            (19 * solar.millisPerYear()).toLong()
        )
        val kids = DashaEngine.generateChildren(md, solar)
        val at = kids.first { it.contains(kids[1].startEpochMillis) }
        assertEquals(DashaPlanet.MERCURY, at.planet)
    }

    @Test
    fun birthAntardashaCanBeNonFirst() {
        // Venus halfway: birth AD is Rahu, the 5th Antardasha.
        val path = DashaEngine.activePath(
            1.5 * DashaEngine.NAK_SPAN_DEG, birth, birth, solar
        )
        assertEquals(DashaPlanet.VENUS, path[0].planet)
        assertEquals(DashaPlanet.RAHU, path[1].planet)
        // His chart: Moon 287.70 -> Moon/Saturn at birth.
        val his = DashaEngine.activePath(287.70, birth, birth, solar)
        assertEquals(DashaPlanet.MOON, his[0].planet)
        assertEquals(DashaPlanet.SATURN, his[1].planet)
    }

    @Test
    fun saturnVenusJupiterDuration() {
        val md = DashaPeriod(
            1, DashaPlanet.SATURN, 0L,
            (19 * solar.millisPerYear()).toLong()
        )
        val venus = DashaEngine.generateChildren(md, solar)
            .first { it.planet == DashaPlanet.VENUS }
        val jup = DashaEngine.generateChildren(venus, solar)
            .first { it.planet == DashaPlanet.JUPITER }
        assertEquals(154.2135, jup.durationMillis / 86_400_000.0, 0.001)
    }

    @Test
    fun julianDayToMillisKnownDate() {
        assertEquals(946_728_000_000L, DashaEngine.julianDayToMillis(2451545.0))
    }

    @Test
    fun allConventionsTileExactly() {
        for (conv in DashaYearConvention.entries) {
            val md = DashaPeriod(
                1, DashaPlanet.SATURN, 0L,
                (19 * conv.millisPerYear()).toLong()
            )
            val kids = DashaEngine.generateChildren(md, conv)
            assertEquals(md.durationMillis, kids.sumOf { it.durationMillis })
        }
    }

    @Test
    fun activePathAlwaysHasFiveLevels() {
        val path = DashaEngine.activePath(287.70, birth, birth + 123456789L, julian)
        assertEquals(5, path.size)
        assertTrue(path.zipWithNext().all { (a, b) -> a.level + 1 == b.level })
    }
}
