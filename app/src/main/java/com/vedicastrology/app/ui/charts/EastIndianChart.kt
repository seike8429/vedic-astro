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

/**
 * Style 3 — Myanmar chart (Bengali / Surya Chakra layout; the user calls
 * this layout မြန်မာ, so that is its user-facing name — code identifiers
 * keep the EastIndian name). Modes D1/D9/Bhava.
 *
 * Fixed-sign layout (like South Indian): signs never move; houses are
 * counted counterclockwise from the Lagna. Geometry: a 3x3 grid plus one
 * diagonal in each corner square (outer corner to inner corner) gives
 * 4 edge-center rectangles + 8 corner triangles = 12 sections; the
 * center square stays empty.
 *
 * Sign sections, counterclockwise from the top-center (the unique
 * ordering that preserves strict counterclockwise zodiac order with
 * Aries at the top-center):
 *  1 Aries    top-center rectangle
 *  2 Taurus   top-left corner, top-side triangle
 *  3 Gemini   top-left corner, left-side triangle
 *  4 Cancer   left-middle rectangle
 *  5 Leo      bottom-left corner, left-side triangle
 *  6 Virgo    bottom-left corner, bottom-side triangle
 *  7 Libra    bottom-center rectangle
 *  8 Scorpio  bottom-right corner, bottom-side triangle
 *  9 Sagittarius bottom-right corner, right-side triangle
 * 10 Capricorn right-middle rectangle
 * 11 Aquarius top-right corner, right-side triangle
 * 12 Pisces   top-right corner, top-side triangle
 *
 * Each section shows its sign number and its planets as code + degree
 * in sign (e.g. "Su 8", reference-app style); retrograde carries "(R)";
 * the Lagna sign's section is marked "As <deg>" (e.g. "As 21").
 *
 * Verified conventions (multiple independent sources agree):
 * KundliGPT "North, South & East Indian Chart Styles" (3x3 grid +
 * diagonals, Aries top-center, counterclockwise sign flow,
 * counterclockwise house counting from Lagna, Lagna marked);
 * jothishi.com (centre top square always Aries, anti-clockwise
 * counting); aryabhatt.com lesson 8 (fixed sign order, anti-clockwise
 * houses). Caveat: secondary descriptions use loose position words
 * and minor regional triangle variants exist; this implementation is
 * the geometrically consistent counterclockwise mapping above.
 */
@Composable
fun EastIndianChart(
    planets: List<ChartPlanet>,
    mode: DiamondMode = DiamondMode.D1,
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xFF6A1B1A),
    accentColor: Color = Color(0xFFB8860B)
) {
    val layout = remember(planets, mode) { buildEastIndianLayout(planets, mode) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        val w = size.width
        val h = size.height
        fun pt(x: Double, y: Double) = Offset((x * w).toFloat(), (y * h).toFloat())

        // Frame: outer square + 3x3 grid lines.
        drawRect(lineColor, style = Stroke(4f))
        drawLine(lineColor, pt(1.0 / 3, 0.0), pt(1.0 / 3, 1.0), strokeWidth = 2.5f)
        drawLine(lineColor, pt(2.0 / 3, 0.0), pt(2.0 / 3, 1.0), strokeWidth = 2.5f)
        drawLine(lineColor, pt(0.0, 1.0 / 3), pt(1.0, 1.0 / 3), strokeWidth = 2.5f)
        drawLine(lineColor, pt(0.0, 2.0 / 3), pt(1.0, 2.0 / 3), strokeWidth = 2.5f)
        // Corner diagonals (outer corner to inner corner of each corner square).
        drawLine(lineColor, pt(0.0, 0.0), pt(1.0 / 3, 1.0 / 3), strokeWidth = 2.5f)
        drawLine(lineColor, pt(1.0, 0.0), pt(2.0 / 3, 1.0 / 3), strokeWidth = 2.5f)
        drawLine(lineColor, pt(0.0, 1.0), pt(1.0 / 3, 2.0 / 3), strokeWidth = 2.5f)
        drawLine(lineColor, pt(1.0, 1.0), pt(2.0 / 3, 2.0 / 3), strokeWidth = 2.5f)

        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            val signPaint = Paint().apply {
                color = accentColor.toArgb()
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
                isAntiAlias = true
            }
            val planetPaint = Paint().apply {
                color = lineColor.toArgb()
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            val ascPaint = Paint().apply {
                color = accentColor.toArgb()
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
                isAntiAlias = true
            }

            // Text layout (fix for overflowing/clipped labels): every
            // line is drawn INSIDE its own cell polygon — never across a
            // cell border and never past the canvas edge. The whole block
            // (title + planet lines) is centered on the cell's centroid
            // and clamped inside the cell's y-extent; each line is
            // centered within the INTERSECTION of the polygon's
            // x-intervals at its slot's top and bottom (so no text-box
            // corner can cross a slanted border); the font scales down
            // whenever the block would exceed the cell (crowded cells).
            for (cell in layout) {
                val lines = mutableListOf<Triple<String, Paint, Float>>()
                lines.add(Triple(cell.title, signPaint, w * 0.038f))
                cell.planets.forEach { pl ->
                    lines.add(
                        Triple(
                            pl.text,
                            if (pl.isAsc) ascPaint else planetPaint,
                            w * 0.042f
                        )
                    )
                }
                // Adaptive scale loop (placeCellLines is pure, so the
                // final placement is recomputed at the settled scale).
                var scale = 1.0f
                for (pass in 0 until 4) {
                    val (_, need) = placeCellLines(
                        cell.polygon, lines, cell.cx, cell.cy, w, h, scale
                    )
                    val next = need.coerceIn(0.45f, 1.0f)
                    if (next >= scale - 0.001f) break
                    scale = next
                }
                val (placed, _) = placeCellLines(
                    cell.polygon, lines, cell.cx, cell.cy, w, h, scale
                )
                placed.forEach { pl ->
                    pl.paint.textSize = pl.sizePx
                    nc.drawText(pl.text, pl.cxPx, pl.baselinePx, pl.paint)
                }
            }
        }
    }
}

