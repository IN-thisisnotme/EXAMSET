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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import com.example.ui.MainViewModel
import com.example.ui.theme.ExamTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PromptGeneratorTab(
    viewModel: MainViewModel,
    onNavigateToCreator: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var examType by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }
    var questionCount by remember { mutableFloatStateOf(20f) }
    var difficulty by remember { mutableStateOf("Moderate") }
    var questionType by remember { mutableStateOf("Mixed Formats") }
    var questionLanguage by remember { mutableStateOf("English") }
    var explanationChoice by remember { mutableStateOf("Same as Question") }
    var customExplanationLanguage by remember { mutableStateOf("") }
    var explainAllFourOptions by remember { mutableStateOf(true) }
    var includeFormulasAndDiagrams by remember { mutableStateOf(true) }
    var promptPhasingStyle by remember { mutableStateOf("Standard") }
    var targetOutputFormat by remember { mutableStateOf("Strict JSON") } // "Strict JSON", "Standard Text (Aiken)", "Markdown Table"
    var promptDensity by remember { mutableStateOf("Concise") } // "Concise", "Comprehensive"

    // Format & Quantity Correction Rule (Disabled by default as requested: Unrestricted Full Stream)
    var enableQuantityCorrectionRule by remember { mutableStateOf(false) }

    var copied by remember { mutableStateOf(false) }

    val countInt = questionCount.toInt()

    val effectiveExplanationLanguage by remember(questionLanguage, explanationChoice, customExplanationLanguage) {
        derivedStateOf {
            val q = questionLanguage.trim().ifBlank { "English" }
            when (explanationChoice) {
                "Same as Question" -> "$q (same language as the questions)"
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
        targetOutputFormat, promptDensity
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
                                "d": "Option D text"
                                },
                                "correctAnswer": "A",
                                "explanation": "Explanation text here"
                            }
                        ]

                        ### 3. QUESTION QUALITY AND ANSWER RULES
                        1. Generate exactly $countInt questions relevant to $targetSubject and appropriate for $targetExam.
                        2. Each question must contain EXACTLY four options, with keys "a", "b", "c", and "d". Never include an option "e" or any additional options.
                        3. Each option must be plausible, relevant, and clearly distinguishable from the others. Avoid overlapping options, grammatical clues, and making the correct option noticeably longer without a valid reason.
                        4. The "correctAnswer" value must be exactly one uppercase letter: "A", "B", "C", or "D". It must identify the option that is factually and logically correct.
                        5. Distribute correct answers approximately evenly across A, B, C, and D when feasible. Never compromise correctness to balance the answer key.

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

                    if (explainAllFourOptions) {
                        append(
                            """

                            OPTION-BY-OPTION EXPLANATION
                            Explain options A, B, C, and D individually and in that order. For each option, explicitly state whether it is correct or incorrect and give a specific reason. Explain why the correct option is valid and why each distractor fails. Do not repeat the same reasoning in different words.
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

                    append("\n\n")

                    if (includeFormulasAndDiagrams) {
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
                        - Every options object contains exactly the keys "a", "b", "c", and "d".
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
                    append("Generate exactly $countInt multiple-choice questions (MCQs) for $targetSubject at $difficulty difficulty in $qLang.\n\n")
                    append("OUTPUT FORMAT: MARKDOWN TABLE\n")
                    append("Format the entire question set as a clean, standardized Markdown table with the following column headers:\n")
                    append("| # | Question | Option A | Option B | Option C | Option D | Answer | Explanation |\n")
                    append("|---|---|---|---|---|---|:---:|---|\n\n")
                    append("Rules:\n")
                    append("1. STRICT 4-OPTION CONSTRAINT: Each question must have EXACTLY 4 options (Option A, Option B, Option C, Option D). Never include Option E or more than 4 options.\n")
                    append("2. Put each question in a single table row.\n")
                    append("3. In the 'Answer' column, write strictly 'A', 'B', 'C', or 'D'.\n")
                    append("4. In the 'Explanation' column, provide a concise, step-by-step rationale (in $effectiveExplanationLanguage).\n")
                    append("5. Balanced key distribution across A, B, C, and D.\n")
                    append("6. Do not include extra commentary or greeting text before or after the table.")
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
                    append("- Structural Markers: Regardless of the language used, keep question labels as \"Q1.\", \"Q2.\" and option keys strictly as \"(A)\", \"(B)\", \"(C)\", \"(D)\" for universal OMR and parser compatibility.\n\n")

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
                    append("3. STRICT 4-OPTION CONSTRAINT: Every question must contain EXACTLY 4 options labeled (A), (B), (C), and (D). Never include a fifth option (E) or more than 4 options under any circumstances.\n")
                    append("4. DISTRACTOR HOMOGENEITY: All 4 options must be mutually exclusive and grammatically parallel.\n")
                    append("5. LENGTH CONTROL: The correct option must NOT be the longest option by default.\n")
                    append("6. ORDERING: If options contain numerical values or dates, order them strictly from lowest to highest.\n")
                    append("7. PROHIBITED PHRASES: Never use \"All of the above\", \"None of the above\", or \"Both A and B\".\n")
                    if (includeFormulasAndDiagrams) {
                        append("8. SCIENTIFIC & MATH NOTATION: Enclose all mathematical expressions in LaTeX (e.g. \$E = mc^2\$).\n")
                    }
                    append("\n")

                    append("### 3. STATISTICAL BALANCE & ZERO-DISCREPANCY\n")
                    append("1. BALANCED KEY DISTRIBUTION: Distribute correct answers roughly evenly (~25% each across A, B, C, D).\n")
                    append("2. ZERO CHATTER: Output starts immediately with \"Phase 1: Questions\". No introductory remarks or disclaimers.\n\n")

                    append("### 4. PLAIN-TEXT FORMATTING RULES\n")
                    append("1. ZERO INDENTATION: Every line starts flush-left.\n")
                    append("2. ANTI-LIST RULE: Do not use bullet points or markdown lists. Use Q1., (A), (B), (C), (D).\n\n")

                    append("### EXACT OUTPUT FORMAT TEMPLATE:\n\n")
                    append("Phase 1: Questions\n\n")
                    append("Q1. [Question stem]\n")
                    append("(A) [Option A]\n")
                    append("(B) [Option B]\n")
                    append("(C) [Option C]\n")
                    append("(D) [Option D]\n\n")
                    append("[Repeat through Q$countInt]\n\n")

                    if (promptPhasingStyle == "Reasoning-First") {
                        append("Phase 2: Explanations & Solution\n\n")
                        append("Q1.\n")
                        append("Core Concept & Reasoning: [Reasoning]\n")
                        append("Option (A) [Correct/Incorrect]: [Explanation]\n")
                        append("Option (B) [Correct/Incorrect]: [Explanation]\n")
                        append("Option (C) [Correct/Incorrect]: [Explanation]\n")
                        append("Option (D) [Correct/Incorrect]: [Explanation]\n\n")
                        append("Phase 3: Answer Key\n\n")
                        append("Q1: (A)\n")
                        append("Q2: (C)\n\n")
                    } else {
                        append("Phase 2: Answer Key\n\n")
                        append("Q1: (A)\n")
                        append("Q2: (C)\n\n")
                        append("Phase 3: Explanations & Solution\n\n")
                        append("Q1.\n")
                        append("Core Concept & Reasoning: [Reasoning]\n")
                        append("Why Correct: [Reason for correct option]\n")
                        append("Distractor Breakdown: [Why other options fail]\n\n")
                    }

                    append("Execute the instructions now, starting directly with Phase 1: Questions.")
                }
            }
        }
    }

    val copyAction = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("AI Exam Prompt", generatedPrompt)
        clipboard.setPrimaryClip(clip)
        copied = true
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // Top Header
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(0.8.dp, colors.border)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = colors.amberLight,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = colors.amber,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "AI Prompt Builder",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.inkBold
                                )
                                Text(
                                    text = "Zero-drift prompts for Gemini, Claude, and ChatGPT",
                                    fontSize = 11.5.sp,
                                    color = colors.inkMuted
                                )
                            }
                        }

                        Button(
                            onClick = copyAction,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (copied) colors.green else colors.navyBanner,
                                contentColor = colors.navyBannerText
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier.heightIn(min = 38.dp)
                        ) {
                            Icon(
                                if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                if (copied) "Copied!" else "Copy Prompt",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        // Configuration Form
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(0.8.dp, colors.border)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "EXAMINATION PARAMETERS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkMuted,
                        letterSpacing = 0.5.sp
                    )

                    // Extended Exam Preset Catalog
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
                            val isSelected = examType.equals(preset, ignoreCase = true) || (examType.isNotBlank() && examType.startsWith(preset.take(6)))
                            Surface(
                                modifier = Modifier.clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    examType = preset
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) colors.teal else colors.surfaceVariant,
                                border = BorderStroke(if (isSelected) 1.5.dp else 0.5.dp, if (isSelected) colors.tealDark else colors.border)
                            ) {
                                Text(
                                    text = preset,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) Color.White else colors.inkBold,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = examType,
                        onValueChange = { examType = it },
                        label = { Text("Exam Name / Target Agency", fontSize = 11.sp) },
                        placeholder = { Text("e.g. UPSC, State PCS, NEET, School Board", fontSize = 11.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Quick-Pick Subject Suggestions
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
                        fontSize = 10.5.sp,
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
                                modifier = Modifier.clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    subject = quickSub
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSubSelected) colors.teal else colors.surfaceVariant.copy(alpha = 0.6f),
                                border = BorderStroke(if (isSubSelected) 1.5.dp else 0.5.dp, if (isSubSelected) colors.tealLight else colors.border)
                            ) {
                                Text(
                                    text = quickSub,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSubSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSubSelected) Color.White else colors.inkBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        label = { Text("Subject", fontSize = 11.sp) },
                        placeholder = { Text("e.g. General Science, Physics, History", fontSize = 11.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = topic,
                        onValueChange = { topic = it },
                        label = { Text("Focus Topic (Optional)", fontSize = 11.sp) },
                        placeholder = { Text("e.g. Thermodynamics, Ecology & Climate Change", fontSize = 11.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Question Count Selector & Chips
                    Column {
                        val presetCounts = listOf(5, 10, 25, 50, 100, 180)
                        val isPreset = countInt in presetCounts

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Question Count", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = colors.ink)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = if (isPreset) "Preset" else "Custom",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isPreset) colors.teal else colors.muted
                                )
                                Text("$countInt MCQs", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = colors.teal)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // Preset Count Chips (5, 10, 25, 50, 100, 180) - synchronized with max 200
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            presetCounts.forEach { cnt ->
                                val isSelected = countInt == cnt
                                Surface(
                                    onClick = { questionCount = cnt.toFloat() },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) colors.teal else colors.surfaceVariant,
                                    border = BorderStroke(0.6.dp, if (isSelected) colors.teal else colors.border)
                                ) {
                                    Text(
                                        text = "$cnt Qs",
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else colors.inkBold,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Slider snapping to multiples of 5 with 39 steps between 5 and 200 (5, 10, 15, ..., 200)
                        Slider(
                            value = questionCount,
                            onValueChange = { newVal ->
                                val snapped = kotlin.math.round(newVal / 5f) * 5f
                                questionCount = snapped.coerceIn(5f, 200f)
                            },
                            valueRange = 5f..200f,
                            steps = 38
                        )
                    }

                    // Language Configuration (Supports ANY Question Language & Flexible Explanations)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .border(0.6.dp, colors.border, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Question Language (Can be ANY language)
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Question Language",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.inkBold
                                )
                                Text(
                                    "Any language supported",
                                    fontSize = 10.sp,
                                    color = colors.teal,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                "Type any language or select from presets below (universal (A)-(D) option labels are preserved)",
                                fontSize = 10.sp,
                                color = colors.muted
                            )
                            Spacer(modifier = Modifier.height(5.dp))
                            OutlinedTextField(
                                value = questionLanguage,
                                onValueChange = { questionLanguage = it },
                                placeholder = { Text("e.g. English, Hindi, Tamil, Bengali, French, German...", fontSize = 11.5.sp, color = colors.muted) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.inkBold),
                                shape = RoundedCornerShape(6.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.teal,
                                    unfocusedBorderColor = colors.border,
                                    focusedContainerColor = colors.surface,
                                    unfocusedContainerColor = colors.surface
                                )
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Quick preset chips for rapid selection
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(
                                    "English", "Hindi", "Bengali", "Marathi", "Telugu",
                                    "Tamil", "Gujarati", "Urdu", "Kannada", "Malayalam",
                                    "Odia", "Punjabi", "Sanskrit", "Spanish", "French", "German"
                                ).forEach { lang ->
                                    val isSelected = questionLanguage.trim().equals(lang, ignoreCase = true)
                                    Surface(
                                        onClick = { questionLanguage = lang },
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isSelected) colors.teal else colors.surface,
                                        border = BorderStroke(0.6.dp, if (isSelected) colors.teal else colors.border)
                                    ) {
                                        Text(
                                            text = lang,
                                            fontSize = 10.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) Color.White else colors.ink,
                                            modifier = Modifier.padding(horizontal = 7.5.dp, vertical = 3.5.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Explanation Language
                        Column {
                            val activeQLang = questionLanguage.trim().ifBlank { "English" }
                            val bilingualPresetName = if (activeQLang.equals("English", ignoreCase = true)) {
                                "Bilingual (Eng & Hin)"
                            } else {
                                "Bilingual (Eng & $activeQLang)"
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Explanation Language",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.inkBold
                                )
                                Text(
                                    "Current: $effectiveExplanationLanguage",
                                    fontSize = 10.sp,
                                    color = colors.muted,
                                    maxLines = 1
                                )
                            }

                            Spacer(modifier = Modifier.height(5.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                                        onClick = { explanationChoice = key },
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isSelected) colors.teal else colors.surface,
                                        border = BorderStroke(0.6.dp, if (isSelected) colors.teal else colors.border)
                                    ) {
                                        Text(
                                            text = displayLabel,
                                            fontSize = 10.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) Color.White else colors.ink,
                                            modifier = Modifier.padding(horizontal = 7.5.dp, vertical = 3.5.dp)
                                        )
                                    }
                                }
                            }

                            if (explanationChoice == "Custom") {
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = customExplanationLanguage,
                                    onValueChange = { customExplanationLanguage = it },
                                    placeholder = { Text("Type custom explanation language (e.g. Marathi, Sanskrit, French)...", fontSize = 11.5.sp, color = colors.muted) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.inkBold),
                                    shape = RoundedCornerShape(6.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = colors.teal,
                                        unfocusedBorderColor = colors.border,
                                        focusedContainerColor = colors.surface,
                                        unfocusedContainerColor = colors.surface
                                    )
                                )
                            }
                        }
                    }
                    // TARGET AI OUTPUT FORMAT SELECTOR (JSON vs Standard Text vs Markdown Table)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .border(0.8.dp, colors.border, RoundedCornerShape(8.dp))
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
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = colors.teal.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = targetOutputFormat,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.tealDark,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                                    border = BorderStroke(if (isSelected) 1.2.dp else 0.6.dp, if (isSelected) colors.teal else colors.border)
                                ) {
                                    Text(
                                        text = fmt,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else colors.ink,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }

                        // Format Specification & Sample Viewer
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colors.surfaceVariant.copy(alpha = 0.5f),
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
                                            "Strict JSON" -> "JSON Schema (Strict 4-Option)"
                                            "Markdown Table" -> "Markdown Table (Strict 4-Option)"
                                            else -> "Structured Text (Strict 4-Option)"
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.tealDark,
                                        modifier = Modifier.weight(1f, fill = false),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    val sampleText = when (targetOutputFormat) {
                                        "Strict JSON" -> """[
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
                                        "Markdown Table" -> """| # | Question | Option A | Option B | Option C | Option D | Answer | Explanation |
|---|---|---|---|---|---|:---:|---|
| 1 | Which gas is most abundant in Earth's atmosphere? | Oxygen | Nitrogen | Carbon Dioxide | Argon | B | Nitrogen accounts for approximately 78% of Earth's atmosphere by volume. |"""
                                        else -> """Phase 1: Questions

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

                                    Surface(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("Sample Format", sampleText)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "Copied sample $targetOutputFormat format", Toast.LENGTH_SHORT).show()
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        color = colors.surface,
                                        border = BorderStroke(0.6.dp, colors.teal.copy(alpha = 0.5f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = null,
                                                tint = colors.tealDark,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Copy Sample Format",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.tealDark,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = when (targetOutputFormat) {
                                        "Strict JSON" -> "JSON array with num, question, options (a, b, c, d), correctAnswer, and explanation. Always strictly 4 options (no option E)."
                                        "Markdown Table" -> "8-column table with strictly 4 options (Option A, Option B, Option C, Option D). Never includes Option E."
                                        else -> "Standard Aiken/Structured format with Q1.. (A)-(D), Answer Key, and Detailed Explanations. Strictly 4 options."
                                    },
                                    fontSize = 10.sp,
                                    color = colors.inkMuted,
                                    lineHeight = 13.5.sp
                                )
                            }
                        }

                        // Prompt Density / Conciseness Selector
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Prompt Style / Length",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.inkMuted
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("Concise", "Comprehensive").forEach { density ->
                                    val isSelected = promptDensity == density
                                    Surface(
                                        onClick = { promptDensity = density },
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (isSelected) colors.tealDark else colors.surface,
                                        border = BorderStroke(0.6.dp, if (isSelected) colors.tealDark else colors.border)
                                    ) {
                                        Text(
                                            text = if (density == "Concise") "Concise (Fast)" else "Detailed",
                                            fontSize = 9.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color.White else colors.inkMuted,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 1. Difficulty Level Selector
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Difficulty Level", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = colors.ink)
                            Text(difficulty, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.teal)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("Easy", "Moderate", "Hard", "Extreme", "Mixed").forEach { diff ->
                                val label = when(diff) {
                                    "Easy" -> "Easy"
                                    "Moderate" -> "Moderate"
                                    "Hard" -> "Hard"
                                    "Extreme" -> "Extreme (Olympiad)"
                                    else -> "Mixed Difficulty"
                                }
                                val isSelected = difficulty == diff
                                Surface(
                                    modifier = Modifier.clickable { difficulty = diff },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) colors.teal else colors.surfaceVariant,
                                    border = BorderStroke(0.5.dp, if (isSelected) colors.teal else colors.border)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSelected) Color.White else colors.inkBold,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 2. Question Types & Formats Selector
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Question Format & Pattern", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = colors.ink)
                            Text(questionType, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.teal)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                                    modifier = Modifier.clickable { questionType = qType },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) colors.teal else colors.surfaceVariant,
                                    border = BorderStroke(0.5.dp, if (isSelected) colors.teal else colors.border)
                                ) {
                                    Text(
                                        text = qType,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSelected) Color.White else colors.inkBold,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Option Explanations Checkbox
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = explainAllFourOptions,
                            onCheckedChange = { explainAllFourOptions = it }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(
                                text = "Explain All 4 Options Individually",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.inkBold
                            )
                            Text(
                                text = "Explains why correct option is right and why each distractor is wrong",
                                fontSize = 10.5.sp,
                                color = colors.muted
                            )
                        }
                    }

                    // Formulas & Diagrams Checkbox
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = includeFormulasAndDiagrams,
                            onCheckedChange = { includeFormulasAndDiagrams = it }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(
                                text = "Include Formulas & Diagram References",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.inkBold
                            )
                            Text(
                                text = "Formats equations and figures so the app renders them visually",
                                fontSize = 10.5.sp,
                                color = colors.muted
                            )
                        }
                    }

                    // --- OUTPUT PHASING & REASONING FLOW (CHAIN-OF-THOUGHT) ---
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "AI Output Phasing (Chain-of-Thought)",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold
                            )
                            Text(
                                text = if (promptPhasingStyle == "Reasoning-First") "Derive First" else "Standard",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (promptPhasingStyle == "Reasoning-First") colors.green else colors.teal
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val isReasoning = promptPhasingStyle == "Reasoning-First"
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        promptPhasingStyle = "Reasoning-First"
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isReasoning) colors.greenLight else colors.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(if (isReasoning) 1.5.dp else 0.6.dp, if (isReasoning) colors.green else colors.border)
                            ) {
                                Column(modifier = Modifier.padding(9.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = if (isReasoning) colors.green else colors.muted,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Reasoning-First",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isReasoning) colors.green else colors.inkBold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Q -> Explanations -> Key\nLLM derives answers first to prevent wrong keys.",
                                        fontSize = 9.5.sp,
                                        lineHeight = 13.sp,
                                        color = if (isReasoning) colors.green else colors.muted
                                    )
                                }
                            }

                            val isStandard = promptPhasingStyle == "Standard"
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        promptPhasingStyle = "Standard"
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isStandard) colors.tealLight else colors.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(if (isStandard) 1.5.dp else 0.6.dp, if (isStandard) colors.teal else colors.border)
                            ) {
                                Column(modifier = Modifier.padding(9.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.EditNote,
                                            contentDescription = null,
                                            tint = if (isStandard) colors.teal else colors.muted,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Standard Key-First",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isStandard) colors.tealDark else colors.inkBold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Q -> Key -> Explanations\nClassic output sequence for standard workflows.",
                                        fontSize = 9.5.sp,
                                        lineHeight = 13.sp,
                                        color = if (isStandard) colors.tealDark else colors.muted
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // --- FORMAT & QUANTITY CORRECTION RULE TOGGLE (OFF BY DEFAULT) ---
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (enableQuantityCorrectionRule) colors.tealLight else colors.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(0.8.dp, if (enableQuantityCorrectionRule) colors.teal else colors.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { enableQuantityCorrectionRule = !enableQuantityCorrectionRule }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
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
                                        color = if (enableQuantityCorrectionRule) colors.tealDark else colors.inkBold
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
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

        // Live Generated Prompt Card
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
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
                            shape = RoundedCornerShape(4.dp),
                            color = colors.greenLight,
                            border = BorderStroke(0.5.dp, colors.green.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = if (enableQuantityCorrectionRule) "Guard Active" else "Full Stream (Default)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.green,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
                                .heightIn(min = 44.dp),
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
                                softWrap = false
                            )
                        }

                        OutlinedButton(
                            onClick = onNavigateToCreator,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = colors.tealDark,
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
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}
