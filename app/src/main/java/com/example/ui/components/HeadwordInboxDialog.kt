package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.HeadwordQueueItem
import com.example.model.VocabularyLevel
import com.example.ui.MainViewModel
import com.example.ui.theme.ExamTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HeadwordInboxDialog(
    viewModel: MainViewModel,
    initialLanguageFilter: String? = null,
    onDismiss: () -> Unit
) {
    val colors = ExamTheme.colors
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val inboxItems by viewModel.headwordInbox.collectAsState()
    val dismissedKeys by viewModel.dismissedWordKeys.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedLang by remember(initialLanguageFilter) {
        mutableStateOf(initialLanguageFilter?.trim()?.ifBlank { "All" } ?: "All")
    }
    var selectedLevelFilter by remember { mutableStateOf("All") }
    var selectedCategoryFilter by remember { mutableStateOf("All") }

    var showQuickCaptureDialog by remember { mutableStateOf(false) }
    var showPasteImportDialog by remember { mutableStateOf(false) }
    var showPasteEnrichedDialog by remember { mutableStateOf(false) }
    var showConfirmClearDialog by remember { mutableStateOf(false) }

    val distinctLanguages = remember(inboxItems) {
        listOf("All") + inboxItems.map { it.language.trim() }.filter { it.isNotBlank() }.distinct().sorted()
    }

    val languageMatchedItems = remember(inboxItems, selectedLang) {
        inboxItems.filter { item ->
            selectedLang.equals("All", ignoreCase = true) ||
                item.language.equals(selectedLang, ignoreCase = true)
        }
    }

    val availableLevelFilters = remember(languageMatchedItems) {
        val counts = languageMatchedItems
            .groupingBy { it.resolvedLevel().code }
            .eachCount()
        val ordered = VocabularyLevel.entries
            .filter { (counts[it.code] ?: 0) > 0 }
            .sortedBy { it.sortRank }
            .map { it.code to (counts[it.code] ?: 0) }
        listOf("All" to languageMatchedItems.size) + ordered
    }

    val distinctCategories = remember(languageMatchedItems) {
        val cats = languageMatchedItems
            .map { it.domain.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
        if (cats.size > 1) listOf("All") + cats else emptyList()
    }

    val filteredItems = remember(
        languageMatchedItems,
        selectedLevelFilter,
        selectedCategoryFilter,
        searchQuery
    ) {
        languageMatchedItems.filter { item ->
            val matchLevel = selectedLevelFilter.equals("All", ignoreCase = true) ||
                item.resolvedLevel().code.equals(selectedLevelFilter, ignoreCase = true)
            val matchCat = selectedCategoryFilter.equals("All", ignoreCase = true) ||
                item.domain.equals(selectedCategoryFilter, ignoreCase = true)
            val matchSearch = searchQuery.isBlank() ||
                item.word.contains(searchQuery, ignoreCase = true) ||
                item.utilityReason.contains(searchQuery, ignoreCase = true) ||
                item.domain.contains(searchQuery, ignoreCase = true)
            matchLevel && matchCat && matchSearch
        }
    }

    val targetLanguageForActions = remember(selectedLang, inboxItems) {
        if (!selectedLang.equals("All", ignoreCase = true)) {
            selectedLang
        } else {
            inboxItems.firstOrNull()?.language?.trim()?.ifBlank { "English" } ?: "English"
        }
    }

    val batchCountForActiveFilters = remember(
        inboxItems,
        targetLanguageForActions,
        selectedLevelFilter,
        selectedCategoryFilter
    ) {
        inboxItems.count { item ->
            val matchLang = item.language.equals(targetLanguageForActions, ignoreCase = true)
            val matchLevel = selectedLevelFilter.equals("All", ignoreCase = true) ||
                item.resolvedLevel().code.equals(selectedLevelFilter, ignoreCase = true)
            val matchCat = selectedCategoryFilter.equals("All", ignoreCase = true) ||
                item.domain.equals(selectedCategoryFilter, ignoreCase = true)
            matchLang && matchLevel && matchCat
        }.coerceAtMost(15)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 720.dp)
                .padding(vertical = 12.dp)
                .testTag("headword_inbox_dialog"),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            border = BorderStroke(1.dp, colors.border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(colors.tealLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inbox,
                            contentDescription = "Headword Inbox",
                            tint = colors.tealDark,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Headword Inbox",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = colors.tealLight,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            ) {
                                Text(
                                    text = "${inboxItems.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.tealDark,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Stored silently until Stage-2 enrichment",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.inkMuted
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close Headword Inbox",
                            tint = colors.inkMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Responsive Action Bar (FlowRow prevents horizontal clipping at any system font scale)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showQuickCaptureDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primaryButtonBg,
                            contentColor = colors.primaryButtonText
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("inbox_quick_capture_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            "Quick Capture",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }

                    OutlinedButton(
                        onClick = { showPasteImportDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, colors.teal),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.teal),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("inbox_import_json_btn")
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            "Import JSON",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }

                    if (inboxItems.isNotEmpty() && batchCountForActiveFilters > 0) {
                        val levelSummary = if (!selectedLevelFilter.equals("All", ignoreCase = true)) {
                            " ${VocabularyLevel.fromCode(selectedLevelFilter).compactBadgeForLanguage(targetLanguageForActions)}"
                        } else {
                            ""
                        }
                        OutlinedButton(
                            onClick = {
                                val prompt = viewModel.generateEnrichmentPromptForBatch(
                                    language = targetLanguageForActions,
                                    maxCount = 15,
                                    levelCodeFilter = selectedLevelFilter,
                                    categoryFilter = selectedCategoryFilter
                                )
                                clipboardManager.setText(AnnotatedString(prompt))
                                Toast.makeText(
                                    context,
                                    "Stage-2 prompt ($batchCountForActiveFilters$levelSummary $targetLanguageForActions words) copied!",
                                    Toast.LENGTH_LONG
                                ).show()
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, colors.amber),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.amber),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("inbox_enrich_batch_btn")
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Enrich Batch ($batchCountForActiveFilters)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { showPasteEnrichedDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, colors.green),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.green),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("inbox_paste_enriched_btn")
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            "Paste Enriched",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }

                    if (inboxItems.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { showConfirmClearDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, colors.border),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.inkMuted),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("inbox_clear_all_btn")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Clear",
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            "Search queued words or notes...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = colors.inkMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Clear search",
                                    tint = colors.inkMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else null,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.teal,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.inkBold,
                        unfocusedTextColor = colors.ink
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("inbox_search_input")
                )

                // Language filter chips
                if (distinctLanguages.size > 2) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        distinctLanguages.forEach { lang ->
                            val isSel = selectedLang.equals(lang, ignoreCase = true)
                            InboxFilterChip(
                                label = lang,
                                selected = isSel,
                                onClick = {
                                    selectedLang = lang
                                    selectedLevelFilter = "All"
                                    selectedCategoryFilter = "All"
                                }
                            )
                        }
                    }
                }

                // Level filter chips (language-calibrated: A1..C2 / JLPT / HSK etc.)
                if (availableLevelFilters.size > 2) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        availableLevelFilters.forEach { (code, count) ->
                            val isSel = selectedLevelFilter.equals(code, ignoreCase = true)
                            val badge = if (code == "All") {
                                "All Levels ($count)"
                            } else {
                                "${VocabularyLevel.fromCode(code).compactBadgeForLanguage(targetLanguageForActions)} ($count)"
                            }
                            InboxFilterChip(
                                label = badge,
                                selected = isSel,
                                onClick = { selectedLevelFilter = code },
                                testTag = "inbox_level_filter_${code.lowercase()}"
                            )
                        }
                    }
                }

                // Category filter chips when multiple categories exist
                if (distinctCategories.size > 2) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        distinctCategories.forEach { cat ->
                            val isSel = selectedCategoryFilter.equals(cat, ignoreCase = true)
                            InboxFilterChip(
                                label = cat,
                                selected = isSel,
                                onClick = { selectedCategoryFilter = cat }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = colors.border.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(8.dp))

                // Inbox items list or empty state
                if (filteredItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.surfaceVariant.copy(alpha = 0.35f))
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Inbox,
                                contentDescription = null,
                                tint = colors.inkMuted,
                                modifier = Modifier.size(38.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (inboxItems.isEmpty()) "Headword Inbox is Empty" else "No matching queued words",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (inboxItems.isEmpty()) {
                                    "Use Quick Capture for bare words, or Import JSON from AI Prompt Builder's Headword Index mode. Queued words stay hidden from Word of the Day and SM-2 until enriched."
                                } else {
                                    "Try clearing your search query or adjusting the level filter."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.inkMuted,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(filteredItems, key = { it.id }) { item ->
                            HeadwordQueueItemCard(
                                item = item,
                                onDismissWord = { viewModel.dismissHeadword(item.id) },
                                onRemoveFromQueue = { viewModel.removeHeadwordFromInbox(item.id) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Footer stats & Done (wraps cleanly at large font scales)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Skipped forever: ${dismissedKeys.size} word(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.inkMuted,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.surfaceVariant,
                            contentColor = colors.inkBold
                        ),
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .heightIn(min = 48.dp)
                    ) {
                        Text(
                            "Done",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }

    // 1. Quick Capture Bare Headwords Dialog
    if (showQuickCaptureDialog) {
        QuickHeadwordCaptureDialog(
            defaultLanguage = targetLanguageForActions,
            onAddWords = { rawText, lang, levelCode ->
                val result = viewModel.addQuickHeadwordsToInbox(rawText, lang, levelCode)
                val skipped = result.skippedAlreadyInDeckCount +
                    result.skippedAlreadyInQueueCount +
                    result.skippedDismissedCount
                val toastMsg = if (result.addedItems.isNotEmpty()) {
                    "Saved ${result.addedItems.size} word(s) to Inbox" +
                        if (skipped > 0) " ($skipped skipped)" else ""
                } else {
                    "No new words added" + if (skipped > 0) " ($skipped already saved/ignored)" else ""
                }
                Toast.makeText(context, toastMsg, Toast.LENGTH_LONG).show()
                showQuickCaptureDialog = false
            },
            onDismiss = { showQuickCaptureDialog = false }
        )
    }

    // 2. Stage-1 Headword Index JSON Import Dialog
    if (showPasteImportDialog) {
        HeadwordJsonPasteDialog(
            defaultLanguage = targetLanguageForActions,
            initialStrictLevel = if (selectedLevelFilter.equals("All", ignoreCase = true)) "All" else selectedLevelFilter,
            onImport = { json, lang, strictLevel ->
                val result = viewModel.importHeadwordsJson(
                    rawJson = json,
                    defaultLanguage = lang,
                    requiredLevelCode = strictLevel.takeUnless { it.equals("All", ignoreCase = true) }
                )
                val skipped = result.skippedAlreadyInDeckCount +
                    result.skippedAlreadyInQueueCount +
                    result.skippedDismissedCount +
                    result.skippedOffLevelCount
                val toastMsg = if (result.addedItems.isNotEmpty()) {
                    "Queued ${result.addedItems.size} headword(s)" +
                        if (skipped > 0) " ($skipped duplicate/off-level/skipped)" else ""
                } else {
                    "No new headwords imported" + if (skipped > 0) " ($skipped duplicate/off-level/skipped)" else ""
                }
                Toast.makeText(context, toastMsg, Toast.LENGTH_LONG).show()
                if (result.totalProcessedCount > 0) {
                    showPasteImportDialog = false
                }
            },
            onDismiss = { showPasteImportDialog = false }
        )
    }

    // 3. Stage-2 Enriched Cards Paste & Graduate Dialog
    if (showPasteEnrichedDialog) {
        Stage2EnrichedPasteDialog(
            defaultLanguage = targetLanguageForActions,
            onImportEnriched = { rawText, lang ->
                val graduated = viewModel.importEnrichedStage2CardsFromInbox(rawText, lang)
                if (graduated > 0) {
                    Toast.makeText(
                        context,
                        "Enriched & saved $graduated card(s) to Vocabulary Hub!",
                        Toast.LENGTH_LONG
                    ).show()
                    showPasteEnrichedDialog = false
                } else {
                    Toast.makeText(
                        context,
                        "No complete cards found. Ensure meanings are included in the AI output.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            },
            onDismiss = { showPasteEnrichedDialog = false }
        )
    }

    // Confirm Clear All Dialog
    if (showConfirmClearDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmClearDialog = false },
            containerColor = colors.surface,
            titleContentColor = colors.inkBold,
            textContentColor = colors.ink,
            title = { Text("Clear Headword Inbox?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This will remove all ${inboxItems.size} pending headword(s) from the queue. Saved vocabulary decks and permanent ignore lists will not be affected."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearHeadwordInbox()
                        showConfirmClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.red,
                        contentColor = colors.primaryButtonText
                    ),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Clear All", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showConfirmClearDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel", color = colors.inkBold)
                }
            }
        )
    }
}

@Composable
private fun InboxFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String? = null
) {
    val colors = ExamTheme.colors
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) colors.teal else colors.surfaceVariant,
        border = BorderStroke(0.7.dp, if (selected) colors.teal else colors.border),
        modifier = Modifier
            .heightIn(min = 36.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) colors.primaryButtonText else colors.ink,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeadwordQueueItemCard(
    item: HeadwordQueueItem,
    onDismissWord: () -> Unit,
    onRemoveFromQueue: () -> Unit
) {
    val colors = ExamTheme.colors

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant.copy(alpha = 0.35f)),
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("headword_card_${item.word.lowercase()}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Row 1: Headword + POS separated cleanly from compact single-line pills
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.align(Alignment.CenterVertically)
                ) {
                    Text(
                        text = item.word,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkBold,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                    if (item.partOfSpeech.isNotBlank()) {
                        Text(
                            text = item.partOfSpeech,
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            color = colors.inkMuted,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.align(Alignment.CenterVertically)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = colors.surfaceVariant
                    ) {
                        Text(
                            text = item.language,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.ink,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = colors.tealLight
                    ) {
                        Text(
                            text = item.effectiveLevelBadge(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.tealDark,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            if (item.utilityReason.isNotBlank()) {
                Text(
                    text = item.utilityReason,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.ink
                )
            }

            // Footer row: subtle category hint (only if helpful) + Skip Forever / Remove actions
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val cleanDomain = item.domain.trim()
                if (cleanDomain.isNotBlank() &&
                    !cleanDomain.equals("General", ignoreCase = true) &&
                    !cleanDomain.equals("Quick Capture", ignoreCase = true)
                ) {
                    Text(
                        text = cleanDomain,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.inkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.align(Alignment.CenterVertically)
                ) {
                    OutlinedButton(
                        onClick = onDismissWord,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(0.8.dp, colors.border),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.inkMuted),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("dismiss_headword_${item.word.lowercase()}")
                    ) {
                        Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Skip Forever",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1
                        )
                    }

                    IconButton(
                        onClick = onRemoveFromQueue,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove ${item.word} from inbox",
                            tint = colors.inkMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickHeadwordCaptureDialog(
    defaultLanguage: String,
    onAddWords: (rawInput: String, language: String, levelCode: String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ExamTheme.colors
    var rawInput by remember { mutableStateOf("") }
    var language by remember { mutableStateOf(defaultLanguage.ifBlank { "English" }) }
    var selectedLevel by remember { mutableStateOf("B2") }

    val quickLevels = listOf("A1", "A2", "B1", "B2", "C1", "C2")
    val commonLanguages = listOf("English", "Hindi", "Japanese", "Spanish", "French", "German", "Sanskrit")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(16.dp)
                .testTag("quick_capture_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp)
            ) {
                Text(
                    text = "Quick Capture Bare Headwords",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Capture words silently (comma or newline separated, optional hint in parentheses e.g. sanction (penalty)). Already saved or skipped words are automatically excluded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkMuted
                )

                Spacer(modifier = Modifier.height(12.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    commonLanguages.forEach { presetLang ->
                        val active = language.equals(presetLang, ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (active) colors.teal else colors.surfaceVariant,
                            modifier = Modifier.clickable { language = presetLang }
                        ) {
                            Text(
                                text = presetLang,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                color = if (active) colors.primaryButtonText else colors.ink,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = language,
                    onValueChange = { language = it },
                    label = { Text("Target Language", style = MaterialTheme.typography.labelMedium) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.teal,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.inkBold,
                        unfocusedTextColor = colors.ink
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Default Target Level",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.inkMuted
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickLevels.forEach { lvl ->
                        val active = selectedLevel == lvl
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (active) colors.teal else colors.surfaceVariant,
                            modifier = Modifier.clickable { selectedLevel = lvl }
                        ) {
                            Text(
                                text = VocabularyLevel.fromCode(lvl).compactBadgeForLanguage(language),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                color = if (active) colors.primaryButtonText else colors.ink,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = rawInput,
                    onValueChange = { rawInput = it },
                    label = { Text("Headwords", style = MaterialTheme.typography.labelMedium) },
                    placeholder = {
                        Text(
                            "ephemeral, sanction (penalty)\nobfuscate\nlucid",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.inkMuted
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 130.dp)
                        .testTag("quick_capture_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.teal,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.inkBold,
                        unfocusedTextColor = colors.ink
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Cancel", color = colors.inkBold)
                    }
                    Button(
                        onClick = {
                            if (rawInput.isNotBlank()) {
                                onAddWords(rawInput, language, selectedLevel)
                            }
                        },
                        enabled = rawInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primaryButtonBg,
                            contentColor = colors.primaryButtonText
                        ),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("quick_capture_submit_btn")
                    ) {
                        Text("Store Silently")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeadwordJsonPasteDialog(
    defaultLanguage: String,
    initialStrictLevel: String = "All",
    onImport: (json: String, language: String, strictLevel: String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ExamTheme.colors
    var jsonText by remember { mutableStateOf("") }
    var language by remember { mutableStateOf(defaultLanguage.ifBlank { "English" }) }
    var strictLevel by remember(initialStrictLevel) { mutableStateOf(initialStrictLevel) }
    val levelOptions = listOf("All", "A1", "A2", "B1", "B2", "C1", "C2")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(16.dp)
                .testTag("headword_json_paste_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp)
            ) {
                Text(
                    text = "Import Stage-1 Headwords JSON",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Paste the Stage-1 Headword Index JSON from AI Studio. Words already saved in your library, already queued, or marked Skip Forever are filtered out automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkMuted
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = language,
                    onValueChange = { language = it },
                    label = { Text("Fallback Target Language", style = MaterialTheme.typography.labelMedium) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.teal,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.inkBold,
                        unfocusedTextColor = colors.ink
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Strict Level Filter on Import",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.inkMuted
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    levelOptions.forEach { lvl ->
                        val active = strictLevel.equals(lvl, ignoreCase = true)
                        val label = if (lvl == "All") {
                            "Keep All Levels"
                        } else {
                            VocabularyLevel.fromCode(lvl).compactBadgeForLanguage(language)
                        }
                        InboxFilterChip(
                            label = label,
                            selected = active,
                            onClick = { strictLevel = lvl }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = jsonText,
                    onValueChange = { jsonText = it },
                    placeholder = {
                        Text(
                            "[\n  {\n    \"word\": \"Ephemeral\",\n    \"level\": \"C1\",\n    \"pos\": \"adjective\",\n    \"utilityReason\": \"High-frequency editorial nuance\"\n  }\n]",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = colors.inkMuted
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp)
                        .testTag("headword_json_paste_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.teal,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.inkBold,
                        unfocusedTextColor = colors.ink
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Cancel", color = colors.inkBold)
                    }
                    Button(
                        onClick = {
                            if (jsonText.isNotBlank()) {
                                onImport(jsonText, language, strictLevel)
                            }
                        },
                        enabled = jsonText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primaryButtonBg,
                            contentColor = colors.primaryButtonText
                        ),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("headword_json_paste_submit_btn")
                    ) {
                        Text("Import & Deduplicate")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Stage2EnrichedPasteDialog(
    defaultLanguage: String,
    onImportEnriched: (rawInput: String, fallbackLanguage: String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ExamTheme.colors
    var rawText by remember { mutableStateOf("") }
    var language by remember { mutableStateOf(defaultLanguage.ifBlank { "English" }) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(16.dp)
                .testTag("stage2_enriched_paste_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp)
            ) {
                Text(
                    text = "Paste Enriched Stage-2 Cards",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Paste the full enriched JSON or Structured Text generated from your Stage-2 Enrichment prompt. Cards are automatically routed to their (Language, Level) folders and graduated out of this Inbox.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkMuted
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = language,
                    onValueChange = { language = it },
                    label = { Text("Fallback Target Language", style = MaterialTheme.typography.labelMedium) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.teal,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.inkBold,
                        unfocusedTextColor = colors.ink
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = rawText,
                    onValueChange = { rawText = it },
                    placeholder = {
                        Text(
                            "Paste full Stage-2 enriched ```json array``` or WORD:/MEANING:/EXAMPLES: cards here...",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.inkMuted
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 170.dp)
                        .testTag("stage2_enriched_paste_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.teal,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.inkBold,
                        unfocusedTextColor = colors.ink
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Cancel", color = colors.inkBold)
                    }
                    Button(
                        onClick = {
                            if (rawText.isNotBlank()) {
                                onImportEnriched(rawText, language)
                            }
                        },
                        enabled = rawText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.green,
                            contentColor = colors.primaryButtonText
                        ),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("stage2_enriched_paste_submit_btn")
                    ) {
                        Text("Save & Graduate Words")
                    }
                }
            }
        }
    }
}
