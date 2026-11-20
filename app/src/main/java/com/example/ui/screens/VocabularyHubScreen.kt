package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.model.FlashcardSrsState
import com.example.model.FlashcardSrsStatus
import com.example.model.SrsRating
import com.example.model.VocabularyCompiledSource
import com.example.model.VocabularyDeck
import com.example.model.VocabularyEntry
import com.example.model.VocabularyLanguageFolderSummary
import com.example.model.VocabularyLanguageVariants
import com.example.model.VocabularyLevel
import com.example.model.VocabularyLevelFolderSummary
import com.example.ui.theme.ExamColorPalette
import com.example.ui.theme.ExamTheme
import com.example.util.SpacedRepetitionEngine
import com.example.util.TrustedTimeManager
import com.example.util.VocabularyAuditFinding
import com.example.util.VocabularyEngine
import com.example.util.VocabularyImportAnalysis
import com.example.util.VocabularyMergeDecision
import com.example.util.VocabularySmartRouteTargetChoice
import com.example.util.VocabularyTtsSpeaker
import com.example.util.WordOfTheDayHistoryItem
import com.example.util.WordOfTheDayService
import com.example.util.WordOfTheDayUiState
import com.example.util.rememberVocabularyTtsSpeaker
import kotlinx.coroutines.delay
import java.util.Locale

private enum class VocabListFilter(val label: String) {
    ALL("All (A–Z)"),
    DUE("Due"),
    BOOKMARKED("Saved"),
    UNMASTERED("Learning")
}

private sealed class VocabNavTier {
    data object LanguagesRoot : VocabNavTier()
    data class LanguageFolder(val language: String) : VocabNavTier()
    data class LevelFolder(
        val language: String,
        val levelCode: String,
        val customLevelLabel: String = ""
    ) : VocabNavTier()
    data class MixedFolder(val language: String, val initialTab: Int = 0) : VocabNavTier()
    data class AllDueReview(val initialTab: Int = 1) : VocabNavTier()
}

/**
 * Dedicated full-screen Vocabulary Hub — organized into a clean folder hierarchy:
 * Tier 1: Language Folders (+ Global Word Search & Cross-Language Due Review)
 * Tier 2: Proficiency Level Folders (A1 -> C2 -> Custom) + Language-Specific Word of the Day
 *         + Pinned Last "Mixed Vocabulary" All-Levels Compilation Folder
 * Tier 3: Words Directly (A-Z Word List & SM-2 Active Recall inside each Level or Mixed Compilation).
 */
