package com.vedicastrology.app.engine.yoga

/**
 * Rule catalog (spec G: YogaRuleCatalog) + shared classical tables.
 *
 * SOURCE HONESTY: every rule carries [YogaRule.sourceNote]. No BPHS
 * chapter/verse number is asserted here, because none was verified against a
 * named edition in this environment. When the user supplies his edition's
 * references, attach them to the matching rule and only then print numbers.
 * Definitions below are the standard traditional formulations as commonly
 * documented; variants are flagged in the rule's variantNote.
 */
object YogaCatalog {

    val SIGNS = listOf(
        "Aries", "Taurus", "Gemini", "Cancer", "Leo", "Virgo",
        "Libra", "Scorpio", "Sagittarius", "Capricorn", "Aquarius", "Pisces"
    )
    val SIGNS_MM = listOf(
        "မိဿ", "ပြိဿ", "မေထုန်", "ကရကဋ်", "သိဟ်", "ကန်",
        "တူ", "ဗြိစ္ဆာ", "ဓနု", "မကာရ", "ကုံ", "မိန်"
    )
    val PLANETS_MM = mapOf(
        "Sun" to "နေ", "Moon" to "လ", "Mars" to "အင်္ဂါ",
        "Mercury" to "ဗုဒ္ဓဟူး", "Jupiter" to "ဂုရု", "Venus" to "သောကြာ",
        "Saturn" to "စနေ", "Rahu" to "ရာဟု", "Ketu" to "ကေတု"
    )

    /** Classical sign rulers (7 planets; nodes do not rule signs here). */
    val LORD = mapOf(
        "Aries" to "Mars", "Taurus" to "Venus", "Gemini" to "Mercury",
        "Cancer" to "Moon", "Leo" to "Sun", "Virgo" to "Mercury",
        "Libra" to "Venus", "Scorpio" to "Mars", "Sagittarius" to "Jupiter",
        "Capricorn" to "Saturn", "Aquarius" to "Saturn", "Pisces" to "Jupiter"
    )

    val EXALT = mapOf(
        "Sun" to "Aries", "Moon" to "Taurus", "Mars" to "Capricorn",
        "Mercury" to "Virgo", "Jupiter" to "Cancer", "Venus" to "Pisces",
        "Saturn" to "Libra"
    )

    /** Moolatrikona signs. Moon=Cancer is the MOTAA-specific value (standard texts
     *  usually give Taurus); the difference is flagged wherever it matters. */
    val MOOLA = mapOf(
        "Sun" to "Leo", "Moon" to "Cancer", "Mars" to "Aries",
        "Mercury" to "Virgo", "Jupiter" to "Sagittarius", "Venus" to "Libra",
        "Saturn" to "Aquarius"
    )

    /** Combustion orbs in degrees (MOTAA Ch.7 factor (၃); Sun/Rahu/Ketu exempt). */
    val COMBUST_ORB = mapOf(
        "Moon" to 12.0, "Mars" to 17.0, "Mercury" to 14.0,
        "Jupiter" to 10.0, "Venus" to 11.0, "Saturn" to 15.0
    )

    val KENDRA = setOf(1, 4, 7, 10)
    val TRIKONA = setOf(1, 5, 9)
    val DUSTHANA = setOf(6, 8, 12)

    fun signIndex(sign: String): Int = SIGNS.indexOf(sign)
    fun signOf(lon: Double): String {
        val norm = ((lon % 360.0) + 360.0) % 360.0
        return SIGNS[(norm / 30.0).toInt() % 12]
    }
    fun d9SignOf(lon: Double): String {
        val norm = ((lon % 360.0) + 360.0) % 360.0
        return SIGNS[(norm / (30.0 / 9.0)).toInt() % 12]
    }
    fun signMm(sign: String): String {
        val i = SIGNS.indexOf(sign)
        return if (i >= 0) SIGNS_MM[i] else sign
    }
    fun planetMm(planet: String): String = PLANETS_MM[planet] ?: planet

    /** Whole-sign house of [sign] counted from [referenceSign] (1-12). */
    fun houseFrom(referenceSign: String, sign: String): Int {
        val r = signIndex(referenceSign)
        val s = signIndex(sign)
        if (r < 0 || s < 0) return -1
        return ((s - r) % 12 + 12) % 12 + 1
    }

    fun dignityOf(planet: String, sign: String): Dignity {
        if (EXALT[planet] == sign) return Dignity.EXALTED
        if (MOOLA[planet] == sign) return Dignity.MOOLA
        if (LORD[sign] == planet) return Dignity.OWN
        val exaltSign = EXALT[planet]
        if (exaltSign != null) {
            val debilSign = SIGNS[(signIndex(exaltSign) + 6) % 12]
            if (debilSign == sign) return Dignity.DEBILITATED
        }
        return Dignity.NEUTRAL
    }

