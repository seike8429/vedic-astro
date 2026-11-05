package com.vedicastrology.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vedicastrology.app.engine.ChartResult
import com.vedicastrology.app.engine.StrengthCalculator
import com.vedicastrology.app.engine.yoga.YogaCatalog
import kotlin.math.abs
import kotlin.math.max

/**
 * Strength tab — SUMMARY FIRST (per Dr Myo's direction):
 * planet-strength graph + house-strength graph on top, factor detail below.
 *
 * Computation: MOTAA Chapter 7 (StrengthCalculator). The chart shown is the
 * currently open natal chart; transit is not applied here.
 */
@Composable
fun StrengthScreen(
    natal: ChartResult,
    chartName: String,
    transit: ChartResult?,
    transitDateLabel: String,
    onRequestTransit: () -> Unit
) {
    var transitMode by remember { mutableStateOf(false) }
    val active = if (transitMode && transit != null) transit else natal
    val result = remember(active) {
        val lons = mutableMapOf<String, Double>()
        val navamsa = mutableMapOf<String, String>()
        for (cp in active.planets) {
            val name = cp.row.planet
            if (name == "Lagna") continue
            lons[name] = cp.row.longitude
            navamsa[name] = cp.row.navamsa
        }
        val res = StrengthCalculator.compute(lons, active.ascendant)
        Triple(res, navamsa, lons)
    }
    val strength = result.first
    val navamsa = result.second
    val lons = result.third

    val planetCodes = mapOf(
        "Sun" to "Su", "Moon" to "Mo", "Mars" to "Ma", "Mercury" to "Me",
        "Jupiter" to "Ju", "Venus" to "Ve", "Saturn" to "Sa",
        "Rahu" to "Ra", "Ketu" to "Ke"
    )
    val planetMm = mapOf(
        "Sun" to "တနင်္ဂနွေ", "Moon" to "တနင်္လာ", "Mars" to "အင်္ဂါ",
        "Mercury" to "ဗုဒ္ဓဟူး", "Jupiter" to "ကြာသပတေး", "Venus" to "သောကြာ",
        "Saturn" to "စနေ", "Rahu" to "ရာဟု", "Ketu" to "ကိတ်"
    )

    LazyColumn(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        item {
            Text(
                "ဂြိုလ်အား နှင့် ဘာဝအား",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                if (transitMode) {
                    if (transitDateLabel.isNotEmpty()) "လက်ရှိ ကောင်းကင် — $transitDateLabel"
                    else "လက်ရှိ ကောင်းကင်"
                } else if (chartName.isNotEmpty()) "ဇာတာ — $chartName" else "ဇာတာ",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(4.dp))
            // MOTAA Ch.7 finals are SIGNED scores, not percentages: a value
            // like -278 is valid (book example: Mars +241). The "%" labels
            // previously shown here were misleading and have been removed.
            Text(
                "ဂြိုလ်အား (MOTAA Ch.7) သည် လက္ခဏာပါ ကိန်း (signed score) ဖြစ်သည် — " +
                    "အနှုတ် = အားနည်း၊ အပေါင်း = အားကောင်း၊ −300 မှ +300 ခန့် ရှိနိုင်သည်။ % မဟုတ်ပါ။",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!transitMode) {
                    Button(onClick = { transitMode = false }) { Text("မွေးဇာတာ") }
                    OutlinedButton(onClick = {
                        onRequestTransit()
                        transitMode = true
                    }) { Text("လက်ရှိ ကောင်းကင်") }
                } else {
                    OutlinedButton(onClick = { transitMode = false }) { Text("မွေးဇာတာ") }
                    Button(onClick = {
                        onRequestTransit()
                        transitMode = true
                    }) { Text("လက်ရှိ ကောင်းကင်") }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        // ---- Summary 1: planet graph ----
        item {
            Text("ဂြိုလ်အား (Planet Strength)", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    val bars = strength.planets.map { p ->
                        BarData(
                            label = planetCodes[p.planet] ?: p.planet,
                            final = p.final.toFloat(),
                            benefic = (strength.planetBenefic[p.planet] ?: 0).toFloat(),
                            malefic = (strength.planetMalefic[p.planet] ?: 0).toFloat()
                        )
                    }
                    StackedBarChart(bars)
                    Spacer(Modifier.height(8.dp))
                    LegendRow()
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        // ---- Summary 2: house graph ----
        item {
            Text("ဘာဝအား (House Strength)", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    val bars = strength.houseGraph.map { h ->
                        BarData(
                            label = "H${h.house}",
                            final = h.strength.toFloat(),
                            benefic = h.benefic.toFloat(),
                            malefic = h.malefic.toFloat()
                        )
                    }
                    StackedBarChart(bars)
                    Spacer(Modifier.height(8.dp))
                    LegendRow()
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "ဘာဝအား = ထိုအိမ်၏ မူလတြိကောဏသခင်၏ final အား (မူလသခင်မရှိသောအိမ်က 0)",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        // ---- Detail: factor breakdown per planet (secondary) ----
        item {
            Text(
                "အသေးစိတ် — အချက် (၁) မှ (၆)",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(6.dp))
        }
        items(strength.planets.size) { idx ->
            val p = strength.planets[idx]
            var expanded by remember { mutableStateOf(false) }
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    .clickable { expanded = !expanded }
            ) {
                Column(Modifier.padding(12.dp)) {
                    // Score measured first, locked to one line: on narrow
                    // screens / large system fonts (e.g. his Vivo) the long
                    // D1+D9 header used to squeeze the score until it
                    // wrapped and clipped at the right edge. Now the planet
                    // line wraps and the score never does.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${planetMm[p.planet] ?: p.planet} (${planetCodes[p.planet]})" +
                                " · D1 ${YogaCatalog.signOf(lons[p.planet] ?: 0.0)}" +
                                " · D9 ${navamsa[p.planet] ?: ""}",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "အား ${p.final} မှတ်",
                            maxLines = 1,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    if (expanded) {
                        Spacer(Modifier.height(6.dp))
                        FactorRow("(၁) ရာသီ + နဝင်္ဂ", p.f1)
                        FactorRow("(၂) ဘာဝ", p.f2)
                        FactorRow("(၃) လောင်ကျွမ်း", p.f3)
                        FactorRow("(၄) မူလအိမ်", p.f4)
                        FactorRow("(၅) တည်ရာအိမ်", p.f5)
                        FactorRow("(၁–၅) စုစုပေါင်း", p.total15)
                        FactorRow("(၆) ပိုင်ရှင်", p.f6)
                        FactorRow(
                            "ကောင်း / ဆိုး လွှမ်းမိုးမှု",
                            null,
                            extra = "${strength.planetBenefic[p.planet] ?: 0} / " +
                                "${strength.planetMalefic[p.planet] ?: 0} မှတ်"
                        )
                    } else {
                        Text(
                            "အသေးစိတ်ကြည့်ရန် နှိပ်ပါ",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
        // ---- Detail: house table ----
        item {
            Spacer(Modifier.height(8.dp))
            Text("ဘာဝအသေးစိတ်", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    for (h in strength.houseGraph) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("H${h.house}", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "အား ${h.strength} · ကောင်း ${h.benefic} · ဆိုး ${h.malefic} မှတ်",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private data class BarData(
    val label: String,
    val final: Float,
    val benefic: Float,
    val malefic: Float
)

private val FinalColor = Color(0xFF333333)
private val BeneficColor = Color(0xFF2E7D32)
private val MaleficColor = Color(0xFFC62828)
private val GridColor = Color(0xFFCCCCCC)

/** Stacked bars: positives stack up (final dark + benefic green), negatives down.
 *  A labeled score axis sits in a left gutter so each bar can be read against the
 *  scale (the label under a bar is its Final only; green/red are influences). */
@Composable
private fun StackedBarChart(bars: List<BarData>) {
    // Scale always reaches at least one +/-100 gridline, so both sides of
    // the axis carry labels even when the data max is below 100 (e.g. +75).
    // Gridline units are score points, NOT percents.
    val maxPos = bars.maxOf { max(it.final, 0f) + it.benefic }.coerceAtLeast(100f)
    val maxNeg = bars.maxOf { -(minOf(it.final, 0f) + it.malefic) }.coerceAtLeast(100f)
    val total = maxPos + maxNeg
    val chartH = 200.dp
    // 6dp inset top/bottom so the outermost gridlines sit inside the chart,
    // not on its edge (a +100 line at y=0 is invisible).
    val insetDp = 6f
    val plotHDp = 200f - 2f * insetDp
    val unitDp = plotHDp / total
    val zeroYDp = insetDp + plotHDp * maxPos / total
    val axisW = 38.dp
    Box(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(modifier = Modifier.fillMaxWidth()) {
                // Score axis gutter: 0 + every 100-point gridline, aligned
                // with the Canvas. Labels are score points, not percents.
                Box(modifier = Modifier.width(axisW).height(chartH)) {
                    val axisLabels = mutableListOf<Pair<String, Float>>()
                    axisLabels.add("0" to zeroYDp)
                    var g = 100f
                    while (g <= maxPos) {
                        axisLabels.add("+${g.toInt()}" to (zeroYDp - g * unitDp))
                        g += 100f
                    }
                    g = 100f
                    while (g <= maxNeg) {
                        axisLabels.add("-${g.toInt()}" to (zeroYDp + g * unitDp))
                        g += 100f
                    }
                    for ((text, yDp) in axisLabels) {
                        val ly = (yDp - 6f).coerceIn(0f, 187f)
                        Text(
                            text,
                            fontSize = 9.sp,
                            color = Color(0xFF666666),
                            modifier = Modifier.align(Alignment.TopStart).offset(y = ly.dp)
                        )
                    }
                }
                Canvas(modifier = Modifier.weight(1f).height(chartH)) {
                    val w = size.width
                    val h = size.height
                    val insetPx = 6.dp.toPx()
                    val plotH = h - 2f * insetPx
                    val zeroY = insetPx + plotH * (maxPos / total)
                    val unit = plotH / total
                    val n = bars.size
                    val slot = w / n
                    val barW = slot * 0.55f
                    // gridlines every 100 points, including the outermost (<=, not <)
                    var g = 100f
                    while (g <= maxPos) {
                        val y = zeroY - g * unit
                        drawLine(GridColor, Offset(0f, y), Offset(w, y))
                        g += 100f
                    }
                    g = 100f
                    while (g <= maxNeg) {
                        val y = zeroY + g * unit
                        drawLine(GridColor, Offset(0f, y), Offset(w, y))
                        g += 100f
                    }
                    // zero line: bold black (its "0" label lives in the axis gutter)
                    drawLine(Color.Black, Offset(0f, zeroY), Offset(w, zeroY), strokeWidth = 2.5f)
                    for ((i, b) in bars.withIndex()) {
                        val cx = slot * i + slot / 2
                        val left = cx - barW / 2
                        // positive stack
                        var yTop = zeroY
                        if (b.final > 0) {
                            val hh = b.final * unit
                            drawRect(FinalColor, Offset(left, yTop - hh), androidx.compose.ui.geometry.Size(barW, hh))
                            yTop -= hh
                        }
                        if (b.benefic > 0) {
                            val hh = b.benefic * unit
                            drawRect(BeneficColor, Offset(left, yTop - hh), androidx.compose.ui.geometry.Size(barW, hh))
                        }
                        // negative stack
                        var yBot = zeroY
                        if (b.final < 0) {
                            val hh = -b.final * unit
                            drawRect(FinalColor, Offset(left, yBot), androidx.compose.ui.geometry.Size(barW, hh))
                            yBot += hh
                        }
                        if (b.malefic < 0) {
                            val hh = -b.malefic * unit
                            drawRect(MaleficColor, Offset(left, yBot), androidx.compose.ui.geometry.Size(barW, hh))
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.width(axisW))
                Row(modifier = Modifier.weight(1f)) {
                    for (b in bars) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(b.label, fontSize = 11.sp, textAlign = TextAlign.Center)
                            Text(
                                "${b.final.toInt()}",
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendRow() {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LegendDot(FinalColor, "Final")
        LegendDot(BeneficColor, "ကောင်း")
        LegendDot(MaleficColor, "ဆိုး")
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Canvas(modifier = Modifier.size(10.dp)) {
            drawRect(color)
        }
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun FactorRow(label: String, value: Double?, extra: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            extra ?: String.format("%.1f", value ?: 0.0),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
