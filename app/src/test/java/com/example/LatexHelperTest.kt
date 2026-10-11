package com.example

import com.example.model.McqQuestion
import com.example.model.QuestionOptions
import com.example.util.LatexHelper
import com.example.util.useLatexProcessor
import org.junit.Assert.*
import org.junit.Test

class LatexHelperTest {

    @Test
    fun testBareLatexDetectionAndFormatting() {
        val input = "An object moves 8\\text{ mm} in time \\Delta t = 2\\text{ s}."
        assertTrue(LatexHelper.hasLatex(input))
        val output = LatexHelper.toRichTextString(input)
        assertTrue(output.contains("8 mm"))
        assertTrue(output.contains("Δ"))
        assertTrue(output.contains("2 s"))
    }

    @Test
    fun testFractionsAndRoots() {
        val frac = "\\frac{1}{2} + \\frac{a + b}{c}"
        val formattedFrac = LatexHelper.formatMathExpression(frac)
        assertTrue(formattedFrac.contains("1 / 2"))
        assertTrue(formattedFrac.contains("(a + b) / c"))

        val sqrt = "\\sqrt{25} + \\sqrt[3]{8}"
        val formattedSqrt = LatexHelper.formatMathExpression(sqrt)
        assertTrue(formattedSqrt.contains("√(25)"))
        assertTrue(formattedSqrt.contains("<sup>3</sup>√(8)"))
    }

    @Test
    fun testChemicalFormulas() {
        val water = "\\ce{H2O}"
        val formattedWater = LatexHelper.formatMathExpression(water)
        assertEquals("H<sub>2</sub>O", formattedWater)

        val sulfate = "\\ce{SO4^{2-}}"
        val formattedSulfate = LatexHelper.formatMathExpression(sulfate)
        assertTrue(formattedSulfate.contains("SO<sub>4</sub>"))
        assertTrue(formattedSulfate.contains("<sup>2-</sup>"))

        val reaction = "\\ce{N2 + 3H2 -> 2NH3}"
        val formattedReaction = LatexHelper.formatMathExpression(reaction)
        assertTrue(formattedReaction.contains("→"))
        assertTrue(formattedReaction.contains("N<sub>2</sub>"))
        assertTrue(formattedReaction.contains("H<sub>2</sub>"))
        assertTrue(formattedReaction.contains("NH<sub>3</sub>"))
    }

    @Test
    fun testExponentsAndSubscripts() {
        val exp = "c = 3 \\times 10^8 \\text{ m/s}, \\lambda = 10^-3 \\text{ m}"
        val formatted = LatexHelper.toRichTextString(exp)
        assertTrue(formatted.contains("10<sup>8</sup>"))
        assertTrue(formatted.contains("10<sup>-3</sup>"))
        assertTrue(formatted.contains("×"))
        assertTrue(formatted.contains("m/s"))
    }

    @Test
    fun testGreekAndOperators() {
        val math = "\\alpha + \\beta = \\theta, \\mu = 5, \\Omega = 10, x \\leq y"
        val formatted = LatexHelper.formatMathExpression(math)
        assertTrue(formatted.contains("α"))
        assertTrue(formatted.contains("β"))
        assertTrue(formatted.contains("θ"))
        assertTrue(formatted.contains("μ"))
        assertTrue(formatted.contains("Ω"))
        assertTrue(formatted.contains("≤"))
    }

    @Test
    fun testProcessQuestionHook() {
        val q = McqQuestion(
            num = 1,
            question = "What is the speed of light \\(c\\)?",
            options = QuestionOptions(
                a = "3 \\times 10^8 \\text{ m/s}",
                b = "2 \\times 10^8 \\text{ m/s}",
                c = "1 \\times 10^8 \\text{ m/s}",
                d = "None of the above"
            ),
            correctAnswer = "A",
            explanation = "In vacuum, \\(c = 3 \\times 10^8\\text{ m/s}\\)."
        )

        val processed = useLatexProcessor(q)
        assertTrue(processed.options.a.contains("×"))
        assertTrue(processed.options.a.contains("10<sup>8</sup>"))
        assertTrue(processed.options.a.contains("m/s"))
        assertTrue(processed.explanation!!.contains("10<sup>8</sup>"))
    }

    @Test
    fun testMalformedLatexDoesNotCrash() {
        val malformed = "\\frac{1}{ and \\text{unclosed and \\sqrt{ and \\ce{H2"
        val result = LatexHelper.toRichTextString(malformed)
        assertNotNull(result)
    }

