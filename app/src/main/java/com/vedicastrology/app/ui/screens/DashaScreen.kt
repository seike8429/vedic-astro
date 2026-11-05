package com.vedicastrology.app.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vedicastrology.app.engine.BirthData
import com.vedicastrology.app.engine.ChartResult
import com.vedicastrology.app.engine.dasha.DashaEngine
import com.vedicastrology.app.engine.dasha.DashaPeriod
import com.vedicastrology.app.engine.dasha.DashaYearConvention
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.floor

internal val DASHA_LEVEL_NAMES = listOf(
    "မဟာဒသာ", "အန္တရဒသာ", "ပြတန္တရဒသာ", "သုက္ခမဒသာ", "ပရာဏဒသာ"
)

/**
 * Dasha tab — Vimshottari, five levels (spec sections 8 + 10).
 *
 * Separate module from the chart screen's existing Mahadasha list:
 * that list (VedicEngine.vimshottari) is untouched. This tab uses the
 * new DashaEngine (instant precision, half-open intervals, selectable
 * year convention) on the CURRENT natal chart only; transit is not
 * applied here. Dasha timing only — no Strength percentages here.
 */
@Composable
fun DashaScreen(
    natal: ChartResult,
    birth: BirthData,
    chartName: String
) {
    val moonRow = natal.planets.firstOrNull { it.row.planet == "Moon" }?.row
    val moonLon = moonRow?.longitude ?: 0.0
    val birthMillis = DashaEngine.julianDayToMillis(natal.julianDay)

    // Display timezone = the chart's own timezone (GMT mode -> GMT).
    val tz = remember(birth) { chartTimeZone(birth) }
    val dateFmt = remember(tz) {
        SimpleDateFormat("dd/MM/yyyy", Locale.US).apply { timeZone = tz }
    }
    fun fmt(millis: Long): String = dateFmt.format(java.util.Date(millis))

    var convention by remember { mutableStateOf(DashaYearConvention.DEFAULT) }

    // Display age range (POLICY, not calculation): default birth..age-100,
    // opt-in birth..age-120. The underlying timeline is never changed.
    var showTo120 by remember { mutableStateOf(false) }

    // Query date: today in the chart timezone by default.
    val todayCal = remember(tz) { Calendar.getInstance(tz) }
    var queryY by remember { mutableStateOf(todayCal.get(Calendar.YEAR)) }
    var queryM by remember { mutableStateOf(todayCal.get(Calendar.MONTH) + 1) }
    var queryD by remember { mutableStateOf(todayCal.get(Calendar.DAY_OF_MONTH)) }
    val queryMillis = remember(queryY, queryM, queryD, tz) {
        Calendar.getInstance(tz).apply {
            set(Calendar.YEAR, queryY)
            set(Calendar.MONTH, queryM - 1)
            set(Calendar.DAY_OF_MONTH, queryD)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val balance = remember(moonLon, birthMillis, convention) {
        DashaEngine.birthBalance(moonLon, birthMillis, convention)
    }
    val path = remember(moonLon, birthMillis, queryMillis, convention) {
        DashaEngine.activePath(moonLon, birthMillis, queryMillis, convention)
    }
    val mdList = remember(balance, convention, queryMillis, birthMillis) {
        val until = maxOf(
            queryMillis,
            birthMillis + (120 * convention.millisPerYear()).toLong()
        )
        DashaEngine.mahadashas(balance, convention, until)
    }
    val activeKeys = remember(path) {
        path.map { "${it.level}:${it.startEpochMillis}:${it.planet}" }.toSet()
    }

    // Display range end: birth + 100y (default) or 120y (opt-in). If the
    // query date lies beyond it, extend to the query so the queried period
    // still shows. Top-level MDs outside [birth, rangeEnd] are dropped;
    // shown rows are clipped (see DashaNode) — never recalculated.
    val rangeEnd = remember(birthMillis, convention, queryMillis, showTo120) {
        maxOf(
            birthMillis + ((if (showTo120) 120.0 else 100.0) * convention.millisPerYear()).toLong(),
            queryMillis
        )
    }
    val displayMdList = remember(mdList, birthMillis, rangeEnd) {
        mdList.filter { it.endEpochMillis > birthMillis && it.startEpochMillis < rangeEnd }
    }

    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                "ဒသာ (Vimshottari) — $chartName",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Ayanamsa: Lahiri ${"%.2f".format(natal.ayanamsa)}° · " +
                    "လ ${moonRow?.nakshatraMm ?: ""} ပဒ ${moonRow?.pada ?: ""}",
                style = MaterialTheme.typography.bodySmall
            )
        }

        // ---- Convention selector ----
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("နှစ်-တွက်ပုံ (Year convention)", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    DashaYearConvention.entries.forEach { conv ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { convention = conv }
                                .padding(vertical = 3.dp)
                        ) {
                            Text(
                                if (conv == convention) "◉ " else "○ ",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(conv.label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "မူရင်း (default) = Julian 365.25 ရက် — ဇာတာစာမျက်နှာက " +
                            "Mahadasha စာရင်းနဲ့ convention တူအောင်ပါ။",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // ---- Display age range (policy, not calculation) ----
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("ပြသမည့်အသက် အပိုင်းအခြား", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showTo120 = false }
                            .padding(vertical = 3.dp)
                    ) {
                        Text(
                            if (!showTo120) "◉ " else "○ ",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text("အသက် ၁၀၀ အထိ (ပုံမှန်)", style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showTo120 = true }
                            .padding(vertical = 3.dp)
                    ) {
                        Text(
                            if (showTo120) "◉ " else "○ ",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text("အသက် ၁၂၀ အထိ (စက်ဝန်းအပြည့်)", style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "တွက်ချက်မှုကို မပြောင်းပါ — ပြသမှုကိုသာ ဖြတ်ပါသည်။ " +
                            "အပိုင်းခြားကို လွန်နေသော ကာလကို ဖြတ်တောက်ပြီး ပြသည်။",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // ---- Birth balance ----
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("မွေးချိန် ဒသာ (Birth balance)", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text("မဟာဒသာ: ${balance.planet.mm} (${balance.planet.code}) — ${"%.2f".format(balance.planet.years.toDouble())} နှစ်")
                    Text("စတင်: ${fmt(balance.mahadashaStartMillis)} (မွေးချိန်မတိုင်မီ ${"%.2f".format(balance.elapsedYears)} နှစ်)")
                    Text("မွေးချိန်တွင် ကျန်: ${"%.2f".format(balance.remainingYears)} နှစ်")
                    Text(
                        "မွေးချိန် Antardasha သည် ပထမတစ်ခုမဟုတ်နိုင်ပါ — " +
                            "ဒသာအစကနေ လျှောက်တွက်ထားတာပါ။",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // ---- Date selector + active path ----
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("ကြည့်မည့်ရက် (Query date)", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    queryY = y; queryM = m + 1; queryD = d
                                },
                                queryY, queryM - 1, queryD
                            ).show()
                        }) {
                            Text("%02d/%02d/%d".format(queryD, queryM, queryY))
                        }
                        Button(onClick = {
                            val c = Calendar.getInstance(tz)
                            queryY = c.get(Calendar.YEAR)
                            queryM = c.get(Calendar.MONTH) + 1
                            queryD = c.get(Calendar.DAY_OF_MONTH)
                        }) {
                            Text("ဒီနေ့")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    // Direct date entry: typing dd/mm/yyyy is faster than
                    // scrolling the picker month by month to a far year.
                    var dayText by remember(queryD) { mutableStateOf("%02d".format(queryD)) }
                    var monthText by remember(queryM) { mutableStateOf("%02d".format(queryM)) }
                    var yearText by remember(queryY) { mutableStateOf("$queryY") }
                    // A stale error must not survive a date change via the
                    // picker / Today button, so the error state is keyed on
                    // the query date too.
                    var dateError by remember(queryD, queryM, queryY) { mutableStateOf("") }
                    Text(
                        "ရက်ကို တိုက်ရိုက်ရိုက်ထည့်ရန် (ရက်/လ/နှစ်):",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = dayText,
                            onValueChange = { dayText = normalizeDigits(it).filter { ch -> ch.isDigit() }.take(2) },
                            label = { Text("ရက်") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(72.dp)
                        )
                        OutlinedTextField(
                            value = monthText,
                            onValueChange = { monthText = normalizeDigits(it).filter { ch -> ch.isDigit() }.take(2) },
                            label = { Text("လ") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(72.dp)
                        )
                        OutlinedTextField(
                            value = yearText,
                            onValueChange = { yearText = normalizeDigits(it).filter { ch -> ch.isDigit() }.take(4) },
                            label = { Text("နှစ်") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(104.dp)
                        )
                        Button(onClick = {
                            val d = normalizeDigits(dayText).toIntOrNull()
                            val m = normalizeDigits(monthText).toIntOrNull()
                            val y = normalizeDigits(yearText).toIntOrNull()
                            val valid = if (d == null || m == null || y == null) {
                                false
                            } else try {
                                val cal = Calendar.getInstance(tz).apply {
                                    isLenient = false
                                    set(Calendar.YEAR, y)
                                    set(Calendar.MONTH, m - 1)
                                    set(Calendar.DAY_OF_MONTH, d)
                                }
                                cal.time // throws if the date does not exist
                                y in 1500..2500
                            } catch (e: Exception) {
                                false
                            }
                            if (valid) {
                                queryD = d!!; queryM = m!!; queryY = y!!
                                dateError = ""
                            } else {
                                dateError = "ရက်မမှန်ပါ — ရက်/လ/နှစ် ပြန်စစ်ပါ (ဥပမာ 29/02 သည် leap year မှ သာလျှင် ရှိသည်)။"
                            }
                        }) {
                            Text("ကြည့်ရန်")
                        }
                    }
                    if (dateError.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            dateError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    if (queryMillis < birthMillis) {
                        Text(
                            "မှတ်ချက်: ရွေးထားတဲ့ရက်က မွေးချိန်မတိုင်မီပါ " +
                                "(ဒသာသီအိုရီအရ အစကနေ တွက်ပြထားတာပါ)။",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    if (path.isEmpty()) {
                        Text("ဒီရက်အတွက် ဒသာ မရှိသေးပါ။")
                    } else {
                        Text("လက်ရှိ ဒသာ (၅ ဆင့်)", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        path.forEach { p ->
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
                                "  ${fmt(p.startEpochMillis)} → ${fmt(p.endEpochMillis)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (p.level == 1) {
                                Text(
                                    "  ကျန်: ${formatRemaining(p.endEpochMillis - queryMillis, convention)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---- Expandable timeline ----
        item {
            Text("ဒသာ Timeline (ချဲ့ပြီး ကြည့်နိုင်သည်)", style = MaterialTheme.typography.titleMedium)
            Text(
                "နှိပ်ပြီး ချဲ့ပါ — အဆင့်တိုင်းကို လိုအပ်မှသာ တွက်ပါသည်။ " +
                    "ပြသမှု: မွေးချိန်မှ အသက် ${if (showTo120) 120 else 100} အထိ။",
                style = MaterialTheme.typography.bodySmall
            )
        }
        items(displayMdList, key = { "md:${it.startEpochMillis}" }) { md ->
            DashaNode(
                period = md,
                convention = convention,
                activeKeys = activeKeys,
                fmt = ::fmt,
                depth = 0,
                birthMillis = birthMillis,
                rangeEnd = rangeEnd,
                queryMillis = queryMillis
            )
        }

        item {
            Text(
                "မှတ်ချက် — ဇာတာစာမျက်နှာရှိ ဒသာဇယား (အဟောင်း) ကို မထိထားပါ။ " +
                    "ဒါက ၅ ဆင့်အထိ instant တိကျစွာ တွက်တဲ့ module အသစ်ပါ။ " +
                    "ဒသာကာလနှင့် ဒသာအဓိပ္ပာယ်ကို သီးခြားထားပါသည်။",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun DashaNode(
    period: DashaPeriod,
    convention: DashaYearConvention,
    activeKeys: Set<String>,
    fmt: (Long) -> String,
    depth: Int,
    birthMillis: Long,
    rangeEnd: Long,
    queryMillis: Long
) {
    var expanded by remember { mutableStateOf(false) }
    val key = "${period.level}:${period.startEpochMillis}:${period.planet}"
    val isActive = key in activeKeys
    // Display clipping only: the true period still drives children and the
    // active path. A period entirely outside the range (and therefore its
    // whole subtree, since children tile within the parent) renders nothing.
    val displayStart = maxOf(period.startEpochMillis, birthMillis)
    val displayEnd = minOf(period.endEpochMillis, rangeEnd)
    if (displayStart >= displayEnd) return
    val children = remember(expanded, period, convention) {
        if (expanded && period.level < DashaEngine.MAX_LEVEL) {
            DashaEngine.generateChildren(period, convention)
        } else {
            emptyList()
        }
    }
    // Past / current / future against the query date, on the TRUE interval.
    val stateLabel = when {
        isActive -> " ● လက်ရှိ"
        period.endEpochMillis <= queryMillis -> " (ပြီးဆုံး)"
        period.startEpochMillis > queryMillis -> " (လာမည်)"
        else -> ""
    }
    val yearMs = convention.millisPerYear()
    val ageStart = (displayStart - birthMillis) / yearMs
    val ageEnd = (displayEnd - birthMillis) / yearMs
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 16).dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (period.level < DashaEngine.MAX_LEVEL) expanded = !expanded
                    }
                    .padding(10.dp)
            ) {
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        DASHA_LEVEL_NAMES[period.level - 1],
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (period.level < DashaEngine.MAX_LEVEL) {
                        Text(if (expanded) "▾" else "▸", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(
                    "${period.planet.mm} (${period.planet.code})" + stateLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    "${fmt(displayStart)} → ${fmt(displayEnd)}" +
                        " (အသက် ${"%.1f".format(ageStart)}–${"%.1f".format(ageEnd)})",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        if (expanded) {
            Spacer(Modifier.height(4.dp))
            children.forEach { child ->
                DashaNode(
                    period = child,
                    convention = convention,
                    activeKeys = activeKeys,
                    fmt = fmt,
                    depth = depth + 1,
                    birthMillis = birthMillis,
                    rangeEnd = rangeEnd,
                    queryMillis = queryMillis
                )
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

/** Display timezone for a chart: its own offset, or GMT in GMT mode. */
internal fun chartTimeZone(birth: BirthData): TimeZone {
    if (birth.timeInGmt) return TimeZone.getTimeZone("GMT")
    val totalMinutes = (birth.tzOffset * 60).toInt()
    val sign = if (totalMinutes < 0) "-" else "+"
    val absMin = kotlin.math.abs(totalMinutes)
    return TimeZone.getTimeZone(
        "GMT%s%02d:%02d".format(sign, absMin / 60, absMin % 60)
    )
}

/** Remaining time as "X နှစ် Y လ Z ရက်" under the convention. */
/**
 * Myanmar digits (U+1040..U+1049) pass Char.isDigit() but fail
 * String.toIntOrNull(), so a date typed with a Myanmar keyboard was always
 * rejected as invalid. Normalize them to ASCII digits on input and on parse.
 */
internal fun normalizeDigits(s: String): String = s.map { ch ->
    if (ch in '\u1040'..'\u1049') ('0' + (ch - '\u1040')) else ch
}.joinToString("")

internal fun formatRemaining(millis: Long, convention: DashaYearConvention): String {
    if (millis <= 0) return "ကုန်ဆုံးပြီ"
    val days = millis / 86_400_000.0
    val years = floor(days / convention.daysPerYear).toLong()
    val rem1 = days - years * convention.daysPerYear
    val monthLen = convention.daysPerYear / 12.0
    val months = floor(rem1 / monthLen).toLong()
    val d = floor(rem1 - months * monthLen).toLong()
    return "$years နှစ် $months လ $d ရက်"
}
