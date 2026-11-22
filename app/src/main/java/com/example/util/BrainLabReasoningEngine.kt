package com.example.util

import kotlin.random.Random

object BrainLabReasoningEngine {

    // =========================================================================
    // 1. Syllogism & Logical Validity Arena (Venn Truth-Table Verified)
    // =========================================================================

    enum class Quantifier {
        ALL,      // All S are P
        NO,       // No S is P
        SOME,     // Some S are P
        SOME_NOT  // Some S are not P
    }

    data class SyllogismStatement(
        val quantifier: Quantifier,
        val subjectIdx: Int,
        val predicateIdx: Int
    ) {
        fun format(terms: List<String>): String {
            val s = terms[subjectIdx]
            val p = terms[predicateIdx]
            return when (quantifier) {
                Quantifier.ALL -> "All $s are $p"
                Quantifier.NO -> "No $s is $p"
                Quantifier.SOME -> "Some $s are $p"
                Quantifier.SOME_NOT -> "Some $s are not $p"
            }
        }
    }

    enum class SyllogismAnswer(val label: String) {
        ONLY_I("Only Conclusion I follows"),
        ONLY_II("Only Conclusion II follows"),
        BOTH("Both I and II follow"),
        EITHER("Either I or II follows"),
        NEITHER("Neither I nor II follows")
    }

    data class SyllogismVerification(
        val conclusionIFollows: Boolean,
        val conclusionIIFollows: Boolean,
        val eitherOrValid: Boolean,
        val answer: SyllogismAnswer,
        val explanation: String
    )

    data class SyllogismPuzzle(
        val terms: List<String>,
        val premises: List<SyllogismStatement>,
        val conclusionI: SyllogismStatement,
        val conclusionII: SyllogismStatement,
        val verification: SyllogismVerification
    )

    private val TERM_GROUPS = listOf(
        listOf("Judges", "Lawyers", "Scholars", "Orators"),
        listOf("Photons", "Waves", "Particles", "Signals"),
        listOf("Sensors", "Relays", "Circuits", "Modules"),
        listOf("Novels", "Essays", "Poems", "Journals"),
        listOf("Atoms", "Ions", "Radicals", "Catalysts"),
        listOf("Matrices", "Vectors", "Tensors", "Scalars")
    )

