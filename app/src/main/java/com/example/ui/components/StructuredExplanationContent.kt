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
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ExamTheme
import com.example.util.McqParser
import com.example.util.RichTextHelper

enum class PedagogicalSectionType {
    CORE_CONCEPT,
    FORMULA,
    KEY_IDEA,
    MEMORY_ANCHOR,
    DERIVATION,
    SUMMARY,
    GENERAL
}

data class PedagogicalSection(
    val type: PedagogicalSectionType,
    val title: String,
    val content: String
)

data class OptionExplanationItem(
    val letter: String,
    val isCorrect: Boolean,
    val content: String
)

data class ParsedExplanationData(
    val intro: String?,
    val pedagogicalSections: List<PedagogicalSection> = emptyList(),
    val options: List<OptionExplanationItem>,
    val outro: String?,
    val outroPedagogicalSections: List<PedagogicalSection> = emptyList()
)

object ExplanationBreakdownParser {
    // Regex matching lines that ONLY state the correct answer / option or header:
    // e.g., "1. Correct Answer: (A)", "1. (b)", "Q1. Correct Answer: B", "Ans: (A)", "Detailed Explanations", "Correct answer: 4", "उत्तर: (ग)", "सही उत्तर: ग"
    private val STANDALONE_ANSWER_OR_HEADER_REGEX = Regex(
        """^(?:(?:(?:\d+|Q\d+|प्रश्न\s*\d+|प्र\.\s*\d+)[\.:\-—]?\s*)?(?:(?:Correct\s+(?:Answer|Option)|Answer|Ans|सही\s*उत्तर|उत्तर)\b\s*[:\-—]?\s*(?:(?:विकल्प|Option)\s*)?[\(\[]?[A-Da-d1-4कखगघअबसद१-४][\)\]]?\s*[\.:\-—]?|(?:\d+|Q\d+|प्रश्न\s*\d+)[\.:\-—]\s*[\(\[]?[A-Da-d1-4कखगघअबसद१-४][\)\]]?\s*[\.:\-—]?|(?:Detailed\s+)?Explanations?\s*[:\-—]?|(?:विस्तृत\s+)?व्याख्या\s*[:\-—]?))\s*$""",
        RegexOption.IGNORE_CASE
    )

    // Regex matching leading "Correct Answer: B." or "Explanation:" or "व्याख्या:" at the beginning of an explanation sentence
    private val LEADING_ANSWER_PREFIX_REGEX = Regex(
        """^(?:(?:(?:\d+|Q\d+|प्रश्न\s*\d+)[\.:\-—]?\s*)?(?:(?:Correct\s+(?:Answer|Option)|Answer|Ans|सही\s*उत्तर|उत्तर)\b\s*[:\-—]?\s*(?:(?:विकल्प|Option)\s*)?[\(\[]?[A-Da-d1-4कखगघअबसद१-४][\)\]]?\s*[\.:\-—]?\s*)*(?:(?:Detailed\s+)?Explanation|(?:विस्तृत\s+)?व्याख्या)\s*[:\-—]?\s*)""",
        RegexOption.IGNORE_CASE
    )

