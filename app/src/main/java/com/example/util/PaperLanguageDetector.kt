package com.example.util

/**
 * Detects the actual human language(s) present in a question paper's content
 * so UI cards accurately display e.g. "Available in English", "Available in Hindi",
 * or "Available in English, Hindi" (bilingual) instead of a hardcoded label.
 */
object PaperLanguageDetector {

    private val METADATA_LINE_REGEX = Regex(
        """(?im)^\s*(?:Title|Subject|Topic|Series|Set(?:\s*Number)?|Exam(?:ination)?|Time|Duration|Max\s*Marks|Roll(?:\s*Number)?|Booklet(?:\s*Number)?|Language|Answer\s*Key|Detailed\s*Explanations?|Correct\s*Answer|Explanation|Formula)\s*:.*$"""
    )
    private val BOILERPLATE_LABELS_REGEX = Regex(
        """(?i)\b(?:Answer\s*Key|Detailed\s*Explanations?|Correct\s*Answer|Explanation|Option\s*[A-D]|Correct|Incorrect|Formula|True|False)\b"""
    )
    private val JSON_KEYS_REGEX = Regex(
        """\"(?:num|question|options|a|b|c|d|correctAnswer|explanation|title|subject|topic|setNumber|exam|questionType|difficulty|metadata|questions|positiveMarks|negativeMarks|durationMinutes|manualCategory)\"\s*:"""
    )
    private val OPTION_MARKER_REGEX = Regex("""\(\s*[a-dA-D]\s*\)|\b[a-dA-D]\s*[).:]""")

    fun detectLanguages(rawText: String, title: String = ""): List<String> {
        val source = buildString {
            if (title.isNotBlank()) append(title).append('\n')
            append(rawText)
        }
        if (source.isBlank()) return listOf("English")

        val cleaned = source
            .replace(METADATA_LINE_REGEX, " ")
            .replace(JSON_KEYS_REGEX, " ")
            .replace(BOILERPLATE_LABELS_REGEX, " ")
            .replace(OPTION_MARKER_REGEX, " ")

        var latinLetters = 0
        var devanagariLetters = 0
        var hangulLetters = 0
        var kanaLetters = 0
        var hanLetters = 0
        var arabicLetters = 0
        var hasUrduSpecificChars = false
        var bengaliLetters = 0
        var tamilLetters = 0
        var teluguLetters = 0
        var kannadaLetters = 0
        var malayalamLetters = 0
        var gujaratiLetters = 0
        var gurmukhiLetters = 0
        var odiaLetters = 0
        var cyrillicLetters = 0

        var index = 0
        while (index < cleaned.length) {
            val cp = cleaned.codePointAt(index)
            index += Character.charCount(cp)
            if (!Character.isLetter(cp)) continue

            when (Character.UnicodeScript.of(cp)) {
                Character.UnicodeScript.LATIN -> latinLetters++
                Character.UnicodeScript.DEVANAGARI -> devanagariLetters++
                Character.UnicodeScript.HANGUL -> hangulLetters++
                Character.UnicodeScript.HIRAGANA,
                Character.UnicodeScript.KATAKANA -> kanaLetters++
                Character.UnicodeScript.HAN -> hanLetters++
                Character.UnicodeScript.ARABIC -> {
                    arabicLetters++
                    if (cp == 0x0679 || cp == 0x067E || cp == 0x0686 || cp == 0x0688 ||
                        cp == 0x0691 || cp == 0x0698 || cp == 0x06AF || cp == 0x06BA ||
                        cp == 0x06C1 || cp == 0x06D2
                    ) {
                        hasUrduSpecificChars = true
                    }
                }
                Character.UnicodeScript.BENGALI -> bengaliLetters++
                Character.UnicodeScript.TAMIL -> tamilLetters++
                Character.UnicodeScript.TELUGU -> teluguLetters++
                Character.UnicodeScript.KANNADA -> kannadaLetters++
                Character.UnicodeScript.MALAYALAM -> malayalamLetters++
                Character.UnicodeScript.GUJARATI -> gujaratiLetters++
                Character.UnicodeScript.GURMUKHI -> gurmukhiLetters++
                Character.UnicodeScript.ORIYA -> odiaLetters++
                Character.UnicodeScript.CYRILLIC -> cyrillicLetters++
                else -> {}
            }
        }

        val totalLetters = (
            latinLetters + devanagariLetters + hangulLetters + kanaLetters +
                hanLetters + arabicLetters + bengaliLetters + tamilLetters +
                teluguLetters + kannadaLetters + malayalamLetters +
                gujaratiLetters + gurmukhiLetters + odiaLetters + cyrillicLetters
            ).coerceAtLeast(1)

        val nonLatinDetected = mutableListOf<String>()
        fun checkScript(count: Int, label: String) {
            if (count >= 12 || (count >= 4 && count.toDouble() / totalLetters >= 0.05)) {
                nonLatinDetected.add(label)
            }
        }

        checkScript(devanagariLetters, "Hindi")
        checkScript(hangulLetters, "Korean")
        if (kanaLetters >= 6 || (kanaLetters > 0 && hanLetters > 0)) {
            nonLatinDetected.add("Japanese")
        } else {
            checkScript(hanLetters, "Chinese")
        }
        if (arabicLetters >= 12 || (arabicLetters >= 4 && arabicLetters.toDouble() / totalLetters >= 0.05)) {
            nonLatinDetected.add(if (hasUrduSpecificChars) "Urdu" else "Arabic")
        }
        checkScript(bengaliLetters, "Bengali")
        checkScript(tamilLetters, "Tamil")
        checkScript(teluguLetters, "Telugu")
        checkScript(kannadaLetters, "Kannada")
        checkScript(malayalamLetters, "Malayalam")
        checkScript(gujaratiLetters, "Gujarati")
        checkScript(gurmukhiLetters, "Punjabi")
        checkScript(odiaLetters, "Odia")
        checkScript(cyrillicLetters, "Cyrillic")

        if (nonLatinDetected.isEmpty()) {
            return listOf("English")
        }

        // Only treat the paper as bilingual with English if Latin prose is genuinely substantial
        // (ignores stray English proper nouns/symbols like "UNESCO", "Hangul", or formula symbols).
        val latinRatio = latinLetters.toDouble() / totalLetters
        val hasSubstantialEnglish = latinLetters >= 60 && latinRatio >= 0.25
        return if (hasSubstantialEnglish) {
            listOf("English") + nonLatinDetected
        } else {
            nonLatinDetected
        }
    }

    fun formatAvailableLabel(languages: List<String>): String {
        val clean = languages.filter { it.isNotBlank() }.distinct()
        return if (clean.isEmpty()) {
            "Available in English"
        } else {
            "Available in ${clean.joinToString(", ")}"
        }
    }
}