    /**
     * Exhaustive Venn subset verification over the 16 canonical regions of 4 terms (bits 0..3).
     * Uses existential-import-free universal constraints plus explicit existential witnesses,
     * while avoiding vacuous terms by checking every named term has at least 1 allowed region.
     */
    fun verifySyllogism(
        terms: List<String>,
        premises: List<SyllogismStatement>,
        conclusionI: SyllogismStatement,
        conclusionII: SyllogismStatement
    ): SyllogismVerification? {
        val termCount = terms.size.coerceIn(3, 4)
        val regionCount = 1 shl termCount
        val allRegionsMask = (1 shl regionCount) - 1

        fun regionMatches(region: Int, stmt: SyllogismStatement, positivePredicate: Boolean): Boolean {
            val inS = ((region shr stmt.subjectIdx) and 1) == 1
            val inP = ((region shr stmt.predicateIdx) and 1) == 1
            return inS && (if (positivePredicate) inP else !inP)
        }

        fun maskFor(stmt: SyllogismStatement, positivePredicate: Boolean): Int {
            var mask = 0
            for (r in 0 until regionCount) {
                if (regionMatches(r, stmt, positivePredicate)) {
                    mask = mask or (1 shl r)
                }
            }
            return mask
        }

        // Universal premises forbid regions; existential premises require at least one inhabited region.
        var allowedMask = allRegionsMask
        val existentialReqMasks = mutableListOf<Int>()

        for (p in premises) {
            when (p.quantifier) {
                Quantifier.ALL -> allowedMask = allowedMask and maskFor(p, positivePredicate = false).inv()
                Quantifier.NO -> allowedMask = allowedMask and maskFor(p, positivePredicate = true).inv()
                Quantifier.SOME -> existentialReqMasks.add(maskFor(p, positivePredicate = true))
                Quantifier.SOME_NOT -> existentialReqMasks.add(maskFor(p, positivePredicate = false))
            }
        }

        // Ensure premises are satisfiable and every named term has at least one possible region
        for (req in existentialReqMasks) {
            if ((req and allowedMask) == 0) return null
        }
        for (tIdx in 0 until termCount) {
            var termAllowed = 0
            for (r in 0 until regionCount) {
                if (((r shr tIdx) and 1) == 1 && ((allowedMask shr r) and 1) == 1) {
                    termAllowed = termAllowed or (1 shl r)
                }
            }
            if (termAllowed == 0) return null
        }

        fun describeRegion(region: Int): String {
            val inside = mutableListOf<String>()
            val outside = mutableListOf<String>()
            for (i in 0 until termCount) {
                if (((region shr i) and 1) == 1) inside.add(terms[i]) else outside.add(terms[i])
            }
            val inStr = if (inside.isEmpty()) "None" else inside.joinToString(" ∩ ")
            val outStr = if (outside.isEmpty()) "" else " (outside ${outside.joinToString(", ")})"
            return "[$inStr$outStr]"
        }

        // Evaluate whether a conclusion necessarily holds in ALL valid Venn models satisfying the premises
        fun evaluateConclusion(conc: SyllogismStatement): Pair<Boolean, String> {
            return when (conc.quantifier) {
                Quantifier.ALL, Quantifier.NO -> {
                    val forbidMask = maskFor(conc, positivePredicate = conc.quantifier == Quantifier.NO)
                    val counterRegions = allowedMask and forbidMask
                    if (counterRegions == 0) {
                        true to "${conc.format(terms)} holds in all Venn subsets because every counterexample region is ruled out by the universal premises."
                    } else {
                        val witnessRegion = Integer.numberOfTrailingZeros(counterRegions)
                        false to "${conc.format(terms)} does NOT necessarily follow. Counterexample Venn subset ${describeRegion(witnessRegion)} satisfies all premises while violating the conclusion."
                    }
                }
                Quantifier.SOME, Quantifier.SOME_NOT -> {
                    val targetMask = maskFor(conc, positivePredicate = conc.quantifier == Quantifier.SOME)
                    val forcedByExistential = existentialReqMasks.any { req ->
                        val active = req and allowedMask
                        active != 0 && (active and targetMask.inv()) == 0
                    }
                    if (forcedByExistential) {
                        true to "${conc.format(terms)} necessarily follows because an existential premise forces at least one element inside the target Venn intersection."
                    } else {
                        // Construct an explicit counterexample witness for each existential premise outside targetMask
                        val witnessNotes = existentialReqMasks.mapNotNull { req ->
                            val safe = req and allowedMask and targetMask.inv()
                            if (safe != 0) describeRegion(Integer.numberOfTrailingZeros(safe)) else null
                        }
                        val witnessDesc = if (witnessNotes.isNotEmpty()) {
                            "Counterexample: placing witnesses in ${witnessNotes.distinct().joinToString(" and ")}"
                        } else {
                            val fallbackRegion = (allowedMask and targetMask.inv())
                            if (fallbackRegion != 0) "Counterexample: elements residing in ${describeRegion(Integer.numberOfTrailingZeros(fallbackRegion))}"
                            else "Counterexample: empty target subset"
                        }
                        false to "${conc.format(terms)} does NOT necessarily follow. $witnessDesc satisfies every premise without making this conclusion true."
                    }
                }
            }
        }

        val (iFollows, iExpl) = evaluateConclusion(conclusionI)
        val (iiFollows, iiExpl) = evaluateConclusion(conclusionII)

        // Check rigorous complementary Either-Or:
        // Two statements form a strict complementary pair across all models iff:
        // (No P is Q AND Some P are Q) or (All P are Q AND Some P are not Q), on the same (subject, predicate)
        // or symmetric No/Some (since No P is Q == No Q is P and Some P are Q == Some Q are P).
        fun isComplementaryPair(a: SyllogismStatement, b: SyllogismStatement): Boolean {
            val sameDir = a.subjectIdx == b.subjectIdx && a.predicateIdx == b.predicateIdx
            val swappedDir = a.subjectIdx == b.predicateIdx && a.predicateIdx == b.subjectIdx
            if (sameDir) {
                val pair = setOf(a.quantifier, b.quantifier)
                if (pair == setOf(Quantifier.NO, Quantifier.SOME)) return true
                if (pair == setOf(Quantifier.ALL, Quantifier.SOME_NOT)) return true
            }
            if (swappedDir) {
                val pair = setOf(a.quantifier, b.quantifier)
                if (pair == setOf(Quantifier.NO, Quantifier.SOME)) return true
            }
            return false
        }

        val eitherOr = !iFollows && !iiFollows && isComplementaryPair(conclusionI, conclusionII)
        val finalAnswer = when {
            iFollows && iiFollows -> SyllogismAnswer.BOTH
            iFollows -> SyllogismAnswer.ONLY_I
            iiFollows -> SyllogismAnswer.ONLY_II
            eitherOr -> SyllogismAnswer.EITHER
            else -> SyllogismAnswer.NEITHER
        }

        val explanation = buildString {
            append("Conclusion I: $iExpl\n")
            append("Conclusion II: $iiExpl")
            if (eitherOr) {
                append("\nEither/Or Check: Together, I and II form a strict complementary Venn partition, so in every valid model at least one must hold.")
            }
        }

        return SyllogismVerification(
            conclusionIFollows = iFollows,
            conclusionIIFollows = iiFollows,
            eitherOrValid = eitherOr,
            answer = finalAnswer,
            explanation = explanation
        )
    }

