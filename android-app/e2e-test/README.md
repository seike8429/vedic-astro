# E2E validation (JVM, not Android)

`E2ETest.kt` runs the app's real ephemeris path (vendored SwissEph JAR,
Moshier mode) + `VedicEngine` + `ChartCalculator` on a JVM and checks
against the validated Python values (see `../VALIDATION.md`).

Reference: MOTAA V31.1, 4.6.1995 16:15:15 +6:30, Kawkareik.

Run (needs kotlinc + a JRE):
```sh
kotlinc -cp app/libs/swisseph-2.01.00.jar \
  app/src/main/java/com/vedicastrology/app/engine/*.kt \
  e2e-test/E2ETest.kt -d /tmp/vedic-e2e
java -cp /tmp/vedic-e2e:app/libs/swisseph-2.01.00.jar:kotlin-stdlib.jar E2ETestKt
```

Last run 2026-10-23: **ALL E2E PASS**
- 9/9 planets 0.00–0.13 arcmin error (tolerance 1')
- Retrograde flags all correct (incl. Ketu with Rahu)
- Dasha: Mercury (birth) -> Ketu 2000-10-16 -> Venus — exact match
