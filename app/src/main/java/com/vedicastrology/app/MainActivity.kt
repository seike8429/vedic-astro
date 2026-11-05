package com.vedicastrology.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vedicastrology.app.data.MyanmarCalendar
import com.vedicastrology.app.ui.ChartViewModel
import com.vedicastrology.app.ui.Screen
import com.vedicastrology.app.ui.screens.BirthInputScreen
import com.vedicastrology.app.ui.screens.ChartListScreen
import com.vedicastrology.app.ui.screens.AstroNewsScreen
import com.vedicastrology.app.ui.screens.ChartScreen
import com.vedicastrology.app.ui.screens.StrengthScreen
import com.vedicastrology.app.ui.screens.DashaScreen
import com.vedicastrology.app.ui.screens.YogaScreen
import com.vedicastrology.app.ui.screens.TransitEffectScreen
import com.vedicastrology.app.ui.screens.RahuKalamScreen
import com.vedicastrology.app.ui.screens.PlanetTableScreen
import com.vedicastrology.app.ui.theme.VedicAstroTheme

/**
 * Vedic Astrology (Burmese) — Phase 2.
 * Two chart styles (wheel + North Indian diamond), planetary table,
 * Vimshottari dasha, transit slider, Myanmar calendar date.
 */
class MainActivity : ComponentActivity() {

