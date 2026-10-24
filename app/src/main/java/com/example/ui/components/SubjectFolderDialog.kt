package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.AttemptEntity
import com.example.data.PaperEntity
import com.example.ui.theme.ExamTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class DialogSegregation(val label: String, val icon: ImageVector) {
    ALL("All", Icons.Default.ViewList),
    STAGE("By Stage", Icons.Default.Layers),
    SET_SERIES("By Set", Icons.Default.ViewAgenda)
}

@Composable
fun SubjectFolderDialog(
    subjectName: String,
    papers: List<PaperEntity>,
    allAttempts: List<AttemptEntity>,
    onSelectPaper: (PaperEntity) -> Unit,
    onStartTest: (PaperEntity) -> Unit,
    onDeletePaper: (PaperEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ExamTheme.colors
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }
    var searchQuery by remember { mutableStateOf("") }
    var segregationMode by remember { mutableStateOf(DialogSegregation.ALL) }

    val subjectPapers = remember(papers, subjectName) {
        val s = subjectName.trim().lowercase()
        papers.filter {
            val pSub = it.subject.trim().lowercase()
            pSub == s || pSub.contains(s) || it.title.lowercase().contains(s) || it.topic.lowercase().contains(s)
        }
    }

    val filteredPapers = remember(subjectPapers, searchQuery) {
        if (searchQuery.isBlank()) {
            subjectPapers
        } else {
            val q = searchQuery.trim().lowercase()
            subjectPapers.filter {
                it.title.lowercase().contains(q) ||
                it.topic.lowercase().contains(q) ||
                it.setNumber.lowercase().contains(q)
            }
        }
    }

    val totalAttempts = remember(subjectPapers, allAttempts) {
        val ids = subjectPapers.map { it.id }.toSet()
        allAttempts.count { it.paperId in ids }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.border),
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                // Header with breadcrumb styling
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Surface(
                            shape = CircleShape,
                            color = colors.tealLight,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = colors.teal,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = subjectName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${subjectPapers.size} sets • $totalAttempts total attempts",
                                fontSize = 11.5.sp,
                                color = colors.muted
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.muted)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Segregation Mode Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DialogSegregation.values().forEach { mode ->
                        val isSelected = segregationMode == mode
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) colors.inkBold else colors.surfaceVariant,
                            border = BorderStroke(0.6.dp, if (isSelected) colors.inkBold else colors.border),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { segregationMode = mode }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 5.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = mode.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) colors.surface else colors.inkMuted,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = mode.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) colors.surface else colors.ink
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Search Bar inside folder
                if (subjectPapers.size > 2) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        placeholder = {
                            Text(
                                text = "Search sets...",
                                fontSize = 12.5.sp,
                                maxLines = 1,
                                softWrap = false,
                                color = colors.muted
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(17.dp), tint = colors.muted)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(15.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Question Sets List
                if (filteredPapers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "No matching sets found" else "No question sets in this subject",
                            color = colors.muted,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        when (segregationMode) {
                            DialogSegregation.ALL -> {
                                items(filteredPapers, key = { it.id }) { paper ->
                                    SubjectPaperItem(
                                        paper = paper,
                                        allAttempts = allAttempts,
                                        dateFormat = dateFormat,
                                        onSelectPaper = onSelectPaper,
                                        onStartTest = onStartTest,
                                        onDeletePaper = onDeletePaper,
                                        onDismiss = onDismiss
                                    )
                                }
                            }

                            DialogSegregation.STAGE -> {
                                val grouped = filteredPapers.groupBy { getDialogStageCategory(it) }
                                grouped.forEach { (stage, groupList) ->
                                    item(key = "stage_header_$stage") {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = colors.teal.copy(alpha = 0.09f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(text = stage, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = colors.inkBold)
                                                Text(text = "${groupList.size} Sets", fontSize = 10.5.sp, color = colors.tealDark, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                    items(groupList, key = { it.id }) { paper ->
                                        SubjectPaperItem(
                                            paper = paper,
                                            allAttempts = allAttempts,
                                            dateFormat = dateFormat,
                                            onSelectPaper = onSelectPaper,
                                            onStartTest = onStartTest,
                                            onDeletePaper = onDeletePaper,
                                            onDismiss = onDismiss
                                        )
                                    }
                                }
                            }

                            DialogSegregation.SET_SERIES -> {
                                val grouped = filteredPapers.groupBy { getDialogSetSeries(it) }.toSortedMap()
                                grouped.forEach { (series, groupList) ->
                                    item(key = "series_header_$series") {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFF0D9488).copy(alpha = 0.09f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(text = series, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = colors.inkBold)
                                                Text(text = "${groupList.size} Sets", fontSize = 10.5.sp, color = Color(0xFF0F766E), fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                    items(groupList, key = { it.id }) { paper ->
                                        SubjectPaperItem(
                                            paper = paper,
                                            allAttempts = allAttempts,
                                            dateFormat = dateFormat,
                                            onSelectPaper = onSelectPaper,
                                            onStartTest = onStartTest,
                                            onDeletePaper = onDeletePaper,
                                            onDismiss = onDismiss
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubjectPaperItem(
    paper: PaperEntity,
    allAttempts: List<AttemptEntity>,
    dateFormat: SimpleDateFormat,
    onSelectPaper: (PaperEntity) -> Unit,
    onStartTest: (PaperEntity) -> Unit,
    onDeletePaper: (PaperEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ExamTheme.colors
    val attemptsForPaper = allAttempts.count { it.paperId == paper.id }

    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant),
        border = BorderStroke(1.dp, colors.border),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colors.tealLight,
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Text(
                                text = "Set ${paper.setNumber.ifBlank { "01" }}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.teal,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = paper.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (paper.topic.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Topic: ${paper.topic}",
                            fontSize = 12.sp,
                            color = colors.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = colors.muted, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("${paper.questionCount} MCQs", fontSize = 11.sp, color = colors.muted)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.Assignment, contentDescription = null, tint = colors.muted, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("$attemptsForPaper attempts", fontSize = 11.sp, color = colors.muted)
                        }
                        Text(
                            text = dateFormat.format(Date(paper.updatedAt)),
                            fontSize = 11.sp,
                            color = colors.muted
                        )
                    }
                }

                IconButton(
                    onClick = { onDeletePaper(paper) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete Set",
                        tint = colors.red,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons: Open & Start Test
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onSelectPaper(paper)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.inkBold),
                    border = BorderStroke(1.dp, colors.border)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.teal)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("View Paper", fontSize = 12.sp, maxLines = 1, softWrap = false)
                }

                Button(
                    onClick = {
                        onStartTest(paper)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primaryButtonBg,
                        contentColor = colors.primaryButtonText
                    )
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(15.dp), tint = colors.primaryButtonText)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Start Test", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false, color = colors.primaryButtonText)
                }
            }
        }
    }
}

private fun getDialogStageCategory(paper: PaperEntity): String {
    val text = "${paper.title} ${paper.subject} ${paper.topic}".lowercase()
    return when {
        "csat" in text || "paper 2" in text -> "CSAT / Paper II"
        "gs 1" in text || "gs-1" in text || "general studies 1" in text -> "GS Paper I"
        "gs 2" in text || "gs-2" in text || "general studies 2" in text -> "GS Paper II"
        "gs 3" in text || "gs-3" in text || "general studies 3" in text -> "GS Paper III"
        "gs 4" in text || "ethics" in text -> "GS Paper IV (Ethics)"
        "prelim" in text -> "Prelims Stage"
        "mains" in text -> "Mains Stage"
        paper.topic.isNotBlank() -> paper.topic
        else -> "General Sets"
    }
}

private fun getDialogSetSeries(paper: PaperEntity): String {
    val raw = paper.setNumber.trim()
    return when {
        raw.isBlank() -> "Set 01 Series"
        raw.startsWith("Set", ignoreCase = true) -> raw.uppercase()
        raw.all { it.isDigit() } -> "Set ${raw.padStart(2, '0')}"
        else -> "Set ${raw.uppercase()}"
    }
}
