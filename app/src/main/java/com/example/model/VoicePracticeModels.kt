package com.example.model

enum class VoicePracticeMode(
    val label: String,
    val description: String
) {
    READ_ALOUD(
        label = "Read Aloud Check",
        description = "Read the shown text aloud and compare every matched, missed, extra, and mistaken word offline."
    ),
    EXPLAIN_FROM_MEMORY(
        label = "Explain from Memory",
        description = "See only the topic or question, speak your answer aloud, then compare against the reference notes."
    ),
    FREE_SUBJECT_SPEAK(
        label = "Subject Free-Speak",
        description = "Speak freely on the folder's subject prompt and inspect pace, repetitions, and keyword coverage."
    )
}

enum class VoiceSourceFilter(val label: String) {
    ALL("Notes + MCQs"),
    NOTES_ONLY("Study Notes"),
    PAPERS_ONLY("Exam MCQs")
}

enum class VoiceFillerCategory(val label: String) {
    VOCAL_DISFLUENCY("Vocal Hesitation (em/um/uh)"),
    LEXICAL_FILLER("Filler Phrase")
}

enum class VoiceWordMatchStatus {
    MATCHED,
    MISTAKE,
    MISSED,
    EXTRA,
    REPEATED
}

data class VoiceAlignmentToken(
    val expectedWord: String? = null,
    val spokenWord: String? = null,
    val status: VoiceWordMatchStatus
)

data class VoiceMistakePair(
    val expected: String,
    val spoken: String
)

data class VoicePromptItem(
    val id: String,
    val title: String,
    val promptQuestion: String,
    val referenceSummary: String,
    val sourceLabel: String,
    val folderId: String?,
    val folderPath: String,
    val language: String = "English",
    val formulaExpressions: List<String> = emptyList(),
    val optionExpressions: List<String> = emptyList()
)

data class VoiceFolderPromptResult(
    val prompts: List<VoicePromptItem>,
    val skippedFormulaItems: Int,
    val matchedFolderCount: Int
)

data class VoiceFillerOccurrence(
    val token: String,
    val count: Int,
    val category: VoiceFillerCategory
)

data class VoiceHesitationGap(
    val startMs: Long,
    val endMs: Long,
    val durationMs: Long
)

data class VoiceTranscriptSegment(
    val text: String,
    val isFiller: Boolean,
    val category: VoiceFillerCategory? = null
)

data class VoiceComparisonStats(
    val totalReferenceWords: Int,
    val totalSpokenWords: Int,
    val matchedCount: Int,
    val matchPercent: Int,
    val netAccuracyPercent: Int,
    val mistakeCount: Int,
    val missedCount: Int,
    val extraCount: Int,
    val repetitionCount: Int,
    val paceLabel: String,
    val alignmentTokens: List<VoiceAlignmentToken>,
    val mistakePairs: List<VoiceMistakePair>,
    val missedWords: List<String>,
    val extraWords: List<String>,
    val repeatedWords: List<String>
)

data class VoiceFluencyMetrics(
    val durationMs: Long,
    val speakingMs: Long,
    val silenceMs: Long,
    val hesitationPauseCount: Int,
    val hesitationRatioPercent: Int,
    val estimatedWpm: Int,
    val verbatimFillerCount: Int,
    val fillerRatePerMinute: Double,
    val fillerBreakdown: List<VoiceFillerOccurrence>,
    val keywordCoveragePercent: Int? = null,
    val comparison: VoiceComparisonStats? = null
)

data class VoiceAnalysisResult(
    val verbatimTranscript: String,
    val annotatedSegments: List<VoiceTranscriptSegment>,
    val metrics: VoiceFluencyMetrics,
    val topicAccuracyPercent: Int?,
    val strengths: List<String>,
    val coachingTips: List<String>,
    val usedAiAudioAnalysis: Boolean,
    val engineLabel: String
)

sealed interface VoiceAnalysisUiState {
    data object Idle : VoiceAnalysisUiState
    data object Recording : VoiceAnalysisUiState
    data class RecordedReady(val durationMs: Long) : VoiceAnalysisUiState
    data object Analyzing : VoiceAnalysisUiState
    data class Success(val result: VoiceAnalysisResult) : VoiceAnalysisUiState
    data class Error(val message: String) : VoiceAnalysisUiState
}