/**
 * Cell polygons (0..1 coords, y down), sign 1 = top-center,
 * counterclockwise. These are the EXISTING cell borders (frame + 3x3
 * grid + corner diagonals) — unchanged. Text is placed inside them;
 * the old per-sign text anchors are replaced by polygon centroids
 * because the old Leo anchor (1/9, 8/9) sat exactly ON its triangle's
 * diagonal, which pushed "Ma 14"-style labels across the border.
 */
private val CELL_POLYGONS: List<List<Pair<Double, Double>>> = listOf(
    // 1 Aries, top-center rectangle
    listOf(1.0 / 3 to 0.0, 2.0 / 3 to 0.0, 2.0 / 3 to 1.0 / 3, 1.0 / 3 to 1.0 / 3),
    // 2 Taurus, top-left top-side triangle
    listOf(0.0 to 0.0, 1.0 / 3 to 0.0, 1.0 / 3 to 1.0 / 3),
    // 3 Gemini, top-left left-side triangle
    listOf(0.0 to 0.0, 0.0 to 1.0 / 3, 1.0 / 3 to 1.0 / 3),
    // 4 Cancer, left-middle rectangle
    listOf(0.0 to 1.0 / 3, 1.0 / 3 to 1.0 / 3, 1.0 / 3 to 2.0 / 3, 0.0 to 2.0 / 3),
    // 5 Leo, bottom-left left-side triangle
    listOf(0.0 to 2.0 / 3, 1.0 / 3 to 2.0 / 3, 0.0 to 1.0),
    // 6 Virgo, bottom-left bottom-side triangle
    listOf(0.0 to 1.0, 1.0 / 3 to 2.0 / 3, 1.0 / 3 to 1.0),
    // 7 Libra, bottom-center rectangle
    listOf(1.0 / 3 to 2.0 / 3, 2.0 / 3 to 2.0 / 3, 2.0 / 3 to 1.0, 1.0 / 3 to 1.0),
    // 8 Scorpio, bottom-right bottom-side triangle
    listOf(2.0 / 3 to 2.0 / 3, 2.0 / 3 to 1.0, 1.0 to 1.0),
    // 9 Sagittarius, bottom-right right-side triangle
    listOf(2.0 / 3 to 2.0 / 3, 1.0 to 2.0 / 3, 1.0 to 1.0),
    // 10 Capricorn, right-middle rectangle
    listOf(2.0 / 3 to 1.0 / 3, 1.0 to 1.0 / 3, 1.0 to 2.0 / 3, 2.0 / 3 to 2.0 / 3),
    // 11 Aquarius, top-right right-side triangle
    listOf(1.0 to 0.0, 2.0 / 3 to 1.0 / 3, 1.0 to 1.0 / 3),
    // 12 Pisces, top-right top-side triangle
    listOf(2.0 / 3 to 0.0, 2.0 / 3 to 1.0 / 3, 1.0 to 0.0),
)

