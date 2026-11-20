package com.example.util

import com.example.model.DeckSrsStats
import com.example.model.FlashcardSrsState
import com.example.model.FlashcardSrsStatus
import com.example.model.SrsRating
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Pure Kotlin SM-2 Spaced Repetition scheduler and queue builder.
 * Kept free of Android framework / org.json dependencies so fast JVM unit tests run directly.
 */
object SpacedRepetitionEngine {

    const val DAY_MILLIS: Long = 86_400_000L
    const val AGAIN_DELAY_MILLIS: Long = 10L * 60_000L
    const val MIN_EASE_FACTOR: Float = 1.3f
    const val DEFAULT_EASE_FACTOR: Float = 2.5f
    const val MAX_EASE_FACTOR: Float = 3.0f
    const val MAX_INTERVAL_DAYS: Int = 365
    const val MASTERED_MIN_INTERVAL_DAYS: Int = 7

    fun computeNextState(
        current: FlashcardSrsState?,
        blockIndex: Int,
        rating: SrsRating,
        nowMillis: Long
    ): FlashcardSrsState {
        val oldEase = (current?.easeFactor ?: DEFAULT_EASE_FACTOR).coerceIn(MIN_EASE_FACTOR, MAX_EASE_FACTOR)
        val oldReps = (current?.repetitions ?: 0).coerceAtLeast(0)
        val oldInterval = (current?.intervalDays ?: 0).coerceAtLeast(0)
        val oldLapses = current?.lapses ?: 0
        val oldReviews = current?.reviewCount ?: 0
        val oldCorrect = current?.correctCount ?: 0

        val newReps: Int
        val newIntervalDays: Int
        val newEase: Float
        val newDueAtMillis: Long
        val newLapses: Int

        when (rating) {
            SrsRating.AGAIN -> {
                newReps = 0
                newIntervalDays = 0
                newEase = (oldEase - 0.20f).coerceAtLeast(MIN_EASE_FACTOR)
                newDueAtMillis = nowMillis + AGAIN_DELAY_MILLIS
                newLapses = oldLapses + 1
            }

            SrsRating.HARD -> {
                newReps = oldReps + 1
                newIntervalDays = when (oldReps) {
                    0 -> 1
                    1 -> 3
                    else -> (oldInterval * 1.2f).roundToInt()
                        .coerceAtLeast(oldInterval + 1)
                        .coerceAtMost(MAX_INTERVAL_DAYS)
                }
                newEase = (oldEase - 0.15f).coerceAtLeast(MIN_EASE_FACTOR)
                newDueAtMillis = nowMillis + newIntervalDays * DAY_MILLIS
                newLapses = oldLapses
            }

            SrsRating.GOOD -> {
                newReps = oldReps + 1
                newIntervalDays = when (oldReps) {
                    0 -> 1
                    1 -> 6
                    else -> (oldInterval * oldEase).roundToInt()
                        .coerceAtLeast(oldInterval + 1)
                        .coerceAtMost(MAX_INTERVAL_DAYS)
                }
                newEase = oldEase
                newDueAtMillis = nowMillis + newIntervalDays * DAY_MILLIS
                newLapses = oldLapses
            }

            SrsRating.EASY -> {
                newReps = oldReps + 1
                newIntervalDays = when (oldReps) {
                    0 -> 4
                    1 -> 8
                    else -> (oldInterval * oldEase * 1.3f).roundToInt()
                        .coerceAtLeast(oldInterval + 2)
                        .coerceAtMost(MAX_INTERVAL_DAYS)
                }
                newEase = (oldEase + 0.15f).coerceAtMost(MAX_EASE_FACTOR)
                newDueAtMillis = nowMillis + newIntervalDays * DAY_MILLIS
                newLapses = oldLapses
            }
        }

        return FlashcardSrsState(
            blockIndex = blockIndex,
            repetitions = newReps,
            easeFactor = newEase,
            intervalDays = newIntervalDays,
            dueAtMillis = newDueAtMillis,
            lastReviewedAtMillis = nowMillis,
            lapses = newLapses,
            lastRating = rating,
            reviewCount = oldReviews + 1,
            correctCount = oldCorrect + if (rating == SrsRating.AGAIN) 0 else 1
        )
    }

    fun formatInterval(intervalDays: Int, rating: SrsRating? = null): String {
        if (rating == SrsRating.AGAIN || intervalDays <= 0) return "<10m"
        return when {
            intervalDays < 14 -> "${intervalDays}d"
            intervalDays < 30 -> "${(intervalDays / 7f).roundToInt().coerceAtLeast(2)}w"
            intervalDays < 365 -> "${(intervalDays / 30f).roundToInt().coerceAtLeast(1)}mo"
            else -> "1y"
        }
    }

