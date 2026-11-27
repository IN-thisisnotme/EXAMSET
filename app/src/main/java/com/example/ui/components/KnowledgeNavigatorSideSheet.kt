package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.FlashcardSrsState
import com.example.model.FlashcardSrsStatus
import com.example.ui.theme.ExamTheme
import com.example.util.SpacedRepetitionEngine
import com.example.util.StudyNoteBlock

private enum class KnowledgeNavigatorFilter {
    ALL,
    UNMASTERED,
    STARRED,
    DUE_SRS
}

/**
 * Slide-Over Knowledge Set Navigator Sheet (mirroring ExamPaletteSideSheet).
 * Allows one-handed jump navigation across numbered study units and ## Section Headings,
 * with filters for Unmastered, Starred, and SRS Due items.
 */
@Composable
fun KnowledgeNavigatorSideSheet(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    noteTitle: String,
    badgeLabel: String,
    allBlocks: List<StudyNoteBlock>,
    bookmarkedIndices: Set<Int>,
    masteredIndices: Set<Int>,
    srsMap: Map<Int, FlashcardSrsState> = emptyMap(),
    currentBlockIndex: Int? = null,
    onJumpToBlock: (blockIndex: Int) -> Unit,
    onScrollToTop: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isOpen) return

    val colors = ExamTheme.colors
    BackHandler(enabled = isOpen) { onDismiss() }

    val nowMillis = remember(isOpen, srsMap) { System.currentTimeMillis() }
    val headings = remember(allBlocks) {
        allBlocks.filterIsInstance<StudyNoteBlock.Heading>()
    }
    val studyUnits = remember(allBlocks) {
        allBlocks.filter { it is StudyNoteBlock.OneLiner || it is StudyNoteBlock.MultiLinerCard }
    }

    val unmasteredCount = remember(studyUnits, masteredIndices) {
        studyUnits.count { it.blockIndex !in masteredIndices }
    }
    val starredCount = remember(studyUnits, bookmarkedIndices) {
        studyUnits.count { it.blockIndex in bookmarkedIndices }
    }
    val dueBlockIndices = remember(studyUnits, srsMap, nowMillis) {
        studyUnits.filter { unit ->
            val st = srsMap[unit.blockIndex]
            st != null && SpacedRepetitionEngine.statusOf(st, nowMillis) == FlashcardSrsStatus.DUE
        }.map { it.blockIndex }.toSet()
    }

    var filter by remember { mutableStateOf(KnowledgeNavigatorFilter.ALL) }

    val filteredUnits = remember(studyUnits, filter, masteredIndices, bookmarkedIndices, dueBlockIndices) {
        studyUnits.mapIndexed { ordinal, block -> ordinal + 1 to block }.filter { (_, unit) ->
            when (filter) {
                KnowledgeNavigatorFilter.ALL -> true
                KnowledgeNavigatorFilter.UNMASTERED -> unit.blockIndex !in masteredIndices
                KnowledgeNavigatorFilter.STARRED -> unit.blockIndex in bookmarkedIndices
                KnowledgeNavigatorFilter.DUE_SRS -> unit.blockIndex in dueBlockIndices
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .testTag("knowledge_navigator_side_sheet")
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )

            Surface(
                shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                color = colors.surface,
                border = BorderStroke(1.dp, colors.border),
                shadowElevation = 16.dp,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .widthIn(min = 300.dp, max = 380.dp)
                    .fillMaxWidth(0.86f)
                    .fillMaxHeight()
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val isCompactHeight = maxHeight < 480.dp
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(vertical = if (isCompactHeight) 6.dp else 12.dp)
                    ) {
                        // Top Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = CircleShape,
                                        color = colors.teal.copy(alpha = 0.15f),
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.GridView,
                                                contentDescription = null,
                                                tint = colors.teal,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Study Navigator",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.inkBold
                                    )
                                }
                                Text(
                                    text = "$badgeLabel • ${studyUnits.size} Units (${masteredIndices.size} Mastered)",
                                    fontSize = 11.5.sp,
                                    color = colors.inkMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = 36.dp)
                                )
                            }

                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("knowledge_navigator_close_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Navigator",
                                    tint = colors.inkMuted
                                )
                            }
                        }

                        if (noteTitle.isNotBlank()) {
                            Text(
                                text = noteTitle,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.inkMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        HorizontalDivider(color = colors.border, thickness = 0.6.dp)

                        // Section Headings Outline Strip (if ## Headings exist)
                        if (headings.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bookmarks,
                                    contentDescription = null,
                                    tint = colors.teal,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "CHAPTER OUTLINE (${headings.size})",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = colors.inkMuted,
                                    letterSpacing = 0.5.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(5.dp))
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(headings, key = { "nav_hdr_${it.blockIndex}" }) { hdr ->
                                    Surface(
                                        onClick = {
                                            onJumpToBlock(hdr.blockIndex)
                                            onDismiss()
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = colors.tealLight.copy(alpha = 0.55f),
                                        border = BorderStroke(0.8.dp, colors.teal.copy(alpha = 0.4f)),
                                        modifier = Modifier.heightIn(min = 36.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = hdr.text,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = colors.tealDark,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = colors.border.copy(alpha = 0.6f), thickness = 0.5.dp)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Filter Chips
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = filter == KnowledgeNavigatorFilter.ALL,
                                    onClick = { filter = KnowledgeNavigatorFilter.ALL },
                                    label = { Text("All (${studyUnits.size})", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = colors.tealLight,
                                        selectedLabelColor = colors.tealDark
                                    )
                                )
                            }
                            item {
                                FilterChip(
                                    selected = filter == KnowledgeNavigatorFilter.UNMASTERED,
                                    onClick = { filter = KnowledgeNavigatorFilter.UNMASTERED },
                                    label = { Text("Unmastered ($unmasteredCount)", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = colors.blueLight,
                                        selectedLabelColor = colors.tealDark
                                    )
                                )
                            }
                            item {
                                FilterChip(
                                    selected = filter == KnowledgeNavigatorFilter.STARRED,
                                    onClick = { filter = KnowledgeNavigatorFilter.STARRED },
                                    label = { Text("Starred ($starredCount)", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = colors.amberLight,
                                        selectedLabelColor = colors.amber
                                    )
                                )
                            }
                            if (srsMap.isNotEmpty() || dueBlockIndices.isNotEmpty()) {
                                item {
                                    FilterChip(
                                        selected = filter == KnowledgeNavigatorFilter.DUE_SRS,
                                        onClick = { filter = KnowledgeNavigatorFilter.DUE_SRS },
                                        label = { Text("SRS Due (${dueBlockIndices.size})", fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFF7C3AED).copy(alpha = 0.16f),
                                            selectedLabelColor = Color(0xFF6D28D9)
                                        )
                                    )
                                }
                            }
                        }

                        // Compact Legend Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LegendDot(color = Color(0xFF22C55E), label = "Mastered", textColor = colors.inkMuted)
                            LegendDot(color = Color(0xFFF59E0B), label = "Starred", textColor = colors.inkMuted)
                            if (srsMap.isNotEmpty()) {
                                LegendDot(color = Color(0xFF7C3AED), label = "Due", textColor = colors.inkMuted)
                            }
                            LegendDot(
                                color = if (colors.isDark) colors.surfaceVariant else Color(0xFFE2E8F0),
                                label = "Pending",
                                textColor = colors.inkMuted
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // 5-Column Item Number Grid
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(5),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .testTag("knowledge_navigator_grid")
                        ) {
                            items(filteredUnits, key = { (_, u) -> "nav_unit_${u.blockIndex}" }) { (ordinalNum, unit) ->
                                val isMastered = unit.blockIndex in masteredIndices
                                val isStarred = unit.blockIndex in bookmarkedIndices
                                val isDue = unit.blockIndex in dueBlockIndices
                                val isCurrent = currentBlockIndex == unit.blockIndex

                                val (bg, borderColor, txtColor) = when {
                                    isMastered -> Triple(
                                        colors.greenLight,
                                        colors.green,
                                        colors.green
                                    )
                                    isDue -> Triple(
                                        Color(0xFF7C3AED).copy(alpha = 0.14f),
                                        Color(0xFF7C3AED),
                                        Color(0xFF6D28D9)
                                    )
                                    isStarred -> Triple(
                                        colors.amberLight,
                                        colors.amber,
                                        colors.amber
                                    )
                                    else -> Triple(
                                        if (colors.isDark) colors.surfaceVariant else Color(0xFFF1F5F9),
                                        colors.border,
                                        colors.inkBold
                                    )
                                }

                                val shortLabel = when (unit) {
                                    is StudyNoteBlock.OneLiner -> unit.displayIndex.ifBlank { "$ordinalNum" }
                                    is StudyNoteBlock.MultiLinerCard -> unit.displayIndex.ifBlank { "$ordinalNum" }
                                    else -> "$ordinalNum"
                                }.take(4)

                                Surface(
                                    onClick = {
                                        onJumpToBlock(unit.blockIndex)
                                        onDismiss()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = bg,
                                    border = BorderStroke(
                                        width = if (isCurrent) 2.2.dp else 1.dp,
                                        color = if (isCurrent) colors.teal else borderColor
                                    ),
                                    shadowElevation = if (isCurrent) 3.dp else 0.dp,
                                    modifier = Modifier
                                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                        .testTag("knowledge_nav_bubble_${unit.blockIndex}")
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = shortLabel,
                                            fontSize = 12.sp,
                                            fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Bold,
                                            color = txtColor,
                                            maxLines = 1
                                        )
                                        if (isStarred) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(3.dp)
                                                    .size(7.dp)
                                                    .background(Color(0xFFF59E0B), CircleShape)
                                                    .border(1.dp, Color.White, CircleShape)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = colors.border, thickness = 0.6.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    onScrollToTop()
                                    onDismiss()
                                },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = null,
                                    tint = colors.teal,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Top of Notes", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.teal)
                            }
                            Text(
                                text = "${filteredUnits.size} shown",
                                fontSize = 11.sp,
                                color = colors.inkMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String, textColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, fontSize = 10.5.sp, color = textColor)
    }
}
