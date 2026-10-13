package com.example.util

import com.example.model.McqQuestion
import com.example.model.PaperMetadata
import com.example.model.QuestionOptions
import com.example.model.QuestionPaper
import java.util.regex.Pattern

/**
 * Parser for MCQ practice sets directly implementing and expanding
 * the Python MCQ PDF Generator specification.
 */
object McqParser {

    private val ANSWER_HEADING_REGEX = Pattern.compile(
        """^\s*(?:#+\s*)?(?:(?:Phase|Part|Section|Stage|Step|Level)\s*(?:[1-9IVX]+)?\s*[:\-–—]\s*)?(?:(?:quick|official|final|mcq)\s+)?(?:answer\s*keys?|answers?|keys?|उत्तर\s*कुंजी|उत्तर\s*तालिका|उत्तरमाला|उत्तर(?=[\s:\-–—\d\(\[]|$)|정답\s*및\s*해설|정답표|정답|답|正解一覧|正解|解答一覧|解答|正答|参考答案|正确答案|答案|Ответы|Таблица\s*ответов|Ответ|Ключ)\s*[:\-–—]?\s*.*$""",
        Pattern.CASE_INSENSITIVE
    )

    private val EXPLANATION_HEADING_REGEX = Pattern.compile(
        """^\s*(?:#+\s*)?(?:(?:Phase|Part|Section|Stage|Step|Level)\s*(?:[1-9IVX]+)?\s*[:\-–—]\s*)?(?:(?:detailed|option[- ]by[- ]option|step[- ]by[- ]step|comprehensive|complete|question[- ]wise|mcq)\s+)*(?:explanations?|solutions?|व्याख्या\s*सहित\s*उत्तर|विस्तृत\s*व्याख्या|व्याख्यात्मक\s*हल|व्याख्यात्मक\s*उत्तर|व्याख्या|हल|해설|풀이|解説|解答・解説|解法|详细解析|答案解析|试题解析|解析|Пояснение|Решение|Объяснение|Разбор|analysis|rationale)\b.*$""",
        Pattern.CASE_INSENSITIVE
    )

    private val QUESTION_START_REGEX = Pattern.compile(
        """^\s*(?:#+\s*)?(?:\*\*|__)?\s*(?:第\s*(\d{1,4}|[\p{Nd}]{1,4})\s*[問题문]|제\s*(\d{1,4}|[\p{Nd}]{1,4})\s*문|(?:Q(?:uestion)?|MCQ[s]?|प्रश्न|प्र|सवाल|प्रश्नसंख्या|প্রশ্ন|கேள்வி|ప్రశ్న|문제|문|問|問題|题|Вопрос|Задание|№|Задача|Problem|Prob|Item)\.?\s*(?:No\.?|#)?\s*[\(\[]?(\d{1,4}|[\p{Nd}]{1,4})[\)\]]?|[\(\[]?(\d{1,4}|[\p{Nd}]{1,4})[\)\]\.\:\-])\s*[\:\-\.]?\s*(?:\*\*|__)?\s*(.*?)(?:\*\*|__)?\s*$""",
        Pattern.CASE_INSENSITIVE
    )

    private val OPTION_REGEX = Pattern.compile(
        """(?m)(?:^\s*(?:[-*•]\s*)?|\s+)(?:\*\*)?(?:Option\s+|Opt\s+)?(?:\(([a-eA-E1-5कखगघङअबसदई१-५i-vI-Vⅰ-ⅴⅠ-Ⅴকখগঘঙઅબકડઈஅஆஇஈஉఅఆఇఈఉഎബിസിഡിഇأابجده甲乙丙丁戊가나다라마㈎㈏㈐㈑㈒アイウエオあいうえおА-Да-д\u0410-\u0414\u0430-\u0434])\)|\[([a-eA-E1-5कखगघङअबसदई१-५i-vI-Vⅰ-ⅴⅠ-Ⅴকখগঘঙઅબકડઈஅஆஇஈஉఅఆఇఈఉഎബിസിഡിഇأابجده甲乙丙丁戊가나다라마㈎㈏㈐㈑㈒アイウエオあいうえおА-Да-д\u0410-\u0414\u0430-\u0434])\]|([a-eA-Eकखगघङअबसदईকখগঘঙઅબકડઈஅஆஇஈஉఅఆఇఈఉഎബിസിഡിഇأابجده甲乙丙丁戊가나다라마アイウエオあいうえおА-Да-д\u0410-\u0414\u0430-\u0434])(?:\*\*|\*)?\s*[\)\.\:\-–—](?=\s+\S|$)|([a-eA-Eकखगघङअबसदईকখগঘঙઅબકડઈஅஆஇஈஉఅఆఇఈఉഎബിസിഡിഇأابجده甲乙丙丁戊가나다라마アイウエオあいうえおА-Да-д\u0410-\u0414\u0430-\u0434])\b(?=\s*[:\-–—]))(?:\*\*)?\s*|(?m)^\s*(?:[-*•]\s*)?(?:\*\*)?(?:Option\s+|Opt\s+)?([1-5१-५])(?:\*\*)?\s*[\)\.](?=\s+\S|$)|(?m)(?:^\s*(?:[-*•]\s*)?|\s+)(?:\*\*)?([①-⑤❶-❺⓵-⓹⒈-⒌Ⓐ-Ⓔⓐ-ⓔ㈎-㈒])(?:\*\*)?\s*""",
        Pattern.CASE_INSENSITIVE
    )

    fun parseLocalizedInt(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val digitsOnly = StringBuilder()
        for (ch in raw) {
            if (Character.isDigit(ch)) {
                val num = Character.getNumericValue(ch)
                if (num in 0..9) {
                    digitsOnly.append(num)
                }
            }
        }
        return digitsOnly.toString().toIntOrNull()
    }

    fun canonicalOption(raw: String): String? {
        val u = raw.trim().uppercase()
        return when (u) {
            "A", "1", "क", "अ", "①", "❶", "⓵", "⒈", "Ⓐ", "ⓐ", "I", "I.", "ⅰ", "Ⅰ", "१", "১", "૧", "ক", "அ", "అ", "ఎ", "അ", "أ", "ا", "甲", "가", "㈎", "ア", "あ", "А", "\u0410", "\u0430" -> "A"
            "B", "2", "ख", "ब", "②", "❷", "⓶", "⒉", "Ⓑ", "ⓑ", "II", "II.", "ⅱ", "Ⅱ", "२", "২", "૨", "খ", "ஆ", "ఆ", "బి", "ബ", "ب", "乙", "나", "㈏", "イ", "い", "Б", "\u0411", "\u0431" -> "B"
            "C", "3", "ग", "स", "③", "❸", "⓷", "⒊", "Ⓒ", "ⓒ", "III", "III.", "ⅲ", "Ⅲ", "३", "৩", "૩", "গ", "இ", "ఇ", "సి", "സ", "ج", "丙", "다", "㈐", "ウ", "う", "В", "\u0412", "\u0432" -> "C"
            "D", "4", "घ", "द", "④", "❹", "⓸", "⒋", "Ⓓ", "ⓓ", "IV", "IV.", "ⅳ", "Ⅳ", "४", "৪", "૪", "ঘ", "ஈ", "ఈ", "డి", "ദ", "د", "丁", "라", "㈑", "エ", "え", "Г", "\u0413", "\u0433" -> "D"
            "E", "5", "ङ", "ई", "⑤", "❺", "⓹", "⒌", "Ⓔ", "ⓔ", "V", "V.", "ⅴ", "Ⅴ", "५", "৫", "૫", "ঙ", "உ", "ఉ", "ഇ", "هـ", "戊", "마", "㈒", "オ", "お", "Д", "\u0414", "\u0434" -> "E"
            else -> null
        }
    }

    /**
     * Splits questions that are glued onto the same line or immediately follow an option/explanation.
     */
    fun separateMergedQuestions(raw: String): String {
        return raw.lines().joinToString("\n") { line ->
            // Skip lines with matching pairs like 1-B, 2-C, 3-D
            if (Regex("""\b\d\s*[-–—]\s*[A-Za-z]""").containsMatchIn(line)) {
                line
            } else {
                val inlineQuestionPattern = Regex(
                    """([.!?\u2713\u2714\'\"]|(?:(?:\([dD4घ]\)|\[[dD4घ]\]|[dD4घ][\.\):])\s*[^\n]{1,60}?))\s{1,}((?:(?:Q(?:uestion)?|MCQ[s]?|प्रश्न|प्र|Problem|Item)\.?\s*(?:No\.?|#)?\s*\d{1,4}\b|\b\d{1,4}\s*[\.\):])\s+[A-Za-z\u0900-\u097F\(\[\"\'])""",
                    RegexOption.IGNORE_CASE
                )
                inlineQuestionPattern.replace(line, "$1\n$2")
            }
        }
    }

    private val PAIR_ANSWER_REGEX = Pattern.compile(
        """(?<!\w)(?:\*\*|__)?\s*(?:(?:Q(?:uestion)?|MCQ[s]?|प्रश्न|प्र|सवाल|प्रश्नसंख्या|প্রশ্ন|கேள்வி|ప్రశ్న|문제|문|問|第\s*\d+\s*問|第\s*\d+\s*题|题|Вопрос|Задание|№|Задача|Item|Problem)\.?\s*(?:No\.?|#)?\s*)?(\d{1,4}|[\p{Nd}]{1,4})\s*(?:\*\*|__)?\s*[\:\-\.]\s*(?:\*\*|__)?\s*[\(\[]?([ABCD1-4कखगघअबसद१-४①-④❶-❹⓵-⓸⒈-⒋Ⓐ-Ⓓⓐ-ⓓকখগঘઅબકડஅஆஇஈఅఆఇఈഎബിസിഡിأابجد甲乙丙丁가나다라㈎㈏㈐㈑アイウエあいうえА-Да-д\u0410-\u0413\u0430-\u0433])[\)\]]?\s*(?:\*\*|__)?(?!\w)""",
        Pattern.CASE_INSENSITIVE
    )

    private val ANSWER_LABEL_REGEX = Pattern.compile(
        """(?:\b(?:correct\s+(?:answer|option)|answer|ans)\b|(?:सही\s*उत्तर|उत्तर|정답|답|正解|解答|正确答案|答案|Правильный\s*ответ|Ответ))\s*[:\-]?\s*(?:option\s*)?[\(\[]?([ABCD1-4कखगघअबसद१-४①-④❶-❹⓵-⓸⒈-⒋Ⓐ-Ⓓⓐ-ⓓকখগঘઅબકડஅஆஇஈఅఆఇఈഎബിസിഡിأابجد甲乙丙丁가나다라㈎㈏㈐㈑アイウエあいうえА-Да-д\u0410-\u0413\u0430-\u0433])[\)\]]?""",
        Pattern.CASE_INSENSITIVE
    )

