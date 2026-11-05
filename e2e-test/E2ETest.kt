import com.vedicastrology.app.engine.BirthData
import com.vedicastrology.app.engine.ChartCalculator
import kotlin.math.abs

// E2E: Android ephemeris path (Java port, Moshier) vs validated Python values.
// Reference: MOTAA V31.1, 4.6.1995 16:15:15 +6:30, Kawkareik 16.56N 98.24E.
// Expected (from VALIDATION.md, Python full-ephemeris):
// Sun Tau 19:31:50.5, Moon Cnc 25:47:27.6, Mars Leo 10:45:34.3,
// Mercury Tau 20:47:11.2 R, Jupiter Sco 16:20:47.6 R, Venus Ari 28:34:38.7,
// Saturn Psc 00:07:00.1, Rahu Lib 11:02:37.2 R, Ketu Ari 11:02:37.2
fun main() {
    val birth = BirthData(1995, 6, 4, 16, 15, 6.5, 16.56, 98.24, "Kawkareik")
    // Note: seconds (15s) ignored by BirthData (min precision) — sub-arcmin impact.
    val result = ChartCalculator.compute(birth)

    val expected = mapOf(
        // sign, deg, min, sec, retro
        "Sun" to listOf("Taurus", 19, 31, 50.5, 0),
        "Moon" to listOf("Cancer", 25, 47, 27.6, 0),
        "Mars" to listOf("Leo", 10, 45, 34.3, 0),
        "Mercury" to listOf("Taurus", 20, 47, 11.2, 1),
        "Jupiter" to listOf("Scorpio", 16, 20, 47.6, 1),
        "Venus" to listOf("Aries", 28, 34, 38.7, 0),
        "Saturn" to listOf("Pisces", 0, 7, 0.1, 0),
        "Rahu" to listOf("Libra", 11, 2, 37.2, 1),
        "Ketu" to listOf("Aries", 11, 2, 37.2, 1), // Ketu moves with Rahu: retrograde
    )

    var allPass = true
    for (cp in result.planets) {
        val name = cp.row.planet
        if (name == "Lagna") {
            println("Lagna: ${cp.row.sign} ${cp.row.dms} (asc=${"%.2f".format(result.ascendant)})")
            continue
        }
        val exp = expected[name] ?: continue
        val expLon = signToLon(exp[0] as String, (exp[1] as Int) + (exp[2] as Int)/60.0 + (exp[3] as Double)/3600.0)
        var err = abs(cp.row.longitude - expLon) * 60.0  // arcmin
        if (err > 180*60) err = 360*60 - err
        val retroOk = (cp.retrograde == ((exp[4] as Int) == 1))
        val pass = err < 2.0 && retroOk && cp.row.sign == exp[0]
        if (!pass) allPass = false
        println("${if (pass) "PASS" else "FAIL"} $name: got ${cp.row.sign} ${cp.row.dms} R=${cp.retrograde} | err=${"%.2f".format(err)}' retroOk=$retroOk")
    }
    // Dasha check: first transition Ketu->Venus ~2000-10-16
    val d0 = result.dashas[0]
    val d1 = result.dashas[1]
    println("Dasha0: ${d0.lord} ${d0.startY}-${d0.startM}-${d0.startD} -> ${d0.endY}-${d0.endM}-${d0.endD}")
    println("Dasha1: ${d1.lord} ${d1.startY}-${d1.startM}-${d1.startD} -> ${d1.endY}-${d1.endM}-${d1.endD}")
    val dashaOk = d0.lord == "Mercury" && d1.lord == "Ketu" && result.dashas[2].lord == "Venus" // Moon in Ashlesha -> Mercury at birth
    println(if (dashaOk) "PASS dasha sequence" else "FAIL dasha sequence")
    println(if (allPass && dashaOk) "ALL E2E PASS" else "E2E FAILURES PRESENT")
}

fun signToLon(sign: String, degInSign: Double): Double {
    val idx = listOf("Aries","Taurus","Gemini","Cancer","Leo","Virgo","Libra","Scorpio","Sagittarius","Capricorn","Aquarius","Pisces").indexOf(sign)
    return idx * 30 + degInSign
}
