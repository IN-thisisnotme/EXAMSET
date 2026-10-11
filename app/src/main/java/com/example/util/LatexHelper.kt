package com.example.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * LaTeX Detection, Segmentation, and Mathematical Formatting Engine.
 * Converts LaTeX math expressions (inline, display, and bare commands) into readable rich text
 * compatible with Compose AnnotatedString.
 */
object LatexHelper {

    data class MathSegment(
        val content: String,
        val isMath: Boolean,
        val isDisplay: Boolean = false
    )

    private val GREEK_SYMBOLS = mapOf(
        "\\alpha" to "α", "\\beta" to "β", "\\gamma" to "γ", "\\delta" to "δ",
        "\\epsilon" to "ε", "\\varepsilon" to "ε", "\\zeta" to "ζ", "\\eta" to "η",
        "\\theta" to "θ", "\\vartheta" to "ϑ", "\\iota" to "ι", "\\kappa" to "κ",
        "\\lambda" to "λ", "\\mu" to "μ", "\\micro" to "μ", "\\nu" to "ν", "\\xi" to "ξ",
        "\\pi" to "π", "\\varpi" to "ϖ", "\\rho" to "ρ", "\\varrho" to "ϱ",
        "\\sigma" to "σ", "\\varsigma" to "ς", "\\tau" to "τ",
        "\\upsilon" to "υ", "\\phi" to "ϕ", "\\varphi" to "φ", "\\chi" to "χ",
        "\\psi" to "ψ", "\\omega" to "ω",
        "\\Gamma" to "Γ", "\\Delta" to "Δ", "\\Theta" to "Θ", "\\Lambda" to "Λ",
        "\\Xi" to "Ξ", "\\Pi" to "Π", "\\Sigma" to "Σ", "\\Upsilon" to "Υ",
        "\\Phi" to "Φ", "\\Psi" to "Ψ", "\\Omega" to "Ω", "\\ohm" to "Ω"
    )

