package com.vedicastrology.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vedicastrology.app.engine.BirthData
import com.vedicastrology.app.engine.ChartResult
import com.vedicastrology.app.engine.Ephemeris
import com.vedicastrology.app.engine.RahuKalam
import com.vedicastrology.app.engine.SIGNS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Rahu Kalam tab — Sayagyi (ဆရာကြီး) wheel, from the book
 * မြန်မာ့ရိုးရာ သရေခေတ္တရာဟုကာလ (Vol. 2). One wheel style, the book
 * cover itself, 100% identical — bright-yellow outer ring of 28
 * nakshatras, pink Dasagga group ring, blue group segments, yellow
 * diamond divided into the 6 Sayagyi parts, red centre dot. Rotated so
 * the mouth's nakshatra is centred at the
 * top, as the cover shows it. (The ZarTarPro concentric-ring style was
 * removed in v1.8.27 at the author's order — book cover only.)
 * The wheel is built from REAL calculated data (SwissEph, Lahiri,
 * True/Mean node as chosen) — no hardcoded positions. It is driven
 * by the DISPLAYED wheel: ဇာတာ = natal Rahu's wheel, လက်ရှိ = current
 * Rahu's wheel (when Rahu moves to a new nakshatra the mouth moves and
 * every part shifts with it — the wheel is dynamic). One planet set at
 * a time (natal filled / now hollow); the author dislikes overlap.
 * The daily reading uses the CURRENT Moon: day 1 = today, day 2 = tomorrow.
 */

/** One computed reading for the screen. */
private data class Reading(
    val day1Millis: Long,
    val day2Millis: Long,
    val natalRahuLon: Double,
    /** Natal planets as (name, sidereal longitude), Lagna excluded. */
    val natalPlanets: List<Pair<String, Double>>,
    /** Current-sky (transit) planets as (name, sidereal longitude). */
    val transitPlanets: List<Pair<String, Double>>,
    val moon1Lon: Double,
    val moon2Lon: Double,
    /** Natal wheel: mouth = the nakshatra natal Rahu occupies. */
    val wheel: RahuKalam.SayagyiWheel,
    /** Transit wheel: mouth = the nakshatra CURRENT (transit) Rahu occupies.
     *  When Rahu moves to a new nakshatra the mouth moves and every part
     *  shifts with it — the wheel is dynamic, not fixed. */
    val transitWheel: RahuKalam.SayagyiWheel,
    val useTrueRahu: Boolean
)

/** Burmese single-letter glyphs for the planet markers. */
private val PLANET_GLYPHS = mapOf(
    "Sun" to "တ", "Moon" to "လ", "Mars" to "င", "Mercury" to "ဗ",
    "Jupiter" to "ဂ", "Venus" to "စ", "Saturn" to "န",
    "Rahu" to "ရ", "Ketu" to "က"
)

/** Marker colours (glyph distinguishes same colours). */
private val PLANET_COLORS = mapOf(
    "Sun" to Color(0xFFF57F17), "Moon" to Color(0xFF212121),
    "Mars" to Color(0xFFD32F2F), "Mercury" to Color(0xFF1565C0),
    "Jupiter" to Color(0xFF2E7D32), "Venus" to Color(0xFF0277BD),
    "Saturn" to Color(0xFF4527A0), "Rahu" to Color(0xFF558B2F),
    "Ketu" to Color(0xFF6D4C41)
)