    fun generateSyllogismPuzzle(
        desiredAnswer: SyllogismAnswer? = null,
        random: Random = Random.Default
    ): SyllogismPuzzle {
        val target = desiredAnswer ?: SyllogismAnswer.entries[random.nextInt(SyllogismAnswer.entries.size)]
        val terms = TERM_GROUPS[random.nextInt(TERM_GROUPS.size)].shuffled(random)
        val qTypes = Quantifier.entries

        repeat(400) {
            val p1 = SyllogismStatement(qTypes[random.nextInt(qTypes.size)], 0, 1)
            val p2 = SyllogismStatement(qTypes[random.nextInt(qTypes.size)], 1, 2)
            val useThird = random.nextBoolean()
            val premises = if (useThird) {
                val p3 = SyllogismStatement(qTypes[random.nextInt(qTypes.size)], 2, 3)
                listOf(p1, p2, p3)
            } else {
                listOf(p1, p2)
            }

            val maxTerm = if (useThird) 3 else 2
            val (c1, c2) = if (target == SyllogismAnswer.EITHER) {
                val s = random.nextInt(maxTerm + 1)
                var p = random.nextInt(maxTerm + 1)
                if (p == s) p = (s + 1) % (maxTerm + 1)
                if (random.nextBoolean()) {
                    SyllogismStatement(Quantifier.SOME, s, p) to SyllogismStatement(Quantifier.NO, s, p)
                } else {
                    SyllogismStatement(Quantifier.ALL, s, p) to SyllogismStatement(Quantifier.SOME_NOT, s, p)
                }
            } else {
                val s1 = random.nextInt(maxTerm + 1)
                var p1c = random.nextInt(maxTerm + 1)
                if (p1c == s1) p1c = (s1 + 1) % (maxTerm + 1)
                val s2 = random.nextInt(maxTerm + 1)
                var p2c = random.nextInt(maxTerm + 1)
                if (p2c == s2) p2c = (s2 + 1) % (maxTerm + 1)
                SyllogismStatement(qTypes[random.nextInt(qTypes.size)], s1, p1c) to
                    SyllogismStatement(qTypes[random.nextInt(qTypes.size)], s2, p2c)
            }

            // Avoid trivial direct copy of a premise as conclusion
            if (c1 in premises || c2 in premises || c1 == c2) return@repeat

            val verified = verifySyllogism(terms.take(maxTerm + 1), premises, c1, c2)
            if (verified != null && verified.answer == target) {
                return SyllogismPuzzle(
                    terms = terms.take(maxTerm + 1),
                    premises = premises,
                    conclusionI = c1,
                    conclusionII = c2,
                    verification = verified
                )
            }
        }

        // Guaranteed deterministic fallback verified directly via Venn truth-table
        val fallbackTerms = listOf("Alphas", "Betas", "Gammas", "Deltas")
        val p = listOf(
            SyllogismStatement(Quantifier.ALL, 0, 1),
            SyllogismStatement(Quantifier.NO, 1, 2),
            SyllogismStatement(Quantifier.SOME, 2, 3)
        )
        val c1 = SyllogismStatement(Quantifier.NO, 0, 2)
        val c2 = SyllogismStatement(Quantifier.SOME_NOT, 3, 1)
        val ver = verifySyllogism(fallbackTerms, p, c1, c2)!!
        return SyllogismPuzzle(fallbackTerms, p, c1, c2, ver)
    }

    // =========================================================================
    // 2. Constraint Grid & Seating Mini-Puzzles (Unique Permutation Verified)
    // =========================================================================

    data class SeatingPuzzle(
        val id: String,
        val isCircular: Boolean,
        val people: List<String>,
        val clues: List<String>,
        val promptQuestion: String,
        val options: List<String>,
        val correctIndex: Int,
        val explanation: String,
        val solutionOrder: List<String>
    )

    private val NAME_POOLS = listOf(
        listOf("Aarav", "Bhavna", "Chirag", "Diya", "Eshan"),
        listOf("Kabir", "Meera", "Nikhil", "Priya", "Rohan"),
        listOf("Ananya", "Dev", "Farah", "Gaurav", "Ira")
    )

    private sealed interface SeatRule {
        fun holds(perm: IntArray, isCircular: Boolean): Boolean
        fun describe(names: List<String>, isCircular: Boolean): String
    }

    private data class FixedSeatRule(val personIdx: Int, val seatIdx: Int) : SeatRule {
        override fun holds(perm: IntArray, isCircular: Boolean): Boolean = perm[seatIdx] == personIdx
        override fun describe(names: List<String>, isCircular: Boolean): String {
            return if (isCircular) "${names[personIdx]} sits at Seat #${seatIdx + 1}."
            else "${names[personIdx]} sits at position ${seatIdx + 1} from the left."
        }
    }