    private val MATH_OPERATORS = mapOf(
        // Trigonometric / logarithmic
        "\\sin" to "sin",
        "\\cos" to "cos",
        "\\tan" to "tan",
        "\\cot" to "cot",
        "\\sec" to "sec",
        "\\csc" to "csc",
        "\\arcsin" to "arcsin",
        "\\arccos" to "arccos",
        "\\arctan" to "arctan",
        "\\sinh" to "sinh",
        "\\cosh" to "cosh",
        "\\tanh" to "tanh",
        "\\log" to "log",
        "\\ln" to "ln",
        "\\exp" to "exp",

        // Common mathematical functions
        "\\lim" to "lim",
        "\\max" to "max",
        "\\min" to "min",
        "\\sup" to "sup",
        "\\inf" to "inf",
        "\\det" to "det",
        "\\gcd" to "gcd",
        "\\ker" to "ker",
        "\\dim" to "dim",
        "\\mod" to "mod",
        "\\Pr" to "Pr",

        // Arithmetic
        "\\times" to "×",
        "\\div" to "÷",
        "\\pm" to "±",
        "\\mp" to "∓",
        "\\cdot" to "·",
        "\\ast" to "∗",
        "\\star" to "⋆",
        "\\circ" to "°",
        "\\bullet" to "•",

        // Relations
        "\\leq" to "≤",
        "\\le" to "≤",
        "\\geq" to "≥",
        "\\ge" to "≥",
        "\\neq" to "≠",
        "\\ne" to "≠",
        "\\approx" to "≈",
        "\\equiv" to "≡",
        "\\cong" to "≅",
        "\\sim" to "∼",
        "\\simeq" to "≃",
        "\\asymp" to "≍",
        "\\propto" to "∝",

        // Arrows
        "\\to" to "→",
        "\\rightarrow" to "→",
        "\\leftarrow" to "←",
        "\\leftrightarrow" to "↔",
        "\\Rightarrow" to "⇒",
        "\\Leftarrow" to "⇐",
        "\\Leftrightarrow" to "⇔",
        "\\mapsto" to "↦",
        "\\longrightarrow" to "⟶",
        "\\longleftarrow" to "⟵",
        "\\longleftrightarrow" to "⟷",
        "\\Longrightarrow" to "⟹",
        "\\Longleftarrow" to "⟸",
        "\\Longleftrightarrow" to "⟺",
        "\\implies" to "⟹",
        "\\iff" to "⟺",

        // Calculus
        "\\infty" to "∞",
        "\\partial" to "∂",
        "\\nabla" to "∇",
        "\\prime" to "′",
        "\\int" to "∫",
        "\\iint" to "∬",
        "\\iiint" to "∭",
        "\\oint" to "∮",
        "\\sum" to "∑",
        "\\prod" to "∏",
        "\\coprod" to "∐",

        // Sets
        "\\in" to "∈",
        "\\notin" to "∉",
        "\\ni" to "∋",
        "\\notni" to "∌",
        "\\subset" to "⊂",
        "\\supset" to "⊃",
        "\\subseteq" to "⊆",
        "\\supseteq" to "⊇",
        "\\subsetneq" to "⊊",
        "\\supsetneq" to "⊋",
        "\\cup" to "∪",
        "\\cap" to "∩",
        "\\bigcup" to "⋃",
        "\\bigcap" to "⋂",
        "\\setminus" to "∖",
        "\\emptyset" to "∅",

        // Logic
        "\\forall" to "∀",
        "\\exists" to "∃",
        "\\nexists" to "∄",
        "\\neg" to "¬",
        "\\lnot" to "¬",
        "\\land" to "∧",
        "\\lor" to "∨",
        "\\wedge" to "∧",
        "\\vee" to "∨",

        // Number / algebra symbols
        "\\aleph" to "ℵ",
        "\\hbar" to "ℏ",
        "\\Re" to "ℜ",
        "\\Im" to "ℑ",

        // Geometry & Units
        "\\degree" to "°",
        "\\angle" to "∠",
        "\\triangle" to "△",
        "\\square" to "□",
        "\\perp" to "⊥",
        "\\parallel" to "∥",
        "\\circ" to "°",

        // Chemistry / science
        "\\rightleftharpoons" to "⇌",
        "\\leftrightharpoons" to "⇋",
        "\\uparrow" to "↑",
        "\\downarrow" to "↓",
        "\\updownarrow" to "↕",

        // Dots
        "\\dots" to "…",
        "\\ldots" to "…",
        "\\cdots" to "⋯",
        "\\vdots" to "⋮",
        "\\ddots" to "⋱",

        // Spacing
        "\\quad" to "  ",
        "\\qquad" to "    ",
        "\\," to " ",
        "\\;" to " ",
        "\\:" to " ",
        "\\!" to "",

        // Miscellaneous
        "\\therefore" to "∴",
        "\\because" to "∵",
        "\\checkmark" to "✓",
        "\\copyright" to "©",
        "\\dagger" to "†",
        "\\ddagger" to "‡"
    )

    // Single unified map of all symbols for O(1) lookup
    private val ALL_SYMBOL_MAP: Map<String, String> = (MATH_OPERATORS + GREEK_SYMBOLS)

    // Single precompiled regex to match any LaTeX command in one pass
    private val COMMAND_REGEX = Regex("""\\[A-Za-z]+|\\[,;!:]""")

    private val NUMBER_SETS = mapOf(
        "R" to "ℝ",
        "N" to "ℕ",
        "Z" to "ℤ",
        "Q" to "ℚ",
        "C" to "ℂ",
        "H" to "ℍ",
        "P" to "ℙ"
    )

