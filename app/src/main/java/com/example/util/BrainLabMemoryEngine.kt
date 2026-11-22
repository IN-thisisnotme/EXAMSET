package com.example.util

import com.example.model.NBackMode
import kotlin.random.Random

object BrainLabMemoryEngine {

    val N_BACK_LETTERS = listOf('A', 'C', 'H', 'K', 'L', 'Q', 'R', 'T')

    data class NBackTrial(
        val stepIndex: Int,
        val cellIndex: Int,
        val letter: Char,
        val isPositionMatch: Boolean,
        val isLetterMatch: Boolean
    )

    data class NBackEvaluation(
        val totalEvaluatedTrials: Int,
        val hits: Int,
        val misses: Int,
        val falseAlarms: Int,
        val accuracyPercent: Int,
        val nextRecommendedN: Int,
        val tierDeltaLabel: String
    )

    fun generateNBackSequence(
        n: Int,
        totalTrials: Int = 16 + n.coerceIn(1, 4),
        mode: NBackMode = NBackMode.DUAL,
        random: Random = Random.Default
    ): List<NBackTrial> {
        val safeN = n.coerceIn(1, 4)
        val count = totalTrials.coerceAtLeast(safeN + 6)
        val positions = IntArray(count)
        val letters = CharArray(count)

        for (i in 0 until count) {
            if (i < safeN) {
                positions[i] = random.nextInt(9)
                letters[i] = N_BACK_LETTERS[random.nextInt(N_BACK_LETTERS.size)]
            } else {
                val forcePosMatch = mode != NBackMode.LETTER_ONLY && random.nextFloat() < 0.30f
                val forceLetterMatch = mode != NBackMode.POSITION_ONLY && random.nextFloat() < 0.30f

                positions[i] = if (forcePosMatch) {
                    positions[i - safeN]
                } else {
                    var candidate = random.nextInt(9)
                    if (candidate == positions[i - safeN]) {
                        candidate = (candidate + 1 + random.nextInt(8)) % 9
                    }
                    candidate
                }

                letters[i] = if (forceLetterMatch) {
                    letters[i - safeN]
                } else {
                    var candidate = N_BACK_LETTERS[random.nextInt(N_BACK_LETTERS.size)]
                    if (candidate == letters[i - safeN]) {
                        val altIdx = (N_BACK_LETTERS.indexOf(candidate) + 1 + random.nextInt(N_BACK_LETTERS.size - 1)) % N_BACK_LETTERS.size
                        candidate = N_BACK_LETTERS[altIdx]
                    }
                    candidate
                }
            }
        }

        return (0 until count).map { idx ->
            val posMatch = idx >= safeN && positions[idx] == positions[idx - safeN] && mode != NBackMode.LETTER_ONLY
            val letMatch = idx >= safeN && letters[idx] == letters[idx - safeN] && mode != NBackMode.POSITION_ONLY
            NBackTrial(
                stepIndex = idx,
                cellIndex = positions[idx],
                letter = letters[idx],
                isPositionMatch = posMatch,
                isLetterMatch = letMatch
            )
        }
    }

    fun evaluateNBackSession(
        currentN: Int,
        mode: NBackMode,
        trials: List<NBackTrial>,
        userPosTaps: Set<Int>,
        userLetterTaps: Set<Int>
    ): NBackEvaluation {
        val safeN = currentN.coerceIn(1, 4)
        var hits = 0
        var misses = 0
        var falseAlarms = 0
        var correctRejections = 0

        val activeChannels = when (mode) {
            NBackMode.DUAL -> 2
            NBackMode.POSITION_ONLY, NBackMode.LETTER_ONLY -> 1
        }
        val scoredTrials = trials.filter { it.stepIndex >= safeN }
        scoredTrials.forEach { trial ->
            if (mode != NBackMode.LETTER_ONLY) {
                val tapped = trial.stepIndex in userPosTaps
                when {
                    trial.isPositionMatch && tapped -> hits++
                    trial.isPositionMatch && !tapped -> misses++
                    !trial.isPositionMatch && tapped -> falseAlarms++
                    else -> correctRejections++
                }
            }
            if (mode != NBackMode.POSITION_ONLY) {
                val tapped = trial.stepIndex in userLetterTaps
                when {
                    trial.isLetterMatch && tapped -> hits++
                    trial.isLetterMatch && !tapped -> misses++
                    !trial.isLetterMatch && tapped -> falseAlarms++
                    else -> correctRejections++
                }
            }
        }

        val totalSignals = scoredTrials.size * activeChannels
        val accuracy = if (totalSignals > 0) {
            (((hits + correctRejections).toFloat() / totalSignals) * 100f).toInt().coerceIn(0, 100)
        } else 0

        val nextN = when {
            accuracy >= 85 -> (safeN + 1).coerceAtMost(4)
            accuracy < 55 -> (safeN - 1).coerceAtLeast(1)
            else -> safeN
        }
        val deltaLabel = when {
            nextN > safeN -> "Promoted to $nextN-Back"
            nextN < safeN -> "Stepped down to $nextN-Back"
            else -> "Holding at $nextN-Back"
        }

        return NBackEvaluation(
            totalEvaluatedTrials = scoredTrials.size,
            hits = hits,
            misses = misses,
            falseAlarms = falseAlarms,
            accuracyPercent = accuracy,
            nextRecommendedN = nextN,
            tierDeltaLabel = deltaLabel
        )
    }

    // --- Corsi Spatial Matrix & Reverse Sequence Span ---

