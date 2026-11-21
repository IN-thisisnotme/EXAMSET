package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.HeadwordQueueItem
import com.example.model.VocabularyLevel
import com.example.ui.MainViewModel
import com.example.ui.theme.ExamTheme

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

    var showQuickCaptureDialog by remember { mutableStateOf(false) }
    var showPasteImportDialog by remember { mutableStateOf(false) }
    var showPasteEnrichedDialog by remember { mutableStateOf(false) }
    var showConfirmClearDialog by remember { mutableStateOf(false) }

    val distinctLanguages = remember(inboxItems) {
        listOf("All") + inboxItems.map { it.language.trim() }.filter { it.isNotBlank() }.distinct().sorted()
    }

    val filteredItems = remember(inboxItems, selectedLang, searchQuery) {
        inboxItems.filter { item ->
            val matchLang = selectedLang.equals("All", ignoreCase = true) ||
                item.language.equals(selectedLang, ignoreCase = true)
            val matchSearch = searchQuery.isBlank() ||
                item.word.contains(searchQuery, ignoreCase = true) ||
                item.utilityReason.contains(searchQuery, ignoreCase = true) ||
                item.domain.contains(searchQuery, ignoreCase = true)
            matchLang && matchSearch
        }
    }

    val targetLanguageForActions = remember(selectedLang, inboxItems) {
        if (!selectedLang.equals("All", ignoreCase = true)) {
            selectedLang
        } else {
            inboxItems.firstOrNull()?.language?.trim()?.ifBlank { "English" } ?: "English"
        }
    }

    val batchCountForSelectedLang = remember(inboxItems, targetLanguageForActions) {
        inboxItems.count { it.language.equals(targetLanguageForActions, ignoreCase = true) }.coerceAtMost(15)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 20.dp)
                .testTag("headword_inbox_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            border = BorderStroke(1.dp, colors.border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(colors.tealLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inbox,
                            contentDescription = "Headword Inbox",
                            tint = colors.teal,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Headword Inbox",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = colors.teal.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "${inboxItems.size}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.teal,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Queued words stored silently until Stage-2 enrichment",
                            fontSize = 12.sp,
                            color = colors.inkMuted
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close Headword Inbox", tint = colors.inkMuted)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action Bar: Quick Capture | Import JSON | Enrich Batch | Paste Enriched | Clear
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
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
                        Text("Quick Capture", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
                        Text("Import JSON", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    if (inboxItems.isNotEmpty() && batchCountForSelectedLang > 0) {
                        OutlinedButton(
                            onClick = {
                                val prompt = viewModel.generateEnrichmentPromptForBatch(
                                    language = targetLanguageForActions,
                                    maxCount = 15
                                )
                                clipboardManager.setText(AnnotatedString(prompt))
                                Toast.makeText(
                                    context,
                                    "Stage-2 enrichment prompt ($batchCountForSelectedLang $targetLanguageForActions words) copied!",
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
                                text = "Enrich Batch ($batchCountForSelectedLang)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
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
                        Text("Paste Enriched", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
                            Text("Clear", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search queued words, domain, or reason...", fontSize = 12.5.sp) },
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

                if (distinctLanguages.size > 2) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        distinctLanguages.forEach { lang ->
                            val isSel = selectedLang.equals(lang, ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) colors.teal else colors.surfaceVariant,
                                modifier = Modifier
                                    .heightIn(min = 36.dp)
                                    .clickable { selectedLang = lang }
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                    Text(
                                        text = lang,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSel) colors.primaryButtonText else colors.ink
                                    )
                                }
                            }
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
                            .height(220.dp)
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
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (inboxItems.isEmpty()) "Headword Inbox is Empty" else "No matching queued words",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (inboxItems.isEmpty()) {
                                    "Use Quick Capture for bare words, or Import JSON from AI Prompt Builder's Headword Index mode. Queued words stay hidden from Word of the Day and SM-2 until enriched."
                                } else {
                                    "Try clearing your search query or switching language filter."
                                },
                                fontSize = 12.sp,
                                color = colors.inkMuted,
                                lineHeight = 16.sp,
                                modifier = Modifier.fillMaxWidth(0.9f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
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

                // Footer stats & Done
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Skipped forever: ${dismissedKeys.size} word(s)",
                        fontSize = 11.5.sp,
                        color = colors.inkMuted
                    )
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.surfaceVariant,
                            contentColor = colors.inkBold
                        ),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Done", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
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
            onImport = { json, lang ->
                val result = viewModel.importHeadwordsJson(json, lang)
                val skipped = result.skippedAlreadyInDeckCount +
                    result.skippedAlreadyInQueueCount +
                    result.skippedDismissedCount
                val toastMsg = if (result.addedItems.isNotEmpty()) {
                    "Queued ${result.addedItems.size} headword(s)" +
                        if (skipped > 0) " ($skipped duplicate/skipped)" else ""
                } else {
                    "No new headwords imported" + if (skipped > 0) " ($skipped duplicate/skipped)" else ""
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
private fun HeadwordQueueItemCard(
    item: HeadwordQueueItem,
    onDismissWord: () -> Unit,
    onRemoveFromQueue: () -> Unit
) {
    val colors = ExamTheme.colors

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant.copy(alpha = 0.4f)),
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("headword_card_${item.word.lowercase()}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.word,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkBold
                )

                if (item.partOfSpeech.isNotBlank()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = item.partOfSpeech,
                        fontSize = 11.5.sp,
                        fontStyle = FontStyle.Italic,
                        color = colors.inkMuted
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = colors.surfaceVariant
                ) {
                    Text(
                        text = item.language,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.ink,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.width(5.dp))

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = colors.tealLight
                ) {
                    Text(
                        text = item.levelCode,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.teal,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                if (item.domain.isNotBlank()) {
                    Spacer(modifier = Modifier.width(5.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = colors.amberLight
                    ) {
                        Text(
                            text = item.domain.take(16),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.badgeWarningText,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (item.utilityReason.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.utilityReason,
                    fontSize = 12.sp,
                    color = colors.ink,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
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
                    Text("Skip Forever", fontSize = 11.5.sp)
                }

                Spacer(modifier = Modifier.width(4.dp))

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
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Capture words silently (comma or newline separated, optional hint in parentheses e.g. sanction (penalty)). Already saved or skipped words are automatically excluded.",
                    fontSize = 12.sp,
                    color = colors.inkMuted,
                    lineHeight = 16.sp
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
                                fontSize = 11.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                color = if (active) colors.primaryButtonText else colors.ink,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = language,
                    onValueChange = { language = it },
                    label = { Text("Target Language", fontSize = 11.5.sp) },
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

                Text(
                    text = "Default Target Level",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.inkMuted
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                                fontSize = 11.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                color = if (active) colors.primaryButtonText else colors.ink,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = rawInput,
                    onValueChange = { rawInput = it },
                    label = { Text("Headwords", fontSize = 11.5.sp) },
                    placeholder = {
                        Text(
                            "ephemeral, sanction (penalty)\nobfuscate\nlucid",
                            fontSize = 12.sp,
                            color = colors.inkMuted
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Cancel", color = colors.inkBold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
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

@Composable
private fun HeadwordJsonPasteDialog(
    defaultLanguage: String,
    onImport: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ExamTheme.colors
    var jsonText by remember { mutableStateOf("") }
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
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Paste the Stage-1 Headword Index JSON from AI Studio. Words already saved in your library, already queued, or marked Skip Forever are filtered out automatically.",
                    fontSize = 12.sp,
                    color = colors.inkMuted,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = language,
                    onValueChange = { language = it },
                    label = { Text("Fallback Target Language", fontSize = 11.5.sp) },
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
                    value = jsonText,
                    onValueChange = { jsonText = it },
                    placeholder = {
                        Text(
                            "[\n  {\n    \"word\": \"Ephemeral\",\n    \"level\": \"C1\",\n    \"pos\": \"adjective\",\n    \"domain\": \"Editorial\",\n    \"utilityReason\": \"High-frequency editorial nuance\"\n  }\n]",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = colors.inkMuted
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Cancel", color = colors.inkBold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (jsonText.isNotBlank()) {
                                onImport(jsonText, language)
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
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Paste the full enriched JSON or Structured Text generated from your Stage-2 Enrichment prompt. Cards are automatically routed to their (Language, Level) folders and graduated out of this Inbox.",
                    fontSize = 12.sp,
                    color = colors.inkMuted,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = language,
                    onValueChange = { language = it },
                    label = { Text("Fallback Target Language", fontSize = 11.5.sp) },
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
                            fontSize = 11.5.sp,
                            color = colors.inkMuted
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Cancel", color = colors.inkBold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
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