    private fun replaceStructuredLatex(expr: String): String {
        var result = expr

        // Group B: Binomial coefficient: \binom{n}{r} -> (n choose r)
        result = result.replace(Regex("""\\binom\{([^{}]+)\}\{([^{}]+)\}""")) {
            "(${it.groupValues[1]} choose ${it.groupValues[2]})"
        }

        // Group C: Vectors and accents (Unicode combining arrow \u20D7 must come AFTER the character)
        result = result.replace(Regex("""\\vec\{([^{}]+)\}""")) {
            "${it.groupValues[1]}\u20D7"
        }
        result = result.replace(Regex("""\\bar\{([^{}]+)\}""")) {
            "${it.groupValues[1]}̄"
        }
        result = result.replace(Regex("""\\hat\{([^{}]+)\}""")) {
            "${it.groupValues[1]}̂"
        }
        result = result.replace(Regex("""\\dot\{([^{}]+)\}""")) {
            "${it.groupValues[1]}̇"
        }

        // Single-character unbraced forms: \vec x, \hat x, \bar x, \dot x
        result = result.replace(Regex("""\\vec\s+([A-Za-z0-9])""")) {
            "${it.groupValues[1]}\u20D7"
        }
        result = result.replace(Regex("""\\bar\s+([A-Za-z0-9])""")) {
            "${it.groupValues[1]}̄"
        }
        result = result.replace(Regex("""\\hat\s+([A-Za-z0-9])""")) {
            "${it.groupValues[1]}̂"
        }
        result = result.replace(Regex("""\\dot\s+([A-Za-z0-9])""")) {
            "${it.groupValues[1]}̇"
        }

        // Group D: Number sets \mathbb{R} -> ℝ
        result = result.replace(Regex("""\\mathbb\{([A-Za-z])\}""")) {
            NUMBER_SETS[it.groupValues[1]] ?: it.groupValues[1]
        }

        // Group E: Chemistry \ce{...} (supports nested braces like \ce{SO4^{2-}})
        result = result.replace(Regex("""\\ce\{((?:[^{}]+|\{[^{}]*\})*)\}""")) {
            formatChemistry(it.groupValues[1])
        }

        // Group F: Matrix environments
        result = replaceLatexEnvironment(result, "matrix") { rows ->
            rows.joinToString(" ; ") { row -> row.joinToString("  ") }
        }
        result = replaceLatexEnvironment(result, "pmatrix") { rows ->
            "( " + rows.joinToString(" ; ") { row -> row.joinToString("  ") } + " )"
        }
        result = replaceLatexEnvironment(result, "bmatrix") { rows ->
            "[ " + rows.joinToString(" ; ") { row -> row.joinToString("  ") } + " ]"
        }
        result = replaceLatexEnvironment(result, "cases") { rows ->
            "{ " + rows.joinToString(" ; ") { row -> row.joinToString("  ") }
        }
        result = replaceLatexEnvironment(result, "aligned") { rows ->
            rows.joinToString("\n") { row -> row.joinToString("  ") }
        }
        result = replaceLatexEnvironment(result, "array") { rows ->
            rows.joinToString("\n") { row -> row.joinToString("    ") }
        }

        return result
    }

    private fun replaceLatexEnvironment(
        text: String,
        environment: String,
        formatter: (List<List<String>>) -> String
    ): String {
        val pattern = Regex(
            """\\begin\{$environment\}(?:\{[^}]*\})?([\s\S]*?)\\end\{$environment\}"""
        )

        return text.replace(pattern) { match ->
            val rawBody = match.groupValues[1].trim()
            // Normalize dense delimiters like 1\\1\\0 or 1\1\0, single trailing backslash at line end, and array spacing tags
            val normalizedBody = rawBody
                .replace(Regex("""\\\[\s*\d+\s*(?:pt|em|ex|mm)?\s*\]"""), "")
                .replace(Regex("""(?m)\\\s*$"""), "\\\\")
                .replace(Regex("""(?<=\d)\\(?=\d)"""), "\\\\")
                .replace(Regex("""(?<=\d)\\\\(?=\d)"""), " \\\\ ")

            // Split into rows by \\ or \cr, or newlines if no \\ is present
            val rowStrings = if (normalizedBody.contains("\\\\") || normalizedBody.contains("\\cr")) {
                normalizedBody.split(Regex("""\\\\|\\cr\b"""))
            } else {
                normalizedBody.lines()
            }

            val rows = rowStrings
                .map { rowStr ->
                    rowStr.trim()
                        .split("&")
                        .map { it.trim().replace(Regex("""\s+"""), " ") }
                        .filter { it.isNotEmpty() }
                }
                .filter { it.isNotEmpty() }

            formatter(rows)
        }
    }

