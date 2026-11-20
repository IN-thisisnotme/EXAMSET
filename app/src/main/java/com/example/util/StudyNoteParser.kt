package com.example.util

import com.example.data.StudyNoteEntity
import com.example.model.FlashcardPromptStyle
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
        val isReversed: Boolean = false,
        val isQuestionCard: Boolean = false,
        val questionType: String = "",
        val statusTag: String = "",
        val candidateAnswer: String = "",
        val optionsList: List<Pair<String, String>> = emptyList(),
        val correctAnswer: String = "",
        val keyTakeaway: String = ""
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
                val joined = calloutLines.joinToString("\n")
                val splitColon = joined.split(":", limit = 2)
                val rawPrefixCleaned = splitColon[0]
                    .replace(Regex("""^\s*[*_`]+|[*_`]+\s*$"""), "")
                    .trim()
                val candidateLabel = cleanMarkdownDecorators(rawPrefixCleaned)
                val hasLabel = splitColon.size == 2 && candidateLabel.isNotBlank() && candidateLabel.length <= 28
                val label = if (hasLabel) candidateLabel else "Key Insight"
                val body = if (hasLabel) {
                    splitColon[1]
                        .replace(Regex("""^\s*[*_]{1,3}\s*"""), "")
                        .trim()
                } else {
                    joined.trim()
                }
                blocks.add(StudyNoteBlock.Callout(blockCounter++, label, body))
                i = j
                continue
            }

            // 5. Q: / A: multi-line (and multi-paragraph) pair
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
                    if (nextTrim.isEmpty()) {
                        val nextNonEmptyIdx = findNextNonEmptyLine(lines, j + 1)
                        if (nextNonEmptyIdx == -1) break
                        val lookAhead = lines[nextNonEmptyIdx].trim()
                        if (isQaBlockBoundary(lookAhead, leadingSpaces(lines[nextNonEmptyIdx]), rule.layerOrder)) break
                        j = nextNonEmptyIdx
                        continue
                    }
                    if (isQaBlockBoundary(nextTrim, leadingSpaces(nextRaw), rule.layerOrder)) break

                    // Multi-line display math block $$...$$ or \[...\]
                    if (isDisplayMathStart(nextTrim)) {
                        val (mathBlock, nextAfterMath) = collectDisplayMathBlock(lines, j)
                        if (formula.isBlank()) formula = mathBlock else paragraphs.add(mathBlock)
                        j = nextAfterMath
                        continue
                    }

                    val aMatch = Regex("""^(?:A(?:ns(?:wer)?)?|Explanation|Detail)\s*[:.\-]\s*(.+)$""", RegexOption.IGNORE_CASE).find(nextTrim)
                    val qaBulletMatch = Regex("""^[-*•‣▪]\s+(.+)$""").find(nextTrim)
                    if (aMatch != null) {
                        paragraphs.add(aMatch.groupValues[1].trim())
                    } else if (qaBulletMatch != null) {
                        val bulletBody = qaBulletMatch.groupValues[1].trim()
                        val formulaBullet = Regex("""^(?:\*\*|__)?(?:Formula|Equation)(?:\*\*|__)?\s*[:\-]\s*(.+)$""", RegexOption.IGNORE_CASE).find(bulletBody)
                        if (formulaBullet != null && formula.isBlank()) {
                            formula = cleanMarkdownDecorators(formulaBullet.groupValues[1])
                        } else {
                            nested.add(countIndentLevel(nextRaw) to bulletBody)
                        }
                    } else {
                        if (looksLikeFormula(nextTrim) && formula.isBlank()) formula = nextTrim
                        else paragraphs.add(nextTrim)
                    }
                    j++
                }
                if (paragraphs.isNotEmpty() || nested.isNotEmpty() || formula.isNotBlank()) {
                    blocks.add(
                        StudyNoteBlock.MultiLinerCard(
                            blockIndex = blockCounter++,
                            displayIndex = "${factSeq++}",
                            title = qText,
                            badge = if (rule.collectionType == KnowledgeCollectionType.LONG_ANSWER) "Long Q&A" else "Q & A",
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

            // 6. Numbered or top-level bulleted item (possibly followed by nested sub-bullets / multi-paragraph explanation)
            val listMatch = Regex("""^(?:(\d{1,4})[.)]|[-*•‣▪])\s+(.+)$""").find(trimmed)
            if (listMatch != null) {
                val baseIndent = leadingSpaces(rawLine)
                val explicitNum = listMatch.groupValues[1]
                val displayIdx = if (explicitNum.isNotBlank()) explicitNum else "$factSeq"
                factSeq++
                val content = listMatch.groupValues[2].trim()

                // Look ahead for indented sub-bullets, Formula/Example/Why lines, or multi-paragraph continuation lines
                val nestedBullets = mutableListOf<Pair<Int, String>>()
                val extraParagraphs = mutableListOf<String>()
                var formula = ""
                var parsedQuestionType = ""
                var parsedStatusTag = ""
                var parsedCandidateAnswer = ""
                val parsedOptions = mutableListOf<Pair<String, String>>()
                var parsedCorrectAnswer = ""
                var parsedKeyTakeaway = ""
                var j = i + 1

                if (rule.preserveNestedBullets || rule.collectionType == KnowledgeCollectionType.LONG_ANSWER) {
                    while (j < lines.size) {
                        val subRaw = lines[j]
                        val subTrim = subRaw.trim()
                        if (subTrim.isEmpty()) {
                            val nextNonEmptyIdx = findNextNonEmptyLine(lines, j + 1)
                            if (nextNonEmptyIdx == -1) break
                            val lookRaw = lines[nextNonEmptyIdx]
                            val lookTrim = lookRaw.trim()
                            val lookIndent = leadingSpaces(lookRaw)
                            if (isStructuralBoundary(lookTrim)) break
                            val isNextSiblingList = lookIndent <= baseIndent &&
                                Regex("""^(?:\d{1,4}[.)]|[-*•‣▪])\s+""").containsMatchIn(lookTrim)
                            val isNextDirectKeyValue = lookIndent <= baseIndent &&
                                splitDirectKeyValue(lookTrim, rule.layerOrder) != null
                            if (isNextSiblingList || isNextDirectKeyValue) break

                            val isLongFormContinuation = lookIndent > baseIndent ||
                                isDisplayMathStart(lookTrim) ||
                                matchTagSubLine(lookTrim) != null ||
                                rule.collectionType == KnowledgeCollectionType.LONG_ANSWER ||
                                rule.collectionType == KnowledgeCollectionType.CONCEPT_CODEX
                            if (!isLongFormContinuation) break
                            j = nextNonEmptyIdx
                            continue
                        }
                        if (isStructuralBoundary(subTrim)) break

                        if (isDisplayMathStart(subTrim)) {
                            val (mathBlock, nextAfterMath) = collectDisplayMathBlock(lines, j)
                            if (formula.isBlank()) formula = mathBlock else extraParagraphs.add(mathBlock)
                            j = nextAfterMath
                            continue
                        }

                        val subIndent = leadingSpaces(subRaw)
                        val isSubBullet = Regex("""^(?:[-*•‣▪◦]|\d+[.)]|[a-zA-Z][.)])\s+(.+)$""").find(subTrim)
                        val isIndentedMore = subIndent > baseIndent
                        val isTagSubLine = matchTagSubLine(subTrim)

                        if (isIndentedMore && isSubBullet != null) {
                            val level = ((subIndent - baseIndent) / 2).coerceIn(1, 3)
                            val bulletRaw = isSubBullet.groupValues[1].trim()
                            val bulletTextClean = cleanMarkdownDecorators(bulletRaw)
                            val optMatch = Regex("""^\(([A-Ea-e])\)\s+(.+)$""").find(bulletTextClean)
                            val qMetaMatch = Regex("""^(Question Type|Type|Status|Correct Answer|Key Takeaway|Explanation|Solution|Formula|Equation)\s*:\s*(.+)$""", RegexOption.IGNORE_CASE).find(bulletTextClean)

                            when {
                                optMatch != null && level == 1 -> {
                                    parsedOptions.add(optMatch.groupValues[1].uppercase() to optMatch.groupValues[2].trim())
                                }
                                qMetaMatch != null -> {
                                    val key = qMetaMatch.groupValues[1].trim().lowercase()
                                    val rawBody = bulletRaw.substringAfter(":").trim()
                                    val cleanValue = qMetaMatch.groupValues[2].trim()
                                    when (key) {
                                        "type", "question type" -> parsedQuestionType = cleanValue
                                        "status" -> {
                                            parsedStatusTag = cleanValue
                                            val marked = Regex("""Marked\s+([A-Ea-e0-9\-]+)""", RegexOption.IGNORE_CASE).find(cleanValue)
                                            if (marked != null) {
                                                parsedCandidateAnswer = marked.groupValues[1].uppercase()
                                            }
                                        }
                                        "correct answer" -> parsedCorrectAnswer = cleanValue
                                        "key takeaway", "explanation", "solution" -> {
                                            parsedKeyTakeaway = appendSectionText(parsedKeyTakeaway, rawBody)
                                        }
                                        "formula", "equation" -> formula = cleanValue
                                    }
                                }
                                looksLikeFormula(bulletTextClean) && formula.isBlank() -> {
                                    formula = bulletTextClean
                                }
                                else -> {
                                    nestedBullets.add(level to bulletRaw)
                                }
                            }
                            j++
                        } else if (isTagSubLine != null) {
                            val tag = cleanMarkdownDecorators(subTrim.substringBefore(":").substringBefore("-").trim())
                            val body = isTagSubLine.groupValues[1].trim()
                            when {
                                tag.equals("Formula", ignoreCase = true) || tag.equals("Equation", ignoreCase = true) -> formula = cleanMarkdownDecorators(body)
                                tag.equals("Type", ignoreCase = true) || tag.equals("Question Type", ignoreCase = true) -> parsedQuestionType = cleanMarkdownDecorators(body)
                                tag.equals("Status", ignoreCase = true) -> parsedStatusTag = cleanMarkdownDecorators(body)
                                tag.equals("Correct Answer", ignoreCase = true) || tag.equals("Answer", ignoreCase = true) -> parsedCorrectAnswer = cleanMarkdownDecorators(body)
                                tag.equals("Key Takeaway", ignoreCase = true) || tag.equals("Explanation", ignoreCase = true) || tag.equals("Solution", ignoreCase = true) -> {
                                    parsedKeyTakeaway = appendSectionText(parsedKeyTakeaway, body)
                                }
                                else -> nestedBullets.add(1 to "**$tag:** $body")
                            }
                            j++
                        } else if (isIndentedMore ||
                            ((rule.collectionType == KnowledgeCollectionType.LONG_ANSWER || rule.collectionType == KnowledgeCollectionType.CONCEPT_CODEX) &&
                                !Regex("""^(?:\d{1,4}[.)]|[-*•‣▪])\s+""").containsMatchIn(subTrim) &&
                                splitDirectKeyValue(subTrim, rule.layerOrder) == null)
                        ) {
                            if (looksLikeFormula(subTrim) && formula.isBlank()) formula = subTrim
                            else extraParagraphs.add(subTrim)
                            j++
                        } else {
                            break
                        }
                    }
                }

                // Detect question / remediation card (`**Q1: <stem>**` or `Q1: <stem>` or weak-area structured block)
                val cleanedFullContent = cleanMarkdownDecorators(content)
                val questionPrefixMatch = Regex("""^(Q\d+)\s*[:.]\s*(.+)$""", RegexOption.IGNORE_CASE).find(cleanedFullContent)
                val isQuestionStructuredCard = questionPrefixMatch != null ||
                    parsedStatusTag.isNotBlank() ||
                    parsedCorrectAnswer.isNotBlank() ||
                    parsedOptions.isNotEmpty() ||
                    (parsedKeyTakeaway.isNotBlank() && rule.collectionType != KnowledgeCollectionType.LONG_ANSWER)

                if (isQuestionStructuredCard) {
                    val qBadge = questionPrefixMatch?.groupValues?.get(1)?.uppercase() ?: "Q$displayIdx"
                    val qStem = questionPrefixMatch?.groupValues?.get(2)?.trim() ?: cleanedFullContent
                    val resolvedType = parsedQuestionType.ifBlank {
                        inferQuestionTypeLabel(qStem, parsedOptions, formula, rule.collectionType)
                    }
                    blocks.add(
                        StudyNoteBlock.MultiLinerCard(
                            blockIndex = blockCounter++,
                            displayIndex = qBadge,
                            title = qStem,
                            badge = resolvedType,
                            paragraphs = extraParagraphs.distinct(),
                            nestedBullets = nestedBullets,
                            formula = formula,
                            isReversed = false,
                            isQuestionCard = true,
                            questionType = resolvedType,
                            statusTag = parsedStatusTag,
                            candidateAnswer = parsedCandidateAnswer,
                            optionsList = parsedOptions,
                            correctAnswer = parsedCorrectAnswer,
                            keyTakeaway = parsedKeyTakeaway
                        )
                    )
                    i = j
                    continue
                }

                val splitResult = splitLeadAndDetail(content, rule.layerOrder)
                val useMultiLiner = nestedBullets.isNotEmpty() ||
                    extraParagraphs.isNotEmpty() ||
                    formula.isNotBlank() ||
                    splitResult.detail.length > 155 ||
                    rule.collectionType == KnowledgeCollectionType.MULTI_LINER ||
                    rule.collectionType == KnowledgeCollectionType.CONCEPT_CODEX ||
                    rule.collectionType == KnowledgeCollectionType.LONG_ANSWER

                if (useMultiLiner) {
                    val cardTitle = splitResult.lead.ifBlank {
                        splitResult.detail.take(88).let { if (splitResult.detail.length > 88) "$it…" else it }
                    }
                    val cardParagraphs = buildList {
                        if (splitResult.lead.isNotBlank() && splitResult.detail.isNotBlank()) {
                            add(splitResult.detail)
                        } else if (splitResult.lead.isBlank() && splitResult.detail.isNotBlank() && splitResult.detail.length > 88) {
                            add(splitResult.detail)
                        } else if (splitResult.lead.isBlank() && splitResult.detail.isNotBlank() && extraParagraphs.isEmpty() && nestedBullets.isEmpty()) {
                            add(splitResult.detail)
                        }
                        addAll(extraParagraphs)
                    }
                    val resolvedType = parsedQuestionType.ifBlank {
                        when {
                            rule.collectionType == KnowledgeCollectionType.LONG_ANSWER -> "Long-Form Q&A"
                            rule.collectionType == KnowledgeCollectionType.FLASHCARDS -> "Flashcard"
                            formula.isNotBlank() -> "Formula & Concept"
                            rule.collectionType == KnowledgeCollectionType.FORMULA_SHEET -> "Formula"
                            else -> "Concept Card"
                        }
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
                            isReversed = splitResult.isReversed,
                            questionType = resolvedType,
                            keyTakeaway = parsedKeyTakeaway
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

            // 7b. Standalone multi-line display math block ($$...$$ or \[...\])
            if (isDisplayMathStart(trimmed)) {
                val (mathBlock, nextAfterMath) = collectDisplayMathBlock(lines, i)
                blocks.add(StudyNoteBlock.Paragraph(blockCounter++, mathBlock))
                i = nextAfterMath
                continue
            }

            // 8. Standard Paragraph (merge consecutive plain lines in the same paragraph)
            val paraLines = mutableListOf(trimmed)
            var j = i + 1
            while (j < lines.size) {
                val nxt = lines[j].trim()
                if (nxt.isEmpty() ||
                    nxt.startsWith("#") ||
                    nxt.startsWith(">") ||
                    nxt.startsWith("|") ||
                    nxt.startsWith("```") ||
                    isDisplayMathStart(nxt) ||
                    Regex("""^(?:\d+[.)]|[-*•‣▪])\s+""").containsMatchIn(nxt) ||
                    splitDirectKeyValue(nxt, rule.layerOrder) != null
                ) break
                paraLines.add(nxt)
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
        rule: KnowledgeFormatRule,
        examTarget: String = "",
        subject: String = "",
        itemCount: Int? = null,
        includeBilingualTerms: Boolean = false,
        includePyqTags: Boolean = false
    ): String {
        val cleanTopic = topic.trim().ifBlank { "[ENTER YOUR SUBJECT / TOPIC HERE]" }
        val cleanSubject = subject.trim()
        val cleanExam = examTarget.trim()
        val collectionTitle = rule.customCollectionLabel.trim().ifBlank { collectionType.label }
        val targetLang = rule.language.ifBlank { "English" }
        val isOneLinerMode = collectionType == KnowledgeCollectionType.ONE_LINERS
        val isTableMode = collectionType == KnowledgeCollectionType.COMPARISON_TABLE
        val isLongAnswerMode = collectionType == KnowledgeCollectionType.LONG_ANSWER
        val isFlashcardMode = collectionType == KnowledgeCollectionType.FLASHCARDS
        val resolvedCount = itemCount?.takeIf { it > 0 }

        if (isFlashcardMode) {
            val defaultStyle = when (rule.layerOrder) {
                KnowledgeLayerOrder.QA_SEPARATE_LINES -> FlashcardPromptStyle.QA_RETRIEVAL
                KnowledgeLayerOrder.BACKWARDS_ANSWER_FIRST -> FlashcardPromptStyle.CLOZE_SCENARIO
                else -> FlashcardPromptStyle.TERM_DEFINITION
            }
            return compileFlashcardPrompt(
                topic = topic,
                subject = subject,
                examTarget = examTarget,
                itemCount = resolvedCount ?: 20,
                style = defaultStyle,
                depth = rule.depth,
                language = targetLang,
                includeBilingualTerms = includeBilingualTerms,
                includeMnemonics = rule.includeCallouts,
                includeExamples = rule.preserveNestedBullets,
                includeLatexMath = rule.includeLatexMath,
                includeExamTraps = includePyqTags || rule.includeCallouts,
                atomicSrsMode = true,
                customInstruction = rule.customInstruction
            )
        }

        val roleLine = if (cleanExam.isNotBlank()) {
            "Act as a senior $cleanExam educator and curriculum designer."
        } else {
            "Act as a senior competitive-exam educator and curriculum designer."
        }

        val depthInstruction = when {
            isOneLinerMode -> "Keep every item ultra-crisp and high-yield on a single line (<= 140 characters after `->`) so One-Liner recall cards stay concise."
            isLongAnswerMode -> when (rule.depth) {
                KnowledgeDepth.CRISP -> "Write structured ~150-word model answers with a crisp Introduction, bulleted Core Arguments (with Constitutional Articles/Data/Examples), and a 2-line Conclusion."
                KnowledgeDepth.BALANCED -> "Write comprehensive ~250-word UPSC Mains–grade model answers with contextual Introduction, multi-dimensional Body sub-headings, supporting data/cases/equations, Challenges, and Way Forward."
                KnowledgeDepth.DEEP -> "Write exhaustive 400+ word analytical model answers covering historical/constitutional context, multi-dimensional arguments, counter-arguments, empirical data, derivations/LaTeX where relevant, and actionable Way Forward."
            }
            isTableMode -> when (rule.depth) {
                KnowledgeDepth.CRISP -> "Keep every table cell ultra-concise (3–8 words) for instant side-by-side revision."
                KnowledgeDepth.BALANCED -> "Keep every table cell clear and structured (<= 12 words per cell) highlighting core mechanisms, differences, and exam traps."
                KnowledgeDepth.DEEP -> "Include rich technical precision inside concise single-line table cells (exceptions, threshold values, examples) followed by `> Exam Tip:` takeaways."
            }
            else -> when (rule.depth) {
                KnowledgeDepth.CRISP -> "Keep every item crisp, high-yield, and exam-focused (1–2 lines max per takeaway)."
                KnowledgeDepth.BALANCED -> "Use balanced multi-layer structure: bold key concept followed by clear core reasoning and 1–2 nested sub-points where helpful."
                KnowledgeDepth.DEEP -> "Provide deep conceptual breakdowns with definitions, mechanisms, edge-cases, exceptions, and exam traps."
            }
        }

        val layerInstruction = when {
            isTableMode -> "Format comparison entries strictly as clean single-line Markdown table rows (`| Feature / Parameter | Item A | Item B |`) with a `| --- |` header separator."
            isLongAnswerMode -> when (rule.layerOrder) {
                KnowledgeLayerOrder.TERM_FIRST -> "Start each item as `1. **<Question / Directive>**` followed by multi-paragraph structured answer sections (`  - Introduction:`, `  - Core Arguments:`, `  - Way Forward:`)."
                KnowledgeLayerOrder.BACKWARDS_ANSWER_FIRST -> "Start with the core analytical conclusion/scenario clue followed by `-> **Core Concept / Theme**` and structured analytical paragraphs underneath (`  - Analytical Dimensions:`, `  - Way Forward:`)."
                else -> "Format each long-form question cleanly as `Q1: <Full Question / UPSC Directive>` on one line, followed on the next line by `A: <Introduction>` and multi-paragraph structured sections (`**Core Analytical Dimensions:**`, `  - Point ...`, `**Way Forward:**`)."
            }
            isOneLinerMode && rule.layerOrder == KnowledgeLayerOrder.AUTO_DETECT ->
                "Always put the Key Term / Concept FIRST in **bold**, followed by `->` and the concise 1-line explanation (`1. **Concept** -> Crisp fact`)."
            else -> when (rule.layerOrder) {
                KnowledgeLayerOrder.TERM_FIRST -> "Always put the Key Term / Title FIRST in **bold**, followed by `->` and the concise explanation (e.g., `1. **Article 32** -> Right to Constitutional Remedies`)."
                KnowledgeLayerOrder.BACKWARDS_ANSWER_FIRST -> "Format for active recall: state the scenario/symptom/clue FIRST, followed by `->` and the exact **Concept / Answer** in **bold** at the end of the line (keep trailing **Concept** short, <= 6 words)."
                KnowledgeLayerOrder.QA_SEPARATE_LINES -> "Format each item as `Q: <concept question>` on one line, followed IMMEDIATELY on the very next line by `A: <concise explanation>` with optional 2-space indented `- ` bullets (NO blank line between `Q:` and `A:`; leave one blank line between separate Q&A items)."
                KnowledgeLayerOrder.AUTO_DETECT -> "Use clean numbered items (`1. **Concept** -> Core Fact`) with optional 2-space indented `- sub-point` bullets for multi-liners."
            }
        }

        val countPrefix = resolvedCount?.let { "Generate exactly $it" }
        val styleRule = when (collectionType) {
            KnowledgeCollectionType.ONE_LINERS -> "${countPrefix ?: "Generate 20–30"} ultra-dense exam one-liners grouped under `## Section Headings`."
            KnowledgeCollectionType.MULTI_LINER -> "${countPrefix ?: "Generate 10–15"} Multi-Liner Concept Cards. Each numbered item has a **Concept Title** and 2–3 indented `- sub-points` (`- Mechanism:`, `- Example:`, `- Exception:`)."
            KnowledgeCollectionType.CONCEPT_CODEX -> "${resolvedCount?.let { "Generate exactly $it structured concept entries grouped under 4–8 `## Topic Headings` in" } ?: "Generate"} a comprehensive Concept Codex with `## Topic Headings`, **Core Principles**, indented sub-bullets, and `> Exam Trap:` callouts."
            KnowledgeCollectionType.LONG_ANSWER -> "${countPrefix ?: "Generate 3–5"} full-length subjective / UPSC Mains–level Q&A cards grouped under `## Theme Headings`, without compressing or truncating multi-paragraph answers."
            KnowledgeCollectionType.COMPARISON_TABLE -> {
                if (resolvedCount != null && resolvedCount >= 25) {
                    "Cover $resolvedCount key comparison rows split cleanly across 2–5 focused Markdown tables under `## Section Headings` (`| Feature | Item A | Item B |`) followed by `> Key Difference:` insights."
                } else {
                    "${resolvedCount?.let { "Cover $it key comparison rows across" } ?: "Structure the core comparison as"} clean Markdown tables (`| Feature | Item A | Item B |`) followed by `> Key Difference:` insights."
                }
            }
            KnowledgeCollectionType.FORMULA_SHEET -> "${resolvedCount?.let { "Generate exactly $it important laws/formulas" } ?: "List every important law/formula"} grouped under `## Section Headings` with `  - Formula:`, `  - Units & Conditions:`, and numerical pitfall lines."
            KnowledgeCollectionType.FLASHCARDS -> "${countPrefix ?: "Generate 20"} atomic spaced-repetition flashcards grouped under `## Section Headings`."
            KnowledgeCollectionType.WEAK_AREA_REVIEW -> "${countPrefix ?: "Create 12–20"} targeted remediation cards using `  - Trap:`, `  - Correct Rule:`, and `  - Memory Anchor:` sub-lines to eliminate look-alike mistakes."
            KnowledgeCollectionType.CUSTOM -> "${resolvedCount?.let { "Generate exactly $it items formatted" } ?: "Structure the output"} cleanly as a '$collectionTitle' sheet tailored to exam mastery."
        }

        val sampleTemplate = buildSampleTemplate(
            collectionType = collectionType,
            layerOrder = rule.layerOrder,
            includeLatexMath = rule.includeLatexMath,
            includeCallouts = rule.includeCallouts,
            includePyqTags = includePyqTags
        )

        return buildString {
            appendLine(roleLine)
            appendLine("Create a high-yield **$collectionTitle** study sheet for: $cleanTopic")
            if (cleanSubject.isNotBlank() && !cleanSubject.equals(cleanTopic, ignoreCase = true)) {
                appendLine("Subject Domain: $cleanSubject")
            }
            if (cleanExam.isNotBlank()) {
                appendLine("Target Examination: $cleanExam")
            }
            appendLine("Output Language: $targetLang")
            appendLine()
            appendLine("ACCURACY & COMPLETION STANDARD:")
            appendLine("- Accuracy Guard: Verify all constitutional articles, statutory sections, dates, SI units, physical constants, reactions, and clinical thresholds; never fabricate citations.")
            if ((resolvedCount != null && resolvedCount >= 25) || rule.depth == KnowledgeDepth.DEEP || isLongAnswerMode) {
                val targetTotal = resolvedCount?.toString() ?: "final"
                appendLine("- Completion Parity: Maintain uniform depth and quality from item 1 through item $targetTotal; never summarize, compress, skip later items, or end with 'etc.'.")
            }
            appendLine()
            appendLine("STRICT FORMATTING RULES (so ExamSet parses 100% cleanly):")
            appendLine("1. $styleRule")
            appendLine("2. $depthInstruction")
            appendLine("3. $layerInstruction")
            var ruleNum = 4
            when {
                isOneLinerMode -> appendLine("$ruleNum. Keep each numbered fact on a single crisp line with NO nested sub-bullets so One-Liner recall cards parse cleanly.")
                isTableMode -> appendLine("$ruleNum. Keep every table row on a single line; NEVER insert nested bullets or raw unescaped `|` characters inside table cells (use words or `\\lvert x \\rvert`).")
                isLongAnswerMode -> appendLine("$ruleNum. Inside each `Q:` / `A:` or numbered question card, you may use multiple paragraphs and 2-space indented `- sub-points`; only start a new numbered card / `Q2:` / `## Heading` when beginning the next question.")
                rule.preserveNestedBullets -> appendLine("$ruleNum. For multi-layer concepts, indent sub-points with exactly 2 spaces (`  - Sub-point`, `  - Formula: ...`, `  - Key Takeaway: ...`) directly under the parent item with no blank lines inside a card.")
                else -> appendLine("$ruleNum. Avoid nested sub-bullets; keep each fact concise without child lists.")
            }
            ruleNum++
            if (rule.includeLatexMath) {
                if (isLongAnswerMode) {
                    appendLine("$ruleNum. Wrap inline mathematical symbols/equations in `\$...\$` and multi-line derivations, matrices, or stacked fractions in display `\$\$...\$\$` blocks (including `\\ce{...}` for chemistry); keep LaTeX commands in ASCII and never wrap the whole response in code fences.")
                } else {
                    appendLine("$ruleNum. Wrap all mathematical formulas, chemical symbols, and equations in single-line inline `\$...\$` ONLY (keep LaTeX commands and variables in ASCII). NEVER use `\$\$...\$\$`, `\\\\[...\\\\]`, `\\\\(...\\\\)`, multi-line math blocks, or code blocks.")
                }
            } else {
                appendLine("$ruleNum. Write formulas in clean plain Unicode text without LaTeX delimiters.")
            }
            ruleNum++
            if (rule.includeCallouts) {
                appendLine("$ruleNum. Highlight high-risk exam traps and memory mnemonics using flush-left `> Exam Tip: ...` blockquotes between concepts.")
                ruleNum++
            }
            if (rule.includeComparisonTable && !isTableMode) {
                appendLine("$ruleNum. Include at least one Markdown comparison table (`| Parameter | ... |`); keep cells single-line without raw unescaped `|` inside cell text.")
                ruleNum++
            }
            if (includeBilingualTerms && !targetLang.equals("English", ignoreCase = true)) {
                appendLine("$ruleNum. Keep standard technical/scientific terms in English inside parentheses alongside $targetLang explanations (without splitting `->` delimiters or modifying `\$...\$` LaTeX).")
                ruleNum++
            }
            if (includePyqTags) {
                when {
                    isTableMode -> appendLine("$ruleNum. Append concise exam-relevance or PYQ markers (e.g., `[High-Yield]` or `[PYQ Focus]`) inside comparison notes or `> Key Difference:` insights where applicable.")
                    rule.layerOrder == KnowledgeLayerOrder.BACKWARDS_ANSWER_FIRST -> appendLine("$ruleNum. Append concise relevance or PYQ markers (e.g., `[High-Yield]` or `[PYQ Focus]`) at the end of the left-side clue BEFORE `->` or on a sub-bullet—NEVER after trailing `**Concept**`.")
                    else -> appendLine("$ruleNum. Append concise exam-relevance or PYQ markers (e.g., `[High-Yield]` or `[PYQ Focus]`) at the end of the explanation line—never between **Term** and `->`.")
                }
                ruleNum++
            }
            appendLine("$ruleNum. Output ONLY the structured notes directly (no intro chatter, no closing remarks, no outer ```markdown``` wrapper).")
            appendLine()
            appendLine("EXACT OUTPUT EXAMPLE (FOLLOW THIS SYNTAX LINE-FOR-LINE):")
            appendLine(sampleTemplate)
            if (rule.customInstruction.isNotBlank()) {
                appendLine()
                appendLine("USER CUSTOM REQUIREMENT:")
                appendLine(rule.customInstruction.trim())
            }
        }.trim()
    }

    fun buildSampleTemplate(
        collectionType: KnowledgeCollectionType,
        layerOrder: KnowledgeLayerOrder,
        includeLatexMath: Boolean = true,
        includeCallouts: Boolean = true,
        includePyqTags: Boolean = false
    ): String {
        val pyqSuffix = if (includePyqTags) " [PYQ Focus]" else ""
        val sampleLine = when (layerOrder) {
            KnowledgeLayerOrder.BACKWARDS_ANSWER_FIRST ->
                "1. Rapid voltage spike across an inductive load opposing current change$pyqSuffix -> **Lenz's Law**"
            KnowledgeLayerOrder.QA_SEPARATE_LINES ->
                "Q: What governs induced EMF direction?\nA: Lenz's Law — induced EMF always opposes the flux change producing it.$pyqSuffix"
            else ->
                "1. **Lenz's Law** -> Induced EMF always opposes the magnetic flux change causing it.$pyqSuffix"
        }
        val faradayFormula = if (includeLatexMath) "\$\\mathcal{E} = -N \\frac{d\\Phi_B}{dt}\$" else "ℰ = -N (dΦB / dt)"
        val thermoFormula = if (includeLatexMath) "\$\\Delta U = Q - W\$" else "ΔU = Q - W"
        val thermoCond = if (includeLatexMath) "Joules (J); \$W > 0\$ when work is done by the system." else "Joules (J); W > 0 when work is done by the system."
        val isoWork = if (includeLatexMath) "\$nRT \\ln(V_2/V_1)\$" else "nRT ln(V2/V1)"
        val adiaWork = if (includeLatexMath) "\$-\\Delta U\$" else "-ΔU"
        val tempSym = if (includeLatexMath) "Temperature (\$T\$)" else "Temperature (T)"
        val heatSym = if (includeLatexMath) "Entropy / Heat (\$Q = 0\$)" else "Entropy / Heat (Q = 0)"
        val isoZero = if (includeLatexMath) "\$\\Delta U = 0\$" else "ΔU = 0"
        val fiscalMath = if (includeLatexMath) "\$\$\\text{Fiscal Deficit} = \\text{Total Expenditure} - \\text{Total Receipts (excl. borrowings)}\$\$" else "Fiscal Deficit = Total Expenditure - Total Receipts (excl. borrowings)"

        return when (collectionType) {
            KnowledgeCollectionType.LONG_ANSWER -> buildString {
                appendLine("## Governance & Economic Framework")
                when (layerOrder) {
                    KnowledgeLayerOrder.TERM_FIRST -> {
                        appendLine("1. **Examine the significance of Fiscal Responsibility and Budget Management (FRBM) in balancing macroeconomic stability with capital expenditure.**$pyqSuffix")
                        appendLine("  - Introduction: Enacted in 2003, the FRBM framework institutionalizes fiscal prudence to contain public debt and anchor inflation expectations.")
                        appendLine("  - Core Arguments: Targets fiscal glide paths while preserving counter-cyclical capital outlay.")
                        appendLine(fiscalMath)
                        append("  - Way Forward: Adopt N.K. Singh Committee escape-clause transparency, medium-term debt ceilings, and outcome-linked CapEx.")
                    }
                    KnowledgeLayerOrder.BACKWARDS_ANSWER_FIRST -> {
                        appendLine("1. Institutionalizes fiscal glide paths to cap revenue deficit while safeguarding productive capital outlay$pyqSuffix -> **FRBM Framework (2003)**")
                        appendLine("  - Core Analytical Dimensions: Debt stabilization, crowding-in private CapEx, and counter-cyclical flexibility.")
                        appendLine(fiscalMath)
                        append("  - Way Forward: Operationalize N.K. Singh Committee escape clauses with medium-term debt anchors.")
                    }
                    else -> {
                        appendLine("Q1: Examine the significance of Fiscal Responsibility and Budget Management (FRBM) in balancing macroeconomic stability with capital expenditure.$pyqSuffix")
                        appendLine("A: **Introduction:** Enacted in 2003, the FRBM framework institutionalizes fiscal prudence to contain public debt and anchor inflation expectations.")
                        appendLine()
                        appendLine("**Core Analytical Dimensions:**")
                        appendLine("  - **Debt & Deficit Anchors:** Targets fiscal glide paths while preserving space for counter-cyclical public investment.")
                        appendLine("  - **Quality of Expenditure:** Distinguishes revenue deficit from capital outlay driving long-term multiplier growth.")
                        appendLine(fiscalMath)
                        appendLine()
                        append("**Way Forward:** Adopt N.K. Singh Committee escape-clause transparency, medium-term debt ceilings, and outcome-linked CapEx.")
                    }
                }
                if (includeCallouts) {
                    appendLine()
                    appendLine()
                    append("> Exam Tip: Substantiate Mains answers with committee names (N.K. Singh), constitutional provisions (Art. 112/292), and balanced pros/cons.")
                }
            }.trimIndent()
            KnowledgeCollectionType.COMPARISON_TABLE -> buildString {
                appendLine("## Key Comparison")
                appendLine("| Parameter | Isothermal | Adiabatic |")
                appendLine("| --- | --- | --- |")
                appendLine("| Constant | $tempSym | $heatSym |")
                append("| Work Done | $isoWork | $adiaWork |")
                if (includeCallouts) {
                    appendLine()
                    appendLine()
                    append("> Exam Tip: Adiabatic curve is always steeper than isothermal at the same state point.$pyqSuffix")
                }
            }.trimIndent()
            KnowledgeCollectionType.FORMULA_SHEET -> buildString {
                appendLine("## Core Laws & Equations")
                when (layerOrder) {
                    KnowledgeLayerOrder.QA_SEPARATE_LINES -> {
                        appendLine("Q: State the First Law of Thermodynamics and its standard work convention.$pyqSuffix")
                        appendLine("A: Conservation of internal energy across heat transfer and thermodynamic work.")
                    }
                    KnowledgeLayerOrder.BACKWARDS_ANSWER_FIRST -> {
                        appendLine("1. Law equating internal energy change to heat supplied minus work done by the system$pyqSuffix -> **First Law of Thermodynamics**")
                    }
                    else -> {
                        appendLine("1. **First Law of Thermodynamics** -> Conservation of internal energy, heat, and work.$pyqSuffix")
                    }
                }
                appendLine("  - Formula: $thermoFormula")
                append("  - Units & Conditions: $thermoCond")
                if (includeCallouts) {
                    appendLine()
                    append("> Exam Tip: For an isolated system, $isoZero always holds.")
                }
            }.trimIndent()
            KnowledgeCollectionType.FLASHCARDS -> buildFlashcardSampleTemplate(
                style = when (layerOrder) {
                    KnowledgeLayerOrder.QA_SEPARATE_LINES -> FlashcardPromptStyle.QA_RETRIEVAL
                    KnowledgeLayerOrder.BACKWARDS_ANSWER_FIRST -> FlashcardPromptStyle.CLOZE_SCENARIO
                    else -> FlashcardPromptStyle.TERM_DEFINITION
                },
                includeLatexMath = includeLatexMath,
                includeMnemonics = includeCallouts,
                includeExamples = true,
                includeExamTraps = includePyqTags || includeCallouts
            )
            KnowledgeCollectionType.ONE_LINERS -> {
                val secondLine = when (layerOrder) {
                    KnowledgeLayerOrder.BACKWARDS_ANSWER_FIRST ->
                        "2. Enforces Fundamental Rights via five Supreme Court writs$pyqSuffix -> **Article 32**"
                    KnowledgeLayerOrder.QA_SEPARATE_LINES ->
                        "Q: Which Constitutional Article grants Supreme Court writ jurisdiction for Fundamental Rights?\nA: Article 32 — empowers the Supreme Court to issue five constitutional writs.$pyqSuffix"
                    else ->
                        "2. **Article 32** -> Right to Constitutional Remedies; enforceable via five Supreme Court writs.$pyqSuffix"
                }
                """
                ## High-Yield One-Liners
                $sampleLine
                $secondLine
                """.trimIndent()
            }
            KnowledgeCollectionType.WEAK_AREA_REVIEW -> buildString {
                appendLine("## High-Yield Mistake & Trap Log")
                appendLine(sampleLine)
                appendLine("  - Trap: Assuming the negative sign means negative EMF magnitude.")
                appendLine("  - Correct Rule: Minus sign indicates direction opposing changing flux ($faradayFormula).")
                append("  - Memory Anchor: Energy conservation forbids self-reinforcing flux growth.")
                if (includeCallouts) {
                    appendLine()
                    append("> Exam Tip: Always verify direction using right-hand grip rule before sign selection.")
                }
            }.trimIndent()
            else -> buildString {
                appendLine("## Core Concept Card")
                appendLine(sampleLine)
                appendLine("  - Formula: $faradayFormula")
                append("  - Exam Trap: Negative sign denotes opposition, not negative magnitude.")
                if (includeCallouts) {
                    appendLine()
                    append("> Exam Tip: Conservation of energy underpins Lenz's Law.")
                }
            }.trimIndent()
        }
    }

    fun compileFlashcardPrompt(
        topic: String,
        subject: String = "",
        examTarget: String = "",
        itemCount: Int = 20,
        style: FlashcardPromptStyle = FlashcardPromptStyle.QA_RETRIEVAL,
        depth: KnowledgeDepth = KnowledgeDepth.BALANCED,
        language: String = "English",
        includeBilingualTerms: Boolean = false,
        includeMnemonics: Boolean = true,
        includeExamples: Boolean = true,
        includeLatexMath: Boolean = true,
        includeExamTraps: Boolean = true,
        atomicSrsMode: Boolean = true,
        customInstruction: String = ""
    ): String {
        val cleanTopic = topic.trim().ifBlank { "[ENTER DECK TOPIC / CHAPTER HERE]" }
        val cleanSubject = subject.trim()
        val cleanExam = examTarget.trim()
        val targetLang = language.trim().ifBlank { "English" }
        val safeCount = itemCount.coerceIn(1, 100)

        val roleLine = if (cleanExam.isNotBlank()) {
            "Act as a senior cognitive-science educator and spaced-repetition flashcard designer for $cleanExam."
        } else {
            "Act as a senior cognitive-science educator and spaced-repetition flashcard designer."
        }

        val formatInstruction = when (style) {
            FlashcardPromptStyle.QA_RETRIEVAL ->
                "Format EVERY flashcard strictly with `Q<N>: <Focused active-recall prompt>` on line 1, followed immediately on line 2 by `A: <Direct core answer>` (zero blank line between Q and A; one blank line between separate flashcards)."
            FlashcardPromptStyle.TERM_DEFINITION ->
                "Format EVERY flashcard strictly as `<N>. **<Front Term / Law / Entity>** -> <Crisp, unambiguous back definition/rule>`."
            FlashcardPromptStyle.CLOZE_SCENARIO ->
                "Format EVERY flashcard for reverse active recall: `<N>. <Scenario, symptom, condition, or diagnostic clue> -> **<Exact Target Concept / Rule>**` (keep trailing **Concept** <= 6 words)."
            FlashcardPromptStyle.FORMULA_TRAP ->
                "Format EVERY flashcard strictly as `<N>. **<Law / Equation Name>** -> <What it relates & when it applies>`, followed on the next line by `  - Formula: <single-line equation>`."
        }

        val atomicityRule = if (atomicSrsMode) {
            "Minimum-Information Principle (SM-2 Ready): Test ONE atomic fact, causal link, threshold, or formula per card. Keep Front prompts short (1 line, <= 22 words) and never give away the answer keyword in the question."
        } else {
            "Group tightly related sub-facts onto unified recall cards where conceptual contrast aids retention."
        }

        val depthRule = when (depth) {
            KnowledgeDepth.CRISP -> "Keep every Back answer razor-sharp (<= 25 words) for rapid <10-second spaced-repetition reviews."
            KnowledgeDepth.BALANCED -> "Give a decisive 1-sentence core Back answer plus up to 1–2 concise 2-space indented helper bullets."
            KnowledgeDepth.DEEP -> "Include core retrieval answer plus crisp indented helper bullets covering mechanism, boundary conditions, and clinical/exam caveats."
        }

        val sample = buildFlashcardSampleTemplate(
            style = style,
            includeLatexMath = includeLatexMath,
            includeMnemonics = includeMnemonics,
            includeExamples = includeExamples,
            includeExamTraps = includeExamTraps
        )

        return buildString {
            appendLine(roleLine)
            appendLine("Create a high-yield **Spaced Repetition Flashcard Deck** of exactly **$safeCount cards** for: $cleanTopic")
            if (cleanSubject.isNotBlank() && !cleanSubject.equals(cleanTopic, ignoreCase = true)) {
                appendLine("Subject Domain: $cleanSubject")
            }
            if (cleanExam.isNotBlank()) {
                appendLine("Target Examination: $cleanExam")
            }
            appendLine("Output Language: $targetLang")
            appendLine()
            appendLine("SPACED REPETITION QUALITY & PARITY RULES:")
            appendLine("1. Card Count & Progression: Output strictly all $safeCount flashcards organized under 2–5 logical `## Deck Section` headings; maintain identical accuracy and card rigor from card 1 to card $safeCount (never summarize or write 'etc.').")
            appendLine("2. $atomicityRule")
            appendLine("3. $formatInstruction")
            appendLine("4. $depthRule")
            var ruleIdx = 5
            val enabledExtras = buildList {
                if (includeLatexMath && style != FlashcardPromptStyle.FORMULA_TRAP) add("`  - Formula: ...`")
                if (includeMnemonics) add("`  - Mnemonic: ...` (only when genuinely memorable)")
                if (includeExamples) add("`  - Example: ...`")
                if (includeExamTraps) add("`  - Exam Trap: ...` (common look-alike confuser)")
            }
            if (enabledExtras.isNotEmpty()) {
                appendLine("$ruleIdx. Optional 2-space indented back-of-card helpers directly under the answer (no blank lines inside a card): ${enabledExtras.joinToString(", ")}.")
            } else {
                appendLine("$ruleIdx. Keep flashcards strictly 2 lines / 1 line without extra sub-bullets for rapid-fire drilling.")
            }
            ruleIdx++
            if (includeLatexMath) {
                appendLine("$ruleIdx. Wrap equations and chemical symbols in single-line inline `\$...\$` LaTeX (keep commands ASCII, e.g. `\$\\Delta G = \\Delta H - T\\Delta S\$`). Never use `\$\$...\$\$` or outer code fences.")
            } else {
                appendLine("$ruleIdx. Write math/symbols in plain Unicode without LaTeX delimiters.")
            }
            ruleIdx++
            if (includeBilingualTerms && !targetLang.equals("English", ignoreCase = true)) {
                appendLine("$ruleIdx. Retain standard English terminology in parentheses alongside $targetLang text without altering `->`, `Q:`, or `\$...\$` syntax.")
                ruleIdx++
            }
            appendLine("$ruleIdx. Output ONLY the structured flashcard deck directly (zero conversational intro, zero closing tips, no markdown code fence).")
            appendLine()
            appendLine("EXACT OUTPUT EXAMPLE (FOLLOW THIS SYNTAX LINE-FOR-LINE):")
            appendLine(sample)
            if (customInstruction.isNotBlank()) {
                appendLine()
                appendLine("USER CUSTOM REQUIREMENT:")
                appendLine(customInstruction.trim())
            }
        }.trim()
    }

    fun buildFlashcardSampleTemplate(
        style: FlashcardPromptStyle,
        includeLatexMath: Boolean = true,
        includeMnemonics: Boolean = true,
        includeExamples: Boolean = true,
        includeExamTraps: Boolean = true
    ): String {
        val gibbsEq = if (includeLatexMath) "\$\\Delta G = \\Delta H - T\\Delta S\$" else "ΔG = ΔH - TΔS"
        val pHFormula = if (includeLatexMath) "\$\\text{pH} = \\text{p}K_a + \\log\\frac{[\\text{A}^-]}{[\\text{HA}]}\$" else "pH = pKa + log([A-]/[HA])"

        return buildString {
            appendLine("## Core Active-Recall Deck")
            when (style) {
                FlashcardPromptStyle.QA_RETRIEVAL -> {
                    appendLine("Q1: Under what thermodynamic condition is a process spontaneous at all temperatures?")
                    appendLine("A: When enthalpy is exothermic (\$\\Delta H < 0\$) and entropy increases (\$\\Delta S > 0\$), making \$\\Delta G\$ always negative.".let {
                        if (includeLatexMath) it else "A: When ΔH < 0 (exothermic) and ΔS > 0 (entropy increases), making ΔG always negative."
                    })
                    if (includeLatexMath) appendLine("  - Formula: $gibbsEq")
                    if (includeExamTraps) appendLine("  - Exam Trap: Exothermic alone does NOT guarantee spontaneity at high T if ΔS < 0.")
                    appendLine()
                    appendLine("Q2: Which Supreme Court writ stops a lower tribunal from exceeding its jurisdiction?")
                    append("A: Writ of Prohibition — issued before a final order is pronounced.")
                    if (includeMnemonics) append("\n  - Mnemonic: Prohibition = Prevent; Certiorari = Correct/Quash after order.")
                }
                FlashcardPromptStyle.TERM_DEFINITION -> {
                    appendLine("1. **Henderson–Hasselbalch Equation** -> Relates buffer solution pH to acid pKa and conjugate base/acid ratio.")
                    if (includeLatexMath) appendLine("  - Formula: $pHFormula")
                    if (includeExamples) appendLine("  - Example: Half-equivalence point gives pH = pKa.")
                    if (includeExamTraps) append("  - Exam Trap: Fails for very dilute (<10⁻⁶ M) or strongly acidic buffers.")
                    appendLine()
                    append("2. **Article 143 (Constitution)** -> Authorizes President to seek advisory opinion from Supreme Court; non-binding except pre-constitution treaties.")
                    if (includeMnemonics) append("\n  - Mnemonic: 1-4-3 = Advisory plea from President to SC.")
                }
                FlashcardPromptStyle.CLOZE_SCENARIO -> {
                    appendLine("1. Unilateral shift in buffer pH equals pKa when [A-] equals [HA] ratio of 1:1 -> **Half-Equivalence Point**")
                    if (includeLatexMath) appendLine("  - Formula: $pHFormula")
                    if (includeExamTraps) append("  - Exam Trap: Maximum buffer capacity occurs strictly at pH = pKa.")
                    appendLine()
                    append("2. Supreme Court advisory opinion binding on pre-1950 treaty disputes but optional otherwise -> **Article 143(2) vs 143(1)**")
                    if (includeMnemonics) append("\n  - Mnemonic: 143(1) Optional advice; 143(2) Obligatory SC answer.")
                }
                FlashcardPromptStyle.FORMULA_TRAP -> {
                    appendLine("1. **Gibbs Free Energy Criterion** -> Predicts reaction spontaneity at constant temperature and pressure.")
                    appendLine("  - Formula: $gibbsEq")
                    appendLine("  - Exam Trap: Negative ΔG indicates thermodynamic favorability, NOT reaction speed (kinetics).")
                    if (includeExamples) appendLine("  - Example: Diamond -> Graphite has ΔG < 0 yet proceeds negligibly slowly at 298 K.")
                    appendLine()
                    appendLine("2. **Henderson–Hasselbalch Equation** -> Relates buffer solution pH to pKa and salt-to-acid ratio.")
                    append("  - Formula: $pHFormula")
                    if (includeMnemonics) append("\n  - Mnemonic: Base/Salt stays UP in the log numerator.")
                }
            }
        }.trimIndent()
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

    private fun inferQuestionTypeLabel(
        stem: String,
        options: List<Pair<String, String>>,
        formula: String,
        collectionType: KnowledgeCollectionType = KnowledgeCollectionType.ONE_LINERS
    ): String {
        val lower = stem.lowercase()
        return when {
            lower.contains("assertion") && lower.contains("reason") -> "Assertion–Reason"
            lower.contains("match the following") || lower.contains("list i") && lower.contains("list ii") -> "Match Type"
            lower.contains("statement i") || lower.contains("consider the following statements") -> "Statement MCQ"
            options.isEmpty() && collectionType == KnowledgeCollectionType.LONG_ANSWER -> "Long-Form Q&A"
            formula.isNotBlank() || lower.contains("calculate") || lower.contains("ratio of") || lower.contains("numerical") -> "Numerical MCQ"
            options.isEmpty() -> "Concept Question"
            else -> "Multiple Choice"
        }
    }

    fun buildWeakAreaNoteContent(
        paper: QuestionPaper,
        incorrectNums: Set<Int>,
        unattendedNums: Set<Int>,
        answers: Map<Int, String>,
        rollNumber: String? = null,
        bookletNumber: String? = null,
        bookletCode: String? = null,
        attemptId: Long? = null,
        attemptTimestamp: Long? = null,
        score: Double? = null,
        totalMarks: Double? = null
    ): String {
        val targetQuestions = paper.questions.filter {
            it.num in incorrectNums || it.num in unattendedNums
        }
        if (targetQuestions.isEmpty()) {
            return "## ${paper.metadata.title}\n> All Questions Mastered: No incorrect or skipped items in this test attempt."
        }

        val resolvedRoll = rollNumber?.takeIf { it.isNotBlank() }
            ?: paper.metadata.rollNumber.takeIf { it.isNotBlank() }
            ?: "UNASSIGNED"
        val resolvedBooklet = bookletNumber?.takeIf { it.isNotBlank() }
            ?: paper.metadata.bookletNumber.takeIf { it.isNotBlank() }
            ?: "0000001"
        val resolvedCode = bookletCode?.takeIf { it.isNotBlank() }
            ?: paper.metadata.setNumber.takeIf { it.isNotBlank() }
            ?: "Set A"

        val dateStamp = if (attemptTimestamp != null && attemptTimestamp > 0L) {
            java.text.SimpleDateFormat("MMM dd, yyyy • HH:mm", java.util.Locale.US).format(java.util.Date(attemptTimestamp))
        } else {
            java.text.SimpleDateFormat("MMM dd, yyyy • HH:mm", java.util.Locale.US).format(java.util.Date())
        }

        val attemptLabel = if (attemptId != null && attemptId > 0L) " • Attempt #$attemptId" else ""
        val scoreLabel = if (score != null) {
            val maxStr = if (totalMarks != null && totalMarks > 0.0) String.format(java.util.Locale.US, " / %.2f", totalMarks) else ""
            " • Score: " + String.format(java.util.Locale.US, "%.2f", score) + maxStr
        } else ""

        return buildString {
            appendLine("## Weak-Area Remediation: ${paper.metadata.title}")
            appendLine("> **Review Identification:** Roll No: `$resolvedRoll` • Booklet No: `$resolvedBooklet` • Series: `$resolvedCode`$attemptLabel • Evaluated: $dateStamp")
            appendLine("> **Diagnostic Summary:** ${incorrectNums.size} Incorrect • ${unattendedNums.size} Skipped • Subject: ${paper.metadata.subject}$scoreLabel")
            appendLine()

            var seq = 1
            targetQuestions.forEach { q ->
                val markedAns = answers[q.num]?.trim().orEmpty()
                val statusTag = if (q.num in incorrectNums) {
                    val markedDisplay = if (markedAns.isNotBlank()) "Candidate Marked: $markedAns" else "Incorrect"
                    "Incorrect ($markedDisplay)"
                } else {
                    "Skipped"
                }
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
                val nonBlankOptions = q.options.asList.filter { it.second.isNotBlank() }
                val inferredType = q.questionType?.takeIf { it.isNotBlank() }
                    ?: inferQuestionTypeLabel(cleanStem, nonBlankOptions, q.formula.orEmpty())

                appendLine("${seq++}. **Q${q.num}: $cleanStem**")
                appendLine("  - Question Type: $inferredType")
                appendLine("  - Status: $statusTag")
                nonBlankOptions.forEach { (optLetter, optText) ->
                    val cleanOpt = optText.replace(Regex("""\s+"""), " ").trim()
                    appendLine("  - ($optLetter) $cleanOpt")
                }
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

    /**
     * Compiles a unique, deduplicated Master Active Recall Knowledge Set for an entire Subject or Course
     * from all available study materials:
     * - Multiple Question Papers (explanations, formulas, question concepts, and options)
     * - Historical Weak-Area / Mistake logs
     * - Existing Study Notes and One-Liners
     */
    fun compileSubjectActiveRecallSet(
        subjectName: String,
        papers: List<QuestionPaper>,
        existingNotes: List<StudyNoteEntity>,
        courseOrFolderTitle: String? = null
    ): String {
        val cleanSubject = subjectName.trim().ifBlank { courseOrFolderTitle?.trim().orEmpty() }.ifBlank { "General Studies" }
        val dateStamp = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.US).format(java.util.Date())

        val deduplicatedCards = mutableListOf<CompiledActiveRecallItem>()
        val seenConceptKeys = mutableSetOf<String>()

        fun normalizeKey(text: String): String {
            return text.lowercase()
                .replace(Regex("""[^a-z0-9]"""), "")
                .take(60)
        }

        // 1. Ingest from Question Papers (MCQs with rich explanations, formulas, and high-yield questions)
        papers.forEach { paper ->
            paper.questions.forEach { q ->
                val cleanStem = q.question.replace(Regex("""\s+"""), " ").trim()
                if (cleanStem.isBlank()) return@forEach
                val key = normalizeKey(cleanStem)
                if (key.length >= 6 && !seenConceptKeys.add(key)) {
                    // Duplicate concept already captured
                    return@forEach
                }

                val correctOpt = when (q.correctAnswer?.uppercase()) {
                    "A" -> q.options.a
                    "B" -> q.options.b
                    "C" -> q.options.c
                    "D" -> q.options.d
                    "E" -> q.options.e
                    else -> ""
                }.trim()

                val takeaway = q.explanation?.trim().orEmpty()
                val formula = q.formula?.trim().orEmpty()
                val ansLine = if (q.correctAnswer != null) {
                    if (correctOpt.isNotBlank()) "(${q.correctAnswer.uppercase()}) $correctOpt" else q.correctAnswer.uppercase()
                } else ""

                deduplicatedCards.add(
                    CompiledActiveRecallItem(
                        prompt = cleanStem,
                        recallAnswer = ansLine,
                        keyTakeaway = takeaway,
                        formula = formula,
                        source = "${paper.metadata.title.take(30)} • Q${q.num}",
                        topic = paper.metadata.topic.takeIf { it.isNotBlank() } ?: cleanSubject
                    )
                )
            }
        }

        // 2. Ingest from Existing Notes & Study Materials (One-liners, Markdown bullet notes, cards)
        existingNotes.forEach { note ->
            val parsedBlocks = parseBlocks(note.rawContent, note.parsedFormatRule())
            parsedBlocks.forEach { block ->
                when (block) {
                    is StudyNoteBlock.OneLiner -> {
                        val key = normalizeKey(block.leadTerm)
                        if (key.length >= 4 && seenConceptKeys.add(key)) {
                            deduplicatedCards.add(
                                CompiledActiveRecallItem(
                                    prompt = block.leadTerm,
                                    recallAnswer = block.detail,
                                    keyTakeaway = "",
                                    formula = "",
                                    source = note.title.take(30),
                                    topic = note.subject.ifBlank { cleanSubject }
                                )
                            )
                        }
                    }
                    is StudyNoteBlock.MultiLinerCard -> {
                        val key = normalizeKey(block.title)
                        if (key.length >= 6 && seenConceptKeys.add(key)) {
                            deduplicatedCards.add(
                                CompiledActiveRecallItem(
                                    prompt = block.title,
                                    recallAnswer = block.correctAnswer.ifBlank { block.paragraphs.firstOrNull().orEmpty() },
                                    keyTakeaway = block.keyTakeaway,
                                    formula = block.formula,
                                    source = note.title.take(30),
                                    topic = note.subject.ifBlank { cleanSubject }
                                )
                            )
                        }
                    }
                    else -> { /* Skip headings and tables from raw ingestion */ }
                }
            }
        }

        if (deduplicatedCards.isEmpty()) {
            return "## Master Active Recall: $cleanSubject\n> Compiled Course Study Set • Generated on $dateStamp\n> No question items or study notes found for this subject."
        }

        // Group by topic or present in sequential high-yield order
        return buildString {
            appendLine("## Master Active Recall: $cleanSubject")
            appendLine("> **Course Compilation:** Compiled from ${papers.size} Question Sets and ${existingNotes.size} Study Notes")
            appendLine("> **Total Active Recall Cards:** ${deduplicatedCards.size} unique high-yield cards (deduplicated) • Compiled on $dateStamp")
            appendLine()

            val byTopic = deduplicatedCards.groupBy { it.topic.ifBlank { cleanSubject } }
            var cardSeq = 1

            byTopic.forEach { (topicName, items) ->
                appendLine("### Topic: $topicName")
                appendLine()
                items.forEach { card ->
                    appendLine("${cardSeq++}. **${card.prompt}**")
                    if (card.recallAnswer.isNotBlank()) {
                        appendLine("  - Correct Answer: ${card.recallAnswer}")
                    }
                    if (card.formula.isNotBlank()) {
                        appendLine("  - Formula: ${card.formula}")
                    }
                    if (card.keyTakeaway.isNotBlank()) {
                        appendLine("  - Key Takeaway: ${card.keyTakeaway}")
                    }
                    if (card.source.isNotBlank()) {
                        appendLine("  - Source Material: ${card.source}")
                    }
                    appendLine()
                }
            }
        }.trim()
    }

    private data class CompiledActiveRecallItem(
        val prompt: String,
        val recallAnswer: String,
        val keyTakeaway: String,
        val formula: String,
        val source: String,
        val topic: String
    )

    fun convertNoteBlocksToMcqDraft(note: StudyNoteEntity): String {
        val blocks = parseBlocks(note.rawContent, note.parsedFormatRule())
        val starred = note.getBookmarkedIndices()
        val candidates = blocks.filter {
            (it is StudyNoteBlock.OneLiner && it.leadTerm.isNotBlank() && it.detail.isNotBlank()) ||
                (it is StudyNoteBlock.MultiLinerCard && it.title.isNotBlank() &&
                    (it.paragraphs.isNotEmpty() || it.nestedBullets.isNotEmpty() || it.correctAnswer.isNotBlank() || it.keyTakeaway.isNotBlank()))
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
                is StudyNoteBlock.MultiLinerCard -> blk.correctAnswer.take(90).trim().takeIf { it.isNotBlank() }
                    ?: blk.paragraphs.firstOrNull()?.take(90)?.trim()
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
                if (blk is StudyNoteBlock.MultiLinerCard && blk.isQuestionCard && blk.optionsList.size >= 2 && blk.correctAnswer.isNotBlank()) {
                    val ansLetterMatch = Regex("""^\(?([A-E])\)?""").find(blk.correctAnswer.trim().uppercase())
                    val ansLetter = ansLetterMatch?.groupValues?.get(1) ?: "A"
                    appendLine("Q$qNum. ${blk.title}")
                    blk.optionsList.forEach { (l, txt) -> appendLine("($l) $txt") }
                    appendLine("Correct Answer: $ansLetter")
                    val expText = blk.keyTakeaway.ifBlank { blk.correctAnswer }
                    appendLine("Explanation: $expText")
                    appendLine()
                } else {
                    val (prompt, correct, explanation) = when (blk) {
                        is StudyNoteBlock.OneLiner -> Triple(
                            "Which of the following accurately describes \"${blk.leadTerm}\"?",
                            blk.detail,
                            "${blk.leadTerm}: ${blk.detail}"
                        )
                        is StudyNoteBlock.MultiLinerCard -> {
                            val mainDetail = blk.correctAnswer.takeIf { it.isNotBlank() }
                                ?: blk.paragraphs.firstOrNull()
                                ?: blk.nestedBullets.firstOrNull()?.second
                                ?: blk.title
                            val fullExp = (listOfNotNull(
                                blk.correctAnswer.takeIf { it.isNotBlank() },
                                blk.keyTakeaway.takeIf { it.isNotBlank() }
                            ) + blk.paragraphs + blk.nestedBullets.map { it.second }).joinToString(" | ")
                            Triple(
                                if (blk.isQuestionCard) blk.title else "With reference to \"${blk.title}\", identify the accurate statement:",
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
            .replace(Regex("""\\\((.+?)\\\)""", RegexOption.DOT_MATCHES_ALL)) { m -> "\$${m.groupValues[1]}\$" }
    }

    private fun findNextNonEmptyLine(lines: List<String>, startIdx: Int): Int {
        var idx = startIdx
        while (idx < lines.size) {
            if (lines[idx].trim().isNotEmpty()) return idx
            idx++
        }
        return -1
    }

    private fun isStructuralBoundary(trimmed: String): Boolean {
        return trimmed.startsWith("#") ||
            trimmed.startsWith("|") ||
            trimmed.startsWith(">") ||
            trimmed == "---" ||
            trimmed == "***" ||
            trimmed.startsWith("```") ||
            Regex("""^(?:Q(?:\d+)?|Question)\s*[:.\-]\s*""", RegexOption.IGNORE_CASE).containsMatchIn(trimmed) ||
            (Regex("""^(?:TOPIC|SECTION|UNIT|CHAPTER|PART|MODULE)\s*[\dIVXA-Z]*\s*[:\-]\s*.+""", RegexOption.IGNORE_CASE).matches(trimmed) && trimmed.length <= 120)
    }

    private fun isQaBlockBoundary(trimmed: String, indent: Int, layerOrder: KnowledgeLayerOrder): Boolean {
        if (isStructuralBoundary(trimmed)) return true
        if (indent == 0 && Regex("""^\d{1,4}[.)]\s+""").containsMatchIn(trimmed)) {
            if (splitDirectKeyValue(trimmed.replaceFirst(Regex("""^\d{1,4}[.)]\s+"""), ""), layerOrder) != null) {
                return true
            }
            if (Regex("""^\d{1,4}[.)]\s+\*\*.+?\*\*""").containsMatchIn(trimmed)) {
                return true
            }
        }
        return false
    }

    private fun isDisplayMathStart(trimmed: String): Boolean {
        return trimmed.startsWith("$$") || trimmed.startsWith("\\[")
    }

    private fun collectDisplayMathBlock(lines: List<String>, startIdx: Int): Pair<String, Int> {
        val firstTrim = lines[startIdx].trim()
        val isDollarDisplay = firstTrim.startsWith("$$")
        val endDelimiter = if (isDollarDisplay) "$$" else "\\]"
        val startOffset = if (isDollarDisplay) 2 else 2

        if (firstTrim.length > startOffset && firstTrim.endsWith(endDelimiter)) {
            return firstTrim to (startIdx + 1)
        }
        val buf = mutableListOf(firstTrim)
        var j = startIdx + 1
        while (j < lines.size) {
            val lineTrim = lines[j].trim()
            buf.add(lineTrim)
            if (lineTrim.endsWith(endDelimiter) || lineTrim.contains(endDelimiter)) {
                j++
                break
            }
            j++
        }
        return buf.joinToString("\n") to j
    }

    private fun matchTagSubLine(trimmed: String): MatchResult? {
        return Regex(
            """^(?:\*\*|__)?(?:Formula|Equation|Example|E\.g\.|Note|Why|Trick|Mnemonic|Hint|Memory Hook|Exam Trap|Pitfall|Trap|Application|Rule|Cloze|Answer|Correct Answer|Key Takeaway|Explanation|Solution|Status|Type|Question Type|Concept|Takeaway|Introduction|Body|Conclusion|Way Forward|Arguments|Challenges|Significance|Case Study|Keywords|Data|Committee|Criticism|Analysis|Derivation|Proof)(?:\*\*|__)?\s*[:\-]\s*(.+)$""",
            RegexOption.IGNORE_CASE
        ).find(trimmed)
    }

    private fun appendSectionText(existing: String, addition: String): String {
        val cleanAdd = addition.trim()
        if (cleanAdd.isBlank()) return existing
        return if (existing.isBlank()) cleanAdd else "$existing\n\n$cleanAdd"
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
        val t = text.trim()
        if (t.isEmpty()) return false
        val isPureDelimitedMath = (t.startsWith("$$") && t.endsWith("$$")) ||
            (t.startsWith("\\[") && t.endsWith("\\]")) ||
            (t.startsWith("$") && t.endsWith("$") && t.count { it == '$' } == 2)
        if (isPureDelimitedMath) return true
        return t.contains("=") && t.length <= 90 && !t.contains("http") && !t.contains(". ")
    }

    private fun cleanMarkdownDecorators(text: String): String {
        return text
            .replace(Regex("""\*\*\*(.+?)\*\*\*"""), "$1")
            .replace(Regex("""\*\*(.+?)\*\*"""), "$1")
            .replace(Regex("""__(.+?)__"""), "$1")
            .replace(Regex("""`([^`]+)`"""), "$1")
            .trim()
    }

    private fun formatRichHtml(raw: String): String {
        if (raw.isBlank()) return ""
        val withLatex = if (LatexHelper.hasLatex(raw)) LatexHelper.toRichTextString(raw) else raw
        val withSubSup = RichTextHelper.sanitizeSubSuper(withLatex)
        // Escape HTML first, then restore safe formatting tags emitted by LatexHelper/RichTextHelper and Markdown
        val escaped = withSubSup
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
        return escaped
            .replace(Regex("""&lt;(/?)(b|i|sub|sup)&gt;""", RegexOption.IGNORE_CASE), "<$1$2>")
            .replace(Regex("""\*\*\*(.+?)\*\*\*"""), "<strong><em>$1</em></strong>")
            .replace(Regex("""\*\*(.+?)\*\*"""), "<strong>$1</strong>")
            .replace(Regex("""(?<!_)__([^\s_](?:[\s\S]*?[^\s_])?)__(?!_)"""), "<strong>$1</strong>")
            .replace(Regex("""\*([^*\n]+?)\*"""), "<em>$1</em>")
            .replace(Regex("""`([^`\n]+?)`"""), "<code>$1</code>")
            .replace("\n", "<br/>")
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
                    body { font-family: 'Segoe UI', Roboto, Arial, sans-serif; color: #0F172A; margin: 28px; line-height: 1.6; }
                    .header { border-bottom: 2px solid #0D9488; padding-bottom: 12px; margin-bottom: 18px; }
                    .badge { display: inline-block; background: #CCFBF1; color: #0F766E; padding: 3px 9px; border-radius: 6px; font-size: 11px; font-weight: 700; }
                    h1 { margin: 6px 0 4px 0; font-size: 20px; }
                    h2 { font-size: 16px; color: #0F172A; margin: 18px 0 8px 0; border-left: 4px solid #2563EB; padding-left: 8px; }
                    h3 { font-size: 14px; color: #334155; margin: 12px 0 6px 0; }
                    .oneliner, .multicard { padding: 11px 13px; margin: 9px 0; background: #F8FAFC; border: 1px solid #E2E8F0; border-radius: 6px; }
                    .oneliner.star, .multicard.star { border-left: 4px solid #F59E0B; background: #FFFBEB; }
                    .idx { font-weight: 700; color: #0D9488; margin-right: 6px; }
                    .lead { font-weight: 700; color: #0F172A; }
                    .formula { margin: 8px 0; padding: 7px 10px; background: #EFF6FF; border-left: 3px solid #0D9488; border-radius: 4px; font-family: 'Cambria Math', 'Segoe UI', serif; font-size: 13px; }
                    ul.nested { margin: 6px 0 2px 18px; padding-left: 8px; }
                    ul.nested li { margin: 4px 0; font-size: 13px; }
                    .callout { padding: 10px 12px; background: #EFF6FF; border-left: 4px solid #2563EB; border-radius: 6px; margin: 10px 0; }
                    table { width: 100%; border-collapse: collapse; margin: 12px 0; font-size: 12.5px; }
                    th, td { border: 1px solid #CBD5E1; padding: 6px 8px; text-align: left; }
                    th { background: #F1F5F9; font-weight: 700; }
                    p { margin: 8px 0; font-size: 13.5px; }
                    code { background: #F1F5F9; padding: 1px 4px; border-radius: 3px; font-family: monospace; font-size: 12px; }
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
                        append("<$tag>${formatRichHtml(block.text)}</$tag>\n")
                    }
                    is StudyNoteBlock.OneLiner -> {
                        val starClass = if (block.blockIndex in bookmarked) " star" else ""
                        val starMark = if (block.blockIndex in bookmarked) "★ " else ""
                        append("<div class='oneliner$starClass'>")
                        append("<span class='idx'>$starMark${esc(block.displayIndex)}.</span>")
                        if (block.leadTerm.isNotBlank()) {
                            append("<span class='lead'>${formatRichHtml(block.leadTerm)}</span> — ")
                        }
                        append("${formatRichHtml(block.detail)}</div>\n")
                    }
                    is StudyNoteBlock.MultiLinerCard -> {
                        val starClass = if (block.blockIndex in bookmarked) " star" else ""
                        val starMark = if (block.blockIndex in bookmarked) "★ " else ""
                        append("<div class='multicard$starClass'>")
                        append("<div><span class='idx'>$starMark${esc(block.displayIndex)}.</span><span class='lead'>${formatRichHtml(block.title)}</span>")
                        if (block.questionType.isNotBlank()) {
                            append(" <span class='badge' style='margin-left:6px;'>${esc(block.questionType)}</span>")
                        }
                        if (block.statusTag.isNotBlank()) {
                            append(" <span class='badge' style='background:#FEE2E2;color:#991B1B;margin-left:4px;'>${esc(block.statusTag)}</span>")
                        }
                        append("</div>")
                        if (block.optionsList.isNotEmpty()) {
                            append("<ul class='nested' style='list-style-type:none;padding-left:0;'>")
                            block.optionsList.forEach { (l, txt) ->
                                append("<li><strong>(${esc(l)})</strong> ${formatRichHtml(txt)}</li>")
                            }
                            append("</ul>")
                        }
                        block.paragraphs.forEach { p ->
                            if (isDisplayMathStart(p.trim())) {
                                append("<div class='formula'>${formatRichHtml(p)}</div>")
                            } else {
                                append("<p style='margin:7px 0 5px 0;'>${formatRichHtml(p)}</p>")
                            }
                        }
                        if (block.correctAnswer.isNotBlank()) {
                            append("<p style='margin:6px 0 4px 0;color:#0F766E;'><strong>Correct Answer:</strong> ${formatRichHtml(block.correctAnswer)}</p>")
                        }
                        if (block.formula.isNotBlank()) {
                            append("<div class='formula'>${formatRichHtml(block.formula)}</div>")
                        }
                        if (block.keyTakeaway.isNotBlank()) {
                            append("<p style='margin:6px 0 4px 0;'><strong>Key Takeaway:</strong> ${formatRichHtml(block.keyTakeaway)}</p>")
                        }
                        if (block.nestedBullets.isNotEmpty()) {
                            append("<ul class='nested'>")
                            block.nestedBullets.forEach { (_, txt) ->
                                append("<li>${formatRichHtml(txt)}</li>")
                            }
                            append("</ul>")
                        }
                        append("</div>\n")
                    }
                    is StudyNoteBlock.Callout -> {
                        append("<div class='callout'><strong>${esc(block.label)}:</strong> ${formatRichHtml(block.body)}</div>\n")
                    }
                    is StudyNoteBlock.Table -> {
                        append("<table><thead><tr>")
                        block.headers.forEach { h -> append("<th>${formatRichHtml(h)}</th>") }
                        append("</tr></thead><tbody>")
                        block.rows.forEach { row ->
                            append("<tr>")
                            row.forEach { cell -> append("<td>${formatRichHtml(cell)}</td>") }
                            append("</tr>")
                        }
                        append("</tbody></table>\n")
                    }
                    is StudyNoteBlock.Paragraph -> {
                        if (isDisplayMathStart(block.text.trim())) {
                            append("<div class='formula'>${formatRichHtml(block.text)}</div>\n")
                        } else {
                            append("<p>${formatRichHtml(block.text)}</p>\n")
                        }
                    }
                }
            }
            append("</body></html>")
        }
    }
}
