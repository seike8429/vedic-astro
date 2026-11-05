package com.vedicastrology.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vedicastrology.app.data.CITY_PRESETS
import com.vedicastrology.app.engine.BirthData

private val MONTHS = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

/** Split |value| into whole degrees + whole minutes (60-min carry handled). */
private fun splitDegMin(value: Double): Pair<Int, Int> {
    val a = kotlin.math.abs(value)
    var d = a.toInt()
    var m = kotlin.math.round((a - d) * 60).toInt()
    if (m >= 60) { d += 1; m = 0 }
    return d to m
}

private fun parseClamped(text: String, fallback: Int, min: Int, max: Int): Int =
    text.toIntOrNull()?.coerceIn(min, max) ?: fallback

/**
 * Birth-data entry as a dialog, matching the user's reference dialog:
 * Day / Month / Year, Time + AM/PM, Time Zone (+/− E/W, hours, minutes),
 * Place, Longitude (deg, E/W, min), Latitude (deg, N/S, min),
 * "Set as default" checkbox, Cancel / Done.
 *
 * The GMT toggle (enter time directly in GMT, skipping the timezone
 * conversion) is kept and integrated: when ON, the timezone row is
 * disabled and the entered time is used as GMT.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthInputScreen(
    initial: BirthData,
    initialName: String,
    onCompute: (BirthData, String, Boolean) -> Unit,
    onCancel: () -> Unit,
    isComputing: Boolean,
    // Add vs Edit must be unmistakable: the old dialog showed the same
    // "Enter Birth Data" title for both, so editing the open chart
    // (chart screen's back button) looked exactly like adding a new
    // one — and saving overwrote the open chart instead of adding.
    isEditing: Boolean = false,
    editingName: String = ""
) {
    var nameText by remember { mutableStateOf(initialName) }
    var dayText by remember { mutableStateOf(initial.day.toString()) }
    var month by remember { mutableStateOf(initial.month.coerceIn(1, 12)) }
    var yearText by remember { mutableStateOf(initial.year.toString()) }

    val initHour12 = when (initial.hour) {
        0 -> 12 to false
        in 1..11 -> initial.hour to false
        12 -> 12 to true
        else -> (initial.hour - 12) to true
    }
    var hourText by remember { mutableStateOf(initHour12.first.toString()) }
    var minuteText by remember { mutableStateOf(initial.minute.toString()) }
    var isPm by remember { mutableStateOf(initHour12.second) }

    var tzEast by remember { mutableStateOf(initial.tzOffset >= 0) }
    val tzSplit = splitDegMin(initial.tzOffset)
    var tzHourText by remember { mutableStateOf(tzSplit.first.toString()) }
    var tzMinText by remember { mutableStateOf(tzSplit.second.toString()) }
    var timeInGmt by remember { mutableStateOf(initial.timeInGmt) }

    var placeText by remember { mutableStateOf(initial.placeName) }

    val lonSplit = splitDegMin(initial.longitude)
    var lonDegText by remember { mutableStateOf(lonSplit.first.toString()) }
    var lonMinText by remember { mutableStateOf(lonSplit.second.toString()) }
    var lonEast by remember { mutableStateOf(initial.longitude >= 0) }

    val latSplit = splitDegMin(initial.latitude)
    var latDegText by remember { mutableStateOf(latSplit.first.toString()) }
    var latMinText by remember { mutableStateOf(latSplit.second.toString()) }
    var latNorth by remember { mutableStateOf(initial.latitude >= 0) }

    var setAsDefault by remember { mutableStateOf(true) }
    var monthExpanded by remember { mutableStateOf(false) }

    // Major-city dropdown state. Pre-selected when the opened data
    // already matches a preset (within ~5 km); otherwise unselected.
    // Selecting a city only FILLS the fields below — Place stays a
    // free-text field and every filled value stays editable by hand.
    var cityExpanded by remember { mutableStateOf(false) }
    var selectedCity by remember {
        mutableStateOf(
            CITY_PRESETS.firstOrNull { city ->
                kotlin.math.abs(city.lat - initial.latitude) < 0.05 &&
                    kotlin.math.abs(city.lon - initial.longitude) < 0.05
            }?.name
        )
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(modifier = Modifier.fillMaxWidth(0.96f)) {
            Column {
                // Title bar (reference dialog header).
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF2E6DA4))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        if (isEditing) "Edit chart" else "Add new chart",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxHeight(0.92f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // What Done will do — stated plainly so Add can never
                    // be mistaken for Edit (or vice versa) again.
                    Text(
                        if (isEditing) {
                            val n = editingName.ifEmpty { "this chart" }
                            "Saving updates “$n” — it will not create a copy."
                        } else {
                            "A new chart will be added to your list — existing charts stay untouched."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Name/label for the saved-charts list.
                    Text("Name:")
                    OutlinedTextField(
                        value = nameText,
                        onValueChange = { nameText = it },
                        singleLine = true,
                        placeholder = { Text("e.g. My Chart") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Day / Month / Year.
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Day:", modifier = Modifier.weight(0.55f))
                        Text("Month:", modifier = Modifier.weight(0.9f))
                        Text("Year:", modifier = Modifier.weight(0.9f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = dayText,
                            onValueChange = { dayText = it.filter { c -> c.isDigit() }.take(2) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(0.55f)
                        )
                        ExposedDropdownMenuBox(
                            expanded = monthExpanded,
                            onExpandedChange = { monthExpanded = it },
                            modifier = Modifier.weight(0.9f)
                        ) {
                            OutlinedTextField(
                                value = MONTHS[month - 1],
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(monthExpanded) },
                                singleLine = true,
                                modifier = Modifier.menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = monthExpanded,
                                onDismissRequest = { monthExpanded = false }
                            ) {
                                MONTHS.forEachIndexed { i, name ->
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = { month = i + 1; monthExpanded = false }
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = yearText,
                            onValueChange = { yearText = it.filter { c -> c.isDigit() }.take(4) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(0.9f)
                        )
                    }

                    // Time: hour, minute, AM/PM.
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Time:", modifier = Modifier.weight(0.8f))
                        OutlinedTextField(
                            value = hourText,
                            onValueChange = { hourText = it.filter { c -> c.isDigit() }.take(2) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(0.7f)
                        )
                        OutlinedTextField(
                            value = minuteText,
                            onValueChange = { minuteText = it.filter { c -> c.isDigit() }.take(2) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(0.7f)
                        )
                        OutlinedButton(onClick = { isPm = !isPm }) {
                            Text(if (isPm) "PM" else "AM")
                        }
                    }

                    // Time Zone: sign (E/W), hours, minutes.
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Time Zone:", modifier = Modifier.weight(0.8f))
                        OutlinedButton(
                            onClick = { tzEast = !tzEast },
                            enabled = !timeInGmt
                        ) {
                            Text(if (tzEast) "+ (E)" else "− (W)")
                        }
                        OutlinedTextField(
                            value = tzHourText,
                            onValueChange = { tzHourText = it.filter { c -> c.isDigit() }.take(2) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            enabled = !timeInGmt,
                            modifier = Modifier.weight(0.7f)
                        )
                        OutlinedTextField(
                            value = tzMinText,
                            onValueChange = { tzMinText = it.filter { c -> c.isDigit() }.take(2) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            enabled = !timeInGmt,
                            modifier = Modifier.weight(0.7f)
                        )
                    }
                    Text(
                        if (timeInGmt) "GMT mode: time is used as GMT directly."
                        else "(For DST, adjust the Time-Zone accordingly.)",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (timeInGmt) MaterialTheme.colorScheme.primary
                                else Color(0xFFC0392B)
                    )

                    // GMT toggle (kept from the earlier approved feature).
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("အချိန် GMT နဲ့ထည့်")
                        Switch(checked = timeInGmt, onCheckedChange = { timeInGmt = it })
                    }

                    // Major-city dropdown: fills Place + Longitude +
                    // Latitude. Custom places stay possible — the Place
                    // field below remains free text and every filled
                    // value stays editable by hand afterwards.
                    Text("Major city (fills Place + coordinates):")
                    ExposedDropdownMenuBox(
                        expanded = cityExpanded,
                        onExpandedChange = { cityExpanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedCity?.let { name ->
                                val mm = CITY_PRESETS.firstOrNull { it.name == name }?.nameMm
                                if (mm != null) "$name ($mm)" else name
                            } ?: "Choose a city…",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(cityExpanded) },
                            singleLine = true,
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = cityExpanded,
                            onDismissRequest = { cityExpanded = false }
                        ) {
                            CITY_PRESETS.forEach { city ->
                                DropdownMenuItem(
                                    text = { Text("${city.name} (${city.nameMm})") },
                                    onClick = {
                                        selectedCity = city.name
                                        placeText = "${city.name}, Myanmar"
                                        val lonS = splitDegMin(city.lon)
                                        lonDegText = lonS.first.toString()
                                        lonMinText = lonS.second.toString()
                                        lonEast = city.lon >= 0
                                        val latS = splitDegMin(city.lat)
                                        latDegText = latS.first.toString()
                                        latMinText = latS.second.toString()
                                        latNorth = city.lat >= 0
                                        cityExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Place.
                    Text("Place:")
                    OutlinedTextField(
                        value = placeText,
                        onValueChange = { placeText = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Longitude: degrees, E/W, minutes.
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Longitude:", modifier = Modifier.weight(0.8f))
                        OutlinedTextField(
                            value = lonDegText,
                            onValueChange = { lonDegText = it.filter { c -> c.isDigit() }.take(3) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(0.7f)
                        )
                        OutlinedButton(onClick = { lonEast = !lonEast }) {
                            Text(if (lonEast) "E" else "W")
                        }
                        OutlinedTextField(
                            value = lonMinText,
                            onValueChange = { lonMinText = it.filter { c -> c.isDigit() }.take(2) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(0.7f)
                        )
                    }

                    // Latitude: degrees, N/S, minutes.
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Latitude:", modifier = Modifier.weight(0.8f))
                        OutlinedTextField(
                            value = latDegText,
                            onValueChange = { latDegText = it.filter { c -> c.isDigit() }.take(2) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(0.7f)
                        )
                        OutlinedButton(onClick = { latNorth = !latNorth }) {
                            Text(if (latNorth) "N" else "S")
                        }
                        OutlinedTextField(
                            value = latMinText,
                            onValueChange = { latMinText = it.filter { c -> c.isDigit() }.take(2) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(0.7f)
                        )
                    }

                    // Set as default (reference dialog checkbox, checked by default).
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = setAsDefault,
                            onCheckedChange = { setAsDefault = it }
                        )
                        Text("Set this place, longitude and latitude as default.")
                    }

                    Spacer(Modifier.height(4.dp))

                    // Cancel / Done (reference dialog actions).
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.weight(1f)
                        ) { Text("Cancel") }
                        Button(
                            onClick = {
                                val h12 = parseClamped(hourText, 12, 1, 12)
                                val hour24 = if (isPm) {
                                    if (h12 == 12) 12 else h12 + 12
                                } else {
                                    if (h12 == 12) 0 else h12
                                }
                                val tzSign = if (tzEast) 1.0 else -1.0
                                val tz = tzSign * (
                                    parseClamped(tzHourText, 6, 0, 14) +
                                    parseClamped(tzMinText, 30, 0, 59) / 60.0
                                )
                                val lonSign = if (lonEast) 1.0 else -1.0
                                val lon = lonSign * (
                                    parseClamped(lonDegText, 96, 0, 180) +
                                    parseClamped(lonMinText, 11, 0, 59) / 60.0
                                )
                                val latSign = if (latNorth) 1.0 else -1.0
                                val lat = latSign * (
                                    parseClamped(latDegText, 16, 0, 90) +
                                    parseClamped(latMinText, 51, 0, 59) / 60.0
                                )
                                onCompute(
                                    BirthData(
                                        year = parseClamped(yearText, 1976, 1800, 2200),
                                        month = month,
                                        day = parseClamped(dayText, 22, 1, 31),
                                        hour = hour24,
                                        minute = parseClamped(minuteText, 30, 0, 59),
                                        tzOffset = if (timeInGmt) initial.tzOffset else tz,
                                        latitude = lat,
                                        longitude = lon,
                                        placeName = placeText,
                                        timeInGmt = timeInGmt
                                    ),
                                    nameText.trim().ifEmpty { "Unnamed chart" },
                                    setAsDefault
                                )
                            },
                            enabled = !isComputing,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (isComputing) "တွက်နေတယ်…" else "Done")
                        }
                    }
                }
            }
        }
    }
}
