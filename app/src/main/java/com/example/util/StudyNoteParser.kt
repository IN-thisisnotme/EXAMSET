package com.example.util

import com.example.data.StudyNoteEntity
import com.example.model.KnowledgeCollectionType
import com.example.model.KnowledgeDepth
import com.example.model.KnowledgeFormatRule
import com.example.model.KnowledgeLayerOrder
import com.example.model.KnowledgeTransformAction
import com.example.model.QuestionPaper

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

    data class MultiLinerCard(
        override val blockIndex: Int,
        val displayIndex: String,
        val title: String,
        val badge: String = "",
        val paragraphs: List<String> = emptyList(),
        val nestedBullets: List<Pair<Int, String>> = emptyList(),
        val formula: String = "",
        val isReversed: Boolean = false
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
        val sample = rawText.take(800).lowercase()
        return when {
            "|" in sample && "---" in sample -> "Structured Matrix"
            Regex("""^\s{2,}[-*•\d]""", RegexOption.MULTILINE).containsMatchIn(rawText) -> "Multi-Liner Hierarchy"
            Regex("""^\s*(?:\d+[.)]|[-*•])\s+""", RegexOption.MULTILINE).containsMatchIn(rawText) -> "One-Liners / Bullets"
            "## " in rawText || "**" in rawText -> "Markdown Knowledge"
            else -> "Knowledge Set"
        }
    }

    fun countOneLiners(rawText: String, rule: KnowledgeFormatRule = KnowledgeFormatRule()): Int {
        return parseBlocks(rawText, rule).count {
            it is StudyNoteBlock.OneLiner || it is StudyNoteBlock.MultiLinerCard
        }
    }

    fun parseBlocks(rawText: String, rule: KnowledgeFormatRule = KnowledgeFormatRule()): List<StudyNoteBlock> {
        val normalized = normalizeDelimiters(rawText).trim()
        if (normalized.isEmpty()) return emptyList()

        val lines = normalized.lines()
        val blocks = mutableListOf<StudyNoteBlock>()
        var blockCounter = 0
        var factSeq = 1
        var i = 0

        while (i < lines.size) {
            val rawLine = lines[i]
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty() || trimmed == "---" || trimmed == "***" || trimmed.startsWith("```")) {
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
            if (Regex("""^(?:TOPIC|SECTION|UNIT|CHAPTER|PART|MODULE)\s*[\dIVXA-Z]*\s*[:\-]\s*.+""", RegexOption.IGNORE_CASE).matches(trimmed) && trimmed.length <= 120) {
                blocks.add(StudyNoteBlock.Heading(blockCounter++, 2, cleanMarkdownDecorators(trimmed)))
                i++
                continue
            }

            // 3. Markdown Table block
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
                val joined = cleanMarkdownDecorators(calloutLines.joinToString("\n"))
                val splitColon = joined.split(":", limit = 2)
                val label = if (splitColon.size == 2 && splitColon[0].length <= 28) splitColon[0].trim() else "Key Insight"
                val body = if (splitColon.size == 2 && splitColon[0].length <= 28) splitColon[1].trim() else joined
                blocks.add(StudyNoteBlock.Callout(blockCounter++, label, body))
                i = j
                continue
            }

            // 5. Q: / A: multi-line pair
            val qMatch = Regex("""^(?:Q(?:\d+)?|Question)\s*[:.\-]\s*(.+)$""", RegexOption.IGNORE_CASE).find(trimmed)
            if (qMatch != null) {
                val qText = cleanMarkdownDecorators(qMatch.groupValues[1])
                val paragraphs = mutableListOf<String>()
                val nested = mutableListOf<Pair<Int, String>>()
                var formula = ""
                var j = i + 1
                while (j < lines.size) {
                    val nextRaw = lines[j]
                    val nextTrim = nextRaw.trim()
                    if (nextTrim.isEmpty()) break
                    if (Regex("""^(?:Q(?:\d+)?|Question)\s*[:.\-]\s*""", RegexOption.IGNORE_CASE).containsMatchIn(nextTrim)) break
                    val aMatch = Regex("""^(?:A(?:ns(?:wer)?)?|Explanation|Detail)\s*[:.\-]\s*(.+)$""", RegexOption.IGNORE_CASE).find(nextTrim)
                    if (aMatch != null) {
                        paragraphs.add(cleanMarkdownDecorators(aMatch.groupValues[1]))
                    } else if (nextTrim.startsWith("-") || nextTrim.startsWith("*") || nextTrim.startsWith("•")) {
                        nested.add(countIndentLevel(nextRaw) to cleanMarkdownDecorators(nextTrim.drop(1).trim()))
                    } else {
                        val cleaned = cleanMarkdownDecorators(nextTrim)
                        if (looksLikeFormula(cleaned) && formula.isBlank()) formula = cleaned
                        else paragraphs.add(cleaned)
                    }
                    j++
                }
                if (paragraphs.isNotEmpty() || nested.isNotEmpty()) {
                    blocks.add(
                        StudyNoteBlock.MultiLinerCard(
                            blockIndex = blockCounter++,
                            displayIndex = "${factSeq++}",
                            title = qText,
                            badge = "Q & A",
                            paragraphs = paragraphs,
                            nestedBullets = nested,
                            formula = formula,
                            isReversed = false
                        )
                    )
                    i = j
                    continue
                }
            }

            // 6. Numbered or top-level bulleted item (possibly followed by nested sub-bullets / multi-line explanation)
            val listMatch = Regex("""^(?:(\d{1,4})[.)]|[-*•‣▪])\s+(.+)$""").find(trimmed)
            if (listMatch != null) {
                val baseIndent = leadingSpaces(rawLine)
                val explicitNum = listMatch.groupValues[1]
                val displayIdx = if (explicitNum.isNotBlank()) explicitNum else "$factSeq"
                factSeq++
                val content = listMatch.groupValues[2].trim()

                // Look ahead for indented sub-bullets, Formula/Example/Why lines, or continuation lines
                val nestedBullets = mutableListOf<Pair<Int, String>>()
                val extraParagraphs = mutableListOf<String>()
                var formula = ""
                var j = i + 1

                if (rule.preserveNestedBullets) {
                    while (j < lines.size) {
                        val subRaw = lines[j]
                        val subTrim = subRaw.trim()
                        if (subTrim.isEmpty()) break
                        if (subTrim.startsWith("#") || subTrim.startsWith("|") || subTrim.startsWith(">") || subTrim == "---") break

                        val subIndent = leadingSpaces(subRaw)
                        val isSubBullet = Regex("""^(?:[-*•‣▪◦]|\d+[.)]|[a-zA-Z][.)])\s+(.+)$""").find(subTrim)
                        val isIndentedMore = subIndent > baseIndent
                        val isTagSubLine = Regex("""^(?:Formula|Equation|Example|E\.g\.|Note|Why|Trick|Mnemonic|Answer|Concept|Takeaway)\s*[:\-]\s*(.+)$""", RegexOption.IGNORE_CASE).find(subTrim)

                        if (isIndentedMore && isSubBullet != null) {
                            val level = ((subIndent - baseIndent) / 2).coerceIn(1, 3)
                            val bulletText = cleanMarkdownDecorators(isSubBullet.groupValues[1])
                            if (looksLikeFormula(bulletText) && formula.isBlank()) {
                                formula = bulletText
                            } else {
                                nestedBullets.add(level to bulletText)
                            }
                            j++
                        } else if (isTagSubLine != null) {
                            val tag = subTrim.substringBefore(":").substringBefore("-").trim()
                            val body = cleanMarkdownDecorators(isTagSubLine.groupValues[1])
                            if (tag.equals("Formula", ignoreCase = true) || tag.equals("Equation", ignoreCase = true)) {
                                formula = body
                            } else {
                                nestedBullets.add(1 to "$tag: $body")
                            }
                            j++
                        } else if (isIndentedMore) {
                            val cleanedSub = cleanMarkdownDecorators(subTrim)
                            if (looksLikeFormula(cleanedSub) && formula.isBlank()) formula = cleanedSub
                            else extraParagraphs.add(cleanedSub)
                            j++
                        } else {
                            break
                        }
                    }
                }

                val splitResult = splitLeadAndDetail(content, rule.layerOrder)
                val useMultiLiner = nestedBullets.isNotEmpty() ||
                    extraParagraphs.isNotEmpty() ||
                    formula.isNotBlank() ||
                    splitResult.detail.length > 155 ||
                    rule.collectionType == KnowledgeCollectionType.MULTI_LINER ||
                    rule.collectionType == KnowledgeCollectionType.CONCEPT_CODEX

                if (useMultiLiner) {
                    val cardTitle = splitResult.lead.ifBlank {
                        splitResult.detail.take(64).let { if (splitResult.detail.length > 64) "$it…" else it }
                    }
                    val cardParagraphs = buildList {
                        if (splitResult.lead.isNotBlank() && splitResult.detail.isNotBlank()) {
                            add(splitResult.detail)
                        } else if (splitResult.lead.isBlank() && splitResult.detail.isNotBlank()) {
                            add(splitResult.detail)
                        }
                        addAll(extraParagraphs)
                    }
                    blocks.add(
                        StudyNoteBlock.MultiLinerCard(
                            blockIndex = blockCounter++,
                            displayIndex = displayIdx,
                            title = cardTitle,
                            badge = if (splitResult.isReversed) "REVERSED" else "",
                            paragraphs = cardParagraphs.distinct(),
                            nestedBullets = nestedBullets,
                            formula = formula,
                            isReversed = splitResult.isReversed
                        )
                    )
                } else {
                    blocks.add(
                        StudyNoteBlock.OneLiner(
                            blockIndex = blockCounter++,
                            displayIndex = displayIdx,
                            leadTerm = splitResult.lead,
                            detail = splitResult.detail,
                            hasRecallTarget = splitResult.hasRecallTarget
                        )
                    )
                }
                i = j
                continue
            }

            // 7. Colon / Arrow / Dash separated standalone one-liner (e.g. "Article 352 -> National Emergency")
            val directPair = splitDirectKeyValue(trimmed, rule.layerOrder)
            if (directPair != null) {
                blocks.add(
                    StudyNoteBlock.OneLiner(
                        blockIndex = blockCounter++,
                        displayIndex = "${factSeq++}",
                        leadTerm = directPair.lead,
                        detail = directPair.detail,
                        hasRecallTarget = true
                    )
                )
                i++
                continue
            }

            // 8. Standard Paragraph (merge consecutive plain lines in the same paragraph)
            val paraLines = mutableListOf(cleanMarkdownDecorators(trimmed))
            var j = i + 1
            while (j < lines.size) {
                val nxt = lines[j].trim()
                if (nxt.isEmpty() ||
                    nxt.startsWith("#") ||
                    nxt.startsWith(">") ||
                    nxt.startsWith("|") ||
                    nxt.startsWith("```") ||
                    Regex("""^(?:\d+[.)]|[-*•‣▪])\s+""").containsMatchIn(nxt) ||
                    splitDirectKeyValue(nxt, rule.layerOrder) != null
                ) break
                paraLines.add(cleanMarkdownDecorators(nxt))
                j++
            }
            blocks.add(StudyNoteBlock.Paragraph(blockCounter++, paraLines.joinToString("\n")))
            i = j
        }

        return blocks
    }

    fun compilePrompt(
        topic: String,
        collectionType: KnowledgeCollectionType,
        rule: KnowledgeFormatRule
    ): String {
        val cleanTopic = topic.trim().ifBlank { "[ENTER YOUR SUBJECT / TOPIC HERE]" }
        val collectionTitle = rule.customCollectionLabel.trim().ifBlank { collectionType.label }

        val depthInstruction = when (rule.depth) {
            KnowledgeDepth.CRISP -> "Keep every item crisp, high-yield, and exam-focused (1-2 lines max per takeaway)."
            KnowledgeDepth.BALANCED -> "Use balanced multi-layer structure: bold key concept followed by clear core reasoning and 1-2 nested sub-points where helpful."
            KnowledgeDepth.DEEP -> "Provide deep conceptual breakdowns with definitions, mechanisms, edge-cases, exceptions, and exam traps."
        }

        val layerInstruction = when (rule.layerOrder) {
            KnowledgeLayerOrder.TERM_FIRST -> "Always put the Key Term / Title FIRST in **bold**, followed by `->` and the concise explanation (e.g., `1. **Article 32** -> Right to Constitutional Remedies`)."
            KnowledgeLayerOrder.BACKWARDS_ANSWER_FIRST -> "Format for active recall: state the scenario/symptom/clue first, followed by `->` and the exact **Concept / Answer**."
            KnowledgeLayerOrder.QA_SEPARATE_LINES -> "Format each item as `Q: <concept question>` on one line, followed immediately by `A: <concise explanation>` and optional indented `- ` bullets."
            KnowledgeLayerOrder.AUTO_DETECT -> "Use clean numbered items (`1. **Concept** -> Core Fact`) with optional 2-space indented `- sub-point` bullets for multi-liners."
        }

        val styleRule = when (collectionType) {
            KnowledgeCollectionType.ONE_LINERS -> "Generate 20–30 ultra-dense exam one-liners grouped under `## Section Headings`."
            KnowledgeCollectionType.MULTI_LINER -> "Generate 10–15 Multi-Liner Concept Cards. Each numbered item has a **Concept Title** and 2–3 indented `- sub-points` (mechanism, example, exception)."
            KnowledgeCollectionType.CONCEPT_CODEX -> "Generate a comprehensive Concept Codex with `## Topic Headings`, **Core Principles**, indented sub-bullets, and `> Exam Trap:` callouts."
            KnowledgeCollectionType.COMPARISON_TABLE -> "Structure the core comparison as clean Markdown tables (`| Feature | Item A | Item B |`) followed by `> Key Difference:` insights."
            KnowledgeCollectionType.FORMULA_SHEET -> "List every important law/formula with `Formula:` lines, SI units, boundary conditions, and common numerical pitfalls."
            KnowledgeCollectionType.WEAK_AREA_REVIEW -> "Create targeted remediation cards focusing on misconceptions, look-alike traps, and quick memory anchors."
            KnowledgeCollectionType.CUSTOM -> "Structure the output cleanly as a '$collectionTitle' sheet tailored to exam mastery."
        }

        return buildString {
            appendLine("Create a high-yield **$collectionTitle** study sheet for: $cleanTopic")
            appendLine("Output Language: ${rule.language.ifBlank { "English" }}")
            appendLine()
            appendLine("STRICT FORMATTING RULES (so ExamSet parses 100% cleanly):")
            appendLine("1. $styleRule")
            appendLine("2. $depthInstruction")
            appendLine("3. $layerInstruction")
            if (rule.preserveNestedBullets) {
                appendLine("4. For multi-layer concepts, indent sub-points with exactly 2 spaces (`  - Sub-point`) under the parent numbered item.")
            } else {
                appendLine("4. Avoid nested sub-bullets; keep each fact on a single crisp line.")
            }
            if (rule.includeLatexMath) {
                appendLine("5. Wrap all mathematical formulas, chemical symbols, and equations in inline `\$...\$` (do NOT use `\\\\[...\\\\]` or code blocks).")
            } else {
                appendLine("5. Write formulas in clean plain Unicode text without LaTeX delimiters.")
            }
            if (rule.includeCallouts) {
                appendLine("6. Highlight high-risk exam traps and memory mnemonics using `> Exam Tip: ...` blockquotes.")
            }
            if (rule.includeComparisonTable) {
                appendLine("7. Include at least one Markdown comparison table (`| Parameter | ... |`).")
            }
            appendLine("8. Output ONLY the structured notes directly (no intro chatter, no closing remarks, no outer ```markdown``` wrapper).")
            if (rule.customInstruction.isNotBlank()) {
                appendLine()
                appendLine("USER CUSTOM REQUIREMENT:")
                appendLine(rule.customInstruction.trim())
            }
        }.trim()
    }

    fun applyTransform(rawText: String, action: KnowledgeTransformAction): String {
        val cleanedBase = normalizeDelimiters(rawText)
        if (cleanedBase.isBlank()) return ""

        return when (action) {
            KnowledgeTransformAction.STRIP_AI_CHATTER -> stripAiChatter(cleanedBase)
            KnowledgeTransformAction.FIX_BACKWARDS_CHATGPT -> fixBackwardsChatGpt(cleanedBase)
            KnowledgeTransformAction.SWAP_LEAD_DETAIL -> swapLeadDetail(cleanedBase)
            KnowledgeTransformAction.MERGE_NESTED_BULLETS -> mergeNestedBullets(cleanedBase)
            KnowledgeTransformAction.SPLIT_SEMICOLONS -> splitSemicolons(cleanedBase)
        }
    }

    fun buildWeakAreaNoteContent(
        paper: QuestionPaper,
        incorrectNums: Set<Int>,
        unattendedNums: Set<Int>,
        answers: Map<Int, String>
    ): String {
        val targetQuestions = paper.questions.filter {
            it.num in incorrectNums || it.num in unattendedNums
        }
        if (targetQuestions.isEmpty()) {
            return "## ${paper.metadata.title}\n> All Questions Mastered: No incorrect or skipped items in this test attempt."
        }

        return buildString {
            appendLine("## Weak-Area Remediation: ${paper.metadata.title}")
            appendLine("> Summary: ${incorrectNums.size} Incorrect • ${unattendedNums.size} Skipped • Subject: ${paper.metadata.subject}")
            appendLine()

            var seq = 1
            targetQuestions.forEach { q ->
                val statusTag = if (q.num in incorrectNums) "Incorrect (Marked ${answers[q.num] ?: "-"})" else "Skipped"
                val correctLetter = q.correctAnswer?.uppercase()
                val correctOptionText = when (correctLetter) {
                    "A" -> q.options.a
                    "B" -> q.options.b
                    "C" -> q.options.c
                    "D" -> q.options.d
                    "E" -> q.options.e
                    else -> ""
                }.trim()

                val cleanStem = q.question.replace(Regex("""\s+"""), " ").trim()
                appendLine("${seq++}. **Q${q.num}: $cleanStem**")
                appendLine("  - Status: $statusTag")
                if (correctLetter != null) {
                    val ansLine = if (correctOptionText.isNotBlank()) "($correctLetter) $correctOptionText" else correctLetter
                    appendLine("  - Correct Answer: $ansLine")
                }
                if (!q.formula.isNullOrBlank()) {
                    appendLine("  - Formula: ${q.formula.trim()}")
                }
                if (!q.explanation.isNullOrBlank()) {
                    val cleanExp = q.explanation.replace(Regex("""\s+"""), " ").trim()
                    appendLine("  - Key Takeaway: $cleanExp")
                }
                appendLine()
            }
        }.trim()
    }

    fun convertNoteBlocksToMcqDraft(note: StudyNoteEntity): String {
        val blocks = parseBlocks(note.rawContent, note.parsedFormatRule())
        val starred = note.getBookmarkedIndices()
        val candidates = blocks.filter {
            (it is StudyNoteBlock.OneLiner && it.leadTerm.isNotBlank() && it.detail.isNotBlank()) ||
                (it is StudyNoteBlock.MultiLinerCard && it.title.isNotBlank() && (it.paragraphs.isNotEmpty() || it.nestedBullets.isNotEmpty()))
        }
        val selected = if (starred.isNotEmpty()) {
            val onlyStarred = candidates.filter { it.blockIndex in starred }
            if (onlyStarred.isNotEmpty()) onlyStarred else candidates
        } else {
            candidates
        }

        if (selected.isEmpty()) return ""

        val poolAnswers = selected.mapNotNull { blk ->
            when (blk) {
                is StudyNoteBlock.OneLiner -> blk.detail.take(90).trim().takeIf { it.isNotBlank() }
                is StudyNoteBlock.MultiLinerCard -> blk.paragraphs.firstOrNull()?.take(90)?.trim()
                    ?: blk.nestedBullets.firstOrNull()?.second?.take(90)?.trim()
                else -> null
            }
        }.distinct()

        return buildString {
            appendLine("TITLE: ${note.title} — Rapid Practice Set")
            appendLine("SUBJECT: ${note.subject.ifBlank { "General Revision" }}")
            appendLine()
            selected.take(25).forEachIndexed { idx, blk ->
                val qNum = idx + 1
                val (prompt, correct, explanation) = when (blk) {
                    is StudyNoteBlock.OneLiner -> Triple(
                        "Which of the following accurately describes \"${blk.leadTerm}\"?",
                        blk.detail,
                        "${blk.leadTerm}: ${blk.detail}"
                    )
                    is StudyNoteBlock.MultiLinerCard -> {
                        val mainDetail = blk.paragraphs.firstOrNull()
                            ?: blk.nestedBullets.firstOrNull()?.second
                            ?: blk.title
                        val fullExp = (blk.paragraphs + blk.nestedBullets.map { it.second }).joinToString(" | ")
                        Triple(
                            "With reference to \"${blk.title}\", identify the accurate statement:",
                            mainDetail.take(140),
                            fullExp
                        )
                    }
                    else -> Triple("", "", "")
                }
                if (prompt.isNotBlank() && correct.isNotBlank()) {
                    val distractors = poolAnswers.filter { !it.equals(correct, ignoreCase = true) }.take(3).toMutableList()
                    while (distractors.size < 3) {
                        distractors.add(
                            when (distractors.size) {
                                0 -> "None of the statements listed above"
                                1 -> "Applies exclusively under reversed conditions"
                                else -> "Not associated with this concept"
                            }
                        )
                    }
                    appendLine("Q$qNum. $prompt")
                    appendLine("(A) $correct")
                    appendLine("(B) ${distractors[0]}")
                    appendLine("(C) ${distractors[1]}")
                    appendLine("(D) ${distractors[2]}")
                    appendLine("Correct Answer: A")
                    appendLine("Explanation: $explanation")
                    appendLine()
                }
            }
        }.trim()
    }

    private fun stripAiChatter(text: String): String {
        val lines = text.lines()
        val dropPrefixes = listOf(
            "sure!", "sure,", "certainly!", "of course!", "here is", "here are", "below is",
            "let me know", "hope this helps", "would you like", "feel free to", "i can also"
        )
        val filtered = lines.filterIndexed { index, raw ->
            val t = raw.trim()
            if (t.startsWith("```")) return@filterIndexed false
            val isEdge = index < 3 || index >= lines.size - 3
            val lower = t.lowercase()
            !(isEdge && dropPrefixes.any { lower.startsWith(it) })
        }
        return filtered.joinToString("\n").trim()
    }

    private fun fixBackwardsChatGpt(text: String): String {
        val cleaned = stripAiChatter(text)
        val out = mutableListOf<String>()
        val lines = cleaned.lines()
        for (raw in lines) {
            val indent = raw.takeWhile { it == ' ' || it == '\t' }
            val t = raw.trim()
            if (t.isEmpty()) {
                out.add("")
                continue
            }
            val listPrefixMatch = Regex("""^((?:\d+[.)]|[-*•‣▪])\s+)(.+)$""").find(t)
            val prefix = listPrefixMatch?.groupValues?.get(1).orEmpty()
            val body = listPrefixMatch?.groupValues?.get(2) ?: t

            // Normalize bullet character to standard `- ` if not numbered
            val normalizedPrefix = if (prefix.isNotBlank() && !prefix.first().isDigit()) "- " else prefix
            val split = splitLeadAndDetail(body, KnowledgeLayerOrder.BACKWARDS_ANSWER_FIRST)
            if (split.hasRecallTarget && split.lead.isNotBlank() && split.detail.isNotBlank()) {
                out.add("$indent$normalizedPrefix**${split.lead}** -> ${split.detail}")
            } else {
                out.add("$indent$normalizedPrefix$body")
            }
        }
        return out.joinToString("\n").trim()
    }

    private fun swapLeadDetail(text: String): String {
        return text.lines().joinToString("\n") { raw ->
            val indent = raw.takeWhile { it == ' ' || it == '\t' }
            val t = raw.trim()
            val listPrefixMatch = Regex("""^((?:\d+[.)]|[-*•‣▪])\s+)(.+)$""").find(t)
            val prefix = listPrefixMatch?.groupValues?.get(1).orEmpty()
            val body = listPrefixMatch?.groupValues?.get(2) ?: t
            val pair = splitDirectKeyValue(body, KnowledgeLayerOrder.TERM_FIRST)
            if (pair != null && pair.lead.isNotBlank() && pair.detail.isNotBlank()) {
                "$indent$prefix**${pair.detail}** -> ${pair.lead}"
            } else {
                raw
            }
        }
    }

    private fun mergeNestedBullets(text: String): String {
        val lines = text.lines()
        val out = mutableListOf<String>()
        var insideNumberedCard = false
        for (raw in lines) {
            val t = raw.trim()
            if (t.isEmpty()) {
                insideNumberedCard = false
                out.add("")
                continue
            }
            when {
                t.startsWith("#") || t.startsWith(">") || t.startsWith("|") -> {
                    insideNumberedCard = false
                    out.add(t)
                }
                Regex("""^\d+[.)]\s+.+""").matches(t) -> {
                    insideNumberedCard = true
                    out.add(t)
                }
                insideNumberedCard && Regex("""^[-*•‣▪]\s+.+""").matches(t) -> {
                    out.add("  - ${t.drop(1).trim()}")
                }
                insideNumberedCard && raw.startsWith("  ") -> {
                    out.add(raw)
                }
                insideNumberedCard && !t.contains("->") -> {
                    out.add("  - $t")
                }
                else -> {
                    insideNumberedCard = false
                    out.add(t)
                }
            }
        }
        return out.joinToString("\n").trim()
    }

    private fun splitSemicolons(text: String): String {
        val out = mutableListOf<String>()
        for (raw in text.lines()) {
            val t = raw.trim()
            if (t.contains(";") && !t.startsWith("|")) {
                val listPrefixMatch = Regex("""^((?:\d+[.)]|[-*•‣▪])\s+)(.+)$""").find(t)
                val prefix = listPrefixMatch?.groupValues?.get(1) ?: "1. "
                val body = listPrefixMatch?.groupValues?.get(2) ?: t
                val parts = body.split(";").map { it.trim() }.filter { it.isNotBlank() }
                if (parts.size >= 2) {
                    val splitFirst = splitDirectKeyValue(parts.first(), KnowledgeLayerOrder.AUTO_DETECT)
                    if (splitFirst != null) {
                        out.add("$prefix**${splitFirst.lead}** -> ${splitFirst.detail}")
                    } else {
                        out.add("$prefix${parts.first()}")
                    }
                    parts.drop(1).forEach { sub ->
                        out.add("  - $sub")
                    }
                } else {
                    out.add(raw)
                }
            } else {
                out.add(raw)
            }
        }
        return out.joinToString("\n").trim()
    }

    private data class SplitPairResult(
        val lead: String,
        val detail: String,
        val hasRecallTarget: Boolean,
        val isReversed: Boolean = false
    )

    private fun splitLeadAndDetail(content: String, layerOrder: KnowledgeLayerOrder): SplitPairResult {
        // Explicit bold lead: **Term** : / -> / — Detail
        val boldMatch = Regex("""^\*\*(.+?)\*\*\s*(?:[:\-–—=]|->|=>)?\s*(.*)$""").find(content)
        if (boldMatch != null && boldMatch.groupValues[2].isNotBlank()) {
            val left = cleanMarkdownDecorators(boldMatch.groupValues[1])
            val right = cleanMarkdownDecorators(boldMatch.groupValues[2])
            return orientPair(left, right, layerOrder, explicitBoldLeft = true)
        }

        // Trailing bold concept (backwards ChatGPT style): Explanation... -> **Concept**
        val trailingBoldMatch = Regex("""^(.+?)\s*(?:->|=>|[:\-–—])\s*\*\*(.+?)\*\*$""").find(content)
        if (trailingBoldMatch != null) {
            val left = cleanMarkdownDecorators(trailingBoldMatch.groupValues[1])
            val right = cleanMarkdownDecorators(trailingBoldMatch.groupValues[2])
            if (right.length <= 64 && left.length > right.length) {
                return SplitPairResult(lead = right, detail = left, hasRecallTarget = true, isReversed = true)
            }
        }

        val direct = splitDirectKeyValue(content, layerOrder)
        if (direct != null) return direct

        return SplitPairResult(
            lead = "",
            detail = cleanMarkdownDecorators(content),
            hasRecallTarget = false,
            isReversed = false
        )
    }

    private fun splitDirectKeyValue(line: String, layerOrder: KnowledgeLayerOrder): SplitPairResult? {
        val cleaned = line.trim()
        val separators = listOf(" -> ", " => ", " — ", " – ", ": ", " - ")
        for (sep in separators) {
            val idx = cleaned.indexOf(sep)
            if (idx >= 2 && idx < cleaned.length - sep.length) {
                val left = cleanMarkdownDecorators(cleaned.substring(0, idx))
                val right = cleanMarkdownDecorators(cleaned.substring(idx + sep.length))
                if (left.isNotBlank() && right.isNotBlank()) {
                    // Reject huge left sides unless backwards mode or short right side
                    val looksReversed = left.length > 55 && right.length in 2..48
                    if (left.length <= 80 || looksReversed || layerOrder == KnowledgeLayerOrder.BACKWARDS_ANSWER_FIRST) {
                        return orientPair(left, right, layerOrder, explicitBoldLeft = false)
                    }
                }
            }
        }
        return null
    }

    private fun orientPair(
        left: String,
        right: String,
        layerOrder: KnowledgeLayerOrder,
        explicitBoldLeft: Boolean
    ): SplitPairResult {
        return when (layerOrder) {
            KnowledgeLayerOrder.BACKWARDS_ANSWER_FIRST -> {
                if (right.length <= 68 && (left.length > right.length || !explicitBoldLeft)) {
                    SplitPairResult(lead = right, detail = left, hasRecallTarget = true, isReversed = true)
                } else {
                    SplitPairResult(lead = left, detail = right, hasRecallTarget = true, isReversed = false)
                }
            }
            KnowledgeLayerOrder.AUTO_DETECT -> {
                val autoReversed = !explicitBoldLeft && left.length > 52 && right.length in 2..42 && !right.endsWith(".")
                if (autoReversed) {
                    SplitPairResult(lead = right, detail = left, hasRecallTarget = true, isReversed = true)
                } else {
                    SplitPairResult(lead = left, detail = right, hasRecallTarget = true, isReversed = false)
                }
            }
            else -> SplitPairResult(lead = left, detail = right, hasRecallTarget = true, isReversed = false)
        }
    }

    private fun normalizeDelimiters(text: String): String {
        return text
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace(Regex("""\\\[(.+?)\\\]""", RegexOption.DOT_MATCHES_ALL)) { m -> "\$${m.groupValues[1]}\$" }
            .replace(Regex("""\\\((.+?)\\\)""", RegexOption.DOT_MATCHES_ALL)) { m -> "\$${m.groupValues[1]}\$" }
    }

    private fun leadingSpaces(line: String): Int {
        var count = 0
        for (ch in line) {
            when (ch) {
                ' ' -> count++
                '\t' -> count += 2
                else -> break
            }
        }
        return count
    }

    private fun countIndentLevel(line: String): Int {
        return (leadingSpaces(line) / 2).coerceIn(1, 3)
    }

    private fun looksLikeFormula(text: String): Boolean {
        return (text.contains("$") && text.count { it == '$' } >= 2) ||
            (text.contains("=") && text.length <= 90 && !text.contains("http"))
    }

    private fun cleanMarkdownDecorators(text: String): String {
        return text
            .replace(Regex("""\*\*\*(.+?)\*\*\*"""), "$1")
            .replace(Regex("""\*\*(.+?)\*\*"""), "$1")
            .replace(Regex("""__(.+?)__"""), "$1")
            .replace(Regex("""`([^`]+)`"""), "$1")
            .trim()
    }

    fun generatePrintableHtml(note: StudyNoteEntity): String {
        val rule = note.parsedFormatRule()
        val blocks = parseBlocks(note.rawContent, rule)
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
                    .oneliner, .multicard { padding: 10px 12px; margin: 8px 0; background: #F8FAFC; border: 1px solid #E2E8F0; border-radius: 6px; }
                    .oneliner.star, .multicard.star { border-left: 4px solid #F59E0B; background: #FFFBEB; }
                    .idx { font-weight: 700; color: #0D9488; margin-right: 6px; }
                    .lead { font-weight: 700; color: #0F172A; }
                    .formula { margin-top: 6px; padding: 5px 8px; background: #F1F5F9; border-radius: 4px; font-family: monospace; font-size: 12px; }
                    ul.nested { margin: 6px 0 2px 18px; padding-left: 8px; }
                    ul.nested li { margin: 3px 0; font-size: 13px; }
                    .callout { padding: 10px 12px; background: #EFF6FF; border-left: 4px solid #2563EB; border-radius: 6px; margin: 10px 0; }
                    table { width: 100%; border-collapse: collapse; margin: 12px 0; font-size: 12.5px; }
                    th, td { border: 1px solid #CBD5E1; padding: 6px 8px; text-align: left; }
                    th { background: #F1F5F9; font-weight: 700; }
                    p { margin: 8px 0; font-size: 13.5px; }
                  </style>
                </head>
                <body>
                  <div class="header">
                    <span class="badge">${esc(note.effectiveBadgeLabel())}</span>
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
                    is StudyNoteBlock.MultiLinerCard -> {
                        val starClass = if (block.blockIndex in bookmarked) " star" else ""
                        val starMark = if (block.blockIndex in bookmarked) "★ " else ""
                        append("<div class='multicard$starClass'>")
                        append("<div><span class='idx'>$starMark${esc(block.displayIndex)}.</span><span class='lead'>${esc(block.title)}</span></div>")
                        block.paragraphs.forEach { p ->
                            append("<p style='margin:5px 0 3px 0;'>${esc(p)}</p>")
                        }
                        if (block.formula.isNotBlank()) {
                            append("<div class='formula'>${esc(block.formula)}</div>")
                        }
                        if (block.nestedBullets.isNotEmpty()) {
                            append("<ul class='nested'>")
                            block.nestedBullets.forEach { (_, txt) ->
                                append("<li>${esc(txt)}</li>")
                            }
                            append("</ul>")
                        }
                        append("</div>\n")
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
