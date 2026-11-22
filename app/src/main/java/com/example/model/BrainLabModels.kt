package com.example.model

import org.json.JSONObject

enum class BrainLabPillar(
    val title: String,
    val subtitle: String,
    val badge: String
) {
    WORKING_MEMORY(
        title = "Working Memory",
        subtitle = "Active updating, sequence reversal & distractor inhibition",
        badge = "ATTENTION"
    ),
    CRITICAL_REASONING(
        title = "Critical Deduction",
        subtitle = "Venn-verified syllogisms, seating grids & fallacy critique",
        badge = "LOGIC"
    ),
    VISUAL_QUANT(
        title = "Scan & Quant Blitz",
        subtitle = "Peripheral Schulte tables, trail switching & fast mental math",
        badge = "SPEED"
    ),
    CALM_RESET(
        title = "Calm & Reset",
        subtitle = "Nervous-system breathing pacers, 20-20-20 & untimed Zen grids",
        badge = "RECOVERY"
    )
}

enum class BrainLabActivity(
    val pillar: BrainLabPillar,
    val title: String,
    val shortSummary: String,
    val examTransfer: String,
    val defaultBudgetMinutes: Int,
    val countsAsCognitiveStudy: Boolean
) {
    N_BACK(
        pillar = BrainLabPillar.WORKING_MEMORY,
        title = "Adaptive N-Back (1–4)",
        shortSummary = "Dual or single position + letter updating with adaptive tiering",
        examTransfer = "Holds nested legal clauses, RC threads, and multi-condition rules in active memory",
        defaultBudgetMinutes = 3,
        countsAsCognitiveStudy = true
    ),
    CORSI_SPAN(
        pillar = BrainLabPillar.WORKING_MEMORY,
        title = "Corsi & Reverse Span",
        shortSummary = "4×4 / 5×5 spatial sequence & reverse sequence manipulation",
        examTransfer = "Strengthens active manipulation rather than passive visual echo",
        defaultBudgetMinutes = 3,
        countsAsCognitiveStudy = true
    ),
    STROOP_SWITCH(
        pillar = BrainLabPillar.WORKING_MEMORY,
        title = "Stroop Rule-Switch",
        shortSummary = "Color-word & arrow-direction conflicts with live rule flips",
        examTransfer = "Trains impulse inhibition against NOT-incorrect distractor traps",
        defaultBudgetMinutes = 3,
        countsAsCognitiveStudy = true
    ),
    SYLLOGISM(
        pillar = BrainLabPillar.CRITICAL_REASONING,
        title = "Venn Syllogism Arena",
        shortSummary = "Exhaustively verified 2–3 premise deductions with counterexamples",
        examTransfer = "Eliminates intuitive leaps on All/Some/No/Some-Not exam items",
        defaultBudgetMinutes = 4,
        countsAsCognitiveStudy = true
    ),
    CONSTRAINT_SEATING(
        pillar = BrainLabPillar.CRITICAL_REASONING,
        title = "Seating & Pairing Grid",
        shortSummary = "Unique-solution DILR mini-puzzles with a 1-tap scratchpad",
        examTransfer = "Sharpens structured elimination for CAT DILR & analytical reasoning",
        defaultBudgetMinutes = 5,
        countsAsCognitiveStudy = true
    ),
    ARGUMENT_CRITIQUE(
        pillar = BrainLabPillar.CRITICAL_REASONING,
        title = "Argument & Fallacy",
        shortSummary = "Pinpoint required assumptions, inferences & named logical fallacies",
        examTransfer = "Targets editorial CR passages, CLAT & GMAT assumption traps",
        defaultBudgetMinutes = 4,
        countsAsCognitiveStudy = true
    ),
    DATA_SUFFICIENCY(
        pillar = BrainLabPillar.CRITICAL_REASONING,
        title = "Data Sufficiency Check",
        shortSummary = "Judge Statement I & II sufficiency without full arithmetic",
        examTransfer = "Catches hidden negative, zero, and fractional boundary traps",
        defaultBudgetMinutes = 4,
        countsAsCognitiveStudy = true
    ),
    SCHULTE_TRAIL(
        pillar = BrainLabPillar.VISUAL_QUANT,
        title = "Schulte & Trail Grid",
        shortSummary = "5×5 / 6×6 peripheral scan and alternating 1→A→2→B switching",
        examTransfer = "Widens eye span for faster RC skimming & clean OMR row alignment",
        defaultBudgetMinutes = 3,
        countsAsCognitiveStudy = true
    ),
    MENTAL_QUANT(
        pillar = BrainLabPillar.VISUAL_QUANT,
        title = "Mental Quant Blitz",
        shortSummary = "60s fraction-to-% benchmarks, compound shifts & ratio duels",
        examTransfer = "Builds pen-free DI approximation speed under strict time pressure",
        defaultBudgetMinutes = 3,
        countsAsCognitiveStudy = true
    ),
    GUIDED_BREATHING(
        pillar = BrainLabPillar.CALM_RESET,
        title = "Guided Breathing Pacer",
        shortSummary = "Box 4-4-4-4, Cyclic Sigh & 4-7-8 smooth 60fps ring + haptics",
        examTransfer = "Downshifts pre-exam adrenaline so prefrontal working memory stays online",
        defaultBudgetMinutes = 3,
        countsAsCognitiveStudy = false
    ),
    VISUAL_SOFT_RESET(
        pillar = BrainLabPillar.CALM_RESET,
        title = "60s Visual Soft-Reset",
        shortSummary = "20-20-20 far-gaze, shoulder/jaw release & slow blink cadence",
        examTransfer = "Relieves eye strain after long mock tests or dense textbook sessions",
        defaultBudgetMinutes = 1,
        countsAsCognitiveStudy = false
    ),
    ZEN_LOGIC_GRID(
        pillar = BrainLabPillar.CALM_RESET,
        title = "Untimed Zen Logic Grid",
        shortSummary = "Calm 4×4 & 6×6 single-solution grids with zero timers or penalties",
        examTransfer = "Settles racing thoughts cleanly post-mock or before sleep",
        defaultBudgetMinutes = 5,
        countsAsCognitiveStudy = false
    )
}

