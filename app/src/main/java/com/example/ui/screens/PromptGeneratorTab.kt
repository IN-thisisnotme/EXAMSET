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

    var examType by remember { mutableStateOf("UPSC Civil Services Examination") }
    var subject by remember { mutableStateOf("General Science") }
    var topic by remember { mutableStateOf("Ecology, Biodiversity, Climate Change & Green Energy") }
    var questionCount by remember { mutableFloatStateOf(20f) }
    var difficulty by remember { mutableStateOf("Mixed") }
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
        examType, subject, topic, countInt, difficulty,
        questionLanguage, effectiveExplanationLanguage, explainAllFourOptions,
        includeFormulasAndDiagrams, enableQuantityCorrectionRule
    ) {
        derivedStateOf {
            buildString {
                val qLang = questionLanguage.trim().ifBlank { "English" }
                val targetExam = examType.trim().ifBlank { "Competitive Examination" }
                val targetSubject = if (topic.isNotBlank()) "$subject (Focus: $topic)" else subject

                append("Act as a senior psychometrician and chief examination paper setter for $targetExam.\n\n")
                append("Generate exactly $countInt multiple-choice questions (MCQs) for $targetSubject at $difficulty difficulty in $qLang.\n\n")

                append("### 1. LANGUAGE & SCRIPT CONVENTIONS\n")
                if (effectiveExplanationLanguage == qLang || explanationChoice == "Same as Question") {
                    append("- Language: Write the question stems, options, and explanations entirely in $qLang using natural, academically rigorous terminology.\n")
                } else {
                    append("- Language: Write question stems and options in $qLang, and explanations in $effectiveExplanationLanguage, using natural, academically rigorous terminology.\n")
                }
                append("- Structural Markers: Regardless of the language used, keep question labels as \"Q1.\", \"Q2.\" and option keys strictly as \"(A)\", \"(B)\", \"(C)\", \"(D)\" for universal OMR and parser compatibility.\n\n")

                append("### 2. PSYCHOMETRIC & DISTRACTOR RULES\n")
                append("1. COGNITIVE LEVEL: Target higher-order reasoning (application, diagnosis, multi-step calculation, or critical deduction) over rote recall.\n")
                append("2. DISTRACTOR HOMOGENEITY: All 4 options must be mutually exclusive and grammatically parallel (all identical parts of speech/sentence structure).\n")
                append("3. LENGTH CONTROL: The correct option must NOT be the longest option by default. Maintain a tight word-count ratio across all 4 options (no option exceeding 1.25x the length of the shortest).\n")
                append("4. ORDERING: If options contain numerical values, dates, or quantitative ranges, order them strictly from lowest to highest.\n")
                append("5. PROHIBITED PHRASES: Never use \"All of the above\", \"None of the above\", or \"Both A and B\".\n")
                append("6. NEGATIVE STEMS: Avoid negative questions where possible. If unavoidable, highlight the negative operator in uppercase bold (e.g., **NOT**, **EXCEPT**, **LEAST**).\n")
                if (includeFormulasAndDiagrams) {
                    append("7. SCIENTIFIC & MATH NOTATION: Enclose all mathematical expressions, chemical equations, units, and formulas in standard LaTeX using single dollar signs (e.g., \$E = mc^2\$, \$H_2SO_4\$, \$9.8\\text{ m/s}^2\$).\n")
                    append("8. DIAGRAMS: If a question references a diagram, chart, or figure, include: Diagram: [Figure title or description | Optional URL].\n")
                } else {
                    append("7. SCIENTIFIC & MATH NOTATION: Enclose all mathematical expressions, chemical equations, units, and formulas in standard LaTeX using single dollar signs (e.g., \$E = mc^2\$, \$H_2SO_4\$, \$9.8\\text{ m/s}^2\$).\n")
                }
                append("\n")

                append("### 3. STATISTICAL BALANCE & ZERO-DISCREPANCY\n")
                append("1. BALANCED KEY DISTRIBUTION: Distribute correct answers roughly evenly (~25% each across A, B, C, D). Never allow more than two consecutive identical answer keys (e.g., A, A is allowed; A, A, A is strictly prohibited).\n")
                append("2. PHASE SYNC: The option declared in Phase 2 MUST match the option identified as \"Correct\" in Phase 3 without exception.\n")
                append("3. ZERO CHATTER: Output starts immediately with \"Phase 1: Questions\". No introductory remarks, disclaimers, or closing text.\n")
                append("4. NO DIVIDER LINES: Do NOT use markdown horizontal divider lines (---) anywhere in the questions or explanations.\n\n")

                append("### EXACT OUTPUT FORMAT TEMPLATE:\n\n")
                append("Phase 1: Questions\n")
                append("Q1. [Clear, unambiguous question stem or scenario]\n")
                append("(A) [Option A]\n")
                append("(B) [Option B]\n")
                append("(C) [Option C]\n")
                append("(D) [Option D]\n\n")
                append("[Repeat through Q$countInt]\n\n")

                append("Phase 2: Answer Key\n")
                append("Q1: (A)\n")
                append("Q2: (C)\n")
                append("[Repeat through Q$countInt]\n\n")

                append("Phase 3: Explanations & Solution\n")
                if (explainAllFourOptions) {
                    append("Q1.\n")
                    append("• Core Concept & Reasoning: [1-2 concise sentences stating the governing principle, theorem, or calculation path]\n")
                    append("• Option Breakdown:\n")
                    append("  - (A) [Correct/Incorrect]: [Direct explanation with underlying reason]\n")
                    append("  - (B) [Correct/Incorrect]: [Direct explanation with underlying reason]\n")
                    append("  - (C) [Correct/Incorrect]: [Direct explanation with underlying reason]\n")
                    append("  - (D) [Correct/Incorrect]: [Direct explanation with underlying reason]\n")
                    append("• Key Takeaway / Memory Anchor: [Single memorable takeaway, key formula, or mental model]\n\n")
                } else {
                    append("Q1.\n")
                    append("• Core Concept & Reasoning: [1-2 concise sentences stating the governing principle, theorem, or calculation path]\n")
                    append("• Why Correct: [Direct explanation with underlying reason for the correct option]\n")
                    append("• Distractor Breakdown: [Key distinctions explaining why incorrect options fail]\n")
                    append("• Key Takeaway / Memory Anchor: [Single memorable takeaway, key formula, or mental model]\n\n")
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
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        examPresets.forEach { preset ->
                            val isSelected = examType.equals(preset, ignoreCase = true) || (examType.isNotBlank() && examType.startsWith(preset.take(6)))
                            Surface(
                                modifier = Modifier.clickable { examType = preset },
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) Color(0xFF2563EB) else colors.surfaceVariant,
                                border = BorderStroke(0.5.dp, colors.border)
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
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
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
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        quickSubjects.forEach { quickSub ->
                            val isSubSelected = subject.equals(quickSub, ignoreCase = true)
                            Surface(
                                modifier = Modifier.clickable { subject = quickSub },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSubSelected) colors.tealLight else colors.surfaceVariant.copy(alpha = 0.6f),
                                border = BorderStroke(0.5.dp, if (isSubSelected) colors.teal else colors.border)
                            ) {
                                Text(
                                    text = quickSub,
                                    fontSize = 10.sp,
                                    color = if (isSubSelected) colors.teal else colors.inkBold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.5.dp)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = subject,
                            onValueChange = { subject = it },
                            label = { Text("Subject", fontSize = 11.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = topic,
                            onValueChange = { topic = it },
                            label = { Text("Focus Topic", fontSize = 11.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Question Count Selector & Chips
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Question Count", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = colors.ink)
                            Text("$countInt MCQs", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // Preset Count Chips (5, 10, 25, 50, 100, 180)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(5, 10, 25, 50, 100, 180).forEach { cnt ->
                                Surface(
                                    onClick = { questionCount = cnt.toFloat() },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (countInt == cnt) Color(0xFF2563EB) else colors.surfaceVariant,
                                    border = BorderStroke(0.6.dp, if (countInt == cnt) Color(0xFF2563EB) else colors.border)
                                ) {
                                    Text(
                                        text = "$cnt Qs",
                                        fontSize = 11.sp,
                                        fontWeight = if (countInt == cnt) FontWeight.Bold else FontWeight.Normal,
                                        color = if (countInt == cnt) Color.White else colors.inkBold,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Slider(
                            value = questionCount,
                            onValueChange = { questionCount = it },
                            valueRange = 5f..200f
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

                    // Standard / Difficulty
                    Text("Target Standard / Pattern", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = colors.ink)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Mixed", "Conceptual", "Standard", "Challenging", "Assertion-Reason").forEach { diff ->
                            Surface(
                                modifier = Modifier.clickable { difficulty = diff },
                                shape = RoundedCornerShape(6.dp),
                                color = if (difficulty == diff) Color(0xFF2563EB) else colors.surfaceVariant,
                                border = BorderStroke(0.5.dp, colors.border)
                            ) {
                                Text(
                                    text = diff,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (difficulty == diff) Color.White else colors.inkBold,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                )
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
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = copyAction,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                            modifier = Modifier.heightIn(min = 42.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (copied) Color(0xFF059669) else Color(0xFF2563EB)
                            )
                        ) {
                            Icon(
                                if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (copied) "Prompt Copied to Clipboard!" else "Copy AI Prompt",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}
