package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Psychology
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.FlashcardSrsState
import com.example.model.FlashcardSrsStatus
import com.example.model.SrsRating
import com.example.model.VocabularyDeck
import com.example.model.VocabularyEntry
import com.example.model.VocabularyLevel
import com.example.ui.theme.ExamColorPalette
import com.example.ui.theme.ExamTheme
import com.example.util.SpacedRepetitionEngine
import com.example.util.VocabularyAuditFinding
import com.example.util.VocabularyEngine
import com.example.util.VocabularyImportAnalysis
import com.example.util.VocabularyMergeDecision

private enum class VocabListFilter(val label: String) {
    ALL("All (A–Z)"),
    DUE("Due"),
    BOOKMARKED("Saved"),
    UNMASTERED("Learning")
}

/**
 * Dedicated full-screen Vocabulary Hub — completely separate from Knowledge Sets.
 * Hierarchy: Vocabulary Hub -> Language -> Proficiency Level -> Vocabulary Set -> A-Z Word List & SM-2 Recall.
 */
@Composable
fun VocabularyHubDialog(
    decks: List<VocabularyDeck>,
    srsByDeck: Map<String, Map<String, FlashcardSrsState>>,
    initialLanguageFilter: String? = null,
    initialDeckId: String? = null,
    onDismiss: () -> Unit,
    onOpenAiPromptBuilderForVocab: () -> Unit,
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
    ) -> Unit,
    onRenameDeck: (deckId: String, newTitle: String, language: String, translationLanguage: String, levelCode: String, customLevelLabel: String) -> Unit,
    onDeleteDeck: (deckId: String) -> Unit,
    onToggleEntryBookmark: (deckId: String, entryId: String) -> Unit,
    onDeleteEntry: (deckId: String, entryId: String) -> Unit,
    onRateEntry: (deckId: String, entryId: String, rating: SrsRating) -> Unit,
    onResetDeckSrs: (deckId: String) -> Unit,
    onApplyAuditFixes: (deckId: String, findings: List<VocabularyAuditFinding>) -> Unit
) {
    val colors = ExamTheme.colors
    var selectedLanguage by remember { mutableStateOf(initialLanguageFilter ?: "All") }
    var selectedLevelCode by remember { mutableStateOf("ALL") }
    var activeDeckId by remember { mutableStateOf(initialDeckId) }
    var hubSearchQuery by remember { mutableStateOf("") }

    var importTargetDeck by remember { mutableStateOf<VocabularyDeck?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var auditTargetDeck by remember { mutableStateOf<VocabularyDeck?>(null) }
    var renameTargetDeck by remember { mutableStateOf<VocabularyDeck?>(null) }
    var deleteTargetDeck by remember { mutableStateOf<VocabularyDeck?>(null) }

    val activeDeck = remember(decks, activeDeckId) {
        activeDeckId?.let { id -> decks.firstOrNull { it.id == id } }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        BackHandler {
            if (activeDeck != null) {
                activeDeckId = null
            } else {
                onDismiss()
            }
        }

        Surface(
            color = colors.background,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .testTag("vocabulary_hub_screen")
        ) {
            if (activeDeck != null) {
                VocabularyDeckDetailView(
                    deck = activeDeck,
                    srsMap = srsByDeck[activeDeck.id].orEmpty(),
                    onBack = { activeDeckId = null },
                    onAddWords = {
                        importTargetDeck = activeDeck
                        showImportDialog = true
                    },
                    onOpenAudit = { auditTargetDeck = activeDeck },
                    onToggleBookmark = { entryId -> onToggleEntryBookmark(activeDeck.id, entryId) },
                    onDeleteEntry = { entryId -> onDeleteEntry(activeDeck.id, entryId) },
                    onRateEntry = { entryId, rating -> onRateEntry(activeDeck.id, entryId, rating) },
                    onResetSrs = { onResetDeckSrs(activeDeck.id) }
                )
            } else {
                VocabularyHubOverview(
                    decks = decks,
                    srsByDeck = srsByDeck,
                    selectedLanguage = selectedLanguage,
                    onSelectLanguage = { selectedLanguage = it; selectedLevelCode = "ALL" },
                    selectedLevelCode = selectedLevelCode,
                    onSelectLevelCode = { selectedLevelCode = it },
                    searchQuery = hubSearchQuery,
                    onSearchQueryChange = { hubSearchQuery = it },
                    onClose = onDismiss,
                    onOpenDeck = { activeDeckId = it.id },
                    onCreateNewDeck = {
                        importTargetDeck = null
                        showImportDialog = true
                    },
                    onOpenAiPromptBuilder = {
                        onDismiss()
                        onOpenAiPromptBuilderForVocab()
                    },
                    onAuditDeck = { auditTargetDeck = it },
                    onRenameDeck = { renameTargetDeck = it },
                    onDeleteDeck = { deleteTargetDeck = it }
                )
            }
        }
    }

    if (showImportDialog) {
        VocabularyImportDialog(
            targetDeck = importTargetDeck,
            allDecks = decks,
            initialLanguage = if (selectedLanguage == "All") "English" else selectedLanguage,
            initialLevelCode = if (selectedLevelCode == "ALL") VocabularyLevel.INTERMEDIATE.code else selectedLevelCode,
            onDismiss = {
                showImportDialog = false
                importTargetDeck = null
            },
            onCommitImport = { targetId, title, lang, transLang, lvlCode, customLvl, dom, analysis, decisions ->
                onCommitImport(targetId, title, lang, transLang, lvlCode, customLvl, dom, analysis, decisions)
            }
        )
    }

    auditTargetDeck?.let { target ->
        VocabularyAuditHubDialog(
            deck = target,
            onDismiss = { auditTargetDeck = null },
            onApplyAuditFixes = { findings ->
                onApplyAuditFixes(target.id, findings)
            }
        )
    }

    renameTargetDeck?.let { target ->
        EditVocabularyDeckMetadataDialog(
            deck = target,
            colors = colors,
            onDismiss = { renameTargetDeck = null },
            onSave = { newTitle, newLang, newTrans, newLvl, newCustomLvl ->
                onRenameDeck(target.id, newTitle, newLang, newTrans, newLvl, newCustomLvl)
                renameTargetDeck = null
            }
        )
    }

    deleteTargetDeck?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTargetDeck = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .widthIn(max = 460.dp),
            containerColor = colors.surface,
            titleContentColor = colors.inkBold,
            textContentColor = colors.ink,
            title = { Text("Delete Vocabulary Set?", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Text(
                    "\"${target.title}\" (${target.entries.size} words) and its spaced repetition history will be permanently removed.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (activeDeckId == target.id) activeDeckId = null
                        onDeleteDeck(target.id)
                        deleteTargetDeck = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.red, contentColor = Color.White)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTargetDeck = null }) {
                    Text("Cancel", color = colors.inkMuted)
                }
            }
        )
    }
}