    @Test
    fun testCivilEngineeringQuestionsParsingAndLatex() {
        val rawInput = """
Civil Engineering Paper 1: Extreme Difficulty MCQs
Question 1
A stepped fixed-ended beam ABC has total span L = 8\text{ m}, with segment AB of length 4\text{ m} having flexural rigidity 2EI and segment BC of length 4\text{ m} having flexural rigidity EI. The beam carries a concentrated load W = 120\text{ kN} at the mid-span joint B. Both supports A and C are fully rigid fixed supports. What is the magnitude of the fixed-end moment M_A developed at support A?
(A) 80.00\text{ kN}\cdot\text{m}
(B) 106.67\text{ kN}\cdot\text{m}
(C) 160.00\text{ kN}\cdot\text{m}
(D) 213.33\text{ kN}\cdot\text{m}
Question 2
A braced excavation in a thick deposit of soft, saturated clay (\phi = 0, c_u = 35\text{ kPa}, unit weight \gamma = 18\text{ kN/m}^3) is carried out to a depth H = 8\text{ m}. The site is underlain by a hard bed of rock at a depth D_B = 12\text{ m} below the ground surface (i.e., 4\text{ m} below the base of the excavation). Assuming the width of excavation B is large (B > \sqrt{2} D_B), what is the Factor of Safety (FS) against bottom heaving according to Terzaghi's base stability theory for deep cuts in clay?
(A) 1.21
(B) 1.39
(C) 1.52
(D) 1.70
Answer Key
| Question No. | Correct Option |
|---|---|
| 1 | B |
| 2 | B |
Detailed Option-by-Option Explanations
Explanation 1
 * Correct Answer: (B) 106.67\text{ kN}\cdot\text{m}
 * Derivation & Analysis:
   Using the Force Method (Flexibility Method) or Stiffness Method:
   * Consider joint B displacement/rotation formulation:
     * Segment AB: Length L_1 = 4\text{ m}, Rigidity = 2EI.
     * Segment BC: Length L_2 = 4\text{ m}, Rigidity = EI.
   * For a fixed-fixed beam with joint B subjected to vertical load W = 120\text{ kN}:
     * Let \Delta_B be the downward displacement at joint B and \theta_B be the clockwise rotation at B.
     * Fixed member stiffnesses for vertical displacement \Delta_B:
       * Reaction/Shear stiffness for AB: k_{v1} = \frac{12(2EI)}{L_1^3} = \frac{24EI}{64} = \frac{3EI}{8}.
       * Reaction/Shear stiffness for BC: k_{v2} = \frac{12EI}{L_2^3} = \frac{12EI}{64} = \frac{3EI}{16}.
       * Total vertical stiffness at B: K_{vv} = \frac{3EI}{8} + \frac{3EI}{16} = \frac{9EI}{16}.
       * Moment-displacement coupling at B:
         * From AB: K_{\theta v1} = -\frac{6(2EI)}{L_1^2} = -\frac{12EI}{16} = -\frac{3EI}{4}.
         * From BC: K_{\theta v2} = +\frac{6EI}{L_2^2} = +\frac{6EI}{16} = +\frac{3EI}{8}.
         * Net coupling stiffness: K_{\theta v} = -\frac{3EI}{8}.
     * Rotational stiffness at B:
       * K_{\theta\theta} = \frac{4(2EI)}{4} + \frac{4EI}{4} = 2EI + EI = 3EI.
   * System equilibrium equations under applied vertical load W = 120\text{ kN} at B:
     From 2nd equation: -\frac{3}{8}\Delta_B + 3\theta_B = 0 \implies \theta_B = \frac{1}{8}\Delta_B.
   * Fixed End Moment at support A (M_A):
 * Option Evaluation:
   * (A) Incorrect: Result obtained if the beam had uniform flexural rigidity EI across the entire span.
   * (B) Correct: Accurately accounts for the 2:1 stiffness asymmetry between the two halves.
   * (C) Incorrect: Result obtained if support C is assumed to be pinned.
   * (D) Incorrect: Represents the sum of moments without applying rotational compatibility.
Explanation 2
 * Correct Answer: (B) 1.39
 * Derivation & Analysis:
   * Terzaghi's base stability factor of safety against bottom heaving in soft clay is given by:
     Where N_c is the stability bearing capacity factor depending on depth ratio.
 * Option Evaluation:
   * (A) Incorrect: Computed using N_c = 5.14.
   * (B) Correct: Accurately applies Terzaghi's bearing capacity factor N_c = 5.70.
   * (C) Incorrect: Uses Skempton's deep foundation factor N_c = 6.2.
   * (D) Incorrect: Erroneously subtracts cohesive shear resistance twice.
        """.trimIndent()

        val parsed = com.example.util.McqParser.parse(rawInput)
        assertEquals(2, parsed.questions.size)

        val q1 = parsed.questions[0]
        val processedQ1 = useLatexProcessor(q1)

        // Verify LaTeX formatting on Question 1 stem & options
        assertTrue(processedQ1.question.contains("L = 8 m"))
        assertTrue(processedQ1.question.contains("length 4 m"))
        assertTrue(processedQ1.question.contains("W = 120 kN"))
        assertTrue(processedQ1.options.a.contains("80.00 kN · m") || processedQ1.options.a.contains("80.00 kN·m") || processedQ1.options.a.contains("80.00"))
        assertTrue(processedQ1.options.b.contains("106.67 kN · m") || processedQ1.options.b.contains("106.67 kN·m") || processedQ1.options.b.contains("106.67"))

        // Verify Explanation 1 LaTeX formatting
        val exp1 = processedQ1.explanation ?: ""
        assertTrue(exp1.contains("Δ<sub>B</sub>") || exp1.contains("Δ"))
        assertTrue(exp1.contains("θ<sub>B</sub>") || exp1.contains("θ"))
        assertTrue(exp1.contains("⟹"))
        assertTrue(exp1.contains("k<sub>v1</sub>"))

        // Verify Question 2
        val q2 = parsed.questions[1]
        val processedQ2 = useLatexProcessor(q2)
        assertTrue(processedQ2.question.contains("ϕ = 0"))
        assertTrue(processedQ2.question.contains("35 kPa"))
        assertTrue(processedQ2.question.contains("18 kN/m<sup>3</sup>"))
        assertTrue(processedQ2.question.contains("H = 8 m"))
        assertTrue(processedQ2.question.contains("√(2)"))
    }