enum class NBackMode(val label: String) {
    DUAL("Dual (Position + Letter)"),
    POSITION_ONLY("Position Only"),
    LETTER_ONLY("Letter Only")
}

enum class BreathingProtocol(
    val title: String,
    val ratioLabel: String,
    val description: String
) {
    BOX_4_4_4_4(
        title = "Box Breathing",
        ratioLabel = "4-4-4-4",
        description = "Balanced cadence for steady pre-mock focus and composure."
    ),
    CYCLIC_SIGH(
        title = "Physiological Cyclic Sigh",
        ratioLabel = "2.5-1.5-6",
        description = "Double nasal inhale + extended exhale for rapid autonomic downshift."
    ),
    CALM_4_7_8(
        title = "4-7-8 Calm",
        ratioLabel = "4-7-8",
        description = "Long breath hold and exhale for post-study recovery and sleep wind-down."
    )
}

data class BrainLabSettings(
    val audioEnabled: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val countCalmInDailyStudyTime: Boolean = false,
    val preferredBudgetMinutes: Int = 3
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("audioEnabled", audioEnabled)
        put("hapticsEnabled", hapticsEnabled)
        put("countCalmInDailyStudyTime", countCalmInDailyStudyTime)
        put("preferredBudgetMinutes", preferredBudgetMinutes.coerceIn(1, 10))
    }

    companion object {
        fun fromJson(obj: JSONObject?): BrainLabSettings {
            if (obj == null) return BrainLabSettings()
            return BrainLabSettings(
                audioEnabled = obj.optBoolean("audioEnabled", false),
                hapticsEnabled = obj.optBoolean("hapticsEnabled", true),
                countCalmInDailyStudyTime = obj.optBoolean("countCalmInDailyStudyTime", false),
                preferredBudgetMinutes = obj.optInt("preferredBudgetMinutes", 3).coerceIn(1, 10)
            )
        }
    }
}