    private val EXPLANATION_LABEL_REGEX = Pattern.compile(
        """(?:\b(?:detailed\s+explanation|explanation|exp)\b|(?:व्याख्या|विस्तृत\s*व्याख्या|व्याख्यात्मक\s*हल|हल|해설|풀이|解説|解答・解説|解析|详细解析|Пояснение|Решение|Объяснение))\s*[:\-]?\s*""",
        Pattern.CASE_INSENSITIVE
    )

    fun parse(rawText: String): QuestionPaper {
        val sanitizedRaw = sanitizePastedText(rawText)
        val cleanInput = sanitizedRaw.trim()
        if (cleanInput.isEmpty()) {
            return QuestionPaper(
                metadata = PaperMetadata(),
                questions = emptyList(),
                rawText = ""
            )
        }

        val metadata = extractMetadata(cleanInput)
        val (questionSection, answerSection, explanationSection) = findSections(cleanInput)

        val rawQuestions = parseQuestions(separateMergedQuestions(questionSection))
        
        // Parse answers, explanations, and question types
        val (ansFromAnswerSec, expFromAnswerSec, typesFromAnswerSec) = parseAnswersAndExplanations(answerSection)
        val (ansFromExpSec, expFromExpSec, typesFromExpSec) = parseAnswersAndExplanations(explanationSection)

        val allAnswers = mutableMapOf<Int, String>()
        allAnswers.putAll(ansFromExpSec)
        allAnswers.putAll(ansFromAnswerSec) // Explicit Answer Key section takes precedence over inferred explanations!

        val allExplanations = mutableMapOf<Int, String>()
        allExplanations.putAll(expFromAnswerSec)
        allExplanations.putAll(expFromExpSec)

        val allTypes = mutableMapOf<Int, String>()
        allTypes.putAll(typesFromAnswerSec)
        allTypes.putAll(typesFromExpSec)

        val mergedQuestions = rawQuestions.map { q ->
            val correct = allAnswers[q.num] ?: q.correctAnswer
            val exp = allExplanations[q.num] ?: q.explanation
            val qType = q.questionType ?: allTypes[q.num] ?: inferQuestionType(q.question, q.options)
            q.copy(
                correctAnswer = correct,
                explanation = exp,
                questionType = qType
            )
        }

        val quickCount = mergedQuestions.count { it.correctAnswer != null }
        val expCount = mergedQuestions.count { !it.explanation.isNullOrBlank() }

        return QuestionPaper(
            metadata = metadata,
            questions = mergedQuestions,
            quickAnswersCount = quickCount,
            explanationsCount = expCount,
            rawText = rawText
        )
    }

    /**
     * Sanitizes raw pasted text by stripping Markdown code fences, system prompt markers,
     * and extraneous conversational introductory filler.
     */
    fun sanitizePastedText(raw: String): String {
        var text = raw.replace("\r", "\n")
        // Remove markdown code fences ```markdown / ```text / ```
        text = text.replace(Regex("""^```[a-zA-Z0-9_-]*\s*""", RegexOption.MULTILINE), "")
        text = text.replace(Regex("""^```\s*$""", RegexOption.MULTILINE), "")
        
        val lines = text.lines()
        val cleanedLines = mutableListOf<String>()
        var foundRealContent = false

        for (line in lines) {
            val trimmed = line.trim()
            if (!foundRealContent) {
                if (isPromptOrInstruction(trimmed)) {
                    continue // Skip prompt instructions or conversation intro before questions/title
                }
                if (trimmed.isNotEmpty()) {
                    foundRealContent = true
                }
            }
            cleanedLines.add(line)
        }

        return cleanedLines.joinToString("\n")
    }

    /**
     * Identifies prompt generation templates, system instructions, and LLM filler.
     */
    fun isPromptOrInstruction(line: String): Boolean {
        val l = line.trim()
        if (l.isEmpty()) return false
        // Common AI prompt instructions and conversational intros
        if (Regex("""(?i)^\s*(?:Act as|You are a|Generate|Create\s+\[|Create\s+\d+|Please provide|Here (?:is|are)|Below (?:is|are)|Sure|Certainly|Hope this helps|I have created|In this test|This test contains).*""").matches(l)) return true
        if (Regex("""(?i)^\s*(?:System\s*Prompt|User\s*Prompt|Your\s*Prompt|Prompt|Feedback|Notes?|Review|Instructions?|Guidelines?|Rules?|Format(?:\s*Requirements)?)\s*:.*""").matches(l)) return true
        if (Regex("""(?i)^\s*(?:Quality & Format Guidelines|Questions numbered|Options arranged|Provide all questions|Compact 2-column|Based on new syllabus).*""").matches(l)) return true
        if (Regex("""(?i)^\s*(?:Language|Examination\s*Name|MCQ\s*Count|Syllabus\s*Specification|Topic\s*Name)\s*:.*""").matches(l)) return true
        if (Regex("""(?i)^\s*\[.*?(?:mcq|topic|language|examination|syllabus|insert).*?\]\s*$""").matches(l)) return true
        return false
    }

    fun extractMetadata(rawText: String): PaperMetadata {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        var title = ""
        var subject = ""
        var topic = ""
        var setNumber = ""
        var exam = ""

        val scanLimit = minOf(35, lines.size)
        for (i in 0 until scanLimit) {
            val line = lines[i]
            if (isPromptOrInstruction(line)) continue

            val clean = stripMarkdown(line)

            val titleExplicit = Regex("""\b(?:title|paper\s*title|exam\s*name)\s*:\s*(.+)""", RegexOption.IGNORE_CASE).find(clean)
            if (titleExplicit != null && title.isEmpty()) {
                val cand = titleExplicit.groupValues[1].trim()
                if (!isPromptOrInstruction(cand)) {
                    title = cand
                }
                continue
            }

            val subjMatch = Regex("""\bsubject\s*:\s*(.+)""", RegexOption.IGNORE_CASE).find(clean)
            if (subjMatch != null && subject.isEmpty()) {
                val cand = subjMatch.groupValues[1].trim()
                if (!isPromptOrInstruction(cand)) {
                    subject = cand
                }
                continue
            }

            val topicMatch = Regex("""\b(?:topic|विषय|unit|chapter)\s*:\s*(.+)""", RegexOption.IGNORE_CASE).find(clean)
            if (topicMatch != null && topic.isEmpty()) {
                val cand = topicMatch.groupValues[1].trim()
                if (!isPromptOrInstruction(cand)) {
                    topic = cand
                }
                continue
            }

            val setMatch = Regex("""\b(?:set(?:\s*no\.?|\s*code)?|series)\s*[:\-]?\s*([A-Za-z0-9\-]+)""", RegexOption.IGNORE_CASE).find(clean)
            if (setMatch != null && setNumber.isEmpty()) {
                val foundSet = setMatch.groupValues[1].trim()
                setNumber = if (!foundSet.startsWith("Set", ignoreCase = true)) "Set $foundSet" else foundSet
                continue
            }

            if (title.isEmpty() && line.startsWith("#")) {
                val candidate = line.replace(Regex("""^#+\s*"""), "").trim()
                val isSectionHeader = Regex(
                    """(questions?|answer|explanation|objective|mcq|instructions?|guidelines?|part\s*[A-Z0-9])""",
                    RegexOption.IGNORE_CASE
                ).containsMatchIn(candidate)
                if (!isSectionHeader && !isPromptOrInstruction(candidate) && candidate.length in 3..80) {
                    title = candidate
                }
            } else if (title.isEmpty() && !clean.startsWith("(") && !QUESTION_START_REGEX.matcher(clean).matches()) {
                val isSectionHeader = Regex(
                    """(?i)^\s*(questions?|answer|explanation|objective|instructions?|guidelines?|part\s*[A-Z0-9])\b"""
                ).containsMatchIn(clean)
                if (!isSectionHeader && !isPromptOrInstruction(clean) && clean.length in 4..90 && !clean.contains("|")) {
                    if (Regex("""(?i)\b(paper|test|exam|quiz|series|set|mcqs?|engineering|physics|chemistry|biology|mathematics|history|geography)\b""").containsMatchIn(clean)) {
                        title = clean
                    }
                }
            }

            if (exam.isEmpty()) {
                val examMatch = Regex(
                    """\b(UKSSSC|UKPSC|UPSC|SSC|BANK|RAILWAY|RRB|CTET|TET|NEET|JEE|CBSE|ICSE)\b.*""",
                    RegexOption.IGNORE_CASE
                ).find(clean)
                if (examMatch != null && !isPromptOrInstruction(examMatch.value)) {
                    exam = examMatch.value.trim()
                }
            }
        }

        if (title.isEmpty()) {
            title = if (topic.isNotEmpty()) "$topic Practice Set" else "MCQ Practice Set"
        }

        if (subject.isEmpty()) {
            val subjFromTitle = Regex("""(?i)\b(Civil Engineering|Mechanical Engineering|Electrical Engineering|Computer Science|Physics|Chemistry|Biology|Mathematics|History|Geography|Economics|Polity)\b""").find(title)
            subject = subjFromTitle?.value ?: if (topic.isNotEmpty()) topic else "General Studies"
        }

        if (setNumber.isEmpty()) {
            // Check if title mentions Set A/B/C/etc.
            val titleSetMatch = Regex("""\b(Set\s*[\-A-Za-z0-9]+)\b""", RegexOption.IGNORE_CASE).find(title)
            setNumber = titleSetMatch?.value?.trim() ?: "Set A"
        }

        return PaperMetadata(
            title = title,
            subject = subject,
            topic = topic,
            setNumber = setNumber,
            exam = exam
        )
    }

