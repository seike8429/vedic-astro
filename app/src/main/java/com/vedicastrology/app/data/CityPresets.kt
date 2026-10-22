package com.vedicastrology.app.data

/** Birth-place presets for Myanmar (lat/long in decimal degrees). */
data class CityPreset(val name: String, val nameMm: String, val lat: Double, val lon: Double)

val CITY_PRESETS = listOf(
    CityPreset("Yangon", "ရန်ကုန်", 16.84, 96.17),
    CityPreset("Mandalay", "မန္တလေး", 21.98, 96.09),
    CityPreset("Naypyidaw", "နေပြည်တော်", 19.76, 96.07),
    CityPreset("Kawkareik", "ကော့ကရိတ်", 16.56, 98.24),
    CityPreset("Mawlamyine", "မော်လမြိုင်", 16.49, 97.63),
    CityPreset("Taunggyi", "တောင်ကြီး", 20.79, 97.04),
    CityPreset("Bago", "ပဲခူး", 17.34, 96.50),
    CityPreset("Myitkyina", "မြစ်ကြီးနား", 25.38, 97.40),
)

/** Common UTC offsets (hours). Myanmar = +6:30. */
val TZ_PRESETS = listOf(6.5, 6.0, 7.0, 8.0, 5.5)
