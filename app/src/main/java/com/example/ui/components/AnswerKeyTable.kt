package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.McqQuestion
import com.example.ui.theme.ExamTheme

/**
 * Answer Key Table directly recreating the 5-column pair grid from the Python generator.
 * Harmoniously styled for both Light and Eye-Safe Dark modes.
 */
@Composable
fun AnswerKeyTable(
    questions: List<McqQuestion>,
    modifier: Modifier = Modifier
) {
    if (questions.isEmpty()) return

    val colors = ExamTheme.colors
    val pairs = 5
    val total = questions.size
    val rows = Math.ceil(total.toDouble() / pairs).toInt()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(0.6.dp, colors.border),
        elevation = CardDefaults.cardElevation(defaultElevation = if (colors.isDark) 0.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            // Header Row (Teal Light with Navy / Teal Text)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.tealLight, shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                    .border(BorderStroke(0.5.dp, colors.border), shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                    .padding(vertical = 5.dp)
            ) {
                repeat(pairs) {
                    Box(
                        modifier = Modifier.weight(0.7f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Q",
                            color = if (colors.isDark) colors.teal else colors.navyBanner,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Box(
                        modifier = Modifier.weight(1.1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Ans",
                            color = if (colors.isDark) colors.teal else colors.navyBanner,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Data Rows
            for (r in 0 until rows) {
                val isEvenRow = r % 2 == 0
                val rowBg = if (isEvenRow) colors.surface else colors.surfaceVariant

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(rowBg)
                        .border(BorderStroke(0.3.dp, colors.border.copy(alpha = 0.6f)))
                        .padding(vertical = 4.dp)
                ) {
                    for (c in 0 until pairs) {
                        val idx = r + c * rows
                        if (idx < total) {
                            val q = questions[idx]
                            val ans = q.correctAnswer ?: "—"

                            Box(
                                modifier = Modifier.weight(0.7f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${q.num}",
                                    color = colors.inkBold,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Box(
                                modifier = Modifier.weight(1.1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = ans,
                                    color = colors.tealDark,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(0.7f))
                            Spacer(modifier = Modifier.weight(1.1f))
                        }
                    }
                }
            }
        }
    }
}