@Composable
fun RahuKalamScreen(natal: ChartResult, birth: BirthData, chartName: String, useTrueRahu: Boolean) {
    var refreshKey by remember { mutableIntStateOf(0) }
    var reading by remember { mutableStateOf<Reading?>(null) }
    // Wheel mode: false = ဇာတာ (natal, filled), true = လက်ရှိ (now, hollow).
    // One set at a time — the author dislikes the two sets overlapping.
    var showTransit by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }

    val mmtFmt = remember {
        SimpleDateFormat("dd/MM/yyyy", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT+06:30")
        }
    }

    LaunchedEffect(refreshKey, natal) {
        try {
            val r = withContext(Dispatchers.Default) {
                val rahuLon = natal.planets
                    .first { it.row.planet == "Rahu" }.row.longitude
                val planets = natal.planets
                    .filter { it.row.planet in PLANET_GLYPHS }
                    .map { it.row.planet to it.row.longitude }
                val now = System.currentTimeMillis()
                val jd1 = RahuKalam.jdFromMillis(now)
                val jd2 = RahuKalam.jdFromMillis(now + 24L * 60 * 60 * 1000)
                val moon1 = Ephemeris.planetPosition(jd1, "Moon", useTrueRahu).first
                val moon2 = Ephemeris.planetPosition(jd2, "Moon", useTrueRahu).first
                // Current-sky (transit) planets, same ayanamsa + node setting.
                val transitNames = listOf(
                    "Sun", "Moon", "Mars", "Mercury", "Jupiter",
                    "Venus", "Saturn", "Rahu", "Ketu"
                )
                val transit = transitNames.map { name ->
                    name to Ephemeris.planetPosition(jd1, name, useTrueRahu).first
                }
                // Transit wheel: mouth = the nakshatra CURRENT Rahu occupies.
                val transitRahuLon = transit.first { it.first == "Rahu" }.second
                Reading(
                    now, now + 24L * 60 * 60 * 1000, rahuLon, planets, transit,
                    moon1, moon2, RahuKalam.sayagyiWheel(rahuLon),
                    RahuKalam.sayagyiWheel(transitRahuLon), useTrueRahu
                )
            }
            reading = r
            loadError = null
        } catch (e: Exception) {
            loadError = e.message ?: "တွက်မရပါ"
        }
    }

    LazyColumn(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        item {
            Text("ရာဟုကာလနစက်", style = MaterialTheme.typography.titleLarge)
            Text(
                "ဆရာကြီး စက်ဝိုင်း — မြန်မာ့ရိုးရာ သရေခေတ္တရာဟုကာလ စာအုပ်နည်းအတိုင်း။ " +
                    "ရာဟု စီးတဲ့ နက္ခတ်က ခံတွင်း၊ ကျန်အပိုင်းတွေ အစဉ်အတိုင်း။ " +
                    "ရာဟု နက္ခတ် ပြောင်းရင် ခံတွင်း ရွေ့ပြီး အပိုင်းအားလုံး လိုက်ရွေ့တယ် — " +
                    "ဇာတာ စက်ဝိုင်း (ဇာတာ ရာဟု) နဲ့ လက်ရှိ စက်ဝိုင်း (လက်ရှိ ရာဟု) ခွဲပြထားသည်။ " +
                    "လ ရဲ့ အပိုင်း (၁ နေ့ / ၂ လာ) ကို ကြည့်ပြီး အဟော ဖတ်သည်။",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(12.dp))
        }

        val r = reading
        if (r == null) {
            item {
                if (loadError != null) {
                    Text(loadError!!, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { refreshKey++ }) { Text("ပြန်တွက်") }
                } else {
                    CircularProgressIndicator()
                }
            }
            return@LazyColumn
        }

        val rahuN28 = RahuKalam.nakshatra28(r.natalRahuLon)
        val transitRahuLon = r.transitPlanets.first { it.first == "Rahu" }.second
        val transitRahuN28 = RahuKalam.nakshatra28(transitRahuLon)
        val moon1N28 = RahuKalam.nakshatra28(r.moon1Lon)
        val moon2N28 = RahuKalam.nakshatra28(r.moon2Lon)
        // The displayed wheel follows the mode: ဇာတာ = natal Rahu's wheel,
        // လက်ရှိ = current Rahu's wheel (mouth + all parts shifted with Rahu).
        val displayWheel = if (showTransit) r.transitWheel else r.wheel
        val moon1Part = displayWheel.partOf(moon1N28)
        val moon2Part = displayWheel.partOf(moon2N28)
        val verdict2 = RahuKalam.day2Verdict(moon2Part)

        // Circular wheel — one set at a time (no overlap):
        // ဇာတာ (natal wheel, filled) or လက်ရှိ (transit wheel, hollow),
        // like the D1/D9 switch. Book-cover style only.
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
                // The ACTIVE mode is the dark-red (primary) button; the other
                // is grey — the author reads the highlight as the current mode.
                Button(
                    onClick = { showTransit = false },
                    modifier = Modifier.padding(end = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!showTransit) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (!showTransit) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) { Text("● ဇာတာ") }
                Button(
                    onClick = { showTransit = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (showTransit) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (showTransit) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) { Text("○ လက်ရှိ (now)") }
            }
            // The displayed planet set follows the mode (one set at a time).
            RahuKalamWheel(
                wheel = displayWheel,
                natalPlanets = r.natalPlanets,
                transitPlanets = r.transitPlanets,
                showTransit = showTransit,
                moon1N28 = moon1N28,
                moon2N28 = moon2N28,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
            )
            Spacer(Modifier.height(12.dp))
        }

        // Summary table.
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(4.dp)) {
                    TableRow("ဇာတာ", chartName.ifEmpty { "—" }, boldValue = true)
                    HorizontalDivider()
                    TableRow(
                        if (showTransit) {
                            "လက်ရှိ ရာဟု (${if (r.useTrueRahu) "True" else "Mean"})"
                        } else {
                            "ဇာတာ ရာဟု (${if (r.useTrueRahu) "True" else "Mean"})"
                        },
                        if (showTransit) {
                            "${RahuKalam.name28(transitRahuN28)} (နံပါတ် $transitRahuN28) — ခံတွင်း အစ"
                        } else {
                            "${RahuKalam.name28(rahuN28)} (နံပါတ် $rahuN28) — ခံတွင်း အစ"
                        },
                        boldValue = true
                    )
                    HorizontalDivider()
                    TableRow(
                        "၁ နေ့ (${mmtFmt.format(Date(r.day1Millis))})",
                        "လ — ${RahuKalam.name28(moon1N28)} ($moon1N28)၊ ${moon1Part.mm}"
                    )
                    HorizontalDivider()
                    TableRow(
                        "၂ လာ (${mmtFmt.format(Date(r.day2Millis))})",
                        "လ — ${RahuKalam.name28(moon2N28)} ($moon2N28)၊ ${moon2Part.mm}"
                    )
                    HorizontalDivider()
                    TableRow(
                        "၂ လာ အဟော (ယေဘုယျ)",
                        "${verdict2.mm} — " + if (verdict2.good) {
                            "အပိုင်း ${moon2Part.mm} မှာ လ ရောက်တဲ့အခါ အလုပ်အောင်မြင်၊ အိမ်ထောင်ရေး ကောင်း။"
                        } else {
                            "အပိုင်း ${moon2Part.mm} မှာ လ ရောက်တဲ့အခါ အလုပ်မှာ အခက်အခဲ၊ အိမ်ထောင်ရေး မတည်။"
                        },
                        boldValue = true
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Six-part table, in construction order (head first, from Rahu).
        item {
            Text("စက်ဝိုင်း ၆ ပိုင်း", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(4.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                        Text("အပိုင်း", Modifier.weight(0.9f), fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium)
                        Text("နံပါတ်", Modifier.weight(1.6f), fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium)
                        Text("လ ၁ နေ့", Modifier.weight(0.7f), fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium)
                        Text("လ ၂ လာ", Modifier.weight(0.7f), fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    HorizontalDivider()
                    val displayOrder = listOf(
                        RahuKalam.WheelPart.HEAD, RahuKalam.WheelPart.MOUTH,
                        RahuKalam.WheelPart.CHEST, RahuKalam.WheelPart.BELLY,
                        RahuKalam.WheelPart.ANUS, RahuKalam.WheelPart.TAIL
                    )
                    val byPart = displayWheel.parts.associateBy { it.part }
                    displayOrder.forEachIndexed { idx, part ->
                        val entry = byPart[part] ?: return@forEachIndexed
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                            Text(entry.part.mm, Modifier.weight(0.9f),
                                fontWeight = if (part == RahuKalam.WheelPart.HEAD) FontWeight.Bold else null,
                                style = MaterialTheme.typography.bodyMedium)
                            Text(
                                entry.numbers.joinToString(", ") { RahuKalam.burmeseNum(it) },
                                Modifier.weight(1.6f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                if (moon1N28 in entry.numbers) "●" else "",
                                Modifier.weight(0.7f),
                                color = Color(0xFF1565C0),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                if (moon2N28 in entry.numbers) "●" else "",
                                Modifier.weight(0.7f),
                                color = Color(0xFF2E7D32),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (idx < displayOrder.size - 1) HorizontalDivider()
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Text("● လ ၁ နေ့", style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF1565C0), modifier = Modifier.padding(end = 12.dp))
                Text("● လ ၂ လာ", style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF2E7D32))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "အဟော အပြည့်အစုံ (အပိုင်းအတွဲ × ၅ ခေါင်းစဉ် — စီးပွားရေး၊ ပညာရေး၊ " +
                    "ကျန်းမာရေး၊ အိမ်ထောင်ရေး၊ အချစ်အဆိုင်) ကို စာအုပ်ကနေ နောက်ဗားရှင်းမှာ ထည့်ပါမည်။",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(12.dp))
        }

        // Planets: the displayed set (ဇာတာ or လက်ရှိ, following the toggle),
        // one line per planet — ဂြိုလ် | နက္ခတ် | ပါဒ | သရ (author's format).
        item {
            Text(
                if (showTransit) "ဂြိုလ် — လက်ရှိ (now)" else "ဂြိုလ် — ဇာတာ",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(4.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(4.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                        Text("ဂြိုလ်", Modifier.weight(0.9f), fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium)
                        Text("နက္ခတ်", Modifier.weight(1.2f), fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium)
                        Text("ပါဒ", Modifier.weight(0.5f), fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium)
                        Text("သရ", Modifier.weight(0.6f), fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    HorizontalDivider()
                    val planetMm = mapOf(
                        "Sun" to "နေ", "Moon" to "လ", "Mars" to "အင်္ဂါ",
                        "Mercury" to "ဗုဒ္ဓဟူး", "Jupiter" to "ကြာသပတေး",
                        "Venus" to "သောကြာ", "Saturn" to "စနေ",
                        "Rahu" to "ရာဟု", "Ketu" to "ကေတု"
                    )
                    val displayByName = (if (showTransit) r.transitPlanets else r.natalPlanets).toMap()
                    val order = listOf(
                        "Sun", "Moon", "Mars", "Mercury", "Jupiter",
                        "Venus", "Saturn", "Rahu", "Ketu"
                    )
                    order.forEachIndexed { idx, name ->
                        val lon = displayByName[name]
                        val (n28, pada) = if (lon == null) 0 to 0 else RahuKalam.padaOf(lon)
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                            Text(planetMm[name] ?: name, Modifier.weight(0.9f),
                                style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (lon == null) "—" else RahuKalam.name28(n28),
                                Modifier.weight(1.2f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                if (lon == null || n28 == 22) "—" else "$pada",
                                Modifier.weight(0.5f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                if (lon == null) "—" else RahuKalam.syllableOf(lon),
                                Modifier.weight(0.6f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (idx < order.size - 1) HorizontalDivider()
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Dasagga groups: which nakshatras are in each group.
        item {
            Text("ဒသဂ္ဂ အဖွဲ့ ၁၀ ခု", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(4.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                        Text("အဖွဲ့", Modifier.weight(0.8f), fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium)
                        Text("နက္ခတ်", Modifier.weight(2.2f), fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    HorizontalDivider()
                    val displayGroups = RahuKalam.dasaggaGroups(displayWheel.mouthStart28)
                    displayGroups.forEachIndexed { idx, group ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                            Text(group.mm, Modifier.weight(0.8f),
                                style = MaterialTheme.typography.bodyMedium)
                            Text(
                                group.numbers.joinToString(", ") {
                                    "${RahuKalam.name28(it)} (${RahuKalam.burmeseNum(it)})"
                                },
                                Modifier.weight(2.2f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (idx < displayGroups.size - 1) HorizontalDivider()
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "သရ (ပါဒ သင်္ကေတ) များမှာ စံ သင်္ကေတ များကို မြန်မာလို ရေးထားခြင်း ဖြစ်သည် — " +
                    "မာဃ မ/မိ/မု/မေ ကို ဆရာ အတည်ပြုပြီးသား။ စာအုပ်နဲ့ ကွဲတဲ့ စာလုံးပေါင်း ရှိရင် ပြင်ပေးပါ။",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { refreshKey++ }) { Text("ပြန်တွက်") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** One label/value row of the summary table. */
@Composable
private fun TableRow(label: String, value: String, boldValue: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
        Text(label, Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium)
        Text(value, Modifier.weight(1.6f),
            fontWeight = if (boldValue) FontWeight.Bold else null,
            style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Circular wheel, EXACTLY like the book cover
 * (မြန်မာ့ရိုးရာ သရေခေတ္တရာဟုကာလ, Vol. 2, cover), outside -> in:
 * bright-yellow outer ring of 28 nakshatras (Burmese numeral per cell),
 * mauve-pink Dasagga group ring (10 groups, names along the arc),
 * blue Dasagga group segments (one per group, names along the arc),
 * light-green ground, a yellow diamond divided into the 6 Sayagyi
 * parts (mouth pink triangle at the top, anus pink triangle at the
 * bottom, head left, chest top-right, belly bottom-right, tail
 * bottom-left), and a red centre dot. (The cover's pink မော / ဇီဝ
 * vertex boxes were removed in v1.8.29 at the author's order — they
 * are wrong.) Planet markers (natal filled, now hollow,
 * with their pada syllable သရ) sit in the outer nakshatra ring.
 * The wheel is ROTATED so the mouth's nakshatra is centred at the
 * top — exactly as the cover shows it (mouth top, head left) — for
 * any natal chart. Angles are clockwise from the top.
 */
@Composable
private fun RahuKalamWheel(
    wheel: RahuKalam.SayagyiWheel,
    natalPlanets: List<Pair<String, Double>>,
    transitPlanets: List<Pair<String, Double>>,
    showTransit: Boolean,
    moon1N28: Int,
    moon2N28: Int,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val R = size.minDimension / 2f
        val D = R * 0.78f
        // Cover colours.
        val coverGreen = Color(0xFFC5E1A5)
        val coverYellow = Color(0xFFFFEB3B)
        val coverPink = Color(0xFFD69AAE)
        val coverBlue = Color(0xFF8EA6DB)
        val diamondYellow = Color(0xFFFFF9A8)
        val trianglePink = Color(0xFFF8BBD0)
        val centreRed = Color(0xFFC62828)

        fun rad(degClockwiseFromTop: Double) =
            Math.toRadians(degClockwiseFromTop - 90.0)
        fun px(r: Float, deg: Double) = cx + (r * cos(rad(deg))).toFloat()
        fun py(r: Float, deg: Double) = cy + (r * sin(rad(deg))).toFloat()
        fun norm360(a: Double) = ((a % 360.0) + 360.0) % 360.0

        // Rotation: the mouth's nakshatra is centred at the top (0 deg),
        // exactly as the cover shows it.
        val mouthN28 = wheel.parts
            .first { it.part == RahuKalam.WheelPart.MOUTH }.numbers.first()
        val mouthCentre = (mouthN28 - 1) * 360.0 / 28.0 + 360.0 / 56.0
        val rot = -mouthCentre
        /** Angle (clockwise from top, rotated frame) of a longitude. */
        fun rotAngle(lon: Double) = norm360(lon + rot)
        /** Angle (rotated frame) of a nakshatra number's cell centre. */
        fun cellAngle(n28: Int) =
            norm360((n28 - 1) * 360.0 / 28.0 + 360.0 / 56.0 + rot)
        /** Angle (rotated frame) of a nakshatra number's cell start. */
        fun cellStart(n28: Int) = norm360((n28 - 1) * 360.0 / 28.0 + rot)
        /** Distance from the centre to the diamond's edge at an angle. */
        fun diamondR(degClockwiseFromTop: Double): Float {
            val t = Math.toRadians(degClockwiseFromTop)
            return (D / (kotlin.math.abs(sin(t)) + kotlin.math.abs(cos(t)))).toFloat()
        }

        // ---- Green ground (whole wheel) ----
        drawCircle(color = coverGreen, radius = R * 0.97f, center = Offset(cx, cy))

        // ---- Outer yellow ring: 28 nakshatra cells (Burmese numeral) ----
        val outerR = R * 0.935f
        val outerW = R * 0.07f
        for (n in 1..28) {
            val start = cellStart(n)
            drawArc(
                color = coverYellow, startAngle = (start - 90f).toFloat(),
                sweepAngle = 360f / 28f, useCenter = false,
                topLeft = Offset(cx - outerR, cy - outerR),
                size = Size(outerR * 2, outerR * 2),
                style = Stroke(width = outerW)
            )
            // Radial divider at the cell start.
            drawLine(
                color = Color.Black,
                start = Offset(px(outerR - outerW / 2f, start), py(outerR - outerW / 2f, start)),
                end = Offset(px(outerR + outerW / 2f, start), py(outerR + outerW / 2f, start)),
                strokeWidth = 2f
            )
            // Burmese numeral, along the arc.
            drawArcLabel(
                RahuKalam.burmeseNum(n), cellAngle(n), R * 0.955f,
                size.minDimension * 0.018f, cx, cy, bold = false
            )
        }
        // Outer + inner boundary circles of the yellow ring.
        drawCircle(
            color = Color.Black, radius = R * 0.97f, center = Offset(cx, cy),
            style = Stroke(width = 2f)
        )
        drawCircle(
            color = Color.Black, radius = R * 0.90f, center = Offset(cx, cy),
            style = Stroke(width = 2f)
        )

        // ---- Pink Dasagga group ring (10 groups, names along the arc) ----
        val groupR = R * 0.865f
        val groupW = R * 0.07f
        RahuKalam.dasaggaGroups(wheel.mouthStart28).forEach { group ->
            val first = group.numbers.first()
            val start = cellStart(first)
            val sweep = group.numbers.size * 360.0 / 28.0
            drawArc(
                color = coverPink, startAngle = (start - 90f).toFloat(),
                sweepAngle = sweep.toFloat(), useCenter = false,
                topLeft = Offset(cx - groupR, cy - groupR),
                size = Size(groupR * 2, groupR * 2),
                style = Stroke(width = groupW)
            )
            drawLine(
                color = Color.Black,
                start = Offset(px(groupR - groupW / 2f, start), py(groupR - groupW / 2f, start)),
                end = Offset(px(groupR + groupW / 2f, start), py(groupR + groupW / 2f, start)),
                strokeWidth = 2f
            )
            val mid = norm360(start + sweep / 2.0)
            drawArcLabel(
                group.mm, mid, groupR, size.minDimension * 0.020f, cx, cy, bold = true
            )
        }
        drawCircle(
            color = Color.Black, radius = R * 0.83f, center = Offset(cx, cy),
            style = Stroke(width = 2f)
        )

        // ---- Blue Dasagga group segments (diamond edge .. pink ring) ----
        RahuKalam.dasaggaGroups(wheel.mouthStart28).forEach { group ->
            val first = group.numbers.first()
            val start = cellStart(first)
            val sweep = group.numbers.size * 360.0 / 28.0
            val end = start + sweep
            val path = androidx.compose.ui.graphics.Path().apply {
                // Outer arc (pink-ring radius).
                val steps = maxOf(4, (sweep / 2.0).toInt())
                for (i in 0..steps) {
                    val a = start + sweep * i / steps
                    val p = Offset(px(R * 0.83f, a), py(R * 0.83f, a))
                    if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                }
                // Inner boundary (diamond edge), back to the start.
                for (i in steps downTo 0) {
                    val a = start + sweep * i / steps
                    val rr = diamondR(a)
                    val p = Offset(px(rr, a), py(rr, a))
                    lineTo(p.x, p.y)
                }
                close()
            }
            drawPath(path = path, color = coverBlue)
            drawPath(path = path, color = Color.Black, style = Stroke(width = 2f))
            // Group name, along the arc, midway between diamond and ring.
            val mid = norm360(start + sweep / 2.0)
            val midR = (R * 0.83f + diamondR(mid)) / 2f
            drawArcLabel(
                group.mm, mid, midR, size.minDimension * 0.020f, cx, cy, bold = true
            )
        }

        // ---- Diamond: 6 Sayagyi parts (wedges from the centre) ----
        val partColors = mapOf(
            RahuKalam.WheelPart.HEAD to diamondYellow,
            RahuKalam.WheelPart.MOUTH to trianglePink,
            RahuKalam.WheelPart.CHEST to diamondYellow,
            RahuKalam.WheelPart.BELLY to diamondYellow,
            RahuKalam.WheelPart.ANUS to trianglePink,
            RahuKalam.WheelPart.TAIL to diamondYellow
        )
        wheel.parts.forEach { entry ->
            val first = entry.numbers.first()
            val start = cellStart(first)
            val sweep = entry.numbers.size * 360.0 / 28.0
            val end = start + sweep
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(cx, cy)
                val steps = maxOf(4, (sweep / 2.0).toInt())
                for (i in 0..steps) {
                    val a = start + sweep * i / steps
                    val rr = diamondR(a)
                    val p = Offset(px(rr, a), py(rr, a))
                    lineTo(p.x, p.y)
                }
                close()
            }
            drawPath(path = path, color = partColors[entry.part] ?: diamondYellow)
            drawPath(path = path, color = Color.Black, style = Stroke(width = 2f))
        }

        // ---- Part labels (mouth/anus vertical, the rest horizontal) ----
        val partLabels = mapOf(
            RahuKalam.WheelPart.HEAD to "ဦးခေါင်း",
            RahuKalam.WheelPart.MOUTH to "ခံတွင်း",
            RahuKalam.WheelPart.CHEST to "ရင်",
            RahuKalam.WheelPart.BELLY to "ဝမ်း",
            RahuKalam.WheelPart.ANUS to "စအို",
            RahuKalam.WheelPart.TAIL to "အမြှီး"
        )
        wheel.parts.forEach { entry ->
            val first = entry.numbers.first()
            val start = cellStart(first)
            val sweep = entry.numbers.size * 360.0 / 28.0
            val mid = norm360(start + sweep / 2.0)
            val label = partLabels[entry.part] ?: return@forEach
            val labelR = D * 0.48f
            val lx = px(labelR, mid)
            val ly = py(labelR, mid)
            val vertical = entry.part == RahuKalam.WheelPart.MOUTH ||
                entry.part == RahuKalam.WheelPart.ANUS
            drawContext.canvas.nativeCanvas.apply {
                save()
                if (vertical) rotate(90f, lx, ly)
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.BLACK
                    textSize = size.minDimension * 0.024f
                    textAlign = android.graphics.Paint.Align.CENTER
                    isFakeBoldText = true
                }
                drawText(label, lx, ly + paint.textSize / 3f, paint)
                restore()
            }
        }

        // ---- Centre red dot ----
        drawCircle(color = centreRed, radius = R * 0.025f, center = Offset(cx, cy))
        drawCircle(
            color = Color.Black, radius = R * 0.025f, center = Offset(cx, cy),
            style = Stroke(width = 2f)
        )

        // ---- Planet markers in the outer nakshatra ring ----
        // One set only (no overlap): natal (filled) or now (hollow).
        val displayPlanets = if (showTransit) transitPlanets else natalPlanets
        val markerR = R * 0.925f
        displayPlanets.forEach { (name, lon) ->
            val ang = rotAngle(lon)
            val pos = Offset(px(markerR, ang), py(markerR, ang))
            val color = PLANET_COLORS[name] ?: Color.Gray
            val radius = size.minDimension * 0.012f
            if (!showTransit) {
                drawCircle(color = color, radius = radius, center = pos)
                drawCircle(
                    color = Color.White, radius = radius, center = pos,
                    style = Stroke(width = 2f)
                )
            } else {
                drawCircle(color = Color.White, radius = radius, center = pos)
                drawCircle(
                    color = color, radius = radius, center = pos,
                    style = Stroke(width = 2.5f)
                )
            }
            drawContext.canvas.nativeCanvas.apply {
                val glyphPaint = android.graphics.Paint().apply {
                    this.color = if (!showTransit) {
                        android.graphics.Color.WHITE
                    } else {
                        android.graphics.Color.rgb(
                            (color.red * 255).toInt(),
                            (color.green * 255).toInt(),
                            (color.blue * 255).toInt()
                        )
                    }
                    textSize = size.minDimension * 0.020f
                    textAlign = android.graphics.Paint.Align.CENTER
                    isFakeBoldText = true
                }
                drawText(
                    PLANET_GLYPHS[name] ?: "?", pos.x,
                    pos.y + glyphPaint.textSize / 3f, glyphPaint
                )
                // သရ, just clockwise of the marker (tangent direction).
                val theta = Math.toRadians(ang)
                val sx = pos.x + (R * 0.055f * cos(theta)).toFloat()
                val sy = pos.y + (R * 0.055f * sin(theta)).toFloat()
                val saraPaint = android.graphics.Paint().apply {
                    this.color = android.graphics.Color.BLACK
                    textSize = size.minDimension * 0.016f
                    textAlign = android.graphics.Paint.Align.CENTER
                    isFakeBoldText = true
                }
                drawText(
                    RahuKalam.syllableOf(lon), sx,
                    sy + saraPaint.textSize / 3f, saraPaint
                )
            }
        }
        // Current Moon day 1 (blue ring) / day 2 (green ring) — only in
        // လက်ရှိ mode, since day 1 / day 2 Moon is the current Moon.
        if (showTransit) {
            val transitMoonLon = transitPlanets.firstOrNull { it.first == "Moon" }?.second
            if (transitMoonLon != null) {
                val pos = Offset(
                    px(markerR, rotAngle(transitMoonLon)),
                    py(markerR, rotAngle(transitMoonLon))
                )
                drawCircle(
                    color = Color(0xFF1565C0), radius = size.minDimension * 0.019f,
                    center = pos, style = Stroke(width = 3f)
                )
            }
            val moon2Pos = Offset(
                px(markerR, cellAngle(moon2N28)), py(markerR, cellAngle(moon2N28))
            )
            drawCircle(
                color = Color(0xFF2E7D32), radius = size.minDimension * 0.019f,
                center = moon2Pos, style = Stroke(width = 3f)
            )
        }
    }
}





/** Draw text along an arc, upright (flipped on the bottom half). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArcLabel(
    text: String, midDegClockwiseFromTop: Double, radius: Float,
    textSize: Float, cx: Float, cy: Float, bold: Boolean
) {
    val rad = Math.toRadians(midDegClockwiseFromTop - 90.0)
    val x = cx + (radius * cos(rad)).toFloat()
    val y = cy + (radius * sin(rad)).toFloat()
    val flip = midDegClockwiseFromTop > 90.0 && midDegClockwiseFromTop < 270.0
    val rot = (if (flip) midDegClockwiseFromTop + 180.0 else midDegClockwiseFromTop).toFloat()
    drawContext.canvas.nativeCanvas.apply {
        save()
        rotate(rot, x, y)
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            this.textSize = textSize
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = bold
        }
        drawText(text, x, y + paint.textSize / 3f, paint)
        restore()
    }
}
