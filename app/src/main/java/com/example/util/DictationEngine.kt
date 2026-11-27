package com.example.util

import kotlin.math.roundToLong

enum class DictationPunctuationMode(
    val label: String,
    val summary: String
) {
    OFF("Off", "Silent pauses only — clean reading rhythm"),
    ESSENTIAL("Essential", "Speaks comma, full stop, ? ! : ; and new paragraph"),
    FULL_VERBATIM("Full Verbatim", "Adds brackets, quotes, dash, ellipsis, slash, bullets & headings")
}

enum class DictationElevationInterval(
    val label: String,
    val intervalSeconds: Int,
    val isPerPassage: Boolean = false
) {
    EVERY_45S("Every 45s", 45),
    EVERY_60S("Every 60s", 60),
    EVERY_90S("Every 90s", 90),
    PER_PASSAGE("Per Card", 0, isPerPassage = true)
}

data class DictationConfig(
    val targetWpm: Int = DEFAULT_WPM,
    val speechBurstRate: Float = DEFAULT_BURST_SPEECH_RATE,
    val speechBurstWpmBaseline: Int = DEFAULT_BURST_WPM_BASELINE,
    val speakPunctuationCues: Boolean = false,
    val punctuationMode: DictationPunctuationMode = DictationPunctuationMode.OFF,
    val elevatingEnabled: Boolean = false,
    val startWpm: Int = 35,
    val targetMaxWpm: Int = 75,
    val stepWpm: Int = 5,
    val elevationInterval: DictationElevationInterval = DictationElevationInterval.EVERY_60S,
    val autoAdvancePassages: Boolean = true
) {
    val effectivePunctuationMode: DictationPunctuationMode
        get() = when {
            punctuationMode != DictationPunctuationMode.OFF -> punctuationMode
            speakPunctuationCues -> DictationPunctuationMode.ESSENTIAL
            else -> DictationPunctuationMode.OFF
        }

    fun normalized(): DictationConfig {
        val clampedWpm = targetWpm.coerceIn(MIN_WPM, MAX_WPM)
        val clampedStart = startWpm.coerceIn(MIN_WPM, MAX_WPM)
        val clampedMax = targetMaxWpm.coerceIn(clampedStart, MAX_WPM)
        val resolvedPunctMode = effectivePunctuationMode
        return copy(
            targetWpm = clampedWpm,
            speechBurstRate = speechBurstRate.coerceIn(MIN_BURST_SPEECH_RATE, MAX_BURST_SPEECH_RATE),
            speechBurstWpmBaseline = speechBurstWpmBaseline.coerceIn(110, 185),
            speakPunctuationCues = resolvedPunctMode != DictationPunctuationMode.OFF,
            punctuationMode = resolvedPunctMode,
            startWpm = clampedStart,
            targetMaxWpm = clampedMax,
            stepWpm = stepWpm.coerceIn(5, 15)
        )
    }

    companion object {
        const val MIN_WPM = 25
        const val MAX_WPM = 120
        const val DEFAULT_WPM = 45
        const val WPM_STEP = 5
        const val MIN_BURST_SPEECH_RATE = 0.80f
        const val MAX_BURST_SPEECH_RATE = 1.35f
        const val DEFAULT_BURST_SPEECH_RATE = 0.96f
        const val DEFAULT_BURST_WPM_BASELINE = 148
    }
}

data class DictationPassageItem(
    val id: String,
    val blockIndex: Int,
    val title: String,
    val badge: String,
    val promptQuestion: String,
    val rawDictationText: String,
    val originalProofreadText: String
)

data class DictationPhrase(
    val index: Int,
    val sentenceIndex: Int,
    val paragraphIndex: Int,
    val displayText: String,
    val spokenText: String,
    val wordCount: Int,
    val charCount: Int,
    val isSentenceEnd: Boolean = false,
    val isParagraphEnd: Boolean = false
)

object DictationEngine {