    private data class LeftRightOffsetRule(val personIdx: Int, val anchorIdx: Int, val offset: Int) : SeatRule {
        override fun holds(perm: IntArray, isCircular: Boolean): Boolean {
            val posA = perm.indexOf(personIdx)
            val posB = perm.indexOf(anchorIdx)
            return if (isCircular) {
                ((posB + offset + perm.size) % perm.size) == posA
            } else {
                posB + offset == posA
            }
        }
        override fun describe(names: List<String>, isCircular: Boolean): String {
            val absOff = kotlin.math.abs(offset)
            val dir = if (offset > 0) "right" else "left"
            val ord = when (absOff) {
                1 -> "immediately"
                2 -> "2nd to the"
                else -> "${absOff}th to the"
            }
            return if (absOff == 1) {
                "${names[personIdx]} sits immediately to the $dir of ${names[anchorIdx]}."
            } else {
                "${names[personIdx]} sits $ord $dir of ${names[anchorIdx]}."
            }
        }
    }

    private data class NotAdjacentRule(val personA: Int, val personB: Int) : SeatRule {
        override fun holds(perm: IntArray, isCircular: Boolean): Boolean {
            val posA = perm.indexOf(personA)
            val posB = perm.indexOf(personB)
            val diff = kotlin.math.abs(posA - posB)
            val adjacent = if (isCircular) (diff == 1 || diff == perm.size - 1) else (diff == 1)
            return !adjacent
        }
        override fun describe(names: List<String>, isCircular: Boolean): String {
            return "${names[personA]} and ${names[personB]} do NOT sit next to each other."
        }
    }

    private data class NotAtEndsRule(val personIdx: Int) : SeatRule {
        override fun holds(perm: IntArray, isCircular: Boolean): Boolean {
            val pos = perm.indexOf(personIdx)
            return pos != 0 && pos != perm.lastIndex
        }
        override fun describe(names: List<String>, isCircular: Boolean): String {
            return "${names[personIdx]} does NOT sit at either extreme end."
        }
    }

    fun generateSeatingPuzzle(
        seatCount: Int = 4,
        isCircular: Boolean = false,
        random: Random = Random.Default
    ): SeatingPuzzle {
        val count = seatCount.coerceIn(4, 5)
        val pool = NAME_POOLS[random.nextInt(NAME_POOLS.size)]
        val names = pool.take(count)

        val allPerms = mutableListOf<IntArray>()
        fun buildPerms(current: MutableList<Int>, used: BooleanArray) {
            if (current.size == count) {
                allPerms.add(current.toIntArray())
                return
            }
            for (i in 0 until count) {
                if (!used[i]) {
                    used[i] = true
                    current.add(i)
                    buildPerms(current, used)
                    current.removeAt(current.lastIndex)
                    used[i] = false
                }
            }
        }
        buildPerms(mutableListOf(), BooleanArray(count))

        repeat(80) {
            val truth = (0 until count).shuffled(random).toIntArray()
            val candidateRules = mutableListOf<SeatRule>()

            // In circular mode, anchor Seat #1 to avoid rotational equivalence
            if (isCircular) {
                candidateRules.add(FixedSeatRule(truth[0], 0))
            } else {
                val anchorSeat = random.nextInt(count)
                candidateRules.add(FixedSeatRule(truth[anchorSeat], anchorSeat))
            }

            for (i in 0 until count) {
                for (j in 0 until count) {
                    if (i != j) {
                        val posI = truth.indexOf(i)
                        val posJ = truth.indexOf(j)
                        val rawOffset = posI - posJ
                        if (isCircular) {
                            val cwOffset = (posI - posJ + count) % count
                            if (cwOffset in 1..2) candidateRules.add(LeftRightOffsetRule(i, j, cwOffset))
                            val ccwOffset = (posJ - posI + count) % count
                            if (ccwOffset in 1..2) candidateRules.add(LeftRightOffsetRule(i, j, -ccwOffset))
                        } else if (rawOffset in -2..2 && rawOffset != 0) {
                            candidateRules.add(LeftRightOffsetRule(i, j, rawOffset))
                        }
                        if (i < j && NotAdjacentRule(i, j).holds(truth, isCircular)) {
                            candidateRules.add(NotAdjacentRule(i, j))
                        }
                    }
                }
                if (!isCircular && NotAtEndsRule(i).holds(truth, false)) {
                    candidateRules.add(NotAtEndsRule(i))
                }
            }

            val chosenRules = mutableListOf<SeatRule>()
            chosenRules.add(candidateRules.first()) // anchor rule
            val remainingPool = candidateRules.drop(1).shuffled(random)
            var matching = allPerms.filter { p -> chosenRules.all { it.holds(p, isCircular) } }

            for (rule in remainingPool) {
                if (matching.size <= 1) break
                val narrowed = matching.filter { rule.holds(it, isCircular) }
                if (narrowed.size < matching.size && narrowed.isNotEmpty()) {
                    chosenRules.add(rule)
                    matching = narrowed
                }
            }

            if (matching.size == 1 && chosenRules.size <= 5) {
                val solution = matching.first()
                val solutionNames = solution.map { names[it] }
                val targetSeat = if (isCircular) (1 until count).random(random) else random.nextInt(count)
                val targetName = solutionNames[targetSeat]
                val prompt = if (isCircular) {
                    "Everyone faces the center of a $count-seat circular table. Who sits at Seat #${targetSeat + 1}?"
                } else {
                    "All $count people sit in a straight row facing North. Who sits at position ${targetSeat + 1} from the left?"
                }
                val options = names.shuffled(random)
                val correctIdx = options.indexOf(targetName)
                val orderStr = if (isCircular) {
                    solutionNames.mapIndexed { idx, n -> "#${idx + 1}:$n" }.joinToString(" → ")
                } else {
                    solutionNames.joinToString(" | ")
                }
                return SeatingPuzzle(
                    id = "seat_${count}_${random.nextInt(10000)}",
                    isCircular = isCircular,
                    people = names,
                    clues = chosenRules.map { it.describe(names, isCircular) },
                    promptQuestion = prompt,
                    options = options,
                    correctIndex = correctIdx,
                    explanation = "Unique verified arrangement: $orderStr. Therefore $targetName occupies Seat ${targetSeat + 1}.",
                    solutionOrder = solutionNames
                )
            }
        }

        // Deterministic fallback
        val fallbackOrder = listOf("Aarav", "Chirag", "Bhavna", "Diya")
        return SeatingPuzzle(
            id = "seat_fallback",
            isCircular = false,
            people = fallbackOrder,
            clues = listOf(
                "Aarav sits at position 1 from the left.",
                "Bhavna sits 2nd to the right of Aarav.",
                "Chirag sits immediately to the left of Bhavna."
            ),
            promptQuestion = "All 4 people sit in a row facing North. Who sits at position 4 from the left?",
            options = listOf("Aarav", "Bhavna", "Chirag", "Diya"),
            correctIndex = 3,
            explanation = "Unique verified arrangement: Aarav | Chirag | Bhavna | Diya.",
            solutionOrder = fallbackOrder
        )
    }

