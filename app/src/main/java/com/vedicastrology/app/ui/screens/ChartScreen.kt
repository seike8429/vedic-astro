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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vedicastrology.app.engine.BirthData
import com.vedicastrology.app.engine.ChartResult
import com.vedicastrology.app.ui.ChartStyle
import com.vedicastrology.app.ui.charts.DiamondChart
import com.vedicastrology.app.ui.charts.DiamondMode
import com.vedicastrology.app.ui.charts.WheelChart
import com.vedicastrology.app.ui.theme.PlanetNames
import java.util.Calendar
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

/** Whole days from a to b. */
private fun daysBetween(a: BirthData, b: BirthData): Int {
    val ca = Calendar.getInstance().apply { set(a.year, a.month - 1, a.day) }
    val cb = Calendar.getInstance().apply { set(b.year, b.month - 1, b.day) }
    return ((cb.timeInMillis - ca.timeInMillis) / 86400000).toInt()
}

/**
 * Main chart page: style switcher (wheel/diamond), divisional tabs,
 * dasha timeline, Myanmar date, and transit date slider.
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
    onBack: () -> Unit
) {
    val shown = if (showTransit) transit ?: natal else natal
    var diamondMode by remember { mutableStateOf(DiamondMode.D1) }
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
        // Header: back + Myanmar date.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onBack) { Text("←") }
            Text(myanmarDateLine, style = MaterialTheme.typography.bodyMedium)
        }

        // Style switcher.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = style == ChartStyle.WHEEL,
                onClick = { onStyleChange(ChartStyle.WHEEL) },
                label = { Text("စက်ဝိုင်း") }
            )
            FilterChip(
                selected = style == ChartStyle.DIAMOND,
                onClick = { onStyleChange(ChartStyle.DIAMOND) },
                label = { Text("လေးထောင့်") }
            )
        }
        if (style == ChartStyle.DIAMOND) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DiamondMode.entries.forEach { m ->
                    FilterChip(
                        selected = diamondMode == m,
                        onClick = { diamondMode = m },
                        label = { Text(m.name) }
                    )
                }
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
            Text("ရာဟု: ${if (useTrueRahu) "True" else "Mean"}")
            Switch(checked = useTrueRahu, onCheckedChange = onRahuChange)
        }

        // Chart.
        if (style == ChartStyle.WHEEL) {
            WheelChart(planets = shown.planets, ascendant = shown.ascendant)
        } else {
            DiamondChart(planets = shown.planets, mode = diamondMode)
        }

        // Transit date controls.
        if (showTransit) {
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
                        valueRange = -365f..(365f * 30),
                        modifier = Modifier.fillMaxWidth()
                    )
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
                                    PlanetNames.full[d.lord] ?: d.lord,
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
