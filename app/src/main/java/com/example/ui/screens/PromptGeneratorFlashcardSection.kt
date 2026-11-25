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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Style
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.IconButton
import androidx.compose.runtime.remember
import com.example.model.FlashcardPromptConfig
import com.example.model.FlashcardPromptStyle
import com.example.model.FolderItem
import com.example.model.KnowledgeCollectionType
import com.example.model.KnowledgeDepth
import com.example.model.KnowledgeFormatRule
import com.example.model.KnowledgeLayerOrder
import com.example.ui.components.UserFolderDropdownMenu
import com.example.ui.theme.ExamColorPalette
import com.example.util.PaperLanguageDetector
import com.example.util.StudyNoteParser
import kotlin.math.roundToInt

class FlashcardPromptState {
    var examTarget by mutableStateOf("")
    var subject by mutableStateOf("")
    var topic by mutableStateOf("")
    var cardStyle by mutableStateOf(FlashcardPromptStyle.QA_RETRIEVAL)
    var cardCount by mutableFloatStateOf(20f)
    var depth by mutableStateOf(KnowledgeDepth.BALANCED)
    var language by mutableStateOf("English")
    var includeBilingualTerms by mutableStateOf(false)
    var atomicSrsMode by mutableStateOf(true)
    var includeMnemonics by mutableStateOf(true)
    var includeExamples by mutableStateOf(true)
    var includeLatexMath by mutableStateOf(true)
    var includeExamTraps by mutableStateOf(true)
    var customInstruction by mutableStateOf("")

    fun toFormatRule(): KnowledgeFormatRule {
        val mappedLayer = when (cardStyle) {
            FlashcardPromptStyle.QA_RETRIEVAL -> KnowledgeLayerOrder.QA_SEPARATE_LINES
            FlashcardPromptStyle.CLOZE_SCENARIO -> KnowledgeLayerOrder.BACKWARDS_ANSWER_FIRST
            FlashcardPromptStyle.TERM_DEFINITION,
            FlashcardPromptStyle.FORMULA_TRAP -> KnowledgeLayerOrder.TERM_FIRST
        }
        return KnowledgeFormatRule(
            collectionType = KnowledgeCollectionType.FLASHCARDS,
            customCollectionLabel = "Flashcards",
            layerOrder = mappedLayer,
            depth = depth,
            language = language.trim().ifBlank { "English" },
            preserveNestedBullets = includeMnemonics || includeExamples || includeExamTraps,
            includeLatexMath = includeLatexMath,
            includeCallouts = includeMnemonics || includeExamTraps,
            includeComparisonTable = false,
            customInstruction = customInstruction.trim()
        )
    }

    fun buildPrompt(): String {
        val resolvedTopic = topic.trim().ifBlank { subject.trim() }
        return StudyNoteParser.compileFlashcardPrompt(
            topic = resolvedTopic,
            subject = subject,
            examTarget = examTarget,
            itemCount = cardCount.toInt(),
            style = cardStyle,
            depth = depth,
            language = language,
            includeBilingualTerms = includeBilingualTerms,
            includeMnemonics = includeMnemonics,
            includeExamples = includeExamples,
            includeLatexMath = includeLatexMath,
            includeExamTraps = includeExamTraps,
            atomicSrsMode = atomicSrsMode,
            customInstruction = customInstruction
        )
    }

    fun toConfig(): FlashcardPromptConfig = FlashcardPromptConfig(
        examTarget = examTarget,
        subject = subject,
        cardStyle = cardStyle,
        cardCount = cardCount.toInt(),
        depth = depth,
        language = language,
        includeBilingualTerms = includeBilingualTerms,
        atomicSrsMode = atomicSrsMode,
        includeMnemonics = includeMnemonics,
        includeExamples = includeExamples,
        includeLatexMath = includeLatexMath,
        includeExamTraps = includeExamTraps,
        customInstruction = customInstruction
    ).normalized()

    fun applyConfig(config: FlashcardPromptConfig, preserveTopic: Boolean = true) {
        val c = config.normalized()
        examTarget = c.examTarget
        subject = c.subject
        if (!preserveTopic) topic = ""
        cardStyle = c.cardStyle
        cardCount = c.cardCount.toFloat()
        depth = c.depth
        language = c.language
        includeBilingualTerms = c.includeBilingualTerms
        atomicSrsMode = c.atomicSrsMode
        includeMnemonics = c.includeMnemonics
        includeExamples = c.includeExamples
        includeLatexMath = c.includeLatexMath
        includeExamTraps = c.includeExamTraps
        customInstruction = c.customInstruction
    }

