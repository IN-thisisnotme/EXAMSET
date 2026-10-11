package com.example.util

import com.example.model.McqQuestion
import com.example.model.PaperMetadata
import com.example.model.QuestionOptions
import com.example.model.QuestionPaper
import org.json.JSONArray
import org.json.JSONObject

object ExamJsonHelper {

    fun toJson(paper: QuestionPaper): String {
        val root = JSONObject()

        // Metadata
        val metaObj = JSONObject().apply {
            put("id", if (paper.metadata.id.isNotBlank()) paper.metadata.id else "paper-${paper.id}")
            put("title", paper.metadata.title)
            put("subject", paper.metadata.subject)
            put("topic", paper.metadata.topic)
            put("setNumber", paper.metadata.setNumber)
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

    fun fromJson(jsonStr: String): QuestionPaper {
        val root = JSONObject(jsonStr)

        val metaObj = root.optJSONObject("metadata")
        val metadata = if (metaObj != null) {
            PaperMetadata(
                id = metaObj.optString("id", ""),
                title = metaObj.optString("title", "Exam Paper"),
                subject = metaObj.optString("subject", "General Knowledge"),
                topic = metaObj.optString("topic", ""),
                setNumber = metaObj.optString("setNumber", "Set A"),
                exam = metaObj.optString("targetExam", ""),
                folderId = if (metaObj.has("folderId")) metaObj.optString("folderId") else null,
                createdAt = metaObj.optLong("createdAt", System.currentTimeMillis())
            )
        } else {
            PaperMetadata()
        }

        val qArray = root.getJSONArray("questions")
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
}
