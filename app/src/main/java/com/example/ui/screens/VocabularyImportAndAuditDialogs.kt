package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.VocabularyDeck
import com.example.model.VocabularyEntry
import com.example.model.VocabularyExample
import com.example.model.VocabularyLevel
import com.example.ui.theme.ExamColorPalette
import com.example.ui.theme.ExamTheme
import com.example.util.VocabularyAuditFinding
import com.example.util.VocabularyAuditReport
import com.example.util.VocabularyAuditStatus
import com.example.util.VocabularyDuplicateAction
import com.example.util.VocabularyDuplicateItem
import com.example.util.VocabularyEngine
import com.example.util.VocabularyImportAnalysis
import com.example.util.VocabularyMergeDecision

private val QUICK_IMPORT_LANGUAGES = listOf("English", "Hindi", "Spanish", "French", "German", "Japanese", "Arabic", "Korean", "Sanskrit")

/**
 * Full-screen Vocabulary Set Creator & Importer (JSON, Structured Text, or Manual Word)
 * that automatically invokes the Audit-Style Duplicate & Merge Review Hub whenever
 * repeated vocabulary words are detected.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VocabularyImportDialog(
    targetDeck: VocabularyDeck?,
    allDecks: List<VocabularyDeck>,
    initialLanguage: String = "English",
    initialTranslationLanguage: String = "",
    initialLevelCode: String = VocabularyLevel.INTERMEDIATE.code,
    initialCustomLevelLabel: String = "",
    onDismiss: () -> Unit,
    onCommitImport: (
        targetDeckId: String?,
        title: String,
        language: String,
        translationLanguage: String,
        levelCode: String,
        customLevelLabel: String,
        domain: String,
        analysis: VocabularyImportAnalysis,
        decisions: Map<String, VocabularyMergeDecision>
    ) -> Unit
) {
    val colors = ExamTheme.colors
    val context = LocalContext.current

    var inputMode by remember { mutableIntStateOf(0) } // 0 = Paste JSON/Text, 1 = Add Single Word
    var title by remember { mutableStateOf(targetDeck?.title ?: "") }
    var language by remember { mutableStateOf(targetDeck?.language ?: initialLanguage.ifBlank { "English" }) }
    var translationLanguage by remember { mutableStateOf(targetDeck?.translationLanguage ?: initialTranslationLanguage) }
    var levelCode by remember { mutableStateOf(targetDeck?.levelCode ?: initialLevelCode) }
    var customLevelLabel by remember { mutableStateOf(targetDeck?.customLevelLabel ?: initialCustomLevelLabel) }
    var domain by remember { mutableStateOf(targetDeck?.domain ?: "General") }
    var checkCrossDeck by remember { mutableStateOf(true) }

    // Bulk paste state
    var rawPaste by remember { mutableStateOf("") }
    var parseError by remember { mutableStateOf<String?>(null) }

    // Manual single-word state
    var manualWord by remember { mutableStateOf("") }
    var manualPron by remember { mutableStateOf("") }
    var manualPos by remember { mutableStateOf("") }
    var manualMeaning by remember { mutableStateOf("") }
    var manualTranslation by remember { mutableStateOf("") }
    var manualExample1Ctx by remember { mutableStateOf("Everyday") }
    var manualExample1 by remember { mutableStateOf("") }
    var manualExample2Ctx by remember { mutableStateOf("Formal") }
    var manualExample2 by remember { mutableStateOf("") }
    var manualSynonyms by remember { mutableStateOf("") }
    var manualNote by remember { mutableStateOf("") }

    // Step 2: Audit-Style Duplicate Review Hub
    var pendingAnalysis by remember { mutableStateOf<VocabularyImportAnalysis?>(null) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.teal,
        unfocusedBorderColor = colors.border,
        focusedLabelColor = colors.teal,
        unfocusedLabelColor = colors.inkMuted,
        focusedTextColor = colors.inkBold,
        unfocusedTextColor = colors.inkBold
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        BackHandler {
            if (pendingAnalysis != null) pendingAnalysis = null else onDismiss()
        }

        val currentAnalysis = pendingAnalysis
        if (currentAnalysis != null) {
            VocabularyDuplicateMergeReviewHub(
                analysis = currentAnalysis,
                deckTitle = title.trim().ifBlank { targetDeck?.title ?: "$language Vocabulary" },
                onBack = { pendingAnalysis = null },
                onApplyDecisions = { decisions ->
                    onCommitImport(
                        targetDeck?.id,
                        title.trim().ifBlank { targetDeck?.title ?: "$language ${VocabularyLevel.fromCode(levelCode).displayTitle(customLevelLabel)}" },
                        language.trim().ifBlank { "English" },
                        translationLanguage.trim(),
                        levelCode,
                        customLevelLabel.trim(),
                        domain.trim().ifBlank { "General" },
                        currentAnalysis,
                        decisions
                    )
                    onDismiss()
                }
            )
        } else {
            Surface(
                color = colors.background,
                modifier = Modifier.fillMaxSize()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Top Bar
                    Surface(color = colors.surface, shadowElevation = 1.dp) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close", tint = colors.inkBold)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (targetDeck != null) "Add Words to \"${targetDeck.title}\"" else "New Vocabulary Set",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Supports Strict JSON, Structured Text, or manual entry with duplicate review",
                                fontSize = 11.sp,
                                color = colors.inkMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Mode toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(0 to "Paste JSON / Text", 1 to "Add Single Word").forEach { (idx, label) ->
                            val selected = inputMode == idx
                            Surface(
                                onClick = { inputMode = idx; parseError = null },
                                shape = RoundedCornerShape(9.dp),
                                color = if (selected) colors.tealLight else colors.surface,
                                border = BorderStroke(if (selected) 1.2.dp else 0.8.dp, if (selected) colors.teal else colors.border),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 44.dp)
                                    .testTag("vocab_import_mode_$idx")
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
                                    Text(
                                        text = label,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) colors.tealDark else colors.inkBold
                                    )
                                }
                            }
                        }
                    }

                    // Deck Metadata (only when creating a brand-new Vocabulary Set)
                    if (targetDeck == null) {
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
                                    text = "SET DETAILS (LANGUAGE & LEVEL)",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.teal,
                                    letterSpacing = 0.5.sp
                                )

                                OutlinedTextField(
                                    value = title,
                                    onValueChange = { title = it },
                                    label = { Text("Vocabulary Set Title", fontSize = 12.sp) },
                                    placeholder = { Text("e.g. Academic Verbs, Daily Japanese, Editorial Words", fontSize = 11.5.sp) },
                                    singleLine = true,
                                    colors = fieldColors,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("vocab_set_title_input")
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    QUICK_IMPORT_LANGUAGES.forEach { lang ->
                                        val selected = language.equals(lang, ignoreCase = true)
                                        Surface(
                                            onClick = { language = lang },
                                            shape = RoundedCornerShape(7.dp),
                                            color = if (selected) colors.tealLight else colors.surfaceVariant.copy(alpha = 0.45f),
                                            border = BorderStroke(0.8.dp, if (selected) colors.teal else colors.border)
                                        ) {
                                            Text(
                                                text = lang,
                                                fontSize = 11.sp,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (selected) colors.tealDark else colors.inkBold,
                                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = language,
                                        onValueChange = { language = it },
                                        label = { Text("Language", fontSize = 11.5.sp) },
                                        singleLine = true,
                                        colors = fieldColors,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = translationLanguage,
                                        onValueChange = { translationLanguage = it },
                                        label = { Text("Translation (Opt.)", fontSize = 11.5.sp) },
                                        placeholder = { Text("Blank = None", fontSize = 11.sp) },
                                        singleLine = true,
                                        colors = fieldColors,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    VocabularyLevel.entries.forEach { lvl ->
                                        val selected = levelCode.equals(lvl.code, ignoreCase = true)
                                        Surface(
                                            onClick = { levelCode = lvl.code },
                                            shape = RoundedCornerShape(7.dp),
                                            color = if (selected) colors.tealLight else colors.surfaceVariant.copy(alpha = 0.45f),
                                            border = BorderStroke(0.8.dp, if (selected) colors.teal else colors.border)
                                        ) {
                                            Text(
                                                text = lvl.displayTitle(),
                                                fontSize = 11.sp,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (selected) colors.tealDark else colors.inkBold,
                                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                            )
                                        }
                                    }
                                }

                                if (VocabularyLevel.fromCode(levelCode) == VocabularyLevel.CUSTOM) {
                                    OutlinedTextField(
                                        value = customLevelLabel,
                                        onValueChange = { customLevelLabel = it },
                                        label = { Text("Custom Level Name", fontSize = 11.5.sp) },
                                        placeholder = { Text("e.g. JLPT N3, GRE, HSK 4", fontSize = 11.5.sp) },
                                        singleLine = true,
                                        colors = fieldColors,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }

                    // Input section
                    if (inputMode == 0) {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = colors.surface),
                            border = BorderStroke(0.8.dp, colors.border)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "PASTE AI VOCABULARY (JSON OR TEXT)",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.teal
                                    )
                                    TextButton(
                                        onClick = {
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                                            if (clip.isNotBlank()) {
                                                rawPaste = clip
                                                parseError = null
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.ContentPaste, contentDescription = null, tint = colors.teal, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Paste", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.teal)
                                    }
                                }

                                OutlinedTextField(
                                    value = rawPaste,
                                    onValueChange = { rawPaste = it; parseError = null },
                                    placeholder = {
                                        Text(
                                            text = "Paste JSON array [{\"word\": ..., \"meaning\": ..., \"examples\": [...]}] OR Structured Text (WORD:, MEANING:, EXAMPLES:)...",
                                            fontSize = 11.5.sp
                                        )
                                    },
                                    minLines = 7,
                                    maxLines = 14,
                                    colors = fieldColors,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("vocab_raw_paste_input")
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { checkCrossDeck = !checkCrossDeck },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = checkCrossDeck,
                                        onCheckedChange = { checkCrossDeck = it },
                                        colors = CheckboxDefaults.colors(checkedColor = colors.teal)
                                    )
                                    Text(
                                        text = "Also detect duplicates across other ${language.ifBlank { "English" }} sets",
                                        fontSize = 12.sp,
                                        color = colors.inkBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    } else {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = colors.surface),
                            border = BorderStroke(0.8.dp, colors.border)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("MANUAL VOCABULARY CARD", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = colors.teal)
                                OutlinedTextField(
                                    value = manualWord,
                                    onValueChange = { manualWord = it; parseError = null },
                                    label = { Text("Word / Expression *", fontSize = 12.sp) },
                                    singleLine = true,
                                    colors = fieldColors,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("vocab_manual_word_input")
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = manualPos,
                                        onValueChange = { manualPos = it },
                                        label = { Text("Part of Speech", fontSize = 11.5.sp) },
                                        placeholder = { Text("noun, verb, adj...", fontSize = 11.sp) },
                                        singleLine = true,
                                        colors = fieldColors,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = manualPron,
                                        onValueChange = { manualPron = it },
                                        label = { Text("Pronunciation / IPA", fontSize = 11.5.sp) },
                                        singleLine = true,
                                        colors = fieldColors,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                OutlinedTextField(
                                    value = manualMeaning,
                                    onValueChange = { manualMeaning = it; parseError = null },
                                    label = { Text("Meaning / Definition *", fontSize = 12.sp) },
                                    minLines = 2,
                                    colors = fieldColors,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("vocab_manual_meaning_input")
                                )
                                OutlinedTextField(
                                    value = manualTranslation,
                                    onValueChange = { manualTranslation = it },
                                    label = { Text("Translation (Optional)", fontSize = 12.sp) },
                                    singleLine = true,
                                    colors = fieldColors,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = manualExample1,
                                    onValueChange = { manualExample1 = it },
                                    label = { Text("Example 1 ($manualExample1Ctx Context)", fontSize = 11.5.sp) },
                                    colors = fieldColors,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = manualExample2,
                                    onValueChange = { manualExample2 = it },
                                    label = { Text("Example 2 ($manualExample2Ctx Context)", fontSize = 11.5.sp) },
                                    colors = fieldColors,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = manualSynonyms,
                                    onValueChange = { manualSynonyms = it },
                                    label = { Text("Synonyms (comma-separated)", fontSize = 11.5.sp) },
                                    singleLine = true,
                                    colors = fieldColors,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = manualNote,
                                    onValueChange = { manualNote = it },
                                    label = { Text("Usage / Nuance Note (Optional)", fontSize = 11.5.sp) },
                                    singleLine = true,
                                    colors = fieldColors,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    if (parseError != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = colors.redLight,
                            border = BorderStroke(0.8.dp, colors.red.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = parseError.orEmpty(),
                                fontSize = 12.sp,
                                color = colors.red,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                // Bottom Commit Bar
                Surface(color = colors.surface, shadowElevation = 4.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Cancel", color = colors.inkBold)
                        }
                        Button(
                            onClick = {
                                val parseResult = if (inputMode == 0) {
                                    VocabularyEngine.parseVocabularyInput(
                                        rawInput = rawPaste,
                                        defaultLevelCode = levelCode,
                                        defaultCustomLevelLabel = customLevelLabel
                                    )
                                } else {
                                    if (manualWord.isBlank() || (manualMeaning.isBlank() && manualTranslation.isBlank())) {
                                        parseError = "Please enter a word and its meaning."
                                        return@Button
                                    }
                                    val exList = buildList {
                                        if (manualExample1.isNotBlank()) add(VocabularyExample(manualExample1Ctx, manualExample1.trim()))
                                        if (manualExample2.isNotBlank()) add(VocabularyExample(manualExample2Ctx, manualExample2.trim()))
                                    }
                                    val entry = VocabularyEntry(
                                        word = manualWord.trim(),
                                        pronunciation = manualPron.trim(),
                                        partOfSpeech = manualPos.trim(),
                                        levelCode = levelCode,
                                        customLevelLabel = customLevelLabel.trim(),
                                        meaning = manualMeaning.trim().ifBlank { manualTranslation.trim() },
                                        translation = manualTranslation.trim(),
                                        examples = exList,
                                        synonyms = manualSynonyms.split(",").map { it.trim() }.filter { it.isNotBlank() },
                                        usageNote = manualNote.trim()
                                    )
                                    com.example.util.VocabularyParseResult(
                                        entries = listOf(entry),
                                        detectedFormat = com.example.util.VocabularyInputFormat.STRUCTURED_TEXT
                                    )
                                }

                                if (parseResult.entries.isEmpty()) {
                                    parseError = parseResult.warnings.firstOrNull() ?: "No vocabulary entries detected."
                                    return@Button
                                }

                                val analysis = VocabularyEngine.analyzeVocabularyImport(
                                    parseResult = parseResult,
                                    targetDeck = targetDeck,
                                    targetLanguage = language,
                                    allDecks = allDecks,
                                    checkAcrossSameLanguage = checkCrossDeck
                                )

                                if (analysis.duplicateItems.isNotEmpty()) {
                                    pendingAnalysis = analysis
                                } else {
                                    onCommitImport(
                                        targetDeck?.id,
                                        title.trim().ifBlank { targetDeck?.title ?: "$language ${VocabularyLevel.fromCode(levelCode).displayTitle(customLevelLabel)}" },
                                        language.trim().ifBlank { "English" },
                                        translationLanguage.trim(),
                                        levelCode,
                                        customLevelLabel.trim(),
                                        domain.trim().ifBlank { "General" },
                                        analysis,
                                        emptyMap()
                                    )
                                    onDismiss()
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("vocab_import_analyze_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (inputMode == 0) "Analyze & Save" else "Add Word",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

/**
 * Audit-Style Duplicate & Granular Segment Merge Review Hub.
 * Ensures zero silent repetition while letting users merge new examples, translations,
 * synonyms, or notes into the exact segment of an existing word.
 */
