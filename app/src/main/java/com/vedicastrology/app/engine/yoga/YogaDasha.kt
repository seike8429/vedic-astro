package com.vedicastrology.app.engine.yoga

import com.vedicastrology.app.engine.dasha.DashaEngine
import com.vedicastrology.app.engine.dasha.DashaYearConvention
import com.vedicastrology.app.engine.yoga.YogaCatalog.LORD
import com.vedicastrology.app.engine.yoga.YogaCatalog.planetMm
import com.vedicastrology.app.engine.yoga.YogaCatalog.signOf

/**
 * Spec G: YogaDashaLinker (spec D).
 *
 * Links Yogas to Vimshottari periods WITHOUT assuming a Yoga planet's Dasha
 * is automatically beneficial, and WITHOUT assuming shorter levels are more
 * accurate. Classification:
 * - DIRECT: a Yoga planet rules a level of the current 5-level path.
 * - INDIRECT: a current-path planet is conjunct with, or dispositor of, a
 *   Yoga planet (documented relationship only).
 * - NONE: neither applies. INSUFFICIENT: no path could be computed.
 *
 * Relevant periods listed = Mahadashas of Yoga planets (Julian 365.25 default,
 * matching the existing Dasha tab). Antardasha detail lives in the Dasha tab.
 */
object YogaDashaLinker {

    private val CODE_TO_NAME = mapOf(
        "Ke" to "Ketu", "Ve" to "Venus", "Su" to "Sun", "Mo" to "Moon",
        "Ma" to "Mars", "Ra" to "Rahu", "Ju" to "Jupiter",
        "Sa" to "Saturn", "Me" to "Mercury"
    )

    data class LinkResult(
        val link: DashaLink,
        val periods: List<YogaDashaPeriod>,
        val currentPathPlanets: List<String>,
        /** Why an INDIRECT link holds, e.g. "ဗုဒ္ဓဟူး သည် ဂုရုနှင့် တစ်ရာသီတည်း ယှဉ်". */
        val linkDetail: String = "",
        /** The 5-level path active at the query date (level "1".."5"). */
        val activePath: List<YogaDashaPeriod> = emptyList()
    )

    fun link(
        yogaPlanets: Set<String>,
        lons: Map<String, Double>,
        moonLon: Double,
        birthMillis: Long,
        queryMillis: Long,
        convention: DashaYearConvention = DashaYearConvention.DEFAULT
    ): LinkResult {
        val balance = DashaEngine.birthBalance(moonLon, birthMillis, convention)
        // Internal timeline stays COMPLETE (birth+130y, covering the full
        // 120y cycle plus its repeat): the age-100/120 display limits are a
        // UI policy applied in YogaScreen, never a cut to the calculation.
        val until = birthMillis + (130.0 * convention.millisPerYear()).toLong()
        val mds = DashaEngine.mahadashas(balance, convention, until)
        val periods = mds
            .filter { CODE_TO_NAME[it.planet.code] in yogaPlanets }
            .map {
                YogaDashaPeriod(
                    "Mahadasha",
                    CODE_TO_NAME[it.planet.code] ?: it.planet.code,
                    it.startEpochMillis, it.endEpochMillis
                )
            }
        val path = DashaEngine.activePath(moonLon, birthMillis, queryMillis, convention)
        if (path.isEmpty()) {
            return LinkResult(DashaLink.INSUFFICIENT, periods, emptyList())
        }
        val pathPlanets = path.mapNotNull { CODE_TO_NAME[it.planet.code] }
        val pathPeriods = path.map {
            YogaDashaPeriod(
                it.level.toString(),
                CODE_TO_NAME[it.planet.code] ?: it.planet.code,
                it.startEpochMillis, it.endEpochMillis
            )
        }
        if (pathPlanets.any { it in yogaPlanets }) {
            return LinkResult(DashaLink.DIRECT, periods, pathPlanets, "", pathPeriods)
        }
        val signOfP: (String) -> String? = { p -> lons[p]?.let { signOf(it) } }
        var detail = ""
        val indirect = pathPlanets.any { pathPlanet ->
            yogaPlanets.any { yogaPlanet ->
                val ys = signOfP(yogaPlanet) ?: return@any false
                when {
                    signOfP(pathPlanet) == ys -> {
                        detail = "${planetMm(pathPlanet)} သည် ${planetMm(yogaPlanet)}နှင့် တစ်ရာသီတည်း ယှဉ်"
                        true
                    }
                    LORD[ys] == pathPlanet -> {
                        detail = "${planetMm(pathPlanet)} သည် ${planetMm(yogaPlanet)}၏ ရာသီရှင်"
                        true
                    }
                    else -> false
                }
            }
        }
        return LinkResult(
            if (indirect) DashaLink.INDIRECT else DashaLink.NONE,
            periods, pathPlanets, detail, pathPeriods
        )
    }

    /** Display policy (NOT a calculation change): past / current / future
     *  of a period against the query date, using the TRUE interval. */
    enum class PeriodState { PAST, CURRENT, FUTURE }

    fun classifyPeriod(
        startEpochMillis: Long,
        endEpochMillis: Long,
        queryMillis: Long
    ): PeriodState =
        when {
            queryMillis < startEpochMillis -> PeriodState.FUTURE
            queryMillis >= endEpochMillis -> PeriodState.PAST
            else -> PeriodState.CURRENT
        }

    /**
     * Clip a period to [rangeStart, rangeEnd] for DISPLAY only. Returns null
     * when the period does not overlap the range. The underlying calculated
     * dates are never changed — callers keep the true period for children
     * and classification and show only the clipped portion.
     */
    fun clipPeriod(
        period: YogaDashaPeriod,
        rangeStart: Long,
        rangeEnd: Long
    ): YogaDashaPeriod? {
        val s = maxOf(period.startEpochMillis, rangeStart)
        val e = minOf(period.endEpochMillis, rangeEnd)
        if (s >= e) return null
        return period.copy(startEpochMillis = s, endEpochMillis = e)
    }

    /** Age in years (same convention year length as the calculation). */
    fun ageAt(
        millis: Long,
        birthMillis: Long,
        convention: DashaYearConvention = DashaYearConvention.DEFAULT
    ): Double = (millis - birthMillis) / convention.millisPerYear()
}
