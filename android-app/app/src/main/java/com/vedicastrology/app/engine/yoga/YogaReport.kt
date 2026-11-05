package com.vedicastrology.app.engine.yoga

import com.vedicastrology.app.engine.StrengthCalculator
import com.vedicastrology.app.engine.yoga.YogaCatalog.DUSTHANA
import com.vedicastrology.app.engine.yoga.YogaCatalog.KENDRA
import com.vedicastrology.app.engine.yoga.YogaCatalog.LORD
import com.vedicastrology.app.engine.yoga.YogaCatalog.TRIKONA
import com.vedicastrology.app.engine.yoga.YogaCatalog.houseFrom
import com.vedicastrology.app.engine.yoga.YogaCatalog.planetMm
import com.vedicastrology.app.engine.yoga.YogaCatalog.ruleById
import com.vedicastrology.app.engine.yoga.YogaCatalog.signMm
import com.vedicastrology.app.engine.yoga.YogaCatalog.signOf

/**
 * Spec G: YogaInterpretationEngine + YogaReportGenerator.
 *
 * Builds the full report from detection + quality + dasha. The language layer
 * explains calculated results only: it never invents positions, formations,
 * citations, or Dasha dates. Every interpretation is conditional traditional
 * language, never a guaranteed outcome.
 *
 * Spec F: no netting of positive/negative Yogas. Conflict notes name shared
 * planets and preserve contradictions; the UI must not present a score.
 */
object YogaReportBuilder {

