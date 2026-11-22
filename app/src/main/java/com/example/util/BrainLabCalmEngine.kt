package com.example.util

import com.example.model.BreathingProtocol
import kotlin.random.Random

object BrainLabCalmEngine {

    // =========================================================================
    // 1. Guided Breathing Pacers (60fps phase curve + haptic transition trigger)
    // =========================================================================

    enum class BreathCue(val label: String, val subCue: String) {
        INHALE("Inhale", "Draw air in smoothly through the nose"),
        TOP_UP("Top-Up Inhale", "Take one short extra sip of air"),
        HOLD("Hold", "Keep shoulders relaxed and jaw soft"),
        EXHALE("Exhale", "Release breath slowly and steadily"),
        REST("Hold Empty", "Rest softly before the next cycle")
    }

    data class BreathingPhaseSample(
        val cue: BreathCue,
        val phaseIndex: Int,
        val secondsRemainingInPhase: Int,
        val ringScale: Float,
        val cycleCount: Int
    )

    fun sampleBreathingState(
        protocol: BreathingProtocol,
        elapsedMillis: Long
    ): BreathingPhaseSample {
        val safeMs = elapsedMillis.coerceAtLeast(0L)
        return when (protocol) {
            BreathingProtocol.BOX_4_4_4_4 -> {
                val cycleMs = 16_000L
                val cycle = (safeMs / cycleMs).toInt()
                val inCycle = safeMs % cycleMs
                val phase = (inCycle / 4_000L).toInt()
                val phaseProgress = (inCycle % 4_000L) / 4_000f
                val remSec = ((4_000L - (inCycle % 4_000L) + 999L) / 1000L).toInt().coerceIn(1, 4)
                val (cue, scale) = when (phase) {
                    0 -> BreathCue.INHALE to (0.42f + 0.50f * easeInOut(phaseProgress))
                    1 -> BreathCue.HOLD to 0.92f
                    2 -> BreathCue.EXHALE to (0.92f - 0.50f * easeInOut(phaseProgress))
                    else -> BreathCue.REST to 0.42f
                }
                BreathingPhaseSample(cue, phase, remSec, scale, cycle)
            }
            BreathingProtocol.CYCLIC_SIGH -> {
                // 2.5s Inhale + 1.5s Top-Up + 6.0s Exhale = 10s cycle
                val cycleMs = 10_000L
                val cycle = (safeMs / cycleMs).toInt()
                val inCycle = safeMs % cycleMs
                when {
                    inCycle < 2_500L -> {
                        val p = inCycle / 2_500f
                        val rem = ((2_500L - inCycle + 999L) / 1000L).toInt().coerceIn(1, 3)
                        BreathingPhaseSample(BreathCue.INHALE, 0, rem, 0.40f + 0.36f * easeInOut(p), cycle)
                    }
                    inCycle < 4_000L -> {
                        val p = (inCycle - 2_500L) / 1_500f
                        val rem = ((4_000L - inCycle + 999L) / 1000L).toInt().coerceIn(1, 2)
                        BreathingPhaseSample(BreathCue.TOP_UP, 1, rem, 0.76f + 0.18f * easeInOut(p), cycle)
                    }
                    else -> {
                        val p = (inCycle - 4_000L) / 6_000f
                        val rem = ((10_000L - inCycle + 999L) / 1000L).toInt().coerceIn(1, 6)
                        BreathingPhaseSample(BreathCue.EXHALE, 2, rem, 0.94f - 0.54f * easeInOut(p), cycle)
                    }
                }
            }
            BreathingProtocol.CALM_4_7_8 -> {
                // 4s Inhale + 7s Hold + 8s Exhale = 19s cycle
                val cycleMs = 19_000L
                val cycle = (safeMs / cycleMs).toInt()
                val inCycle = safeMs % cycleMs
                when {
                    inCycle < 4_000L -> {
                        val p = inCycle / 4_000f
                        val rem = ((4_000L - inCycle + 999L) / 1000L).toInt().coerceIn(1, 4)
                        BreathingPhaseSample(BreathCue.INHALE, 0, rem, 0.42f + 0.50f * easeInOut(p), cycle)
                    }
                    inCycle < 11_000L -> {
                        val rem = ((11_000L - inCycle + 999L) / 1000L).toInt().coerceIn(1, 7)
                        BreathingPhaseSample(BreathCue.HOLD, 1, rem, 0.92f, cycle)
                    }
                    else -> {
                        val p = (inCycle - 11_000L) / 8_000f
                        val rem = ((19_000L - inCycle + 999L) / 1000L).toInt().coerceIn(1, 8)
                        BreathingPhaseSample(BreathCue.EXHALE, 2, rem, 0.92f - 0.50f * easeInOut(p), cycle)
                    }
                }
            }
        }
    }