    private val protectedAbbreviationPairs = listOf(
        "Art." to "Art\u0001",
        "Sec." to "Sec\u0001",
        "Fig." to "Fig\u0001",
        "Eq." to "Eq\u0001",
        "No." to "No\u0001",
        "Vol." to "Vol\u0001",
        "Ch." to "Ch\u0001",
        "Dr." to "Dr\u0001",
        "Mr." to "Mr\u0001",
        "Mrs." to "Mrs\u0001",
        "Ms." to "Ms\u0001",
        "Prof." to "Prof\u0001",
        "vs." to "vs\u0001",
        "etc." to "etc\u0001",
        "e.g." to "e\u0001g\u0001",
        "i.e." to "i\u0001e\u0001",
        "U.S." to "U\u0001S\u0001",
        "U.K." to "U\u0001K\u0001",
        "U.N." to "U\u0001N\u0001",
        "Sr." to "Sr\u0001",
        "Jr." to "Jr\u0001"
    )

    private val clauseConjunctions = setOf(
        "and", "but", "because", "therefore", "however", "although", "though",
        "while", "whereas", "moreover", "furthermore", "hence", "since", "which", "that"
    )

    fun extractPassages(blocks: List<StudyNoteBlock>): List<DictationPassageItem> {
        return blocks.mapNotNull { block ->
            when (block) {
                is StudyNoteBlock.OneLiner -> {
                    val body = listOf(block.leadTerm, block.detail)
                        .filter { it.isNotBlank() }
                        .joinToString(". ")
                    if (body.isBlank()) null else {
                        DictationPassageItem(
                            id = "block_${block.blockIndex}",
                            blockIndex = block.blockIndex,
                            title = block.leadTerm.ifBlank { "Concept ${block.displayIndex}" },
                            badge = "One-Liner",
                            promptQuestion = block.leadTerm,
                            rawDictationText = body,
                            originalProofreadText = if (block.detail.isNotBlank()) {
                                "${block.leadTerm} — ${block.detail}"
                            } else {
                                block.leadTerm
                            }
                        )
                    }
                }
                is StudyNoteBlock.MultiLinerCard -> {
                    val sections = mutableListOf<String>()
                    if (block.paragraphs.isNotEmpty()) {
                        sections.addAll(block.paragraphs.filter { it.isNotBlank() })
                    }
                    if (block.nestedBullets.isNotEmpty()) {
                        val bulletLines = block.nestedBullets
                            .map { it.second.trim() }
                            .filter { it.isNotBlank() }
                            .map { "• $it" }
                        if (bulletLines.isNotEmpty()) {
                            sections.add(bulletLines.joinToString("\n"))
                        }
                    }
                    if (block.correctAnswer.isNotBlank()) {
                        sections.add("Answer: ${block.correctAnswer}")
                    }
                    if (block.keyTakeaway.isNotBlank()) {
                        sections.add("Key Takeaway: ${block.keyTakeaway}")
                    }
                    if (block.formula.isNotBlank()) {
                        sections.add("Formula: ${block.formula}")
                    }
                    block.richSections.forEach { sec ->
                        when (sec) {
                            is StudyNoteCardSection.Callout -> {
                                val text = listOf(sec.label, sec.body).filter { it.isNotBlank() }.joinToString(": ")
                                if (text.isNotBlank()) sections.add(text)
                            }
                            is StudyNoteCardSection.Table -> {
                                val tblLines = buildList {
                                    if (sec.headers.isNotEmpty()) add(sec.headers.joinToString(" | "))
                                    sec.rows.forEach { row -> add(row.joinToString(" | ")) }
                                }
                                if (tblLines.isNotEmpty()) sections.add(tblLines.joinToString("\n"))
                            }
                            else -> Unit
                        }
                    }
                    val dictateBody = if (sections.isNotEmpty()) {
                        sections.joinToString("\n\n")
                    } else {
                        block.title
                    }
                    val proofread = buildString {
                        if (block.title.isNotBlank()) appendLine(block.title)
                        if (sections.isNotEmpty()) {
                            appendLine()
                            append(sections.joinToString("\n\n"))
                        }
                    }.trim()
                    if (dictateBody.isBlank()) null else {
                        DictationPassageItem(
                            id = "block_${block.blockIndex}",
                            blockIndex = block.blockIndex,
                            title = block.title.ifBlank { "Card ${block.displayIndex}" },
                            badge = block.badge.ifBlank { "Multi-Liner" },
                            promptQuestion = block.title,
                            rawDictationText = dictateBody,
                            originalProofreadText = proofread
                        )
                    }
                }
                is StudyNoteBlock.Callout -> {
                    val body = listOf(block.label, block.body).filter { it.isNotBlank() }.joinToString(". ")
                    if (body.isBlank()) null else {
                        DictationPassageItem(
                            id = "block_${block.blockIndex}",
                            blockIndex = block.blockIndex,
                            title = block.label.ifBlank { "Key Note" },
                            badge = "Callout",
                            promptQuestion = block.label,
                            rawDictationText = body,
                            originalProofreadText = if (block.label.isNotBlank()) "${block.label}: ${block.body}" else block.body
                        )
                    }
                }
                is StudyNoteBlock.Paragraph -> {
                    val clean = block.text.trim()
                    if (clean.isBlank()) null else {
                        DictationPassageItem(
                            id = "block_${block.blockIndex}",
                            blockIndex = block.blockIndex,
                            title = clean.lineSequence().firstOrNull()?.take(60) ?: "Passage",
                            badge = "Passage",
                            promptQuestion = "",
                            rawDictationText = clean,
                            originalProofreadText = clean
                        )
                    }
                }
                is StudyNoteBlock.Table -> {
                    val lines = block.rows.map { row ->
                        row.mapIndexed { idx, cell ->
                            val header = block.headers.getOrNull(idx).orEmpty().trim()
                            if (header.isNotBlank() && cell.isNotBlank()) "$header: $cell" else cell
                        }.filter { it.isNotBlank() }.joinToString(", ")
                    }.filter { it.isNotBlank() }
                    if (lines.isEmpty()) null else {
                        val joined = lines.joinToString("\n")
                        DictationPassageItem(
                            id = "block_${block.blockIndex}",
                            blockIndex = block.blockIndex,
                            title = block.headers.firstOrNull()?.takeIf { it.isNotBlank() } ?: "Comparison Table",
                            badge = "Table",
                            promptQuestion = block.headers.joinToString(" | "),
                            rawDictationText = joined,
                            originalProofreadText = joined
                        )
                    }
                }
                is StudyNoteBlock.Heading -> null
            }
        }
    }

