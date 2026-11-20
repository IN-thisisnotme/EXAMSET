package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.IconButton
import com.example.model.KnowledgeCollectionType
import com.example.model.KnowledgeDepth
import com.example.model.KnowledgeFormatRule
import com.example.model.KnowledgeLayerOrder
import com.example.model.KnowledgePromptConfig
import com.example.ui.theme.ExamColorPalette
import com.example.util.PaperLanguageDetector
import com.example.util.StudyNoteParser
import kotlin.math.roundToInt

class KnowledgePromptState {
    var examTarget by mutableStateOf("")
    var subject by mutableStateOf("")
    var topic by mutableStateOf("")
    var collectionType by mutableStateOf(KnowledgeCollectionType.MULTI_LINER)
    var customCollectionLabel by mutableStateOf("")
    var itemCount by mutableFloatStateOf(20f)
    var depth by mutableStateOf(KnowledgeDepth.BALANCED)
    var layerOrder by mutableStateOf(KnowledgeLayerOrder.AUTO_DETECT)
    var language by mutableStateOf("English")
    var includeBilingualTerms by mutableStateOf(false)
    var preserveNestedBullets by mutableStateOf(true)
    var includeLatexMath by mutableStateOf(true)
    var includeCallouts by mutableStateOf(true)
    var includeComparisonTable by mutableStateOf(false)
    var includePyqTags by mutableStateOf(false)
    var customInstruction by mutableStateOf("")

    fun toFormatRule(): KnowledgeFormatRule {
        return KnowledgeFormatRule(
            collectionType = collectionType,
            customCollectionLabel = customCollectionLabel.trim(),
            layerOrder = layerOrder,
            depth = depth,
            language = language.trim().ifBlank { "English" },
            preserveNestedBullets = preserveNestedBullets,
            includeLatexMath = includeLatexMath,
            includeCallouts = includeCallouts,
            includeComparisonTable = includeComparisonTable,
            customInstruction = customInstruction.trim()
        )
    }

    fun buildPrompt(): String {
        val resolvedTopic = topic.trim().ifBlank { subject.trim() }
        return StudyNoteParser.compilePrompt(
            topic = resolvedTopic,
            collectionType = collectionType,
            rule = toFormatRule(),
            examTarget = examTarget,
            subject = subject,
            itemCount = itemCount.toInt(),
            includeBilingualTerms = includeBilingualTerms,
            includePyqTags = includePyqTags
        )
    }

    fun toConfig(): KnowledgePromptConfig = KnowledgePromptConfig(
        examTarget = examTarget,
        subject = subject,
        collectionType = collectionType,
        customCollectionLabel = customCollectionLabel,
        itemCount = itemCount.toInt(),
        depth = depth,
        layerOrder = layerOrder,
        language = language,
        includeBilingualTerms = includeBilingualTerms,
        preserveNestedBullets = preserveNestedBullets,
        includeLatexMath = includeLatexMath,
        includeCallouts = includeCallouts,
        includeComparisonTable = includeComparisonTable,
        includePyqTags = includePyqTags,
        customInstruction = customInstruction
    ).normalized()

    fun applyConfig(config: KnowledgePromptConfig, preserveTopic: Boolean = true) {
        val c = config.normalized()
        examTarget = c.examTarget
        subject = c.subject
        if (!preserveTopic) topic = ""
        collectionType = c.collectionType
        customCollectionLabel = c.customCollectionLabel
        itemCount = c.itemCount.toFloat()
        depth = c.depth
        layerOrder = c.layerOrder
        language = c.language
        includeBilingualTerms = c.includeBilingualTerms
        preserveNestedBullets = c.preserveNestedBullets
        includeLatexMath = c.includeLatexMath
        includeCallouts = c.includeCallouts
        includeComparisonTable = c.includeComparisonTable
        includePyqTags = c.includePyqTags
        customInstruction = c.customInstruction
    }

