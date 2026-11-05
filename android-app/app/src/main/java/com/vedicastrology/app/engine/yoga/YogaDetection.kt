package com.vedicastrology.app.engine.yoga

import com.vedicastrology.app.engine.yoga.YogaCatalog.DUSTHANA
import com.vedicastrology.app.engine.yoga.YogaCatalog.KENDRA
import com.vedicastrology.app.engine.yoga.YogaCatalog.LORD
import com.vedicastrology.app.engine.yoga.YogaCatalog.TRIKONA
import com.vedicastrology.app.engine.yoga.YogaCatalog.dignityOf
import com.vedicastrology.app.engine.yoga.YogaCatalog.houseFrom
import com.vedicastrology.app.engine.yoga.YogaCatalog.planetMm
import com.vedicastrology.app.engine.yoga.YogaCatalog.signMm
import com.vedicastrology.app.engine.yoga.YogaCatalog.signOf

/** Raw detection from YogaDetectionEngine; enriched into DetectedYoga by YogaReport. */
data class RawDetection(
    val ruleId: String,
    val status: YogaStatus,
    val planets: List<String>,
    val formationFacts: List<String>,
    val supportingFactors: List<String>,
    val limitingFactors: List<String>,
    /** Cancellation evidence, separate from strength (Error Prevention Rule 3). */
    val cancellationFacts: List<String> = emptyList()
)

/**
 * Spec G: YogaDetectionEngine. Whole-sign houses from Lagna (the classical yoga
 * basis, matching MOTAA whole-sign). Strength % is never consulted here.
 *
 * Pair rules (Raja sambandha, Dhana) emit ONLY formed instances; the fixed
 * rules (Pancha x5, Gaja Kesari, Yogakaraka) always emit one entry so the
 * report can show why a Yoga did or did not form.
 */
object YogaDetectionEngine {

    private val SEVEN = listOf("Sun", "Moon", "Mars", "Mercury", "Jupiter", "Venus", "Saturn")

    fun dignityMm(d: Dignity): String = when (d) {
        Dignity.EXALTED -> "ဥုစ်"
        Dignity.OWN -> "ကိုယ်ပိုင်"
        Dignity.MOOLA -> "မူလ"
        Dignity.DEBILITATED -> "နိစ်"
        Dignity.NEUTRAL -> "သာမန်"
    }

