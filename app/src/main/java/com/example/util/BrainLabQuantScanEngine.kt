package com.example.util

import kotlin.math.roundToInt
import kotlin.random.Random

object BrainLabQuantScanEngine {

    // =========================================================================
    // 1. Schulte Table & Alternating Trail Grid (5x5 / 6x6)
    // =========================================================================

    enum class SchulteMode(val label: String, val gridSize: Int) {
        SCHULTE_5X5("5×5 Classic (1 → 25)", 5),
        SCHULTE_6X6("6×6 Wide (1 → 36)", 6),
        TRAIL_5X5("5×5 Alternating Trail (1 → A → 2 → B…)", 5)
    }

    data class SchulteBoard(
        val mode: SchulteMode,
        val gridSize: Int,
        val cells: List<String>,
        val expectedOrder: List<String>
    )

    fun generateSchulteBoard(
        mode: SchulteMode,
        random: Random = Random.Default
    ): SchulteBoard {
        val total = mode.gridSize * mode.gridSize
        val ordered = if (mode == SchulteMode.TRAIL_5X5) {
            buildList {
                var num = 1
                var ch = 'A'
                while (size < total) {
                    add(num.toString())
                    num++
                    if (size < total) {
                        add(ch.toString())
                        ch++
                    }
                }
            }
        } else {
            (1..total).map { it.toString() }
        }
        return SchulteBoard(
            mode = mode,
            gridSize = mode.gridSize,
            cells = ordered.shuffled(random),
            expectedOrder = ordered
        )
    }

    // =========================================================================
    // 2. Mental Quant, Fraction-% & Approximation Blitz
    // =========================================================================

    enum class QuantQuestionType(val badge: String) {
        FRACTION_PERCENT("FRACTION → %"),
        COMPOUND_SHIFT("COMPOUND % SHIFT"),
        FRACTION_COMPARE("RATIO DUEL"),
        FAST_APPROX("FAST APPROX")
    }

    data class QuantQuestion(
        val id: Int,
        val type: QuantQuestionType,
        val prompt: String,
        val options: List<String>,
        val correctIndex: Int,
        val explanation: String
    )

    private data class BenchmarkFraction(val num: Int, val den: Int, val pctText: String, val pctVal: Double)

    private val BENCHMARKS = listOf(
        BenchmarkFraction(1, 6, "16.67%", 16.67),
        BenchmarkFraction(1, 7, "14.28%", 14.28),
        BenchmarkFraction(2, 7, "28.57%", 28.57),
        BenchmarkFraction(3, 7, "42.86%", 42.86),
        BenchmarkFraction(1, 8, "12.50%", 12.50),
        BenchmarkFraction(3, 8, "37.50%", 37.50),
        BenchmarkFraction(5, 8, "62.50%", 62.50),
        BenchmarkFraction(7, 8, "87.50%", 87.50),
        BenchmarkFraction(4, 9, "44.44%", 44.44),
        BenchmarkFraction(5, 9, "55.56%", 55.56),
        BenchmarkFraction(2, 11, "18.18%", 18.18),
        BenchmarkFraction(5, 12, "41.67%", 41.67),
        BenchmarkFraction(7, 12, "58.33%", 58.33),
        BenchmarkFraction(1, 15, "6.67%", 6.67)
    )

    fun generateQuantQuestion(
        id: Int,
        random: Random = Random.Default
    ): QuantQuestion {
        val kind = QuantQuestionType.entries[random.nextInt(QuantQuestionType.entries.size)]
        return when (kind) {
            QuantQuestionType.FRACTION_PERCENT -> buildFractionPercentQuestion(id, random)
            QuantQuestionType.COMPOUND_SHIFT -> buildCompoundShiftQuestion(id, random)
            QuantQuestionType.FRACTION_COMPARE -> buildFractionCompareQuestion(id, random)
            QuantQuestionType.FAST_APPROX -> buildFastApproxQuestion(id, random)
        }
    }

    private fun buildFractionPercentQuestion(id: Int, random: Random): QuantQuestion {
        val target = BENCHMARKS[random.nextInt(BENCHMARKS.size)]
        val distractors = BENCHMARKS
            .filter { it.pctText != target.pctText }
            .shuffled(random)
            .take(3)
            .map { it.pctText }
        val options = (listOf(target.pctText) + distractors).shuffled(random)
        val correctIdx = options.indexOf(target.pctText)
        return QuantQuestion(
            id = id,
            type = QuantQuestionType.FRACTION_PERCENT,
            prompt = "Closest percentage equivalent of  ${target.num} / ${target.den} ?",
            options = options,
            correctIndex = correctIdx,
            explanation = "1/${target.den} ≈ ${String.format("%.2f", 100.0 / target.den)}%, so ${target.num}/${target.den} ≈ ${target.pctText}."
        )
    }

