package com.vedicastrology.app.data

/** Birth-place presets for Myanmar (lat/long in decimal degrees). */
data class CityPreset(val name: String, val nameMm: String, val lat: Double, val lon: Double)

val CITY_PRESETS = listOf(
    // Yangon/Mandalay/Naypyidaw match the user's confirmed coordinates
    // (Yangon 16°51'N 96°11'E from his own reference-app screenshots).
    CityPreset("Yangon", "ရန်ကုန်", 16.85, 96.1833),
    CityPreset("Mandalay", "မန္တလေး", 21.967, 96.083),
    CityPreset("Naypyidaw", "နေပြည်တော်", 19.75, 96.117),
    CityPreset("Sagaing", "စစ်ကိုင်း", 21.88, 95.98),
    CityPreset("Bago", "ပဲခူး", 17.34, 96.50),
    CityPreset("Mawlamyine", "မော်လမြိုင်", 16.49, 97.63),
    CityPreset("Taunggyi", "တောင်ကြီး", 20.79, 97.04),
    CityPreset("Myitkyina", "မြစ်ကြီးနား", 25.38, 97.40),
    CityPreset("Sittwe", "စစ်တွေ", 20.14, 92.90),
    CityPreset("Pathein", "ပုသိမ်", 16.77, 94.74),
    CityPreset("Monywa", "မုံရွာ", 22.11, 95.14),
    CityPreset("Meiktila", "မိတ္ထီလာ", 20.88, 95.86),
    CityPreset("Pyay", "ပြည်", 18.82, 95.22),
    CityPreset("Lashio", "လားရှိုး", 22.93, 97.75),
    CityPreset("Magway", "မကွေး", 20.15, 94.95),
    CityPreset("Hpa-An", "ဘားအံ", 16.89, 97.63),
    CityPreset("Kawkareik", "ကော့ကရိတ်", 16.56, 98.24),
)

/** Common UTC offsets (hours). Myanmar = +6:30. */
val TZ_PRESETS = listOf(6.5, 6.0, 7.0, 8.0, 5.5)
