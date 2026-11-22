package com.example.util

import android.util.JsonWriter
import com.example.data.PaperEntity
import com.example.model.FolderItem
import com.example.model.McqQuestion
import com.example.model.MsqScoringPolicy
import com.example.model.PaperMetadata
import com.example.model.QuestionOptions
import com.example.model.QuestionPaper
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.io.OutputStreamWriter

object ExamJsonHelper {

    const val CURRENT_SCHEMA_VERSION = 2

    /**
     * Builds the Schema v2 JSONObject tree for a single question paper without stringifying.
     */
    fun toJsonObject(paper: QuestionPaper): JSONObject {
        val root = JSONObject()
        root.put("schemaVersion", CURRENT_SCHEMA_VERSION)
        root.put("exportType", "SINGLE_QUESTION_SET")

        // Top-level identifiers for immediate visibility
        root.put("setNumber", paper.metadata.setNumber)
        root.put("rollNumber", paper.metadata.rollNumber)
        root.put("bookletNumber", paper.metadata.bookletNumber)
        if (!paper.metadata.folderId.isNullOrBlank()) {
            root.put("folderId", paper.metadata.folderId)
        }

        // Full Metadata Block
        val metaObj = JSONObject().apply {
            put("id", if (paper.metadata.id.isNotBlank()) paper.metadata.id else "paper-${paper.id}")
            put("title", paper.metadata.title)
            put("subject", paper.metadata.subject)
            put("topic", paper.metadata.topic)
            put("setNumber", paper.metadata.setNumber)
            put("rollNumber", paper.metadata.rollNumber)
            put("bookletNumber", paper.metadata.bookletNumber)
            put("targetExam", paper.metadata.exam)
            put("msqScoringPolicy", paper.metadata.msqScoringPolicy.code)
            put("createdAt", paper.createdAt)
            if (!paper.metadata.folderId.isNullOrBlank()) {
                put("folderId", paper.metadata.folderId)
            }
        }
        root.put("metadata", metaObj)

        // Questions
        val qArray = JSONArray()
        paper.questions.forEach { q ->
            val qObj = JSONObject().apply {
                put("id", "q-${q.num}")
                put("num", q.num)
                put("question", q.question)

                val optObj = JSONObject().apply {
                    put("a", q.options.a)
                    put("b", q.options.b)
                    put("c", q.options.c)
                    put("d", q.options.d)
                    if (q.options.e.isNotBlank()) {
                        put("e", q.options.e)
                    }
                }
                put("options", optObj)

                put("correctAnswer", q.correctAnswer ?: JSONObject.NULL)
                put("explanation", q.explanation ?: "")
                if (!q.questionType.isNullOrBlank()) {
                    put("questionType", q.questionType)
                }
                if (!q.diagramUrl.isNullOrBlank()) {
                    put("diagramUrl", q.diagramUrl)
                }
                if (!q.diagramLabel.isNullOrBlank()) {
                    put("diagramLabel", q.diagramLabel)
                }
                if (!q.formula.isNullOrBlank()) {
                    put("formula", q.formula)
                }
            }
            qArray.put(qObj)
        }
        root.put("questions", qArray)

        return root
    }

    /**
     * Exports a single question paper as a structured Schema v2 JSON string.
     */
    fun toJson(paper: QuestionPaper): String {
        return toJsonObject(paper).toString(2)
    }

    /**
     * Exports all question sets belonging to an exam or folder into an Enterprise Schema v2 multi-set bundle.
     */
    fun bundleToJson(examTitle: String, folderId: String?, papers: List<QuestionPaper>): String {
        val root = JSONObject()
        root.put("schemaVersion", CURRENT_SCHEMA_VERSION)
        root.put("exportType", "MULTI_SET_EXAM_BUNDLE")
        root.put("examTitle", examTitle)
        if (!folderId.isNullOrBlank()) {
            root.put("folderId", folderId)
        }
        root.put("totalSets", papers.size)
        root.put("exportedAt", System.currentTimeMillis())

        val setsArray = JSONArray()
        papers.forEach { paper ->
            setsArray.put(toJsonObject(paper))
        }
        root.put("questionSets", setsArray)

        return if (papers.size <= 3) root.toString(2) else root.toString()
    }