    private val OPTION_START_REGEX = Regex(
        """^(?:[-*•]\s*)?(?:\*\*|__)?\s*(?:(?:Correct|Ans|Answer|सही\s*उत्तर|उत्तर)[\.:\-]?\s*)?(?:(?:Option|Opt|विकल्प)\s+[\(\[]?([A-Da-d1-4कखगघअबसद१-४①-④❶-❹⓵-⓸⒈-⒋Ⓐ-Ⓓⓐ-ⓓকখগঘઅબકડஅஆஇஈఅఆఇఈഎബിസിഡിأابجد甲乙丙丁가나다라㈎㈏㈐㈑アイウエあいうえА-Да-д\u0410-\u0413\u0430-\u0433]|i{1,3}|iv|I{1,3}|IV)[\)\]\.:\-–—\s]|[\(\[]([A-Da-d1-4कखगघअबसद१-४①-④❶-❹⓵-⓸⒈-⒋Ⓐ-Ⓓⓐ-ⓓকখগঘઅબકડஅஆஇஈఅఆఇఈഎബിസിഡിأابجد甲乙丙丁가나다라㈎㈏㈐㈑アイウエあいうえА-Да-д\u0410-\u0413\u0430-\u0433]|i{1,3}|iv|I{1,3}|IV)[\)\]]|([①-④❶-❹⓵-⓸⒈-⒋Ⓐ-Ⓓⓐ-ⓓ㈎-㈑])|(?:\*\*|__)(?:(?:Option|Opt|विकल्प)\s+)?[\(\[]?([A-Da-d1-4कखगघअबसद१-४কখগঘઅબકડஅஆஇஈఅఆఇఈഎബിസിഡിأابجد甲乙丙丁가나다라アイウエあいうえА-Да-д\u0410-\u0413\u0430-\u0433]|i{1,3}|iv|I{1,3}|IV)[\)\]]?\s*[\.:\-–—]?(?:\s*\([^\)]*\))?(?:\*\*|__)|([A-Da-d1-4कखगघअबसद१-४কখগঘઅબકડஅஆஇஈఅఆఇఈഎബിസിഡിأابجد甲乙丙丁가나다라アイウエあいうえА-Да-д\u0410-\u0413\u0430-\u0433]|i{1,3}|iv|I{1,3}|IV)\s*(?:[\)\.\:\-–—]|(?=\s+(?:सही|गलत|सत्य|असत्य|correct|incorrect|true|false|옳음|정답|오답|틀림|правильно|неверно|верно))))(?:\*\*|__)?\s*[\.:\-–—]?\s*(.*)$""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Cleans inline labels like "— Correct.", "[Correct]", "(Correct)", "Correct - "
     * from explanation option content so all options align identically without giant label spans.
     */
    fun cleanOptionContent(text: String): String {
        var s = text.trim()
        // Normalize escaped markdown characters
        s = s.replace("""\*\*""", "**").replace("""\*""", "*").replace("""\|""", "|")
        // Strip any standalone separator lines like ---, ***, ___
        s = s.replace(Regex("""(?m)^\s*(?:[-*_=\u2014\u2013\u2500\u2501\u2504\u2505]\s*){3,}${'$'}"""), "").trim()
        // Remove bold/markdown markers on status words like **Incorrect.** or **Correct.**
        s = s.replace(Regex("""^\*+\s*(?:Incorrect|Correct|सही|गलत|सत्य|असत्य|옳음|정답|오답|틀림|正解|誤り|正确|错误|Верно|Неверно)\.?\*+\s*""", RegexOption.IGNORE_CASE), "")
        s = s.replace(Regex("""^[—\-–:]\s*(?:\*+)?(?:Incorrect|Correct|सही|गलत|सत्य|असत्य|옳음|정답|오답|틀림|正解|誤り|正确|错误|Верно|Неверно)\.?(?:\*+)?\s*""", RegexOption.IGNORE_CASE), "")
        // 1. Remove leading redundant markers like "सही:", "गलत:", "[Correct]", "(Correct)", "Correct -", "Correct: "
        s = s.replace(
            Regex(
                """^(?:\[\s*Correct(?:\s+Option)?\s*\]|\(\s*Correct(?:\s+Option)?\s*\)|\bCorrect(?:\s+Option)?\b\s*[:\-—\.]?\s*|(?:सही|गलत|सत्य|असत्य|옳음|정답|오답|틀림|Верно|Неверно|Правильно)\s*[:\-—\.]?\s*)""",
                RegexOption.IGNORE_CASE
            ),
            ""
        ).trim()
        // 2. Remove inline or trailing "— Correct.", "— Correct", "- Correct", "[Correct]", "— Incorrect" (multilingual)
        s = s.replace(
            Regex(
                """\s*[—\-–]\s*(?:\[\s*(?:Correct|सही|सत्य|옳음|정답|正解|正确|Верно)(?:\s+Option)?\s*\]|\(\s*(?:Correct|सही|सत्य|옳음|정답|正解|正确|Верно)(?:\s+Option)?\s*\)|(?:Correct|सही|सत्य|옳음|정답|正解|正确|Верно|Правильно)(?:\s+Option)?\b[\.:\-—]?|(?:Incorrect|गलत|असत्य|옳지\s*않음|오답|틀림|誤り|不適当|错误|不正确|Неверно|Неправильно|Ошибка)\b[\.:\-—]?)\s*""",
                RegexOption.IGNORE_CASE
            ),
            ". "
        ).trim()
        // 3. Remove standalone "[Correct]" or "(Correct)" anywhere in the text
        s = s.replace(Regex("""\s*\[\s*Correct(?:\s+Option)?\s*\]\s*""", RegexOption.IGNORE_CASE), " ").trim()
        s = s.replace(Regex("""\s*\(\s*Correct(?:\s+Option)?\s*\)\s*""", RegexOption.IGNORE_CASE), " ").trim()
        // 4. Clean up trailing bold/italic asterisks or underscores left over (e.g. from **① text — Incorrect.**)
        s = s.replace(Regex("""(?:\*+|_+)\s*$"""), "").trim()
        // 5. Clean up any double periods or leading separators
        s = s.replace(Regex("""\.\s*\."""), ".").trim()
        s = s.replace(Regex("""^[—\-–\.:]\s*"""), "").trim()
        return s
    }

    /**
     * Parses pedagogical blocks into typed sections (Core Concept, Formula, Key Idea, Memory Anchor, etc.).
     */
    fun parsePedagogicalSections(lines: List<String>): List<PedagogicalSection> {
        val sections = mutableListOf<PedagogicalSection>()
        if (lines.isEmpty()) return sections

        var currentType = PedagogicalSectionType.GENERAL
        var currentTitle = ""
        val currentBuffer = StringBuilder()

        fun flush() {
            val content = currentBuffer.toString().trim()
            if (content.isNotEmpty()) {
                sections.add(
                    PedagogicalSection(
                        type = currentType,
                        title = currentTitle,
                        content = content
                    )
                )
                currentBuffer.clear()
            }
        }

        val headingPattern = Regex(
            """^(?:#+\s*)?(?:\*\*|__)?\\?\*?\\?\*?\s*(Core\s*Concept[s]?|Concept\s*&\s*Reasoning|Fundamental\s*Concept[s]?|Theoretical\s*Principle[s]?|Formula[s]?|Important\s*Formula[s]?|Governing\s*Equation[s]?|Key\s*Formula[s]?|Key\s*Idea[s]?|Key\s*Points?|Key\s*Principle[s]?|Key\s*Takeaways?|Takeaways?|Memory\s*Anchor[s]?|Memory\s*Trick[s]?|Mnemonic[s]?|Exam\s*Tips?|Quick\s*Tricks?|Derivation\s*&\s*Analysis|Derivation|Step-by-Step\s*Derivation|Calculation|Summary|Conclusion|Note|Important\s*Note|Takeaway|मुख्य\s*अवधारणा|सूत्र|मुख्य\s*बिंदु|याद\s*रखें|स्मरण\s*रहे|विश्लेषण|निष्कर्ष|नोट)\s*(?:\*\*|__)?\\?\*?\\?\*?\s*[:\-–—]?\s*(.*)$""",
            RegexOption.IGNORE_CASE
        )

        for (line in lines) {
            val trimmed = line.trim()
                .replace("""\*\*""", "**")
                .replace("""\*""", "*")
                .replace("""\|""", "|")
                .replace(Regex("""^\|\s*([A-Za-z\u0900-\u097F\s&:\-]+?)\s*\|$"""), "$1")
            if (trimmed.isEmpty()) continue

            val match = headingPattern.find(trimmed)
            if (match != null) {
                flush()
                val rawTitle = match.groupValues[1].trim()
                val textAfter = match.groupValues[2].trim()

                val normalizedTitle = rawTitle.lowercase()
                currentType = when {
                    normalizedTitle.contains("core concept") || normalizedTitle.contains("fundamental concept") || normalizedTitle.contains("theoretical principle") || normalizedTitle.contains("concept & reasoning") || normalizedTitle.contains("मुख्य अवधारणा") -> PedagogicalSectionType.CORE_CONCEPT
                    normalizedTitle.contains("formula") || normalizedTitle.contains("governing equation") || normalizedTitle.contains("सूत्र") -> PedagogicalSectionType.FORMULA
                    normalizedTitle.contains("key idea") || normalizedTitle.contains("key point") || normalizedTitle.contains("key principle") || normalizedTitle.contains("key takeaway") || normalizedTitle.contains("takeaway") || normalizedTitle.contains("मुख्य बिंदु") -> PedagogicalSectionType.KEY_IDEA
                    normalizedTitle.contains("memory") || normalizedTitle.contains("mnemonic") || normalizedTitle.contains("exam tip") || normalizedTitle.contains("quick trick") || normalizedTitle.contains("याद रखें") || normalizedTitle.contains("स्मरण रहे") -> PedagogicalSectionType.MEMORY_ANCHOR
                    normalizedTitle.contains("derivation") || normalizedTitle.contains("calculation") || normalizedTitle.contains("विश्लेषण") -> PedagogicalSectionType.DERIVATION
                    normalizedTitle.contains("summary") || normalizedTitle.contains("conclusion") || normalizedTitle.contains("note") || normalizedTitle.contains("निष्कर्ष") || normalizedTitle.contains("नोट") -> PedagogicalSectionType.SUMMARY
                    else -> PedagogicalSectionType.GENERAL
                }
                currentTitle = rawTitle.replace(Regex("""^\*+|\*+$"""), "").trim()
                if (textAfter.isNotEmpty()) {
                    currentBuffer.append(textAfter)
                }
            } else {
                if (currentBuffer.isNotEmpty()) currentBuffer.append("\n")
                currentBuffer.append(trimmed)
            }
        }
        flush()
        return sections
    }

