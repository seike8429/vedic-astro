package com.vedicastrology.app.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vedicastrology.app.engine.ChartResult
import com.vedicastrology.app.engine.transit.TransitEffectLogic
import com.vedicastrology.app.engine.yoga.YogaCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Transit effect tab: how the CURRENT sky affects, as general logic.
 * Like seasons — when the Sun reaches winter, everyone feels the cold —
 * these effects apply to the whole period, not to one chart. Personal
 * touch comes from the natal chart + dasha, which this tab does not
 * re-score: it shows the Astro News effect narratives whole, exactly as
 * written (fact → traditional meaning → what to watch). No per-event
 * caution bullets are invented: the dataset carries no separate caution
 * field, so the narrative itself is shown under "သက်ရောက်မှုနှင့်
 * သတိပြုရန်".
 *
 * Sections: current sky positions (Sidereal Lahiri) → currently active
 * periods (window contains today, or point/window-start within 14 days
 * either side, MMT) → full chronological period list through 2027, each
 * expandable, with active periods marked.
 */
@Composable
fun TransitEffectScreen(
    transit: ChartResult?,
    transitDateLabel: String,
    onRequestTransit: () -> Unit
) {
    val context = LocalContext.current
    var events by remember { mutableStateOf<List<NewsEvent>?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var yearSel by remember { mutableStateOf(2) } // 0=2026, 1=2027, 2=both
    // Date filter: yyyy-MM-dd (MMT), null = show all. "ယနေ့မှ" jumps to
    // today; the picker sets any from-date; the list keeps periods whose
    // end falls on/after the from-date (running windows still count).
    var fromDate by remember { mutableStateOf<String?>(null) }
    val mmtDayFmt = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT+06:30")
        }
    }
    val todayStr = remember { mmtDayFmt.format(java.util.Date()) }
    fun fmtDisplay(iso: String): String {
        val p = iso.split("-")
        return if (p.size == 3) "${p[2]}/${p[1]}/${p[0]}" else iso
    }

    LaunchedEffect(Unit) {
        try {
            val json = withContext(Dispatchers.IO) {
                context.assets.open("astro-news-2026-2027.json")
                    .bufferedReader().use { it.readText() }
            }
            events = parseNewsEvents(JSONObject(json))
        } catch (e: Exception) {
            loadError = e.message ?: "load failed"
        }
    }

    val todayDay = remember { TransitEffectLogic.todayDayMillis(System.currentTimeMillis()) }
    val activeIds = remember(events) {
        events?.filter {
            TransitEffectLogic.isActive(it.startDate, it.endDate, todayDay)
        }?.map { it.id }?.toSet() ?: emptySet()
    }
    val activeEvents = remember(events) {
        events?.filter { it.id in activeIds } ?: emptyList()
    }

    LazyColumn(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        item {
            Text("Transit effect", style = MaterialTheme.typography.titleLarge)
            Text(
                "လက်ရှိ ကောင်းကင်ရဲ့ ဖြစ်စဉ်တွေဟာ ရာသီဥတုလိုပါပဲ — နေက ဆောင်းတွင်းရောက်ရင် " +
                    "လူတိုင်း အေးတာကို ခံစားရသလို၊ ဒီဖြစ်စဉ်တွေကလည်း ကာလတစ်ခုလုံးအပေါ် " +
                    "ယေဘုယျ သက်ရောက်ပါတယ်။ ပုဂ္ဂိုလ်ရေးအရ ဘယ်လောက် ထိသလဲ ဆိုတာက " +
                    "မွေးဇာတာ + ဒသာနဲ့ ဆုံးဖြတ်သည်။",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(12.dp))
            Text("လက်ရှိ ကောင်းကင် (Sidereal Lahiri)", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            if (transit == null) {
                Text(
                    "လက်ရှိ ကောင်းကင် မတွက်ရသေးပါ။",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(4.dp))
                Button(onClick = onRequestTransit) {
                    Text("လက်ရှိ ကောင်းကင် တွက်ရန်")
                }
            } else {
                if (transitDateLabel.isNotEmpty()) {
                    Text(transitDateLabel, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                }
                val grahas = setOf(
                    "Sun", "Moon", "Mars", "Mercury", "Jupiter", "Venus", "Saturn", "Rahu", "Ketu"
                )
                for (p in transit.planets.filter { it.row.planet in grahas }) {
                    val lon = p.row.longitude
                    val degInSign = ((lon % 30.0) + 30.0) % 30.0
                    Text(
                        "• ${YogaCatalog.planetMm(p.row.planet)} — " +
                            "${YogaCatalog.signMm(YogaCatalog.signOf(lon))} " +
                            "${"%.2f".format(degInSign)}°" +
                            if (p.retrograde) " (နောက်ပြန်)" else "",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "လက်ရှိ သက်ရောက်နေသော ကာလများ",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "window အတွင်း ရှိနေသော ကာလ + ၂ ပတ်အတွင်း ဖြစ်စဉ် (MMT)။",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
        }
        if (events == null && loadError == null) {
            item { CircularProgressIndicator() }
        } else if (loadError != null) {
            item { Text("ဖြစ်စဉ်များ ဖတ်မရပါ: $loadError", style = MaterialTheme.typography.bodyMedium) }
        } else if (activeEvents.isEmpty()) {
            item {
                Text(
                    "လက်ရှိ active ဖြစ်စဉ် မရှိပါ (၂ ပတ်အတွင်း)။",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
            }
        } else {
            items(activeEvents, key = { "active-" + it.id }) { e ->
                EventCard(e, expandedByDefault = true)
                Spacer(Modifier.height(8.dp))
            }
        }
        item {
            Text("၂၀၂၇ ထိ ကာလအလိုက်", style = MaterialTheme.typography.titleMedium)
            Text(
                "ကာလတစ်ခုချင်းစီကို နှိပ်ပြီး သက်ရောက်မှုနှင့် သတိပြုရန် ဖတ်ပါ။ " +
                    "● လက်ရှိ = အခု သက်ရောက်နေသော ကာလ။",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "ရက်စွဲမှ စစ်ထုတ်ရန်",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium
            )
            Row {
                Button(onClick = { fromDate = todayStr }) {
                    Text("ယနေ့မှ")
                }
                Spacer(Modifier.padding(horizontal = 4.dp))
                OutlinedButton(onClick = {
                    val base = fromDate ?: todayStr
                    val parts = base.split("-").mapNotNull { it.toIntOrNull() }
                    if (parts.size == 3) {
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                fromDate = "%04d-%02d-%02d".format(y, m + 1, d)
                            },
                            parts[0], parts[1] - 1, parts[2]
                        ).show()
                    }
                }) {
                    Text(fromDate?.let { fmtDisplay(it) } ?: "ရက်ရွေး")
                }
                if (fromDate != null) {
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    OutlinedButton(onClick = { fromDate = null }) {
                        Text("အားလုံး ပြန်ပြ")
                    }
                }
            }
            if (fromDate != null) {
                Text(
                    "${fmtDisplay(fromDate!!)} မှ စသော ကာလများသာ ပြထားသည် " +
                        "(မပြီးသေးသော window များ ပါဝင်သည်)။",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(Modifier.height(4.dp))
            Row {
                FilterChip(
                    selected = yearSel == 2,
                    onClick = { yearSel = 2 },
                    label = { Text("အားလုံး") }
                )
                Spacer(Modifier.padding(horizontal = 4.dp))
                FilterChip(
                    selected = yearSel == 0,
                    onClick = { yearSel = 0 },
                    label = { Text("၂၀၂၆") }
                )
                Spacer(Modifier.padding(horizontal = 4.dp))
                FilterChip(
                    selected = yearSel == 1,
                    onClick = { yearSel = 1 },
                    label = { Text("၂၀၂၇") }
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        val fromMs = fromDate?.let { TransitEffectLogic.dayMillis(it) }
        val shown = (events ?: emptyList()).filter {
            yearSel == 2 || it.year == 2026 + yearSel
        }.filter { e ->
            if (fromMs == null) {
                true
            } else {
                val endMs = TransitEffectLogic.dayMillis(e.endDate)
                    ?: TransitEffectLogic.dayMillis(e.startDate)
                endMs == null || endMs >= fromMs
            }
        }
        items(shown, key = { it.id }) { e ->
            EventCard(e, expandedByDefault = false, markActive = e.id in activeIds)
            Spacer(Modifier.height(8.dp))
        }
        item {
            Text(
                "ဤအနက်များသည် ရိုးရာဗေဒင်အနက်များသာ ဖြစ်ပြီး သိပ္ပံနည်းကျ " +
                    "သက်သေပြထားသော ဟောကိန်းများ မဟုတ်ပါ။ အပြည့်အစုံနှင့် " +
                    "ကျော်ကြားသော site များ၏ အနက်ကို Astro News tab တွင် ကြည့်ပါ။",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun EventCard(
    e: NewsEvent,
    expandedByDefault: Boolean,
    markActive: Boolean = false
) {
    var expanded by remember { mutableStateOf(expandedByDefault) }
    Card(
        modifier = Modifier.fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column(Modifier.padding(12.dp)) {
            Row {
                Text(
                    e.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                if (markActive) {
                    Text(
                        " ● လက်ရှိ",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                "${e.dateLine} · ${catLabel(e.category)}",
                style = MaterialTheme.typography.bodyMedium
            )
            if (e.visibility != null) {
                Text(e.visibility, style = MaterialTheme.typography.bodyMedium)
            }
            if (expanded) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "သက်ရောက်မှုနှင့် သတိပြုရန်",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    e.narrative ?: "အဟော မပါပါ။",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(
                    "အသေးစိတ်ကြည့်ရန် နှိပ်ပါ ▸",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
