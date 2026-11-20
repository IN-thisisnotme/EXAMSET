package com.example.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class PromptBuilderMode(
    val title: String,
    val subtitle: String,
    val testTag: String
) {
    QUESTION_SET("Question Set", "MCQs & Keys", "prompt_mode_question_set"),
    KNOWLEDGE_SET("Knowledge Set", "Notes & Codex", "prompt_mode_knowledge_set"),
    FLASHCARD_SET("Flashcard Set", "SM-2 Decks", "prompt_mode_flashcard_set")
}

class QuestionPromptState {
    var examType by mutableStateOf("")
    var subject by mutableStateOf("")
    var topic by mutableStateOf("")
    var questionCount by mutableFloatStateOf(20f)
    var difficulty by mutableStateOf("Moderate")
    var questionType by mutableStateOf("Mixed Formats")
    var questionLanguage by mutableStateOf("English")
    var explanationChoice by mutableStateOf("Same as Question")
    var customExplanationLanguage by mutableStateOf("")
    var optionCount by mutableIntStateOf(4)
    var explainAllFourOptions by mutableStateOf(true)
    var includeFormulasAndDiagrams by mutableStateOf(true)
    var promptPhasingStyle by mutableStateOf("Standard")
    var targetOutputFormat by mutableStateOf("Strict JSON")
    var promptDensity by mutableStateOf("Concise")
    var enableQuantityCorrectionRule by mutableStateOf(false)

    fun reset() {
        examType = ""
        subject = ""
        topic = ""
        questionCount = 20f
        difficulty = "Moderate"
        questionType = "Mixed Formats"
        questionLanguage = "English"
        explanationChoice = "Same as Question"
        customExplanationLanguage = ""
        optionCount = 4
        explainAllFourOptions = true
        includeFormulasAndDiagrams = true
        promptPhasingStyle = "Standard"
        targetOutputFormat = "Strict JSON"
        promptDensity = "Concise"
        enableQuantityCorrectionRule = false
    }
}
