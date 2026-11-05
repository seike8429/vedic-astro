package com.vedicastrology.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject


/**
 * Astro News tab: 2026–2027 mundane events, chronological, with the
 * traditional narrative layer and the famous-sites layer (tappable URLs).
 * Times are Myanmar Time (MMT); signs are Sidereal Lahiri. No personal
 * predictions: narratives are traditional interpretations only.
 */
@Composable
fun AstroNewsScreen() {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var events by remember { mutableStateOf<List<NewsEvent>?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var yearSel by remember { mutableStateOf(2) } // 0=2026, 1=2027, 2=both
    var catSel by remember { mutableStateOf("all") }

    LaunchedEffect(Unit) {
        try {
            val json = withContext(Dispatchers.IO) {
                context.assets.open("astro-news-2026-2027.json")
                    .bufferedReader().use { it.readText() }
            }
            events = parseNewsEvents(JSONObject(json))
        } catch (t: Throwable) {
            loadError = t.message ?: "ဖတ်မရပါ"
        }
    }

    val all = events
    if (all == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (loadError != null) {
                Text(loadError!!, modifier = Modifier.padding(24.dp))
            } else {
                CircularProgressIndicator()
            }
        }
        return
    }

    val filtered = all.filter { e ->
        (yearSel == 2 || e.year == 2026 + yearSel) &&
            (catSel == "all" || catGroup(e.category) == catSel)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                "တနှစ်စာ Astro News — ၂၀၂၆ နှင့် ၂၀၂၇",
                style = MaterialTheme.typography.titleLarge
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("၂၀၂၆" to 0, "၂၀၂၇" to 1, "နှစ်ခုလုံး" to 2).forEach { (label, v) ->
                    FilterChip(
                        selected = yearSel == v,
                        onClick = { yearSel = v },
                        label = { Text(label) }
                    )
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "အားလုံး" to "all",
                    "နေကြတ်/လကြတ်" to "eclipse",
                    "ဂြိုလ်စစ်ထိုး" to "war",
                    "ပြောင်းပြန်" to "retro",
                    "ဆုံမှု/ဆန့်ကျင်ဘက်" to "conjopp",
                    "ရာသီပြောင်း" to "ingress",
                    "နိစ်ထပ်မှု" to "debil"
                ).forEach { (label, v) ->
                    FilterChip(
                        selected = catSel == v,
                        onClick = { catSel = v },
                        label = { Text(label) }
                    )
                }
            }
        }
        items(filtered, key = { it.id }) { e ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(e.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${e.dateLine} · ${catLabel(e.category)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (e.visibility != null) {
                        Text(e.visibility, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (e.narrative != null) {
                        Text(e.narrative, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (e.sitesText != null && e.urls.isNotEmpty()) {
                        Text(
                            "နာမည်ကြီးဆိုဒ်များက ဘာပြောသလဲ",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(e.sitesText, style = MaterialTheme.typography.bodySmall)
                        e.urls.forEach { url ->
                            Text(
                                url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable {
                                    uriHandler.openUri(url)
                                }
                            )
                        }
                    }
                }
            }
        }
        item {
            Text(
                "အချိန်အားလုံး မြန်မာစံတော်ချိန် (MMT) ဖြစ်သည်။ " +
                    "ရာသီခွင်အားလုံး Sidereal Lahiri ဖြစ်သည်။ " +
                    "ဤအကြောင်းအရာသည် ရိုးရာအဓိပ္ပာယ်ဖွင့်ဆိုချက်သာ ဖြစ်သည်။",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}
