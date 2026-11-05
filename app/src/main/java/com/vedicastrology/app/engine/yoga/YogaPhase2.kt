package com.vedicastrology.app.engine.yoga

import com.vedicastrology.app.engine.yoga.YogaCatalog.DUSTHANA
import com.vedicastrology.app.engine.yoga.YogaCatalog.EXALT
import com.vedicastrology.app.engine.yoga.YogaCatalog.KENDRA
import com.vedicastrology.app.engine.yoga.YogaCatalog.LORD
import com.vedicastrology.app.engine.yoga.YogaCatalog.TRIKONA
import com.vedicastrology.app.engine.yoga.YogaCatalog.dignityOf
import com.vedicastrology.app.engine.yoga.YogaCatalog.houseFrom
import com.vedicastrology.app.engine.yoga.YogaCatalog.planetMm
import com.vedicastrology.app.engine.yoga.YogaCatalog.signIndex
import com.vedicastrology.app.engine.yoga.YogaCatalog.signMm
import com.vedicastrology.app.engine.yoga.YogaCatalog.signOf
import kotlin.math.abs

/**
 * Spec G (Phase 2, v1.8.0): Chandra, Viparita Raja, Neecha Bhanga, Daridra,
 * Arishta, Chandra/Guru Mangala, Lakshmi, Saraswati, Adhi, Amala,
 * Parivartana, Vasumati, Shakata, Grahana, Mahabhagya.
 *
 * Same honesty rules as Phase 1: whole-sign houses, Strength % never
 * consulted, disputed/variant rules flagged in the catalog, no scores.
 * Fixed rules always emit one entry (formed or not); pair rules
 * (Parivartana, Grahana) emit formed instances only, plus a single
 * NOT_FORMED entry when nothing forms.
 */
object YogaPhase2Engine {

    private val SEVEN = listOf("Sun", "Moon", "Mars", "Mercury", "Jupiter", "Venus", "Saturn")
    private val BENEFICS = listOf("Jupiter", "Venus", "Mercury")
    private val NATURAL_MALEFICS = listOf("Mars", "Saturn", "Rahu", "Ketu")
    private val UPACHAYA = setOf(3, 6, 10, 11)

