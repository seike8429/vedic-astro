package com.vedicastrology.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vedicastrology.app.data.SavedChart

private val MONTHS = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

/** "22 Apr 1976, 08:30 AM" + place line for a saved chart. */
private fun chartSubtitle(chart: SavedChart): String {
    val b = chart.birth
    val hour12 = when (b.hour) {
        0 -> 12 to "AM"
        in 1..11 -> b.hour to "AM"
        12 -> 12 to "PM"
        else -> (b.hour - 12) to "PM"
    }
    val date = "%02d %s %04d, %02d:%02d %s".format(
        b.day, MONTHS[(b.month - 1).coerceIn(0, 11)], b.year,
        hour12.first, b.minute, hour12.second
    )
    return if (b.placeName.isNotEmpty()) "$date • ${b.placeName}" else date
}

/**
 * Saved-charts list: tap a chart to open it, set one as the default
 * (opens automatically on app launch), delete a chart, add a new one.
 */
@Composable
fun ChartListScreen(
    charts: List<SavedChart>,
    defaultId: String?,
    currentId: String?,
    onOpen: (SavedChart) -> Unit,
    onSetDefault: (String) -> Unit,
    onDelete: (String) -> Unit,
    onAdd: () -> Unit,
    onBack: () -> Unit
) {
    var pendingDelete: SavedChart? by remember { mutableStateOf(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Saved charts (${charts.size})",
                style = MaterialTheme.typography.titleLarge
            )
            OutlinedButton(onClick = onBack) { Text("← Chart") }
        }

        if (charts.isEmpty()) {
            Text(
                "No saved charts yet. Add one below.",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(charts, key = { it.id }) { chart ->
                val isDefault = chart.id == defaultId
                val isCurrent = chart.id == currentId
                Card(
                    modifier = Modifier.fillMaxWidth()
                        .clickable { onOpen(chart) }
                ) {
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                chart.name.ifEmpty { "Unnamed chart" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            if (isDefault) {
                                Text(
                                    "Default",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (isCurrent) {
                                Text(
                                    "Open",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        Text(
                            chartSubtitle(chart),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!isDefault) {
                                OutlinedButton(
                                    onClick = { onSetDefault(chart.id) }
                                ) { Text("Set default") }
                            }
                            OutlinedButton(
                                onClick = { pendingDelete = chart }
                            ) { Text("Delete") }
                        }
                    }
                }
            }
        }

        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
            Text("Add new chart")
        }
        Spacer(Modifier.height(2.dp))
    }

    pendingDelete?.let { chart ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete chart?") },
            text = {
                Text(
                    "Delete \"${chart.name.ifEmpty { "Unnamed chart" }}\"? " +
                        "This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(chart.id)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}
