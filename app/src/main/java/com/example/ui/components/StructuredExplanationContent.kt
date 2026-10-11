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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ExamTheme
import com.example.util.McqParser
import com.example.util.RichTextHelper

data class OptionExplanationItem(
    val letter: String,
    val isCorrect: Boolean,
    val content: String
)

data class ParsedExplanationData(
    val intro: String?,
    val options: List<OptionExplanationItem>,
    val outro: String?
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
        """^(?:[-*•]\s*)?(?:\*\*|__)?\s*(?:(?:Correct|Ans|Answer|सही\s*उत्तर|उत्तर)[\.:\-]?\s*)?(?:(?:Option|Opt|विकल्प)\s+[\(\[]?([A-Da-d1-4कखगघअबसद१-४①-④❶-❹⓵-⓸⒈-⒋Ⓐ-Ⓓⓐ-ⓓকখগঘઅબકડஅஆஇஈఅఆఇఈഎബിസിഡിأابجد甲乙丙丁가나다라㈎㈏㈐㈑アイウエあいうえА-Да-д\u0410-\u0413\u0430-\u0433]|i{1,3}|iv|I{1,3}|IV)[\)\]\.:\-–—\s]|[\(\[]([A-Da-d1-4कखगघअबसद१-४①-④❶-❹⓵-⓸⒈-⒋Ⓐ-Ⓓⓐ-ⓓকখগঘઅબકડஅஆஇஈఅఆఇఈഎബിസിഡിأابجد甲乙丙丁가나다라㈎㈏㈐㈑アイウエあいうえА-Да-д\u0410-\u0413\u0430-\u0433]|i{1,3}|iv|I{1,3}|IV)[\)\]]|([①-④❶-❹⓵-⓸⒈-⒋Ⓐ-Ⓓⓐ-ⓓ㈎-㈑])|(?:\*\*|__)([A-Da-d1-4कखगघअबसद१-४কখগঘઅબકડஅஆஇஈఅఆఇఈഎബിസിഡിأابجد甲乙丙丁가나다라アイウエあいうえА-Да-д\u0410-\u0413\u0430-\u0433]|i{1,3}|iv|I{1,3}|IV)(?:\*\*|__)|([A-Da-d1-4कखगघअबसद१-४কখগঘઅબકડஅஆஇஈఅఆఇఈഎബിസിഡിأابجد甲乙丙丁가나다라アイウエあいうえА-Да-д\u0410-\u0413\u0430-\u0433]|i{1,3}|iv|I{1,3}|IV)\s*(?:[\)\.\:\-–—]|(?=\s+(?:सही|गलत|सत्य|असत्य|correct|incorrect|true|false|옳음|정답|오답|틀림|правильно|неверно|верно))))(?:\*\*|__)?\s*[\.:\-–—]?\s*(.*)$""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Cleans inline labels like "— Correct.", "[Correct]", "(Correct)", "Correct - "
     * from explanation option content so all options align identically without giant label spans.
     */
    fun cleanOptionContent(text: String): String {
        var s = text.trim()
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

    fun parse(rawExplanation: String?, correctLetter: String?): ParsedExplanationData {
        if (rawExplanation.isNullOrBlank()) {
            return ParsedExplanationData(null, emptyList(), null)
        }

        val formatted = McqParser.formatMultiLineExplanation(rawExplanation)
        val optionItems = mutableListOf<OptionExplanationItem>()
        val introLines = mutableListOf<String>()
        val outroLines = mutableListOf<String>()

        // Check if explanation has a dedicated Option-by-Option section
        val optSectionRegex = Regex(
            """(?im)^\s*(?:#+\s*)?(?:(?:Option|Opt)(?:s)?(?:\s*[- ]by[- ]Option)?\s+(?:Analysis|Explanation|Rationale|Breakdown)|विकल्प\s*वार\s*व्याख्या|विकल्प\s*वार\s*विश्लेषण)\s*[:.\-]?\s*$"""
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

            // Guard against common explanation section subheadings like "Option Analysis:", "Concept & Reasoning:", "Key Point:"
            val isPedagogicalHeading = Regex(
                """(?i)^\s*(?:#+\s*)?(?:(?:Option|Opt)(?:s)?\s*(?:Analysis|Breakdown|Explanation)|Concept\s*&\s*Reasoning|Key\s*Points?|Core\s*Concept|Summary|Takeaway|विकल्प\s*वार\s*(?:व्याख्या|विश्लेषण)|मुख्य\s*अवधारणा|मुख्य\s*बिंदु)\s*[:.\-]?\s*$"""
            ).matches(line)
            if (isPedagogicalHeading) {
                if (!foundFirstOption) {
                    introLines.add(line)
                } else {
                    outroLines.add(line)
                }
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
                    if (Regex("""^(?:Note|Conclusion|Summary|Hence|Therefore|Key\s*(?:Point|distinction|takeaway|concept|difference)|\*\*Final|Final\s*matching|निष्कर्ष|मुख्य\s*बिंदु|नोट|विशेष|याद\s*रखें|स्मरण\s*रहे)\s*[:\-]""", RegexOption.IGNORE_CASE).containsMatchIn(line)) {
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
            options = optionItems,
            outro = outroText
        )
    }
}

/**
 * Clean, professional pedagogical explanation rendering:
 * - Single cohesive container for the explanation body.
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
            // 1. Intro section (e.g. Concept / Pedagogical preamble / Derivation)
            if (!parsed.intro.isNullOrBlank()) {
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
                    if (!parsed.intro.isNullOrBlank()) {
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

            // 3. Outro section (e.g. Conclusion / Key takeaway / Key distinction)
            if (!parsed.outro.isNullOrBlank()) {
                if (!parsed.intro.isNullOrBlank() || parsed.options.isNotEmpty()) {
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