    /** Metadata for one Yoga rule. [variantNote] is empty when the rule is stable. */
    data class YogaRule(
        val id: String,
        val nameEn: String,
        val nameMm: String,
        val formationMm: String,
        val themesMm: String,
        val sourceNote: String,
        val variantNote: String = ""
    )

    const val SOURCE_BPHS_UNVERIFIED =
        "အရင်းအမြစ်: ဗြဟတ်ပရာသရဟောရာရှာသျှ (ပရာသရရိုးရာ) — " +
            "အခန်း/ဂါထာ အမှတ်ကို edition ဖြင့် တိုက်စစ်ရန် ကျန်ရှိ"
    const val SOURCE_MOTAA_FUNCTIONAL =
        "လုပ်ငန်းသဘော ကောင်း/ဆိုး စည်းမျဉ်း: MOTAA Ch.7 §၂ (စာအုပ်မှ တိုက်စစ်ပြီး) — " +
            "၆/၈/၁၂ အိမ်၏ မူလတြိကောဏသခင်များနှင့် ရာဟု/ကေတုမှာ ဆိုး၊ ကျန်တာ ကောင်း"
    const val SOURCE_MOTAA_COMBUST =
        "လောင်ကျွမ်းမှု (combustion) orb: MOTAA Ch.7 (၃) (စာအုပ်မှ တိုက်စစ်ပြီး)"

    val PANCHA = listOf(
        YogaRule(
            "rucaka", "Ruchaka Mahapurusha Yoga", "ရုစကယောဂ",
            "အင်္ဂါဂြိုဟ်က ကိုယ်ပိုင်ရာသီ သို့မဟုတ် ဥုစ်ရာသီတွင် ရှိ၍ လဂ်မှ ကိန်း (၁/၄/၇/၁၀) တွင် တည်နေခြင်း",
            "ဤဇာတာတွင် ရဲရင့်ထက်မြက်မှု၊ အာဏာနှင့် ဦးဆောင်မှုတို့ ရနိုင်ပြီး စစ်ရေး၊ အုပ်ချုပ်ရေးနှင့် ပြိုင်ဆိုင်မှုတို့တွင် ထင်ရှားနိုင်သည်ဟု ရိုးရာဗေဒင်အရ ယူဆကြသည်။",
            SOURCE_BPHS_UNVERIFIED,
            "အချို့မူကွဲများတွင် လဂ်အစား လမှ ကိန်းကို ရေတွက်သည် — ဤ app တွင် လဂ်မှ ရေတွက်ခြင်းကို အဓိကထားသည်။"
        ),
        YogaRule(
            "bhadra", "Bhadra Mahapurusha Yoga", "ဘဒြယောဂ",
            "ဗုဒ္ဓဟူးဂြိုဟ်က ကိုယ်ပိုင်ရာသီ သို့မဟုတ် ဥုစ်ရာသီတွင် ရှိ၍ လဂ်မှ ကိန်း (၁/၄/၇/၁၀) တွင် တည်နေခြင်း",
            "ဤဇာတာတွင် ဉာဏ်ပညာနှင့် စကားစွမ်းရည်ဖြင့် ထင်ရှားနိုင်ပြီး ပညာရေး၊ စာပေနှင့် ကူးသန်းရောင်းဝယ်ရေးတို့တွင် အောင်မြင်နိုင်သည်ဟု ရိုးရာဗေဒင်အရ ယူဆကြသည်။",
            SOURCE_BPHS_UNVERIFIED,
            "အချို့မူကွဲများတွင် လဂ်အစား လမှ ကိန်းကို ရေတွက်သည် — ဤ app တွင် လဂ်မှ ရေတွက်ခြင်းကို အဓိကထားသည်။"
        ),
        YogaRule(
            "hamsa", "Hamsa Mahapurusha Yoga", "ဟံသယောဂ",
            "ဂုရုဂြိုဟ်က ကိုယ်ပိုင်ရာသီ သို့မဟုတ် ဥုစ်ရာသီတွင် ရှိ၍ လဂ်မှ ကိန်း (၁/၄/၇/၁၀) တွင် တည်နေခြင်း",
            "ဤဇာတာတွင် ပညာဂုဏ်နှင့် ဂုဏ်သိက္ခာဖြင့် ထင်ရှားနိုင်ပြီး ဓမ္မနှင့် ပညာရေးတို့တွင် သူတပါးတို့၏ လေးစားမှုကို ရနိုင်သည်ဟု ရိုးရာဗေဒင်အရ ယူဆကြသည်။",
            SOURCE_BPHS_UNVERIFIED,
            "အချို့မူကွဲများတွင် လဂ်အစား လမှ ကိန်းကို ရေတွက်သည် — ဤ app တွင် လဂ်မှ ရေတွက်ခြင်းကို အဓိကထားသည်။"
        ),
        YogaRule(
            "malavya", "Malavya Mahapurusha Yoga", "မာလဝျယောဂ",
            "သောကြာဂြိုဟ်က ကိုယ်ပိုင်ရာသီ သို့မဟုတ် ဥုစ်ရာသီတွင် ရှိ၍ လဂ်မှ ကိန်း (၁/၄/၇/၁၀) တွင် တည်နေခြင်း",
            "ဤဇာတာတွင် အနုပညာနှင့် စည်းစိမ်ချမ်းသာကို ခံစားနိုင်ပြီး ယာဉ်ရထား၊ အဆင်တန်ဆာနှင့် သက်တောင့်သက်သာရှိသော ဘဝကို ရနိုင်သည်ဟု ရိုးရာဗေဒင်အရ ယူဆကြသည်။",
            SOURCE_BPHS_UNVERIFIED,
            "အချို့မူကွဲများတွင် လဂ်အစား လမှ ကိန်းကို ရေတွက်သည် — ဤ app တွင် လဂ်မှ ရေတွက်ခြင်းကို အဓိကထားသည်။"
        ),
        YogaRule(
            "sasa", "Sasa Mahapurusha Yoga", "သဿယောဂ",
            "စနေဂြိုဟ်က ကိုယ်ပိုင်ရာသီ သို့မဟုတ် ဥုစ်ရာသီတွင် ရှိ၍ လဂ်မှ ကိန်း (၁/၄/၇/၁၀) တွင် တည်နေခြင်း",
            "ဤဇာတာတွင် သည်းခံနိုင်စွမ်းနှင့် အမှုဆောင်စွမ်းရည်ဖြင့် ထင်ရှားနိုင်ပြီး လူအများကို ဦးဆောင်ရသော လုပ်ငန်းတို့တွင် အောင်မြင်နိုင်သည်ဟု ရိုးရာဗေဒင်အရ ယူဆကြသည်။",
            SOURCE_BPHS_UNVERIFIED,
            "အချို့မူကွဲများတွင် လဂ်အစား လမှ ကိန်းကို ရေတွက်သည် — ဤ app တွင် လဂ်မှ ရေတွက်ခြင်းကို အဓိကထားသည်။"
        )
    )

