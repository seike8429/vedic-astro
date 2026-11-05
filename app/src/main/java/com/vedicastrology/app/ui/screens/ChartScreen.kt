package com.vedicastrology.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vedicastrology.app.engine.BirthData
import com.vedicastrology.app.engine.ChartPlanet
import com.vedicastrology.app.engine.ChartResult
import com.vedicastrology.app.engine.SIGNS
import com.vedicastrology.app.engine.formatDms
import com.vedicastrology.app.BuildConfig
import com.vedicastrology.app.data.SavedChart
import com.vedicastrology.app.engine.norm360
import com.vedicastrology.app.engine.dasha.DashaEngine
import com.vedicastrology.app.engine.dasha.DashaYearConvention
import com.vedicastrology.app.ui.ChartStyle
import com.vedicastrology.app.ui.charts.DiamondMode
import com.vedicastrology.app.ui.charts.EastIndianChart
import com.vedicastrology.app.ui.theme.PlanetNames
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

/** Date + N days (time kept). */
private fun BirthData.plusDays(days: Int): BirthData {
    val c = Calendar.getInstance().apply {
        set(year, month - 1, day)
        add(Calendar.DAY_OF_YEAR, days)
    }
    return copy(
        year = c.get(Calendar.YEAR),
        month = c.get(Calendar.MONTH) + 1,
        day = c.get(Calendar.DAY_OF_MONTH)
    )
}

/** Today's date fields on the natal's time/timezone/place (device-local date). */
private fun todayBirthData(natal: BirthData): BirthData {
    val c = Calendar.getInstance()
    return natal.copy(
        year = c.get(Calendar.YEAR),
        month = c.get(Calendar.MONTH) + 1,
        day = c.get(Calendar.DAY_OF_MONTH)
    )
}

/** Whole days from a to b. */
private fun daysBetween(a: BirthData, b: BirthData): Int {
    val ca = Calendar.getInstance().apply { set(a.year, a.month - 1, a.day) }
    val cb = Calendar.getInstance().apply { set(b.year, b.month - 1, b.day) }
    return ((cb.timeInMillis - ca.timeInMillis) / 86400000).toInt()
}

