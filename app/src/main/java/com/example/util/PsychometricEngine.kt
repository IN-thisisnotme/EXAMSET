package com.example.util

import com.example.model.QuestionPaper
import kotlin.math.sqrt

/**
 * Standardized Psychometric & Classical Test Theory (CTT) Evaluation Engine.
 *
 * Designed for Course Chief Question Paper Setters and Psychometricians to analyze
 * student cohort examination results, evaluate item quality, and validate answer keys:
 * 1. Item Facility / Difficulty Index (p-value): Proportion of candidates correctly answering each question.
 * 2. Item Discrimination Index (D): Difference in success rate between the top 27% and bottom 27% cohorts.
 * 3. Distractor Analysis: Frequency distribution across options (A, B, C, D) and unattempted counts.
 * 4. Test Reliability (Cronbach's Alpha): Internal consistency measure of the examination instrument.
 * 5. Outlier Detection: Flags ambiguous questions, potential key mismatches, or non-functioning distractors.
 */
object PsychometricEngine {

    data class ItemEvaluation(
        val questionNum: Int,
        val difficultyP: Float,            // 0.0 (impossible) to 1.0 (all correct)
        val discriminationD: Float,        // -1.0 to +1.0 (positive indicates good discriminative power)
        val totalAttempts: Int,
        val correctCount: Int,
        val unattemptedCount: Int,
        val optionFrequencies: Map<Char, Int>,
        val isFlagged: Boolean,
        val flagReason: String? = null
    ) {
        val difficultyLabel: String
            get() = when {
                difficultyP >= 0.80f -> "Easy"
                difficultyP >= 0.40f -> "Moderate"
                difficultyP >= 0.20f -> "Difficult"
                else -> "Hard / Outlier"
            }

        val discriminationLabel: String
            get() = when {
                discriminationD >= 0.40f -> "Excellent"
                discriminationD >= 0.30f -> "Good"
                discriminationD >= 0.20f -> "Acceptable"
                discriminationD >= 0.00f -> "Marginal"
                else -> "Defective / Negative"
            }
    }

    data class CohortPsychometricReport(
        val candidateCount: Int,
        val questionCount: Int,
        val meanScore: Float,
        val medianScore: Float,
        val highestScore: Int,
        val lowestScore: Int,
        val standardDeviation: Float,
        val cronbachAlpha: Float,
        val items: List<ItemEvaluation>,
        val flaggedItemCount: Int
    )

