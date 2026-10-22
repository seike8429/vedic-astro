package com.vedicastrology.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vedicastrology.app.data.MyanmarCalendar
import com.vedicastrology.app.ui.ChartViewModel
import com.vedicastrology.app.ui.Screen
import com.vedicastrology.app.ui.screens.BirthInputScreen
import com.vedicastrology.app.ui.screens.ChartScreen
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
    when (val s = vm.screen) {
        is Screen.BirthInput -> BirthInputScreen(
            initial = vm.birthData ?: vm.defaultBirthData(),
            onCompute = { vm.computeChart(it) },
            isComputing = vm.isComputing
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
                    onShowTransitChange = { vm.showTransit = it },
                    style = vm.style,
                    onStyleChange = { vm.style = it },
                    myanmarDateLine = vm.myanmarDate?.let { MyanmarCalendar.format(it) } ?: "",
                    transitDate = vm.transitDate,
                    onTransitDateChange = { vm.computeTransit(it) },
                    useTrueRahu = vm.useTrueRahu,
                    onRahuChange = { vm.setRahuMode(it) },
                    onOpenTable = { vm.goTo(Screen.PlanetTable) },
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
                    onBack = { vm.goTo(Screen.Chart) }
                )
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