/** Polygon centroid (text-block center) for a cell polygon. */
private fun centroidOf(poly: List<Pair<Double, Double>>): Pair<Double, Double> =
    poly.fold(0.0 to 0.0) { acc, p -> acc.first + p.first to acc.second + p.second }
        .let { (it.first / poly.size) to (it.second / poly.size) }

/**
 * X-interval of a convex polygon at height [y] (0..1 coords). Falls back
 * to the polygon's full x-range if the height misses every edge (only
 * at exact vertices).
 */
private fun intervalAt01(poly: List<Pair<Double, Double>>, y: Double): Pair<Double, Double> {
    val yy = y.coerceIn(0.001, 0.999)
    val xs = mutableListOf<Double>()
    for (i in poly.indices) {
        val (ax, ay) = poly[i]
        val (bx, by) = poly[(i + 1) % poly.size]
        if ((ay <= yy && by > yy) || (by <= yy && ay > yy)) {
            xs.add(ax + (yy - ay) * (bx - ax) / (by - ay))
        }
    }
    return if (xs.size >= 2) xs.min() to xs.max()
    else poly.minOf { it.first } to poly.maxOf { it.first }
}

private data class PlacedLine(
    val text: String, val paint: Paint, val sizePx: Float,
    val cxPx: Float, val baselinePx: Float
)

/**
 * Places a cell's lines (text, paint, base size in px) inside the convex
 * cell polygon [poly01] (0..1 coords). All geometry runs in 0..1
 * fractions; only the returned pixel positions touch the canvas.
 * Returns the placements at [scale] plus the largest scale (<= 1) under
 * which the block fits the cell height and every line fits its slot's
 * interval intersection (so no text-box corner can cross a border).
 * [centroidX] is unused for placement (lines center on their slot
 * intervals) but kept for signature clarity with [centroidY].
 */
private fun placeCellLines(
    poly01: List<Pair<Double, Double>>,
    lines: List<Triple<String, Paint, Float>>,
    centroidX: Double, centroidY: Double,
    w: Float, h: Float,
    scale: Float
): Pair<List<PlacedLine>, Float> {
    val minY = poly01.minOf { it.second }
    val maxY = poly01.maxOf { it.second }
    val marginX = 0.008
    val marginY = 0.012
    val availH = (maxY - minY) - 2 * marginY
    // Line heights as fractions of h.
    val lineH = lines.map { it.third * scale * 1.18f / h }
    val blockH = lineH.sum()
    var need = 1.0f
    if (blockH > availH) need = minOf(need, scale * (availH / blockH).toFloat())
    // Block top: centered on the centroid, clamped inside the y-extent
    // (a tall block in a triangle would otherwise poke past the apex
    // edge — the old code's unclamped centering did exactly that).
    val topMax = maxOf(maxY - marginY - blockH, minY + marginY)
    var top = (centroidY - blockH / 2).coerceIn(minY + marginY, topMax)
    val placed = mutableListOf<PlacedLine>()
    lines.forEachIndexed { i, (text, paint, base) ->
        val sizePx = base * scale
        paint.textSize = sizePx
        val slotTop = top
        val slotBot = top + lineH[i]
        val slotC = (slotTop + slotBot) / 2
        // Interval INTERSECTION over the slot (top and bottom edges):
        // for slanted borders the interval narrows toward one end, and
        // centering on the mid-slot interval alone lets box corners
        // cross the border (the old Taurus "Ve 23" case).
        val (xLt, xRt) = intervalAt01(poly01, slotTop)
        val (xLb, xRb) = intervalAt01(poly01, slotBot)
        val xL = maxOf(xLt, xLb)
        val xR = minOf(xRt, xRb)
        val availW = (xR - xL) - 2 * marginX
        val twFrac = paint.measureText(text) / w
        if (twFrac > availW) need = minOf(need, scale * (availW / twFrac).toFloat())
        val halfW = twFrac / 2
        var cxFrac = (xL + xR) / 2
        if (2 * halfW + 2 * marginX <= xR - xL) {
            cxFrac = cxFrac.coerceIn(xL + marginX + halfW, xR - marginX - halfW)
        }
        val fm = paint.fontMetrics
        val baselineFrac = slotC - ((fm.ascent + fm.descent) / 2) / h
        placed.add(
            PlacedLine(
                text, paint, sizePx,
                (cxFrac * w).toFloat(), (baselineFrac * h).toFloat()
            )
        )
        top = slotBot
    }
    return placed to need
}

