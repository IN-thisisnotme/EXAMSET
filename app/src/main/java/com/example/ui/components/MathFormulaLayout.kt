package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ExamTheme
import com.example.util.LatexHelper
import com.example.util.RichTextHelper

sealed interface MathNode {
    data class TextNode(val text: String) : MathNode
    data class FractionNode(val numerator: List<MathNode>, val denominator: List<MathNode>) : MathNode
    data class RootNode(val degree: String?, val radicand: List<MathNode>) : MathNode
    data class MatrixNode(val environment: String, val rows: List<List<List<MathNode>>>) : MathNode
    data class BracketsNode(val open: String, val close: String, val inner: List<MathNode>) : MathNode
}

object MathFormulaParser {

    fun has2DMath(text: String): Boolean {
        if (text.isBlank()) return false
        val normalized = LatexHelper.normalizeLatexAndMarkdown(text)
        return normalized.contains("\\frac") ||
                normalized.contains("\\dfrac") ||
                normalized.contains("\\sqrt") ||
                normalized.contains("\\begin{") ||
                Regex("""[A-Za-z0-9_\^]+\s*/\s*\([^\)]+\)""").containsMatchIn(normalized) ||
                Regex("""(?m)(?:=|\b(?:is\s+given\s+by|equals|formula\s*[:\-])\b)\s*\[?\s*[^=\n/\[\]]+/[^=\n/\[\]]+""").containsMatchIn(normalized)
    }

    private fun parseBalancedBraces(text: String, startIdx: Int): Pair<String, Int>? {
        var cur = startIdx
        while (cur < text.length && text[cur].isWhitespace()) cur++
        if (cur >= text.length || text[cur] != '{') return null
        val open = cur
        var depth = 1
        cur++
        while (cur < text.length && depth > 0) {
            when (text[cur]) {
                '{' -> depth++
                '}' -> depth--
            }
            if (depth > 0) cur++
        }
        if (depth == 0) {
            return Pair(text.substring(open + 1, cur), cur + 1)
        }
        return null
    }

    private fun parseBalancedBracket(text: String, startIdx: Int): Pair<String, Int>? {
        var cur = startIdx
        while (cur < text.length && text[cur].isWhitespace()) cur++
        if (cur >= text.length || text[cur] != '[') return null
        val open = cur
        var depth = 1
        cur++
        while (cur < text.length && depth > 0) {
            when (text[cur]) {
                '[' -> depth++
                ']' -> depth--
            }
            if (depth > 0) cur++
        }
        if (depth == 0) {
            return Pair(text.substring(open + 1, cur), cur + 1)
        }
        return null
    }

    private fun parseSlashFractions(text: String): List<MathNode> {
        // 1. Bracketed ratio equation: e.g. "Population growth rate (%) = [(Population at end - Population at beginning) / Population at beginning] * 100"
        val bracketEqMatch = Regex("""(?i)^([\s\S]*?=\s*)\[\s*\(?([^\[\]/]+?)\)?\s*/\s*\(?([^\[\]/]+?)\)?\s*\]([\s\S]*)$""").find(text.trim())
        if (bracketEqMatch != null) {
            val lhs = bracketEqMatch.groupValues[1]
            val num = bracketEqMatch.groupValues[2].trim()
            val den = bracketEqMatch.groupValues[3].trim()
            val trailing = bracketEqMatch.groupValues[4]
            val result = mutableListOf<MathNode>()
            if (lhs.isNotEmpty()) result.add(MathNode.TextNode(lhs))
            result.add(
                MathNode.BracketsNode(
                    open = "[",
                    close = "]",
                    inner = listOf(
                        MathNode.FractionNode(
                            numerator = parseMathNodes(num),
                            denominator = parseMathNodes(den)
                        )
                    )
                )
            )
            if (trailing.isNotEmpty()) result.addAll(parseSlashFractions(trailing))
            return result
        }

        // 2. Simple equation fraction: e.g. "Population density = Total population / Total land area"
        val simpleEqMatch = Regex("""(?i)^([\s\S]*?=\s*)([^=\n/]+?)\s*/\s*([^=\n/]+?)$""").find(text.trim())
        if (simpleEqMatch != null) {
            val lhs = simpleEqMatch.groupValues[1]
            val num = simpleEqMatch.groupValues[2].trim()
            val den = simpleEqMatch.groupValues[3].trim()
            val result = mutableListOf<MathNode>()
            if (lhs.isNotEmpty()) result.add(MathNode.TextNode(lhs))
            result.add(
                MathNode.FractionNode(
                    numerator = parseMathNodes(num),
                    denominator = parseMathNodes(den)
                )
            )
            return result
        }

        // 3. Slash fractions with parentheses: e.g. "w^2L^3 / (24P^2)"
        val slashRegex = Regex("""([A-Za-z0-9_\^]+)\s*/\s*\(([^\)]+)\)""")
        if (!slashRegex.containsMatchIn(text)) {
            return listOf(MathNode.TextNode(text))
        }

        val result = mutableListOf<MathNode>()
        var lastIdx = 0
        for (match in slashRegex.findAll(text)) {
            val start = match.range.first
            if (start > lastIdx) {
                result.add(MathNode.TextNode(text.substring(lastIdx, start)))
            }
            val numStr = match.groupValues[1]
            val denStr = match.groupValues[2]
            result.add(
                MathNode.FractionNode(
                    numerator = parseMathNodes(numStr),
                    denominator = parseMathNodes(denStr)
                )
            )
            lastIdx = match.range.last + 1
        }
        if (lastIdx < text.length) {
            result.add(MathNode.TextNode(text.substring(lastIdx)))
        }
        return result
    }