    fun reset() {
        examTarget = ""
        subject = ""
        topic = ""
        collectionType = KnowledgeCollectionType.MULTI_LINER
        customCollectionLabel = ""
        itemCount = 20f
        depth = KnowledgeDepth.BALANCED
        layerOrder = KnowledgeLayerOrder.AUTO_DETECT
        language = "English"
        includeBilingualTerms = false
        preserveNestedBullets = true
        includeLatexMath = true
        includeCallouts = true
        includeComparisonTable = false
        includePyqTags = false
        customInstruction = ""
    }
}

@OptIn(ExperimentalLayoutApi::class)
fun LazyListScope.knowledgePromptBuilderItems(
    state: KnowledgePromptState,
    colors: ExamColorPalette,
    fieldColors: TextFieldColors,
    context: Context,
    haptic: HapticFeedback,
    copied: Boolean,
    onCopyPrompt: () -> Unit,
    onOpenKnowledgeStudio: (title: String, subject: String, collectionType: KnowledgeCollectionType, rule: KnowledgeFormatRule) -> Unit,
    showFooterButtons: Boolean = true,
    showAdvancedSettings: Boolean = true,
    onToggleAdvancedSettings: (() -> Unit)? = null
) {
    val countInt = state.itemCount.toInt()
    val compiledPrompt = state.buildPrompt()

    // 1. Study Scope & Topic
    item(key = "knowledge_scope_params") {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            border = BorderStroke(0.8.dp, colors.border)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "STUDY SCOPE & TOPIC",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkMuted,
                    letterSpacing = 0.5.sp
                )

                val examPresets = listOf(
                    "UPSC Civil Services",
                    "State PCS",
                    "SSC CGL / Banking",
                    "Railways / RRB",
                    "NEET / Biology",
                    "JEE / Physics",
                    "GATE / Engineering",
                    "USMLE / Clinical",
                    "BAR / Law Exam",
                    "SAT / ACT",
                    "School Board"
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    examPresets.forEach { preset ->
                        val isSelected = state.examTarget.equals(preset, ignoreCase = true)
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                state.examTarget = preset
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) colors.teal else colors.surfaceVariant,
                            border = BorderStroke(
                                if (isSelected) 1.2.dp else 0.6.dp,
                                if (isSelected) colors.tealDark else colors.border
                            )
                        ) {
                            Text(
                                text = preset,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) Color.White else colors.inkBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = state.examTarget,
                    onValueChange = { state.examTarget = it },
                    label = { Text("Exam Target / Level (Optional)", fontSize = 12.sp) },
                    placeholder = { Text("e.g. UPSC, NEET, JEE, University", fontSize = 12.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("knowledge_prompt_exam_input")
                )

                val quickSubjects = listOf(
                    "General Science",
                    "Modern Indian History",
                    "Polity & Governance",
                    "Geography & Climate",
                    "Organic Chemistry",
                    "Clinical Medicine",
                    "Physics & Mechanics",
                    "Economics & Finance"
                )
                Text(
                    text = "Quick Subject Ideas",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.inkMuted
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickSubjects.forEach { quickSub ->
                        val isSubSelected = state.subject.equals(quickSub, ignoreCase = true)
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                state.subject = quickSub
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSubSelected) colors.teal else colors.surfaceVariant.copy(alpha = 0.7f),
                            border = BorderStroke(
                                if (isSubSelected) 1.2.dp else 0.6.dp,
                                if (isSubSelected) colors.tealDark else colors.border
                            )
                        ) {
                            Text(
                                text = quickSub,
                                fontSize = 11.sp,
                                fontWeight = if (isSubSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSubSelected) Color.White else colors.inkBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = state.subject,
                    onValueChange = { state.subject = it },
                    label = { Text("Subject", fontSize = 12.sp) },
                    placeholder = { Text("e.g. Physics, Indian Polity, Biology", fontSize = 12.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("knowledge_prompt_subject_input")
                )

                OutlinedTextField(
                    value = state.topic,
                    onValueChange = { state.topic = it },
                    label = { Text("Chapter / Focus Topic", fontSize = 12.sp) },
                    placeholder = { Text("e.g. Fundamental Rights, Thermodynamics Laws", fontSize = 12.sp) },
                    singleLine = true,
                    trailingIcon = if (state.topic.isNotEmpty()) {
                        {
                            IconButton(
                                onClick = { state.topic = "" },
                                modifier = Modifier.testTag("knowledge_prompt_clear_topic_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear topic",
                                    tint = colors.inkMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else null,
                    shape = RoundedCornerShape(8.dp),
                    colors = fieldColors,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("knowledge_prompt_topic_input")
                )
            }
        }
    }

    if (!showAdvancedSettings && onToggleAdvancedSettings != null) {
        item(key = "knowledge_collapsed_settings_summary") {
            val summaryChips = state.toConfig().specChips()
            Surface(
                onClick = onToggleAdvancedSettings,
                shape = RoundedCornerShape(12.dp),
                color = colors.surface,
                border = BorderStroke(0.8.dp, colors.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("knowledge_expand_settings_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = colors.teal,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Format, Depth & Parser Rules",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.inkBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = (listOf(state.toConfig().formatBadge()) + summaryChips.take(3)).joinToString(" · "),
                            fontSize = 11.sp,
                            color = colors.inkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Customize",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.teal
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = "Expand knowledge settings",
                            tint = colors.teal,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    if (showAdvancedSettings) {
    // 2. Collection Format, Item Volume, Depth & Layer Order
    item(key = "knowledge_collection_format") {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            border = BorderStroke(0.8.dp, colors.border)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "COLLECTION FORMAT & DEPTH",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkMuted,
                    letterSpacing = 0.5.sp
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Knowledge Sheet Type",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.inkBold
                        )
                        Text(
                            text = state.collectionType.shortBadge,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.teal
                        )
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        KnowledgeCollectionType.entries.forEach { type ->
                            val isSelected = state.collectionType == type
                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    state.collectionType = type
                                    if (type == KnowledgeCollectionType.COMPARISON_TABLE) {
                                        state.includeComparisonTable = true
                                    } else if (type == KnowledgeCollectionType.FORMULA_SHEET) {
                                        state.includeLatexMath = true
                                    } else if (type == KnowledgeCollectionType.LONG_ANSWER && state.itemCount > 10f) {
                                        state.itemCount = 5f
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) colors.teal else colors.surfaceVariant,
                                border = BorderStroke(
                                    if (isSelected) 1.2.dp else 0.6.dp,
                                    if (isSelected) colors.tealDark else colors.border
                                ),
                                modifier = Modifier.testTag("knowledge_type_${type.code.lowercase()}")
                            ) {
                                Text(
                                    text = type.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isSelected) Color.White else colors.inkBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = state.collectionType.description,
                        fontSize = 10.5.sp,
                        color = colors.inkMuted,
                        lineHeight = 14.sp
                    )

                    if (state.collectionType == KnowledgeCollectionType.CUSTOM) {
                        OutlinedTextField(
                            value = state.customCollectionLabel,
                            onValueChange = { state.customCollectionLabel = it },
                            label = { Text("Custom Format Title", fontSize = 12.sp) },
                            placeholder = { Text("e.g. Case Law & Landmark Amendments", fontSize = 12.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = fieldColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                HorizontalDivider(color = colors.border.copy(alpha = 0.5f), thickness = 0.6.dp)

                // Item Count Selector & Slider
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val presetCounts = listOf(1, 5, 10, 15, 25, 40, 60)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Cards / Concepts Count",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.inkBold
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colors.tealLight,
                            border = BorderStroke(0.6.dp, colors.teal.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "$countInt Items",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.teal,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presetCounts.forEach { cnt ->
                            val isSelected = countInt == cnt
                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    state.itemCount = cnt.toFloat()
                                },
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSelected) colors.teal else colors.surfaceVariant,
                                border = BorderStroke(0.6.dp, if (isSelected) colors.teal else colors.border)
                            ) {
                                Text(
                                    text = if (cnt == 1) "1 Item" else "$cnt Items",
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else colors.inkBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Slider(
                        value = state.itemCount,
                        onValueChange = { newVal ->
                            val snapped = if (newVal < 5f) {
                                newVal.roundToInt().toFloat().coerceIn(1f, 4f)
                            } else {
                                ((newVal / 5f).roundToInt() * 5f).coerceIn(5f, 80f)
                            }
                            state.itemCount = snapped
                        },
                        valueRange = 1f..80f,
                        colors = SliderDefaults.colors(
                            thumbColor = colors.teal,
                            activeTrackColor = colors.teal,
                            inactiveTrackColor = colors.border
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider(color = colors.border.copy(alpha = 0.5f), thickness = 0.6.dp)

                // Concept Depth Selector
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Concept Depth",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.inkBold
                        )
                        Text(
                            text = state.depth.label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.teal
                        )
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        KnowledgeDepth.entries.forEach { d ->
                            val isSelected = state.depth == d
                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    state.depth = d
                                },
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSelected) colors.teal else colors.surfaceVariant,
                                border = BorderStroke(0.6.dp, if (isSelected) colors.teal else colors.border)
                            ) {
                                Text(
                                    text = d.label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) Color.White else colors.inkBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = colors.border.copy(alpha = 0.5f), thickness = 0.6.dp)

                // Layer Order Selector
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Layer Order (Prevents Backwards AI Output)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.inkBold
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        KnowledgeLayerOrder.entries.forEach { order ->
                            val isSelected = state.layerOrder == order
                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    state.layerOrder = order
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) colors.tealLight else colors.surfaceVariant.copy(alpha = 0.6f),
                                border = BorderStroke(
                                    if (isSelected) 1.3.dp else 0.6.dp,
                                    if (isSelected) colors.teal else colors.border
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                    Text(
                                        text = order.label,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) colors.teal else colors.inkBold
                                    )
                                    Text(
                                        text = order.hint,
                                        fontSize = 10.sp,
                                        lineHeight = 13.5.sp,
                                        color = if (isSelected) colors.tealDark else colors.inkMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 3. Language Settings
    item(key = "knowledge_language_config") {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            border = BorderStroke(0.8.dp, colors.border)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "LANGUAGE SETTINGS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkMuted,
                    letterSpacing = 0.5.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Output Notes Language",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkBold
                    )
                    Text(
                        text = "Any language supported",
                        fontSize = 10.5.sp,
                        color = colors.teal,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                OutlinedTextField(
                    value = state.language,
                    onValueChange = { state.language = it },
                    placeholder = { Text("e.g. English, Hindi, Marathi, Tamil, French...", fontSize = 12.sp, color = colors.muted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = colors.inkBold),
                    shape = RoundedCornerShape(8.dp),
                    colors = fieldColors
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PaperLanguageDetector.PRESET_LANGUAGES.forEach { lang ->
                        val isSelected = state.language.trim().equals(lang, ignoreCase = true)
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                state.language = lang
                            },
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) colors.teal else colors.surfaceVariant,
                            border = BorderStroke(0.6.dp, if (isSelected) colors.teal else colors.border)
                        ) {
                            Text(
                                text = lang,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else colors.inkBold,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                if (!state.language.trim().equals("English", ignoreCase = true)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { state.includeBilingualTerms = !state.includeBilingualTerms }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = state.includeBilingualTerms,
                            onCheckedChange = { state.includeBilingualTerms = it },
                            colors = CheckboxDefaults.colors(checkedColor = colors.teal)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Include English Terms in Brackets",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.inkBold
                            )
                            Text(
                                text = "Keeps standard scientific/technical terms alongside ${state.language.trim()}",
                                fontSize = 10.5.sp,
                                color = colors.muted
                            )
                        }
                    }
                }
            }
        }
    }

    // 4. Parser Rules & Format Sample
    item(key = "knowledge_parser_rules") {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            border = BorderStroke(0.8.dp, colors.border)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "PARSER RULES & FORMAT SAMPLE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkMuted,
                    letterSpacing = 0.5.sp
                )

                val sampleSnippet = StudyNoteParser.buildSampleTemplate(
                    collectionType = state.collectionType,
                    layerOrder = state.layerOrder,
                    includeLatexMath = state.includeLatexMath,
                    includeCallouts = state.includeCallouts,
                    includePyqTags = state.includePyqTags
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .border(0.8.dp, colors.border, RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Expected Output Sample",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.teal
                        )
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Knowledge Format Sample", sampleSnippet))
                                Toast.makeText(context, "Copied sample Knowledge format", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(6.dp),
                            color = colors.surface,
                            border = BorderStroke(0.6.dp, colors.teal.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = colors.teal, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy Sample", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = colors.teal)
                            }
                        }
                    }
                    Text(
                        text = sampleSnippet,
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 14.5.sp,
                        color = colors.inkBold
                    )
                }

                KnowledgeRuleCheckRow(
                    title = "Multi-Liner Nested Sub-Bullets",
                    subtitle = "2-space indented points under numbered concept cards",
                    checked = state.preserveNestedBullets,
                    onCheckedChange = { state.preserveNestedBullets = it },
                    colors = colors
                )
                KnowledgeRuleCheckRow(
                    title = "LaTeX Math & Chemical Equations",
                    subtitle = "Renders inline \$...\$, display \$\$...\$\$, matrices, roots & \\ce{...} in Reader & PDF",
                    checked = state.includeLatexMath,
                    onCheckedChange = { state.includeLatexMath = it },
                    colors = colors
                )
                KnowledgeRuleCheckRow(
                    title = "Exam Trap & Mnemonic Callouts",
                    subtitle = "Adds highlighted > Exam Tip: callout cards",
                    checked = state.includeCallouts,
                    onCheckedChange = { state.includeCallouts = it },
                    colors = colors
                )
                KnowledgeRuleCheckRow(
                    title = "Include Comparison Matrix Table",
                    subtitle = "Embeds Markdown side-by-side comparison tables",
                    checked = state.includeComparisonTable,
                    onCheckedChange = { state.includeComparisonTable = it },
                    colors = colors
                )
                KnowledgeRuleCheckRow(
                    title = "Include PYQ & High-Yield Tags",
                    subtitle = "Marks high-frequency exam concepts & past-year cues",
                    checked = state.includePyqTags,
                    onCheckedChange = { state.includePyqTags = it },
                    colors = colors
                )

                OutlinedTextField(
                    value = state.customInstruction,
                    onValueChange = { state.customInstruction = it },
                    label = { Text("Custom AI Instruction (Optional)", fontSize = 12.sp) },
                    placeholder = { Text("e.g. Focus on numerical exceptions and NCERT tables", fontSize = 12.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
    } // end showAdvancedSettings

    // 5. Constructed Knowledge Prompt Preview & CTAs
    item(key = "knowledge_constructed_prompt") {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            border = BorderStroke(0.8.dp, colors.border)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CONSTRUCTED KNOWLEDGE PROMPT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkMuted,
                        letterSpacing = 0.5.sp
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = colors.greenLight,
                        border = BorderStroke(0.5.dp, colors.green.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "${state.collectionType.shortBadge} • $countInt Items",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.green,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colors.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(0.6.dp, colors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = compiledPrompt,
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = colors.inkBold,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                if (showFooterButtons) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onCopyPrompt,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("knowledge_prompt_footer_copy_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (copied) colors.green else colors.navyBanner,
                                contentColor = colors.navyBannerText
                            )
                        ) {
                            Icon(
                                if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (copied) "Prompt Copied!" else "Copy Prompt",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                onOpenKnowledgeStudio(
                                    state.topic.trim().ifBlank { state.subject.trim() },
                                    state.subject.trim(),
                                    state.collectionType,
                                    state.toFormatRule()
                                )
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("knowledge_prompt_open_studio_button"),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = colors.teal,
                                containerColor = colors.surface
                            ),
                            border = BorderStroke(1.dp, colors.teal.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.LibraryBooks,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = colors.teal
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Open Studio",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }

    item(key = "knowledge_bottom_spacer") {
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun KnowledgeRuleCheckRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    colors: ExamColorPalette
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = colors.teal)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.inkBold
            )
            Text(
                text = subtitle,
                fontSize = 10.5.sp,
                color = colors.muted,
                lineHeight = 14.sp
            )
        }
    }
}
