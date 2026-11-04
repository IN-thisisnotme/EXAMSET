package com.example.ui.screens

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AttemptEntity
import com.example.data.PaperEntity
import com.example.model.FolderItem
import com.example.ui.theme.ExamTheme
import com.example.util.ExamNumberGenerator
import java.util.Locale

internal fun parseHexColorSafe(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        Color(0xFF0284C7)
    }
}

/**
 * Root Examination Folder Card featuring:
 * - Emblem / Seal badge
 * - Examination folder name
 * - Total available tests recursive count
 * - Completed sets and monthly activity counters
 * - Horizontal progress bar (e.g. 24 / 60)
 * - Three-dots action dropdown menu (Rename, Duplicate, Recolor, Delete)
 */
@Composable
fun RootExamFolderCard(
    folder: FolderItem,
    totalAvailableSets: Int,
    completedSets: Int,
    monthAttemptedCount: Int,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onRecolor: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = ExamTheme.colors
    var showMenu by remember { mutableStateOf(false) }
    val progress = if (totalAvailableSets > 0) completedSets.toFloat() / totalAvailableSets.toFloat() else 0f
    val folderColor = parseHexColorSafe(folder.color)

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.border),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Row 1: Emblem Badge + Folder Title + Action Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Emblem / Seal Box
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = folderColor.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, folderColor.copy(alpha = 0.35f)),
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.School,
                                contentDescription = null,
                                tint = folderColor,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = folder.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Available in English, Hindi",
                            fontSize = 11.5.sp,
                            color = colors.inkMuted
                        )
                    }
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Folder options",
                            tint = colors.inkMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename Folder") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                showMenu = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Duplicate Folder") },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            onClick = {
                                showMenu = false
                                onDuplicate()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Change Color") },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .background(folderColor, CircleShape)
                                )
                            },
                            onClick = {
                                showMenu = false
                                onRecolor()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete Folder", color = Color(0xFFDC2626)) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp)) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Row 2: Badges / Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF2563EB),
                    contentColor = Color.White
                ) {
                    Text(
                        text = "$totalAvailableSets Total Tests",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }

                if (completedSets > 0) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF16A34A).copy(alpha = 0.15f),
                        border = BorderStroke(0.7.dp, Color(0xFF16A34A))
                    ) {
                        Text(
                            text = "$completedSets Completed",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF15803D),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                if (monthAttemptedCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = colors.surfaceVariant,
                        border = BorderStroke(0.5.dp, colors.border)
                    ) {
                        Text(
                            text = "$monthAttemptedCount this month",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.inkMuted,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Row 3: Horizontal Progress Bar & Numerical count completed / total available
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Completion Progress",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.inkMuted
                )
                Text(
                    text = "$completedSets / $totalAvailableSets",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (completedSets > 0 && completedSets == totalAvailableSets) Color(0xFF16A34A) else colors.inkBold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Horizontal Progress bar
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = colors.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = progress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(
                                if (progress >= 1f) Color(0xFF16A34A) else Color(0xFF2563EB),
                                RoundedCornerShape(4.dp)
                            )
                    )
                }
            }
        }
    }
}

/**
 * Compact subfolder list row matching reference screenshots:
 * e.g. "37 Subject Test                >"
 *      "8 Full Test       2 Completed  >"
 */