    /**
     * Memory-safe O(1)-heap streaming exporter for large libraries (20+ sets, 2,000+ long questions).
     * Parses and writes one PaperEntity at a time directly to UTF-8 JSON without ever building
     * a giant in-memory JSONObject tree or monolithic String.
     */
    fun streamBundleToOutputStream(
        examTitle: String,
        folderId: String?,
        papers: List<PaperEntity>,
        outputStream: OutputStream,
        onProgress: (index: Int, total: Int) -> Unit = { _, _ -> },
        paperBuilder: (PaperEntity) -> QuestionPaper
    ) {
        JsonWriter(OutputStreamWriter(BufferedOutputStream(outputStream, 32 * 1024), Charsets.UTF_8)).use { writer ->
            writer.beginObject()
            writer.name("schemaVersion").value(CURRENT_SCHEMA_VERSION)
            writer.name("exportType").value("MULTI_SET_EXAM_BUNDLE")
            writer.name("examTitle").value(examTitle)
            if (!folderId.isNullOrBlank()) {
                writer.name("folderId").value(folderId)
            }
            writer.name("totalSets").value(papers.size)
            writer.name("exportedAt").value(System.currentTimeMillis())

            writer.name("questionSets")
            writer.beginArray()
            papers.forEachIndexed { idx, entity ->
                onProgress(idx + 1, papers.size)
                val paper = paperBuilder(entity)
                writeQuestionPaper(writer, paper)
            }
            writer.endArray()
            writer.endObject()
            writer.flush()
        }
    }

    private fun writeQuestionPaper(writer: JsonWriter, paper: QuestionPaper) {
        writer.beginObject()
        writer.name("schemaVersion").value(CURRENT_SCHEMA_VERSION)
        writer.name("exportType").value("SINGLE_QUESTION_SET")
        writer.name("setNumber").value(paper.metadata.setNumber)
        writer.name("rollNumber").value(paper.metadata.rollNumber)
        writer.name("bookletNumber").value(paper.metadata.bookletNumber)
        if (!paper.metadata.folderId.isNullOrBlank()) {
            writer.name("folderId").value(paper.metadata.folderId)
        }

        writer.name("metadata")
        writer.beginObject()
        writer.name("id").value(if (paper.metadata.id.isNotBlank()) paper.metadata.id else "paper-${paper.id}")
        writer.name("title").value(paper.metadata.title)
        writer.name("subject").value(paper.metadata.subject)
        writer.name("topic").value(paper.metadata.topic)
        writer.name("setNumber").value(paper.metadata.setNumber)
        writer.name("rollNumber").value(paper.metadata.rollNumber)
        writer.name("bookletNumber").value(paper.metadata.bookletNumber)
        writer.name("targetExam").value(paper.metadata.exam)
        writer.name("msqScoringPolicy").value(paper.metadata.msqScoringPolicy.code)
        writer.name("createdAt").value(paper.createdAt)
        if (!paper.metadata.folderId.isNullOrBlank()) {
            writer.name("folderId").value(paper.metadata.folderId)
        }
        writer.endObject()

        writer.name("questions")
        writer.beginArray()
        paper.questions.forEach { q ->
            writer.beginObject()
            writer.name("id").value("q-${q.num}")
            writer.name("num").value(q.num)
            writer.name("question").value(q.question)

            writer.name("options")
            writer.beginObject()
            writer.name("a").value(q.options.a)
            writer.name("b").value(q.options.b)
            writer.name("c").value(q.options.c)
            writer.name("d").value(q.options.d)
            if (q.options.e.isNotBlank()) {
                writer.name("e").value(q.options.e)
            }
            writer.endObject()

            writer.name("correctAnswer")
            if (q.correctAnswer != null) writer.value(q.correctAnswer) else writer.nullValue()
            writer.name("explanation").value(q.explanation ?: "")
            if (!q.questionType.isNullOrBlank()) {
                writer.name("questionType").value(q.questionType)
            }
            if (!q.diagramUrl.isNullOrBlank()) {
                writer.name("diagramUrl").value(q.diagramUrl)
            }
            if (!q.diagramLabel.isNullOrBlank()) {
                writer.name("diagramLabel").value(q.diagramLabel)
            }
            if (!q.formula.isNullOrBlank()) {
                writer.name("formula").value(q.formula)
            }
            writer.endObject()
        }
        writer.endArray()
        writer.endObject()
    }