    // =========================================================================
    // 3. Argument Critique: Assumption, Inference & Named Logical Fallacies
    // =========================================================================

    data class ArgumentQuestion(
        val id: String,
        val categoryTag: String,
        val passage: String,
        val prompt: String,
        val options: List<String>,
        val correctIndex: Int,
        val explanation: String
    )

    private val ARGUMENT_BANK = listOf(
        ArgumentQuestion(
            id = "arg_1",
            categoryTag = "Correlation ≠ Causation",
            passage = "A city survey found that students who carry expensive scientific calculators score 18% higher on quantitative exams than those using basic calculators. Therefore, buying an advanced calculator boosts quantitative reasoning ability.",
            prompt = "Which flaw most undermines the conclusion?",
            options = listOf(
                "Confuses correlation with causation by ignoring confounding preparation or track differences",
                "Uses circular reasoning by restating the conclusion in the premise",
                "Commits a strawman fallacy by misrepresenting student preferences",
                "Relies on an appeal to authority without statistical evidence"
            ),
            correctIndex = 0,
            explanation = "Students enrolled in advanced STEM tracks are more likely both to own scientific calculators and to have stronger quantitative training. Correlation alone does not prove the calculator caused the higher scores."
        ),
        ArgumentQuestion(
            id = "arg_2",
            categoryTag = "Required Assumption",
            passage = "To cut highway congestion during peak hours, the municipal council plans to double toll charges between 8 AM and 10 AM. Because commuters dislike higher fees, morning highway traffic will drop sharply.",
            prompt = "Which assumption is strictly required for the plan to work?",
            options = listOf(
                "Commuters have feasible alternatives (such as shifting travel times or using transit) instead of paying the toll",
                "Doubling the toll will generate enough revenue to build two additional highway lanes",
                "Afternoon traffic will decrease by the exact same proportion as morning traffic",
                "All morning commuters currently travel alone in private cars"
            ),
            correctIndex = 0,
            explanation = "Even if commuters dislike higher fees, if morning travel is completely inelastic (no transit or flexible hours), traffic will not drop—commuters will simply pay more."
        ),
        ArgumentQuestion(
            id = "arg_3",
            categoryTag = "False Dilemma",
            passage = "Editorial: 'Either the examination board cancels negative marking entirely, or deserving students will continue losing marks due to careless clerical errors.'",
            prompt = "Which logical fallacy is committed in the editorial?",
            options = listOf(
                "False Dilemma — treats two extreme choices as the only possibilities",
                "Hasty Generalization — extrapolates from an unrepresentative sample",
                "Ad Hominem — attacks the motives of the examination board",
                "Post Hoc — assumes an earlier event caused a later one"
            ),
            correctIndex = 0,
            explanation = "Middle-ground options exist (e.g., partial credit, clearer OMR formatting, or lower penalty ratios), so presenting only two extremes is a False Dilemma."
        ),
        ArgumentQuestion(
            id = "arg_4",
            categoryTag = "Direct Inference",
            passage = "Every candidate shortlisted for the final interview passed both the written aptitude screen and the domain essay. Priya was shortlisted for the final interview, whereas Rohan passed the written aptitude screen but was not shortlisted.",
            prompt = "Which statement must be true based strictly on the passage?",
            options = listOf(
                "Priya passed the domain essay",
                "Rohan failed the domain essay",
                "Everyone who passed both papers was shortlisted",
                "Priya scored higher than Rohan on the written aptitude screen"
            ),
            correctIndex = 0,
            explanation = "Passing both tests is a necessary condition for shortlisting. Since Priya was shortlisted, she must have passed the domain essay. Rohan might have passed both yet missed a tie-breaker or quota."
        ),
        ArgumentQuestion(
            id = "arg_5",
            categoryTag = "Circular Reasoning",
            passage = "The new textbook is undeniably the most accurate syllabus guide on the market, because every statement in it conforms completely to the authoritative facts presented inside the textbook itself.",
            prompt = "Which reasoning error appears here?",
            options = listOf(
                "Circular Reasoning — uses the textbook's own claims to prove its accuracy",
                "Strawman — exaggerates a rival textbook's weaknesses",
                "Slippery Slope — predicts an unsupported chain of disasters",
                "Equivocation — shifts the meaning of a technical term mid-argument"
            ),
            correctIndex = 0,
            explanation = "Justifying a source's accuracy solely by referencing the source itself assumes what needs independent verification (circular reasoning)."
        ),
        ArgumentQuestion(
            id = "arg_6",
            categoryTag = "Strawman Fallacy",
            passage = "A: 'We should allocate 15 minutes of class time daily to silent reading comprehension.'\nB: 'My opponent wants to abolish mathematics instruction and turn the entire school day into leisure reading!'",
            prompt = "How does Speaker B distort the debate?",
            options = listOf(
                "Strawman — replaces A's modest proposal with an extreme caricature that is easy to attack",
                "False Analogy — compares two unrelated educational systems",
                "Bandwagon — appeals to majority opinion rather than evidence",
                "Red Herring — introduces an irrelevant budget statistic"
            ),
            correctIndex = 0,
            explanation = "Speaker A proposed 15 minutes of reading comprehension, not abolishing math. Attacking an exaggerated version of an opponent's position is a Strawman."
        ),
        ArgumentQuestion(
            id = "arg_7",
            categoryTag = "Hasty Generalization",
            passage = "Two candidates from District X who enrolled in our weekend mock series topped the state rank list. Therefore, every weekend-series student from District X is guaranteed a top-100 state rank.",
            prompt = "What is the primary logical flaw?",
            options = listOf(
                "Hasty Generalization — leaps to a universal guarantee from only two standout cases",
                "Tu Quoque — accuses critics of hypocrisy",
                "Equivocation — uses 'top' in two incompatible senses",
                "Composition — assumes parts inherit properties of the whole"
            ),
            correctIndex = 0,
            explanation = "Drawing an 'every student is guaranteed' conclusion from two top performers ignores sample size and survivorship bias."
        ),
        ArgumentQuestion(
            id = "arg_8",
            categoryTag = "Necessary vs Sufficient",
            passage = "Policy: 'To qualify for a research stipend, an applicant must publish at least one peer-reviewed paper.' Meera has published three peer-reviewed papers, so she is assured of receiving the stipend.",
            prompt = "Why is the conclusion flawed?",
            options = listOf(
                "Confuses a necessary condition with a sufficient condition",
                "Relies on an outdated definition of peer review",
                "Assumes publication quality equals publication count",
                "Neglects to specify when the stipend is disbursed"
            ),
            correctIndex = 0,
            explanation = "Publishing one paper is required ('must'), not sufficient on its own—other criteria such as GPA, funding caps, or interviews may still disqualify Meera."
        ),
        ArgumentQuestion(
            id = "arg_9",
            categoryTag = "Base Rate Neglect",
            passage = "Diagnostic Alert: A rapid test is 95% accurate. It flags a rare syllabus misconception found in only 1% of test-takers. Therefore, a student flagged positive has a 95% chance of actually having the misconception.",
            prompt = "Why is the 95% probability claim incorrect?",
            options = listOf(
                "It ignores the 1% base rate: out of 1,000 students, ~50 false positives outnumber ~10 true positives",
                "It assumes students take the test twice independently",
                "It confuses precision with recall in reverse",
                "It overlooks the confidence interval of the sample size"
            ),
            correctIndex = 0,
            explanation = "With 1% prevalence in 1,000 students: ~10 true cases (~9.5 flagged) vs ~990 healthy cases (~49.5 false positives). Most positive flags are false positives!"
        ),
        ArgumentQuestion(
            id = "arg_10",
            categoryTag = "Required Assumption",
            passage = "After installing glare-free LED lamps in Study Hall B, average reading speed rose by 12% over the next month. Hence, glare-free lighting directly increases reading speed.",
            prompt = "Which unstated assumption does the conclusion depend upon?",
            options = listOf(
                "No other factor (such as easier practice passages or self-selection of fast readers into Hall B) changed during that month",
                "Glare-free LEDs consume less electricity than fluorescent tubes",
                "All students in Study Hall B prefer bright white lighting over warm lighting",
                "Reading comprehension accuracy also increased by at least 12%"
            ),
            correctIndex = 0,
            explanation = "Causal attribution from a before-and-after observation requires ruling out rival explanations like passage difficulty or student cohort changes."
        )
    )

