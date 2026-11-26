package com.example.util

import com.example.data.PaperEntity
import com.example.data.StudyNoteEntity
import com.example.data.toQuestionPaper
import com.example.model.FolderItem
import com.example.model.VoiceFillerCategory
import com.example.model.VoiceFillerOccurrence
import com.example.model.VoiceFluencyMetrics
import com.example.model.VoiceFolderPromptResult
import com.example.model.VoiceHesitationGap
import com.example.model.VoicePracticeMode
import com.example.model.VoicePromptItem
import com.example.model.VoiceSourceFilter
import com.example.model.VoiceTranscriptSegment
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Pure Kotlin engine for:
 * 1. Resolving Parent > Child > Sub-Child folder hierarchies recursively.
 * 2. Building subject-grounded, formula-free speaking prompts from Study Notes and Exam MCQs.
 * 3. Detecting hesitation gaps from RMS audio envelopes and highlighting em/um/uh/er fillers.
 */
object VoiceFluencyEngine {

    private val vocalDisfluencyTokens = setOf(
        "em", "emm", "emmm",
        "um", "umm", "ummm",
        "uh", "uhh", "uhhh",
        "er", "err", "errr",
        "ah", "ahh",
        "hm", "hmm", "hmmm",
        "erm", "uhm"
    )

    private val singleWordLexicalFillers = setOf(
        "basically", "actually", "literally", "honestly"
    )

    private val multiWordLexicalFillers = listOf(
        "you know", "i mean", "kind of", "sort of"
    )

    private val stopWords = setOf(
        "the", "and", "for", "with", "that", "this", "from", "into", "about", "which",
        "what", "when", "where", "were", "have", "has", "had", "are", "was", "been",
        "being", "will", "would", "could", "should", "they", "them", "their", "there",
        "than", "then", "also", "only", "more", "most", "such", "some", "each", "both",
        "between", "through", "during", "under", "over", "after", "before", "because"
    )

