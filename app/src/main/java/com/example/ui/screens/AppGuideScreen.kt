package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ExamTheme

enum class AppGuideCategory(val label: String) {
    ALL("All"),
    EXAMS_TESTS("Exams & Tests"),
    NOTES_FLASHCARDS("Notes & Flashcards"),
    LANGUAGES_AUDIO("Languages & Audio"),
    FOCUS_PRODUCTIVITY("Focus & Productivity"),
    BACKUP_TOOLS("Backup & Tools")
}

enum class GuideDestination {
    EXAMS_LIBRARY,
    INPUT_MCQ_SHEET,
    STUDENT_TEST,
    PRINT_OMR,
    AI_PROMPT_STUDIO,
    KNOWLEDGE_STUDIO,
    VOCABULARY_HUB,
    HEADWORD_INBOX,
    STUDY_PLANNER,
    ANALYTICS,
    BACKUP_VAULT,
    COUNTDOWNS,
    BRAIN_LAB
}

data class AppGuideEntry(
    val id: String,
    val title: String,
    val category: AppGuideCategory,
    val icon: ImageVector,
    val summary: String,
    val quickTip: String,
    val details: String,
    val keywords: List<String>,
    val actionLabel: String,
    val destination: GuideDestination
)

private val APP_GUIDE_CATALOG: List<AppGuideEntry> = listOf(
    AppGuideEntry(
        id = "mcq_importer",
        title = "Question Set Importer (Aiken, JSON, Table)",
        category = AppGuideCategory.EXAMS_TESTS,
        icon = Icons.Default.EditNote,
        summary = "Paste or import questions from text, Markdown tables, JSON arrays, or files into organized folders.",
        quickTip = "Number questions cleanly (1. / Q1.), keep options (A)-(D), and place an Answer Key section like `1: A` at the end.",
        details = "Supports single-line, multi-line, Hindi/Devanagari options, LaTeX math ($...$), Assertion-Reason blocks, and automatic duplicate detection across your folder.",
        keywords = listOf("import", "aiken", "json", "csv", "markdown", "table", "paste", "questions", "format", "latex"),
        actionLabel = "Open Importer",
        destination = GuideDestination.INPUT_MCQ_SHEET
    ),
    AppGuideEntry(
        id = "tcs_cbt_test",
        title = "TCS iON CBT & Scrollable Exam Modes",
        category = AppGuideCategory.EXAMS_TESTS,
        icon = Icons.Default.Quiz,
        summary = "Practice full-screen timed mock tests with Save & Next, Mark for Review, 5-state palette, or continuous paper scroll.",
        quickTip = "Tap 'Paper' or 'CBT' in the test header anytime to switch between single-question CBT and continuous paper scroll.",
        details = "Timers anchor to the device monotonic clock so screen sleep never drifts your remaining minutes. Autosaves every 30s and on exit/pause.",
        keywords = listOf("cbt", "tcs", "mock", "test", "timer", "countdown", "review", "palette", "submit", "practice"),
        actionLabel = "Open Student Test",
        destination = GuideDestination.STUDENT_TEST
    ),
    AppGuideEntry(
        id = "question_pacing",
        title = "Per-Question Pacing & Time Telemetry",
        category = AppGuideCategory.EXAMS_TESTS,
        icon = Icons.Default.Speed,
        summary = "Measures exact seconds spent on each question and surfaces your slowest items plus Correct vs. Incorrect pacing.",
        quickTip = "Open Result Hub -> Diagnostics or Study Analytics to spot time-sink questions after submitting a test.",
        details = "Pacing pauses automatically whenever you pause/background the app or open the question palette, keeping averages accurate.",
        keywords = listOf("pacing", "time", "speed", "slowest", "seconds", "telemetry", "diagnostics", "analytics"),
        actionLabel = "View Analytics",
        destination = GuideDestination.ANALYTICS
    ),
    AppGuideEntry(
        id = "exam_countdowns",
        title = "Exam-Day Countdowns & Pinned Badges",
        category = AppGuideCategory.FOCUS_PRODUCTIVITY,
        icon = Icons.Default.EventAvailable,
        summary = "Pin upcoming exam target dates on your home library strip so days remaining stay visible every day.",
        quickTip = "Pin up to 2 priority exams for compact top badges, or tap Countdowns to manage your full schedule.",
        details = "Synced via Trusted Time and included inside encrypted Backup Vault archives so targets never vanish across devices.",
        keywords = listOf("countdown", "exam date", "days left", "schedule", "target", "calendar", "pin"),
        actionLabel = "Manage Countdowns",
        destination = GuideDestination.COUNTDOWNS
    ),
    AppGuideEntry(
        id = "print_omr",
        title = "Printable Exam Papers & Camera OMR Scanner",
        category = AppGuideCategory.EXAMS_TESTS,
        icon = Icons.Default.Print,
        summary = "Export print-ready PDF booklets with OMR bubble sheets or grade physical filled sheets with the camera.",
        quickTip = "Include Candidate Details and OMR Sheet in Print Config for classroom-grade paper tests.",
        details = "Supports Student Paper, Answer Key Table, and Master Booklet with green-highlighted explanations and automatic OMR evaluation.",
        keywords = listOf("print", "pdf", "omr", "scanner", "camera", "bubble", "booklet", "offline html"),
        actionLabel = "Open Print & OMR",
        destination = GuideDestination.PRINT_OMR
    ),
    AppGuideEntry(
        id = "knowledge_studio",
        title = "Knowledge Studio & SM-2 Flashcards",
        category = AppGuideCategory.NOTES_FLASHCARDS,
        icon = Icons.AutoMirrored.Filled.MenuBook,
        summary = "Convert one-liners, missed test questions, and structured study notes into spaced-repetition flashcards.",
        quickTip = "Use `Term :: Definition` or `# Heading` blocks; SM-2 schedules optimal review intervals automatically.",
        details = "Turn incorrect and skipped test questions into targeted Active Recall decks with a single tap on your scorecard.",
        keywords = listOf("notes", "flashcards", "sm2", "spaced repetition", "active recall", "knowledge", "study"),
        actionLabel = "Open Library",
        destination = GuideDestination.KNOWLEDGE_STUDIO
    ),
    AppGuideEntry(
        id = "notebook_dictation",
        title = "Notebook Dictation Mode (WPM & Cues)",
        category = AppGuideCategory.LANGUAGES_AUDIO,
        icon = Icons.Default.RecordVoiceOver,
        summary = "Listen to notes dictated naturally with adjustable Words Per Minute, elevating pace, and punctuation cues.",
        quickTip = "Switch Punctuation Cues between Off, Essential, and Full Verbatim when writing down definitions in a notebook.",
        details = "Built on Android TextToSpeech with sentence/line replay controls and hands-free dictation pacing.",
        keywords = listOf("dictation", "audio", "tts", "voice", "wpm", "speed", "notebook", "punctuation", "speak"),
        actionLabel = "Open Library",
        destination = GuideDestination.EXAMS_LIBRARY
    ),
    AppGuideEntry(
        id = "vocab_hub",
        title = "Vocabulary Hub & Headword Inbox",
        category = AppGuideCategory.LANGUAGES_AUDIO,
        icon = Icons.Default.Translate,
        summary = "Organize multilingual decks with IPA/romanization, Word of the Day, duplicate protection, and a queued inbox.",
        quickTip = "Queue new words in Headword Inbox first, copy an AI enrichment prompt, and import zero-duplicate cards back.",
        details = "Supports language-specific levels (A1-C2 / Beginner-Advanced), variant normalization, and daily vocabulary reminders.",
        keywords = listOf("vocabulary", "words", "headword", "inbox", "language", "dictionary", "srs", "ipa"),
        actionLabel = "Open Vocabulary Hub",
        destination = GuideDestination.VOCABULARY_HUB
    ),
    AppGuideEntry(
        id = "headword_queue",
        title = "Headword Inbox Queue",
        category = AppGuideCategory.LANGUAGES_AUDIO,
        icon = Icons.Default.Inbox,
        summary = "Capture tricky words while reading or practicing and batch-enrich them into your Vocabulary Hub.",
        quickTip = "Filter queued words by language and graduate them automatically once imported.",
        details = "Prevents duplicate entries against both existing decks and previously dismissed headwords.",
        keywords = listOf("headword", "inbox", "queue", "capture", "words", "english", "hindi", "korean"),
        actionLabel = "Open Headword Inbox",
        destination = GuideDestination.HEADWORD_INBOX
    ),
    AppGuideEntry(
        id = "study_planner_dnd",
        title = "Smart Study Planner & Protected DND Focus Clock",
        category = AppGuideCategory.FOCUS_PRODUCTIVITY,
        icon = Icons.Default.NotificationsOff,
        summary = "Combine auto-detected due flashcards/vocab/tests, custom to-dos, and timestamp-accurate Pomodoro focus blocks.",
        quickTip = "Enable Quiet Mode (DND) optionally during Focus blocks; an instant 'Turn Off' control is always one tap away.",
        details = "Stores a synchronous backup of your previous Do Not Disturb mode before silencing notifications and restores it on pause, break, stop, or restart.",
        keywords = listOf("planner", "todo", "pomodoro", "focus", "dnd", "do not disturb", "timer", "tasks", "due"),
        actionLabel = "Open Study Planner",
        destination = GuideDestination.STUDY_PLANNER
    ),
    AppGuideEntry(
        id = "ai_prompt_studio",
        title = "AI Prompt Studio (Questions, Notes, Vocab)",
        category = AppGuideCategory.BACKUP_TOOLS,
        icon = Icons.Default.AutoAwesome,
        summary = "Generate precision prompts for external AI assistants that output ExamSet-ready MCQs, Knowledge Sets, and Vocab JSON.",
        quickTip = "Save reusable prompt presets and pin your favorite subjects for one-tap prompt generation.",
        details = "Supports custom instructions, difficulty weighting, syllabus scope, and schema-strict formatting rules.",
        keywords = listOf("ai", "prompt", "generator", "preset", "template", "chatgpt", "gemini", "claude"),
        actionLabel = "Open AI Prompt Studio",
        destination = GuideDestination.AI_PROMPT_STUDIO
    ),
    AppGuideEntry(
        id = "backup_vault",
        title = "Backup Vault & Cloud Sync",
        category = AppGuideCategory.BACKUP_TOOLS,
        icon = Icons.Default.Backup,
        summary = "Create SHA-256 verified ZIP snapshots of all papers, attempts, study notes, countdowns, tasks, and decks.",
        quickTip = "Use Smart Merge when combining libraries across devices, or Full Replace to clone an exact snapshot.",
        details = "Supports local downloads, linked cloud files, Google Drive, OneDrive, and automatic backup schedules.",
        keywords = listOf("backup", "vault", "restore", "zip", "sync", "drive", "onedrive", "export", "merge"),
        actionLabel = "Open Backup Vault",
        destination = GuideDestination.BACKUP_VAULT
    ),
    AppGuideEntry(
        id = "brain_calm_lab",
        title = "Brain & Calm Lab (N-Back, Logic, Schulte & Reset)",
        category = AppGuideCategory.FOCUS_PRODUCTIVITY,
        icon = Icons.Default.Psychology,
        summary = "Offline cognitive drills (N-Back, Corsi, Stroop, Venn Syllogisms, Seating Grids, Schulte, Mental Quant) plus guided breathing and untimed Zen grids.",
        quickTip = "Use the built-in 3–5 minute break budget before mock tests or during Pomodoro breaks to reset focus without procrastination.",
        details = "All drills run 100% offline with deterministic verification, personal-best tracking, and Vault backup parity.",
        keywords = listOf("brain", "calm", "n-back", "corsi", "stroop", "syllogism", "seating", "schulte", "quant", "breathing", "zen", "sudoku"),
        actionLabel = "Open Brain & Calm Lab",
        destination = GuideDestination.BRAIN_LAB
    )
)

