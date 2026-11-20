package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.theme.ExamTheme

import com.example.model.KnowledgeCollectionType
import com.example.model.KnowledgeFormatRule

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PromptGeneratorTab(
    viewModel: MainViewModel,
    onNavigateToCreator: () -> Unit,
    onOpenKnowledgeStudio: (title: String, subject: String, collectionType: KnowledgeCollectionType, rule: KnowledgeFormatRule) -> Unit = { _, _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var isKnowledgeMode by remember { mutableStateOf(false) }
    val knowledgeState = remember { KnowledgePromptState() }

    var examType by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }
    var questionCount by remember { mutableFloatStateOf(20f) }
    var difficulty by remember { mutableStateOf("Moderate") }
    var questionType by remember { mutableStateOf("Mixed Formats") }
    var questionLanguage by remember { mutableStateOf("English") }
    var explanationChoice by remember { mutableStateOf("Same as Question") }
    var customExplanationLanguage by remember { mutableStateOf("") }
    var optionCount by remember { mutableIntStateOf(4) } // 4 or 5 options
    var explainAllFourOptions by remember { mutableStateOf(true) }
    var includeFormulasAndDiagrams by remember { mutableStateOf(true) }
    var promptPhasingStyle by remember { mutableStateOf("Standard") }
    var targetOutputFormat by remember { mutableStateOf("Strict JSON") } // "Strict JSON", "Standard Text (Aiken)", "Markdown Table"
    var promptDensity by remember { mutableStateOf("Concise") } // "Concise", "Comprehensive"

    // Format & Quantity Correction Rule (Disabled by default: Unrestricted Full Stream)
    var enableQuantityCorrectionRule by remember { mutableStateOf(false) }

    var copied by remember { mutableStateOf(false) }

    val countInt = questionCount.toInt()

    val effectiveExplanationLanguage by remember(questionLanguage, explanationChoice, customExplanationLanguage) {
        derivedStateOf {
            val q = questionLanguage.trim().ifBlank { "English" }
            when (explanationChoice) {
                "Same as Question" -> "$q (same language as questions)"
                "Bilingual" -> {
                    if (q.equals("English", ignoreCase = true)) {
                        "Bilingual (English & Hindi)"
                    } else {
                        "Bilingual (English & $q)"
                    }
                }
                "English" -> "English"
                "Hindi" -> "Hindi"
                "Custom" -> customExplanationLanguage.trim().ifBlank { q }
                else -> explanationChoice
            }
        }
    }

    val generatedPrompt by remember(
        examType, subject, topic, countInt, difficulty, questionType,
        questionLanguage, effectiveExplanationLanguage, explainAllFourOptions,
        includeFormulasAndDiagrams, promptPhasingStyle, enableQuantityCorrectionRule,
        targetOutputFormat, promptDensity, optionCount
    ) {
        derivedStateOf {
            val qLang = questionLanguage.trim().ifBlank { "English" }
            val targetExam = examType.trim().ifBlank { "Competitive Examination" }
            val targetSubject = buildString {
                if (subject.isNotBlank()) {
                    append(subject.trim())
                    if (topic.isNotBlank()) {
                        append(" (Focus: ${topic.trim()})")
                    }
                } else if (topic.isNotBlank()) {
                    append("Focus: ${topic.trim()}")
                } else {
                    append("Comprehensive Subject Curriculum")
                }
            }

            if (targetOutputFormat == "Strict JSON") {
                buildString {
                    append(
                        """
                        Act as a senior psychometrician and chief examination paper setter for $targetExam.

                        Generate exactly $countInt multiple-choice questions (MCQs) for $targetSubject in $qLang at $difficulty difficulty (Question Pattern: $questionType).

                        ### 1. STRICT JSON OUTPUT REQUIREMENTS
                        Output only a valid JSON array containing the complete question set, correct answers, and explanations. Do not include introductory remarks, Markdown code fences, or trailing commentary.

                        ### 2. JSON SCHEMA
                        [
                            {
                                "num": 1,
                                "question": "Question text here",
                                "options": {
                                "a": "Option A text",
                                "b": "Option B text",
                                "c": "Option C text",
                                "d": "Option D text"${if (optionCount == 5) ",\n                                \"e\": \"Option E text\"" else ""}
                                },
                                "correctAnswer": "A",
                                "explanation": "Explanation text here"
                            }
                        ]

                        ### 3. QUESTION QUALITY AND ANSWER RULES
                        1. Generate exactly $countInt questions relevant to $targetSubject and appropriate for $targetExam.
                        2. Each question must contain EXACTLY $optionCount options, with keys ${if (optionCount == 5) "\"a\", \"b\", \"c\", \"d\", and \"e\"" else "\"a\", \"b\", \"c\", and \"d\""}.${if (optionCount == 4) " Never include an option \"e\" or any additional options." else ""}
                        3. Each option must be plausible, relevant, and clearly distinguishable from the others. Avoid overlapping options, grammatical clues, and making the correct option noticeably longer without a valid reason.
                        4. The "correctAnswer" value must be exactly one uppercase letter: ${if (optionCount == 5) "\"A\", \"B\", \"C\", \"D\", or \"E\"" else "\"A\", \"B\", \"C\", or \"D\""}. It must identify the option that is factually and logically correct.
                        5. Distribute correct answers approximately evenly across ${if (optionCount == 5) "A, B, C, D, and E" else "A, B, C, and D"} when feasible. Never compromise correctness to balance the answer key.

                        ### 4. EXPLANATION REQUIREMENTS
                        Write every explanation in $effectiveExplanationLanguage. Ensure that the reasoning is accurate, specific to the question, and sufficient to justify the answer. Avoid repetition, filler, vague claims, and unrelated background information.
                        """.trimIndent()
                    )

                    append("\n\n")

                    if (promptDensity == "Concise") {
                        append(
                            """
                            EXPLANATION DEPTH
                            Keep explanations concise but complete. State the decisive reasoning and include only the concepts, key terms, formulas, or calculations needed to establish the answer.
                            """.trimIndent()
                        )
                    } else {
                        append(
                            """
                            EXPLANATION DEPTH
                            Provide a detailed, logically organized explanation. Explain the relevant concepts, key terms, principles, formulas, and calculations where applicable. Show why the reasoning leads to the answer rather than merely restating the correct option.
                            """.trimIndent()
                        )
                    }

                    if (enableQuantityCorrectionRule) {
                        append("\n\n")
                        append(
                            """
                            TRUNCATION GUARD (STRICT EXPLANATION BUDGET)
                            Keep each explanation strictly within 2–3 concise sentences so all $countInt questions complete without output truncation.
                            """.trimIndent()
                        )
                    }

                    append("\n\n")

                    if (explainAllFourOptions) {
                        append(
                            """
                            OPTION-BY-OPTION EXPLANATION
                            Explain options ${if (optionCount == 5) "A, B, C, D, and E" else "A, B, C, and D"} individually and in that order. For each option, explicitly state whether it is correct or incorrect and give a specific reason. Explain why the correct option is valid and why each distractor fails. Do not repeat the same reasoning in different words.
                            """.trimIndent()
                        )
                    } else {
                        append(
                            """
                            ANSWER EXPLANATION
                            Explain why the correct answer is right and identify the decisive reasoning. Do not provide a separate explanation for every incorrect option.
                            """.trimIndent()
                        )
                    }

                    append("\n\n")

                    append(
                        """
                        ### 5. ADAPT EXPLANATIONS TO THE QUESTION TYPE
                        - Conceptual questions: Explain the governing concept and connect it directly to the answer.
                        - Definition-based questions: Clarify the key term or definition that determines the answer.
                        - Numerical questions: Identify the relevant given information, state the applicable formula or principle, show substitution and essential calculations, and state the final result.
                        - Formula-based questions: Explain the relevant formula and how it applies to the problem.
                        - Statement-based questions: Evaluate the relevant statements and explain the reasoning behind the answer.
                        - Comparison questions: Explain the decisive similarities, differences, or relationships.
                        - Process-based questions: Explain the relevant stages or causal relationships in logical order.

                        Use only the explanation elements relevant to each question. Do not force conceptual questions into numerical steps or use an identical explanation structure for every question.
                        """.trimIndent()
                    )

                    if (includeFormulasAndDiagrams) {
                        append("\n\n")
                        append(
                            """
                            ### 6. MATHEMATICAL NOTATION
                            Use LaTeX formatting for mathematical expressions and chemical formulas when it improves clarity. Ordinary text is acceptable when sufficient. Do not add unnecessary formulas, LaTeX delimiters, or backslashes.
                            """.trimIndent()
                        )
                    }

                    append("\n\n")

                    append(
                        """
                        ### 7. JSON STRING ESCAPING
                        Use ordinary text wherever possible. Do not add unnecessary escape characters. When a JSON string contains a literal backslash, escape it as two consecutive backslashes. Escape double quotes and control characters according to JSON rules. Represent line breaks inside JSON string values using the JSON newline escape sequence, not an unescaped line break. Ensure that all strings are valid JSON strings.

                        ### 8. FINAL VALIDATION
                        Before returning the result, verify that:
                        - The array contains exactly $countInt question objects.
                        - Question numbers are sequential, starting at 1.
                        - Every object contains exactly the fields "num", "question", "options", "correctAnswer", and "explanation".
                        - Every options object contains exactly the keys ${if (optionCount == 5) "\"a\", \"b\", \"c\", \"d\", and \"e\"" else "\"a\", \"b\", \"c\", and \"d\""}.
                        - Every correctAnswer matches the correct option and uses one uppercase letter.
                        - Explanations follow the selected explanation depth and option-explanation setting.
                        - All JSON strings are correctly escaped and there are no trailing commas or comments.

                        Return ONLY the valid JSON array, beginning with [ and ending with ].
                        """.trimIndent()
                    )
                }
            } else if (targetOutputFormat == "Markdown Table") {
                buildString {
                    append("Act as an examination paper setter for $targetExam.\n\n")
                    append("Generate exactly $countInt multiple-choice questions (MCQs) for $targetSubject at $difficulty difficulty in $qLang (Question Pattern: $questionType).\n\n")
                    append("OUTPUT FORMAT: MARKDOWN TABLE\n")
                    append("Format the entire question set as a clean, standardized Markdown table with the following column headers:\n")
                    if (optionCount == 5) {
                        append("| # | Question | Option A | Option B | Option C | Option D | Option E | Answer | Explanation |\n")
                        append("|---|---|---|---|---|---|---|:---:|---|\n\n")
                    } else {
                        append("| # | Question | Option A | Option B | Option C | Option D | Answer | Explanation |\n")
                        append("|---|---|---|---|---|---|:---:|---|\n\n")
                    }
                    append("Rules:\n")
                    append("1. STRICT $optionCount-OPTION CONSTRAINT: Each question must have EXACTLY $optionCount options (${if (optionCount == 5) "Option A, B, C, D, E" else "Option A, Option B, Option C, Option D"}).\n")
                    append("2. Put each question in a single table row.\n")
                    append("3. In the 'Answer' column, write strictly ${if (optionCount == 5) "'A', 'B', 'C', 'D', or 'E'" else "'A', 'B', 'C', or 'D'"}.\n")
                    append("4. In the 'Explanation' column, provide a ${if (promptDensity == "Concise") "concise" else "detailed"}, step-by-step rationale (in $effectiveExplanationLanguage)${if (enableQuantityCorrectionRule) " kept strictly within 2–3 sentences to prevent truncation" else ""}.\n")
                    append("5. Balanced key distribution across ${if (optionCount == 5) "A, B, C, D, and E" else "A, B, C, and D"}.\n")
                    if (includeFormulasAndDiagrams) {
                        append("6. Use LaTeX formatting for mathematical expressions and chemical formulas where appropriate.\n")
                        append("7. Do not include extra commentary or greeting text before or after the table.")
                    } else {
                        append("6. Do not include extra commentary or greeting text before or after the table.")
                    }
                }
            } else {
                buildString {
                    append("Act as a senior psychometrician and chief examination paper setter for $targetExam.\n\n")
                    append("Generate exactly $countInt multiple-choice questions (MCQs) for $targetSubject at $difficulty difficulty (Question Pattern: $questionType) in $qLang.\n\n")

                    append("### 1. LANGUAGE & SCRIPT CONVENTIONS\n")
                    if (effectiveExplanationLanguage == qLang || explanationChoice == "Same as Question") {
                        append("- Language: Write the question stems, options, and explanations entirely in $qLang using natural, academically rigorous terminology.\n")
                    } else {
                        append("- Language: Write question stems and options in $qLang, and explanations in $effectiveExplanationLanguage, using natural, academically rigorous terminology.\n")
                    }
                    append("- Structural Markers: Regardless of the language used, keep question labels as \"Q1.\", \"Q2.\" and option keys strictly as ${if (optionCount == 5) "\"(A)\", \"(B)\", \"(C)\", \"(D)\", \"(E)\"" else "\"(A)\", \"(B)\", \"(C)\", \"(D)\""} for universal OMR and parser compatibility.\n\n")

                    append("### 2. PSYCHOMETRIC & DISTRACTOR RULES\n")
                    when (difficulty) {
                        "Easy" -> append("1. COGNITIVE LEVEL (Easy / Foundational): Direct conceptual recall, core standard definitions, basic formulas, and straightforward identification with unambiguous distractors.\n")
                        "Moderate" -> append("1. COGNITIVE LEVEL (Moderate / Standard): Standard examination caliber requiring multi-step comprehension, application of principles, and analytical deduction.\n")
                        "Hard" -> append("1. COGNITIVE LEVEL (Hard / Advanced): Advanced analytical rigor, intricate edge cases, multi-layered calculations, and trap options diagnosing common misconceptions.\n")
                        "Extreme" -> append("1. COGNITIVE LEVEL (Extreme Olympiad / Rank-Decider): Deep synthesis, Olympiad-level multi-concept integration, subtle distractor nuances, and demanding problem-solving.\n")
                        else -> append("1. COGNITIVE LEVEL (Mixed Difficulty): Balanced distribution spanning ~25% Foundational, ~50% Standard/Moderate, and ~25% High-Rigor/Challenging questions.\n")
                    }
                    when (questionType) {
                        "Conceptual & Theory" -> append("2. QUESTION FORMAT FOCUS: Strictly conceptual & theoretical questions evaluating core laws, principles, and cause-effect reasoning.\n")
                        "Numerical & Calculation" -> append("2. QUESTION FORMAT FOCUS: Quantitative & numerical problem-solving with exact formulas, numerical options, and clear calculation steps.\n")
                        "Assertion-Reason & Statement-Based" -> append("2. QUESTION FORMAT FOCUS: Deep analytical items alternating between 'Assertion (A) & Reason (R)' and 'Statement I & Statement II / Consider the following statements'.\n")
                        "Case Study & Passage-Based" -> append("2. QUESTION FORMAT FOCUS: Practical context-driven scenarios, experimental setups, clinical/field case studies, or passage evaluation.\n")
                        else -> {
                            append("2. MANDATORY FORMAT DIVERSITY (AUTHENTIC COMPETITIVE EXAM BLUEPRINT):\n")
                            append("   Do NOT generate only plain, single-sentence recall questions. You MUST actively distribute these $countInt questions across diverse examination formats.\n")
                        }
                    }
                    append("3. STRICT $optionCount-OPTION CONSTRAINT: Every question must contain EXACTLY $optionCount options labeled (A), (B), (C), (D)${if (optionCount == 5) ", and (E)" else ""}.\n")
                    append("4. DISTRACTOR HOMOGENEITY: All $optionCount options must be mutually exclusive and grammatically parallel.\n")
                    append("5. LENGTH CONTROL: The correct option must NOT be the longest option by default.\n")
                    append("6. ORDERING: If options contain numerical values or dates, order them strictly from lowest to highest.\n")
                    append("7. PROHIBITED PHRASES: Never use \"All of the above\", \"None of the above\", or \"Both A and B\".\n")
                    if (includeFormulasAndDiagrams) {
                        append("8. SCIENTIFIC & MATH NOTATION: Enclose all mathematical expressions in LaTeX (e.g. \$E = mc^2\$).\n")
                    }
                    if (enableQuantityCorrectionRule) {
                        append("9. TRUNCATION GUARD: Keep each explanation strictly within 2–3 concise sentences so all $countInt questions finish completely without token truncation.\n")
                    } else if (promptDensity == "Concise") {
                        append("9. EXPLANATION DEPTH: Keep explanations concise, focusing on the decisive reasoning.\n")
                    }
                    append("\n")

                    append("### 3. STATISTICAL BALANCE & ZERO-DISCREPANCY\n")
                    append("1. BALANCED KEY DISTRIBUTION: Distribute correct answers roughly evenly (~${100 / optionCount}% each across ${if (optionCount == 5) "A, B, C, D, E" else "A, B, C, D"}).\n")
                    append("2. ZERO CHATTER: Output starts immediately with \"Phase 1: Questions\". No introductory remarks or disclaimers.\n\n")

                    append("### 4. PLAIN-TEXT FORMATTING RULES\n")
                    append("1. ZERO INDENTATION: Every line starts flush-left.\n")
                    append("2. ANTI-LIST RULE: Do not use bullet points or markdown lists. Use Q1., (A), (B), (C), (D)${if (optionCount == 5) ", (E)" else ""}.\n\n")

                    append("### EXACT OUTPUT FORMAT TEMPLATE:\n\n")
                    append("Phase 1: Questions\n\n")
                    append("Q1. [Question stem]\n")
                    append("(A) [Option A]\n")
                    append("(B) [Option B]\n")
                    append("(C) [Option C]\n")
                    append("(D) [Option D]\n")
                    if (optionCount == 5) {
                        append("(E) [Option E]\n\n")
                    } else {
                        append("\n")
                    }
                    append("[Repeat through Q$countInt]\n\n")

                    if (promptPhasingStyle == "Reasoning-First") {
                        append("Phase 2: Explanations & Solution\n\n")
                        append("Q1.\n")
                        append("Core Concept & Reasoning: [Reasoning]\n")
                        append("Option (A) [Correct/Incorrect]: [Explanation]\n")
                        append("Option (B) [Correct/Incorrect]: [Explanation]\n")
                        append("Option (C) [Correct/Incorrect]: [Explanation]\n")
                        append("Option (D) [Correct/Incorrect]: [Explanation]\n")
                        if (optionCount == 5) {
                            append("Option (E) [Correct/Incorrect]: [Explanation]\n")
                        }
                        append("\nPhase 3: Answer Key\n\n")
                        append("Q1: (A)\n")
                        append("Q2: (C)\n\n")
                    } else {
                        append("Phase 2: Answer Key\n\n")
                        append("Q1: (A)\n")
                        append("Q2: (C)\n\n")
                        append("Phase 3: Explanations & Solution\n\n")
                        append("Q1.\n")
                        append("Core Concept & Reasoning: [Reasoning]\n")
                        if (explainAllFourOptions) {
                            append("Why Correct & Distractor Breakdown: [Option-by-option evaluation]\n\n")
                        } else {
                            append("Why Correct: [Reason for correct option]\n")
                            append("Distractor Breakdown: [Why other options fail]\n\n")
                        }
                    }

                    append("Execute the instructions now, starting directly with Phase 1: Questions.")
                }
            }
        }
    }

    val copyAction = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val activePrompt = if (isKnowledgeMode) knowledgeState.buildPrompt() else generatedPrompt
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(
            if (isKnowledgeMode) "AI Knowledge Prompt" else "AI Exam Prompt",
            activePrompt
        )
        clipboard.setPrimaryClip(clip)
        copied = true
        Toast.makeText(
            context,
            if (isKnowledgeMode) "Knowledge prompt copied to clipboard" else "Prompt copied to clipboard",
            Toast.LENGTH_SHORT
        ).show()
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.teal,
        unfocusedBorderColor = colors.border,
        focusedLabelColor = colors.teal,
        unfocusedLabelColor = colors.inkMuted,
        focusedTextColor = colors.inkBold,
        unfocusedTextColor = colors.inkBold,
        focusedContainerColor = colors.surfaceVariant.copy(alpha = 0.25f),
        unfocusedContainerColor = colors.surfaceVariant.copy(alpha = 0.15f)
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("prompt_lazy_column"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "top_spacer") { Spacer(modifier = Modifier.height(4.dp)) }

        // 1. Top Header Card (Full-width title so it never wraps/squeezes awkwardly)
        item(key = "header") {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(0.8.dp, colors.border)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = colors.amberLight,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = colors.amber,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "AI Prompt Builder",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Zero-drift prompts for Gemini, Claude, and ChatGPT",
                                fontSize = 12.sp,
                                color = colors.inkMuted,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                isKnowledgeMode = false
                                copied = false
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .heightIn(min = 48.dp)
                                .testTag("prompt_mode_question_set"),
                            shape = RoundedCornerShape(8.dp),
                            color = if (!isKnowledgeMode) colors.tealLight else colors.surfaceVariant.copy(alpha = 0.6f),
                            border = BorderStroke(
                                if (!isKnowledgeMode) 1.4.dp else 0.6.dp,
                                if (!isKnowledgeMode) colors.teal else colors.border
                            )
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                Text(
                                    text = "Question Set",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (!isKnowledgeMode) colors.teal else colors.inkBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "MCQs & Answer Keys",
                                    fontSize = 10.sp,
                                    color = if (!isKnowledgeMode) colors.tealDark else colors.inkMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                isKnowledgeMode = true
                                copied = false
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .heightIn(min = 48.dp)
                                .testTag("prompt_mode_knowledge_set"),
                            shape = RoundedCornerShape(8.dp),
                            color = if (isKnowledgeMode) colors.tealLight else colors.surfaceVariant.copy(alpha = 0.6f),
                            border = BorderStroke(
                                if (isKnowledgeMode) 1.4.dp else 0.6.dp,
                                if (isKnowledgeMode) colors.teal else colors.border
                            )
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                Text(
                                    text = "Knowledge Set",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isKnowledgeMode) colors.teal else colors.inkBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "One-Liners & Codex",
                                    fontSize = 10.sp,
                                    color = if (isKnowledgeMode) colors.tealDark else colors.inkMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = copyAction,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (copied) colors.green else colors.navyBanner,
                                contentColor = colors.navyBannerText
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                                .testTag("prompt_header_copy_button")
                        ) {
                            Icon(
                                if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (copied) "Copied!" else "Copy Prompt",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (isKnowledgeMode) {
                                    knowledgeState.reset()
                                } else {
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
                                copied = false
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.inkBold),
                            border = BorderStroke(0.8.dp, colors.border),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .testTag("prompt_reset_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset prompt parameters",
                                modifier = Modifier.size(16.dp),
                                tint = colors.inkMuted
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Reset",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.ink
                            )
                        }
                    }
                }
            }
        }

        if (isKnowledgeMode) {
            knowledgePromptBuilderItems(
                state = knowledgeState,
                colors = colors,
                fieldColors = fieldColors,
                context = context,
                haptic = haptic,
                copied = copied,
                onCopyPrompt = copyAction,
                onOpenKnowledgeStudio = onOpenKnowledgeStudio
            )
        } else {
        // 2. Card 1: Examination Parameters (Exam & Subject Details)
        item(key = "exam_params") {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(0.8.dp, colors.border)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "EXAMINATION PARAMETERS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkMuted,
                        letterSpacing = 0.5.sp
                    )

                    val examPresets = listOf(
                        "UPSC Civil Services",
                        "State PCS",
                        "SSC CGL / Banking",
                        "Railways / RRB",
                        "NEET / Biology",
                        "JEE / Physics",
                        "GATE / Engineering",
                        "USMLE / Clinical",
                        "BAR / Law Exam",
                        "SAT / ACT",
                        "School Board"
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        examPresets.forEach { preset ->
                            val isSelected = examType.equals(preset, ignoreCase = true)
                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    examType = preset
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) colors.teal else colors.surfaceVariant,
                                border = BorderStroke(
                                    if (isSelected) 1.2.dp else 0.6.dp,
                                    if (isSelected) colors.tealDark else colors.border
                                )
                            ) {
                                Text(
                                    text = preset,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) Color.White else colors.inkBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = examType,
                        onValueChange = { examType = it },
                        label = { Text("Exam Name / Target Agency", fontSize = 12.sp) },
                        placeholder = { Text("e.g. UPSC, State PCS, NEET, School Board", fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = fieldColors,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("prompt_exam_input")
                    )

                    val quickSubjects = listOf(
                        "General Science",
                        "Modern Indian History",
                        "Polity & Governance",
                        "Geography & Climate",
                        "Quantitative Aptitude",
                        "Clinical Medicine",
                        "Physics & Mechanics",
                        "English Comprehension"
                    )
                    Text(
                        text = "Quick Subject Ideas",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.inkMuted
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        quickSubjects.forEach { quickSub ->
                            val isSubSelected = subject.equals(quickSub, ignoreCase = true)
                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    subject = quickSub
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSubSelected) colors.teal else colors.surfaceVariant.copy(alpha = 0.7f),
                                border = BorderStroke(
                                    if (isSubSelected) 1.2.dp else 0.6.dp,
                                    if (isSubSelected) colors.tealDark else colors.border
                                )
                            ) {
                                Text(
                                    text = quickSub,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSubSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSubSelected) Color.White else colors.inkBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        label = { Text("Subject", fontSize = 12.sp) },
                        placeholder = { Text("e.g. General Science, Physics, History", fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = fieldColors,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("prompt_subject_input")
                    )

                    OutlinedTextField(
                        value = topic,
                        onValueChange = { topic = it },
                        label = { Text("Focus Topic (Optional)", fontSize = 12.sp) },
                        placeholder = { Text("e.g. Thermodynamics, Ecology & Climate Change", fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = fieldColors,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("prompt_topic_input")
                    )
                }
            }
        }

        // 3. Card 2: Question Volume, Difficulty, Format & Option Count
        item(key = "question_volume_format") {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(0.8.dp, colors.border)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "QUESTION VOLUME & PATTERN",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkMuted,
                        letterSpacing = 0.5.sp
                    )

                    // Question Count Selector & Chips
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val presetCounts = listOf(5, 10, 25, 50, 100, 180)
                        val isPreset = countInt in presetCounts

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Question Count",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.inkBold
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (isPreset) "Preset" else "Custom",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isPreset) colors.teal else colors.muted
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = colors.tealLight,
                                    border = BorderStroke(0.6.dp, colors.teal.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = "$countInt MCQs",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.teal,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            presetCounts.forEach { cnt ->
                                val isSelected = countInt == cnt
                                Surface(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        questionCount = cnt.toFloat()
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) colors.teal else colors.surfaceVariant,
                                    border = BorderStroke(0.6.dp, if (isSelected) colors.teal else colors.border)
                                ) {
                                    Text(
                                        text = "$cnt Qs",
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else colors.inkBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        Slider(
                            value = questionCount,
                            onValueChange = { newVal ->
                                val snapped = kotlin.math.round(newVal / 5f) * 5f
                                questionCount = snapped.coerceIn(5f, 200f)
                            },
                            valueRange = 5f..200f,
                            colors = SliderDefaults.colors(
                                thumbColor = colors.teal,
                                activeTrackColor = colors.teal,
                                inactiveTrackColor = colors.border
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    HorizontalDivider(color = colors.border.copy(alpha = 0.5f), thickness = 0.6.dp)

                    // Difficulty Level Selector
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Difficulty Level",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.inkBold
                            )
                            Text(
                                text = difficulty,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.teal
                            )
                        }
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("Easy", "Moderate", "Hard", "Extreme", "Mixed").forEach { diff ->
                                val label = when (diff) {
                                    "Easy" -> "Easy"
                                    "Moderate" -> "Moderate"
                                    "Hard" -> "Hard"
                                    "Extreme" -> "Extreme (Olympiad)"
                                    else -> "Mixed Difficulty"
                                }
                                val isSelected = difficulty == diff
                                Surface(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        difficulty = diff
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) colors.teal else colors.surfaceVariant,
                                    border = BorderStroke(0.6.dp, if (isSelected) colors.teal else colors.border)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSelected) Color.White else colors.inkBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = colors.border.copy(alpha = 0.5f), thickness = 0.6.dp)

                    // Question Format & Pattern Selector
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Question Format & Pattern",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.inkBold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = questionType,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.teal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "Mixed Formats",
                                "Conceptual & Theory",
                                "Numerical & Calculation",
                                "Assertion-Reason & Statement-Based",
                                "Case Study & Passage-Based"
                            ).forEach { qType ->
                                val isSelected = questionType == qType
                                Surface(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        questionType = qType
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) colors.teal else colors.surfaceVariant,
                                    border = BorderStroke(0.6.dp, if (isSelected) colors.teal else colors.border)
                                ) {
                                    Text(
                                        text = qType,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSelected) Color.White else colors.inkBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = colors.border.copy(alpha = 0.5f), thickness = 0.6.dp)

                    // Option Count per Question (4 vs 5 Options - balanced side-by-side cards)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Option Count per Question",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.inkBold
                            )
                            Text(
                                text = if (optionCount == 5) "5 Options (A–E)" else "4 Options (A–D)",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.teal
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Max),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    optionCount = 4
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                shape = RoundedCornerShape(8.dp),
                                color = if (optionCount == 4) colors.tealLight else colors.surfaceVariant.copy(alpha = 0.6f),
                                border = BorderStroke(
                                    if (optionCount == 4) 1.5.dp else 0.6.dp,
                                    if (optionCount == 4) colors.teal else colors.border
                                )
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "4 Options (A–D)",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (optionCount == 4) colors.teal else colors.inkBold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "SSC, Railways, JEE, NEET, UPSC",
                                        fontSize = 10.sp,
                                        lineHeight = 13.sp,
                                        color = if (optionCount == 4) colors.tealDark else colors.inkMuted
                                    )
                                }
                            }

                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    optionCount = 5
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                shape = RoundedCornerShape(8.dp),
                                color = if (optionCount == 5) colors.tealLight else colors.surfaceVariant.copy(alpha = 0.6f),
                                border = BorderStroke(
                                    if (optionCount == 5) 1.5.dp else 0.6.dp,
                                    if (optionCount == 5) colors.teal else colors.border
                                )
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "5 Options (A–E)",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (optionCount == 5) colors.teal else colors.inkBold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Banking, IBPS, State PSC",
                                        fontSize = 10.sp,
                                        lineHeight = 13.sp,
                                        color = if (optionCount == 5) colors.tealDark else colors.inkMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Card 3: Language Configuration
        item(key = "language_config") {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(0.8.dp, colors.border)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "LANGUAGE SETTINGS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkMuted,
                        letterSpacing = 0.5.sp
                    )

                    // Question Language
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Question Language",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold
                            )
                            Text(
                                text = "Any language supported",
                                fontSize = 10.5.sp,
                                color = colors.teal,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "Type any language or select from presets below (universal (A)–(${if (optionCount == 5) "E" else "D"}) labels preserved)",
                            fontSize = 10.5.sp,
                            color = colors.muted,
                            lineHeight = 14.sp
                        )

                        OutlinedTextField(
                            value = questionLanguage,
                            onValueChange = { questionLanguage = it },
                            placeholder = { Text("e.g. English, Hindi, Tamil, Bengali, French...", fontSize = 12.sp, color = colors.muted) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = colors.inkBold),
                            shape = RoundedCornerShape(8.dp),
                            colors = fieldColors
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            com.example.util.PaperLanguageDetector.PRESET_LANGUAGES.forEach { lang ->
                                val isSelected = questionLanguage.trim().equals(lang, ignoreCase = true)
                                Surface(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        questionLanguage = lang
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) colors.teal else colors.surfaceVariant,
                                    border = BorderStroke(0.6.dp, if (isSelected) colors.teal else colors.border)
                                ) {
                                    Text(
                                        text = lang,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else colors.inkBold,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = colors.border.copy(alpha = 0.5f), thickness = 0.6.dp)

                    // Explanation Language (cleanly separated from active status so zero text collision)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val activeQLang = questionLanguage.trim().ifBlank { "English" }
                        val bilingualPresetName = if (activeQLang.equals("English", ignoreCase = true)) {
                            "Bilingual (Eng & Hin)"
                        } else {
                            "Bilingual (Eng & $activeQLang)"
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Explanation Language",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold
                            )
                            Text(
                                text = "Active: $effectiveExplanationLanguage",
                                fontSize = 11.sp,
                                color = colors.teal,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val explanationOptions = listOf(
                                "Same as Question" to "Same as Question",
                                "English" to "English",
                                "Hindi" to "Hindi",
                                "Bilingual" to bilingualPresetName,
                                "Custom" to "Custom..."
                            )

                            explanationOptions.forEach { (key, displayLabel) ->
                                val isSelected = explanationChoice == key
                                Surface(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        explanationChoice = key
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) colors.teal else colors.surfaceVariant,
                                    border = BorderStroke(0.6.dp, if (isSelected) colors.teal else colors.border)
                                ) {
                                    Text(
                                        text = displayLabel,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else colors.inkBold,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        if (explanationChoice == "Custom") {
                            OutlinedTextField(
                                value = customExplanationLanguage,
                                onValueChange = { customExplanationLanguage = it },
                                placeholder = { Text("Type custom explanation language (e.g. Marathi, Sanskrit, French)...", fontSize = 11.5.sp, color = colors.muted) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.inkBold),
                                shape = RoundedCornerShape(8.dp),
                                colors = fieldColors
                            )
                        }
                    }
                }
            }
        }

        // 5. Card 4: Output Format, Explanation Rules, Phasing & Guard
        item(key = "output_format_reasoning") {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(0.8.dp, colors.border)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "OUTPUT FORMAT & REASONING",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkMuted,
                        letterSpacing = 0.5.sp
                    )

                    // Target Output Format Box
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .border(0.8.dp, colors.border, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Target Output Format",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = colors.teal.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = targetOutputFormat,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.teal,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("Strict JSON", "Standard Text (Aiken)", "Markdown Table").forEach { fmt ->
                                val isSelected = targetOutputFormat == fmt
                                Surface(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        targetOutputFormat = fmt
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) colors.teal else colors.surface,
                                    border = BorderStroke(
                                        if (isSelected) 1.2.dp else 0.6.dp,
                                        if (isSelected) colors.teal else colors.border
                                    )
                                ) {
                                    Text(
                                        text = fmt,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else colors.inkBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        // Format Specification & Sample Viewer
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colors.surface.copy(alpha = 0.7f),
                            border = BorderStroke(0.6.dp, colors.border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = when (targetOutputFormat) {
                                            "Strict JSON" -> "JSON Schema ($optionCount-Option)"
                                            "Markdown Table" -> "Markdown Table ($optionCount-Option)"
                                            else -> "Structured Text ($optionCount-Option)"
                                        },
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.teal,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    val sampleText = when (targetOutputFormat) {
                                        "Strict JSON" -> if (optionCount == 5) {
                                            """[
  {
    "num": 1,
    "question": "Which gas is most abundant in Earth's atmosphere?",
    "options": {
      "a": "Oxygen",
      "b": "Nitrogen",
      "c": "Carbon Dioxide",
      "d": "Argon",
      "e": "Neon"
    },
    "correctAnswer": "B",
    "explanation": "Nitrogen accounts for approximately 78% of Earth's atmosphere by volume."
  }
]"""
                                        } else {
                                            """[
  {
    "num": 1,
    "question": "Which gas is most abundant in Earth's atmosphere?",
    "options": {
      "a": "Oxygen",
      "b": "Nitrogen",
      "c": "Carbon Dioxide",
      "d": "Argon"
    },
    "correctAnswer": "B",
    "explanation": "Nitrogen accounts for approximately 78% of Earth's atmosphere by volume."
  }
]"""
                                        }
                                        "Markdown Table" -> if (optionCount == 5) {
                                            """| # | Question | Option A | Option B | Option C | Option D | Option E | Answer | Explanation |
|---|---|---|---|---|---|---|:---:|---|
| 1 | Which gas is most abundant in Earth's atmosphere? | Oxygen | Nitrogen | Carbon Dioxide | Argon | Neon | B | Nitrogen accounts for approximately 78% of Earth's atmosphere by volume. |"""
                                        } else {
                                            """| # | Question | Option A | Option B | Option C | Option D | Answer | Explanation |
|---|---|---|---|---|---|:---:|---|
| 1 | Which gas is most abundant in Earth's atmosphere? | Oxygen | Nitrogen | Carbon Dioxide | Argon | B | Nitrogen accounts for approximately 78% of Earth's atmosphere by volume. |"""
                                        }
                                        else -> if (optionCount == 5) {
                                            """Phase 1: Questions

Q1. Which gas is most abundant in Earth's atmosphere?
(A) Oxygen
(B) Nitrogen
(C) Carbon Dioxide
(D) Argon
(E) Neon

Phase 2: Answer Key
Q1: (B)

Phase 3: Explanations & Solution
Q1. Nitrogen accounts for approximately 78% of Earth's atmosphere by volume."""
                                        } else {
                                            """Phase 1: Questions

Q1. Which gas is most abundant in Earth's atmosphere?
(A) Oxygen
(B) Nitrogen
(C) Carbon Dioxide
(D) Argon

Phase 2: Answer Key
Q1: (B)

Phase 3: Explanations & Solution
Q1. Nitrogen accounts for approximately 78% of Earth's atmosphere by volume."""
                                        }
                                    }

                                    Surface(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("Sample Format", sampleText)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "Copied sample $targetOutputFormat format", Toast.LENGTH_SHORT).show()
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        color = colors.surfaceVariant,
                                        border = BorderStroke(0.6.dp, colors.teal.copy(alpha = 0.5f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = null,
                                                tint = colors.teal,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Copy Sample",
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.teal,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = when (targetOutputFormat) {
                                        "Strict JSON" -> "JSON array with num, question, options (${if (optionCount == 5) "a, b, c, d, e" else "a, b, c, d"}), correctAnswer, and explanation. Strictly $optionCount options."
                                        "Markdown Table" -> "${if (optionCount == 5) "9" else "8"}-column table with strictly $optionCount options (${if (optionCount == 5) "Option A through E" else "Option A through D"})."
                                        else -> "Standard Aiken/Structured format with Q1.. (A)–(${if (optionCount == 5) "E" else "D"}), Answer Key, and Detailed Explanations."
                                    },
                                    fontSize = 10.5.sp,
                                    color = colors.inkMuted,
                                    lineHeight = 14.5.sp
                                )
                            }
                        }

                        // Prompt Style / Length Selector
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Prompt Style / Length",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.inkBold,
                                modifier = Modifier.weight(1f)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("Concise", "Comprehensive").forEach { density ->
                                    val isSelected = promptDensity == density
                                    Surface(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            promptDensity = density
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isSelected) colors.teal else colors.surface,
                                        border = BorderStroke(0.6.dp, if (isSelected) colors.teal else colors.border)
                                    ) {
                                        Text(
                                            text = if (density == "Concise") "Concise (Fast)" else "Detailed",
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color.White else colors.inkBold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Option Explanations Checkbox
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { explainAllFourOptions = !explainAllFourOptions }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = explainAllFourOptions,
                            onCheckedChange = { explainAllFourOptions = it },
                            colors = CheckboxDefaults.colors(checkedColor = colors.teal)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Explain All $optionCount Options Individually",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.inkBold
                            )
                            Text(
                                text = "Explains why correct option is right and why each distractor is wrong",
                                fontSize = 10.5.sp,
                                color = colors.muted,
                                lineHeight = 14.sp
                            )
                        }
                    }

                    // Formulas & Diagrams Checkbox
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { includeFormulasAndDiagrams = !includeFormulasAndDiagrams }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = includeFormulasAndDiagrams,
                            onCheckedChange = { includeFormulasAndDiagrams = it },
                            colors = CheckboxDefaults.colors(checkedColor = colors.teal)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Include Formulas & Diagram References",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.inkBold
                            )
                            Text(
                                text = "Formats equations and figures so the app renders them visually",
                                fontSize = 10.5.sp,
                                color = colors.muted,
                                lineHeight = 14.sp
                            )
                        }
                    }

                    // AI Output Phasing (Chain-of-Thought)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "AI Output Phasing (Chain-of-Thought)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = if (promptPhasingStyle == "Reasoning-First") "Derive First" else "Standard",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (promptPhasingStyle == "Reasoning-First") colors.green else colors.teal
                            )
                        }

                        if (targetOutputFormat != "Standard Text (Aiken)") {
                            Text(
                                text = "Phase ordering applies when Standard Text (Aiken) format is selected.",
                                fontSize = 10.sp,
                                color = colors.muted
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Max),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val isReasoning = promptPhasingStyle == "Reasoning-First"
                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    promptPhasingStyle = "Reasoning-First"
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                shape = RoundedCornerShape(8.dp),
                                color = if (isReasoning) colors.greenLight else colors.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(
                                    if (isReasoning) 1.5.dp else 0.6.dp,
                                    if (isReasoning) colors.green else colors.border
                                )
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = if (isReasoning) colors.green else colors.muted,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = "Reasoning-First",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isReasoning) colors.green else colors.inkBold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Q → Explanations → Key\nLLM derives answers first to prevent wrong keys.",
                                        fontSize = 10.sp,
                                        lineHeight = 13.5.sp,
                                        color = if (isReasoning) colors.green else colors.muted
                                    )
                                }
                            }

                            val isStandard = promptPhasingStyle == "Standard"
                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    promptPhasingStyle = "Standard"
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                shape = RoundedCornerShape(8.dp),
                                color = if (isStandard) colors.tealLight else colors.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(
                                    if (isStandard) 1.5.dp else 0.6.dp,
                                    if (isStandard) colors.teal else colors.border
                                )
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.EditNote,
                                            contentDescription = null,
                                            tint = if (isStandard) colors.teal else colors.muted,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = "Standard Key-First",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isStandard) colors.teal else colors.inkBold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Q → Key → Explanations\nClassic output sequence for standard workflows.",
                                        fontSize = 10.sp,
                                        lineHeight = 13.5.sp,
                                        color = if (isStandard) colors.tealDark else colors.muted
                                    )
                                }
                            }
                        }
                    }

                    // Format & Quantity Truncation Guard Toggle
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (enableQuantityCorrectionRule) colors.tealLight else colors.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(0.8.dp, if (enableQuantityCorrectionRule) colors.teal else colors.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { enableQuantityCorrectionRule = !enableQuantityCorrectionRule }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = null,
                                        tint = if (enableQuantityCorrectionRule) colors.teal else colors.muted,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Format & Quantity Truncation Guard",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (enableQuantityCorrectionRule) colors.teal else colors.inkBold
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = if (enableQuantityCorrectionRule) {
                                        "ACTIVE: Enforces a strict 2–3 sentence explanation budget to prevent token cutoffs for 20–25+ question sets."
                                    } else {
                                        "DISABLED (Default): Unrestricted full stream. Generates all $countInt questions continuously without artificial limits."
                                    },
                                    fontSize = 10.5.sp,
                                    lineHeight = 14.sp,
                                    color = if (enableQuantityCorrectionRule) colors.tealDark else colors.muted
                                )
                            }

                            Switch(
                                checked = enableQuantityCorrectionRule,
                                onCheckedChange = { enableQuantityCorrectionRule = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = colors.teal
                                )
                            )
                        }
                    }
                }
            }
        }

        // 6. Card 5: Live Generated Prompt Card
        item(key = "constructed_prompt") {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(0.8.dp, colors.border)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CONSTRUCTED AI PROMPT",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkMuted,
                            letterSpacing = 0.5.sp
                        )

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colors.greenLight,
                            border = BorderStroke(0.5.dp, colors.green.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = if (enableQuantityCorrectionRule) "Guard Active" else "Full Stream (Default)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.green,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colors.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(0.6.dp, colors.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = generatedPrompt,
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = colors.inkBold,
                            lineHeight = 17.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = copyAction,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("prompt_footer_copy_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (copied) colors.green else colors.navyBanner,
                                contentColor = colors.navyBannerText
                            )
                        ) {
                            Icon(
                                if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (copied) "Prompt Copied!" else "Copy AI Prompt",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        OutlinedButton(
                            onClick = onNavigateToCreator,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("prompt_paste_response_button"),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = colors.teal,
                                containerColor = colors.surface
                            ),
                            border = BorderStroke(1.dp, colors.teal.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.EditNote,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = colors.teal
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Paste Response",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        item(key = "bottom_spacer") {
            Spacer(
                modifier = Modifier
                    .navigationBarsPadding()
                    .height(24.dp)
            )
        }
        }
    }
}
