package com.vedicastrology.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
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

/** Planet numbers for the markers (author's scheme 2026-11-06): Sun ၁, Moon ၂, Mars ၃, Mercury ၄, Jupiter ၅, Venus ၆, Saturn ၇, Rahu ၈, Ketu ၉. Replaces the old single-letter glyphs (e.g. Saturn န) — the author wants numbers, not letters. */
private val PLANET_GLYPHS = mapOf(
    "Sun" to "၁", "Moon" to "၂", "Mars" to "၃", "Mercury" to "၄",
    "Jupiter" to "၅", "Venus" to "၆", "Saturn" to "၇",
    "Rahu" to "၈", "Ketu" to "၉"
)

/** Marker colours (number distinguishes same colours). */
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
    // Wheel zoom (the wheel is small on a phone): pinch to zoom, or the
    // − / ＋ buttons; 1x resets. Pan only while zoomed in. The gesture
    // is detected manually (not transformable) so the pinch centroid
    // stays fixed — the wheel no longer jumps when pinching off-centre —
    // and the offset is clamped so the wheel can never be panned
    // off-screen or stick slightly zoomed and shifted.
    var wheelScale by remember { mutableFloatStateOf(1f) }
    var wheelOffset by remember { mutableStateOf(Offset.Zero) }
    // Clamp the pan offset so the zoomed wheel always covers the box
    // (no empty space, and the wheel can always be pinched back).
    fun clampWheelOffset(offset: Offset, scale: Float, size: IntSize): Offset {
        if (scale <= 1f || size.width == 0 || size.height == 0) return Offset.Zero
        val maxX = size.width * (scale - 1f) / 2f
        val maxY = size.height * (scale - 1f) / 2f
        return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
    }
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
                    "လ ရဲ့ အပိုင်း (လ ၁ နေ့ / လ ၂ လာ) ကို ကြည့်ပြီး အဟော ဖတ်သည်။",
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
            // Zoom controls (the wheel is small on a phone): − / ＋ and 1x reset.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        wheelScale = (wheelScale / 1.5f).coerceIn(1f, 4f)
                        if (wheelScale == 1f) wheelOffset = Offset.Zero
                    }
                ) { Text("−") }
                OutlinedButton(
                    onClick = { wheelScale = (wheelScale * 1.5f).coerceIn(1f, 4f) }
                ) { Text("＋") }
                OutlinedButton(
                    onClick = { wheelScale = 1f; wheelOffset = Offset.Zero }
                ) { Text("1x") }
                Text(
                    text = "Zoom ${"%.1f".format(wheelScale)}x — လက်နှစ်ချောင်းနဲ့ ဆွဲကြည့်လို့ရသည်",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(androidx.compose.ui.Alignment.CenterVertically)
                )
            }
            Spacer(Modifier.height(4.dp))
            // The displayed planet set follows the mode (one set at a time).
            // Pinch to zoom (centroid stays fixed), drag to pan while
            // zoomed in; a one-finger drag at 1x still scrolls the page.
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
            ) {
                // Box size (px) for the zoom math: pinch centre + clamping.
                val boxSize = IntSize(constraints.maxWidth, constraints.maxHeight)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(boxSize) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                // Own per-pointer tracking: previousPosition
                                // is unreliable for a just-down pointer, so
                                // seed each new pointer to its down position.
                                // Zoom is anchored to the pinch START
                                // (distance + scale when the 2nd finger
                                // lands), not to incremental moves — so a
                                // quick pinch zooms, and pinch-out cannot
                                // stall. Events are read in the Initial pass
                                // so the page scroll cannot swallow them.
                                val lastPos = mutableMapOf<PointerId, Offset>()
                                var prevCentroid = Offset.Zero
                                var havePrevCentroid = false
                                var pinching = false
                                var pinchStartDist = 0f
                                var pinchStartScale = 1f
                                var pinchCentroid = Offset.Zero
                                while (true) {
                                    val event = awaitPointerEvent(
                                        PointerEventPass.Initial
                                    )
                                    val beforeCount = lastPos.size
                                    for (change in event.changes) {
                                        if (change.pressed) {
                                            lastPos[change.id] =
                                                change.position
                                        } else {
                                            lastPos.remove(change.id)
                                        }
                                    }
                                    if (lastPos.isEmpty()) break
                                    val positions = lastPos.values.toList()
                                    val centroid = positions
                                        .reduce { a, b -> a + b } /
                                        positions.size.toFloat()
                                    // A finger landing or lifting moves the
                                    // centroid: reset the pan baseline so
                                    // that jump cannot pan or zoom the wheel.
                                    if (lastPos.size != beforeCount) {
                                        havePrevCentroid = false
                                        if (lastPos.size < 2) pinching = false
                                    }
                                    if (lastPos.size >= 2) {
                                        val avgDist = positions
                                            .map {
                                                (it - centroid).getDistance()
                                            }
                                            .average().toFloat()
                                        if (!pinching) {
                                            // Pinch just started: anchor.
                                            pinching = true
                                            pinchStartDist = avgDist
                                            pinchStartScale = wheelScale
                                            pinchCentroid = centroid
                                        } else if (pinchStartDist > 0f &&
                                            avgDist > 0f
                                        ) {
                                            val newScale = (pinchStartScale *
                                                avgDist / pinchStartDist)
                                                .coerceIn(1f, 4f)
                                            val factor = newScale / wheelScale
                                            val center = Offset(
                                                boxSize.width / 2f,
                                                boxSize.height / 2f
                                            )
                                            wheelOffset = wheelOffset * factor +
                                                (centroid - center) *
                                                (1f - factor) +
                                                (centroid - pinchCentroid)
                                            wheelScale = newScale
                                            pinchCentroid = centroid
                                            if (wheelScale <= 1.15f) {
                                                wheelScale = 1f
                                                wheelOffset = Offset.Zero
                                            } else {
                                                wheelOffset = clampWheelOffset(
                                                    wheelOffset, wheelScale,
                                                    boxSize
                                                )
                                            }
                                        }
                                        event.changes.forEach { it.consume() }
                                    } else if (lastPos.size == 1 &&
                                        wheelScale > 1f
                                    ) {
                                        // One finger while zoomed: pan, but
                                        // only with a baseline, so a finger
                                        // lifting mid-pinch cannot jump it.
                                        if (havePrevCentroid) {
                                            wheelOffset = clampWheelOffset(
                                                wheelOffset +
                                                    (centroid - prevCentroid),
                                                wheelScale, boxSize
                                            )
                                            event.changes.forEach {
                                                it.consume()
                                            }
                                        }
                                    }
                                    // One finger at 1x: not consumed, so
                                    // the page scrolls as normal.
                                    prevCentroid = centroid
                                    havePrevCentroid = true
                                }
                            }
                        }
                    .graphicsLayer(
                        scaleX = wheelScale,
                        scaleY = wheelScale,
                        translationX = wheelOffset.x,
                        translationY = wheelOffset.y
                    )
            ) {
                RahuKalamWheel(
                    wheel = displayWheel,
                    natalPlanets = r.natalPlanets,
                    transitPlanets = r.transitPlanets,
                    showTransit = showTransit,
                    moon1N28 = moon1N28,
                    moon2N28 = moon2N28,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f)
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
                        Text("နံပါတ်", Modifier.weight(1.2f), fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium)
                        Text("လ ၁ နေ့", Modifier.weight(0.9f), fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium)
                        Text("လ ၂ လာ", Modifier.weight(0.9f), fontWeight = FontWeight.Bold,
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
                                entry.numbers.joinToString(", ") { RahuKalam.displayNumLabel(it) },
                                Modifier.weight(1.2f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                if (moon1N28 in entry.numbers) "●" else "",
                                Modifier.weight(0.9f),
                                color = Color(0xFF1565C0),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                if (moon2N28 in entry.numbers) "●" else "",
                                Modifier.weight(0.9f),
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
                        Text("အဖွဲ့", Modifier.weight(0.6f), fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium)
                        Text("နက္ခတ်", Modifier.weight(1.7f), fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium)
                        Text("သရ", Modifier.weight(1.7f), fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    HorizontalDivider()
                    val displayGroups = RahuKalam.dasaggaGroups(displayWheel.mouthStart28)
                    displayGroups.forEachIndexed { gIdx, group ->
                        // One row per nakshatra (author's order): 3 nakshatras
                        // = 3 rows; each nakshatra's 4 သရ sit on its own row.
                        group.numbers.forEachIndexed { nIdx, n ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                                Text(
                                    if (nIdx == 0) group.mm else "",
                                    Modifier.weight(0.6f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    run {
                                        val d = RahuKalam.displayNum(n)
                                        if (d == null) RahuKalam.name28(n)
                                        else "${RahuKalam.name28(n)} (${RahuKalam.burmeseNum(d)})"
                                    },
                                    Modifier.weight(1.7f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    // 4 သရ per nakshatra (3 for Uttara
                                    // Ashadha, 1 for Abhijit), one row.
                                    RahuKalam.PADA_SYLLABLES[n - 1]
                                        .take(RahuKalam.padaCount(n))
                                        .joinToString(", "),
                                    Modifier.weight(1.7f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        if (gIdx < displayGroups.size - 1) HorizontalDivider()
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

/**
 * Circular wheel, EXACTLY like the book cover
 * (မြန်မာ့ရိုးရာ သရေခေတ္တရာဟုကာလ, Vol. 2, cover), outside -> in:
 * outermost white pada ring (108 pada syllables, သရ — e.g. Magha
 * မ မိ မု မေ), bright-yellow ring of 28 nakshatra NAMES (replacing
 * the numerals, v1.8.30 at the author's order),
 * mauve-pink Dasagga group ring (10 groups, names along the arc),
 * blue Dasagga group segments (one per group, names along the arc),
 * light-green ground, a yellow diamond divided into the 6 Sayagyi
 * parts (mouth pink triangle at the top, anus pink triangle at the
 * bottom, head left, chest top-right, belly bottom-right, tail
 * bottom-left), and a red centre dot. (The cover's pink မော / ဇီဝ
 * vertex boxes were removed in v1.8.29 at the author's order — they
 * are wrong.) No planet markers and no Moon rings on the wheel since
 * v1.8.42 at the author's order — the wheel shows nakshatra names +
 * pada syllables + the 6 parts only; planet positions stay in the
 * table below, and the Moon's part is shown by the dots in the
 * 6-part table below the wheel.
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
        // (markerAngle removed in v1.8.42 — no markers or Moon rings are
        // drawn on the wheel any more, so no longitude-to-angle mapping
        // for markers is needed. The true-nakshatra anchoring logic is
        // recorded in the project notes if markers ever return.)
        /** Distance from the centre to the diamond's edge at an angle. */
        fun diamondR(degClockwiseFromTop: Double): Float {
            val t = Math.toRadians(degClockwiseFromTop)
            return (D / (kotlin.math.abs(sin(t)) + kotlin.math.abs(cos(t)))).toFloat()
        }

        // ---- Green ground (whole wheel) ----
        drawCircle(color = coverGreen, radius = R * 0.99f, center = Offset(cx, cy))

        // ---- Outermost pada ring: 108 pada syllables (သရ), as in the book ----
        // One segment per pada (4 per nakshatra, 3 for Uttara Ashadha, 1 for
        // Abhijit), each showing its syllable — e.g. Magha: မ မိ မု မေ.
        val padaR = R * 0.962f
        val padaW = R * 0.05f
        val cellW = 360.0 / 28.0
        for (n in 1..28) {
            val start = cellStart(n)
            val count = RahuKalam.padaCount(n)
            for (p in 0 until count) {
                val pStart = start + p * cellW / count
                val pSweep = cellW / count
                drawArc(
                    color = Color.White, startAngle = (pStart - 90f).toFloat(),
                    sweepAngle = pSweep.toFloat(), useCenter = false,
                    topLeft = Offset(cx - padaR, cy - padaR),
                    size = Size(padaR * 2, padaR * 2),
                    style = Stroke(width = padaW)
                )
                drawLine(
                    color = Color.Black,
                    start = Offset(px(padaR - padaW / 2f, pStart), py(padaR - padaW / 2f, pStart)),
                    end = Offset(px(padaR + padaW / 2f, pStart), py(padaR + padaW / 2f, pStart)),
                    strokeWidth = 1.5f
                )
                val mid = norm360(pStart + pSweep / 2.0)
                drawArcLabel(
                    RahuKalam.PADA_SYLLABLES[n - 1][p], mid, padaR,
                    size.minDimension * 0.013f, cx, cy, bold = false
                )
            }
        }
        // Outer + inner boundary circles of the pada ring.
        drawCircle(
            color = Color.Black, radius = R * 0.987f, center = Offset(cx, cy),
            style = Stroke(width = 2f)
        )
        drawCircle(
            color = Color.Black, radius = R * 0.937f, center = Offset(cx, cy),
            style = Stroke(width = 2f)
        )

        // ---- Yellow ring: 28 nakshatra NAMES (replacing numerals) ----
        val outerR = R * 0.90f
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
            // Nakshatra name, along the arc.
            drawArcLabel(
                RahuKalam.name28(n), cellAngle(n), R * 0.92f,
                size.minDimension * 0.013f, cx, cy, bold = false
            )
        }
        // Outer + inner boundary circles of the yellow ring.
        drawCircle(
            color = Color.Black, radius = R * 0.935f, center = Offset(cx, cy),
            style = Stroke(width = 2f)
        )
        drawCircle(
            color = Color.Black, radius = R * 0.865f, center = Offset(cx, cy),
            style = Stroke(width = 2f)
        )

        // ---- Pink Dasagga group ring (10 groups, names along the arc) ----
        val groupR = R * 0.83f
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
            color = Color.Black, radius = R * 0.795f, center = Offset(cx, cy),
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
                    val p = Offset(px(R * 0.795f, a), py(R * 0.795f, a))
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
            val midR = (R * 0.795f + diamondR(mid)) / 2f
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

        // ---- No planet markers and no Moon rings on the wheel ----
        // (author's order, v1.8.42): the wheel shows nakshatra names +
        // pada syllables (သရ) + the 6 parts only. Planet positions stay
        // in the planets table below; the Moon's part is shown by the
        // dots in the 6-part table below the wheel.
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
