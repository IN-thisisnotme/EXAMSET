package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material3.TextFieldDefaults
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
        includeFormulasAndDiagrams, enableQuantityCorrectionRule
    ) {
        derivedStateOf {
            buildString {
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
                        append("   Do NOT generate only plain, single-sentence recall questions. You MUST actively distribute these $countInt questions across the diverse examination formats used in real papers:\n")
                        append("   - Multi-Statement Questions (e.g., 'Consider the following statements: 1. [...] 2. [...] 3. [...] Which of the statements given above is/are correct? (A) 1 and 2 only (B) 2 and 3 only (C) 1 and 3 only (D) 1, 2 and 3')\n")
                        append("   - Assertion (A) & Reason (R) Questions (e.g., 'Assertion (A): [...] \\nReason (R): [...] \\n(A) Both A and R are true and R is the correct explanation of A...')\n")
                        append("   - Match-the-Following (e.g., 'Match List-I with List-II: \\nList-I \\n1. [...] \\n2. [...] \\nList-II \\nP. [...] \\nQ. [...] \\n(A) 1-P, 2-Q, 3-S, 4-R...')\n")
                        append("   - Chronological / Process Sequence Questions (e.g., 'Arrange the following stages/events in the correct order: 1. [...] 2. [...] (A) 1-2-3-4 (B) 2-1-4-3...')\n")
                        append("   - Quantitative / Numerical Calculation Questions (clear parameters, formula application, and numerical options)\n")
                        append("   - Direct Conceptual & Scenario Analysis Questions\n")
                        append("   STRICT PROHIBITION ON CATEGORY LABELS: Do NOT print category headers, type prefixes, or bracketed tags (such as [Assertion-Reason], [Match the Following], [Statement-Based], or 'Type: ...') anywhere in the questions. Present every question naturally as an authentic examination item starting immediately with 'Q[N]. [Question text]'.\n")
                    }
                }
                append("3. DISTRACTOR HOMOGENEITY: All 4 options must be mutually exclusive and grammatically parallel (all identical parts of speech/sentence structure).\n")
                append("4. LENGTH CONTROL: The correct option must NOT be the longest option by default. Maintain a tight word-count ratio across all 4 options (no option exceeding 1.25x the length of the shortest).\n")
                append("5. ORDERING: If options contain numerical values, dates, or quantitative ranges, order them strictly from lowest to highest.\n")
                append("6. PROHIBITED PHRASES: Never use \"All of the above\", \"None of the above\", or \"Both A and B\".\n")
                append("7. NEGATIVE STEMS: Avoid negative questions where possible. If unavoidable, highlight the negative operator in uppercase bold (e.g., **NOT**, **EXCEPT**, **LEAST**).\n")
                if (includeFormulasAndDiagrams) {
                    append("8. SCIENTIFIC & MATH NOTATION: Enclose all mathematical expressions, chemical equations, units, and formulas in standard LaTeX using single dollar signs (e.g., \$E = mc^2\$, \$H_2SO_4\$, \$9.8\\text{ m/s}^2\$).\n")
                    append("9. DIAGRAMS: If a question references a diagram, chart, or figure, include: Diagram: [Figure title or description | Optional URL].\n")
                } else {
                    append("8. SCIENTIFIC & MATH NOTATION: Enclose all mathematical expressions, chemical equations, units, and formulas in standard LaTeX using single dollar signs (e.g., \$E = mc^2\$, \$H_2SO_4\$, \$9.8\\text{ m/s}^2\$).\n")
                }
                append("\n")

                append("### 3. STATISTICAL BALANCE & ZERO-DISCREPANCY\n")
                append("1. BALANCED KEY DISTRIBUTION: Distribute correct answers roughly evenly (~25% each across A, B, C, D). Never allow more than two consecutive identical answer keys (e.g., A, A is allowed; A, A, A is strictly prohibited).\n")
                append("2. PHASE SYNC: The option declared in Phase 2 MUST match the option identified as \"Correct\" in Phase 3 without exception.\n")
                append("3. ZERO CHATTER: Output starts immediately with \"Phase 1: Questions\". No introductory remarks, disclaimers, or closing text.\n")
                append("4. NO DIVIDER LINES: Do NOT use markdown horizontal divider lines (---) anywhere in the questions or explanations.\n\n")

                append("### 4. PLAIN-TEXT FORMATTING, ZERO-INDENTATION & ANTI-LIST RULES\n")
                append("1. ZERO INDENTATION (FLUSH-LEFT): Every single line (questions, options, answers, and explanations) must start strictly at the left margin (Column 0). Never insert leading spaces or tab characters (\\t).\n")
                append("2. ANTI-LIST RULE: Never use bullet points (•, *), list hyphens (-), or blockquotes (>). Never format question numbers as markdown numbered lists (e.g., '1. '). Use 'Q1.', 'Q2.' so that copying into Microsoft Word, Google Docs, or text editors pastes as pure plain text and never triggers automatic numbered or bulleted list styles.\n")
                append("3. NO TABLES OR MULTI-SPACE ALIGNMENT: Never format options as a markdown table, HTML table, or aligned column grid. Each option must be on its own fresh line with exactly ONE space after the label: '(A) [Option text]'. Never use multiple spaces or tabs to align numbers, units, or option letters.\n")
                append("4. NO CODE FENCES: Do not wrap the output inside markdown code fences (```). Output directly selectable, continuous plain text.\n\n")

                append("### EXACT OUTPUT FORMAT TEMPLATE:\n\n")
                append("Phase 1: Questions\n\n")
                append("Q1. [Clear, unambiguous question stem or scenario]\n")
                append("(A) [Option A]\n")
                append("(B) [Option B]\n")
                append("(C) [Option C]\n")
                append("(D) [Option D]\n\n")
                append("[Repeat through Q$countInt]\n\n")

                append("Phase 2: Answer Key\n\n")
                append("Q1: (A)\n")
                append("Q2: (C)\n")
                append("[Repeat through Q$countInt]\n\n")

                append("Phase 3: Explanations & Solution\n\n")
                if (explainAllFourOptions) {
                    append("Q1.\n")
                    append("Core Concept & Reasoning: [1-2 concise sentences stating the governing principle, theorem, or calculation path]\n")
                    append("Option (A) [Correct/Incorrect]: [Direct explanation with underlying reason]\n")
                    append("Option (B) [Correct/Incorrect]: [Direct explanation with underlying reason]\n")
                    append("Option (C) [Correct/Incorrect]: [Direct explanation with underlying reason]\n")
                    append("Option (D) [Correct/Incorrect]: [Direct explanation with underlying reason]\n")
                    append("Memory Anchor: [Single memorable takeaway, key formula, or mental model]\n\n")
                } else {
                    append("Q1.\n")
                    append("Core Concept & Reasoning: [1-2 concise sentences stating the governing principle, theorem, or calculation path]\n")
                    append("Why Correct: [Direct explanation with underlying reason for the correct option]\n")
                    append("Distractor Breakdown: [Key distinctions explaining why incorrect options fail]\n")
                    append("Memory Anchor: [Single memorable takeaway, key formula, or mental model]\n\n")
                }
                append("[Repeat through Q$countInt without any horizontal divider lines (---)]\n\n")

                if (enableQuantityCorrectionRule) {
                    append("Phase 4: Format & Quantity Enforcement (Truncation Guard Active)\n")
                    append("1. Strict Quantity Check: Generate exactly all $countInt questions from 1 to $countInt without stopping early or omitting questions.\n")
                    append("2. High-Density Token Budget: To fit all $countInt questions and explanations completely without token limit cutoffs, keep each option's explanation to 2 to 3 concise, high-impact sentences (30–45 words).\n")
                    append("3. Depth Uniformity: Maintain identical pedagogical depth from Question 1 through Question $countInt. Do not rush or shorten later questions.\n\n")
                }

                append("Execute the instructions now, starting directly with Phase 1: Questions.")
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
                                color = Color(0xFFF59E0B).copy(alpha = 0.12f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = Color(0xFFD97706),
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
                                containerColor = if (copied) Color(0xFF059669) else Color(0xFF2563EB)
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
                            Text(if (copied) "Copied!" else "Copy Prompt", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
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
                                color = if (isSelected) Color(0xFF2563EB) else colors.surfaceVariant,
                                border = BorderStroke(if (isSelected) 1.5.dp else 0.5.dp, if (isSelected) Color(0xFF93C5FD) else colors.border)
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
                                    color = if (isPreset) Color(0xFF2563EB) else colors.muted
                                )
                                Text("$countInt MCQs", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
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
                                    color = if (isSelected) Color(0xFF2563EB) else colors.surfaceVariant,
                                    border = BorderStroke(0.6.dp, if (isSelected) Color(0xFF2563EB) else colors.border)
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
                                    color = Color(0xFF2563EB),
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
                                    focusedBorderColor = Color(0xFF2563EB),
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
                                        color = if (isSelected) Color(0xFF2563EB) else colors.surface,
                                        border = BorderStroke(0.6.dp, if (isSelected) Color(0xFF2563EB) else colors.border)
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
                                        color = if (isSelected) Color(0xFF2563EB) else colors.surface,
                                        border = BorderStroke(0.6.dp, if (isSelected) Color(0xFF2563EB) else colors.border)
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
                                        focusedBorderColor = Color(0xFF2563EB),
                                        unfocusedBorderColor = colors.border,
                                        focusedContainerColor = colors.surface,
                                        unfocusedContainerColor = colors.surface
                                    )
                                )
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
                            Text(difficulty, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
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
                                    color = if (isSelected) Color(0xFF2563EB) else colors.surfaceVariant,
                                    border = BorderStroke(0.5.dp, colors.border)
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
                            Text(questionType, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
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
                                    color = if (isSelected) Color(0xFF2563EB) else colors.surfaceVariant,
                                    border = BorderStroke(0.5.dp, colors.border)
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

                    // --- FORMAT & QUANTITY CORRECTION RULE TOGGLE (OFF BY DEFAULT) ---
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (enableQuantityCorrectionRule) Color(0xFFEFF6FF) else colors.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(0.8.dp, if (enableQuantityCorrectionRule) Color(0xFF93C5FD) else colors.border),
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
                                        tint = if (enableQuantityCorrectionRule) Color(0xFF2563EB) else colors.muted,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Format & Quantity Truncation Guard",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (enableQuantityCorrectionRule) Color(0xFF1D4ED8) else colors.inkBold
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
                                    color = if (enableQuantityCorrectionRule) Color(0xFF1E40AF) else colors.muted
                                )
                            }

                            Switch(
                                checked = enableQuantityCorrectionRule,
                                onCheckedChange = { enableQuantityCorrectionRule = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF2563EB)
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
                            color = Color(0xFFECFDF5),
                            border = BorderStroke(0.5.dp, Color(0xFFA7F3D0))
                        ) {
                            Text(
                                text = if (enableQuantityCorrectionRule) "Guard Active" else "Full Stream (Default)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF047857),
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
                                containerColor = if (copied) Color(0xFF059669) else Color(0xFF2563EB),
                                contentColor = Color.White
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
