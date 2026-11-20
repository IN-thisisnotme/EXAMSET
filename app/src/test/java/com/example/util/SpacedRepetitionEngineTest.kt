package com.example.util

import com.example.model.FlashcardSrsState
import com.example.model.FlashcardSrsStatus
import com.example.model.SrsRating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpacedRepetitionEngineTest {

    private val dayMs = SpacedRepetitionEngine.DAY_MILLIS

    @Test
    fun `SM-2 good progression schedules 1d then 6d then ease-multiplied intervals`() {
        val t0 = 1_700_000_000_000L
        val first = SpacedRepetitionEngine.computeNextState(null, blockIndex = 1, rating = SrsRating.GOOD, nowMillis = t0)
        assertEquals(1, first.repetitions)
        assertEquals(1, first.intervalDays)
        assertEquals(t0 + 1 * dayMs, first.dueAtMillis)
        assertEquals(0, first.lapses)
        assertEquals(1, first.reviewCount)
        assertEquals(1, first.correctCount)

        val second = SpacedRepetitionEngine.computeNextState(first, blockIndex = 1, rating = SrsRating.GOOD, nowMillis = t0 + dayMs)
        assertEquals(2, second.repetitions)
        assertEquals(6, second.intervalDays)

        val third = SpacedRepetitionEngine.computeNextState(second, blockIndex = 1, rating = SrsRating.GOOD, nowMillis = t0 + 7 * dayMs)
        assertEquals(3, third.repetitions)
        assertEquals(15, third.intervalDays)
        assertTrue(SpacedRepetitionEngine.isMasteredBySrs(third))
    }

    @Test
    fun `again resets repetitions, increments lapses, clamps ease at 1_3, and schedules within 10 minutes`() {
        val t0 = 1_700_000_000_000L
        val lowEase = FlashcardSrsState(
            blockIndex = 2,
            repetitions = 4,
            easeFactor = 1.35f,
            intervalDays = 20,
            dueAtMillis = t0,
            lapses = 1,
            reviewCount = 5,
            correctCount = 4
        )
        val missed = SpacedRepetitionEngine.computeNextState(lowEase, blockIndex = 2, rating = SrsRating.AGAIN, nowMillis = t0)
        assertEquals(0, missed.repetitions)
        assertEquals(0, missed.intervalDays)
        assertEquals(2, missed.lapses)
        assertEquals(SpacedRepetitionEngine.MIN_EASE_FACTOR, missed.easeFactor, 0.001f)
        assertEquals(t0 + SpacedRepetitionEngine.AGAIN_DELAY_MILLIS, missed.dueAtMillis)
        assertEquals(6, missed.reviewCount)
        assertEquals(4, missed.correctCount)
    }

    @Test
    fun `easy boosts interval and ease factor while hard steps modestly`() {
        val t0 = 1_700_000_000_000L
        val easyFirst = SpacedRepetitionEngine.computeNextState(null, 0, SrsRating.EASY, t0)
        assertEquals(4, easyFirst.intervalDays)
        assertTrue(easyFirst.easeFactor > SpacedRepetitionEngine.DEFAULT_EASE_FACTOR)

        val easySecond = SpacedRepetitionEngine.computeNextState(easyFirst, 0, SrsRating.EASY, t0 + 4 * dayMs)
        assertEquals(8, easySecond.intervalDays)
        assertTrue(SpacedRepetitionEngine.isMasteredBySrs(easySecond))

        val hardFirst = SpacedRepetitionEngine.computeNextState(null, 1, SrsRating.HARD, t0)
        assertEquals(1, hardFirst.intervalDays)
        assertTrue(hardFirst.easeFactor < SpacedRepetitionEngine.DEFAULT_EASE_FACTOR)

        val previews = SpacedRepetitionEngine.previewNextIntervalLabels(null, t0)
        assertEquals("<10m", previews[SrsRating.AGAIN])
        assertEquals("1d", previews[SrsRating.HARD])
        assertEquals("1d", previews[SrsRating.GOOD])
        assertEquals("4d", previews[SrsRating.EASY])
    }

    @Test
    fun `buildStudyQueue orders overdue before new, excludes ahead unless cramming, and computeDeckStats summarizes`() {
        val now = 1_700_000_000_000L
        val cards = listOf(
            StudyNoteBlock.OneLiner(blockIndex = 0, displayIndex = "1", leadTerm = "Card A", detail = "Mastered ahead", hasRecallTarget = true),
            StudyNoteBlock.OneLiner(blockIndex = 1, displayIndex = "2", leadTerm = "Card B", detail = "New", hasRecallTarget = true),
            StudyNoteBlock.OneLiner(blockIndex = 2, displayIndex = "3", leadTerm = "Card C", detail = "Due recently", hasRecallTarget = true),
            StudyNoteBlock.OneLiner(blockIndex = 3, displayIndex = "4", leadTerm = "Card D", detail = "Most overdue", hasRecallTarget = true)
        )
        val srsMap = mapOf(
            0 to FlashcardSrsState(blockIndex = 0, repetitions = 3, intervalDays = 14, dueAtMillis = now + 5 * dayMs, reviewCount = 3, correctCount = 3),
            2 to FlashcardSrsState(blockIndex = 2, repetitions = 1, intervalDays = 1, dueAtMillis = now - 1_000L, reviewCount = 2, correctCount = 1),
            3 to FlashcardSrsState(blockIndex = 3, repetitions = 1, intervalDays = 1, dueAtMillis = now - 50_000L, reviewCount = 1, correctCount = 1)
        )

        assertEquals(FlashcardSrsStatus.MASTERED, SpacedRepetitionEngine.statusOf(srsMap[0], now))
        assertEquals(FlashcardSrsStatus.NEW, SpacedRepetitionEngine.statusOf(srsMap[1], now))
        assertEquals(FlashcardSrsStatus.DUE, SpacedRepetitionEngine.statusOf(srsMap[2], now))

        val normalQueue = SpacedRepetitionEngine.buildStudyQueue(cards, srsMap, now, cramAll = false)
        assertEquals(listOf(3, 2, 1), normalQueue.map { it.blockIndex })

        val cramQueue = SpacedRepetitionEngine.buildStudyQueue(cards, srsMap, now, cramAll = true)
        assertEquals(listOf(3, 2, 1, 0), cramQueue.map { it.blockIndex })

        val reinserted = SpacedRepetitionEngine.reinsertCardSoon(normalQueue, currentIndex = 0, card = cards[3])
        assertEquals(4, reinserted.size)
        assertEquals(3, reinserted.last().blockIndex)

        val stats = SpacedRepetitionEngine.computeDeckStats(cards, srsMap, now)
        assertEquals(4, stats.totalCount)
        assertEquals(2, stats.dueCount)
        assertEquals(1, stats.newCount)
        assertEquals(0, stats.learningCount)
        assertEquals(1, stats.masteredCount)
        assertEquals(83, stats.retentionPercent) // 5 / 6 = 83%
    }
}
