package com.vedicastrology.app.engine.yoga

/**
 * Phase 1: Parashara Yoga detection + interpretation (spec: Muse AI Yoga Engine).
 *
 * Pure Kotlin, no Android imports. Deterministic: same chart in -> same reports out.
 * The language layer (YogaReport) explains calculated results only; it never
 * invents positions, formations, citations, or Dasha dates.
 *
 * HONESTY RULES (spec sections A, C, I):
 * - Strength % is NEVER formation evidence; it is supporting context only.
 * - A classical rule that is disputed, variant, or unverified is marked as such
 *   in [YogaRule.sourceNote]; no chapter/verse number is printed unless it was
 *   verified against a named edition (none is verified yet).
 * - Interpretations are conditional traditional readings, never guarantees.
 */

/** Detection outcome per spec A. */
enum class YogaStatus { FORMED, QUALIFIED, NOT_FORMED, INDETERMINATE }

/** Dasha relationship per spec D. */
enum class DashaLink { DIRECT, INDIRECT, NONE, INSUFFICIENT }

/** Dignity of a planet in a sign (D1 or D9). */
enum class Dignity { EXALTED, OWN, MOOLA, DEBILITATED, NEUTRAL }

/** One planet's quality assessment (spec C). Strength final is context only. */
data class PlanetQuality(
    val planet: String,
    val sign: String,
    val dignity: Dignity,
    /** Functional status per the app's documented rule (MOTAA Ch.7 §2, see catalog). */
    val functionalBenefic: Boolean,
    val combust: Boolean,
    val d9Sign: String,
    val d9Dignity: Dignity,
    /** MOTAA Ch.7 final strength, supporting evidence ONLY. */
    val strengthFinal: Int,
    /** Other planets in the same sign. */
    val conjunctPlanets: List<String>,
    /** Sidereal longitude (degrees) — shown as evidence on the card. */
    val longitude: Double = 0.0,
    /** Planets aspecting this planet (Parashari sign aspects) with their
     *  MOTAA functional status (true = benefic). */
    val aspecting: List<Pair<String, Boolean>> = emptyList()
)

/** One Dasha period relevant to a Yoga (dates are epoch millis; UI formats them). */
data class YogaDashaPeriod(
    val level: String, // "Mahadasha" / "Antardasha"
    val planet: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long
)

/**
 * Effect state shown as the card badge. Formation and effect are SEPARATE:
 * a formed Yoga is not automatically beneficent — an adverse Yoga (e.g.
 * Kemadruma) formed without cancellation is HARDSHIP (red), never green.
 */
enum class YogaEffect {
    /** Non-adverse, supporting present, limiting absent. */
    STRONG,
    /** Non-adverse, neither supporting nor limiting. */
    NEUTRAL,
    /** Non-adverse, limiting present. */
    WEAK,
    /** Adverse, but cancellation facts soften it. */
    DIMINISHED,
    /** Adverse, no cancellation. */
    HARDSHIP,
    /** Not formed / indeterminate: no effect to grade. */
    NONE
}

/** One detected Yoga with all evidence needed for the report (spec E). */
data class DetectedYoga(
    val ruleId: String,
    val status: YogaStatus,
    /** Planets that form the Yoga. */
    val planets: List<String>,
    /** Structured formation facts (Burmese sentences are built in YogaReport). */
    val formationFacts: List<String>,
    /** Cancellation evidence, kept SEPARATE from strength (Error Prevention Rule 3):
     *  Kemadruma softening, Neecha Bhanga branches, Shakata Jupiter-strong. */
    val cancellationFacts: List<String> = emptyList(),
    val supportingFactors: List<String>,
    val limitingFactors: List<String>,
    val quality: Map<String, PlanetQuality>,
    val dashaLink: DashaLink,
    val dashaPeriods: List<YogaDashaPeriod>,
    /** Current 5-level path planets (for the Dasha relevance line). */
    val currentPathPlanets: List<String>,
    /** Conditional Burmese interpretation built by YogaReport (never a guarantee). */
    val interpretation: String,
    /** §1 အဓိကဟောကိန်း: the primary reading alone (themes + conditional). */
    val mainReading: String = "",
    /** §4 အကျိုးပေးကာလ prose: the dasha clause alone ("" when none). */
    val dashaReading: String = "",
    /** Effect badge state (formation ≠ beneficence). */
    val effect: YogaEffect = YogaEffect.NONE,
    /** Why the badge state was assigned (shown in §5 detail). */
    val effectReason: String = "",
    /** §3 caution line: the delay implication, kept OUT of the main reading
     *  so each card's primary prediction stays distinctive. */
    val cautionNote: String = "",
    /** Card header names. Pair rules (parivartana, sambandha, dhana,
     *  grahana) show their planet pair, so two instances of one rule
     *  never look like a duplicated card. */
    val displayNameMm: String = "",
    val displayNameEn: String = "",
    /** Why an INDIRECT dasha link holds (conjunct / dispositor relation). */
    val dashaLinkDetail: String = "",
    /** The 5-level path active at the query date (level "1".."5"). */
    val activePath: List<YogaDashaPeriod> = emptyList(),
    /** Detailed narrative for DIRECT yogas only (formation + quality +
     *  dasha connection + traditional meaning, all from verified fields).
     *  Empty for every other link — INDIRECT yogas are related, not "in"
     *  the current dasha, and never get this section. */
    val dashaNarrative: String = ""
)

/** Full Yoga report for one chart (spec E + F). */
data class YogaReport(
    val chartName: String,
    val yogas: List<DetectedYoga>,
    /** Shared-planet / conflicting-indication notes (spec F). Empty when none. */
    val conflictNotes: List<String>
)
