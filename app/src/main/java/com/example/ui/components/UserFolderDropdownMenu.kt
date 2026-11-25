package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.example.model.FolderItem
import com.example.ui.theme.ExamTheme
import com.example.util.FolderPathHelper
import com.example.util.FolderPathSuggestion

/**
 * On-demand DropdownMenu displaying the user's folder hierarchy in unambiguous
 * Parent -> Child -> Sub-Child -> L4+ order. Opens only when clicked via the
 * drop-down icon; never pops up automatically while typing.
 */
@Composable
fun UserFolderDropdownMenu(
    expanded: Boolean,
    query: String,
    folders: List<FolderItem>,
    onDismissRequest: () -> Unit,
    onSelectEntry: (FolderPathSuggestion) -> Unit,
    headerActionLabel: String? = null,
    onHeaderActionClick: (() -> Unit)? = null,
    footerActionLabel: String? = null,
    onFooterActionClick: (() -> Unit)? = null
) {
    val colors = ExamTheme.colors
    var showFullTreeOverride by remember(expanded, query) { mutableStateOf(false) }

    val allFolders = remember(folders) {
        FolderPathHelper.buildUserFolderSuggestions(folders)
    }
    val prefixMatches = remember(query, folders) {
        FolderPathHelper.searchUserFolders(query, folders, maxResults = Int.MAX_VALUE)
    }
    val displayedEntries = if (showFullTreeOverride || query.isBlank()) allFolders else prefixMatches

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = {
            showFullTreeOverride = false
            onDismissRequest()
        },
        properties = PopupProperties(focusable = false),
        modifier = Modifier.heightIn(max = 320.dp)
    ) {
        if (headerActionLabel != null && onHeaderActionClick != null) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = headerActionLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.inkBold
                    )
                },
                onClick = {
                    showFullTreeOverride = false
                    onHeaderActionClick()
                }
            )
            HorizontalDivider(color = colors.border.copy(alpha = 0.5f), thickness = 0.6.dp)
        }

        if (allFolders.isEmpty()) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = "No folders created yet",
                        fontSize = 12.sp,
                        color = colors.muted
                    )
                },
                enabled = false,
                onClick = {}
            )
        } else if (displayedEntries.isEmpty()) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = "No folders starting with \"${query.trim()}\"",
                        fontSize = 12.sp,
                        color = colors.muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                enabled = false,
                onClick = {}
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Show all folders (${allFolders.size})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.teal
                    )
                },
                onClick = { showFullTreeOverride = true }
            )
        } else {
            if (!showFullTreeOverride && query.isNotBlank() && prefixMatches.size < allFolders.size) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "Show all folders (${allFolders.size})",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.teal
                        )
                    },
                    onClick = { showFullTreeOverride = true }
                )
                HorizontalDivider(color = colors.border.copy(alpha = 0.4f), thickness = 0.5.dp)
            }

            displayedEntries.forEach { entry ->
                val indentDp = ((entry.depth - 1).coerceIn(0, 4) * 12).dp
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = indentDp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = colors.teal.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = entry.levelLabel,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.teal,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = if (entry.depth > 1) "└─ ${entry.leafName}" else entry.leafName,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (entry.depth == 1) FontWeight.Bold else FontWeight.SemiBold,
                                    color = colors.inkBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (entry.depth > 1) {
                                    Text(
                                        text = entry.fullPath,
                                        fontSize = 10.sp,
                                        color = colors.muted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    },
                    onClick = {
                        showFullTreeOverride = false
                        onSelectEntry(entry)
                    }
                )
            }
        }

        if (footerActionLabel != null && onFooterActionClick != null) {
            HorizontalDivider(color = colors.border.copy(alpha = 0.5f), thickness = 0.6.dp)
            DropdownMenuItem(
                text = {
                    Text(
                        text = footerActionLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.teal
                    )
                },
                onClick = {
                    showFullTreeOverride = false
                    onFooterActionClick()
                }
            )
        }
    }
}
