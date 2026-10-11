package com.example.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

object RichTextHelper {

    private val SUB_MAP = mapOf(
        '₀' to '0', '₁' to '1', '₂' to '2', '₃' to '3', '₄' to '4',
        '₅' to '5', '₆' to '6', '₇' to '7', '₈' to '8', '₉' to '9',
        '₊' to '+', '₋' to '-', '₌' to '='
    )

    private val SUP_MAP = mapOf(
        '⁰' to '0', '¹' to '1', '²' to '2', '³' to '3', '⁴' to '4',
        '⁵' to '5', '⁶' to '6', '⁷' to '7', '⁸' to '8', '⁹' to '9',
        '⁺' to '+', '⁻' to '-', '⁼' to '='
    )

    /**
     * Converts unicode subscripts/superscripts to HTML tags for HTML export,
     * directly matching the Python generator's sanitize_sub_super().
     */
    fun sanitizeSubSuper(text: String): String {
        if (text.isEmpty()) return ""
        var result = text
        for ((subChar, regular) in SUB_MAP) {
            result = result.replace(subChar.toString(), "<sub>$regular</sub>")
        }
        for ((supChar, regular) in SUP_MAP) {
            result = result.replace(supChar.toString(), "<sup>$regular</sup>")
        }
        return result
    }

    /**
     * Builds a Jetpack Compose AnnotatedString supporting:
     * - Unicode subscripts (₀-₉) and superscripts (⁰-⁹)
     * - HTML tags: <sub>, <sup>, <b>, <i>
     * - Markdown: **bold**, *italic*, `code`
     */
    fun buildRichText(
        text: String,
        baseColor: Color = Color.Unspecified,
        baseFontSize: TextUnit = 14.sp,
        isBold: Boolean = false
    ): AnnotatedString {
        if (text.isEmpty()) return AnnotatedString("")

        // First convert raw Unicode sub/sup into standard tags so tokenizer can parse them uniformly
        var tagged = sanitizeSubSuper(text)

        // Convert paired markdown bold (__bold__) to <b>bold</b> so that fill-in-the-blank patterns
        // like ______ or ___ are not mistakenly consumed by the bold tokenizer.
        tagged = tagged.replace(Regex("""(?<!_)__([^\s_](?:[\s\S]*?[^\s_])?)__(?!_)"""), "<b>$1</b>")

        return buildAnnotatedString {
            // Tokenize by HTML tags and Markdown delimiters (**bold**, *italic*, `code`)
            val tokenRegex = Regex("""(<b>|</b>|<i>|</i>|<sub>|</sub>|<sup>|</sup>|\*\*|\*|`)""", RegexOption.IGNORE_CASE)
            val tokens = tokenRegex.split(tagged)
            val matches = tokenRegex.findAll(tagged).toList()

            var currentBold = isBold
            var currentItalic = false
            var currentSub = false
            var currentSup = false

            fun applyCurrentSpan(content: String) {
                if (content.isEmpty()) return
                val start = length
                append(content)
                val end = length

                val style = SpanStyle(
                    color = baseColor,
                    fontWeight = if (currentBold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (currentItalic) FontStyle.Italic else FontStyle.Normal,
                    fontSize = if (currentSub || currentSup) (baseFontSize.value * 0.75f).sp else baseFontSize,
                    baselineShift = when {
                        currentSub -> BaselineShift.Subscript
                        currentSup -> BaselineShift.Superscript
                        else -> BaselineShift.None
                    }
                )
                addStyle(style, start, end)
            }

            for (i in tokens.indices) {
                applyCurrentSpan(tokens[i])

                if (i < matches.size) {
                    val tag = matches[i].value.lowercase()
                    when (tag) {
                        "<b>", "**" -> currentBold = !currentBold
                        "</b>" -> currentBold = isBold
                        "<i>", "*" -> currentItalic = !currentItalic
                        "</i>" -> currentItalic = false
                        "<sub>" -> currentSub = true
                        "</sub>" -> currentSub = false
                        "<sup>" -> currentSup = true
                        "</sup>" -> currentSup = false
                    }
                }
            }
        }
    }
}