    fun reset() {
        examTarget = ""
        subject = ""
        topic = ""
        cardStyle = FlashcardPromptStyle.QA_RETRIEVAL
        cardCount = 20f
        depth = KnowledgeDepth.BALANCED
        language = "English"
        includeBilingualTerms = false
        atomicSrsMode = true
        includeMnemonics = true
        includeExamples = true
        includeLatexMath = true
        includeExamTraps = true
        customInstruction = ""
    }
}

@OptIn(ExperimentalLayoutApi::class)
fun LazyListScope.flashcardPromptBuilderItems(
    state: FlashcardPromptState,
    colors: ExamColorPalette,
    fieldColors: TextFieldColors,
    context: Context,
    haptic: HapticFeedback,
    copied: Boolean,
    onCopyPrompt: () -> Unit,
    onOpenFlashcardStudio: (title: String, subject: String, collectionType: KnowledgeCollectionType, rule: KnowledgeFormatRule) -> Unit,
    showAdvancedSettings: Boolean = true,
    onToggleAdvancedSettings: (() -> Unit)? = null,
    folders: List<FolderItem> = emptyList()
) {
    val countInt = state.cardCount.toInt()
    val compiledPrompt = state.buildPrompt()

    // 1. Flashcard Scope & Deck Topic
    item(key = "flashcard_scope_params") {
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
                    text = "FLASHCARD DECK SCOPE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkMuted,
                    letterSpacing = 0.5.sp
                )

                // [DEPRECATED 2026-11-25] Reason: Replaced static examPresets strip with on-demand user folder hierarchy dropdowns (Parent > Child > Sub-Child > L4+). Restore if: Static preset strips are explicitly requested again.
                var isExamDropdownOpen by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = state.examTarget,
                        onValueChange = { state.examTarget = it },
                        label = { Text("Exam / Level Target (Optional)", fontSize = 12.sp) },
                        placeholder = { Text("e.g. University Course, Engineering, Certification", fontSize = 12.sp) },
                        singleLine = true,
                        trailingIcon = if (folders.isNotEmpty()) {
                            {
                                IconButton(
                                    onClick = { isExamDropdownOpen = !isExamDropdownOpen },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("flashcard_prompt_exam_dropdown_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Select folder for exam target",
                                        tint = colors.muted
                                    )
                                }
                            }
                        } else null,
                        shape = RoundedCornerShape(8.dp),
                        colors = fieldColors,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("flashcard_prompt_exam_input")
                    )

                    UserFolderDropdownMenu(
                        expanded = isExamDropdownOpen && folders.isNotEmpty(),
                        query = state.examTarget,
                        folders = folders,
                        onDismissRequest = { isExamDropdownOpen = false },
                        onSelectEntry = { entry ->
                            val segs = entry.fullPath.split(">").map { it.trim() }.filter { it.isNotEmpty() }
                            state.examTarget = segs.firstOrNull() ?: entry.fullPath
                            if (segs.size >= 2) {
                                state.subject = segs.drop(1).joinToString(" > ")
                            }
                            isExamDropdownOpen = false
                        }
                    )
                }

                var isSubjectDropdownOpen by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = state.subject,
                        onValueChange = { state.subject = it },
                        label = { Text("Subject / Course / Folder Path", fontSize = 12.sp) },
                        placeholder = { Text("e.g. Organic Chemistry, Thermodynamics, Law", fontSize = 12.sp) },
                        singleLine = true,
                        trailingIcon = if (folders.isNotEmpty()) {
                            {
                                IconButton(
                                    onClick = { isSubjectDropdownOpen = !isSubjectDropdownOpen },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("flashcard_prompt_subject_dropdown_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Select folder for subject",
                                        tint = colors.muted
                                    )
                                }
                            }
                        } else null,
                        shape = RoundedCornerShape(8.dp),
                        colors = fieldColors,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("flashcard_prompt_subject_input")
                    )

                    UserFolderDropdownMenu(
                        expanded = isSubjectDropdownOpen && folders.isNotEmpty(),
                        query = state.subject,
                        folders = folders,
                        onDismissRequest = { isSubjectDropdownOpen = false },
                        onSelectEntry = { entry ->
                            state.subject = entry.fullPath
                            isSubjectDropdownOpen = false
                        }
                    )
                }

                OutlinedTextField(
                    value = state.topic,
                    onValueChange = { state.topic = it },
                    label = { Text("Deck Topic / Chapter", fontSize = 12.sp) },
                    placeholder = { Text("e.g. Buffers & Acid-Base Equilibria, Fundamental Writs", fontSize = 12.sp) },
                    singleLine = true,
                    trailingIcon = if (state.topic.isNotEmpty()) {
                        {
                            IconButton(
                                onClick = { state.topic = "" },
                                modifier = Modifier.testTag("flashcard_prompt_clear_topic_button")
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
                        .testTag("flashcard_prompt_topic_input")
                )
            }
        }
    }

    if (!showAdvancedSettings && onToggleAdvancedSettings != null) {
        item(key = "flashcard_collapsed_settings_summary") {
            val summaryChips = state.toConfig().specChips()
            Surface(
                onClick = onToggleAdvancedSettings,
                shape = RoundedCornerShape(12.dp),
                color = colors.surface,
                border = BorderStroke(0.8.dp, colors.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("flashcard_expand_settings_card")
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
                            text = "Deck Style, Volume & SRS Rules",
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
                            contentDescription = "Expand flashcard settings",
                            tint = colors.teal,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    if (showAdvancedSettings) {
    // 2. Card Pattern, Volume & Depth
    item(key = "flashcard_pattern_volume") {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            border = BorderStroke(0.8.dp, colors.border)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "CARD PATTERN & VOLUME",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkMuted,
                    letterSpacing = 0.5.sp
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FlashcardPromptStyle.entries.forEach { style ->
                        val isSelected = state.cardStyle == style
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                state.cardStyle = style
                                if (style == FlashcardPromptStyle.FORMULA_TRAP) {
                                    state.includeLatexMath = true
                                    state.includeExamTraps = true
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) colors.tealLight else colors.surfaceVariant.copy(alpha = 0.55f),
                            border = BorderStroke(
                                if (isSelected) 1.4.dp else 0.7.dp,
                                if (isSelected) colors.teal else colors.border
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("flashcard_style_${style.name.lowercase()}")
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = style.label,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) colors.tealDark else colors.inkBold
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(5.dp),
                                        color = if (isSelected) colors.teal else colors.surface
                                    ) {
                                        Text(
                                            text = style.shortBadge,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else colors.inkMuted,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = style.description,
                                    fontSize = 10.5.sp,
                                    color = if (isSelected) colors.tealDark else colors.inkMuted,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = colors.border.copy(alpha = 0.5f), thickness = 0.6.dp)

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val presetCounts = listOf(10, 15, 20, 30, 40, 60)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Flashcard Count",
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
                                text = "$countInt Cards",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.teal,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        presetCounts.forEach { cnt ->
                            val isSelected = countInt == cnt
                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    state.cardCount = cnt.toFloat()
                                },
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSelected) colors.teal else colors.surfaceVariant,
                                border = BorderStroke(0.6.dp, if (isSelected) colors.teal else colors.border)
                            ) {
                                Text(
                                    text = "$cnt Cards",
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else colors.inkBold,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Slider(
                        value = state.cardCount,
                        onValueChange = { newVal ->
                            val snapped = ((newVal / 5f).roundToInt() * 5f).coerceIn(5f, 80f)
                            state.cardCount = snapped
                        },
                        valueRange = 5f..80f,
                        colors = SliderDefaults.colors(
                            thumbColor = colors.teal,
                            activeTrackColor = colors.teal,
                            inactiveTrackColor = colors.border
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider(color = colors.border.copy(alpha = 0.5f), thickness = 0.6.dp)

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Back-of-Card Detail Level",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.inkBold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
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
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 3. Language Settings
    item(key = "flashcard_language_config") {
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

                OutlinedTextField(
                    value = state.language,
                    onValueChange = { state.language = it },
                    placeholder = { Text("e.g. English, Hindi, Marathi, Tamil...", fontSize = 12.sp, color = colors.muted) },
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
                        Text(
                            text = "Keep technical English terms in parentheses",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.inkBold
                        )
                    }
                }
            }
        }
    }

    // 4. Spaced Repetition Rules & Format Sample
    item(key = "flashcard_srs_rules") {
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
                    text = "SPACED REPETITION RULES & SAMPLE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkMuted,
                    letterSpacing = 0.5.sp
                )

                val sampleText = StudyNoteParser.buildFlashcardSampleTemplate(
                    style = state.cardStyle,
                    includeLatexMath = state.includeLatexMath,
                    includeMnemonics = state.includeMnemonics,
                    includeExamples = state.includeExamples,
                    includeExamTraps = state.includeExamTraps
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
                            text = "Expected Flashcard Deck Format",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.teal
                        )
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Flashcard Sample Format", sampleText))
                                Toast.makeText(context, "Copied sample flashcard format", Toast.LENGTH_SHORT).show()
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
                        text = sampleText,
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 14.5.sp,
                        color = colors.inkBold
                    )
                }

                FlashcardCheckRow(
                    title = "Atomic SM-2 Cards (1 Fact per Card)",
                    subtitle = "Keeps Front prompts concise and isolates one concept for accurate SM-2 grading",
                    checked = state.atomicSrsMode,
                    onCheckedChange = { state.atomicSrsMode = it },
                    colors = colors
                )
                FlashcardCheckRow(
                    title = "Memory Mnemonics & Hooks",
                    subtitle = "Adds `- Mnemonic:` cues on back of card for tricky distinctions",
                    checked = state.includeMnemonics,
                    onCheckedChange = { state.includeMnemonics = it },
                    colors = colors
                )
                FlashcardCheckRow(
                    title = "Worked Examples & Applications",
                    subtitle = "Includes concise `- Example:` proof points on back of card",
                    checked = state.includeExamples,
                    onCheckedChange = { state.includeExamples = it },
                    colors = colors
                )
                FlashcardCheckRow(
                    title = "LaTeX Math & Chemical Equations",
                    subtitle = "Formats equations cleanly with `${'$'}...${'$'}` and `\\ce{...}`",
                    checked = state.includeLatexMath,
                    onCheckedChange = { state.includeLatexMath = it },
                    colors = colors
                )
                FlashcardCheckRow(
                    title = "Exam Trap Alerts",
                    subtitle = "Highlights common MCQ confusers and exceptions under `- Exam Trap:`",
                    checked = state.includeExamTraps,
                    onCheckedChange = { state.includeExamTraps = it },
                    colors = colors
                )

                OutlinedTextField(
                    value = state.customInstruction,
                    onValueChange = { state.customInstruction = it },
                    label = { Text("Custom Deck Instruction (Optional)", fontSize = 12.sp) },
                    placeholder = { Text("e.g. Emphasize differential diagnoses or constitutional articles", fontSize = 12.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
    } // end showAdvancedSettings

    // 5. Constructed Flashcard Prompt Preview & CTAs
    item(key = "flashcard_constructed_prompt") {
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
                        text = "CONSTRUCTED FLASHCARD PROMPT",
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
                            text = "${state.cardStyle.shortBadge} • $countInt Cards",
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

                Spacer(modifier = Modifier.height(12.dp))

                val stackFooterButtons = LocalDensity.current.fontScale >= 1.35f
                if (stackFooterButtons) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onCopyPrompt,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("flashcard_prompt_footer_copy_button"),
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
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                onOpenFlashcardStudio(
                                    state.topic.trim().ifBlank { state.subject.trim() },
                                    state.subject.trim(),
                                    KnowledgeCollectionType.FLASHCARDS,
                                    state.toFormatRule()
                                )
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("flashcard_prompt_open_studio_button"),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = colors.teal,
                                containerColor = colors.surface
                            ),
                            border = BorderStroke(1.dp, colors.teal.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Style,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = colors.teal
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Open Deck Studio",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                } else {
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
                                .testTag("flashcard_prompt_footer_copy_button"),
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
                                onOpenFlashcardStudio(
                                    state.topic.trim().ifBlank { state.subject.trim() },
                                    state.subject.trim(),
                                    KnowledgeCollectionType.FLASHCARDS,
                                    state.toFormatRule()
                                )
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("flashcard_prompt_open_studio_button"),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = colors.teal,
                                containerColor = colors.surface
                            ),
                            border = BorderStroke(1.dp, colors.teal.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Style,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = colors.teal
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Open Deck Studio",
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

    item(key = "flashcard_bottom_spacer") {
        Spacer(
            modifier = Modifier
                .navigationBarsPadding()
                .height(24.dp)
        )
    }
}

@Composable
private fun FlashcardCheckRow(
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