/**
 * Main chart page: Myanmar chart view (the only chart view; wheel and
 * diamond files stay in the codebase unused for a later return),
 * D1/D9/Bhava tabs, dasha timeline, Myanmar date, transit date slider.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartScreen(
    natal: ChartResult,
    natalBirth: BirthData,
    transit: ChartResult?,
    showTransit: Boolean,
    onShowTransitChange: (Boolean) -> Unit,
    style: ChartStyle,
    onStyleChange: (ChartStyle) -> Unit,
    myanmarDateLine: String,
    transitDate: BirthData?,
    onTransitDateChange: (BirthData) -> Unit,
    useTrueRahu: Boolean,
    onRahuChange: (Boolean) -> Unit,
    onOpenTable: () -> Unit,
    onOpenList: () -> Unit,
    onOpenDasha: () -> Unit,
    onBack: () -> Unit,
    savedCharts: List<SavedChart>,
    currentChartId: String?,
    onOpenChart: (SavedChart) -> Unit,
    onAddChart: () -> Unit
) {
    val shown = if (showTransit) transit ?: natal else natal
    var chartMode by remember { mutableStateOf(DiamondMode.D1) }
    var showTransitPicker by remember { mutableStateOf(false) }
    // Slider: absolute day offset from the NATAL date (-1y .. +30y).
    var sliderDays by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Saved charts at the top, labelled ဇာတာရှင်များ: quick-switch chips
        // + a labelled entry for casting a new chart.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("ဇာတာရှင်များ", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = onAddChart) { Text("ဇာတာဖွဲ့ရန်") }
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            savedCharts.forEach { c ->
                FilterChip(
                    selected = c.id == currentChartId,
                    onClick = { if (c.id != currentChartId) onOpenChart(c) },
                    label = { Text(c.name) }
                )
            }
        }
        // App version (from gradle versionName, bumped per delivery) — so an
        // installed build can always be identified without its APK filename.
        Text(
            "Version ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall
        )

        // Header: back + Myanmar date.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onBack) { Text("←") }
            Text(myanmarDateLine, style = MaterialTheme.typography.bodyMedium)
        }

        // Only chart view: မြန်မာ (Myanmar) layout. Wheel/diamond are
        // unwired from the UI (their files stay in place, unused).
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = style == ChartStyle.EAST_INDIAN,
                onClick = { onStyleChange(ChartStyle.EAST_INDIAN) },
                label = { Text("မြန်မာ") }
            )
        }
        // Divisional tabs: always visible (Myanmar view supports D1/D9/Bhava).
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DiamondMode.entries.forEach { m ->
                FilterChip(
                    selected = chartMode == m,
                    onClick = { chartMode = m },
                    label = { Text(m.name) }
                )
            }
        }

        // Natal / transit toggle.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (showTransit) "ဂြိုဟ်သွား (Transit)" else "ဇာတာ (Natal)")
            Switch(checked = showTransit, onCheckedChange = onShowTransitChange)
        }

        // Rahu setting.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Rahu: ${if (useTrueRahu) "True" else "Mean"}")
            Switch(checked = useTrueRahu, onCheckedChange = onRahuChange)
        }

        // Chart: Myanmar layout only, with the same NL/SL side columns
        // + nakshatra strip.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            EastIndianChart(
                planets = shown.planets,
                mode = chartMode,
                modifier = Modifier.weight(1f)
            )
            NlSlSideColumns(planets = shown.planets)
        }
        NakshatraStrip(planets = shown.planets, ayanamsa = shown.ayanamsa)

        // Transit date controls.
        if (showTransit) {
            // Slider range must reach today even for old birth dates:
            // born 1976 -> ~18,400 days ago, beyond the old +30y cap.
            val daysToToday = daysBetween(natalBirth, todayBirthData(natalBirth))
            val sliderMax = maxOf(365 * 30, daysToToday + 365).toFloat()
            // Keep the slider in sync with the transit date. The ViewModel
            // auto-sets it to today on first transit open; the picker sets
            // it explicitly. Dragging never changes transitDate until the
            // drag finishes, so this cannot fight the user's drag.
            LaunchedEffect(transitDate) {
                if (transitDate != null) {
                    sliderDays = daysBetween(natalBirth, transitDate!!).toFloat()
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Transit ရက်")
                        OutlinedButton(onClick = { showTransitPicker = true }) {
                            Text(transitDate?.let { "%04d-%02d-%02d".format(it.year, it.month, it.day) } ?: "ရွေး")
                        }
                    }
                    Text("မွေးနေ့မှ ရက်ပေါင်း: ${sliderDays.roundToInt()}")
                    Slider(
                        value = sliderDays,
                        onValueChange = { sliderDays = it },
                        onValueChangeFinished = {
                            onTransitDateChange(natalBirth.plusDays(sliderDays.roundToInt()))
                        },
                        valueRange = -365f..sliderMax,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Current dasha (natal, 5 levels) — linked with this chart,
        // shown on the first page. Always natal: transit never moves it.
        val dashaTz = remember(natalBirth) { chartTimeZone(natalBirth) }
        val dashaFmt = remember(dashaTz) {
            SimpleDateFormat("dd/MM/yyyy", Locale.US).apply { timeZone = dashaTz }
        }
        val dashaNow = System.currentTimeMillis()
        val dashaPath = remember(natal) {
            val moonLon = natal.planets.firstOrNull { it.row.planet == "Moon" }?.row?.longitude ?: 0.0
            val birthMillis = DashaEngine.julianDayToMillis(natal.julianDay)
            DashaEngine.activePath(moonLon, birthMillis, dashaNow, DashaYearConvention.DEFAULT)
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("လက်ရှိ ဒသာ (အခု ဘယ်အောက်ရောက်နေလဲ)", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                if (dashaPath.isEmpty()) {
                    Text("ဒသာ မတွက်နိုင်သေးပါ", style = MaterialTheme.typography.bodyMedium)
                } else {
                    dashaPath.forEach { p ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                DASHA_LEVEL_NAMES[p.level - 1],
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "${p.planet.mm} (${p.planet.code})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            "  ${dashaFmt.format(java.util.Date(p.startEpochMillis))} → ${dashaFmt.format(java.util.Date(p.endEpochMillis))}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (p.level == 1) {
                            Text(
                                "  ကျန်: ${formatRemaining(p.endEpochMillis - dashaNow, DashaYearConvention.DEFAULT)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "convention: Julian 365.25 ရက် (ဒသာ tab မှာ ပြောင်းနိုင်သည်)",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.height(6.dp))
                Button(onClick = onOpenDasha, modifier = Modifier.fillMaxWidth()) {
                    Text("ဒသာ အပြည့်အစုံ ကြည့်ရန် →")
                }
            }
        }

        // Dasha timeline (natal).
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("ဒသာ (Vimshottari)", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    natal.dashas.forEach { d ->
                        Card {
                            Column(Modifier.padding(8.dp)) {
                                Text(
                                    PlanetNames.code[d.lord] ?: d.lord,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "%d → %d".format(d.startY, d.endY),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }

        Button(onClick = onOpenTable, modifier = Modifier.fillMaxWidth()) {
            Text("ဂြိုဟ်စာရင်း (ဇယား)")
        }

        OutlinedButton(onClick = onOpenList, modifier = Modifier.fillMaxWidth()) {
            Text("Saved charts")
        }
    }

    if (showTransitPicker) {
        val base = transitDate ?: natalBirth
        val cal = Calendar.getInstance().apply {
            set(base.year, base.month - 1, base.day)
        }
        val state = rememberDatePickerState(initialSelectedDateMillis = cal.timeInMillis)
        DatePickerDialog(
            onDismissRequest = { showTransitPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        val c = Calendar.getInstance().apply { timeInMillis = it }
                        val picked = natalBirth.copy(
                            year = c.get(Calendar.YEAR),
                            month = c.get(Calendar.MONTH) + 1,
                            day = c.get(Calendar.DAY_OF_MONTH)
                        )
                        sliderDays = daysBetween(natalBirth, picked).toFloat()
                        onTransitDateChange(picked)
                    }
                    showTransitPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTransitPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state) }
    }
}

/** Planet number for a lord name (author's scheme: Sun ၁ … Ketu ၉, Lagna လ). */
private fun shortLord(english: String): String =
    PlanetNames.num.entries.firstOrNull { (k, _) -> k == english }?.value ?: english.take(2)

