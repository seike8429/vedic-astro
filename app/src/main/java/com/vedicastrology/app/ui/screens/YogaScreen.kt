package com.vedicastrology.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vedicastrology.app.engine.BirthData
import com.vedicastrology.app.engine.ChartResult
import com.vedicastrology.app.engine.dasha.DashaEngine
import com.vedicastrology.app.engine.dasha.DashaYearConvention
import com.vedicastrology.app.engine.yoga.DashaLink
import com.vedicastrology.app.engine.yoga.DetectedYoga
import com.vedicastrology.app.engine.yoga.YogaCatalog
import com.vedicastrology.app.engine.yoga.YogaDashaLinker
import com.vedicastrology.app.engine.yoga.YogaDetectionEngine
import com.vedicastrology.app.engine.yoga.YogaEffect
import com.vedicastrology.app.engine.yoga.YogaReportBuilder
import com.vedicastrology.app.engine.yoga.YogaStatus
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Yoga tab: expandable traditional Yoga reports for the current chart.
 * Card badge = EFFECT state (formation ≠ beneficence): STRONG green,
 * NEUTRAL blue, WEAK amber, DIMINISHED amber, HARDSHIP red, NONE grey.
 * Collapsed card = name + badge + concise primary reading. Expanded card =
 * five sections: §1 အဓိကဟောကိန်း, §2 ကောင်းကျိုးများ, §3 သတိပြုရန်,
 * §4 အကျိုးပေးကာလ (link + reading + current path + current / upcoming /
 * past-collapsed / full-120-collapsed Mahadasha sections with ages;
 * display policy birth..age-100, full view opt-in to 120),
 * §5 တွက်ချက်မှုအသေးစိတ် (hidden by default: formation, cancellation,
 * planet quality, source + rule id). No scores, no netting (spec F).
 */