    fun sanitizeForSpeech(
        rawText: String,
        preserveStructuralMarkers: Boolean = false,
        preserveDashes: Boolean = false
    ): String {
        if (rawText.isBlank()) return ""
        var text = rawText.replace("\r\n", "\n")

        // Strip code fences
        text = text.replace(Regex("```[a-zA-Z0-9_-]*\\n?"), " ")
        text = text.replace("```", " ")

        // Replace display & inline LaTeX smartly before punctuation cleanup
        text = text.replace(Regex("\\$\\$([^$]+)\\$\\$")) { match ->
            latexToSpeechFriendly(match.groupValues[1])
        }
        text = text.replace(Regex("\\$([^$\n]+)\\$")) { match ->
            latexToSpeechFriendly(match.groupValues[1])
        }
        text = text.replace(Regex("\\\\\\[([\\s\\S]*?)\\\\]")) { match ->
            latexToSpeechFriendly(match.groupValues[1])
        }
        text = text.replace(Regex("\\\\\\(([^)]*)\\\\\\)")) { match ->
            latexToSpeechFriendly(match.groupValues[1])
        }

        // Convert markdown links [label](url) -> label
        text = text.replace(Regex("\\[([^\\]]+)]\\([^)]+\\)"), "$1")

        // Clean line-leading markdown headings, blockquotes, and bullet symbols cleanly
        text = text.lines().joinToString("\n") { line ->
            val trimmed = line.trim()
            val cleanedLine = when {
                preserveStructuralMarkers && Regex("^#{1,6}\\s+").containsMatchIn(trimmed) ->
                    "\u0003HD\u0003 " + trimmed.replace(Regex("^#{1,6}\\s+"), "")
                preserveStructuralMarkers && Regex("^[-*•+]\\s+").containsMatchIn(trimmed) ->
                    "\u0003BL\u0003 " + trimmed.replace(Regex("^[-*•+]\\s+"), "")
                else -> trimmed
                    .replace(Regex("^#{1,6}\\s+"), "")
                    .replace(Regex("^[-*•+]\\s+"), "")
            }
                .replace(Regex("^>+\\s*"), "")
                .replace("**", "")
                .replace("__", "")
                .replace("`", "")
                .replace("→", " leads to ")
                .replace("->", " leads to ")

            if (preserveDashes) {
                cleanedLine
                    .replace("—", " — ")
                    .replace("–", " – ")
            } else {
                cleanedLine
                    .replace("—", ", ")
                    .replace("–", ", ")
            }
        }

        // Normalize repeated spaces on lines without destroying paragraph breaks (\n\n)
        text = text.replace(Regex("[ \\t]+"), " ")
        text = text.replace(Regex("\\n{3,}"), "\n\n")
        return text.trim()
    }