    val GAJA_KESARI = YogaRule(
        "gaja_kesari", "Gaja Kesari Yoga", "ဂဇကေသရီယောဂ",
        "ဂုရုဂြိုဟ်က လမှ ကိန်း (၁/၄/၇/၁၀) တွင် တည်နေခြင်း",
        "ဤဇာတာတွင် ပညာဂုဏ်ဖြင့် ထင်ရှားနိုင်ပြီး ပညာရှိ သူတော်ကောင်းတို့၏ ချီးမြောက်ခြင်းကို ခံရနိုင်သည်ဟု ရိုးရာဗေဒင်အရ ယူဆကြသည်။",
        SOURCE_BPHS_UNVERIFIED,
        "အချို့မူကွဲများတွင် လဂ်မှ ကိန်းကိုပါ ထည့်ရေတွက်သည် — ဤ app တွင် လမှ ရေတွက်ခြင်းကို အဓိကထားသည်။ " +
            "ဂုရု နိစ် (Capricorn) ဖြစ်ခြင်း သို့မဟုတ် လောင်ကျွမ်းနေခြင်းသည် ဖွဲ့စည်းမှုကို မဖျက်ပါ၊ အားနည်းစေသော အချက်အဖြစ် မှတ်တမ်းတင်သည်။"
    )

    val BUDHA_ADITYA = YogaRule(
        "budha_aditya", "Budha Aditya Yoga", "ဗုဓာဒိတျယောဂ",
        "နေ နှင့် ဗုဒ္ဓဟူး တစ်ရာသီတည်း ယှဉ်နေခြင်း",
        "ဤဇာတာတွင် ဉာဏ်ပညာနှင့် စကားစွမ်းရည်ဖြင့် ထင်ရှားနိုင်ပြီး ပညာရေးနှင့် ဂုဏ်သိက္ခာတို့တွင် ထူးချွန်နိုင်သည်ဟု ရိုးရာဗေဒင်အရ ယူဆကြသည်။",
        SOURCE_BPHS_UNVERIFIED,
        "အချို့မူကွဲများတွင် ကိန်း/တြိကောဏတွင် တည်မှသာ အပြည့်အဝ သတ်မှတ်သည် — " +
            "ဤ app တွင် ယှဉ်ခြင်းကို အဓိကထားပြီး တည်သောအိမ်ကို ကန့်သတ်/အားပေးချက်အဖြစ် မှတ်တမ်းတင်သည်။"
    )