    fun getShuffledArgumentBank(random: Random = Random.Default): List<ArgumentQuestion> {
        return ARGUMENT_BANK.shuffled(random).map { q ->
            val indexed = q.options.mapIndexed { idx, text -> idx to text }.shuffled(random)
            q.copy(
                options = indexed.map { it.second },
                correctIndex = indexed.indexOfFirst { it.first == q.correctIndex }
            )
        }
    }

    // =========================================================================
    // 4. Data Sufficiency Discipline Check (Boundary & Hidden-Assumption Traps)
    // =========================================================================

    enum class DataSufficiencyAnswer(val label: String) {
        STATEMENT_I_ALONE("Statement I alone is sufficient"),
        STATEMENT_II_ALONE("Statement II alone is sufficient"),
        EITHER_ALONE("Either Statement I alone OR II alone is sufficient"),
        BOTH_TOGETHER("Both Statements I and II together are needed"),
        NEITHER("Neither I nor II (even together) is sufficient")
    }

    data class DataSufficiencyQuestion(
        val id: String,
        val trapTag: String,
        val question: String,
        val statementI: String,
        val statementII: String,
        val answer: DataSufficiencyAnswer,
        val explanation: String
    )

    private val DATA_SUFFICIENCY_BANK = listOf(
        DataSufficiencyQuestion(
            id = "ds_1",
            trapTag = "Negative & Fractional Trap",
            question = "Is x² > x ?",
            statementI = "x > 0",
            statementII = "x < 1",
            answer = DataSufficiencyAnswer.BOTH_TOGETHER,
            explanation = "From I alone: if x = 0.5, x² = 0.25 < x (No); if x = 2, x² = 4 > x (Yes). Not sufficient.\nFrom II alone: if x = 0.5, No; if x = -2, (-2)² = 4 > -2 (Yes). Not sufficient.\nTogether (0 < x < 1): x² is ALWAYS strictly less than x, giving a definite 'NO'. Thus BOTH together suffice!"
        ),
        DataSufficiencyQuestion(
            id = "ds_2",
            trapTag = "Even Power Sign Trap",
            question = "What is the exact value of real number x ?",
            statementI = "x² = 25",
            statementII = "x³ = -125",
            answer = DataSufficiencyAnswer.STATEMENT_II_ALONE,
            explanation = "Statement I gives x = +5 or x = -5 (two values, not unique).\nStatement II gives only one real solution: x = -5. Hence II alone is sufficient."
        ),
        DataSufficiencyQuestion(
            id = "ds_3",
            trapTag = "Zero & Non-Integer Trap",
            question = "Is integer n divisible by 6?",
            statementI = "n is divisible by 2.",
            statementII = "n is divisible by 3.",
            answer = DataSufficiencyAnswer.BOTH_TOGETHER,
            explanation = "Statement I alone allows n = 4 (No) or n = 6 (Yes). Statement II alone allows n = 9 (No) or n = 6 (Yes). Because gcd(2, 3) = 1, combining both guarantees n is divisible by 6."
        ),
        DataSufficiencyQuestion(
            id = "ds_4",
            trapTag = "Division by Zero / Negative Sign",
            question = "Is a > b ?",
            statementI = "a / c > b / c",
            statementII = "c > 0",
            answer = DataSufficiencyAnswer.BOTH_TOGETHER,
            explanation = "From I alone, c could be negative (e.g., c = -1 flips the inequality: -2 > -4 gives 2 < 4). From II alone, we know nothing about a or b. Together, c > 0 lets us multiply safely: a > b."
        ),
        DataSufficiencyQuestion(
            id = "ds_5",
            trapTag = "Linear Equation Elimination",
            question = "What is the value of (3x + 2y)?",
            statementI = "6x + 4y = 28",
            statementII = "x - y = 3",
            answer = DataSufficiencyAnswer.STATEMENT_I_ALONE,
            explanation = "Do not rush to solve for x and y separately! Dividing Statement I by 2 immediately gives 3x + 2y = 14. Statement I alone suffices."
        ),
        DataSufficiencyQuestion(
            id = "ds_6",
            trapTag = "Non-Integer Domain Trap",
            question = "Is x an odd integer?",
            statementI = "2x is an even integer.",
            statementII = "3x is an odd integer.",
            answer = DataSufficiencyAnswer.NEITHER,
            explanation = "Watch out! The question does NOT state x is an integer! From I: x could be 2 (even), 3 (odd), or 1 (odd). Together: try x = 1 (3x=3, 2x=2; x is odd) vs x = 1/3? Wait: if x = 1/3, 2x = 2/3 (not even). What about x = 2? 3x = 6 (not odd). What about x = -1? Odd. Wait: if 2x is an integer AND 3x is an integer, then x = 3x - 2x MUST be an integer! And since 3x is odd, x cannot be even, so x MUST be odd! Let's check carefully—WAIT: 3x - 2x = integer!"
        ),
        DataSufficiencyQuestion(
            id = "ds_7",
            trapTag = "Sign of Product Trap",
            question = "Is xy > 0 ?",
            statementI = "x + y > 0",
            statementII = "|x| > |y|",
            answer = DataSufficiencyAnswer.NEITHER,
            explanation = "Test boundary values satisfying both I and II:\nCase 1: x = 5, y = 2 → x+y = 7 > 0, |5| > |2|, and xy = 10 > 0 (Yes).\nCase 2: x = 5, y = -2 → x+y = 3 > 0, |5| > |-2|, yet xy = -10 < 0 (No). Even together they are insufficient."
        ),
        DataSufficiencyQuestion(
            id = "ds_8",
            trapTag = "Absolute Value Equality",
            question = "Is |x - 3| < 2 ?",
            statementI = "1 < x < 5",
            statementII = "x² - 6x + 5 < 0",
            answer = DataSufficiencyAnswer.EITHER_ALONE,
            explanation = "|x - 3| < 2 simplifies to 1 < x < 5.\nStatement I states 1 < x < 5 directly.\nStatement II factors as (x - 1)(x - 5) < 0, which ALSO holds iff 1 < x < 5.\nSo either statement alone is sufficient!"
        ),
        DataSufficiencyQuestion(
            id = "ds_9",
            trapTag = "Zero Exponent Trap",
            question = "What is the value of real number p ?",
            statementI = "p² = p",
            statementII = "p > 0",
            answer = DataSufficiencyAnswer.BOTH_TOGETHER,
            explanation = "Statement I gives p(p - 1) = 0, so p = 0 or p = 1. Statement II rules out p = 0, leaving uniquely p = 1. Together they suffice."
        ),
        DataSufficiencyQuestion(
            id = "ds_10",
            trapTag = "Percentage Base Asymmetry",
            question = "Did Store A earn a higher total profit in May than in April?",
            statementI = "Store A's revenue rose 20% from April to May.",
            statementII = "Store A's profit margin (% of revenue) stayed constant from April to May.",
            answer = DataSufficiencyAnswer.BOTH_TOGETHER,
            explanation = "Wait: what if April profit was zero or negative (a loss)? If April had negative margin (-10%) and May revenue rose 20%, a -10% margin on higher revenue means a BIGGER loss (lower profit)!Wait—let's keep statements clean: if we check profit > 0 or note margin sign!"
        )
    )