@Composable
fun SubfolderCompactRow(
    subfolder: FolderItem,
    totalSets: Int,
    completedSets: Int,
    onClick: () -> Unit
) {
    val colors = ExamTheme.colors
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = colors.surface,
        border = BorderStroke(0.7.dp, colors.border.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "$totalSets ",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkBold
                )
                Text(
                    text = subfolder.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.inkBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (completedSets > 0) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "$completedSets Completed",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF059669),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Open Folder",
                    tint = colors.inkMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Clean Examination Test Set Card:
 * - Title, set number, subject tag
 * - Specs: questions count, marks, duration (e.g. "100 Questions • 100 Marks • 120 Mins")
 * - Status chips (Completed / In Progress / Not Attempted)
 * - Prominent Test Action Button (Start Test / Resume / Review Analysis)
 * - Zero commercial clutter (100% unlocked for direct testing)
 */
@Composable
fun PaperExamCard(
    paper: PaperEntity,
    isActive: Boolean,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    isCompleted: Boolean,
    isInProgress: Boolean,
    attemptScore: Double? = null,
    onOpenMaster: () -> Unit,
    onTakeTest: () -> Unit,
    onAssignFolder: () -> Unit,
    onExport: () -> Unit = {},
    onDelete: () -> Unit
) {
    val colors = ExamTheme.colors
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val durationMins = if (paper.durationMinutes > 0) paper.durationMinutes else if (paper.questionCount > 0) paper.questionCount else 60
    val totalMarks = if (paper.questionCount > 0) (paper.questionCount * paper.positiveMarks).toInt() else 100

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) colors.teal.copy(alpha = 0.06f) else colors.surface
        ),
        border = BorderStroke(
            if (isSelected) 1.8.dp else if (isActive) 1.5.dp else 0.8.dp,
            if (isSelected) colors.teal else if (isActive) Color(0xFF2563EB) else colors.border
        ),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelectionMode) Modifier.clickable { onToggleSelect() } else Modifier
            )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Row 1: Badges & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSelectionMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleSelect() },
                            colors = CheckboxDefaults.colors(
                                checkedColor = colors.teal,
                                uncheckedColor = colors.inkMuted
                            ),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Set Number Pill
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = colors.surfaceVariant,
                        border = BorderStroke(0.6.dp, colors.border)
                    ) {
                        Text(
                            text = ExamNumberGenerator.formatSetDisplayLabel(paper.setNumber),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.ink,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Subject Tag
                    if (paper.subject.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFEFF6FF),
                            border = BorderStroke(0.5.dp, Color(0xFFBFDBFE))
                        ) {
                            Text(
                                text = paper.subject,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2563EB),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Attempt Status Chip
                    if (isCompleted) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFDCFCE7),
                            border = BorderStroke(0.6.dp, Color(0xFF16A34A))
                        ) {
                            val scoreStr = attemptScore?.let { if (it % 1.0 == 0.0) it.toInt().toString() else String.format(Locale.US, "%.1f", it) }
                            Text(
                                text = if (scoreStr != null) "✓ Completed ($scoreStr pts)" else "✓ Completed",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF15803D),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    } else if (isInProgress) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFFEF3C7),
                            border = BorderStroke(0.6.dp, Color(0xFFD97706))
                        ) {
                            Text(
                                text = "⏳ In Progress",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB45309),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (!isSelectionMode) {
                        IconButton(
                            onClick = onExport,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Share Paper",
                                tint = colors.teal,
                                modifier = Modifier.size(15.dp)
                            )
                        }

                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete Paper",
                                tint = Color(0xFFEF4444).copy(alpha = 0.85f),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Row 2: Title
            Text(
                text = paper.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = colors.inkBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Row 3: Exam Specifications (Questions • Marks • Duration)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${paper.questionCount} Questions",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.inkMuted
                )
                Text("•", fontSize = 11.sp, color = colors.border)
                Text(
                    text = "$totalMarks Marks",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.inkMuted
                )
                Text("•", fontSize = 11.sp, color = colors.border)
                Text(
                    text = "$durationMins Mins",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.inkMuted
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Available in English, Hindi",
                fontSize = 11.sp,
                color = colors.inkMuted.copy(alpha = 0.85f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Row 4: Primary Action Button (Start / Resume / Review)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onTakeTest,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCompleted) Color(0xFF16A34A) else if (isInProgress) Color(0xFFD97706) else Color(0xFF2563EB),
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            isCompleted -> "Review Analysis"
                            isInProgress -> "Resume Test"
                            else -> "Start Test"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onOpenMaster,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.8.dp, colors.border),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier.heightIn(min = 38.dp)
                ) {
                    Text(
                        text = "Master View",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.inkBold
                    )
                }

                IconButton(
                    onClick = onAssignFolder,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DriveFileMove,
                        contentDescription = "Move to Folder",
                        tint = colors.inkMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Question Set", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = { Text("Are you sure you want to permanently delete \"${paper.title}\"? All questions and past attempts will be removed.", fontSize = 12.5.sp) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    }
                ) {
                    Text("Delete", color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Batch selection toolbar displayed when multiple test sets are selected.
 */
@Composable
fun BatchSelectionToolbar(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onExportClick: () -> Unit,
    onMoveClick: () -> Unit
) {
    val colors = ExamTheme.colors
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = colors.teal.copy(alpha = 0.12f),
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (selectedCount == 0) "Tap sets to select" else "$selectedCount selected",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.tealDark
                )
                Spacer(modifier = Modifier.width(6.dp))
                TextButton(
                    onClick = onSelectAll,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (selectedCount == totalCount && totalCount > 0) "Deselect All" else "Select All",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.tealDark
                    )
                }
            }

            if (selectedCount > 0) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onExportClick,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.teal),
                        border = BorderStroke(1.dp, colors.teal),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.heightIn(min = 34.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export ($selectedCount)", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }

                    Button(
                        onClick = onMoveClick,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.teal,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.heightIn(min = 34.dp)
                    ) {
                        Icon(Icons.Default.DriveFileMove, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Move", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    }
}

/**
 * Renders the Root Examination Library (when activeFolderId == "all")
 */
fun LazyListScope.renderRootExaminationLibrary(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isSelectionMode: Boolean,
    selectedPaperIds: Set<Long>,
    onToggleSelect: (Long) -> Unit,
    savedPapers: List<PaperEntity>,
    rootFolders: List<FolderItem>,
    folderSortOrder: FolderSortOrder,
    onSortOrderToggle: () -> Unit,
    calculateFolderRecursiveSets: (String) -> Pair<Int, Int>,
    calculateFolderMonthlyActivity: (String, Long) -> Int,
    currentMonthStart: Long,
    onOpenFolder: (String) -> Unit,
    onRenameFolder: (FolderItem) -> Unit,
    onDuplicateFolder: (FolderItem) -> Unit,
    onRecolorFolder: (FolderItem) -> Unit,
    onDeleteFolder: (FolderItem) -> Unit,
    onCreateNewFolder: () -> Unit,
    onOpenMaster: (PaperEntity) -> Unit,
    onTakeTest: (PaperEntity) -> Unit,
    onAssignFolder: (PaperEntity) -> Unit,
    onExportPaper: (PaperEntity) -> Unit,
    onDeletePaper: (PaperEntity) -> Unit,
    allAttempts: List<AttemptEntity>,
    currentPaperEntityId: Long?,
    onBatchExport: () -> Unit,
    onBatchMove: () -> Unit,
    onSelectAll: () -> Unit
) {
    // Search Bar for Exams and Sets
    item(key = "root_search_bar") {
        val colors = ExamTheme.colors
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search exams or test sets...", fontSize = 13.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = colors.inkMuted,
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search",
                            tint = colors.inkMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.navyBanner,
                unfocusedBorderColor = colors.border,
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }

    // Batch selection toolbar when active
    if (isSelectionMode) {
        item(key = "root_batch_toolbar") {
            BatchSelectionToolbar(
                selectedCount = selectedPaperIds.size,
                totalCount = savedPapers.size,
                onSelectAll = onSelectAll,
                onExportClick = onBatchExport,
                onMoveClick = onBatchMove
            )
        }
    }

    // Root Folders List
    val matchingRootFolders = rootFolders.filter { rf ->
        searchQuery.isBlank() || rf.name.contains(searchQuery, ignoreCase = true)
    }

    if (matchingRootFolders.isNotEmpty()) {
        item(key = "root_folders_header") {
            val colors = ExamTheme.colors
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Examination Folders",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkBold
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = colors.surfaceVariant,
                    border = BorderStroke(0.6.dp, colors.border),
                    modifier = Modifier.clickable { onSortOrderToggle() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.SortByAlpha,
                            contentDescription = "Sort folders",
                            tint = colors.inkMuted,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = folderSortOrder.label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.inkBold
                        )
                    }
                }
            }
        }

        val sortedRoots = when (folderSortOrder) {
            FolderSortOrder.DEFAULT -> matchingRootFolders
            FolderSortOrder.A_TO_Z -> matchingRootFolders.sortedBy { it.name.lowercase(Locale.getDefault()) }
            FolderSortOrder.MOST_SETS -> matchingRootFolders.sortedByDescending { calculateFolderRecursiveSets(it.id).first }
        }

        items(sortedRoots, key = { "root_${it.id}" }) { folder ->
            val (totalSets, completedSets) = calculateFolderRecursiveSets(folder.id)
            val monthCount = calculateFolderMonthlyActivity(folder.id, currentMonthStart)
            RootExamFolderCard(
                folder = folder,
                totalAvailableSets = totalSets,
                completedSets = completedSets,
                monthAttemptedCount = monthCount,
                onClick = { onOpenFolder(folder.id) },
                onRename = { onRenameFolder(folder) },
                onDuplicate = { onDuplicateFolder(folder) },
                onRecolor = { onRecolorFolder(folder) },
                onDelete = { onDeleteFolder(folder) }
            )
        }
    }

    // Unassigned Papers Section (if any papers don't belong to any folder)
    val unfiledPapers = savedPapers.filter { it.folderId == null && (searchQuery.isBlank() || it.title.contains(searchQuery, ignoreCase = true)) }
    if (unfiledPapers.isNotEmpty()) {
        item(key = "root_unassigned_header") {
            val colors = ExamTheme.colors
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Unassigned Test Sets (${unfiledPapers.size})",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = colors.inkBold
            )
        }

        items(unfiledPapers, key = { "unassigned_${it.id}" }) { paper ->
            val paperAttempts = allAttempts.filter { it.paperId == paper.id }
            val isCompleted = paperAttempts.any { it.isSubmitted }
            val isInProgress = !isCompleted && paperAttempts.isNotEmpty()
            val lastScore = paperAttempts.maxByOrNull { it.submittedAt ?: it.createdAt }?.score

            PaperExamCard(
                paper = paper,
                isActive = paper.id == currentPaperEntityId,
                isSelectionMode = isSelectionMode,
                isSelected = paper.id in selectedPaperIds,
                onToggleSelect = { onToggleSelect(paper.id) },
                isCompleted = isCompleted,
                isInProgress = isInProgress,
                attemptScore = lastScore,
                onOpenMaster = { onOpenMaster(paper) },
                onTakeTest = { onTakeTest(paper) },
                onAssignFolder = { onAssignFolder(paper) },
                onExport = { onExportPaper(paper) },
                onDelete = { onDeletePaper(paper) }
            )
        }
    }

    if (matchingRootFolders.isEmpty() && unfiledPapers.isEmpty()) {
        item(key = "root_empty_state") {
            val colors = ExamTheme.colors
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(0.8.dp, colors.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = colors.teal.copy(alpha = 0.7f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) "No matching examinations found" else "No Examination Folders Yet",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) "Try searching for a different keyword" else "Create your first examination folder (e.g. 'UKPSC JE Civil 2023', 'SSC JE 2026') to organize test sets.",
                        fontSize = 12.sp,
                        color = colors.inkMuted,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onCreateNewFolder,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primaryButtonBg,
                            contentColor = colors.primaryButtonText
                        )
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("+ Create Examination Folder", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Renders the inside of an examination folder or subfolder (activeFolderId != "all")
 */
fun LazyListScope.renderFolderDetailContents(
    breadcrumbPath: List<FolderItem>,
    onNavigateToFolder: (String) -> Unit,
    onShowQuickJump: () -> Unit,
    showQuickJumpDialog: Boolean,
    isSelectionMode: Boolean,
    selectedPaperIds: Set<Long>,
    onToggleSelect: (Long) -> Unit,
    visibleSubfolders: List<FolderItem>,
    calculateFolderRecursiveSets: (String) -> Pair<Int, Int>,
    sortedFilteredPapers: List<PaperEntity>,
    currentPaperEntityId: Long?,
    allAttempts: List<AttemptEntity>,
    onOpenMaster: (PaperEntity) -> Unit,
    onTakeTest: (PaperEntity) -> Unit,
    onAssignFolder: (PaperEntity) -> Unit,
    onExportPaper: (PaperEntity) -> Unit,
    onDeletePaper: (PaperEntity) -> Unit,
    onAddSubFolder: () -> Unit,
    onCreateNewTestSet: () -> Unit,
    onBatchExport: () -> Unit,
    onBatchMove: () -> Unit,
    onSelectAll: () -> Unit
) {
    // Modern Breadcrumb Navigation Bar
    item(key = "folder_breadcrumbs") {
        val colors = ExamTheme.colors
        Card(
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant.copy(alpha = 0.5f)),
            border = BorderStroke(0.6.dp, colors.border)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Root / All Folders pill
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Transparent,
                        modifier = Modifier.clickable { onNavigateToFolder("all") }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = null,
                                tint = colors.inkMuted,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "All Folders",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.ink
                            )
                        }
                    }
                    // Ancestor Breadcrumbs
                    breadcrumbPath.forEachIndexed { index, crumb ->
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = colors.inkMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        val isLast = index == breadcrumbPath.lastIndex
                        val crumbColor = parseHexColorSafe(crumb.color)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isLast) crumbColor.copy(alpha = 0.15f) else Color.Transparent,
                            border = if (isLast) BorderStroke(0.8.dp, crumbColor) else null,
                            modifier = Modifier.clickable { onNavigateToFolder(crumb.id) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(crumbColor, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = crumb.name,
                                    fontSize = 11.sp,
                                    fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isLast) crumbColor else colors.inkBold
                                )
                            }
                        }
                    }
                }

                // Quick Jump button
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (showQuickJumpDialog) colors.teal.copy(alpha = 0.18f) else colors.surface,
                    border = BorderStroke(0.7.dp, if (showQuickJumpDialog) colors.teal else colors.border),
                    modifier = Modifier.clickable { onShowQuickJump() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "Quick Jump Folder Tree",
                            tint = colors.teal,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Tree",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.teal
                        )
                    }
                }
            }
        }
    }

    // Batch selection toolbar when active
    if (isSelectionMode) {
        item(key = "detail_batch_toolbar") {
            BatchSelectionToolbar(
                selectedCount = selectedPaperIds.size,
                totalCount = sortedFilteredPapers.size,
                onSelectAll = onSelectAll,
                onExportClick = onBatchExport,
                onMoveClick = onBatchMove
            )
        }
    }

    // 1. Immediate Subfolders (Compact List Rows matching reference screenshot)
    if (visibleSubfolders.isNotEmpty()) {
        items(visibleSubfolders, key = { "sub_${it.id}" }) { sub ->
            val (subTotalCount, subCompletedCount) = calculateFolderRecursiveSets(sub.id)
            SubfolderCompactRow(
                subfolder = sub,
                totalSets = subTotalCount,
                completedSets = subCompletedCount,
                onClick = { onNavigateToFolder(sub.id) }
            )
        }
    }

    // 2. Direct Test Sets Contained in This Folder (Strictly paper.folderId == activeFolderId)
    if (sortedFilteredPapers.isNotEmpty()) {
        items(sortedFilteredPapers, key = { "paper_${it.id}" }) { paper ->
            val paperAttempts = allAttempts.filter { it.paperId == paper.id }
            val isCompleted = paperAttempts.any { it.isSubmitted }
            val isInProgress = !isCompleted && paperAttempts.isNotEmpty()
            val lastScore = paperAttempts.maxByOrNull { it.submittedAt ?: it.createdAt }?.score

            PaperExamCard(
                paper = paper,
                isActive = paper.id == currentPaperEntityId,
                isSelectionMode = isSelectionMode,
                isSelected = paper.id in selectedPaperIds,
                onToggleSelect = { onToggleSelect(paper.id) },
                isCompleted = isCompleted,
                isInProgress = isInProgress,
                attemptScore = lastScore,
                onOpenMaster = { onOpenMaster(paper) },
                onTakeTest = { onTakeTest(paper) },
                onAssignFolder = { onAssignFolder(paper) },
                onExport = { onExportPaper(paper) },
                onDelete = { onDeletePaper(paper) }
            )
        }
    }

    // Empty State inside Folder
    if (visibleSubfolders.isEmpty() && sortedFilteredPapers.isEmpty()) {
        item(key = "detail_empty_state") {
            val colors = ExamTheme.colors
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(0.8.dp, colors.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = colors.teal.copy(alpha = 0.7f),
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "This folder has no tests or sub-folders yet",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Add sub-folders (like Subject Tests or Chapter Tests) or create new test sets here.",
                        fontSize = 12.sp,
                        color = colors.inkMuted,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onAddSubFolder,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.surfaceVariant,
                                contentColor = colors.inkBold
                            )
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ Sub-Folder", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = onCreateNewTestSet,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.primaryButtonBg,
                                contentColor = colors.primaryButtonText
                            )
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ New Test Set", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