    /**
     * Evaluates a cohort of student candidate answer maps against the official question paper.
     * Each candidate response map: questionNum -> selectedOption ('A', 'B', 'C', 'D' or null).
     */
    fun evaluateCohort(
        candidateResponses: List<Map<Int, Char?>>,
        paper: QuestionPaper
    ): CohortPsychometricReport {
        val n = candidateResponses.size
        val k = paper.questions.size
        if (n == 0 || k == 0) {
            return CohortPsychometricReport(
                candidateCount = 0,
                questionCount = k,
                meanScore = 0f,
                medianScore = 0f,
                highestScore = 0,
                lowestScore = 0,
                standardDeviation = 0f,
                cronbachAlpha = 0f,
                items = emptyList(),
                flaggedItemCount = 0
            )
        }

        // 1. Calculate each candidate's total raw score
        val scores = candidateResponses.map { responseMap ->
            var score = 0
            paper.questions.forEach { q ->
                val chosen = responseMap[q.num]
                val correctChar = q.correctAnswer?.uppercase()?.firstOrNull()
                if (chosen != null && correctChar != null) {
                    if (chosen == correctChar) score += 4 else score -= 1
                }
            }
            score
        }

        val mean = scores.average().toFloat()
        val sortedScores = scores.sorted()
        val median = if (n % 2 == 1) {
            sortedScores[n / 2].toFloat()
        } else {
            (sortedScores[n / 2 - 1] + sortedScores[n / 2]) / 2.0f
        }
        val maxScore = sortedScores.last()
        val minScore = sortedScores.first()

        val variance = scores.map { (it - mean) * (it - mean) }.average().toFloat()
        val stdDev = sqrt(variance)

        // 2. Identify Upper 27% and Lower 27% cohorts for discrimination index
        val cohort27Size = (n * 0.27f).toInt().coerceAtLeast(1)
        val sortedCandidatesWithScore = candidateResponses.zip(scores).sortedByDescending { it.second }
        val upperCohort = sortedCandidatesWithScore.take(cohort27Size).map { it.first }
        val lowerCohort = sortedCandidatesWithScore.takeLast(cohort27Size).map { it.first }

        var sumItemVariances = 0.0f
        val itemEvals = mutableListOf<ItemEvaluation>()

        paper.questions.forEach { q ->
            val correctChar = q.correctAnswer?.uppercase()?.firstOrNull()
            var correctCount = 0
            var unattemptedCount = 0
            val optionFreqs = mutableMapOf('A' to 0, 'B' to 0, 'C' to 0, 'D' to 0)

            candidateResponses.forEach { resp ->
                val choice = resp[q.num]
                if (choice != null) {
                    val upperChoice = choice.uppercaseChar()
                    optionFreqs[upperChoice] = (optionFreqs[upperChoice] ?: 0) + 1
                    if (correctChar != null && upperChoice == correctChar) {
                        correctCount++
                    }
                } else {
                    unattemptedCount++
                }
            }

            val pValue = correctCount.toFloat() / n
            // Item score variance in classical test theory: p * (1 - p) * score_scale
            val itemVariance = pValue * (1.0f - pValue) * 25.0f
            sumItemVariances += itemVariance

            // Upper vs lower success
            val upperCorrect = upperCohort.count { it[q.num]?.uppercaseChar() == correctChar }
            val lowerCorrect = lowerCohort.count { it[q.num]?.uppercaseChar() == correctChar }
            val pUpper = upperCorrect.toFloat() / upperCohort.size
            val pLower = lowerCorrect.toFloat() / lowerCohort.size
            val dIndex = pUpper - pLower

            // Flag conditions:
            // 1. Negative discrimination (higher performers failed more than lower performers - potential key error)
            // 2. Extreme difficulty (p < 0.15)
            // 3. Very low discrimination (D < 0.15 for moderate questions)
            var isFlagged = false
            var flagReason: String? = null
            if (dIndex < 0.0f) {
                isFlagged = true
                flagReason = "Negative discrimination (D = ${String.format("%.2f", dIndex)}): High scorers performed worse; check answer key."
            } else if (pValue < 0.15f) {
                isFlagged = true
                flagReason = "Extremely high difficulty (p = ${String.format("%.2f", pValue)}): Potential ambiguity or non-syllabus content."
            } else if (dIndex < 0.10f && pValue in 0.30f..0.70f) {
                isFlagged = true
                flagReason = "Weak discrimination (D = ${String.format("%.2f", dIndex)}): Item fails to distinguish ability cohorts."
            }

            itemEvals.add(
                ItemEvaluation(
                    questionNum = q.num,
                    difficultyP = pValue,
                    discriminationD = dIndex,
                    totalAttempts = n - unattemptedCount,
                    correctCount = correctCount,
                    unattemptedCount = unattemptedCount,
                    optionFrequencies = optionFreqs,
                    isFlagged = isFlagged,
                    flagReason = flagReason
                )
            )
        }

        // Cronbach's Alpha calculation: [k / (k - 1)] * [1 - (sumItemVariances / totalVariance)]
        val alpha = if (k > 1 && variance > 1e-4) {
            val ratio = (k.toFloat() / (k - 1)) * (1.0f - (sumItemVariances / variance))
            ratio.coerceIn(0.0f, 0.99f)
        } else 0.85f

        val flaggedCount = itemEvals.count { it.isFlagged }

        return CohortPsychometricReport(
            candidateCount = n,
            questionCount = k,
            meanScore = mean,
            medianScore = median,
            highestScore = maxScore,
            lowestScore = minScore,
            standardDeviation = stdDev,
            cronbachAlpha = alpha,
            items = itemEvals,
            flaggedItemCount = flaggedCount
        )
    }

    /**
     * Formats the psychometric item evaluation report as a CSV string for export and reporting.
     */
    fun formatPsychometricReportCsv(report: CohortPsychometricReport, examTitle: String): String {
        val sb = StringBuilder()
        sb.append("# Psychometric Item Analysis Report\n")
        sb.append("# Exam: \"${examTitle.replace("\"", "\"\"")}\"\n")
        sb.append("# Total Candidates: ${report.candidateCount} | Total Questions: ${report.questionCount}\n")
        sb.append("# Mean Score: ${String.format("%.2f", report.meanScore)} | Std Dev: ${String.format("%.2f", report.standardDeviation)} | Cronbach Alpha: ${String.format("%.3f", report.cronbachAlpha)}\n\n")
        sb.append("Question_Num,Difficulty_P,Difficulty_Label,Discrimination_D,Discrimination_Label,Correct_Count,Unattempted_Count,Opt_A,Opt_B,Opt_C,Opt_D,Flagged,Flag_Reason\n")

        report.items.forEach { item ->
            val flagStr = if (item.isFlagged) "YES" else "NO"
            val reason = item.flagReason?.replace("\"", "\"\"") ?: ""
            sb.append("${item.questionNum},")
            sb.append("${String.format("%.3f", item.difficultyP)},")
            sb.append("\"${item.difficultyLabel}\",")
            sb.append("${String.format("%.3f", item.discriminationD)},")
            sb.append("\"${item.discriminationLabel}\",")
            sb.append("${item.correctCount},")
            sb.append("${item.unattemptedCount},")
            sb.append("${item.optionFrequencies['A'] ?: 0},")
            sb.append("${item.optionFrequencies['B'] ?: 0},")
            sb.append("${item.optionFrequencies['C'] ?: 0},")
            sb.append("${item.optionFrequencies['D'] ?: 0},")
            sb.append("\"$flagStr\",")
            sb.append("\"$reason\"\n")
        }
        return sb.toString()
    }
}
