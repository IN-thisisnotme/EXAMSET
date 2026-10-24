package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.SaveAlt
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.Language
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import com.example.ui.components.AuthenticOmrSheetView
import com.example.model.ExamConfig
import com.example.model.OmrGeometry
import com.example.model.PrintMode
import com.example.model.QuestionPaper
import com.example.util.PrintDocumentHelper
import com.example.util.ExamNumberGenerator
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

    // Preview Mode: 0 = Question Paper, 1 = A4 OMR Sheet, 2 = Master Booklet, 3 = Live Print HTML
    var previewMode by remember {
        mutableIntStateOf(
            when (config.printMode) {
                PrintMode.OMR_ONLY -> 1
                PrintMode.MASTER_BOOKLET -> 2
                else -> 0
            }
        )
    }

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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Title and Subtitle Row with full width
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (config.printMode == PrintMode.STUDENT_PAPER) "Student Question Paper" else "Master Examination Booklet",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Formatted for ISO A4 Standard Printing",
                        fontSize = 11.5.sp,
                        color = colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Uniform Action Buttons Row with equal 1f weight
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Settings / Customize button
                    OutlinedButton(
                        onClick = onOpenConfig,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = colors.inkBold
                        ),
                        border = BorderStroke(1.dp, colors.border),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .testTag("configure_exam_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Configure",
                            modifier = Modifier.size(15.dp),
                            tint = colors.teal
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Customize", fontSize = 12.sp, maxLines = 1, softWrap = false, color = colors.inkBold)
                    }

                    // Save PDF directly to File Manager
                    OutlinedButton(
                        onClick = {
                            PrintDocumentHelper.savePdfDirectly(
                                context = context,
                                paper = paper,
                                config = config,
                                onSuccess = { savedPath ->
                                    android.widget.Toast.makeText(
                                        context,
                                        "Saved to $savedPath",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                },
                                onError = { errorMsg ->
                                    android.widget.Toast.makeText(
                                        context,
                                        errorMsg,
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            )
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = colors.inkBold
                        ),
                        border = BorderStroke(1.dp, colors.teal),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .testTag("quick_save_pdf_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SaveAlt,
                            contentDescription = "Save PDF",
                            modifier = Modifier.size(15.dp),
                            tint = colors.teal
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save PDF", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, color = colors.inkBold)
                    }

                    // Native Print Spooler Button
                    Button(
                        onClick = {
                            PrintDocumentHelper.printQuestionPaper(context, paper, config)
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primaryButtonBg,
                            contentColor = colors.primaryButtonText
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .testTag("quick_print_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = "Print",
                            modifier = Modifier.size(15.dp),
                            tint = colors.primaryButtonText
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Print", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, color = colors.primaryButtonText)
                    }
                }
            }
        }

        // Preview Mode Selector Bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            shape = RoundedCornerShape(8.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.border)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val previewTabs = listOf(
                    Triple(0, "Question Paper", Icons.Default.Description),
                    Triple(1, "A4 OMR Sheet", Icons.Default.Radio),
                    Triple(2, "Master Booklet", Icons.Default.ViewCarousel),
                    Triple(3, "Live A4 Print HTML", Icons.Default.Language)
                )

                previewTabs.forEach { (mode, label, icon) ->
                    val isSelected = previewMode == mode
                    Surface(
                        modifier = Modifier
                            .clickable { previewMode = mode }
                            .testTag("preview_tab_$mode"),
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSelected) {
                            if (colors.isDark) colors.tealDark.copy(alpha = 0.35f) else colors.tealLight
                        } else Color.Transparent,
                        border = if (isSelected) BorderStroke(1.2.dp, colors.teal) else BorderStroke(0.6.dp, Color.Transparent)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = if (isSelected) colors.teal else colors.muted,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) (if (colors.isDark) colors.teal else colors.navyBanner) else colors.inkBold
                            )
                        }
                    }
                }
            }
        }

        // Examination Paper / OMR Preview Container
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(4.dp),
            colors = CardDefaults.cardColors(containerColor = colors.printPaperBg),
            border = BorderStroke(1.5.dp, colors.printPaperBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = if (colors.isDark) 0.dp else 4.dp)
        ) {
            when (previewMode) {
                1 -> {
                    // Dedicated Full A4 OMR Sheet View — renders the authentic official A4 OMR HTML
                    // scaled to fit screen via WebView just like Live A4 Print HTML:
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.apply {
                                    loadWithOverviewMode = true
                                    useWideViewPort = true
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                }
                            }
                        },
                        update = { webView ->
                            val omrConfig = config.copy(printMode = PrintMode.OMR_ONLY, includeOmrSheet = true)
                            val html = PrintDocumentHelper.generatePrintHtml(paper, omrConfig)
                            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Previous custom AuthenticOmrSheetView retained in comments per preservation rule:
                    /*
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(8.dp)
                    ) {
                        AuthenticOmrSheetView(
                            paper = paper,
                            config = config,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    */
                }
                3 -> {
                    // Live Print HTML rendered via native WebView
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.apply {
                                    loadWithOverviewMode = true
                                    useWideViewPort = true
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                }
                            }
                        },
                        update = { webView ->
                            val html = PrintDocumentHelper.generatePrintHtml(paper, config)
                            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
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
                                text = "Booklet No: ${config.bookletNumber.ifBlank { paper.metadata.bookletNumber.ifBlank { "0000001" } }}",
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

                // Candidate Details Box (With responsive lines, Roll Number & Booklet Number boxes)
                if (config.includeCandidateDetails && config.printMode == PrintMode.STUDENT_PAPER) {
                    item {
                        val defaultRoll = config.rollNumber.ifBlank { paper.metadata.rollNumber.ifBlank { "0000000001" } }
                        val defaultBooklet = config.bookletNumber.ifBlank { paper.metadata.bookletNumber.ifBlank { "0000001" } }
                        val rollDigits = ExamNumberGenerator.normalizeRollNumber(defaultRoll, 10)
                        val bookletDigits = ExamNumberGenerator.normalizeBookletNumber(defaultBooklet, 7)

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

                            Spacer(modifier = Modifier.height(6.dp))

                            // Roll No & Booklet No Rows
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Roll No: ",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.printPaperText
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        for (i in rollDigits.indices) {
                                            Box(
                                                modifier = Modifier
                                                    .size(width = 14.dp, height = 18.dp)
                                                    .border(BorderStroke(1.dp, colors.printPaperBorder)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = rollDigits[i].toString(),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colors.printPaperText
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Booklet No: ",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.printPaperText
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        for (i in bookletDigits.indices) {
                                            Box(
                                                modifier = Modifier
                                                    .size(width = 14.dp, height = 18.dp)
                                                    .border(BorderStroke(1.dp, colors.printPaperBorder)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = bookletDigits[i].toString(),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colors.printPaperText
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

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

                // If in MASTER_BOOKLET print mode or Master Booklet preview tab is selected: display Answer Key and Detailed Explanations
                if (config.printMode == PrintMode.MASTER_BOOKLET || previewMode == 2) {
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

                // Attached Official A4 OMR Response Sheet Preview (Authentic NEET / UPSC Theme)
                // Commented out per user request: OMR preview is omitted from question paper preview
                /*
                if (config.includeOmrSheet && (previewMode == 0 || config.printMode == PrintMode.STUDENT_PAPER)) {
                    item {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "❖ ATTACHED OFFICIAL A4 OMR RESPONSE SHEET ❖",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFBE185D),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )
                        AuthenticOmrSheetView(
                            paper = paper,
                            config = config,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                */

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
        }

        Spacer(modifier = Modifier.height(10.dp))
    }
}
}
