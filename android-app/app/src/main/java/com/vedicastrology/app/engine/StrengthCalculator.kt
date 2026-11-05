package com.vedicastrology.app.engine

import kotlin.math.floor
import kotlin.math.sin

/**
 * MOTAA Chapter 7 planet-strength / house-strength computation.
 *
 * Pure-Kotlin port of the validated Python prototype
 * (~/workspace/build/vedic-astro/strength/prototype.py), which reproduces
 * 135/138 book example values exactly. Full method spec:
 * ~/workspace/build/vedic-astro/strength/SPEC.md
 *
 * Inputs are sidereal longitudes in degrees (as computed by VedicEngine)
 * plus the sidereal Lagna. Retrograde flags do NOT change any computation.
 *
 * TODO (pending Dr Myo's answer, 2026-10-26): the book's printed house-graph
 * lord strengths for H10/H11/H12 (-43/119/9) disagree with the book's own
 * written rule (lord's final strength: Sun -23, Mercury 128, Venus 38 for the
 * example chart), while H2/H4/H6/H9 match lord finals. houseStrength[] below
 * follows the WRITTEN RULE (lord finals). If he confirms a different rule,
 * only houseStrengthFor() needs to change.
 */
object StrengthCalculator {

    data class PlanetStrength(
        val planet: String,
        val f1: Double,
        val f2: Double,
        val f3: Double,
        val f4: Double,
        val f5: Double,
        val total15: Double,
        val f6: Double,
        val final: Int
    )

    data class HouseRow(
        val house: Int,
        val strength: Int,
        val benefic: Int,
        val malefic: Int
    )

    data class StrengthResult(
        val planets: List<PlanetStrength>,
        val planetBenefic: Map<String, Int>,
        val planetMalefic: Map<String, Int>,
        val houseSums: Map<Int, Int>,
        val houseGraph: List<HouseRow>
    )

    private val SIGNS = listOf(
        "Aries", "Taurus", "Gemini", "Cancer", "Leo", "Virgo",
        "Libra", "Scorpio", "Sagittarius", "Capricorn", "Aquarius", "Pisces"
    )
    private val LORD = mapOf(
        "Aries" to "Mars", "Taurus" to "Venus", "Gemini" to "Mercury",
        "Cancer" to "Moon", "Leo" to "Sun", "Virgo" to "Mercury",
        "Libra" to "Venus", "Scorpio" to "Mars", "Sagittarius" to "Jupiter",
        "Capricorn" to "Saturn", "Aquarius" to "Saturn", "Pisces" to "Jupiter"
    )
    private val OWN_CHECK = mapOf(
        "Sun" to "Leo", "Moon" to "Cancer", "Mars" to "Scorpio",
        "Mercury" to "Virgo", "Jupiter" to "Pisces", "Venus" to "Taurus",
        "Saturn" to "Capricorn"
    )
    private val MOOLA = mapOf(
        "Sun" to "Leo", "Moon" to "Cancer", "Mars" to "Aries",
        "Mercury" to "Virgo", "Jupiter" to "Sagittarius", "Venus" to "Libra",
        "Saturn" to "Aquarius"
    )
    private val EXALT = mapOf(
        "Sun" to "Aries", "Moon" to "Taurus", "Mars" to "Capricorn",
        "Mercury" to "Virgo", "Jupiter" to "Cancer", "Venus" to "Pisces",
        "Saturn" to "Libra"
    )
    private val FRIENDS = mapOf(
        "Sun" to setOf("Moon", "Mars", "Jupiter"),
        "Moon" to setOf("Sun", "Mars", "Jupiter"),
        "Mars" to setOf("Sun", "Moon", "Jupiter"),
        "Mercury" to setOf("Sun", "Venus"),
        "Jupiter" to setOf("Sun", "Moon", "Mars"),
        "Venus" to setOf("Mercury", "Saturn"),
        "Saturn" to setOf("Mercury", "Venus")
    )
    private val ENEMIES = mapOf(
        "Sun" to setOf("Venus", "Saturn"),
        "Moon" to setOf("Mercury"),
        "Mars" to setOf("Mercury"),
        "Mercury" to setOf("Mars"),
        "Jupiter" to setOf("Venus", "Mercury"),
        "Venus" to setOf("Sun", "Moon"),
        "Saturn" to setOf("Sun", "Moon", "Mars")
    )
    private val ORBS = mapOf(
        "Moon" to 12.0, "Mars" to 17.0, "Mercury" to 14.0,
        "Jupiter" to 10.0, "Venus" to 11.0, "Saturn" to 15.0
    )
    private val ASPECTS = mapOf(
        "Sun" to listOf(0.0, 180.0),
        "Moon" to listOf(0.0, 180.0),
        "Mercury" to listOf(0.0, 180.0),
        "Venus" to listOf(0.0, 180.0),
        "Mars" to listOf(0.0, 90.0, 180.0, 210.0),
        "Jupiter" to listOf(0.0, 120.0, 180.0, 240.0),
        "Rahu" to listOf(0.0, 120.0, 180.0, 240.0),
        "Ketu" to listOf(0.0, 120.0, 180.0, 240.0),
        "Saturn" to listOf(0.0, 60.0, 180.0, 270.0)
    )
    val GRAHAS = listOf(
        "Sun", "Moon", "Mars", "Mercury", "Jupiter", "Venus", "Saturn", "Rahu", "Ketu"
    )