    fun build(
        chartName: String,
        lons: Map<String, Double>,
        lagna: Double,
        moonLon: Double?,
        birthMillis: Long,
        queryMillis: Long
    ): YogaReport {
        val strength: StrengthCalculator.StrengthResult? = try {
            StrengthCalculator.compute(lons, lagna)
        } catch (e: Exception) {
            null
        }
        val detections = YogaDetectionEngine.detect(lons, lagna) +
            YogaPhase2Engine.detectPhase2(lons, lagna)
        val yogas = detections.map { det ->
            val quality = YogaQualityEvaluator.qualityMap(det.planets, lons, lagna, strength)
            val limiting = (det.limitingFactors +
                quality.values.flatMap { YogaQualityEvaluator.limitingNotes(it) })
                .distinct()
            val supporting = (det.supportingFactors +
                quality.values.flatMap { YogaQualityEvaluator.supportingNotes(it) })
                .distinct()
            val linkResult = if ((det.status == YogaStatus.FORMED || det.status == YogaStatus.QUALIFIED) &&
                moonLon != null && det.planets.isNotEmpty()
            ) {
                YogaDashaLinker.link(det.planets.toSet(), lons, moonLon, birthMillis, queryMillis)
            } else {
                YogaDashaLinker.LinkResult(DashaLink.NONE, emptyList(), emptyList())
            }
            val rule = ruleById(det.ruleId)
            // Effect badge: formation and effect are separate. Order
            // matters: adverse + cancellation -> DIMINISHED, adverse
            // alone -> HARDSHIP, then quality-based WEAK/STRONG/NEUTRAL.
            // STRONG needs QUALITY evidence (exalt/own dignity from the
            // quality map), never a detection note: yogakaraka status
            // alone must not make a yoga strong.
            val qualitySupporting = quality.values
                .flatMap { YogaQualityEvaluator.supportingNotes(it) }.distinct()
            val effect = when {
                det.status != YogaStatus.FORMED && det.status != YogaStatus.QUALIFIED -> YogaEffect.NONE
                det.ruleId in ADVERSE_RULES && det.cancellationFacts.isNotEmpty() -> YogaEffect.DIMINISHED
                det.ruleId in ADVERSE_RULES -> YogaEffect.HARDSHIP
                limiting.isNotEmpty() -> YogaEffect.WEAK
                qualitySupporting.isNotEmpty() -> YogaEffect.STRONG
                else -> YogaEffect.NEUTRAL
            }
            val effectReason = when (effect) {
                YogaEffect.STRONG -> "အားပေးချက် ရှိပြီး ကန့်သတ်ချက် မရှိသောကြောင့် အကျိုးပေးအားကောင်း အဖြစ် သတ်မှတ်သည်။"
                YogaEffect.NEUTRAL -> "ထူးခြားသော အားပေး/ကန့်သတ်ချက် မရှိသောကြောင့် ဖွဲ့စည်းမှုရှိ အဖြစ် သတ်မှတ်သည်။"
                YogaEffect.WEAK -> "ကန့်သတ်ချက် ရှိသောကြောင့် အကျိုးပေးအားနည်း အဖြစ် သတ်မှတ်သည်။"
                YogaEffect.DIMINISHED -> "အခက်အခဲ ယောဂ ဖြစ်သော်လည်း ပျက်ပြယ်ချက် ရှိသောကြောင့် အကျိုးသက်ရောက်မှု လျော့ပါး အဖြစ် သတ်မှတ်သည်။"
                YogaEffect.HARDSHIP -> "အခက်အခဲ ယောဂ ဖွဲ့စည်းပြီး ပျက်ပြယ်ချက် မရှိသောကြောင့် ဖွဲ့သည် (သတိပြုရန်) အဖြစ် သတ်မှတ်သည်။"
                YogaEffect.NONE -> ""
            }
            // Pair-rule card headers name their planets, so two instances
            // of one rule never look like a duplicated card.
            val pairRules = setOf("parivartana", "raja_sambandha", "dhana", "grahana")
            val isPairCard = det.ruleId in pairRules && det.planets.size == 2 && rule != null
            val displayMm = if (isPairCard) {
                "${rule!!.nameMm} (${planetMm(det.planets[0])}–${planetMm(det.planets[1])})"
            } else {
                rule?.nameMm ?: det.ruleId
            }
            val displayEn = if (isPairCard) {
                "${rule!!.nameEn} (${det.planets[0]}–${det.planets[1]})"
            } else {
                rule?.nameEn ?: det.ruleId
            }
            // Parivartana formed instances get an instance-specific reading
            // (planets, ruled houses, class, life areas) built only from
            // engine data; insufficient evidence shows the insufficient
            // sentence instead of the generic theme.
            val mainReading = if (det.ruleId == "parivartana" &&
                (det.status == YogaStatus.FORMED || det.status == YogaStatus.QUALIFIED)
            ) {
                buildParivartanaReading(det, lons, lagna)
                    ?: "တွက်ချက်မှုအချက်အလက် မပြည့်စုံသေးပါ။"
            } else {
                buildMainReading(det, rule, limiting)
            }
            DetectedYoga(
                ruleId = det.ruleId,
                status = det.status,
                effect = effect,
                planets = det.planets,
                formationFacts = det.formationFacts,
                cancellationFacts = det.cancellationFacts,
                supportingFactors = supporting,
                limitingFactors = limiting,
                quality = quality,
                dashaLink = linkResult.link,
                dashaPeriods = linkResult.periods,
                currentPathPlanets = linkResult.currentPathPlanets,
                displayNameMm = displayMm,
                displayNameEn = displayEn,
                dashaLinkDetail = linkResult.linkDetail,
                activePath = linkResult.activePath,
                interpretation = buildInterpretation(det, linkResult, mainReading),
                mainReading = mainReading,
                dashaReading = if (det.status == YogaStatus.FORMED || det.status == YogaStatus.QUALIFIED) {
                    buildDashaReading(linkResult)
                } else {
                    ""
                },
                effectReason = effectReason,
                cautionNote = buildCautionNote(det, limiting),
                dashaNarrative = if (linkResult.link == DashaLink.DIRECT) {
                    buildDashaNarrative(
                        displayMm, det.formationFacts, supporting, limiting,
                        effectReason, linkResult.currentPathPlanets,
                        linkResult.activePath, det.planets, mainReading
                    )
                } else {
                    ""
                }
            )
        }
        return YogaReport(chartName, yogas, buildConflictNotes(yogas))
    }