    /**
     * Exports an entire folder subtree including folder hierarchy metadata and all contained question sets.
     */
    fun subtreeBundleToJson(
        rootFolder: FolderItem,
        folderTree: List<FolderItem>,
        papers: List<QuestionPaper>
    ): String {
        val root = JSONObject()
        root.put("schemaVersion", CURRENT_SCHEMA_VERSION)
        root.put("exportType", "FOLDER_SUBTREE_BUNDLE")
        root.put("rootFolderId", rootFolder.id)
        root.put("rootFolderName", rootFolder.name)
        root.put("totalFolders", folderTree.size)
        root.put("totalSets", papers.size)
        root.put("exportedAt", System.currentTimeMillis())

        val foldersArray = JSONArray()
        folderTree.forEach { f ->
            val fObj = JSONObject().apply {
                put("id", f.id)
                put("name", f.name)
                put("color", f.color)
                if (f.parentId != null) put("parentId", f.parentId) else put("parentId", JSONObject.NULL)
                put("createdAt", f.createdAt)
            }
            foldersArray.put(fObj)
        }
        root.put("folders", foldersArray)

        val setsArray = JSONArray()
        papers.forEach { paper ->
            setsArray.put(toJsonObject(paper))
        }
        root.put("questionSets", setsArray)

        return if (papers.size <= 3) root.toString(2) else root.toString()
    }

    /**
     * Strips Markdown code block delimiters (```json ... ```) if present.
     */
    fun cleanJsonString(raw: String): String {
        var text = raw.trim()
        if (text.startsWith("```")) {
            text = text.substringAfter("\n")
        }
        if (text.endsWith("```")) {
            text = text.substringBeforeLast("```").trim()
        }
        text = text.trim()

        // Strip LaTeX math block wrappers: \[ ... \] or \\[ ... \\] or $$ ... $$
        while (text.startsWith("\\[") || text.startsWith("\\\\[") || text.startsWith("$$") || text.startsWith("$")) {
            if (text.startsWith("\\\\[")) text = text.removePrefix("\\\\[").trim()
            else if (text.startsWith("\\[")) text = text.removePrefix("\\[").trim()
            else if (text.startsWith("$$")) text = text.removePrefix("$$").trim()
            else if (text.startsWith("$") && !(text.length > 1 && text[1] == '{')) text = text.removePrefix("$").trim()
            else break
        }

        while (text.endsWith("\\]") || text.endsWith("\\\\]") || text.endsWith("$$") || text.endsWith("$")) {
            if (text.endsWith("\\\\]")) text = text.removeSuffix("\\\\]").trim()
            else if (text.endsWith("\\]")) text = text.removeSuffix("\\]").trim()
            else if (text.endsWith("$$")) text = text.removeSuffix("$$").trim()
            else if (text.endsWith("$")) text = text.removeSuffix("$").trim()
            else break
        }

        // Bracket matching: If surrounded by arbitrary preamble or postscript, extract outermost JSON array [ ... ] or object { ... }
        val firstSquare = text.indexOf('[')
        val firstCurly = text.indexOf('{')
        if (firstSquare != -1 && (firstCurly == -1 || firstSquare < firstCurly)) {
            val lastSquare = text.lastIndexOf(']')
            if (lastSquare > firstSquare) {
                text = text.substring(firstSquare, lastSquare + 1)
            }
        } else if (firstCurly != -1) {
            val lastCurly = text.lastIndexOf('}')
            if (lastCurly > firstCurly) {
                text = text.substring(firstCurly, lastCurly + 1)
            }
        }

        return text.trim()
    }

