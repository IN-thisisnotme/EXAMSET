package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ExamConfig
import com.example.model.PrintMode
import com.example.model.QuestionPaper
import com.example.util.PrintDocumentHelper
import com.example.ui.theme.ExamTheme
import com.example.util.McqParser
import com.example.util.RichTextHelper

@Composable
fun PrintablePaperTab(
    paper: QuestionPaper,
    config: ExamConfig,
    onOpenConfig: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = ExamTheme.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Quick Action Bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(10.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.border),
            shadowElevation = if (colors.isDark) 0.dp else 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (config.printMode == PrintMode.STUDENT_PAPER) "Student Question Paper" else "Master Examination Booklet",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkBold
                    )
                    Text(
                        text = "Formatted for ISO A4 Standard Printing",
                        fontSize = 11.sp,
                        color = colors.muted
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Settings / Customize button
                    OutlinedButton(
                        onClick = onOpenConfig,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = colors.inkBold
                        ),
                        border = BorderStroke(1.dp, colors.border),
                        modifier = Modifier.testTag("configure_exam_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Configure",
                            modifier = Modifier.size(16.dp),
                            tint = colors.teal
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Customize", fontSize = 12.sp, color = colors.inkBold)
                    }

                    // Print / PDF Button
                    Button(
                        onClick = {
                            PrintDocumentHelper.printQuestionPaper(context, paper, config)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.navyBanner,
                            contentColor = colors.navyBannerText
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("quick_print_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = "Print",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Print", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Examination Paper Preview (Simulating actual printed sheet with responsive layout that never warps text)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(4.dp),
            colors = CardDefaults.cardColors(containerColor = colors.printPaperBg),
            border = BorderStroke(1.5.dp, colors.printPaperBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = if (colors.isDark) 0.dp else 4.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Official Examination Header Box
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(BorderStroke(1.5.dp, colors.printPaperBorder))
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = config.institutionName.uppercase(),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp,
                            textAlign = TextAlign.Center,
                            color = colors.printPaperText
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = config.examTitle,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.teal,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))
                        HorizontalDivider(thickness = 0.8.dp, color = colors.printPaperBorder.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(6.dp))

                        // Subject & Topic row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Subject: ${paper.metadata.subject}",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.printPaperText,
                                modifier = Modifier.weight(1f)
                            )
                            if (paper.metadata.topic.isNotBlank()) {
                                Text(
                                    text = "Topic: ${paper.metadata.topic}",
                                    fontSize = 10.sp,
                                    color = colors.printPaperText.copy(alpha = 0.85f),
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Code / Time / Marks Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Series: ${config.seriesCode.ifBlank { paper.metadata.setNumber.ifBlank { "SET A" } }}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.printPaperText
                            )
                            Text(
                                text = "Time: ${config.timeAllowed}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Normal,
                                color = colors.printPaperText
                            )
                            Text(
                                text = "Max Marks: ${config.maxMarks}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.printPaperText
                            )
                        }
                    }
                }

                // Candidate Details Box (With responsive lines that never warp, no roll number)
                if (config.includeCandidateDetails && config.printMode == PrintMode.STUDENT_PAPER) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(BorderStroke(1.dp, colors.printPaperBorder))
                                .padding(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Candidate Name: ",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.printPaperText
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(1.dp)
                                        .background(colors.printPaperBorder.copy(alpha = 0.7f))
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Candidate Sign: ",
                                        fontSize = 9.5.sp,
                                        color = colors.printPaperText
                                    )
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(1.dp)
                                            .background(colors.printPaperBorder.copy(alpha = 0.7f))
                                    )
                                }

                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Invigilator Sign: ",
                                        fontSize = 9.5.sp,
                                        color = colors.printPaperText
                                    )
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(1.dp)
                                            .background(colors.printPaperBorder.copy(alpha = 0.7f))
                                    )
                                }
                            }
                        }
                    }
                }

                // Instructions Box
                if (config.includeInstructions && config.printMode == PrintMode.STUDENT_PAPER) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(BorderStroke(0.8.dp, colors.printPaperBorder.copy(alpha = 0.6f)))
                                .background(if (colors.isDark) colors.surfaceVariant else Color(0xFFFCFCFC))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "INSTRUCTIONS TO CANDIDATES:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.printPaperText
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            config.customInstructions.forEachIndexed { index, instruction ->
                                Text(
                                    text = "${index + 1}. $instruction",
                                    fontSize = 9.5.sp,
                                    lineHeight = 13.sp,
                                    color = colors.printPaperText
                                )
                            }
                        }
                    }
                }

                // Examination Questions (Non-warping layout)
                items(paper.questions, key = { "print_preview_${it.num}" }) { q ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        // Question prompt
                        Text(
                            text = RichTextHelper.buildRichText(
                                text = "${q.num}. ${
                                    q.question
                                        .trim()
                                        .replace(Regex("""[ \t]*\n[ \t]*\n+"""), "\n")
                                }",
                                baseColor = colors.printPaperText,
                                baseFontSize = 11.5.sp,
                                isBold = true
                            ),
                            lineHeight = 13.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        // Smart adaptive options layout:
                        // If all options are short (<= 18 chars), display in 2 columns.
                        // Otherwise, stack each option on its own row to guarantee zero text warping!
                        val allOptionsShort = listOf(q.options.a, q.options.b, q.options.c, q.options.d).all { it.length <= 18 }

                        if (allOptionsShort) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = RichTextHelper.buildRichText(
                                        text = "(A) ${q.options.a}",
                                        baseColor = colors.printPaperText,
                                        baseFontSize = 10.5.sp
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = RichTextHelper.buildRichText(
                                        text = "(B) ${q.options.b}",
                                        baseColor = colors.printPaperText,
                                        baseFontSize = 10.5.sp
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = RichTextHelper.buildRichText(
                                        text = "(C) ${q.options.c}",
                                        baseColor = colors.printPaperText,
                                        baseFontSize = 10.5.sp
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = RichTextHelper.buildRichText(
                                        text = "(D) ${q.options.d}",
                                        baseColor = colors.printPaperText,
                                        baseFontSize = 10.5.sp
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        } else {
                            // Stacked format: clean full-width rows with bold letter headers
                            listOf(
                                "A" to q.options.a,
                                "B" to q.options.b,
                                "C" to q.options.c,
                                "D" to q.options.d
                            ).forEach { (optKey, optVal) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 1.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = "($optKey) ",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.printPaperText
                                    )
                                    Text(
                                        text = RichTextHelper.buildRichText(
                                            text = optVal,
                                            baseColor = colors.printPaperText,
                                            baseFontSize = 10.5.sp
                                        ),
                                        lineHeight = 15.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        HorizontalDivider(thickness = 0.5.dp, color = colors.printPaperBorder.copy(alpha = 0.3f))
                    }
                }

                // If in MASTER_BOOKLET print mode: display Answer Key and Detailed Explanations
                if (config.printMode == PrintMode.MASTER_BOOKLET) {
                    // Section II: Answer Key
                    item {
                        Spacer(modifier = Modifier.height(10.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(BorderStroke(1.dp, colors.printPaperBorder))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "SECTION II: OFFICIAL ANSWER KEY",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.printPaperText,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            // Grid of 5 answers per row
                            val answers = paper.questions.filter { it.correctAnswer != null }
                            val chunked = answers.chunked(5)
                            chunked.forEach { rowAnswers ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    rowAnswers.forEach { q ->
                                        Text(
                                            text = "Q${q.num}: [${q.correctAnswer}]",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.printPaperText
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Section III: Explanations
                    val explainedQuestions = paper.questions.filter { !it.explanation.isNullOrBlank() }
                    if (explainedQuestions.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "SECTION III: DETAILED EXPLANATIONS & SOLUTIONS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.printPaperText,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        items(explainedQuestions, key = { "print_exp_${it.num}" }) { q ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Q${q.num}. Correct: (${q.correctAnswer ?: "-"})",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.printPaperText
                                    )
                                    val optText = q.correctAnswer?.let { ansKey -> q.options[ansKey] }
                                    if (!optText.isNullOrBlank()) {
                                        Text(
                                            text = " - $optText",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = colors.printPaperText
                                        )
                                    }
                                }
                                Text(
                                    text = RichTextHelper.buildRichText(
                                        text = McqParser.formatMultiLineExplanation(q.explanation.orEmpty()),
                                        baseColor = colors.printPaperText.copy(alpha = 0.85f),
                                        baseFontSize = 9.5.sp
                                    ),
                                    lineHeight = 14.sp
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                HorizontalDivider(thickness = 0.5.dp, color = colors.printPaperBorder.copy(alpha = 0.2f))
                            }
                        }
                    }
                }

                // OMR Sheet Simulation Preview (for Student Paper mode - Drop-out Red Theme)
                if (config.includeOmrSheet && config.printMode == PrintMode.STUDENT_PAPER) {
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White)
                                .border(BorderStroke(1.5.dp, Color(0xFFB91C1C)))
                        ) {
                            // 4 Corner Registration Anchors in #b91c1c
                            Box(modifier = Modifier.size(8.dp).background(Color(0xFFB91C1C)).align(Alignment.TopStart))
                            Box(modifier = Modifier.size(8.dp).background(Color(0xFFB91C1C)).align(Alignment.TopEnd))
                            Box(modifier = Modifier.size(8.dp).background(Color(0xFFB91C1C)).align(Alignment.BottomStart))
                            Box(modifier = Modifier.size(8.dp).background(Color(0xFFB91C1C)).align(Alignment.BottomEnd))

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = "OMR RESPONSE SHEET (DROP-OUT RED EDITION)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = TextAlign.Center,
                                    color = Color(0xFFB91C1C),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    text = "Universal 3.8mm Bubbles | Drop-Out Red #B91C1C | No Horizontal Lines | 5-Row Zebra Tint",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    color = Color(0xFFB91C1C),
                                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp)
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                // Sample rows of bubbles with 5-row alternating zebra tint (no horizontal lines between questions)
                                val sampleQuestions = paper.questions.take(minOf(10, paper.questions.size))
                                sampleQuestions.forEachIndexed { index, q ->
                                    val isShaded = (index / 5) % 2 == 1

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(if (isShaded) Color(0xFFFFF1F2) else Color.White)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 2.5.dp, horizontal = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = String.format("%02d.", q.num),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFB91C1C),
                                                modifier = Modifier.width(28.dp)
                                            )
                                            listOf("A", "B", "C", "D").forEach { opt ->
                                                Surface(
                                                    modifier = Modifier
                                                        .padding(horizontal = 4.dp)
                                                        .size(16.dp),
                                                    shape = CircleShape,
                                                    border = BorderStroke(1.2.dp, Color(0xFFB91C1C)),
                                                    color = Color.White
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = opt,
                                                            fontSize = 8.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFFB91C1C)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                if (paper.questions.size > 10) {
                                    Text(
                                        text = "... + remaining ${paper.questions.size - 10} questions with alternating 5-row zebra strips (#FFF1F2)",
                                        fontSize = 8.5.sp,
                                        color = Color(0xFFB91C1C),
                                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Verification row at bottom (Fixed 1-inch signature boxes, no roll number)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // Candidate Name Box
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .border(BorderStroke(1.dp, Color(0xFFB91C1C)))
                                            .background(Color(0xFFFFFBFA))
                                            .padding(4.dp)
                                            .height(42.dp),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Candidate Name (Block Letters)",
                                            fontSize = 7.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFB91C1C)
                                        )
                                        HorizontalDivider(thickness = 1.dp, color = Color(0xFFB91C1C))
                                    }

                                    // Candidate Signature Box (1-inch fixed)
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .border(BorderStroke(1.dp, Color(0xFFB91C1C)))
                                            .background(Color(0xFFFFFBFA))
                                            .padding(4.dp)
                                            .height(42.dp),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Candidate Signature",
                                            fontSize = 7.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFB91C1C)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(26.dp)
                                                .border(BorderStroke(1.dp, Color(0xFFF87171)))
                                                .background(Color.White),
                                            contentAlignment = Alignment.BottomCenter
                                        ) {
                                            Text(
                                                text = "Sign Inside Box",
                                                fontSize = 6.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFB91C1C),
                                                modifier = Modifier.padding(bottom = 2.dp)
                                            )
                                        }
                                    }

                                    // Invigilator Signature Box (1-inch fixed)
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .border(BorderStroke(1.dp, Color(0xFFB91C1C)))
                                            .background(Color(0xFFFFFBFA))
                                            .padding(4.dp)
                                            .height(42.dp),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Invigilator Sig & Stamp",
                                            fontSize = 7.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFB91C1C)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(26.dp)
                                                .border(BorderStroke(1.dp, Color(0xFFF87171)))
                                                .background(Color.White),
                                            contentAlignment = Alignment.BottomCenter
                                        ) {
                                            Text(
                                                text = "Sig & Stamp",
                                                fontSize = 6.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFB91C1C),
                                                modifier = Modifier.padding(bottom = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "*** END OF EXAMINATION PAPER ***",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
    }
}