    /** Adverse-outcome yogas: their themes already describe hardship, so the
     *  generic "weak strength delays plans" conditional does not apply to
     *  them; cancellation softening applies instead. */
    private val ADVERSE_RULES = setOf(
        "chandra_kemadruma", "daridra", "arishta", "shakata", "grahana"
    )

    /**
     * Explanatory voice (since v1.8.5, his choice): "ဤဇာတာတွင် ... ဟု
     * ရိုးရာဗေဒင်အရ ယူဆကြသည်" — one concise sentence per yoga, possibility
     * verbs ("နိုင်သည်"), no "ဇာတာရှင်သည် ... တတ်သည်" template. The prose
     * is the traditional reading only — no formation sentence, no variant
     * note, no disclaimer tail. The card header already shows name +
     * status, the detail section shows formation/variant/source, and the
     * screen footer carries the traditional-reading disclaimer (Error
     * Prevention Rule 11 is kept by the conditional "နိုင်သည်" verbs, not
     * by a tail).
     */
    /**
     * Split into §1 main reading + §4 dasha reading so the card can show
     * the five interpretation sections separately without repeating text.
     */
    private fun buildMainReading(
        det: RawDetection,
        rule: YogaCatalog.YogaRule?,
        limiting: List<String>
    ): String {
        val name = if (rule != null) "${rule.nameMm} (${rule.nameEn})" else det.ruleId
        return when (det.status) {
            YogaStatus.FORMED, YogaStatus.QUALIFIED -> {
                val sb = StringBuilder()
                if (rule != null) sb.append(rule.themesMm).append(" ")
                if (det.cancellationFacts.isNotEmpty() && det.ruleId in ADVERSE_RULES) {
                    sb.append("သို့သော် ပျက်ပြယ်ချက် ရှိသောကြောင့် အနက်ဆိုးသည် ပျော့နိုင်သည်ဟု ယူဆကြသည်။ ")
                }
                sb.toString().trim()
            }
            YogaStatus.NOT_FORMED -> "ဤဇာတာတွင် $name မဖွဲ့စည်းပါ။"
            YogaStatus.INDETERMINATE -> "ဤဇာတာတွင် $name ကို ဒေတာ မပြည့်စုံသဖြင့် မဆုံးဖြတ်နိုင်ပါ။"
        }
    }

    /**
     * §3 caution line: the delay implication, kept OUT of the main reading
     * so each card's primary prediction is distinctive. Shown with the
     * specific limiting factors, not as a repeated generic prediction.
     */
    private fun buildCautionNote(
        det: RawDetection,
        limiting: List<String>
    ): String =
        if ((det.status == YogaStatus.FORMED || det.status == YogaStatus.QUALIFIED) &&
            det.ruleId !in ADVERSE_RULES && limiting.isNotEmpty()
        ) {
            "ဂြိုဟ်အားချို့တဲ့ပါက အကြံအစည်များ နှောင့်နှေးကြန့်ကြာနိုင်သည်။"
        } else {
            ""
        }

    private fun buildDashaReading(link: YogaDashaLinker.LinkResult): String =
        when (link.link) {
            DashaLink.DIRECT -> "သက်ဆိုင်ရာ ဂြိုဟ်ဒသာကာလတွင် အကျိုးပေးခြင်း ပိုမိုထင်ရှားနိုင်သည်။"
            DashaLink.INDIRECT -> "သက်ဆိုင်ရာ ဒသာနှင့် သွယ်ဝိုက် ဆက်စပ်နေသဖြင့် " +
                "အကျိုးကို တစ်ဆင့်ခံ ခံစားရနိုင်သည်။"
            DashaLink.NONE -> "လက်ရှိ ဒသာနှင့် တိုက်ရိုက်မဆက်စပ်သေးသဖြင့် " +
                "အကျိုးပေးချိန်ကို စောင့်ရနိုင်သည်။"
            DashaLink.INSUFFICIENT -> ""
        }