@Composable
private fun VocabularyHubOverview(
    decks: List<VocabularyDeck>,
    srsByDeck: Map<String, Map<String, FlashcardSrsState>>,
    selectedLanguage: String,
    onSelectLanguage: (String) -> Unit,
    selectedLevelCode: String,
    onSelectLevelCode: (String) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    onOpenDeck: (VocabularyDeck) -> Unit,
    onCreateNewDeck: () -> Unit,
    onOpenAiPromptBuilder: () -> Unit,
    onAuditDeck: (VocabularyDeck) -> Unit,
    onRenameDeck: (VocabularyDeck) -> Unit,
    onDeleteDeck: (VocabularyDeck) -> Unit
) {
    val colors = ExamTheme.colors
    val nowMillis = System.currentTimeMillis()

    val languages = remember(decks) {
        decks.map { it.language.trim().ifBlank { "English" } }
            .distinctBy { it.lowercase() }
            .sorted()
    }

    val languageFilteredDecks = remember(decks, selectedLanguage) {
        if (selectedLanguage == "All") decks
        else decks.filter { it.language.equals(selectedLanguage, ignoreCase = true) }
    }

    val availableLevels = remember(languageFilteredDecks) {
        languageFilteredDecks
            .map { it.resolvedLevel() }
            .distinct()
            .sortedBy { it.sortRank }
    }

    val visibleDecks = remember(languageFilteredDecks, selectedLevelCode, searchQuery) {
        val q = searchQuery.trim().lowercase()
        languageFilteredDecks
            .filter { deck ->
                val lvlMatch = selectedLevelCode == "ALL" || deck.levelCode.equals(selectedLevelCode, ignoreCase = true)
                val queryMatch = q.isBlank() ||
                    deck.title.lowercase().contains(q) ||
                    deck.language.lowercase().contains(q) ||
                    deck.domain.lowercase().contains(q) ||
                    deck.entries.any { it.word.lowercase().contains(q) || it.meaning.lowercase().contains(q) }
                lvlMatch && queryMatch
            }
            .sortedWith(
                compareBy<VocabularyDeck>(
                    { it.language.lowercase() },
                    { it.resolvedLevel().sortRank },
                    { it.title.lowercase() }
                )
            )
    }

    val totalWords = remember(decks) { decks.sumOf { it.entries.size } }
    val totalDue = remember(decks, srsByDeck) {
        decks.sumOf { deck ->
            VocabularyEngine.computeDeckSrsStats(deck, srsByDeck[deck.id].orEmpty(), nowMillis).dueCount
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Header Bar
        Surface(color = colors.surface, shadowElevation = 1.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose, modifier = Modifier.testTag("vocab_hub_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.inkBold)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Vocabulary Hub",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.inkBold
                    )
                    Text(
                        text = "${decks.size} Sets • $totalWords Words${if (totalDue > 0) " • $totalDue Due" else ""}",
                        fontSize = 11.5.sp,
                        color = colors.inkMuted
                    )
                }
                IconButton(
                    onClick = onOpenAiPromptBuilder,
                    modifier = Modifier.testTag("vocab_hub_prompt_shortcut")
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "Vocabulary AI Prompt Builder", tint = colors.amber)
                }
                Button(
                    onClick = onCreateNewDeck,
                    shape = RoundedCornerShape(9.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .heightIn(min = 40.dp)
                        .testTag("vocab_hub_new_set_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New Set", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
            // Search across sets & words
            if (decks.isNotEmpty()) {
                item(key = "vocab_hub_search") {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("Search sets, languages, or any word...", fontSize = 12.5.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(18.dp)) },
                        trailingIcon = if (searchQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = { onSearchQueryChange("") }) {
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
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Tier 1: Language Selector Strip
            if (languages.isNotEmpty()) {
                item(key = "vocab_lang_filter") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "1. LANGUAGE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkMuted,
                            letterSpacing = 0.6.sp
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            VocabFilterChip(
                                label = "All Languages (${decks.size})",
                                selected = selectedLanguage == "All",
                                colors = colors,
                                onClick = { onSelectLanguage("All") }
                            )
                            languages.forEach { lang ->
                                val count = decks.count { it.language.equals(lang, ignoreCase = true) }
                                VocabFilterChip(
                                    label = "$lang ($count)",
                                    selected = selectedLanguage.equals(lang, ignoreCase = true),
                                    colors = colors,
                                    onClick = { onSelectLanguage(lang) }
                                )
                            }
                        }
                    }
                }
            }

            // Tier 2: Proficiency Level Selector Strip
            if (availableLevels.isNotEmpty()) {
                item(key = "vocab_level_filter") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "2. PROFICIENCY LEVEL",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkMuted,
                            letterSpacing = 0.6.sp
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            VocabFilterChip(
                                label = "All Levels",
                                selected = selectedLevelCode == "ALL",
                                colors = colors,
                                isAccentAmber = true,
                                onClick = { onSelectLevelCode("ALL") }
                            )
                            availableLevels.forEach { lvl ->
                                VocabFilterChip(
                                    label = lvl.displayTitle(),
                                    selected = selectedLevelCode.equals(lvl.code, ignoreCase = true),
                                    colors = colors,
                                    isAccentAmber = true,
                                    onClick = { onSelectLevelCode(lvl.code) }
                                )
                            }
                        }
                    }
                }
            }

            // Empty Onboarding Card when user hasn't created any Vocabulary Set yet
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
                                text = "Organize by Language -> Level (Beginner A1 to Mastery C2 or Custom) -> Set. Includes A–Z word lists, multiple context examples, SM-2 spaced repetition recall, duplicate merge review, and AI Audit Hub.",
                                fontSize = 12.5.sp,
                                color = colors.inkMuted,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = onCreateNewDeck,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Create or Paste Vocabulary Set", fontWeight = FontWeight.Bold)
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
            } else if (visibleDecks.isEmpty()) {
                item(key = "vocab_no_matches") {
                    Text(
                        text = "No vocabulary sets match the selected filter.",
                        fontSize = 13.sp,
                        color = colors.inkMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp)
                    )
                }
            } else {
                item(key = "vocab_sets_header") {
                    Text(
                        text = "3. VOCABULARY SETS (${visibleDecks.size})",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkMuted,
                        letterSpacing = 0.6.sp
                    )
                }
                items(visibleDecks, key = { it.id }) { deck ->
                    val stats = remember(deck, srsByDeck[deck.id]) {
                        VocabularyEngine.computeDeckSrsStats(deck, srsByDeck[deck.id].orEmpty(), nowMillis)
                    }
                    VocabularyDeckCard(
                        deck = deck,
                        dueCount = stats.dueCount,
                        masteredCount = stats.masteredCount,
                        colors = colors,
                        onOpen = { onOpenDeck(deck) },
                        onAudit = { onAuditDeck(deck) },
                        onRename = { onRenameDeck(deck) },
                        onDelete = { onDeleteDeck(deck) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VocabularyDeckCard(
    deck: VocabularyDeck,
    dueCount: Int,
    masteredCount: Int,
    colors: ExamColorPalette,
    onOpen: () -> Unit,
    onAudit: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val total = deck.entries.size
    val masteryFraction = if (total > 0) (masteredCount.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(0.9.dp, colors.border),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
            .testTag("vocab_deck_card_${deck.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(6.dp), color = colors.tealLight) {
                            Text(
                                text = deck.languageDisplay(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.tealDark,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                        Surface(shape = RoundedCornerShape(6.dp), color = colors.amberLight) {
                            Text(
                                text = deck.effectiveLevelTitle(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.amber,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = deck.title,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Deck options", tint = colors.inkMuted)
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("AI Audit Hub") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.FactCheck, contentDescription = null, tint = colors.teal) },
                            onClick = { menuExpanded = false; onAudit() }
                        )
                        DropdownMenuItem(
                            text = { Text("Edit Title / Language / Level") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = colors.inkBold) },
                            onClick = { menuExpanded = false; onRename() }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Delete Set", color = colors.red) },
                            leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = colors.red) },
                            onClick = { menuExpanded = false; onDelete() }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$total Words • $masteredCount Mastered • ${deck.domain}",
                    fontSize = 11.5.sp,
                    color = colors.inkMuted
                )
                if (dueCount > 0) {
                    Surface(shape = RoundedCornerShape(6.dp), color = colors.amberLight) {
                        Text(
                            text = "$dueCount Due",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.amber,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
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

@Composable
private fun VocabularyDeckDetailView(
    deck: VocabularyDeck,
    srsMap: Map<String, FlashcardSrsState>,
    onBack: () -> Unit,
    onAddWords: () -> Unit,
    onOpenAudit: () -> Unit,
    onToggleBookmark: (entryId: String) -> Unit,
    onDeleteEntry: (entryId: String) -> Unit,
    onRateEntry: (entryId: String, rating: SrsRating) -> Unit,
    onResetSrs: () -> Unit
) {
    val colors = ExamTheme.colors
    val nowMillis = System.currentTimeMillis()
    var studyModeTab by remember(deck.id) { mutableIntStateOf(0) } // 0 = A-Z List, 1 = SM-2 Active Recall
    var listFilter by remember(deck.id) { mutableStateOf(VocabListFilter.ALL) }
    var searchQuery by remember(deck.id) { mutableStateOf("") }
    var hideMeanings by remember(deck.id) { mutableStateOf(false) }
    val expandedIds = remember(deck.id) { mutableStateMapOf<String, Boolean>() }

    val sortedEntries = remember(deck.entries) { deck.sortedEntriesAscending() }
    val filteredEntries = remember(sortedEntries, srsMap, listFilter, searchQuery) {
        val q = searchQuery.trim().lowercase()
        sortedEntries.filter { entry ->
            val srsStatus = SpacedRepetitionEngine.statusOf(srsMap[entry.id], nowMillis)
            val filterMatch = when (listFilter) {
                VocabListFilter.ALL -> true
                VocabListFilter.DUE -> srsStatus == FlashcardSrsStatus.DUE || srsStatus == FlashcardSrsStatus.NEW
                VocabListFilter.BOOKMARKED -> entry.isBookmarked
                VocabListFilter.UNMASTERED -> srsStatus != FlashcardSrsStatus.MASTERED
            }
            val queryMatch = q.isBlank() ||
                entry.word.lowercase().contains(q) ||
                entry.meaning.lowercase().contains(q) ||
                entry.translation.lowercase().contains(q) ||
                entry.partOfSpeech.lowercase().contains(q)
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
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Vocabulary Hub", tint = colors.inkBold)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = deck.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${deck.languageDisplay()} • ${deck.effectiveLevelTitle()} • ${deck.entries.size} Words",
                            fontSize = 11.sp,
                            color = colors.inkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onOpenAudit,
                        modifier = Modifier.testTag("vocab_deck_audit_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.FactCheck, contentDescription = "Vocabulary Audit Hub", tint = colors.teal)
                    }
                    IconButton(
                        onClick = onAddWords,
                        modifier = Modifier.testTag("vocab_deck_add_words_button")
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
                                .heightIn(min = 44.dp)
                                .testTag("vocab_deck_tab_$idx")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
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
                                    maxLines = 1
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
                                    modifier = Modifier.padding(horizontal = 10.dp),
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
                        VocabularyWordExpandableCard(
                            entry = entry,
                            srsStatus = srsStatus,
                            hideMeanings = hideMeanings,
                            isExpanded = isExpanded,
                            colors = colors,
                            onToggleExpand = { expandedIds[entry.id] = !isExpanded },
                            onToggleBookmark = { onToggleBookmark(entry.id) },
                            onDelete = { onDeleteEntry(entry.id) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VocabularyWordExpandableCard(
    entry: VocabularyEntry,
    srsStatus: FlashcardSrsStatus,
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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                    IconButton(onClick = onToggleBookmark, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = if (entry.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Bookmark word",
                            tint = if (entry.isBookmarked) colors.amber else colors.inkMuted,
                            modifier = Modifier.size(18.dp)
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

            // Meaning & Translation (masked cleanly when Hide Meanings active-recall mode is ON & collapsed)
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

            // Expanded multi-context examples, lexical network & actions
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
                                    Text(ex.context.uppercase(), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = colors.teal)
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
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = colors.red, modifier = Modifier.size(15.dp))
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
        border = BorderStroke(if (selected) 1.1.dp else 0.7.dp, borderColor)
    ) {
        Text(
            text = label,
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = fg,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditVocabularyDeckMetadataDialog(
    deck: VocabularyDeck,
    colors: ExamColorPalette,
    onDismiss: () -> Unit,
    onSave: (title: String, language: String, translationLanguage: String, levelCode: String, customLevelLabel: String) -> Unit
) {
    var title by remember(deck.id) { mutableStateOf(deck.title) }
    var language by remember(deck.id) { mutableStateOf(deck.language) }
    var translation by remember(deck.id) { mutableStateOf(deck.translationLanguage) }
    var levelCode by remember(deck.id) { mutableStateOf(deck.levelCode) }
    var customLevel by remember(deck.id) { mutableStateOf(deck.customLevelLabel) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .widthIn(max = 460.dp),
        containerColor = colors.surface,
        titleContentColor = colors.inkBold,
        textContentColor = colors.ink,
        title = { Text("Edit Vocabulary Set", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Set Title", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = language,
                        onValueChange = { language = it },
                        label = { Text("Language", fontSize = 11.5.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = translation,
                        onValueChange = { translation = it },
                        label = { Text("Translation", fontSize = 11.5.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    VocabularyLevel.entries.forEach { lvl ->
                        val sel = levelCode.equals(lvl.code, ignoreCase = true)
                        Surface(
                            onClick = { levelCode = lvl.code },
                            shape = RoundedCornerShape(6.dp),
                            color = if (sel) colors.tealLight else colors.surfaceVariant,
                            border = BorderStroke(0.7.dp, if (sel) colors.teal else colors.border)
                        ) {
                            Text(
                                text = lvl.shortBadge,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (sel) colors.tealDark else colors.inkBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
                if (VocabularyLevel.fromCode(levelCode) == VocabularyLevel.CUSTOM) {
                    OutlinedTextField(
                        value = customLevel,
                        onValueChange = { customLevel = it },
                        label = { Text("Custom Level Label", fontSize = 11.5.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onSave(title.trim(), language.trim().ifBlank { "English" }, translation.trim(), levelCode, customLevel.trim())
                    }
                },
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White)
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = colors.inkMuted) }
        }
    )
}