@Composable
fun YogaScreen(natal: ChartResult, birth: BirthData, chartName: String) {
    val tz = remember(birth) { chartTimeZone(birth) }
    val fmt = remember(tz) {
        SimpleDateFormat("dd/MM/yyyy", Locale.US).apply { timeZone = tz }
    }
    // One query instant for both the report and the card's past/current/
    // future split, so the link and the shown periods can never disagree.
    val birthMillis = remember(natal) { DashaEngine.julianDayToMillis(natal.julianDay) }
    val now = remember(natal) { System.currentTimeMillis() }
    val report = remember(natal) {
        val grahas = setOf(
            "Sun", "Moon", "Mars", "Mercury", "Jupiter", "Venus", "Saturn", "Rahu", "Ketu"
        )
        val lons = natal.planets
            .filter { it.row.planet in grahas }
            .associate { it.row.planet to it.row.longitude }
        val moonLon = natal.planets.firstOrNull { it.row.planet == "Moon" }?.row?.longitude
        YogaReportBuilder.build(
            chartName, lons, natal.ascendant, moonLon,
            birthMillis, now
        )
    }
    val formed = report.yogas.filter {
        it.status == YogaStatus.FORMED || it.status == YogaStatus.QUALIFIED
    }
    val notFormed = report.yogas.filter {
        it.status == YogaStatus.NOT_FORMED || it.status == YogaStatus.INDETERMINATE
    }
    var showNotFormed by remember { mutableStateOf(false) }

    LazyColumn(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        item {
            Text("ယောဂ (Parashara Yoga)", style = MaterialTheme.typography.titleLarge)
            Text(
                "ဇာတာ — $chartName",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            if (formed.isEmpty()) {
                Text(
                    "ဤဇာတာတွင် Phase 1 ယောဂများ (ပဉ္စမဟာပုရုသ၊ ဂဇကေသရီ၊ ဗုဓာဒိတျ၊ ရာဇ၊ ဓန) " +
                        "ဖွဲ့စည်းထားခြင်း မရှိပါ။",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        // Current-dasha section: DIRECT yogas only, pulled out of the
        // full list with their detailed evidence-based narratives.
        // INDIRECT yogas are related, not "in" the dasha, and stay below.
        val directYogas = report.yogas.filter { it.dashaNarrative.isNotEmpty() }
        item {
            Text(
                "လက်ရှိ ဒသာတွင် အကျိုးပေးနေသော ယောဂများ",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                "လက်ရှိ ဒသာလမ်းကြောင်းတွင် ပါသော ဂြိုဟ်များနှင့် တိုက်ရိုက်ဆက်စပ်နေသော " +
                    "ယောဂများသာ — သွယ်ဝိုက်ဆက်စပ်မှုများ မပါပါ။ ဤယောဂများ၏ ကတ်များသည် " +
                    "အောက်စာရင်းတွင် ထပ်မံမပါပါ။",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            if (directYogas.isEmpty()) {
                Text(
                    "လက်ရှိ ဒသာနှင့် တိုက်ရိုက်ဆက်စပ်သော ယောဂ မရှိပါ။",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        items(directYogas, key = { "direct-" + it.ruleId + it.planets.joinToString() }) { yoga ->
            val rule = YogaCatalog.ruleById(yoga.ruleId)
            val nameMm = yoga.displayNameMm.ifEmpty { rule?.nameMm ?: yoga.ruleId }
            val nameEn = yoga.displayNameEn.ifEmpty { rule?.nameEn ?: yoga.ruleId }
            val (badgeText, badgeColor) = when (yoga.effect) {
                YogaEffect.STRONG -> "အကျိုးပေးအားကောင်း" to Color(0xFF2E7D32)
                YogaEffect.NEUTRAL -> "ဖွဲ့စည်းမှုရှိ" to Color(0xFF1565C0)
                YogaEffect.WEAK -> "အကျိုးပေးအားနည်း" to Color(0xFF8D6E00)
                YogaEffect.DIMINISHED -> "အကျိုးသက်ရောက်မှု လျော့ပါး" to Color(0xFF8D6E00)
                YogaEffect.HARDSHIP -> "ဖွဲ့သည်" to Color(0xFFB71C1C)
                YogaEffect.NONE -> YogaReportBuilder.statusMm(yoga.status) to Color(0xFF666666)
            }
            // Highlight color = effect color: the entry's tint and border
            // follow its badge, so effect states differ at a glance.
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = badgeColor.copy(alpha = 0.10f)
                ),
                border = BorderStroke(1.5.dp, badgeColor)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row {
                        Text(
                            "$nameMm ($nameEn)",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            badgeText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(yoga.dashaNarrative, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "ဆက်စပ်သော ဒသာအဆင့်:",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    for (per in yoga.activePath.filter { it.planet in yoga.planets }) {
                        val levelName = per.level.toIntOrNull()
                            ?.let { DASHA_LEVEL_NAMES.getOrNull(it - 1) } ?: per.level
                        Text(
                            "• $levelName: ${YogaCatalog.planetMm(per.planet)} " +
                                "(${fmt.format(java.util.Date(per.startEpochMillis))} → " +
                                fmt.format(java.util.Date(per.endEpochMillis)) + ")",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        // Pulled out = MOVED, not copied: DIRECT yogas live only in the
        // section above, never repeated in the list below.
        val remainingFormed = formed.filter { it.dashaLink != DashaLink.DIRECT }
        items(remainingFormed, key = { it.ruleId + it.planets.joinToString() }) { yoga ->
            YogaCard(yoga, fmt, birthMillis, now)
            Spacer(Modifier.height(8.dp))
        }
        if (report.conflictNotes.isNotEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "ဆန့်ကျင်နိုင်သော အနက်များ",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        for (note in report.conflictNotes) {
                            Text("• $note", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth()
                    .clickable { showNotFormed = !showNotFormed }
            ) {
                Row(Modifier.padding(12.dp)) {
                    Text(
                        "မဖွဲ့စည်းသောယောဂများ (${notFormed.size}) " +
                            if (showNotFormed) "▾" else "▸",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (showNotFormed) {
                for (yoga in notFormed) {
                    val rule = YogaCatalog.ruleById(yoga.ruleId)
                    val name = if (rule != null) "${rule.nameMm} (${rule.nameEn})" else yoga.ruleId
                    Text(
                        "• $name — ${YogaReportBuilder.statusMm(yoga.status)}: " +
                            yoga.formationFacts.joinToString("; "),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        item {
            Text(
                "ဤအနက်များသည် ရိုးရာဗေဒင်အနက်များသာ ဖြစ်ပြီး သိပ္ပံနည်းကျ " +
                    "သက်သေပြထားသော ဟောကိန်းများ မဟုတ်ပါ။ ယောဂ ရှိရုံနဲ့ အမြဲကောင်းမယ်၊ " +
                    "အမြဲဆိုးမယ်လို့ မသတ်မှတ်ပါနှင့် — ဂြိုဟ်အားနှင့် ဒသာအချိန်ကို " +
                    "ပေါင်းစစ်ပြီးမှ ဖတ်ပါ။ အရေအတွက်ကို ပေါင်းထည့်ပြီး " +
                    "အဖြေထုတ်ခြင်း မပြုပါနှင့်။",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun YogaCard(
    yoga: DetectedYoga,
    fmt: SimpleDateFormat,
    birthMillis: Long,
    queryMillis: Long
) {
    var expanded by remember { mutableStateOf(false) }
    var showDetail by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    val rule = YogaCatalog.ruleById(yoga.ruleId)
    // Pair-rule headers name their planets (displayNameMm/En), so two
    // instances of one rule never look like a duplicated card.
    val nameMm = yoga.displayNameMm.ifEmpty { rule?.nameMm ?: yoga.ruleId }
    val nameEn = yoga.displayNameEn.ifEmpty { rule?.nameEn ?: yoga.ruleId }
    val name = "$nameMm ($nameEn)"
    // Badge = EFFECT state, not formation: a formed Yoga is not
    // automatically beneficent. Kemadruma formed without cancellation is
    // HARDSHIP (red), never green.
    val (badgeText, badgeColor) = when (yoga.effect) {
        YogaEffect.STRONG -> "အကျိုးပေးအားကောင်း" to Color(0xFF2E7D32)
        YogaEffect.NEUTRAL -> "ဖွဲ့စည်းမှုရှိ" to Color(0xFF1565C0)
        YogaEffect.WEAK -> "အကျိုးပေးအားနည်း" to Color(0xFF8D6E00)
        YogaEffect.DIMINISHED -> "အကျိုးသက်ရောက်မှု လျော့ပါး" to Color(0xFF8D6E00)
        YogaEffect.HARDSHIP -> "ဖွဲ့သည်" to Color(0xFFB71C1C)
        YogaEffect.NONE -> YogaReportBuilder.statusMm(yoga.status) to Color(0xFF666666)
    }
    val isActive = yoga.status == YogaStatus.FORMED || yoga.status == YogaStatus.QUALIFIED
    // Display policy: main sections show birth..age-100 only; the full
    // view is the opt-in age-120 view. Classification uses TRUE intervals;
    // shown dates are clipped; the calculation itself is never changed.
    val yearMs = DashaYearConvention.DEFAULT.millisPerYear()
    val end100 = birthMillis + (100.0 * yearMs).toLong()
    val end120 = birthMillis + (120.0 * yearMs).toLong()
    fun periodLine(period: com.vedicastrology.app.engine.yoga.YogaDashaPeriod, markCurrent: Boolean = false): String {
        val ageStart = YogaDashaLinker.ageAt(period.startEpochMillis, birthMillis)
        val ageEnd = YogaDashaLinker.ageAt(period.endEpochMillis, birthMillis)
        return "• ${period.level} ${YogaCatalog.planetMm(period.planet)}: " +
            "${fmt.format(java.util.Date(period.startEpochMillis))} → " +
            fmt.format(java.util.Date(period.endEpochMillis)) +
            " (အသက် ${"%.1f".format(ageStart)}–${"%.1f".format(ageEnd)})" +
            if (markCurrent) " ● လက်ရှိ" else ""
    }
    Card(
        modifier = Modifier.fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column(Modifier.padding(12.dp)) {
            Row {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    badgeText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor
                )
            }
            if (!expanded) {
                // Concise prediction first.
                Spacer(Modifier.height(4.dp))
                Text(yoga.mainReading, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "အသေးစိတ်ကြည့်ရန် နှိပ်ပါ ▸",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                // §1 အဓိကဟောကိန်း
                Spacer(Modifier.height(8.dp))
                Text("အဓိကဟောကိန်း", fontWeight = FontWeight.Bold)
                Text(yoga.mainReading, style = MaterialTheme.typography.bodyMedium)
                if (isActive) {
                    // §2 ကောင်းကျိုးများ: at most 3 observations; the raw
                    // evidence behind them lives in §5, not repeated here.
                    if (yoga.supportingFactors.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text("ကောင်းကျိုးများ", fontWeight = FontWeight.Bold)
                        for (obs in yoga.supportingFactors.take(3)) {
                            Text("• $obs", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    // §3 သတိပြုရန်: delay implication first (moved out
                    // of §1 so main readings stay distinctive), then the
                    // specific limiting factors behind it.
                    if (yoga.cautionNote.isNotEmpty() || yoga.limitingFactors.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text("သတိပြုရန်", fontWeight = FontWeight.Bold)
                        if (yoga.cautionNote.isNotEmpty()) {
                            Text(yoga.cautionNote, style = MaterialTheme.typography.bodyMedium)
                        }
                        for (obs in yoga.limitingFactors.take(3)) {
                            Text("• $obs", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    // §4 အကျိုးပေးကာလ: link + reading + current path, then
                    // current / upcoming (age-100 range) / past (collapsed)
                    // / full timeline to age 120 (collapsed opt-in). A whole
                    // Mahadasha is shown as RELEVANT, never as automatically
                    // activating the Yoga — the conditional dashaReading
                    // sentence carries that.
                    Spacer(Modifier.height(6.dp))
                    Text("အကျိုးပေးကာလ", fontWeight = FontWeight.Bold)
                    Text(
                        "ဒသာ ဆက်စပ်မှု: ${YogaReportBuilder.dashaLinkMm(yoga.dashaLink)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (yoga.dashaReading.isNotEmpty()) {
                        Text(yoga.dashaReading, style = MaterialTheme.typography.bodyMedium)
                    }
                    // Why THIS dasha is relevant: DIRECT names the yoga
                    // planets on the current path (e.g. Saturn, the current
                    // Mahadasha, for the Moon–Saturn exchange); INDIRECT
                    // names the documented relation (conjunct / dispositor).
                    if (yoga.dashaLink == DashaLink.DIRECT) {
                        val linked = yoga.currentPathPlanets.filter { it in yoga.planets }
                        if (linked.isNotEmpty()) {
                            Text(
                                "ယောဂနှင့် တိုက်ရိုက်ဆက်စပ်သော ဒသာဂြိုဟ်: " +
                                    linked.joinToString("၊ ") { YogaCatalog.planetMm(it) },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    if (yoga.dashaLink == DashaLink.INDIRECT && yoga.dashaLinkDetail.isNotEmpty()) {
                        Text(
                            "ယောဂနှင့် သွယ်ဝိုက်ဆက်စပ်မှု: ${yoga.dashaLinkDetail}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    // Dated 5-level hierarchy: names + start/end dates, so
                    // the reader can verify the path is active on the query
                    // date (activePath is constructed to contain it).
                    if (yoga.activePath.isNotEmpty()) {
                        Text(
                            "လက်ရှိ ဒသာလမ်းကြောင်း:",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        for (per in yoga.activePath) {
                            val levelName = per.level.toIntOrNull()
                                ?.let { DASHA_LEVEL_NAMES.getOrNull(it - 1) } ?: per.level
                            Text(
                                "• $levelName: ${YogaCatalog.planetMm(per.planet)} " +
                                    "(${fmt.format(java.util.Date(per.startEpochMillis))} → " +
                                    fmt.format(java.util.Date(per.endEpochMillis)) + ")",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else if (yoga.currentPathPlanets.isNotEmpty()) {
                        Text(
                            "လက်ရှိ ဒသာလမ်းကြောင်း: " +
                                yoga.currentPathPlanets.joinToString(" / ") { YogaCatalog.planetMm(it) },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    val classified = yoga.dashaPeriods.map {
                        it to YogaDashaLinker.classifyPeriod(
                            it.startEpochMillis, it.endEpochMillis, queryMillis
                        )
                    }
                    val currentPeriod = classified
                        .firstOrNull { it.second == YogaDashaLinker.PeriodState.CURRENT }?.first
                    val currentClipped = currentPeriod?.let {
                        YogaDashaLinker.clipPeriod(it, birthMillis, end100)
                    }
                    val upcoming = classified
                        .filter { it.second == YogaDashaLinker.PeriodState.FUTURE }
                        .mapNotNull { YogaDashaLinker.clipPeriod(it.first, birthMillis, end100) }
                        .minByOrNull { it.startEpochMillis }
                    val past = classified
                        .filter { it.second == YogaDashaLinker.PeriodState.PAST }
                        .mapNotNull { YogaDashaLinker.clipPeriod(it.first, birthMillis, end100) }
                    val full120 = yoga.dashaPeriods
                        .mapNotNull { YogaDashaLinker.clipPeriod(it, birthMillis, end120) }
                    Spacer(Modifier.height(4.dp))
                    Text("လက်ရှိအကျိုးပေးဒသာ", fontWeight = FontWeight.Bold)
                    if (currentClipped != null) {
                        Text(periodLine(currentClipped, true), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text(
                            "လက်ရှိ မဟာဒသာတွင် ဤယောဂ၏ ဂြိုဟ် မပါပါ",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("ရှေ့လာမည့်အကျိုးပေးဒသာ", fontWeight = FontWeight.Bold)
                    if (upcoming != null) {
                        Text(periodLine(upcoming), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text(
                            "အသက် ၁၀၀ အတွင်း ရှေ့လာမည့် မဟာဒသာ မရှိပါ",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    // Historical + 100-120y timelines live in Advanced
                    // View only; the main view shows current + upcoming.
                    if (past.isNotEmpty() || full120.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (showAdvanced) "အဆင့်မြင့် ကြည့်ရှုမှု (Advanced View) ▾"
                            else "အဆင့်မြင့် ကြည့်ရှုမှု (Advanced View) ▸",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { showAdvanced = !showAdvanced }
                        )
                        if (showAdvanced) {
                            if (past.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text("ယခင်ဒသာများ", fontWeight = FontWeight.Bold)
                                for (per in past) {
                                    Text(periodLine(per), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            if (full120.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text("ဒသာအပြည့်အစုံ (အသက် ၁၂၀ အထိ)", fontWeight = FontWeight.Bold)
                                for (per in full120) {
                                    Text(periodLine(per), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
                // §5 တွက်ချက်မှုအသေးစိတ် (hidden by default).
                Spacer(Modifier.height(8.dp))
                Text(
                    if (showDetail) "တွက်ချက်မှုအသေးစိတ် ▾" else "တွက်ချက်မှုအသေးစိတ် ▸",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { showDetail = !showDetail }
                )
                if (showDetail) {
                    Spacer(Modifier.height(8.dp))
                    if (yoga.effect != YogaEffect.NONE && yoga.effectReason.isNotEmpty()) {
                        Text("အခြေအနေ အကြောင်းရင်း", fontWeight = FontWeight.Bold)
                        Text(yoga.effectReason, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(6.dp))
                    }
                    Text("ဖွဲ့စည်းမှု", fontWeight = FontWeight.Bold)
                    for (fact in yoga.formationFacts) {
                        Text("• $fact", style = MaterialTheme.typography.bodyMedium)
                    }
                    // Error Prevention Rule 3: cancellation is its own
                    // section, shown right after formation and before
                    // strength, so it is read before any negative reading.
                    if (isActive) {
                        Spacer(Modifier.height(6.dp))
                        Text("ပျက်ပြယ်ချက်", fontWeight = FontWeight.Bold)
                        if (yoga.cancellationFacts.isNotEmpty()) {
                            for (c in yoga.cancellationFacts) {
                                Text("• $c", style = MaterialTheme.typography.bodyMedium)
                            }
                        } else {
                            Text("ပျက်ပြယ်ချက် မရှိပါ", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (yoga.quality.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text("ဂြိုဟ်အရည်အသွေး", fontWeight = FontWeight.Bold)
                        for ((_, q) in yoga.quality) {
                            val conj = if (q.conjunctPlanets.isNotEmpty()) {
                                "၊ ယှဉ်: " + q.conjunctPlanets.joinToString("၊ ") { YogaCatalog.planetMm(it) }
                            } else {
                                ""
                            }
                            val asp = if (q.aspecting.isNotEmpty()) {
                                "၊ အမြင်: " + q.aspecting.joinToString("၊ ") {
                                    "${YogaCatalog.planetMm(it.first)} (${if (it.second) "ကောင်း" else "ဆိုး"})"
                                }
                            } else {
                                ""
                            }
                            Text(
                                "• ${YogaCatalog.planetMm(q.planet)} — " +
                                    "${YogaCatalog.signMm(q.sign)} " +
                                    "${"%.2f".format(q.longitude)}° " +
                                    "(${YogaDetectionEngine.dignityMm(q.dignity)})၊ " +
                                    "D9 ${YogaCatalog.signMm(q.d9Sign)} " +
                                    "(${YogaDetectionEngine.dignityMm(q.d9Dignity)})၊ " +
                                    "Strength ${q.strengthFinal} မှတ် " +
                                    (if (q.functionalBenefic) "(လုပ်ငန်းကောင်း)" else "(လုပ်ငန်းဆိုး)") +
                                    conj + asp,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    if (rule != null) {
                        Spacer(Modifier.height(6.dp))
                        Text("အရင်းအမြစ်", fontWeight = FontWeight.Bold)
                        Text("Rule: ${rule.id}", style = MaterialTheme.typography.bodySmall)
                        Text(rule.sourceNote, style = MaterialTheme.typography.bodySmall)
                        if (rule.variantNote.isNotEmpty()) {
                            Text(rule.variantNote, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
