package com.example.util

import com.example.data.PaperEntity
import com.example.data.StudyNoteEntity
import com.example.data.toQuestionPaper
import com.example.model.FolderItem
import com.example.model.QuestionPaper
import com.example.model.VoiceAlignmentToken
import com.example.model.VoiceAnalysisResult
import com.example.model.VoiceComparisonStats
import com.example.model.VoiceFillerCategory
import com.example.model.VoiceFillerOccurrence
import com.example.model.VoiceFluencyMetrics
import com.example.model.VoiceFolderPromptResult
import com.example.model.VoiceHesitationGap
import com.example.model.VoiceMistakePair
import com.example.model.VoicePracticeMode
import com.example.model.VoicePromptItem
import com.example.model.VoiceSourceFilter
import com.example.model.VoiceTranscriptSegment
import com.example.model.VoiceWordMatchStatus
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
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
        includeFormulas: Boolean = true,
        maxTotalPrompts: Int = Int.MAX_VALUE,
        maxPromptsPerPaper: Int = Int.MAX_VALUE,
        maxPromptsPerNote: Int = Int.MAX_VALUE,
        paperParser: (PaperEntity) -> QuestionPaper = { it.toQuestionPaper() },
        onItemProcessed: () -> Unit = {}
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

        val totalCap = maxTotalPrompts.coerceAtLeast(1)
        val perPaperCap = maxPromptsPerPaper.coerceAtLeast(1)
        val perNoteCap = maxPromptsPerNote.coerceAtLeast(1)

        val includeNotes = sourceFilter == VoiceSourceFilter.ALL || sourceFilter == VoiceSourceFilter.NOTES_ONLY
        val includePapers = sourceFilter == VoiceSourceFilter.ALL || sourceFilter == VoiceSourceFilter.PAPERS_ONLY

        val matchingNotes = if (includeNotes) {
            studyNotes.filter { matchesSelection(it.folderId, it.subject) }
        } else {
            emptyList()
        }
        val matchingPapers = if (includePapers) {
            papers.filter { matchesSelection(it.folderId, it.subject) }
        } else {
            emptyList()
        }

        val prompts = mutableListOf<VoicePromptItem>()
        var skippedFormulaItems = 0
        var isTruncated = false

        fun extractNoteBlockPrompt(
            note: StudyNoteEntity,
            noteFolderPath: String,
            language: String,
            block: StudyNoteBlock
        ): VoicePromptItem? {
            return when (block) {
                is StudyNoteBlock.OneLiner -> {
                    val cleanLead = cleanSegment(block.leadTerm)
                    val cleanDetail = cleanSegment(block.detail)
                    if (cleanLead.isBlank() && cleanDetail.isBlank()) {
                        skippedFormulaItems++
                        return null
                    }
                    if (!includeFormulas && isFormulaHeavyText(block.leadTerm) && isFormulaHeavyText(block.detail)) {
                        skippedFormulaItems++
                        return null
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
                    if (!hasEnoughContent) return null
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
                        return null
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
                }

                is StudyNoteBlock.Callout -> {
                    val cleanLabel = cleanSegment(block.label)
                    val cleanBody = cleanSegment(block.body)
                    if (cleanBody.isBlank()) {
                        skippedFormulaItems++
                        return null
                    }
                    val expressions = if (includeFormulas) {
                        extractFormulaExpressions(block.label, block.body)
                    } else {
                        emptyList()
                    }
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
                        return null
                    }
                    val firstPhrase = cleanText.split(Regex("[.!?\n]")).firstOrNull()?.trim().orEmpty().take(90)
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
                }

                is StudyNoteBlock.Table -> {
                    if (!includeFormulas || block.rows.isEmpty()) return null
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
                    if (rowSummaries.isEmpty()) return null
                    val combined = rowSummaries.joinToString("\n")
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
                }

                is StudyNoteBlock.Heading -> null
            }
        }

        fun processNotesRange(startIdx: Int, capLimit: Int): Pair<Int, Boolean> {
            var idx = startIdx
            while (idx < matchingNotes.size) {
                if (prompts.size >= capLimit) {
                    return idx to true
                }
                val note = matchingNotes[idx]
                try {
                    onItemProcessed()
                    val rule = note.parsedFormatRule()
                    val noteFolderPath = folderPathsById[note.folderId.orEmpty()]
                        ?.ifBlank { FolderPathHelper.formatDisplayPath(note.subject) }
                        ?: FolderPathHelper.formatDisplayPath(note.subject).ifBlank { "General Notes" }
                    val language = rule.language.ifBlank { "English" }
                    val blocks = StudyNoteParser.parseBlocks(note.rawContent, rule)
                    var addedForNote = 0
                    for (block in blocks) {
                        if (prompts.size >= capLimit) {
                            return idx to true
                        }
                        if (addedForNote >= perNoteCap) {
                            isTruncated = true
                            break
                        }
                        val item = extractNoteBlockPrompt(note, noteFolderPath, language, block)
                        if (item != null) {
                            prompts.add(item)
                            addedForNote++
                        }
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (_: Throwable) {
                    // Skip malformed note safely instead of crashing VoicePracticeScreen
                }
                idx++
            }
            return idx to false
        }

        fun processPapers(capLimit: Int) {
            for (paperEntity in matchingPapers) {
                if (prompts.size >= capLimit) {
                    isTruncated = true
                    break
                }
                try {
                    onItemProcessed()
                    val parsed = paperParser(paperEntity)
                    val paperFolderPath = folderPathsById[paperEntity.folderId.orEmpty()]
                        ?.ifBlank { FolderPathHelper.formatDisplayPath(paperEntity.subject) }
                        ?: FolderPathHelper.formatDisplayPath(paperEntity.subject).ifBlank { "Exam Papers" }

                    var addedForPaper = 0
                    for (q in parsed.questions) {
                        if (prompts.size >= capLimit) {
                            isTruncated = true
                            return
                        }
                        if (addedForPaper >= perPaperCap) {
                            isTruncated = true
                            break
                        }
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
                        addedForPaper++
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (_: Throwable) {
                    // Skip malformed paper safely instead of crashing VoicePracticeScreen
                }
            }
        }

        val initialNotesCap = if (includePapers && matchingPapers.isNotEmpty() && totalCap < Int.MAX_VALUE) {
            (totalCap / 2).coerceAtLeast(1)
        } else {
            totalCap
        }

        val (pausedNoteIdx, notesHitInitialCap) = if (includeNotes) {
            processNotesRange(startIdx = 0, capLimit = initialNotesCap)
        } else {
            0 to false
        }

        if (includePapers) {
            processPapers(capLimit = totalCap)
        }

        // Backfill remaining note capacity if papers didn't exhaust totalCap
        if (includeNotes && notesHitInitialCap) {
            if (prompts.size < totalCap && pausedNoteIdx < matchingNotes.size) {
                val resumeIdx = if (pausedNoteIdx + 1 < matchingNotes.size) pausedNoteIdx + 1 else pausedNoteIdx
                if (resumeIdx > pausedNoteIdx || prompts.none { it.id.startsWith("note_${matchingNotes[pausedNoteIdx].id}_") }) {
                    val (_, stillHitCap) = processNotesRange(startIdx = resumeIdx, capLimit = totalCap)
                    if (stillHitCap) isTruncated = true
                } else {
                    isTruncated = true
                }
            } else {
                isTruncated = true
            }
        }

        return VoiceFolderPromptResult(
            prompts = prompts,
            skippedFormulaItems = skippedFormulaItems,
            matchedFolderCount = targetFolderIds.size,
            isTruncated = isTruncated,
            totalMatchingSources = matchingNotes.size + matchingPapers.size
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

    /**
     * O(N) non-backtracking scanner for delimited segments (`openDelim`..`closeDelim`).
     * Prevents StackOverflowError and catastrophic backtracking on long or unclosed inputs.
     */
    private fun collectDelimitedExpressions(
        text: String,
        openDelim: String,
        closeDelim: String,
        allowNewline: Boolean,
        maxLength: Int = 1500,
        out: MutableSet<String>
    ) {
        var idx = 0
        while (idx < text.length) {
            val start = text.indexOf(openDelim, idx)
            if (start == -1) break
            val contentStart = start + openDelim.length
            if (openDelim == "$" && contentStart < text.length && text[contentStart] == '$') {
                // Skip `$$` here so display math is parsed cleanly as `$$...$$`
                idx = contentStart + 1
                continue
            }
            val end = text.indexOf(closeDelim, contentStart)
            if (end == -1) break
            if (end - contentStart > maxLength) {
                idx = contentStart
                continue
            }
            val inner = text.substring(contentStart, end)
            if (inner.isNotBlank() && (allowNewline || !inner.contains('\n'))) {
                out.add(text.substring(start, end + closeDelim.length).trim())
            }
            idx = end + closeDelim.length
        }
    }

    private fun stripDelimitedSegments(
        text: String,
        openDelim: String,
        closeDelim: String,
        allowNewline: Boolean,
        maxLength: Int = 4000
    ): String {
        if (!text.contains(openDelim)) return text
        val sb = StringBuilder(text.length)
        var idx = 0
        while (idx < text.length) {
            val start = text.indexOf(openDelim, idx)
            if (start == -1) {
                sb.append(text, idx, text.length)
                break
            }
            sb.append(text, idx, start)
            val contentStart = start + openDelim.length
            val end = text.indexOf(closeDelim, contentStart)
            if (end == -1 || (!allowNewline && text.indexOf('\n', contentStart).let { it != -1 && it < end }) || end - contentStart > maxLength) {
                sb.append(openDelim)
                idx = contentStart
            } else {
                sb.append(' ')
                idx = end + closeDelim.length
            }
        }
        return sb.toString()
    }

    fun extractFormulaExpressions(vararg texts: String): List<String> {
        val found = linkedSetOf<String>()

        for (raw in texts) {
            if (raw.isBlank()) continue
            val normalized = raw.replace("\r\n", "\n")

            collectDelimitedExpressions(normalized, "$$", "$$", allowNewline = true, out = found)
            collectDelimitedExpressions(normalized, "\\[", "\\]", allowNewline = true, out = found)
            collectDelimitedExpressions(normalized, "$", "$", allowNewline = false, out = found)
            collectDelimitedExpressions(normalized, "\\(", "\\)", allowNewline = false, out = found)

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

                val isPureDelimited = (trimmed.startsWith("$$") && trimmed.endsWith("$$")) ||
                    (trimmed.startsWith("$") && trimmed.endsWith("$")) ||
                    (trimmed.startsWith("\\[") && trimmed.endsWith("\\]")) ||
                    (trimmed.startsWith("\\(") && trimmed.endsWith("\\)"))
                if (isFormulaHeavyText(trimmed) && !isPureDelimited) {
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

        // Remove code blocks and LaTeX display/inline math expressions in O(N) without backtracking regex
        text = stripDelimitedSegments(text, "```", "```", allowNewline = true)
        text = stripDelimitedSegments(text, "$$", "$$", allowNewline = true)
        text = stripDelimitedSegments(text, "$", "$", allowNewline = false)
        text = stripDelimitedSegments(text, "\\[", "\\]", allowNewline = true)
        text = stripDelimitedSegments(text, "\\(", "\\)", allowNewline = false)
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
            .replace("’", "'")
            .replace("‘", "'")
            .replace("“", "\"")
            .replace("”", "\"")
            .replace("\\frac", " fraction ")
            .replace("\\sqrt", " square root ")
            .replace("√", " square root ")
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
            .replace("≠", " not equals ")
            .replace("≤", " less than or equal ")
            .replace("≥", " greater than or equal ")
            .replace("=", " equals ")
            .replace("+", " plus ")
            .replace("−", " minus ")
            .replace("×", " times ")
            .replace("÷", " divided ")
            .replace("°", " degrees ")
            .replace("&", " and ")
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
        val isTimed = durationMs >= 1000L
        val safeDurationMs = durationMs.coerceAtLeast(0L)
        val gaps = detectHesitationGaps(rmsSamples, sampleIntervalMs = sampleIntervalMs)
        val silenceMs = gaps.sumOf { it.durationMs }.coerceAtMost(safeDurationMs)
        val speakingMs = (safeDurationMs - silenceMs).coerceAtLeast(0L)
        val hesitationRatio = if (safeDurationMs > 0L) {
            ((silenceMs.toDouble() / safeDurationMs.toDouble()) * 100.0)
                .roundToInt()
                .coerceIn(0, 100)
        } else {
            0
        }

        val words = transcript.trim().split(Regex("\\s+")).filter { normalizeToken(it).isNotBlank() }
        val minutes = safeDurationMs / 60_000.0
        val wpm = if (isTimed && minutes > 0.0 && words.isNotEmpty()) {
            (words.size / minutes).roundToInt().coerceIn(0, 320)
        } else {
            0
        }

        val breakdown = (explicitFillerOverrides?.takeIf { it.isNotEmpty() }
            ?: extractFillersFromTranscript(transcript))
        val totalFillers = breakdown.sumOf { it.count }
        val fillerRate = if (isTimed && minutes > 0.0) {
            ((totalFillers / minutes) * 10.0).roundToInt() / 10.0
        } else {
            0.0
        }

        val coverage = if (mode == VoicePracticeMode.FREE_SUBJECT_SPEAK) {
            null
        } else {
            computeKeywordCoveragePercent(transcript, referenceSummary)
        }

        val comparison = if (referenceSummary.isNotBlank() && mode != VoicePracticeMode.FREE_SUBJECT_SPEAK) {
            alignSpokenToReference(
                referenceText = referenceSummary,
                spokenText = transcript,
                estimatedWpm = wpm
            )
        } else {
            null
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
            keywordCoveragePercent = coverage,
            comparison = comparison
        )
    }

    /**
     * Single source of truth for the exact passage shown in Read Aloud mode and graded by
     * [evaluateOfflineSpeechCheck]. Strips structural labels ("Formula:", "Key answer:") so
     * every visible word in the Read Aloud script matches word-for-word scoring 1-to-1.
     */
    fun resolveReadAloudPassage(prompt: VoicePromptItem): String {
        val base = prompt.referenceSummary.ifBlank { prompt.promptQuestion }.trim()
        val missingFormulas = prompt.formulaExpressions.filter { expr ->
            expr.isNotBlank() && !base.contains(expr, ignoreCase = true)
        }
        val combined = if (missingFormulas.isEmpty()) base else "$base\n${missingFormulas.joinToString("\n")}"
        return combined.lines()
            .map { line ->
                line.trim()
                    .replace(Regex("""^(?:Formula|Key answer)\s*:\s*""", RegexOption.IGNORE_CASE), "")
                    .trim()
            }
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    /**
     * Returns the exact count of alignable words in [text] using the same tokenizer as scoring.
     */
    fun countAlignmentWords(text: String): Int =
        tokenizeForComparison(prepareReferenceForAlignment(text)).take(260).size

    /**
     * Cleanly appends a new speech-recognition segment onto an existing transcript without
     * losing words or creating duplicate whitespace across user pauses / engine restarts.
     */
    fun mergeTranscriptSegments(committed: String, incomingSegment: String): String {
        val left = committed.trim()
        val right = incomingSegment.trim()
        return when {
            left.isEmpty() -> right
            right.isEmpty() -> left
            else -> "$left $right"
        }
    }

    /**
     * 100% offline, deterministic speech-to-text comparison against the shown subject text.
     * Computes exact matched words, wrong/mispronounced substitutions, skipped omissions,
     * extra pronounced words, consecutive repetitions, pace rating, and actionable tips.
     */
    fun evaluateOfflineSpeechCheck(
        spokenTranscript: String,
        prompt: VoicePromptItem,
        durationMs: Long,
        mode: VoicePracticeMode,
        engineLabel: String = "Offline On-Device Text Comparison"
    ): VoiceAnalysisResult {
        val targetReference = when (mode) {
            VoicePracticeMode.READ_ALOUD -> resolveReadAloudPassage(prompt)
            VoicePracticeMode.EXPLAIN_FROM_MEMORY -> prompt.referenceSummary.ifBlank { prompt.promptQuestion }
            VoicePracticeMode.FREE_SUBJECT_SPEAK -> prompt.referenceSummary.ifBlank { prompt.promptQuestion }
        }

        val metrics = computeMetrics(
            durationMs = durationMs,
            rmsSamples = emptyList(),
            transcript = spokenTranscript,
            referenceSummary = targetReference,
            mode = mode
        )

        val comp = metrics.comparison ?: alignSpokenToReference(
            referenceText = targetReference,
            spokenText = spokenTranscript,
            estimatedWpm = metrics.estimatedWpm
        )

        val strengths = buildList {
            if (comp.matchPercent >= 85) {
                add("Strong alignment: ${comp.matchedCount} of ${comp.totalReferenceWords} reference words matched (${comp.matchPercent}%).")
            } else if (comp.matchedCount > 0) {
                add("Matched ${comp.matchedCount} of ${comp.totalReferenceWords} reference words (${comp.matchPercent}%).")
            }
            if (comp.mistakeCount == 0 && comp.totalSpokenWords > 0) {
                add("Zero word substitutions detected.")
            }
            if (comp.repetitionCount == 0 && comp.totalSpokenWords > 0) {
                add("No consecutive word repetitions.")
            }
            if (metrics.estimatedWpm in 100..160) {
                add("Steady speaking pace (${metrics.estimatedWpm} WPM).")
            }
        }

        val tips = buildList {
            if (comp.mistakePairs.isNotEmpty()) {
                val sampleMistakes = comp.mistakePairs.take(4).joinToString(", ") {
                    "\"${it.expected}\" → \"${it.spoken}\""
                }
                add("Review ${comp.mistakeCount} mistaken word(s): $sampleMistakes.")
            }
            if (comp.missedCount > 0) {
                val sampleMissed = comp.missedWords.take(5).joinToString(", ")
                add("Skipped ${comp.missedCount} word(s) from the text ($sampleMissed).")
            }
            if (comp.extraCount > 0) {
                val repeatNote = if (comp.repetitionCount > 0) " including ${comp.repetitionCount} repetition(s)" else ""
                val sampleExtra = comp.extraWords.take(5).joinToString(", ")
                add("Pronounced ${comp.extraCount} extra word(s)$repeatNote ($sampleExtra).")
            }
            if (metrics.estimatedWpm > 165) {
                add("Pace was fast (${metrics.estimatedWpm} WPM) — slow down slightly on key terms and formulas.")
            } else if (metrics.estimatedWpm in 1..85) {
                add("Pace was slow (${metrics.estimatedWpm} WPM) — practice reading the full phrase smoothly.")
            }
            if (isEmpty() && comp.totalSpokenWords > 0) {
                add("Excellent offline match! Try the next prompt or switch to Explain from Memory.")
            }
        }

        val effectiveMetrics = if (metrics.comparison != null) metrics else metrics.copy(comparison = comp)
        val primaryScore = when (mode) {
            VoicePracticeMode.READ_ALOUD -> comp.netAccuracyPercent
            VoicePracticeMode.EXPLAIN_FROM_MEMORY -> metrics.keywordCoveragePercent ?: comp.matchPercent
            VoicePracticeMode.FREE_SUBJECT_SPEAK -> metrics.keywordCoveragePercent ?: comp.matchPercent
        }

        return VoiceAnalysisResult(
            verbatimTranscript = spokenTranscript.trim(),
            annotatedSegments = tokenizeTranscriptForHighlight(spokenTranscript),
            metrics = effectiveMetrics,
            topicAccuracyPercent = primaryScore,
            strengths = strengths,
            coachingTips = tips,
            usedAiAudioAnalysis = false,
            engineLabel = engineLabel
        )
    }

    private data class AlignmentWord(
        val display: String,
        val key: String
    )

    /**
     * Deterministic Needleman-Wunsch / Levenshtein word alignment between the shown reference text
     * and the user's spoken transcript. Guarantees exact counts for Matched, Mistakes (substitutions),
     * Missed (omissions), Extra pronounced words, and Consecutive Repetitions.
     */
    fun alignSpokenToReference(
        referenceText: String,
        spokenText: String,
        estimatedWpm: Int = 0
    ): VoiceComparisonStats {
        val refWords = tokenizeForComparison(prepareReferenceForAlignment(referenceText)).take(260)
        val spokenWords = tokenizeForComparison(spokenText).take(260)

        val n = refWords.size
        val m = spokenWords.size
        val paceLabel = when {
            estimatedWpm <= 0 -> "Not Timed"
            estimatedWpm < 100 -> "Slow (<100 WPM)"
            estimatedWpm <= 160 -> "Steady (100–160 WPM)"
            else -> "Fast (>160 WPM)"
        }

        if (n == 0 && m == 0) {
            return VoiceComparisonStats(
                totalReferenceWords = 0,
                totalSpokenWords = 0,
                matchedCount = 0,
                matchPercent = 0,
                netAccuracyPercent = 0,
                mistakeCount = 0,
                missedCount = 0,
                extraCount = 0,
                repetitionCount = 0,
                paceLabel = paceLabel,
                alignmentTokens = emptyList(),
                mistakePairs = emptyList(),
                missedWords = emptyList(),
                extraWords = emptyList(),
                repeatedWords = emptyList()
            )
        }

        // Exact LCS edit cost (match=0, delete=1, insert=1) guarantees maximum word matches;
        // balanced gaps between matched anchors are classified as 1-to-1 MISTAKEs in the pass below.
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) dp[i][0] = i
        for (j in 0..m) dp[0][j] = j

        for (i in 1..n) {
            for (j in 1..m) {
                dp[i][j] = if (refWords[i - 1].key == spokenWords[j - 1].key) {
                    dp[i - 1][j - 1]
                } else {
                    minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1)
                }
            }
        }

        val reversedOps = mutableListOf<VoiceAlignmentToken>()
        var i = n
        var j = m
        while (i > 0 || j > 0) {
            when {
                i > 0 && j > 0 && refWords[i - 1].key == spokenWords[j - 1].key -> {
                    val isConsecutiveDuplicate = j >= 2 && spokenWords[j - 1].key == spokenWords[j - 2].key
                    if (isConsecutiveDuplicate && dp[i][j] == dp[i][j - 1] + 1) {
                        reversedOps.add(
                            VoiceAlignmentToken(
                                expectedWord = null,
                                spokenWord = spokenWords[j - 1].display,
                                status = VoiceWordMatchStatus.EXTRA
                            )
                        )
                        j--
                    } else {
                        reversedOps.add(
                            VoiceAlignmentToken(
                                expectedWord = refWords[i - 1].display,
                                spokenWord = spokenWords[j - 1].display,
                                status = VoiceWordMatchStatus.MATCHED
                            )
                        )
                        i--
                        j--
                    }
                }
                i > 0 && (j == 0 || dp[i][j] == dp[i - 1][j] + 1) -> {
                    reversedOps.add(
                        VoiceAlignmentToken(
                            expectedWord = refWords[i - 1].display,
                            spokenWord = null,
                            status = VoiceWordMatchStatus.MISSED
                        )
                    )
                    i--
                }
                else -> {
                    reversedOps.add(
                        VoiceAlignmentToken(
                            expectedWord = null,
                            spokenWord = spokenWords[j - 1].display,
                            status = VoiceWordMatchStatus.EXTRA
                        )
                    )
                    j--
                }
            }
        }

        val rawAlignment = reversedOps.asReversed()
        val finalAlignment = mutableListOf<VoiceAlignmentToken>()
        var lastSpokenKey: String? = null

        var matchedCount = 0
        var mistakeCount = 0
        var missedCount = 0
        var extraCount = 0
        var repetitionCount = 0
        val mistakePairs = mutableListOf<VoiceMistakePair>()
        val missedWords = mutableListOf<String>()
        val extraWords = mutableListOf<String>()
        val repeatedWords = mutableListOf<String>()

        val missedBuffer = mutableListOf<String>()
        val extraBuffer = mutableListOf<String>()

        fun emitExtraOrRepeat(word: String) {
            val key = canonicalKey(word)
            val isRepeat = key.isNotEmpty() && key == lastSpokenKey
            extraCount++
            if (isRepeat) {
                repetitionCount++
                repeatedWords.add(word)
                finalAlignment.add(
                    VoiceAlignmentToken(
                        expectedWord = null,
                        spokenWord = word,
                        status = VoiceWordMatchStatus.REPEATED
                    )
                )
            } else {
                finalAlignment.add(
                    VoiceAlignmentToken(
                        expectedWord = null,
                        spokenWord = word,
                        status = VoiceWordMatchStatus.EXTRA
                    )
                )
            }
            extraWords.add(word)
            if (key.isNotEmpty()) lastSpokenKey = key
        }

        fun flushGap() {
            while (extraBuffer.isNotEmpty()) {
                val firstKey = canonicalKey(extraBuffer.first())
                if (firstKey.isNotEmpty() && firstKey == lastSpokenKey) {
                    emitExtraOrRepeat(extraBuffer.removeAt(0))
                } else {
                    break
                }
            }

            if (missedBuffer.isNotEmpty() && extraBuffer.isNotEmpty()) {
                if (missedBuffer.size == extraBuffer.size) {
                    for (idx in missedBuffer.indices) {
                        val expected = missedBuffer[idx]
                        val spoken = extraBuffer[idx]
                        mistakeCount++
                        mistakePairs.add(VoiceMistakePair(expected = expected, spoken = spoken))
                        finalAlignment.add(
                            VoiceAlignmentToken(
                                expectedWord = expected,
                                spokenWord = spoken,
                                status = VoiceWordMatchStatus.MISTAKE
                            )
                        )
                        val key = canonicalKey(spoken)
                        if (key.isNotEmpty()) lastSpokenKey = key
                    }
                    missedBuffer.clear()
                    extraBuffer.clear()
                    return
                }

                // Collapse consecutive duplicates within extraBuffer to see if distinct slots match missedBuffer
                val grouped = mutableListOf<Pair<String, MutableList<String>>>()
                for (word in extraBuffer) {
                    val key = canonicalKey(word)
                    val last = grouped.lastOrNull()
                    if (last != null && key.isNotEmpty() && canonicalKey(last.first) == key) {
                        last.second.add(word)
                    } else {
                        grouped.add(word to mutableListOf())
                    }
                }
                if (grouped.size == missedBuffer.size) {
                    for (idx in missedBuffer.indices) {
                        val expected = missedBuffer[idx]
                        val (spoken, dups) = grouped[idx]
                        mistakeCount++
                        mistakePairs.add(VoiceMistakePair(expected = expected, spoken = spoken))
                        finalAlignment.add(
                            VoiceAlignmentToken(
                                expectedWord = expected,
                                spokenWord = spoken,
                                status = VoiceWordMatchStatus.MISTAKE
                            )
                        )
                        val key = canonicalKey(spoken)
                        if (key.isNotEmpty()) lastSpokenKey = key
                        for (dup in dups) {
                            emitExtraOrRepeat(dup)
                        }
                    }
                    missedBuffer.clear()
                    extraBuffer.clear()
                    return
                }
            }

            for (expected in missedBuffer) {
                missedCount++
                missedWords.add(expected)
                finalAlignment.add(
                    VoiceAlignmentToken(
                        expectedWord = expected,
                        spokenWord = null,
                        status = VoiceWordMatchStatus.MISSED
                    )
                )
            }
            for (spoken in extraBuffer) {
                emitExtraOrRepeat(spoken)
            }
            missedBuffer.clear()
            extraBuffer.clear()
        }

        for (token in rawAlignment) {
            when (token.status) {
                VoiceWordMatchStatus.MATCHED -> {
                    flushGap()
                    matchedCount++
                    lastSpokenKey = token.spokenWord?.let(::canonicalKey)
                    finalAlignment.add(token)
                }
                VoiceWordMatchStatus.MISSED -> {
                    token.expectedWord?.let(missedBuffer::add)
                }
                VoiceWordMatchStatus.EXTRA, VoiceWordMatchStatus.REPEATED -> {
                    token.spokenWord?.let(extraBuffer::add)
                }
                VoiceWordMatchStatus.MISTAKE -> {
                    flushGap()
                    mistakeCount++
                    mistakePairs.add(
                        VoiceMistakePair(
                            expected = token.expectedWord.orEmpty(),
                            spoken = token.spokenWord.orEmpty()
                        )
                    )
                    lastSpokenKey = token.spokenWord?.let(::canonicalKey)
                    finalAlignment.add(token)
                }
            }
        }
        flushGap()

        val matchPercent = if (n > 0) {
            ((matchedCount.toDouble() / n.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)
        } else {
            0
        }
        val netAccuracyPercent = if (n > 0) {
            (((matchedCount - extraCount).coerceAtLeast(0).toDouble() / n.toDouble()) * 100.0)
                .roundToInt()
                .coerceIn(0, 100)
        } else {
            0
        }

        return VoiceComparisonStats(
            totalReferenceWords = n,
            totalSpokenWords = m,
            matchedCount = matchedCount,
            matchPercent = matchPercent,
            netAccuracyPercent = netAccuracyPercent,
            mistakeCount = mistakeCount,
            missedCount = missedCount,
            extraCount = extraCount,
            repetitionCount = repetitionCount,
            paceLabel = paceLabel,
            alignmentTokens = finalAlignment,
            mistakePairs = mistakePairs,
            missedWords = missedWords,
            extraWords = extraWords,
            repeatedWords = repeatedWords
        )
    }

    private fun prepareReferenceForAlignment(raw: String): String {
        return raw.lines().joinToString(" ") { line ->
            line.trim()
                .replace(Regex("""^(?:Formula|Key answer)\s*:\s*""", RegexOption.IGNORE_CASE), "")
        }
    }

    private fun tokenizeForComparison(raw: String): List<AlignmentWord> {
        if (raw.isBlank()) return emptyList()
        val expanded = verbalizeSymbolsForKeywords(
            raw
                .replace(Regex("""\\frac\s*\{([^}]+)\}\s*\{([^}]+)\}"""), " $1 over $2 ")
                .replace("$$", " ")
                .replace("$", " ")
                .replace("\\(", " ")
                .replace("\\)", " ")
                .replace("\\[", " ")
                .replace("\\]", " ")
                .replace("{", " ")
                .replace("}", " ")
                .replace("%", " percent ")
                .replace("—", " ")
                .replace("–", " ")
                .replace(Regex("""(?<=\p{L})-(?=\p{L})"""), " ")
                .replace(Regex("""(?<=\p{L})/(?=\p{L})"""), " ")
        )
        return expanded
            .split(Regex("\\s+"))
            .mapNotNull { piece ->
                val cleanDisplay = piece.trim('[', ']', '(', ')', ',', '.', ';', ':', '!', '?', '"', '\'', '’', '‘', '“', '”', '—', '–')
                val key = canonicalKey(cleanDisplay)
                if (cleanDisplay.isNotBlank() && key.isNotBlank()) {
                    AlignmentWord(display = cleanDisplay, key = key)
                } else {
                    null
                }
            }
    }

    private val spokenNumberWordMap = mapOf(
        "zero" to "0",
        "one" to "1",
        "two" to "2",
        "three" to "3",
        "four" to "4",
        "five" to "5",
        "six" to "6",
        "seven" to "7",
        "eight" to "8",
        "nine" to "9",
        "ten" to "10",
        "eleven" to "11",
        "twelve" to "12",
        "thirteen" to "13",
        "fourteen" to "14",
        "fifteen" to "15",
        "sixteen" to "16",
        "seventeen" to "17",
        "eighteen" to "18",
        "nineteen" to "19",
        "twenty" to "20",
        "thirty" to "30",
        "forty" to "40",
        "fifty" to "50",
        "sixty" to "60",
        "seventy" to "70",
        "eighty" to "80",
        "ninety" to "90",
        "hundred" to "100"
    )

    private fun canonicalKey(raw: String): String {
        val stripped = raw.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
        return spokenNumberWordMap[stripped] ?: stripped
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
