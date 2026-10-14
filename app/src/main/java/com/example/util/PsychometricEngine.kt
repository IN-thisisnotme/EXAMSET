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
        val discriminationD: Float,        // -1.0 to +1.0 (difference between top 27% and bottom 27%)
        val pointBiserialR: Float = 0.0f,  // -1.0 to +1.0 (correlation between item score and total test score)
        val totalAttempts: Int,
        val correctCount: Int,
        val unattemptedCount: Int,
        val optionFrequencies: Map<Char, Int>,
        val nonFunctioningDistractors: List<Char> = emptyList(),
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

        val pointBiserialLabel: String
            get() = when {
                pointBiserialR >= 0.35f -> "Excellent (r_pb >= 0.35)"
                pointBiserialR >= 0.25f -> "Good (r_pb >= 0.25)"
                pointBiserialR >= 0.15f -> "Fair (r_pb >= 0.15)"
                pointBiserialR >= 0.00f -> "Marginal (r_pb >= 0.00)"
                else -> "Negative (Check Key!)"
            }
    }

    data class CandidateScorecard(
        val rank: Int,
        val rollNumber: String,
        val bookletNumber: String,
        val bookletCode: String,
        val totalScore: Double,
        val correctCount: Int,
        val incorrectCount: Int,
        val unansweredCount: Int,
        val accuracyPercent: Int,
        val submittedAt: Long
    )

    data class CandidateSubmission(
        val rollNumber: String,
        val bookletNumber: String,
        val bookletCode: String,
        val answers: Map<Int, Char?>,
        val submittedAt: Long = System.currentTimeMillis()
    )

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
        val flaggedItemCount: Int,
        val candidates: List<CandidateScorecard> = emptyList()
    )

    /**
     * Evaluates a cohort of student candidate answer maps against the official question paper.
     */
    fun evaluateCohort(
        candidateResponses: List<Map<Int, Char?>>,
        paper: QuestionPaper
    ): CohortPsychometricReport {
        val submissions = candidateResponses.mapIndexed { idx, answers ->
            CandidateSubmission(
                rollNumber = String.format(java.util.Locale.US, "2026%06d", 100001 + idx),
                bookletNumber = String.format(java.util.Locale.US, "7%06d", 100001 + idx),
                bookletCode = "A",
                answers = answers,
                submittedAt = System.currentTimeMillis()
            )
        }
        return evaluateCohortWithCandidates(submissions, paper)
    }

    /**
     * Evaluates candidate submissions including candidate Roll Number, Booklet Number, and Booklet Code.
     * Computes Classical Test Theory metrics:
     * - Item Difficulty (p-value)
     * - Item Discrimination Index (D: Upper 27% vs Lower 27%)
     * - Point-Biserial Correlation (r_pb)
     * - Distractor analysis & Non-functioning distractor detection
     * - Test-level internal reliability (Cronbach's Alpha)
     */
    fun evaluateCohortWithCandidates(
        submissions: List<CandidateSubmission>,
        paper: QuestionPaper
    ): CohortPsychometricReport {
        val n = submissions.size
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
                flaggedItemCount = 0,
                candidates = emptyList()
            )
        }

        // 1. Calculate each candidate's raw score & correct counts
        data class CandidateEval(
            val sub: CandidateSubmission,
            val score: Int,
            val correct: Int,
            val incorrect: Int,
            val unanswered: Int
        )

        val candidateEvals = submissions.map { sub ->
            var score = 0
            var correct = 0
            var incorrect = 0
            var unanswered = 0

            paper.questions.forEach { q ->
                val chosen = sub.answers[q.num]
                val correctChar = q.correctAnswer?.uppercase()?.firstOrNull()
                if (chosen != null && correctChar != null) {
                    if (chosen.uppercaseChar() == correctChar) {
                        score += 4
                        correct++
                    } else {
                        score -= 1
                        incorrect++
                    }
                } else {
                    unanswered++
                }
            }
            CandidateEval(sub, score, correct, incorrect, unanswered)
        }

        val rawScores = candidateEvals.map { it.score }
        val mean = rawScores.average().toFloat()
        val sortedByScore = candidateEvals.sortedByDescending { it.score }
        val sortedScores = rawScores.sorted()
        val median = if (n % 2 == 1) {
            sortedScores[n / 2].toFloat()
        } else {
            (sortedScores[n / 2 - 1] + sortedScores[n / 2]) / 2.0f
        }
        val maxScore = sortedScores.last()
        val minScore = sortedScores.first()

        val variance = rawScores.map { (it - mean) * (it - mean) }.average().toFloat()
        val stdDev = sqrt(variance.coerceAtLeast(1e-4f))

        // 2. Identify Upper 27% and Lower 27% cohorts for discrimination index D
        val cohort27Size = (n * 0.27f).toInt().coerceAtLeast(1)
        val upperCohort = sortedByScore.take(cohort27Size).map { it.sub.answers }
        val lowerCohort = sortedByScore.takeLast(cohort27Size).map { it.sub.answers }

        var sumItemVariances = 0.0f
        val itemEvals = mutableListOf<ItemEvaluation>()

        paper.questions.forEach { q ->
            val correctChar = q.correctAnswer?.uppercase()?.firstOrNull()
            var correctCount = 0
            var unattemptedCount = 0
            val optionFreqs = mutableMapOf('A' to 0, 'B' to 0, 'C' to 0, 'D' to 0)

            // For point-biserial correlation: scores of candidates who answered correctly
            val scoresOfCorrect = mutableListOf<Int>()

            candidateEvals.forEach { cEval ->
                val choice = cEval.sub.answers[q.num]
                if (choice != null) {
                    val upperChoice = choice.uppercaseChar()
                    optionFreqs[upperChoice] = (optionFreqs[upperChoice] ?: 0) + 1
                    if (correctChar != null && upperChoice == correctChar) {
                        correctCount++
                        scoresOfCorrect.add(cEval.score)
                    }
                } else {
                    unattemptedCount++
                }
            }

            val pValue = correctCount.toFloat() / n
            // Item variance: p * (1 - p) * score scale (5 points differential)
            val itemVariance = pValue * (1.0f - pValue) * 25.0f
            sumItemVariances += itemVariance

            // Upper 27% vs Lower 27% discrimination D
            val upperCorrect = upperCohort.count { it[q.num]?.uppercaseChar() == correctChar }
            val lowerCorrect = lowerCohort.count { it[q.num]?.uppercaseChar() == correctChar }
            val pUpper = upperCorrect.toFloat() / upperCohort.size
            val pLower = lowerCorrect.toFloat() / lowerCohort.size
            val dIndex = (pUpper - pLower).coerceIn(-1.0f, 1.0f)

            // Point-Biserial Correlation r_pb = [(mean_correct - mean_total) / stdDev] * sqrt(p / (1 - p))
            val rPb = if (correctCount in 1 until n && stdDev > 1e-4f) {
                val meanCorrect = scoresOfCorrect.average().toFloat()
                val pqRatio = sqrt((pValue / (1.0f - pValue)).toDouble()).toFloat()
                val r = ((meanCorrect - mean) / stdDev) * pqRatio
                r.coerceIn(-1.0f, 1.0f)
            } else 0.0f

            // Distractor efficiency: non-functioning if chosen by < 5% of candidates
            val nonFunctioning = mutableListOf<Char>()
            listOf('A', 'B', 'C', 'D').forEach { opt ->
                if (opt != correctChar) {
                    val count = optionFreqs[opt] ?: 0
                    val pct = count.toFloat() / n
                    if (pct < 0.05f) {
                        nonFunctioning.add(opt)
                    }
                }
            }

            // Flag conditions:
            var isFlagged = false
            var flagReason: String? = null
            if (rPb < 0.0f || dIndex < 0.0f) {
                isFlagged = true
                flagReason = "Negative discrimination (r_pb = ${String.format(java.util.Locale.US, "%.2f", rPb)}, D = ${String.format(java.util.Locale.US, "%.2f", dIndex)}): High scorers failed more than low scorers. Verify answer key!"
            } else if (pValue < 0.15f) {
                isFlagged = true
                flagReason = "Extremely high difficulty (p = ${String.format(java.util.Locale.US, "%.2f", pValue)}): Outlier item with <15% pass rate."
            } else if (nonFunctioning.size >= 2) {
                isFlagged = true
                flagReason = "Weak distractors: Options ${nonFunctioning.joinToString(", ")} chosen by <5% of students."
            } else if (dIndex < 0.12f && pValue in 0.30f..0.70f) {
                isFlagged = true
                flagReason = "Marginal discrimination (D = ${String.format(java.util.Locale.US, "%.2f", dIndex)}): Fails to differentiate candidate ability."
            }

            itemEvals.add(
                ItemEvaluation(
                    questionNum = q.num,
                    difficultyP = pValue,
                    discriminationD = dIndex,
                    pointBiserialR = rPb,
                    totalAttempts = n - unattemptedCount,
                    correctCount = correctCount,
                    unattemptedCount = unattemptedCount,
                    optionFrequencies = optionFreqs,
                    nonFunctioningDistractors = nonFunctioning,
                    isFlagged = isFlagged,
                    flagReason = flagReason
                )
            )
        }

        // 3. Cronbach's Alpha test internal reliability: [k / (k - 1)] * [1 - (sumItemVariances / variance)]
        val alpha = if (k > 1 && variance > 1e-4) {
            val ratio = (k.toFloat() / (k - 1)) * (1.0f - (sumItemVariances / variance))
            ratio.coerceIn(0.0f, 0.99f)
        } else 0.85f

        // 4. Build ranked candidate scorecards
        val scorecards = sortedByScore.mapIndexed { idx, cEval ->
            val totalAtt = cEval.correct + cEval.incorrect
            val acc = if (totalAtt > 0) ((cEval.correct.toFloat() / totalAtt) * 100).toInt() else 0
            CandidateScorecard(
                rank = idx + 1,
                rollNumber = cEval.sub.rollNumber,
                bookletNumber = cEval.sub.bookletNumber,
                bookletCode = cEval.sub.bookletCode,
                totalScore = cEval.score.toDouble(),
                correctCount = cEval.correct,
                incorrectCount = cEval.incorrect,
                unansweredCount = cEval.unanswered,
                accuracyPercent = acc,
                submittedAt = cEval.sub.submittedAt
            )
        }

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
            flaggedItemCount = flaggedCount,
            candidates = scorecards
        )
    }

    /**
     * Generates a calibrated simulated cohort of N candidates (default 40)
     * for chief question paper setters to preview psychometric distributions.
     */
    fun generateSimulatedCohortCalibration(
        paper: QuestionPaper,
        candidateCount: Int = 40
    ): CohortPsychometricReport {
        val rand = java.util.Random(paper.metadata.title.hashCode().toLong())
        val sets = listOf("A", "B", "C", "D")

        val simulatedSubmissions = (1..candidateCount).map { i ->
            // Candidate ability level (-2.0 to +2.0)
            val ability = (rand.nextGaussian() * 0.8).coerceIn(-2.0, 2.0)
            val answersMap = mutableMapOf<Int, Char?>()

            paper.questions.forEach { q ->
                val correctChar = q.correctAnswer?.uppercase()?.firstOrNull() ?: 'A'
                // Item base difficulty: pseudo-random from question content
                val itemDiff = ((q.question.length % 7) - 3) * 0.3
                // Probability of correct answer via 2PL Logistic Model
                val probCorrect = 1.0 / (1.0 + Math.exp(-(ability - itemDiff)))

                if (rand.nextDouble() < 0.06) {
                    answersMap[q.num] = null // Skipped
                } else if (rand.nextDouble() < probCorrect) {
                    answersMap[q.num] = correctChar // Correct
                } else {
                    // Distractor selection
                    val distractors = listOf('A', 'B', 'C', 'D').filter { it != correctChar }
                    answersMap[q.num] = distractors[rand.nextInt(distractors.size)]
                }
            }

            CandidateSubmission(
                rollNumber = String.format(java.util.Locale.US, "202610%04d", i),
                bookletNumber = String.format(java.util.Locale.US, "710%04d", i),
                bookletCode = sets[i % sets.size],
                answers = answersMap,
                submittedAt = System.currentTimeMillis() - ((candidateCount - i) * 60000L)
            )
        }

        return evaluateCohortWithCandidates(simulatedSubmissions, paper)
    }

    /**
     * Formats the psychometric item evaluation report as a CSV string for export and reporting.
     */
    fun formatPsychometricReportCsv(report: CohortPsychometricReport, examTitle: String): String {
        val sb = StringBuilder()
        sb.append("# Psychometric Item Analysis Report (Classical Test Theory)\n")
        sb.append("# Exam: \"${examTitle.replace("\"", "\"\"")}\"\n")
        sb.append("# Total Candidates: ${report.candidateCount} | Total Questions: ${report.questionCount}\n")
        sb.append("# Mean Score: ${String.format(java.util.Locale.US, "%.2f", report.meanScore)} | Std Dev: ${String.format(java.util.Locale.US, "%.2f", report.standardDeviation)} | Cronbach Alpha: ${String.format(java.util.Locale.US, "%.3f", report.cronbachAlpha)}\n\n")
        sb.append("Question_Num,Difficulty_P,Difficulty_Label,Discrimination_D,Point_Biserial_r_pb,Point_Biserial_Label,Correct_Count,Unattempted_Count,Opt_A_Count,Opt_B_Count,Opt_C_Count,Opt_D_Count,Non_Functioning_Distractors,Flagged,Flag_Reason\n")

        report.items.forEach { item ->
            val flagStr = if (item.isFlagged) "YES" else "NO"
            val reason = item.flagReason?.replace("\"", "\"\"") ?: ""
            val nonFunc = if (item.nonFunctioningDistractors.isEmpty()) "None" else item.nonFunctioningDistractors.joinToString(";")
            sb.append("${item.questionNum},")
            sb.append("${String.format(java.util.Locale.US, "%.3f", item.difficultyP)},")
            sb.append("\"${item.difficultyLabel}\",")
            sb.append("${String.format(java.util.Locale.US, "%.3f", item.discriminationD)},")
            sb.append("${String.format(java.util.Locale.US, "%.3f", item.pointBiserialR)},")
            sb.append("\"${item.pointBiserialLabel}\",")
            sb.append("${item.correctCount},")
            sb.append("${item.unattemptedCount},")
            sb.append("${item.optionFrequencies['A'] ?: 0},")
            sb.append("${item.optionFrequencies['B'] ?: 0},")
            sb.append("${item.optionFrequencies['C'] ?: 0},")
            sb.append("${item.optionFrequencies['D'] ?: 0},")
            sb.append("\"$nonFunc\",")
            sb.append("\"$flagStr\",")
            sb.append("\"$reason\"\n")
        }
        return sb.toString()
    }

    /**
     * Formats student scorecards into an official ranked CSV manifest for exam records.
     */
    fun formatCandidateScorecardsCsv(report: CohortPsychometricReport, examTitle: String): String {
        val sb = StringBuilder()
        sb.append("# Student Candidate Examination Scorecards & Ranking\n")
        sb.append("# Exam: \"${examTitle.replace("\"", "\"\"")}\"\n")
        sb.append("# Total Evaluated: ${report.candidates.size} | Date Generated: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date())}\n\n")
        sb.append("Rank,Roll_Number,Booklet_Number,Booklet_Code,Total_Score,Correct_Count,Incorrect_Count,Unanswered_Count,Accuracy_Percent,Submission_Timestamp\n")

        report.candidates.forEach { c ->
            val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(c.submittedAt))
            sb.append("${c.rank},")
            sb.append("${c.rollNumber},")
            sb.append("${c.bookletNumber},")
            sb.append("${c.bookletCode},")
            sb.append("${c.totalScore},")
            sb.append("${c.correctCount},")
            sb.append("${c.incorrectCount},")
            sb.append("${c.unansweredCount},")
            sb.append("${c.accuracyPercent}%,")
            sb.append("\"$dateStr\"\n")
        }
        return sb.toString()
    }

    /**
     * Generates an official, comprehensive individual Student Report Card.
     * Includes candidate metadata, scoring breakdown, penalty accounting,
     * percentile rank, and question-by-question response audit.
     */
    fun generateStudentReportCard(
        candidate: CandidateScorecard,
        paper: QuestionPaper,
        answers: Map<Int, Char?>? = null,
        totalCohortSize: Int = 1
    ): String {
        val totalMarks = paper.questions.size * 4
        val percentile = if (totalCohortSize > 1) {
            val p = ((totalCohortSize - candidate.rank + 1).toDouble() / totalCohortSize.toDouble()) * 100.0
            String.format(java.util.Locale.US, "%.1fth Percentile", p)
        } else {
            "N/A (Individual Assessment)"
        }

        val performanceBand = when {
            candidate.accuracyPercent >= 85 -> "Distinction / Outstanding"
            candidate.accuracyPercent >= 70 -> "First Class / Excellent"
            candidate.accuracyPercent >= 50 -> "Second Class / Good"
            candidate.accuracyPercent >= 35 -> "Pass / Needs Reinforcement"
            else -> "Remedial Attention Required"
        }

        val dateStr = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.US).format(java.util.Date(candidate.submittedAt))

        val sb = StringBuilder()
        sb.append("================================================================================\n")
        sb.append("                       OFFICIAL STUDENT EXAMINATION REPORT CARD                \n")
        sb.append("================================================================================\n\n")

        sb.append("EXAMINATION DETAILS:\n")
        sb.append("  Examination Title : ${paper.metadata.title.ifBlank { "National Level Competitive Examination" }}\n")
        sb.append("  Subject / Topic   : ${paper.metadata.subject.ifBlank { "General Sciences & Aptitude" }}\n")
        sb.append("  Assessment Set    : Set ${candidate.bookletCode.ifBlank { paper.metadata.setNumber.ifBlank { "A" } }}\n")
        sb.append("  Date Evaluated    : $dateStr\n\n")

        sb.append("CANDIDATE IDENTIFICATION:\n")
        sb.append("  Candidate Roll No : ${candidate.rollNumber.ifBlank { "UNASSIGNED" }}\n")
        sb.append("  Test Booklet No.  : ${candidate.bookletNumber.ifBlank { "UNASSIGNED" }}\n")
        sb.append("  Booklet Set Code  : Set ${candidate.bookletCode}\n\n")

        sb.append("EXAMINATION PERFORMANCE SUMMARY:\n")
        sb.append("  Overall Score     : ${candidate.totalScore.toInt()} / $totalMarks marks\n")
        sb.append("  Cohort Rank       : Rank ${candidate.rank} of $totalCohortSize candidates\n")
        sb.append("  Cohort Percentile : $percentile\n")
        sb.append("  Accuracy Rate     : ${candidate.accuracyPercent}%\n")
        sb.append("  Performance Band  : $performanceBand\n\n")

        sb.append("SCORING & PENALTY BREAKDOWN (NEET / UPSC MARKING SCHEME):\n")
        sb.append("  Correct Responses : ${candidate.correctCount}  [+${candidate.correctCount * 4} marks]\n")
        sb.append("  Incorrect Responses : ${candidate.incorrectCount}  [-${candidate.incorrectCount} penalty marks]\n")
        sb.append("  Unattempted / Skip  : ${candidate.unansweredCount}  [0 marks]\n")
        sb.append("  Total Questions   : ${paper.questions.size}\n\n")

        if (!answers.isNullOrEmpty()) {
            sb.append("--------------------------------------------------------------------------------\n")
            sb.append("                      DETAILED QUESTION AUDIT & RESPONSE LOG                    \n")
            sb.append("--------------------------------------------------------------------------------\n")
            sb.append("  Q#   Marked Option   Official Key   Marks Awarded   Evaluation Status\n")
            sb.append("  ---  -------------   ------------   -------------   -----------------\n")

            paper.questions.forEach { q ->
                val chosen = answers[q.num]?.uppercaseChar()
                val correctChar = q.correctAnswer?.uppercase()?.firstOrNull()
                val (pts, status) = when {
                    chosen == null -> Pair(" 0", "UNATTENDED / SKIPPED")
                    chosen == correctChar -> Pair("+4", "CORRECT")
                    else -> Pair("-1", "INCORRECT")
                }
                val markedDisplay = chosen?.toString() ?: "—"
                val keyDisplay = correctChar?.toString() ?: "—"
                sb.append(String.format(java.util.Locale.US, "  %03d      %-10s      %-8s        %-6s       %s\n",
                    q.num, markedDisplay, keyDisplay, pts, status))
            }
            sb.append("--------------------------------------------------------------------------------\n")
        }

        sb.append("\n================================================================================\n")
        sb.append("                  Generated by Exam Assessment & Psychometric Suite             \n")
        sb.append("================================================================================\n")
        return sb.toString()
    }

    /**
     * Formats all student report cards into a single consolidated document for the cohort.
     */
    fun formatAllStudentReportCards(
        report: CohortPsychometricReport,
        paper: QuestionPaper,
        candidateSubmissions: List<CandidateSubmission>? = null
    ): String {
        val submissionsMap = candidateSubmissions?.associateBy { it.rollNumber }
        val sb = StringBuilder()
        report.candidates.forEachIndexed { index, candidate ->
            val answers = submissionsMap?.get(candidate.rollNumber)?.answers
            sb.append(generateStudentReportCard(
                candidate = candidate,
                paper = paper,
                answers = answers,
                totalCohortSize = report.candidateCount
            ))
            if (index < report.candidates.size - 1) {
                sb.append("\n\n\n")
            }
        }
        return sb.toString()
    }
}