    /**
     * Parses a JSON string (single paper, questions array, or extracted bundle) into a QuestionPaper.
     */
    fun fromJson(jsonStr: String): QuestionPaper {
        val cleaned = cleanJsonString(jsonStr)
        if (cleaned.startsWith("[")) {
            val arr = JSONArray(cleaned)
            if (arr.length() > 0) {
                val first = arr.opt(0)
                if (first is JSONObject && !first.has("questions") && (first.has("question") || first.has("stem") || first.has("options"))) {
                    return parseFromQuestionsArray(arr)
                } else if (first is JSONObject) {
                    return fromJson(first.toString())
                }
            }
            return parseFromQuestionsArray(arr)
        }

        val root = JSONObject(cleaned)

        // Polymorphic check: if bundle root is passed, extract first question set
        if (root.has("questionSets")) {
            val arr = root.getJSONArray("questionSets")
            if (arr.length() > 0) {
                return fromJson(arr.getJSONObject(0).toString())
            }
        }
        if (root.has("sets")) {
            val arr = root.getJSONArray("sets")
            if (arr.length() > 0) {
                return fromJson(arr.getJSONObject(0).toString())
            }
        }

        val metaObj = root.optJSONObject("metadata")
        val metadata = if (metaObj != null) {
            val rawRoll = metaObj.optString("rollNumber", "").ifBlank { root.optString("rollNumber", "") }.trim()
            val finalRoll = if (rawRoll.isNotBlank()) rawRoll else ExamNumberGenerator.generateRollNumber()

            val rawBooklet = metaObj.optString("bookletNumber", "").ifBlank { root.optString("bookletNumber", "") }.trim()
            val finalBooklet = if (rawBooklet.isNotBlank()) rawBooklet else ExamNumberGenerator.generateBookletNumber()

            val rawSet = metaObj.optString("setNumber", "").ifBlank { root.optString("setNumber", "01") }.trim()

            val folder = if (metaObj.has("folderId") && !metaObj.isNull("folderId")) {
                metaObj.optString("folderId").ifBlank { null }
            } else if (root.has("folderId") && !root.isNull("folderId")) {
                root.optString("folderId").ifBlank { null }
            } else null

            PaperMetadata(
                id = metaObj.optString("id", ""),
                title = metaObj.optString("title", root.optString("title", "Exam Paper")),
                subject = metaObj.optString("subject", root.optString("subject", "General Knowledge")),
                topic = metaObj.optString("topic", root.optString("topic", "")),
                setNumber = if (rawSet.isNotBlank()) ExamNumberGenerator.normalizeSetNumber(rawSet) else "01",
                exam = metaObj.optString("targetExam", root.optString("exam", "")),
                folderId = folder,
                rollNumber = finalRoll,
                bookletNumber = finalBooklet,
                msqScoringPolicy = MsqScoringPolicy.fromCode(
                    metaObj.optString("msqScoringPolicy", root.optString("msqScoringPolicy", ""))
                ),
                createdAt = metaObj.optLong("createdAt", System.currentTimeMillis())
            )
        } else {
            val rawRoll = root.optString("rollNumber", "").trim()
            val finalRoll = if (rawRoll.isNotBlank()) rawRoll else ExamNumberGenerator.generateRollNumber()

            val rawBooklet = root.optString("bookletNumber", "").trim()
            val finalBooklet = if (rawBooklet.isNotBlank()) rawBooklet else ExamNumberGenerator.generateBookletNumber()

            val rawSet = root.optString("setNumber", "01").trim()
            val folder = if (root.has("folderId") && !root.isNull("folderId")) root.optString("folderId").ifBlank { null } else null

            PaperMetadata(
                id = root.optString("id", ""),
                title = root.optString("title", "Exam Paper"),
                subject = root.optString("subject", "General Knowledge"),
                topic = root.optString("topic", ""),
                setNumber = if (rawSet.isNotBlank()) ExamNumberGenerator.normalizeSetNumber(rawSet) else "01",
                exam = root.optString("exam", root.optString("targetExam", "")),
                folderId = folder,
                rollNumber = finalRoll,
                bookletNumber = finalBooklet,
                msqScoringPolicy = MsqScoringPolicy.fromCode(root.optString("msqScoringPolicy", "")),
                createdAt = root.optLong("createdAt", System.currentTimeMillis())
            )
        }

        val qArray = root.optJSONArray("questions") ?: JSONArray()
        val questions = mutableListOf<McqQuestion>()

        for (i in 0 until qArray.length()) {
            val qObj = qArray.getJSONObject(i)
            questions.add(parseQuestionObject(qObj, i + 1))
        }

        val keysCount = questions.count { it.correctAnswer != null }
        val expCount = questions.count { !it.explanation.isNullOrBlank() }

        return QuestionPaper(
            metadata = metadata,
            questions = questions,
            quickAnswersCount = keysCount,
            explanationsCount = expCount,
            createdAt = metadata.createdAt
        )
    }

