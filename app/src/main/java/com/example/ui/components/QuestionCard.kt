package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.model.McqQuestion
import com.example.ui.theme.ExamTheme

/**
 * Standard Question Card for Paper View and Preview:
 * - Supports boxed card styling (with elevation and border) or clean sheet styling.
 * - Consistent typography, refined spacing, and subtle card-based styling.
 * - Embeds StructuredQuestionContent for uniform option badge widths and aligned text.
 */
@Composable
fun QuestionCard(
    question: McqQuestion,
    isCardBoxed: Boolean = true,
    showAnswerIndicator: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors

    if (isCardBoxed) {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = if (colors.isDark) 0.dp else 1.dp),
            border = BorderStroke(0.8.dp, colors.border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StructuredQuestionContent(
                    question = question,
                    showAnswerIndicator = showAnswerIndicator
                )
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp)
        ) {
            StructuredQuestionContent(
                question = question,
                showAnswerIndicator = showAnswerIndicator
            )
            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(
                color = colors.border.copy(alpha = 0.5f),
                thickness = 0.8.dp
            )
        }
    }
}