    // Let's make sure ds_6 and ds_10 have 100% airtight mathematical statements with zero ambiguity!
    private val VERIFIED_DATA_SUFFICIENCY_BANK = DATA_SUFFICIENCY_BANK.map { item ->
        when (item.id) {
            "ds_6" -> DataSufficiencyQuestion(
                id = "ds_6",
                trapTag = "Algebraic Parity Identity",
                question = "Is x an odd integer?",
                statementI = "2x is an even integer.",
                statementII = "3x is an odd integer.",
                answer = DataSufficiencyAnswer.BOTH_TOGETHER,
                explanation = "From I alone: 2x is even ⇒ x can be any integer (2 gives No, 3 gives Yes).\nFrom II alone: 3x is odd ⇒ x could be 1 (Yes) or 1/3 (Not an integer, No!).\nTogether: Since 3x and 2x are both integers, x = 3x - 2x MUST be an integer; and since 3x is odd, x must be an odd integer! Both together suffice."
            )
            "ds_10" -> DataSufficiencyQuestion(
                id = "ds_10",
                trapTag = "Loss vs Profit Sign Trap",
                question = "Did Store A earn a higher total profit in May than in April?",
                statementI = "Store A's revenue rose 20% from April to May.",
                statementII = "Store A's profit margin (% of revenue) stayed constant at +15% in both months.",
                answer = DataSufficiencyAnswer.BOTH_TOGETHER,
                explanation = "Revenue alone (I) could accompany rising costs and lower profit. A constant +15% margin alone (II) gives no revenue comparison. Together, 15% of 1.2R > 15% of R (assuming positive revenue), so May profit is strictly higher."
            )
            else -> item
        }
    }

    fun getShuffledDataSufficiencyBank(random: Random = Random.Default): List<DataSufficiencyQuestion> {
        return VERIFIED_DATA_SUFFICIENCY_BANK.shuffled(random)
    }
}
