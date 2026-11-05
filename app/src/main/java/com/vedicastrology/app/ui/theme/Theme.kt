package com.vedicastrology.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF6A1B1A),        // deep maroon (traditional)
    onPrimary = Color(0xFFFFF8E7),
    secondary = Color(0xFFB8860B),      // gold
    background = Color(0xFFFFFDF5),
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF2B2118),
    onSurface = Color(0xFF2B2118),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD9A441),
    onPrimary = Color(0xFF2B2118),
    secondary = Color(0xFFB8860B),
    background = Color(0xFF1A1410),
    surface = Color(0xFF241C15),
    onBackground = Color(0xFFF5EAD6),
    onSurface = Color(0xFFF5EAD6),
)

@Composable
fun VedicAstroTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}

/** Burmese display names / abbreviations for planets. */
object PlanetNames {
    /** Full Burmese names. */
    val full = mapOf(
        "Lagna" to "လဂ်",
        "Sun" to "တနင်္ဂနွေ",
        "Moon" to "တနင်္လာ",
        "Mars" to "အင်္ဂါ",
        "Mercury" to "ဗုဒ္ဓဟူး",
        "Jupiter" to "ကြာသပတေး",
        "Venus" to "သောကြာ",
        "Saturn" to "စနေ",
        "Rahu" to "ရာဟု",
        "Ketu" to "ကေတု"
    )

    /** Compact 2-char abbreviations for chart drawing. */
    val short = mapOf(
        "Lagna" to "လဂ်",
        "Sun" to "ရဝိ",
        "Moon" to "စန္ဒ",
        "Mars" to "ကူ",
        "Mercury" to "ဗုဒ္ဓ",
        "Jupiter" to "ဂုရု",
        "Venus" to "သုက်",
        "Saturn" to "မန္ဒ",
        "Rahu" to "ရာဟု",
        "Ketu" to "ကေတု"
    )

    /** English short codes (Su, Mo, Ma, Me, Ju, Ve, Sa, Ra, Ke, As). */
    val code = mapOf(
        "Lagna" to "As",
        "Sun" to "Su",
        "Moon" to "Mo",
        "Mars" to "Ma",
        "Mercury" to "Me",
        "Jupiter" to "Ju",
        "Venus" to "Ve",
        "Saturn" to "Sa",
        "Rahu" to "Ra",
        "Ketu" to "Ke"
    )
}