    /**
     * Collects [selectedFolderId] plus every Child, Sub-Child, and deeper descendant folder ID.
     * When [selectedFolderId] is blank or "all", returns emptySet() to indicate all folders.
     */
    fun collectDescendantFolderIds(
        selectedFolderId: String?,
        folders: List<FolderItem>
    ): Set<String> {
        val root = selectedFolderId?.trim().orEmpty()
        if (root.isEmpty() || root == "all") return emptySet()

        val childrenByParent = folders.groupBy { it.parentId?.trim().orEmpty() }
        val result = linkedSetOf<String>()
        val queue = ArrayDeque<String>()
        result.add(root)
        queue.add(root)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val children = childrenByParent[current].orEmpty()
            for (child in children) {
                if (result.add(child.id)) {
                    queue.add(child.id)
                }
            }
        }
        return result
    }

    /**
     * Builds subject-grounded speaking prompts strictly from the user's selected Parent/Child folder tree.
     *
     * By default (`includeFormulas = true`), mathematical/chemical formulas, LaTeX equations,
     * `MultiLinerCard.formula`, `McqQuestion.formula`, and multi-expression options/tables are
     * preserved for speaking practice.
     */
    // [DEPRECATED 2026-11-26] Reason: Unconditional formula/expression exclusion is replaced by inclusive default (`includeFormulas = true`) so users can practice formulas and multiple expressions aloud. Restore if: reverting default to `includeFormulas = false`.
    fun buildPromptsForFolder(
        selectedFolderId: String?,
        folders: List<FolderItem>,
        studyNotes: List<StudyNoteEntity>,
        papers: List<PaperEntity>,
        sourceFilter: VoiceSourceFilter = VoiceSourceFilter.ALL,
        includeFormulas: Boolean = true
    ): VoiceFolderPromptResult {
        val targetFolderIds = collectDescendantFolderIds(selectedFolderId, folders)
        val folderPathsById = folders.associate { folder ->
            folder.id to FolderPathHelper.getFolderHierarchyPath(folder.id, folders).ifBlank { folder.name.trim() }
        }
        val targetNormalizedPaths = targetFolderIds.mapNotNull { id ->
            folderPathsById[id]?.let { FolderPathHelper.normalizeFolderPath(it).lowercase(Locale.ROOT) }
        }.toSet()
        val selectedRootPathLower = if (targetFolderIds.isEmpty()) {
            ""
        } else {
            folderPathsById[selectedFolderId?.trim().orEmpty()]
                ?.let { FolderPathHelper.normalizeFolderPath(it).lowercase(Locale.ROOT) }
                .orEmpty()
        }

        fun matchesSelection(itemFolderId: String?, itemSubject: String): Boolean {
            if (targetFolderIds.isEmpty()) return true
            val cleanFid = itemFolderId?.trim().orEmpty()
            if (cleanFid.isNotEmpty() && cleanFid in targetFolderIds) return true

            val cleanSubj = FolderPathHelper.normalizeFolderPath(itemSubject).lowercase(Locale.ROOT)
            if (cleanSubj.isBlank()) return false
            if (cleanSubj in targetNormalizedPaths) return true
            if (selectedRootPathLower.isNotBlank() &&
                (cleanSubj == selectedRootPathLower || cleanSubj.startsWith("$selectedRootPathLower > "))
            ) {
                return true
            }
            return false
        }

        fun cleanSegment(raw: String): String =
            if (includeFormulas) cleanPreserveExpressions(raw) else sanitizeNoFormulas(raw)

        val prompts = mutableListOf<VoicePromptItem>()
        var skippedFormulaItems = 0

        if (sourceFilter == VoiceSourceFilter.ALL || sourceFilter == VoiceSourceFilter.NOTES_ONLY) {
            val matchingNotes = studyNotes.filter { matchesSelection(it.folderId, it.subject) }
            for (note in matchingNotes) {
                val rule = note.parsedFormatRule()
                val noteFolderPath = folderPathsById[note.folderId.orEmpty()]
                    ?.ifBlank { FolderPathHelper.formatDisplayPath(note.subject) }
                    ?: FolderPathHelper.formatDisplayPath(note.subject).ifBlank { "General Notes" }
                val language = rule.language.ifBlank { "English" }
                val blocks = StudyNoteParser.parseBlocks(note.rawContent, rule)

                for (block in blocks) {
                    when (block) {
                        is StudyNoteBlock.OneLiner -> {
                            val cleanLead = cleanSegment(block.leadTerm)
                            val cleanDetail = cleanSegment(block.detail)
                            if (cleanLead.isBlank() && cleanDetail.isBlank()) {
                                skippedFormulaItems++
                                continue
                            }
                            if (!includeFormulas && isFormulaHeavyText(block.leadTerm) && isFormulaHeavyText(block.detail)) {
                                skippedFormulaItems++
                                continue
                            }
                            val promptQ = if (cleanLead.isNotBlank()) {
                                "Explain \"$cleanLead\" in your own words."
                            } else {
                                "Summarize this concept clearly."
                            }
                            val ref = listOf(cleanLead, cleanDetail).filter { it.isNotBlank() }.joinToString(" — ")
                            val expressions = if (includeFormulas) {
                                extractFormulaExpressions(block.leadTerm, block.detail)
                            } else {
                                emptyList()
                            }
                            val hasEnoughContent = ref.split(Regex("\\s+")).size >= 3 ||
                                (includeFormulas && (expressions.isNotEmpty() || containsExpressionMarkers(ref)))
                            if (hasEnoughContent) {
                                prompts.add(
                                    VoicePromptItem(
                                        id = "note_${note.id}_${block.blockIndex}",
                                        title = cleanLead.ifBlank { note.title },
                                        promptQuestion = promptQ,
                                        referenceSummary = ref,
                                        sourceLabel = "Note • ${note.title}",
                                        folderId = note.folderId,
                                        folderPath = noteFolderPath,
                                        language = language,
                                        formulaExpressions = expressions
                                    )
                                )
                            }
                        }

                        is StudyNoteBlock.MultiLinerCard -> {
                            val cleanTitle = cleanSegment(block.title)
                            val cleanFormula = if (includeFormulas) cleanPreserveExpressions(block.formula) else ""
                            val rawSections = buildList {
                                addAll(block.paragraphs)
                                addAll(block.nestedBullets.map { it.second })
                                if (includeFormulas && cleanFormula.isNotBlank()) add("Formula: $cleanFormula")
                                if (block.correctAnswer.isNotBlank()) add(block.correctAnswer)
                                if (block.keyTakeaway.isNotBlank()) add(block.keyTakeaway)
                            }
                            val cleanBody = rawSections
                                .map { cleanSegment(it) }
                                .filter { it.isNotBlank() }
                                .joinToString("\n")
                            if (cleanBody.isBlank()) {
                                if (block.formula.isNotBlank() || rawSections.any { it.isNotBlank() }) {
                                    skippedFormulaItems++
                                }
                                continue
                            }
                            val expressions = if (includeFormulas) {
                                extractFormulaExpressions(
                                    cleanFormula,
                                    *block.paragraphs.toTypedArray(),
                                    *block.nestedBullets.map { it.second }.toTypedArray(),
                                    block.keyTakeaway
                                )
                            } else {
                                emptyList()
                            }
                            val options = if (includeFormulas) {
                                block.optionsList
                                    .filter { it.second.isNotBlank() }
                                    .map { "(${it.first}) ${cleanPreserveExpressions(it.second)}" }
                            } else {
                                emptyList()
                            }
                            val promptQ = if (cleanTitle.isNotBlank()) {
                                "Speak clearly about: $cleanTitle"
                            } else {
                                "Explain this topic from ${note.title} in your own words."
                            }
                            prompts.add(
                                VoicePromptItem(
                                    id = "note_${note.id}_${block.blockIndex}",
                                    title = cleanTitle.ifBlank { note.title },
                                    promptQuestion = promptQ,
                                    referenceSummary = cleanBody,
                                    sourceLabel = "Note • ${note.title}",
                                    folderId = note.folderId,
                                    folderPath = noteFolderPath,
                                    language = language,
                                    formulaExpressions = expressions,
                                    optionExpressions = options
                                )
                            )
                        }

                        is StudyNoteBlock.Callout -> {
                            val cleanLabel = cleanSegment(block.label)
                            val cleanBody = cleanSegment(block.body)
                            if (cleanBody.isBlank()) {
                                skippedFormulaItems++
                                continue
                            }
                            val expressions = if (includeFormulas) {
                                extractFormulaExpressions(block.label, block.body)
                            } else {
                                emptyList()
                            }
                            prompts.add(
                                VoicePromptItem(
                                    id = "note_${note.id}_${block.blockIndex}",
                                    title = cleanLabel.ifBlank { note.title },
                                    promptQuestion = if (cleanLabel.isNotBlank()) {
                                        "Explain the key takeaway for \"$cleanLabel\"."
                                    } else {
                                        "Summarize this key note clearly."
                                    },
                                    referenceSummary = cleanBody,
                                    sourceLabel = "Note • ${note.title}",
                                    folderId = note.folderId,
                                    folderPath = noteFolderPath,
                                    language = language,
                                    formulaExpressions = expressions
                                )
                            )
                        }

                        is StudyNoteBlock.Paragraph -> {
                            val cleanText = cleanSegment(block.text)
                            val expressions = if (includeFormulas) {
                                extractFormulaExpressions(block.text)
                            } else {
                                emptyList()
                            }
                            val wordCount = cleanText.split(Regex("\\s+")).filter { it.isNotBlank() }.size
                            val isSubstantial = wordCount >= 6 || (includeFormulas && (expressions.isNotEmpty() || containsExpressionMarkers(cleanText)) && wordCount >= 2)
                            if (!isSubstantial) {
                                if (block.text.isNotBlank() && !includeFormulas) skippedFormulaItems++
                                continue
                            }
                            val firstPhrase = cleanText.split(Regex("[.!?\n]")).firstOrNull()?.trim().orEmpty().take(90)
                            prompts.add(
                                VoicePromptItem(
                                    id = "note_${note.id}_${block.blockIndex}",
                                    title = note.title,
                                    promptQuestion = if (firstPhrase.isNotBlank()) {
                                        "Discuss: $firstPhrase"
                                    } else {
                                        "Explain the main idea of ${note.title}."
                                    },
                                    referenceSummary = cleanText,
                                    sourceLabel = "Note • ${note.title}",
                                    folderId = note.folderId,
                                    folderPath = noteFolderPath,
                                    language = language,
                                    formulaExpressions = expressions
                                )
                            )
                        }

                        is StudyNoteBlock.Table -> {
                            if (includeFormulas && block.rows.isNotEmpty()) {
                                val rowSummaries = block.rows.take(8).mapNotNull { row ->
                                    val paired = row.mapIndexedNotNull { idx, cell ->
                                        val cleanCell = cleanPreserveExpressions(cell)
                                        if (cleanCell.isBlank()) null
                                        else {
                                            val header = block.headers.getOrNull(idx)?.let(::cleanPreserveExpressions).orEmpty()
                                            if (header.isNotBlank()) "$header: $cleanCell" else cleanCell
                                        }
                                    }.joinToString(" | ")
                                    paired.takeIf { it.isNotBlank() }
                                }
                                if (rowSummaries.isNotEmpty()) {
                                    val combined = rowSummaries.joinToString("\n")
                                    prompts.add(
                                        VoicePromptItem(
                                            id = "note_${note.id}_${block.blockIndex}",
                                            title = note.title,
                                            promptQuestion = "Explain the key points and expressions from this ${note.title} table.",
                                            referenceSummary = combined,
                                            sourceLabel = "Note Table • ${note.title}",
                                            folderId = note.folderId,
                                            folderPath = noteFolderPath,
                                            language = language,
                                            formulaExpressions = extractFormulaExpressions(combined)
                                        )
                                    )
                                }
                            }
                        }

                        is StudyNoteBlock.Heading -> Unit
                    }
                }
            }
        }

        if (sourceFilter == VoiceSourceFilter.ALL || sourceFilter == VoiceSourceFilter.PAPERS_ONLY) {
            val matchingPapers = papers.filter { matchesSelection(it.folderId, it.subject) }
            for (paperEntity in matchingPapers) {
                val parsed = paperEntity.toQuestionPaper()
                val paperFolderPath = folderPathsById[paperEntity.folderId.orEmpty()]
                    ?.ifBlank { FolderPathHelper.formatDisplayPath(paperEntity.subject) }
                    ?: FolderPathHelper.formatDisplayPath(paperEntity.subject).ifBlank { "Exam Papers" }

                for (q in parsed.questions) {
                    val cleanQ = cleanSegment(q.question)
                    if (cleanQ.isBlank() || (!includeFormulas && isFormulaHeavyText(q.question))) {
                        skippedFormulaItems++
                        continue
                    }
                    val answerLetter = q.correctAnswer?.trim()?.uppercase(Locale.ROOT).orEmpty()
                    val answerText = answerLetter.firstOrNull()?.let { q.options[it.toString()] }.orEmpty()
                    val cleanAnswer = cleanSegment(answerText)
                    val cleanFormula = if (includeFormulas) cleanPreserveExpressions(q.formula.orEmpty()) else ""
                    val cleanExplanation = cleanSegment(q.explanation.orEmpty())

                    val optionLines = if (includeFormulas) {
                        q.options.asList
                            .filter { it.second.isNotBlank() }
                            .map { (letter, opt) -> "($letter) ${cleanPreserveExpressions(opt)}" }
                    } else {
                        emptyList()
                    }
                    val expressions = if (includeFormulas) {
                        extractFormulaExpressions(
                            cleanFormula,
                            q.question,
                            answerText,
                            q.explanation.orEmpty(),
                            *q.options.asList.map { it.second }.toTypedArray()
                        )
                    } else {
                        emptyList()
                    }
                    val reference = listOf(
                        if (cleanFormula.isNotBlank()) "Formula: $cleanFormula" else "",
                        if (cleanAnswer.isNotBlank()) "Key answer: $cleanAnswer." else "",
                        cleanExplanation
                    ).filter { it.isNotBlank() }.joinToString("\n").ifBlank { cleanQ }

                    prompts.add(
                        VoicePromptItem(
                            id = "paper_${paperEntity.id}_q${q.num}",
                            title = "${paperEntity.title} • Q${q.num}",
                            promptQuestion = cleanQ,
                            referenceSummary = reference,
                            sourceLabel = "MCQ • ${paperEntity.title}",
                            folderId = paperEntity.folderId,
                            folderPath = paperFolderPath,
                            language = "English",
                            formulaExpressions = expressions,
                            optionExpressions = optionLines
                        )
                    )
                }
            }
        }

        return VoiceFolderPromptResult(
            prompts = prompts,
            skippedFormulaItems = skippedFormulaItems,
            matchedFolderCount = targetFolderIds.size
        )
    }

    /**
     * Normalizes markdown list/heading clutter while preserving scientific/math formulas,
     * LaTeX expressions ($...$, $$...$$, \(...\), \[...\]), chemical reactions, and multiple expressions.
     */
    fun cleanPreserveExpressions(rawText: String): String {
        if (rawText.isBlank()) return ""
        val lines = rawText.replace("\r\n", "\n").lines().mapNotNull { line ->
            val cleaned = line
                .trim()
                .replace(Regex("^#{1,6}\\s+"), "")
                .replace(Regex("^[-*•+]\\s+"), "")
                .replace(Regex("^>+\\s*"), "")
                .replace(Regex("\\[([^\\]]+)]\\([^)]+\\)"), "$1")
                .replace("**", "")
                .replace("__", "")
                .trim()
            cleaned.takeIf { it.isNotBlank() && it != "```" }
        }
        return lines.joinToString("\n").trim()
    }

    fun extractFormulaExpressions(vararg texts: String): List<String> {
        val found = linkedSetOf<String>()
        val inlineMathRegex = Regex("""\$\$[\s\S]+?\$\$|\$[^$\n]+\$|\\\[[\s\S]+?\\]|\\\([^)]+\\\)""")

        for (raw in texts) {
            if (raw.isBlank()) continue
            val normalized = raw.replace("\r\n", "\n")

            inlineMathRegex.findAll(normalized).forEach { m ->
                val expr = m.value.trim()
                if (expr.isNotBlank()) found.add(expr)
            }

            for (line in normalized.lines()) {
                val trimmed = line.trim()
                    .replace(Regex("^[-*•+]\\s+"), "")
                    .replace("**", "")
                    .trim()
                if (trimmed.isBlank()) continue

                val prefixedMatch = Regex("""^(?:chemical\s+)?(?:formula|equation|reaction)\s*[:=\-]\s*(.+)$""", RegexOption.IGNORE_CASE).find(trimmed)
                if (prefixedMatch != null) {
                    val body = prefixedMatch.groupValues[1].trim()
                    if (body.isNotBlank()) found.add(body)
                    continue
                }

                if (isFormulaHeavyText(trimmed) && !inlineMathRegex.matches(trimmed)) {
                    found.add(trimmed)
                }
            }
        }
        return found.toList()
    }

    private fun containsExpressionMarkers(text: String): Boolean {
        return text.contains('$') ||
            text.contains("\\frac") ||
            text.contains("\\sqrt") ||
            text.contains('=') ||
            text.contains('→') ||
            text.contains("->") ||
            isFormulaHeavyText(text)
    }

    /**
     * Strips scientific/math formulas (LaTeX blocks, Formula: lines, chemical/math equation lines,
     * and symbolic expressions) so speaking prompts remain natural spoken language.
     */
    fun sanitizeNoFormulas(rawText: String): String {
        if (rawText.isBlank()) return ""
        var text = rawText.replace("\r\n", "\n")

        // Remove code blocks and LaTeX display/inline math expressions completely
        text = text.replace(Regex("```[\\s\\S]*?```"), " ")
        text = text.replace(Regex("\\$\\$[\\s\\S]*?\\$\\$"), " ")
        text = text.replace(Regex("\\$[^$\n]+\\$"), " ")
        text = text.replace(Regex("\\\\\\[[\\s\\S]*?\\\\]"), " ")
        text = text.replace(Regex("\\\\\\([^)]*\\\\\\)"), " ")
        text = text.replace(Regex("\\\\(?:frac|sum|int|sqrt|alpha|beta|gamma|theta|lambda|pi|Delta)\\b[^\\s.,;]*"), " ")

        val cleanLines = text.lines().mapNotNull { line ->
            val trimmed = line
                .trim()
                .replace(Regex("^#{1,6}\\s+"), "")
                .replace(Regex("^[-*•+]\\s+"), "")
                .replace(Regex("^>+\\s*"), "")
                .replace(Regex("\\[([^\\]]+)]\\([^)]+\\)"), "$1")
                .replace("**", "")
                .replace("__", "")
                .replace("`", "")
                .trim()

            when {
                trimmed.isBlank() -> null
                Regex("""^(?:chemical\s+)?(?:formula|equation|reaction)\s*[:=\-]""", RegexOption.IGNORE_CASE).containsMatchIn(trimmed) -> null
                isFormulaHeavyText(trimmed) -> null
                else -> trimmed
            }
        }

        return cleanLines
            .joinToString(" ")
            .replace("→", " leads to ")
            .replace("->", " leads to ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun isFormulaHeavyText(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.contains("$$") || trimmed.contains("\\frac") || trimmed.contains("\\sum") || trimmed.contains("\\int")) {
            return true
        }
        val mathSymbolsCount = trimmed.count { it in setOf('=', '∑', '∫', '√', '≤', '≥', '≠', '±', '×', '÷', '^', '∂', '∆') }
        val hasChemicalArrow = Regex("""\b\d*(?:[A-Z][a-z]?\d*)+\s*\+\s*\d*(?:[A-Z][a-z]?\d*)+\s*(?:→|->|=>|=)""").containsMatchIn(trimmed)
        val hasInlineEquation = Regex("""\b[a-zA-Z]\s*=\s*[-+*/^()0-9a-zA-Z\s]{2,}""").containsMatchIn(trimmed)
        val alphaChars = trimmed.count { it.isLetter() }
        val nonAlphaMath = trimmed.count { it in setOf('=', '+', '*', '/', '^', '(', ')', '{', '}', '[', ']', '<', '>') }

        return hasChemicalArrow ||
            (mathSymbolsCount >= 2 && alphaChars < 30) ||
            (hasInlineEquation && alphaChars < 40) ||
            (nonAlphaMath >= 6 && nonAlphaMath > alphaChars / 2)
    }

    /**
     * Detects silent hesitation pauses inside an active recording from sampled RMS amplitudes (0f..1f).
     */
    fun detectHesitationGaps(
        rmsSamples: List<Float>,
        sampleIntervalMs: Long = 100L,
        silenceThreshold: Float = 0.045f,
        minHesitationPauseMs: Long = 650L
    ): List<VoiceHesitationGap> {
        if (rmsSamples.isEmpty() || sampleIntervalMs <= 0L) return emptyList()
        val firstVoiceIdx = rmsSamples.indexOfFirst { it >= silenceThreshold }
        val lastVoiceIdx = rmsSamples.indexOfLast { it >= silenceThreshold }
        if (firstVoiceIdx == -1 || lastVoiceIdx <= firstVoiceIdx) return emptyList()

        val gaps = mutableListOf<VoiceHesitationGap>()
        var gapStartIdx: Int? = null

        for (i in firstVoiceIdx..lastVoiceIdx) {
            val isSilent = rmsSamples[i] < silenceThreshold
            if (isSilent && gapStartIdx == null) {
                gapStartIdx = i
            } else if (!isSilent && gapStartIdx != null) {
                val duration = (i - gapStartIdx) * sampleIntervalMs
                if (duration >= minHesitationPauseMs) {
                    gaps.add(
                        VoiceHesitationGap(
                            startMs = gapStartIdx * sampleIntervalMs,
                            endMs = i * sampleIntervalMs,
                            durationMs = duration
                        )
                    )
                }
                gapStartIdx = null
            }
        }
        return gaps
    }

    /**
     * Tokenizes a transcript into display segments so vocal disfluencies ([em], um, uh, er, etc.)
     * and lexical fillers can be rendered with clear visual highlights.
     */
    fun tokenizeTranscriptForHighlight(transcript: String): List<VoiceTranscriptSegment> {
        if (transcript.isBlank()) return emptyList()
        val rawPieces = transcript.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val segments = mutableListOf<VoiceTranscriptSegment>()

        var i = 0
        while (i < rawPieces.size) {
            val piece = rawPieces[i]
            val cleaned = normalizeToken(piece)
            val nextCleaned = rawPieces.getOrNull(i + 1)?.let(::normalizeToken)
            val twoWord = if (nextCleaned != null) "$cleaned $nextCleaned" else ""

            when {
                twoWord in multiWordLexicalFillers -> {
                    segments.add(
                        VoiceTranscriptSegment(
                            text = "$piece ${rawPieces[i + 1]}",
                            isFiller = true,
                            category = VoiceFillerCategory.LEXICAL_FILLER
                        )
                    )
                    i += 2
                }
                cleaned in vocalDisfluencyTokens -> {
                    segments.add(
                        VoiceTranscriptSegment(
                            text = piece,
                            isFiller = true,
                            category = VoiceFillerCategory.VOCAL_DISFLUENCY
                        )
                    )
                    i++
                }
                cleaned in singleWordLexicalFillers -> {
                    segments.add(
                        VoiceTranscriptSegment(
                            text = piece,
                            isFiller = true,
                            category = VoiceFillerCategory.LEXICAL_FILLER
                        )
                    )
                    i++
                }
                else -> {
                    segments.add(VoiceTranscriptSegment(text = piece, isFiller = false))
                    i++
                }
            }
        }
        return segments
    }

    fun extractFillersFromTranscript(transcript: String): List<VoiceFillerOccurrence> {
        val segments = tokenizeTranscriptForHighlight(transcript)
        val counts = linkedMapOf<Pair<String, VoiceFillerCategory>, Int>()
        for (seg in segments) {
            if (!seg.isFiller || seg.category == null) continue
            val canonical = canonicalFillerLabel(normalizeToken(seg.text))
            val key = canonical to seg.category
            counts[key] = (counts[key] ?: 0) + 1
        }
        return counts.entries
            .map { (key, count) ->
                VoiceFillerOccurrence(
                    token = key.first,
                    count = count,
                    category = key.second
                )
            }
            .sortedWith(compareByDescending<VoiceFillerOccurrence> { it.count }.thenBy { it.token })
    }

    fun computeKeywordCoveragePercent(
        transcript: String,
        referenceSummary: String
    ): Int? {
        val verbalizedRef = verbalizeSymbolsForKeywords(referenceSummary)
        val refKeywords = verbalizedRef
            .lowercase(Locale.ROOT)
            .split(Regex("[^a-z0-9]+"))
            .filter { token ->
                (token.length >= 4 && token !in stopWords) ||
                    token in spokenMathKeywordWhitelist
            }
            .distinct()
        if (refKeywords.isEmpty()) return null

        val spokenWords = verbalizeSymbolsForKeywords(transcript)
            .lowercase(Locale.ROOT)
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 2 }
            .toSet()
        if (spokenWords.isEmpty()) return 0

        val matched = refKeywords.count { it in spokenWords }
        return ((matched.toDouble() / refKeywords.size.toDouble()) * 100.0)
            .roundToInt()
            .coerceIn(0, 100)
    }

    private val spokenMathKeywordWhitelist = setOf(
        "pi", "sin", "cos", "tan", "log", "ln", "sum", "add", "sub", "mul", "div", "net", "rms"
    )

    private fun verbalizeSymbolsForKeywords(text: String): String {
        return text
            .replace("\\frac", " fraction ")
            .replace("\\sqrt", " square root ")
            .replace("\\pi", " pi ")
            .replace("π", " pi ")
            .replace("\\alpha", " alpha ")
            .replace("α", " alpha ")
            .replace("\\beta", " beta ")
            .replace("β", " beta ")
            .replace("\\gamma", " gamma ")
            .replace("γ", " gamma ")
            .replace("\\theta", " theta ")
            .replace("θ", " theta ")
            .replace("\\Delta", " delta ")
            .replace("Δ", " delta ")
            .replace("²", " squared ")
            .replace("^2", " squared ")
            .replace("³", " cubed ")
            .replace("^3", " cubed ")
            .replace("->", " leads ")
            .replace("→", " leads ")
            .replace("=", " equals ")
            .replace("+", " plus ")
            .replace("×", " times ")
            .replace("÷", " divided ")
    }

    fun computeMetrics(
        durationMs: Long,
        rmsSamples: List<Float>,
        transcript: String,
        referenceSummary: String,
        mode: VoicePracticeMode,
        explicitFillerOverrides: List<VoiceFillerOccurrence>? = null,
        sampleIntervalMs: Long = 100L
    ): VoiceFluencyMetrics {
        val safeDurationMs = durationMs.coerceAtLeast(1000L)
        val gaps = detectHesitationGaps(rmsSamples, sampleIntervalMs = sampleIntervalMs)
        val silenceMs = gaps.sumOf { it.durationMs }.coerceAtMost(safeDurationMs)
        val speakingMs = (safeDurationMs - silenceMs).coerceAtLeast(0L)
        val hesitationRatio = ((silenceMs.toDouble() / safeDurationMs.toDouble()) * 100.0)
            .roundToInt()
            .coerceIn(0, 100)

        val words = transcript.trim().split(Regex("\\s+")).filter { normalizeToken(it).isNotBlank() }
        val minutes = safeDurationMs / 60_000.0
        val wpm = if (minutes > 0.0 && words.isNotEmpty()) {
            (words.size / minutes).roundToInt().coerceIn(0, 320)
        } else {
            0
        }

        val breakdown = (explicitFillerOverrides?.takeIf { it.isNotEmpty() }
            ?: extractFillersFromTranscript(transcript))
        val totalFillers = breakdown.sumOf { it.count }
        val fillerRate = if (minutes > 0.0) {
            ((totalFillers / minutes) * 10.0).roundToInt() / 10.0
        } else {
            0.0
        }

        val coverage = if (mode == VoicePracticeMode.FREE_SUBJECT_SPEAK) {
            null
        } else {
            computeKeywordCoveragePercent(transcript, referenceSummary)
        }

        return VoiceFluencyMetrics(
            durationMs = safeDurationMs,
            speakingMs = speakingMs,
            silenceMs = silenceMs,
            hesitationPauseCount = gaps.size,
            hesitationRatioPercent = hesitationRatio,
            estimatedWpm = wpm,
            verbatimFillerCount = totalFillers,
            fillerRatePerMinute = fillerRate,
            fillerBreakdown = breakdown,
            keywordCoveragePercent = coverage
        )
    }

    private fun normalizeToken(raw: String): String {
        return raw
            .lowercase(Locale.ROOT)
            .trim('[', ']', '(', ')', ',', '.', ';', ':', '!', '?', '"', '\'', '—', '–', '-')
    }

    private fun canonicalFillerLabel(normalized: String): String = when {
        normalized.startsWith("em") -> "em"
        normalized.startsWith("um") || normalized == "uhm" -> "um"
        normalized.startsWith("uh") -> "uh"
        normalized.startsWith("er") -> "er"
        normalized.startsWith("ah") -> "ah"
        normalized.startsWith("hm") -> "hmm"
        else -> normalized
    }
}
