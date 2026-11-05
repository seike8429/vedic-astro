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
 * မြန်မာ့ရိုးရာ သရေခေတ္တရာဟုကာလ (Vol. 2). Two wheel styles, one toggle:
 * - စာအုပ် (BOOK): the book cover itself, 100% identical — bright-yellow
 *   outer ring of 28 nakshatras, pink Dasagga group ring, blue group
 *   segments, yellow diamond divided into the 6 Sayagyi parts, red
 *   centre dot, pink မော / ဇီဝ boxes. Rotated so the mouth's nakshatra
 *   is centred at the top, as the cover shows it.
 * - ZarTarPro (ZARTARPRO): the professional concentric-ring chart —
 *   cream outer ring of 28 nakshatras (name + numeral, pada ticks),
 *   planet-marker circles with Burmese glyphs, Dasagga group ring,
 *   Sayagyi body-part ring, inner nakshatra ring, centre with app name,
 *   birth date+time, a parts grid, "Sidereal LAHIRI" and place, and
 *   planet longitudes in the four corners. True 360° orientation
 *   (Ashwini at the top, clockwise); markers at their exact longitude.
 * Both wheels are built from REAL calculated data (SwissEph, Lahiri,
 * True/Mean node as chosen) — no hardcoded positions. Both are driven
 * by the DISPLAYED wheel: ဇာတာ = natal Rahu's wheel, လက်ရှိ = current
 * Rahu's wheel (when Rahu moves to a new nakshatra the head moves and
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
    /** Natal wheel: head = the nakshatra natal Rahu occupies. */
    val wheel: RahuKalam.SayagyiWheel,
    /** Transit wheel: head = the nakshatra CURRENT (transit) Rahu occupies.
     *  When Rahu moves to a new nakshatra the head moves and every part
     *  shifts with it — the wheel is dynamic, not fixed. */
    val transitWheel: RahuKalam.SayagyiWheel,
    val useTrueRahu: Boolean
)

/** Wheel style: BOOK = book-cover diamond, ZARTARPRO = concentric rings. */
private enum class WheelStyle { BOOK, ZARTARPRO }

/** Burmese single-letter glyphs for the planet markers (ZarTarPro style). */
private val PLANET_GLYPHS = mapOf(
    "Sun" to "တ", "Moon" to "လ", "Mars" to "င", "Mercury" to "ဗ",
    "Jupiter" to "ဂ", "Venus" to "စ", "Saturn" to "န",
    "Rahu" to "ရ", "Ketu" to "က"
)

/** Marker colours (ZarTarPro palette; glyph distinguishes same colours). */
private val PLANET_COLORS = mapOf(
    "Sun" to Color(0xFFF57F17), "Moon" to Color(0xFF212121),
    "Mars" to Color(0xFFD32F2F), "Mercury" to Color(0xFF1565C0),
    "Jupiter" to Color(0xFF2E7D32), "Venus" to Color(0xFF0277BD),
    "Saturn" to Color(0xFF4527A0), "Rahu" to Color(0xFF558B2F),
    "Ketu" to Color(0xFF6D4C41)
)

/** Dasagga group arc colours (light, ZarTarPro palette). */
private val GROUP_COLORS = listOf(
    Color(0xFFFFFDE7), Color(0xFFFFF9C4), Color(0xFFFFECB3),
    Color(0xFFFFCC80), Color(0xFFFFAB91), Color(0xFFF48FB1),
    Color(0xFFCE93D8), Color(0xFFB39DDB), Color(0xFF90CAF9),
    Color(0xFFA5D6A7)
)

/** Sayagyi part arc colours (softened). */
private val PART_COLORS = mapOf(
    RahuKalam.WheelPart.HEAD to Color(0xFFEF9A9A),
    RahuKalam.WheelPart.MOUTH to Color(0xFFFFE082),
    RahuKalam.WheelPart.CHEST to Color(0xFFA5D6A7),
    RahuKalam.WheelPart.BELLY to Color(0xFF90CAF9),
    RahuKalam.WheelPart.ANUS to Color(0xFFCE93D8),
    RahuKalam.WheelPart.HIPS to Color(0xFFFFCC80)
)