    fun detect(lons: Map<String, Double>, lagna: Double): List<RawDetection> {
        val lagnaSign = signOf(lagna)
        val signOfP: (String) -> String? = { p -> lons[p]?.let { signOf(it) } }
        val houseOfP: (String) -> Int = { p ->
            val s = signOfP(p)
            if (s == null) -1 else houseFrom(lagnaSign, s)
        }
        val out = mutableListOf<RawDetection>()

        // ---- Pancha Mahapurusha (fixed five) ----
        val panchaPlanet = mapOf(
            "rucaka" to "Mars", "bhadra" to "Mercury", "hamsa" to "Jupiter",
            "malavya" to "Venus", "sasa" to "Saturn"
        )
        for ((id, planet) in panchaPlanet) {
            val sign = signOfP(planet)
            if (sign == null) {
                out.add(RawDetection(id, YogaStatus.INDETERMINATE, listOf(planet),
                    listOf("${planetMm(planet)}၏ တည်နေရာ မရှိပါ"), emptyList(), emptyList()))
                continue
            }
            val house = houseOfP(planet)
            val dig = dignityOf(planet, sign)
            val fact = "${planetMm(planet)} — ${signMm(sign)} (${dignityMm(dig)})၊ " +
                "လဂ်မှ $house အိမ်" + if (house in KENDRA) " (ကိန်း)" else ""
            when {
                (dig == Dignity.EXALTED || dig == Dignity.OWN) && house in KENDRA ->
                    out.add(RawDetection(id, YogaStatus.FORMED, listOf(planet),
                        listOf(fact), listOf("ကိန်းတွင် ဥုစ်/ကိုယ်ပိုင် တည်ခြင်း"), emptyList()))
                dig == Dignity.MOOLA && house in KENDRA ->
                    out.add(RawDetection(id, YogaStatus.QUALIFIED, listOf(planet),
                        listOf(fact),
                        listOf("ကိန်းတွင် မူလတြိကောဏ တည်ခြင်း (မူကွဲ အရည်အချင်း)"),
                        listOf("ဥုစ်/ကိုယ်ပိုင် မဟုတ်သောကြောင့် အပြည့်အဝ ဖွဲ့စည်းသည်ဟု မသတ်မှတ်ပါ")))
                else ->
                    out.add(RawDetection(id, YogaStatus.NOT_FORMED, listOf(planet),
                        listOf(fact), emptyList(),
                        listOf("ဖွဲ့စည်းမှု စည်းကမ်း မပြည့်မီပါ")))
            }
        }

        // ---- Gaja Kesari (from Moon) ----
        val moonSign = signOfP("Moon")
        val jupSign = signOfP("Jupiter")
        if (moonSign == null || jupSign == null) {
            out.add(RawDetection("gaja_kesari", YogaStatus.INDETERMINATE, listOf("Jupiter"),
                listOf("လ သို့မဟုတ် ဂုရု၏ တည်နေရာ မရှိပါ"), emptyList(), emptyList()))
        } else {
            val houseFromMoon = houseFrom(moonSign, jupSign)
            val fact = "ဂုရု — ${signMm(jupSign)}၊ လမှ $houseFromMoon အိမ်" +
                if (houseFromMoon in KENDRA) " (ကိန်း)" else ""
            if (houseFromMoon in KENDRA) {
                out.add(RawDetection("gaja_kesari", YogaStatus.FORMED, listOf("Jupiter"),
                    listOf(fact), listOf("လမှ ကိန်းတွင် ဂုရု တည်ခြင်း"), emptyList()))
            } else {
                out.add(RawDetection("gaja_kesari", YogaStatus.NOT_FORMED, listOf("Jupiter"),
                    listOf(fact), emptyList(), listOf("လမှ ကိန်း မဟုတ်ပါ")))
            }
        }

        // ---- Budha Aditya (Sun + Mercury, same sign) ----
        val sunSign = signOfP("Sun")
        val mercSign = signOfP("Mercury")
        if (sunSign == null || mercSign == null) {
            out.add(RawDetection("budha_aditya", YogaStatus.INDETERMINATE, listOf("Sun", "Mercury"),
                listOf("နေ သို့မဟုတ် ဗုဒ္ဓဟူး၏ တည်နေရာ မရှိပါ"), emptyList(), emptyList()))
        } else if (sunSign == mercSign) {
            val house = houseFrom(lagnaSign, sunSign)
            val fact = "နေ နှင့် ဗုဒ္ဓဟူး — ${signMm(sunSign)} တွင် ယှဉ် (လဂ်မှ $house အိမ်)"
            val sup = if (house in KENDRA || house in TRIKONA) {
                listOf("ကိန်း/တြိကောဏ ($house အိမ်) တွင် ယှဉ်နေသည်")
            } else emptyList()
            val lim = if (house in DUSTHANA) {
                listOf("ဒုဋ္ဌာန ($house အိမ်) တွင် ယှဉ်နေသည်")
            } else emptyList()
            out.add(RawDetection("budha_aditya", YogaStatus.FORMED, listOf("Sun", "Mercury"),
                listOf(fact), sup, lim))
        } else {
            out.add(RawDetection("budha_aditya", YogaStatus.NOT_FORMED, listOf("Sun", "Mercury"),
                listOf("နေ ${signMm(sunSign)}၊ ဗုဒ္ဓဟူး ${signMm(mercSign)} — တစ်ရာသီတည်း မဟုတ်ပါ"),
                emptyList(), emptyList()))
        }

        // ---- House lords from Lagna ----
        val lagnaIdx = YogaCatalog.signIndex(lagnaSign)
        fun houseSign(house: Int): String = YogaCatalog.SIGNS[(lagnaIdx + house - 1) % 12]
        fun lordsOf(houses: Set<Int>): Set<String> =
            houses.mapNotNull { LORD[houseSign(it)] }.toSet()
        val kendraLords = lordsOf(KENDRA)
        val trikonaLords = lordsOf(TRIKONA)
        val lagnaLord = LORD[lagnaSign]

        // ---- Raja Yogakaraka ----
        val yogakarakas = SEVEN.filter { p ->
            p != lagnaLord &&
                housesRuledBy(p, KENDRA, ::houseSign).isNotEmpty() &&
                housesRuledBy(p, TRIKONA, ::houseSign).isNotEmpty()
        }
        if (yogakarakas.isEmpty()) {
            out.add(RawDetection("raja_yogakaraka", YogaStatus.NOT_FORMED, emptyList(),
                listOf("ကိန်းနှင့် တြိကောဏ နှစ်မျိုးလုံး ပိုင်သော ဂြိုဟ် မရှိပါ"),
                emptyList(), emptyList()))
        } else {
            for (p in yogakarakas) {
                val kHouses = housesRuledBy(p, KENDRA, ::houseSign)
                val tHouses = housesRuledBy(p, TRIKONA, ::houseSign)
                out.add(RawDetection("raja_yogakaraka", YogaStatus.FORMED, listOf(p),
                    listOf(
                        "${planetMm(p)} — ကိန်း အိမ် ${kHouses.joinToString("၊ ")} နှင့် " +
                            "တြိကောဏ အိမ် ${tHouses.joinToString("၊ ")} တို့၏ အိမ်ရှင်"
                    ),
                    listOf("ယောဂကာရက ဂြိုဟ်"), emptyList()))
            }
        }

        // ---- Raja sambandha + Dhana (pair rules, formed instances only) ----
        fun relation(a: String, b: String): String? {
            val sa = signOfP(a) ?: return null
            val sb = signOfP(b) ?: return null
            if (sa == sb) return "တစ်ရာသီတည်း ယှဉ်"
            val ia = YogaCatalog.signIndex(sa)
            val ib = YogaCatalog.signIndex(sb)
            if (kotlin.math.abs(ia - ib) == 6) return "မျက်နှာချင်းဆိုင် (ခုနစ်မျက်နှာ)"
            if (LORD[sa] == b && LORD[sb] == a) return "အပြန်အလှန် ရာသီလဲလှယ် (ပရိဝတ္တန)"
            return null
        }
        fun placementNotes(planets: List<String>): Pair<List<String>, List<String>> {
            val supporting = mutableListOf<String>()
            val limiting = mutableListOf<String>()
            for (p in planets) {
                val h = houseOfP(p)
                if (h in DUSTHANA) limiting.add("${planetMm(p)} သည် ဒုဋ္ဌာန ($h အိမ်) တွင် တည်နေသည်")
                else if (h in KENDRA || h in TRIKONA) supporting.add("${planetMm(p)} သည် ကိန်း/တြိကောဏ ($h အိမ်) တွင် တည်နေသည်")
            }
            return supporting to limiting
        }
        fun pairInstances(
            ruleId: String, pairs: Set<Pair<String, String>>,
            pairLabel: (String, String) -> String
        ) {
            for ((a, b) in pairs.sortedBy { it.first + it.second }) {
                val rel = relation(a, b) ?: continue
                val sa = signOfP(a)!!
                val sb = signOfP(b)!!
                val (sup, lim) = placementNotes(listOf(a, b))
                val fact = "${pairLabel(a, b)} — $rel " +
                    "(${planetMm(a)} ${signMm(sa)}၊ ${planetMm(b)} ${signMm(sb)})"
                out.add(RawDetection(ruleId, YogaStatus.FORMED, listOf(a, b),
                    listOf(fact), sup, lim))
            }
        }
        val rajaPairs = mutableSetOf<Pair<String, String>>()
        for (k in kendraLords) for (t in trikonaLords) {
            if (k != t) rajaPairs.add(if (k < t) k to t else t to k)
        }
        pairInstances("raja_sambandha", rajaPairs) { a, b ->
            val ka = if (a in kendraLords) "ကိန်း" else "တြိကောဏ"
            val kb = if (b in kendraLords) "ကိန်း" else "တြိကောဏ"
            "${planetMm(a)} ($ka အိမ်ရှင်) နှင့် ${planetMm(b)} ($kb အိမ်ရှင်)"
        }
        val dhanaHouses = setOf(2, 5, 9, 11)
        val dhanaLords = lordsOf(dhanaHouses)
        fun dhanaHouseOf(p: String): List<Int> =
            dhanaHouses.filter { LORD[houseSign(it)] == p }
        val dhanaPairs = mutableSetOf<Pair<String, String>>()
        val dhanaList = dhanaLords.sorted()
        for (i in dhanaList.indices) for (j in i + 1 until dhanaList.size) {
            dhanaPairs.add(dhanaList[i] to dhanaList[j])
        }
        pairInstances("dhana", dhanaPairs) { a, b ->
            "${planetMm(a)} (${dhanaHouseOf(a).joinToString("၊ ")} အိမ်ရှင်) နှင့် " +
                "${planetMm(b)} (${dhanaHouseOf(b).joinToString("၊ ")} အိမ်ရှင်)"
        }
        return out
    }

    private fun housesRuledBy(
        planet: String, houses: Set<Int>, houseSign: (Int) -> String
    ): List<Int> = houses.filter { LORD[houseSign(it)] == planet }.sorted()
}