    val RAJA_YOGAKARAKA = YogaRule(
        "raja_yogakaraka", "Raja Yoga — Yogakaraka", "ရာဇယောဂ (ယောဂကာရက)",
        "ဂြိုဟ်တစ်လုံးက ကိန်းအိမ်ရှင်နှင့် တြိကောဏအိမ်ရှင် နှစ်မျိုးလုံး ဖြစ်နေခြင်း (လဂ်ရှင်အပြင်)",
        "ဤဇာတာတွင် အာဏာနှင့် ရာထူးကို ရနိုင်ပြီး ဦးဆောင်မှုနှင့် လူမှုအဆင့်အတန်းတို့တွင် မြင့်မားနိုင်သည်ဟု ရိုးရာဗေဒင်အရ ယူဆကြသည်။",
        SOURCE_BPHS_UNVERIFIED
    )

    val RAJA_SAMBANDHA = YogaRule(
        "raja_sambandha", "Raja Yoga — Kendra-Trikona Sambandha", "ရာဇယောဂ (ကိန်း-တြိကောဏ ဆက်နွယ်)",
        "ကိန်းအိမ်ရှင်နှင့် တြိကောဏအိမ်ရှင် နှစ်လုံးက တစ်ရာသီတည်း (ယှဉ်)၊ မျက်နှာချင်းဆိုင် (ခုနစ်မျက်နှာ) သို့မဟုတ် အပြန်အလှန်ရာသီလဲလှယ် (ပရိဝတ္တန) ရှိနေခြင်း",
        "ဤဇာတာတွင် ရာထူးနှင့် အာဏာကို ရနိုင်ပြီး ကြီးမားသော လုပ်ငန်းများကို ဦးဆောင်နိုင်သည်ဟု ရိုးရာဗေဒင်အရ ယူဆကြသည်။",
        SOURCE_BPHS_UNVERIFIED
    )

    val DHANA = YogaRule(
        "dhana", "Dhana Yoga", "ဓနယောဂ",
        "ဓနအိမ်ရှင်များ (၂/၅/၉/၁၁ အိမ်ရှင်) နှစ်လုံးက တစ်ရာသီတည်း (ယှဉ်)၊ မျက်နှာချင်းဆိုင် (ခုနစ်မျက်နှာ) သို့မဟုတ် အပြန်အလှန်ရာသီလဲလှယ် (ပရိဝတ္တန) ရှိနေခြင်း",
        "ဤဇာတာတွင် စည်းစိမ်ဥစ္စာနှင့် ဝင်ငွေကို ရနိုင်ပြီး ငွေကြေးအခွင့်အလမ်းများ ပေါ်ပေါက်နိုင်သည်ဟု ရိုးရာဗေဒင်အရ ယူဆကြသည်။",
        SOURCE_BPHS_UNVERIFIED,
        "ဓနယောဂသည် ငွေဝင်မှုကို ညွှန်းသော်လည်း သုံးစွဲမှု/ဆုံးရှုံးမှုကို အာမမခံပါ — ယောဂ တည်သောအိမ် (အထူးသဖြင့် ၁၂) ကို ကန့်သတ်ချက်အဖြစ် ဖတ်ရမည်။"
    )

    const val SOURCE_PHALADEEPIKA_UNVERIFIED =
        "အရင်းအမြစ်: ဖလဒီပိကာ ရိုးရာ (Mantreswara) — " +
            "အခန်း/ဂါထာ အမှတ်ကို edition ဖြင့် တိုက်စစ်ရန် ကျန်ရှိ"
    const val SOURCE_SARAVALI_UNVERIFIED =
        "အရင်းအမြစ်: သာရာဝလီ ရိုးရာ (Kalyana Varma) — " +
            "အခန်း/ဂါထာ အမှတ်ကို edition ဖြင့် တိုက်စစ်ရန် ကျန်ရှိ"