    fun detectPhase2(lons: Map<String, Double>, lagna: Double): List<RawDetection> {
        val lagnaSign = signOf(lagna)
        val lagnaIdx = signIndex(lagnaSign)
        val signOfP: (String) -> String? = { p -> lons[p]?.let { signOf(it) } }
        val houseOfP: (String) -> Int = { p ->
            val s = signOfP(p)
            if (s == null) -1 else houseFrom(lagnaSign, s)
        }
        fun houseSign(house: Int): String = YogaCatalog.SIGNS[(lagnaIdx + house - 1) % 12]
        fun lordOfHouse(house: Int): String? = LORD[houseSign(house)]
        fun housesRuledBy(planet: String): List<Int> =
            (1..12).filter { LORD[houseSign(it)] == planet }
        fun housesMm(h: Collection<Int>): String =
            h.sorted().joinToString("၊ ") { n ->
                n.toString().map { c ->
                    if (c in '0'..'9') (0x1040 + (c - '0')).toChar() else c
                }.joinToString("")
            }
        val out = mutableListOf<RawDetection>()
        fun formed(
            id: String, planets: List<String>, facts: List<String>,
            sup: List<String> = emptyList(), lim: List<String> = emptyList(),
            cancel: List<String> = emptyList()
        ) = out.add(RawDetection(id, YogaStatus.FORMED, planets, facts, sup, lim, cancel))
        fun notFormed(id: String, planets: List<String>, facts: List<String>) =
            out.add(RawDetection(id, YogaStatus.NOT_FORMED, planets, facts, emptyList(),
                listOf("ဖွဲ့စည်းမှု စည်းကမ်း မပြည့်မီပါ")))
        fun indet(id: String, planets: List<String>, facts: List<String>) =
            out.add(RawDetection(id, YogaStatus.INDETERMINATE, planets, facts, emptyList(), emptyList()))

        // ---- Chandra set (from Moon; Sun/Rahu/Ketu never counted) ----
        val moonSign = signOfP("Moon")
        if (moonSign == null) {
            for (id in listOf("chandra_sunapha", "chandra_anapha", "chandra_durudhara", "chandra_kemadruma")) {
                indet(id, listOf("Moon"), listOf("လ၏ တည်နေရာ မရှိပါ"))
            }
        } else {
            fun planetsInHouseFromMoon(house: Int): List<String> {
                val target = YogaCatalog.SIGNS[(signIndex(moonSign) + house - 1) % 12]
                return SEVEN.filter { it != "Sun" && signOfP(it) == target }
            }
            val second = planetsInHouseFromMoon(2)
            val twelfth = planetsInHouseFromMoon(12)
            val secondMm = second.joinToString("၊ ") { planetMm(it) }
            val twelfthMm = twelfth.joinToString("၊ ") { planetMm(it) }
            if (second.isNotEmpty()) {
                formed("chandra_sunapha", second,
                    listOf("လမှ ၂ အိမ်တွင် $secondMm တည်နေသည်"))
            } else {
                notFormed("chandra_sunapha", listOf("Moon"),
                    listOf("လမှ ၂ အိမ် လွတ်နေသည်"))
            }
            if (twelfth.isNotEmpty()) {
                formed("chandra_anapha", twelfth,
                    listOf("လမှ ၁၂ အိမ်တွင် $twelfthMm တည်နေသည်"))
            } else {
                notFormed("chandra_anapha", listOf("Moon"),
                    listOf("လမှ ၁၂ အိမ် လွတ်နေသည်"))
            }
            if (second.isNotEmpty() && twelfth.isNotEmpty()) {
                formed("chandra_durudhara", (second + twelfth).distinct(),
                    listOf("လမှ ၂ အိမ်တွင် $secondMm၊ ၁၂ အိမ်တွင် $twelfthMm တည်နေသည်"))
            } else {
                notFormed("chandra_durudhara", listOf("Moon"),
                    listOf("လမှ ၂ အိမ်နှင့် ၁၂ အိမ် နှစ်ဖက်လုံး မပြည့်ပါ"))
            }
            if (second.isEmpty() && twelfth.isEmpty()) {
                // Cancellation (softening) checks, kept SEPARATE from strength
                // (Error Prevention Rule 3 + 5): Moon in kendra from Lagna,
                // or a benefic conjunct / aspecting the Moon. These qualify
                // the negative reading and must be read BEFORE it.
                val cancelNotes = mutableListOf<String>()
                if (houseOfP("Moon") in KENDRA) {
                    cancelNotes.add("လသည် လဂ်မှ ကိန်းတွင် တည်၍ ကေမဒြုမ အကျိုး ပျော့သည်ဟု ဆိုသည်")
                }
                val conjunctBenefics = BENEFICS.filter { signOfP(it) == moonSign }
                if (conjunctBenefics.isNotEmpty()) {
                    cancelNotes.add(
                        conjunctBenefics.joinToString("၊ ") { planetMm(it) } +
                            " လဖြင့် ယှဉ်နေ၍ ကေမဒြုမ အကျိုး ပျော့သည်ဟု ဆိုသည်"
                    )
                }
                val aspectingBenefics = BENEFICS.filter { b ->
                    val bs = signOfP(b) ?: return@filter false
                    val h = houseFrom(bs, moonSign)
                    h == 7 || (b == "Jupiter" && (h == 5 || h == 9))
                }
                if (aspectingBenefics.isNotEmpty() && conjunctBenefics.isEmpty()) {
                    cancelNotes.add(
                        aspectingBenefics.joinToString("၊ ") { planetMm(it) } +
                            " လကို အမြင်ပေး၍ ကေမဒြုမ အကျိုး ပျော့သည်ဟု ဆိုသည်"
                    )
                }
                formed("chandra_kemadruma", listOf("Moon"),
                    listOf("လမှ ၂ အိမ်နှင့် ၁၂ အိမ် နှစ်ဖက်လုံး လွတ်နေသည်"),
                    cancel = cancelNotes)
            } else {
                notFormed("chandra_kemadruma", listOf("Moon"),
                    listOf("လမှ ၂ အိမ်နှင့်/သို့မဟုတ် ၁၂ အိမ်တွင် ဂြိုဟ် ရှိနေသည်"))
            }
        }

        // ---- Viparita Raja (fixed three) ----
        for ((id, house) in listOf(
            "viparita_harsha" to 6, "viparita_sarala" to 8, "viparita_vimala" to 12
        )) {
            val lord = lordOfHouse(house)
            val lordSign = lord?.let { signOfP(it) }
            if (lord == null || lordSign == null) {
                indet(id, emptyList(), listOf("$house အိမ်ရှင်၏ တည်နေရာ မရှိပါ"))
                continue
            }
            val lordHouse = houseFrom(lagnaSign, lordSign)
            if (lordHouse in DUSTHANA) {
                formed(id, listOf(lord),
                    listOf("$house အိမ်ရှင် ${planetMm(lord)} — ${signMm(lordSign)}၊ " +
                        "လဂ်မှ $lordHouse အိမ် (ဒုဋ္ဌာန) တွင် တည်နေသည်"))
            } else {
                notFormed(id, listOf(lord),
                    listOf("$house အိမ်ရှင် ${planetMm(lord)} — ${signMm(lordSign)}၊ " +
                        "လဂ်မှ $lordHouse အိမ် (ဒုဋ္ဌာန မဟုတ်) တွင် တည်နေသည်"))
            }
        }

        // ---- Neecha Bhanga (per debilitated planet) ----
        var debilFound = false
        for (p in SEVEN) {
            val sign = signOfP(p) ?: continue
            if (dignityOf(p, sign) != Dignity.DEBILITATED) continue
            debilFound = true
            val dispositor = LORD[sign]
            val exaltLord = EXALT[p]?.let { LORD[it] }
            val cancelNotes = mutableListOf<String>()
            val cancelPlanets = mutableListOf<String>()
            fun kendraCheck(q: String?, label: String) {
                if (q == null) return
                val qs = signOfP(q) ?: return
                val fromLagna = houseFrom(lagnaSign, qs)
                val fromMoon = if (moonSign != null) houseFrom(moonSign, qs) else -1
                if (fromLagna in KENDRA || fromMoon in KENDRA) {
                    cancelNotes.add(
                        "$label ${planetMm(q)} — ${signMm(qs)}၊ " +
                            (if (fromLagna in KENDRA) "လဂ်မှ ကိန်း" else "လမှ ကိန်း") +
                            " တွင် တည်နေသည်"
                    )
                    cancelPlanets.add(q)
                }
            }
            kendraCheck(dispositor, "နိစ်ရာသီရှင်")
            kendraCheck(exaltLord, "ဥုစ်ရာသီရှင်")
            if (cancelNotes.isNotEmpty()) {
                formed("neecha_bhanga", (listOf(p) + cancelPlanets).distinct(),
                    listOf("${planetMm(p)} ${signMm(sign)} တွင် နိစ် ဖြစ်ပြီး ပျက်ပြယ်ချက် ရှိသည်"),
                    cancel = cancelNotes)
            } else {
                notFormed("neecha_bhanga", listOf(p),
                    listOf("${planetMm(p)} ${signMm(sign)} တွင် နိစ် ဖြစ်သော်လည်း ပျက်ပြယ်ချက် မရှိပါ"))
            }
        }
        if (!debilFound) {
            notFormed("neecha_bhanga", emptyList(), listOf("နိစ် ဂြိုဟ် မရှိပါ"))
        }

        // ---- Daridra ----
        val eleventhLord = lordOfHouse(11)
        val lagnaLord = LORD[lagnaSign]
        val eleventhSign = eleventhLord?.let { signOfP(it) }
        val lagnaLordSign = lagnaLord?.let { signOfP(it) }
        if (eleventhLord == null || eleventhSign == null || lagnaLord == null || lagnaLordSign == null) {
            indet("daridra", emptyList(), listOf("၁၁ အိမ်ရှင် သို့မဟုတ် လဂ်ရှင်၏ တည်နေရာ မရှိပါ"))
        } else {
            val eleventhHouse = houseFrom(lagnaSign, eleventhSign)
            val lagnaLordHouse = houseFrom(lagnaSign, lagnaLordSign)
            val lagnaLordDebil = dignityOf(lagnaLord, lagnaLordSign) == Dignity.DEBILITATED
            val lagnaLordCombust = lons[lagnaLord]?.let { lon ->
                lons["Sun"]?.let { sun -> YogaQualityEvaluator.isCombust(lagnaLord, lon, sun) }
            } == true
            val lagnaLordWeak = lagnaLordDebil || lagnaLordCombust || lagnaLordHouse in DUSTHANA
            val fact = "၁၁ အိမ်ရှင် ${planetMm(eleventhLord)} — ${signMm(eleventhSign)} " +
                "(လဂ်မှ $eleventhHouse အိမ်)၊ လဂ်ရှင် ${planetMm(lagnaLord)} — " +
                "${signMm(lagnaLordSign)} (လဂ်မှ $lagnaLordHouse အိမ်" +
                (if (lagnaLordDebil) "၊ နိစ်" else "") +
                (if (lagnaLordCombust) "၊ လောင်ကျွမ်း" else "") + ")"
            if (eleventhHouse in DUSTHANA && lagnaLordWeak) {
                formed("daridra", listOf(eleventhLord, lagnaLord), listOf(fact))
            } else {
                notFormed("daridra", listOf(eleventhLord, lagnaLord), listOf(fact))
            }
        }

        // ---- Arishta (broad Moon-affliction form) ----
        val eighthSign = houseSign(8)
        val maleficsInEighth = NATURAL_MALEFICS.filter { signOfP(it) == eighthSign }
        val moonDebil = moonSign?.let { dignityOf("Moon", it) == Dignity.DEBILITATED } == true
        val moonConjMalefic = moonSign?.let { ms ->
            NATURAL_MALEFICS.any { signOfP(it) == ms }
        } == true
        val moonDusthana = moonSign?.let { houseFrom(lagnaSign, it) in DUSTHANA } == true
        if (moonSign == null) {
            indet("arishta", emptyList(), listOf("လ၏ တည်နေရာ မရှိပါ"))
        } else if (maleficsInEighth.isNotEmpty() && (moonDebil || moonConjMalefic || moonDusthana)) {
            val reasons = mutableListOf<String>()
            if (moonDebil) reasons.add("လ နိစ်")
            if (moonConjMalefic) reasons.add("လ ဆိုးသောဂြိုဟ်နှင့် ယှဉ်")
            if (moonDusthana) reasons.add("လ ဒုဋ္ဌာနတွင်")
            formed("arishta", (maleficsInEighth + "Moon").distinct(),
                listOf("၈ အိမ်တွင် " + maleficsInEighth.joinToString("၊ ") { planetMm(it) } +
                    " တည်ပြီး၊ " + reasons.joinToString("၊ ") + " ဖြစ်နေသည်"))
        } else {
            notFormed("arishta", listOf("Moon"),
                listOf("၈ အိမ်တွင် ဆိုးသောဂြိုဟ် မရှိ၊ သို့မဟုတ် လ ထိခိုက်မှု စည်းကမ်း မပြည့်ပါ"))
        }

        // ---- Chandra Mangala / Guru Mangala ----
        val moonS = signOfP("Moon")
        val marsS = signOfP("Mars")
        val jupS = signOfP("Jupiter")
        if (moonS == null || marsS == null) {
            indet("chandra_mangala", listOf("Moon", "Mars"), listOf("လ သို့မဟုတ် အင်္ဂါ၏ တည်နေရာ မရှိပါ"))
        } else if (moonS == marsS) {
            formed("chandra_mangala", listOf("Moon", "Mars"),
                listOf("လနှင့် အင်္ဂါ ${signMm(moonS)} တွင် ယှဉ်နေသည်"))
        } else if (abs(signIndex(moonS) - signIndex(marsS)) == 6) {
            formed("chandra_mangala", listOf("Moon", "Mars"),
                listOf("လ ${signMm(moonS)} နှင့် အင်္ဂါ ${signMm(marsS)} မျက်နှာချင်းဆိုင် ဖြစ်နေသည်"))
        } else {
            notFormed("chandra_mangala", listOf("Moon", "Mars"),
                listOf("လ ${signMm(moonS)}၊ အင်္ဂါ ${signMm(marsS)} — ယှဉ်/မျက်နှာချင်းဆိုင် မဟုတ်ပါ"))
        }
        if (jupS == null || marsS == null) {
            indet("guru_mangala", listOf("Jupiter", "Mars"), listOf("ဂုရု သို့မဟုတ် အင်္ဂါ၏ တည်နေရာ မရှိပါ"))
        } else if (jupS == marsS) {
            formed("guru_mangala", listOf("Jupiter", "Mars"),
                listOf("ဂုရုနှင့် အင်္ဂါ ${signMm(jupS)} တွင် ယှဉ်နေသည်"))
        } else {
            notFormed("guru_mangala", listOf("Jupiter", "Mars"),
                listOf("ဂုရု ${signMm(jupS)}၊ အင်္ဂါ ${signMm(marsS)} — တစ်ရာသီတည်း မဟုတ်ပါ"))
        }

        // ---- Lakshmi ----
        val ninthLord = lordOfHouse(9)
        val ninthSign = ninthLord?.let { signOfP(it) }
        if (lagnaLord == null || lagnaLordSign == null || ninthLord == null || ninthSign == null) {
            indet("lakshmi", emptyList(), listOf("လဂ်ရှင် သို့မဟုတ် ၉ အိမ်ရှင်၏ တည်နေရာ မရှိပါ"))
        } else {
            val lagnaLordStrong = dignityOf(lagnaLord, lagnaLordSign) in
                listOf(Dignity.EXALTED, Dignity.OWN, Dignity.MOOLA)
            val ninthHouse = houseFrom(lagnaSign, ninthSign)
            val fact = "လဂ်ရှင် ${planetMm(lagnaLord)} ${signMm(lagnaLordSign)} " +
                "(${YogaDetectionEngine.dignityMm(dignityOf(lagnaLord, lagnaLordSign))})၊ " +
                "၉ အိမ်ရှင် ${planetMm(ninthLord)} ${signMm(ninthSign)} (လဂ်မှ $ninthHouse အိမ်)"
            if (lagnaLordStrong && (ninthHouse in KENDRA || ninthHouse in TRIKONA)) {
                formed("lakshmi", listOf(lagnaLord, ninthLord), listOf(fact))
            } else {
                notFormed("lakshmi", listOf(lagnaLord, ninthLord), listOf(fact))
            }
        }

        // ---- Saraswati ----
        val jupDig = jupS?.let { dignityOf("Jupiter", it) }
        val jupSignLord = jupS?.let { LORD[it] }
        val jupStrong = jupDig in listOf(Dignity.EXALTED, Dignity.OWN, Dignity.MOOLA) ||
            (jupSignLord in listOf("Sun", "Moon", "Mars"))
        val saraHouses = BENEFICS.map { houseOfP(it) }
        if (BENEFICS.any { signOfP(it) == null }) {
            indet("saraswati", BENEFICS, listOf("ဂုရု/သောကြာ/ဗုဒ္ဓဟူး တည်နေရာ မပြည့်စုံပါ"))
        } else if (saraHouses.all { it in KENDRA || it in TRIKONA || it == 2 } && jupStrong) {
            formed("saraswati", BENEFICS,
                listOf("ဂုရု၊ သောကြာ၊ ဗုဒ္ဓဟူး ကိန်း/တြိကောဏ/၂ အိမ်တွင် တည်ပြီး " +
                    "ဂုရု အားကောင်းနေသည် (${jupS?.let { signMm(it) }})"))
        } else {
            notFormed("saraswati", BENEFICS,
                listOf("ဂုရု၊ သောကြာ၊ ဗုဒ္ဓဟူး ကိန်း/တြိကောဏ/၂ အိမ်တွင် အကုန် မတည်ပါ၊ " +
                    "သို့မဟုတ် ဂုရု အားမကောင်းပါ"))
        }

        // ---- Adhi ----
        if (moonSign == null || BENEFICS.any { signOfP(it) == null }) {
            indet("adhi", BENEFICS, listOf("လ သို့မဟုတ် ဂုရု/သောကြာ/ဗုဒ္ဓဟူး တည်နေရာ မရှိပါ"))
        } else {
            val adhiHouses = BENEFICS.map { houseFrom(moonSign, signOfP(it)!!) }
            if (adhiHouses.all { it in setOf(6, 7, 8) }) {
                formed("adhi", BENEFICS,
                    listOf("ဂုရု၊ သောကြာ၊ ဗုဒ္ဓဟူး လမှ ၆/၇/၈ အိမ်တွင် တည်နေသည်"))
            } else {
                notFormed("adhi", BENEFICS,
                    listOf("ဂုရု၊ သောကြာ၊ ဗုဒ္ဓဟူး လမှ ၆/၇/၈ အိမ်တွင် အကုန် မတည်ပါ"))
            }
        }

        // ---- Amala ----
        val amalaPlanets = BENEFICS.filter { p ->
            houseOfP(p) == 10 || (moonSign != null && houseFrom(moonSign, signOfP(p)!!) == 10)
        }
        if (BENEFICS.any { signOfP(it) == null } || moonSign == null) {
            indet("amala", BENEFICS, listOf("လ သို့မဟုတ် ကောင်းသောဂြိုဟ် တည်နေရာ မရှိပါ"))
        } else if (amalaPlanets.isNotEmpty()) {
            formed("amala", amalaPlanets,
                listOf(amalaPlanets.joinToString("၊ ") { planetMm(it) } +
                    " လဂ်မှ သို့မဟုတ် လမှ ၁၀ အိမ်တွင် တည်နေသည်"))
        } else {
            notFormed("amala", BENEFICS,
                listOf("လဂ်မှ သို့မဟုတ် လမှ ၁၀ အိမ်တွင် ကောင်းသောဂြိုဟ် မရှိပါ"))
        }

        // ---- Parivartana (formed instances only, classified) ----
        fun isExchange(a: String, b: String): Boolean {
            val sa = signOfP(a) ?: return false
            val sb = signOfP(b) ?: return false
            return LORD[sa] == b && LORD[sb] == a
        }
        val sortedSeven = SEVEN.sorted()
        for (i in sortedSeven.indices) {
            for (j in i + 1 until sortedSeven.size) {
                val a = sortedSeven[i]
                val b = sortedSeven[j]
                if (!isExchange(a, b)) continue
                // Classical classification by lordship (Saravali summary):
                // Maha = both rule at least one kendra/trikona house;
                // Dainya = both rule at least one dusthana house;
                // Khala = otherwise. (A strict "all ruled houses" rule would
                // make Dainya impossible: every dusthana lord also rules a
                // non-dusthana house.)
                val aRules = housesRuledBy(a).toSet()
                val bRules = housesRuledBy(b).toSet()
                val aKT = aRules.any { it in KENDRA || it in TRIKONA }
                val bKT = bRules.any { it in KENDRA || it in TRIKONA }
                val aD = aRules.any { it in DUSTHANA }
                val bD = bRules.any { it in DUSTHANA }
                val type = when {
                    aKT && bKT -> "မဟာ"
                    aD && bD -> "ဒိုင်းည"
                    else -> "ခလ"
                }
                formed("parivartana", listOf(a, b),
                    listOf("${planetMm(a)} ${signMm(signOfP(a)!!)} (${housesMm(aRules)} အိမ်ရှင်) နှင့် " +
                        "${planetMm(b)} ${signMm(signOfP(b)!!)} (${housesMm(bRules)} အိမ်ရှင်) " +
                        "အပြန်အလှန် ရာသီလဲလှယ် — အမျိုးအစား: $type"))
            }
        }

        // ---- Vasumati ----
        if (BENEFICS.any { signOfP(it) == null }) {
            indet("vasumati", BENEFICS, listOf("ဂုရု/သောကြာ/ဗုဒ္ဓဟူး တည်နေရာ မပြည့်စုံပါ"))
        } else if (BENEFICS.all { houseOfP(it) in UPACHAYA }) {
            formed("vasumati", BENEFICS,
                listOf("ဂုရု၊ သောကြာ၊ ဗုဒ္ဓဟူး လဂ်မှ ဥပစယ (၃/၆/၁၀/၁၁) အိမ်များတွင် တည်နေသည်"))
        } else {
            notFormed("vasumati", BENEFICS,
                listOf("ဂုရု၊ သောကြာ၊ ဗုဒ္ဓဟူး ဥပစယအိမ်များတွင် အကုန် မတည်ပါ"))
        }

        // ---- Shakata ----
        if (moonSign == null || jupS == null) {
            indet("shakata", listOf("Jupiter"), listOf("လ သို့မဟုတ် ဂုရု၏ တည်နေရာ မရှိပါ"))
        } else {
            val jupFromMoon = houseFrom(moonSign, jupS)
            if (jupFromMoon in DUSTHANA) {
                val jupDig = dignityOf("Jupiter", jupS)
                val cancel = if (jupDig in listOf(Dignity.EXALTED, Dignity.OWN, Dignity.MOOLA)) {
                    listOf("ဂုရု ${signMm(jupS)} တွင် ${YogaDetectionEngine.dignityMm(jupDig)} " +
                        "ဖြစ်သဖြင့် ၑခဋ အကျိုး ပျော့သည်ဟု ဆိုသည်")
                } else emptyList()
                formed("shakata", listOf("Jupiter"),
                    listOf("ဂုရု ${signMm(jupS)} — လမှ $jupFromMoon အိမ် (ဒုဋ္ဌာန) တွင် တည်နေသည်"),
                    cancel = cancel)
            } else {
                notFormed("shakata", listOf("Jupiter"),
                    listOf("ဂုရု ${signMm(jupS)} — လမှ $jupFromMoon အိမ် (ဒုဋ္ဌာန မဟုတ်) တွင် တည်နေသည်"))
            }
        }

        // ---- Grahana (formed instances; single NOT_FORMED when none) ----
        var grahanaFormed = false
        for ((body, node) in listOf("Sun" to "Rahu", "Sun" to "Ketu", "Moon" to "Rahu", "Moon" to "Ketu")) {
            val bs = signOfP(body) ?: continue
            val ns = signOfP(node) ?: continue
            if (bs == ns) {
                grahanaFormed = true
                formed("grahana", listOf(body, node),
                    listOf("${planetMm(body)}နှင့် ${planetMm(node)} ${signMm(bs)} တွင် ယှဉ်နေသည်"))
            }
        }
        if (!grahanaFormed) {
            notFormed("grahana", emptyList(),
                listOf("နေ/လ ရာဟု/ကေတုနှင့် ယှဉ်ခြင်း မရှိပါ"))
        }

        // ---- Mahabhagya (gender unknown -> matching branch = INDETERMINATE) ----
        val sunLon = lons["Sun"]
        val moonLon = lons["Moon"]
        if (sunLon == null || moonLon == null) {
            indet("mahabhagya", emptyList(), listOf("နေ သို့မဟုတ် လ၏ တည်နေရာ မရှိပါ"))
        } else {
            val day = run {
                val diff = ((lagna - sunLon) % 360.0 + 360.0) % 360.0
                diff > 0.0 && diff < 180.0
            }
            fun isOddSign(lon: Double): Boolean = (signIndex(signOf(lon)) % 2 == 0)
            val dayBranch = day && isOddSign(sunLon) && isOddSign(moonLon) && isOddSign(lagna)
            val nightBranch = !day && !isOddSign(sunLon) && !isOddSign(moonLon) && !isOddSign(lagna)
            val timeMm = if (day) "နေ့ဖွား" else "ညဖွား"
            when {
                dayBranch -> out.add(RawDetection("mahabhagya", YogaStatus.INDETERMINATE,
                    listOf("Sun", "Moon"),
                    listOf("$timeMm — နေ၊ လ၊ လဂ် သုံးခု စုံရာသီတွင် တည်နေသည် " +
                        "(အမျိုးသား ဖြစ်ပါက ဖွဲ့စည်းသည်)။ ကျား/မ အချက်အလက် လိုအပ်သည်"),
                    emptyList(), emptyList()))
                nightBranch -> out.add(RawDetection("mahabhagya", YogaStatus.INDETERMINATE,
                    listOf("Sun", "Moon"),
                    listOf("$timeMm — နေ၊ လ၊ လဂ် သုံးခု မရာသီတွင် တည်နေသည် " +
                        "(အမျိုးသမီး ဖြစ်ပါက ဖွဲ့စည်းသည်)။ ကျား/မ အချက်အလက် လိုအပ်သည်"),
                    emptyList(), emptyList()))
                else -> notFormed("mahabhagya", listOf("Sun", "Moon"),
                    listOf("$timeMm — နေ၊ လ၊ လဂ် သုံးခု စုံ/မ ရာသီ တစ်မျိုးတည်း မဟုတ်ပါ"))
            }
        }
        return out
    }
}