    fun parseMathNodes(rawText: String): List<MathNode> {
        val clean = LatexHelper.normalizeLatexAndMarkdown(rawText).trim()
            .removePrefix("\\[").removeSuffix("\\]")
            .removePrefix("$$").removeSuffix("$$")
            .removePrefix("$").removeSuffix("$")
            .trim()
        if (clean.isEmpty()) return emptyList()

        val nodes = mutableListOf<MathNode>()
        var i = 0
        val textBuffer = StringBuilder()

        fun flushText() {
            if (textBuffer.isNotEmpty()) {
                val s = textBuffer.toString()
                nodes.addAll(parseSlashFractions(s))
                textBuffer.clear()
            }
        }

        while (i < clean.length) {
            if (clean.startsWith("\\frac", i) || clean.startsWith("\\dfrac", i)) {
                val cmdLen = if (clean.startsWith("\\dfrac", i)) 6 else 5
                val numRes = parseBalancedBraces(clean, i + cmdLen)
                if (numRes != null) {
                    val denRes = parseBalancedBraces(clean, numRes.second)
                    if (denRes != null) {
                        flushText()
                        val numNodes = parseMathNodes(numRes.first)
                        val denNodes = parseMathNodes(denRes.first)
                        nodes.add(MathNode.FractionNode(numNodes, denNodes))
                        i = denRes.second
                        continue
                    }
                }
            }

            if (clean.startsWith("\\sqrt", i)) {
                val cmdLen = 5
                var afterCmd = i + cmdLen
                var degree: String? = null
                val bracketRes = parseBalancedBracket(clean, afterCmd)
                if (bracketRes != null) {
                    degree = bracketRes.first
                    afterCmd = bracketRes.second
                }
                val radicandRes = parseBalancedBraces(clean, afterCmd)
                if (radicandRes != null) {
                    flushText()
                    val radNodes = parseMathNodes(radicandRes.first)
                    nodes.add(MathNode.RootNode(degree, radNodes))
                    i = radicandRes.second
                    continue
                }
            }

            if (clean.startsWith("\\begin{", i)) {
                val envMatch = Regex("""^\\begin\{([a-zA-Z]+)\}""").find(clean.substring(i))
                if (envMatch != null) {
                    val env = envMatch.groupValues[1]
                    val endMarker = "\\end{$env}"
                    val endIdx = clean.indexOf(endMarker, i)
                    if (endIdx != -1) {
                        flushText()
                        val body = clean.substring(i + envMatch.value.length, endIdx).trim()
                        val rows = body.split(Regex("""\\\\|\\cr\b""")).map { rowStr ->
                            rowStr.split("&").map { cellStr ->
                                parseMathNodes(cellStr.trim())
                            }.filter { it.isNotEmpty() }
                        }.filter { it.isNotEmpty() }
                        nodes.add(MathNode.MatrixNode(env, rows))
                        i = endIdx + endMarker.length
                        continue
                    }
                }
            }

            textBuffer.append(clean[i])
            i++
        }
        flushText()
        return nodes
    }
}