    /** Phase 2 rules (v1.8.0). Themes are conditional traditional readings. */
    val PHASE2 = listOf(
        YogaRule(
            "chandra_sunapha", "Sunapha Yoga (Chandra)", "သုနဖယောဂ",
            "လမှ ၂ အိမ်တွင် ဂြိုဟ် (နေ/ရာဟု/ကေတု အပြင်) တည်ခြင်း",
            "ဤဇာတာတွင် ကိုယ်ပိုင်ကြိုးစားမှုဖြင့် စည်းစိမ်ဥစ္စာကို ရနိုင်သည်ဟု ရိုးရာဗေဒင်အရ ယူဆကြသည်။",
            SOURCE_PHALADEEPIKA_UNVERIFIED
        ),
        YogaRule(
            "chandra_anapha", "Anapha Yoga (Chandra)", "အနဖယောဂ",
            "လမှ ၁၂ အိမ်တွင် ဂြိုဟ် (နေ/ရာဟု/ကေတု အပြင်) တည်ခြင်း",
            "ဤဇာတာတွင် ကိုယ်ပိုင်အရည်အချင်းဖြင့် ဂုဏ်သိက္ခာကို ရနိုင်သည်ဟု ရိုးရာဗေဒင်အရ ယူဆကြသည်။",
            SOURCE_PHALADEEPIKA_UNVERIFIED
        ),
        YogaRule(
            "chandra_durudhara", "Durudhara Yoga (Chandra)", "ဒုရုဓရာ‌ယောဂ",
            "လမှ ၂ အိမ်နှင့် ၁၂ အိမ် နှစ်ဖက်လုံးတွင် ဂြိုဟ် (နေ/ရာဟု/ကေတု အပြင်) တည်ခြင်း",
            "ဤဇာတာတွင် စည်းစိမ်နှင့် လူမှုအထောက်အပံ့ နှစ်မျိုးလုံးကို ရနိုင်သည်ဟု ရိုးရာဗေဒင်အရ ယူဆကြသည်။",
            SOURCE_PHALADEEPIKA_UNVERIFIED
        ),
        YogaRule(
            "chandra_kemadruma", "Kemadruma Yoga (Chandra)", "ကေမဒြုမယောဂ",
            "လမှ ၂ အိမ်နှင့် ၁၂ အိမ် နှစ်ဖက်လုံး လွတ်နေခြင်း (နေ/ရာဟု/ကေတု မပါ)",
            "ဤဇာတာတွင် အထီးကျန်မှုနှင့် ငွေကြေးအခက်အခဲ၊ စိတ်ဖိစီးမှုတို့ ကြုံရနိုင်သည်ဟု ရိုးရာဗေဒင်အရ ယူဆကြသည်။",
            SOURCE_PHALADEEPIKA_UNVERIFIED,
            "ပျက်ပြယ်ချက်: လသည် လဂ်မှ ကိန်းတွင် တည်ပါက သို့မဟုတ် ကောင်းသော ဂြိုဟ်နှင့် " +
                "ယှဉ်/အမြင်ရှိပါက အကျိုး ပျော့သည်ဟု ဆိုသည် — ဤ app တွင် ကန့်သတ်ချက်အဖြစ် ပြသည်။"
        ),
        YogaRule(
            "viparita_harsha", "Harsha Yoga (Viparita Raja)", "ဟာရှ‌ယောဂ",
            "၆ အိမ်ရှင်က ၆/၈/၁၂ အိမ်တွင် တည်ခြင်း",
            "ဤဇာတာတွင် ရောဂါ၊ ကြွေးနှင့် အတားအဆီးများကို ကျော်လွှားနိုင်သည်ဟု ရိုးရာဗေဒင်အရ ယူဆကြသည်။",
            SOURCE_BPHS_UNVERIFIED
        ),
        YogaRule(
            "viparita_sarala", "Sarala Yoga (Viparita Raja)", "သရလယောဂ",
            "၈ အိမ်ရှင်က ၆/၈/၁၂ အိမ်တွင် တည်ခြင်း",
            "ဤဇာတာတွင် ရန်သူနှင့် အခက်အခဲတို့မှ အောင်မြင်မှုကို ရနိုင်သည်ဟု ရိုးရာဗေဒင်အရ ယူဆကြသည်။",
            SOURCE_BPHS_UNVERIFIED
        ),
        YogaRule(
            "viparita_vimala", "Vimala Yoga (Viparita Raja)", "ဝိမလယောဂ",
            "၁၂ အိမ်ရှင်က ၆/၈/၁၂ အိမ်တွင် တည်ခြင်း",
            "ဤဇာတာတွင် လွတ်လပ်သော ဘဝနှင့် ကိုယ်ပိုင်ဂုဏ်သိက္ခာကို ရနိုင်ပြီး သုံးစွဲမှုကို ထိန်းနိုင်သည်ဟု ရိုးရာဗေဒင်အရ ယူဆကြသည်။",
            SOURCE_BPHS_UNVERIFIED
        ),
        YogaRule(
            // Error Prevention Rule 4: cancellation alone is Neecha Bhanga, NOT
            // automatically Neecha Bhanga Raja Yoga — the Raja effect needs a
            // separate check (yogakaraka / kendra-trikona sambandha entries).
            "neecha_bhanga", "Neecha Bhanga Yoga", "နိစ္စဘင်္ဂယောဂ",
            "နိစ် ဂြိုဟ်နှင့် ပျက်ပြယ်ချက် (နိစ်ရာသီရှင် သို့မဟုတ် ဥုစ်ရာသီရှင် လဂ်မှ/လမှ ကိန်းတွင်) ကိုက်ညီခြင်း",
            "ဤဇာတာတွင် အစောပိုင်း အတားအဆီးများကို ကျော်လွှားပြီးမှ အောင်မြင်မှုကို ရနိုင်ပြီး ဆုံးရှုံးသော အခွင့်အလမ်းများ ပြန်ရနိုင်သည်ဟု ရိုးရာဗေဒင်အရ ယူဆကြသည်။",
            SOURCE_BPHS_UNVERIFIED,
            "သတိပေးချက်: နိစ် ပျက်ပြယ်ခြင်းကို ရာဇယောဂ အကျိုးဟု အလိုအလျောက် " +
                "မသတ်မှတ်ပါ — ရာဇအကျိုးကို (ယောဂကာရက / ကိန်း-တြိကောဏ ဆက်နွယ် ကဲ့သို့) " +
                "သီးခြား စစ်ရမည်။ ရာဟု/ကေတုကို ရေတွက်ခြင်း မပြုပါ။ " +
                "နိစ် ဂြိုဟ် ရှိသော်လည်း ပျက်ပြယ်ချက် မရှိပါက မဖွဲ့ပါ။"
        ),
        YogaRule(
            "daridra", "Daridra Yoga", "ဒါရိဒြယောဂ",
            "၁၁ အိမ်ရှင် ဒုဋ္ဌာနတွင် တည်ပြီး လဂ်ရှင်ကလည်း နိစ်/လောင်ကျွမ်း/ဒုဋ္ဌာနတွင် တည်ခြင်း",
            "ဤဇာတာတွင် ဝင်ငွေနှင့် သုံးစွဲမှု မမျှတခြင်းကို ကြုံရနိုင်ပြီး စီးပွားရေးအတားအဆီးနှင့် ကြွေးအန္တရာယ်ကို သတိထားရနိုင်သည်ဟု ရိုးရာဗေဒင်အရ ယူဆကြသည်။",
            SOURCE_BPHS_UNVERIFIED,
            "မူကွဲ သတိပေးချက်: ဒါရိဒြယောဂ၏ ဖော်မြူလာသည် ကျမ်းအလိုက် ကွဲပြားသည် — " +
                "ဤဖော်မြူလာသည် အသုံးများသော ပေါင်းစပ်ပုံစံ ဖြစ်ပြီး edition ဖြင့် တိုက်စစ်ရန် ကျန်ရှိ။"
        ),
        YogaRule(
            "arishta", "Arishta Yoga", "အရိဋ္ဌယောဂ",
            "ဆိုးသော ဂြိုဟ် (အင်္ဂါ/စနေ/ရာဟု/ကေတု) ၈ အိမ်တွင် တည်ပြီး လ ထိခိုက်နေခြင်း " +
                "(နိစ် / ဆိုးသောဂြိုဟ်နှင့် ယှဉ် / ဒုဋ္ဌာနတွင်)",
            "ဤဇာတာတွင် လုပ်ငန်းတွင် နှောင့်နှေးမှုနှင့် မမျှော်လင့်သော ပြဿနာများကို ကြုံရနိုင်ပြီး ကျန်းမာရေးကို သတိထားရနိုင်သည်ဟု ရိုးရာဗေဒင်အရ ယူဆကြသည်။",
            SOURCE_BPHS_UNVERIFIED,
            "မူကွဲ သတိပေးချက်: အရိဋ္ဌယောဂ အမျိုးမျိုး ရှိသည် — ဤပုံစံသည် " +
                "လထိခိုက်မှု အကျယ်ပုံစံ ဖြစ်ပြီး edition ဖြင့် တိုက်စစ်ရန် ကျန်ရှိ။"
        ),
        YogaRule(
            "chandra_mangala", "Chandra Mangala Yoga", "စန္ဒြမင်္ဂလယောဂ",
            "လနှင့် အင်္ဂါ တစ်ရာသီတည်း ယှဉ် သို့မဟုတ် မျက်နှာချင်းဆိုင် (၇ မျက်နှာ) ဖြစ်ခြင်း",
            "ဤဇာတာတွင် ရဲရင့်မှုနှင့် စီးပွားရေးစွမ်းရည်ဖြင့် ငွေကြေးအောင်မြင်မှုကို ရနိုင်သည်ဟု ရိုးရာဗေဒင်အရ ယူဆကြသည်။",
            SOURCE_PHALADEEPIKA_UNVERIFIED
        ),
        YogaRule(
            "guru_mangala", "Guru Mangala Yoga", "ဂုရုမင်္ဂလယောဂ",
            "ဂုရုနှင့် အင်္ဂါ တစ်ရာသီတည်း ယှဉ် ဖြစ်ခြင်း",
            "ဤဇာတာတွင် ပညာနှင့် ရဲရင့်မှု ပေါင်းစပ်ခြင်းဖြင့် ဦးဆောင်မှုကို ရနိုင်သည်ဟု ရိုးရာဗေဒင်အရ ယူဆကြသည်။",
            SOURCE_PHALADEEPIKA_UNVERIFIED
        ),
        YogaRule(
            "lakshmi", "Lakshmi Yoga", "လက္မီယောဂ",
            "လဂ်ရှင် ဥုစ်/ကိုယ်ပိုင်/မူလ ဖြစ်ပြီး ၉ အိမ်ရှင် ကိန်း/တြိကောဏ တွင် တည်ခြင်း",
            "ဤဇာတာတွင် စည်းစိမ်ချမ်းသာနှင့် ကံကောင်းမှုကို ရနိုင်သည်ဟု ရိုးရာဗေဒင်အရ ယူဆကြသည်။",
            SOURCE_PHALADEEPIKA_UNVERIFIED,
            "မူကွဲ သတိပေးချက်: လက္မီယောဂ၏ ဖော်မြူလာ ကျမ်းအလိုက် ကွဲပြားသည်။"
        ),
        YogaRule(
            "saraswati", "Saraswati Yoga", "သရသွတီယောဂ",
            "ဂုရု၊ သောကြာ၊ ဗုဒ္ဓဟူး သုံးလုံး ကိန်း/တြိကောဏ/၂ အိမ်တွင် တည်ပြီး " +
                "ဂုရု ဥုစ်/ကိုယ်ပိုင် သို့မဟုတ် မိတ်ဆွေ ရာသီရှင်၏ ရာသီတွင် တည်ခြင်း",
            "ဤဇာတာတွင် ပညာ၊ အနုပညာနှင့် စာပေတို့တွင် အရည်အချင်း ထူးချွန်နိုင်သည်ဟု ရိုးရာဗေဒင်အရ ယူဆကြသည်။",
            SOURCE_PHALADEEPIKA_UNVERIFIED
        ),
        YogaRule(
            "adhi", "Adhi Yoga", "အဓိယောဂ",
            "ဂုရု၊ သောကြာ၊ ဗုဒ္ဓဟူး သုံးလုံး လမှ ၆/၇/၈ အိမ်တွင် တည်ခြင်း",
            "ဤဇာတာတွင် ရာထူးနှင့် ဦးဆောင်မှုကို ရနိုင်ပြီး အဆင့်အတန်းမြင့်မားနိုင်သည်ဟု ရိုးရာဗေဒင်အရ ယူဆကြသည်။",
            SOURCE_PHALADEEPIKA_UNVERIFIED
        ),
        YogaRule(
            "amala", "Amala Yoga", "အမလယောဂ",
            "ကောင်းသော ဂြိုဟ် (ဂုရု/သောကြာ/ဗုဒ္ဓဟူး) တစ်လုံး လဂ်မှ ၁၀ အိမ် " +
                "သို့မဟုတ် လမှ ၁၀ အိမ်တွင် တည်ခြင်း",
            "ဤဇာတာတွင် နာမည်ကောင်းနှင့် သမာအာဇီဝကို ရနိုင်ပြီး ကိုယ်ကျင့်တရားဖြင့် ထင်ရှားနိုင်သည်ဟု ရိုးရာဗေဒင်အရ ယူဆကြသည်။",
            SOURCE_PHALADEEPIKA_UNVERIFIED
        ),
        YogaRule(
            "parivartana", "Parivartana Yoga", "ပရိဝတ္တနယောဂ",
            "အိမ်ရှင် ဂြိုဟ် နှစ်လုံး အပြန်အလှန် ရာသီလဲလှယ်ခြင်း — " +
                "မဟာ (ကိန်း/တြိကောဏ အချင်းချင်း)၊ ဒိုင်းည (ဒုဋ္ဌာန အချင်းချင်း)၊ ခလ (ရောနှော)",
            "ဤဇာတာတွင် လဲလှယ်သော အိမ်နှစ်ခု၏ အကြောင်းအရာများ အပြန်အလှန် သက်ရောက်နိုင်သည်။ မဟာကို အကောင်းဆုံး၊ ဒိုင်းညကို အခက်အခဲဆုံး၊ ခလကို ရောနှောဟု ဖတ်ရှုကြသည်။",
            SOURCE_SARAVALI_UNVERIFIED,
            "အိမ်တွက်ချက်မှုသည် whole-sign (ရာသီ) အလိုက် ဖြစ်သည် — " +
                "အုပ်စိုးရာသီဖြင့် အိမ်ရှင်ကို ဆုံးဖြတ်သည်။ MOTAA Bhava " +
                "(လဂ်ဒီဂရီဗဟို ±၁၅°) နေရာချထားမှုနှင့် ကွဲပြားနိုင်သည်။"
        ),
        YogaRule(
            "vasumati", "Vasumati Yoga", "ဝသုမတီယောဂ",
            "ဂုရု၊ သောကြာ၊ ဗုဒ္ဓဟူး သုံးလုံး လဂ်မှ ဥပစယ (၃/၆/၁၀/၁၁) အိမ်များတွင် တည်ခြင်း",
            "ဤဇာတာတွင် ဥစ္စာဓနနှင့် ကြွယ်ဝမှုကို ရနိုင်သည်ဟု ရိုးရာဗေဒင်အရ ယူဆကြသည်။",
            SOURCE_PHALADEEPIKA_UNVERIFIED
        ),
        YogaRule(
            "shakata", "Shakata Yoga", "ၑခဋယောဂ",
            "ဂုရု လမှ ၆/၈/၁၂ အိမ်တွင် တည်ခြင်း",
            "ဤဇာတာတွင် ကံအတက်အကျနှင့် စီးပွားရေးမတည်ငြိမ်မှုကို ကြုံရနိုင်ပြီး ဂုရု အားကောင်းပါက အကျိုး ပျော့နိုင်သည်ဟု ရိုးရာဗေဒင်အရ ယူဆကြသည်။",
            SOURCE_PHALADEEPIKA_UNVERIFIED,
            "မှတ်ချက်: Nabhasa အမျိုးအစားထဲက Shakata နှင့် မတူပါ။"
        ),
        YogaRule(
            "grahana", "Grahana Yoga", "ဂြဟဏယောဂ",
            "နေ သို့မဟုတ် လက ရာဟု/ကေတုနှင့် တစ်ရာသီတည်း ယှဉ် ဖြစ်ခြင်း",
            "ဤဇာတာတွင် စိတ်ပိုင်းဆိုင်ရာ ဖိစီးမှုနှင့် ဘဝအတားအဆီးများကို ကြုံရနိုင်သည်ဟု ရိုးရာဗေဒင်အရ ယူဆကြသည်။",
            SOURCE_BPHS_UNVERIFIED
        ),
        YogaRule(
            "mahabhagya", "Mahabhagya Yoga", "မဟာဘာဂျယောဂ",
            "နေ့ဖွား အမျိုးသား — နေ၊ လ၊ လဂ် သုံးခု စုံရာသီတွင် တည်ခြင်း၊ သို့မဟုတ် " +
                "ညဖွား အမျိုးသမီး — သုံးခု မရာသီတွင် တည်ခြင်း",
            "ဤဇာတာတွင် ကံကြမ္မာကောင်းခြင်းနှင့် ထူးခြားသော ဘဝလမ်းကြောင်းကို ရနိုင်သည်ဟု ရိုးရာဗေဒင်အရ ယူဆကြသည်။",
            SOURCE_BPHS_UNVERIFIED,
            "ကန့်သတ်ချက်: မွေးဇာတာ entry တွင် ကျား/မ အချက်အလက် မပါသေးသောကြောင့် " +
                "တစ်ဖက်တည်းသာ ကိုက်ညီပါက မဆုံးဖြတ်နိုင်အဖြစ် ပြသည်။"
        )
    )

    fun ruleById(id: String): YogaRule? {
        return PANCHA.firstOrNull { it.id == id }
            ?: PHASE2.firstOrNull { it.id == id }
            ?: when (id) {
                "budha_aditya" -> BUDHA_ADITYA
                "gaja_kesari" -> GAJA_KESARI
                "raja_yogakaraka" -> RAJA_YOGAKARAKA
                "raja_sambandha" -> RAJA_SAMBANDHA
                "dhana" -> DHANA
                else -> null
            }
    }
}
