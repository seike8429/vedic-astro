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
 * Rahu Kalam tab - Sayagyi (ဆရာကြီး) wheel, from the book
 * မြန်မာ့ရိုးရာ သရေခေတ္တရာဟုကာလ (Vol. 2).
 *
 * Layout follows the circular-wheel style of the reference chart the
 * author showed (ZarTarPro "Rahukalana 2026"): a circular wheel with
 * concentric rings, planet markers and centre info, with the detail
 * table below. Our rendering is our own: outer ring = 28 nakshatras,
 * middle ring = the 6 body parts, markers for Rahu (mouth) and the
 * Moon (day 1 and day 2), centre = date + Rahu/Moon nakshatras.
 *
 * The wheel is built from the CURRENT sky (True Rahu by the app's
 * setting, same as the chart tab). Day 1 = now, day 2 = now + 24h.
 * The general day-2 verdict follows the book's Sayagyi rule; the
 * per-pair အဟော texts (5 topics) are transcribed in a later version.
 */

/** One computed reading for the screen. */
private data class Reading(
    val day1Millis: Long,
    val day2Millis: Long,
    val rahuLon: Double,
    val moon1Lon: Double,
    val moon2Lon: Double,
    val wheel: RahuKalam.SayagyiWheel,
    val useTrueRahu: Boolean
)

