package com.vedicastrology.app.engine.transit

import java.text.SimpleDateFormat
import java.util.TimeZone

/**
 * "Active now" rule for the Transit effect tab. Pure Kotlin, no Android
 * imports, so the standalone harness can verify it.
 *
 * Dates are MMT calendar dates (yyyy-MM-dd), matching the Astro News
 * dataset. A period is active when:
 *  - its window contains today (start <= today <= end), or
 *  - its start falls within the next [nearDays] days (upcoming window), or
 *  - a point event (start == end) falls within [nearDays] days either side
 *    of today (recently passed effects still read as current, like weather
 *    that only just changed), or
 *  - an open-start window (start missing, e.g. a retrograde that began
 *    before 2026) has not yet ended.
 * Empty or unparseable dates are never active.
 */
object TransitEffectLogic {
    const val NEAR_DAYS_DEFAULT = 14

    private val mmt = TimeZone.getTimeZone("GMT+06:30")

    fun dayMillis(date: String): Long? {
        if (date.isBlank()) return null
        return try {
            SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply {
                timeZone = mmt
                isLenient = false
            }.parse(date)?.time
        } catch (e: Exception) {
            null
        }
    }

    /** Start of the MMT calendar day containing [nowMillis]. */
    fun todayDayMillis(nowMillis: Long): Long {
        val fmt = SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply { timeZone = mmt }
        return dayMillis(fmt.format(java.util.Date(nowMillis))) ?: nowMillis
    }

    fun isActive(
        startDate: String,
        endDate: String,
        todayDay: Long,
        nearDays: Int = NEAR_DAYS_DEFAULT
    ): Boolean {
        val start = dayMillis(startDate)
        val end = dayMillis(endDate)
        if (start == null && end == null) return false
        val day = 24L * 60 * 60 * 1000
        val near = nearDays * day
        if (start != null && end != null) {
            if (start == end) {
                // Point event: within nearDays either side of today.
                return kotlin.math.abs(start - todayDay) <= near
            }
            // Window: contains today, or starts within the next nearDays.
            return (todayDay in start..end) || (start in (todayDay + 1)..(todayDay + near))
        }
        if (start != null) {
            // Open-end window: active from its start onward (bounded by the
            // near window only for the upcoming case).
            return todayDay >= start - near
        }
        // Open-start window: active until its end.
        return todayDay <= end!!
    }
}
