package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.StudyNoteEntity
import com.example.model.FlashcardSrsState
import com.example.model.FolderItem
import com.example.model.KnowledgeCollectionType
import com.example.ui.theme.ExamTheme
import com.example.util.SpacedRepetitionEngine
import com.example.util.StudyNoteBlock
import com.example.util.StudyNoteParser

private data class KnowledgeCourseBucket(
    val courseName: String,
    val subjects: List<KnowledgeSubjectBucket>,
    val totalNotesCount: Int,
    val totalUnitsCount: Int,
    val totalMasteredCount: Int,
    val dueSrsCount: Int
)

private data class KnowledgeSubjectBucket(
    val courseName: String,
    val subjectName: String,
    val notes: List<StudyNoteEntity>,
    val totalUnitsCount: Int,
    val totalMasteredCount: Int,
    val dueSrsCount: Int
)

@Composable
fun SuggestedNextKnowledgeCard(
    note: StudyNoteEntity,
    unitCount: Int,
    masteredCount: Int,
    dueCount: Int,
    onOpenStudy: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    val labelText = when {
        dueCount > 0 -> "DUE FOR REVISION ($dueCount CARDS)"
        masteredCount in 1 until unitCount -> "CONTINUE STUDYING"
        else -> "SUGGESTED NEXT STUDY"
    }
    val subLine = buildString {
        append("${note.effectiveSubjectLabel()} • ")
        if (unitCount > 0) append("$masteredCount/$unitCount Mastered") else append("${note.rawContent.lines().size} Lines")
        append(" • ${note.effectiveBadgeLabel()}")
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.navyBanner),
        modifier = modifier
            .fillMaxWidth()
            .testTag("suggested_next_knowledge_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = labelText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp,
                    color = colors.navyBannerText.copy(alpha = 0.85f)
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = note.title,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.navyBannerText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subLine,
                    fontSize = 11.sp,
                    color = colors.navyBannerText.copy(alpha = 0.82f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Button(
                onClick = onOpenStudy,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.teal,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("suggested_next_knowledge_cta")
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (dueCount > 0) "Review" else "Study",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Course -> Subject -> Knowledge Sets hierarchical browser with Suggested Next Study,
 * inline Course/Subject creation & renaming, and unified search.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KnowledgeCourseBrowser(
    notes: List<StudyNoteEntity>,
    folders: List<FolderItem>,
    registeredCoursesMap: Map<String, List<String>>,
    selectedCourse: String?,
    selectedSubject: String?,
    onSelectCourse: (String?) -> Unit,
    onSelectSubject: (String?) -> Unit,
    onCreateCourse: (String) -> Unit,
    onAddSubjectToCourse: (courseName: String, subjectName: String) -> Unit,
    onRenameCourse: (oldName: String, newName: String) -> Unit,
    onRenameSubject: (courseName: String, oldSubject: String, newSubject: String) -> Unit,
    onAssignNoteCourseSubject: (noteId: Long, courseName: String, subjectName: String) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onAddKnowledgeSet: (courseName: String, subjectName: String) -> Unit,
    onOpenNote: (StudyNoteEntity) -> Unit,
    onEditNote: (StudyNoteEntity) -> Unit,
    onDeleteNote: (StudyNoteEntity) -> Unit,
    linkedPaperTitles: Map<Long, String> = emptyMap(),
    flashcardSrsByNote: Map<Long, Map<Int, FlashcardSrsState>> = emptyMap(),
    onCompileCourseActiveRecall: ((courseName: String, subjectName: String) -> Unit)? = null,
    isParentOverlayBlocking: Boolean = false
) {
    val colors = ExamTheme.colors
    val context = LocalContext.current

    var showCreateCourseDialog by remember { mutableStateOf(false) }
    var newCourseInput by remember { mutableStateOf("") }

    var showAddSubjectDialogForCourse by remember { mutableStateOf<String?>(null) }
    var newSubjectInput by remember { mutableStateOf("") }

    var renameCourseTarget by remember { mutableStateOf<String?>(null) }
    var renameCourseInput by remember { mutableStateOf("") }

    var renameSubjectTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var renameSubjectInput by remember { mutableStateOf("") }

    var moveNoteTarget by remember { mutableStateOf<StudyNoteEntity?>(null) }
    var moveNoteCourseInput by remember { mutableStateOf("") }
    var moveNoteSubjectInput by remember { mutableStateOf("") }

    BackHandler(
        enabled = !isParentOverlayBlocking &&
            !showCreateCourseDialog &&
            showAddSubjectDialogForCourse == null &&
            renameCourseTarget == null &&
            renameSubjectTarget == null &&
            moveNoteTarget == null &&
            (searchQuery.isNotBlank() || selectedSubject != null || selectedCourse != null)
    ) {
        when {
            searchQuery.isNotBlank() -> onSearchQueryChange("")
            selectedSubject != null -> onSelectSubject(null)
            selectedCourse != null -> onSelectCourse(null)
        }
    }

    val folderMap = remember(folders) { folders.associateBy { it.id } }
    fun rootFolderName(folderId: String?): String {
        if (folderId.isNullOrBlank() || folderId == "all") return ""
        var cur = folderMap[folderId] ?: return ""
        var guard = 0
        while (cur.parentId != null && guard < 16) {
            cur = folderMap[cur.parentId] ?: break
            guard++
        }
        return cur.name.trim()
    }

    // Per-note parsed metrics memoized once
    val noteMetrics = remember(notes, flashcardSrsByNote) {
        val now = System.currentTimeMillis()
        notes.associate { n ->
            val rule = n.parsedFormatRule()
            val units = StudyNoteParser.parseBlocks(n.rawContent, rule).filter {
                it is StudyNoteBlock.OneLiner || it is StudyNoteBlock.MultiLinerCard
            }
            val mastered = n.getMasteredIndices().size
            val srsMap = flashcardSrsByNote[n.id].orEmpty()
            val due = if (n.resolvedCollectionType() == KnowledgeCollectionType.FLASHCARDS || srsMap.isNotEmpty()) {
                SpacedRepetitionEngine.computeDeckStats(units, srsMap, now).actionableCount
            } else {
                0
            }
            n.id to Triple(units.size, mastered, due)
        }
    }

    // Group registered Courses + notes into Course -> Subject -> Notes tree
    val courseBuckets = remember(notes, registeredCoursesMap, folderMap, noteMetrics) {
        val grouped = linkedMapOf<String, LinkedHashMap<String, MutableList<StudyNoteEntity>>>()

        registeredCoursesMap.forEach { (course, subs) ->
            val cName = course.trim()
            if (cName.isNotBlank()) {
                val subMap = grouped.getOrPut(cName) { linkedMapOf() }
                subs.forEach { sub ->
                    val sName = sub.trim()
                    if (sName.isNotBlank()) {
                        subMap.getOrPut(sName) { mutableListOf() }
                    }
                }
            }
        }

        notes.forEach { note ->
            val resolvedCourse = note.effectiveCourseLabel(fallbackFolderRoot = rootFolderName(note.folderId))
            val resolvedSubject = note.effectiveSubjectLabel()
            val courseKey = grouped.keys.firstOrNull { it.equals(resolvedCourse, ignoreCase = true) } ?: resolvedCourse
            val subMap = grouped.getOrPut(courseKey) { linkedMapOf() }
            val subjectKey = subMap.keys.firstOrNull { it.equals(resolvedSubject, ignoreCase = true) } ?: resolvedSubject
            subMap.getOrPut(subjectKey) { mutableListOf() }.add(note)
        }

        grouped.map { (courseName, subMap) ->
            val subjects = subMap.map { (subName, subNotes) ->
                val sortedNotes = subNotes.sortedByDescending { it.updatedAt }
                val totalUnits = sortedNotes.sumOf { noteMetrics[it.id]?.first ?: 0 }
                val totalMastered = sortedNotes.sumOf { noteMetrics[it.id]?.second ?: 0 }
                val dueSrs = sortedNotes.sumOf { noteMetrics[it.id]?.third ?: 0 }
                KnowledgeSubjectBucket(
                    courseName = courseName,
                    subjectName = subName,
                    notes = sortedNotes,
                    totalUnitsCount = totalUnits,
                    totalMasteredCount = totalMastered,
                    dueSrsCount = dueSrs
                )
            }.sortedBy { it.subjectName.lowercase() }

            KnowledgeCourseBucket(
                courseName = courseName,
                subjects = subjects,
                totalNotesCount = subjects.sumOf { it.notes.size },
                totalUnitsCount = subjects.sumOf { it.totalUnitsCount },
                totalMasteredCount = subjects.sumOf { it.totalMasteredCount },
                dueSrsCount = subjects.sumOf { it.dueSrsCount }
            )
        }.sortedByDescending { it.totalNotesCount > 0 }
    }

    val activeCourseBucket = remember(courseBuckets, selectedCourse) {
        selectedCourse?.let { sel -> courseBuckets.firstOrNull { it.courseName.equals(sel, ignoreCase = true) } }
    }
    val activeSubjectBucket = remember(activeCourseBucket, selectedSubject) {
        if (activeCourseBucket != null && selectedSubject != null) {
            activeCourseBucket.subjects.firstOrNull { it.subjectName.equals(selectedSubject, ignoreCase = true) }
        } else null
    }

    // Scope-aware Suggested Next Study note
    val scopedNotes = remember(notes, activeCourseBucket, activeSubjectBucket) {
        when {
            activeSubjectBucket != null -> activeSubjectBucket.notes
            activeCourseBucket != null -> activeCourseBucket.subjects.flatMap { it.notes }
            else -> notes
        }
    }
    val suggestedNote = remember(scopedNotes, noteMetrics) {
        scopedNotes.firstOrNull { (noteMetrics[it.id]?.third ?: 0) > 0 }
            ?: scopedNotes.firstOrNull {
                val (u, m, _) = noteMetrics[it.id] ?: Triple(0, 0, 0)
                u > 0 && m < u
            }
            ?: scopedNotes.firstOrNull()
    }

    val searchMatchingNotes = remember(notes, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else notes.filter { n ->
            n.title.contains(searchQuery, ignoreCase = true) ||
                n.subject.contains(searchQuery, ignoreCase = true) ||
                n.course.contains(searchQuery, ignoreCase = true) ||
                n.customLabel.contains(searchQuery, ignoreCase = true) ||
                n.rawContent.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("knowledge_course_browser"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search courses, subjects, or knowledge sets...", fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(16.dp)) },
            trailingIcon = if (searchQuery.isNotBlank()) {
                {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search", modifier = Modifier.size(16.dp))
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
                .testTag("knowledge_browser_search_input")
        )

        // Breadcrumb Navigation Header when inside Course or Subject
        if (activeCourseBucket != null && searchQuery.isBlank()) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(0.8.dp, colors.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (activeSubjectBucket != null) onSelectSubject(null) else onSelectCourse(null)
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("knowledge_browser_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Step back", tint = colors.inkBold)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (activeSubjectBucket != null) {
                                "${activeCourseBucket.courseName}  ›  ${activeSubjectBucket.subjectName}"
                            } else {
                                "Course: ${activeCourseBucket.courseName}"
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (activeSubjectBucket != null) {
                                "${activeSubjectBucket.notes.size} Knowledge Set(s) • ${activeSubjectBucket.totalUnitsCount} Units"
                            } else {
                                "${activeCourseBucket.subjects.size} Subject(s) • ${activeCourseBucket.totalNotesCount} Knowledge Set(s)"
                            },
                            fontSize = 11.sp,
                            color = colors.inkMuted
                        )
                    }
                }
            }
        }

        // Action Row tailored to current Hierarchy Depth
        if (searchQuery.isBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when {
                    activeCourseBucket == null -> {
                        OutlinedButton(
                            onClick = {
                                newCourseInput = ""
                                showCreateCourseDialog = true
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, colors.teal.copy(alpha = 0.55f)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("create_knowledge_course_button")
                        ) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = colors.teal, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("New Course", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.tealDark)
                        }
                        Button(
                            onClick = { onAddKnowledgeSet("", "") },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("add_study_note_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("New Note Set", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    activeSubjectBucket == null -> {
                        OutlinedButton(
                            onClick = {
                                newSubjectInput = ""
                                showAddSubjectDialogForCourse = activeCourseBucket.courseName
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, colors.teal.copy(alpha = 0.55f)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("create_knowledge_subject_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = colors.teal, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("New Subject", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.tealDark)
                        }
                        Button(
                            onClick = { onAddKnowledgeSet(activeCourseBucket.courseName, "") },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("add_study_note_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("New Note Set", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    else -> {
                        Button(
                            onClick = {
                                onAddKnowledgeSet(activeCourseBucket.courseName, activeSubjectBucket.subjectName)
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("add_study_note_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("New Knowledge Set in ${activeSubjectBucket.subjectName}", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        // Compile Active Recall CTA (when inside a subject or folder context)
        if (onCompileCourseActiveRecall != null && searchQuery.isBlank() && activeCourseBucket != null) {
            FilledTonalButton(
                onClick = {
                    onCompileCourseActiveRecall(
                        activeCourseBucket.courseName,
                        activeSubjectBucket?.subjectName ?: activeCourseBucket.courseName
                    )
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = colors.teal.copy(alpha = 0.14f),
                    contentColor = if (colors.isDark) colors.teal else colors.tealDark
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("compile_course_active_recall_button")
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (activeSubjectBucket != null) {
                        "Compile Master Recall: ${activeSubjectBucket.subjectName}"
                    } else {
                        "Compile Master Recall: ${activeCourseBucket.courseName}"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Suggested Next Study Hero Card (matches SuggestedNextTestCard in Exam Folders!)
        if (searchQuery.isBlank() && suggestedNote != null) {
            val (u, m, d) = noteMetrics[suggestedNote.id] ?: Triple(0, 0, 0)
            SuggestedNextKnowledgeCard(
                note = suggestedNote,
                unitCount = u,
                masteredCount = m,
                dueCount = d,
                onOpenStudy = { onOpenNote(suggestedNote) }
            )
        }

        // Main Hierarchy Content
        when {
            searchQuery.isNotBlank() -> {
                if (searchMatchingNotes.isEmpty()) {
                    EmptyKnowledgeBrowserCard(
                        title = "No matching Knowledge Sets",
                        subtitle = "Try searching another keyword, subject, or course name.",
                        ctaText = "Create New Knowledge Set",
                        onAction = { onAddKnowledgeSet(selectedCourse.orEmpty(), selectedSubject.orEmpty()) }
                    )
                } else {
                    searchMatchingNotes.forEach { note ->
                        KnowledgeNoteWithMoveRow(
                            note = note,
                            linkedPaperTitle = note.linkedPaperId?.let { linkedPaperTitles[it] },
                            srsMap = flashcardSrsByNote[note.id].orEmpty(),
                            onOpen = { onOpenNote(note) },
                            onEdit = { onEditNote(note) },
                            onPrint = { printStudyNoteAsPdf(context, note) },
                            onDelete = { onDeleteNote(note) },
                            onMoveCourseSubject = {
                                moveNoteTarget = note
                                moveNoteCourseInput = note.effectiveCourseLabel(rootFolderName(note.folderId))
                                moveNoteSubjectInput = note.effectiveSubjectLabel()
                            }
                        )
                    }
                }
            }
            activeCourseBucket == null -> {
                // LEVEL 0: COURSES LIST
                if (courseBuckets.isEmpty()) {
                    EmptyKnowledgeBrowserCard(
                        title = "Organize by Course → Subject → Notes",
                        subtitle = "Start by creating a Course (e.g., UPSC Prelims or SSC CGL), add Subjects inside it, and build Study-first Knowledge Sets.",
                        ctaText = "Create First Course",
                        onAction = {
                            newCourseInput = ""
                            showCreateCourseDialog = true
                        }
                    )
                } else {
                    Text(
                        text = "COURSES (${courseBuckets.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.inkMuted,
                        letterSpacing = 0.6.sp
                    )
                    courseBuckets.forEach { bucket ->
                        val frac = if (bucket.totalUnitsCount > 0) {
                            (bucket.totalMasteredCount.toFloat() / bucket.totalUnitsCount.toFloat()).coerceIn(0f, 1f)
                        } else 0f
                        Card(
                            shape = RoundedCornerShape(13.dp),
                            colors = CardDefaults.cardColors(containerColor = colors.surface),
                            border = BorderStroke(0.9.dp, colors.border),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectCourse(bucket.courseName) }
                                .testTag("knowledge_course_card_${bucket.courseName}")
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
                                            Icon(Icons.Default.School, contentDescription = null, tint = colors.tealDark, modifier = Modifier.size(21.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = bucket.courseName,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.inkBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${bucket.subjects.size} Subject(s) • ${bucket.totalNotesCount} Set(s) • ${bucket.totalUnitsCount} Units",
                                            fontSize = 11.5.sp,
                                            color = colors.inkMuted
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            renameCourseTarget = bucket.courseName
                                            renameCourseInput = bucket.courseName
                                        },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Rename course", tint = colors.inkMuted, modifier = Modifier.size(18.dp))
                                    }
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = "Open course",
                                        tint = colors.teal,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                if (bucket.totalUnitsCount > 0 || bucket.dueSrsCount > 0) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LinearProgressIndicator(
                                        progress = { frac },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(5.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = colors.teal,
                                        trackColor = colors.surfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(5.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${bucket.totalMasteredCount}/${bucket.totalUnitsCount} mastered",
                                            fontSize = 10.5.sp,
                                            color = colors.green,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (bucket.dueSrsCount > 0) {
                                            Text(
                                                text = "${bucket.dueSrsCount} SRS due",
                                                fontSize = 10.5.sp,
                                                color = colors.amber,
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
            activeSubjectBucket == null -> {
                // LEVEL 1: SUBJECTS INSIDE SELECTED COURSE
                if (activeCourseBucket.subjects.isEmpty()) {
                    EmptyKnowledgeBrowserCard(
                        title = "Add Subjects to ${activeCourseBucket.courseName}",
                        subtitle = "Group your Knowledge Sets neatly by subject (e.g., Polity, Economy, Current Affairs).",
                        ctaText = "Add First Subject",
                        onAction = {
                            newSubjectInput = ""
                            showAddSubjectDialogForCourse = activeCourseBucket.courseName
                        }
                    )
                } else {
                    Text(
                        text = "SUBJECTS IN ${activeCourseBucket.courseName.uppercase()} (${activeCourseBucket.subjects.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.inkMuted,
                        letterSpacing = 0.6.sp
                    )
                    activeCourseBucket.subjects.forEach { subBucket ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = colors.surface),
                            border = BorderStroke(0.9.dp, colors.border),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectSubject(subBucket.subjectName) }
                                .testTag("knowledge_subject_card_${subBucket.subjectName}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(9.dp),
                                    color = colors.blueLight,
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Folder, contentDescription = null, tint = colors.tealDark, modifier = Modifier.size(20.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = subBucket.subjectName,
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.inkBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${subBucket.notes.size} Note Set(s) • ${subBucket.totalMasteredCount}/${subBucket.totalUnitsCount} Mastered",
                                        fontSize = 11.5.sp,
                                        color = colors.inkMuted
                                    )
                                }
                                if (subBucket.dueSrsCount > 0) {
                                    Surface(shape = RoundedCornerShape(6.dp), color = colors.amberLight) {
                                        Text(
                                            text = "${subBucket.dueSrsCount} due",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.amber,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        renameSubjectTarget = activeCourseBucket.courseName to subBucket.subjectName
                                        renameSubjectInput = subBucket.subjectName
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Rename subject", tint = colors.inkMuted, modifier = Modifier.size(18.dp))
                                }
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Open subject",
                                    tint = colors.teal,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
            else -> {
                // LEVEL 2: NOTES INSIDE SELECTED COURSE -> SUBJECT
                if (activeSubjectBucket.notes.isEmpty()) {
                    EmptyKnowledgeBrowserCard(
                        title = "No Notes in ${activeSubjectBucket.subjectName}",
                        subtitle = "Create a Study-first Knowledge Set, Q&A pack, Formula Sheet, or Flashcard deck here.",
                        ctaText = "Create Knowledge Set",
                        onAction = {
                            onAddKnowledgeSet(activeCourseBucket.courseName, activeSubjectBucket.subjectName)
                        }
                    )
                } else {
                    activeSubjectBucket.notes.forEach { note ->
                        KnowledgeNoteWithMoveRow(
                            note = note,
                            linkedPaperTitle = note.linkedPaperId?.let { linkedPaperTitles[it] },
                            srsMap = flashcardSrsByNote[note.id].orEmpty(),
                            onOpen = { onOpenNote(note) },
                            onEdit = { onEditNote(note) },
                            onPrint = { printStudyNoteAsPdf(context, note) },
                            onDelete = { onDeleteNote(note) },
                            onMoveCourseSubject = {
                                moveNoteTarget = note
                                moveNoteCourseInput = note.effectiveCourseLabel(activeCourseBucket.courseName)
                                moveNoteSubjectInput = note.effectiveSubjectLabel()
                            }
                        )
                    }
                }
            }
        }
    }

    // Create Course Dialog
    if (showCreateCourseDialog) {
        AlertDialog(
            onDismissRequest = { showCreateCourseDialog = false },
            containerColor = colors.surface,
            title = { Text("Create Knowledge Course", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newCourseInput,
                    onValueChange = { newCourseInput = it },
                    label = { Text("Course Name") },
                    placeholder = { Text("e.g., UPSC CSE 2026, SSC CGL, NEET") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_course_name_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newCourseInput.trim()
                        if (trimmed.isNotBlank()) {
                            onCreateCourse(trimmed)
                            showCreateCourseDialog = false
                        }
                    },
                    enabled = newCourseInput.trim().isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Create Course")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCreateCourseDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text("Cancel") }
            }
        )
    }

    // Add Subject Dialog
    showAddSubjectDialogForCourse?.let { targetCourse ->
        AlertDialog(
            onDismissRequest = { showAddSubjectDialogForCourse = null },
            containerColor = colors.surface,
            title = { Text("Add Subject to $targetCourse", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newSubjectInput,
                    onValueChange = { newSubjectInput = it },
                    label = { Text("Subject Name") },
                    placeholder = { Text("e.g., Indian Polity, Modern History") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_subject_name_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newSubjectInput.trim()
                        if (trimmed.isNotBlank()) {
                            onAddSubjectToCourse(targetCourse, trimmed)
                            showAddSubjectDialogForCourse = null
                        }
                    },
                    enabled = newSubjectInput.trim().isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Add Subject")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAddSubjectDialogForCourse = null },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text("Cancel") }
            }
        )
    }

    // Rename Course Dialog
    renameCourseTarget?.let { oldName ->
        AlertDialog(
            onDismissRequest = { renameCourseTarget = null },
            containerColor = colors.surface,
            title = { Text("Rename Course", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameCourseInput,
                    onValueChange = { renameCourseInput = it },
                    label = { Text("Course Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRenameCourse(oldName, renameCourseInput.trim())
                        renameCourseTarget = null
                    },
                    enabled = renameCourseInput.trim().isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameCourseTarget = null }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Cancel")
                }
            }
        )
    }

    // Rename Subject Dialog
    renameSubjectTarget?.let { (cName, oldSub) ->
        AlertDialog(
            onDismissRequest = { renameSubjectTarget = null },
            containerColor = colors.surface,
            title = { Text("Rename Subject", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameSubjectInput,
                    onValueChange = { renameSubjectInput = it },
                    label = { Text("Subject Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRenameSubject(cName, oldSub, renameSubjectInput.trim())
                        renameSubjectTarget = null
                    },
                    enabled = renameSubjectInput.trim().isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameSubjectTarget = null }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Cancel")
                }
            }
        )
    }

    // Move Note to Course / Subject Dialog
    moveNoteTarget?.let { targetNote ->
        var showCourseMenu by remember { mutableStateOf(false) }
        val existingCourseNames = remember(courseBuckets) { courseBuckets.map { it.courseName } }
        AlertDialog(
            onDismissRequest = { moveNoteTarget = null },
            containerColor = colors.surface,
            title = { Text("Assign Course & Subject", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = targetNote.title,
                        fontSize = 12.sp,
                        color = colors.inkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = moveNoteCourseInput,
                            onValueChange = { moveNoteCourseInput = it },
                            label = { Text("Course") },
                            singleLine = true,
                            trailingIcon = if (existingCourseNames.isNotEmpty()) {
                                {
                                    TextButton(onClick = { showCourseMenu = true }) {
                                        Text("Pick", fontSize = 11.sp, color = colors.teal)
                                    }
                                }
                            } else null,
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(expanded = showCourseMenu, onDismissRequest = { showCourseMenu = false }) {
                            existingCourseNames.forEach { cName ->
                                DropdownMenuItem(
                                    text = { Text(cName) },
                                    onClick = {
                                        moveNoteCourseInput = cName
                                        showCourseMenu = false
                                    }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = moveNoteSubjectInput,
                        onValueChange = { moveNoteSubjectInput = it },
                        label = { Text("Subject") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAssignNoteCourseSubject(targetNote.id, moveNoteCourseInput, moveNoteSubjectInput)
                        moveNoteTarget = null
                    },
                    enabled = moveNoteSubjectInput.trim().isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text("Move Note") }
            },
            dismissButton = {
                TextButton(onClick = { moveNoteTarget = null }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun KnowledgeNoteWithMoveRow(
    note: StudyNoteEntity,
    linkedPaperTitle: String?,
    srsMap: Map<Int, FlashcardSrsState>,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onPrint: () -> Unit,
    onDelete: () -> Unit,
    onMoveCourseSubject: () -> Unit
) {
    val colors = ExamTheme.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        StudyNoteCard(
            note = note,
            linkedPaperTitle = linkedPaperTitle,
            srsMap = srsMap,
            onOpen = onOpen,
            onEdit = onEdit,
            onPrint = onPrint,
            onDelete = onDelete
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = onMoveCourseSubject,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                modifier = Modifier.heightIn(min = 36.dp)
            ) {
                Icon(Icons.Default.DriveFileMove, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.inkMuted)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Course / Subject", fontSize = 11.sp, color = colors.inkMuted)
            }
        }
    }
}

@Composable
private fun EmptyKnowledgeBrowserCard(
    title: String,
    subtitle: String,
    ctaText: String,
    onAction: () -> Unit
) {
    val colors = ExamTheme.colors
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(0.8.dp, colors.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                tint = colors.teal,
                modifier = Modifier.size(38.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = colors.inkBold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = colors.inkMuted,
                textAlign = TextAlign.Center,
                lineHeight = 17.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(ctaText, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