    data class CorsiPuzzle(
        val gridSize: Int,
        val span: Int,
        val reverseMode: Boolean,
        val sequence: List<Int>,
        val expectedInput: List<Int>,
        val alphanumericLabels: List<String> = emptyList()
    )

    private val ALPHANUMERIC_POOL = listOf(
        "A1", "B4", "C7", "D2", "E9", "F5", "G3", "H8",
        "J6", "K2", "L7", "M3", "N5", "P8", "R4", "T1",
        "V9", "W6", "X3", "Z7", "Q2", "S5", "K8", "R7", "M9"
    )

    fun generateCorsiPuzzle(
        gridSize: Int = 4,
        span: Int = 4,
        reverseMode: Boolean = true,
        withAlphanumericLabels: Boolean = false,
        random: Random = Random.Default
    ): CorsiPuzzle {
        val safeGrid = gridSize.coerceIn(4, 5)
        val totalCells = safeGrid * safeGrid
        val safeSpan = span.coerceIn(4, 8).coerceAtMost(totalCells)
        val sequence = (0 until totalCells).shuffled(random).take(safeSpan)
        val expected = if (reverseMode) sequence.reversed() else sequence
        val labels = if (withAlphanumericLabels) {
            ALPHANUMERIC_POOL.shuffled(random).take(totalCells)
        } else emptyList()

        return CorsiPuzzle(
            gridSize = safeGrid,
            span = safeSpan,
            reverseMode = reverseMode,
            sequence = sequence,
            expectedInput = expected,
            alphanumericLabels = labels
        )
    }

    // --- Stroop Impulse & Rule-Switch Arena ---

    enum class StroopColor(val label: String) {
        RED("RED"),
        BLUE("BLUE"),
        GREEN("GREEN"),
        AMBER("AMBER")
    }

    enum class StroopDirection(val label: String, val symbol: String) {
        UP("UP", "↑"),
        RIGHT("RIGHT", "→"),
        DOWN("DOWN", "↓"),
        LEFT("LEFT", "←")
    }

    enum class StroopRule(val badge: String, val instruction: String) {
        TAP_INK_COLOR("TAP INK COLOR", "Tap the INK color, ignore the word"),
        TAP_WORD_MEANING("TAP WORD MEANING", "Tap the WORD meaning, ignore ink color"),
        TAP_ARROW_DIR("TAP ARROW DIRECTION", "Tap the direction the arrow POINTS"),
        TAP_OPPOSITE_DIR("TAP OPPOSITE DIRECTION", "Tap the OPPOSITE direction to the arrow")
    }

    data class StroopTrial(
        val id: Int,
        val isArrowMode: Boolean,
        val rule: StroopRule,
        val displayWord: StroopColor = StroopColor.RED,
        val inkColor: StroopColor = StroopColor.BLUE,
        val arrowDirection: StroopDirection = StroopDirection.UP,
        val expectedChoiceIndex: Int,
        val choices: List<String>
    )

    fun generateStroopTrial(
        stepIndex: Int,
        allowArrowMode: Boolean = true,
        previousRule: StroopRule? = null,
        random: Random = Random.Default
    ): StroopTrial {
        val isArrow = allowArrowMode && random.nextFloat() < 0.35f
        if (!isArrow) {
            val word = StroopColor.entries[random.nextInt(StroopColor.entries.size)]
            val ink = if (random.nextFloat() < 0.80f) {
                val others = StroopColor.entries.filter { it != word }
                others[random.nextInt(others.size)]
            } else {
                word
            }
            val rule = when {
                previousRule == null -> if (random.nextBoolean()) StroopRule.TAP_INK_COLOR else StroopRule.TAP_WORD_MEANING
                random.nextFloat() < 0.42f -> if (previousRule == StroopRule.TAP_INK_COLOR) StroopRule.TAP_WORD_MEANING else StroopRule.TAP_INK_COLOR
                previousRule == StroopRule.TAP_INK_COLOR || previousRule == StroopRule.TAP_WORD_MEANING -> previousRule
                else -> StroopRule.TAP_INK_COLOR
            }
            val expected = if (rule == StroopRule.TAP_INK_COLOR) ink.ordinal else word.ordinal
            return StroopTrial(
                id = stepIndex,
                isArrowMode = false,
                rule = rule,
                displayWord = word,
                inkColor = ink,
                expectedChoiceIndex = expected,
                choices = StroopColor.entries.map { it.label }
            )
        } else {
            val dir = StroopDirection.entries[random.nextInt(StroopDirection.entries.size)]
            val rule = when {
                previousRule == null -> if (random.nextBoolean()) StroopRule.TAP_ARROW_DIR else StroopRule.TAP_OPPOSITE_DIR
                random.nextFloat() < 0.45f -> if (previousRule == StroopRule.TAP_ARROW_DIR) StroopRule.TAP_OPPOSITE_DIR else StroopRule.TAP_ARROW_DIR
                previousRule == StroopRule.TAP_ARROW_DIR || previousRule == StroopRule.TAP_OPPOSITE_DIR -> previousRule
                else -> StroopRule.TAP_OPPOSITE_DIR
            }
            val expected = if (rule == StroopRule.TAP_ARROW_DIR) {
                dir.ordinal
            } else {
                (dir.ordinal + 2) % 4
            }
            return StroopTrial(
                id = stepIndex,
                isArrowMode = true,
                rule = rule,
                arrowDirection = dir,
                expectedChoiceIndex = expected,
                choices = StroopDirection.entries.map { "${it.symbol} ${it.label}" }
            )
        }
    }
}