    fun findSections(rawText: String): Triple<String, String, String> {
        val lines = rawText.lines()
        var answerStart: Int? = null
        var explanationStart: Int? = null

        val combinedHeadingRegex = Regex(
            """(?:answer|उत्तर(?=[\s:\-–—\d\(\[]|$)|solution|हल).*(?:detailed\s*explanation|explanation|व्याख्या)|(?:detailed\s*explanation|explanation|व्याख्या).*(?:answer|उत्तर(?=[\s:\-–—\d\(\[]|$))""",
            RegexOption.IGNORE_CASE
        )

        for (i in lines.indices) {
            val cleanLine = stripMarkdown(lines[i]).trim()
            if (cleanLine.isBlank()) continue

            if (combinedHeadingRegex.containsMatchIn(cleanLine)) {
                if (answerStart == null) answerStart = i
                if (explanationStart == null) explanationStart = i
                continue
            }

            val isPhase2Answer = Regex("""(?i)^\s*(?:#+\s*)?(?:Phase|Part|Section|Stage|Step|Level|चरण|भाग|खण्ड|खंड|этап|часть|шаг|단계|단)\s*(?:2|२|২|૨|২|२|II|ii|둘|이)\b.*(?:answer|key|उत्तर(?=[\s:\-–—\d\(\[]|कुंजी|तालिका|$)|कुंजी|तालिका|ответ|정답|답)""").containsMatchIn(cleanLine)
            val isPhase3Exp = Regex("""(?i)^\s*(?:#+\s*)?(?:Phase|Part|Section|Stage|Step|Level|चरण|भाग|खण्ड|खंड|этап|часть|шаг|단계|단)\s*(?:3|३|৩|૩|३|३|III|iii|셋|삼)\b.*(?:explanation|solut|व्याख्या|हल|пояснен|объяснен|해설|풀이)""").containsMatchIn(cleanLine)

            if (isPhase2Answer && answerStart == null) {
                answerStart = i
            } else if (isPhase3Exp && explanationStart == null) {
                explanationStart = i
            } else if (explanationStart == null && EXPLANATION_HEADING_REGEX.matcher(cleanLine).matches()) {
                explanationStart = i
            } else if (answerStart == null && ANSWER_HEADING_REGEX.matcher(cleanLine).matches()) {
                val isSingleAnsLine = Regex("""(?i)^\s*(?:#+\s*)?(?:(?:correct\s+)?(?:answer|ans)|उत्तर(?=[\s:\-–—\d\(\[]|$)|정답|正解|答案)\s*[:\-–—]?\s*[\(\[]?[A-Da-d1-4कखगघअबसद१-४][\)\]]?(?:\s+[^\n]*)?$""").matches(cleanLine)
                if (!isSingleAnsLine) {
                    answerStart = i
                }
            }
        }

        // Fallback: If no explicit heading found, check for a trailing answers/explanations sequence
        // e.g. after questions end, a line like "1 (B) detail_1" or "1. (B) detail" or "1: A"
        if (answerStart == null && explanationStart == null) {
            val trailingStartRegex = Pattern.compile(
                """^\s*(?:\*\*|__)?\s*(?:(?:Q(?:uestion)?|MCQ[s]?|Explanation|Exp|Sol|Solution)\.?\s*(?:No\.?|#)?\s*)?1\s*(?:[\.\:\)\-]|(?=\s*[\(\[]?[ABCD][\)\]]?))\s*[\(\[]?[ABCD][\)\]]?""",
                Pattern.CASE_INSENSITIVE
            )
            // Look for this pattern after at least 5 lines of questions
            for (i in 5 until lines.size) {
                if (trailingStartRegex.matcher(lines[i].trim()).find()) {
                    answerStart = i - 1 // Include potential subheader or split from here
                    explanationStart = i - 1
                    break
                }
            }
        }

        val starts = listOfNotNull(answerStart, explanationStart)
        val firstSection = if (starts.isNotEmpty()) starts.minOrNull() ?: lines.size else lines.size

        val questionText = lines.take(firstSection).joinToString("\n")

        val answerText = if (answerStart != null) {
            val end = if (explanationStart != null && explanationStart > answerStart) {
                explanationStart
            } else {
                lines.size
            }
            lines.subList(answerStart + 1, end).joinToString("\n")
        } else {
            ""
        }

        val explanationText = if (explanationStart != null) {
            lines.subList(explanationStart + 1, lines.size).joinToString("\n")
        } else {
            ""
        }

        return Triple(questionText, answerText, explanationText)
    }

    private fun isExplicitQuestionMarker(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed.startsWith("#") ||
                Regex("""(?i)^\s*(?:#+\s*)?(?:\*\*|__)?\s*(?:Q(?:uestion)?|MCQ[s]?|प्रश्न|प्र|सवाल|प्रश्नसंख्या|প্রশ্ন|கேள்வி|ప్రశ్న|문제|문|問|第\s*\d+\s*[問题문]|제\s*\d+\s*문|题|Вопрос|Задание|№|Задача|Problem|Item)(?:\b|[\d\p{Nd}\.\:\)])""").containsMatchIn(trimmed) ||
                Regex("""^\s*(?:\*\*|__)\s*(?:(?:Q(?:uestion)?|MCQ[s]?|प्रश्न|प्र|सवाल|प्रश्नसंख्या|প্রশ্ন|கேள்வி|ప్రశ్న|문제|문|問|第\s*\d+\s*[問题문]|제\s*\d+\s*문|题|Вопрос|Задание|№|Задача|Problem|Item)\.?\s*(?:No\.?|#)?\s*)?[\d\p{Nd}]{1,4}\s*[\.:\)]\s*(?:\*\*|__)""").containsMatchIn(trimmed)
    }