@Composable
fun RahuKalamScreen(useTrueRahu: Boolean) {
    var refreshKey by remember { mutableIntStateOf(0) }
    var reading by remember { mutableStateOf<Reading?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }

    val mmtFmt = remember {
        SimpleDateFormat("dd/MM/yyyy", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT+06:30")
        }
    }

    LaunchedEffect(refreshKey) {
        try {
            val r = withContext(Dispatchers.Default) {
                val now = System.currentTimeMillis()
                val jd1 = RahuKalam.jdFromMillis(now)
                val jd2 = RahuKalam.jdFromMillis(now + 24L * 60 * 60 * 1000)
                val rahuLon = Ephemeris.planetPosition(jd1, "Rahu", useTrueRahu).first
                val moon1 = Ephemeris.planetPosition(jd1, "Moon", useTrueRahu).first
                val moon2 = Ephemeris.planetPosition(jd2, "Moon", useTrueRahu).first
                Reading(
                    now, now + 24L * 60 * 60 * 1000, rahuLon, moon1, moon2,
                    RahuKalam.sayagyiWheel(rahuLon), useTrueRahu
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
                    "ခံတွင်းမှာ ရာဟု စီးတဲ့ နက္ခတ်၊ လ ရဲ့ အပိုင်း (၁ နေ့ / ၂ လာ) ကို ကြည့်ပြီး အဟော ဖတ်သည်။",
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

        val rahuN28 = RahuKalam.nakshatra28(r.rahuLon)
        val moon1N28 = RahuKalam.nakshatra28(r.moon1Lon)
        val moon2N28 = RahuKalam.nakshatra28(r.moon2Lon)
        val moon1Part = r.wheel.partOf(moon1N28)
        val moon2Part = r.wheel.partOf(moon2N28)
        val verdict2 = RahuKalam.day2Verdict(moon2Part)

        // Circular wheel.
        item {
            RahuKalamWheel(
                wheel = r.wheel,
                moon1N28 = moon1N28,
                moon2N28 = moon2N28,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
            )
            Spacer(Modifier.height(12.dp))
        }

        // Today / tomorrow summary.
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "ရာဟု (${if (r.useTrueRahu) "True" else "Mean"}): " +
                            "${RahuKalam.name28(rahuN28)} (နံပါတ် $rahuN28) — ခံတွင်း",
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

        // The six parts, with their nakshatras.
        item {
            Text("စက်ဝိုင်း ၆ ပိုင်း", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
        }
        r.wheel.parts.forEach { entry ->
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
 * Circular Sayagyi wheel: outer ring = 28 nakshatra numbers, middle
 * ring = 6 body parts (coloured), markers = Rahu (mouth, red dot) and
 * the Moon day 1 (blue) / day 2 (green), centre = mouth nakshatra.
 * Angles: nakshatra n occupies [(n-1) * 360/28, n * 360/28), measured
 * clockwise from the top. Parts follow the same forward order.
 */
@Composable
private fun RahuKalamWheel(
    wheel: RahuKalam.SayagyiWheel,
    moon1N28: Int,
    moon2N28: Int,
    modifier: Modifier = Modifier
) {
    val partColors = mapOf(
        RahuKalam.WheelPart.MOUTH to Color(0xFFE57373),
        RahuKalam.WheelPart.CHEST to Color(0xFFFFD54F),
        RahuKalam.WheelPart.BELLY to Color(0xFF81C784),
        RahuKalam.WheelPart.ANUS to Color(0xFFBA68C8),
        RahuKalam.WheelPart.HIPS to Color(0xFF64B5F6),
        RahuKalam.WheelPart.LEGS to Color(0xFFFFB74D)
    )
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val outerR = size.minDimension / 2f
        val partR = outerR * 0.72f
        val centerR = outerR * 0.30f

        // Outer ring: 28 nakshatra cells, numbered. A thick stroke centred
        // on the 0.92r circle fills the band (0.84r-1.00r); a thin Stroke
        // would only draw the outline and the mouth highlight would not show.
        val bandR = outerR * 0.92f
        for (n in 1..28) {
            val start = (n - 1) * 360f / 28f - 90f
            drawArc(
                color = if (n == wheel.mouth28) Color(0xFFFFCDD2) else Color(0xFFFFF8E1),
                startAngle = start,
                sweepAngle = 360f / 28f,
                useCenter = false,
                topLeft = Offset(cx - bandR, cy - bandR),
                size = Size(bandR * 2, bandR * 2),
                style = Stroke(width = outerR * 0.16f)
            )
            val mid = Math.toRadians((start + 360f / 56f).toDouble())
            val numR = bandR
            val tx = cx + (numR * cos(mid)).toFloat()
            val ty = cy + (numR * sin(mid)).toFloat()
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.BLACK
                    textSize = size.minDimension * 0.028f
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                drawText(n.toString(), tx, ty + paint.textSize / 3f, paint)
            }
        }
        // Middle ring: 6 body parts, coloured arcs + labels.
        var angle = -90f
        wheel.parts.forEach { entry ->
            val sweep = entry.numbers.size * 360f / 28f
            drawArc(
                color = partColors[entry.part] ?: Color.LightGray,
                startAngle = angle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(cx - partR, cy - partR),
                size = Size(partR * 2, partR * 2),
                style = Stroke(width = partR * 0.28f)
            )
            val mid = Math.toRadians((angle + sweep / 2f).toDouble())
            // Label at the middle of the part band (the stroke is centred
            // on partR, so partR is the band's middle, not its edge).
            val labelR = partR
            val tx = cx + (labelR * cos(mid)).toFloat()
            val ty = cy + (labelR * sin(mid)).toFloat()
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.BLACK
                    textSize = size.minDimension * 0.038f
                    textAlign = android.graphics.Paint.Align.CENTER
                    isFakeBoldText = true
                }
                drawText(entry.part.mm, tx, ty + paint.textSize / 3f, paint)
            }
            angle += sweep
        }
        // Centre circle + mouth nakshatra.
        drawCircle(
            color = Color(0xFFFFF3E0),
            radius = centerR,
            center = Offset(cx, cy),
            style = Stroke(width = 2f)
        )
        drawContext.canvas.nativeCanvas.apply {
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.BLACK
                textSize = size.minDimension * 0.042f
                textAlign = android.graphics.Paint.Align.CENTER
                isFakeBoldText = true
            }
            drawText("ခံတွင်း", cx, cy - paint.textSize * 0.2f, paint)
            val paint2 = android.graphics.Paint().apply {
                color = android.graphics.Color.BLACK
                textSize = size.minDimension * 0.034f
                textAlign = android.graphics.Paint.Align.CENTER
            }
            drawText(
                RahuKalam.name28(wheel.mouth28) + " (${wheel.mouth28})",
                cx, cy + paint.textSize * 1.1f, paint2
            )
        }
        // Planet markers: Rahu (mouth) red, Moon day 1 blue, day 2 green.
        fun marker(n28: Int, color: Color, radius: Float) {
            val mid = Math.toRadians(((n28 - 1) * 360.0 / 28.0 + 360.0 / 56.0 - 90.0))
            val r = outerR * 0.72f
            val x = cx + (r * cos(mid)).toFloat()
            val y = cy + (r * sin(mid)).toFloat()
            drawCircle(color = color, radius = radius, center = Offset(x, y))
            drawCircle(
                color = Color.White, radius = radius,
                center = Offset(x, y), style = Stroke(width = 2f)
            )
        }
        marker(wheel.mouth28, Color(0xFFD32F2F), size.minDimension * 0.022f)
        marker(moon1N28, Color(0xFF1565C0), size.minDimension * 0.018f)
        marker(moon2N28, Color(0xFF2E7D32), size.minDimension * 0.018f)
    }
    // Legend.
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(
            "● ရာဟု (ခံတွင်း)", style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFD32F2F), modifier = Modifier.padding(end = 12.dp)
        )
        Text(
            "● လ ၁ နေ့", style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF1565C0), modifier = Modifier.padding(end = 12.dp)
        )
        Text("● လ ၂ လာ", style = MaterialTheme.typography.bodySmall, color = Color(0xFF2E7D32))
    }
}