    private fun easeInOut(t: Float): Float {
        val c = t.coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }

    // =========================================================================
    // 2. 60-Second Visual Soft-Reset (20-20-20 Eye & Focus Break)
    // =========================================================================

    data class SoftResetStep(
        val title: String,
        val prompt: String,
        val durationSeconds: Int
    )

    val SOFT_RESET_STEPS = listOf(
        SoftResetStep(
            title = "20-20-20 Far Gaze",
            prompt = "Look away from your screen toward an object at least 20 feet (6 meters) away. Let your eyes unfocus gently.",
            durationSeconds = 20
        ),
        SoftResetStep(
            title = "Shoulder & Jaw Release",
            prompt = "Drop your shoulders away from your ears, unclench your back teeth, and let your hands rest open.",
            durationSeconds = 20
        ),
        SoftResetStep(
            title = "Slow Blink & Reset",
            prompt = "Blink slowly 4–5 times to rehydrate your eyes, take one quiet breath, and return with clear attention.",
            durationSeconds = 20
        )
    )

    // =========================================================================
    // 3. Untimed Zen Logic Grid (4x4 & 6x6 Unique-Solution Mini-Sudoku)
    // =========================================================================

    data class ZenLogicPuzzle(
        val size: Int, // 4 or 6
        val boxRows: Int, // 2
        val boxCols: Int, // 2 or 3
        val givens: IntArray, // 0 = blank, 1..size = fixed clue
        val solution: IntArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ZenLogicPuzzle) return false
            return size == other.size &&
                givens.contentEquals(other.givens) &&
                solution.contentEquals(other.solution)
        }

        override fun hashCode(): Int {
            var result = size
            result = 31 * result + givens.contentHashCode()
            result = 31 * result + solution.contentHashCode()
            return result
        }
    }

    fun generateZenLogicPuzzle(
        size: Int = 4,
        random: Random = Random.Default
    ): ZenLogicPuzzle {
        val safeSize = if (size == 6) 6 else 4
        val boxRows = 2
        val boxCols = if (safeSize == 6) 3 else 2
        val solution = generateSolvedGrid(safeSize, boxRows, boxCols, random)

        // Start from all cells filled and remove clues only while solution remains strictly unique.
        val puzzle = solution.copyOf()
        val targetBlanks = if (safeSize == 4) 9 else 18
        val indices = (0 until safeSize * safeSize).shuffled(random)
        var removed = 0

        for (idx in indices) {
            if (removed >= targetBlanks) break
            val backup = puzzle[idx]
            puzzle[idx] = 0
            if (countSolutions(puzzle, safeSize, boxRows, boxCols, limit = 2) == 1) {
                removed++
            } else {
                puzzle[idx] = backup
            }
        }

        return ZenLogicPuzzle(
            size = safeSize,
            boxRows = boxRows,
            boxCols = boxCols,
            givens = puzzle,
            solution = solution
        )
    }

    private fun generateSolvedGrid(
        size: Int,
        boxRows: Int,
        boxCols: Int,
        random: Random
    ): IntArray {
        val base = IntArray(size * size)
        for (r in 0 until size) {
            for (c in 0 until size) {
                base[r * size + c] = ((r * boxCols + r / boxRows + c) % size) + 1
            }
        }

        // Permute symbols 1..size
        val symbols = (1..size).shuffled(random)
        for (i in base.indices) {
            base[i] = symbols[base[i] - 1]
        }

        // Permute rows within each band and bands themselves
        val numBands = size / boxRows
        val bandOrder = (0 until numBands).shuffled(random)
        val rowMap = bandOrder.flatMap { band ->
            val rowsInBand = (0 until boxRows).map { band * boxRows + it }.shuffled(random)
            rowsInBand
        }

        // Permute columns within each stack and stacks themselves
        val numStacks = size / boxCols
        val stackOrder = (0 until numStacks).shuffled(random)
        val colMap = stackOrder.flatMap { stack ->
            val colsInStack = (0 until boxCols).map { stack * boxCols + it }.shuffled(random)
            colsInStack
        }

        val permuted = IntArray(size * size)
        for (r in 0 until size) {
            for (c in 0 until size) {
                permuted[r * size + c] = base[rowMap[r] * size + colMap[c]]
            }
        }
        return permuted
    }

    fun countSolutions(
        grid: IntArray,
        size: Int,
        boxRows: Int,
        boxCols: Int,
        limit: Int = 2
    ): Int {
        val work = grid.copyOf()
        var solutions = 0

        fun findNextEmpty(): Pair<Int, Int>? {
            var bestIdx = -1
            var bestMask = 0
            var bestCount = Int.MAX_VALUE

            for (idx in work.indices) {
                if (work[idx] != 0) continue
                val r = idx / size
                val c = idx % size
                val br = (r / boxRows) * boxRows
                val bc = (c / boxCols) * boxCols

                var usedMask = 0
                for (k in 0 until size) {
                    val rv = work[r * size + k]
                    if (rv != 0) usedMask = usedMask or (1 shl rv)
                    val cv = work[k * size + c]
                    if (cv != 0) usedMask = usedMask or (1 shl cv)
                }
                for (dr in 0 until boxRows) {
                    for (dc in 0 until boxCols) {
                        val bv = work[(br + dr) * size + (bc + dc)]
                        if (bv != 0) usedMask = usedMask or (1 shl bv)
                    }
                }

                var candMask = 0
                var candCount = 0
                for (v in 1..size) {
                    if (((usedMask shr v) and 1) == 0) {
                        candMask = candMask or (1 shl v)
                        candCount++
                    }
                }
                if (candCount == 0) return idx to 0
                if (candCount < bestCount) {
                    bestCount = candCount
                    bestIdx = idx
                    bestMask = candMask
                    if (candCount == 1) break
                }
            }
            return if (bestIdx == -1) null else bestIdx to bestMask
        }

        fun dfs() {
            if (solutions >= limit) return
            val next = findNextEmpty()
            if (next == null) {
                solutions++
                return
            }
            val (idx, candMask) = next
            if (candMask == 0) return

            for (v in 1..size) {
                if (((candMask shr v) and 1) != 0) {
                    work[idx] = v
                    dfs()
                    work[idx] = 0
                    if (solutions >= limit) return
                }
            }
        }

        dfs()
        return solutions
    }

    fun isZenGridSolvedCorrectly(
        board: IntArray,
        size: Int,
        boxRows: Int,
        boxCols: Int
    ): Boolean {
        if (board.size != size * size || board.any { it !in 1..size }) return false
        val fullMask = ((1 shl (size + 1)) - 2)
        for (i in 0 until size) {
            var rowMask = 0
            var colMask = 0
            for (j in 0 until size) {
                rowMask = rowMask or (1 shl board[i * size + j])
                colMask = colMask or (1 shl board[j * size + i])
            }
            if (rowMask != fullMask || colMask != fullMask) return false
        }
        for (br in 0 until size step boxRows) {
            for (bc in 0 until size step boxCols) {
                var boxMask = 0
                for (dr in 0 until boxRows) {
                    for (dc in 0 until boxCols) {
                        boxMask = boxMask or (1 shl board[(br + dr) * size + (bc + dc)])
                    }
                }
                if (boxMask != fullMask) return false
            }
        }
        return true
    }
}