    private val DASHA_LEVEL_MM = mapOf(
        "1" to "မဟာဒသာ", "2" to "အန္တရဒသာ", "3" to "ပြတန္တရဒသာ",
        "4" to "သုက္ခမဒသာ", "5" to "ပရာဏဒသာ"
    )

    /**
     * Detailed narrative for a DIRECT yoga: formation evidence, quality
     * state, the exact dasha connection, then the traditional meaning.
     * Every sentence comes from verified fields — no new claim is
     * introduced here. Dates are rendered by the UI next to this text.
     */
    private fun buildDashaNarrative(
        displayMm: String,
        formationFacts: List<String>,
        supporting: List<String>,
        limiting: List<String>,
        effectReason: String,
        pathPlanets: List<String>,
        activePath: List<YogaDashaPeriod>,
        yogaPlanets: List<String>,
        mainReading: String
    ): String {
        val formation = "$displayMm သည် " +
            formationFacts.joinToString("၊ ") + " ကြောင့် ဖွဲ့စည်းထားသည်။"
        val qualityParts = mutableListOf<String>()
        if (supporting.isNotEmpty()) {
            qualityParts.add("အားပေးချက်: " + supporting.joinToString("၊ "))
        }
        if (limiting.isNotEmpty()) {
            qualityParts.add("ကန့်သတ်ချက်: " + limiting.joinToString("၊ "))
        }
        if (effectReason.isNotEmpty()) qualityParts.add(effectReason)
        val quality = if (qualityParts.isEmpty()) {
            "ထူးခြားသော အားပေး/ကန့်သတ်ချက် မရှိပါ။"
        } else {
            qualityParts.joinToString(" ")
        }
        val connected = activePath.filter { it.planet in yogaPlanets }
        val levels = connected.joinToString("၊ ") {
            "${DASHA_LEVEL_MM[it.level] ?: it.level} ${planetMm(it.planet)}"
        }
        val pathNames = pathPlanets.joinToString(" / ") { planetMm(it) }
        val dasha = "လက်ရှိ ဒသာလမ်းကြောင်း ($pathNames) တွင် " +
            yogaPlanets.joinToString("၊ ") { planetMm(it) } +
            " ပါသောကြောင့် ဤယောဂနှင့် တိုက်ရိုက်ဆက်စပ်နေသည်။ " +
            "ဆက်စပ်သော အဆင့်များ: $levels။"
        return "$formation\n\n$quality\n\n$dasha\n\n$mainReading"
    }

    private fun buildInterpretation(
        det: RawDetection,
        link: YogaDashaLinker.LinkResult,
        main: String
    ): String {
        val dasha = if (det.status == YogaStatus.FORMED || det.status == YogaStatus.QUALIFIED) {
            buildDashaReading(link)
        } else {
            ""
        }
        return listOf(main, dasha).filter { it.isNotEmpty() }.joinToString(" ")
    }

    /** Traditional house life-areas (interpretation layer, not positions). */
    private val HOUSE_AREAS = mapOf(
        1 to "မိမိကိုယ်တိုင်",
        2 to "ငွေကြေးနှင့် မိသားစု",
        3 to "ကြိုးစားအားထုတ်မှု",
        4 to "အိမ်/ခြံမြေနှင့် မိခင်",
        5 to "ပညာရေးနှင့် သားသမီး",
        6 to "ရောဂါနှင့် ကြွေး",
        7 to "အိမ်ထောင်ဖက်နှင့် အပေါင်းအသင်း",
        8 to "အခက်အခဲနှင့် သူတစ်ပါးငွေ",
        9 to "ကံကြမ္မာနှင့် ဖခင်၊ အဆင့်မြင့်ပညာရေး",
        10 to "အလုပ်/ရာထူး",
        11 to "ဝင်ငွေနှင့် မိတ်ဆွေ",
        12 to "အသုံးစရိတ်နှင့် နိုင်ငံခြား"
    )

