package com.example.util

import com.example.data.StudyNoteEntity

sealed interface StudyNoteBlock {
    val blockIndex: Int

    data class Heading(
        override val blockIndex: Int,
        val level: Int,
        val text: String
    ) : StudyNoteBlock

    data class OneLiner(
        override val blockIndex: Int,
        val displayIndex: String,
        val leadTerm: String,
        val detail: String,
        val hasRecallTarget: Boolean
    ) : StudyNoteBlock

    data class Table(
        override val blockIndex: Int,
        val headers: List<String>,
        val rows: List<List<String>>
    ) : StudyNoteBlock

    data class Callout(
        override val blockIndex: Int,
        val label: String,
        val body: String
    ) : StudyNoteBlock

    data class Paragraph(
        override val blockIndex: Int,
        val text: String
    ) : StudyNoteBlock
}

object StudyNoteParser {

    fun detectSourceHint(rawText: String): String {
        val sample = rawText.take(600).lowercase()
        return when {
            "|" in sample && "---" in sample -> "AI Structured Table"
            Regex("""^\s*(?:\d+[\.\)]|[-*•])\s+""", RegexOption.MULTILINE).containsMatchIn(rawText) -> "One-Liners / Bullets"
            "## " in rawText || "**" in rawText -> "Markdown Notes"
            else -> "Study Notes"
        }
    }

    fun parseBlocks(rawText: String): List<StudyNoteBlock> {
        val normalized = rawText.replace("\r\n", "\n").replace("\r", "\n").trim()
        if (normalized.isEmpty()) return emptyList()

        val lines = normalized.lines()
        val blocks = mutableListOf<StudyNoteBlock>()
        var blockCounter = 0
        var oneLinerSeq = 1
        var i = 0

        while (i < lines.size) {
            val rawLine = lines[i]
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty() || trimmed == "---" || trimmed == "***") {
                i++
                continue
            }

            // 1. Markdown Headings (#, ##, ###)
            val mdHeadingMatch = Regex("""^(#{1,4})\s+(.+)$""").find(trimmed)
            if (mdHeadingMatch != null) {
                val level = mdHeadingMatch.groupValues[1].length
                val title = cleanMarkdownDecorators(mdHeadingMatch.groupValues[2])
                blocks.add(StudyNoteBlock.Heading(blockCounter++, level, title))
                i++
                continue
            }

            // 2. Standalone Section Headings (e.g. "TOPIC: ...", "PART A - ...")
            if (Regex("""^(?:TOPIC|SECTION|UNIT|CHAPTER|PART)\s*[\dIVXA-Z]*\s*[:\-]\s*.+""", RegexOption.IGNORE_CASE).matches(trimmed) && trimmed.length <= 120) {
                blocks.add(StudyNoteBlock.Heading(blockCounter++, 2, cleanMarkdownDecorators(trimmed)))
                i++
                continue
            }

            // 3. Markdown Table block (lines starting/containing pipe delimiters)
            if (trimmed.startsWith("|") && trimmed.count { it == '|' } >= 2 && i + 1 < lines.size && lines[i + 1].trim().contains("|")) {
                val tableLines = mutableListOf<String>()
                var j = i
                while (j < lines.size && lines[j].trim().contains("|")) {
                    tableLines.add(lines[j].trim())
                    j++
                }
                val parsedRows = tableLines.filterNot { line ->
                    Regex("""^\|?\s*[:\-]+(\s*\|\s*[:\-]+)+\s*\|?$""").matches(line)
                }.map { row ->
                    row.trim('|').split("|").map { cleanMarkdownDecorators(it.trim()) }
                }.filter { cells -> cells.any { it.isNotBlank() } }

                if (parsedRows.size >= 2) {
                    blocks.add(
                        StudyNoteBlock.Table(
                            blockIndex = blockCounter++,
                            headers = parsedRows.first(),
                            rows = parsedRows.drop(1)
                        )
                    )
                    i = j
                    continue
                }
            }

            // 4. Callout / Blockquote (> ...)
            if (trimmed.startsWith(">")) {
                val calloutLines = mutableListOf<String>()
                var j = i
                while (j < lines.size && lines[j].trim().startsWith(">")) {
                    calloutLines.add(lines[j].trim().removePrefix(">").trim())
                    j++
                }
                val joined = cleanMarkdownDecorators(calloutLines.joinToString(" "))
                val splitColon = joined.split(":", limit = 2)
                val label = if (splitColon.size == 2 && splitColon[0].length <= 28) splitColon[0].trim() else "Key Insight"
                val body = if (splitColon.size == 2 && splitColon[0].length <= 28) splitColon[1].trim() else joined
                blocks.add(StudyNoteBlock.Callout(blockCounter++, label, body))
                i = j
                continue
            }

            // 5. Numbered or Bulleted One-Liner / Key Fact
            val listMatch = Regex("""^(?:(\d{1,4})[.)]|[-*•‣▪])\s+(.+)$""").find(trimmed)
            if (listMatch != null) {
                val explicitNum = listMatch.groupValues[1]
                val displayIdx = if (explicitNum.isNotBlank()) explicitNum else "${oneLinerSeq}"
                oneLinerSeq++
                val content = listMatch.groupValues[2].trim()
                val (lead, detail, recallable) = splitLeadAndDetail(content)
                blocks.add(
                    StudyNoteBlock.OneLiner(
                        blockIndex = blockCounter++,
                        displayIndex = displayIdx,
                        leadTerm = lead,
                        detail = detail,
                        hasRecallTarget = recallable
                    )
                )
                i++
                continue
            }

            // 6. Colon / Arrow / Dash separated standalone one-liner (e.g. "Article 352 -> National Emergency")
            val directPair = splitDirectKeyValue(trimmed)
            if (directPair != null) {
                blocks.add(
                    StudyNoteBlock.OneLiner(
                        blockIndex = blockCounter++,
                        displayIndex = "${oneLinerSeq++}",
                        leadTerm = directPair.first,
                        detail = directPair.second,
                        hasRecallTarget = true
                    )
                )
                i++
                continue
            }

            // 7. Standard Paragraph
            blocks.add(StudyNoteBlock.Paragraph(blockCounter++, cleanMarkdownDecorators(trimmed)))
            i++
        }

