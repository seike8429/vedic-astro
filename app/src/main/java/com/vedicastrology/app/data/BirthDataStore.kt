package com.vedicastrology.app.data

import android.content.Context
import com.vedicastrology.app.engine.BirthData
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** One saved birth chart: a user-given name plus the birth data. */
data class SavedChart(
    val id: String,
    val name: String,
    val birth: BirthData
) {
    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}

/**
 * Persists MANY birth charts (SharedPreferences + org.json, built into
 * Android — no extra dependency). Each chart is just small text data, so
 * there is no practical cap on the number of saved charts.
 *
 * Keys: "charts_json" (JSONArray of chart objects), "default_id" (the id
 * of the chart that opens automatically on app launch).
 */
class BirthDataStore(context: Context) {

    private val prefs =
        context.getSharedPreferences("vedic_charts", Context.MODE_PRIVATE)

    /** All saved charts in saved order. Never throws on bad data (skips it). */
    fun loadAll(): List<SavedChart> {
        val raw = prefs.getString(KEY_CHARTS, null) ?: return emptyList()
        val out = mutableListOf<SavedChart>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                fromJson(arr.optJSONObject(i))?.let { out.add(it) }
            }
        } catch (_: Exception) {
            // Corrupt store: treat as empty; the caller re-seeds.
        }
        return out
    }

    /** Id of the default (launch) chart, or null if none set. */
    fun loadDefaultId(): String? = prefs.getString(KEY_DEFAULT, null)

    /** The default chart, falling back to the first saved chart. */
    fun getDefault(): SavedChart? {
        val all = loadAll()
        if (all.isEmpty()) return null
        val id = loadDefaultId()
        return all.firstOrNull { it.id == id } ?: all.first()
    }

    /** Insert or replace [chart] by id. Also sets it as default if none exists. */
    fun saveChart(chart: SavedChart) {
        val all = loadAll().toMutableList()
        val idx = all.indexOfFirst { it.id == chart.id }
        if (idx >= 0) all[idx] = chart else all.add(chart)
        persist(all)
        if (loadDefaultId() == null) setDefault(chart.id)
    }

    /**
     * Delete one chart. Returns the remaining charts. If the deleted chart
     * was the default, the first remaining chart becomes the default
     * (or none, if the list is now empty).
     */
    fun deleteChart(id: String): List<SavedChart> {
        val remaining = loadAll().filter { it.id != id }
        persist(remaining)
        if (loadDefaultId() == id) {
            val next = remaining.firstOrNull()
            if (next != null) setDefault(next.id)
            else prefs.edit().remove(KEY_DEFAULT).apply()
        }
        return remaining
    }

    /** Make [id] the chart that opens automatically on app launch. */
    fun setDefault(id: String) {
        prefs.edit().putString(KEY_DEFAULT, id).apply()
    }

    private fun persist(charts: List<SavedChart>) {
        val arr = JSONArray()
        for (c in charts) arr.put(toJson(c))
        prefs.edit().putString(KEY_CHARTS, arr.toString()).apply()
    }

    private fun toJson(c: SavedChart): JSONObject {
        val b = c.birth
        return JSONObject()
            .put("id", c.id)
            .put("name", c.name)
            .put("year", b.year)
            .put("month", b.month)
            .put("day", b.day)
            .put("hour", b.hour)
            .put("minute", b.minute)
            .put("tz", b.tzOffset)
            .put("lat", b.latitude)
            .put("lon", b.longitude)
            .put("place", b.placeName)
            .put("gmt", b.timeInGmt)
    }

    private fun fromJson(o: JSONObject?): SavedChart? {
        if (o == null) return null
        val id = o.optString("id")
        if (id.isEmpty()) return null
        val year = o.optInt("year", 0)
        val month = o.optInt("month", 0)
        val day = o.optInt("day", 0)
        if (year == 0 || month == 0 || day == 0) return null
        return SavedChart(
            id = id,
            name = o.optString("name", ""),
            birth = BirthData(
                year = year, month = month, day = day,
                hour = o.optInt("hour", 0),
                minute = o.optInt("minute", 0),
                tzOffset = o.optDouble("tz", 6.5),
                latitude = o.optDouble("lat", 0.0),
                longitude = o.optDouble("lon", 0.0),
                placeName = o.optString("place", ""),
                timeInGmt = o.optBoolean("gmt", false)
            )
        )
    }

    private companion object {
        const val KEY_CHARTS = "charts_json"
        const val KEY_DEFAULT = "default_id"
    }
}
