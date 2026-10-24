package com.example.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.LatexHelper
import com.example.util.RichTextHelper


/**
 * Step 3: Dedicated LaTeX & Math-enabled Text Composable.
 * - Seamlessly parses and displays inline and display LaTeX math expressions ($...$, $$...$$).
 * - Automatic graceful fallback to standard RichText if no LaTeX or if any error occurs.
 */
@Composable
fun LatexText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    fontStyle: FontStyle? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    isBold: Boolean = false
) {
    val style = LocalTextStyle.current
    val targetFontSize = if (fontSize != TextUnit.Unspecified) fontSize else style.fontSize
    val trimmed = text.trim()
    val isPureDisplayMath = (trimmed.startsWith("\\[") && trimmed.endsWith("\\]")) ||
            (trimmed.startsWith("$$") && trimmed.endsWith("$$")) ||
            (trimmed.startsWith("\\begin{") && trimmed.contains("\\end{"))

    val effectiveLineHeight = when {
        lineHeight != TextUnit.Unspecified -> lineHeight
        targetFontSize != TextUnit.Unspecified -> (targetFontSize.value * 1.58f).sp
        else -> 23.sp
    }

    if (isPureDisplayMath && MathFormulaParser.has2DMath(trimmed)) {
        MathFormulaView(
            formula = trimmed,
            color = color,
            fontSize = targetFontSize,
            modifier = modifier.padding(vertical = 5.dp)
        )
    } else {
        val annotatedString = remember(text, color, targetFontSize, isBold) {
            try {
                if (LatexHelper.hasLatex(text)) {
                    LatexHelper.buildLatexAnnotatedString(
                        text = text,
                        baseColor = color,
                        baseFontSize = targetFontSize,
                        isBold = isBold
                    )
                } else {
                    RichTextHelper.buildRichText(
                        text = text,
                        baseColor = color,
                        baseFontSize = targetFontSize,
                        isBold = isBold
                    )
                }
            } catch (e: Exception) {
                RichTextHelper.buildRichText(
                    text = text,
                    baseColor = color,
                    baseFontSize = targetFontSize,
                    isBold = isBold
                )
            }
        }

        Text(
            text = annotatedString,
            modifier = modifier,
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            lineHeight = effectiveLineHeight,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = overflow
        )
    }
}
