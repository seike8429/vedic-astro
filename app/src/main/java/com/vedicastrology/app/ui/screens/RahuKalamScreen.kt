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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.cos
import kotlin.math.sin

/**
 * Rahu Kalam tab — Sayagyi (ဆရာကြီး) wheel, from the book
 * မြန်မာ့ရိုးရာ သရေခေတ္တရာဟုကာလ (Vol. 2).
 *
 * The wheel is built from the NATAL chart (the chart currently open):
 * the head (ဦးခေါင်း) starts at the nakshatra Rahu occupies natally,
 * and the rest follow in order — head 7, mouth 1, chest 7, belly 6,
 * anus 1, hips 6. The daily reading uses the CURRENT Moon: day 1 =
 * today, day 2 = tomorrow.
 *
 * Layout follows the circular-wheel style of the reference chart the
 * author showed (ZarTarPro "Rahukalana 2026"): outer ring = 28
 * nakshatras (name + number), middle ring = the 6 body parts
 * (coloured), markers for the natal planets and for the current Moon
 * (day 1 / day 2), centre = head nakshatra + chart + BIRTH date
 * (ZarTarPro shows the birth date in the centre — the wheel is natal,
 * so today's date belongs in the summary, not the centre). Detail
 * table below. Our rendering is our own.
 */

/** One computed reading for the screen. */
private data class Reading(
    val day1Millis: Long,
    val day2Millis: Long,
    val natalRahuLon: Double,
    val natalPlanets: List<Pair<String, Int>>,
    val moon1Lon: Double,
    val moon2Lon: Double,
    val wheel: RahuKalam.SayagyiWheel,
    val useTrueRahu: Boolean
)

/** Short labels for the natal-planet markers. */
private val PLANET_LABELS = mapOf(
    "Sun" to "Su", "Moon" to "Mo", "Mars" to "Ma", "Mercury" to "Me",
    "Jupiter" to "Ju", "Venus" to "Ve", "Saturn" to "Sa",
    "Rahu" to "Ra", "Ketu" to "Ke"
)