@Composable
fun AppGuideScreen(
    onDismiss: () -> Unit,
    onNavigateDestination: (GuideDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(AppGuideCategory.ALL) }
    var expandedCardIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    BackHandler { onDismiss() }

    val filteredEntries = remember(searchQuery, selectedCategory) {
        val q = searchQuery.trim().lowercase()
        APP_GUIDE_CATALOG.filter { entry ->
            val matchesCat = selectedCategory == AppGuideCategory.ALL || entry.category == selectedCategory
            val matchesQuery = q.isBlank() ||
                entry.title.lowercase().contains(q) ||
                entry.summary.lowercase().contains(q) ||
                entry.quickTip.lowercase().contains(q) ||
                entry.details.lowercase().contains(q) ||
                entry.keywords.any { it.lowercase().contains(q) }
            matchesCat && matchesQuery
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("app_guide_screen"),
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
                                .testTag("app_guide_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Close App Guide",
                                tint = colors.inkBold
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "App Guide & Feature Finder",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Search workflows, formats & jump straight to any tool",
                                fontSize = 11.5.sp,
                                color = colors.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = colors.tealLight
                        ) {
                            Text(
                                text = "${filteredEntries.size}/${APP_GUIDE_CATALOG.size}",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.teal,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Search Field
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = "Search features, OMR, Pomodoro, Aiken, SM-2...",
                                fontSize = 13.sp,
                                color = colors.muted
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = colors.teal)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search", tint = colors.muted)
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.teal,
                            unfocusedBorderColor = colors.border,
                            focusedTextColor = colors.inkBold,
                            unfocusedTextColor = colors.inkBold
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .testTag("app_guide_search_input")
                    )

                    // Category Filter Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppGuideCategory.entries.forEach { category ->
                            val isSelected = selectedCategory == category
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedCategory = category },
                                label = {
                                    Text(
                                        text = category.label,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colors.tealLight,
                                    selectedLabelColor = colors.teal,
                                    labelColor = colors.ink
                                ),
                                modifier = Modifier.testTag("guide_cat_${category.name.lowercase()}")
                            )
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
            if (filteredEntries.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp)
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = null,
                        tint = colors.muted,
                        modifier = Modifier.size(44.dp)
                    )
                    Text(
                        text = "No matching features found",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkBold
                    )
                    Text(
                        text = "Try searching keywords like \"timer\", \"flashcard\", \"OMR\", \"DND\", or \"backup\".",
                        fontSize = 13.sp,
                        color = colors.muted
                    )
                    OutlinedButton(
                        onClick = {
                            searchQuery = ""
                            selectedCategory = AppGuideCategory.ALL
                        },
                        border = BorderStroke(1.dp, colors.teal),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Reset Filters", color = colors.teal, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 760.dp),
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredEntries, key = { it.id }) { entry ->
                        val isExpanded = entry.id in expandedCardIds
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("guide_card_${entry.id}"),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = colors.surface),
                            border = BorderStroke(1.dp, colors.border)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = colors.tealLight,
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = entry.icon,
                                                contentDescription = null,
                                                tint = colors.teal,
                                                modifier = Modifier.size(21.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = entry.title,
                                            fontSize = 14.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.inkBold
                                        )
                                        Text(
                                            text = entry.category.label,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.teal
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            expandedCardIds = if (isExpanded) {
                                                expandedCardIds - entry.id
                                            } else {
                                                expandedCardIds + entry.id
                                            }
                                        },
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = if (isExpanded) "Hide Details" else "Show Details",
                                            tint = colors.muted
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = entry.summary,
                                    fontSize = 12.5.sp,
                                    color = colors.ink,
                                    lineHeight = 18.sp
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = colors.surfaceVariant,
                                    border = BorderStroke(0.8.dp, colors.borderLight),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lightbulb,
                                            contentDescription = null,
                                            tint = colors.amber,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = entry.quickTip,
                                            fontSize = 11.5.sp,
                                            color = colors.inkBold,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }

                                AnimatedVisibility(visible = isExpanded) {
                                    Column {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = entry.details,
                                            fontSize = 12.sp,
                                            color = colors.muted,
                                            lineHeight = 17.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Button(
                                        onClick = {
                                            onDismiss()
                                            onNavigateDestination(entry.destination)
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = colors.teal,
                                            contentColor = Color.White
                                        ),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                        modifier = Modifier
                                            .heightIn(min = 44.dp)
                                            .testTag("guide_open_${entry.id}")
                                    ) {
                                        Text(
                                            text = entry.actionLabel,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
fun DismissibleFormatTipBanner(
    tipId: String,
    title: String,
    body: String,
    isDismissed: Boolean,
    onDismissTip: (String) -> Unit,
    onOpenGuide: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (isDismissed) return
    val colors = ExamTheme.colors
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("format_tip_banner_$tipId"),
        shape = RoundedCornerShape(12.dp),
        color = colors.tealLight.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, colors.teal.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = colors.teal,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { onDismissTip(tipId) },
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("dismiss_format_tip_$tipId")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss tip",
                        tint = colors.muted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Text(
                text = body,
                fontSize = 11.5.sp,
                color = colors.ink,
                lineHeight = 16.sp
            )
            if (onOpenGuide != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onOpenGuide,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.heightIn(min = 34.dp)
                    ) {
                        Text(
                            text = "Open App Guide",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.teal
                        )
                    }
                }
            }
        }
    }
}
