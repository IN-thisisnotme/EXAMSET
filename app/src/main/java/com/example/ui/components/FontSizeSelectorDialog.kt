package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.AppFontScaleMode
import com.example.ui.theme.ExamTheme
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FontSizeSelectorDialog(
    currentMode: AppFontScaleMode,
    onModeSelected: (AppFontScaleMode) -> Unit,
    onDismissRequest: () -> Unit
) {
    val colors = ExamTheme.colors
    val scrollState = rememberScrollState()

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            val maxDialogHeight = (maxHeight * 0.92f).coerceAtLeast(300.dp)
            // [DEPRECATED 2026-11-25] Reason: Unconstrained 0.92f dialog stretched across tablets and fixed 680dp overflowed phone landscape. Restore if: reverting to fixed 680dp cap.
            // modifier = Modifier.fillMaxWidth(0.92f).heightIn(max = 680.dp)
            Surface(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxDialogHeight)
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, colors.border, RoundedCornerShape(24.dp))
                    .testTag("font_size_selector_dialog"),
                color = colors.surface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(scrollState)
                ) {
                    // Header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(colors.tealLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FormatSize,
                                contentDescription = "Typography Scale",
                                tint = colors.tealDark,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Typography & Scaling",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold
                            )
                            Text(
                                text = "Follows your device font size or locks a fixed reading scale",
                                fontSize = 12.sp,
                                color = colors.inkMuted,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Informational note
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surfaceElevated)
                            .border(1.dp, colors.borderLight, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = colors.teal,
                                modifier = Modifier
                                    .size(18.dp)
                                    .padding(top = 1.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Follow Device adapts cleanly to Android OS font settings (85%–160%). Choose Standard (100% Fixed) anytime to lock typography at 1.0x.",
                                fontSize = 12.sp,
                                color = colors.ink,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Mode Selection Options
                    val systemFontScale = LocalConfiguration.current.fontScale.coerceAtLeast(0.5f)
                    AppFontScaleMode.entries.forEach { mode ->
                        val isSelected = mode == currentMode
                        val effectiveTitle = if (mode == AppFontScaleMode.FOLLOW_SYSTEM) {
                            // [DEPRECATED 2026-11-25] Reason: Expanded OS font display badge from 1.35f to 1.6f to match MainActivity clamp. Restore if: restricting clamp to 1.35f.
                            // val clamped = (systemFontScale.coerceIn(0.85f, 1.35f) * 100f).roundToInt()
                            val clamped = (systemFontScale.coerceIn(0.85f, 1.6f) * 100f).roundToInt()
                            "${mode.title} • Now $clamped%"
                        } else mode.title
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onModeSelected(mode) }
                                .testTag("font_mode_${mode.name.lowercase()}"),
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) colors.tealLight.copy(alpha = 0.5f) else colors.surfaceElevated,
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) colors.teal else colors.borderLight
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .border(
                                            width = 1.5.dp,
                                            color = if (isSelected) colors.teal else colors.border,
                                            shape = CircleShape
                                        )
                                        .background(if (isSelected) colors.teal else colors.surface),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = colors.surface,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = effectiveTitle,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        color = if (isSelected) colors.tealDark else colors.inkBold,
                                        softWrap = true
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = mode.subtitle,
                                        fontSize = 11.sp,
                                        color = colors.inkMuted,
                                        lineHeight = 15.sp,
                                        softWrap = true
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Live Preview Card
                    Text(
                        text = "Live Typography Preview",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, colors.border, RoundedCornerShape(12.dp)),
                        color = colors.surface,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Q1. Find the rate of change for f(x) = 3x² + 5x at x = 2.",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.inkBold,
                                softWrap = true
                            )
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(text = "A. 17", fontSize = 12.sp, color = colors.ink)
                                Text(text = "B. 12", fontSize = 12.sp, color = colors.ink)
                                Text(text = "C. 21", fontSize = 12.sp, color = colors.ink)
                                Text(text = "D. 9", fontSize = 12.sp, color = colors.ink)
                            }
                            Text(
                                text = "Vocab: Perspicacious • /ˌpɜː.spɪˈkeɪ.ʃəs/ • C2 (UK)",
                                fontSize = 12.sp,
                                color = colors.tealDark,
                                fontWeight = FontWeight.Medium,
                                softWrap = true
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Bottom Action
                    Button(
                        onClick = onDismissRequest,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("close_font_size_dialog_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.teal,
                            contentColor = colors.surface
                        )
                    ) {
                        Text(
                            text = "Apply & Done",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