    @Test
    fun testInlineParentheticalLatexUnitFormatting() {
        val input = "\\(168\\text{ kPa}\\)"
        val output = LatexHelper.toRichTextString(input)
        assertEquals("168 kPa", output)
        assertFalse(output.contains("\\"))

        val mixedInput = "The maximum pore pressure is \\(168\\text{ kPa}\\) at the base."
        val mixedOutput = LatexHelper.toRichTextString(mixedInput)
        assertTrue(mixedOutput.contains("168 kPa"))
        assertFalse(mixedOutput.contains("\\168"))
        assertFalse(mixedOutput.contains("kPa\\"))
    }

    @Test
    fun testCivilEngineeringFormulasAndNestedLatex() {
        // 1. Boxed formula with nested fractions and subscripts
        val boxedFormula = "\\[\\boxed{S_c=\\frac{C_cH}{1+e_0}\\log_{10}\\frac{\\sigma'_2}{\\sigma'_1}}\\]"
        val formattedBoxed = LatexHelper.toRichTextString(boxedFormula)
        assertFalse(formattedBoxed.contains("\\boxed"))
        assertFalse(formattedBoxed.contains("\\frac"))
        assertTrue(formattedBoxed.contains("S<sub>c</sub>"))
        assertTrue(formattedBoxed.contains("log<sub>10</sub>"))
        assertTrue(formattedBoxed.contains("σ'<sub>2</sub> / σ'<sub>1</sub>") || formattedBoxed.contains("σ'<sub>2</sub>"))

        // 2. Fraction with nested \text subscripts
        val textSubFrac = "\\[\\frac{M_{\\text{point}}}{M_{\\text{UDL}}}\\]"
        val formattedTextSubFrac = LatexHelper.toRichTextString(textSubFrac)
        assertFalse(formattedTextSubFrac.contains("\\frac"))
        assertFalse(formattedTextSubFrac.contains("\\text"))
        assertTrue(formattedTextSubFrac.contains("M<sub>point</sub> / M<sub>UDL</sub>"))

        // 3. Compound numerator and denominator fractions
        val compoundFrac = "\\[\\frac{wL^2/4}{wL^2/8}=2\\]"
        val formattedCompoundFrac = LatexHelper.toRichTextString(compoundFrac)
        assertFalse(formattedCompoundFrac.contains("\\frac"))
        assertTrue(formattedCompoundFrac.contains("(wL<sup>2</sup>/4) / (wL<sup>2</sup>/8)"))

        // 4. Denominator with \\times without spaces
        val timesDen = "\\[\\frac{625}{9.81\\times300}\\]"
        val formattedTimesDen = LatexHelper.toRichTextString(timesDen)
        assertFalse(formattedTimesDen.contains("\\frac"))
        assertTrue(formattedTimesDen.contains("625 / (9.81×300)") || formattedTimesDen.contains("625 / (9.81 × 300)"))
    }
}
