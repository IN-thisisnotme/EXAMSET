package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.data.PaperEntity
import com.example.data.StudyNoteEntity
import com.example.model.DueSuggestionCategory
import com.example.model.PomodoroDndMode
import com.example.model.PomodoroPhase
import com.example.model.PomodoroPreset
import com.example.model.StudyDateUtils
import com.example.model.StudyDueSuggestion
import com.example.model.StudyTodoItem
import com.example.model.TodoFilter
import com.example.model.TodoLinkType
import com.example.model.TodoPriority
import com.example.ui.theme.ExamTheme
import com.example.util.PomodoroUiState
import com.example.util.TrustedTimeManager
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StudyPlannerScreen(
    pomodoroStateFlow: StateFlow<PomodoroUiState>,
    dueSuggestions: List<StudyDueSuggestion>,
    todos: List<StudyTodoItem>,
    savedPapers: List<PaperEntity>,
    studyNotes: List<StudyNoteEntity>,
    vocabLanguages: List<String>,
    onStartFocus: (todoId: String?, todoTitle: String?, enableDnd: Boolean) -> Unit,
    onPauseFocus: () -> Unit,
    onResumeFocus: () -> Unit,
    onSkipToBreak: () -> Unit,
    onSkipBreak: () -> Unit,
    onStopFocus: () -> Unit,
    onSelectPreset: (PomodoroPreset) -> Unit,
    onSetCustomDurations: (Int, Int) -> Unit,
    onToggleDndPreference: (Boolean) -> Boolean,
    onSetPreferredDndMode: (PomodoroDndMode) -> Unit,
    onOpenDndSettings: () -> Unit,
    onDisableDndImmediately: () -> Unit,
    onAddTodo: (
        title: String,
        priority: TodoPriority,
        dueDateStr: String,
        targetPomodoros: Int,
        linkedType: TodoLinkType,
        linkedPaperId: Long?,
        linkedNoteId: Long?,
        linkedDeckLanguage: String,
        linkedLabel: String
    ) -> Unit,
    onToggleTodoCompleted: (String) -> Unit,
    onDeleteTodo: (String) -> Unit,
    onFocusTodo: (StudyTodoItem) -> Unit,
    onOpenDueSuggestion: (StudyDueSuggestion) -> Unit,
    onOpenLinkedTodoResource: (StudyTodoItem) -> Unit,
    onDismiss: () -> Unit,
    onOpenBrainCalmBreak: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    var activeFilter by remember { mutableStateOf(TodoFilter.ALL) }
    var showAddTodoDialog by remember { mutableStateOf(false) }

    BackHandler { onDismiss() }

    val todayDateStr = remember {
        StudyDateUtils.formatDateString(TrustedTimeManager.getTrustedTimeMillis())
    }

    val filteredTodos = remember(todos, activeFilter, todayDateStr) {
        when (activeFilter) {
            TodoFilter.ALL -> todos
            TodoFilter.TODAY -> todos.filter {
                !it.isCompleted && (it.dueDateStr.isBlank() || it.dueDateStr <= todayDateStr)
            }
            TodoFilter.HIGH_PRIORITY -> todos.filter {
                !it.isCompleted && it.priority == TodoPriority.HIGH
            }
            TodoFilter.COMPLETED -> todos.filter { it.isCompleted }
        }
    }

    val pendingCount = remember(todos) { todos.count { !it.isCompleted } }

    if (showAddTodoDialog) {
        AddStudyTodoDialog(
            savedPapers = savedPapers,
            studyNotes = studyNotes,
            vocabLanguages = vocabLanguages,
            onAdd = { title, priority, dueDate, targetPomos, linkType, paperId, noteId, lang, label ->
                onAddTodo(title, priority, dueDate, targetPomos, linkType, paperId, noteId, lang, label)
                showAddTodoDialog = false
            },
            onDismiss = { showAddTodoDialog = false }
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("study_planner_screen"),
        containerColor = colors.background,
        topBar = {
            Surface(
                color = colors.surface,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("study_planner_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Close Study Planner",
                                tint = colors.inkBold
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Study Planner & Focus Clock",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "$pendingCount active task(s) • ${dueSuggestions.size} auto-detected due",
                                fontSize = 11.5.sp,
                                color = colors.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Button(
                            onClick = { showAddTodoDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.teal,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier
                                .heightIn(min = 42.dp)
                                .testTag("add_todo_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Task", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    HorizontalDivider(color = colors.border.copy(alpha = 0.6f))
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 780.dp)
                    .testTag("study_planner_lazy_column"),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Leaf-isolated Live Pomodoro Focus Clock Card
                item(key = "live_focus_clock") {
                    LiveFocusClockCard(
                        pomodoroStateFlow = pomodoroStateFlow,
                        onStartFocus = onStartFocus,
                        onPauseFocus = onPauseFocus,
                        onResumeFocus = onResumeFocus,
                        onSkipToBreak = onSkipToBreak,
                        onSkipBreak = onSkipBreak,
                        onStopFocus = onStopFocus,
                        onSelectPreset = onSelectPreset,
                        onSetCustomDurations = onSetCustomDurations,
                        onToggleDndPreference = onToggleDndPreference,
                        onSetPreferredDndMode = onSetPreferredDndMode,
                        onOpenDndSettings = onOpenDndSettings,
                        onDisableDndImmediately = onDisableDndImmediately,
                        onOpenBrainCalmBreak = onOpenBrainCalmBreak
                    )
                }

                // 2. Auto-Detected Due Reviews & Unfinished Mocks Strip
                item(key = "auto_due_strip") {
                    AutoDetectedDueStrip(
                        suggestions = dueSuggestions,
                        onOpenSuggestion = onOpenDueSuggestion
                    )
                }

                // 3. To-Do List Section Header + Filter Chips
                item(key = "todo_header_filters") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.TaskAlt,
                                    contentDescription = null,
                                    tint = colors.teal,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Custom Study To-Do List",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.inkBold
                                )
                            }
                            TextButton(onClick = { showAddTodoDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("New Task", fontWeight = FontWeight.Bold, color = colors.teal)
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TodoFilter.entries.forEach { filter ->
                                val selected = activeFilter == filter
                                FilterChip(
                                    selected = selected,
                                    onClick = { activeFilter = filter },
                                    label = {
                                        Text(
                                            text = filter.label,
                                            fontSize = 12.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = colors.tealLight,
                                        selectedLabelColor = colors.teal,
                                        labelColor = colors.ink
                                    ),
                                    modifier = Modifier.testTag("todo_filter_${filter.name.lowercase()}")
                                )
                            }
                        }
                    }
                }

                // 4. To-Do Items or Empty Card
                if (filteredTodos.isEmpty()) {
                    item(key = "todo_empty") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = colors.surface),
                            border = BorderStroke(1.dp, colors.border)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TaskAlt,
                                    contentDescription = null,
                                    tint = colors.muted,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = if (todos.isEmpty()) "No study tasks planned yet" else "No tasks match \"${activeFilter.label}\"",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.inkBold
                                )
                                Text(
                                    text = "Add a mock test target, chapter revision, or link a Knowledge Set and start a Pomodoro block.",
                                    fontSize = 12.sp,
                                    color = colors.muted
                                )
                                OutlinedButton(
                                    onClick = { showAddTodoDialog = true },
                                    border = BorderStroke(1.dp, colors.teal),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = colors.teal, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Add First Task", color = colors.teal, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    items(filteredTodos, key = { it.id }) { item ->
                        StudyTodoCard(
                            item = item,
                            onToggleCompleted = { onToggleTodoCompleted(item.id) },
                            onDelete = { onDeleteTodo(item.id) },
                            onStartFocus = { onFocusTodo(item) },
                            onOpenLinkedResource = { onOpenLinkedTodoResource(item) }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

/**
 * Leaf-isolated Pomodoro clock card so 1-second timer ticks never recompose the due items or to-do list.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LiveFocusClockCard(
    pomodoroStateFlow: StateFlow<PomodoroUiState>,
    onStartFocus: (todoId: String?, todoTitle: String?, enableDnd: Boolean) -> Unit,
    onPauseFocus: () -> Unit,
    onResumeFocus: () -> Unit,
    onSkipToBreak: () -> Unit,
    onSkipBreak: () -> Unit,
    onStopFocus: () -> Unit,
    onSelectPreset: (PomodoroPreset) -> Unit,
    onSetCustomDurations: (Int, Int) -> Unit,
    onToggleDndPreference: (Boolean) -> Boolean,
    onSetPreferredDndMode: (PomodoroDndMode) -> Unit,
    onOpenDndSettings: () -> Unit,
    onDisableDndImmediately: () -> Unit,
    onOpenBrainCalmBreak: () -> Unit
) {
    val state by pomodoroStateFlow.collectAsState()
    val colors = ExamTheme.colors
    val context = LocalContext.current

    var showDndExplainerDialog by remember { mutableStateOf(false) }
    var showCustomDurationsDialog by remember { mutableStateOf(false) }
    var pendingFocusAfterNotificationPerm by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        if (pendingFocusAfterNotificationPerm) {
            pendingFocusAfterNotificationPerm = false
            onStartFocus(state.linkedTodoId, state.linkedTodoTitle.ifBlank { null }, state.isDndEnabledForSession)
        }
    }

    val startFocusWithNotificationCheck: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                pendingFocusAfterNotificationPerm = true
                runCatching {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }.onFailure {
                    pendingFocusAfterNotificationPerm = false
                    onStartFocus(state.linkedTodoId, state.linkedTodoTitle.ifBlank { null }, state.isDndEnabledForSession)
                }
            } else {
                onStartFocus(state.linkedTodoId, state.linkedTodoTitle.ifBlank { null }, state.isDndEnabledForSession)
            }
        } else {
            onStartFocus(state.linkedTodoId, state.linkedTodoTitle.ifBlank { null }, state.isDndEnabledForSession)
        }
        Unit
    }

    if (showDndExplainerDialog) {
        AlertDialog(
            onDismissRequest = { showDndExplainerDialog = false },
            containerColor = colors.surface,
            icon = {
                Icon(Icons.Default.DoNotDisturbOn, contentDescription = null, tint = colors.teal)
            },
            title = {
                Text("Enable Quiet Mode (Do Not Disturb)?", fontWeight = FontWeight.Bold, color = colors.inkBold)
            },
            text = {
                Text(
                    text = "ExamSet can automatically silence distracting notifications during Focus blocks and restore your normal phone sound on break, pause, stop, or restart.\n\nAndroid requires one-time Do Not Disturb Policy Access permission. You can still run the Focus clock completely without DND.",
                    fontSize = 13.sp,
                    color = colors.ink,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDndExplainerDialog = false
                        onOpenDndSettings()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White)
                ) {
                    Text("Grant in Android Settings", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDndExplainerDialog = false
                        onToggleDndPreference(false)
                    }
                ) {
                    Text("Use Timer Only (No DND)", color = colors.muted)
                }
            },
            modifier = Modifier.testTag("dnd_permission_dialog")
        )
    }

    if (showCustomDurationsDialog) {
        var focusInput by remember { mutableStateOf(state.customFocusMinutes.toString()) }
        var breakInput by remember { mutableStateOf(state.customBreakMinutes.toString()) }
        AlertDialog(
            onDismissRequest = { showCustomDurationsDialog = false },
            containerColor = colors.surface,
            title = { Text("Custom Focus & Break Durations", fontWeight = FontWeight.Bold, color = colors.inkBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = focusInput,
                        onValueChange = { focusInput = it.filter { ch -> ch.isDigit() }.take(3) },
                        label = { Text("Focus Minutes (5–180)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = breakInput,
                        onValueChange = { breakInput = it.filter { ch -> ch.isDigit() }.take(2) },
                        label = { Text("Break Minutes (1–60)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val f = focusInput.toIntOrNull() ?: state.customFocusMinutes
                        val b = breakInput.toIntOrNull() ?: state.customBreakMinutes
                        onSetCustomDurations(f, b)
                        showCustomDurationsDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White)
                ) {
                    Text("Apply", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDurationsDialog = false }) {
                    Text("Cancel", color = colors.muted)
                }
            }
        )
    }

    val ringColor = when (state.phase) {
        PomodoroPhase.FOCUS -> colors.teal
        PomodoroPhase.BREAK -> colors.green
        PomodoroPhase.PAUSED -> colors.amber
        PomodoroPhase.IDLE -> colors.teal.copy(alpha = 0.7f)
    }

    val phaseBadgeLabel = when (state.phase) {
        PomodoroPhase.IDLE -> "READY"
        PomodoroPhase.FOCUS -> "FOCUS"
        PomodoroPhase.BREAK -> "BREAK"
        PomodoroPhase.PAUSED -> "PAUSED"
    }

    val rem = state.remainingSeconds.coerceAtLeast(0L)
    val hrs = rem / 3600L
    val mins = (rem % 3600L) / 60L
    val secs = rem % 60L
    val clockText = if (hrs > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("live_focus_clock_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.2.dp, if (state.isDndCurrentlyActive) colors.teal else colors.border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Emergency / Instant DND Disable Banner whenever DND is active
            if (state.isDndCurrentlyActive) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colors.tealLight,
                    border = BorderStroke(1.dp, colors.teal),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsOff,
                                contentDescription = null,
                                tint = colors.teal,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Do Not Disturb is silencing alerts",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold
                            )
                        }
                        Button(
                            onClick = onDisableDndImmediately,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.red,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .heightIn(min = 36.dp)
                                .testTag("turn_off_dnd_now_button")
                        ) {
                            Text("DND Active • Turn Off", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Header row: Focus Phase Badge + Completed Count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ringColor.copy(alpha = 0.14f),
                    border = BorderStroke(0.8.dp, ringColor.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = phaseBadgeLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = ringColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = "${state.completedSessionsCount} block(s) completed today",
                    fontSize = 11.5.sp,
                    color = colors.muted,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Circular Countdown Ring
            Box(
                modifier = Modifier.size(184.dp),
                contentAlignment = Alignment.Center
            ) {
                val trackColor = colors.border
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokePx = 11.dp.toPx()
                    drawArc(
                        color = trackColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = 360f * state.progressFraction,
                        useCenter = false,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = clockText,
                        fontSize = if (hrs > 0L) 28.sp else 34.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        color = colors.inkBold,
                        modifier = Modifier.testTag("pomodoro_clock_display")
                    )
                    Text(
                        text = "${state.activeFocusMinutes}m Focus • ${state.activeBreakMinutes}m Break",
                        fontSize = 11.sp,
                        color = colors.muted
                    )
                }
            }

            // Linked Task Badge if any
            if (state.linkedTodoTitle.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colors.surfaceVariant,
                    border = BorderStroke(0.8.dp, colors.border)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Flag, contentDescription = null, tint = colors.teal, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Focusing on: ${state.linkedTodoTitle}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.inkBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Primary Controls
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (state.phase) {
                    PomodoroPhase.IDLE -> {
                        Button(
                            onClick = startFocusWithNotificationCheck,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("pomodoro_start_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Start Focus", fontWeight = FontWeight.Bold)
                        }
                    }
                    PomodoroPhase.FOCUS, PomodoroPhase.BREAK -> {
                        Button(
                            onClick = onPauseFocus,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.amber, contentColor = Color.White),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("pomodoro_pause_button")
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pause", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { if (state.phase == PomodoroPhase.FOCUS) onSkipToBreak() else onSkipBreak() },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, colors.border),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.SkipNext, contentDescription = null, tint = colors.inkBold, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (state.phase == PomodoroPhase.FOCUS) "Break" else "Skip Break", color = colors.inkBold)
                        }
                        OutlinedButton(
                            onClick = onStopFocus,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, colors.red.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("pomodoro_stop_button")
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, tint = colors.red, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Stop", color = colors.red, fontWeight = FontWeight.SemiBold)
                        }
                        if (state.phase == PomodoroPhase.BREAK) {
                            OutlinedButton(
                                onClick = onOpenBrainCalmBreak,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, colors.teal),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("pomodoro_brain_calm_break_btn")
                            ) {
                                Icon(Icons.Default.Psychology, contentDescription = null, tint = colors.teal, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Brain & Calm Break", color = colors.teal, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    PomodoroPhase.PAUSED -> {
                        Button(
                            onClick = onResumeFocus,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("pomodoro_resume_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Resume", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = onStopFocus,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, colors.red.copy(alpha = 0.6f)),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, tint = colors.red, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset", color = colors.red, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Preset Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PomodoroPreset.entries.forEach { preset ->
                    val selected = state.preset == preset
                    FilterChip(
                        selected = selected,
                        onClick = {
                            if (preset == PomodoroPreset.CUSTOM) {
                                showCustomDurationsDialog = true
                            } else {
                                onSelectPreset(preset)
                            }
                        },
                        label = {
                            Text(
                                text = if (preset == PomodoroPreset.CUSTOM) {
                                    "Custom (${state.customFocusMinutes}/${state.customBreakMinutes})"
                                } else {
                                    preset.label
                                },
                                fontSize = 11.5.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.tealLight,
                            selectedLabelColor = colors.teal
                        ),
                        modifier = Modifier.testTag("preset_${preset.name.lowercase()}")
                    )
                }
            }

            HorizontalDivider(color = colors.borderLight)

            // Opt-in Protected DND Controls
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val nextEnable = !state.isDndEnabledForSession
                            val ok = onToggleDndPreference(nextEnable)
                            if (nextEnable && !ok) {
                                showDndExplainerDialog = true
                            }
                        }
                        .padding(vertical = 4.dp)
                        .testTag("dnd_toggle_row"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DoNotDisturbOn,
                            contentDescription = null,
                            tint = if (state.isDndEnabledForSession) colors.teal else colors.muted,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Quiet Mode (Auto DND during Focus)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold
                            )
                            Text(
                                text = if (state.hasDndPolicyAccess) {
                                    "Policy Access Granted • Restores automatically on break/stop"
                                } else {
                                    "Tap to grant DND Policy Access or use Timer Only"
                                },
                                fontSize = 11.sp,
                                color = colors.muted
                            )
                        }
                    }

                    Switch(
                        checked = state.isDndEnabledForSession,
                        onCheckedChange = { wantEnable ->
                            val ok = onToggleDndPreference(wantEnable)
                            if (wantEnable && !ok) {
                                showDndExplainerDialog = true
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = colors.teal
                        ),
                        modifier = Modifier.testTag("dnd_toggle_switch")
                    )
                }

                // Mode pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PomodoroDndMode.entries.forEach { dndMode ->
                        val isSelected = state.preferredDndMode == dndMode
                        Surface(
                            onClick = { onSetPreferredDndMode(dndMode) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) colors.tealLight else colors.surfaceVariant,
                            border = BorderStroke(1.dp, if (isSelected) colors.teal else colors.border),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                Text(
                                    text = dndMode.title,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) colors.teal else colors.inkBold
                                )
                                Text(
                                    text = if (dndMode == PomodoroDndMode.PRIORITY_CALLS) "Allows starred calls" else "Strict alarms only",
                                    fontSize = 10.5.sp,
                                    color = colors.muted
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AutoDetectedDueStrip(
    suggestions: List<StudyDueSuggestion>,
    onOpenSuggestion: (StudyDueSuggestion) -> Unit
) {
    val colors = ExamTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = colors.teal, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Due Today & Resume Queue",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkBold
                )
            }
            Text(
                text = if (suggestions.isEmpty()) "All caught up!" else "${suggestions.size} item(s)",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (suggestions.isEmpty()) colors.green else colors.teal
            )
        }

        if (suggestions.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.greenLight.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, colors.green.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = colors.green, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "No overdue flashcards, vocab reviews, or unfinished mock tests right now.",
                        fontSize = 12.sp,
                        color = colors.inkBold
                    )
                }
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.testTag("auto_due_lazy_row")
            ) {
                items(suggestions, key = { it.id }) { suggestion ->
                    val (catIcon, catTint) = when (suggestion.category) {
                        DueSuggestionCategory.FLASHCARDS -> Icons.AutoMirrored.Filled.MenuBook to colors.teal
                        DueSuggestionCategory.VOCABULARY -> Icons.Default.Translate to colors.green
                        DueSuggestionCategory.HEADWORD_INBOX -> Icons.Default.Inbox to colors.amber
                        DueSuggestionCategory.PAUSED_TEST -> Icons.Default.School to colors.red
                    }
                    Card(
                        onClick = { onOpenSuggestion(suggestion) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.surface),
                        border = BorderStroke(1.dp, catTint.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .width(228.dp)
                            .testTag("due_card_${suggestion.id}")
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = catTint.copy(alpha = 0.14f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(catIcon, contentDescription = null, tint = catTint, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = suggestion.category.badgeLabel,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = catTint
                                        )
                                    }
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = catTint
                                ) {
                                    Text(
                                        text = "${suggestion.count}",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = suggestion.title,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = suggestion.subtitle,
                                fontSize = 11.5.sp,
                                color = colors.muted,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudyTodoCard(
    item: StudyTodoItem,
    onToggleCompleted: () -> Unit,
    onDelete: () -> Unit,
    onStartFocus: () -> Unit,
    onOpenLinkedResource: () -> Unit
) {
    val colors = ExamTheme.colors
    val priorityColor = when (item.priority) {
        TodoPriority.HIGH -> colors.red
        TodoPriority.MEDIUM -> colors.amber
        TodoPriority.LOW -> colors.teal
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("todo_card_${item.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isCompleted) colors.surfaceVariant.copy(alpha = 0.7f) else colors.surface
        ),
        border = BorderStroke(1.dp, colors.border)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = item.isCompleted,
                    onCheckedChange = { onToggleCompleted() },
                    colors = CheckboxDefaults.colors(checkedColor = colors.teal),
                    modifier = Modifier.testTag("todo_checkbox_${item.id}")
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (item.isCompleted) colors.muted else colors.inkBold,
                        textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = priorityColor.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = item.priority.label,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = priorityColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        if (item.dueDateStr.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = colors.surfaceVariant
                            ) {
                                Text(
                                    text = StudyDateUtils.formatHumanDate(item.dueDateStr),
                                    fontSize = 10.5.sp,
                                    color = colors.ink,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colors.tealLight
                        ) {
                            Text(
                                text = "${item.completedPomodoros}/${item.targetPomodoros} blocks",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.teal,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        if (item.linkedType != TodoLinkType.NONE && item.linkedLabel.isNotBlank()) {
                            Surface(
                                onClick = onOpenLinkedResource,
                                shape = RoundedCornerShape(6.dp),
                                color = colors.blueLight,
                                border = BorderStroke(0.6.dp, colors.teal.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Link, contentDescription = null, tint = colors.teal, modifier = Modifier.size(11.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = item.linkedLabel,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.tealDark,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                if (!item.isCompleted) {
                    OutlinedButton(
                        onClick = onStartFocus,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, colors.teal.copy(alpha = 0.6f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("todo_focus_${item.id}")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = colors.teal, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Focus", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = colors.teal)
                    }
                }

                var showDeleteConfirm by remember { mutableStateOf(false) }
                if (showDeleteConfirm) {
                    AlertDialog(
                        onDismissRequest = { showDeleteConfirm = false },
                        containerColor = colors.surface,
                        titleContentColor = colors.inkBold,
                        textContentColor = colors.ink,
                        title = { Text("Delete Study Task?", fontWeight = FontWeight.Bold) },
                        text = {
                            Text("Remove \"${item.title}\" from your Study Planner?")
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showDeleteConfirm = false
                                    onDelete()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.red, contentColor = Color.White),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("Delete", fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { showDeleteConfirm = false },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                IconButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Task",
                        tint = colors.muted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddStudyTodoDialog(
    savedPapers: List<PaperEntity>,
    studyNotes: List<StudyNoteEntity>,
    vocabLanguages: List<String>,
    onAdd: (
        title: String,
        priority: TodoPriority,
        dueDateStr: String,
        targetPomodoros: Int,
        linkedType: TodoLinkType,
        linkedPaperId: Long?,
        linkedNoteId: Long?,
        linkedDeckLanguage: String,
        linkedLabel: String
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ExamTheme.colors
    val nowMs = remember { TrustedTimeManager.getTrustedTimeMillis() }
    val todayStr = remember { StudyDateUtils.formatDateString(nowMs) }
    val tomorrowStr = remember { StudyDateUtils.addDaysToDateString(nowMs, 1) }

    var title by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf(TodoPriority.MEDIUM) }
    var dueDateStr by remember { mutableStateOf(todayStr) }
    var targetPomodoros by remember { mutableIntStateOf(1) }
    var showDatePicker by remember { mutableStateOf(false) }

    var linkType by remember { mutableStateOf(TodoLinkType.NONE) }
    var selectedPaper by remember { mutableStateOf<PaperEntity?>(savedPapers.firstOrNull()) }
    var selectedNote by remember { mutableStateOf<StudyNoteEntity?>(studyNotes.firstOrNull()) }
    var selectedVocabLang by remember { mutableStateOf(vocabLanguages.firstOrNull().orEmpty()) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = StudyDateUtils.parseDateToStartOfDayMillis(dueDateStr) ?: nowMs
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { picked ->
                            dueDateStr = StudyDateUtils.formatDateString(picked)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("Select", fontWeight = FontWeight.Bold, color = colors.teal)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", color = colors.muted)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.border),
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .widthIn(max = 460.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Add Study Task", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.inkBold)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Task Title") },
                        placeholder = { Text("e.g., Revise Polity Articles 12–35") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("todo_title_input")
                    )

                Text("Priority", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.inkBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TodoPriority.entries.forEach { p ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text(p.label, fontSize = 11.5.sp) },
                            modifier = Modifier.testTag("todo_priority_${p.name.lowercase()}")
                        )
                    }
                }

                Text("Due Date", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.inkBold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = dueDateStr.isBlank(),
                        onClick = { dueDateStr = "" },
                        label = { Text("No Date", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = dueDateStr == todayStr,
                        onClick = { dueDateStr = todayStr },
                        label = { Text("Today", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = dueDateStr == tomorrowStr,
                        onClick = { dueDateStr = tomorrowStr },
                        label = { Text("Tomorrow", fontSize = 11.sp) }
                    )
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.heightIn(min = 34.dp)
                    ) {
                        Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (dueDateStr.isBlank()) "Pick Date" else dueDateStr, fontSize = 11.sp)
                    }
                }

                Text("Target Focus Blocks (${targetPomodoros})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.inkBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1, 2, 3, 4, 6).forEach { count ->
                        FilterChip(
                            selected = targetPomodoros == count,
                            onClick = { targetPomodoros = count },
                            label = { Text("$count", fontSize = 11.5.sp) }
                        )
                    }
                }

                Text("Optional Link", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.inkBold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = linkType == TodoLinkType.NONE,
                        onClick = { linkType = TodoLinkType.NONE },
                        label = { Text("None", fontSize = 11.sp) }
                    )
                    if (savedPapers.isNotEmpty()) {
                        FilterChip(
                            selected = linkType == TodoLinkType.PAPER,
                            onClick = { linkType = TodoLinkType.PAPER },
                            label = { Text("Question Set", fontSize = 11.sp) }
                        )
                    }
                    if (studyNotes.isNotEmpty()) {
                        FilterChip(
                            selected = linkType == TodoLinkType.NOTE,
                            onClick = { linkType = TodoLinkType.NOTE },
                            label = { Text("Knowledge Note", fontSize = 11.sp) }
                        )
                    }
                    if (vocabLanguages.isNotEmpty()) {
                        FilterChip(
                            selected = linkType == TodoLinkType.VOCAB_DECK,
                            onClick = { linkType = TodoLinkType.VOCAB_DECK },
                            label = { Text("Vocabulary", fontSize = 11.sp) }
                        )
                    }
                }

                when (linkType) {
                    TodoLinkType.PAPER -> {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            savedPapers.take(5).forEach { paper ->
                                val isSel = selectedPaper?.id == paper.id
                                Surface(
                                    onClick = { selectedPaper = paper },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSel) colors.tealLight else colors.surfaceVariant,
                                    border = BorderStroke(0.8.dp, if (isSel) colors.teal else colors.border),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = paper.title.ifBlank { "Paper #${paper.id}" },
                                        fontSize = 11.5.sp,
                                        color = colors.inkBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                    TodoLinkType.NOTE -> {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            studyNotes.take(5).forEach { note ->
                                val isSel = selectedNote?.id == note.id
                                Surface(
                                    onClick = { selectedNote = note },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSel) colors.tealLight else colors.surfaceVariant,
                                    border = BorderStroke(0.8.dp, if (isSel) colors.teal else colors.border),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = note.title.ifBlank { "Note #${note.id}" },
                                        fontSize = 11.5.sp,
                                        color = colors.inkBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                    TodoLinkType.VOCAB_DECK -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            vocabLanguages.forEach { lang ->
                                FilterChip(
                                    selected = selectedVocabLang.equals(lang, ignoreCase = true),
                                    onClick = { selectedVocabLang = lang },
                                    label = { Text(lang, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                    TodoLinkType.NONE -> Unit
                }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = colors.muted)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val label = when (linkType) {
                                TodoLinkType.PAPER -> selectedPaper?.title.orEmpty()
                                TodoLinkType.NOTE -> selectedNote?.title.orEmpty()
                                TodoLinkType.VOCAB_DECK -> "$selectedVocabLang Vocab"
                                TodoLinkType.NONE -> ""
                            }
                            onAdd(
                                title,
                                priority,
                                dueDateStr,
                                targetPomodoros,
                                linkType,
                                if (linkType == TodoLinkType.PAPER) selectedPaper?.id else null,
                                if (linkType == TodoLinkType.NOTE) selectedNote?.id else null,
                                if (linkType == TodoLinkType.VOCAB_DECK) selectedVocabLang else "",
                                label
                            )
                        },
                        enabled = title.trim().isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White),
                        modifier = Modifier.testTag("confirm_add_todo_button")
                    ) {
                        Text("Save Task", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