    fun previewNextIntervalLabels(
        current: FlashcardSrsState?,
        nowMillis: Long = 0L
    ): Map<SrsRating, String> {
        val blockIdx = current?.blockIndex ?: 0
        return SrsRating.entries.associateWith { rating ->
            val simulated = computeNextState(current, blockIdx, rating, nowMillis)
            formatInterval(simulated.intervalDays, rating)
        }
    }

    fun statusOf(state: FlashcardSrsState?, nowMillis: Long): FlashcardSrsStatus {
        if (state == null || state.reviewCount == 0) return FlashcardSrsStatus.NEW
        if (state.dueAtMillis <= nowMillis) return FlashcardSrsStatus.DUE
        if (state.intervalDays >= MASTERED_MIN_INTERVAL_DAYS && state.repetitions >= 2) {
            return FlashcardSrsStatus.MASTERED
        }
        return FlashcardSrsStatus.LEARNING
    }

    fun isMasteredBySrs(state: FlashcardSrsState?): Boolean {
        return state != null && state.intervalDays >= MASTERED_MIN_INTERVAL_DAYS && state.repetitions >= 2
    }

    fun buildStudyQueue(
        allCards: List<StudyNoteBlock>,
        srsMap: Map<Int, FlashcardSrsState>,
        nowMillis: Long,
        cramAll: Boolean,
        prioritizeDue: Boolean = true,
        shuffleSeed: Int = 0
    ): List<StudyNoteBlock> {
        if (allCards.isEmpty()) return emptyList()

        val dueList = mutableListOf<StudyNoteBlock>()
        val newList = mutableListOf<StudyNoteBlock>()
        val aheadList = mutableListOf<StudyNoteBlock>()

        allCards.forEach { card ->
            val st = srsMap[card.blockIndex]
            when (statusOf(st, nowMillis)) {
                FlashcardSrsStatus.DUE -> dueList.add(card)
                FlashcardSrsStatus.NEW -> newList.add(card)
                FlashcardSrsStatus.LEARNING, FlashcardSrsStatus.MASTERED -> aheadList.add(card)
            }
        }

        val sortedDue = if (prioritizeDue) {
            dueList.sortedBy { srsMap[it.blockIndex]?.dueAtMillis ?: 0L }
        } else {
            dueList
        }

        val baseQueue: List<StudyNoteBlock> = if (cramAll) {
            if (prioritizeDue) {
                sortedDue + newList + aheadList.sortedBy { srsMap[it.blockIndex]?.dueAtMillis ?: Long.MAX_VALUE }
            } else {
                allCards
            }
        } else {
            sortedDue + newList
        }

        return if (shuffleSeed != 0 && baseQueue.size > 1) {
            baseQueue.shuffled(Random(shuffleSeed))
        } else {
            baseQueue
        }
    }

    fun computeDeckStats(
        allCards: List<StudyNoteBlock>,
        srsMap: Map<Int, FlashcardSrsState>,
        nowMillis: Long
    ): DeckSrsStats {
        if (allCards.isEmpty()) return DeckSrsStats()
        var due = 0
        var fresh = 0
        var learning = 0
        var mastered = 0
        var totalReviews = 0
        var totalCorrect = 0

        allCards.forEach { card ->
            val st = srsMap[card.blockIndex]
            when (statusOf(st, nowMillis)) {
                FlashcardSrsStatus.DUE -> due++
                FlashcardSrsStatus.NEW -> fresh++
                FlashcardSrsStatus.LEARNING -> learning++
                FlashcardSrsStatus.MASTERED -> mastered++
            }
            if (st != null) {
                totalReviews += st.reviewCount
                totalCorrect += st.correctCount
            }
        }

        val retention = if (totalReviews > 0) {
            ((totalCorrect.toFloat() / totalReviews.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
        } else {
            0
        }

        return DeckSrsStats(
            totalCount = allCards.size,
            dueCount = due,
            newCount = fresh,
            learningCount = learning,
            masteredCount = mastered,
            retentionPercent = retention
        )
    }

    /**
     * Inserts a missed ("Again") card 2-3 slots ahead in the current session queue so the learner
     * recalls it again before finishing the session.
     */
    fun reinsertCardSoon(
        currentQueue: List<StudyNoteBlock>,
        currentIndex: Int,
        card: StudyNoteBlock
    ): List<StudyNoteBlock> {
        if (currentQueue.isEmpty()) return listOf(card)
        val mutable = currentQueue.toMutableList()
        val insertPos = (currentIndex + 3).coerceAtMost(mutable.size)
        mutable.add(insertPos, card)
        return mutable
    }
}