@Composable
fun VocabularyDuplicateMergeReviewHub(
    analysis: VocabularyImportAnalysis,
    deckTitle: String,
    onBack: () -> Unit,
    onApplyDecisions: (Map<String, VocabularyMergeDecision>) -> Unit
) {
    val colors = ExamTheme.colors
    val decisions = remember(analysis) {
        mutableStateMapOf<String, VocabularyMergeDecision>().apply {
            analysis.duplicateItems.forEach { dup ->
                put(
                    dup.id,
                    VocabularyMergeDecision(
                        action = if (dup.hasNewExamples || dup.hasNewTranslation || dup.hasNewLexicalLinks) {
                            VocabularyDuplicateAction.MERGE_SEGMENTS
                        } else {
                            VocabularyDuplicateAction.SKIP
                        },
                        mergeExamples = dup.hasNewExamples,
                        mergeTranslation = dup.hasNewTranslation,
                        mergeSynonymsAndCollocations = dup.hasNewLexicalLinks,
                        mergeMeaningAndNote = false,
                        mergeLevelAndMeta = false
                    )
                )
            }
        }
    }

    Surface(
        color = colors.background,
        modifier = Modifier
            .fillMaxSize()
            .testTag("vocab_duplicate_review_hub")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Surface(color = colors.surface, shadowElevation = 1.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.inkBold)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Duplicate & Merge Review Hub",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkBold
                        )
                        Text(
                            text = "$deckTitle • ${analysis.uniqueNewEntries.size} New · ${analysis.duplicateItems.size} Repeated",
                            fontSize = 11.5.sp,
                            color = colors.amber,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Summary & Bulk Actions
                item(key = "dup_summary") {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.surface),
                        border = BorderStroke(0.9.dp, colors.amber.copy(alpha = 0.45f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.MergeType, contentDescription = null, tint = colors.amber, modifier = Modifier.size(20.dp))
                                Text(
                                    text = "${analysis.duplicateItems.size} repeated words detected before saving",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.inkBold
                                )
                            }
                            Text(
                                text = "Choose per word whether to merge specific segments (new examples, translation, synonyms) into the existing card, skip, replace, or keep separate for homonyms.",
                                fontSize = 11.5.sp,
                                color = colors.inkMuted,
                                lineHeight = 16.sp
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        analysis.duplicateItems.forEach { dup ->
                                            decisions[dup.id] = VocabularyMergeDecision(
                                                action = VocabularyDuplicateAction.MERGE_SEGMENTS,
                                                mergeExamples = true,
                                                mergeTranslation = true,
                                                mergeSynonymsAndCollocations = true,
                                                mergeMeaningAndNote = false,
                                                mergeLevelAndMeta = false
                                            )
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Merge New Segments", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = colors.tealDark, maxLines = 1)
                                }
                                OutlinedButton(
                                    onClick = {
                                        analysis.duplicateItems.forEach { dup ->
                                            decisions[dup.id] = VocabularyMergeDecision(action = VocabularyDuplicateAction.SKIP)
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Skip All Repeats", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = colors.inkBold, maxLines = 1)
                                }
                            }
                        }
                    }
                }

                items(analysis.duplicateItems, key = { it.id }) { dup ->
                    val decision = decisions[dup.id] ?: VocabularyMergeDecision()
                    DuplicateWordDiffCard(
                        dup = dup,
                        decision = decision,
                        colors = colors,
                        onDecisionChange = { updated -> decisions[dup.id] = updated }
                    )
                }
            }

            // Bottom Apply Bar
            Surface(color = colors.surface, shadowElevation = 4.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) { Text("Back", color = colors.inkBold) }
                    Button(
                        onClick = { onApplyDecisions(decisions.toMap()) },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("vocab_duplicate_apply_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Apply Decisions & Save", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DuplicateWordDiffCard(
    dup: VocabularyDuplicateItem,
    decision: VocabularyMergeDecision,
    colors: ExamColorPalette,
    onDecisionChange: (VocabularyMergeDecision) -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(0.9.dp, colors.border)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = dup.incoming.word,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.inkBold
                    )
                    Text(
                        text = when {
                            dup.isIntraBatch -> "Repeated within this pasted list"
                            dup.isSameDeck -> "Already in this set (${dup.existingLevelTitle})"
                            else -> "Exists in \"${dup.existingDeckTitle}\" (${dup.existingLevelTitle})"
                        },
                        fontSize = 11.sp,
                        color = colors.amber,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Surface(shape = RoundedCornerShape(6.dp), color = colors.tealLight) {
                    Text(
                        text = dup.existing.effectiveLevelBadge(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.tealDark,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }

            // Side-by-side compact diff comparison
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = colors.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(0.6.dp, colors.borderLight),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("EXISTING", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = colors.inkMuted)
                        Text(dup.existing.meaning, fontSize = 11.5.sp, color = colors.inkBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        if (dup.existing.translation.isNotBlank()) {
                            Text(dup.existing.translation, fontSize = 11.sp, color = colors.tealDark, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text("${dup.existing.examples.size} examples • ${dup.existing.synonyms.size} syns", fontSize = 10.sp, color = colors.inkMuted)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = colors.tealLight.copy(alpha = 0.35f),
                    border = BorderStroke(0.6.dp, colors.teal.copy(alpha = 0.35f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("INCOMING", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = colors.tealDark)
                        Text(dup.incoming.meaning, fontSize = 11.5.sp, color = colors.inkBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        if (dup.incoming.translation.isNotBlank()) {
                            Text(dup.incoming.translation, fontSize = 11.sp, color = colors.tealDark, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text("${dup.incoming.examples.size} examples • ${dup.incoming.synonyms.size} syns", fontSize = 10.sp, color = colors.inkMuted)
                    }
                }
            }

            // Action chips
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                VocabularyDuplicateAction.entries.forEach { act ->
                    val selected = decision.action == act
                    Surface(
                        onClick = { onDecisionChange(decision.copy(action = act)) },
                        shape = RoundedCornerShape(7.dp),
                        color = if (selected) colors.teal else colors.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(0.7.dp, if (selected) colors.teal else colors.border)
                    ) {
                        Text(
                            text = act.label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) Color.White else colors.inkBold,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Segment checkboxes when Merge Segments is selected
            if (decision.action == VocabularyDuplicateAction.MERGE_SEGMENTS) {
                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = colors.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(0.6.dp, colors.borderLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                        Text(
                            text = "MERGE INTO SPECIFIC SEGMENTS",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkMuted,
                            modifier = Modifier.padding(start = 6.dp, top = 2.dp, bottom = 2.dp)
                        )
                        SegmentCheckRow(
                            label = "Append new context examples (${dup.incoming.examples.size} incoming)",
                            checked = decision.mergeExamples,
                            onChange = { onDecisionChange(decision.copy(mergeExamples = it)) },
                            colors = colors
                        )
                        SegmentCheckRow(
                            label = "Add / merge translation",
                            checked = decision.mergeTranslation,
                            onChange = { onDecisionChange(decision.copy(mergeTranslation = it)) },
                            colors = colors
                        )
                        SegmentCheckRow(
                            label = "Merge synonyms, antonyms & collocations",
                            checked = decision.mergeSynonymsAndCollocations,
                            onChange = { onDecisionChange(decision.copy(mergeSynonymsAndCollocations = it)) },
                            colors = colors
                        )
                        SegmentCheckRow(
                            label = "Merge meaning & usage note",
                            checked = decision.mergeMeaningAndNote,
                            onChange = { onDecisionChange(decision.copy(mergeMeaningAndNote = it)) },
                            colors = colors
                        )
                        SegmentCheckRow(
                            label = "Update level, POS & pronunciation",
                            checked = decision.mergeLevelAndMeta,
                            onChange = { onDecisionChange(decision.copy(mergeLevelAndMeta = it)) },
                            colors = colors
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SegmentCheckRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    colors: ExamColorPalette
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onChange,
            colors = CheckboxDefaults.colors(checkedColor = colors.teal)
        )
        Text(text = label, fontSize = 11.5.sp, color = colors.inkBold, modifier = Modifier.weight(1f))
    }
}

/**
 * Vocabulary AI Audit Resolution Hub — modeled directly after ExamSet's Question Set Audit Hub.
 * Verifies Category/Word Class, Proficiency Level, Meanings, Translations, and Context Examples,
 * showing side-by-side diffs before safely applying user-approved fixes.
 */
@Composable
fun VocabularyAuditHubDialog(
    deck: VocabularyDeck,
    onDismiss: () -> Unit,
    onApplyAuditFixes: (List<VocabularyAuditFinding>) -> Unit
) {
    val colors = ExamTheme.colors
    val context = LocalContext.current

    var activeTab by remember { mutableIntStateOf(0) } // 0 = Audit Prompt, 1 = Review & Apply Findings
    val auditPrompt = remember(deck) { VocabularyEngine.buildVocabularyAuditPrompt(deck) }
    var auditJsonInput by remember { mutableStateOf("") }
    var report by remember { mutableStateOf<VocabularyAuditReport?>(null) }
    var parseWarning by remember { mutableStateOf<String?>(null) }
    val selectedFixIds = remember { mutableStateMapOf<String, Boolean>() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        BackHandler { onDismiss() }

        Surface(
            color = colors.background,
            modifier = Modifier
                .fillMaxSize()
                .testTag("vocab_audit_hub_dialog")
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Surface(color = colors.surface, shadowElevation = 1.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close Audit Hub", tint = colors.inkBold)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Vocabulary Audit Hub", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.inkBold)
                            Text(
                                text = "${deck.title} • Category, Level & Usage Verification",
                                fontSize = 11.sp,
                                color = colors.inkMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Tab bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(0 to "1. Copy Audit Prompt", 1 to "2. Review AI Audit").forEach { (idx, label) ->
                        val selected = activeTab == idx
                        Surface(
                            onClick = { activeTab = idx },
                            shape = RoundedCornerShape(9.dp),
                            color = if (selected) colors.tealLight else colors.surface,
                            border = BorderStroke(if (selected) 1.2.dp else 0.8.dp, if (selected) colors.teal else colors.border),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) colors.tealDark else colors.inkBold
                                )
                            }
                        }
                    }
                }

                if (activeTab == 0) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = colors.surface),
                            border = BorderStroke(0.8.dp, colors.border)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.FactCheck, contentDescription = null, tint = colors.teal, modifier = Modifier.size(20.dp))
                                    Text("What This Audit Verifies", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = colors.inkBold)
                                }
                                Text(
                                    text = "• True Word Category / Part of Speech (noun, verb, idiom, phrasal verb)\n• True Proficiency Level (A1–C2) & Register alignment\n• Meaning & Translation precision\n• Context example naturalness & duplicate overlap",
                                    fontSize = 12.sp,
                                    color = colors.ink,
                                    lineHeight = 18.sp
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.surfaceVariant.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                                .border(0.7.dp, colors.border, RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = auditPrompt,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = colors.ink,
                                lineHeight = 16.sp,
                                maxLines = 24,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Button(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Vocabulary Audit Prompt", auditPrompt))
                                Toast.makeText(context, "Audit prompt copied! Paste AI JSON reply in Tab 2.", Toast.LENGTH_SHORT).show()
                                activeTab = 1
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.navyBanner, contentColor = colors.navyBannerText),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("vocab_audit_copy_prompt_button")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy Audit Prompt & Go to Review", fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .then(if (report == null) Modifier.windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)) else Modifier)
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item(key = "audit_input") {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = colors.surface),
                                border = BorderStroke(0.8.dp, colors.border)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("PASTE AI AUDIT JSON REPORT", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = colors.teal)
                                        TextButton(
                                            onClick = {
                                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                val clip = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                                                if (clip.isNotBlank()) auditJsonInput = clip
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Icon(Icons.Default.ContentPaste, contentDescription = null, tint = colors.teal, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Paste", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.teal)
                                        }
                                    }

                                    OutlinedTextField(
                                        value = auditJsonInput,
                                        onValueChange = { auditJsonInput = it; parseWarning = null },
                                        placeholder = { Text("Paste the JSON audit array returned by AI...", fontSize = 11.5.sp) },
                                        minLines = 5,
                                        maxLines = 10,
                                        shape = RoundedCornerShape(9.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("vocab_audit_json_input")
                                    )

                                    Button(
                                        onClick = {
                                            val parsed = VocabularyEngine.parseVocabularyAuditReport(auditJsonInput, deck)
                                            if (parsed.findings.isEmpty()) {
                                                parseWarning = "No matching words found in audit JSON. Ensure the AI returned the JSON array using exact words from this set."
                                            } else {
                                                parseWarning = null
                                                report = parsed
                                                selectedFixIds.clear()
                                                parsed.findings.forEach { f ->
                                                    if (f.hasActionableChanges) selectedFixIds[f.entryId] = true
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(9.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 46.dp)
                                            .testTag("vocab_audit_parse_button")
                                    ) {
                                        Text("Parse Audit Findings", fontWeight = FontWeight.Bold)
                                    }

                                    if (parseWarning != null) {
                                        Text(parseWarning.orEmpty(), fontSize = 11.5.sp, color = colors.red)
                                    }
                                }
                            }
                        }

                        val activeReport = report
                        if (activeReport != null) {
                            item(key = "audit_summary_bar") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = colors.greenLight,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = colors.green, modifier = Modifier.size(18.dp))
                                            Text("${activeReport.verifiedCount} Verified", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = colors.green)
                                        }
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = colors.amberLight,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(Icons.Default.Warning, contentDescription = null, tint = colors.amber, modifier = Modifier.size(18.dp))
                                            Text("${activeReport.flaggedCount} Flagged", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = colors.amber)
                                        }
                                    }
                                }
                            }

                            items(activeReport.findings, key = { it.entryId }) { finding ->
                                val isChecked = selectedFixIds[finding.entryId] == true
                                AuditFindingCard(
                                    finding = finding,
                                    checked = isChecked,
                                    colors = colors,
                                    onToggleCheck = { selectedFixIds[finding.entryId] = it }
                                )
                            }
                        }
                    }

                    val activeReport = report
                    val approvedFindings = activeReport?.findings?.filter {
                        it.hasActionableChanges && selectedFixIds[it.entryId] == true
                    }.orEmpty()

                    if (activeReport != null) {
                        Surface(color = colors.surface, shadowElevation = 4.dp) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onDismiss,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) { Text("Close", color = colors.inkBold) }
                                Button(
                                    onClick = {
                                        onApplyAuditFixes(approvedFindings)
                                        onDismiss()
                                    },
                                    enabled = approvedFindings.isNotEmpty(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("vocab_audit_apply_fixes_button")
                                ) {
                                    Text("Apply ${approvedFindings.size} Selected Fix(es)", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditFindingCard(
    finding: VocabularyAuditFinding,
    checked: Boolean,
    colors: ExamColorPalette,
    onToggleCheck: (Boolean) -> Unit
) {
    val isVerified = finding.status == VocabularyAuditStatus.VERIFIED
    val badgeBg = if (isVerified) colors.greenLight else colors.amberLight
    val badgeFg = if (isVerified) colors.green else colors.amber

    Card(
        shape = RoundedCornerShape(13.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(0.8.dp, if (isVerified) colors.green.copy(alpha = 0.35f) else colors.amber.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    if (finding.hasActionableChanges) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = onToggleCheck,
                            colors = CheckboxDefaults.colors(checkedColor = colors.teal)
                        )
                    }
                    Column {
                        Text(finding.currentEntry.word, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = colors.inkBold)
                        if (finding.verifiedCategoryType.isNotBlank()) {
                            Text(
                                text = "Category: ${finding.verifiedCategoryType}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.tealDark
                            )
                        }
                    }
                }
                Surface(shape = RoundedCornerShape(6.dp), color = badgeBg) {
                    Text(
                        text = finding.status.label,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeFg,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }

            Text(finding.issueSummary, fontSize = 12.sp, color = colors.ink, lineHeight = 17.sp)

            if (finding.hasActionableChanges) {
                HorizontalDivider(color = colors.borderLight)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    finding.suggestedPartOfSpeech?.let {
                        Text("POS: ${finding.currentEntry.partOfSpeech.ifBlank { "—" }} -> $it", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = colors.tealDark)
                    }
                    finding.suggestedLevelCode?.let {
                        Text("Level: ${finding.currentEntry.effectiveLevelBadge()} -> $it", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = colors.tealDark)
                    }
                    finding.suggestedMeaning?.let {
                        Text("Meaning: $it", fontSize = 11.5.sp, color = colors.inkBold)
                    }
                    finding.suggestedTranslation?.let {
                        Text("Translation: $it", fontSize = 11.5.sp, color = colors.tealDark)
                    }
                    finding.suggestedUsageNote?.let {
                        Text("Note: $it", fontSize = 11.sp, color = colors.inkMuted)
                    }
                }
            }
        }
    }
}