@Composable
fun RahuKalamScreen(natal: ChartResult, birth: BirthData, chartName: String, useTrueRahu: Boolean) {
    var refreshKey by remember { mutableIntStateOf(0) }
    var reading by remember { mutableStateOf<Reading?>(null) }
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
                    .filter { it.row.planet in PLANET_LABELS }
                    .map { (PLANET_LABELS[it.row.planet] ?: "?") to
                        RahuKalam.nakshatra28(it.row.longitude) }
                val now = System.currentTimeMillis()
                val jd1 = RahuKalam.jdFromMillis(now)
                val jd2 = RahuKalam.jdFromMillis(now + 24L * 60 * 60 * 1000)
                val moon1 = Ephemeris.planetPosition(jd1, "Moon", useTrueRahu).first
                val moon2 = Ephemeris.planetPosition(jd2, "Moon", useTrueRahu).first
                Reading(
                    now, now + 24L * 60 * 60 * 1000, rahuLon, planets,
                    moon1, moon2, RahuKalam.sayagyiWheel(rahuLon), useTrueRahu
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
                    "ဇာတာမှာ ရာဟု စီးတဲ့ နက္ခတ်က ဦးခေါင်း၊ ကျန်အပိုင်းတွေ အစဉ်အတိုင်း။ " +
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
        val moon1N28 = RahuKalam.nakshatra28(r.moon1Lon)
        val moon2N28 = RahuKalam.nakshatra28(r.moon2Lon)
        val moon1Part = r.wheel.partOf(moon1N28)
        val moon2Part = r.wheel.partOf(moon2N28)
        val verdict2 = RahuKalam.day2Verdict(moon2Part)

        // Circular wheel.
        item {
            RahuKalamWheel(
                wheel = r.wheel,
                natalPlanets = r.natalPlanets,
                moon1N28 = moon1N28,
                moon2N28 = moon2N28,
                chartName = chartName,
                // Centre shows the BIRTH date (ZarTarPro does the same);
                // today/tomorrow live in the summary card below.
                dateLabel = String.format(
                    Locale.US, "%02d/%02d/%04d", birth.day, birth.month, birth.year
                ),
                modifier = Modifier.fillMaxWidth(0.96f).aspectRatio(1f)
            )
            Spacer(Modifier.height(12.dp))
        }

        // Today / tomorrow summary.
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "ဇာတာ ရာဟု (${if (r.useTrueRahu) "True" else "Mean"}): " +
                            "${RahuKalam.name28(rahuN28)} (နံပါတ် $rahuN28) — ဦးခေါင်း အစ",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "၁ နေ့ (${mmtFmt.format(Date(r.day1Millis))}): လ — " +
                            "${RahuKalam.name28(moon1N28)} (နံပါတ် $moon1N28)၊ " +
                            "အပိုင်း ${moon1Part.mm}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "၂ လာ (${mmtFmt.format(Date(r.day2Millis))}): လ — " +
                            "${RahuKalam.name28(moon2N28)} (နံပါတ် $moon2N28)၊ " +
                            "အပိုင်း ${moon2Part.mm}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "၂ လာ အဟော (ယေဘုယျ): ${verdict2.mm} — " +
                            if (verdict2.good) {
                                "အပိုင်း ${moon2Part.mm} မှာ လ ရောက်တဲ့အခါ အလုပ်အောင်မြင်၊ အိမ်ထောင်ရေး ကောင်း။"
                            } else {
                                "အပိုင်း ${moon2Part.mm} မှာ လ ရောက်တဲ့အခါ အလုပ်မှာ အခက်အခဲ၊ အိမ်ထောင်ရေး မတည်။"
                            },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // The six parts, in construction order (head first, from Rahu).
        item {
            Text("စက်ဝိုင်း ၆ ပိုင်း", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
        }
        val displayOrder = listOf(
            RahuKalam.WheelPart.HEAD, RahuKalam.WheelPart.MOUTH,
            RahuKalam.WheelPart.CHEST, RahuKalam.WheelPart.BELLY,
            RahuKalam.WheelPart.ANUS, RahuKalam.WheelPart.HIPS
        )
        val byPart = r.wheel.parts.associateBy { it.part }
        displayOrder.forEach { part ->
            val entry = byPart[part] ?: return@forEach
            item {
                val isMoon1 = moon1N28 in entry.numbers
                val isMoon2 = moon2N28 in entry.numbers
                val marks = buildString {
                    if (isMoon1) append("  ← လ (၁ နေ့)")
                    if (isMoon2) append("  ← လ (၂ လာ)")
                }
                Text(
                    "${entry.part.mm}: " +
                        entry.numbers.joinToString(", ") { n ->
                            "${RahuKalam.name28(n)} ($n)"
                        } + marks,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isMoon1 || isMoon2) FontWeight.Bold else null,
                    modifier = Modifier.padding(vertical = 3.dp)
                )
            }
        }
        item {
            Spacer(Modifier.height(12.dp))
            Text(
                "အဟော အပြည့်အစုံ (အပိုင်းအတွဲ × ၅ ခေါင်းစဉ် — စီးပွားရေး၊ ပညာရေး၊ " +
                    "ကျန်းမာရေး၊ အိမ်ထောင်ရေး၊ အချစ်အဆိုင်) ကို စာအုပ်ကနေ နောက်ဗားရှင်းမှာ ထည့်ပါမည်။",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { refreshKey++ }) { Text("ပြန်တွက်") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Circular Sayagyi wheel, ZarTarPro-style: outer ring = 28 nakshatra
 * cells (Burmese name + number), middle ring = 6 body parts (coloured
 * arcs at each part's own nakshatra angles), markers = natal planets
 * (labelled dots) and the current Moon day 1 (blue) / day 2 (green),
 * centre = head nakshatra + chart + date.
 * Angles: nakshatra n occupies [(n-1) * 360/28, n * 360/28), measured
 * clockwise from the top.
 */
@Composable
private fun RahuKalamWheel(
    wheel: RahuKalam.SayagyiWheel,
    natalPlanets: List<Pair<String, Int>>,
    moon1N28: Int,
    moon2N28: Int,
    chartName: String,
    dateLabel: String,
    modifier: Modifier = Modifier
) {
    val partColors = mapOf(
        RahuKalam.WheelPart.HEAD to Color(0xFFE57373),
        RahuKalam.WheelPart.MOUTH to Color(0xFFFFD54F),
        RahuKalam.WheelPart.CHEST to Color(0xFF81C784),
        RahuKalam.WheelPart.BELLY to Color(0xFF64B5F6),
        RahuKalam.WheelPart.ANUS to Color(0xFFBA68C8),
        RahuKalam.WheelPart.HIPS to Color(0xFFFFB74D)
    )
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val outerR = size.minDimension / 2f
        val partR = outerR * 0.70f
        val centerR = outerR * 0.32f

        // Outer ring: 28 nakshatra cells, name + number.
        val bandR = outerR * 0.92f
        for (n in 1..28) {
            val start = (n - 1) * 360f / 28f - 90f
            drawArc(
                color = if (n == wheel.headStart28) Color(0xFFFFCDD2) else Color(0xFFFFF8E1),
                startAngle = start,
                sweepAngle = 360f / 28f,
                useCenter = false,
                topLeft = Offset(cx - bandR, cy - bandR),
                size = Size(bandR * 2, bandR * 2),
                style = Stroke(width = outerR * 0.16f)
            )
            val mid = Math.toRadians((start + 360f / 56f).toDouble())
            val tx = cx + (bandR * cos(mid)).toFloat()
            val ty = cy + (bandR * sin(mid)).toFloat()
            drawContext.canvas.nativeCanvas.apply {
                val namePaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.BLACK
                    textSize = size.minDimension * 0.024f
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                val numPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.DKGRAY
                    textSize = size.minDimension * 0.020f
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                drawText(RahuKalam.name28(n), tx, ty - namePaint.textSize * 0.1f, namePaint)
                drawText("($n)", tx, ty + numPaint.textSize * 1.1f, numPaint)
            }
        }
        // Middle ring: 6 body parts, coloured arcs at their own angles.
        wheel.parts.forEach { entry ->
            val first = entry.numbers.first()
            val start = (first - 1) * 360f / 28f - 90f
            val sweep = entry.numbers.size * 360f / 28f
            drawArc(
                color = partColors[entry.part] ?: Color.LightGray,
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(cx - partR, cy - partR),
                size = Size(partR * 2, partR * 2),
                style = Stroke(width = partR * 0.26f)
            )
            val mid = Math.toRadians((start + sweep / 2f).toDouble())
            val tx = cx + (partR * cos(mid)).toFloat()
            val ty = cy + (partR * sin(mid)).toFloat()
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.BLACK
                    textSize = size.minDimension * 0.030f
                    textAlign = android.graphics.Paint.Align.CENTER
                    isFakeBoldText = true
                }
                drawText(entry.part.mm, tx, ty + paint.textSize / 3f, paint)
            }
        }
        // Centre circle: head nakshatra + chart + date.
        drawCircle(
            color = Color(0xFFFFF3E0),
            radius = centerR,
            center = Offset(cx, cy),
            style = Stroke(width = 2f)
        )
        drawContext.canvas.nativeCanvas.apply {
            val headPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.BLACK
                textSize = size.minDimension * 0.036f
                textAlign = android.graphics.Paint.Align.CENTER
                isFakeBoldText = true
            }
            val subPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.BLACK
                textSize = size.minDimension * 0.028f
                textAlign = android.graphics.Paint.Align.CENTER
            }
            val smallPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.DKGRAY
                textSize = size.minDimension * 0.024f
                textAlign = android.graphics.Paint.Align.CENTER
            }
            drawText("ဦးခေါင်း", cx, cy - headPaint.textSize * 0.9f, headPaint)
            drawText(
                RahuKalam.name28(wheel.headStart28) + " (${wheel.headStart28})",
                cx, cy + subPaint.textSize * 0.4f, subPaint
            )
            if (chartName.isNotEmpty()) {
                drawText(chartName, cx, cy + subPaint.textSize * 1.5f, smallPaint)
            }
            drawText("$dateLabel · Lahiri", cx, cy + subPaint.textSize * 2.5f, smallPaint)
        }
        // Markers: natal planets (labelled dots) + current Moon day 1/2.
        fun markerAngle(n28: Int): Double =
            Math.toRadians((n28 - 1) * 360.0 / 28.0 + 360.0 / 56.0 - 90.0)
        natalPlanets.forEach { (label, n28) ->
            val mid = markerAngle(n28)
            val x = cx + (partR * cos(mid)).toFloat()
            val y = cy + (partR * sin(mid)).toFloat()
            drawCircle(
                color = Color(0xFF5D4037), radius = size.minDimension * 0.016f,
                center = Offset(x, y)
            )
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.BLACK
                    textSize = size.minDimension * 0.024f
                    textAlign = android.graphics.Paint.Align.CENTER
                    isFakeBoldText = true
                }
                drawText(label, x, y - size.minDimension * 0.022f, paint)
            }
        }
        fun moonMarker(n28: Int, color: Color) {
            val mid = markerAngle(n28)
            val x = cx + (partR * cos(mid)).toFloat()
            val y = cy + (partR * sin(mid)).toFloat()
            drawCircle(color = color, radius = size.minDimension * 0.020f, center = Offset(x, y))
            drawCircle(
                color = Color.White, radius = size.minDimension * 0.020f,
                center = Offset(x, y), style = Stroke(width = 2f)
            )
        }
        moonMarker(moon1N28, Color(0xFF1565C0))
        moonMarker(moon2N28, Color(0xFF2E7D32))
    }
    // Legend.
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(
            "● ဇာတာ ဂြိုလ် (Su Mo Ma Me Ju Ve Sa Ra Ke)",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF5D4037), modifier = Modifier.padding(end = 12.dp)
        )
        Text(
            "● လ ၁ နေ့", style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF1565C0), modifier = Modifier.padding(end = 12.dp)
        )
        Text("● လ ၂ လာ", style = MaterialTheme.typography.bodySmall, color = Color(0xFF2E7D32))
    }
}