    /**
     * Formats chemical formulas (supporting mhchem style without complex external dependencies).
     * Handles: H2O -> H₂O, SO4^2- -> SO₄²⁻, CaCO3 -> CaCO₃, Fe^{3+} -> Fe³⁺, arrows ->, <->.
     */
    private fun formatChemistry(formula: String): String {
        var result = formula.trim()

        // Explicit subscripts: H_2, SO_4, O_{2}
        result = result.replace(Regex("""_\{([^{}]+)\}""")) {
            "<sub>${it.groupValues[1]}</sub>"
        }
        result = result.replace(Regex("""_([0-9]+)""")) {
            "<sub>${it.groupValues[1]}</sub>"
        }

        // Implicit stoichiometry in chemical elements: H2O -> H₂O, CO2 -> CO₂, CaCO3 -> CaCO₃
        // Chemical element symbols start with capital letter, or closing bracket ) or ]
        result = result.replace(Regex("""([A-Z][a-z]?|[)\]])([0-9]+)""")) {
            "${it.groupValues[1]}<sub>${it.groupValues[2]}</sub>"
        }

        // Braced charges: SO_4^{2-}, Fe^{3+}
        result = result.replace(Regex("""\^\{([^{}]+)\}""")) {
            "<sup>${it.groupValues[1]}</sup>"
        }

        // Unbraced charges: H^+, SO_4^2-, Fe^3+
        result = result.replace(Regex("""\^([0-9+\-]+)""")) {
            "<sup>${it.groupValues[1]}</sup>"
        }

        // Chemical arrows
        result = result
            .replace("<=>", "⇌")
            .replace("<->", "⇌")
            .replace("->", "→")
            .replace("<-", "←")

        return result
    }

    /**
     * Checks if a string contains any LaTeX math delimiters or commands (both explicit and bare).
     */
    fun hasLatex(text: String): Boolean {
        if (text.isBlank()) return false

        try {
            // Explicit LaTeX delimiters: $...$, $$...$$, \(...\), \[...\]
            if (text.contains("$") ||
                text.contains("\\(") ||
                text.contains("\\[")
            ) {
                return true
            }

            // Text wrappers and math constructors: \text{...}, \frac{...}{...}, \sqrt{...}
            val commonMathCommandRegex = Regex(
                """\\(?:text|mathrm|operatorname|mathbf|mathit|textbf|textit|frac|dfrac|sqrt|binom|vec|bar|hat|dot|mathbb|ce|begin|end|degree|circ|displaystyle|displaytext|boxed|boldsymbol|array)\b"""
            )
            if (commonMathCommandRegex.containsMatchIn(text)) {
                return true
            }

            if (Regex("""(?m)^[ \t]*\[[ \t]*$""").containsMatchIn(text) && text.contains("\\")) {
                return true
            }

            // Greek symbols or Math operators
            val commandRegex = Regex("""\\[A-Za-z]+|\\[,;!:]""")
            if (commandRegex.findAll(text).any { match -> ALL_SYMBOL_MAP.containsKey(match.value) }) {
                return true
            }

            // Subscript/superscript syntax (e.g., x^2, 10^-3, H_2O, 5^\circ)
            val subSuperRegex = Regex("""(?:[A-Za-z0-9)\]\}])(?:\^|_)(?:\{[^{}]+\}|[A-Za-z0-9+\-]+|\\[A-Za-z]+)""")
            if (subSuperRegex.containsMatchIn(text)) {
                return true
            }
        } catch (_: Throwable) {
            return false
        }

        return false
    }

    /**
     * Checks if a string contains bare LaTeX commands or math syntax outside of delimiters.
     */
    fun hasBareLatex(text: String): Boolean {
        return hasLatex(text)
    }

