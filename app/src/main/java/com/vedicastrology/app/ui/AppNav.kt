package com.vedicastrology.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedicastrology.app.data.CITY_PRESETS
import com.vedicastrology.app.data.MyanmarCalendar
import com.vedicastrology.app.data.MyanmarDate
import com.vedicastrology.app.engine.BirthData
import com.vedicastrology.app.engine.ChartCalculator
import com.vedicastrology.app.engine.ChartResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** App screens. */
sealed interface Screen {
    data object BirthInput : Screen
    data object Chart : Screen
    data object PlanetTable : Screen
}

/** Chart style. */
enum class ChartStyle { WHEEL, DIAMOND }

/** Holds birth data, computed chart, transit date and settings. */
class ChartViewModel : ViewModel() {

    var screen: Screen by mutableStateOf(Screen.BirthInput)
        private set

    var birthData: BirthData? by mutableStateOf(null)
        private set

    var chart: ChartResult? by mutableStateOf(null)
        private set

    var transitChart: ChartResult? by mutableStateOf(null)
        private set

    var showTransit: Boolean by mutableStateOf(false)

    var style: ChartStyle by mutableStateOf(ChartStyle.WHEEL)

    var useTrueRahu: Boolean by mutableStateOf(true)

    var isComputing: Boolean by mutableStateOf(false)
        private set

    var error: String? by mutableStateOf(null)
        private set

    /** Transit date: defaults to today; user-adjustable via slider/picker. */
    var transitDate: BirthData? by mutableStateOf(null)

    var myanmarDate: MyanmarDate? by mutableStateOf(null)
        private set

    fun defaultBirthData(): BirthData {
        val yangon = CITY_PRESETS[0]
        return BirthData(1995, 6, 4, 12, 0, 6.5, yangon.lat, yangon.lon, yangon.name)
    }

    fun computeChart(data: BirthData) {
        birthData = data
        isComputing = true
        error = null
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val result = ChartCalculator.compute(data, useTrueRahu = useTrueRahu)
                val sunLon = result.planets.first { it.row.planet == "Sun" }.row.longitude
                val moonLon = result.planets.first { it.row.planet == "Moon" }.row.longitude
                val md = MyanmarCalendar.fromWestern(
                    data.year, data.month, data.day, sunLon, moonLon
                )
                chart = result
                myanmarDate = md
                // Default transit = birth moment; user can move the slider.
                transitDate = data
                transitChart = result
                screen = Screen.Chart
            } catch (t: Throwable) {
                error = t.message ?: "Computation failed"
            } finally {
                isComputing = false
            }
        }
    }

    /** Recompute the transit chart for a different date/time (natal dasha kept). */
    fun computeTransit(date: BirthData) {
        val natal = birthData ?: return
        transitDate = date
        isComputing = true
        viewModelScope.launch(Dispatchers.Default) {
            try {
                transitChart = ChartCalculator.compute(date, natal, useTrueRahu)
            } catch (t: Throwable) {
                error = t.message ?: "Transit computation failed"
            } finally {
                isComputing = false
            }
        }
    }

    /** Change True/Mean Rahu and recompute natal + transit. */
    fun setRahuMode(useTrue: Boolean) {
        if (useTrue == useTrueRahu) return
        useTrueRahu = useTrue
        val natal = birthData ?: return
        computeChart(natal)
    }

    fun goTo(s: Screen) { screen = s }
    fun backToInput() { screen = Screen.BirthInput }
}
