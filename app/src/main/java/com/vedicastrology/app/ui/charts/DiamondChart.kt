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

/** Which divisional chart the diamond shows. */
enum class DiamondMode { D1, D9, BHAVA }

/**
 * Style 2 — North Indian diamond chart.
 *
 * Fixed 12-bhava layout (Lagna top-center kite, houses counterclockwise).
 * D1: Rashi signs + planets by bhava. D9: Navamsa signs. BHAVA: bhava numbers.
 * Each region shows the sign number and planet abbreviations (Burmese).
 */
@Composable
fun DiamondChart(
    planets: List<ChartPlanet>,
    mode: DiamondMode,
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xFF6A1B1A),
    accentColor: Color = Color(0xFFB8860B)
) {
    val layout = remember(planets, mode) { buildDiamondLayout(planets, mode) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        val w = size.width
        val h = size.height
        fun pt(x: Double, y: Double) = Offset((x * w).toFloat(), (y * h).toFloat())

        // Frame: square + diagonals + midpoint diamond.
        drawRect(lineColor, style = Stroke(4f))
        drawLine(lineColor, pt(0.0, 0.0), pt(1.0, 1.0), strokeWidth = 2.5f)
        drawLine(lineColor, pt(1.0, 0.0), pt(0.0, 1.0), strokeWidth = 2.5f)
        val diamond = listOf(
            pt(0.5, 0.0), pt(1.0, 0.5), pt(0.5, 1.0), pt(0.0, 0.5), pt(0.5, 0.0)
        )
        for (i in 0 until 4) drawLine(lineColor, diamond[i], diamond[i + 1], strokeWidth = 2.5f)

        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            val signPaint = Paint().apply {
                color = accentColor.toArgb()
                textAlign = Paint.Align.CENTER
                textSize = w * 0.038f
                typeface = Typeface.DEFAULT_BOLD
                isAntiAlias = true
            }
            val planetPaint = Paint().apply {
                color = lineColor.toArgb()
                textAlign = Paint.Align.CENTER
                textSize = w * 0.042f
                isAntiAlias = true
            }

            for (house in layout) {
                val c = pt(house.cx, house.cy)
                // Sign number (or bhava number in BHAVA mode).
                nc.drawText(
                    house.title,
                    c.x, c.y - planetPaint.textSize * 0.9f, signPaint
                )
                // Planets in this house, one per line.
                house.planets.forEachIndexed { idx, label ->
                    nc.drawText(
                        label, c.x,
                        c.y + planetPaint.textSize * (0.35f + idx * 1.05f),
                        planetPaint
                    )
                }
            }
        }
    }
}

/** Label anchor per house (0..1 coords), house 1 = top kite, counterclockwise. */
private val HOUSE_ANCHORS = listOf(
    0.50 to 0.26,  // 1 top kite
    0.25 to 0.07,  // 2
    0.07 to 0.25,  // 3
    0.26 to 0.50,  // 4 left kite
    0.07 to 0.75,  // 5
    0.25 to 0.93,  // 6
    0.50 to 0.74,  // 7 bottom kite
    0.75 to 0.93,  // 8
    0.93 to 0.75,  // 9
    0.74 to 0.50,  // 10 right kite
    0.93 to 0.25,  // 11
    0.75 to 0.07,  // 12
)

private data class HouseCell(
    val cx: Double, val cy: Double,
    val title: String,
    val planets: List<String>
)

private fun signIndexOf(englishName: String): Int =
    SIGNS.indexOfFirst { it.en == englishName }.coerceAtLeast(0)

private fun buildDiamondLayout(
    planets: List<ChartPlanet>,
    mode: DiamondMode
): List<HouseCell> {
    val lagna = planets.firstOrNull { it.row.planet == "Lagna" }
    val lagnaSignIdx = lagna?.let { signIndexOf(it.row.sign) } ?: 0
    val navLagnaIdx = lagna?.let { signIndexOf(it.row.navamsa) } ?: 0

    // House number (1-12) for each planet in this mode.
    fun houseOf(cp: ChartPlanet): Int = when (mode) {
        DiamondMode.D1, DiamondMode.BHAVA -> cp.row.bhava
        DiamondMode.D9 -> {
            val navIdx = signIndexOf(cp.row.navamsa)
            ((navIdx - navLagnaIdx + 12) % 12) + 1
        }
    }

    // Sign shown in house i (1-based).
    fun signInHouse(house: Int): Int = when (mode) {
        DiamondMode.D9 -> (navLagnaIdx + house - 1) % 12
        else -> (lagnaSignIdx + house - 1) % 12
    }

    val byHouse = planets.groupBy { houseOf(it) }

    return (1..12).map { h ->
        val (ax, ay) = HOUSE_ANCHORS[h - 1]
        val title = when (mode) {
            DiamondMode.BHAVA -> h.toString()
            else -> (signInHouse(h) + 1).toString()
        }
        val labels = (byHouse[h] ?: emptyList()).map { cp ->
            val base = PlanetNames.short[cp.row.planet] ?: cp.row.planet
            if (cp.retrograde) "$base(R)" else base
        }
        HouseCell(ax, ay, title, labels)
    }
}
