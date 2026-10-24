package com.example.ui.components

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.McqQuestion
import com.example.ui.theme.ExamTheme
import com.example.util.RichTextHelper

/**
 * Renders attached diagrams, charts, and chemical/mathematical formulas
 * for an individual MCQ question with zoom dialog and rich formatting.
 */
@Composable
fun QuestionAttachmentView(
    question: McqQuestion,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    var showZoomDialog by remember { mutableStateOf(false) }

    val hasDiagram = !question.diagramUrl.isNullOrBlank()
    val hasFormula = !question.formula.isNullOrBlank()

    if (!hasDiagram && !hasFormula) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Chemical or Mathematical Formula Box
        if (hasFormula) {
            val formulaText = question.formula!!.trim()
            val isChemistry = formulaText.contains("→") ||
                    formulaText.contains("⇌") ||
                    formulaText.contains("+") && Regex("""[A-Z][a-z]?\d*""").containsMatchIn(formulaText)

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = colors.surfaceVariant.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, colors.border.copy(alpha = 0.8f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isChemistry) Icons.Default.Science else Icons.Default.Functions,
                                contentDescription = null,
                                tint = if (isChemistry) colors.teal else Color(0xFF6366F1),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isChemistry) "Chemical Formula / Reaction" else "Mathematical Formula / Equation",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (isChemistry) colors.teal.copy(alpha = 0.12f) else Color(0xFF6366F1).copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = if (isChemistry) "CHEM" else "MATH",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isChemistry) colors.teal else Color(0xFF6366F1),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Formula rendered with subscript / superscript / Greek symbols
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surface, RoundedCornerShape(6.dp))
                            .border(BorderStroke(0.6.dp, colors.border.copy(alpha = 0.6f)), RoundedCornerShape(6.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        if (MathFormulaParser.has2DMath(formulaText)) {
                            MathFormulaView(
                                formula = formulaText,
                                color = colors.inkBold,
                                fontSize = 15.sp,
                                modifier = Modifier.align(Alignment.CenterStart)
                            )
                        } else {
                            LatexText(
                                text = formulaText,
                                color = colors.inkBold,
                                fontSize = 14.5.sp,
                                isBold = true,
                                lineHeight = 22.sp,
                                modifier = Modifier.align(Alignment.CenterStart)
                            )
                        }
                    }
                }
            }
        }

        // 2. Diagram / Chart Attachment Card
        if (hasDiagram) {
            val diagramUrl = question.diagramUrl!!.trim()
            val label = question.diagramLabel?.trim()

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = colors.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, colors.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    // Header with Caption and Zoom Affordance
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = colors.teal,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (!label.isNullOrBlank()) label else "Refer to Diagram / Chart below:",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.inkBold
                            )
                        }

                        Surface(
                            onClick = { showZoomDialog = true },
                            shape = RoundedCornerShape(4.dp),
                            color = colors.teal.copy(alpha = 0.12f),
                            border = BorderStroke(0.6.dp, colors.teal.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ZoomIn,
                                    contentDescription = "Zoom Diagram",
                                    tint = colors.teal,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Tap to Enlarge",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.teal
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Diagram Image Container
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 220.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White)
                            .clickable { showZoomDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(diagramUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = label ?: "Question Diagram",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .padding(4.dp)
                        )
                    }
                }
            }
        }
    }

    // Full Screen Zoom Dialog for Diagram
    if (showZoomDialog && hasDiagram) {
        val diagramUrl = question.diagramUrl!!.trim()
        val label = question.diagramLabel?.trim()

        Dialog(onDismissRequest = { showZoomDialog = false }) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(1.dp, colors.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label ?: "Question ${question.num} Diagram",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkBold
                        )
                        IconButton(
                            onClick = { showZoomDialog = false },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = colors.ink
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 220.dp, max = 380.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(diagramUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = label ?: "Question Diagram",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Question: ${question.question}",
                        fontSize = 11.5.sp,
                        color = colors.inkMuted,
                        maxLines = 2
                    )
                }
            }
        }
    }
}
