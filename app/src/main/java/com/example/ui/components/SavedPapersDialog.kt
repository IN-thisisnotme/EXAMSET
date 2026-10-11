package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.PaperEntity
import com.example.ui.theme.ExamTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SavedPapersDialog(
    papers: List<PaperEntity>,
    onSelectPaper: (PaperEntity) -> Unit,
    onDeletePaper: (PaperEntity) -> Unit,
    onEditPaper: ((PaperEntity) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val colors = ExamTheme.colors
    val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

    // Extract categories (subjects)
    val categories = remember(papers) {
        listOf("All") + papers.map { it.subject.trim() }.filter { it.isNotBlank() }.distinct()
    }
    var selectedCategory by remember { mutableStateOf("All") }
    var paperToDelete by remember { mutableStateOf<PaperEntity?>(null) }

    val filteredPapers = remember(papers, selectedCategory) {
        if (selectedCategory == "All") {
            papers
        } else {
            papers.filter { it.subject.trim().equals(selectedCategory, ignoreCase = true) }
        }
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
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = colors.teal,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Saved Question Sets",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkBold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.muted)
                    }
                }

                // Category Chips (Subject/Topic Categorisation)
                if (categories.size > 2) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categories.forEach { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = {
                                    val count = if (cat == "All") papers.size else papers.count { it.subject.trim().equals(cat, ignoreCase = true) }
                                    Text("$cat ($count)", fontSize = 11.5.sp)
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colors.teal,
                                    selectedLabelColor = Color.White,
                                    containerColor = colors.surfaceVariant,
                                    labelColor = colors.ink
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selectedCategory == cat,
                                    borderColor = colors.border,
                                    selectedBorderColor = colors.teal
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (papers.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            tint = colors.muted.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No saved question sets yet.",
                            color = colors.muted,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Add or paste your question sets to access them here.",
                            color = colors.muted.copy(alpha = 0.7f),
                            fontSize = 11.5.sp
                        )
                    }
                } else if (filteredPapers.isEmpty()) {
                    Text(
                        text = "No papers found in category '$selectedCategory'.",
                        fontSize = 12.sp,
                        color = colors.muted,
                        modifier = Modifier.padding(vertical = 20.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredPapers, key = { it.id }) { item ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectPaper(item)
                                        onDismiss()
                                    }
                                    .testTag("saved_paper_item_${item.id}"),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant),
                                border = BorderStroke(0.8.dp, colors.border)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            // Set Number Tag
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = colors.teal.copy(alpha = 0.2f)
                                            ) {
                                                Text(
                                                    text = item.setNumber.ifBlank { "SET A" }.uppercase(),
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colors.tealDark,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                )
                                            }

                                            Text(
                                                text = item.title,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.inkBold,
                                                maxLines = 1,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(2.dp))

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = item.subject,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = colors.ink,
                                                maxLines = 1
                                            )

                                            if (item.topic.isNotBlank()) {
                                                Text("•", fontSize = 10.sp, color = colors.muted)
                                                Text(
                                                    text = item.topic,
                                                    fontSize = 11.sp,
                                                    color = colors.tealDark,
                                                    maxLines = 1
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${item.questionCount} Questions",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = colors.tealDark
                                            )
                                            Text(
                                                text = dateFormat.format(Date(item.createdAt)),
                                                fontSize = 10.sp,
                                                color = colors.muted
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (onEditPaper != null) {
                                            IconButton(
                                                onClick = {
                                                    onEditPaper(item)
                                                    onDismiss()
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Edit Details",
                                                    tint = colors.teal,
                                                    modifier = Modifier.size(17.dp)
                                                )
                                            }
                                        }

                                        IconButton(
                                            onClick = { paperToDelete = item },
                                            modifier = Modifier
                                                .size(32.dp)
                                                .testTag("delete_saved_paper_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Delete",
                                                tint = colors.red.copy(alpha = 0.8f),
                                                modifier = Modifier.size(18.dp)
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

    // Confirmation Dialog before deleting paper
    if (paperToDelete != null) {
        val target = paperToDelete!!
        AlertDialog(
            onDismissRequest = { paperToDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = null,
                    tint = colors.red
                )
            },
            title = {
                Text(
                    text = "Delete Question Set?",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkBold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete \"${target.title.ifBlank { "Untitled Set" }}\"? This will permanently remove the question set and cannot be undone.",
                    fontSize = 12.5.sp,
                    color = colors.ink
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toDel = target
                        paperToDelete = null
                        onDeletePaper(toDel)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.red),
                    modifier = Modifier.testTag("confirm_delete_saved_paper_button")
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { paperToDelete = null }) {
                    Text("Cancel", color = colors.ink)
                }
            }
        )
    }
}