    /**
     * Step 1: Segments a text into plain text and math expressions.
     * Supports:
     * - Display math: $$...$$ and \[...\]
     * - Inline math: $...$ and \(...\)
     */
    fun segmentLatex(text: String): List<MathSegment> {
        if (text.isEmpty()) return emptyList()
        if (!hasLatex(text)) return listOf(MathSegment(text, isMath = false))

        val segments = mutableListOf<MathSegment>()
        val pattern = Regex("""(\$\$(.+?)\$\$|\\\[(.+?)\\\]|\$(.+?)\$|\\\((.+?)\\\))""", RegexOption.DOT_MATCHES_ALL)

        var lastIndex = 0
        try {
            for (match in pattern.findAll(text)) {
                val start = match.range.first
                val end = match.range.last + 1

                if (start > lastIndex) {
                    segments.add(MathSegment(text.substring(lastIndex, start), isMath = false))
                }

                val rawMatch = match.value
                val (formulaContent, isDisplay) = when {
                    rawMatch.startsWith("$$") -> rawMatch.removeSurrounding("$$") to true
                    rawMatch.startsWith("\\[") -> rawMatch.removeSurrounding("\\[", "\\]") to true
                    rawMatch.startsWith("\\(") -> rawMatch.removeSurrounding("\\(", "\\)") to false
                    rawMatch.startsWith("$") -> rawMatch.removeSurrounding("$") to false
                    else -> rawMatch to false
                }

                segments.add(MathSegment(formulaContent.trim(), isMath = true, isDisplay = isDisplay))
                lastIndex = end
            }

            if (lastIndex < text.length) {
                segments.add(MathSegment(text.substring(lastIndex), isMath = false))
            }
        } catch (_: Throwable) {
            return listOf(MathSegment(text, isMath = false))
        }

        return segments
    }

    private fun unwrapCommandWithBraces(
        text: String,
        command: String,
        transform: (String) -> String
    ): String {
        var result = text
        val prefix = "\\$command"
        var idx = result.indexOf(prefix)
        var guard = 0
        while (idx != -1 && guard < 50) {
            guard++
            var braceStart = idx + prefix.length
            while (braceStart < result.length && result[braceStart].isWhitespace()) {
                braceStart++
            }
            if (braceStart < result.length && result[braceStart] == '{') {
                var depth = 1
                var cur = braceStart + 1
                while (cur < result.length && depth > 0) {
                    when (result[cur]) {
                        '{' -> depth++
                        '}' -> depth--
                    }
                    if (depth > 0) cur++
                }
                if (depth == 0) {
                    val inner = result.substring(braceStart + 1, cur)
                    val replacement = transform(inner)
                    result = result.substring(0, idx) + replacement + result.substring(cur + 1)
                    idx = result.indexOf(prefix)
                    continue
                }
            }
            idx = result.indexOf(prefix, idx + prefix.length)
        }
        return result
    }

    private fun formatFractionPart(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        if (trimmed.startsWith("(") && trimmed.endsWith(")")) return trimmed

        val isCompound = trimmed.contains(" ") ||
                trimmed.contains("+") ||
                trimmed.contains("-") ||
                trimmed.contains("/") ||
                trimmed.contains("*") ||
                trimmed.contains("×") ||
                trimmed.contains("\\times") ||
                trimmed.contains("·") ||
                trimmed.contains("\\cdot") ||
                trimmed.contains("÷") ||
                trimmed.contains("\\div")

        return if (isCompound) "($trimmed)" else trimmed
    }