@Composable
fun MathNodeView(
    node: MathNode,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = 15.sp,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    when (node) {
        is MathNode.TextNode -> {
            val targetColor = if (color != Color.Unspecified) color else colors.inkBold
            val formatted = LatexHelper.formatMathExpression(node.text)
            Text(
                text = RichTextHelper.buildRichText(
                    text = formatted,
                    baseColor = targetColor,
                    baseFontSize = fontSize,
                    isBold = false
                ),
                color = targetColor,
                fontSize = fontSize,
                modifier = modifier
            )
        }
        is MathNode.FractionNode -> {
            val barColor = if (color != Color.Unspecified) color else colors.inkBold
            val smallerSize = (fontSize.value * 0.9f).sp

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = modifier
                    .width(IntrinsicSize.Min)
                    .padding(horizontal = 4.dp)
            ) {
                // Numerator
                Box(
                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    MathNodeListView(
                        nodes = node.numerator,
                        color = color,
                        fontSize = smallerSize
                    )
                }

                // Horizontal Fraction Bar (automatically fills Min width of Column = wider of num or den)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.3.dp)
                        .background(barColor)
                )

                // Denominator
                Box(
                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    MathNodeListView(
                        nodes = node.denominator,
                        color = color,
                        fontSize = smallerSize
                    )
                }
            }
        }
        is MathNode.RootNode -> {
            val rootColor = if (color != Color.Unspecified) color else colors.inkBold

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = modifier.padding(horizontal = 3.dp)
            ) {
                if (!node.degree.isNullOrBlank()) {
                    Text(
                        text = node.degree,
                        fontSize = (fontSize.value * 0.72f).sp,
                        color = rootColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 1.dp, bottom = 6.dp)
                    )
                }
                Text(
                    text = "√",
                    fontSize = (fontSize.value * 1.35f).sp,
                    fontWeight = FontWeight.Light,
                    color = rootColor
                )
                Box(
                    modifier = Modifier
                        .drawBehind {
                            drawLine(
                                color = rootColor,
                                start = Offset(0f, 2f),
                                end = Offset(size.width, 2f),
                                strokeWidth = 1.2.dp.toPx()
                            )
                        }
                        .padding(top = 3.dp, start = 2.dp, end = 3.dp)
                ) {
                    MathNodeListView(
                        nodes = node.radicand,
                        color = color,
                        fontSize = fontSize
                    )
                }
            }
        }
        is MathNode.MatrixNode -> {
            val bracketColor = if (color != Color.Unspecified) color else colors.inkBold

            val (openBracket, closeBracket) = when (node.environment) {
                "pmatrix" -> "(" to ")"
                "bmatrix" -> "[" to "]"
                "cases" -> "{" to ""
                else -> "" to ""
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = modifier.padding(horizontal = 3.dp)
            ) {
                if (openBracket.isNotEmpty()) {
                    Text(
                        text = openBracket,
                        fontSize = (fontSize.value * 1.6f).sp,
                        fontWeight = FontWeight.Light,
                        color = bracketColor
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    node.rows.forEach { rowCells ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            rowCells.forEach { cellNodes ->
                                Box(contentAlignment = Alignment.Center) {
                                    MathNodeListView(nodes = cellNodes, color = color, fontSize = fontSize)
                                }
                            }
                        }
                    }
                }

                if (closeBracket.isNotEmpty()) {
                    Text(
                        text = closeBracket,
                        fontSize = (fontSize.value * 1.6f).sp,
                        fontWeight = FontWeight.Light,
                        color = bracketColor
                    )
                }
            }
        }
        is MathNode.BracketsNode -> {
            val bracketColor = if (color != Color.Unspecified) color else colors.inkBold

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = modifier.padding(horizontal = 2.dp)
            ) {
                Text(text = node.open, fontSize = (fontSize.value * 1.3f).sp, color = bracketColor)
                MathNodeListView(nodes = node.inner, color = color, fontSize = fontSize)
                Text(text = node.close, fontSize = (fontSize.value * 1.3f).sp, color = bracketColor)
            }
        }
    }
}

@Composable
fun MathNodeListView(
    nodes: List<MathNode>,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = 15.sp,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
    ) {
        nodes.forEach { node ->
            MathNodeView(node = node, color = color, fontSize = fontSize)
        }
    }
}

/**
 * 2D Mathematical Formula Composable:
 * Renders mathematical expressions with true 2D stacked fractions (numerator over horizontal bar over denominator),
 * square/nth roots, matrices, sub/superscripts, and Greek symbols.
 * Automatically scrolls horizontally on small screens to prevent clipping.
 */
@Composable
fun MathFormulaView(
    formula: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = 15.sp,
    scrollable: Boolean = true
) {
    val nodes = remember(formula) {
        MathFormulaParser.parseMathNodes(formula)
    }

    val scrollState = rememberScrollState()
    val scrollModifier = if (scrollable) Modifier.horizontalScroll(scrollState) else Modifier

    Box(
        modifier = modifier
            .then(scrollModifier)
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        MathNodeListView(
            nodes = nodes,
            color = color,
            fontSize = fontSize
        )
    }
}