    private val vm: ChartViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VedicAstroTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { inner ->
                    Box(Modifier.padding(inner).fillMaxSize()) {
                        AppContent(vm)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppContent(vm: ChartViewModel) {
    // Launch: the saved (or default) chart is computing — show a spinner,
    // never the empty entry form, so the app opens directly on the chart.
    if (vm.isInitializing) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val showTabs = !vm.isInitializing && vm.screen !is Screen.BirthInput
    Column(Modifier.fillMaxSize()) {
        if (showTabs) {
            // Two-line tab bar: 4 main tabs on line 1, Yoga + Transit effect
            // on line 2, so every tab is visible without horizontal
            // scrolling. Custom items (not TabRow) so that no line-1 tab
            // shows selected while a line-2 tab is active.
            val isChart = vm.screen !is Screen.AstroNews && vm.screen !is Screen.Strength &&
                vm.screen !is Screen.Dasha && vm.screen !is Screen.Yoga &&
                vm.screen !is Screen.TransitEffect && vm.screen !is Screen.RahuKalam
            Row(Modifier.fillMaxWidth()) {
                TabItem("ဇာတာ", isChart, Modifier.weight(1f)) { vm.goTo(Screen.Chart) }
                TabItem("Astro News", vm.screen is Screen.AstroNews, Modifier.weight(1f)) { vm.goTo(Screen.AstroNews) }
                TabItem("ဂြိုလ်အား", vm.screen is Screen.Strength, Modifier.weight(1f)) { vm.goTo(Screen.Strength) }
                TabItem("ဒသာ", vm.screen is Screen.Dasha, Modifier.weight(1f)) { vm.goTo(Screen.Dasha) }
            }
            // Line 2 must use weight like line 1: TabItem's indicator Box
            // is fillMaxWidth, so a text-width item grabs the whole Row and
            // pushes the second tab off-screen (that was the v1.8.12 bug —
            // Yoga looked centered and Transit effect was invisible).
            Row(Modifier.fillMaxWidth()) {
                TabItem("ယောဂ", vm.screen is Screen.Yoga, Modifier.weight(1f)) { vm.goTo(Screen.Yoga) }
                TabItem("Transit effect", vm.screen is Screen.TransitEffect, Modifier.weight(1f)) { vm.goTo(Screen.TransitEffect) }
                TabItem("ရာဟုကာလ", vm.screen is Screen.RahuKalam, Modifier.weight(1f)) { vm.goTo(Screen.RahuKalam) }
            }
            HorizontalDivider()
        }
        when (val s = vm.screen) {
        is Screen.BirthInput -> BirthInputScreen(
            initial = vm.entryInitial,
            initialName = vm.entryInitialName,
            onCompute = { data, name, setDefault ->
                vm.saveAndOpen(data, name, setDefault)
            },
            onCancel = { vm.cancelEntry() },
            isComputing = vm.isComputing,
            isEditing = vm.entryEditingId != null,
            editingName = vm.entryInitialName
        )

        is Screen.ChartList -> ChartListScreen(
            charts = vm.savedCharts,
            defaultId = vm.defaultChartId,
            currentId = vm.currentChartId,
            onOpen = { vm.openChart(it) },
            onSetDefault = { vm.setDefaultChart(it) },
            onDelete = { vm.deleteChart(it) },
            onAdd = { vm.openEntryForNew() },
            onBack = { if (vm.chart != null) vm.goTo(Screen.Chart) }
        )

        is Screen.Chart -> {
            val chart = vm.chart
            val birth = vm.birthData
            if (chart == null || birth == null) {
                LoadingOrError(vm)
            } else {
                ChartScreen(
                    natal = chart,
                    natalBirth = birth,
                    transit = vm.transitChart,
                    showTransit = vm.showTransit,
                    onShowTransitChange = { vm.toggleTransit(it) },
                    style = vm.style,
                    onStyleChange = { vm.style = it },
                    myanmarDateLine = vm.myanmarDate?.let { MyanmarCalendar.format(it) } ?: "",
                    transitDate = vm.transitDate,
                    onTransitDateChange = { vm.computeTransit(it) },
                    useTrueRahu = vm.useTrueRahu,
                    onRahuChange = { vm.setRahuMode(it) },
                    onOpenTable = { vm.goTo(Screen.PlanetTable) },
                    onOpenList = { vm.goTo(Screen.ChartList) },
                    onOpenDasha = { vm.goTo(Screen.Dasha) },
                    savedCharts = vm.savedCharts,
                    currentChartId = vm.currentChartId,
                    onOpenChart = { vm.openChart(it) },
                    onAddChart = { vm.openEntryForNew() },
                    onBack = { vm.backToInput() }
                )
            }
        }

        is Screen.PlanetTable -> {
            val chart = vm.chart
            if (chart == null) {
                LoadingOrError(vm)
            } else {
                PlanetTableScreen(
                    planets = (if (vm.showTransit) vm.transitChart else null)?.planets
                        ?: chart.planets,
                    ayanamsa = (if (vm.showTransit) vm.transitChart else null)?.ayanamsa
                        ?: chart.ayanamsa,
                    onBack = { vm.goTo(Screen.Chart) }
                )
            }
        }
        is Screen.AstroNews -> AstroNewsScreen()
        is Screen.Strength -> {
            val chart = vm.chart
            if (chart == null) {
                LoadingOrError(vm)
            } else {
                StrengthScreen(
                    natal = chart,
                    chartName = vm.savedCharts.firstOrNull { it.id == vm.currentChartId }?.name ?: "",
                    transit = vm.transitChart,
                    transitDateLabel = vm.transitDate?.let { "${it.day}/${it.month}/${it.year}" } ?: "",
                    onRequestTransit = { vm.ensureTransitToday() }
                )
            }
        }
        is Screen.Dasha -> {
            val chart = vm.chart
            val birth = vm.birthData
            if (chart == null || birth == null) {
                LoadingOrError(vm)
            } else {
                DashaScreen(
                    natal = chart,
                    birth = birth,
                    chartName = vm.savedCharts.firstOrNull { it.id == vm.currentChartId }?.name ?: ""
                )
            }
        }
        is Screen.Yoga -> {
            val chart = vm.chart
            val birth = vm.birthData
            if (chart == null || birth == null) {
                LoadingOrError(vm)
            } else {
                YogaScreen(
                    natal = chart,
                    birth = birth,
                    chartName = vm.savedCharts.firstOrNull { it.id == vm.currentChartId }?.name ?: ""
                )
            }
        }
        is Screen.TransitEffect -> {
            if (vm.chart == null) {
                LoadingOrError(vm)
            } else {
                TransitEffectScreen(
                    transit = vm.transitChart,
                    transitDateLabel = vm.transitDate?.let { "${it.day}/${it.month}/${it.year}" } ?: "",
                    onRequestTransit = { vm.ensureTransitToday() }
                )
            }
        }
        is Screen.RahuKalam -> {
            val chart = vm.chart
            val birth = vm.birthData
            if (chart == null || birth == null) {
                LoadingOrError(vm)
            } else {
                RahuKalamScreen(
                    natal = chart,
                    birth = birth,
                    chartName = vm.savedCharts.firstOrNull { it.id == vm.currentChartId }?.name ?: "",
                    useTrueRahu = vm.useTrueRahu
                )
            }
        }
    }
    }
}

@Composable
private fun LoadingOrError(vm: ChartViewModel) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (vm.isComputing) {
            CircularProgressIndicator()
        } else {
            Text(
                vm.error ?: "ဒေတာ မရှိသေးပါ",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(24.dp)
            )
        }
    }
}

/**
 * One tab-bar item: centered label with a 2dp indicator bar underneath
 * (primary color when selected, transparent otherwise), mimicking TabRow.
 * On the Yoga line the item is text-width, so the bar sits under the word
 * only; on line 1 each item fills a quarter of the width.
 */
@Composable
private fun TabItem(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text,
            modifier = Modifier.padding(vertical = 12.dp),
            color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleSmall
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary
                    else Color.Transparent
                )
        )
    }
}
