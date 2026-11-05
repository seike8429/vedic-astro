package com.vedicastrology.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vedicastrology.app.data.BirthDataStore
import com.vedicastrology.app.data.MyanmarCalendar
import com.vedicastrology.app.data.MyanmarDate
import com.vedicastrology.app.data.SavedChart
import com.vedicastrology.app.engine.BirthData
import com.vedicastrology.app.engine.ChartCalculator
import com.vedicastrology.app.engine.ChartResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/** App screens. */
sealed interface Screen {
    data object BirthInput : Screen
    data object Chart : Screen
    data object PlanetTable : Screen
    data object ChartList : Screen
    data object AstroNews : Screen
    data object Strength : Screen
    data object Dasha : Screen
    data object Yoga : Screen
    data object TransitEffect : Screen
    data object RahuKalam : Screen
}

/** Chart style. */
enum class ChartStyle { WHEEL, DIAMOND, EAST_INDIAN }

/**
 * Holds birth data, computed chart, transit date, settings, and the
 * saved-charts list.
 *
 * Launch behavior: the DEFAULT saved chart (or, on first launch, the
 * user's own confirmed chart, seeded as the first saved chart and the
 * default) is computed immediately, so the app opens directly on the
 * chart with no re-entry. The entry screen is only for adding/editing.
 */
class ChartViewModel(application: Application) : AndroidViewModel(application) {

    /** The user's confirmed birth data (his own reference screenshots):
     * 22 Apr 1976, 08:30 AM, +06:30, Yangon 96°11'E, 16°51'N. */
    companion object {
        val USER_DEFAULT_BIRTH = BirthData(
            year = 1976, month = 4, day = 22,
            hour = 8, minute = 30,
            tzOffset = 6.5,
            latitude = 16.85, longitude = 96.1833,
            placeName = "Yangon, Myanmar (Burma)",
            timeInGmt = false
        )
        const val USER_DEFAULT_NAME = "My Chart"
    }

    private val store = BirthDataStore(application)

    var screen: Screen by mutableStateOf(Screen.BirthInput)
        private set

    var birthData: BirthData? by mutableStateOf(null)
        private set

    /** Id of the chart currently shown (null until the first compute). */
    var currentChartId: String? by mutableStateOf(null)
        private set

    /** All saved charts, refreshed after every save/delete/default change. */
    var savedCharts: List<SavedChart> by mutableStateOf(emptyList())
        private set

    /** Id of the default (launch) chart. */
    var defaultChartId: String? by mutableStateOf(null)
        private set

    /** Entry-screen state: chart being edited (null = new chart). */
    var entryEditingId: String? by mutableStateOf(null)
        private set
    var entryInitial: BirthData by mutableStateOf(USER_DEFAULT_BIRTH)
        private set
    var entryInitialName: String by mutableStateOf("")
        private set
    /** Where Cancel on the entry screen returns to. */
    var entryReturnTo: Screen by mutableStateOf(Screen.Chart)
        private set

    var chart: ChartResult? by mutableStateOf(null)
        private set

    var transitChart: ChartResult? by mutableStateOf(null)
        private set

    var showTransit: Boolean by mutableStateOf(false)

    // Only view is the Myanmar layout now; wheel/diamond files stay in
    // the codebase unused, so the style field is retained for a return.
    var style: ChartStyle by mutableStateOf(ChartStyle.EAST_INDIAN)

    var useTrueRahu: Boolean by mutableStateOf(true)

    var isComputing: Boolean by mutableStateOf(false)
        private set

    /** True until the first (launch) computation finishes. */
    var isInitializing: Boolean by mutableStateOf(true)
        private set

    var error: String? by mutableStateOf(null)
        private set

    /** Transit date: auto-set to today on first transit open; user-adjustable via slider/picker. */
    var transitDate: BirthData? by mutableStateOf(null)

    /** True once the user has explicitly picked a transit date (slider/picker). */
    private var transitExplicit = false

    var myanmarDate: MyanmarDate? by mutableStateOf(null)
        private set

    private var firstCompute = true

    init {
        // First launch: seed the user's own chart as the first saved chart
        // and the default, so the app is never chart-less.
        if (store.loadAll().isEmpty()) {
            val seed = SavedChart(
                SavedChart.newId(), USER_DEFAULT_NAME, USER_DEFAULT_BIRTH
            )
            store.saveChart(seed)
            store.setDefault(seed.id)
        }
        refreshSaved()
        val initial = store.getDefault() ?: store.loadAll().first()
        openChart(initial, saveAsDefault = false)
    }

    private fun refreshSaved() {
        savedCharts = store.loadAll()
        defaultChartId = store.loadDefaultId() ?: savedCharts.firstOrNull()?.id
    }

    /** Fallback initial data for the entry screen (same as launch default). */
    fun defaultBirthData(): BirthData = birthData ?: USER_DEFAULT_BIRTH

    /** Open a saved chart (tap on the list). */
    fun openChart(chart: SavedChart, saveAsDefault: Boolean = false) {
        currentChartId = chart.id
        computeChart(chart.birth, saveAsDefault = saveAsDefault)
    }

