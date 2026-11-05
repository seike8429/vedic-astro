package com.vedicastrology.app.ui.screens

import org.json.JSONArray
import org.json.JSONObject

/** One Astro News event, built from the bundled JSON (facts) + folded narratives. */
data class NewsEvent(
    val id: String,
    val year: Int,
    val category: String,
    val sortDate: String,
    val dateLine: String,
    // Raw MMT dates (yyyy-MM-dd, "" = open): point events carry date in both.
    val startDate: String,
    val endDate: String,
    val title: String,
    val visibility: String?,
    val narrative: String?,
    val sitesText: String?,
    val urls: List<String>
)

private fun JSONObject.str(key: String): String? =
    if (has(key) && !isNull(key)) optString(key) else null

private fun JSONObject.strList(key: String): List<String> {
    val a: JSONArray = optJSONArray(key) ?: return emptyList()
    return (0 until a.length()).map { a.optString(it) }
}

private fun fmtDeg(v: Double): String =
    if (v == Double.NaN) "" else "%.1f".format(v)

fun parseNewsEvents(root: JSONObject): List<NewsEvent> {
    val arr = root.optJSONArray("events") ?: return emptyList()
    val out = ArrayList<NewsEvent>()
    for (i in 0 until arr.length()) {
        val o = arr.getJSONObject(i)
        val id = o.optString("id")
        val year = o.optInt("year", 0)
        val cat = o.optString("category")
        val bodiesMm = o.strList("bodies_mm").ifEmpty { o.strList("bodies_my") }
        val bodyMm = o.str("body_mm")
        val signMm = o.str("sign_mm") ?: o.str("sign_my")  // Saturn-Neptune uses sign_my; without this its title rendered "null 6.5°".
        val deg = fmtDeg(o.optDouble("degree", Double.NaN))
        val title: String
        val sortDate: String
        val dateLine: String
        var visibility: String? = null
        when (cat) {
            "eclipse_solar", "eclipse_lunar" -> {
                val kind = if (cat == "eclipse_solar") "နေကြတ်" else "လကြတ်"
                val typ = o.str("eclipse_type") ?: ""
                title = "$kind ($typ) — $signMm $deg°"
                sortDate = o.str("date") ?: ""
                val t = o.str("time_mmt")
                dateLine = sortDate + (if (t != null) " $t MMT" else "")
                val vis = o.optBoolean("myanmar_visible", false)
                val localMax = o.str("local_max_time_mmt")
                val obsc = o.optDouble("max_obscuration", Double.NaN)
                visibility = if (vis) {
                    var v = "မြန်မာကနေ မြင်ရသည်"
                    if (id == "eclipse-2027-08-02") v += " (တွက်ချက်မှုအရ)"
                    if (localMax != null) v += " · ဒေသအမြင့်ဆုံး $localMax MMT"
                    if (!obsc.isNaN()) v += " (~${(obsc * 100).toInt()}%)"
                    v
                } else {
                    "မြန်မာကနေ မမြင်ရပါ"
                }
            }
            "planetary_war" -> {
                title = "${bodiesMm.joinToString("–")} စစ်ထိုး ($signMm $deg°)"
                sortDate = o.str("date") ?: ""
                val t = o.str("time_mmt")
                dateLine = sortDate + (if (t != null) " $t MMT" else "")
            }
            "opposition" -> {
                val sunSign = o.str("sun_sign_mm")
                val sunDeg = fmtDeg(o.optDouble("sun_degree", Double.NaN))
                title = "${bodiesMm.joinToString("–")} ဆန့်ကျင်ဘက် (နေ $sunSign $sunDeg°)"
                sortDate = o.str("date") ?: ""
                val t = o.str("time_mmt")
                dateLine = sortDate + (if (t != null) " $t MMT" else "")
            }
            "conjunction" -> {
                title = "${bodiesMm.joinToString("–")} ဆုံမှု ($signMm $deg°)"
                sortDate = o.str("date") ?: ""
                val t = o.str("time_mmt")
                dateLine = sortDate + (if (t != null) " $t MMT" else "")
            }
            "retrograde" -> {
                val startD = o.str("start_date")
                val endD = o.str("end_date")
                val startT = o.str("start_time_mmt")
                val endT = o.str("end_time_mmt")
                val sDisp = if (startD != null) startD + (if (startT != null) " $startT" else "") else "2026 မတိုင်မီ"
                val eDisp = if (endD != null) endD + (if (endT != null) " $endT" else "") else "အဆုံး မသိ"
                title = "$bodyMm ပြောင်းပြန်"
                sortDate = startD ?: endD ?: ""
                val sSign = o.str("sign_start_mm")
                val sDeg = fmtDeg(o.optDouble("degree_start", Double.NaN))
                val eSign = o.str("sign_end_mm")
                val eDeg = fmtDeg(o.optDouble("degree_end", Double.NaN))
                val range = buildString {
                    if (sSign != null && sDeg.isNotEmpty()) append("$sSign $sDeg°")
                    append(" → ")
                    if (eSign != null && eDeg.isNotEmpty()) append("$eSign $eDeg°")
                }
                dateLine = "$sDisp → $eDisp" + (if (range.trim() != "→") " ($range)" else "")
            }
            "ingress" -> {
                title = "$bodyMm ရာသီပြောင်း: ${o.str("from_sign_mm")} → ${o.str("to_sign_mm")}"
                sortDate = o.str("date") ?: ""
                val t = o.str("time_mmt")
                dateLine = sortDate + (if (t != null) " $t MMT" else "")
            }
            "debilitation_window" -> {
                val planets = o.strList("planets_mm")
                val count = o.optInt("count", planets.size)
                title = "နိစ်ထပ်မှု ($count လုံး): ${planets.joinToString(", ")}"
                val sD = o.str("start_date") ?: ""
                val eD = o.str("end_date") ?: ""
                sortDate = sD
                dateLine = "$sD → $eD"
            }
            else -> {
                title = id
                sortDate = o.str("date") ?: o.str("start_date") ?: ""
                dateLine = sortDate
            }
        }
        out.add(
            NewsEvent(
                id = id,
                startDate = o.str("start_date") ?: o.str("date") ?: "",
                endDate = o.str("end_date") ?: o.str("date") ?: "",
                year = year,
                category = cat,
                sortDate = sortDate,
                dateLine = dateLine,
                title = title,
                visibility = visibility,
                narrative = o.str("narrative_my")?.takeIf { it.isNotBlank() },
                sitesText = o.str("sites_my")?.takeIf { it.isNotBlank() },
                urls = o.strList("sites_urls")
            )
        )
    }
    return out.sortedWith(compareBy({ it.sortDate }, { it.dateLine }))
}

fun catLabel(cat: String): String = when (cat) {
    "eclipse_solar", "eclipse_lunar" -> "ဂရဟဏ"
    "planetary_war" -> "ဂြိုလ်စစ်ထိုး"
    "retrograde" -> "ပြောင်းပြန်"
    "conjunction" -> "ဆုံမှု"
    "opposition" -> "ဆန့်ကျင်ဘက်"
    "ingress" -> "ရာသီပြောင်း"
    "debilitation_window" -> "နိစ်ထပ်မှု"
    else -> cat
}

fun catGroup(cat: String): String = when (cat) {
    "eclipse_solar", "eclipse_lunar" -> "eclipse"
    "planetary_war" -> "war"
    "retrograde" -> "retro"
    "conjunction", "opposition" -> "conjopp"
    "ingress" -> "ingress"
    "debilitation_window" -> "debil"
    else -> "other"
}
