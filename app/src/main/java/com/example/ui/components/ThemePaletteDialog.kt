package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.ColorSchemePreset
import com.example.data.ThemeCategory
import com.example.data.ThemeMode
import com.example.ui.MainViewModel
import com.example.ui.theme.ExamTheme
import com.example.ui.theme.resolveExamColorPalette

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemePaletteDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val colors = ExamTheme.colors
    val context = LocalContext.current
    val currentThemeMode by viewModel.themeMode.collectAsState()
    val activePreset by viewModel.colorSchemePreset.collectAsState()
    var selectedCategory by remember { mutableStateOf(ThemeCategory.ALL) }

    val filteredPresets = remember(selectedCategory) {
        if (selectedCategory == ThemeCategory.ALL) {
            ColorSchemePreset.entries
        } else {
            ColorSchemePreset.entries.filter { it.category == selectedCategory }
        }
    }

    val previewCanvasBg by animateColorAsState(colors.background, tween(220), label = "preview_bg")
    val previewCardBg by animateColorAsState(colors.surface, tween(220), label = "preview_card")
    val previewBannerBg by animateColorAsState(colors.navyBanner, tween(220), label = "preview_banner")
    val previewBtnBg by animateColorAsState(colors.primaryButtonBg, tween(220), label = "preview_btn")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth(0.94f)
                .padding(vertical = 16.dp)
                .testTag("theme_palette_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            border = BorderStroke(1.dp, colors.border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 18.dp)
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
                            .background(colors.tealLight)
                            .border(1.dp, colors.borderLight, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "Appearance and Theme",
                            tint = colors.inkBold,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Appearance & Theme",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkBold
                        )
                        Text(
                            text = "Active: ${activePreset.title} • ${if (colors.isDark) "Dark" else "Light"}",
                            fontSize = 12.sp,
                            color = colors.inkMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Live Theme Preview Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = previewCanvasBg,
                    border = BorderStroke(1.dp, colors.border)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = previewBannerBg,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Live Preview • ${activePreset.title}",
                                    color = colors.navyBannerText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color.White.copy(alpha = 0.16f)
                                ) {
                                    Text(
                                        text = if (colors.isDark) "DARK" else "LIGHT",
                                        color = colors.navyBannerText,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = previewCardBg,
                            border = BorderStroke(1.dp, colors.borderLight),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Focused Typography & Ink",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.inkBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Card contrast, borders & muted labels",
                                        fontSize = 11.sp,
                                        color = colors.inkMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = previewBtnBg
                                ) {
                                    Text(
                                        text = "Sample CTA",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.primaryButtonText,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Mode Selector (System, Light, Dark)
                Text(
                    text = "THEME MODE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkMuted,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceVariant)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val modes = listOf(
                        Triple(ThemeMode.SYSTEM, "System", Icons.Default.BrightnessAuto),
                        Triple(ThemeMode.LIGHT, "Light", Icons.Default.LightMode),
                        Triple(ThemeMode.DARK, "Dark", Icons.Default.DarkMode)
                    )

                    modes.forEach { (mode, label, icon) ->
                        val isSelected = currentThemeMode == mode
                        val btnBg by animateColorAsState(
                            targetValue = if (isSelected) colors.surface else Color.Transparent,
                            animationSpec = tween(200),
                            label = "mode_bg"
                        )
                        val btnText = if (isSelected) colors.inkBold else colors.inkMuted
                        val btnIcon = if (isSelected) colors.inkBold else colors.inkMuted

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.setThemeMode(mode) }
                                .testTag("theme_mode_${mode.name.lowercase()}"),
                            shape = RoundedCornerShape(8.dp),
                            color = btnBg,
                            border = if (isSelected) BorderStroke(1.dp, colors.border) else null
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = btnIcon,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = btnText,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = colors.border.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(12.dp))

                // Category Filter Bar (Horizontal Scroll)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "COLOR THEMES (${filteredPresets.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkMuted,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = "Swipe to browse",
                        fontSize = 10.5.sp,
                        color = colors.inkMuted
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeCategory.entries.forEach { cat ->
                        val isCatSelected = selectedCategory == cat
                        FilterChip(
                            selected = isCatSelected,
                            onClick = { selectedCategory = cat },
                            label = {
                                Text(
                                    text = cat.label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.primaryButtonBg,
                                selectedLabelColor = colors.primaryButtonText,
                                containerColor = colors.surfaceVariant,
                                labelColor = colors.ink
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isCatSelected,
                                borderColor = colors.border,
                                selectedBorderColor = colors.primaryButtonBg
                            ),
                            modifier = Modifier.testTag("theme_category_${cat.name.lowercase()}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Color Schemes List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 310.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(filteredPresets, key = { it.name }) { preset ->
                        val isSelected = activePreset == preset
                        val previewPalette = resolveExamColorPalette(preset, colors.isDark, context)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { viewModel.setColorSchemePreset(preset) }
                                .testTag("color_preset_${preset.name.lowercase()}"),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) {
                                    colors.tealLight.copy(alpha = if (colors.isDark) 0.55f else 0.7f)
                                } else {
                                    colors.surfaceVariant.copy(alpha = 0.45f)
                                }
                            ),
                            border = BorderStroke(
                                width = if (isSelected) 1.8.dp else 1.dp,
                                color = if (isSelected) colors.primaryButtonBg else colors.border
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Compact 4-color swatch pill
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, colors.border, RoundedCornerShape(8.dp)),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 12.dp, height = 28.dp)
                                            .background(previewPalette.background)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(width = 12.dp, height = 28.dp)
                                            .background(previewPalette.surface)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(width = 12.dp, height = 28.dp)
                                            .background(previewPalette.teal)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(width = 12.dp, height = 28.dp)
                                            .background(previewPalette.navyBanner)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = preset.title,
                                            fontSize = 14.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                            color = if (isSelected) colors.inkBold else colors.ink,
                                            modifier = Modifier.align(Alignment.CenterVertically)
                                        )
                                        if (preset.styleTag.isNotBlank()) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (preset == ColorSchemePreset.MINIMAL) {
                                                    colors.primaryButtonBg.copy(alpha = if (colors.isDark) 0.18f else 0.1f)
                                                } else {
                                                    colors.surface
                                                },
                                                border = BorderStroke(0.6.dp, colors.border),
                                                modifier = Modifier.align(Alignment.CenterVertically)
                                            ) {
                                                Text(
                                                    text = preset.styleTag,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = colors.inkMuted,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = preset.subtitle,
                                        fontSize = 11.5.sp,
                                        color = colors.inkMuted,
                                        lineHeight = 15.sp
                                    )
                                }

                                if (isSelected) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(colors.primaryButtonBg),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected theme",
                                            tint = colors.primaryButtonText,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Done Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("theme_dialog_done"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primaryButtonBg,
                        contentColor = colors.primaryButtonText
                    )
                ) {
                    Text("Apply & Close", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}