    private fun replaceFractionsWithBalancedBraces(text: String): String {
        var expr = text
        var loop = 0
        while ((expr.contains("\\frac") || expr.contains("\\dfrac")) && loop < 15) {
            loop++
            var changed = false
            for (cmd in listOf("\\frac", "\\dfrac")) {
                var idx = expr.indexOf(cmd)
                while (idx != -1) {
                    var numBraceStart = idx + cmd.length
                    while (numBraceStart < expr.length && expr[numBraceStart].isWhitespace()) {
                        numBraceStart++
                    }
                    if (numBraceStart < expr.length && expr[numBraceStart] == '{') {
                        var depth = 1
                        var numCur = numBraceStart + 1
                        while (numCur < expr.length && depth > 0) {
                            when (expr[numCur]) {
                                '{' -> depth++
                                '}' -> depth--
                            }
                            if (depth > 0) numCur++
                        }
                        if (depth == 0) {
                            val numContent = expr.substring(numBraceStart + 1, numCur).trim()
                            var denBraceStart = numCur + 1
                            while (denBraceStart < expr.length && expr[denBraceStart].isWhitespace()) {
                                denBraceStart++
                            }
                            if (denBraceStart < expr.length && expr[denBraceStart] == '{') {
                                depth = 1
                                var denCur = denBraceStart + 1
                                while (denCur < expr.length && depth > 0) {
                                    when (expr[denCur]) {
                                        '{' -> depth++
                                        '}' -> depth--
                                    }
                                    if (depth > 0) denCur++
                                }
                                if (depth == 0) {
                                    val denContent = expr.substring(denBraceStart + 1, denCur).trim()
                                    val cleanNum = formatFractionPart(numContent)
                                    val cleanDen = formatFractionPart(denContent)
                                    val replacement = "$cleanNum / $cleanDen"
                                    expr = expr.substring(0, idx) + replacement + expr.substring(denCur + 1)
                                    changed = true
                                    break
                                }
                            }
                        }
                    }
                    idx = expr.indexOf(cmd, idx + cmd.length)
                }
                if (changed) break
            }
            if (!changed) {
                val singleReplaced = expr.replace(Regex("""\\(?:frac|dfrac)\s+([A-Za-z0-9])\s+([A-Za-z0-9])""")) {
                    "${it.groupValues[1]} / ${it.groupValues[2]}"
                }
                if (singleReplaced != expr) {
                    expr = singleReplaced
                } else {
                    val unbracedReplaced = expr.replace(Regex("""\\(?:frac|dfrac)\s*([0-9])\s*([0-9])""")) {
                        "${it.groupValues[1]} / ${it.groupValues[2]}"
                    }
                    if (unbracedReplaced != expr) {
                        expr = unbracedReplaced
                    } else {
                        break
                    }
                }
            }
        }
        return expr
    }

