package com.example.util

import com.example.model.BrainLabStats
import com.example.model.BreathingProtocol
import com.example.model.NBackMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BrainLabEngineTest {

    @Test
    fun nBack_generatesValidSequence_andAdaptsTierCorrectly() {
        val rng = Random(42)
        val trials = BrainLabMemoryEngine.generateNBackSequence(
            n = 2,
            totalTrials = 18,
            mode = NBackMode.DUAL,
            random = rng
        )
        assertEquals(18, trials.size)
        assertFalse(trials[0].isPositionMatch)
        assertFalse(trials[1].isLetterMatch)
        assertTrue(trials.all { it.cellIndex in 0..8 && it.letter in BrainLabMemoryEngine.N_BACK_LETTERS })

        val perfectPosTaps = trials.filter { it.isPositionMatch }.map { it.stepIndex }.toSet()
        val perfectLetterTaps = trials.filter { it.isLetterMatch }.map { it.stepIndex }.toSet()
        val highEval = BrainLabMemoryEngine.evaluateNBackSession(
            currentN = 2,
            mode = NBackMode.DUAL,
            trials = trials,
            userPosTaps = perfectPosTaps,
            userLetterTaps = perfectLetterTaps
        )
        assertEquals(100, highEval.accuracyPercent)
        assertEquals(3, highEval.nextRecommendedN)

        val invertedPos = trials.filter { it.stepIndex >= 2 && !it.isPositionMatch }.map { it.stepIndex }.toSet()
        val invertedLet = trials.filter { it.stepIndex >= 2 && !it.isLetterMatch }.map { it.stepIndex }.toSet()
        val lowEval = BrainLabMemoryEngine.evaluateNBackSession(
            currentN = 3,
            mode = NBackMode.DUAL,
            trials = trials,
            userPosTaps = invertedPos,
            userLetterTaps = invertedLet
        )
        assertTrue(lowEval.accuracyPercent < 55)
        assertEquals(2, lowEval.nextRecommendedN)
    }

    @Test
    fun corsiAndStroop_generateDeterministicValidTrials() {
        val rng = Random(17)
        val corsiRev = BrainLabMemoryEngine.generateCorsiPuzzle(
            gridSize = 4,
            span = 5,
            reverseMode = true,
            withAlphanumericLabels = true,
            random = rng
        )
        assertEquals(5, corsiRev.sequence.size)
        assertEquals(corsiRev.sequence.reversed(), corsiRev.expectedInput)
        assertEquals(16, corsiRev.alphanumericLabels.size)

        val stroopTrials = (0 until 20).map { idx ->
            BrainLabMemoryEngine.generateStroopTrial(stepIndex = idx, allowArrowMode = true, random = rng)
        }
        assertTrue(stroopTrials.all { it.expectedChoiceIndex in it.choices.indices })
    }

    @Test
    fun syllogismVerifier_verifiesAllFiveAnswerTypesWithCounterexamples() {
        val terms = listOf("Alphas", "Betas", "Gammas")

        val p1 = listOf(
            BrainLabReasoningEngine.SyllogismStatement(BrainLabReasoningEngine.Quantifier.ALL, 0, 1),
            BrainLabReasoningEngine.SyllogismStatement(BrainLabReasoningEngine.Quantifier.SOME, 0, 2)
        )
        val c1 = BrainLabReasoningEngine.SyllogismStatement(BrainLabReasoningEngine.Quantifier.SOME, 1, 2)
        val c2 = BrainLabReasoningEngine.SyllogismStatement(BrainLabReasoningEngine.Quantifier.NO, 1, 2)
        val verOnlyI = BrainLabReasoningEngine.verifySyllogism(terms, p1, c1, c2)
        assertNotNull(verOnlyI)
        assertEquals(BrainLabReasoningEngine.SyllogismAnswer.ONLY_I, verOnlyI!!.answer)
        assertTrue(verOnlyI.explanation.contains("Counterexample"))

        val p2 = listOf(
            BrainLabReasoningEngine.SyllogismStatement(BrainLabReasoningEngine.Quantifier.ALL, 0, 1),
            BrainLabReasoningEngine.SyllogismStatement(BrainLabReasoningEngine.Quantifier.SOME, 1, 2)
        )
        val e1 = BrainLabReasoningEngine.SyllogismStatement(BrainLabReasoningEngine.Quantifier.SOME, 0, 2)
        val e2 = BrainLabReasoningEngine.SyllogismStatement(BrainLabReasoningEngine.Quantifier.NO, 0, 2)
        val verEither = BrainLabReasoningEngine.verifySyllogism(terms, p2, e1, e2)
        assertNotNull(verEither)
        assertEquals(BrainLabReasoningEngine.SyllogismAnswer.EITHER, verEither!!.answer)

        val rng = Random(99)
        BrainLabReasoningEngine.SyllogismAnswer.entries.forEach { target ->
            val generated = BrainLabReasoningEngine.generateSyllogismPuzzle(desiredAnswer = target, random = rng)
            assertEquals(target, generated.verification.answer)
        }
    }

    @Test
    fun seatingArgumentAndDataSufficiency_areValidAndNonEmpty() {
        val rng = Random(123)
        val linear = BrainLabReasoningEngine.generateSeatingPuzzle(seatCount = 4, isCircular = false, random = rng)
        assertEquals(4, linear.people.size)
        assertTrue(linear.clues.isNotEmpty())
        assertTrue(linear.correctIndex in linear.options.indices)

        val circular = BrainLabReasoningEngine.generateSeatingPuzzle(seatCount = 5, isCircular = true, random = rng)
        assertEquals(5, circular.people.size)
        assertTrue(circular.isCircular)

        val args = BrainLabReasoningEngine.getShuffledArgumentBank(rng)
        assertTrue(args.size >= 10)
        assertTrue(args.all { it.correctIndex in it.options.indices && it.explanation.isNotBlank() })

        val ds = BrainLabReasoningEngine.getShuffledDataSufficiencyBank(rng)
        assertTrue(ds.size >= 10)
        assertTrue(ds.all { it.explanation.isNotBlank() })
    }

    @Test
    fun schulteTrailAndMentalQuant_produceExactOrdersAndValidQuestions() {
        val rng = Random(77)
        val schulte5 = BrainLabQuantScanEngine.generateSchulteBoard(BrainLabQuantScanEngine.SchulteMode.SCHULTE_5X5, rng)
        assertEquals(25, schulte5.cells.size)
        assertEquals((1..25).map { it.toString() }, schulte5.expectedOrder)

        val trail5 = BrainLabQuantScanEngine.generateSchulteBoard(BrainLabQuantScanEngine.SchulteMode.TRAIL_5X5, rng)
        assertEquals(25, trail5.cells.size)
        assertEquals(listOf("1", "A", "2", "B", "3", "C"), trail5.expectedOrder.take(6))
        assertEquals("13", trail5.expectedOrder.last())

        val quantQuestions = (1..30).map { BrainLabQuantScanEngine.generateQuantQuestion(it, rng) }
        assertTrue(quantQuestions.all { it.correctIndex in it.options.indices && it.options.distinct().size == it.options.size })
    }

    @Test
    fun calmBreathingAndZenGrids_haveValidPhasesAndUniqueSolutions() {
        val boxStart = BrainLabCalmEngine.sampleBreathingState(BreathingProtocol.BOX_4_4_4_4, 0L)
        assertEquals(BrainLabCalmEngine.BreathCue.INHALE, boxStart.cue)
        val boxHold = BrainLabCalmEngine.sampleBreathingState(BreathingProtocol.BOX_4_4_4_4, 4_500L)
        assertEquals(BrainLabCalmEngine.BreathCue.HOLD, boxHold.cue)

        val sighExhale = BrainLabCalmEngine.sampleBreathingState(BreathingProtocol.CYCLIC_SIGH, 5_000L)
        assertEquals(BrainLabCalmEngine.BreathCue.EXHALE, sighExhale.cue)

        val rng = Random(55)
        val zen4 = BrainLabCalmEngine.generateZenLogicPuzzle(size = 4, random = rng)
        assertEquals(1, BrainLabCalmEngine.countSolutions(zen4.givens, zen4.size, zen4.boxRows, zen4.boxCols, limit = 2))
        assertTrue(BrainLabCalmEngine.isZenGridSolvedCorrectly(zen4.solution, zen4.size, zen4.boxRows, zen4.boxCols))

        val zen6 = BrainLabCalmEngine.generateZenLogicPuzzle(size = 6, random = rng)
        assertEquals(1, BrainLabCalmEngine.countSolutions(zen6.givens, zen6.size, zen6.boxRows, zen6.boxCols, limit = 2))
        assertTrue(BrainLabCalmEngine.isZenGridSolvedCorrectly(zen6.solution, zen6.size, zen6.boxRows, zen6.boxCols))
    }

    @Test
    fun statsMergeForRestore_keepsPersonalBestsAcrossDevices() {
        val local = BrainLabStats(
            bestNBackTier = 3,
            bestNBackAccuracy = 88,
            bestCorsiReverseSpan = 6,
            bestSchulte5x5Ms = 14_200L,
            bestQuantBlitzScore = 18,
            calmSessionsCompleted = 4,
            lastUpdatedAtMillis = 1_000L
        )
        val incoming = BrainLabStats(
            bestNBackTier = 4,
            bestNBackAccuracy = 84,
            bestCorsiReverseSpan = 5,
            bestSchulte5x5Ms = 18_000L,
            bestQuantBlitzScore = 22,
            calmSessionsCompleted = 2,
            reasoningStreak = 5,
            lastUpdatedAtMillis = 2_000L
        )

        val merged = BrainLabStats.mergeForRestore(local, incoming)
        assertEquals(4, merged.bestNBackTier)
        assertEquals(88, merged.bestNBackAccuracy)
        assertEquals(6, merged.bestCorsiReverseSpan)
        assertEquals(14_200L, merged.bestSchulte5x5Ms)
        assertEquals(22, merged.bestQuantBlitzScore)
        assertEquals(4, merged.calmSessionsCompleted)
        assertEquals(5, merged.reasoningStreak)
    }
}
