package com.example.util

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
     * Parses a JSON string (single paper or extracted bundle) into a QuestionPaper.
     */
    fun fromJson(jsonStr: String): QuestionPaper {
        val root = JSONObject(jsonStr)

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
            val rawRoll = metaObj.optString("rollNumber", root.optString("rollNumber", "")).trim()
            val finalRoll = if (rawRoll.isNotBlank()) rawRoll else ExamNumberGenerator.generateRollNumber()

            val rawBooklet = metaObj.optString("bookletNumber", root.optString("bookletNumber", "")).trim()
            val finalBooklet = if (rawBooklet.isNotBlank()) rawBooklet else ExamNumberGenerator.generateBookletNumber()

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
                setNumber = metaObj.optString("setNumber", root.optString("setNumber", "Set A")),
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

            val folder = if (root.has("folderId") && !root.isNull("folderId")) root.optString("folderId").ifBlank { null } else null

            PaperMetadata(
                id = root.optString("id", ""),
                title = root.optString("title", "Exam Paper"),
                subject = root.optString("subject", "General Knowledge"),
                topic = root.optString("topic", ""),
                setNumber = root.optString("setNumber", "Set A"),
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
            val num = qObj.optInt("num", i + 1)
            val questionText = qObj.optString("question", "")

            val optObj = qObj.optJSONObject("options")
            val options = if (optObj != null) {
                QuestionOptions(
                    a = optObj.optString("a", ""),
                    b = optObj.optString("b", ""),
                    c = optObj.optString("c", ""),
                    d = optObj.optString("d", "")
                )
            } else {
                QuestionOptions()
            }

            val ans = if (qObj.isNull("correctAnswer")) null else qObj.optString("correctAnswer", null)
            val exp = qObj.optString("explanation", "").ifBlank { null }
            val qType = qObj.optString("questionType", "").ifBlank { null }
            val diagramUrl = qObj.optString("diagramUrl", "").ifBlank { null }
            val diagramLabel = qObj.optString("diagramLabel", "").ifBlank { null }
            val formula = qObj.optString("formula", "").ifBlank { null }

            questions.add(
                McqQuestion(
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
            )
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
     * Polymorphic importer that parses multi-set bundles, arrays, or single papers into a list of QuestionPapers.
     */
    fun bundleFromJson(jsonStr: String): List<QuestionPaper> {
        val trimmed = jsonStr.trim()
        if (trimmed.startsWith("[")) {
            val arr = JSONArray(trimmed)
            val list = mutableListOf<QuestionPaper>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                list.add(fromJson(obj.toString()))
            }
            return list
        }

        val root = JSONObject(trimmed)
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

        return listOf(fromJson(trimmed))
    }
}