    /**
     * Parses a single question from a JSONObject, supporting flexible field aliases.
     */
    fun parseQuestionObject(qObj: JSONObject, defaultNum: Int): McqQuestion {
        val num = qObj.optInt("num", qObj.optInt("id", defaultNum))
        val questionText = qObj.optString("question", qObj.optString("stem", qObj.optString("text", "")))

        // Flexible options: can be JSONObject or JSONArray
        val options = if (qObj.has("options")) {
            val optVal = qObj.get("options")
            if (optVal is JSONObject) {
                QuestionOptions(
                    a = optVal.optString("a", optVal.optString("A", "")),
                    b = optVal.optString("b", optVal.optString("B", "")),
                    c = optVal.optString("c", optVal.optString("C", "")),
                    d = optVal.optString("d", optVal.optString("D", "")),
                    e = optVal.optString("e", optVal.optString("E", ""))
                )
            } else if (optVal is JSONArray) {
                QuestionOptions(
                    a = optVal.optString(0, ""),
                    b = optVal.optString(1, ""),
                    c = optVal.optString(2, ""),
                    d = optVal.optString(3, ""),
                    e = optVal.optString(4, "")
                )
            } else {
                QuestionOptions()
            }
        } else {
            // Check top-level option fields
            QuestionOptions(
                a = qObj.optString("a", qObj.optString("optionA", qObj.optString("A", ""))),
                b = qObj.optString("b", qObj.optString("optionB", qObj.optString("B", ""))),
                c = qObj.optString("c", qObj.optString("optionC", qObj.optString("C", ""))),
                d = qObj.optString("d", qObj.optString("optionD", qObj.optString("D", ""))),
                e = qObj.optString("e", qObj.optString("optionE", qObj.optString("E", "")))
            )
        }

        val rawAnsAny: Any? = when {
            qObj.has("correctAnswer") && !qObj.isNull("correctAnswer") -> qObj.get("correctAnswer")
            qObj.has("correctAnswers") && !qObj.isNull("correctAnswers") -> qObj.get("correctAnswers")
            qObj.has("answer") && !qObj.isNull("answer") -> qObj.get("answer")
            qObj.has("ans") && !qObj.isNull("ans") -> qObj.get("ans")
            qObj.has("correctOption") && !qObj.isNull("correctOption") -> qObj.get("correctOption")
            else -> null
        }
        val rawAnsString = when (rawAnsAny) {
            is JSONArray -> (0 until rawAnsAny.length()).mapNotNull { idx ->
                rawAnsAny.optString(idx).takeIf { it.isNotBlank() }
            }.joinToString(",")
            else -> rawAnsAny?.toString().orEmpty()
        }
        val ans = AnswerKeyMatcher.normalizeKey(rawAnsString).ifBlank { null }

        val exp = qObj.optString("explanation", qObj.optString("detailedExplanation", qObj.optString("solution", qObj.optString("rationale", qObj.optString("exp", ""))))).ifBlank { null }
        val explicitQType = qObj.optString("questionType", qObj.optString("type", "")).ifBlank { null }
        val qType = if (explicitQType == null && AnswerKeyMatcher.parseSelectedLetters(ans).size >= 2) {
            "MSQ"
        } else {
            explicitQType
        }
        val diagramUrl = qObj.optString("diagramUrl", "").ifBlank { null }
        val diagramLabel = qObj.optString("diagramLabel", "").ifBlank { null }
        val formula = qObj.optString("formula", "").ifBlank { null }

        return McqQuestion(
            num = num,
            question = questionText,
            options = options,
            correctAnswer = ans,
            explanation = exp,
            questionType = qType,
            diagramUrl = diagramUrl,
            diagramLabel = diagramLabel,
            formula = formula
        )
    }

    /**
     * Parses a QuestionPaper directly from a JSON array of question objects.
     */
    fun parseFromQuestionsArray(arr: JSONArray, title: String = "Imported Question Set"): QuestionPaper {
        val questions = mutableListOf<McqQuestion>()
        for (i in 0 until arr.length()) {
            val qObj = arr.optJSONObject(i) ?: continue
            questions.add(parseQuestionObject(qObj, i + 1))
        }

        val keysCount = questions.count { it.correctAnswer != null }
        val expCount = questions.count { !it.explanation.isNullOrBlank() }

        val metadata = PaperMetadata(
            title = title,
            subject = "General Studies",
            topic = "",
            setNumber = "01",
            rollNumber = ExamNumberGenerator.generateRollNumber(),
            bookletNumber = ExamNumberGenerator.generateBookletNumber()
        )

        return QuestionPaper(
            metadata = metadata,
            questions = questions,
            quickAnswersCount = keysCount,
            explanationsCount = expCount,
            createdAt = metadata.createdAt
        )
    }