    private fun latexToSpeechFriendly(formulaRaw: String): String {
        val trimmed = formulaRaw.trim()
        if (trimmed.isEmpty()) return ""
        val isComplex = trimmed.length > 28 ||
            trimmed.contains("\\sum") ||
            trimmed.contains("\\int") ||
            trimmed.contains("\\lim") ||
            trimmed.contains("\\begin") ||
            trimmed.contains("\\matrix") ||
            trimmed.contains("\\sqrt{") && trimmed.length > 18
        if (isComplex) {
            return "equation shown on screen"
        }
        var readable = trimmed
            .replace(Regex("\\\\frac\\{([^}]+)}\\{([^}]+)}"), "$1 over $2")
            .replace("\\times", " times ")
            .replace("\\cdot", " dot ")
            .replace("\\pm", " plus or minus ")
            .replace("\\leq", " less than or equal to ")
            .replace("\\geq", " greater than or equal to ")
            .replace("\\neq", " not equal to ")
            .replace("\\approx", " approximately ")
            .replace("\\alpha", " alpha ")
            .replace("\\beta", " beta ")
            .replace("\\gamma", " gamma ")
            .replace("\\theta", " theta ")
            .replace("\\lambda", " lambda ")
            .replace("\\mu", " mu ")
            .replace("\\pi", " pi ")
            .replace("\\omega", " omega ")
            .replace("\\Delta", " delta ")
            .replace("^2", " squared ")
            .replace("^3", " cubed ")
            .replace("=", " equals ")
            .replace("{", " ")
            .replace("}", " ")
            .replace("\\", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (readable.isBlank()) readable = "equation shown on screen"
        return readable
    }

    fun splitPassageIntoPhrases(
        rawText: String,
        speakPunctuationCues: Boolean = false
    ): List<DictationPhrase> {
        return splitPassageIntoPhrases(
            rawText = rawText,
            punctuationMode = if (speakPunctuationCues) DictationPunctuationMode.ESSENTIAL else DictationPunctuationMode.OFF
        )
    }

    fun splitPassageIntoPhrases(
        rawText: String,
        punctuationMode: DictationPunctuationMode
    ): List<DictationPhrase> {
        val isVerbatim = punctuationMode == DictationPunctuationMode.FULL_VERBATIM
        val sanitized = sanitizeForSpeech(
            rawText = rawText,
            preserveStructuralMarkers = isVerbatim,
            preserveDashes = isVerbatim
        )
        if (sanitized.isBlank()) return emptyList()

        val rawParagraphs = sanitized
            .split(Regex("\\n\\s*\\n+"))
            .map { it.replace(Regex("([.!?।])\\n+"), "$1 ").replace("\n", ". ").trim() }
            .filter { it.isNotBlank() }

        val phrases = mutableListOf<DictationPhrase>()
        var globalPhraseIdx = 0
        var globalSentenceIdx = 0

        rawParagraphs.forEachIndexed { paraIdx, paragraph ->
            val sentences = splitIntoSentences(paragraph)
            sentences.forEachIndexed { sentLocalIdx, sentence ->
                val isLastSentenceInPara = sentLocalIdx == sentences.lastIndex
                val clauses = splitSentenceIntoClauses(sentence)

                clauses.forEachIndexed { clauseIdx, clause ->
                    val isLastClauseInSentence = clauseIdx == clauses.lastIndex
                    val chunks = chunkClauseWords(clause)

                    chunks.forEachIndexed { chunkIdx, chunk ->
                        val isLastChunkInClause = chunkIdx == chunks.lastIndex
                        val isSentenceEnd = isLastClauseInSentence && isLastChunkInClause
                        val isParagraphEnd = isLastSentenceInPara && isSentenceEnd

                        val trimmedChunk = chunk.trim()
                        val isHeadingStart = trimmedChunk.startsWith("\u0003HD\u0003")
                        val isBulletStart = trimmedChunk.startsWith("\u0003BL\u0003")
                        val displayClean = trimmedChunk
                            .replace("\u0003HD\u0003", "")
                            .replace("\u0003BL\u0003", "")
                            .trim()
                        val wordTokens = displayClean
                            .split(Regex("\\s+"))
                            .map { it.trim(',', '.', ';', ':', '!', '?', '।', '"', '\'', '(', ')', '[', ']', '—', '–') }
                            .filter { it.isNotBlank() }
                        val wordCount = wordTokens.size.coerceAtLeast(1)
                        val charCount = wordTokens.sumOf { it.length }.coerceAtLeast(wordCount)

                        val spoken = buildSpokenPhrase(
                            chunk = displayClean,
                            punctuationMode = punctuationMode,
                            isSentenceEnd = isSentenceEnd,
                            isParagraphEnd = isParagraphEnd,
                            isHeadingStart = isHeadingStart,
                            isBulletStart = isBulletStart
                        )

                        phrases.add(
                            DictationPhrase(
                                index = globalPhraseIdx++,
                                sentenceIndex = globalSentenceIdx,
                                paragraphIndex = paraIdx,
                                displayText = displayClean,
                                spokenText = spoken,
                                wordCount = wordCount,
                                charCount = charCount,
                                isSentenceEnd = isSentenceEnd,
                                isParagraphEnd = isParagraphEnd
                            )
                        )
                    }
                }
                globalSentenceIdx++
            }
        }
        return phrases
    }

    private fun protectTextTokens(input: String): String {
        var out = input
        protectedAbbreviationPairs.forEach { (abbr, marker) ->
            out = out.replace(abbr, marker, ignoreCase = false)
        }
        // Protect decimal numbers like 9.81 or 3.14
        out = out.replace(Regex("(\\d)\\.(\\d)"), "$1\u0002$2")
        return out
    }

    private fun restoreTextTokens(input: String): String {
        return input
            .replace("\u0001", ".")
            .replace("\u0002", ".")
    }

    private fun splitIntoSentences(paragraph: String): List<String> {
        val protected = protectTextTokens(paragraph)
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var i = 0
        while (i < protected.length) {
            val ch = protected[i]
            sb.append(ch)
            val isTerminal = ch == '.' || ch == '!' || ch == '?' || ch == '।'
            if (isTerminal) {
                val next = protected.getOrNull(i + 1)
                if (next == null || next.isWhitespace() || next == '"' || next == ')' || next == ']') {
                    if (next == '"' || next == ')') {
                        sb.append(next)
                        i++
                    }
                    val restored = restoreTextTokens(sb.toString()).trim()
                    if (restored.isNotBlank()) result.add(restored)
                    sb.clear()
                }
            }
            i++
        }
        val tail = restoreTextTokens(sb.toString()).trim()
        if (tail.isNotBlank()) result.add(tail)
        return result
    }

    private fun splitSentenceIntoClauses(sentence: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        sentence.forEachIndexed { idx, ch ->
            sb.append(ch)
            if (ch == ',' || ch == ';' || ch == ':') {
                val next = sentence.getOrNull(idx + 1)
                // Don't split numbers like 10,000 or time like 12:30
                val prev = sentence.getOrNull(idx - 1)
                val isNumericSeparator = (prev?.isDigit() == true && next?.isDigit() == true)
                if (!isNumericSeparator && (next == null || next.isWhitespace())) {
                    val piece = sb.toString().trim()
                    if (piece.isNotBlank()) result.add(piece)
                    sb.clear()
                }
            }
        }
        val tail = sb.toString().trim()
        if (tail.isNotBlank()) result.add(tail)
        return if (result.isEmpty()) listOf(sentence) else result
    }

    private fun chunkClauseWords(clause: String): List<String> {
        val words = clause.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size <= 5) return listOf(clause.trim())

        val chunks = mutableListOf<String>()
        val buffer = mutableListOf<String>()

        for (idx in words.indices) {
            val w = words[idx]
            val cleanLower = w.lowercase().trim(',', '.', ';', ':', '!', '?')
            val shouldBreakBeforeConjunction = buffer.size >= 3 && cleanLower in clauseConjunctions
            if (shouldBreakBeforeConjunction) {
                chunks.add(buffer.joinToString(" "))
                buffer.clear()
            }
            buffer.add(w)
            val remaining = words.size - 1 - idx
            if (buffer.size >= 5 || (buffer.size >= 4 && remaining >= 2)) {
                chunks.add(buffer.joinToString(" "))
                buffer.clear()
            }
        }
        if (buffer.isNotEmpty()) {
            // Avoid 1-word orphan chunk when previous chunk can absorb it comfortably
            if (buffer.size == 1 && chunks.isNotEmpty() && chunks.last().split(" ").size <= 5) {
                val merged = "${chunks.removeAt(chunks.lastIndex)} ${buffer.first()}"
                chunks.add(merged)
            } else {
                chunks.add(buffer.joinToString(" "))
            }
        }
        return chunks
    }