    /**
     * Save a chart from the entry screen.
     *
     * ADD flow (entry opened from the list, no editing id): ALWAYS
     * creates a NEW entry with a fresh id and returns to the LIST, so
     * the user sees the old and the new chart side by side — adding
     * can never overwrite the previously open chart.
     *
     * EDIT flow (entry opened from the chart screen's back button):
     * updates that chart in place and returns to the chart.
     *
     * A stale editing id (chart deleted meanwhile) degrades to ADD
     * instead of silently overwriting anything.
     *
     * When [setAsDefault] is true, the saved chart also becomes the
     * chart that opens automatically on app launch.
     */
    fun saveAndOpen(
        data: BirthData,
        name: String,
        setAsDefault: Boolean
    ) {
        val editingId = entryEditingId
        val wasAdd = editingId == null ||
            savedCharts.none { it.id == editingId }
        // In the edit branch, wasAdd == false proves editingId != null
        // (smart cast: the first disjunct of wasAdd is false).
        val id = if (wasAdd) SavedChart.newId() else editingId
        val chart = SavedChart(id, name, data)
        store.saveChart(chart)
        if (setAsDefault) store.setDefault(id)
        refreshSaved()
        if (wasAdd) {
            // New entry: land on the list (both charts visible). The
            // new chart is computed in the background without a screen
            // jump and marked as the current row in the list.
            currentChartId = id
            computeChart(chart.birth, goToChart = false)
            screen = Screen.ChartList
        } else {
            openChart(chart)
        }
    }

    /** Delete a saved chart; never leaves the app chart-less. */
    fun deleteChart(id: String) {
        var remaining = store.deleteChart(id)
        if (remaining.isEmpty()) {
            val seed = SavedChart(
                SavedChart.newId(), USER_DEFAULT_NAME, USER_DEFAULT_BIRTH
            )
            store.saveChart(seed)
            store.setDefault(seed.id)
            remaining = store.loadAll()
        }
        refreshSaved()
        if (id == currentChartId) {
            val next = store.getDefault() ?: remaining.first()
            openChart(next)
        }
    }

    /** Make [id] the chart that opens automatically on app launch. */
    fun setDefaultChart(id: String) {
        store.setDefault(id)
        refreshSaved()
    }

    /** Open the entry screen to EDIT the currently shown chart. */
    fun openEntryForCurrent() {
        val id = currentChartId
        val current = savedCharts.firstOrNull { it.id == id }
        entryEditingId = id
        entryInitial = birthData ?: USER_DEFAULT_BIRTH
        entryInitialName = current?.name ?: ""
        entryReturnTo = Screen.Chart
        screen = Screen.BirthInput
    }

    /** Open the entry screen to ADD a new chart (prefilled from default). */
    fun openEntryForNew() {
        entryEditingId = null
        entryInitial = store.getDefault()?.birth ?: USER_DEFAULT_BIRTH
        entryInitialName = ""
        entryReturnTo = Screen.ChartList
        screen = Screen.BirthInput
    }

    /** Cancel on the entry screen: back to where it was opened from. */
    fun cancelEntry() {
        screen = if (chart != null) entryReturnTo else Screen.Chart
    }

    /**
     * Compute the chart for [data]. [saveAsDefault] is unused by the
     * multi-chart flow (saveAndOpen handles persistence) and kept only
     * for the launch path signature compatibility. [goToChart] false
     * computes in the background without navigating (used by the ADD
     * flow, which lands on the chart list instead of the chart).
     */
    fun computeChart(
        data: BirthData,
        saveAsDefault: Boolean = false,
        goToChart: Boolean = true
    ) {
        birthData = data
        isComputing = true
        error = null
        // A new chart resets the transit pick: the next transit open
        // auto-sets to today again.
        transitExplicit = false
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
                // Transit starts at the birth moment; the first transit open
                // auto-sets it to today (see toggleTransit), and an
                // explicit user pick overrides that default.
                transitDate = data
                transitChart = result
                if (goToChart) screen = Screen.Chart
            } catch (t: Throwable) {
                error = t.message ?: "Computation failed"
            } finally {
                isComputing = false
                if (firstCompute) {
                    firstCompute = false
                    isInitializing = false
                }
            }
        }
    }

    /** Recompute the transit chart for a user-picked date/time (natal dasha kept). */
    fun computeTransit(date: BirthData) {
        computeTransitInternal(date, explicit = true)
    }

    private fun computeTransitInternal(date: BirthData, explicit: Boolean) {
        val natal = birthData ?: return
        if (explicit) transitExplicit = true
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

    /**
     * Transit toggle. On first open (no explicit user pick yet for this
     * chart), the transit date auto-sets to TODAY (device-local date,
     * natal time/timezone/place) — recomputed on every fresh open, never
     * a stale persisted date. An explicit pick overrides the default
     * until a new chart is opened.
     */
    fun toggleTransit(on: Boolean) {
        showTransit = on
        if (on && !transitExplicit) {
            val natal = birthData ?: return
            val c = Calendar.getInstance()
            val today = natal.copy(
                year = c.get(Calendar.YEAR),
                month = c.get(Calendar.MONTH) + 1,
                day = c.get(Calendar.DAY_OF_MONTH)
            )
            computeTransitInternal(today, explicit = false)
        }
    }

    /**
     * Compute today's transit chart for the Strength tab's "current sky" view,
     * without touching the Chart screen's transit overlay switch.
     */
    fun ensureTransitToday() {
        val natal = birthData ?: return
        val c = Calendar.getInstance()
        val today = natal.copy(
            year = c.get(Calendar.YEAR),
            month = c.get(Calendar.MONTH) + 1,
            day = c.get(Calendar.DAY_OF_MONTH)
        )
        computeTransitInternal(today, explicit = false)
    }

    /** Change True/Mean Rahu and recompute natal + transit. */
    fun setRahuMode(useTrue: Boolean) {
        if (useTrue == useTrueRahu) return
        useTrueRahu = useTrue
        val natal = birthData ?: return
        computeChart(natal)
    }

    fun goTo(s: Screen) { screen = s }
    fun backToInput() { openEntryForCurrent() }
}