    /**
     * Step 2: Formats a LaTeX mathematical expression into unicode/HTML rich text tags.
     */
    fun formatMathExpression(latex: String): String {
        try {
            var expr = latex.trim().replace("\\[", "").replace("\\]", "")

            // Handle structured environments first
            expr = replaceStructuredLatex(expr)

            // 0. Remove style switches and format boxes (with balanced brace support)
            expr = expr.replace(Regex("""\\(?:displaystyle|textstyle|scriptstyle|scriptscriptstyle|displaytext)\s*"""), "")
            expr = expr.replace(Regex("""\\rm\s*"""), "")
            expr = unwrapCommandWithBraces(expr, "boxed") { "[ $it ]" }

            // 1. Text wrapping commands: \text{...}, \mathrm{...}, \operatorname{...}, \displaytext{...}
            expr = unwrapCommandWithBraces(expr, "text") { it }
            expr = unwrapCommandWithBraces(expr, "mathrm") { it }
            expr = unwrapCommandWithBraces(expr, "displaytext") { it }
            expr = unwrapCommandWithBraces(expr, "operatorname") { it }
            expr = unwrapCommandWithBraces(expr, "mathbf") { "<b>$it</b>" }
            expr = unwrapCommandWithBraces(expr, "textbf") { "<b>$it</b>" }
            expr = unwrapCommandWithBraces(expr, "boldsymbol") { "<b>$it</b>" }
            expr = unwrapCommandWithBraces(expr, "mathit") { "<i>$it</i>" }
            expr = unwrapCommandWithBraces(expr, "textit") { "<i>$it</i>" }

            // 2. Fractions: \frac{a}{b} -> a / b (supporting nested balanced braces)
            expr = replaceFractionsWithBalancedBraces(expr)

            // 3. Roots: \sqrt[n]{x} -> ⁿ√(x), \sqrt{x} -> √(x)
            expr = expr.replace(Regex("""\\sqrt\s*\[([^\]]+)\]\s*\{([^{}]+)\}""")) {
                "<sup>${it.groupValues[1]}</sup>√(${it.groupValues[2]})"
            }
            expr = expr.replace(Regex("""\\sqrt\s*\{([^{}]+)\}""")) {
                "√(${it.groupValues[1]})"
            }
            expr = expr.replace(Regex("""\\sqrt\s+([A-Za-z0-9])""")) {
                "√(${it.groupValues[1]})"
            }

            // 4. Degree notations: ^\circ, ^{\circ}, ^\degree -> °
            expr = expr.replace(Regex("""\^\{?\\(?:circ|degree)\}?"""), "°")

            // 5. Braced Superscripts & Subscripts
            expr = expr.replace(Regex("""\^\{([^{}]+)\}""")) {
                "<sup>${it.groupValues[1]}</sup>"
            }
            expr = expr.replace(Regex("""_\{([^{}]+)\}""")) {
                "<sub>${it.groupValues[1]}</sub>"
            }

            // 6. Greek symbols & Math operators (single-pass O(N) replacement via precompiled regex & map)
            expr = COMMAND_REGEX.replace(expr) { match ->
                ALL_SYMBOL_MAP[match.value] ?: match.value
            }

            // 7. Unbraced Superscripts & Subscripts (supporting Latin, Greek, and Prime ′ / ' characters)
            expr = expr.replace(Regex("""([A-Za-z0-9)\]\}\'\u2032\p{IsGreek}\u0370-\u03FF])\^([+-]?[0-9a-zA-Z\p{IsGreek}\u0370-\u03FF]+)""")) {
                "${it.groupValues[1]}<sup>${it.groupValues[2]}</sup>"
            }
            expr = expr.replace(Regex("""([A-Za-z0-9)\]\}\'\u2032\p{IsGreek}\u0370-\u03FF])_([0-9]+|[a-zA-Z\p{IsGreek}\u0370-\u03FF]+\b)""")) {
                "${it.groupValues[1]}<sub>${it.groupValues[2]}</sub>"
            }

            // 8. Spacing & bracket cleanup
            expr = expr.replace(Regex("""\\(?:quad|qquad|qqud)\b"""), "  ")
            expr = expr.replace(Regex("""\\[,;! ]"""), " ")
            expr = expr.replace(Regex("""\\left\s*([(\[{|.])?""")) { it.groupValues[1].takeIf { g -> g != "." } ?: "" }
            expr = expr.replace(Regex("""\\right\s*([)\]}|.])?""")) { it.groupValues[1].takeIf { g -> g != "." } ?: "" }
            expr = expr.replace(Regex("""\\left|\\right"""), "")
            expr = expr.replace(Regex("""\\\{"""), "{")
            expr = expr.replace(Regex("""\\\}"""), "}")

            return expr
        } catch (_: Throwable) {
            return latex
        }
    }

    /**
     * Formats bare LaTeX expressions safely without requiring explicit $ delimiters.
     */
    fun formatBareLatex(text: String): String {
        return formatMathExpression(text)
    }