@Composable
fun RahuKalamScreen(natal: ChartResult, birth: BirthData, chartName: String, useTrueRahu: Boolean) {
    var refreshKey by remember { mutableIntStateOf(0) }
    var reading by remember { mutableStateOf<Reading?>(null) }
    // Wheel mode: false = ဇာတာ (natal, filled), true = လက်ရှိ (now, hollow).
    // One set at a time — the author dislikes the two sets overlapping.
    var showTransit by remember { mutableStateOf(false) }
    // Wheel style: BOOK (book-cover diamond) or ZARTARPRO (concentric rings).
    var wheelStyle by remember { mutableStateOf(WheelStyle.BOOK) }
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
                // Transit wheel: head = the nakshatra CURRENT Rahu occupies.
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
            Text("ရာဟုကာလ", style = MaterialTheme.typography.titleLarge)
            Text(
                "ဆရာကြီး စက်ဝိုင်း — မြန်မာ့ရိုးရာ သရေခေတ္တရာဟုကာလ စာအုပ်နည်းအတိုင်း။ " +
                    "ရာဟု စီးတဲ့ နက္ခတ်က ဦးခေါင်း၊ ကျန်အပိုင်းတွေ အစဉ်အတိုင်း။ " +
                    "ရာဟု နက္ခတ် ပြောင်းရင် ခေါင်း ရွေ့ပြီး အပိုင်းအားလုံး လိုက်ရွေ့တယ် — " +
                    "ဇာတာ စက်ဝိုင်း (ဇာတာ ရာဟု) နဲ့ လက်ရှိ စက်ဝိုင်း (လက်ရှိ ရာဟု) ခွဲပြထားသည်။ " +
                    "စက်ဝိုင်း ပုံစံ နှစ်မျိုး — စာအုပ် (စိန်) နဲ့ ZarTarPro (စက်ဝိုင်းအထပ်) — ခလုတ်နဲ့ ပြောင်းကြည့်နိုင်သည်။ " +
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
        // လက်ရှိ = current Rahu's wheel (head + all parts shifted with Rahu).
        val displayWheel = if (showTransit) r.transitWheel else r.wheel
        val moon1Part = displayWheel.partOf(moon1N28)
        val moon2Part = displayWheel.partOf(moon2N28)
        val verdict2 = RahuKalam.day2Verdict(moon2Part)

        // Circular wheel — one set at a time (no overlap):
        // ဇာတာ (natal wheel, filled) or လက်ရှိ (transit wheel, hollow),
        // like the D1/D9 switch; and two wheel styles (စာအုပ် / ZarTarPro).
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
                Button(
                    onClick = { showTransit = false },
                    modifier = Modifier.padding(end = 8.dp),
                    enabled = showTransit
                ) { Text("● ဇာတာ") }
                Button(
                    onClick = { showTransit = true },
                    enabled = !showTransit
                ) { Text("○ လက်ရှိ (now)") }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
                Button(
                    onClick = { wheelStyle = WheelStyle.BOOK },
                    modifier = Modifier.padding(end = 8.dp),
                    enabled = wheelStyle != WheelStyle.BOOK
                ) { Text("စာအုပ်") }
                Button(
                    onClick = { wheelStyle = WheelStyle.ZARTARPRO },
                    enabled = wheelStyle != WheelStyle.ZARTARPRO
                ) { Text("ZarTarPro") }
            }
            // The displayed planet set follows the mode (one set at a time).
            val displayPlanets = if (showTransit) r.transitPlanets else r.natalPlanets
            when (wheelStyle) {
                WheelStyle.BOOK -> RahuKalamWheel(
                    wheel = displayWheel,
                    natalPlanets = r.natalPlanets,
                    transitPlanets = r.transitPlanets,
                    showTransit = showTransit,
                    moon1N28 = moon1N28,
                    moon2N28 = moon2N28,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                )
                WheelStyle.ZARTARPRO -> RahuKalamWheelZarTarPro(
                    wheel = displayWheel,
                    displayPlanets = displayPlanets,
                    showTransit = showTransit,
                    moon2N28 = moon2N28,
                    dateLabel = "${birth.day}.${birth.month}.${birth.year} " +
                        "${birth.hour}:${birth.minute}:00",
                    placeLabel = birth.placeName,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                )
            }
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
                            "${RahuKalam.name28(transitRahuN28)} (နံပါတ် $transitRahuN28) — ဦးခေါင်း အစ"
                        } else {
                            "${RahuKalam.name28(rahuN28)} (နံပါတ် $rahuN28) — ဦးခေါင်း အစ"
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
                        RahuKalam.WheelPart.ANUS, RahuKalam.WheelPart.HIPS
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

        // Planets: natal vs now (transit), with nakshatra + pada + သရ.
        item {
            Text("ဂြိုလ် — ဇာတာ နှင့် လက်ရှိ", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(4.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                        Text("ဂြိုလ်", Modifier.weight(0.7f), fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium)
                        Text("ဇာတာ", Modifier.weight(1.15f), fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium)
                        Text("လက်ရှိ (now)", Modifier.weight(1.15f), fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    HorizontalDivider()
                    val planetMm = mapOf(
                        "Sun" to "နေ", "Moon" to "လ", "Mars" to "အင်္ဂါ",
                        "Mercury" to "ဗုဒ္ဓဟူး", "Jupiter" to "ကြာသပတေး",
                        "Venus" to "သောကြာ", "Saturn" to "စနေ",
                        "Rahu" to "ရာဟု", "Ketu" to "ကေတု"
                    )
                    val natalByName = r.natalPlanets.toMap()
                    val transitByName = r.transitPlanets.toMap()
                    val order = listOf(
                        "Sun", "Moon", "Mars", "Mercury", "Jupiter",
                        "Venus", "Saturn", "Rahu", "Ketu"
                    )
                    order.forEachIndexed { idx, name ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                            Text(planetMm[name] ?: name, Modifier.weight(0.7f),
                                style = MaterialTheme.typography.bodyMedium)
                            Text(planetCell(natalByName[name]), Modifier.weight(1.15f),
                                style = MaterialTheme.typography.bodyMedium)
                            Text(planetCell(transitByName[name]), Modifier.weight(1.15f),
                                style = MaterialTheme.typography.bodyMedium)
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
                    RahuKalam.DASAGGA_GROUPS.forEachIndexed { idx, group ->
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
                        if (idx < RahuKalam.DASAGGA_GROUPS.size - 1) HorizontalDivider()
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

/** One planet's cell: nakshatra name (number) · pada number (သရ). */
private fun planetCell(lon: Double?): String {
    if (lon == null) return "—"
    val (n28, pada) = RahuKalam.padaOf(lon)
    val syllable = RahuKalam.syllableOf(lon)
    val padaText = if (n28 == 22) "ပါဒ" else "$pada ပါဒ"
    return "${RahuKalam.name28(n28)} (${RahuKalam.burmeseNum(n28)}) · " +
        "$padaText ($syllable)"
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
 * bottom, head left, chest top-right, belly bottom-right, hips
 * bottom-left), a red centre dot, and pink မော / ဇီဝ boxes at the
 * left / right vertices. Planet markers (natal filled, now hollow,
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
        RahuKalam.DASAGGA_GROUPS.forEach { group ->
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
        RahuKalam.DASAGGA_GROUPS.forEach { group ->
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
            RahuKalam.WheelPart.HIPS to diamondYellow
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
            RahuKalam.WheelPart.HIPS to "အပြီး"
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

        // ---- Pink မော / ဇီဝ boxes at the left / right vertices ----
        fun vertexBox(angleDeg: Double, text: String) {
            val vx = px(D, angleDeg)
            val vy = py(D, angleDeg)
            val bw = size.minDimension * 0.075f
            val bh = size.minDimension * 0.045f
            drawRect(
                color = trianglePink,
                topLeft = Offset(vx - bw / 2f, vy - bh / 2f),
                size = Size(bw, bh)
            )
            drawRect(
                color = Color.Black,
                topLeft = Offset(vx - bw / 2f, vy - bh / 2f),
                size = Size(bw, bh), style = Stroke(width = 2f)
            )
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.BLACK
                    textSize = size.minDimension * 0.024f
                    textAlign = android.graphics.Paint.Align.CENTER
                    isFakeBoldText = true
                }
                drawText(text, vx, vy + paint.textSize / 3f, paint)
            }
        }
        vertexBox(270.0, "မော")
        vertexBox(90.0, "ဇီဝ")

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


/**
 * Circular wheel, ZarTarPro style (concentric rings), outside -> in:
 * cream outer ring of 28 nakshatras (Burmese name + numeral, pada
 * ticks), coloured planet-marker circles with Burmese glyphs, Dasagga
 * group ring (10 coloured arcs, names along the arc), Sayagyi
 * body-part ring (6 coloured arcs), inner nakshatra ring, and centre
 * with app name, birth date+time, a parts grid, "Sidereal LAHIRI"
 * and place; planet longitudes in the four corners.
 * True 360° orientation: Ashwini at the top, clockwise; planet
 * markers sit at their EXACT calculated longitude (not just the cell
 * midpoint). One planet set at a time (natal filled / now hollow),
 * following the ဇာတာ / လက်ရှိ mode; corner longitudes and the centre
 * grid follow the DISPLAYED wheel too.
 */
@Composable
private fun RahuKalamWheelZarTarPro(
    wheel: RahuKalam.SayagyiWheel,
    displayPlanets: List<Pair<String, Double>>,
    showTransit: Boolean,
    moon2N28: Int,
    dateLabel: String,
    placeLabel: String,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val R = size.minDimension / 2f
        val cream = Color(0xFFFFF8E1)
        val creamDark = Color(0xFFFFF3C4)

        fun rad(degClockwiseFromTop: Double) =
            Math.toRadians(degClockwiseFromTop - 90.0)
        fun px(r: Float, deg: Double) = cx + (r * cos(rad(deg))).toFloat()
        fun py(r: Float, deg: Double) = cy + (r * sin(rad(deg))).toFloat()
        fun cellMid(n28: Int) = (n28 - 1) * 360.0 / 28.0 + 360.0 / 56.0

        // ---- Corner planet longitudes (ZarTarPro style) ----
        // From the DISPLAYED planet set (natal in ဇာတာ, now in လက်ရှိ).
        fun planetLabel(lon: Double): String {
            val sign = SIGNS[((lon / 30.0).toInt()) % 12]
            val within = lon % 30.0
            val deg = floor(within).toInt()
            val min = floor((within - deg) * 60.0).toInt()
            return "${sign.mm} $deg°$min'"
        }
        val lonByName = displayPlanets.toMap()
        fun cornerLine(name: String): String? {
            val lon = lonByName[name] ?: return null
            return "${PLANET_GLYPHS[name]} ${planetLabel(lon)}"
        }
        drawContext.canvas.nativeCanvas.apply {
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.BLACK
                textSize = size.minDimension * 0.021f
                isFakeBoldText = true
            }
            val lineH = paint.textSize * 1.35f
            val x0 = size.minDimension * 0.015f
            val x1 = size.width - size.minDimension * 0.015f
            // Top-left: Sun, Moon, Mars. Top-right: Mercury, Jupiter.
            // Bottom-left: Venus, Saturn. Bottom-right: Rahu, Ketu.
            val tl = listOf("Sun", "Moon", "Mars").mapNotNull(::cornerLine)
            val tr = listOf("Mercury", "Jupiter").mapNotNull(::cornerLine)
            val bl = listOf("Venus", "Saturn").mapNotNull(::cornerLine)
            val br = listOf("Rahu", "Ketu").mapNotNull(::cornerLine)
            paint.textAlign = android.graphics.Paint.Align.LEFT
            tl.forEachIndexed { i, s -> drawText(s, x0, lineH * (i + 1), paint) }
            bl.forEachIndexed { i, s ->
                drawText(s, x0, size.height - lineH * (bl.size - i), paint)
            }
            paint.textAlign = android.graphics.Paint.Align.RIGHT
            tr.forEachIndexed { i, s -> drawText(s, x1, lineH * (i + 1), paint) }
            br.forEachIndexed { i, s ->
                drawText(s, x1, size.height - lineH * (br.size - i), paint)
            }
        }

        // ---- Outer ring: 28 nakshatra cells (cream, name + numeral) ----
        val outerR = R * 0.84f
        val outerW = R * 0.12f
        for (n in 1..28) {
            val start = (n - 1) * 360f / 28f - 90f
            drawArc(
                color = cream, startAngle = start, sweepAngle = 360f / 28f,
                useCenter = false,
                topLeft = Offset(cx - outerR, cy - outerR),
                size = Size(outerR * 2, outerR * 2),
                style = Stroke(width = outerW)
            )
        }
        // Dividers + pada ticks (3 per cell) on the outer ring.
        for (n in 1..28) {
            val base = (n - 1) * 360.0 / 28.0
            for (p in 0..3) {
                val deg = base + p * 360.0 / 28.0 / 4.0
                val isBoundary = p == 0
                val r1 = outerR - outerW / 2f
                val r2 = outerR + outerW / 2f
                drawLine(
                    color = if (isBoundary) Color.Black else Color(0xFFBDBDBD),
                    start = Offset(px(r1, deg), py(r1, deg)),
                    end = Offset(px(r2, deg), py(r2, deg)),
                    strokeWidth = if (isBoundary) 2f else 1f
                )
            }
            // Name + Burmese numeral, horizontal at the cell midpoint.
            val mid = cellMid(n)
            val tx = px(outerR, mid)
            val ty = py(outerR, mid)
            drawContext.canvas.nativeCanvas.apply {
                val namePaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.BLACK
                    textSize = size.minDimension * 0.021f
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                val numPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.DKGRAY
                    textSize = size.minDimension * 0.018f
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                drawText(RahuKalam.name28(n), tx, ty - namePaint.textSize * 0.15f, namePaint)
                drawText(
                    RahuKalam.burmeseNum(n), tx, ty + numPaint.textSize * 1.15f, numPaint
                )
            }
        }

        // ---- Planet markers: ONE set only (no overlap) ----
        // Each marker carries its pada syllable (သရ) just clockwise of it.
        // Markers sit at their exact calculated longitude, not the cell
        // midpoint — accurate 360° rendering.
        val markerR = R * 0.73f
        fun planetMarker(name: String, lon: Double, filled: Boolean) {
            val pos = Offset(px(markerR, lon), py(markerR, lon))
            val color = PLANET_COLORS[name] ?: Color.Gray
            val radius = size.minDimension * 0.022f
            if (filled) {
                drawCircle(color = color, radius = radius, center = pos)
                drawCircle(
                    color = Color.White, radius = radius, center = pos,
                    style = Stroke(width = 2f)
                )
            } else {
                drawCircle(color = Color.White, radius = radius, center = pos)
                drawCircle(
                    color = color, radius = radius, center = pos,
                    style = Stroke(width = 3f)
                )
            }
            drawContext.canvas.nativeCanvas.apply {
                val glyphPaint = android.graphics.Paint().apply {
                    this.color = if (filled) {
                        android.graphics.Color.WHITE
                    } else {
                        // Hollow (transit) marker: glyph in the planet colour.
                        android.graphics.Color.rgb(
                            (color.red * 255).toInt(),
                            (color.green * 255).toInt(),
                            (color.blue * 255).toInt()
                        )
                    }
                    textSize = size.minDimension * 0.024f
                    textAlign = android.graphics.Paint.Align.CENTER
                    isFakeBoldText = true
                }
                drawText(
                    PLANET_GLYPHS[name] ?: "?", pos.x,
                    pos.y + glyphPaint.textSize / 3f, glyphPaint
                )
                // သရ, just clockwise of the marker (tangent direction).
                val theta = Math.toRadians(lon)
                val sx = pos.x + (R * 0.065f * cos(theta)).toFloat()
                val sy = pos.y + (R * 0.065f * sin(theta)).toFloat()
                val saraPaint = android.graphics.Paint().apply {
                    this.color = android.graphics.Color.BLACK
                    textSize = size.minDimension * 0.017f
                    textAlign = android.graphics.Paint.Align.CENTER
                    isFakeBoldText = true
                }
                drawText(
                    RahuKalam.syllableOf(lon), sx,
                    sy + saraPaint.textSize / 3f, saraPaint
                )
            }
        }
        displayPlanets.forEach { (name, lon) ->
            planetMarker(name, lon, filled = !showTransit)
        }
        // Current Moon day 1 (blue ring) / day 2 (green ring) — only in
        // လက်ရှိ mode, since day 1 / day 2 Moon is the current Moon.
        if (showTransit) {
            val displayMoonLon = displayPlanets.firstOrNull { it.first == "Moon" }?.second
            if (displayMoonLon != null) {
                val pos = Offset(
                    px(markerR, displayMoonLon), py(markerR, displayMoonLon)
                )
                drawCircle(
                    color = Color(0xFF1565C0), radius = size.minDimension * 0.029f,
                    center = pos, style = Stroke(width = 3f)
                )
            }
            val moon2Pos = Offset(
                px(markerR, cellMid(moon2N28)), py(markerR, cellMid(moon2N28))
            )
            drawCircle(
                color = Color(0xFF2E7D32), radius = size.minDimension * 0.029f,
                center = moon2Pos, style = Stroke(width = 3f)
            )
        }

        // ---- Dasagga group ring (10 coloured arcs, names along the arc) ----
        val groupR = R * 0.58f
        val groupW = R * 0.10f
        RahuKalam.DASAGGA_GROUPS.forEachIndexed { idx, group ->
            val first = group.numbers.first()
            val start = (first - 1) * 360f / 28f - 90f
            val sweep = group.numbers.size * 360f / 28f
            drawArc(
                color = GROUP_COLORS[idx % GROUP_COLORS.size],
                startAngle = start, sweepAngle = sweep, useCenter = false,
                topLeft = Offset(cx - groupR, cy - groupR),
                size = Size(groupR * 2, groupR * 2),
                style = Stroke(width = groupW)
            )
            // Divider at the group's first nakshatra.
            drawLine(
                color = Color.Black,
                start = Offset(px(groupR - groupW / 2f, (first - 1) * 360.0 / 28.0),
                    py(groupR - groupW / 2f, (first - 1) * 360.0 / 28.0)),
                end = Offset(px(groupR + groupW / 2f, (first - 1) * 360.0 / 28.0),
                    py(groupR + groupW / 2f, (first - 1) * 360.0 / 28.0)),
                strokeWidth = 2f
            )
            drawArcLabel(
                group.mm,
                (first - 1) * 360.0 / 28.0 + group.numbers.size * 360.0 / 56.0,
                groupR, size.minDimension * 0.022f, cx, cy, bold = true
            )
        }

        // ---- Sayagyi body-part ring (6 coloured arcs, names along the arc) ----
        val partR = R * 0.46f
        val partW = R * 0.10f
        wheel.parts.forEach { entry ->
            val first = entry.numbers.first()
            val start = (first - 1) * 360f / 28f - 90f
            val sweep = entry.numbers.size * 360f / 28f
            drawArc(
                color = PART_COLORS[entry.part] ?: Color.LightGray,
                startAngle = start, sweepAngle = sweep, useCenter = false,
                topLeft = Offset(cx - partR, cy - partR),
                size = Size(partR * 2, partR * 2),
                style = Stroke(width = partW)
            )
            drawLine(
                color = Color.Black,
                start = Offset(px(partR - partW / 2f, (first - 1) * 360.0 / 28.0),
                    py(partR - partW / 2f, (first - 1) * 360.0 / 28.0)),
                end = Offset(px(partR + partW / 2f, (first - 1) * 360.0 / 28.0),
                    py(partR + partW / 2f, (first - 1) * 360.0 / 28.0)),
                strokeWidth = 2f
            )
            drawArcLabel(
                entry.part.mm,
                (first - 1) * 360.0 / 28.0 + entry.numbers.size * 360.0 / 56.0,
                partR, size.minDimension * 0.024f, cx, cy, bold = true
            )
        }

        // ---- Inner ring: 28 nakshatra cells (Burmese numerals) ----
        val innerR = R * 0.35f
        val innerW = R * 0.08f
        for (n in 1..28) {
            val start = (n - 1) * 360f / 28f - 90f
            drawArc(
                color = creamDark, startAngle = start, sweepAngle = 360f / 28f,
                useCenter = false,
                topLeft = Offset(cx - innerR, cy - innerR),
                size = Size(innerR * 2, innerR * 2),
                style = Stroke(width = innerW)
            )
            val mid = cellMid(n)
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.BLACK
                    textSize = size.minDimension * 0.019f
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                drawText(
                    RahuKalam.burmeseNum(n), px(innerR, mid),
                    py(innerR, mid) + paint.textSize / 3f, paint
                )
            }
        }

        // ---- Centre: app name, birth date+time, parts grid, Sidereal LAHIRI, place ----
        val centerR = R * 0.30f
        drawCircle(
            color = Color(0xFFFFFDF5), radius = centerR, center = Offset(cx, cy)
        )
        drawCircle(
            color = Color.Black, radius = centerR, center = Offset(cx, cy),
            style = Stroke(width = 2f)
        )
        drawContext.canvas.nativeCanvas.apply {
            val namePaint = android.graphics.Paint().apply {
                color = android.graphics.Color.BLACK
                textSize = size.minDimension * 0.028f
                textAlign = android.graphics.Paint.Align.CENTER
                isFakeBoldText = true
            }
            val subPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.BLACK
                textSize = size.minDimension * 0.024f
                textAlign = android.graphics.Paint.Align.CENTER
            }
            val smallPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.DKGRAY
                textSize = size.minDimension * 0.020f
                textAlign = android.graphics.Paint.Align.CENTER
            }
            drawText("ဗေဒင်", cx, cy - R * 0.235f, namePaint)
            drawText(dateLabel, cx, cy - R * 0.165f, subPaint)
            drawText("Sidereal LAHIRI", cx, cy + R * 0.195f, smallPaint)
            if (placeLabel.isNotEmpty()) {
                drawText(placeLabel, cx, cy + R * 0.245f, smallPaint)
            }
        }
        drawCentreGrid(wheel, cx, cy, R)
    }
}

/**
 * Centre grid: 6 columns (the Sayagyi parts, head first) x 8 rows
 * (header + up to 7 nakshatra numbers, Burmese numerals; Abhijit as X).
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCentreGrid(
    wheel: RahuKalam.SayagyiWheel, cx: Float, cy: Float, R: Float
) {
    val order = listOf(
        RahuKalam.WheelPart.HEAD, RahuKalam.WheelPart.MOUTH,
        RahuKalam.WheelPart.CHEST, RahuKalam.WheelPart.BELLY,
        RahuKalam.WheelPart.ANUS, RahuKalam.WheelPart.HIPS
    )
    val headers = mapOf(
        RahuKalam.WheelPart.HEAD to "ဦး", RahuKalam.WheelPart.MOUTH to "ခံ",
        RahuKalam.WheelPart.CHEST to "ရင်", RahuKalam.WheelPart.BELLY to "ဝမ်း",
        RahuKalam.WheelPart.ANUS to "စအို", RahuKalam.WheelPart.HIPS to "အပြီး"
    )
    val byPart = wheel.parts.associateBy { it.part }
    val gridW = R * 0.52f
    val gridH = R * 0.26f
    val x0 = cx - gridW / 2f
    val y0 = cy - gridH / 2f + R * 0.01f
    val cellW = gridW / 6f
    val cellH = gridH / 8f
    // Outer border + dividers.
    drawRect(
        color = Color.Black, topLeft = Offset(x0, y0), size = Size(gridW, gridH),
        style = Stroke(width = 1.5f)
    )
    for (c in 1 until 6) {
        drawLine(
            color = Color.Black, start = Offset(x0 + c * cellW, y0),
            end = Offset(x0 + c * cellW, y0 + gridH), strokeWidth = 1f
        )
    }
    for (r in 1 until 8) {
        drawLine(
            color = Color.Black, start = Offset(x0, y0 + r * cellH),
            end = Offset(x0 + gridW, y0 + r * cellH), strokeWidth = 1f
        )
    }
    drawContext.canvas.nativeCanvas.apply {
        val headPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = size.minDimension * 0.016f
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
        }
        val cellPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = size.minDimension * 0.016f
            textAlign = android.graphics.Paint.Align.CENTER
        }
        order.forEachIndexed { ci, part ->
            val hx = x0 + (ci + 0.5f) * cellW
            val hy = y0 + cellH * 0.5f + headPaint.textSize / 3f
            drawText(headers[part] ?: "", hx, hy, headPaint)
            val entry = byPart[part] ?: return@forEachIndexed
            entry.numbers.forEachIndexed { ri, n28 ->
                val tx = hx
                val ty = y0 + (ri + 1.5f) * cellH + cellPaint.textSize / 3f
                drawText(
                    if (n28 == 22) "X" else RahuKalam.burmeseNum(n28),
                    tx, ty, cellPaint
                )
            }
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