    private fun buildCompoundShiftQuestion(id: Int, random: Random): QuantQuestion {
        val shifts = listOf(-25, -20, -15, -10, 10, 15, 20, 25, 30)
        val a = shifts[random.nextInt(shifts.size)]
        val b = shifts[random.nextInt(shifts.size)]
        val net = a + b + (a * b) / 100.0

        fun formatPct(v: Double): String {
            val rounded = (v * 100.0).roundToInt() / 100.0
            val prefix = if (rounded > 0) "+" else ""
            return if (rounded % 1.0 == 0.0) {
                "$prefix${rounded.toInt()}%"
            } else {
                "$prefix${String.format("%.2f", rounded)}%"
            }
        }

        val correctStr = formatPct(net)
        val candidateDistractors = listOf(
            formatPct((a + b).toDouble()),
            formatPct(net + 4.0),
            formatPct(net - 4.0),
            formatPct(net + 2.5),
            formatPct(net - 2.0),
            formatPct(-net)
        ).distinct().filter { it != correctStr }.take(3)

        val options = (listOf(correctStr) + candidateDistractors).shuffled(random)
        val aLabel = if (a > 0) "+$a%" else "$a%"
        val bLabel = if (b > 0) "+$b%" else "$b%"
        return QuantQuestion(
            id = id,
            type = QuantQuestionType.COMPOUND_SHIFT,
            prompt = "A value changes by $aLabel, then by $bLabel. What is the net % change?",
            options = options,
            correctIndex = options.indexOf(correctStr),
            explanation = "Successive formula: a + b + (ab)/100 = $a + $b + (${a * b})/100 = $correctStr."
        )
    }

    private fun buildFractionCompareQuestion(id: Int, random: Random): QuantQuestion {
        var n1: Int
        var d1: Int
        var n2: Int
        var d2: Int
        while (true) {
            d1 = random.nextInt(13, 35)
            n1 = random.nextInt(7, d1 - 1)
            d2 = random.nextInt(13, 35)
            n2 = random.nextInt(7, d2 - 1)
            val lhs = n1 * d2
            val rhs = n2 * d1
            if (lhs != rhs && kotlin.math.abs((n1.toDouble() / d1) - (n2.toDouble() / d2)) in 0.012..0.12) {
                break
            }
        }
        val f1 = "$n1/$d1"
        val f2 = "$n2/$d2"
        val lhs = n1 * d2
        val rhs = n2 * d1
        val larger = if (lhs > rhs) f1 else f2
        val options = listOf(f1, f2, "Both are equal", "Cannot be compared")
        val correctIdx = options.indexOf(larger)
        return QuantQuestion(
            id = id,
            type = QuantQuestionType.FRACTION_COMPARE,
            prompt = "Which fraction is strictly LARGER:  $f1  or  $f2 ?",
            options = options,
            correctIndex = correctIdx,
            explanation = "Cross-multiply: $n1 × $d2 = $lhs vs $n2 × $d1 = $rhs. Since ${maxOf(lhs, rhs)} > ${minOf(lhs, rhs)}, $larger is larger."
        )
    }

    private fun buildFastApproxQuestion(id: Int, random: Random): QuantQuestion {
        val pctOptions = listOf(12, 15, 18, 22, 24, 28, 35, 45)
        val pct = pctOptions[random.nextInt(pctOptions.size)]
        val base = random.nextInt(12, 48) * 10 + random.nextInt(1, 9)
        val exact = (pct * base) / 100.0
        val rounded = exact.roundToInt()
        val correctStr = "~$rounded"
        val distractors = listOf(
            "~${(rounded * 0.78).roundToInt().coerceAtLeast(1)}",
            "~${(rounded * 1.24).roundToInt()}",
            "~${(rounded + 19)}"
        ).distinct().filter { it != correctStr }.take(3)
        val options = (listOf(correctStr) + distractors).shuffled(random)
        return QuantQuestion(
            id = id,
            type = QuantQuestionType.FAST_APPROX,
            prompt = "Closest integer to  $pct% of $base ?",
            options = options,
            correctIndex = options.indexOf(correctStr),
            explanation = "$pct% of $base = ${String.format("%.2f", exact)}, closest to $rounded."
        )
    }
}