private data class SignCell(
    val cx: Double, val cy: Double,
    val polygon: List<Pair<Double, Double>>,
    val title: String,
    val planets: List<EastIndianPlanetLabel>
)

/** Chart planet label; Lagna is drawn as a distinct "လ" marker. */
private data class EastIndianPlanetLabel(val text: String, val isAsc: Boolean)

/** Sidereal sign index (0 = Aries) from a sidereal longitude. */
private fun signIndexOf(longitude: Double): Int {
    val lon = ((longitude % 360.0) + 360.0) % 360.0
    return (lon / 30.0).toInt().coerceIn(0, 11)
}

/** Sign index (0 = Aries) from an English sign name (row.sign / row.navamsa). */
private fun signIndexOfName(name: String): Int =
    SIGNS.indexOfFirst { it.en == name }.coerceAtLeast(0)

/** Whole degree within the sign, as the reference app shows ("Su 8"). */
private fun degreeInSign(longitude: Double): Int {
    val lon = ((longitude % 360.0) + 360.0) % 360.0
    return (lon % 30.0).toInt()
}

/**
 * Planet labels for one cell. D1 shows number + degree (author's scheme:
 * Sun ၁, Moon ၂, Mars ၃, Mercury ၄, Jupiter ၅, Venus ၆, Saturn ၇, Rahu ၈,
 * Ketu ၉, Lagna လ); D9/Bhava show number only, mirroring the diamond
 * view's modes.
 */
private fun labelsFor(members: List<ChartPlanet>, withDegree: Boolean): List<EastIndianPlanetLabel> {
    return members.map { cp ->
        val isAsc = cp.row.planet == "Lagna"
        val code = PlanetNames.num[cp.row.planet] ?: cp.row.planet
        val text = when {
            isAsc -> if (withDegree) "လ ${degreeInSign(cp.row.longitude)}" else "လ"
            cp.retrograde -> if (withDegree) "$code ${degreeInSign(cp.row.longitude)}(R)" else "$code(R)"
            else -> if (withDegree) "$code ${degreeInSign(cp.row.longitude)}" else code
        }
        EastIndianPlanetLabel(text, isAsc)
    }
}

private fun buildEastIndianLayout(
    planets: List<ChartPlanet>,
    mode: DiamondMode
): List<SignCell> {
    val lagna = planets.firstOrNull { it.row.planet == "Lagna" }
    val lagnaSignIdx = lagna?.let { signIndexOfName(it.row.sign) } ?: 0
    return when (mode) {
        // D1: planets by sidereal sign into that sign's fixed cell.
        DiamondMode.D1 -> {
            val bySign = planets.groupBy { signIndexOf(it.row.longitude) }
            (0 until 12).map { signIdx ->
                val poly = CELL_POLYGONS[signIdx]
                val (cx, cy) = centroidOf(poly)
                SignCell(cx, cy, poly, (signIdx + 1).toString(), labelsFor(bySign[signIdx] ?: emptyList(), withDegree = true))
            }
        }
        // D9: planets by navamsa sign into that sign's fixed cell, using
        // the same row.navamsa field the diamond D9 view uses. The navamsa
        // Lagna lands in its own navamsa-sign cell and is marked "As";
        // houses count counterclockwise from it by fixed-cell order.
        DiamondMode.D9 -> {
            val byNav = planets.groupBy { signIndexOfName(it.row.navamsa) }
            (0 until 12).map { signIdx ->
                val poly = CELL_POLYGONS[signIdx]
                val (cx, cy) = centroidOf(poly)
                SignCell(cx, cy, poly, (signIdx + 1).toString(), labelsFor(byNav[signIdx] ?: emptyList(), withDegree = false))
            }
        }
        // Bhava: planets grouped by row.bhava — the same field the diamond
        // Bhava view uses, so grouping is identical by construction.
        // House h sits in the fixed cell of sign (lagnaSign + h - 1),
        // i.e. bhava numbers counted counterclockwise from the Lagna's cell.
        DiamondMode.BHAVA -> {
            val byBhava = planets.groupBy { it.row.bhava }
            (1..12).map { h ->
                val signIdx = (lagnaSignIdx + h - 1) % 12
                val poly = CELL_POLYGONS[signIdx]
                val (cx, cy) = centroidOf(poly)
                SignCell(cx, cy, poly, h.toString(), labelsFor(byBhava[h] ?: emptyList(), withDegree = false))
            }
        }
    }
}