    private fun parseQuestions(questionText: String): List<McqQuestion> {
        val lines = questionText.lines()
        val allStarts = mutableListOf<Triple<Int, Int, String>>() // lineIndex, number, firstLineText

        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.isEmpty()) continue
            // If the line is purely an option sequence (e.g. starts with (1) or (a) with no question header before it), skip it
            val optPair = extractOptionsFromBlock(line)
            if (optPair != null) {
                val textBeforeOpt = line.substring(0, optPair.second).trim()
                val stemWords = textBeforeOpt.replace(
                    Regex("""^\s*(?:#+\s*)?(?:\*\*|__)?\s*(?:Q(?:uestion)?|MCQ[s]?|प्रश्न|प्र|Problem|Prob|Item)?\.?\s*(?:No\.?|#)?\s*[\(\[]?\d+[\)\]\.\:\-]?\s*""", RegexOption.IGNORE_CASE),
                    ""
                ).trim()
                if (textBeforeOpt.isEmpty() || stemWords.isEmpty() || !QUESTION_START_REGEX.matcher(textBeforeOpt).matches()) {
                    continue
                }
            }
            val matcher = QUESTION_START_REGEX.matcher(lines[i])
            if (matcher.matches()) {
                val num = (1 until matcher.groupCount()).firstNotNullOfOrNull { idx ->
                    matcher.group(idx)?.takeIf { it.isNotBlank() }?.let { parseLocalizedInt(it) }
                }
                val text = matcher.group(matcher.groupCount())?.trim() ?: ""
                if (num != null && !isPromptOrInstruction(text)) {
                    allStarts.add(Triple(i, num, text))
                }
            }
        }

        // Identify real question boundaries without merging adjacent questions.
        val explicitStarts = allStarts.filter { isExplicitQuestionMarker(lines[it.first]) }

        val effectiveStarts = if (explicitStarts.isNotEmpty()) {
            val firstExplicitIdx = allStarts.indexOfFirst { isExplicitQuestionMarker(lines[it.first]) }
            if (firstExplicitIdx > 0 && allStarts[0].second == 1) {
                listOf(allStarts[0]) + explicitStarts
            } else {
                explicitStarts
            }
        } else {
            allStarts
        }

        val starts = if (effectiveStarts.size <= 1) {
            effectiveStarts
        } else {
            val filtered = mutableListOf<Triple<Int, Int, String>>()

            for (i in effectiveStarts.indices) {
                val candidate = effectiveStarts[i]
                if (filtered.isEmpty()) {
                    filtered.add(candidate)
                    continue
                }

                val previous = filtered.last()
                val previousStart = previous.first
                val candidateStart = candidate.first
                val candidateLine = lines[candidateStart].trim()

                // Check 1: Explicit question marker (e.g. Q1, **Q1**, Question 1, MCQ 1, प्रश्न 1, or header #)
                val isExplicitHeader = isExplicitQuestionMarker(candidateLine)

                // Outlier lookahead check: if candidate number jumps ahead (e.g. 2 to 4), check if a closer sequential candidate exists immediately ahead (e.g. 3)
                if (candidate.second > previous.second + 1 && !isExplicitHeader) {
                    val hasCloserAhead = (i + 1 until minOf(effectiveStarts.size, i + 5)).any { aheadIdx ->
                        val aheadNum = effectiveStarts[aheadIdx].second
                        aheadNum > previous.second && aheadNum < candidate.second
                    }
                    if (hasCloserAhead) {
                        continue
                    }
                }

                // Verify candidate has non-empty stem text before its options
                val nextCandidateStart = if (i + 1 < effectiveStarts.size) effectiveStarts[i + 1].first else lines.size
                val candidateBlock = lines.subList(candidateStart, nextCandidateStart).joinToString("\n")
                val candidateOptPair = extractOptionsFromBlock(candidateBlock) ?: recoverEmbeddedOptions(candidateBlock)
                val stemBeforeOpt = if (candidateOptPair != null) candidateBlock.substring(0, candidateOptPair.second) else candidateBlock
                if (cleanQuestionNumbered(stemBeforeOpt).trim().isEmpty()) {
                    // Entire block before options is empty or just a number with no stem words
                    continue
                }

                // Check 2: Examine previous question block text for options
                val previousBlock = lines.subList(previousStart, candidateStart).joinToString("\n")
                val prevHasOptions = extractOptionsFromBlock(previousBlock) != null

                // If both previous and candidate are explicit headers for the same question number (e.g. "MCQ 1" followed by "Question 1"), collapse them
                if (isExplicitHeader && candidate.second == previous.second && !prevHasOptions) {
                    filtered[filtered.size - 1] = candidate
                    continue
                }

                // If candidate is a continuation item in a numbered statement list (1., 2., 3.) before options are provided
                val isItemInList = (candidate.second in 2..9 && Regex("""(?m)^\s*[1-3१-३]\s*[\.\:\)]""").containsMatchIn(previousBlock) && !prevHasOptions)
                if (isItemInList && !isExplicitHeader) {
                    continue
                }

                // If not an explicit header, a new question number must be strictly greater than the previous question number
                if (!isExplicitHeader && candidate.second <= previous.second) {
                    continue
                }

                if (isExplicitHeader || prevHasOptions) {
                    filtered.add(candidate)
                    continue
                }

                // Check 3: Check candidate's own block text for options
                val candidateHasOptions = candidateOptPair != null

                val isInsideStatementQuestion = (Regex("""(?i)\b(?:combination|statements?|कथन|following|codes?|कूट|pairs?|items?|above|given)\b""").containsMatchIn(previousBlock) ||
                        Regex("""(?m)^\s*[1-3१-३]\s*[\.\:\)]""").containsMatchIn(previousBlock)) &&
                        !Regex("""(?i)\b(?:which|correct|true|false|सही|गलत)\b""").containsMatchIn(candidateLine)

                if (candidateHasOptions && !isInsideStatementQuestion) {
                    filtered.add(candidate)
                }
            }

            if (filtered.isNotEmpty()) filtered else effectiveStarts
        }

        val questions = mutableListOf<McqQuestion>()

        for (idx in starts.indices) {
            val (lineNo, number, firstText) = starts[idx]
            val endLine = if (idx + 1 < starts.size) starts[idx + 1].first else lines.size
            val blockLines = lines.subList(lineNo, endLine)

            var detectedType: String? = null
            val remainingLines = blockLines.drop(1)
            val cleanFirst = firstText.replace(Regex("""^\s*[-–—:\.]\s*"""), "").trim()
            val isHeaderType = isQuestionTypeHeader(firstText) || isQuestionTypeHeader(cleanFirst)
            val hasTableOrTab = cleanFirst.contains("\t") || cleanFirst.contains("|") || cleanFirst.contains(Regex("""\s{3,}""")) ||
                    Regex("""(?i)\b(?:column|list|सूची|स्तम्भ|association|hormone)\b""").containsMatchIn(cleanFirst)
            val isTopicHeader = !isHeaderType && !hasTableOrTab && cleanFirst.isNotBlank() && cleanFirst.length in 3..60 && !cleanFirst.endsWith("?") && !cleanFirst.contains("?") && remainingLines.any { it.isNotBlank() }

            if (isHeaderType) {
                detectedType = normalizeQuestionType(if (isQuestionTypeHeader(cleanFirst)) cleanFirst else firstText)
            } else if (isTopicHeader) {
                detectedType = cleanFirst
            }

            val block = if (isHeaderType || isTopicHeader) {
                remainingLines.joinToString("\n")
            } else {
                listOf(firstText).plus(remainingLines).joinToString("\n")
            }

            val optionsPair = extractOptionsFromBlock(block) ?: recoverEmbeddedOptions(block)
            val options = optionsPair?.first ?: QuestionOptions()
            val optAStart = optionsPair?.second ?: block.length

            var rawQText = block.substring(0, optAStart)
            rawQText = rawQText.replace(Regex("""(?:\n|\s+)\d+[\.\:\)\-]?\s*$"""), "\n").trim()
            val qText = cleanQuestionNumbered(rawQText)

            // Also check for inline answer/explanation in block
            var inlineAns: String? = null
            var inlineExp: String? = null
            val inlineAnsMatch = ANSWER_LABEL_REGEX.matcher(block)
            if (inlineAnsMatch.find()) {
                val rawGroup = inlineAnsMatch.group(1)
                inlineAns = canonicalAnswer(rawGroup) ?: rawGroup?.uppercase()
                val expIdx = inlineAnsMatch.end()
                val expSub = block.substring(expIdx)
                val expLabel = EXPLANATION_LABEL_REGEX.matcher(expSub)
                if (expLabel.find()) {
                    inlineExp = normalizeSpace(expSub.substring(expLabel.end()))
                }
            }

            // Extract Diagram / Chart and Formula attachments from question block
            var parsedDiagramUrl: String? = null
            var parsedDiagramLabel: String? = null
            var parsedFormula: String? = null

            val diagramMatch = Regex("""(?i)(?:^\s*(?:diagram|image|figure|chart)\s*:\s*(.+)$|!\[(.*?)\]\((.*?)\)|\[(?:diagram|image|figure|chart)\s*:\s*(.+?)\])""", RegexOption.MULTILINE).find(rawQText)
            if (diagramMatch != null) {
                if (diagramMatch.groupValues[1].isNotBlank()) {
                    val rawVal = diagramMatch.groupValues[1].trim()
                    if (rawVal.contains("|")) {
                        val parts = rawVal.split("|", limit = 2)
                        parsedDiagramLabel = parts[0].trim()
                        parsedDiagramUrl = parts[1].trim()
                    } else {
                        parsedDiagramUrl = rawVal
                    }
                } else if (diagramMatch.groupValues[3].isNotBlank()) {
                    parsedDiagramLabel = diagramMatch.groupValues[2].trim().ifBlank { null }
                    parsedDiagramUrl = diagramMatch.groupValues[3].trim()
                } else if (diagramMatch.groupValues[4].isNotBlank()) {
                    val rawVal = diagramMatch.groupValues[4].trim()
                    if (rawVal.contains("|")) {
                        val parts = rawVal.split("|", limit = 2)
                        parsedDiagramLabel = parts[0].trim()
                        parsedDiagramUrl = parts[1].trim()
                    } else {
                        parsedDiagramUrl = rawVal
                    }
                }
            }

            val formulaMatch = Regex("""(?i)(?:^\s*(?:formula|equation|reaction)\s*:\s*(.+)$|\[(?:formula|equation)\s*:\s*(.+?)\]|\$\$(.+?)\$\$)""", RegexOption.MULTILINE).find(rawQText)
            if (formulaMatch != null) {
                val candidate = formulaMatch.groupValues[1].ifBlank {
                    formulaMatch.groupValues[2].ifBlank { formulaMatch.groupValues[3] }
                }
                parsedFormula = candidate.trim().ifBlank { null }
            }

            var finalQText = if (qText.isNotBlank()) qText else cleanQuestionNumbered(firstText)
            if (diagramMatch != null) {
                finalQText = finalQText.replace(diagramMatch.value, "").trim()
            }
            if (formulaMatch != null) {
                finalQText = finalQText.replace(formulaMatch.value, "").trim()
            }

            questions.add(
                McqQuestion(
                    num = number,
                    question = finalQText,
                    options = options,
                    correctAnswer = inlineAns,
                    explanation = inlineExp,
                    questionType = detectedType,
                    diagramUrl = parsedDiagramUrl,
                    diagramLabel = parsedDiagramLabel,
                    formula = parsedFormula
                )
            )
        }

        return questions.distinctBy { it.num }.sortedBy { it.num }
    }

    private fun extractOptionsFromBlock(block: String): Pair<QuestionOptions, Int>? {
        val cleanBlock = block.replace("\r", "\n")
        val matcher = OPTION_REGEX.matcher(cleanBlock)
        val rawMatches = mutableListOf<Triple<String, Int, Int>>()

        while (matcher.find()) {
            val rawLetter = (matcher.group(1) ?: matcher.group(2) ?: matcher.group(3) ?: matcher.group(4) ?: matcher.group(5) ?: matcher.group(6)) ?: continue
            val letter = canonicalOption(rawLetter) ?: continue
            val start = matcher.start()
            val end = matcher.end()

            // Discard if inside a markdown table row (line contains | as column separator)
            val lineStart = if (start <= 0) 0 else {
                val prevNl = cleanBlock.lastIndexOf('\n', start - 1)
                if (prevNl == -1) 0 else prevNl + 1
            }
            val lineEnd = {
                val nextNl = cleanBlock.indexOf('\n', start)
                if (nextNl == -1) cleanBlock.length else nextNl
            }()
            val currentLine = if (lineStart in 0..lineEnd && lineEnd <= cleanBlock.length) {
                cleanBlock.substring(lineStart, lineEnd)
            } else {
                ""
            }
            val isOptionLine = Regex("""^\s*(?:[-*•]\s*)?(?:(?:\*\*)?(?:Option\s+|Opt\s+)?[a-eA-E1-5कखगघङअबसदई१-५i-vI-Vⅰ-ⅴⅠ-Ⅴ](?:\*\*)?[\.\:\)\-–—]|\([a-eA-E1-5कखगघङअबसदई१-५i-vI-Vⅰ-ⅴⅠ-Ⅴ]\)|\[[a-eA-E1-5कखगघङअबसदई१-५i-vI-Vⅰ-ⅴⅠ-Ⅴ]\]|[①-⑤❶-❺⓵-⓹⒈-⒌Ⓐ-Ⓔⓐ-ⓔ])""").containsMatchIn(currentLine)
            val isMarkdownTableRow = !isOptionLine && (currentLine.trim().startsWith("|") || Regex("""\s\|\s""").containsMatchIn(currentLine))
            if (isMarkdownTableRow) {
                continue
            }

            // Discard if inside a markdown heading (e.g. #### A — Source inference or # Part A)
            val trimmedLine = currentLine.trim()
            if (trimmedLine.startsWith("#") || Regex("""^\s*#+""").containsMatchIn(currentLine)) {
                continue
            }
            if (Regex("""(?i)^\s*(?:Part|Section|Claim|Task|Step|Phase|Stage|Level)\b""").containsMatchIn(trimmedLine)) {
                continue
            }

            // Discard if preceded by Assertion, Reason, Statement, Column, List, Group, Part, etc.
            val prefixStart = maxOf(0, start - 25)
            val prefix = if (prefixStart in 0..start && start <= cleanBlock.length) {
                cleanBlock.substring(prefixStart, start)
            } else {
                ""
            }
            if (Regex("""(?i)\b(?:Assertion|Reason|Statement|Column|List|Group|Set|Table|Figure|Fig|Eq|Equation|Part|Section|Claim|Task|Step)\s*$""").containsMatchIn(prefix)) {
                continue
            }

            // Discard unparenthesized A. or 1. if preceded by common prepositions (strictly for letter A or 1, NEVER for B, C, D, E)
            if ((letter == "A" || letter == "1") && (matcher.group(3) != null || matcher.group(4) != null || matcher.group(5) != null)) {
                val endsWithQuoteOrPunct = Regex("""[“\"”’'!?.:;]\s*$""").containsMatchIn(prefix)
                if (!endsWithQuoteOrPunct && Regex("""(?i)\b(?:of|in|to|for|with|by|at|from|is|are|was|were|and|or|not|the|an|a|than)\s*$""").containsMatchIn(prefix)) {
                    continue
                }
            }

            // Discard unparenthesized letter if part of a code/pair like 1-B or A-1
            if (matcher.group(3) != null || matcher.group(4) != null || matcher.group(5) != null) {
                val immediatePre = cleanBlock.substring(maxOf(0, start - 3), start)
                val immediatePost = cleanBlock.substring(end, minOf(cleanBlock.length, end + 3))
                if (Regex("""\d\s*[-–—]\s*$""").containsMatchIn(immediatePre) ||
                    Regex("""^\s*[-–—]\s*\d""").containsMatchIn(immediatePost)) {
                    continue
                }
            }

            rawMatches.add(Triple(letter, start, end))
        }

        if (rawMatches.size < 3) return null

        val validSequences = mutableListOf<List<Triple<String, Int, Int>>>()
        val aMatches = rawMatches.filter { it.first == "A" }
        val bMatches = rawMatches.filter { it.first == "B" }
        val cMatches = rawMatches.filter { it.first == "C" }
        val dMatches = rawMatches.filter { it.first == "D" }
        val eMatches = rawMatches.filter { it.first == "E" }

        for (a in aMatches) {
            val b = bMatches.firstOrNull { it.second > a.third } ?: continue
            val c = cMatches.firstOrNull { it.second > b.third } ?: continue
            val d = dMatches.firstOrNull { it.second > c.third }
            val e = if (d != null) eMatches.firstOrNull { it.second > d.third } else null
            if (d != null && e != null) {
                validSequences.add(listOf(a, b, c, d, e))
            } else if (d != null) {
                validSequences.add(listOf(a, b, c, d))
            } else {
                validSequences.add(listOf(a, b, c))
            }
        }

        if (validSequences.isEmpty()) return null

        // If there are sequences that are NOT bare digits (e.g. (a), (b), (c), (d) or (A), (B), (C), (D)),
        // prioritize them completely over bare digits (e.g. 1., 2., 3., 4.) which are usually numbered statements in the stem.
        val nonBareDigitSequences = validSequences.filter { seq ->
            seq.any { match ->
                val matchedText = cleanBlock.substring(match.second, match.third)
                !Regex("""^\s*(?:[-*•]\s*)?[1-5१-५][\.\)]""").containsMatchIn(matchedText)
            }
        }
        val poolSequences = if (nonBareDigitSequences.isNotEmpty()) nonBareDigitSequences else validSequences

        // Filter out sequences of bare digits where the context before or after indicates
        // they are numbered statements or items inside the question stem.
        val filteredSequences = poolSequences.filter { seq ->
            val isDigitSeq = seq.all { match ->
                val matchedText = cleanBlock.substring(match.second, match.third)
                Regex("""^\s*(?:[-*•]\s*)?[1-5१-५][\.\)]""").containsMatchIn(matchedText)
            }
            if (!isDigitSeq) return@filter true

            val firstMatch = seq.first()
            val textBeforeSeq = cleanBlock.substring(0, firstMatch.second).trim()
            val lastMatch = seq.last()
            val remainingAfterSeq = cleanBlock.substring(lastMatch.third).trim()

            // 1. If text after the sequence contains actual letter options, this digit sequence is statements, not options
            val hasOptionsAfter = Regex("""(?m)^\s*(?:\([a-eA-E]\)|\[[a-eA-E]\]|[a-eA-E][\.\)])""").containsMatchIn(remainingAfterSeq)
            if (hasOptionsAfter) return@filter false

            // 2. If text before asks for a combination/statement assessment, digits are statements
            val isStatementQuestionPrompt = Regex("""(?i)\b(?:combination|statements?|कथन|following\s+(?:statements?|items?|codes?)|codes?|कूट|pairs?|items?|विचार|सुमेलित|निम्न)\b""").containsMatchIn(textBeforeSeq)
            if (isStatementQuestionPrompt) return@filter false

            // 3. If text after asks which statement/option/combination is correct
            val isPromptAfter = Regex("""(?i)\b(?:which\s+(?:of\s+the|statement|statements|option|combination)|select\s+the\s+correct|choose\s+the\s+correct|उपरोक्त\s+में\s+से\s+कौन)\b""").containsMatchIn(remainingAfterSeq)
            if (isPromptAfter) return@filter false

            true
        }
        val candidateSequences = filteredSequences
        if (candidateSequences.isEmpty()) return null

        val prioritized = candidateSequences.sortedWith(
            compareBy<List<Triple<String, Int, Int>>> { seq ->
                seq.size == 4
            }.thenBy { seq ->
                val isBareDigit = seq.all { match ->
                    val t = cleanBlock.substring(match.second, match.third)
                    Regex("""^\s*(?:[-*•]\s*)?[1-5१-५][\.\)]""").containsMatchIn(t)
                }
                !isBareDigit
            }.thenBy { seq ->
                seq.first().second
            }
        )
        val selected = prioritized.last()
        val found = mutableMapOf<String, String>()

        for (i in selected.indices) {
            val (letter, _, matchEnd) = selected[i]
            val start = matchEnd
            var end = if (i + 1 < selected.size) selected[i + 1].second else cleanBlock.length

            // For last option, truncate before any inline Answer / Explanation section
            if (i == selected.size - 1 && start in 0..end && end <= cleanBlock.length) {
                val sub = cleanBlock.substring(start, end)
                val inlineDivider = Regex(
                    """(?:\n|\s{2,})(?:correct\s+(?:answer|option)|answer\s*key|answer|ans|उत्तर|explanation|detailed\s+explanation|व्याख्या)\s*[:\-]""",
                    RegexOption.IGNORE_CASE
                ).find(sub)
                if (inlineDivider != null) {
                    end = start + inlineDivider.range.first
                }
            }

            if (start <= end && start <= cleanBlock.length) {
                var value = cleanBlock.substring(start, end)
                // Clean any inline answer or explanation tags inside option text
                val optInlineDiv = Regex(
                    """(?:\n|\s{2,})(?:\*\*|__)?(?:correct\s+(?:answer|option)|answer|ans|उत्तर|explanation|detailed\s+explanation|व्याख्या)\s*[:\-].*""",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
                ).find(value)
                if (optInlineDiv != null) {
                    value = value.substring(0, optInlineDiv.range.first)
                }
                // Strip trailing correctness badges like [Correct], (Correct), ✓, [Right Answer]
                value = value.replace(
                    Regex("""(?i)\s*(?:\[\s*(?:correct|right(?:\s*answer)?)\s*\]|\(\s*(?:correct|right(?:\s*answer)?)\s*\)|\*\s*correct\s*\*|✓|✔)\s*$"""),
                    ""
                )
                // Strip decorative line separators (e.g. ---, ===, ___)
                value = value.replace(Regex("""^\s*[-=_]{3,}\s*$""", RegexOption.MULTILINE), "")
                value = stripMarkdown(value).trim()
                if (value.isNotEmpty()) {
                    found[letter] = value
                }
            }
        }

        val optA = found["A"].orEmpty()
        val optB = found["B"].orEmpty()
        val optC = found["C"].orEmpty()
        val optD = found["D"].orEmpty()
        val optE = found["E"].orEmpty()

        return if (optA.isNotBlank() && optB.isNotBlank()) {
            Pair(
                QuestionOptions(
                    a = optA,
                    b = optB,
                    c = optC,
                    d = optD,
                    e = optE
                ),
                selected[0].second
            )
        } else {
            null
        }
    }

    /**
     * Fallback recovery parser that scans the question block line-by-line
     * to detect options that were missed by primary block regex.
     */
    fun recoverEmbeddedOptions(block: String): Pair<QuestionOptions, Int>? {
        val lines = block.lines()
        val optIndices = mutableMapOf<String, Int>()
        val optRegex = Regex("""^\s*(?:[-*•]\s*)?(?:\*\*)?(?:Option\s+|Opt\s+)?[\(\[]?([A-Da-d1-4])[\)\]\.\:\-–—](?:\*\*)?\s*(.*)$""")

        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.startsWith("#") || Regex("""(?i)^\s*(?:Part|Section|Claim|Task|Step|Phase)\b""").containsMatchIn(line)) {
                continue
            }
            val m = optRegex.find(line)
            if (m != null) {
                val letter = canonicalOption(m.groupValues[1])
                if (letter != null && !optIndices.containsKey(letter)) {
                    optIndices[letter] = i
                }
            }
        }

        if (listOf("A", "B", "C").all { optIndices.containsKey(it) }) {
            val aLine = optIndices["A"] ?: return null
            val bLine = optIndices["B"] ?: return null
            val cLine = optIndices["C"] ?: return null
            val dLine = optIndices["D"]

            if (aLine < bLine && bLine < cLine && (dLine == null || cLine < dLine)) {
                var charOffset = 0
                for (i in 0 until aLine) {
                    charOffset += lines[i].length + 1
                }

                fun extractText(fromLine: Int, toLine: Int): String {
                    val first = lines[fromLine].trim().replaceFirst(optRegex, "$2").trim()
                    val rest = lines.subList(fromLine + 1, toLine).map { it.trim() }.filter { it.isNotEmpty() }
                    return (listOf(first) + rest).joinToString(" ").trim()
                }

                val optA = stripMarkdown(extractText(aLine, bLine))
                val optB = stripMarkdown(extractText(bLine, cLine))
                val optC = stripMarkdown(extractText(cLine, dLine ?: lines.size))
                val optD = if (dLine != null) stripMarkdown(extractText(dLine, lines.size)) else ""

                if (optA.isNotBlank() && optB.isNotBlank()) {
                    return Pair(
                        QuestionOptions(a = optA, b = optB, c = optC, d = optD),
                        charOffset.coerceAtMost(block.length)
                    )
                }
            }
        }
        return null
    }

    /**
     * Unified parser extracting both answers and explanations from any text section.
     * Supports:
     * - "1 (B) detail_1"
     * - "2(C) detail_2"
     * - "1. (B) detail"
     * - "1. Ans: B \n व्याख्या: ..."
     * - "1. (A) Detailed Explanation: ..."
     * - Markdown tables: | 1 | B | detail | or | Q | Ans | Q | Ans |
     * - Quick pairs: 1: A, 2: B
     */
    fun parseAnswersAndExplanations(text: String): Triple<Map<Int, String>, Map<Int, String>, Map<Int, String>> {
        val answers = mutableMapOf<Int, String>()
        val explanations = mutableMapOf<Int, String>()
        val questionTypes = mutableMapOf<Int, String>()
        if (text.isBlank()) return Triple(answers, explanations, questionTypes)

        // 1. Markdown table and tabular lines parsing
        val cleanText = Regex("""(?im)^\s*(?:Quality[- ]control\s+notes?|Review\s+notes?|QC\s+notes?|Internal\s+check\b|Remarks?\b|Important\s+Notes?\b(?:\s*[:\-–—]))""").find(text)?.let {
            text.substring(0, it.range.first)
        } ?: text

        for (line in cleanText.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            if (trimmed.contains("|")) {
                val cells = trimmed.split("|")
                    .map { stripMarkdown(normalizeSpace(it)) }
                    .filter { it.isNotEmpty() }

                // Check if this row is a multi-column answer grid (e.g. 1 | (c) | 11 | (c) or 1 | A | 6 | B | 11 | C)
                val isMultiColAnswerGrid = cells.size >= 4 && cells.size % 2 == 0 && (0 until cells.size step 2).all { idx ->
                    val qNum = parseLocalizedInt(cells[idx])
                    val ans = canonicalAnswer(cells[idx + 1])
                    qNum != null && ans != null
                }

                if (isMultiColAnswerGrid) {
                    var j = 0
                    while (j + 1 < cells.size) {
                        val qNum = parseLocalizedInt(cells[j])
                        val ans = canonicalAnswer(cells[j + 1]) ?: cells[j + 1].trim().takeIf { it.isNotBlank() }
                        if (qNum != null && ans != null) {
                            answers[qNum] = ans
                        }
                        j += 2
                    }
                } else if (cells.size >= 3) {
                    val qNum = parseLocalizedInt(cells[0])
                    val rawAns = canonicalAnswer(cells[1]) ?: cells[1].trim().takeIf { it.isNotBlank() }
                    if (qNum != null && rawAns != null) {
                        val subKey = stripMarkdown(cells[0]).trim()
                        val isSubPart = Regex("""(?i)^\d+\s*[-–—]\s*[A-Za-z0-9ivIV]+$""").matches(subKey)
                        if (isSubPart) {
                            val existing = answers[qNum]
                            answers[qNum] = if (existing != null) "$existing\n$subKey: $rawAns" else "$subKey: $rawAns"
                        } else {
                            answers[qNum] = rawAns
                        }
                        val thirdCol = cells.drop(2).joinToString(" ").trim()
                        if (isQuestionTypeHeader(thirdCol)) {
                            questionTypes[qNum] = normalizeQuestionType(thirdCol)
                        } else if (thirdCol.isNotEmpty()) {
                            explanations[qNum] = thirdCol
                        }
                    }
                } else if (cells.size >= 2) {
                    var j = 0
                    while (j + 1 < cells.size) {
                        val qNum = parseLocalizedInt(cells[j])
                        val rawAns = canonicalAnswer(cells[j + 1]) ?: cells[j + 1].trim().takeIf { it.isNotBlank() }
                        if (qNum != null && rawAns != null) {
                            val subKey = stripMarkdown(cells[j]).trim()
                            val isSubPart = Regex("""(?i)^\d+\s*[-–—]\s*[A-Za-z0-9ivIV]+$""").matches(subKey)
                            if (isSubPart) {
                                val existing = answers[qNum]
                                answers[qNum] = if (existing != null) "$existing\n$subKey: $rawAns" else "$subKey: $rawAns"
                            } else {
                                answers[qNum] = rawAns
                            }
                        }
                        j += 2
                    }
                }
            } else if (!trimmed.startsWith("#") && !Regex("""(?i)^\s*Q(?:uestion)?\s+(?:Ans|Answer)""").containsMatchIn(trimmed)) {
                // Tab or multi-space separated line with possible Type column
                val match = Regex("""(?i)^\s*(?:(?:Q(?:uestion)?|MCQ[s]?|प्रश्न|प्र|Problem|Item)\.?\s*(?:No\.?|#)?\s*)?(\d{1,4}|[\p{Nd}]{1,4})\s*[\t ]+[\(\[]?([ABCD1-4कखगघअबसद१-४①-④❶-❹⓵-⓸⒈-⒋Ⓐ-Ⓓⓐ-ⓓকখগঘઅબકડஅஆஇஈఅఆఇఈഎബിസിഡിأابجد甲乙丙丁])[\)\]]?\s*(?:[\t ]+(.*))?$""").find(trimmed)
                if (match != null) {
                    val qNum = parseLocalizedInt(match.groupValues[1])
                    val ans = canonicalAnswer(match.groupValues[2])
                    val extra = match.groupValues[3].trim()
                    if (qNum != null && ans != null) {
                        answers[qNum] = ans
                        if (isQuestionTypeHeader(extra)) {
                            questionTypes[qNum] = normalizeQuestionType(extra)
                        } else if (extra.isNotEmpty()) {
                            explanations[qNum] = extra
                        }
                    }
                }
            }
        }

        // 2. Line/Block parsing for formats like:
        // "Explanation 1", "MCQ 1", "1 (B) detail_1", "2(C) detail_2", "1. (B) detail", "1. व्याख्या: ...", "### 1. ..."
        val explicitItemKeyword = """(?:(?:Explanation|Exp|Solution|Sol|व्याख्या|हल|Question|Q|MCQ[s]?|Problem|Prob|Item|सवाल|उत्तर|വിശദീകരണം|விளக்கம்|వివరణ)\s*(?:for\s+)?(?:Q(?:uestion)?|MCQ[s]?|#|No\.?)?\s*)?"""
        val itemStartRegex = Pattern.compile(
            """(?m)^\s*(?:#+\s*)?(?:\*\*|__)?\s*$explicitItemKeyword(\d{1,4}|[\p{Nd}]{1,4})\s*(?:[\.\:\)\-]\s*|(?=[\(\[]?[ABCD][\)\]]?\b)|\s+(?=[\(\[]?[ABCD][\)\]]?\b)|(?=\s*(?:Ans|उत्तर|सटीक|Correct))|(?=[\r\n]|$))""",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = itemStartRegex.matcher(cleanText)
        val itemStarts = mutableListOf<Triple<Int, Int, Int>>() // qNum, matchStart, matchEnd

        while (matcher.find()) {
            val qNum = parseLocalizedInt(matcher.group(1)) ?: continue
            if (qNum !in 1..10000) continue

            // Guard A: Ignore numbers inside display LaTeX block \[ ... \]
            val textBefore = cleanText.substring(0, matcher.start())
            val lastOpenBracket = textBefore.lastIndexOf("\\[")
            val lastCloseBracket = textBefore.lastIndexOf("\\]")
            if (lastOpenBracket != -1 && (lastCloseBracket == -1 || lastOpenBracket > lastCloseBracket)) {
                continue
            }

            // Guard B: Ignore math subtraction expressions like 1-(0.70) or 1-x or 1 - \frac
            val matchedFull = matcher.group(0) ?: ""
            val textAfterMatch = cleanText.substring(matcher.end()).trimStart()
            if (matchedFull.contains("-") && (textAfterMatch.startsWith("(") || textAfterMatch.startsWith("\\") || textAfterMatch.matches(Regex("""^[\d\.\\\(\)\+\*\/].*""")))) {
                continue
            }

            // Guard C: Disallow out-of-order numbers unless explicitly labeled (e.g. "Question 1" or "Explanation 1" or "Q1")
            val hasExplicitKeyword = Regex("""(?i)(?:Explanation|Exp|Solution|Sol|व्याख्या|हल|Question|Q|MCQ[s]?|Problem|Prob|Item|सवाल|उत्तर)""").containsMatchIn(matchedFull)
            if (!hasExplicitKeyword && itemStarts.isNotEmpty() && qNum <= itemStarts.last().first) {
                continue
            }

            // Guard D: Ignore statement evaluations inside explanations like "**1. Correct.**" or "1. Incorrect." or "1. True"
            // But NEVER discard answer headings like "1. Correct Answer: (A)", "1. Correct Option: B", "1. Answer: (A)", "1. उत्तर: A"
            val lineAfter = cleanText.substring(matcher.end()).trimStart()
            val isAnswerHeader = Regex("""^(?:\*+|_+)?\s*(?:Correct\s+(?:Answer|Option)|Answer|Ans|उत्तर|Option|Opt)\b""", RegexOption.IGNORE_CASE).containsMatchIn(lineAfter)
            if (!hasExplicitKeyword && !isAnswerHeader && Regex("""^(?:\*+|_+)?\s*(?:Correct|Incorrect|True|False|Right|Wrong|सही|गलत)\s*[\.\*_\-–—:]""", RegexOption.IGNORE_CASE).containsMatchIn(lineAfter)) {
                continue
            }

            itemStarts.add(Triple(qNum, matcher.start(), matcher.end()))
        }

        for (i in itemStarts.indices) {
            val (qNum, _, startIdx) = itemStarts[i]
            val endIdx = if (i + 1 < itemStarts.size) itemStarts[i + 1].second else cleanText.length

            val entry = cleanText.substring(startIdx, endIdx).trim()
            if (entry.isEmpty()) continue

            // 1. Labeled answer first: "Answer: B", "Correct Answer: (C)", "उत्तर: B"
            val labeledAnsMatch = ANSWER_LABEL_REGEX.matcher(entry)
            val hasLabeledAns = labeledAnsMatch.find()

            // 2. Explicit statement: "Correct: (C)", "Option (C) is correct", "(C) - Correct", "- A सही:", "- А правильно:", "\boxed{B}"
            val explicitCorrectMatch = Regex(
                """(?i)(?:^|\s|\n)(?:[-*•]\s*)?(?:Correct[\.:\-]?\s*[\(\[]?([ABCD1-4])\b[\)\]]?|Option\s+([ABCD1-4])\b\s+(?:is\s+)?correct\b|Option\s+([ABCD1-4])\s*[-—:]?\s*(?:is\s+)?correct\b|[\(\[]([ABCD1-4])[\)]\s*[-—:]?\s*(?:is\s+)?correct\b|\\boxed\{\s*(?:\\mathrm\{)?([ABCD1-4])\s*\}?|(?:[-*•]\s*)?[\(\[]?([A-Da-d1-4])[\)\]]?\s*(?:[-—:]\s*)?(?:सही|सत्य|सटीक|옳음|정답|Правильно|Верно)\b)"""
            ).find(entry)

            // Check if entry has option breakdown (e.g. contains (b) or (c) or B. or C.)
            val hasOptionBreakdown = Regex("""(?m)(?:^\s*|\s{2,}|\n)(?:Correct[\.:\-]?\s*)?(?:[\(\[][bcdBCD1-4][\)\]]|[bcdBCD1-4][\.\:\)\-–—]\s+)""").containsMatchIn(entry)

            var ansLetter: String? = null
            var expText = ""

            if (hasLabeledAns) {
                ansLetter = canonicalAnswer(labeledAnsMatch.group(1))
                val expLabelMatch = EXPLANATION_LABEL_REGEX.matcher(entry)
                expText = if (expLabelMatch.find()) {
                    entry.substring(expLabelMatch.end()).trim()
                } else {
                    // Check if entry has a standalone Answer: X line at the beginning
                    val stripped = entry.replaceFirst(
                        Regex("""^(?:\*+\s*)?(?:correct\s+(?:answer|option)|answer|ans|उत्तर)\s*[:\-]?\s*(?:option\s*)?[\(\[]?[ABCD1-4][\)\]]?\s*(?:\r?\n|$)""", RegexOption.IGNORE_CASE),
                        ""
                    ).trim()
                    if (stripped.isNotBlank()) stripped else entry.substring(labeledAnsMatch.end()).trimStart(' ', ':', '-', '\n')
                }
            } else {
                val leadingAnsMatch = Regex(
                    """^(?:\*\*|__)?\s*(?:[\(\[]([ABCD1-4])[\)\]]|([ABCD1-4])\s*[\.\:\-–—\)](?!\w)|([ABCD1-4])(?=\s*(?:\r?\n|$)))\s*(?:\*\*|__)?\s*""",
                    RegexOption.IGNORE_CASE
                ).find(entry)

                if (leadingAnsMatch != null) {
                    val rawAns = leadingAnsMatch.groupValues[1].ifEmpty {
                        leadingAnsMatch.groupValues[2].ifEmpty {
                            leadingAnsMatch.groupValues[3]
                        }
                    }
                    ansLetter = canonicalAnswer(rawAns)
                    val afterStart = (leadingAnsMatch.range.last + 1).coerceAtMost(entry.length)
                    val afterAns = entry.substring(afterStart).trim()
                    val expLabelMatch = EXPLANATION_LABEL_REGEX.matcher(afterAns)
                    expText = if (expLabelMatch.find() && expLabelMatch.end() <= afterAns.length) {
                        afterAns.substring(expLabelMatch.end()).trim()
                    } else {
                        afterAns
                    }
                } else if (explicitCorrectMatch != null) {
                    val raw = explicitCorrectMatch.groupValues.drop(1).firstOrNull { it.isNotEmpty() } ?: ""
                    ansLetter = canonicalAnswer(raw)
                    expText = entry
                } else {
                    val bareAnsMatch = Regex("""\b([ABCD])\b""").find(entry)
                    if (bareAnsMatch != null && !hasOptionBreakdown) {
                        ansLetter = bareAnsMatch.groupValues[1].uppercase()
                    }
                    expText = entry
                }
            }

            if (ansLetter != null && !answers.containsKey(qNum)) {
                answers[qNum] = ansLetter
            }

            // Remove duplicated question text if repeated in explanation
            expText = expText.replace(
                Regex("""^\s*(?:question|प्रश्न)\s*[:\-]?\s*(?:\*\*|__)?.{0,250}?(?:\*\*|__)?\s*(?=(?:\b(?:correct|answer|ans)\b|व्याख्या|उत्तर))""",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
                ),
                ""
            ).trim()

            expText = expText.trimStart(':', '-', ' ', '\n', '\t')

            if (expText.isNotBlank()) {
                explanations[qNum] = formatMultiLineExplanation(expText)
            }
        }

        // 3. Fallback for pair answer format (e.g. 1: A, 2: B, 3: C, 4: D)
        val pairMatcher = PAIR_ANSWER_REGEX.matcher(cleanText)
        while (pairMatcher.find()) {
            val num = parseLocalizedInt(pairMatcher.group(1))
            val ans = canonicalAnswer(pairMatcher.group(2))
            if (num != null && ans != null && num in 1..10000 && !answers.containsKey(num)) {
                answers[num] = ans
            }
        }

        // 3b. Fallback for LaTeX boxed answer pairs (e.g. \boxed{ 1.\ \mathrm{B} \qquad 2.\ \mathrm{C} ... })
        val latexAnsRegex = Regex("""(?<!\w)(\d{1,4})\s*[\.\:\-\)]\s*\\?\s*(?:\\mathrm\{|\\mathbf\{)?\s*([A-Ea-e1-5])\s*\}?""")
        for (m in latexAnsRegex.findAll(cleanText)) {
            val num = parseLocalizedInt(m.groupValues[1])
            val ans = canonicalAnswer(m.groupValues[2])
            if (num != null && ans != null && num in 1..10000 && !answers.containsKey(num)) {
                answers[num] = ans
            }
        }

        // 3c. Fallback for unnumbered answer lines (e.g. single "C. was", or unnumbered list of answers)
        if (answers.isEmpty() && cleanText.isNotBlank()) {
            val lines = cleanText.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val singleLineMatch = Regex("""^(?:(?:\*+|__)?\s*(?:Correct\s+(?:Answer|Option)|Answer|Ans|उत्तर)\s*[:\-]?\s*)?[\(\[]?([ABCD1-4])[\)\]]?(?:\s*[\.:\-–—]\s*.*|\s*)$""", RegexOption.IGNORE_CASE)
            if (lines.size == 1) {
                val m = singleLineMatch.find(lines[0])
                if (m != null) {
                    val ans = canonicalAnswer(m.groupValues[1])
                    if (ans != null) {
                        answers[1] = ans
                    }
                }
            } else if (lines.isNotEmpty() && lines.all { singleLineMatch.matches(it) }) {
                lines.forEachIndexed { idx, l ->
                    val m = singleLineMatch.find(l)
                    if (m != null) {
                        val ans = canonicalAnswer(m.groupValues[1])
                        if (ans != null) {
                            answers[idx + 1] = ans
                        }
                    }
                }
            }
        }

        // 4. Fallback for single-question papers or explanation sections without numbered headings
        if (explanations.isEmpty() && text.isNotBlank()) {
            val clean = text.trim()
            if (clean.length > 20 && !clean.startsWith("|") && !Regex("""^(?:\*+\s*)?(?:answer\s*key|answers?|उत्तर)\s*[:\-]""", RegexOption.IGNORE_CASE).containsMatchIn(clean)) {
                explanations[1] = formatMultiLineExplanation(clean)
            }
        }

        return Triple(answers, explanations, questionTypes)
    }

    fun isQuestionTypeHeader(text: String): Boolean {
        val clean = stripMarkdown(text).trim()
        val lower = clean.lowercase()
        return lower in listOf(
            "assertion–reason", "assertion-reason", "assertion reason",
            "match the following", "statement-based question", "statement based question",
            "statement-based", "statement based", "direct conceptual question", "direct conceptual",
            "pair-based question", "pair-based", "pair based question", "pair based",
            "chronological sequence question", "chronological sequence", "sequence question",
            "process/sequence question", "process/sequence", "process sequence question",
            "multiple statements + combination", "multiple statements",
            "numerical/application question", "numerical/application", "numerical question",
            "case-based/analytical question", "case-based/analytical", "case-based question", "analytical question"
        ) || Regex("""(?i)^(?:Direct Conceptual|Statement[- ]Based|Assertion[-– ]Reason|Match the Following|Pair[- ]Based|Chronological Sequence|Process[/ ]Sequence|Multiple Statements|Numerical[/ ]Application|Case[- ]Based[/ ]Analytical)(?:\s+Questions?)?$""").matches(clean)
    }

    fun normalizeQuestionType(raw: String): String {
        val clean = stripMarkdown(raw).trim()
        val lower = clean.lowercase()
        return when {
            lower.contains("assertion") -> "Assertion–Reason"
            lower.contains("match") || lower.contains("matching") -> "Match the Following"
            lower.contains("pair") -> "Pair-Based"
            lower.contains("chronological") -> "Chronological Sequence"
            lower.contains("process") || (lower.contains("sequence") && !lower.contains("chronological")) -> "Process/Sequence"
            lower.contains("statement") -> {
                if (lower.contains("multiple")) "Multiple Statements" else "Statement-Based"
            }
            lower.contains("numerical") || lower.contains("calculation") || lower.contains("application") -> "Numerical/Application"
            lower.contains("case") || lower.contains("analytical") -> "Case-Based/Analytical"
            lower.contains("conceptual") || lower.contains("direct") -> "Direct Conceptual"
            else -> clean.replace(Regex("""(?i)\s*Questions?$"""), "").trim()
        }
    }

    fun inferQuestionType(questionText: String, options: QuestionOptions): String {
        val qLower = questionText.lowercase()
        val optLower = options.asList.joinToString(" ") { it.second }.lowercase()

        return when {
            // 1. Assertion–Reason
            (qLower.contains("assertion") && (qLower.contains("reason") || optLower.contains("both a and r"))) ||
            optLower.contains("both a and r are true") ||
            optLower.contains("correct explanation of a") ||
            optLower.contains("a is true, but r is false") -> "Assertion–Reason"

            // 2. Match the Following
            qLower.contains("match list") || qLower.contains("match column") ||
            qLower.contains("list-i") || qLower.contains("list - i") ||
            qLower.contains("column-i") || qLower.contains("column i") ||
            Regex("""\b1\s*[-–—:]\s*[A-D]\b""").containsMatchIn(optLower) ||
            Regex("""\b[A-D]\s*[-–—:]\s*1\b""").containsMatchIn(optLower) -> "Match the Following"

            // 3. Pair-Based
            (qLower.contains("pairs:") || qLower.contains("following pairs") || qLower.contains("pairs given above")) &&
            (qLower.contains("correctly matched") || qLower.contains("matched")) -> "Pair-Based"

            // 4. Chronological Sequence / Process / Sequence
            (qLower.contains("sequence") || qLower.contains("chronological") || qLower.contains("arrange the following")) &&
            (optLower.contains("→") || optLower.contains("->") || qLower.contains("events") || qLower.contains("order")) -> "Chronological Sequence"

            optLower.contains("→") || optLower.contains("->") ||
            ((qLower.contains("sequence") || qLower.contains("flow") || qLower.contains("process")) &&
            (qLower.contains("information flow") || qLower.contains("steps") || qLower.contains("cycle") || qLower.contains("pathway"))) -> "Process/Sequence"

            // 5. Statement-Based / Multiple Statements
            (qLower.contains("consider the following statements") || qLower.contains("statements regarding") ||
             qLower.contains("which of the statements")) &&
            (optLower.contains("only") || optLower.contains("1 and") || optLower.contains("1, 2") || optLower.contains("all of the above")) -> {
                if (qLower.contains("combination") || qLower.contains("multiple") || optLower.contains("only")) "Statement-Based" else "Multiple Statements"
            }

            // 6. Numerical / Application
            (qLower.contains("frequency") && qLower.contains("hardy")) ||
            qLower.contains("calculate") || qLower.contains("numerical") ||
            Regex("""\b\d+\s*×\s*10\b""").containsMatchIn(optLower) ||
            Regex("""\bq²\b|\bp²\b|\b2pq\b""").containsMatchIn(qLower) ||
            (Regex("""\b\d+\.\d+\b""").findAll(optLower).count() >= 3) -> "Numerical/Application"

            // 7. Case-Based / Analytical
            qLower.contains("a researcher observes") || qLower.contains("a patient presents") ||
            qLower.contains("case study") || qLower.contains("an experiment") ||
            qLower.contains("which combination most appropriately explains") ||
            (qLower.contains("observes that") && qLower.contains("characteristics")) -> "Case-Based/Analytical"

            // Default
            else -> "Direct Conceptual"
        }
    }

    val STANDALONE_SEPARATOR_REGEX = Regex("""^\s*(?:[-*_=\u2014\u2013\u2500\u2501\u2504\u2505]\s*){3,}${'$'}""")

    fun formatMultiLineExplanation(text: String): String {
        if (text.isBlank()) return text
        val normalized = text
            .replace("\r\n", "\n")
            .replace("\r", "\n")
        val lines = normalized.lines()
        val filteredLines = lines.filterNot { line ->
            val trimmed = line.trim()
            // Preserve markdown table rows (e.g. | --- | :---: |)
            if (trimmed.contains("|")) false
            else STANDALONE_SEPARATOR_REGEX.matches(trimmed)
        }
        return filteredLines.joinToString("\n")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
    }

    fun canonicalAnswer(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val stripped = stripMarkdown(value).trim()
        val direct = canonicalOption(stripped)
        if (direct != null) return direct

        // Check for multi-select options like "A, C, D, E" or "A, B"
        val tokens = stripped.split(Regex("[,;\\s]+")).filter { it.isNotBlank() }
        if (tokens.size > 1 && tokens.all { canonicalOption(it) != null }) {
            return tokens.mapNotNull { canonicalOption(it) }.distinct().sorted().joinToString(", ")
        }

        // Check for parenthesized or bracketed options like (A), [b], (3), (क), (ब), (iii), (가), (ア), (А)
        val bracketMatch = Regex("""[\(\[]\s*([a-eA-E1-5कखगघङअबसदई१-५①-⑤❶-❺⓵-⓹⒈-⒌Ⓐ-Ⓔⓐ-ⓔকখগঘঙઅબકડઈஅஆஇஈஉఅఆఇఈఉഎബിസിഡിഇأابجده甲乙丙丁戊가나다라마㈎㈏㈐㈑㈒アイウエオあいうえおА-Да-д\u0410-\u0414\u0430-\u0434]|i{1,4}|v|I{1,4}|V)\s*[\)\]]""").find(stripped)
        if (bracketMatch != null) {
            val cand = canonicalOption(bracketMatch.groupValues[1])
            if (cand != null) return cand
        }

        // Check for circled characters or regional language characters
        val circledMatch = Regex("""([①-⑤❶-❺⓵-⓹⒈-⒌Ⓐ-Ⓔⓐ-ⓔকখগঘঙઅબકડઈஅஆஇஈஉఅఆఇఈఉഎബിസിഡിഇأابجده甲乙丙丁戊가나다라마㈎㈏㈐㈑㈒アイウエオあいうえおА-Да-д\u0410-\u0414\u0430-\u0434कखगघङअबसदई१-५])""").find(stripped)
        if (circledMatch != null) {
            val cand = canonicalOption(circledMatch.groupValues[1])
            if (cand != null) return cand
        }

        val letterMatch = Regex("""\b([A-E])\b""", RegexOption.IGNORE_CASE).find(stripped)
        if (letterMatch != null) {
            return letterMatch.groupValues[1].uppercase()
        }
        val digitMatch = Regex("""\b([1-5])\b""").find(stripped)
        if (digitMatch != null) {
            return canonicalOption(digitMatch.groupValues[1])
        }
        return null
    }

    fun cleanQuestionNumbered(text: String): String {
        var clean = text.replace(Regex("""^\s*#+\s*"""), "")
        // Strip leading question numbering: 1., (1), [1], Q1., **Q1.**, Question 1:, Q. 1 -, प्रश्न 1, etc.
        clean = clean.replace(
            Regex("""^\s*(?:\*\*|__)?\s*(?:(?:Q(?:uestion)?|MCQ[s]?|प्रश्न|प्र|Problem|Prob|Item)\.?\s*(?:No\.?|#)?\s*)?(?:[\(\[]?\d+\s*[\)\]\.\:\-–—]|(?:Q(?:uestion)?|MCQ[s]?|प्रश्न|प्र|Problem|Prob|Item)\.?\s*(?:No\.?|#)?\s*\d+)\s*[\:\-\.–—]?\s*(?:\*\*|__)?\s*""", RegexOption.IGNORE_CASE),
            ""
        )
        clean = clean.replace(Regex("""^\s*[-–—:]\s*"""), "")
        // Strip leading section / part headers embedded with questions (e.g. SECTION - A, PART 1 - PHYSICS)
        clean = clean.replace(
            Regex("""^\s*(?:SECTION|PART|UNIT|CHAPTER)\s*[-:]?\s*[A-Z0-9]+(?:\s*[-:]\s*[^:\n]+)?\s*[:\-\.]?\s*""", RegexOption.IGNORE_CASE),
            ""
        )
        // Strip question category labels (e.g. Multiple Choice Questions, Objective Type Questions)
        clean = clean.replace(
            Regex("""^\s*(?:Multiple Choice Questions?|Objective Type Questions?|MCQs?)\s*[:\-\.]?\s*""", RegexOption.IGNORE_CASE),
            ""
        )
        // Strip prompt echoes (e.g. Here is question 1:, Question 1 is:)
        clean = clean.replace(
            Regex("""^\s*Here is question\s*\d*\s*[:\-]?\s*""", RegexOption.IGNORE_CASE),
            ""
        )
        // Strip inline metadata brackets: [1 Mark], (Marks: 2), [Difficulty: Medium], (Points: 1)
        clean = clean.replace(
            Regex("""\[\s*(?:Marks?|Points?|Difficulty|Taxonomy|Topic)\s*:[^\]]+\]""", RegexOption.IGNORE_CASE),
            ""
        )
        clean = clean.replace(
            Regex("""\(\s*(?:Marks?|Points?|Difficulty)\s*:[^\)]+\)""", RegexOption.IGNORE_CASE),
            ""
        )
        clean = clean.replace(
            Regex("""\[\s*\d+\s*Marks?\s*\]""", RegexOption.IGNORE_CASE),
            ""
        )
        clean = clean.replace(
            Regex("""\(\s*\d+\s*Marks?\s*\)""", RegexOption.IGNORE_CASE),
            ""
        )

        // Ensure Assertion and Reason are cleanly separated by newline
        clean = clean.replace(
            Regex("""(?i)(?<=[^\n])\s*(?:\*\*)?\s*(Reason\s*(?:\([R]\)|:))\s*(?:\*\*)?"""),
            "\n\n$1"
        )

        return clean.trim()
    }

    fun stripMarkdown(text: String): String {
        var res = text.replace(Regex("""^\s*#+\s*"""), "")
        res = res.replace(Regex("""\*\*(.*?)\*\*"""), "$1")
        res = res.replace(Regex("""(?<!_)__([^\s_](?:[\s\S]*?[^\s_])?)__(?!_)"""), "$1")
        res = res.replace(Regex("""`([^`]*)`"""), "$1")
        return normalizeSpace(res)
    }

    fun normalizeSpace(text: String): String {
        var res = text.replace("\u00a0", " ")
            .replace("\u2003", " ")
            .replace("\u2002", " ")
            .replace("\t", " ")
        res = res.replace(Regex("""[ ]{2,}"""), " ")
        return res.trim()
    }

    fun sanitizeSubSuper(text: String): String {
        if (text.isEmpty()) return ""
        val subMap = mapOf(
            '₀' to "0", '₁' to "1", '₂' to "2", '₃' to "3", '₄' to "4",
            '₅' to "5", '₆' to "6", '₇' to "7", '₈' to "8", '₉' to "9"
        )
        val supMap = mapOf(
            '⁰' to "0", '¹' to "1", '²' to "2", '³' to "3", '⁴' to "4",
            '⁵' to "5", '⁶' to "6", '⁷' to "7", '⁸' to "8", '⁹' to "9"
        )
        var result = text
        subMap.forEach { (subChar, num) ->
            result = result.replace(subChar.toString(), num)
        }
        supMap.forEach { (supChar, num) ->
            result = result.replace(supChar.toString(), "^$num")
        }
        return result
    }

    fun parseWithResult(rawText: String): com.example.model.ParseResult {
        val paper = parse(rawText)
        val rawCount = paper.questions.size
        val hasAnswerKeys = paper.questions.isNotEmpty() && paper.questions.all { it.correctAnswer != null }
        val hasExplanations = paper.questions.isNotEmpty() && paper.questions.all { !it.explanation.isNullOrBlank() }
        val warnings = mutableListOf<String>()

        val missingKeys = paper.questions.filter { it.correctAnswer == null }.map { "Q${it.num}" }
        if (missingKeys.isNotEmpty()) {
            warnings.add("Missing answer keys for: ${missingKeys.joinToString(", ")}")
        }

        val missingExps = paper.questions.filter { it.explanation.isNullOrBlank() }.map { "Q${it.num}" }
        if (missingExps.isNotEmpty()) {
            warnings.add("Missing explanations for: ${missingExps.joinToString(", ")}")
        }

        val incompleteOptions = paper.questions.filter { !it.options.isValid }.map { "Q${it.num}" }
        if (incompleteOptions.isNotEmpty()) {
            warnings.add("Questions with fewer than 4 options: ${incompleteOptions.joinToString(", ")}")
        }

        return com.example.model.ParseResult(
            paper = paper,
            rawCount = rawCount,
            hasAnswerKeys = hasAnswerKeys,
            hasExplanations = hasExplanations,
            warnings = warnings
        )
    }

    fun reconstructToRaw(paper: QuestionPaper): String {
        val sb = StringBuilder()
        sb.append("# ").append(paper.metadata.title).append("\n")
        sb.append("Subject: ").append(paper.metadata.subject).append("\n")
        if (paper.metadata.topic.isNotBlank()) {
            sb.append("Topic: ").append(paper.metadata.topic).append("\n")
        }
        sb.append("Series: ").append(paper.metadata.setNumber).append("\n\n")

        paper.questions.forEach { q ->
            sb.append("${q.num}. ").append(q.question).append("\n")
            if (!q.formula.isNullOrBlank()) {
                sb.append("Formula: ").append(q.formula.trim()).append("\n")
            }
            if (!q.diagramUrl.isNullOrBlank()) {
                if (!q.diagramLabel.isNullOrBlank()) {
                    sb.append("Diagram: ").append(q.diagramLabel.trim()).append(" | ").append(q.diagramUrl.trim()).append("\n")
                } else {
                    sb.append("Diagram: ").append(q.diagramUrl.trim()).append("\n")
                }
            }
            sb.append("(A) ").append(q.options.a).append("\n")
            sb.append("(B) ").append(q.options.b).append("\n")
            sb.append("(C) ").append(q.options.c).append("\n")
            sb.append("(D) ").append(q.options.d).append("\n\n")
        }

        val hasKeys = paper.questions.any { it.correctAnswer != null }
        if (hasKeys) {
            sb.append("Answer Key\n")
            paper.questions.forEach { q ->
                if (q.correctAnswer != null) {
                    sb.append("${q.num}: ${q.correctAnswer}\n")
                }
            }
            sb.append("\n")
        }

        val hasExps = paper.questions.any { !it.explanation.isNullOrBlank() }
        if (hasExps) {
            sb.append("Detailed Explanations\n")
            paper.questions.forEach { q ->
                if (!q.explanation.isNullOrBlank()) {
                    sb.append("${q.num}. Correct Answer: (${q.correctAnswer ?: "A"})\n")
                    sb.append("Explanation: ").append(q.explanation).append("\n\n")
                }
            }
        }

        return sb.toString().trim()
    }
}
