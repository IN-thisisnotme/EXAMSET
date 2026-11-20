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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.FolderOpen
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.VocabularyDeck
import com.example.model.VocabularyEntry
import com.example.model.VocabularyExample
import com.example.model.VocabularyLanguageVariants
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
import com.example.util.VocabularyInputFormat
import com.example.util.VocabularyMergeDecision
import com.example.util.VocabularyParseResult
import com.example.util.VocabularyRouteBucket
import com.example.util.VocabularySmartRouteTargetChoice

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
    preferredEnglishVariant: String = VocabularyLanguageVariants.DEFAULT_ENGLISH_VARIANT,
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
        decisions: Map<String, VocabularyMergeDecision>,
        languageVariant: String
    ) -> Unit,
    onCommitSmartRoutedImport: ((
        targets: List<VocabularySmartRouteTargetChoice>,
        entryBucketById: Map<String, String>,
        analysis: VocabularyImportAnalysis,
        decisions: Map<String, VocabularyMergeDecision>
    ) -> Unit)? = null
) {
    val colors = ExamTheme.colors
    val context = LocalContext.current
    val isHubImport = targetDeck == null

    // Unified 2-mode toggle: 0 = Paste & Auto-Sort (or Paste JSON/Text inside targetDeck), 1 = Add Single Word
    var inputMode by remember(targetDeck?.id) { mutableIntStateOf(0) }
    val isManualSingleWord = inputMode == 1
    val isSmartRouteMode = isHubImport && inputMode == 0

    val safeInitialLevel = remember(targetDeck?.id, initialLevelCode) {
        val raw = targetDeck?.levelCode ?: initialLevelCode
        VocabularyLevel.fromCode(raw)
            .takeUnless { it == VocabularyLevel.MIXED }
            ?.code ?: VocabularyLevel.INTERMEDIATE.code
    }
    val safeInitialCustom = remember(targetDeck?.id, safeInitialLevel, initialCustomLevelLabel) {
        if (VocabularyLevel.fromCode(safeInitialLevel) == VocabularyLevel.CUSTOM) {
            targetDeck?.customLevelLabel ?: initialCustomLevelLabel
        } else ""
    }

    var title by remember { mutableStateOf(targetDeck?.title ?: "") }
    var language by remember { mutableStateOf(targetDeck?.language ?: initialLanguage.ifBlank { "English" }) }
    var languageVariant by remember {
        mutableStateOf(
            targetDeck?.languageVariant?.ifBlank {
                if (VocabularyLanguageVariants.isEnglish(targetDeck.language)) preferredEnglishVariant else ""
            } ?: if (VocabularyLanguageVariants.isEnglish(initialLanguage)) preferredEnglishVariant else ""
        )
    }
    var translationLanguage by remember { mutableStateOf(targetDeck?.translationLanguage ?: initialTranslationLanguage) }
    var levelCode by remember(targetDeck?.id, safeInitialLevel) { mutableStateOf(safeInitialLevel) }
    var customLevelLabel by remember(targetDeck?.id, safeInitialCustom) { mutableStateOf(safeInitialCustom) }
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

    // Live detection preview in Paste-First Smart Auto-Route mode
    val liveDetectionPreview = remember(
        rawPaste,
        isSmartRouteMode,
        preferredEnglishVariant,
        safeInitialLevel,
        safeInitialCustom
    ) {
        if (!isSmartRouteMode || rawPaste.trim().length < 6) {
            null
        } else {
            val parsed = VocabularyEngine.parseVocabularyInput(
                rawInput = rawPaste,
                defaultLevelCode = safeInitialLevel,
                defaultCustomLevelLabel = safeInitialCustom,
                preferredEnglishVariant = preferredEnglishVariant
            )
            if (parsed.entries.isEmpty()) null else {
                val buckets = VocabularyEngine.buildSmartRoutingPlan(
                    parseResult = parsed,
                    allDecks = allDecks,
                    preferredEnglishVariant = preferredEnglishVariant
                )
                parsed to buckets
            }
        }
    }

    // Step 2A: Smart Folder & Level Routing Review
    var pendingSmartParse by remember { mutableStateOf<VocabularyParseResult?>(null) }
    var pendingSmartBuckets by remember { mutableStateOf<List<VocabularyRouteBucket>>(emptyList()) }
    val smartChoices = remember { mutableStateMapOf<String, VocabularySmartRouteTargetChoice>() }
    var pendingSmartEntryBucketMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var pendingSmartTargetsSnapshot by remember { mutableStateOf<List<VocabularySmartRouteTargetChoice>>(emptyList()) }

    // Step 2B: Audit-Style Duplicate Review Hub
    var pendingAnalysis by remember { mutableStateOf<VocabularyImportAnalysis?>(null) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.teal,
        unfocusedBorderColor = colors.border,
        focusedLabelColor = colors.teal,
        unfocusedLabelColor = colors.inkMuted,
        focusedTextColor = colors.inkBold,
        unfocusedTextColor = colors.inkBold
    )

    val commitSmartChoice: (
        targets: List<VocabularySmartRouteTargetChoice>,
        entryBucketById: Map<String, String>,
        analysis: VocabularyImportAnalysis,
        decisions: Map<String, VocabularyMergeDecision>
    ) -> Unit = { targets, entryBucketById, analysis, decisions ->
        if (onCommitSmartRoutedImport != null) {
            onCommitSmartRoutedImport(targets, entryBucketById, analysis, decisions)
        } else {
            val primary = targets.firstOrNull()
            if (primary != null) {
                onCommitImport(
                    primary.targetDeckId,
                    primary.customTitle,
                    primary.language,
                    primary.translationLanguage,
                    primary.levelCode,
                    primary.customLevelLabel,
                    primary.domain,
                    analysis,
                    decisions,
                    primary.languageVariant
                )
            }
        }
    }

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
            when {
                pendingAnalysis != null -> pendingAnalysis = null
                pendingSmartParse != null -> {
                    pendingSmartParse = null
                    pendingSmartBuckets = emptyList()
                }
                else -> onDismiss()
            }
        }

        val currentAnalysis = pendingAnalysis
        val currentSmartParse = pendingSmartParse
        if (currentAnalysis != null) {
            val summaryTitle = if (pendingSmartTargetsSnapshot.isNotEmpty()) {
                if (pendingSmartTargetsSnapshot.size == 1) {
                    val only = pendingSmartTargetsSnapshot.first()
                    only.targetDeckId?.let { id -> allDecks.firstOrNull { it.id == id }?.title } ?: only.customTitle
                } else {
                    "${pendingSmartTargetsSnapshot.size} Level Folders"
                }
            } else {
                title.trim().ifBlank { targetDeck?.title ?: "$language • ${VocabularyLevel.fromCode(levelCode).displayTitleForLanguage(language, customLevelLabel)}" }
            }

            VocabularyDuplicateMergeReviewHub(
                analysis = currentAnalysis,
                deckTitle = summaryTitle,
                onBack = { pendingAnalysis = null },
                onApplyDecisions = { decisions ->
                    if (pendingSmartTargetsSnapshot.isNotEmpty()) {
                        commitSmartChoice(
                            pendingSmartTargetsSnapshot,
                            pendingSmartEntryBucketMap,
                            currentAnalysis,
                            decisions
                        )
                    } else {
                        onCommitImport(
                            targetDeck?.id,
                            title.trim().ifBlank { targetDeck?.title ?: "$language • ${VocabularyLevel.fromCode(levelCode).displayTitleForLanguage(language, customLevelLabel)}" },
                            language.trim().ifBlank { "English" },
                            translationLanguage.trim(),
                            levelCode,
                            customLevelLabel.trim(),
                            domain.trim().ifBlank { "General" },
                            currentAnalysis,
                            decisions,
                            if (VocabularyLanguageVariants.isEnglish(language)) languageVariant.ifBlank { preferredEnglishVariant } else languageVariant
                        )
                    }
                    onDismiss()
                }
            )
        } else if (currentSmartParse != null && pendingSmartBuckets.isNotEmpty()) {
            SmartVocabularyRoutingStep(
                parseResult = currentSmartParse,
                buckets = pendingSmartBuckets,
                smartChoices = smartChoices,
                allDecks = allDecks,
                checkCrossDeck = checkCrossDeck,
                onToggleCheckCrossDeck = { checkCrossDeck = it },
                onBack = {
                    pendingSmartParse = null
                    pendingSmartBuckets = emptyList()
                },
                onConfirmRoute = {
                    val resolvedTargets = pendingSmartBuckets.map { bucket ->
                        smartChoices[bucket.bucketKey] ?: VocabularySmartRouteTargetChoice(
                            bucketKey = bucket.bucketKey,
                            targetDeckId = bucket.recommendedDeckId,
                            customTitle = bucket.defaultNewTitle,
                            language = bucket.language,
                            languageVariant = bucket.languageVariant,
                            translationLanguage = bucket.translationLanguage,
                            levelCode = bucket.levelCode,
                            customLevelLabel = bucket.customLevelLabel
                        )
                    }
                    val routeAnalysis = VocabularyEngine.analyzeSmartRoutedImport(
                        parseResult = currentSmartParse,
                        buckets = pendingSmartBuckets,
                        choices = resolvedTargets.associateBy { it.bucketKey },
                        allDecks = allDecks,
                        checkAcrossSameLanguage = checkCrossDeck
                    )
                    if (routeAnalysis.analysis.duplicateItems.isNotEmpty()) {
                        pendingSmartEntryBucketMap = routeAnalysis.entryBucketById
                        pendingSmartTargetsSnapshot = resolvedTargets
                        pendingAnalysis = routeAnalysis.analysis
                    } else {
                        commitSmartChoice(
                            resolvedTargets,
                            routeAnalysis.entryBucketById,
                            routeAnalysis.analysis,
                            emptyMap()
                        )
                        onDismiss()
                    }
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
                                    text = if (targetDeck != null) "Add Words to \"${targetDeck.title}\"" else "Add Vocabulary",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.inkBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (isHubImport) {
                                        "Paste AI output to auto-sort into exact Language & Level folders, or add a single word"
                                    } else {
                                        "Supports Strict JSON, Structured Text, or manual entry with duplicate review"
                                    },
                                    fontSize = 11.sp,
                                    color = colors.inkMuted,
                                    maxLines = 2,
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
                        // Mode toggle (horizontally scrollable so large system font never truncates)
                        val modeOptions = if (isHubImport) {
                            listOf(
                                0 to "Paste & Auto-Sort",
                                1 to "Add Single Word"
                            )
                        } else {
                            listOf(
                                0 to "Paste JSON / Text",
                                1 to "Add Single Word"
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            modeOptions.forEach { (idx, label) ->
                                val selected = inputMode == idx
                                Surface(
                                    onClick = { inputMode = idx; parseError = null },
                                    shape = RoundedCornerShape(9.dp),
                                    color = if (selected) colors.tealLight else colors.surface,
                                    border = BorderStroke(if (selected) 1.2.dp else 0.8.dp, if (selected) colors.teal else colors.border),
                                    modifier = Modifier
                                        .heightIn(min = 44.dp)
                                        .testTag("vocab_import_mode_$idx")
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                                        Text(
                                            text = label,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (selected) colors.tealDark else colors.inkBold,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }

                        // Paste Input FIRST when importing via Paste (Smart Auto-Route or Single Set)
                        if (!isManualSingleWord) {
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
                                            text = if (isSmartRouteMode) "PASTE AI RESPONSE (AUTO LANGUAGE & LEVEL)" else "PASTE AI VOCABULARY (JSON OR TEXT)",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.teal,
                                            modifier = Modifier.weight(1f)
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
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.testTag("vocab_paste_clipboard_button")
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
                                                text = if (isSmartRouteMode) {
                                                    "Paste AI vocabulary here (mixed levels like A2/C1/C2 or mixed languages auto-sort into folders)..."
                                                } else {
                                                    "Paste JSON array [{\"word\": ..., \"meaning\": ..., \"examples\": [...]}] OR Structured Text (WORD:, MEANING:, EXAMPLES:)..."
                                                },
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

                                    if (isSmartRouteMode && liveDetectionPreview != null) {
                                        val (previewParse, previewBuckets) = liveDetectionPreview
                                        val levelsList = previewBuckets.map { VocabularyLevel.fromCode(it.levelCode).compactBadge(it.customLevelLabel) }.distinct()
                                        val detectedTransList = previewBuckets.mapNotNull { it.translationLanguage.ifBlank { null } }.distinct()
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(shape = RoundedCornerShape(6.dp), color = colors.tealLight) {
                                                Text(
                                                    text = "${previewParse.entries.size} word(s)",
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colors.tealDark,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                            previewParse.detectedLanguages.forEach { detectedLang ->
                                                val vTag = if (VocabularyLanguageVariants.isEnglish(detectedLang) && previewParse.detectedVariant.isNotBlank()) {
                                                    " · ${VocabularyLanguageVariants.shortBadge(previewParse.detectedVariant)}"
                                                } else ""
                                                Surface(shape = RoundedCornerShape(6.dp), color = colors.surfaceVariant) {
                                                    Text(
                                                        text = "$detectedLang$vTag",
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = colors.inkBold,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                            if (levelsList.isNotEmpty()) {
                                                Surface(shape = RoundedCornerShape(6.dp), color = colors.amberLight) {
                                                    Text(
                                                        text = "Levels: ${levelsList.joinToString(", ")}",
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = colors.amber,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                            if (detectedTransList.isNotEmpty()) {
                                                Surface(shape = RoundedCornerShape(6.dp), color = colors.surfaceVariant) {
                                                    Text(
                                                        text = "Trans: ${detectedTransList.joinToString(", ")}",
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = colors.inkBold,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                            Surface(shape = RoundedCornerShape(6.dp), color = colors.greenLight) {
                                                Text(
                                                    text = "${previewBuckets.size} level folder(s)",
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colors.green,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }

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
                                            text = if (isSmartRouteMode) {
                                                "Detect & merge duplicates across existing same-language folders"
                                            } else {
                                                "Also detect duplicates across other ${language.ifBlank { "English" }} folders"
                                            },
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

                        // Folder & Level Placement (shown in Add Single Word mode when creating/routing directly into a Level Folder)
                        if (isHubImport && !isSmartRouteMode) {
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
                                        text = "FOLDER & LEVEL PLACEMENT",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.teal,
                                        letterSpacing = 0.5.sp
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
                                                onClick = {
                                                    language = lang
                                                    languageVariant = if (VocabularyLanguageVariants.isEnglish(lang)) preferredEnglishVariant else ""
                                                },
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
                                            onValueChange = {
                                                language = it
                                                if (VocabularyLanguageVariants.isEnglish(it) && languageVariant.isBlank()) {
                                                    languageVariant = preferredEnglishVariant
                                                }
                                            },
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

                                    if (VocabularyLanguageVariants.isEnglish(language)) {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                text = "ENGLISH ORIGIN / STANDARD",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.inkMuted
                                            )
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                VocabularyLanguageVariants.ENGLISH_VARIANTS.forEach { v ->
                                                    val normSel = languageVariant.ifBlank { preferredEnglishVariant }
                                                    val selected = normSel.equals(v, ignoreCase = true)
                                                    Surface(
                                                        onClick = { languageVariant = v },
                                                        shape = RoundedCornerShape(7.dp),
                                                        color = if (selected) colors.tealLight else colors.surfaceVariant.copy(alpha = 0.45f),
                                                        border = BorderStroke(0.8.dp, if (selected) colors.teal else colors.border)
                                                    ) {
                                                        Text(
                                                            text = VocabularyLanguageVariants.displayLabel(v),
                                                            fontSize = 11.sp,
                                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                            color = if (selected) colors.tealDark else colors.inkBold,
                                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        VocabularyLevel.folderSelectableLevels().forEach { lvl ->
                                            val selected = levelCode.equals(lvl.code, ignoreCase = true)
                                            Surface(
                                                onClick = { levelCode = lvl.code },
                                                shape = RoundedCornerShape(7.dp),
                                                color = if (selected) colors.tealLight else colors.surfaceVariant.copy(alpha = 0.45f),
                                                border = BorderStroke(0.8.dp, if (selected) colors.teal else colors.border)
                                            ) {
                                                Text(
                                                    text = lvl.displayTitleForLanguage(language),
                                                    fontSize = 11.sp,
                                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (selected) colors.tealDark else colors.inkBold,
                                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                                )
                                            }
                                        }
                                    }

                                    val activeManualDescriptor = VocabularyLevel.fromCode(levelCode)
                                        .descriptorForLanguage(language, customLevelLabel)
                                    Text(
                                        text = activeManualDescriptor.description,
                                        fontSize = 11.sp,
                                        fontStyle = FontStyle.Italic,
                                        color = colors.inkMuted
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
                                    if (isSmartRouteMode) {
                                        val parsed = VocabularyEngine.parseVocabularyInput(
                                            rawInput = rawPaste,
                                            defaultLevelCode = safeInitialLevel,
                                            defaultCustomLevelLabel = safeInitialCustom,
                                            preferredEnglishVariant = preferredEnglishVariant
                                        )
                                        if (parsed.entries.isEmpty()) {
                                            parseError = parsed.warnings.firstOrNull() ?: "No vocabulary entries detected."
                                            return@Button
                                        }
                                        val buckets = VocabularyEngine.buildSmartRoutingPlan(
                                            parseResult = parsed,
                                            allDecks = allDecks,
                                            preferredEnglishVariant = preferredEnglishVariant
                                        )
                                        smartChoices.clear()
                                        buckets.forEach { b ->
                                            smartChoices[b.bucketKey] = VocabularySmartRouteTargetChoice(
                                                bucketKey = b.bucketKey,
                                                targetDeckId = b.recommendedDeckId,
                                                customTitle = b.defaultNewTitle,
                                                language = b.language,
                                                languageVariant = b.languageVariant,
                                                translationLanguage = b.translationLanguage,
                                                levelCode = b.levelCode,
                                                customLevelLabel = b.customLevelLabel
                                            )
                                        }
                                        pendingSmartBuckets = buckets
                                        pendingSmartParse = parsed
                                        return@Button
                                    }

                                    val resolvedVar = if (VocabularyLanguageVariants.isEnglish(language)) {
                                        languageVariant.ifBlank { preferredEnglishVariant }
                                    } else {
                                        languageVariant
                                    }

                                    val parseResult = if (!isManualSingleWord) {
                                        VocabularyEngine.parseVocabularyInput(
                                            rawInput = rawPaste,
                                            defaultLevelCode = levelCode,
                                            defaultCustomLevelLabel = customLevelLabel,
                                            preferredEnglishVariant = resolvedVar.ifBlank { preferredEnglishVariant }
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
                                         val cleanCustomForLevel = if (VocabularyLevel.fromCode(levelCode) == VocabularyLevel.CUSTOM) {
                                             customLevelLabel.trim()
                                         } else ""
                                         val entry = VocabularyEntry(
                                             word = manualWord.trim(),
                                             pronunciation = manualPron.trim(),
                                             partOfSpeech = manualPos.trim(),
                                             levelCode = levelCode,
                                             customLevelLabel = cleanCustomForLevel,
                                             language = language.trim(),
                                             languageVariant = resolvedVar,
                                             meaning = manualMeaning.trim().ifBlank { manualTranslation.trim() },
                                             translation = manualTranslation.trim(),
                                             examples = exList,
                                             synonyms = manualSynonyms.split(",").map { it.trim() }.filter { it.isNotBlank() },
                                             usageNote = manualNote.trim()
                                         )
                                         VocabularyParseResult(
                                             entries = listOf(entry),
                                             detectedFormat = VocabularyInputFormat.STRUCTURED_TEXT
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

                                     val resolvedTransLang = translationLanguage.trim().ifBlank {
                                         parseResult.detectedTranslationLanguage
                                     }
                                     if (resolvedTransLang.isNotBlank() && translationLanguage.isBlank()) {
                                         translationLanguage = resolvedTransLang
                                     }

                                     if (analysis.duplicateItems.isNotEmpty()) {
                                         pendingSmartTargetsSnapshot = emptyList()
                                         pendingAnalysis = analysis
                                     } else {
                                         val cleanCustom = if (VocabularyLevel.fromCode(levelCode) == VocabularyLevel.CUSTOM) {
                                             customLevelLabel.trim()
                                         } else ""
                                         onCommitImport(
                                             targetDeck?.id,
                                             title.trim().ifBlank { targetDeck?.title ?: "$language • ${VocabularyLevel.fromCode(levelCode).displayTitleForLanguage(language, cleanCustom)}" },
                                             language.trim().ifBlank { "English" },
                                             resolvedTransLang,
                                             levelCode,
                                             cleanCustom,
                                             domain.trim().ifBlank { "General" },
                                             analysis,
                                             emptyMap(),
                                             resolvedVar
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
                                Icon(
                                    imageVector = if (isSmartRouteMode) Icons.Default.AutoAwesome else Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = when {
                                        isSmartRouteMode -> "Analyze & Route"
                                        isManualSingleWord -> "Add Word"
                                        else -> "Analyze & Save"
                                    },
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SmartVocabularyRoutingStep(
    parseResult: VocabularyParseResult,
    buckets: List<VocabularyRouteBucket>,
    smartChoices: MutableMap<String, VocabularySmartRouteTargetChoice>,
    allDecks: List<VocabularyDeck>,
    checkCrossDeck: Boolean,
    onToggleCheckCrossDeck: (Boolean) -> Unit,
    onBack: () -> Unit,
    onConfirmRoute: () -> Unit
) {
    val colors = ExamTheme.colors
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.teal,
        unfocusedBorderColor = colors.border,
        focusedLabelColor = colors.teal,
        unfocusedLabelColor = colors.inkMuted,
        focusedTextColor = colors.inkBold,
        unfocusedTextColor = colors.inkBold
    )

    Surface(
        color = colors.background,
        modifier = Modifier
            .fillMaxSize()
            .testTag("vocab_smart_route_step")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                            text = "Destination Level Folders",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${parseResult.entries.size} word(s) sorted into ${buckets.size} Language & Level folder(s)",
                            fontSize = 11.5.sp,
                            color = colors.tealDark,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
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
                if (buckets.size > 1) {
                    item(key = "route_multi_level_banner") {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = colors.tealLight.copy(alpha = 0.65f),
                            border = BorderStroke(0.8.dp, colors.teal.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Multiple proficiency levels detected — words will be placed directly into ${buckets.size} separate level folders.",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.tealDark,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }

                if (parseResult.warnings.isNotEmpty()) {
                    item(key = "route_warning") {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = colors.amberLight,
                            border = BorderStroke(0.7.dp, colors.amber.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = parseResult.warnings.first(),
                                fontSize = 11.5.sp,
                                color = colors.inkBold,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }

                items(buckets, key = { it.bucketKey }) { bucket ->
                    val choice = smartChoices[bucket.bucketKey] ?: VocabularySmartRouteTargetChoice(
                        bucketKey = bucket.bucketKey,
                        targetDeckId = bucket.recommendedDeckId,
                        customTitle = bucket.defaultNewTitle,
                        language = bucket.language,
                        languageVariant = bucket.languageVariant,
                        translationLanguage = bucket.translationLanguage,
                        levelCode = bucket.levelCode,
                        customLevelLabel = bucket.customLevelLabel
                    )
                    val effectiveLang = choice.language.trim().ifBlank { bucket.language }
                    val levelDisplay = VocabularyLevel.fromCode(bucket.levelCode).displayTitleForLanguage(effectiveLang, bucket.customLevelLabel)
                    val isEnglish = VocabularyLanguageVariants.isEnglish(effectiveLang)
                    val effectiveTrans = choice.translationLanguage.trim()

                    fun buildDynamicDefaultTitle(langVal: String, variantVal: String, transVal: String): String {
                        val vSuffix = variantVal.takeIf { it.isNotBlank() && !it.equals("Neutral", ignoreCase = true) }
                            ?.let { " (${VocabularyLanguageVariants.shortBadge(it)})" }
                            .orEmpty()
                        return if (transVal.isNotBlank()) {
                            "$langVal$vSuffix -> $transVal • $levelDisplay"
                        } else {
                            "$langVal$vSuffix • $levelDisplay"
                        }
                    }

                    fun updateChoiceLanguages(newLang: String, newVariant: String, newTrans: String) {
                        val cleanLang = newLang.ifBlank { bucket.language }.trim()
                        val prevAuto = buildDynamicDefaultTitle(effectiveLang, choice.languageVariant, effectiveTrans)
                        val shouldUpdateTitle = choice.customTitle.isBlank() ||
                            choice.customTitle == bucket.defaultNewTitle ||
                            choice.customTitle == prevAuto
                        val nextTitle = if (shouldUpdateTitle) {
                            buildDynamicDefaultTitle(cleanLang, newVariant.trim(), newTrans.trim())
                        } else choice.customTitle
                        val targetLvl = VocabularyLevel.fromCode(bucket.levelCode)
                        val matchingDeck = allDecks.firstOrNull { d ->
                            d.language.trim().ifBlank { "English" }.equals(cleanLang, ignoreCase = true) &&
                                d.resolvedLevel() == targetLvl &&
                                (targetLvl != VocabularyLevel.CUSTOM || d.customLevelLabel.trim().equals(bucket.customLevelLabel.trim(), ignoreCase = true))
                        }
                        smartChoices[bucket.bucketKey] = choice.copy(
                            targetDeckId = matchingDeck?.id,
                            language = newLang,
                            languageVariant = newVariant,
                            translationLanguage = newTrans,
                            customTitle = nextTitle
                        )
                    }

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.surface),
                        border = BorderStroke(0.9.dp, colors.border)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Bucket summary badges (horizontally scrollable)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(shape = RoundedCornerShape(6.dp), color = colors.tealLight) {
                                    val vTag = if (choice.languageVariant.isNotBlank() && !choice.languageVariant.equals("Neutral", ignoreCase = true)) {
                                        " · ${VocabularyLanguageVariants.shortBadge(choice.languageVariant)}"
                                    } else ""
                                    Text(
                                        text = "$effectiveLang$vTag",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.tealDark,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                Surface(shape = RoundedCornerShape(6.dp), color = colors.amberLight) {
                                    Text(
                                        text = levelDisplay,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.amber,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                if (effectiveTrans.isNotBlank()) {
                                    Surface(shape = RoundedCornerShape(6.dp), color = colors.surfaceVariant) {
                                        Text(
                                            text = "-> $effectiveTrans",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.inkBold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                                Surface(shape = RoundedCornerShape(6.dp), color = colors.greenLight) {
                                    Text(
                                        text = "${bucket.entries.size} word(s)",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.green,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            // Words preview strip
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                bucket.entries.forEach { entry ->
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = colors.surfaceVariant.copy(alpha = 0.6f),
                                        border = BorderStroke(0.6.dp, colors.borderLight)
                                    ) {
                                        Text(
                                            text = entry.word,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.inkBold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            val matchingExistingWords = remember(allDecks, effectiveLang, bucket.levelCode, bucket.customLevelLabel) {
                                val targetLvl = VocabularyLevel.fromCode(bucket.levelCode)
                                allDecks.filter { d ->
                                    d.language.trim().ifBlank { "English" }.equals(effectiveLang, ignoreCase = true)
                                }.sumOf { deck ->
                                    deck.entries.count { entry ->
                                        val (eLvl, eCustom) = VocabularyEngine.resolveEffectiveEntryLevel(entry, deck)
                                        if (targetLvl == VocabularyLevel.CUSTOM) {
                                            eLvl == VocabularyLevel.CUSTOM && eCustom.equals(bucket.customLevelLabel.trim(), ignoreCase = true)
                                        } else {
                                            eLvl == targetLvl
                                        }
                                    }
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(9.dp),
                                color = if (matchingExistingWords > 0) colors.tealLight.copy(alpha = 0.55f) else colors.surfaceVariant.copy(alpha = 0.45f),
                                border = BorderStroke(0.7.dp, if (matchingExistingWords > 0) colors.teal.copy(alpha = 0.45f) else colors.borderLight),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (matchingExistingWords > 0) Icons.Default.FolderOpen else Icons.Default.CreateNewFolder,
                                        contentDescription = null,
                                        tint = if (matchingExistingWords > 0) colors.tealDark else colors.inkMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (matchingExistingWords > 0) {
                                            "Merges into existing $effectiveLang • $levelDisplay folder ($matchingExistingWords words)"
                                        } else {
                                            "Creates $effectiveLang • $levelDisplay folder"
                                        },
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (matchingExistingWords > 0) colors.tealDark else colors.inkBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            // Bilingual Language + Translation Language editor
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = choice.language,
                                    onValueChange = { newLang ->
                                        val nextVar = if (VocabularyLanguageVariants.isEnglish(newLang)) {
                                            choice.languageVariant.ifBlank { VocabularyLanguageVariants.DEFAULT_ENGLISH_VARIANT }
                                        } else ""
                                        updateChoiceLanguages(newLang, nextVar, choice.translationLanguage)
                                    },
                                    label = { Text("Language", fontSize = 11.sp) },
                                    singleLine = true,
                                    colors = fieldColors,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("smart_route_lang_input_${bucket.bucketKey}")
                                )
                                OutlinedTextField(
                                    value = choice.translationLanguage,
                                    onValueChange = { newTrans ->
                                        updateChoiceLanguages(choice.language, choice.languageVariant, newTrans)
                                    },
                                    label = { Text("Translation (Opt.)", fontSize = 11.sp) },
                                    placeholder = { Text("Monolingual", fontSize = 10.5.sp) },
                                    singleLine = true,
                                    colors = fieldColors,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("smart_route_trans_input_${bucket.bucketKey}")
                                )
                            }

                            // Quick translation language chips
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Bridge:", fontSize = 10.5.sp, color = colors.inkMuted, fontWeight = FontWeight.SemiBold)
                                listOf("" to "Monolingual", "English" to "English", "Hindi" to "Hindi", "Hinglish" to "Hinglish", "Spanish" to "Spanish")
                                    .filter { (valCode, _) -> valCode.isBlank() || !valCode.equals(effectiveLang, ignoreCase = true) }
                                    .forEach { (valCode, chipLabel) ->
                                        val sel = effectiveTrans.equals(valCode, ignoreCase = true)
                                        Surface(
                                            onClick = { updateChoiceLanguages(choice.language, choice.languageVariant, valCode) },
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (sel) colors.amberLight else colors.surfaceVariant.copy(alpha = 0.4f),
                                            border = BorderStroke(0.7.dp, if (sel) colors.amber else colors.border)
                                        ) {
                                            Text(
                                                text = chipLabel,
                                                fontSize = 10.5.sp,
                                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                                                color = if (sel) colors.amber else colors.inkBold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                            }

                            if (isEnglish) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Origin:", fontSize = 10.5.sp, color = colors.inkMuted, fontWeight = FontWeight.SemiBold)
                                    VocabularyLanguageVariants.ENGLISH_VARIANTS.forEach { v ->
                                        val sel = choice.languageVariant.equals(v, ignoreCase = true)
                                        Surface(
                                            onClick = { updateChoiceLanguages(choice.language, v, choice.translationLanguage) },
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (sel) colors.tealLight else colors.surfaceVariant.copy(alpha = 0.4f),
                                            border = BorderStroke(0.7.dp, if (sel) colors.teal else colors.border)
                                        ) {
                                            Text(
                                                text = VocabularyLanguageVariants.shortBadge(v),
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (sel) colors.tealDark else colors.inkBold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item(key = "route_cross_check") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleCheckCrossDeck(!checkCrossDeck) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checkCrossDeck,
                            onCheckedChange = onToggleCheckCrossDeck,
                            colors = CheckboxDefaults.colors(checkedColor = colors.teal)
                        )
                        Text(
                            text = "Check duplicates across all existing same-language folders",
                            fontSize = 12.sp,
                            color = colors.inkBold,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Surface(color = colors.surface, shadowElevation = 4.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Back", color = colors.inkBold)
                    }
                    Button(
                        onClick = onConfirmRoute,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("vocab_smart_route_confirm_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (buckets.size > 1) "Save to ${buckets.size} Level Folders" else "Save to Level Folder",
                            fontWeight = FontWeight.Bold
                        )
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
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 44.dp)
                                ) {
                                    Text(
                                        text = "Merge New Segments",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.tealDark,
                                        maxLines = 2,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        analysis.duplicateItems.forEach { dup ->
                                            decisions[dup.id] = VocabularyMergeDecision(action = VocabularyDuplicateAction.SKIP)
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 44.dp)
                                ) {
                                    Text(
                                        text = "Skip All Repeats",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.inkBold,
                                        maxLines = 2,
                                        textAlign = TextAlign.Center
                                    )
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
    preferredEnglishVariant: String = VocabularyLanguageVariants.DEFAULT_ENGLISH_VARIANT,
    onDismiss: () -> Unit,
    onApplyAuditFixes: (List<VocabularyAuditFinding>) -> Unit
) {
    val colors = ExamTheme.colors
    val context = LocalContext.current

    var activeTab by remember { mutableIntStateOf(0) } // 0 = Audit Prompt, 1 = Review & Apply Findings
    val auditPrompt = remember(deck, preferredEnglishVariant) {
        VocabularyEngine.buildVocabularyAuditPrompt(deck, preferredEnglishVariant)
    }
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
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) colors.tealDark else colors.inkBold,
                                    maxLines = 2,
                                    textAlign = TextAlign.Center
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
                                .heightIn(min = 120.dp, max = 300.dp)
                                .background(colors.surfaceVariant.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                                .border(0.7.dp, colors.border, RoundedCornerShape(10.dp))
                                .verticalScroll(rememberScrollState())
                                .horizontalScroll(rememberScrollState())
                                .padding(12.dp)
                        ) {
                            Text(
                                text = auditPrompt,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = colors.ink,
                                lineHeight = 16.sp,
                                softWrap = false
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
