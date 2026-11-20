package com.example.model

import org.json.JSONObject

enum class KnowledgeCollectionType(
    val code: String,
    val label: String,
    val shortBadge: String,
    val description: String
) {
    ONE_LINERS(
        code = "ONE_LINERS",
        label = "One-Liners",
        shortBadge = "ONE-LINER",
        description = "Crisp term-to-fact recall lines & numbered takeaways"
    ),
    MULTI_LINER(
        code = "MULTI_LINER",
        label = "Multi-Liner Cards",
        shortBadge = "MULTI-LINER",
        description = "Rich concept cards with nested sub-points, examples & formulas"
    ),
    CONCEPT_CODEX(
        code = "CONCEPT_CODEX",
        label = "Concept Codex",
        shortBadge = "CODEX",
        description = "Deep multi-layer topic breakdown for thorough study"
    ),
    LONG_ANSWER(
        code = "LONG_ANSWER",
        label = "Long-Form / Mains Q&A",
        shortBadge = "LONG Q&A",
        description = "Multi-paragraph UPSC Mains & subjective answers with LaTeX, tables & citations"
    ),
    COMPARISON_TABLE(
        code = "COMPARISON_TABLE",
        label = "Comparison Matrix",
        shortBadge = "MATRIX",
        description = "Structured side-by-side Markdown comparison tables"
    ),
    FORMULA_SHEET(
        code = "FORMULA_SHEET",
        label = "Formula & Law Sheet",
        shortBadge = "FORMULAS",
        description = "LaTeX equations, constants, units & quick derivations"
    ),
    WEAK_AREA_REVIEW(
        code = "WEAK_AREA_REVIEW",
        label = "Mistake & Weak-Area Log",
        shortBadge = "WEAK AREAS",
        description = "Compiled from missed or skipped test questions"
    ),
    CUSTOM(
        code = "CUSTOM",
        label = "Custom Format",
        shortBadge = "CUSTOM",
        description = "User-defined layout and custom AI prompt rules"
    );

    companion object {
        fun fromCode(code: String?): KnowledgeCollectionType {
            if (code.isNullOrBlank()) return ONE_LINERS
            return entries.firstOrNull {
                it.code.equals(code, ignoreCase = true) ||
                    it.label.equals(code, ignoreCase = true)
            } ?: CUSTOM
        }
    }
}

enum class KnowledgeDepth(val label: String) {
    CRISP("Crisp & High-Yield"),
    BALANCED("Balanced Multi-Layer"),
    DEEP("Comprehensive Deep Dive")
}

enum class KnowledgeLayerOrder(val label: String, val hint: String) {
    TERM_FIRST("Term -> Explanation", "Standard: Lead concept first, explanation second"),
    BACKWARDS_ANSWER_FIRST("Answer / Detail -> Concept", "Backwards ChatGPT layer: Detail/answer first, key term after"),
    QA_SEPARATE_LINES("Q: / A: Multi-Line", "Question/Prompt on line 1, Answer/Reasoning on next lines"),
    AUTO_DETECT("Auto-Detect Mixed Layers", "Smart tree parser handles mixed & nested AI layers")
}

data class KnowledgeFormatRule(
    val collectionType: KnowledgeCollectionType = KnowledgeCollectionType.ONE_LINERS,
    val customCollectionLabel: String = "",
    val layerOrder: KnowledgeLayerOrder = KnowledgeLayerOrder.AUTO_DETECT,
    val depth: KnowledgeDepth = KnowledgeDepth.BALANCED,
    val language: String = "English",
    val preserveNestedBullets: Boolean = true,
    val includeLatexMath: Boolean = true,
    val includeCallouts: Boolean = true,
    val includeComparisonTable: Boolean = false,
    val customInstruction: String = ""
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("collectionType", collectionType.code)
            put("customCollectionLabel", customCollectionLabel)
            put("layerOrder", layerOrder.name)
            put("depth", depth.name)
            put("language", language)
            put("preserveNestedBullets", preserveNestedBullets)
            put("includeLatexMath", includeLatexMath)
            put("includeCallouts", includeCallouts)
            put("includeComparisonTable", includeComparisonTable)
            put("customInstruction", customInstruction)
        }.toString()
    }

    companion object {
        fun fromJson(json: String?): KnowledgeFormatRule {
            if (json.isNullOrBlank() || json == "{}") return KnowledgeFormatRule()
            return try {
                val obj = JSONObject(json)
                KnowledgeFormatRule(
                    collectionType = KnowledgeCollectionType.fromCode(obj.optString("collectionType")),
                    customCollectionLabel = obj.optString("customCollectionLabel", ""),
                    layerOrder = runCatching {
                        KnowledgeLayerOrder.valueOf(obj.optString("layerOrder", KnowledgeLayerOrder.AUTO_DETECT.name))
                    }.getOrDefault(KnowledgeLayerOrder.AUTO_DETECT),
                    depth = runCatching {
                        KnowledgeDepth.valueOf(obj.optString("depth", KnowledgeDepth.BALANCED.name))
                    }.getOrDefault(KnowledgeDepth.BALANCED),
                    language = obj.optString("language", "English").ifBlank { "English" },
                    preserveNestedBullets = obj.optBoolean("preserveNestedBullets", true),
                    includeLatexMath = obj.optBoolean("includeLatexMath", true),
                    includeCallouts = obj.optBoolean("includeCallouts", true),
                    includeComparisonTable = obj.optBoolean("includeComparisonTable", false),
                    customInstruction = obj.optString("customInstruction", "")
                )
            } catch (_: Exception) {
                KnowledgeFormatRule()
            }
        }
    }
}

enum class KnowledgeTransformAction(val label: String, val description: String) {
    FIX_BACKWARDS_CHATGPT("Fix Backwards ChatGPT", "Normalizes reversed answers, nested layers & chatter"),
    SWAP_LEAD_DETAIL("Swap Term <-> Detail", "Flips left and right sides across key-value lines"),
    MERGE_NESTED_BULLETS("Group Multi-Liners", "Indents & merges loose sub-points into concept cards"),
    STRIP_AI_CHATTER("Clean AI Chatter", "Strips greetings, fences, trailing tips & rules"),
    SPLIT_SEMICOLONS("Split Dense Lines", "Expands semicolon-packed lines into clean bullets")
}