/**
 * MOTAA-style compact NL/SL side columns beside the diamond chart.
 * One row per planet of the currently shown chart (natal or transit).
 */
@Composable
private fun NlSlSideColumns(planets: List<ChartPlanet>) {
    Card {
        Column(
            Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row {
                Text("", Modifier.width(34.dp), style = MaterialTheme.typography.bodySmall)
                Text("NL", Modifier.width(26.dp), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                Text("SL", Modifier.width(26.dp), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }
            planets.forEach { cp ->
                val name = (PlanetNames.num[cp.row.planet] ?: cp.row.planet) +
                    if (cp.retrograde) "(R)" else ""
                Row {
                    Text(
                        name,
                        Modifier.width(34.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (cp.row.planet == "Lagna") FontWeight.Bold else null,
                        maxLines = 1
                    )
                    Text(shortLord(cp.row.nl), Modifier.width(26.dp), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    Text(shortLord(cp.row.sl), Modifier.width(26.dp), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
        }
    }
}

/**
 * Tropical (Sayana) degree-in-sign + rashi (Myanmar) for a sidereal
 * longitude, using this chart's own Lahiri ayanamsa (the value SwissEph
 * already applied). Pure display derivation — no engine math, no new
 * constant. Shared with the table screen (same package).
 */
internal fun tropicalDmsAndSign(siderealLon: Double, ayanamsa: Double): Pair<String, String> {
    val t = norm360(siderealLon + ayanamsa)
    val s = (t / 30).toInt()
    return formatDms(t % 30) to SIGNS[s].mm
}

/**
 * Two-system comparison strip under the diamond chart.
 * Per planet, side by side: Nirayana (Sidereal) degree/rashi +
 * nakshatra (Myanmar) + pada + NL + SL, AND Sayana (Tropical)
 * degree/rashi. The diamond chart itself stays sidereal; Sayana is a
 * comparison display alongside, never a replacement.
 * Nakshatra / NL / SL / dasha stay Nirayana only — there are
 * deliberately NO tropical nakshatra/NL/SL columns.
 * Uses the currently shown chart result, so it follows the natal/transit
 * toggle (each result carries its own ayanamsa).
 */
@Composable
private fun NakshatraStrip(planets: List<ChartPlanet>, ayanamsa: Double) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "နိယာယန (Sidereal) နှင့် သာယန (Tropical)",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "နက္ခတ် · NL · SL သည် နိယာယန သာဖြစ်သည်။ သာယနမှာ ဒီဂရီ + ရာသီသာ ပြသည်။",
                style = MaterialTheme.typography.bodySmall
            )
            val scroll = rememberScrollState()
            Row(modifier = Modifier.horizontalScroll(scroll)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // System group header.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StripCell("", 56)
                        StripCell("နိယာယန (Sidereal)", 92 + 72 + 96 + 44 + 40 + 40, bold = true)
                        StripCell("သာယန (Tropical)", 92 + 72, bold = true)
                    }
                    // Column header.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StripCell("ဂြိုဟ်", 56, bold = true)
                        StripCell("ဒီဂရီ", 92, bold = true)
                        StripCell("ရာသီ", 72, bold = true)
                        StripCell("နက္ခတ်", 96, bold = true)
                        StripCell("Pada", 44, bold = true)
                        StripCell("NL", 40, bold = true)
                        StripCell("SL", 40, bold = true)
                        StripCell("ဒီဂရီ", 92, bold = true)
                        StripCell("ရာသီ", 72, bold = true)
                    }
                    planets.forEach { cp ->
                        val r = cp.row
                        val name = (PlanetNames.num[r.planet] ?: r.planet) +
                            if (cp.retrograde) "(R)" else ""
                        val (tDms, tSignMm) = tropicalDmsAndSign(r.longitude, ayanamsa)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StripCell(name, 56, bold = r.planet == "Lagna")
                            StripCell(r.dms, 92)
                            StripCell(r.signMm, 72)
                            StripCell(r.nakshatraMm, 96)
                            StripCell(r.pada.toString(), 44)
                            StripCell(shortLord(r.nl), 40)
                            StripCell(shortLord(r.sl), 40)
                            StripCell(tDms, 92)
                            StripCell(tSignMm, 72)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StripCell(text: String, widthDp: Int, bold: Boolean = false) {
    Text(
        text,
        modifier = Modifier.width(widthDp.dp).padding(end = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (bold) FontWeight.Bold else null,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}
