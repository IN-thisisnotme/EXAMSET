package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons 
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import com.example.util.McqParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.example.ui.components.AttachmentEditorSection
import com.example.ui.components.QuestionAttachmentView
import com.example.ui.components.EditMetadataDialog
import com.example.ui.components.ExamPaletteSideSheet
import com.example.ui.components.ReviewChangesDialog
import com.example.ui.components.computeQuestionDiffs
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AutoSaveStatus
import com.example.model.McqQuestion
import com.example.model.QuestionOptions
import com.example.model.QuestionPaper
import com.example.ui.MainViewModel
import com.example.ui.components.AnswerKeyTable
import com.example.ui.components.ExplanationCard
import com.example.ui.components.HeaderBanner
import com.example.ui.components.StructuredQuestionContent
import com.example.ui.theme.ExamTheme
import com.example.util.QuestionFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MasterViewTab(
    viewModel: MainViewModel,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    val coroutineScope = rememberCoroutineScope()
    val currentPaper by viewModel.currentPaper.collectAsState()
    val lastOpenedPaper by viewModel.lastOpenedPaper.collectAsState()
    val autoSaveStatus by viewModel.autoSaveStatus.collectAsState()
    val lastSavedTime by viewModel.lastSavedTime.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val examConfig by viewModel.examConfig.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf("all") } // "all", "missing_key", "missing_exp"
    var showEditMetadataDialog by remember { mutableStateOf(false) }
    var isEditMode by remember { mutableStateOf(false) }
    var originalQuestionsSnapshot by remember { mutableStateOf<List<McqQuestion>?>(null) }
    var showReviewDialog by remember { mutableStateOf(false) }
    var showDiscardConfirmDialog by remember { mutableStateOf(false) }
    var showAddQuestionDialog by remember { mutableStateOf(false) }
    var showPaletteOverlay by remember { mutableStateOf(false) }

    if (currentPaper == null || currentPaper!!.questions.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(0.8.dp, colors.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.ListAlt,
                        contentDescription = null,
                        tint = colors.inkMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No Questions in Master View",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Select a saved question set in Folders & Library or import exam files in Exam Tools to begin editing.",
                        fontSize = 12.sp,
                        color = colors.inkMuted
                    )
                    if (lastOpenedPaper != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { viewModel.loadSavedPaper(lastOpenedPaper!!) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.teal
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("master_open_last_paper_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Open Last Set: ${lastOpenedPaper!!.title}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.selectTab(0) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.primaryButtonBg,
                                contentColor = colors.primaryButtonText
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .testTag("master_browse_library_button")
                        ) {
                            Icon(Icons.Default.ListAlt, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.primaryButtonText)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Browse Question Library", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.primaryButtonText)
                        }
                        OutlinedButton(
                            onClick = { viewModel.selectTab(4) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.inkBold),
                            border = BorderStroke(1.dp, colors.border),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .testTag("master_create_set_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.inkBold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Create New Question Set", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.inkBold)
                        }
                    }
                }
            }
        }
        return
    }

    val paper = currentPaper!!
    val questions = paper.questions

    val enterEditMode: () -> Unit = {
        originalQuestionsSnapshot = paper.questions.map { it.copy(options = it.options.copy()) }
        isEditMode = true
    }

    val exitEditMode: (Boolean) -> Unit = { discard ->
        if (discard && originalQuestionsSnapshot != null) {
            viewModel.restorePaperQuestions(originalQuestionsSnapshot!!)
        }
        isEditMode = false
        originalQuestionsSnapshot = null
        showReviewDialog = false
        showDiscardConfirmDialog = false
    }

    val currentDiffs = remember(paper.questions, originalQuestionsSnapshot) {
        val snapshot = originalQuestionsSnapshot
        if (snapshot == null) emptyList() else computeQuestionDiffs(snapshot, paper.questions)
    }
    val changedCount = currentDiffs.size

    val filteredQuestions = questions.filter { q ->
        val matchesSearch = searchQuery.isBlank() ||
                q.question.contains(searchQuery, ignoreCase = true) ||
                q.explanation?.contains(searchQuery, ignoreCase = true) == true ||
                q.options.a.contains(searchQuery, ignoreCase = true) ||
                q.options.b.contains(searchQuery, ignoreCase = true) ||
                q.options.c.contains(searchQuery, ignoreCase = true) ||
                q.options.d.contains(searchQuery, ignoreCase = true)

        val matchesFilter = when (filterMode) {
            "missing_key" -> q.correctAnswer == null
            "missing_exp" -> q.explanation.isNullOrBlank()
            "missing_option" -> q.options.hasMissingOptions
            else -> true
        }
        matchesSearch && matchesFilter
    }

    val isScrolledPastHeader by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(
                top = 4.dp,
                bottom = 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // Top Signature Banner (Edit Details only; Edit Set lives in the Action Toolbar)
            item {
                HeaderBanner(
                    metadata = paper.metadata,
                    totalQuestions = paper.questions.size,
                    onEditClick = { showEditMetadataDialog = true },
                    onEditQuestionsClick = null,
                    isEditingQuestions = isEditMode
                )
            }

            // Action Toolbar & Stats Row
            item {
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    border = BorderStroke(0.8.dp, colors.border)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Title & Status (stable height to prevent interface jump/bounce)
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Master Inspection",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.inkBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (isEditMode) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = colors.teal.copy(alpha = 0.15f),
                                            border = BorderStroke(0.6.dp, colors.teal)
                                        ) {
                                            Text(
                                                text = if (changedCount > 0) "EDITING ($changedCount)" else "EDITING",
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.teal,
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = if (isEditMode) {
                                        if (changedCount > 0) "$changedCount question${if (changedCount == 1) "" else "s"} modified" else "Tap options or cards to edit"
                                    } else if (autoSaveStatus == AutoSaveStatus.SAVED && lastSavedTime.isNotBlank()) {
                                        "• Saved $lastSavedTime"
                                    } else {
                                        "${paper.questions.size} questions • ${paper.quickAnswersCount} answer keys"
                                    },
                                    fontSize = 10.5.sp,
                                    color = if (isEditMode) colors.teal else colors.muted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Toolbar Actions: Clean Edit Mode toggle & Add Question
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!isEditMode) {
                                    Surface(
                                        onClick = enterEditMode,
                                        shape = RoundedCornerShape(6.dp),
                                        color = colors.surfaceVariant,
                                        border = BorderStroke(1.dp, colors.border),
                                        modifier = Modifier
                                            .heightIn(min = 38.dp)
                                            .testTag("toolbar_edit_set_button")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = null,
                                                tint = colors.teal,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                text = "Edit Set",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = colors.inkBold
                                            )
                                        }
                                    }
                                } else {
                                    Surface(
                                        onClick = {
                                            if (changedCount > 0) showReviewDialog = true else exitEditMode(false)
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        color = colors.teal.copy(alpha = 0.15f),
                                        border = BorderStroke(1.dp, colors.teal.copy(alpha = 0.6f)),
                                        modifier = Modifier
                                            .heightIn(min = 38.dp)
                                            .testTag("toolbar_done_button")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = colors.teal,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                text = if (changedCount > 0) "Review ($changedCount)" else "Done",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.teal
                                            )
                                        }
                                    }
                                }

                                // Add Question Action
                                Button(
                                    onClick = {
                                        if (!isEditMode) enterEditMode()
                                        showAddQuestionDialog = true
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.primaryButtonBg,
                                        contentColor = colors.primaryButtonText
                                    ),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp),
                                    modifier = Modifier.heightIn(min = 38.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp), tint = colors.primaryButtonText)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.primaryButtonText)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                    // Search input with comfortable height & clear text contrast
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = "Search questions...",
                                fontSize = 12.5.sp,
                                maxLines = 1,
                                softWrap = false,
                                color = colors.muted
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                modifier = Modifier.size(16.dp),
                                tint = colors.muted
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear search",
                                        modifier = Modifier.size(15.dp),
                                        tint = colors.muted
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = colors.surfaceVariant.copy(alpha = 0.35f),
                            unfocusedContainerColor = colors.surfaceVariant.copy(alpha = 0.2f),
                            focusedTextColor = colors.inkBold,
                            unfocusedTextColor = colors.ink,
                            focusedBorderColor = colors.teal,
                            unfocusedBorderColor = colors.border,
                            cursorColor = colors.teal
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Filter mode chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val missingKeyCount = questions.count { it.correctAnswer == null }
                        val missingExpCount = questions.count { it.explanation.isNullOrBlank() }
                        val missingOptionCount = questions.count { it.options.hasMissingOptions }

                        FilterChip(
                            label = "All (${questions.size})",
                            isSelected = filterMode == "all",
                            onClick = { filterMode = "all" }
                        )

                        FilterChip(
                            label = "Missing Key ($missingKeyCount)",
                            isSelected = filterMode == "missing_key",
                            onClick = { filterMode = "missing_key" },
                            accentColor = if (missingKeyCount > 0) Color(0xFFD97706) else null
                        )

                        FilterChip(
                            label = "Missing Exp ($missingExpCount)",
                            isSelected = filterMode == "missing_exp",
                            onClick = { filterMode = "missing_exp" },
                            accentColor = if (missingExpCount > 0) Color(0xFF2563EB) else null
                        )

                        FilterChip(
                            label = "Missing Option ($missingOptionCount)",
                            isSelected = filterMode == "missing_option",
                            onClick = { filterMode = "missing_option" },
                            accentColor = if (missingOptionCount > 0) colors.amber else null
                        )
                    }
                }
            }
        }

        // Inline Quick Question Palette Row
        item {
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(0.8.dp, colors.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Apps,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = colors.teal
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Question Palette (${filteredQuestions.size})",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold
                            )
                        }

                        TextButton(
                            onClick = { showPaletteOverlay = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.heightIn(min = 28.dp).testTag("open_full_palette_button")
                        ) {
                            Text(
                                text = "Full Grid",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.teal
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(filteredQuestions) { qIdx, q ->
                            val hasKey = q.correctAnswer != null
                            val isChanged = currentDiffs.any { it.questionNum == q.num }

                            Surface(
                                onClick = {
                                    coroutineScope.launch {
                                        // 0: spacer, 1: banner, 2: toolbar, 3: inline palette. Questions start at item 4.
                                        listState.animateScrollToItem(qIdx + 4)
                                    }
                                },
                                shape = RoundedCornerShape(6.dp),
                                color = when {
                                    isChanged -> colors.amberLight
                                    hasKey -> colors.surfaceVariant
                                    else -> Color(0xFFFEF3C7).copy(alpha = if (colors.isDark) 0.2f else 1f)
                                },
                                border = BorderStroke(
                                    1.dp,
                                    when {
                                        isChanged -> colors.amber
                                        hasKey -> colors.teal.copy(alpha = 0.5f)
                                        else -> Color(0xFFD97706).copy(alpha = 0.5f)
                                    }
                                ),
                                modifier = Modifier.size(34.dp).testTag("inline_palette_q_${q.num}")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${q.num}",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when {
                                            isChanged -> colors.amber
                                            hasKey -> colors.tealDark
                                            else -> Color(0xFFB45309)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Questions List
        itemsIndexed(filteredQuestions, key = { _, q -> "master_q_${q.num}" }) { idx, q ->
            MasterQuestionCard(
                question = q,
                isEditProtected = !isEditMode,
                canMoveUp = idx > 0,
                canMoveDown = idx < filteredQuestions.size - 1,
                onSelectAnswer = { letter ->
                    viewModel.updateQuestion(q.copy(correctAnswer = letter))
                },
                onUpdateQuestion = { updated ->
                    viewModel.updateQuestion(updated)
                },
                onMoveUp = { viewModel.moveQuestion(idx, true) },
                onMoveDown = { viewModel.moveQuestion(idx, false) },
                onDuplicate = { viewModel.duplicateQuestion(q) },
                onDelete = { viewModel.deleteQuestion(q.num) }
            )
        }

        // Quick Add Question Button
        item {
            Card(
                onClick = {
                    if (!isEditMode) enterEditMode()
                    showAddQuestionDialog = true
                },
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(1.dp, Color(0xFF93C5FD)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "+ Add Question / Append Set",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2563EB)
                    )
                }
            }
        }

        // Answer Key Table Section
        item {
            Spacer(modifier = Modifier.height(10.dp))
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(0.8.dp, colors.border)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "OFFICIAL ANSWER KEY TABLE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkMuted,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    AnswerKeyTable(questions = questions)
                }
            }
        }

        // Detailed Explanations Section (Virtualized with lazy list items to prevent scroll lag)
        val explainedQuestions = questions.filter { !it.explanation.isNullOrBlank() }
        if (explainedQuestions.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    border = BorderStroke(0.8.dp, colors.border)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = colors.teal,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "DETAILED PEDAGOGICAL EXPLANATIONS",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold,
                                letterSpacing = 0.5.sp
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colors.teal.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "${explainedQuestions.size} Solutions",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.tealDark,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            items(explainedQuestions, key = { "master_exp_${it.num}" }) { q ->
                ExplanationCard(question = q)
            }
        }

        item { Spacer(modifier = Modifier.height(32.dp)) }
    }

    // Floating Edit Bar anchored right above navigation tabs when editing is active
    if (isEditMode) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .testTag("floating_edit_bar"),
            shape = RoundedCornerShape(16.dp),
            color = colors.surfaceElevated,
            border = BorderStroke(1.2.dp, if (changedCount > 0) colors.teal else colors.border),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Single-line status (will not wrap into multiple lines)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(
                                if (changedCount > 0) colors.teal.copy(alpha = 0.2f) else colors.surfaceVariant,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = if (changedCount > 0) colors.teal else colors.muted,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (changedCount == 0) "Editing Mode" else "$changedCount Modified",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (changedCount > 0) colors.teal else colors.inkBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (changedCount == 0) "Tap cards to edit" else "Tap review to save",
                            fontSize = 10.sp,
                            color = colors.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Right: Quick Palette jumper, Discard, Done/Review
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Question Palette Jumper inside edit mode
                    IconButton(
                        onClick = { showPaletteOverlay = true },
                        modifier = Modifier.size(34.dp).testTag("floating_edit_palette_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Apps,
                            contentDescription = "Question Palette",
                            tint = colors.teal,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    if (changedCount > 0) {
                        TextButton(
                            onClick = { showDiscardConfirmDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.heightIn(min = 34.dp).testTag("floating_discard_button")
                        ) {
                            Text("Discard", fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = colors.red)
                        }
                    }

                    Button(
                        onClick = {
                            if (changedCount == 0) {
                                exitEditMode(false)
                            } else {
                                showReviewDialog = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primaryButtonBg,
                            contentColor = colors.primaryButtonText
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .heightIn(min = 36.dp)
                            .testTag("floating_review_save_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (changedCount > 0) "Review ($changedCount)" else "Done",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }

    // Floating Navigation & Action Dock when not in edit mode:
    // Provides instant overlay access to Question Palette, Edit Set, and Scroll-to-Top anywhere in the list!
    if (!isEditMode) {
        AnimatedVisibility(
            visible = isScrolledPastHeader,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = colors.surfaceElevated,
                border = BorderStroke(1.dp, colors.border),
                shadowElevation = 8.dp,
                modifier = Modifier.testTag("floating_master_actions_bar")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Question Palette Jumper Button
                    Surface(
                        onClick = { showPaletteOverlay = true },
                        shape = RoundedCornerShape(18.dp),
                        color = colors.teal.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, colors.teal.copy(alpha = 0.4f)),
                        modifier = Modifier.heightIn(min = 36.dp).testTag("floating_open_palette_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Apps,
                                contentDescription = "Question Palette",
                                tint = colors.teal,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Palette (${filteredQuestions.size})",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.tealDark
                            )
                        }
                    }

                    // 2. Edit Set Action
                    Surface(
                        onClick = enterEditMode,
                        shape = RoundedCornerShape(18.dp),
                        color = colors.surfaceVariant,
                        border = BorderStroke(1.dp, colors.border),
                        modifier = Modifier.heightIn(min = 36.dp).testTag("floating_edit_set_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Set",
                                tint = colors.inkBold,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Edit Set",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.inkBold
                            )
                        }
                    }

                    // 3. Quick Scroll to Top Action
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                listState.animateScrollToItem(0)
                            }
                        },
                        modifier = Modifier.size(36.dp).testTag("floating_scroll_to_top_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Scroll to top",
                            tint = colors.muted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

    // Question Palette Slide-Over Side Sheet (UPSC / State PCS / JE & AE Navigator)
    ExamPaletteSideSheet(
        isOpen = showPaletteOverlay,
        onDismiss = { showPaletteOverlay = false },
        questions = filteredQuestions,
        paperTitle = paper.metadata.title,
        setNumber = paper.metadata.setNumber,
        isEditMode = isEditMode,
        modifiedQuestionNums = currentDiffs.map { it.questionNum }.toSet(),
        onQuestionClick = { qIdx, _ ->
            coroutineScope.launch {
                val target = qIdx + 4
                if (kotlin.math.abs(listState.firstVisibleItemIndex - target) > 8) {
                    listState.scrollToItem(target)
                } else {
                    listState.animateScrollToItem(target)
                }
            }
        },
        onScrollToTop = {
            coroutineScope.launch { listState.animateScrollToItem(0) }
        },
        onScrollToAnswerKey = {
            coroutineScope.launch {
                val keyTableIndex = 4 + filteredQuestions.size + 1
                listState.animateScrollToItem(keyTableIndex)
            }
        }
    )

    // Review Changes Diff Modal
    if (showReviewDialog) {
        ReviewChangesDialog(
            diffs = currentDiffs,
            onConfirmSave = {
                viewModel.saveAllPaperQuestions(paper.questions)
                isEditMode = false
                originalQuestionsSnapshot = null
                showReviewDialog = false
            },
            onDiscard = {
                exitEditMode(true)
            },
            onDismiss = {
                showReviewDialog = false
            }
        )
    }

    // Discard Confirmation Dialog
    if (showDiscardConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirmDialog = false },
            title = { Text("Discard Edits?", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                Text(
                    "Are you sure you want to discard all changes made during this editing session? Your modifications will be reverted to the original state.",
                    fontSize = 13.sp,
                    color = colors.ink
                )
            },
            confirmButton = {
                Button(
                    onClick = { exitEditMode(true) },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.red),
                    modifier = Modifier.testTag("confirm_discard_dialog_button")
                ) {
                    Text("Discard All Changes", color = Color.White, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDiscardConfirmDialog = false },
                    modifier = Modifier.testTag("cancel_discard_dialog_button")
                ) {
                    Text("Keep Editing", fontSize = 12.sp)
                }
            }
        )
    }

    // Add Question Dialog (Form & Paste Append with Duplicate Prevention)
    if (showAddQuestionDialog) {
        AddQuestionDialog(
            viewModel = viewModel,
            onDismiss = { showAddQuestionDialog = false }
        )
    }

    // Edit Metadata Dialog with direct access to Question Editing Mode, Duration & Marks
    if (showEditMetadataDialog) {
        EditMetadataDialog(
            initialMetadata = paper.metadata,
            folders = folders,
            initialConfig = examConfig,
            onSave = { title, subject, topic, setNumber, rollNumber, bookletNumber ->
                viewModel.updatePaperMetadata(title, subject, topic, setNumber, rollNumber, bookletNumber)
            },
            onSaveConfig = { newConfig ->
                viewModel.updateExamConfig(newConfig)
            },
            onDismiss = { showEditMetadataDialog = false },
            onEnterQuestionEditMode = enterEditMode
        )
    }
}

@Composable
private fun MasterQuestionCard(
    question: McqQuestion,
    isEditProtected: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onSelectAnswer: (String) -> Unit,
    onUpdateQuestion: (McqQuestion) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = ExamTheme.colors
    var isEditing by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // If edit protection is enabled, cancel inline editing immediately
    LaunchedEffect(isEditProtected) {
        if (isEditProtected) {
            isEditing = false
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF4444)) },
            title = { Text("Delete Question Q${question.num}?", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                Text(
                    "Are you sure you want to remove this question? Remaining questions will be automatically renumbered.",
                    fontSize = 12.5.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(0.8.dp, colors.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Q#, Category Badge, Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val qType = QuestionFormatter.displayType(question)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = colors.navyBanner
                    ) {
                        Text(
                            text = "Q${question.num}",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.navyBannerText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    // Question category badge (Conceptual, Numerical, etc.) commented out per user request:
                    /*
                    if (!qType.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = when {
                                qType.contains("Assertion") -> colors.tealLight
                                qType.contains("Match") -> Color(0xFFFEF3C7).copy(alpha = if (colors.isDark) 0.2f else 1f)
                                qType.contains("Statement") -> Color(0xFFE0E7FF).copy(alpha = if (colors.isDark) 0.2f else 1f)
                                else -> colors.surfaceVariant
                            },
                            border = BorderStroke(
                                0.6.dp,
                                when {
                                    qType.contains("Assertion") -> colors.teal.copy(alpha = 0.5f)
                                    qType.contains("Match") -> Color(0xFFD97706).copy(alpha = 0.5f)
                                    qType.contains("Statement") -> Color(0xFF6366F1).copy(alpha = 0.5f)
                                    else -> colors.border
                                }
                            )
                        ) {
                            Text(
                                text = qType,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = when {
                                    qType.contains("Assertion") -> colors.tealDark
                                    qType.contains("Match") -> if (colors.isDark) Color(0xFFFBBF24) else Color(0xFFB45309)
                                    qType.contains("Statement") -> if (colors.isDark) Color(0xFFA5B4FC) else Color(0xFF4338CA)
                                    else -> colors.ink
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    */

                    if (question.options.hasMissingOptions) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colors.amberLight,
                            border = BorderStroke(0.8.dp, colors.amber)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = colors.amber,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Missing Option (${question.options.missingOptions.joinToString(",")})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.amber
                                )
                            }
                        }
                    }
                }

                // Control Icons only shown when Editing is active
                if (!isEditProtected) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", modifier = Modifier.size(16.dp), tint = colors.ink)
                        }
                        IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", modifier = Modifier.size(16.dp), tint = colors.ink)
                        }
                        IconButton(onClick = onDuplicate, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate", modifier = Modifier.size(16.dp), tint = colors.ink)
                        }
                        IconButton(onClick = { isEditing = !isEditing }, modifier = Modifier.size(34.dp)) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit Question",
                                tint = if (isEditing) colors.teal else colors.muted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = colors.red, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Answer Key Display / Editor Row
            if (isEditProtected) {
                // Read-only Answer Row (Protected against accidental touch)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (question.correctAnswer != null) colors.green else colors.muted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Official Answer Key:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.inkBold
                        )
                    }

                    if (question.correctAnswer != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colors.greenLight,
                            border = BorderStroke(0.8.dp, colors.green.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "Option ${question.correctAnswer.uppercase()}",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.green,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.5.dp)
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colors.amberLight,
                            border = BorderStroke(0.8.dp, colors.amber.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "Not Set",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.amber,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.5.dp)
                            )
                        }
                    }
                }
            } else {
                // Interactive Answer Key Selector Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceVariant.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = colors.teal, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Set Correct Key:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.inkBold
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("A", "B", "C", "D").forEach { opt ->
                            val isSelected = question.correctAnswer.equals(opt, ignoreCase = true)
                            Surface(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clickable { onSelectAnswer(opt) },
                                shape = CircleShape,
                                color = if (isSelected) Color(0xFF059669) else colors.surface,
                                border = BorderStroke(
                                    1.2.dp,
                                    if (isSelected) Color(0xFF047857) else colors.border
                                )
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = opt,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else colors.inkBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (!isEditing) {
                // Read-only Structured Content View
                StructuredQuestionContent(
                    question = question,
                    showAnswerIndicator = true,
                    showNumber = false,
                    showTypeBadge = false
                )
            } else {
                // Interactive Editing Mode
                var editedStem by remember { mutableStateOf(question.question) }
                var editedA by remember { mutableStateOf(question.options.a) }
                var editedB by remember { mutableStateOf(question.options.b) }
                var editedC by remember { mutableStateOf(question.options.c) }
                var editedD by remember { mutableStateOf(question.options.d) }
                var editedExp by remember { mutableStateOf(question.explanation ?: "") }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editedStem,
                        onValueChange = {
                            editedStem = it
                            onUpdateQuestion(question.copy(question = it))
                        },
                        label = { Text("Question Stem / Statement", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = editedA,
                            onValueChange = {
                                editedA = it
                                onUpdateQuestion(question.copy(options = question.options.copy(a = it)))
                            },
                            label = { Text("(A)", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = editedB,
                            onValueChange = {
                                editedB = it
                                onUpdateQuestion(question.copy(options = question.options.copy(b = it)))
                            },
                            label = { Text("(B)", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = editedC,
                            onValueChange = {
                                editedC = it
                                onUpdateQuestion(question.copy(options = question.options.copy(c = it)))
                            },
                            label = { Text("(C)", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = editedD,
                            onValueChange = {
                                editedD = it
                                onUpdateQuestion(question.copy(options = question.options.copy(d = it)))
                            },
                            label = { Text("(D)", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = editedExp,
                        onValueChange = {
                            editedExp = it
                            onUpdateQuestion(question.copy(explanation = it))
                        },
                        label = { Text("Pedagogical Explanation", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Diagram, Chart, and Formula Attachment Editor
                    AttachmentEditorSection(
                        diagramUrl = question.diagramUrl,
                        diagramLabel = question.diagramLabel,
                        formula = question.formula,
                        onDiagramChanged = { url, label ->
                            onUpdateQuestion(question.copy(diagramUrl = url, diagramLabel = label))
                        },
                        onFormulaChanged = { f ->
                            onUpdateQuestion(question.copy(formula = f))
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = { isEditing = false },
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier.heightIn(min = 40.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Done Editing", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddQuestionDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colors = ExamTheme.colors
    val currentPaper by viewModel.currentPaper.collectAsState()
    val totalExistingQuestions = currentPaper?.questions?.size ?: 0

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Form Entry, 1: Paste & Append

    // Single Question Form State
    var questionText by remember { mutableStateOf("") }
    var optA by remember { mutableStateOf("") }
    var optB by remember { mutableStateOf("") }
    var optC by remember { mutableStateOf("") }
    var optD by remember { mutableStateOf("") }
    var selectedAnswer by remember { mutableStateOf("A") }
    var explanationText by remember { mutableStateOf("") }
    var questionType by remember { mutableStateOf("Direct Fact") }

    var customDiagramUrl by remember { mutableStateOf<String?>(null) }
    var customDiagramLabel by remember { mutableStateOf<String?>(null) }
    var customFormula by remember { mutableStateOf<String?>(null) }

    // Batch Paste Text
    var pastedBatchText by remember { mutableStateOf("") }

    // Live Duplicate Question Check
    val duplicateNum = remember(questionText) {
        if (questionText.trim().length > 5) {
            viewModel.findDuplicateQuestionNumber(questionText)
        } else null
    }

    // Debounced real-time parser telemetry for Paste & Append tab
    val existingNorms = remember(currentPaper) {
        currentPaper?.questions?.map { viewModel.normalizeQuestionText(it.question) }?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
    }

    var parsedBatchResult by remember { mutableStateOf<com.example.model.ParseResult?>(null) }
    var isParsingBatch by remember { mutableStateOf(false) }

    LaunchedEffect(pastedBatchText) {
        if (pastedBatchText.isBlank()) {
            parsedBatchResult = null
            isParsingBatch = false
        } else {
            isParsingBatch = true
            delay(280L)
            val res = withContext(Dispatchers.Default) {
                McqParser.parseWithResult(pastedBatchText)
            }
            parsedBatchResult = res
            isParsingBatch = false
        }
    }

    val duplicatesCount = remember(parsedBatchResult, existingNorms) {
        parsedBatchResult?.paper?.questions?.count { q ->
            val norm = viewModel.normalizeQuestionText(q.question)
            norm.isNotBlank() && existingNorms.contains(norm)
        } ?: 0
    }
    val parsedQuestionsCount = parsedBatchResult?.paper?.questions?.size ?: 0
    val newQuestionsCount = (parsedQuestionsCount - duplicatesCount).coerceAtLeast(0)
    val nextStartNumber = totalExistingQuestions + 1
    val nextEndNumber = totalExistingQuestions + newQuestionsCount

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = colors.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .navigationBarsPadding()
            ) {
                // Header Bar
                Surface(
                    color = colors.surfaceVariant,
                    tonalElevation = 2.dp,
                    border = BorderStroke(0.5.dp, colors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Add Questions to Paper",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold
                            )
                            Text(
                                text = "Current Set: ${currentPaper?.metadata?.title.takeIf { !it.isNullOrBlank() } ?: "Question Paper"} (Q1–Q$totalExistingQuestions)",
                                fontSize = 11.5.sp,
                                color = colors.muted
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (selectedTab == 1) {
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                                        if (clipText.isNotBlank()) {
                                            pastedBatchText = clipText
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentPaste,
                                        contentDescription = "Paste from clipboard",
                                        tint = colors.teal
                                    )
                                }
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = colors.muted
                                )
                            }
                        }
                    }
                }

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = colors.surfaceVariant.copy(alpha = 0.5f),
                    contentColor = colors.tealDark,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = colors.teal
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Manual Form", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Paste & Append", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold) }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 18.dp)
                ) {
                        if (selectedTab == 0) {
                            // Manual Form
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Duplicate Question Warning Banner
                                if (duplicateNum != null) {
                                    item {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFFFEF2F2),
                                            border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Default.Warning,
                                                    contentDescription = null,
                                                    tint = Color(0xFFDC2626),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Duplicate Detected! This question statement already exists as Q$duplicateNum in this paper.",
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFB91C1C)
                                                )
                                            }
                                        }
                                    }
                                }

                                item {
                                    OutlinedTextField(
                                        value = questionText,
                                        onValueChange = { questionText = it },
                                        label = { Text("Question Statement / Stem *", fontSize = 11.sp) },
                                        placeholder = { Text("Enter question statement...", fontSize = 11.sp) },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 2,
                                        maxLines = 4
                                    )
                                }

                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = optA,
                                            onValueChange = { optA = it },
                                            label = { Text("Option (A) *", fontSize = 10.5.sp) },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true
                                        )
                                        OutlinedTextField(
                                            value = optB,
                                            onValueChange = { optB = it },
                                            label = { Text("Option (B) *", fontSize = 10.5.sp) },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true
                                        )
                                    }
                                }

                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = optC,
                                            onValueChange = { optC = it },
                                            label = { Text("Option (C) *", fontSize = 10.5.sp) },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true
                                        )
                                        OutlinedTextField(
                                            value = optD,
                                            onValueChange = { optD = it },
                                            label = { Text("Option (D) *", fontSize = 10.5.sp) },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true
                                        )
                                    }
                                }

                                // Correct Answer Selector
                                item {
                                    Column(modifier = Modifier.padding(top = 4.dp)) {
                                        Text(
                                            text = "Correct Answer Key:",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.ink
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            listOf("A", "B", "C", "D").forEach { opt ->
                                                val isSel = selectedAnswer == opt
                                                Surface(
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .clickable { selectedAnswer = opt },
                                                    shape = CircleShape,
                                                    color = if (isSel) Color(0xFF059669) else colors.surfaceVariant,
                                                    border = BorderStroke(1.dp, if (isSel) Color(0xFF047857) else colors.border)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = opt,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isSel) Color.White else colors.inkBold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Question Type Selector
                                item {
                                    Column(modifier = Modifier.padding(top = 4.dp)) {
                                        Text(
                                            text = "Question Type:",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.ink
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            listOf("Direct Fact", "Statement Based", "Assertion & Reason").forEach { type ->
                                                val isSel = questionType == type
                                                Surface(
                                                    modifier = Modifier.clickable { questionType = type },
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = if (isSel) Color(0xFF7E22CE) else colors.surfaceVariant,
                                                    border = BorderStroke(0.5.dp, if (isSel) Color(0xFF6B21A8) else colors.border)
                                                ) {
                                                    Text(
                                                        text = type,
                                                        fontSize = 10.sp,
                                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isSel) Color.White else colors.ink,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                item {
                                    OutlinedTextField(
                                        value = explanationText,
                                        onValueChange = { explanationText = it },
                                        label = { Text("Explanation (Optional)", fontSize = 11.sp) },
                                        placeholder = { Text("Provide pedagogical rationale...", fontSize = 11.sp) },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 2,
                                        maxLines = 3
                                    )
                                }

                                // Diagram, Chart, and Formula Attachment Editor
                                item {
                                    AttachmentEditorSection(
                                        diagramUrl = customDiagramUrl,
                                        diagramLabel = customDiagramLabel,
                                        formula = customFormula,
                                        onDiagramChanged = { url, label ->
                                            customDiagramUrl = url
                                            customDiagramLabel = label
                                        },
                                        onFormulaChanged = { f ->
                                            customFormula = f
                                        }
                                    )
                                }
                            }
                        } else {
                            // Batch Paste & Append with Real-Time Parser Telemetry & Duplicate Preview
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Telemetry & Parsing Status Chips
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = colors.surfaceVariant,
                                    border = BorderStroke(1.dp, colors.border),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.ContentPaste,
                                                    contentDescription = null,
                                                    tint = colors.teal,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = "Parser & Duplicate Telemetry",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colors.inkBold
                                                )
                                            }

                                            if (pastedBatchText.isNotBlank()) {
                                                TextButton(
                                                    onClick = { pastedBatchText = "" },
                                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(13.dp), tint = colors.muted)
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text("Clear", fontSize = 11.sp, color = colors.muted)
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        if (isParsingBatch) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.padding(vertical = 4.dp)
                                            ) {
                                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = colors.teal)
                                                Text("Analyzing pasted questions...", fontSize = 11.sp, color = colors.muted)
                                            }
                                        } else if (pastedBatchText.isBlank()) {
                                            Text(
                                                text = "Paste questions in any standard format (questions, options, answers & solutions). Real-time telemetry will appear automatically.",
                                                fontSize = 11.sp,
                                                color = colors.muted,
                                                lineHeight = 15.sp
                                            )
                                        } else {
                                            // Live metrics flow
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                // Parsed count
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = if (parsedQuestionsCount > 0) Color(0xFFEFF6FF) else Color(0xFFFEF2F2),
                                                    border = BorderStroke(0.6.dp, if (parsedQuestionsCount > 0) Color(0xFFBFDBFE) else Color(0xFFFCA5A5))
                                                ) {
                                                    Text(
                                                        text = "$parsedQuestionsCount Questions Parsed",
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (parsedQuestionsCount > 0) Color(0xFF1D4ED8) else Color(0xFFDC2626),
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                    )
                                                }

                                                // Keys count
                                                val keysCount = parsedBatchResult?.paper?.questions?.count { it.correctAnswer != null } ?: 0
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = Color(0xFFF0FDF4),
                                                    border = BorderStroke(0.6.dp, Color(0xFFBBF7D0))
                                                ) {
                                                    Text(
                                                        text = "$keysCount Answer Keys",
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF15803D),
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                    )
                                                }

                                                // Explanations count
                                                val expCount = parsedBatchResult?.paper?.questions?.count { !it.explanation.isNullOrBlank() } ?: 0
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = Color(0xFFF8FAFC),
                                                    border = BorderStroke(0.6.dp, Color(0xFFCBD5E1))
                                                ) {
                                                    Text(
                                                        text = "$expCount Explanations",
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = Color(0xFF475569),
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                    )
                                                }

                                                // Duplicates status
                                                if (duplicatesCount > 0) {
                                                    Surface(
                                                        shape = RoundedCornerShape(12.dp),
                                                        color = Color(0xFFFEF3C7),
                                                        border = BorderStroke(0.6.dp, Color(0xFFFDE68A))
                                                    ) {
                                                        Text(
                                                            text = "$duplicatesCount Duplicate${if (duplicatesCount > 1) "s" else ""} (will be filtered)",
                                                            fontSize = 10.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFFB45309),
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                        )
                                                    }
                                                }

                                                // Numbering range
                                                if (newQuestionsCount > 0) {
                                                    Surface(
                                                        shape = RoundedCornerShape(12.dp),
                                                        color = Color(0xFFEEF2FF),
                                                        border = BorderStroke(0.6.dp, Color(0xFFC7D2FE))
                                                    ) {
                                                        Text(
                                                            text = "Will append as Q$nextStartNumber – Q$nextEndNumber",
                                                            fontSize = 10.5.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF4338CA),
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Quick Format helper / Sample loader row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Batch Question Input",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.inkBold
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        TextButton(
                                            onClick = {
                                                pastedBatchText = """
1. What is the SI unit of electric current?
(A) Volt
(B) Ampere
(C) Ohm
(D) Watt
Answer: B
Explanation: The SI unit of electric current is the ampere (A).

2. Which organelle is called the powerhouse of the cell?
(A) Nucleus
(B) Ribosome
(C) Mitochondria
(D) Golgi apparatus
Answer: C
Explanation: Mitochondria generate most of the cell's ATP.
                                                """.trimIndent()
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("Load Sample", fontSize = 11.sp, color = colors.teal)
                                        }

                                        TextButton(
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                                                if (clipText.isNotBlank()) {
                                                    pastedBatchText = clipText
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(13.dp), tint = colors.teal)
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text("Paste", fontSize = 11.sp, color = colors.teal)
                                        }
                                    }
                                }

                                // Text area
                                OutlinedTextField(
                                    value = pastedBatchText,
                                    onValueChange = { pastedBatchText = it },
                                    placeholder = {
                                        Text(
                                            "1. Question text here...\n(A) Option 1\n(B) Option 2\n(C) Option 3\n(D) Option 4\nAnswer: B\nExplanation: Explanation text...",
                                            fontSize = 11.5.sp,
                                            lineHeight = 16.sp
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 220.dp, max = 340.dp)
                                        .testTag("paste_append_text_field"),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }

                    // Pinned Bottom Action Bar
                    Surface(
                        color = colors.surfaceVariant,
                        tonalElevation = 3.dp,
                        border = BorderStroke(0.5.dp, colors.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = onDismiss,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                modifier = Modifier.heightIn(min = 40.dp)
                            ) {
                                Text("Cancel", fontSize = 12.sp, color = colors.ink)
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            if (selectedTab == 0) {
                                val canAdd = questionText.isNotBlank() && optA.isNotBlank() && optB.isNotBlank() && duplicateNum == null
                                Button(
                                    onClick = {
                                        val res = viewModel.addCustomQuestion(
                                            questionText = questionText,
                                            optionA = optA,
                                            optionB = optB,
                                            optionC = optC,
                                            optionD = optD,
                                            correctAnswer = selectedAnswer,
                                            explanation = explanationText,
                                            questionType = questionType,
                                            diagramUrl = customDiagramUrl,
                                            diagramLabel = customDiagramLabel,
                                            formula = customFormula
                                        )
                                        if (res.first) {
                                            onDismiss()
                                        }
                                    },
                                    enabled = canAdd,
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    modifier = Modifier.heightIn(min = 40.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.teal)
                                ) {
                                    Text("Add Question", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            } else {
                                val buttonText = when {
                                    newQuestionsCount > 0 -> "Append $newQuestionsCount Questions (Q$nextStartNumber–Q$nextEndNumber)"
                                    parsedQuestionsCount > 0 && duplicatesCount == parsedQuestionsCount -> "All $parsedQuestionsCount Questions are Duplicates"
                                    else -> "Append Questions"
                                }
                                Button(
                                    onClick = {
                                        if (pastedBatchText.isNotBlank()) {
                                            viewModel.appendQuestionsFromRawText(pastedBatchText)
                                            onDismiss()
                                        }
                                    },
                                    enabled = pastedBatchText.isNotBlank() && (newQuestionsCount > 0 || parsedQuestionsCount == 0),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    modifier = Modifier.heightIn(min = 40.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.teal)
                                ) {
                                    Text(buttonText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

@Composable
private fun FilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    accentColor: Color? = null
) {
    val colors = ExamTheme.colors
    Surface(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) (accentColor ?: Color(0xFF2563EB)) else colors.surfaceVariant,
        border = BorderStroke(0.6.dp, if (isSelected) (accentColor ?: Color(0xFF1D4ED8)) else colors.border)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (isSelected) Color.White else (accentColor ?: colors.inkBold),
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}
