package com.vedicastrology.app.engine.yoga

import com.vedicastrology.app.engine.StrengthCalculator
import com.vedicastrology.app.engine.yoga.YogaCatalog.COMBUST_ORB
import com.vedicastrology.app.engine.yoga.YogaCatalog.DUSTHANA
import com.vedicastrology.app.engine.yoga.YogaCatalog.MOOLA
import com.vedicastrology.app.engine.yoga.YogaCatalog.d9SignOf
import com.vedicastrology.app.engine.yoga.YogaCatalog.dignityOf
import com.vedicastrology.app.engine.yoga.YogaCatalog.houseFrom
import com.vedicastrology.app.engine.yoga.YogaCatalog.signOf
import kotlin.math.abs

/**
 * Spec G: YogaQualityEvaluator (spec C).
 *
 * Assesses Yoga planets on D1 dignity, functional status, combustion,
 * D9 placement, and Strength final. Strength final is SUPPORTING EVIDENCE
 * ONLY — it never forms, qualifies, or cancels a Yoga.
 *
 * Functional rule = the app's documented MOTAA Ch.7 §2 rule (moola lords of
 * 6/8/12 are malefic; Rahu/Ketu malefic; all others benefic). Classical
 * Parashari functional lordship varies by text; this documented choice is
 * stated in the catalog, not presented as universal BPHS.
 *
 * Combustion uses wrapped angular distance (true proximity). MOTAA's (၃)
 * table lists raw ABS distances for its example values; wrapping is applied
 * here because a planet 5° across the 0° boundary from the Sun is combust.
 */
object YogaQualityEvaluator {

    fun angularDistance(a: Double, b: Double): Double {
        val d = abs(a - b) % 360.0
        return if (d > 180.0) 360.0 - d else d
    }

    fun isCombust(planet: String, lon: Double, sunLon: Double): Boolean {
        val orb = COMBUST_ORB[planet] ?: return false
        return angularDistance(lon, sunLon) < orb
    }

    /** Functional benefic per the MOTAA Ch.7 §2 rule (see catalog note). */
    fun isFunctionalBenefic(planet: String, lagnaSign: String): Boolean {
        if (planet == "Rahu" || planet == "Ketu") return false
        val moolaSign = MOOLA[planet] ?: return true
        val moolaHouse = houseFrom(lagnaSign, moolaSign)
        return moolaHouse !in DUSTHANA
    }

    /**
     * Parashari sign aspects (Rashi drishti): whole-sign, not degree-orb.
     * All planets aspect the 7th sign; Saturn also 3rd/10th; Mars also
     * 4th/8th; Jupiter/Rahu/Ketu also 5th/9th. Same aspect set as
     * StrengthCalculator; classification uses the MOTAA functional rule.
     * (Strength aspects are degree-based with a bell; yoga aspects are
     * sign-based — two documented machineries, not a contradiction.)
     */
    fun aspectingPlanets(planet: String, lons: Map<String, Double>): List<String> {
        val targetSign = lons[planet]?.let { signOf(it) } ?: return emptyList()
        return lons.keys.filter { q ->
            q != planet && lons[q]?.let { qlon ->
                val h = houseFrom(signOf(qlon), targetSign)
                h in when (q) {
                    "Saturn" -> setOf(3, 7, 10)
                    "Mars" -> setOf(4, 7, 8)
                    "Jupiter", "Rahu", "Ketu" -> setOf(5, 7, 9)
                    else -> setOf(7)
                }
            } == true
        }.sorted()
    }

