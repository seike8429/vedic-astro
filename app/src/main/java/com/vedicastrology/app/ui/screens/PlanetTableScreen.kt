package com.vedicastrology.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vedicastrology.app.engine.ChartPlanet
import com.vedicastrology.app.ui.theme.PlanetNames

/**
 * Planetary positions table:
 * Planet | Degree (DMS) | Rashi | Nakshatra | Paada | RL | NL | SL
 * (Reference: Lagna 15:39:59 Kataka, Pushyami, Paada 4, RL Mo, NL Sa, SL Ju)
 */
@Composable
fun PlanetTableScreen(
    planets: List<ChartPlanet>,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(onClick = onBack) { Text("← ဇာတာ") }
            Text("ဂြိုဟ်စာရင်း", style = MaterialTheme.typography.titleLarge)
        }

        val scroll = rememberScrollState()
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scroll)
        ) {
            item {
                HeaderRow()
                HorizontalDivider()
            }
            items(planets) { cp ->
                PlanetRow(cp)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun HeaderRow() {
    Row(modifier = Modifier.padding(vertical = 6.dp)) {
        Cell("ဂြိုဟ်", 64, bold = true)
        Cell("Degree", 92, bold = true)
        Cell("Rashi", 72, bold = true)
        Cell("Nakshatra", 96, bold = true)
        Cell("Pada", 44, bold = true)
        Cell("RL", 40, bold = true)
        Cell("NL", 40, bold = true)
        Cell("SL", 40, bold = true)
    }
}

@Composable
private fun PlanetRow(cp: ChartPlanet) {
    val r = cp.row
    Row(modifier = Modifier.padding(vertical = 6.dp)) {
        val name = (PlanetNames.short[r.planet] ?: r.planet) + if (cp.retrograde) "(R)" else ""
        Cell(name, 64, bold = r.planet == "Lagna")
        Cell(r.dms, 92)
        Cell("${r.signMm}", 72)
        Cell(r.nakshatraMm, 96)
        Cell(r.pada.toString(), 44)
        Cell(shortLord(r.rl), 40)
        Cell(shortLord(r.nl), 40)
        Cell(shortLord(r.sl), 40)
    }
}

@Composable
private fun Cell(text: String, widthDp: Int, bold: Boolean = false) {
    Text(
        text,
        modifier = Modifier.width(widthDp.dp).padding(end = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (bold) FontWeight.Bold else null,
        maxLines = 1
    )
}

/** "Jupiter" -> "Ju" style short lord codes. */
private fun shortLord(english: String): String =
    PlanetNames.code.entries.firstOrNull { (k, _) -> k == english }?.value ?: english.take(2)