    /**
     * Converts a full string (mixing normal text, delimited LaTeX, and bare LaTeX)
     * into a formatted string ready for RichTextHelper.buildRichText.
     */
    fun toRichTextString(text: String): String {
        if (!hasLatex(text)) return text

        try {
            val normalized = text
                // Normalize unescaped standalone [ ... ] containing LaTeX commands to \[ ... \]
                .replace(Regex("""(?m)^[ \t]*\[[ \t]*\r?\n([\s\S]*?)\r?\n[ \t]*\][ \t]*$""")) { match ->
                    val inner = match.groupValues[1]
                    if (inner.contains("\\") || inner.contains("_") || inner.contains("^") || inner.contains("&")) {
                        "\\[\n$inner\n\\]"
                    } else {
                        match.value
                    }
                }
                // Normalize unescaped parenthetical inline LaTeX like (\gamma>0), (A_d), (A_{\rm cl}T)
                // Only match if the opening parenthesis is NOT already escaped by a backslash
                .replace(Regex("""(?<!\\)\(([^()\n]*\\[A-Za-z]+[^()\n]*)\)""")) { match ->
                    "\\(${match.groupValues[1]}\\)"
                }

            val segments = segmentLatex(normalized)
            val sb = StringBuilder()
            for (seg in segments) {
                if (seg.isMath) {
                    val formatted = formatMathExpression(seg.content)
                    if (seg.isDisplay) {
                        sb.append("\n").append(formatted).append("\n")
                    } else {
                        sb.append(formatted)
                    }
                } else {
                    val cleanSegment = seg.content
                        .replace(Regex("""(?m)^\s*\\\[\s*$\r?\n?"""), "")
                        .replace(Regex("""(?m)^\s*\\\]\s*$\r?\n?"""), "")
                        .replace("\\[", "")
                        .replace("\\]", "")
                        .replace("\\(", "")
                        .replace("\\)", "")
                    if (cleanSegment.isNotEmpty()) {
                        if (hasBareLatex(cleanSegment)) {
                            sb.append(formatBareLatex(cleanSegment))
                        } else {
                            sb.append(cleanSegment)
                        }
                    }
                }
            }
            return sb.toString().replace(Regex("""\n{3,}"""), "\n\n").trim()
        } catch (_: Throwable) {
            return text
        }
    }

    /**
     * High-level entry point: builds an AnnotatedString from any string containing LaTeX.
     */
    fun buildLatexAnnotatedString(
        text: String,
        baseColor: Color = Color.Unspecified,
        baseFontSize: TextUnit = 14.sp,
        isBold: Boolean = false
    ): AnnotatedString {
        return try {
            val prepared = toRichTextString(text)
            RichTextHelper.buildRichText(
                text = prepared,
                baseColor = baseColor,
                baseFontSize = baseFontSize,
                isBold = isBold
            )
        } catch (_: Throwable) {
            RichTextHelper.buildRichText(
                text = text,
                baseColor = baseColor,
                baseFontSize = baseFontSize,
                isBold = isBold
            )
        }
    }

    /**
     * Central hook: scans an McqQuestion and pre-processes all its text fields
     * (question, options A-D, explanation, formula) for raw and bare LaTeX.
     */
    fun processQuestion(question: com.example.model.McqQuestion): com.example.model.McqQuestion {
        return try {
            question.copy(
                question = toRichTextString(question.question),
                options = question.options.copy(
                    a = toRichTextString(question.options.a),
                    b = toRichTextString(question.options.b),
                    c = toRichTextString(question.options.c),
                    d = toRichTextString(question.options.d)
                ),
                explanation = question.explanation?.let { toRichTextString(it) },
                formula = question.formula?.let { toRichTextString(it) }
            )
        } catch (_: Throwable) {
            question
        }
    }
}

/**
 * Central hook: scans an McqQuestion for raw LaTeX patterns and transforms
 * them into KaTeX-compatible strings before they are rendered.
 */
fun useLatexProcessor(question: com.example.model.McqQuestion): com.example.model.McqQuestion {
    return LatexHelper.processQuestion(question)
}

/**
 * Text overload for useLatexProcessor.
 */
fun useLatexProcessor(text: String): String {
    return LatexHelper.toRichTextString(text)
}

/**
 * Compose hook version of useLatexProcessor with memoization.
 */
@androidx.compose.runtime.Composable
fun rememberProcessedQuestion(question: com.example.model.McqQuestion): com.example.model.McqQuestion {
    return androidx.compose.runtime.remember(question) {
        useLatexProcessor(question)
    }
}