    private fun mmDigit(n: Int): String =
        n.toString().map { c ->
            if (c in '0'..'9') (0x1040 + (c - '0')).toChar() else c
        }.joinToString("")

    /** House numbers as "၃" / "၇ နှင့် ၁၂" / "၂၊ ၅ နှင့် ၇". */
    private fun housesPhrase(houses: List<Int>): String {
        val ds = houses.sorted().map { mmDigit(it) }
        return when (ds.size) {
            0 -> ""
            1 -> ds[0]
            2 -> ds.joinToString(" နှင့် ")
            else -> ds.dropLast(1).joinToString("၊ ") + " နှင့် " + ds.last()
        }
    }

    /**
     * Instance-specific Parivartana reading, built ONLY from engine data
     * (longitudes, lagna, lordships, the detection's classification rule).
     * Returns null when the evidence is insufficient — the caller then
     * shows the insufficient-data sentence instead of fabricating.
     * The static catalog theme stays as the general description only.
     */
    private fun buildParivartanaReading(
        det: RawDetection,
        lons: Map<String, Double>,
        lagna: Double
    ): String? {
        if (det.planets.size != 2) return null
        val (a, b) = det.planets
        val sa = lons[a]?.let { signOf(it) } ?: return null
        val sb = lons[b]?.let { signOf(it) } ?: return null
        if (LORD[sa] != b || LORD[sb] != a) return null
        val lagnaSign = signOf(lagna)
        fun ruledBy(planet: String): List<Int> =
            LORD.entries.filter { it.value == planet }
                .map { houseFrom(lagnaSign, it.key) }.sorted()
        val aRules = ruledBy(a)
        val bRules = ruledBy(b)
        val aKT = aRules.any { it in KENDRA || it in TRIKONA }
        val bKT = bRules.any { it in KENDRA || it in TRIKONA }
        val aD = aRules.any { it in DUSTHANA }
        val bD = bRules.any { it in DUSTHANA }
        val type = when {
            aKT && bKT -> "မဟာ"
            aD && bD -> "ဒိုင်းည"
            else -> "ခလ"
        }
        val areas = (aRules + bRules).toSet().sorted()
            .mapNotNull { HOUSE_AREAS[it] }.distinct()
        if (areas.isEmpty()) return null
        val aAreas = aRules.mapNotNull { HOUSE_AREAS[it] }.distinct().joinToString("၊ ")
        val bAreas = bRules.mapNotNull { HOUSE_AREAS[it] }.distinct().joinToString("၊ ")
        // Connection sentence: how the two sides' affairs interpenetrate.
        val connectionSentence = "$aAreas နှင့် $bAreas တို့သည် ရာသီလဲလှယ်ခြင်းကြောင့် " +
            "အပြန်အလှန် ဆက်စပ်နေသောကြောင့် တစ်ဖက်က အခြေအနေသည် အခြားတစ်ဖက်ကို " +
            "သက်ရောက်နိုင်သည်ဟု ယူဆကြသည်။"
        val effectSentence = when (type) {
            "မဟာ" -> "ကိန်း/တြိကောဏ အိမ်ရှင်များ လဲလှယ်ခြင်းဖြစ်သောကြောင့် ထိုအကြောင်းအရာများတွင် ကောင်းကျိုး အားသာနိုင်သည်ဟု ယူဆကြသည်။"
            "ဒိုင်းည" -> "ဒုဋ္ဌာန အိမ်ရှင်များ လဲလှယ်ခြင်းဖြစ်သောကြောင့် ထိုအကြောင်းအရာများတွင် အခက်အခဲ ဖြစ်နိုင်သည်ဟု ယူဆကြသည်။"
            else -> "ကိန်း/တြိကောဏနှင့် ဒုဋ္ဌာန ရောနှောသော လဲလှယ်ခြင်းဖြစ်သောကြောင့် ထိုအကြောင်းအရာများတွင် ကောင်းကျိုးနှင့် အခက်အခဲ နှစ်မျိုးလုံး ဖြစ်နိုင်သည်ဟု ယူဆကြသည်။"
        }
        val aHouses = housesPhrase(aRules)
        val bHouses = housesPhrase(bRules)
        return "ဤဇာတာတွင် ${planetMm(a)} နှင့် ${planetMm(b)} ရာသီအပြန်အလှန် လဲလှယ်ထားသော " +
            "${type}ပရိဝတ္တနယောဂ ဖွဲ့စည်းထားသည်ဟု ရိုးရာဗေဒင်အရ ယူဆကြသည်။ " +
            "လဂ် ${signMm(lagnaSign)}မှ ရေတွက်လျှင် " +
            "${planetMm(a)}သည် $aHouses အိမ်${if (aRules.size > 1) "တို့" else ""}၏ ရှင်၊ " +
            "${planetMm(b)}သည် $bHouses အိမ်${if (bRules.size > 1) "တို့" else ""}၏ ရှင် ဖြစ်သည်။ " +
            connectionSentence + " " + effectSentence
    }

