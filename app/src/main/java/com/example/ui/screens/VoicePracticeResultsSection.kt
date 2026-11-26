package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.model.VoiceAnalysisResult
import com.example.model.VoiceAnalysisUiState
import com.example.model.VoiceFillerCategory
import com.example.ui.theme.ExamTheme

@OptIn(ExperimentalLayoutApi::class)
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
                shape = RoundedCornerShape(16.dp),
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
                            text = "Analyzing speech & em/um fillers...",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Transcribing audio verbatim to surface hesitations, pacing, and subject clarity.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.inkMuted
                        )
                    }
                }
            }
        }

        is VoiceAnalysisUiState.Error -> {
            Card(
                shape = RoundedCornerShape(16.dp),
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
                            contentDescription = "Analysis error",
                            tint = colors.red,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Could not complete audio analysis",
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
                        Text("Retry Analysis", fontWeight = FontWeight.Bold)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VoiceSuccessBreakdownCard(
    result: VoiceAnalysisResult,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    val metrics = result.metrics

    val annotatedTranscript = remember(result.annotatedSegments, colors) {
        buildAnnotatedString {
            result.annotatedSegments.forEachIndexed { index, seg ->
                if (index > 0) append(" ")
                if (seg.isFiller) {
                    val isVocal = seg.category == VoiceFillerCategory.VOCAL_DISFLUENCY
                    withStyle(
                        SpanStyle(
                            background = if (isVocal) colors.amberLight else colors.tealLight,
                            color = if (isVocal) colors.amber else colors.tealDark,
                            fontWeight = FontWeight.ExtraBold
                        )
                    ) {
                        append(" ${seg.text} ")
                    }
                } else {
                    append(seg.text)
                }
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Telemetry Grid
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("voice_metrics_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (result.usedAiAudioAnalysis) Icons.Default.AutoAwesome else Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = colors.teal,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = result.engineLabel,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.tealDark
                        )
                    }
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VoiceMetricChip(
                        label = "Fillers (em/um)",
                        value = "${metrics.verbatimFillerCount}",
                        fg = if (metrics.verbatimFillerCount == 0) colors.green else colors.amber,
                        bg = if (metrics.verbatimFillerCount == 0) colors.greenLight else colors.amberLight
                    )
                    VoiceMetricChip(
                        label = "Speaking Pace",
                        value = "${metrics.estimatedWpm} WPM",
                        fg = colors.tealDark,
                        bg = colors.tealLight
                    )
                    VoiceMetricChip(
                        label = "Hesitation Pauses",
                        value = "${metrics.hesitationPauseCount} (${formatDecimalSec(metrics.silenceMs)}s)",
                        fg = colors.inkBold,
                        bg = colors.surfaceVariant
                    )
                    result.topicAccuracyPercent?.let { acc ->
                        VoiceMetricChip(
                            label = "Subject Accuracy",
                            value = "$acc%",
                            fg = if (acc >= 70) colors.green else colors.tealDark,
                            bg = if (acc >= 70) colors.greenLight else colors.tealLight
                        )
                    }
                }

                // Exact Filler Token Breakdown
                HorizontalDivider(color = colors.borderLight)
                Text(
                    text = "DETECTED FILLER SOUNDS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkMuted
                )
                if (metrics.fillerBreakdown.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = colors.greenLight,
                        border = BorderStroke(0.8.dp, colors.green.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = colors.green,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Clean delivery — no em, um, uh, or filler words detected.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.inkBold
                            )
                        }
                    }
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        metrics.fillerBreakdown.forEach { item ->
                            val isVocal = item.category == VoiceFillerCategory.VOCAL_DISFLUENCY
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isVocal) colors.amberLight else colors.tealLight,
                                border = BorderStroke(
                                    0.8.dp,
                                    (if (isVocal) colors.amber else colors.teal).copy(alpha = 0.55f)
                                )
                            ) {
                                Text(
                                    text = "\"${item.token}\" × ${item.count}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isVocal) colors.amber else colors.tealDark,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Highlighted Verbatim Transcript
        Card(
            shape = RoundedCornerShape(16.dp),
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
                Text(
                    text = "VERBATIM TRANSCRIPT",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.tealDark
                )
                if (result.verbatimTranscript.isBlank()) {
                    Text(
                        text = "No clear speech detected in this recording.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.inkMuted
                    )
                } else {
                    Text(
                        text = annotatedTranscript,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.inkBold
                    )
                }
            }
        }

        // 3. Strengths & Coaching Tips
        if (result.strengths.isNotEmpty() || result.coachingTips.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(16.dp),
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
                            text = "DELIVERY COACHING",
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
private fun VoiceMetricChip(
    label: String,
    value: String,
    fg: Color,
    bg: Color
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bg,
        border = BorderStroke(0.8.dp, fg.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = colorsForLabel(fg)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = fg
            )
        }
    }
}

private fun colorsForLabel(fg: Color): Color = fg.copy(alpha = 0.85f)

private fun formatDecimalSec(ms: Long): String {
    val sec = ms / 1000.0
    return String.format(java.util.Locale.US, "%.1f", sec)
}