    fun parse(rawExplanation: String?, correctLetter: String?): ParsedExplanationData {
        if (rawExplanation.isNullOrBlank()) {
            return ParsedExplanationData(null, emptyList(), emptyList(), null)
        }

        val rawFormatted = McqParser.formatMultiLineExplanation(rawExplanation)
        // Split lines where multiple option items or distractors are packed together, e.g.:
        // "**Answer and Distractor Analysis:** - **A:** ... - **B:** ..."
        val formatted = rawFormatted.replace(
            Regex("""(?<=[^\n])\s+(?:[-*•]\s*)(?=(?:\*\*|__)?\s*[\(\[]?[A-Da-d1-4][\)\]]?\s*[\.:\-–—])"""),
            "\n- "
        )
        val optionItems = mutableListOf<OptionExplanationItem>()
        val introLines = mutableListOf<String>()
        val outroLines = mutableListOf<String>()

        // Check if explanation has a dedicated Option-by-Option section
        val optSectionRegex = Regex(
            """(?im)^\s*(?:#+\s*)?(?:\*\*|__)?\s*(?:(?:Option|Opt|Distractor|Answer\s+and\s+Distractor|Option\s+and\s+Distractor)(?:s)?(?:\s*[- ]by[- ]Option)?\s+(?:Analysis|Explanation|Rationale|Breakdown|Review)|विकल्प\s*वार\s*(?:व्याख्या|विश्लेषण))\s*[:.\-]?(?:\*\*|__)?\s*$"""
        )
        val optSectionMatch = optSectionRegex.find(formatted)

        val targetLines: List<String>
        if (optSectionMatch != null) {
            val preSectionText = formatted.substring(0, optSectionMatch.range.first).trim()
            if (preSectionText.isNotEmpty()) {
                introLines.addAll(
                    preSectionText.lines()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() && !McqParser.STANDALONE_SEPARATOR_REGEX.matches(it) && !it.startsWith("---") }
                )
            }
            val postSectionText = formatted.substring(optSectionMatch.range.last).trim()
            targetLines = postSectionText.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !McqParser.STANDALONE_SEPARATOR_REGEX.matches(it) && !it.startsWith("---") }
        } else {
            targetLines = formatted.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !McqParser.STANDALONE_SEPARATOR_REGEX.matches(it) && !it.startsWith("---") }
        }

        var foundFirstOption = false
        var currentLetter: String? = null
        var currentIsCorrect = false
        val currentBuffer = StringBuilder()

        fun flushCurrent() {
            if (currentLetter != null) {
                val fullText = cleanOptionContent(currentBuffer.toString())
                optionItems.add(
                    OptionExplanationItem(
                        letter = currentLetter!!,
                        isCorrect = currentIsCorrect,
                        content = fullText
                    )
                )
                currentBuffer.clear()
            }
        }

        for (line in targetLines) {
            // Guard against standalone separator lines (e.g. ---, ***, ___)
            if (McqParser.STANDALONE_SEPARATOR_REGEX.matches(line) || line.startsWith("---")) {
                continue
            }

            // Guard against common explanation section subheadings like "Option Analysis:", "विकल्प वार व्याख्या:", "Answer and Distractor Analysis:"
            val isPedagogicalHeading = Regex(
                """(?i)^\s*(?:#+\s*)?(?:\*\*|__)?\s*(?:(?:Option|Opt|Distractor|Answer\s+and\s+Distractor)(?:s)?\s*(?:Analysis|Breakdown|Explanation|Rationale)|विकल्प\s*वार\s*(?:व्याख्या|विश्लेषण))\s*[:.\-]?(?:\*\*|__)?\s*$"""
            ).matches(line)
            if (isPedagogicalHeading) {
                continue
            }

            val match = OPTION_START_REGEX.find(line)
            if (match != null) {
                flushCurrent()
                foundFirstOption = true
                val rawLetter = match.groupValues.slice(1..5).firstOrNull { it.isNotEmpty() } ?: ""
                val letter = McqParser.canonicalOption(rawLetter) ?: rawLetter.uppercase()
                val textAfterMarker = match.groupValues.lastOrNull()?.trim() ?: ""

                val isCorrectWord = (line.contains("correct", ignoreCase = true) && !line.contains("incorrect", ignoreCase = true)) ||
                        ((line.contains("सही") || line.contains("सत्य") || line.contains("सटीक")) && !line.contains("गलत") && !line.contains("असत्य") && !line.contains("सही नहीं")) ||
                        ((line.contains("옳음") || line.contains("정답") || line.contains("맞음")) && !line.contains("옳지") && !line.contains("오답") && !line.contains("틀림")) ||
                        ((line.contains("正解") || line.contains("正しい") || line.contains("適当")) && !line.contains("誤り") && !line.contains("不適当") && !line.contains("不正解")) ||
                        (line.contains("正确") && !line.contains("错误") && !line.contains("不正确")) ||
                        ((line.contains("Верно", ignoreCase = true) || line.contains("Правильно", ignoreCase = true) || line.contains("Верный", ignoreCase = true)) && !line.contains("Неверно", ignoreCase = true) && !line.contains("Неправильно", ignoreCase = true))

                val canonicalExpected = correctLetter?.let {
                    McqParser.canonicalOption(it) ?: it.trim().uppercase().removeSurrounding("(", ")").removeSurrounding("[", "]").removePrefix("OPTION").trim().removeSuffix(".")
                }

                val lineIsCorrect = isCorrectWord || (canonicalExpected != null && letter.equals(canonicalExpected, ignoreCase = true)) || letter.equals(correctLetter?.trim(), ignoreCase = true)

                currentLetter = letter
                currentIsCorrect = lineIsCorrect
                currentBuffer.append(textAfterMarker)
            } else {
                if (!foundFirstOption) {
                    // Filter out duplicate standalone "Correct Answer: B" lines from the explanation body
                    if (!STANDALONE_ANSWER_OR_HEADER_REGEX.matches(line)) {
                        val cleanedLine = line.replace(LEADING_ANSWER_PREFIX_REGEX, "").trim()
                        if (cleanedLine.isNotEmpty()) {
                            introLines.add(cleanedLine)
                        }
                    }
                } else if (currentLetter != null) {
                    if (Regex("""^(?:#+\s*)?(?:\*\*|__)?\\?\*?\\?\*?\s*(?:Note|Conclusion|Summary|Hence|Therefore|Key\s*(?:Idea|Ideas|Point|Points|Principle|Principles|Takeaway|Takeaways|Formula|Formulas|distinction|concept|difference)|Memory\s*(?:Anchor|Anchors|Trick|Tricks)|Mnemonic[s]?|Exam\s*Tips?|Quick\s*Tricks?|Core\s*Concept|Formula|Governing\s*Equation|\*\*Final|Final\s*matching|निष्कर्ष|मुख्य\s*बिंदु|नोट|विशेष|याद\s*रखें|स्मरण\s*रहे)\s*(?:\*\*|__)?\\?\*?\\?\*?\s*[:\-–—]?""", RegexOption.IGNORE_CASE).containsMatchIn(line)) {
                        flushCurrent()
                        currentLetter = null
                        outroLines.add(line)
                    } else {
                        if (currentBuffer.isNotEmpty()) currentBuffer.append("\n")
                        currentBuffer.append(line)
                    }
                } else {
                    outroLines.add(line)
                }
            }
        }
        flushCurrent()

        val introPedagogicalSections = parsePedagogicalSections(introLines)
        val outroPedagogicalSections = parsePedagogicalSections(outroLines)

        val introText = introLines.joinToString("\n\n")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
            .ifEmpty { null }

        val outroText = outroLines.joinToString("\n\n")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
            .ifEmpty { null }

        return ParsedExplanationData(
            intro = introText,
            pedagogicalSections = introPedagogicalSections,
            options = optionItems,
            outro = outroText,
            outroPedagogicalSections = outroPedagogicalSections
        )
    }
}

