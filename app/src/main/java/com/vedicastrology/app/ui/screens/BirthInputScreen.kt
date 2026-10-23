package com.vedicastrology.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vedicastrology.app.data.CITY_PRESETS
import com.vedicastrology.app.data.TZ_PRESETS
import com.vedicastrology.app.engine.BirthData
import java.util.Calendar

/**
 * First page: birth date/time/timezone/place.
 * Simple, all main controls visible — per user preference.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthInputScreen(
    initial: BirthData,
    onCompute: (BirthData) -> Unit,
    isComputing: Boolean
) {
    var year by remember { mutableStateOf(initial.year) }
    var month by remember { mutableStateOf(initial.month) }
    var day by remember { mutableStateOf(initial.day) }
    var hour by remember { mutableStateOf(initial.hour) }
    var minute by remember { mutableStateOf(initial.minute) }
    var tz by remember { mutableStateOf(initial.tzOffset) }
    var timeInGmt by remember { mutableStateOf(initial.timeInGmt) }
    var cityIdx by remember { mutableStateOf(0) }
    var customPlace by remember { mutableStateOf(false) }
    var latText by remember { mutableStateOf(CITY_PRESETS[0].lat.toString()) }
    var lonText by remember { mutableStateOf(CITY_PRESETS[0].lon.toString()) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var tzExpanded by remember { mutableStateOf(false) }
    var cityExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("မွေးနေ့ / အချိန်", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)

        // Date + time buttons.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.weight(1f)) {
                Text("%04d-%02d-%02d".format(year, month, day))
            }
            OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.weight(1f)) {
                Text("%02d:%02d".format(hour, minute))
            }
        }

        // GMT toggle: when ON, the entered time is used as GMT directly
        // (no timezone conversion) — works for any location.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text("အချိန် GMT နဲ့ထည့်")
            Switch(checked = timeInGmt, onCheckedChange = { timeInGmt = it })
        }

        // Timezone dropdown (disabled in GMT mode).
        ExposedDropdownMenuBox(expanded = tzExpanded, onExpandedChange = { if (!timeInGmt) tzExpanded = it }) {
            OutlinedTextField(
                value = if (timeInGmt) "GMT (no conversion)" else "UTC%+.1f".format(tz),
                onValueChange = {},
                readOnly = true,
                enabled = !timeInGmt,
                label = { Text("Timezone") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(tzExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = tzExpanded, onDismissRequest = { tzExpanded = false }) {
                TZ_PRESETS.forEach { t ->
                    DropdownMenuItem(
                        text = { Text("UTC%+.1f".format(t)) },
                        onClick = { tz = t; tzExpanded = false }
                    )
                }
            }
        }

        // Place: preset or custom.
        if (!customPlace) {
            ExposedDropdownMenuBox(expanded = cityExpanded, onExpandedChange = { cityExpanded = it }) {
                OutlinedTextField(
                    value = "${CITY_PRESETS[cityIdx].nameMm} (${CITY_PRESETS[cityIdx].name})",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("မွေးရပ်မြေ") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(cityExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = cityExpanded, onDismissRequest = { cityExpanded = false }) {
                    CITY_PRESETS.forEachIndexed { i, c ->
                        DropdownMenuItem(
                            text = { Text("${c.nameMm} (${c.name})") },
                            onClick = {
                                cityIdx = i
                                latText = c.lat.toString()
                                lonText = c.lon.toString()
                                cityExpanded = false
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Custom… (လက်နဲ့ထည့်)") },
                        onClick = { customPlace = true; cityExpanded = false }
                    )
                }
            }
        } else {
            OutlinedTextField(
                value = latText,
                onValueChange = { latText = it },
                label = { Text("Latitude (ဒီဂရီ)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = lonText,
                onValueChange = { lonText = it },
                label = { Text("Longitude (ဒီဂရီ)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(onClick = { customPlace = false }) { Text("← မြို့ရွေးမယ်") }
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                val lat = latText.toDoubleOrNull() ?: 0.0
                val lon = lonText.toDoubleOrNull() ?: 0.0
                val place = if (customPlace) "Custom" else CITY_PRESETS[cityIdx].name
                onCompute(BirthData(year, month, day, hour, minute, tz, lat, lon, place, timeInGmt))
            },
            enabled = !isComputing,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isComputing) "တွက်နေတယ်…" else "ဇာတာဖွင့်")
        }
    }

    // Dialogs.
    if (showDatePicker) {
        val cal = Calendar.getInstance().apply { set(year, month - 1, day) }
        val state = rememberDatePickerState(initialSelectedDateMillis = cal.timeInMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        val c = Calendar.getInstance().apply { timeInMillis = it }
                        year = c.get(Calendar.YEAR)
                        month = c.get(Calendar.MONTH) + 1
                        day = c.get(Calendar.DAY_OF_MONTH)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state) }
    }

    if (showTimePicker) {
        val state = rememberTimePickerState(initialHour = hour, initialMinute = minute)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    hour = state.hour; minute = state.minute
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } },
            text = { TimePicker(state) }
        )
    }
}