        return blocks
    }

    fun countOneLiners(rawText: String): Int {
        return parseBlocks(rawText).count { it is StudyNoteBlock.OneLiner }
    }

    private fun splitLeadAndDetail(content: String): Triple<String, String, Boolean> {
        // Check **Bold Lead** : / - / — Detail
        val boldMatch = Regex("""^\*\*(.+?)\*\*\s*(?:[:\-–—=]|->|=>)?\s*(.*)$""").find(content)
        if (boldMatch != null && boldMatch.groupValues[2].isNotBlank()) {
            return Triple(
                cleanMarkdownDecorators(boldMatch.groupValues[1]),
                cleanMarkdownDecorators(boldMatch.groupValues[2]),
                true
            )
        }

        // Check Term -> Detail / Term : Detail / Term — Detail
        val directPair = splitDirectKeyValue(content)
        if (directPair != null) {
            return Triple(directPair.first, directPair.second, true)
        }

        return Triple("", cleanMarkdownDecorators(content), false)
    }

    private fun splitDirectKeyValue(line: String): Pair<String, String>? {
        val cleaned = line.trim()
        val separators = listOf(" -> ", " => ", " — ", " – ", ": ")
        for (sep in separators) {
            val idx = cleaned.indexOf(sep)
            if (idx in 2..70) {
                val left = cleanMarkdownDecorators(cleaned.substring(0, idx))
                val right = cleanMarkdownDecorators(cleaned.substring(idx + sep.length))
                if (left.isNotBlank() && right.isNotBlank()) {
                    return left to right
                }
            }
        }
        return null
    }

    private fun cleanMarkdownDecorators(text: String): String {
        return text
            .replace(Regex("""\*\*(.+?)\*\*"""), "$1")
            .replace(Regex("""__(.+?)__"""), "$1")
            .replace(Regex("""`([^`]+)`"""), "$1")
            .trim()
    }

    fun generatePrintableHtml(note: StudyNoteEntity): String {
        val blocks = parseBlocks(note.rawContent)
        val bookmarked = note.getBookmarkedIndices()
        val esc: (String) -> String = { s ->
            s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
        }
        return buildString {
            append("""
                <!DOCTYPE html>
                <html>
                <head>
                  <meta charset="utf-8"/>
                  <title>${esc(note.title)}</title>
                  <style>
                    body { font-family: 'Segoe UI', Roboto, Arial, sans-serif; color: #0F172A; margin: 28px; line-height: 1.55; }
                    .header { border-bottom: 2px solid #0D9488; padding-bottom: 12px; margin-bottom: 18px; }
                    .badge { display: inline-block; background: #CCFBF1; color: #0F766E; padding: 3px 9px; border-radius: 6px; font-size: 11px; font-weight: 700; }
                    h1 { margin: 6px 0 4px 0; font-size: 20px; }
                    h2 { font-size: 16px; color: #0F172A; margin: 18px 0 8px 0; border-left: 4px solid #2563EB; padding-left: 8px; }
                    h3 { font-size: 14px; color: #334155; margin: 12px 0 6px 0; }
                    .oneliner { padding: 8px 10px; margin: 6px 0; background: #F8FAFC; border: 1px solid #E2E8F0; border-radius: 6px; }
                    .oneliner.star { border-left: 4px solid #F59E0B; background: #FFFBEB; }
                    .idx { font-weight: 700; color: #0D9488; margin-right: 6px; }
                    .lead { font-weight: 700; color: #0F172A; }
                    .callout { padding: 10px 12px; background: #EFF6FF; border-left: 4px solid #2563EB; border-radius: 6px; margin: 10px 0; }
                    table { width: 100%; border-collapse: collapse; margin: 12px 0; font-size: 12.5px; }
                    th, td { border: 1px solid #CBD5E1; padding: 6px 8px; text-align: left; }
                    th { background: #F1F5F9; font-weight: 700; }
                    p { margin: 8px 0; font-size: 13.5px; }
                  </style>
                </head>
                <body>
                  <div class="header">
                    <span class="badge">${esc(note.sourceAi)}</span>
                    ${if (note.subject.isNotBlank()) "<span class='badge' style='background:#EFF6FF;color:#1D4ED8;margin-left:6px;'>${esc(note.subject)}</span>" else ""}
                    <h1>${esc(note.title)}</h1>
                  </div>
            """.trimIndent())

            for (block in blocks) {
                when (block) {
                    is StudyNoteBlock.Heading -> {
                        val tag = if (block.level <= 2) "h2" else "h3"
                        append("<$tag>${esc(block.text)}</$tag>\n")
                    }
                    is StudyNoteBlock.OneLiner -> {
                        val starClass = if (block.blockIndex in bookmarked) " star" else ""
                        val starMark = if (block.blockIndex in bookmarked) "★ " else ""
                        append("<div class='oneliner$starClass'>")
                        append("<span class='idx'>$starMark${esc(block.displayIndex)}.</span>")
                        if (block.leadTerm.isNotBlank()) {
                            append("<span class='lead'>${esc(block.leadTerm)}</span> — ")
                        }
                        append("${esc(block.detail)}</div>\n")
                    }
                    is StudyNoteBlock.Callout -> {
                        append("<div class='callout'><strong>${esc(block.label)}:</strong> ${esc(block.body)}</div>\n")
                    }
                    is StudyNoteBlock.Table -> {
                        append("<table><thead><tr>")
                        block.headers.forEach { h -> append("<th>${esc(h)}</th>") }
                        append("</tr></thead><tbody>")
                        block.rows.forEach { row ->
                            append("<tr>")
                            row.forEach { cell -> append("<td>${esc(cell)}</td>") }
                            append("</tr>")
                        }
                        append("</tbody></table>\n")
                    }
                    is StudyNoteBlock.Paragraph -> {
                        append("<p>${esc(block.text)}</p>\n")
                    }
                }
            }
            append("</body></html>")
        }
    }
}
