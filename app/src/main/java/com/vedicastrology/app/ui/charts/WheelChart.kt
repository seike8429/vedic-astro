package com.vedicastrology.app.ui.charts

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.vedicastrology.app.engine.ChartPlanet
import com.vedicastrology.app.engine.SIGNS
import com.vedicastrology.app.ui.theme.PlanetNames
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Style 1 — Burmese circular wheel chart (MOTAA-style).
 *
 * - 12 rashis with Burmese labels around the outer ring.
 * - Planets placed at sidereal longitudes (0° Aries at top, counterclockwise).
 * - Ascendant marked with a bold radial line.
 * - Center shows Lagna sign (Burmese).
 */
@Composable
fun WheelChart(
    planets: List<ChartPlanet>,
    ascendant: Double,
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xFF6A1B1A),
    accentColor: Color = Color(0xFFB8860B)
) {
    // De-collide planet labels: alternate radii for close longitudes.
    val placed = remember(planets) { placePlanets(planets) }
    val lagnaSign = remember(planets) {
        planets.firstOrNull { it.row.planet == "Lagna" }?.row?.signMm ?: ""
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val rOuter = (size.minDimension / 2f) * 0.96f
        val rSign = rOuter * 0.88f      // sign label radius
        val rPlanetOuter = rOuter * 0.76f
        val rPlanetInner = rOuter * 0.62f
        val rInner = rOuter * 0.50f     // inner circle (center info)
        val lc = lineColor.toArgb()

        fun point(lonDeg: Double, r: Float): Offset {
            val a = (-90.0 - lonDeg) * PI / 180.0
            return Offset(
                (cx + r * cos(a)).toFloat(),
                (cy + r * sin(a)).toFloat()
            )
        }

        // Circles.
        drawCircle(lineColor, rOuter, center = Offset(cx, cy), style = Stroke(3f))
        drawCircle(lineColor, rInner, center = Offset(cx, cy), style = Stroke(2f))
        drawCircle(accentColor, rOuter * 0.985f, center = Offset(cx, cy), style = Stroke(1.5f))

        // 12 spokes (sign boundaries) from inner circle to outer.
        for (i in 0 until 12) {
            val lon = i * 30.0
            drawLine(lineColor, point(lon, rInner), point(lon, rOuter), strokeWidth = 2f)
        }

        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas

            // Sign labels (Burmese) at segment centers.
            val signPaint = Paint().apply {
                color = lc
                textAlign = Paint.Align.CENTER
                textSize = rOuter * 0.075f
                typeface = Typeface.DEFAULT_BOLD
                isAntiAlias = true
            }
            for (i in 0 until 12) {
                val p = point(i * 30.0 + 15.0, rSign)
                nc.drawText(SIGNS[i].mm, p.x, p.y + signPaint.textSize * 0.35f, signPaint)
            }

            // Ascendant: bold radial marker.
            val ascPaint = Paint().apply {
                color = accentColor.toArgb()
                strokeWidth = 7f
                isAntiAlias = true
            }
            val a0 = point(ascendant, rInner)
            val a1 = point(ascendant, rOuter)
            nc.drawLine(a0.x, a0.y, a1.x, a1.y, ascPaint)

            // Planets.
            val planetPaint = Paint().apply {
                color = lc
                textAlign = Paint.Align.CENTER
                textSize = rOuter * 0.072f
                typeface = Typeface.DEFAULT_BOLD
                isAntiAlias = true
            }
            val retroPaint = Paint(planetPaint).apply { color = Color(0xFFB71C1C).toArgb() }
            for ((cp, radius) in placed) {
                val r = if (radius == 0) rPlanetOuter else rPlanetInner
                val p = point(cp.row.longitude, r)
                var label = PlanetNames.short[cp.row.planet] ?: cp.row.planet
                if (cp.retrograde) label += "(R)"
                nc.drawText(label, p.x, p.y + planetPaint.textSize * 0.35f,
                    if (cp.retrograde) retroPaint else planetPaint)
            }

            // Center: Lagna sign.
            val centerPaint = Paint().apply {
                color = accentColor.toArgb()
                textAlign = Paint.Align.CENTER
                textSize = rOuter * 0.11f
                typeface = Typeface.DEFAULT_BOLD
                isAntiAlias = true
            }
            nc.drawText(lagnaSign, cx, cy + centerPaint.textSize * 0.35f, centerPaint)
        }
    }
}

/**
 * Assign each planet to an outer/inner planet-band radius so that labels
 * closer than ~11° don't overlap. Returns list of (planet, band 0|1).
 */
private fun placePlanets(planets: List<ChartPlanet>): List<Pair<ChartPlanet, Int>> {
    // Lagna is shown in the center; don't duplicate it on the wheel.
    val bodies = planets.filter { it.row.planet != "Lagna" }
        .sortedBy { it.row.longitude }
    val result = mutableListOf<Pair<ChartPlanet, Int>>()
    var lastLon = -1000.0
    var band = 0
    for (cp in bodies) {
        var d = cp.row.longitude - lastLon
        if (d < 0) d += 360.0
        band = if (d < 11.0) 1 - band else 0
        result.add(cp to band)
        lastLon = cp.row.longitude
    }
    return result
}