private data class SectionStyle(
    val badgeText: String,
    val badgeColor: Color,
    val badgeBg: Color,
    val cardBg: Color,
    val icon: ImageVector?
)

@Composable
fun PedagogicalSectionCard(
    section: PedagogicalSection,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    val style = when (section.type) {
        PedagogicalSectionType.CORE_CONCEPT -> SectionStyle(
            badgeText = section.title.ifEmpty { "CORE CONCEPT" }.uppercase(),
            badgeColor = colors.tealDark,
            badgeBg = colors.teal.copy(alpha = 0.15f),
            cardBg = colors.tealLight.copy(alpha = if (colors.isDark) 0.15f else 0.35f),
            icon = Icons.Default.Lightbulb
        )
        PedagogicalSectionType.FORMULA -> SectionStyle(
            badgeText = section.title.ifEmpty { "FORMULA / EQUATION" }.uppercase(),
            badgeColor = if (colors.isDark) Color(0xFF818CF8) else Color(0xFF4338CA),
            badgeBg = if (colors.isDark) Color(0xFF312E81) else Color(0xFFEEF2FF),
            cardBg = if (colors.isDark) Color(0xFF1E1B4B).copy(alpha = 0.4f) else Color(0xFFF5F3FF),
            icon = Icons.Default.Functions
        )
        PedagogicalSectionType.KEY_IDEA -> SectionStyle(
            badgeText = section.title.ifEmpty { "KEY IDEA" }.uppercase(),
            badgeColor = if (colors.isDark) Color(0xFFFBBF24) else Color(0xFFB45309),
            badgeBg = if (colors.isDark) Color(0xFF78350F).copy(alpha = 0.4f) else Color(0xFFFEF3C7),
            cardBg = if (colors.isDark) Color(0xFF451A03).copy(alpha = 0.25f) else Color(0xFFFFFBEB),
            icon = Icons.Default.Stars
        )
        PedagogicalSectionType.MEMORY_ANCHOR -> SectionStyle(
            badgeText = section.title.ifEmpty { "MEMORY ANCHOR" }.uppercase(),
            badgeColor = if (colors.isDark) Color(0xFFA78BFA) else Color(0xFF6D28D9),
            badgeBg = if (colors.isDark) Color(0xFF4C1D95).copy(alpha = 0.4f) else Color(0xFFEDE9FE),
            cardBg = if (colors.isDark) Color(0xFF2E1065).copy(alpha = 0.25f) else Color(0xFFFAF5FF),
            icon = Icons.Default.Bookmark
        )
        PedagogicalSectionType.DERIVATION -> SectionStyle(
            badgeText = section.title.ifEmpty { "DERIVATION & ANALYSIS" }.uppercase(),
            badgeColor = if (colors.isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
            badgeBg = if (colors.isDark) Color(0xFF0C4A6E).copy(alpha = 0.4f) else Color(0xFFE0F2FE),
            cardBg = if (colors.isDark) Color(0xFF082F49).copy(alpha = 0.25f) else Color(0xFFF0F9FF),
            icon = Icons.Default.Timeline
        )
        PedagogicalSectionType.SUMMARY -> SectionStyle(
            badgeText = section.title.ifEmpty { "SUMMARY" }.uppercase(),
            badgeColor = colors.inkBold,
            badgeBg = colors.surfaceVariant,
            cardBg = colors.surfaceVariant.copy(alpha = 0.4f),
            icon = Icons.Default.CheckCircle
        )
        PedagogicalSectionType.GENERAL -> SectionStyle(
            badgeText = "",
            badgeColor = colors.inkBold,
            badgeBg = Color.Transparent,
            cardBg = Color.Transparent,
            icon = null
        )
    }

    if (section.type == PedagogicalSectionType.GENERAL) {
        LatexText(
            text = section.content,
            color = colors.ink,
            fontSize = 14.5.sp,
            isBold = false,
            lineHeight = 22.sp,
            modifier = modifier.fillMaxWidth()
        )
    } else {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = style.cardBg,
            border = BorderStroke(0.8.dp, style.badgeColor.copy(alpha = 0.35f)),
            modifier = modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (style.icon != null) {
                        Icon(
                            imageVector = style.icon,
                            contentDescription = null,
                            tint = style.badgeColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = style.badgeBg,
                        border = BorderStroke(0.6.dp, style.badgeColor.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = style.badgeText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = style.badgeColor,
                            letterSpacing = 0.4.sp,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(7.dp))

                if (section.type == PedagogicalSectionType.FORMULA) {
                    val formulaLines = section.content.lines()
                    val whereIdx = formulaLines.indexOfFirst {
                        Regex("""(?i)^\s*(?:where|here|जहाँ|यहाँ)\s*[:,\-]""").containsMatchIn(it)
                    }
                    if (whereIdx > 0) {
                        val formulaPart = formulaLines.subList(0, whereIdx).joinToString("\n").trim()
                        val wherePart = formulaLines.subList(whereIdx, formulaLines.size).joinToString("\n").trim()

                        if (MathFormulaParser.has2DMath(formulaPart)) {
                            MathFormulaView(
                                formula = formulaPart,
                                color = colors.inkBold,
                                fontSize = 16.sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            LatexText(
                                text = formulaPart,
                                color = colors.inkBold,
                                fontSize = 15.sp,
                                isBold = true,
                                lineHeight = 22.sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LatexText(
                            text = wherePart,
                            color = colors.ink,
                            fontSize = 13.5.sp,
                            isBold = false,
                            lineHeight = 20.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else if (MathFormulaParser.has2DMath(section.content)) {
                        MathFormulaView(
                            formula = section.content,
                            color = colors.inkBold,
                            fontSize = 15.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LatexText(
                            text = section.content,
                            color = colors.inkBold,
                            fontSize = 14.5.sp,
                            isBold = false,
                            lineHeight = 22.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else if (MathFormulaParser.has2DMath(section.content)) {
                    MathFormulaView(
                        formula = section.content,
                        color = colors.inkBold,
                        fontSize = 15.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LatexText(
                        text = section.content,
                        color = colors.inkBold,
                        fontSize = 14.5.sp,
                        isBold = false,
                        lineHeight = 22.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * Clean, professional pedagogical explanation rendering:
 * - Single cohesive container for the explanation body.
 * - Distinct cards and accent badges for Core Concept, Formula, Key Idea, and Memory Anchor.
 * - Consistent typography (15.sp font size, 22.sp line-height) and rich LaTeX support.
 * - Option-specific remarks formatted as clean, elegant bullet points rather than nested option cards/pill badges.
 * - Eliminates duplicate option buttons, ensuring the explanation reads as a polished pedagogical solution.
 */
@Composable
fun StructuredExplanationContent(
    rawExplanation: String?,
    correctLetter: String?,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    val parsed = ExplanationBreakdownParser.parse(rawExplanation, correctLetter)

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = colors.surfaceVariant.copy(alpha = if (colors.isDark) 0.35f else 0.45f),
        border = BorderStroke(0.8.dp, colors.border.copy(alpha = 0.7f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            // 1. Pedagogical Sections (Core Concept, Formula, Key Idea, Memory Anchor cards)
            if (parsed.pedagogicalSections.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    parsed.pedagogicalSections.forEach { section ->
                        PedagogicalSectionCard(section = section)
                    }
                }
            } else if (!parsed.intro.isNullOrBlank()) {
                LatexText(
                    text = parsed.intro,
                    color = colors.inkBold,
                    fontSize = 15.sp,
                    isBold = false,
                    lineHeight = 22.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 2. Explanations / Option analysis
            if (parsed.options.isNotEmpty()) {
                if (parsed.options.size == 1 && parsed.intro.isNullOrBlank()) {
                    // Single-item explanation: Render directly as clean continuous narrative,
                    // without prepending pseudo-option letter tags or bullet pills.
                    val singleContent = parsed.options.first().content
                    LatexText(
                        text = singleContent,
                        color = colors.ink,
                        fontSize = 14.5.sp,
                        isBold = false,
                        lineHeight = 22.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    if (!parsed.intro.isNullOrBlank() || parsed.pedagogicalSections.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        parsed.options.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "•",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.muted,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = "(${item.letter}) ",
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.inkBold
                                )
                                LatexText(
                                    text = item.content,
                                    color = colors.ink,
                                    fontSize = 14.5.sp,
                                    isBold = false,
                                    lineHeight = 21.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // 3. Outro pedagogical sections (Key Idea, Memory Anchor, Exam Tip, Summary boxed cards)
            if (parsed.outroPedagogicalSections.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    parsed.outroPedagogicalSections.forEach { section ->
                        PedagogicalSectionCard(section = section)
                    }
                }
            }

            // 4. Any remaining non-pedagogical outro text
            if (parsed.outroPedagogicalSections.isEmpty() && !parsed.outro.isNullOrBlank()) {
                if (!parsed.intro.isNullOrBlank() || parsed.pedagogicalSections.isNotEmpty() || parsed.options.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                }
                LatexText(
                    text = parsed.outro,
                    color = colors.ink,
                    fontSize = 14.sp,
                    isBold = false,
                    lineHeight = 21.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
