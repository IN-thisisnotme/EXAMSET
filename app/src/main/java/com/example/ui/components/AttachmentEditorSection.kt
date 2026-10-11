package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.theme.ExamTheme
import com.example.util.RichTextHelper

/**
 * Interactive editor for attaching diagrams, charts, and chemical/mathematical formulas to questions.
 */
@Composable
fun AttachmentEditorSection(
    diagramUrl: String?,
    diagramLabel: String?,
    formula: String?,
    onDiagramChanged: (url: String?, label: String?) -> Unit,
    onFormulaChanged: (formula: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    val context = LocalContext.current

    var isExpanded by remember {
        mutableStateOf(!diagramUrl.isNullOrBlank() || !formula.isNullOrBlank())
    }

    var localUrl by remember(diagramUrl) { mutableStateOf(diagramUrl ?: "") }
    var localLabel by remember(diagramLabel) { mutableStateOf(diagramLabel ?: "") }
    var localFormula by remember(formula) { mutableStateOf(formula ?: "") }

    // Android Photo Picker launcher (zero permission, compliant with Play Store policy)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val uriString = uri.toString()
            localUrl = uriString
            onDiagramChanged(uriString, localLabel.ifBlank { "Question Diagram" })
        }
    }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant.copy(alpha = 0.4f)),
        border = BorderStroke(0.8.dp, colors.border),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Header Toggle Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Science,
                        contentDescription = null,
                        tint = colors.teal,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Diagram, Chart & Formula Attachments",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkBold
                    )
                    if (localUrl.isNotBlank() || localFormula.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = colors.teal.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "Active",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.teal,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = colors.ink
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // --- PART 1: DIAGRAM / CHART ATTACHMENT ---
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surface, RoundedCornerShape(8.dp))
                            .border(0.6.dp, colors.border, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Image, contentDescription = null, tint = colors.teal, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Diagram / Chart Image", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = colors.inkBold)
                            }
                            if (localUrl.isNotBlank()) {
                                TextButton(
                                    onClick = {
                                        localUrl = ""
                                        localLabel = ""
                                        onDiagramChanged(null, null)
                                    },
                                    modifier = Modifier.heightIn(min = 36.dp)
                                ) {
                                    Text("Remove Image", fontSize = 10.5.sp, color = colors.red)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Action Buttons: Pick Photo or Select Preset
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 40.dp)
                            ) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.teal)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Choose Photo", fontSize = 11.sp, color = colors.inkBold)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Preset Diagram Chips
                        Text("Quick Educational Presets:", fontSize = 10.sp, color = colors.muted)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val presets = listOf(
                                "Benzene Ring" to "https://images.unsplash.com/photo-1532187863486-abf9dbad1b69?w=600&auto=format&fit=crop&q=80",
                                "Cell Structure" to "https://images.unsplash.com/photo-1530026405186-ed1f139313f8?w=600&auto=format&fit=crop&q=80",
                                "Circuit / Physics" to "https://images.unsplash.com/photo-1518770660439-4636190af475?w=600&auto=format&fit=crop&q=80",
                                "Cartesian Graph" to "https://images.unsplash.com/photo-1551288049-bebda4e38f71?w=600&auto=format&fit=crop&q=80"
                            )
                            presets.forEach { (name, url) ->
                                Surface(
                                    onClick = {
                                        localUrl = url
                                        localLabel = "Figure: $name"
                                        onDiagramChanged(url, localLabel)
                                    },
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (localUrl == url) colors.teal.copy(alpha = 0.15f) else colors.surfaceVariant,
                                    border = BorderStroke(0.6.dp, if (localUrl == url) colors.teal else colors.border)
                                ) {
                                    Text(
                                        text = name,
                                        fontSize = 10.sp,
                                        fontWeight = if (localUrl == url) FontWeight.Bold else FontWeight.Normal,
                                        color = if (localUrl == url) colors.teal else colors.ink,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = localUrl,
                            onValueChange = {
                                localUrl = it
                                onDiagramChanged(it.ifBlank { null }, localLabel.ifBlank { null })
                            },
                            label = { Text("Image / Diagram URL or Local URI", fontSize = 10.5.sp) },
                            placeholder = { Text("https://... or content://...", fontSize = 10.5.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = localLabel,
                            onValueChange = {
                                localLabel = it
                                onDiagramChanged(localUrl.ifBlank { null }, it.ifBlank { null })
                            },
                            label = { Text("Figure / Chart Caption (optional)", fontSize = 10.5.sp) },
                            placeholder = { Text("e.g. Figure 1: Resonance Hybrid of Benzene", fontSize = 10.5.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Thumbnail Preview
                        if (localUrl.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White)
                                    .border(0.6.dp, colors.border, RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(localUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = localLabel,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize().padding(4.dp)
                                )
                            }
                        }
                    }

                    // --- PART 2: CHEMICAL & MATHEMATICAL FORMULA ---
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surface, RoundedCornerShape(8.dp))
                            .border(0.6.dp, colors.border, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Functions, contentDescription = null, tint = Color(0xFF6366F1), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Formula / Reaction / Equation", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = colors.inkBold)
                            }
                            if (localFormula.isNotBlank()) {
                                TextButton(
                                    onClick = {
                                        localFormula = ""
                                        onFormulaChanged(null)
                                    },
                                    modifier = Modifier.heightIn(min = 36.dp)
                                ) {
                                    Text("Clear", fontSize = 10.5.sp, color = colors.red)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Quick Math & Chemistry Symbol Palette
                        Text("Tap to insert symbols:", fontSize = 10.sp, color = colors.muted)
                        Spacer(modifier = Modifier.height(4.dp))

                        val symbols = listOf(
                            "→", "⇌", "Δ", "₂", "₃", "₄", "⁺", "⁻",
                            "√", "∫", "∑", "π", "θ", "α", "β", "∞", "±", "²", "³", "≤", "≥", "≠"
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            symbols.forEach { sym ->
                                Surface(
                                    onClick = {
                                        localFormula += sym
                                        onFormulaChanged(localFormula)
                                    },
                                    shape = RoundedCornerShape(4.dp),
                                    color = colors.surfaceVariant,
                                    border = BorderStroke(0.6.dp, colors.border)
                                ) {
                                    Text(
                                        text = sym,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.inkBold,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = localFormula,
                            onValueChange = {
                                localFormula = it
                                onFormulaChanged(it.ifBlank { null })
                            },
                            label = { Text("Formula Text (Chemical or Mathematical)", fontSize = 10.5.sp) },
                            placeholder = { Text("e.g. C₆H₁₂O₆ + 6O₂ → 6CO₂ + 6H₂O or f(x) = ∫ e^(-x²) dx", fontSize = 10.5.sp) },
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Live Rendered Formula Preview
                        if (localFormula.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Live Preview:", fontSize = 10.sp, color = colors.muted)
                            Spacer(modifier = Modifier.height(3.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(colors.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                    .border(0.6.dp, colors.border, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = RichTextHelper.buildRichText(
                                        text = localFormula,
                                        baseColor = colors.inkBold,
                                        baseFontSize = 14.sp,
                                        isBold = true
                                    ),
                                    fontFamily = FontFamily.Serif,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
