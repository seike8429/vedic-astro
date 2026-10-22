# ဗေဒင် — Burmese Vedic Astrology (Android, Phase 2)

Native Android app (Kotlin + Jetpack Compose) with two chart styles,
built on the validated calculation engine in `../android/VedicEngine.kt`.

## Features (Phase 2)

- **Style 1 — Burmese wheel chart** (MOTAA-style): circular wheel, 12 rashis
  with Burmese labels, planets at sidereal longitudes, Ascendant marker.
- **Style 2 — North Indian diamond**: Rashi (D-1), Navamsa (D-9), Bhava —
  switchable tabs, sign numbers + Burmese planet abbreviations per house.
- **Planetary table**: Planet | Degree (DMS) | Rashi | Nakshatra | Paada |
  RL | NL | SL (retrograde flagged).
- **Vimshottari dasha** timeline with dates.
- **Transit mode**: date picker + day-offset slider to view the chart at any date
  (natal dasha is kept).
- **Myanmar calendar date** (astronomical method, validated vs MOTAA V31.1).
- **Settings**: True/Mean Rahu toggle. Lahiri ayanamsa + Vehlow Equal houses fixed.
- **Burmese UI** throughout.

Phase 3 (later, needs the user's book): Sayadaw-style nakshatra/pada detail +
Burmese Rahu Kalam.

## Project layout

```
android-app/
├── app/
│   ├── libs/swisseph-2.01.00.jar   # vendored Swiss Ephemeris Java port
│   └── src/main/java/com/vedicastrology/app/
│       ├── engine/
│       │   ├── VedicEngine.kt      # validated math (package-adjusted copy)
│       │   ├── Ephemeris.kt        # SwissEph bridge (Moshier, Lahiri)
│       │   └── ChartCalculator.kt  # orchestration
│       ├── data/
│       │   ├── CityPresets.kt      # Myanmar cities + timezones
│       │   └── MyanmarCalendar.kt  # Burmese date (validated)
│       └── ui/
│           ├── theme/Theme.kt
│           ├── AppNav.kt           # ViewModel + screen state
│           ├── screens/            # BirthInput, Chart, PlanetTable
│           └── charts/             # WheelChart, DiamondChart (Canvas)
├── .github/workflows/build.yml
└── gradlew (+ wrapper -> Gradle 9.8.1)
```

## Ephemeris

- `swisseph-2.01.00.jar` (Thomas Mack Java port) is **vendored** in `app/libs/`
  — the build needs no network download for the library.
- **Moshier analytical mode** (`SEFLG_MOSEPH`): no ephemeris data files,
  ~1 arcsec accuracy, minimal APK size. Well within the 1-arcminute
  validation tolerance of the engine.
- License note: Swiss Ephemeris is AGPL / dual-licensed by Astrodienst.
  This app's use falls under the free edition; see `LICENSE-NOTE.md`.

## Building

### GitHub Actions (recommended — no local SDK needed)

1. Push this folder to a GitHub repo (or copy `.github/workflows/build.yml`
   into an existing repo containing this folder at root).
2. Push to `master`/`main`, or run the workflow manually
   (Actions → "Build Vedic Astro APK" → Run workflow).
3. Download the `VedicAstro-APK` artifact.

The workflow installs: Android SDK (cmdline-tools 14742923, android-37.0,
build-tools 37.0.0), Java 21 (Temurin) — same versions as the proven
SpiderVPN build.

### Local build

Requires: JDK 17+, Android SDK with `platforms;android-37.0` and
`build-tools;37.0.0`.

```sh
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Verification status

- Calculation engine: validated vs MOTAA V31.1 (max 0.84 arcmin error on
  9 bodies; dasha 5/6 exact). See `../VALIDATION.md`.
- Myanmar calendar: validated vs MOTAA screenshot date
  (2025-10-09 → 1387, Thadingyut, waning 3, Thursday — exact).
- SwissEph JAR API: verified (`swe_calc_ut`, `swe_houses`, `swe_set_sid_mode`,
  `SweConst.SE_HSYS_VEHLOW`, `SweDate.swe_julday` all present).
- UI code: written carefully against Compose Material3 APIs; **not yet
  compiled** (no Android SDK on the build VM). First compile happens in
  GitHub Actions — expect minor API adjustments on the first green build.

## No PHP anywhere

Kotlin + Android SDK only. Python was used for validation only (not shipped).
