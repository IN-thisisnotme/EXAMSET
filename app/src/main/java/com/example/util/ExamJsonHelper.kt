package com.example.util

import com.example.model.FolderItem
import com.example.model.McqQuestion
import com.example.model.PaperMetadata
import com.example.model.QuestionOptions
import com.example.model.QuestionPaper
import org.json.JSONArray
import org.json.JSONObject

object ExamJsonHelper {

    const val CURRENT_SCHEMA_VERSION = 2

    /**
     * Exports a single question paper as a structured Schema v2 JSON string.
     */
    fun toJson(paper: QuestionPaper): String {
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

        return root.toString(2)
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
            setsArray.put(JSONObject(toJson(paper)))
        }
        root.put("questionSets", setsArray)

        return root.toString(2)
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
            setsArray.put(JSONObject(toJson(paper)))
        }
        root.put("questionSets", setsArray)

        return root.toString(2)
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
                    d = optVal.optString("d", optVal.optString("D", ""))
                )
            } else if (optVal is JSONArray) {
                QuestionOptions(
                    a = optVal.optString(0, ""),
                    b = optVal.optString(1, ""),
                    c = optVal.optString(2, ""),
                    d = optVal.optString(3, "")
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
                d = qObj.optString("d", qObj.optString("optionD", qObj.optString("D", "")))
            )
        }

        val rawAns = if (qObj.isNull("correctAnswer")) {
            qObj.optString("answer", qObj.optString("ans", qObj.optString("correctOption", "")))
        } else {
            qObj.optString("correctAnswer", "")
        }
        val ans = rawAns.trim().uppercase().takeIf { it in listOf("A", "B", "C", "D", "1", "2", "3", "4") }?.let {
            when (it) {
                "1" -> "A"; "2" -> "B"; "3" -> "C"; "4" -> "D"; else -> it
            }
        }

        val exp = qObj.optString("explanation", qObj.optString("detailedExplanation", qObj.optString("solution", qObj.optString("rationale", qObj.optString("exp", ""))))).ifBlank { null }
        val qType = qObj.optString("questionType", qObj.optString("type", "")).ifBlank { null }
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