@Composable
fun VocabularyHubDialog(
    decks: List<VocabularyDeck>,
    srsByDeck: Map<String, Map<String, FlashcardSrsState>>,
    preferredEnglishVariant: String = VocabularyLanguageVariants.DEFAULT_ENGLISH_VARIANT,
    wordOfTheDayState: WordOfTheDayUiState = WordOfTheDayUiState.Hidden("", 0),
    vocabularyReminderEnabled: Boolean = false,
    initialLanguageFilter: String? = null,
    initialDeckId: String? = null,
    onDismiss: () -> Unit,
    onOpenAiPromptBuilderForVocab: () -> Unit,
    onChangeEnglishVariant: (String) -> Unit = {},
    onToggleVocabularyReminder: (Boolean) -> Unit = {},
    onActiveLanguageChanged: (String?) -> Unit = {},
    onCycleNextWordOfTheDay: (String) -> Unit = {},
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
    ) -> Unit)? = null,
    onRenameDeck: (deckId: String, newTitle: String, language: String, translationLanguage: String, levelCode: String, customLevelLabel: String, languageVariant: String) -> Unit,
    onDeleteDeck: (deckId: String) -> Unit,
    onToggleEntryBookmark: (deckId: String, entryId: String) -> Unit,
    onDeleteEntry: (deckId: String, entryId: String) -> Unit,
    onRateEntry: (deckId: String, entryId: String, rating: SrsRating) -> Unit,
    onResetDeckSrs: (deckId: String) -> Unit,
    onResetLanguageSrs: (language: String) -> Unit = {},
    onResetLevelSrs: (language: String, levelCode: String, customLevelLabel: String) -> Unit = { _, _, _ -> },
    onDeleteLevelFolder: (language: String, levelCode: String, customLevelLabel: String) -> Unit = { _, _, _ -> },
    onApplyAuditFixes: (deckId: String, findings: List<VocabularyAuditFinding>) -> Unit,
    onApplyMixedAuditFixes: (originDeckIdByEntryId: Map<String, String>, findings: List<VocabularyAuditFinding>) -> Unit = { _, _ -> }
) {
    val colors = ExamTheme.colors
    val ttsSpeaker = rememberVocabularyTtsSpeaker()

    fun resolveLevelTierForDeck(
        deck: VocabularyDeck?,
        fallbackLanguage: String = "English",
        entryId: String? = null,
        levelCodeHint: String = "",
        customHint: String = ""
    ): VocabNavTier {
        if (deck != null) {
            val lang = deck.language.trim().ifBlank { fallbackLanguage.ifBlank { "English" } }
            val entry = if (!entryId.isNullOrBlank()) {
                deck.entries.firstOrNull { it.id == entryId }
            } else {
                deck.entries.firstOrNull()
            }
            val (effLvl, effCustom) = if (entry != null) {
                VocabularyEngine.resolveEffectiveEntryLevel(entry, deck)
            } else {
                val safeLvl = VocabularyLevel.fromCode(deck.levelCode)
                    .takeUnless { it == VocabularyLevel.MIXED }
                    ?: VocabularyLevel.INTERMEDIATE
                safeLvl to if (safeLvl == VocabularyLevel.CUSTOM) deck.customLevelLabel.trim() else ""
            }
            return VocabNavTier.LevelFolder(lang, effLvl.code, effCustom)
        }
        val cleanLang = fallbackLanguage.trim().ifBlank { "English" }
        if (levelCodeHint.isNotBlank()) {
            val safeLvl = VocabularyLevel.fromCode(levelCodeHint)
                .takeUnless { it == VocabularyLevel.MIXED }
                ?: VocabularyLevel.INTERMEDIATE
            val cleanCustom = if (safeLvl == VocabularyLevel.CUSTOM) customHint.trim() else ""
            return VocabNavTier.LevelFolder(cleanLang, safeLvl.code, cleanCustom)
        }
        return VocabNavTier.LanguageFolder(cleanLang)
    }

    var navTier by remember(initialDeckId, initialLanguageFilter) {
        mutableStateOf<VocabNavTier>(
            when {
                !initialDeckId.isNullOrBlank() -> {
                    val host = decks.firstOrNull { it.id == initialDeckId }
                    if (host != null) {
                        resolveLevelTierForDeck(host)
                    } else {
                        VocabNavTier.LanguagesRoot
                    }
                }
                !initialLanguageFilter.isNullOrBlank() && !initialLanguageFilter.equals("All", ignoreCase = true) -> {
                    VocabNavTier.LanguageFolder(initialLanguageFilter.trim())
                }
                else -> VocabNavTier.LanguagesRoot
            }
        )
    }

    var hubSearchQuery by remember { mutableStateOf("") }
    var importTargetDeck by remember { mutableStateOf<VocabularyDeck?>(null) }
    var importInitialLanguage by remember { mutableStateOf("English") }
    var importInitialLevelCode by remember { mutableStateOf(VocabularyLevel.INTERMEDIATE.code) }
    var importInitialCustomLabel by remember { mutableStateOf("") }
    var showImportDialog by remember { mutableStateOf(false) }

    var auditTargetDeck by remember { mutableStateOf<VocabularyDeck?>(null) }
    var auditMixedSources by remember { mutableStateOf<Map<String, VocabularyCompiledSource>?>(null) }
    var pendingResetLevel by remember { mutableStateOf<VocabularyLevelFolderSummary?>(null) }
    var pendingDeleteLevel by remember { mutableStateOf<VocabularyLevelFolderSummary?>(null) }
    var showWotdHistoryForState by remember { mutableStateOf<WordOfTheDayUiState.Ready?>(null) }

    val activeLanguageForWotd = remember(navTier) {
        when (val tier = navTier) {
            VocabNavTier.LanguagesRoot -> null
            is VocabNavTier.AllDueReview -> null
            is VocabNavTier.LanguageFolder -> tier.language
            is VocabNavTier.LevelFolder -> tier.language
            is VocabNavTier.MixedFolder -> tier.language
        }
    }

    LaunchedEffect(activeLanguageForWotd) {
        onActiveLanguageChanged(activeLanguageForWotd)
    }

    val openImportFromContext: (
        targetDeck: VocabularyDeck?,
        language: String,
        levelCode: String,
        customLabel: String
    ) -> Unit = { target, lang, lvlCode, customLvl ->
        importTargetDeck = target
        importInitialLanguage = lang.ifBlank { "English" }
        val safeLvl = VocabularyLevel.fromCode(lvlCode)
            .takeUnless { it == VocabularyLevel.MIXED }
            ?: VocabularyLevel.INTERMEDIATE
        importInitialLevelCode = safeLvl.code
        importInitialCustomLabel = if (safeLvl == VocabularyLevel.CUSTOM) customLvl else ""
        showImportDialog = true
    }

    val stepBackOneTier: () -> Unit = {
        navTier = when (val tier = navTier) {
            is VocabNavTier.MixedFolder -> VocabNavTier.LanguageFolder(tier.language)
            is VocabNavTier.AllDueReview -> VocabNavTier.LanguagesRoot
            is VocabNavTier.LevelFolder -> VocabNavTier.LanguageFolder(tier.language)
            is VocabNavTier.LanguageFolder -> VocabNavTier.LanguagesRoot
            VocabNavTier.LanguagesRoot -> {
                onDismiss()
                VocabNavTier.LanguagesRoot
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
            .testTag("vocabulary_hub_screen")
    ) {
        BackHandler {
            stepBackOneTier()
        }

        Surface(
            color = colors.background,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            when (val tier = navTier) {
                VocabNavTier.LanguagesRoot -> {
                    VocabularyLanguagesRootView(
                        decks = decks,
                        srsByDeck = srsByDeck,
                        preferredEnglishVariant = preferredEnglishVariant,
                        ttsSpeaker = ttsSpeaker,
                        reminderEnabled = vocabularyReminderEnabled,
                        onToggleReminder = onToggleVocabularyReminder,
                        searchQuery = hubSearchQuery,
                        onSearchQueryChange = { hubSearchQuery = it },
                        onBack = onDismiss,
                        onOpenLanguageFolder = { lang ->
                            hubSearchQuery = ""
                            navTier = VocabNavTier.LanguageFolder(lang)
                        },
                        onOpenLevelFolder = { summary ->
                            hubSearchQuery = ""
                            navTier = VocabNavTier.LevelFolder(
                                language = summary.language,
                                levelCode = summary.levelCode,
                                customLevelLabel = summary.customLevelLabel
                            )
                        },
                        onOpenAllDueReview = {
                            navTier = VocabNavTier.AllDueReview(initialTab = 1)
                        },
                        onOpenWordHit = { deck, entry ->
                            hubSearchQuery = ""
                            navTier = resolveLevelTierForDeck(
                                deck = deck,
                                fallbackLanguage = deck.language,
                                entryId = entry.id,
                                levelCodeHint = entry.levelCode,
                                customHint = entry.customLevelLabel
                            )
                        },
                        onOpenImport = {
                            openImportFromContext(null, "English", VocabularyLevel.INTERMEDIATE.code, "")
                        },
                        onOpenAiPromptBuilder = {
                            onDismiss()
                            onOpenAiPromptBuilderForVocab()
                        }
                    )
                }

                is VocabNavTier.LanguageFolder -> {
                    VocabularyLanguageFolderView(
                        language = tier.language,
                        decks = decks,
                        srsByDeck = srsByDeck,
                        preferredEnglishVariant = preferredEnglishVariant,
                        ttsSpeaker = ttsSpeaker,
                        wordOfTheDayState = wordOfTheDayState,
                        onBack = { navTier = VocabNavTier.LanguagesRoot },
                        onChangeEnglishVariant = onChangeEnglishVariant,
                        onCycleNextWordOfTheDay = { onCycleNextWordOfTheDay(tier.language) },
                        onOpenWotdHistory = { readyState -> showWotdHistoryForState = readyState },
                        onToggleWotdBookmark = { deckId, entryId ->
                            onToggleEntryBookmark(deckId, entryId)
                        },
                        onPracticeWotdInMixed = {
                            navTier = VocabNavTier.MixedFolder(tier.language, initialTab = 1)
                        },
                        onOpenWotdSourceLevel = { readyState ->
                            val host = decks.firstOrNull { it.id == readyState.sourceDeckId }
                            navTier = resolveLevelTierForDeck(
                                deck = host,
                                fallbackLanguage = readyState.language,
                                entryId = readyState.entry.id,
                                levelCodeHint = readyState.entry.levelCode,
                                customHint = readyState.entry.customLevelLabel
                            )
                        },
                        onOpenLevelFolder = { summary ->
                            navTier = VocabNavTier.LevelFolder(
                                language = tier.language,
                                levelCode = summary.levelCode,
                                customLevelLabel = summary.customLevelLabel
                            )
                        },
                        onAuditLevelFolder = { summary ->
                            val compiled = VocabularyEngine.compileLevelVocabularyDeck(
                                language = summary.language,
                                levelCode = summary.levelCode,
                                customLevelLabel = summary.customLevelLabel,
                                decks = decks,
                                srsByDeck = srsByDeck
                            )
                            auditMixedSources = compiled.sourceByEntryId
                            auditTargetDeck = compiled.virtualDeck
                        },
                        onResetLevelFolder = { summary ->
                            pendingResetLevel = summary
                        },
                        onDeleteLevelFolder = { summary ->
                            pendingDeleteLevel = summary
                        },
                        onOpenMixedFolder = {
                            navTier = VocabNavTier.MixedFolder(tier.language)
                        },
                        onOpenImport = {
                            openImportFromContext(null, tier.language, VocabularyLevel.INTERMEDIATE.code, "")
                        },
                        onOpenAiPromptBuilder = {
                            onDismiss()
                            onOpenAiPromptBuilderForVocab()
                        }
                    )
                }

                is VocabNavTier.LevelFolder -> {
                    val compiledLevel = remember(tier.language, tier.levelCode, tier.customLevelLabel, decks, srsByDeck) {
                        VocabularyEngine.compileLevelVocabularyDeck(
                            language = tier.language,
                            levelCode = tier.levelCode,
                            customLevelLabel = tier.customLevelLabel,
                            decks = decks,
                            srsByDeck = srsByDeck
                        )
                    }
                    VocabularyDeckDetailView(
                        deck = compiledLevel.virtualDeck,
                        srsMap = compiledLevel.srsByEntryId,
                        preferredEnglishVariant = preferredEnglishVariant,
                        ttsSpeaker = ttsSpeaker,
                        isMixedVirtualDeck = true,
                        showSourceBadges = false,
                        compiledSources = compiledLevel.sourceByEntryId,
                        onBack = { navTier = VocabNavTier.LanguageFolder(tier.language) },
                        onAddWords = {
                            openImportFromContext(null, tier.language, tier.levelCode, tier.customLevelLabel)
                        },
                        onOpenAudit = {
                            auditMixedSources = compiledLevel.sourceByEntryId
                            auditTargetDeck = compiledLevel.virtualDeck
                        },
                        onToggleBookmark = { entryId ->
                            val realDeckId = compiledLevel.sourceByEntryId[entryId]?.originDeckId
                            if (!realDeckId.isNullOrBlank()) {
                                onToggleEntryBookmark(realDeckId, entryId)
                            }
                        },
                        onDeleteEntry = { entryId ->
                            val realDeckId = compiledLevel.sourceByEntryId[entryId]?.originDeckId
                            if (!realDeckId.isNullOrBlank()) {
                                onDeleteEntry(realDeckId, entryId)
                            }
                        },
                        onRateEntry = { entryId, rating ->
                            val realDeckId = compiledLevel.sourceByEntryId[entryId]?.originDeckId
                            if (!realDeckId.isNullOrBlank()) {
                                onRateEntry(realDeckId, entryId, rating)
                            }
                        },
                        onResetSrs = {
                            onResetLevelSrs(tier.language, tier.levelCode, tier.customLevelLabel)
                        }
                    )
                }

                is VocabNavTier.MixedFolder -> {
                    val compiled = remember(tier.language, decks, srsByDeck) {
                        VocabularyEngine.compileMixedVocabularyDeck(
                            language = tier.language,
                            decks = decks,
                            srsByDeck = srsByDeck
                        )
                    }
                    VocabularyDeckDetailView(
                        deck = compiled.virtualDeck,
                        srsMap = compiled.srsByEntryId,
                        initialStudyModeTab = tier.initialTab,
                        preferredEnglishVariant = preferredEnglishVariant,
                        ttsSpeaker = ttsSpeaker,
                        isMixedVirtualDeck = true,
                        showSourceBadges = true,
                        compiledSources = compiled.sourceByEntryId,
                        onBack = { navTier = VocabNavTier.LanguageFolder(tier.language) },
                        onAddWords = {
                            openImportFromContext(null, tier.language, VocabularyLevel.INTERMEDIATE.code, "")
                        },
                        onOpenAudit = {
                            auditMixedSources = compiled.sourceByEntryId
                            auditTargetDeck = compiled.virtualDeck
                        },
                        onToggleBookmark = { entryId ->
                            val realDeckId = compiled.sourceByEntryId[entryId]?.originDeckId
                            if (!realDeckId.isNullOrBlank()) {
                                onToggleEntryBookmark(realDeckId, entryId)
                            }
                        },
                        onDeleteEntry = { entryId ->
                            val realDeckId = compiled.sourceByEntryId[entryId]?.originDeckId
                            if (!realDeckId.isNullOrBlank()) {
                                onDeleteEntry(realDeckId, entryId)
                            }
                        },
                        onRateEntry = { entryId, rating ->
                            val realDeckId = compiled.sourceByEntryId[entryId]?.originDeckId
                            if (!realDeckId.isNullOrBlank()) {
                                onRateEntry(realDeckId, entryId, rating)
                            }
                        },
                        onResetSrs = { onResetLanguageSrs(tier.language) }
                    )
                }

                is VocabNavTier.AllDueReview -> {
                    val compiledDue = remember(decks, srsByDeck) {
                        VocabularyEngine.compileAllDueVocabularyDeck(
                            decks = decks,
                            srsByDeck = srsByDeck,
                            nowMillis = TrustedTimeManager.getTrustedTimeMillis()
                        )
                    }
                    VocabularyDeckDetailView(
                        deck = compiledDue.virtualDeck,
                        srsMap = compiledDue.srsByEntryId,
                        initialStudyModeTab = tier.initialTab,
                        preferredEnglishVariant = preferredEnglishVariant,
                        ttsSpeaker = ttsSpeaker,
                        isMixedVirtualDeck = true,
                        showSourceBadges = true,
                        compiledSources = compiledDue.sourceByEntryId,
                        onBack = { navTier = VocabNavTier.LanguagesRoot },
                        onAddWords = {
                            openImportFromContext(null, "English", VocabularyLevel.INTERMEDIATE.code, "")
                        },
                        onOpenAudit = {
                            auditMixedSources = compiledDue.sourceByEntryId
                            auditTargetDeck = compiledDue.virtualDeck
                        },
                        onToggleBookmark = { entryId ->
                            val realDeckId = compiledDue.sourceByEntryId[entryId]?.originDeckId
                            if (!realDeckId.isNullOrBlank()) {
                                onToggleEntryBookmark(realDeckId, entryId)
                            }
                        },
                        onDeleteEntry = { entryId ->
                            val realDeckId = compiledDue.sourceByEntryId[entryId]?.originDeckId
                            if (!realDeckId.isNullOrBlank()) {
                                onDeleteEntry(realDeckId, entryId)
                            }
                        },
                        onRateEntry = { entryId, rating ->
                            val realDeckId = compiledDue.sourceByEntryId[entryId]?.originDeckId
                            if (!realDeckId.isNullOrBlank()) {
                                onRateEntry(realDeckId, entryId, rating)
                            }
                        },
                        onResetSrs = {}
                    )
                }
            }
        }

        if (showImportDialog) {
            VocabularyImportDialog(
                targetDeck = importTargetDeck,
                allDecks = decks,
                initialLanguage = importInitialLanguage,
                initialLevelCode = importInitialLevelCode,
                initialCustomLevelLabel = importInitialCustomLabel,
                preferredEnglishVariant = preferredEnglishVariant,
                onDismiss = {
                    showImportDialog = false
                    importTargetDeck = null
                },
                onCommitImport = { targetId, title, lang, transLang, lvlCode, customLvl, dom, analysis, decisions, langVariant ->
                    onCommitImport(targetId, title, lang, transLang, lvlCode, customLvl, dom, analysis, decisions, langVariant)
                },
                onCommitSmartRoutedImport = onCommitSmartRoutedImport
            )
        }

        auditTargetDeck?.let { target ->
            val mixedMap = auditMixedSources
            VocabularyAuditHubDialog(
                deck = target,
                preferredEnglishVariant = preferredEnglishVariant,
                onDismiss = {
                    auditTargetDeck = null
                    auditMixedSources = null
                },
                onApplyAuditFixes = { findings ->
                    if (mixedMap != null) {
                        val originByEntryId = mixedMap.mapValues { it.value.originDeckId }
                        onApplyMixedAuditFixes(originByEntryId, findings)
                    } else {
                        onApplyAuditFixes(target.id, findings)
                    }
                }
            )
        }

        showWotdHistoryForState?.let { readyState ->
            WordOfTheDayHistoryDialog(
                language = readyState.language,
                cycleNumber = readyState.cycleNumber,
                seenInCycleCount = readyState.seenInCycleCount,
                uniqueWordCount = readyState.uniqueWordCount,
                history = readyState.history,
                colors = colors,
                onDismiss = { showWotdHistoryForState = null },
                onOpenHistoryItem = { historyItem ->
                    showWotdHistoryForState = null
                    val host = decks.firstOrNull { it.id == historyItem.sourceDeckId }
                    navTier = resolveLevelTierForDeck(
                        deck = host,
                        fallbackLanguage = readyState.language,
                        levelCodeHint = historyItem.levelCode,
                        customHint = historyItem.customLevelLabel
                    )
                }
            )
        }
    }

    pendingResetLevel?.let { summary ->
        AlertDialog(
            onDismissRequest = { pendingResetLevel = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .widthIn(max = 460.dp),
            containerColor = colors.surface,
            titleContentColor = colors.inkBold,
            textContentColor = colors.ink,
            title = { Text("Reset Level Progress?", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Text(
                    "Reset SM-2 spaced repetition progress for \"${summary.language} • ${summary.levelTitle}\"? Your saved words will remain intact.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onResetLevelSrs(summary.language, summary.levelCode, summary.customLevelLabel)
                        pendingResetLevel = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.amber, contentColor = Color.White),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text("Reset") }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingResetLevel = null },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel", color = colors.inkMuted)
                }
            }
        )
    }

    pendingDeleteLevel?.let { summary ->
        AlertDialog(
            onDismissRequest = { pendingDeleteLevel = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .widthIn(max = 460.dp),
            containerColor = colors.surface,
            titleContentColor = colors.inkBold,
            textContentColor = colors.ink,
            title = { Text("Delete Level Folder?", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Text(
                    "\"${summary.language} • ${summary.levelTitle}\" (${summary.uniqueWords} words) and its spaced repetition history will be permanently removed.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val currentTier = navTier
                        if (currentTier is VocabNavTier.LevelFolder &&
                            currentTier.language.equals(summary.language, ignoreCase = true) &&
                            currentTier.levelCode.equals(summary.levelCode, ignoreCase = true) &&
                            currentTier.customLevelLabel.equals(summary.customLevelLabel, ignoreCase = true)
                        ) {
                            navTier = VocabNavTier.LanguageFolder(summary.language)
                        }
                        onDeleteLevelFolder(summary.language, summary.levelCode, summary.customLevelLabel)
                        pendingDeleteLevel = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.red, contentColor = Color.White),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingDeleteLevel = null },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel", color = colors.inkMuted)
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VocabularyLanguagesRootView(
    decks: List<VocabularyDeck>,
    srsByDeck: Map<String, Map<String, FlashcardSrsState>>,
    preferredEnglishVariant: String,
    ttsSpeaker: VocabularyTtsSpeaker,
    reminderEnabled: Boolean,
    onToggleReminder: (Boolean) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onOpenLanguageFolder: (String) -> Unit,
    onOpenLevelFolder: (VocabularyLevelFolderSummary) -> Unit,
    onOpenAllDueReview: () -> Unit,
    onOpenWordHit: (VocabularyDeck, VocabularyEntry) -> Unit,
    onOpenImport: () -> Unit,
    onOpenAiPromptBuilder: () -> Unit
) {
    val colors = ExamTheme.colors
    val context = LocalContext.current
    val nowMillis = TrustedTimeManager.getTrustedTimeMillis()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onToggleReminder(true)
        } else {
            Toast.makeText(
                context,
                "Notification permission denied. Enable notifications in Android settings to receive daily vocabulary reminders.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val handleReminderClick: () -> Unit = {
        if (reminderEnabled) {
            onToggleReminder(false)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                onToggleReminder(true)
            } else {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            onToggleReminder(true)
        }
    }

    val languageSummaries = remember(decks, srsByDeck, nowMillis) {
        VocabularyEngine.buildLanguageFolderSummaries(
            decks = decks,
            srsByDeck = srsByDeck,
            nowMillis = nowMillis,
            wotdThreshold = WordOfTheDayService.MIN_WORDS_THRESHOLD
        )
    }

    val totalWords = remember(languageSummaries) { languageSummaries.sumOf { it.uniqueWords } }
    val totalLevels = remember(languageSummaries) { languageSummaries.sumOf { it.levelCount } }
    val totalDue = remember(languageSummaries) { languageSummaries.sumOf { it.dueCount } }

    val q = searchQuery.trim().lowercase(Locale.ROOT)
    val matchingLevelFolders = remember(languageSummaries, decks, srsByDeck, q, nowMillis) {
        if (q.isBlank()) emptyList() else {
            languageSummaries.flatMap { langSummary ->
                VocabularyEngine.buildLevelFolderSummaries(
                    language = langSummary.language,
                    decks = decks,
                    srsByDeck = srsByDeck,
                    nowMillis = nowMillis
                )
            }.filter { summary ->
                summary.language.lowercase(Locale.ROOT).contains(q) ||
                    summary.levelTitle.lowercase(Locale.ROOT).contains(q) ||
                    summary.compactBadge.lowercase(Locale.ROOT).contains(q) ||
                    summary.customLevelLabel.lowercase(Locale.ROOT).contains(q)
            }.sortedWith(compareBy({ it.language.lowercase(Locale.ROOT) }, { it.sortRank }, { it.levelTitle.lowercase(Locale.ROOT) }))
        }
    }
    val matchingWordHits = remember(decks, q) {
        if (q.isBlank()) emptyList() else VocabularyEngine.searchEntriesAcrossDecks(decks, q, limit = 25)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header Bar
        Surface(color = colors.surface, shadowElevation = 1.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("vocab_hub_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close Vocabulary Hub", tint = colors.inkBold)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Vocabulary Hub",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.inkBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${languageSummaries.size} Language(s) • $totalLevels Level(s) • $totalWords Words${if (totalDue > 0) " • $totalDue Due" else ""}",
                        fontSize = 11.5.sp,
                        color = colors.inkMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = handleReminderClick,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("vocab_hub_reminder_toggle")
                ) {
                    Icon(
                        imageVector = if (reminderEnabled) Icons.Default.Notifications else Icons.Default.NotificationsNone,
                        contentDescription = if (reminderEnabled) "Disable daily vocabulary reminder" else "Enable daily vocabulary reminder",
                        tint = if (reminderEnabled) colors.teal else colors.inkMuted
                    )
                }
                IconButton(
                    onClick = onOpenAiPromptBuilder,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("vocab_hub_prompt_shortcut")
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "Vocabulary AI Prompt Builder", tint = colors.amber)
                }
                Button(
                    onClick = onOpenImport,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .heightIn(min = 48.dp)
                        .testTag("vocab_hub_new_set_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Import", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (decks.isNotEmpty()) {
                item(key = "vocab_hub_search") {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("Search languages, levels, or any word...", fontSize = 12.5.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(18.dp)) },
                        trailingIcon = if (searchQuery.isNotEmpty()) {
                            {
                                IconButton(
                                    onClick = { onSearchQueryChange("") },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear search", tint = colors.inkMuted, modifier = Modifier.size(17.dp))
                                }
                            }
                        } else null,
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.teal,
                            unfocusedBorderColor = colors.border,
                            focusedContainerColor = colors.surface,
                            unfocusedContainerColor = colors.surface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("vocab_hub_search_input")
                    )
                }
            }

            if (decks.isEmpty()) {
                item(key = "vocab_empty_state") {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.surface),
                        border = BorderStroke(0.9.dp, colors.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = colors.tealLight,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Translate, contentDescription = null, tint = colors.teal, modifier = Modifier.size(28.dp))
                                }
                            }
                            Text(
                                text = "Build Your Multi-Language Vocabulary",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Organize cleanly by Language Folder -> Proficiency Level Folder (Beginner A1 to Mastery C2 or Custom Exam Track) + Mixed Vocabulary compilation. Each language unlocks its own offline UTC Word of the Day once ${WordOfTheDayService.MIN_WORDS_THRESHOLD}+ words are saved.",
                                fontSize = 12.5.sp,
                                color = colors.inkMuted,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = onOpenImport,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add or Paste Vocabulary", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = onOpenAiPromptBuilder,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, colors.teal.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = colors.amber, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Generate Vocabulary Prompt with AI Builder", color = colors.tealDark, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else if (q.isNotBlank()) {
                if (matchingWordHits.isNotEmpty()) {
                    item(key = "vocab_search_words_header") {
                        Text(
                            text = "MATCHING WORDS (${matchingWordHits.size})",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkMuted,
                            letterSpacing = 0.6.sp
                        )
                    }
                    items(
                        items = matchingWordHits,
                        key = { "word_hit_${it.deck.id}_${it.entry.id}" }
                    ) { hit ->
                        val (effLvl, effCustom) = VocabularyEngine.resolveEffectiveEntryLevel(hit.entry, hit.deck)
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = colors.surface),
                            border = BorderStroke(0.75.dp, colors.border),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenWordHit(hit.deck, hit.entry) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Text(
                                            text = hit.entry.word,
                                            fontSize = 14.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = colors.inkBold
                                        )
                                        Surface(shape = RoundedCornerShape(5.dp), color = colors.tealLight) {
                                            Text(
                                                text = "${hit.deck.language} • ${effLvl.compactBadge(effCustom)}",
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.tealDark,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = hit.entry.meaning.ifBlank { hit.entry.translation },
                                        fontSize = 12.sp,
                                        color = colors.inkMuted,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        ttsSpeaker.speak(
                                            text = hit.entry.word,
                                            language = hit.deck.language,
                                            preferredEnglishVariant = preferredEnglishVariant,
                                            utteranceKey = "search_${hit.entry.id}"
                                        )
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = "Pronounce ${hit.entry.word}",
                                        tint = colors.teal,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                if (matchingLevelFolders.isNotEmpty()) {
                    item(key = "vocab_search_header") {
                        Text(
                            text = "MATCHING LEVEL FOLDERS (${matchingLevelFolders.size})",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkMuted,
                            letterSpacing = 0.6.sp
                        )
                    }
                    items(
                        items = matchingLevelFolders,
                        key = { "search_lvl_${it.language}_${it.levelCode}_${it.customLevelLabel}" }
                    ) { levelSummary ->
                        LevelFolderCard(
                            summary = levelSummary,
                            colors = colors,
                            showLanguageInTitle = true,
                            onOpen = { onOpenLevelFolder(levelSummary) }
                        )
                    }
                }
                if (matchingLevelFolders.isEmpty() && matchingWordHits.isEmpty()) {
                    item(key = "vocab_search_empty") {
                        Text(
                            text = "No vocabulary folders or words match \"$searchQuery\".",
                            fontSize = 13.sp,
                            color = colors.inkMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 28.dp)
                        )
                    }
                }
            } else {
                if (totalDue > 0) {
                    item(key = "vocab_all_due_banner") {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = colors.amberLight.copy(alpha = 0.55f)),
                            border = BorderStroke(1.dp, colors.amber.copy(alpha = 0.55f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("vocab_all_due_banner")
                        ) {
                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.widthIn(min = 180.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "$totalDue Word(s) Due Across All Languages",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = colors.inkBold
                                    )
                                    Text(
                                        text = "Review overdue & new SM-2 cards together in one quick session.",
                                        fontSize = 11.5.sp,
                                        color = colors.inkMuted
                                    )
                                }
                                Button(
                                    onClick = onOpenAllDueReview,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.amber,
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("vocab_review_all_due_button")
                                ) {
                                    Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Review Due", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                item(key = "vocab_lang_folders_header") {
                    Text(
                        text = "LANGUAGE FOLDERS (${languageSummaries.size})",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkMuted,
                        letterSpacing = 0.6.sp
                    )
                }
                items(languageSummaries, key = { it.language.lowercase(Locale.ROOT) }) { summary ->
                    LanguageFolderCard(
                        summary = summary,
                        colors = colors,
                        onOpen = { onOpenLanguageFolder(summary.language) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageFolderCard(
    summary: VocabularyLanguageFolderSummary,
    colors: ExamColorPalette,
    onOpen: () -> Unit
) {
    val masteryFraction = if (summary.uniqueWords > 0) {
        (summary.masteredCount.toFloat() / summary.uniqueWords.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Card(
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(0.9.dp, colors.border),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
            .testTag("vocab_lang_folder_${summary.language.lowercase(Locale.ROOT)}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.tealLight,
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = "${summary.language} folder",
                            tint = colors.tealDark,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = summary.language,
                        fontSize = 16.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.inkBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${summary.levelCount} Level Folder(s) • ${summary.uniqueWords} Words • Mixed Vocabulary ready",
                        fontSize = 11.5.sp,
                        color = colors.inkMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Open ${summary.language} folder",
                    tint = colors.inkMuted,
                    modifier = Modifier.size(22.dp)
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(shape = RoundedCornerShape(6.dp), color = colors.surfaceVariant) {
                    Text(
                        text = "${summary.uniqueWords} Words",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Surface(shape = RoundedCornerShape(6.dp), color = colors.greenLight) {
                    Text(
                        text = "${summary.masteredCount} Mastered",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.green,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                if (summary.dueCount > 0) {
                    Surface(shape = RoundedCornerShape(6.dp), color = colors.amberLight) {
                        Text(
                            text = "${summary.dueCount} Due",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.amber,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                if (summary.wotdUnlocked) {
                    Surface(shape = RoundedCornerShape(6.dp), color = colors.tealLight) {
                        Text(
                            text = "Word of the Day Active",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.tealDark,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            LinearProgressIndicator(
                progress = { masteryFraction },
                color = colors.teal,
                trackColor = colors.borderLight,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VocabularyLanguageFolderView(
    language: String,
    decks: List<VocabularyDeck>,
    srsByDeck: Map<String, Map<String, FlashcardSrsState>>,
    preferredEnglishVariant: String,
    ttsSpeaker: VocabularyTtsSpeaker,
    wordOfTheDayState: WordOfTheDayUiState,
    onBack: () -> Unit,
    onChangeEnglishVariant: (String) -> Unit,
    onCycleNextWordOfTheDay: () -> Unit,
    onOpenWotdHistory: (WordOfTheDayUiState.Ready) -> Unit,
    onToggleWotdBookmark: (deckId: String, entryId: String) -> Unit,
    onPracticeWotdInMixed: () -> Unit,
    onOpenWotdSourceLevel: (WordOfTheDayUiState.Ready) -> Unit,
    onOpenLevelFolder: (VocabularyLevelFolderSummary) -> Unit,
    onAuditLevelFolder: (VocabularyLevelFolderSummary) -> Unit,
    onResetLevelFolder: (VocabularyLevelFolderSummary) -> Unit,
    onDeleteLevelFolder: (VocabularyLevelFolderSummary) -> Unit,
    onOpenMixedFolder: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenAiPromptBuilder: () -> Unit
) {
    val colors = ExamTheme.colors
    val nowMillis = TrustedTimeManager.getTrustedTimeMillis()
    val isEnglish = VocabularyLanguageVariants.isEnglish(language)

    val levelSummaries = remember(language, decks, srsByDeck, nowMillis) {
        VocabularyEngine.buildLevelFolderSummaries(
            language = language,
            decks = decks,
            srsByDeck = srsByDeck,
            nowMillis = nowMillis
        )
    }

    val compiledMixed = remember(language, decks, srsByDeck) {
        VocabularyEngine.compileMixedVocabularyDeck(
            language = language,
            decks = decks,
            srsByDeck = srsByDeck
        )
    }
    val mixedStats = remember(compiledMixed, nowMillis) {
        VocabularyEngine.computeDeckSrsStats(
            deck = compiledMixed.virtualDeck,
            srsMap = compiledMixed.srsByEntryId,
            nowMillis = nowMillis
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header with breadcrumb
        Surface(color = colors.surface, shadowElevation = 1.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("vocab_lang_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Languages", tint = colors.inkBold)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = language,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.inkBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Vocabulary Hub / $language • ${compiledMixed.virtualDeck.entries.size} Unique Words",
                        fontSize = 11.5.sp,
                        color = colors.inkMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onOpenAiPromptBuilder,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("vocab_lang_prompt_shortcut")
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "Vocabulary AI Prompt Builder", tint = colors.amber)
                }
                Button(
                    onClick = onOpenImport,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .heightIn(min = 48.dp)
                        .testTag("vocab_lang_import_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Import", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // English-only UK/US/Neutral variant bar (never shown on non-English language folders)
            if (isEnglish) {
                item(key = "vocab_english_variant_bar") {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colors.surface,
                        border = BorderStroke(0.8.dp, colors.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FlowRow(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "English Origin:",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkMuted,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                            VocabularyLanguageVariants.ENGLISH_VARIANTS.forEach { variant ->
                                val active = preferredEnglishVariant.equals(variant, ignoreCase = true)
                                Surface(
                                    onClick = { onChangeEnglishVariant(variant) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (active) colors.teal else colors.surfaceVariant.copy(alpha = 0.6f),
                                    border = BorderStroke(0.8.dp, if (active) colors.teal else colors.border),
                                    modifier = Modifier
                                        .heightIn(min = 40.dp)
                                        .testTag("vocab_lang_variant_${variant.lowercase(Locale.ROOT)}")
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = variant,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (active) Color.White else colors.inkBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Language-Specific, Offline, UTC-00:00, Threshold-Gated Word of the Day
            if (wordOfTheDayState is WordOfTheDayUiState.Ready &&
                wordOfTheDayState.language.equals(language, ignoreCase = true)
            ) {
                item(key = "vocab_word_of_the_day") {
                    val liveBookmarked = remember(decks, wordOfTheDayState.sourceDeckId, wordOfTheDayState.entry.id) {
                        decks.firstOrNull { it.id == wordOfTheDayState.sourceDeckId }
                            ?.entries
                            ?.firstOrNull { it.id == wordOfTheDayState.entry.id }
                            ?.isBookmarked
                            ?: wordOfTheDayState.entry.isBookmarked
                    }
                    WordOfTheDayCard(
                        state = wordOfTheDayState,
                        liveBookmarked = liveBookmarked,
                        preferredEnglishVariant = preferredEnglishVariant,
                        ttsSpeaker = ttsSpeaker,
                        colors = colors,
                        onNextWord = onCycleNextWordOfTheDay,
                        onOpenHistory = { onOpenWotdHistory(wordOfTheDayState) },
                        onToggleBookmark = {
                            onToggleWotdBookmark(wordOfTheDayState.sourceDeckId, wordOfTheDayState.entry.id)
                        },
                        onPracticeMixed = onPracticeWotdInMixed,
                        onOpenSourceLevel = { onOpenWotdSourceLevel(wordOfTheDayState) }
                    )
                }
            }

            if (levelSummaries.isEmpty()) {
                item(key = "vocab_lang_empty") {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.surface),
                        border = BorderStroke(0.8.dp, colors.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "No $language words yet",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold
                            )
                            Text(
                                text = "Import or generate $language vocabulary to populate proficiency level folders (A1–C2) and unlock Mixed Vocabulary + daily UTC Word of the Day.",
                                fontSize = 12.5.sp,
                                color = colors.inkMuted,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                            Button(
                                onClick = onOpenImport,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add $language Vocabulary", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                item(key = "vocab_levels_header") {
                    Text(
                        text = "PROFICIENCY LEVEL FOLDERS (${levelSummaries.size})",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkMuted,
                        letterSpacing = 0.6.sp
                    )
                }

                items(
                    items = levelSummaries,
                    key = { "${it.levelCode}_${it.customLevelLabel}" }
                ) { levelSummary ->
                    LevelFolderCard(
                        summary = levelSummary,
                        colors = colors,
                        onOpen = { onOpenLevelFolder(levelSummary) },
                        onAudit = { onAuditLevelFolder(levelSummary) },
                        onResetSrs = { onResetLevelFolder(levelSummary) },
                        onDelete = { onDeleteLevelFolder(levelSummary) }
                    )
                }

                // Pinned Last Subfolder: Mixed Vocabulary (compiles all levels of this language)
                if (compiledMixed.virtualDeck.entries.isNotEmpty()) {
                    item(key = "vocab_mixed_header") {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "ALL-LEVELS COMPILATION",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkMuted,
                            letterSpacing = 0.6.sp
                        )
                    }
                    item(key = "vocab_mixed_folder_card") {
                        MixedVocabularyFolderCard(
                            language = language,
                            uniqueWords = compiledMixed.virtualDeck.entries.size,
                            compiledLevelCount = compiledMixed.compiledLevelCount,
                            dueCount = mixedStats.dueCount,
                            masteredCount = mixedStats.masteredCount,
                            colors = colors,
                            onOpen = onOpenMixedFolder
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LevelFolderCard(
    summary: VocabularyLevelFolderSummary,
    colors: ExamColorPalette,
    showLanguageInTitle: Boolean = false,
    onOpen: () -> Unit,
    onAudit: (() -> Unit)? = null,
    onResetSrs: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val masteryFraction = if (summary.uniqueWords > 0) {
        (summary.masteredCount.toFloat() / summary.uniqueWords.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(0.9.dp, colors.border),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
            .testTag("vocab_level_folder_${summary.language.lowercase(Locale.ROOT)}_${summary.levelCode.lowercase(Locale.ROOT)}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = colors.amberLight,
                    border = BorderStroke(0.8.dp, colors.amber.copy(alpha = 0.4f)),
                    modifier = Modifier.widthIn(min = 48.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = summary.compactBadge,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.amber,
                            maxLines = 1
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = if (showLanguageInTitle) "${summary.language} • ${summary.levelTitle}" else summary.levelTitle,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${summary.uniqueWords} Words • ${summary.masteredCount} Mastered",
                        fontSize = 11.5.sp,
                        color = colors.inkMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (summary.dueCount > 0) {
                    Surface(shape = RoundedCornerShape(6.dp), color = colors.amberLight) {
                        Text(
                            text = "${summary.dueCount} Due",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.amber,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
                if (onAudit != null || onResetSrs != null || onDelete != null) {
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "${summary.levelTitle} folder options", tint = colors.inkMuted)
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            onAudit?.let { auditAction ->
                                DropdownMenuItem(
                                    text = { Text("AI Audit Level") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.FactCheck, contentDescription = null, tint = colors.teal) },
                                    onClick = { menuExpanded = false; auditAction() }
                                )
                            }
                            onResetSrs?.let { resetAction ->
                                DropdownMenuItem(
                                    text = { Text("Reset Level Progress") },
                                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = colors.amber) },
                                    onClick = { menuExpanded = false; resetAction() }
                                )
                            }
                            onDelete?.let { deleteAction ->
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Delete Level Folder", color = colors.red) },
                                    leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = colors.red) },
                                    onClick = { menuExpanded = false; deleteAction() }
                                )
                            }
                        }
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Open ${summary.levelTitle}",
                        tint = colors.inkMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            LinearProgressIndicator(
                progress = { masteryFraction },
                color = colors.teal,
                trackColor = colors.borderLight,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MixedVocabularyFolderCard(
    language: String,
    uniqueWords: Int,
    compiledLevelCount: Int,
    dueCount: Int,
    masteredCount: Int,
    colors: ExamColorPalette,
    onOpen: () -> Unit
) {
    val masteryFraction = if (uniqueWords > 0) {
        (masteredCount.toFloat() / uniqueWords.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Card(
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.2.dp, colors.teal.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
            .testTag("vocab_mixed_folder_${language.lowercase(Locale.ROOT)}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(11.dp),
                    color = colors.tealLight,
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "Mixed Vocabulary compilation",
                            tint = colors.tealDark,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Mixed Vocabulary",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.inkBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Compiles all $uniqueWords $language words across $compiledLevelCount level(s) for unified A–Z study & SM-2 recall.",
                        fontSize = 11.5.sp,
                        color = colors.inkMuted,
                        lineHeight = 16.sp
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Open Mixed Vocabulary",
                    tint = colors.tealDark,
                    modifier = Modifier.size(22.dp)
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(shape = RoundedCornerShape(6.dp), color = colors.tealLight) {
                    Text(
                        text = "$uniqueWords Unique",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.tealDark,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Surface(shape = RoundedCornerShape(6.dp), color = colors.greenLight) {
                    Text(
                        text = "$masteredCount Mastered",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.green,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                if (dueCount > 0) {
                    Surface(shape = RoundedCornerShape(6.dp), color = colors.amberLight) {
                        Text(
                            text = "$dueCount Due",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.amber,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Surface(shape = RoundedCornerShape(6.dp), color = colors.surfaceVariant) {
                    Text(
                        text = "Live Sync",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.inkBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            LinearProgressIndicator(
                progress = { masteryFraction },
                color = colors.teal,
                trackColor = colors.borderLight,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
            )
        }
    }
}

@Composable
private fun VocabularyDeckDetailView(
    deck: VocabularyDeck,
    srsMap: Map<String, FlashcardSrsState>,
    initialStudyModeTab: Int = 0,
    preferredEnglishVariant: String = VocabularyLanguageVariants.DEFAULT_ENGLISH_VARIANT,
    ttsSpeaker: VocabularyTtsSpeaker? = null,
    isMixedVirtualDeck: Boolean = false,
    showSourceBadges: Boolean = isMixedVirtualDeck,
    compiledSources: Map<String, VocabularyCompiledSource> = emptyMap(),
    onBack: () -> Unit,
    onAddWords: () -> Unit,
    onOpenAudit: () -> Unit,
    onToggleBookmark: (entryId: String) -> Unit,
    onDeleteEntry: (entryId: String) -> Unit,
    onRateEntry: (entryId: String, rating: SrsRating) -> Unit,
    onResetSrs: () -> Unit
) {
    val colors = ExamTheme.colors
    val activeSpeaker = ttsSpeaker ?: rememberVocabularyTtsSpeaker()
    val nowMillis = TrustedTimeManager.getTrustedTimeMillis()
    var studyModeTab by remember(deck.id, initialStudyModeTab) { mutableIntStateOf(initialStudyModeTab) } // 0 = A-Z List, 1 = SM-2 Active Recall
    var listFilter by remember(deck.id) { mutableStateOf(VocabListFilter.ALL) }
    var searchQuery by remember(deck.id) { mutableStateOf("") }
    var hideMeanings by remember(deck.id) { mutableStateOf(false) }
    val expandedIds = remember(deck.id) { mutableStateMapOf<String, Boolean>() }
    var pendingDeleteEntry by remember(deck.id) { mutableStateOf<VocabularyEntry?>(null) }

    val sortedEntries = remember(deck.entries) { deck.sortedEntriesAscending() }
    val filteredEntries = remember(sortedEntries, srsMap, listFilter, searchQuery) {
        val q = searchQuery.trim().lowercase(Locale.ROOT)
        sortedEntries.filter { entry ->
            val srsStatus = SpacedRepetitionEngine.statusOf(srsMap[entry.id], nowMillis)
            val filterMatch = when (listFilter) {
                VocabListFilter.ALL -> true
                VocabListFilter.DUE -> srsStatus == FlashcardSrsStatus.DUE || srsStatus == FlashcardSrsStatus.NEW
                VocabListFilter.BOOKMARKED -> entry.isBookmarked
                VocabListFilter.UNMASTERED -> srsStatus != FlashcardSrsStatus.MASTERED
            }
            val queryMatch = q.isBlank() ||
                entry.word.lowercase(Locale.ROOT).contains(q) ||
                entry.meaning.lowercase(Locale.ROOT).contains(q) ||
                entry.translation.lowercase(Locale.ROOT).contains(q) ||
                entry.partOfSpeech.lowercase(Locale.ROOT).contains(q)
            filterMatch && queryMatch
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Deck Header
        Surface(color = colors.surface, shadowElevation = 1.dp) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.inkBold)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = deck.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isMixedVirtualDeck) {
                                "${deck.language} • ${deck.domain} • ${deck.entries.size} Words"
                            } else {
                                "${deck.languageDisplay()} • ${deck.effectiveLevelTitle()} • ${deck.entries.size} Words"
                            },
                            fontSize = 11.sp,
                            color = colors.inkMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onOpenAudit,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("vocab_deck_audit_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.FactCheck, contentDescription = "Vocabulary Audit Hub", tint = colors.teal)
                    }
                    IconButton(
                        onClick = onAddWords,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("vocab_deck_add_words_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Import or add words", tint = colors.inkBold)
                    }
                }

                // Mode Switcher: A-Z Word List vs SM-2 Active Recall
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        Triple(0, "A–Z Word List", Icons.Default.SortByAlpha),
                        Triple(1, "SM-2 Active Recall", Icons.Default.Psychology)
                    ).forEach { (idx, label, icon) ->
                        val selected = studyModeTab == idx
                        Surface(
                            onClick = { studyModeTab = idx },
                            shape = RoundedCornerShape(9.dp),
                            color = if (selected) colors.tealLight else colors.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(if (selected) 1.2.dp else 0.7.dp, if (selected) colors.teal else colors.border),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("vocab_deck_tab_$idx")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(icon, contentDescription = null, tint = if (selected) colors.tealDark else colors.inkMuted, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
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
            }
        }

        if (studyModeTab == 1) {
            VocabularyRecallView(
                deck = deck,
                srsMap = srsMap,
                onRateEntry = onRateEntry,
                onResetDeckSrs = onResetSrs,
                preferredEnglishVariant = preferredEnglishVariant,
                ttsSpeaker = activeSpeaker,
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                // Search + Quick Self-Test Hide Meanings bar
                item(key = "vocab_list_controls") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Filter A–Z words...", fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(18.dp)) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.teal,
                                    unfocusedBorderColor = colors.border,
                                    focusedContainerColor = colors.surface,
                                    unfocusedContainerColor = colors.surface
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                onClick = { hideMeanings = !hideMeanings },
                                shape = RoundedCornerShape(10.dp),
                                color = if (hideMeanings) colors.amberLight else colors.surface,
                                border = BorderStroke(0.9.dp, if (hideMeanings) colors.amber else colors.border),
                                modifier = Modifier
                                    .heightIn(min = 52.dp)
                                    .testTag("vocab_hide_meanings_toggle")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (hideMeanings) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle hide meanings",
                                        tint = if (hideMeanings) colors.amber else colors.inkMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (hideMeanings) "Hidden" else "Recall",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (hideMeanings) colors.amber else colors.inkBold
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            VocabListFilter.entries.forEach { f ->
                                VocabFilterChip(
                                    label = f.label,
                                    selected = listFilter == f,
                                    colors = colors,
                                    onClick = { listFilter = f }
                                )
                            }
                        }
                    }
                }

                if (filteredEntries.isEmpty()) {
                    item(key = "vocab_list_empty") {
                        Text(
                            text = if (deck.entries.isEmpty()) "No words yet. Tap + above to paste JSON/Text or add words." else "No words match this filter.",
                            fontSize = 13.sp,
                            color = colors.inkMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp)
                        )
                    }
                } else {
                    items(filteredEntries, key = { it.id }) { entry ->
                        val isExpanded = expandedIds[entry.id] == true
                        val srsStatus = SpacedRepetitionEngine.statusOf(srsMap[entry.id], nowMillis)
                        val sourceInfo = if (showSourceBadges) compiledSources[entry.id] else null
                        VocabularyWordExpandableCard(
                            entry = entry,
                            srsStatus = srsStatus,
                            sourceDeckTitle = sourceInfo?.originDeckTitle,
                            speakLanguage = entry.language.ifBlank { deck.language },
                            preferredEnglishVariant = preferredEnglishVariant,
                            ttsSpeaker = activeSpeaker,
                            hideMeanings = hideMeanings,
                            isExpanded = isExpanded,
                            colors = colors,
                            onToggleExpand = { expandedIds[entry.id] = !isExpanded },
                            onToggleBookmark = { onToggleBookmark(entry.id) },
                            onDelete = { pendingDeleteEntry = entry }
                        )
                    }
                }
            }
        }
    }

    pendingDeleteEntry?.let { target ->
        val originTitle = compiledSources[target.id]?.originDeckTitle ?: deck.title
        AlertDialog(
            onDismissRequest = { pendingDeleteEntry = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .widthIn(max = 460.dp)
                .testTag("vocab_delete_word_dialog"),
            containerColor = colors.surface,
            titleContentColor = colors.inkBold,
            textContentColor = colors.ink,
            title = { Text("Remove \"${target.word}\"?", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Text(
                    "\"${target.word}\" and its spaced repetition history will be permanently removed from \"$originTitle\".",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        expandedIds.remove(target.id)
                        onDeleteEntry(target.id)
                        pendingDeleteEntry = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.red, contentColor = Color.White),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("vocab_delete_word_confirm_button")
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingDeleteEntry = null },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("vocab_delete_word_cancel_button")
                ) {
                    Text("Cancel", color = colors.inkMuted)
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VocabularyWordExpandableCard(
    entry: VocabularyEntry,
    srsStatus: FlashcardSrsStatus,
    sourceDeckTitle: String? = null,
    speakLanguage: String = "English",
    preferredEnglishVariant: String = VocabularyLanguageVariants.DEFAULT_ENGLISH_VARIANT,
    ttsSpeaker: VocabularyTtsSpeaker,
    hideMeanings: Boolean,
    isExpanded: Boolean,
    colors: ExamColorPalette,
    onToggleExpand: () -> Unit,
    onToggleBookmark: () -> Unit,
    onDelete: () -> Unit
) {
    val (statusBg, statusFg) = when (srsStatus) {
        FlashcardSrsStatus.NEW -> colors.tealLight to colors.tealDark
        FlashcardSrsStatus.DUE -> colors.amberLight to colors.amber
        FlashcardSrsStatus.LEARNING -> colors.surfaceVariant to colors.inkBold
        FlashcardSrsStatus.MASTERED -> colors.greenLight to colors.green
    }

    Card(
        shape = RoundedCornerShape(13.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(if (isExpanded) 1.dp else 0.7.dp, if (isExpanded) colors.teal.copy(alpha = 0.5f) else colors.border),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleExpand() }
            .testTag("vocab_word_card_${entry.normalizedWordKey()}")
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = entry.word,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.inkBold
                        )
                        if (entry.partOfSpeech.isNotBlank()) {
                            Surface(shape = RoundedCornerShape(5.dp), color = colors.surfaceVariant) {
                                Text(
                                    text = entry.partOfSpeech,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.inkMuted,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Surface(shape = RoundedCornerShape(5.dp), color = statusBg) {
                            Text(
                                text = entry.effectiveLevelBadge(),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusFg,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                        if (!sourceDeckTitle.isNullOrBlank()) {
                            Surface(shape = RoundedCornerShape(5.dp), color = colors.surfaceVariant.copy(alpha = 0.7f)) {
                                Text(
                                    text = sourceDeckTitle,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.inkMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .widthIn(max = 160.dp)
                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    if (entry.pronunciation.isNotBlank()) {
                        Text(
                            text = entry.pronunciation,
                            fontSize = 11.5.sp,
                            color = colors.teal,
                            fontStyle = FontStyle.Italic
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            ttsSpeaker.speak(
                                text = entry.word,
                                language = speakLanguage,
                                preferredEnglishVariant = preferredEnglishVariant,
                                utteranceKey = "word_${entry.id}"
                            )
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Pronounce ${entry.word}",
                            tint = colors.teal,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    IconButton(onClick = onToggleBookmark, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = if (entry.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Bookmark word",
                            tint = if (entry.isBookmarked) colors.amber else colors.inkMuted,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse word" else "Expand word",
                        tint = colors.inkMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (hideMeanings && !isExpanded) {
                Text(
                    text = "Tap to reveal meaning, translation & ${entry.examples.size} context example(s)…",
                    fontSize = 12.sp,
                    color = colors.amber,
                    fontStyle = FontStyle.Italic
                )
            } else {
                Text(
                    text = entry.meaning,
                    fontSize = 13.sp,
                    color = colors.ink,
                    lineHeight = 18.sp,
                    maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry.translation.isNotBlank()) {
                    Text(
                        text = entry.translation,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.tealDark
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider(color = colors.borderLight)

                    if (entry.examples.isNotEmpty()) {
                        Text(
                            text = "CONTEXT EXAMPLES (${entry.examples.size})",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkMuted,
                            letterSpacing = 0.5.sp
                        )
                        entry.examples.forEach { ex ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = colors.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(0.6.dp, colors.borderLight),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(ex.context.uppercase(Locale.ROOT), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = colors.teal)
                                    Text(ex.sentence, fontSize = 12.5.sp, color = colors.inkBold, lineHeight = 18.sp)
                                    if (ex.translation.isNotBlank()) {
                                        Text(ex.translation, fontSize = 11.5.sp, color = colors.inkMuted, fontStyle = FontStyle.Italic)
                                    }
                                }
                            }
                        }
                    }

                    if (entry.synonyms.isNotEmpty() || entry.antonyms.isNotEmpty() || entry.collocations.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            entry.synonyms.forEach { syn ->
                                Surface(shape = RoundedCornerShape(6.dp), color = colors.tealLight.copy(alpha = 0.5f)) {
                                    Text("≈ $syn", fontSize = 11.sp, color = colors.tealDark, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
                                }
                            }
                            entry.antonyms.forEach { ant ->
                                Surface(shape = RoundedCornerShape(6.dp), color = colors.redLight) {
                                    Text("≠ $ant", fontSize = 11.sp, color = colors.red, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
                                }
                            }
                            entry.collocations.forEach { col ->
                                Surface(shape = RoundedCornerShape(6.dp), color = colors.surfaceVariant) {
                                    Text("• $col", fontSize = 11.sp, color = colors.ink, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
                                }
                            }
                        }
                    }

                    if (entry.usageNote.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colors.amberLight.copy(alpha = 0.55f),
                            border = BorderStroke(0.6.dp, colors.amber.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Usage Note: ${entry.usageNote}",
                                fontSize = 11.5.sp,
                                color = colors.inkBold,
                                lineHeight = 16.sp,
                                modifier = Modifier.padding(9.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = onDelete,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = colors.red, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Remove Word", fontSize = 11.5.sp, color = colors.red, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VocabFilterChip(
    label: String,
    selected: Boolean,
    colors: ExamColorPalette,
    isAccentAmber: Boolean = false,
    onClick: () -> Unit
) {
    val bg = when {
        selected && isAccentAmber -> colors.amberLight
        selected -> colors.tealLight
        else -> colors.surface
    }
    val fg = when {
        selected && isAccentAmber -> colors.amber
        selected -> colors.tealDark
        else -> colors.inkBold
    }
    val borderColor = when {
        selected && isAccentAmber -> colors.amber
        selected -> colors.teal
        else -> colors.border
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = bg,
        border = BorderStroke(if (selected) 1.1.dp else 0.7.dp, borderColor),
        modifier = Modifier.heightIn(min = 44.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = label,
                fontSize = 11.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = fg,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordOfTheDayCard(
    state: WordOfTheDayUiState.Ready,
    liveBookmarked: Boolean,
    preferredEnglishVariant: String,
    ttsSpeaker: VocabularyTtsSpeaker,
    colors: ExamColorPalette,
    onNextWord: () -> Unit,
    onOpenHistory: () -> Unit,
    onToggleBookmark: () -> Unit,
    onPracticeMixed: () -> Unit,
    onOpenSourceLevel: () -> Unit
) {
    var expanded by remember(state.utcDateKey, state.entry.id) { mutableStateOf(false) }
    var countdownLabel by remember {
        mutableStateOf(WordOfTheDayService.formatUtcCountdownLabel(TrustedTimeManager.getTrustedTimeMillis()))
    }
    LaunchedEffect(state.utcDateKey) {
        while (true) {
            countdownLabel = WordOfTheDayService.formatUtcCountdownLabel(TrustedTimeManager.getTrustedTimeMillis())
            delay(30_000L)
        }
    }
    val entry = state.entry

    Card(
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.teal.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("vocab_word_of_the_day_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header: UTC badge + Countdown + Cycle progress + Speak, Bookmark, History & Next actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Surface(shape = RoundedCornerShape(6.dp), color = colors.tealLight) {
                        Text(
                            text = "WORD OF THE DAY",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.tealDark,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                    Surface(shape = RoundedCornerShape(6.dp), color = colors.surfaceVariant) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "UTC • ${state.dateLabel} • $countdownLabel",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.inkBold
                            )
                        }
                    }
                    Surface(shape = RoundedCornerShape(6.dp), color = colors.amberLight) {
                        Text(
                            text = "Cycle ${state.cycleNumber} • ${state.seenInCycleCount}/${state.uniqueWordCount}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.amber,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            ttsSpeaker.speak(
                                text = entry.word,
                                language = state.language,
                                preferredEnglishVariant = preferredEnglishVariant,
                                utteranceKey = "wotd_${entry.id}"
                            )
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("vocab_wotd_speak_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Pronounce ${entry.word}",
                            tint = colors.teal,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onToggleBookmark,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("vocab_wotd_bookmark_button")
                    ) {
                        Icon(
                            imageVector = if (liveBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = if (liveBookmarked) "Remove bookmark" else "Bookmark Word of the Day",
                            tint = if (liveBookmarked) colors.amber else colors.inkMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onOpenHistory,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("vocab_wotd_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Word of the Day History",
                            tint = colors.inkBold,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onNextWord,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("vocab_wotd_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Next Word of the Day",
                            tint = colors.teal,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Headword + phonetic + POS + level badge
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = entry.word,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.inkBold
                )
                if (entry.pronunciation.isNotBlank()) {
                    Text(
                        text = entry.pronunciation,
                        fontSize = 12.5.sp,
                        color = colors.teal,
                        fontStyle = FontStyle.Italic
                    )
                }
                if (entry.partOfSpeech.isNotBlank()) {
                    Surface(shape = RoundedCornerShape(5.dp), color = colors.surfaceVariant) {
                        Text(
                            text = entry.partOfSpeech,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.inkMuted,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                if (state.variant.isNotBlank()) {
                    Surface(shape = RoundedCornerShape(5.dp), color = colors.amberLight) {
                        Text(
                            text = state.variant,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.amber,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                if (entry.levelCode.isNotBlank()) {
                    Surface(shape = RoundedCornerShape(5.dp), color = colors.tealLight) {
                        Text(
                            text = entry.effectiveLevelBadge(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.tealDark,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Text(
                text = entry.meaning,
                fontSize = 13.sp,
                color = colors.ink,
                lineHeight = 19.sp,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis
            )

            if (entry.translation.isNotBlank()) {
                Text(
                    text = entry.translation,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.tealDark
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (entry.examples.isNotEmpty()) {
                        entry.examples.take(2).forEach { ex ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = colors.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(0.6.dp, colors.borderLight),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(ex.context.uppercase(Locale.ROOT), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = colors.teal)
                                    Text(ex.sentence, fontSize = 12.sp, color = colors.inkBold, lineHeight = 17.sp)
                                    if (ex.translation.isNotBlank()) {
                                        Text(ex.translation, fontSize = 11.5.sp, color = colors.inkMuted, fontStyle = FontStyle.Italic)
                                    }
                                }
                            }
                        }
                    }
                    if (entry.synonyms.isNotEmpty() || entry.antonyms.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            entry.synonyms.forEach { syn ->
                                Surface(shape = RoundedCornerShape(6.dp), color = colors.tealLight.copy(alpha = 0.5f)) {
                                    Text("≈ $syn", fontSize = 11.sp, color = colors.tealDark, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
                                }
                            }
                            entry.antonyms.forEach { ant ->
                                Surface(shape = RoundedCornerShape(6.dp), color = colors.redLight) {
                                    Text("≠ $ant", fontSize = 11.sp, color = colors.red, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
                                }
                            }
                        }
                    }
                    if (entry.usageNote.isNotBlank()) {
                        Text(
                            text = entry.usageNote,
                            fontSize = 11.5.sp,
                            color = colors.inkMuted,
                            fontStyle = FontStyle.Italic,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(
                    onClick = onOpenSourceLevel,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("vocab_wotd_open_deck_button")
                ) {
                    val wotdLevel = VocabularyLevel.fromCode(entry.levelCode)
                        .takeUnless { it == VocabularyLevel.MIXED }
                        ?: VocabularyLevel.INTERMEDIATE
                    Icon(Icons.Default.Folder, contentDescription = null, tint = colors.teal, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Open ${wotdLevel.displayTitle(entry.customLevelLabel)}",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.tealDark,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                TextButton(
                    onClick = onPracticeMixed,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("vocab_wotd_practice_mixed_button")
                ) {
                    Icon(Icons.Default.Psychology, contentDescription = null, tint = colors.amber, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Practice Mixed",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.amber
                    )
                }

                TextButton(
                    onClick = { expanded = !expanded },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(
                        text = if (expanded) "Hide Details" else "Examples & Synonyms",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.inkMuted
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = colors.inkMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordOfTheDayHistoryDialog(
    language: String,
    cycleNumber: Int,
    seenInCycleCount: Int,
    uniqueWordCount: Int,
    history: List<WordOfTheDayHistoryItem>,
    colors: ExamColorPalette,
    onDismiss: () -> Unit,
    onOpenHistoryItem: (WordOfTheDayHistoryItem) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 24.dp)
            .widthIn(max = 520.dp)
            .testTag("vocab_wotd_history_dialog"),
        containerColor = colors.surface,
        titleContentColor = colors.inkBold,
        textContentColor = colors.ink,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "$language • Word of the Day History",
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.inkBold
                )
                Text(
                    text = "Resets daily at 00:00 UTC • Cycle $cycleNumber ($seenInCycleCount/$uniqueWordCount seen without skips)",
                    fontSize = 11.5.sp,
                    color = colors.inkMuted,
                    lineHeight = 16.sp
                )
            }
        },
        text = {
            if (history.isEmpty()) {
                Text(
                    text = "No Word of the Day history recorded yet for $language.",
                    fontSize = 13.sp,
                    color = colors.inkMuted,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = history,
                        key = { "${it.utcDateKey}_${it.wordKey}_${it.shownAtMillis}" }
                    ) { item ->
                        Surface(
                            onClick = { onOpenHistoryItem(item) },
                            shape = RoundedCornerShape(10.dp),
                            color = colors.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(0.7.dp, colors.borderLight),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Surface(shape = RoundedCornerShape(5.dp), color = colors.tealLight) {
                                        Text(
                                            text = "${item.utcDateKey} UTC",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.tealDark,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    if (item.levelCode.isNotBlank()) {
                                        val lvl = VocabularyLevel.fromCode(item.levelCode)
                                            .takeUnless { it == VocabularyLevel.MIXED }
                                            ?: VocabularyLevel.INTERMEDIATE
                                        Surface(shape = RoundedCornerShape(5.dp), color = colors.amberLight) {
                                            Text(
                                                text = lvl.compactBadge(item.customLevelLabel),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.amber,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    if (item.partOfSpeech.isNotBlank()) {
                                        Text(
                                            text = item.partOfSpeech,
                                            fontSize = 10.5.sp,
                                            color = colors.inkMuted,
                                            modifier = Modifier.align(Alignment.CenterVertically)
                                        )
                                    }
                                }
                                Text(
                                    text = item.word,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = colors.inkBold
                                )
                                if (item.meaning.isNotBlank()) {
                                    Text(
                                        text = item.meaning,
                                        fontSize = 12.sp,
                                        color = colors.ink,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 16.sp
                                    )
                                }
                                if (item.translation.isNotBlank()) {
                                    Text(
                                        text = item.translation,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.tealDark
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Close")
            }
        }
    )
}
