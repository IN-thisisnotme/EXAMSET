package com.example.util

import kotlin.math.roundToLong

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
    val elevatingEnabled: Boolean = false,
    val startWpm: Int = 35,
    val targetMaxWpm: Int = 75,
    val stepWpm: Int = 5,
    val elevationInterval: DictationElevationInterval = DictationElevationInterval.EVERY_60S,
    val autoAdvancePassages: Boolean = true
) {
    fun normalized(): DictationConfig {
        val clampedWpm = targetWpm.coerceIn(MIN_WPM, MAX_WPM)
        val clampedStart = startWpm.coerceIn(MIN_WPM, MAX_WPM)
        val clampedMax = targetMaxWpm.coerceIn(clampedStart, MAX_WPM)
        return copy(
            targetWpm = clampedWpm,
            speechBurstRate = speechBurstRate.coerceIn(MIN_BURST_SPEECH_RATE, MAX_BURST_SPEECH_RATE),
            speechBurstWpmBaseline = speechBurstWpmBaseline.coerceIn(110, 185),
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

    fun sanitizeForSpeech(rawText: String): String {
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
            line.trim()
                .replace(Regex("^#{1,6}\\s+"), "")
                .replace(Regex("^>+\\s*"), "")
                .replace(Regex("^[-*•+]\\s+"), "")
                .replace("**", "")
                .replace("__", "")
                .replace("`", "")
                .replace("→", " leads to ")
                .replace("->", " leads to ")
                .replace("—", ", ")
                .replace("–", ", ")
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
        val sanitized = sanitizeForSpeech(rawText)
        if (sanitized.isBlank()) return emptyList()

        val rawParagraphs = sanitized
            .split(Regex("\\n\\s*\\n+"))
            .map { it.replace("\n", ". ").replace(Regex("\\.\\s*\\."), ".").trim() }
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

                        val displayClean = chunk.trim()
                        val wordTokens = displayClean
                            .split(Regex("\\s+"))
                            .map { it.trim(',', '.', ';', ':', '!', '?', '"', '\'') }
                            .filter { it.isNotBlank() }
                        val wordCount = wordTokens.size.coerceAtLeast(1)
                        val charCount = wordTokens.sumOf { it.length }.coerceAtLeast(wordCount)

                        val spoken = buildSpokenPhrase(
                            chunk = displayClean,
                            speakPunctuationCues = speakPunctuationCues,
                            isSentenceEnd = isSentenceEnd,
                            isParagraphEnd = isParagraphEnd
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
        speakPunctuationCues: Boolean,
        isSentenceEnd: Boolean,
        isParagraphEnd: Boolean
    ): String {
        val cleanedCore = chunk.trim()
            .trimEnd(',', ';', ':', '.', '!', '?', '।')
            .trim()
        if (!speakPunctuationCues) {
            return cleanedCore.ifBlank { chunk.trim() }
        }
        val cue = when {
            isParagraphEnd && chunk.endsWith("?") -> "question mark, new paragraph"
            isParagraphEnd -> "full stop, new paragraph"
            isSentenceEnd && chunk.endsWith("?") -> "question mark"
            isSentenceEnd && chunk.endsWith("!") -> "exclamation mark"
            isSentenceEnd -> "full stop"
            chunk.endsWith(",") -> "comma"
            chunk.endsWith(";") -> "semicolon"
            chunk.endsWith(":") -> "colon"
            else -> ""
        }
        val base = cleanedCore.ifBlank { chunk.trim() }
        return if (cue.isNotBlank()) "$base, $cue" else base
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

        val netPauseMs = (totalBudgetMs - estimatedSpeechMs) + cadenceTailMs
        return netPauseMs.roundToLong().coerceIn(300L, 20_000L)
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