data class BrainLabStats(
    val bestNBackTier: Int = 1,
    val bestNBackAccuracy: Int = 0,
    val bestCorsiForwardSpan: Int = 0,
    val bestCorsiReverseSpan: Int = 0,
    val bestStroopAccuracy: Int = 0,
    val bestStroopReactionMs: Long = 0L,
    val syllogismSolved: Int = 0,
    val syllogismCorrect: Int = 0,
    val reasoningStreak: Int = 0,
    val bestReasoningStreak: Int = 0,
    val constraintSolved: Int = 0,
    val argumentSolved: Int = 0,
    val dataSufficiencySolved: Int = 0,
    val bestSchulte5x5Ms: Long = 0L,
    val bestSchulte6x6Ms: Long = 0L,
    val bestTrail5x5Ms: Long = 0L,
    val bestQuantBlitzScore: Int = 0,
    val calmSessionsCompleted: Int = 0,
    val zenGridsCompleted: Int = 0,
    val totalCognitiveSeconds: Long = 0L,
    val totalCalmSeconds: Long = 0L,
    val lastUpdatedAtMillis: Long = 0L
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("bestNBackTier", bestNBackTier)
        put("bestNBackAccuracy", bestNBackAccuracy)
        put("bestCorsiForwardSpan", bestCorsiForwardSpan)
        put("bestCorsiReverseSpan", bestCorsiReverseSpan)
        put("bestStroopAccuracy", bestStroopAccuracy)
        put("bestStroopReactionMs", bestStroopReactionMs)
        put("syllogismSolved", syllogismSolved)
        put("syllogismCorrect", syllogismCorrect)
        put("reasoningStreak", reasoningStreak)
        put("bestReasoningStreak", bestReasoningStreak)
        put("constraintSolved", constraintSolved)
        put("argumentSolved", argumentSolved)
        put("dataSufficiencySolved", dataSufficiencySolved)
        put("bestSchulte5x5Ms", bestSchulte5x5Ms)
        put("bestSchulte6x6Ms", bestSchulte6x6Ms)
        put("bestTrail5x5Ms", bestTrail5x5Ms)
        put("bestQuantBlitzScore", bestQuantBlitzScore)
        put("calmSessionsCompleted", calmSessionsCompleted)
        put("zenGridsCompleted", zenGridsCompleted)
        put("totalCognitiveSeconds", totalCognitiveSeconds)
        put("totalCalmSeconds", totalCalmSeconds)
        put("lastUpdatedAtMillis", lastUpdatedAtMillis)
    }

    companion object {
        fun fromJson(obj: JSONObject?): BrainLabStats {
            if (obj == null) return BrainLabStats()
            return BrainLabStats(
                bestNBackTier = obj.optInt("bestNBackTier", 1).coerceIn(1, 4),
                bestNBackAccuracy = obj.optInt("bestNBackAccuracy", 0).coerceIn(0, 100),
                bestCorsiForwardSpan = obj.optInt("bestCorsiForwardSpan", 0).coerceAtLeast(0),
                bestCorsiReverseSpan = obj.optInt("bestCorsiReverseSpan", 0).coerceAtLeast(0),
                bestStroopAccuracy = obj.optInt("bestStroopAccuracy", 0).coerceIn(0, 100),
                bestStroopReactionMs = obj.optLong("bestStroopReactionMs", 0L).coerceAtLeast(0L),
                syllogismSolved = obj.optInt("syllogismSolved", 0).coerceAtLeast(0),
                syllogismCorrect = obj.optInt("syllogismCorrect", 0).coerceAtLeast(0),
                reasoningStreak = obj.optInt("reasoningStreak", 0).coerceAtLeast(0),
                bestReasoningStreak = obj.optInt("bestReasoningStreak", 0).coerceAtLeast(0),
                constraintSolved = obj.optInt("constraintSolved", 0).coerceAtLeast(0),
                argumentSolved = obj.optInt("argumentSolved", 0).coerceAtLeast(0),
                dataSufficiencySolved = obj.optInt("dataSufficiencySolved", 0).coerceAtLeast(0),
                bestSchulte5x5Ms = obj.optLong("bestSchulte5x5Ms", 0L).coerceAtLeast(0L),
                bestSchulte6x6Ms = obj.optLong("bestSchulte6x6Ms", 0L).coerceAtLeast(0L),
                bestTrail5x5Ms = obj.optLong("bestTrail5x5Ms", 0L).coerceAtLeast(0L),
                bestQuantBlitzScore = obj.optInt("bestQuantBlitzScore", 0).coerceAtLeast(0),
                calmSessionsCompleted = obj.optInt("calmSessionsCompleted", 0).coerceAtLeast(0),
                zenGridsCompleted = obj.optInt("zenGridsCompleted", 0).coerceAtLeast(0),
                totalCognitiveSeconds = obj.optLong("totalCognitiveSeconds", 0L).coerceAtLeast(0L),
                totalCalmSeconds = obj.optLong("totalCalmSeconds", 0L).coerceAtLeast(0L),
                lastUpdatedAtMillis = obj.optLong("lastUpdatedAtMillis", 0L).coerceAtLeast(0L)
            )
        }

        private fun minPositive(a: Long, b: Long): Long = when {
            a <= 0L -> b
            b <= 0L -> a
            else -> minOf(a, b)
        }

        fun mergeForRestore(existing: BrainLabStats, incoming: BrainLabStats): BrainLabStats {
            return BrainLabStats(
                bestNBackTier = maxOf(existing.bestNBackTier, incoming.bestNBackTier),
                bestNBackAccuracy = maxOf(existing.bestNBackAccuracy, incoming.bestNBackAccuracy),
                bestCorsiForwardSpan = maxOf(existing.bestCorsiForwardSpan, incoming.bestCorsiForwardSpan),
                bestCorsiReverseSpan = maxOf(existing.bestCorsiReverseSpan, incoming.bestCorsiReverseSpan),
                bestStroopAccuracy = maxOf(existing.bestStroopAccuracy, incoming.bestStroopAccuracy),
                bestStroopReactionMs = minPositive(existing.bestStroopReactionMs, incoming.bestStroopReactionMs),
                syllogismSolved = maxOf(existing.syllogismSolved, incoming.syllogismSolved),
                syllogismCorrect = maxOf(existing.syllogismCorrect, incoming.syllogismCorrect),
                reasoningStreak = if (incoming.lastUpdatedAtMillis > existing.lastUpdatedAtMillis) incoming.reasoningStreak else existing.reasoningStreak,
                bestReasoningStreak = maxOf(existing.bestReasoningStreak, incoming.bestReasoningStreak),
                constraintSolved = maxOf(existing.constraintSolved, incoming.constraintSolved),
                argumentSolved = maxOf(existing.argumentSolved, incoming.argumentSolved),
                dataSufficiencySolved = maxOf(existing.dataSufficiencySolved, incoming.dataSufficiencySolved),
                bestSchulte5x5Ms = minPositive(existing.bestSchulte5x5Ms, incoming.bestSchulte5x5Ms),
                bestSchulte6x6Ms = minPositive(existing.bestSchulte6x6Ms, incoming.bestSchulte6x6Ms),
                bestTrail5x5Ms = minPositive(existing.bestTrail5x5Ms, incoming.bestTrail5x5Ms),
                bestQuantBlitzScore = maxOf(existing.bestQuantBlitzScore, incoming.bestQuantBlitzScore),
                calmSessionsCompleted = maxOf(existing.calmSessionsCompleted, incoming.calmSessionsCompleted),
                zenGridsCompleted = maxOf(existing.zenGridsCompleted, incoming.zenGridsCompleted),
                totalCognitiveSeconds = maxOf(existing.totalCognitiveSeconds, incoming.totalCognitiveSeconds),
                totalCalmSeconds = maxOf(existing.totalCalmSeconds, incoming.totalCalmSeconds),
                lastUpdatedAtMillis = maxOf(existing.lastUpdatedAtMillis, incoming.lastUpdatedAtMillis)
            )
        }
    }
}
