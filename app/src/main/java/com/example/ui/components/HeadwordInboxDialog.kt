package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.HeadwordQueueItem
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

    var showPasteImportDialog by remember { mutableStateOf(false) }
    var showConfirmClearDialog by remember { mutableStateOf(false) }

    val distinctLanguages = remember(inboxItems) {
        listOf("All") + inboxItems.map { it.language.trim() }.distinct().sorted()
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
                            contentDescription = null,
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
                            text = "Stage-1 curated words awaiting full enrichment",
                            fontSize = 12.sp,
                            color = colors.inkMuted
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.inkMuted)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Bar: Paste Headwords JSON | Enrich Batch (Stage 2 Prompt) | Clear All
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { showPasteImportDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primaryButtonBg,
                            contentColor = colors.primaryButtonText
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp).testTag("inbox_import_json_btn")
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import JSON", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    if (inboxItems.isNotEmpty()) {
                        OutlinedButton(
                            onClick = {
                                val targetLang = if (selectedLang.equals("All", ignoreCase = true)) {
                                    inboxItems.firstOrNull()?.language ?: "English"
                                } else {
                                    selectedLang
                                }
                                val prompt = viewModel.generateEnrichmentPromptForBatch(targetLang, maxCount = 15)
                                clipboardManager.setText(AnnotatedString(prompt))
                                Toast.makeText(
                                    context,
                                    "Stage-2 prompt for $targetLang copied to clipboard! Paste in AI.",
                                    Toast.LENGTH_LONG
                                ).show()
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, colors.teal),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.teal),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(36.dp).testTag("inbox_enrich_batch_btn")
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Enrich Batch (Copy Prompt)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = { showConfirmClearDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, colors.border),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.inkMuted),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search & Language Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search word, domain, or reason...", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(16.dp)) },
                        trailingIcon = if (searchQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(16.dp))
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
                            .weight(1f)
                            .height(44.dp)
                    )
                }

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
                                modifier = Modifier.clickable { selectedLang = lang }
                            ) {
                                Text(
                                    text = lang,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSel) colors.primaryButtonText else colors.ink,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                )
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
                            .height(240.dp)
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
                                text = if (inboxItems.isEmpty()) "Headword Inbox is Empty" else "No matching words found",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (inboxItems.isEmpty()) {
                                    "Tap 'Import JSON' above or build a Headwords Index prompt in AI Studio to curate meaningful competitive exam words."
                                } else {
                                    "Try clearing your search query or switching language filter."
                                },
                                fontSize = 12.sp,
                                color = colors.inkMuted,
                                lineHeight = 16.sp,
                                modifier = Modifier.fillMaxWidth(0.85f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp),
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

                Spacer(modifier = Modifier.height(14.dp))

                // Footer stats
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ignored words: ${dismissedKeys.size} (auto-skipped)",
                        fontSize = 11.sp,
                        color = colors.inkMuted
                    )
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.surfaceVariant,
                            contentColor = colors.inkBold
                        )
                    ) {
                        Text("Done", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    // Paste JSON Import Dialog
    if (showPasteImportDialog) {
        HeadwordJsonPasteDialog(
            defaultLanguage = if (selectedLang.equals("All", ignoreCase = true)) "English" else selectedLang,
            onImport = { json, lang ->
                viewModel.importHeadwordsJson(json, lang)
                showPasteImportDialog = false
            },
            onDismiss = { showPasteImportDialog = false }
        )
    }

    // Confirm Clear All Dialog
    if (showConfirmClearDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmClearDialog = false },
            title = { Text("Clear Headword Inbox?", fontWeight = FontWeight.Bold) },
            text = { Text("This will remove all ${inboxItems.size} pending headwords from the queue. (Saved decks and permanent ignore lists will not be affected).") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearHeadwordInbox()
                        showConfirmClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.red)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showConfirmClearDialog = false }) {
                    Text("Cancel")
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
        modifier = Modifier.fillMaxWidth().testTag("headword_card_${item.word.lowercase()}")
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

                // Level pill
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

                Spacer(modifier = Modifier.width(6.dp))

                // Domain pill
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

            if (item.utilityReason.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.utilityReason,
                    fontSize = 12.sp,
                    color = colors.ink,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Card quick actions: Dismiss (Never Ask Again) | Remove from Inbox
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
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                    modifier = Modifier.height(28.dp).testTag("dismiss_headword_${item.word.lowercase()}")
                ) {
                    Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Skip Forever", fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = onRemoveFromQueue,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove from inbox",
                        tint = colors.inkMuted,
                        modifier = Modifier.size(15.dp)
                    )
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
    var language by remember { mutableStateOf(defaultLanguage) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier.fillMaxWidth(0.96f).padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                Text(
                    text = "Import Headwords JSON",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Paste the AI-generated JSON array of headwords below. Words already saved in your library or previously skipped will be automatically filtered out.",
                    fontSize = 12.sp,
                    color = colors.inkMuted,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = language,
                    onValueChange = { language = it },
                    label = { Text("Target Language", fontSize = 11.5.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.teal,
                        unfocusedBorderColor = colors.border
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
                            "[\n  {\n    \"word\": \"Ephemeral\",\n    \"level\": \"C1\",\n    \"pos\": \"adjective\",\n    \"domain\": \"Editorial\",\n    \"utilityReason\": \"High frequency in editorial discussions on fleeting trends\"\n  }\n]",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = colors.inkMuted
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.teal,
                        unfocusedBorderColor = colors.border
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (jsonText.isNotBlank()) {
                                onImport(jsonText, language)
                            }
                        },
                        enabled = jsonText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primaryButtonBg)
                    ) {
                        Text("Import & Deduplicate")
                    }
                }
            }
        }
    }
}
