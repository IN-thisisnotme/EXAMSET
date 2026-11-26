package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.model.VoiceAnalysisResult
import com.example.model.VoiceAnalysisUiState
import com.example.model.VoiceComparisonStats
import com.example.model.VoiceWordMatchStatus
import com.example.ui.theme.ExamTheme

@Composable
fun VoicePracticeResultsSection(
    uiState: VoiceAnalysisUiState,
    onRetryAnalyze: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors

    when (uiState) {
        VoiceAnalysisUiState.Idle,
        VoiceAnalysisUiState.Recording,
        is VoiceAnalysisUiState.RecordedReady -> Unit

        VoiceAnalysisUiState.Analyzing -> {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(1.dp, colors.teal.copy(alpha = 0.45f)),
                modifier = modifier
                    .fillMaxWidth()
                    .testTag("voice_analyzing_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CircularProgressIndicator(
                        color = colors.teal,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(28.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Comparing speech with text...",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Aligning matched words, mistakes, missed terms, and extra pronunciations offline.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.inkMuted
                        )
                    }
                }
            }
        }

        is VoiceAnalysisUiState.Error -> {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = colors.redLight),
                border = BorderStroke(1.dp, colors.red.copy(alpha = 0.55f)),
                modifier = modifier
                    .fillMaxWidth()
                    .testTag("voice_error_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "Check error",
                            tint = colors.red,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Microphone / Speech Check Notice",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkBold
                        )
                    }
                    Text(
                        text = uiState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.ink
                    )
                    Button(
                        onClick = onRetryAnalyze,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.red,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("voice_retry_analyze_btn")
                    ) {
                        Text(
                            text = "Try Again",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        is VoiceAnalysisUiState.Success -> {
            VoiceSuccessBreakdownCard(
                result = uiState.result,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun VoiceSuccessBreakdownCard(
    result: VoiceAnalysisResult,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    val metrics = result.metrics
    val comp = metrics.comparison

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Hero Score & Horizontal-Scroll Diagnostic Metrics Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("voice_metrics_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                colors.tealLight.copy(alpha = if (colors.isDark) 0.16f else 0.35f),
                                colors.surface
                            )
                        )
                    )
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colors.tealLight
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = colors.tealDark,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = result.engineLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.tealDark,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    if (comp != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (comp.netAccuracyPercent >= 80) colors.greenLight else colors.amberLight
                        ) {
                            Text(
                                text = "Net Accuracy ${comp.netAccuracyPercent}%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (comp.netAccuracyPercent >= 80) colors.green else colors.amber,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                if (comp != null && comp.totalReferenceWords > 0) {
                    val accentColor = when {
                        comp.matchPercent >= 80 -> colors.green
                        comp.matchPercent >= 50 -> colors.teal
                        else -> colors.amber
                    }
                    val animatedProgress by animateFloatAsState(
                        targetValue = (comp.matchPercent / 100f).coerceIn(0f, 1f),
                        animationSpec = tween(durationMillis = 650),
                        label = "match_ring_progress"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(74.dp)
                        ) {
                            Canvas(modifier = Modifier.size(74.dp)) {
                                val strokeWidth = 7.dp.toPx()
                                drawArc(
                                    color = colors.surfaceVariant,
                                    startAngle = -90f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                )
                                drawArc(
                                    color = accentColor,
                                    startAngle = -90f,
                                    sweepAngle = 360f * animatedProgress,
                                    useCenter = false,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                )
                            }
                            Text(
                                text = "${comp.matchPercent}%",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = colors.inkBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${comp.matchedCount} of ${comp.totalReferenceWords} reference words matched",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold
                            )
                            Text(
                                text = "${comp.totalSpokenWords} words spoken • ${comp.paceLabel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.inkMuted
                            )
                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                color = accentColor,
                                trackColor = colors.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        }
                    }

                    // Horizontally scrollable metric strip (never tiles or stacks vertically)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VoiceMetricChip(
                            label = "Matched",
                            value = "${comp.matchedCount} / ${comp.totalReferenceWords}",
                            fg = colors.green,
                            bg = colors.greenLight
                        )
                        if (comp.nearMatchCount > 0) {
                            VoiceMetricChip(
                                label = "Mic Near-Match",
                                value = "${comp.nearMatchCount} (accepted)",
                                fg = colors.tealDark,
                                bg = colors.tealLight
                            )
                        }
                        VoiceMetricChip(
                            label = "Mistakes (Wrong)",
                            value = "${comp.mistakeCount}",
                            fg = if (comp.mistakeCount == 0) colors.green else colors.red,
                            bg = if (comp.mistakeCount == 0) colors.greenLight else colors.redLight
                        )
                        VoiceMetricChip(
                            label = "Missed (Skipped)",
                            value = "${comp.missedCount}",
                            fg = if (comp.missedCount == 0) colors.green else colors.inkBold,
                            bg = if (comp.missedCount == 0) colors.greenLight else colors.surfaceVariant
                        )
                        VoiceMetricChip(
                            label = "Extra Pronounced",
                            value = "${comp.extraCount}",
                            fg = if (comp.extraCount == 0) colors.green else colors.amber,
                            bg = if (comp.extraCount == 0) colors.greenLight else colors.amberLight
                        )
                        VoiceMetricChip(
                            label = "Repeated Words",
                            value = "${comp.repetitionCount}",
                            fg = if (comp.repetitionCount == 0) colors.green else colors.amber,
                            bg = if (comp.repetitionCount == 0) colors.greenLight else colors.amberLight
                        )
                        VoiceMetricChip(
                            label = "Speaking Pace",
                            value = if (metrics.estimatedWpm > 0) "${metrics.estimatedWpm} WPM" else "Untimed",
                            fg = colors.tealDark,
                            bg = colors.tealLight
                        )
                        metrics.keywordCoveragePercent?.let { kw ->
                            VoiceMetricChip(
                                label = "Key Concepts",
                                value = "$kw%",
                                fg = if (kw >= 75) colors.green else colors.tealDark,
                                bg = if (kw >= 75) colors.greenLight else colors.tealLight
                            )
                        }
                    }
                }
            }
        }

        // 2. Word-by-Word Alignment Comparison Card
        if (comp != null && comp.alignmentTokens.isNotEmpty()) {
            VoiceAlignmentComparisonCard(comp = comp)
        }

        // 3. Detailed Mistakes, Near-Matches, Extra Pronounced & Skipped Strips (Horizontally Scrollable)
        if (comp != null && (comp.mistakePairs.isNotEmpty() || comp.nearMatchPairs.isNotEmpty() || comp.extraWords.isNotEmpty() || comp.missedWords.isNotEmpty())) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(1.dp, colors.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("voice_mistake_breakdown_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (comp.nearMatchPairs.isNotEmpty()) {
                        Text(
                            text = "MIC NEAR-MATCHES (ACCEPTED PHONETIC / HOMOPHONE)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.tealDark
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            comp.nearMatchPairs.take(24).forEach { pair ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = colors.tealLight,
                                    border = BorderStroke(0.8.dp, colors.teal.copy(alpha = 0.45f))
                                ) {
                                    Text(
                                        text = "\"${pair.expected}\" ~ \"${pair.spoken}\"",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.tealDark,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                        if (comp.mistakePairs.isNotEmpty()) HorizontalDivider(color = colors.borderLight)
                    }
                    if (comp.mistakePairs.isNotEmpty()) {
                        Text(
                            text = "MISTAKES MADE (EXPECTED → SPOKEN)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.red
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            comp.mistakePairs.take(24).forEach { pair ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = colors.redLight,
                                    border = BorderStroke(0.8.dp, colors.red.copy(alpha = 0.45f))
                                ) {
                                    Text(
                                        text = "\"${pair.expected}\" → \"${pair.spoken}\"",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.red,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (comp.extraWords.isNotEmpty()) {
                        if (comp.mistakePairs.isNotEmpty()) HorizontalDivider(color = colors.borderLight)
                        Text(
                            text = "EXTRA PRONOUNCED / REPEATED WORDS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.amber
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            comp.extraWords.take(24).forEach { word ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = colors.amberLight,
                                    border = BorderStroke(0.8.dp, colors.amber.copy(alpha = 0.45f))
                                ) {
                                    Text(
                                        text = "+ $word",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.amber,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (comp.missedWords.isNotEmpty()) {
                        if (comp.mistakePairs.isNotEmpty() || comp.extraWords.isNotEmpty()) {
                            HorizontalDivider(color = colors.borderLight)
                        }
                        Text(
                            text = "MISSED / SKIPPED FROM SHOWN TEXT",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkMuted
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            comp.missedWords.take(24).forEach { word ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = colors.surfaceVariant,
                                    border = BorderStroke(0.8.dp, colors.border)
                                ) {
                                    Text(
                                        text = word,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = colors.inkMuted,
                                        textDecoration = TextDecoration.LineThrough,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                            if (comp.missedWords.size > 24) {
                                Text(
                                    text = "+${comp.missedWords.size - 24} more",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.inkMuted,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else if (comp != null && comp.totalReferenceWords > 0 && comp.matchPercent == 100) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.greenLight,
                border = BorderStroke(1.dp, colors.green.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = colors.green,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "100% Exact Match — zero mistakes, zero skipped words, and zero extra pronunciations!",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkBold
                    )
                }
            }
        }

        // 4. What You Spoke (Captured Text)
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("voice_transcript_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "WHAT YOU SPOKE (CAPTURED WORDS)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.tealDark
                    )
                    if (comp != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colors.tealLight
                        ) {
                            Text(
                                text = "${comp.totalSpokenWords} words",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.tealDark,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
                Text(
                    text = result.verbatimTranscript.ifBlank { "No words were recognized. Please speak clearly or type your text." },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (result.verbatimTranscript.isBlank()) colors.inkMuted else colors.inkBold
                )
            }
        }

        // 5. Offline Feedback & Strengths
        if (result.strengths.isNotEmpty() || result.coachingTips.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(1.dp, colors.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = colors.teal,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "SPEAKING INSIGHTS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.tealDark
                        )
                    }
                    result.strengths.forEach { s ->
                        Text(
                            text = "✓ $s",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.green,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    result.coachingTips.forEach { tip ->
                        Text(
                            text = "• $tip",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.inkBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceAlignmentComparisonCard(comp: VoiceComparisonStats) {
    val colors = ExamTheme.colors

    val alignedText = remember(comp.alignmentTokens, colors) {
        buildAnnotatedString {
            comp.alignmentTokens.forEachIndexed { index, token ->
                if (index > 0) append(" ")
                when (token.status) {
                    VoiceWordMatchStatus.MATCHED -> {
                        withStyle(SpanStyle(color = colors.green, fontWeight = FontWeight.SemiBold)) {
                            append(token.expectedWord.orEmpty())
                        }
                    }
                    VoiceWordMatchStatus.NEAR_MATCH -> {
                        withStyle(
                            SpanStyle(
                                background = colors.tealLight,
                                color = colors.tealDark,
                                fontWeight = FontWeight.SemiBold
                            )
                        ) {
                            append(" ${token.expectedWord.orEmpty()}(~${token.spokenWord.orEmpty()}) ")
                        }
                    }
                    VoiceWordMatchStatus.MISTAKE -> {
                        withStyle(
                            SpanStyle(
                                background = colors.redLight,
                                color = colors.red,
                                fontWeight = FontWeight.Bold
                            )
                        ) {
                            append(" ${token.expectedWord.orEmpty()}→${token.spokenWord.orEmpty()} ")
                        }
                    }
                    VoiceWordMatchStatus.MISSED -> {
                        withStyle(
                            SpanStyle(
                                color = colors.inkMuted,
                                textDecoration = TextDecoration.LineThrough
                            )
                        ) {
                            append(token.expectedWord.orEmpty())
                        }
                    }
                    VoiceWordMatchStatus.EXTRA -> {
                        withStyle(
                            SpanStyle(
                                background = colors.amberLight,
                                color = colors.amber,
                                fontWeight = FontWeight.Bold
                            )
                        ) {
                            append(" +${token.spokenWord.orEmpty()} ")
                        }
                    }
                    VoiceWordMatchStatus.REPEATED -> {
                        withStyle(
                            SpanStyle(
                                background = colors.amberLight,
                                color = colors.amber,
                                fontWeight = FontWeight.Bold
                            )
                        ) {
                            append(" ↻${token.spokenWord.orEmpty()} ")
                        }
                    }
                }
            }
        }
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("voice_alignment_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Default.CompareArrows,
                    contentDescription = null,
                    tint = colors.teal,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "WORD-BY-WORD MATCH AGAINST TEXT",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.tealDark
                )
            }

            // Horizontally scrollable legend strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendPill(dotColor = colors.green, label = "Matched")
                if (comp.nearMatchCount > 0) {
                    LegendPill(dotColor = colors.teal, label = "Near-Match (accepted)")
                }
                LegendPill(dotColor = colors.red, label = "Mistake (expected→spoken)")
                LegendPill(dotColor = colors.inkMuted, label = "Missed (crossed)")
                LegendPill(dotColor = colors.amber, label = "+Extra / ↻Repeat")
            }

            HorizontalDivider(color = colors.borderLight)

            Text(
                text = alignedText,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun LegendPill(dotColor: Color, label: String) {
    val colors = ExamTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.inkMuted,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun VoiceMetricChip(
    label: String,
    value: String,
    fg: Color,
    bg: Color
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bg,
        border = BorderStroke(0.8.dp, fg.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = fg.copy(alpha = 0.85f),
                maxLines = 1,
                softWrap = false
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = fg,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
