package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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

data class DualListMatchData(
    val leadIn: String? = null,
    val list1Title: String,
    val list1Items: List<Pair<String, String>>,
    val list2Title: String,
    val list2Items: List<Pair<String, String>>,
    val leadOut: String? = null
)

object DualMatchParserHelper {
    private val LIST1_HEADER_REGEX = Regex(
        """(?im)^\s*(?:(?:\*\*|__)?\s*)?(?:###\s*)?(List[- ]?(?:1|I|A)|Column[- ]?(?:1|I|A)|सूची[- ]?(?:1|१|I|A)|Group[- ]?(?:1|I|A)|तालिका[- ]?(?:1|१|I|A))(?:\s*[:\-–—])?(?:\s*(.*?))?(?:\*\*|__)?\s*$"""
    )
    private val LIST2_HEADER_REGEX = Regex(
        """(?im)^\s*(?:(?:\*\*|__)?\s*)?(?:###\s*)?(List[- ]?(?:2|II|B)|Column[- ]?(?:2|II|B)|सूची[- ]?(?:2|२|II|B)|Group[- ]?(?:2|II|B)|तालिका[- ]?(?:2|२|II|B))(?:\s*[:\-–—])?(?:\s*(.*?))?(?:\*\*|__)?\s*$"""
    )

    private val ITEM_REGEX = Regex(
        """^\s*(?:[-*•]\s*)?(?:\*\*|__)?\s*([A-Za-z0-9\u0900-\u097F]+|i{1,4}|I{1,4})[\.\:\)\-–—]\s*(?:\*\*|__)?\s*(.+)$"""
    )

    fun parse(text: String): DualListMatchData? {
        val lines = text.lines()
        val list1Index = lines.indexOfFirst { LIST1_HEADER_REGEX.containsMatchIn(it) }
        val list2Index = lines.indexOfFirst { LIST2_HEADER_REGEX.containsMatchIn(it) }

        if (list1Index == -1 || list2Index == -1 || list2Index <= list1Index) {
            return null
        }

        // Check that this is not already a table
        if (lines.subList(list1Index, list2Index).any { TableParserHelper.isTableRow(it) }) {
            return null
        }

        val leadIn = if (list1Index > 0) {
            lines.subList(0, list1Index).joinToString("\n").trim().takeIf { it.isNotEmpty() }
        } else null

        val list1Title = lines[list1Index].trim().removePrefix("#").trim()

        val list1Items = mutableListOf<Pair<String, String>>()
        for (i in (list1Index + 1) until list2Index) {
            val line = lines[i].trim()
            if (line.isEmpty() || line.startsWith("---")) continue
            val m = ITEM_REGEX.find(line)
            if (m != null) {
                list1Items.add(Pair(m.groupValues[1].trim(), m.groupValues[2].trim()))
            }
        }

        val list2Title = lines[list2Index].trim().removePrefix("#").trim()

        val list2Items = mutableListOf<Pair<String, String>>()
        var leadOutIndex = lines.size

        for (i in (list2Index + 1) until lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty() || line.startsWith("---")) continue
            val m = ITEM_REGEX.find(line)
            if (m != null) {
                list2Items.add(Pair(m.groupValues[1].trim(), m.groupValues[2].trim()))
            } else if (list2Items.isNotEmpty() && !line.startsWith("|")) {
                // First non-item line after List-II starts the leadOut
                leadOutIndex = i
                break
            }
        }

        val leadOut = if (leadOutIndex < lines.size) {
            lines.subList(leadOutIndex, lines.size).joinToString("\n").trim().takeIf { it.isNotEmpty() }
        } else null

        if (list1Items.size >= 2 && list2Items.size >= 2) {
            return DualListMatchData(
                leadIn = leadIn,
                list1Title = list1Title,
                list1Items = list1Items,
                list2Title = list2Title,
                list2Items = list2Items,
                leadOut = leadOut
            )
        }
        return null
    }
}

/**
 * Visual "Match the Following" card component.
 * Organizes List-I and List-II in distinct, structured comparison cards
 * with colored pill badges for high readability.
 */
@Composable
fun DualMatchCard(
    data: DualListMatchData,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Lead-in statement
        if (!data.leadIn.isNullOrBlank()) {
            LatexText(
                text = data.leadIn,
                color = colors.inkBold,
                fontSize = 15.5.sp,
                lineHeight = 23.sp
            )
        }

        // List-I Card
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = colors.surface,
            border = BorderStroke(0.8.dp, colors.border),
            shadowElevation = 0.5.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = colors.tealLight,
                    border = BorderStroke(0.7.dp, colors.teal.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = data.list1Title,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.tealDark,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    data.list1Items.forEach { (label, content) ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = colors.surfaceVariant,
                                border = BorderStroke(0.6.dp, colors.border),
                                modifier = Modifier.width(30.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.inkBold,
                                    modifier = Modifier.padding(vertical = 3.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            LatexText(
                                text = content,
                                color = colors.ink,
                                fontSize = 14.5.sp,
                                lineHeight = 21.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // List-II Card
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = colors.surface,
            border = BorderStroke(0.8.dp, colors.border),
            shadowElevation = 0.5.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFFEF3C7).copy(alpha = if (colors.isDark) 0.25f else 1f),
                    border = BorderStroke(0.7.dp, Color(0xFFD97706).copy(alpha = 0.5f))
                ) {
                    Text(
                        text = data.list2Title,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (colors.isDark) Color(0xFFFBBF24) else Color(0xFFB45309),
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    data.list2Items.forEach { (label, content) ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFFEF3C7).copy(alpha = if (colors.isDark) 0.15f else 0.5f),
                                border = BorderStroke(0.6.dp, Color(0xFFD97706).copy(alpha = 0.4f)),
                                modifier = Modifier.width(30.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (colors.isDark) Color(0xFFFBBF24) else Color(0xFFB45309),
                                    modifier = Modifier.padding(vertical = 3.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            LatexText(
                                text = content,
                                color = colors.ink,
                                fontSize = 14.5.sp,
                                lineHeight = 21.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Lead-out instruction (e.g. Choose the correct combination:)
        if (!data.leadOut.isNullOrBlank()) {
            LatexText(
                text = data.leadOut,
                color = colors.inkBold,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 22.sp
            )
        }
    }
}
