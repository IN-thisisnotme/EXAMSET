package com.example.model

enum class VoicePracticeMode(
    val label: String,
    val description: String
) {
    EXPLAIN_FROM_MEMORY(
        label = "Explain from Memory",
        description = "See only the topic or question, speak your explanation aloud, then check recall & fillers."
    ),
    READ_ALOUD(
        label = "Read Aloud Fluency",
        description = "Read the clean, formula-free study text aloud to track pace, pauses, and filler habits."
    ),
    FREE_SUBJECT_SPEAK(
        label = "Subject Free-Speak",
        description = "Speak freely on the folder's subject prompt to measure clear delivery without 'em' or 'um'."
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

data class VoicePromptItem(
    val id: String,
    val title: String,
    val promptQuestion: String,
    val referenceSummary: String,
    val sourceLabel: String,
    val folderId: String?,
    val folderPath: String,
    val language: String = "English"
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
    val keywordCoveragePercent: Int? = null
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