    /** Spec F: shared-planet notes; contradictions are preserved, never netted. */
    private fun buildConflictNotes(yogas: List<DetectedYoga>): List<String> {
        val notes = mutableListOf<String>()
        val active = yogas.filter {
            it.status == YogaStatus.FORMED || it.status == YogaStatus.QUALIFIED
        }
        val byPlanet = mutableMapOf<String, MutableList<String>>()
        for (y in active) {
            val ruleName = ruleById(y.ruleId)?.nameMm ?: y.ruleId
            for (p in y.planets) byPlanet.getOrPut(p) { mutableListOf() }.add(ruleName)
        }
        for ((planet, names) in byPlanet) {
            if (names.size >= 2) {
                notes.add(
                    "${planetMm(planet)} သည် ယောဂ ${names.size} ခုတွင် ပါဝင်သည် " +
                        "(${names.distinct().joinToString("၊ ")})။ " +
                        "ယောဂတစ်ခုချင်းစီ၏ အနက်ကို သီးခြားဖတ်ပါ — " +
                        "အရေအတွက်ကို ပေါင်းထည့်ပြီး အဖြေထုတ်ခြင်း မပြုပါနှင့်။"
                )
            }
        }
        val afflicted = active.filter { it.limitingFactors.isNotEmpty() }
        if (afflicted.isNotEmpty() && active.size > afflicted.size) {
            notes.add(
                "ကန့်သတ်ချက်ရှိသော ယောဂများနှင့် သန့်ရှင်းသော ယောဂများ တွဲနေသည် — " +
                    "အနက်ချင်း ဆန့်ကျင်နိုင်သည်။ ယောဂတစ်ခုချင်းစီ၏ ကန့်သတ်ချက်များကို " +
                    "သီးခြားကြည့်ပြီး ဆုံးဖြတ်ပါ။"
            )
        }
        return notes
    }

    /** Status label for UI (Burmese). */
    fun statusMm(status: YogaStatus): String = when (status) {
        YogaStatus.FORMED -> "ဖွဲ့စည်း"
        YogaStatus.QUALIFIED -> "အရည်အချင်းပြည့်"
        YogaStatus.NOT_FORMED -> "မဖွဲ့စည်း"
        YogaStatus.INDETERMINATE -> "မဆုံးဖြတ်နိုင်"
    }

    fun dashaLinkMm(link: DashaLink): String = when (link) {
        DashaLink.DIRECT -> "တိုက်ရိုက် ဆက်စပ်"
        DashaLink.INDIRECT -> "သွယ်ဝိုက် ဆက်စပ်"
        DashaLink.NONE -> "ဆက်စပ်မှု မရှိ"
        DashaLink.INSUFFICIENT -> "တွက်ချက်၍ မရ"
    }
}