    /** Bell function: 100% at d=0, 0% at |d| >= width. Returns percent. */
    private fun bell(d: Double, width: Double = 15.0): Double {
        val a = kotlin.math.abs(d)
        if (a >= width) return 0.0
        val s = sin(Math.toRadians((90.0 / width) * a))
        return ((1.0 / (s * s + 1.0)) - 0.5) * 2.0 * 100.0
    }

    private fun wrapDist(a: Double, b: Double): Double {
        val d = kotlin.math.abs(a - b)
        return if (d > 330.0) 360.0 - d else d
    }

    /** Round-half-away-from-zero to Int (matches the book's displayed values). */
    private fun rnd(x: Double): Int =
        if (x >= 0) floor(x + 0.5).toInt() else kotlin.math.ceil(x - 0.5).toInt()

    private fun signOf(lon: Double): String = SIGNS[(lon.toInt() / 30) % 12]
    private fun signCenter(sign: String): Double = (SIGNS.indexOf(sign) * 30 + 15).toDouble()
    private fun d9Sign(lon: Double): String = SIGNS[(lon / (30.0 / 9.0)).toInt() % 12]

    fun compute(longitudes: Map<String, Double>, lagna: Double): StrengthResult {
        val lon = GRAHAS.associateWith { longitudes.getValue(it) }

        fun houseOf(p: Double): Int {
            val rel = ((p - lagna) % 360.0 + 360.0) % 360.0
            var h = (rel / 30).toInt() + (if (rel % 30 > 15) 1 else 0) + 1
            if (h > 12) h -= 12
            return h
        }
        fun houseCenter(h: Int): Double = ((lagna + 30 * (h - 1)) % 360.0 + 360.0) % 360.0
        fun houseSign(h: Int): String = SIGNS[(houseCenter(h).toInt() / 30) % 12]
        fun ruledHouses(planet: String): Set<Int> =
            LORD.filterValues { it == planet }.keys.map { houseOf(signCenter(it)) }.toSet()
        fun moolaHouse(planet: String): Int = houseOf(signCenter(MOOLA.getValue(planet)))
        fun signOfPlanet(planet: String): Int {
            if (planet == "Rahu" || planet == "Ketu") return -1
            return if (moolaHouse(planet) in setOf(6, 8, 12)) -1 else 1
        }

        // Factor (1)
        fun d9CatValue(planet: String): Double {
            val s = d9Sign(lon.getValue(planet))
            val cands = mutableListOf<Double>()
            if (s == EXALT[planet]) cands.add(40.0)
            if (s == OWN_CHECK[planet]) cands.add(30.0) // inferred from p199 factors
            if (s == MOOLA[planet]) cands.add(35.0) // inferred from p199 factors
            val debil = SIGNS[(SIGNS.indexOf(EXALT.getValue(planet)) + 6) % 12]
            if (s == debil) cands.add(-40.0) // inferred from p199 factors
            return cands.maxOrNull() ?: 0.0
        }
        fun factor1(planet: String): Double {
            if (planet == "Rahu" || planet == "Ketu") return 0.0
            val p = lon.getValue(planet)
            val cur = signOf(p)
            var total = 0.0
            total += bell(p - signCenter(OWN_CHECK.getValue(planet))) * 0.5
            total += bell(p - signCenter(MOOLA.getValue(planet))) * 0.75
            total += bell(p - signCenter(EXALT.getValue(planet))) * 1.0
            val debil = SIGNS[(SIGNS.indexOf(EXALT.getValue(planet)) + 6) % 12]
            total += bell(p - signCenter(debil)) * (-1.0)
            val lord = LORD.getValue(cur)
            if (lord in FRIENDS.getValue(planet)) total += bell(p - signCenter(cur)) * 0.25
            else if (lord in ENEMIES.getValue(planet)) total += bell(p - signCenter(cur)) * (-0.25)
            total += bell(p - signCenter(cur)) * 0.5 // degree strength
            total += d9CatValue(planet)
            val d9lord = LORD.getValue(d9Sign(p))
            val d = wrapDist(p, signCenter(d9Sign(p)))
            if (d9lord in FRIENDS.getValue(planet)) total += bell(d) * 0.2
            else if (d9lord in ENEMIES.getValue(planet)) total += bell(d) * (-0.2)
            return total
        }

        // Factor (2)
        fun factor2(planet: String): Double {
            val h = houseOf(lon.getValue(planet))
            val s = if (h in setOf(6, 8, 12) && h !in ruledHouses(planet)) -1 else 1
            return bell(lon.getValue(planet) - houseCenter(h)) * s * 0.5
        }

        // Factor (3)
        fun factor3(planet: String): Double {
            val orb = ORBS[planet] ?: return 0.0
            val d = kotlin.math.abs(lon.getValue(planet) - lon.getValue("Sun"))
            return if (d < orb) bell(d, orb) * (-0.5) else 0.0
        }

        // Signed influence of a planet on a target point; 0 if bell <= 60%.
        fun influence(planet: String, target: Double): Double {
            val nearest = ASPECTS.getValue(planet).minOf { a ->
                wrapDist((lon.getValue(planet) + a) % 360.0, target)
            }
            val b = bell(nearest)
            if (b <= 60.0) return 0.0
            return b * signOfPlanet(planet)
        }

        val f1 = GRAHAS.associateWith { factor1(it) }
        val f2 = GRAHAS.associateWith { factor2(it) }
        val f3 = GRAHAS.associateWith { factor3(it) }
        val hsum = (1..12).associateWith { h ->
            GRAHAS.sumOf { influence(it, houseCenter(h)) }
        }
        val f4 = GRAHAS.associateWith { p ->
            if (p == "Rahu" || p == "Ketu") 0.0 else hsum.getValue(moolaHouse(p))
        }
        val f5 = GRAHAS.associateWith { p -> hsum.getValue(houseOf(lon.getValue(p))) }
        val tot15 = GRAHAS.associateWith { p -> f1.getValue(p) + f2.getValue(p) + f3.getValue(p) + f4.getValue(p) + f5.getValue(p) }
        val f6 = GRAHAS.associateWith { p -> tot15.getValue(LORD.getValue(signOf(lon.getValue(p)))) }
        val finals = GRAHAS.associateWith { p -> rnd((tot15.getValue(p) + f6.getValue(p)) / 2.0) }

        // Planet-planet influences (self pairs excluded).
        val ppBen = GRAHAS.associateWith { 0.0 }.toMutableMap()
        val ppMal = GRAHAS.associateWith { 0.0 }.toMutableMap()
        for (a in GRAHAS) {
            for (t in GRAHAS) {
                if (a == t) continue
                val nearest = ASPECTS.getValue(a).minOf { x ->
                    wrapDist((lon.getValue(a) + x) % 360.0, lon.getValue(t))
                }
                val b = bell(nearest)
                if (b <= 60.0) continue
                val v = b * signOfPlanet(a)
                if (v > 0) ppBen[t] = ppBen.getValue(t) + v else ppMal[t] = ppMal.getValue(t) + v
            }
        }

        // House graph: strength = final of the house's moolatrikona lord, else 0.
        // TODO: H10/H11/H12 per book print vs written rule — see file header.
        val moolaLord = MOOLA.entries.associate { (planet, sign) -> sign to planet }
        val houseGraph = (1..12).map { h ->
            val s = houseSign(h)
            val strength = moolaLord[s]?.let { finals.getValue(it) } ?: 0
            var ben = 0.0
            var mal = 0.0
            for (p in GRAHAS) {
                val v = influence(p, houseCenter(h))
                if (v > 0) ben += v else if (v < 0) mal += v
            }
            HouseRow(h, strength, rnd(ben), rnd(mal))
        }

        val planets = GRAHAS.map { p ->
            PlanetStrength(
                planet = p,
                f1 = f1.getValue(p),
                f2 = f2.getValue(p),
                f3 = f3.getValue(p),
                f4 = f4.getValue(p),
                f5 = f5.getValue(p),
                total15 = tot15.getValue(p),
                f6 = f6.getValue(p),
                final = finals.getValue(p)
            )
        }
        return StrengthResult(
            planets = planets,
            planetBenefic = GRAHAS.associateWith { rnd(ppBen.getValue(it)) },
            planetMalefic = GRAHAS.associateWith { rnd(ppMal.getValue(it)) },
            houseSums = hsum.mapValues { rnd(it.value) },
            houseGraph = houseGraph
        )
    }
}