    private fun buildSpokenPhrase(
        chunk: String,
        punctuationMode: DictationPunctuationMode,
        isSentenceEnd: Boolean,
        isParagraphEnd: Boolean,
        isHeadingStart: Boolean,
        isBulletStart: Boolean
    ): String {
        val rawTrimmed = chunk.trim()
        val hasTrailingEllipsis = rawTrimmed.endsWith("...") || rawTrimmed.endsWith("…")
        val insideQuoteMatch = Regex("^([\\s\\S]*?)([.!?।]+)(\"\\s*|”\\s*|'\\s*|\\)\\s*)$").find(rawTrimmed)

        val terminalMark: String
        val coreBeforeTerminal: String
        when {
            hasTrailingEllipsis -> {
                terminalMark = "..."
                coreBeforeTerminal = rawTrimmed.removeSuffix("...").removeSuffix("…").trim()
            }
            insideQuoteMatch != null -> {
                terminalMark = insideQuoteMatch.groupValues[2].takeLast(1)
                coreBeforeTerminal = (insideQuoteMatch.groupValues[1] + insideQuoteMatch.groupValues[3]).trim()
            }
            else -> {
                val lastCh = rawTrimmed.lastOrNull()
                if (lastCh != null && lastCh in charArrayOf(',', ';', ':', '.', '!', '?', '।')) {
                    terminalMark = lastCh.toString()
                    coreBeforeTerminal = rawTrimmed.dropLast(1).trim()
                } else {
                    terminalMark = ""
                    coreBeforeTerminal = rawTrimmed
                }
            }
        }

        if (punctuationMode == DictationPunctuationMode.OFF) {
            return coreBeforeTerminal.ifBlank { rawTrimmed }
        }

        val structuralPrefix = if (punctuationMode == DictationPunctuationMode.FULL_VERBATIM) {
            when {
                isHeadingStart -> "Heading"
                isBulletStart -> "Bullet point"
                else -> ""
            }
        } else ""

        val expandedBody = if (punctuationMode == DictationPunctuationMode.FULL_VERBATIM) {
            expandInlineVerbatimPunctuation(coreBeforeTerminal.ifBlank { rawTrimmed })
        } else {
            coreBeforeTerminal.ifBlank { rawTrimmed }
        }

        val endCue = when {
            hasTrailingEllipsis && punctuationMode == DictationPunctuationMode.FULL_VERBATIM ->
                if (isParagraphEnd) "ellipsis, new paragraph" else "ellipsis"
            isParagraphEnd && terminalMark == "?" -> "question mark, new paragraph"
            isParagraphEnd && terminalMark == "!" -> "exclamation mark, new paragraph"
            isParagraphEnd && terminalMark == "।" -> "poorna viram, new paragraph"
            isParagraphEnd -> "full stop, new paragraph"
            isSentenceEnd && terminalMark == "?" -> "question mark"
            isSentenceEnd && terminalMark == "!" -> "exclamation mark"
            isSentenceEnd && terminalMark == "।" -> "poorna viram"
            isSentenceEnd -> "full stop"
            terminalMark == "," -> "comma"
            terminalMark == ";" -> "semicolon"
            terminalMark == ":" -> "colon"
            else -> ""
        }

        return listOf(structuralPrefix, expandedBody, endCue)
            .filter { it.isNotBlank() }
            .joinToString(", ")
            .replace(Regex("\\s*,(\\s*,)+"), ",")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun expandInlineVerbatimPunctuation(text: String): String {
        if (text.isBlank()) return text
        var out = text

        // Ellipsis mid-phrase
        out = out.replace("...", ", ellipsis, ").replace("…", ", ellipsis, ")

        // Em-dash, en-dash, double-hyphen, or spaced hyphen -> "dash" (preserve compound hyphens like well-known)
        out = out.replace(Regex("\\s*[—–]\\s*|\\s+-\\s+|\\s*--\\s*"), ", dash, ")

        // Paired straight double quotes first, then curly double quotes
        out = out.replace(Regex("\"([^\"\n]+)\""), ", open quote, $1, close quote, ")
        out = out.replace("“", ", open quote, ").replace("”", ", close quote, ")

        // Paired outer single quotes while protecting apostrophes inside words (don't, citizen's)
        out = out.replace(Regex("(^|\\s)'([^'\n]+)'(?=\\s|[,.;:!?)]|$)"), "$1, open quote, $2, close quote, ")

        // Parentheses & square brackets
        out = out.replace("(", ", open bracket, ")
            .replace("[", ", open bracket, ")
            .replace(")", ", close bracket, ")
            .replace("]", ", close bracket, ")

        // Context-aware slash -> "slash" except common metric rate units (m/s, km/h, etc.)
        out = out.replace(Regex("(?i)(?<!\\b(?:km|m|cm|mm|kg|g|mg|ml|l))/(?!(?:s|hr|h|min|kg|m|cm|l|cc)\\b)"), " slash ")

        return out
            .replace(Regex("\\s*,\\s*"), ", ")
            .replace(Regex("(?:^,\\s*|,\\s*$)"), "")
            .replace(Regex("(,\\s*){2,}"), ", ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun computeWritingPauseMs(
        phrase: DictationPhrase,
        config: DictationConfig
    ): Long {
        val safeConfig = config.normalized()
        // Blend raw word count with normalized 5-char word length so dense technical terms get realistic ink time
        val normalizedCharWords = phrase.charCount / 5.0
        val effectiveWordUnits = (phrase.wordCount * 0.60) + (normalizedCharWords * 0.40)

        val totalBudgetMs = (effectiveWordUnits / safeConfig.targetWpm.toDouble()) * 60_000.0
        val effectiveBurstWpm = safeConfig.speechBurstWpmBaseline * safeConfig.speechBurstRate.toDouble()
        val spokenWords = phrase.spokenText.split(Regex("\\s+")).count { it.isNotBlank() }.coerceAtLeast(1)
        val estimatedSpeechMs = (spokenWords / effectiveBurstWpm.coerceAtLeast(90.0)) * 60_000.0

        val cadenceTailMs = when {
            phrase.isParagraphEnd -> 650.0
            phrase.isSentenceEnd -> 320.0
            else -> 0.0
        }
        val verbatimSymbolAllowanceMs = if (safeConfig.effectivePunctuationMode == DictationPunctuationMode.FULL_VERBATIM) {
            240.0
        } else {
            0.0
        }
        val minPauseMs = if (safeConfig.effectivePunctuationMode == DictationPunctuationMode.FULL_VERBATIM) 480L else 300L

        val netPauseMs = (totalBudgetMs - estimatedSpeechMs) + cadenceTailMs + verbatimSymbolAllowanceMs
        return netPauseMs.roundToLong().coerceIn(minPauseMs, 20_000L)
    }

    fun estimateExpectedSpeechMs(
        phrase: DictationPhrase,
        config: DictationConfig
    ): Long {
        val safeConfig = config.normalized()
        val effectiveBurstWpm = safeConfig.speechBurstWpmBaseline * safeConfig.speechBurstRate.toDouble()
        val spokenWords = phrase.spokenText.split(Regex("\\s+")).count { it.isNotBlank() }.coerceAtLeast(1)
        return ((spokenWords / effectiveBurstWpm.coerceAtLeast(90.0)) * 60_000.0)
            .roundToLong()
            .coerceIn(450L, 12_000L)
    }

    fun computeNextElevatedWpm(
        currentWpm: Int,
        config: DictationConfig
    ): Int {
        val safe = config.normalized()
        if (!safe.elevatingEnabled) return currentWpm.coerceIn(DictationConfig.MIN_WPM, DictationConfig.MAX_WPM)
        return (currentWpm + safe.stepWpm).coerceIn(safe.startWpm, safe.targetMaxWpm)
    }
}