    fun qualityMap(
        planets: List<String>,
        lons: Map<String, Double>,
        lagna: Double,
        strength: StrengthCalculator.StrengthResult?
    ): Map<String, PlanetQuality> {
        val lagnaSign = signOf(lagna)
        val sunLon = lons["Sun"]
        val out = mutableMapOf<String, PlanetQuality>()
        for (p in planets) {
            val lon = lons[p] ?: continue
            val sign = signOf(lon)
            val conjunct = lons.keys.filter { it != p && lons[it]?.let { o -> signOf(o) == sign } == true }.sorted()
            val aspecting = aspectingPlanets(p, lons)
                .map { it to isFunctionalBenefic(it, lagnaSign) }
            out[p] = PlanetQuality(
                planet = p,
                sign = sign,
                dignity = dignityOf(p, sign),
                functionalBenefic = isFunctionalBenefic(p, lagnaSign),
                combust = if (sunLon == null) false else isCombust(p, lon, sunLon),
                d9Sign = d9SignOf(lon),
                d9Dignity = dignityOf(p, d9SignOf(lon)),
                strengthFinal = strength?.planets?.firstOrNull { it.planet == p }?.final ?: 0,
                conjunctPlanets = conjunct,
                longitude = lon,
                aspecting = aspecting
            )
        }
        return out
    }

    /** Limiting-factor sentences for one planet's quality (Burmese, factual).
     *  Malefic aspects limit (affliction); benefic aspects are shown as
     *  evidence on the card but never create STRONG on their own — dignity
     *  remains the STRONG evidence (v1.8.4 principle). */
    fun limitingNotes(q: PlanetQuality): List<String> {
        val notes = mutableListOf<String>()
        if (q.dignity == Dignity.DEBILITATED) {
            notes.add("${YogaCatalog.planetMm(q.planet)} နိစ် (${YogaCatalog.signMm(q.sign)}) ဖြစ်နေသည်")
        }
        if (q.combust) {
            notes.add("${YogaCatalog.planetMm(q.planet)} လောင်ကျွမ်း (နေနှင့် နီးကပ်) နေသည်")
        }
        if (!q.functionalBenefic) {
            notes.add("${YogaCatalog.planetMm(q.planet)} သည် ဤလဂ်အတွက် လုပ်ငန်းသဘော ဆိုးသော ဂြိုဟ် ဖြစ်သည်")
        }
        val maleficConj = q.conjunctPlanets.filter { it == "Rahu" || it == "Ketu" || it == "Saturn" || it == "Mars" }
        if (maleficConj.isNotEmpty()) {
            notes.add(
                "${YogaCatalog.planetMm(q.planet)} နှင့် " +
                    maleficConj.joinToString("၊ ") { YogaCatalog.planetMm(it) } + " ယှဉ်နေသည်"
            )
        }
        val maleficAsp = q.aspecting.filter { !it.second }.map { it.first }
        if (maleficAsp.isNotEmpty()) {
            notes.add(
                "${YogaCatalog.planetMm(q.planet)}သည် " +
                    maleficAsp.joinToString("၊ ") { YogaCatalog.planetMm(it) } + " ၏ ဆိုးသောအမြင်ခံနေသည်"
            )
        }
        if (q.d9Dignity == Dignity.DEBILITATED) {
            notes.add("${YogaCatalog.planetMm(q.planet)} ၏ D9 သည် နိစ် (${YogaCatalog.signMm(q.d9Sign)}) ဖြစ်သည်")
        }
        return notes
    }

    /** Supporting-factor sentences for one planet's quality (Burmese, factual). */
    fun supportingNotes(q: PlanetQuality): List<String> {
        val notes = mutableListOf<String>()
        if (q.dignity == Dignity.EXALTED || q.dignity == Dignity.OWN) {
            notes.add(
                "${YogaCatalog.planetMm(q.planet)} ${YogaCatalog.signMm(q.sign)} တွင် " +
                    YogaDetectionEngine.dignityMm(q.dignity) + " ဖြစ်သည်"
            )
        }
        if (q.d9Dignity == Dignity.EXALTED || q.d9Dignity == Dignity.OWN) {
            notes.add(
                "${YogaCatalog.planetMm(q.planet)} ၏ D9 သည် " +
                    YogaDetectionEngine.dignityMm(q.d9Dignity) + " (${YogaCatalog.signMm(q.d9Sign)}) ဖြစ်သည်"
            )
        }
        return notes
    }
}