    /**
     * Polymorphic importer that parses multi-set bundles, arrays, or single papers into a list of QuestionPapers.
     */
    fun bundleFromJson(jsonStr: String): List<QuestionPaper> {
        val cleaned = cleanJsonString(jsonStr)
        if (cleaned.startsWith("[")) {
            val arr = JSONArray(cleaned)
            if (arr.length() > 0) {
                val first = arr.opt(0)
                if (first is JSONObject && !first.has("questions") && (first.has("question") || first.has("stem") || first.has("options"))) {
                    return listOf(parseFromQuestionsArray(arr))
                }
            }
            val list = mutableListOf<QuestionPaper>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                list.add(fromJson(obj.toString()))
            }
            return list
        }

        val root = JSONObject(cleaned)
        if (root.has("questionSets")) {
            val arr = root.getJSONArray("questionSets")
            val list = mutableListOf<QuestionPaper>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                list.add(fromJson(obj.toString()))
            }
            return list
        }
        if (root.has("sets")) {
            val arr = root.getJSONArray("sets")
            val list = mutableListOf<QuestionPaper>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                list.add(fromJson(obj.toString()))
            }
            return list
        }

        return listOf(fromJson(cleaned))
    }

    /**
     * Generates a sample JSON string for testing or user inspection.
     */
    fun getSampleQuestionsJson(): String {
        return """[
  {
    "num": 1,
    "question": "Which layer of Earth's atmosphere contains the highest concentration of ozone (${'$'}\\text{O}_3${'$'}) and absorbs harmful UV-B radiation?",
    "options": {
      "a": "Troposphere",
      "b": "Stratosphere",
      "c": "Mesosphere",
      "d": "Thermosphere"
    },
    "correctAnswer": "B",
    "explanation": "The ozone layer is primarily situated in the lower portion of the stratosphere, approximately 15 to 35 km above Earth. It absorbs 97-99% of the Sun's medium-frequency ultraviolet radiation."
  },
  {
    "num": 2,
    "question": "What is the equivalent electrical resistance when three resistors of 6 ${'$'}\\Omega${'$'}, 3 ${'$'}\\Omega${'$'}, and 2 ${'$'}\\Omega${'$'} are connected in parallel?",
    "options": {
      "a": "1.0 Ω",
      "b": "2.0 Ω",
      "c": "11.0 Ω",
      "d": "0.5 Ω"
    },
    "correctAnswer": "A",
    "explanation": "Using the parallel resistance formula 1/R_eq = 1/6 + 1/3 + 1/2 = 1/6 + 2/6 + 3/6 = 6/6 = 1.0 Ω. Therefore, R_eq = 1.0 Ω."
  },
  {
    "num": 3,
    "question": "Which enzyme is responsible for catalyzing the synthesis of RNA from a DNA template during transcription?",
    "options": {
      "a": "DNA Polymerase I",
      "b": "DNA Ligase",
      "c": "RNA Polymerase",
      "d": "Reverse Transcriptase"
    },
    "correctAnswer": "C",
    "explanation": "RNA Polymerase binds to the promoter sequence and synthesizes a complementary RNA strand from the DNA template in the 5' to 3' direction."
  }
]""".trimIndent()
    }

    data class OmrExportPayload(
        val candidateRollNumber: String,
        val candidateBookletNumber: String,
        val candidateSetCode: String,
        val paperTitle: String,
        val paperId: Long,
        val totalQuestions: Int,
        val score: Double,
        val correctCount: Int,
        val incorrectCount: Int,
        val unattemptedCount: Int,
        val answers: Map<Int, String>,
        val evaluatedAt: Long = System.currentTimeMillis()
    )

    fun omrEvaluationToJson(payload: OmrExportPayload): String {
        val root = JSONObject().apply {
            put("exportType", "OMR_EVALUATION_RECORD")
            put("schemaVersion", CURRENT_SCHEMA_VERSION)
            put("rollNumber", payload.candidateRollNumber)
            put("bookletNumber", payload.candidateBookletNumber)
            put("setCode", payload.candidateSetCode)
            put("paperTitle", payload.paperTitle)
            put("paperId", payload.paperId)
            put("totalQuestions", payload.totalQuestions)
            put("score", payload.score)
            put("correctCount", payload.correctCount)
            put("incorrectCount", payload.incorrectCount)
            put("unattemptedCount", payload.unattemptedCount)
            put("evaluatedAt", payload.evaluatedAt)
            val ansObj = JSONObject()
            payload.answers.forEach { (q, a) -> ansObj.put(q.toString(), a) }
            put("answers", ansObj)
        }
        return root.toString(2)
    }
}
