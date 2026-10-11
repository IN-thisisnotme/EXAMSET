package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ExamTheme

data class ParsedMarkdownTable(
    val title: String? = null,
    val headers: List<String>,
    val rows: List<List<String>>
)

object TableParserHelper {
    /**
     * Checks if a line is a markdown table row (contains | or \t).
     */
    fun isTableRow(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return false
        if ((trimmed.startsWith("|") && trimmed.endsWith("|")) || (trimmed.count { it == '|' } >= 2)) return true
        if (trimmed.contains('\t') && trimmed.split('\t').count { it.isNotBlank() } >= 2) return true
        return false
    }

    /**
     * Checks if a line is a markdown table separator row like | --- | :---: |
     */
    fun isTableSeparator(line: String): Boolean {
        val trimmed = line.trim()
        if (!trimmed.contains("|")) return false
        val cells = trimmed.split("|").map { it.trim() }.filter { it.isNotEmpty() }
        return cells.isNotEmpty() && cells.all { it.all { ch -> ch == '-' || ch == ':' || ch == ' ' } }
    }

    /**
     * Splits a row line into cell contents.
     */
    fun parseRowCells(line: String): List<String> {
        val trimmed = line.trim()
        if (trimmed.contains("|")) {
            val stripped = trimmed.removePrefix("|").removeSuffix("|")
            return stripped.split("|").map { it.trim() }
        }
        if (trimmed.contains('\t')) {
            return trimmed.split('\t').map { it.trim() }.filter { it.isNotEmpty() }
        }
        return listOf(trimmed)
    }

    /**
     * Parses a block of consecutive markdown table lines into a ParsedMarkdownTable.
     */
    fun parseTableBlock(lines: List<String>, title: String? = null): ParsedMarkdownTable? {
        if (lines.isEmpty()) return null
        val dataRows = mutableListOf<List<String>>()
        var headers: List<String>? = null

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            if (isTableSeparator(trimmed)) {
                // Separator marks the boundary: previous row was the header
                continue
            }
            if (isTableRow(trimmed)) {
                val cells = parseRowCells(trimmed)
                if (headers == null) {
                    headers = cells
                } else {
                    dataRows.add(cells)
                }
            }
        }

        if (headers == null || headers.isEmpty()) return null

        val colCount = headers.size
        // Normalize all rows to colCount
        val normalizedRows = dataRows.map { row ->
            if (row.size < colCount) {
                row + List(colCount - row.size) { "" }
            } else {
                row.take(colCount)
            }
        }

        return ParsedMarkdownTable(
            title = title?.trim()?.removePrefix("#")?.trim()?.removePrefix("###")?.trim(),
            headers = headers,
            rows = normalizedRows
        )
    }
}

/**
 * Beautiful, responsive Material Design 3 table card for MCQ questions and explanations.
 * - In-cell LaTeX formula rendering.
 * - Distinct header styling with bottom divider.
 * - Alternating zebra row shading for high legibility.
 * - Horizontal scrolling with clean padding so tables never truncate or break on mobile.
 */
@Composable
fun MarkdownTableCard(
    table: ParsedMarkdownTable,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    val scrollState = rememberScrollState()

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = colors.surface,
        border = BorderStroke(0.8.dp, colors.border),
        shadowElevation = 0.5.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Optional Table Title Banner
            if (!table.title.isNullOrBlank()) {
                Surface(
                    color = colors.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ListAlt,
                            contentDescription = "Table",
                            tint = colors.tealDark,
                            modifier = Modifier.height(16.dp).width(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = table.title,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.inkBold
                        )
                    }
                }
                HorizontalDivider(thickness = 0.8.dp, color = colors.border)
            }

            // Scrollable Table View
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
            ) {
                Column(modifier = Modifier.width(IntrinsicSize.Max)) {
                    // Header Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surfaceVariant.copy(alpha = 0.75f)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        table.headers.forEachIndexed { colIndex, headerText ->
                            val cellWidth = when (colIndex) {
                                0 -> Modifier.widthIn(min = 140.dp)
                                1 -> Modifier.widthIn(min = 180.dp)
                                else -> Modifier.widthIn(min = 120.dp)
                            }
                            Box(
                                modifier = Modifier
                                    .then(cellWidth)
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                LatexText(
                                    text = headerText,
                                    color = colors.inkBold,
                                    fontSize = 14.sp,
                                    isBold = true,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }

                    HorizontalDivider(thickness = 1.dp, color = colors.border)

                    // Data Rows with alternating background
                    table.rows.forEachIndexed { rowIndex, rowCells ->
                        val isEven = rowIndex % 2 == 0
                        val rowBg = if (isEven) Color.Transparent else colors.surfaceVariant.copy(alpha = 0.35f)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(rowBg),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            rowCells.forEachIndexed { colIndex, cellText ->
                                val cellWidth = when (colIndex) {
                                    0 -> Modifier.widthIn(min = 140.dp)
                                    1 -> Modifier.widthIn(min = 180.dp)
                                    else -> Modifier.widthIn(min = 120.dp)
                                }
                                Box(
                                    modifier = Modifier
                                        .then(cellWidth)
                                        .padding(horizontal = 14.dp, vertical = 9.dp)
                                ) {
                                    LatexText(
                                        text = cellText,
                                        color = colors.ink,
                                        fontSize = 13.5.sp,
                                        isBold = false,
                                        lineHeight = 19.sp
                                    )
                                }
                            }
                        }

                        if (rowIndex < table.rows.size - 1) {
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = colors.border.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
        }
    }
}
