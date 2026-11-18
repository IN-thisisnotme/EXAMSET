package com.example.data

import com.example.BuildConfig
import com.example.model.LinkedCloudFileStatus
import com.example.util.CloudBackupClients
import com.example.util.McqParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleDataAndSyncTest {

    @Test
    fun sampleDataAuditFixes_haveCorrectKeysAndConsistentExplanations() {
        // 1. UPSC GS1 Full Mock 1 — Q1 (Article 72 vs 161: all 3 statements correct -> D)
        val mock1 = McqParser.parse(SampleData.SAMPLE_UPSC_GS1_FULL_MOCK_1)
        val mock1Q1 = mock1.questions.first { it.num == 1 }
        assertEquals("D", mock1Q1.correctAnswer)
        assertNotNull(mock1Q1.explanation)
        assertTrue(mock1Q1.explanation!!.contains("Article 72"))
        assertTrue(mock1Q1.explanation!!.contains("Article 161"))
        assertTrue(mock1Q1.explanation!!.contains("All three statements"))

        // 2. UPSC GS1 PYQ 2024 — Q2 (Part IX Panchayats: statements 1 and 2 only -> B)
        val pyq2024 = McqParser.parse(SampleData.SAMPLE_UPSC_GS1_PYQ_2024)
        val pyq2024Q2 = pyq2024.questions.first { it.num == 2 }
        assertEquals("B", pyq2024Q2.correctAnswer)
        assertNotNull(pyq2024Q2.explanation)
        assertTrue(pyq2024Q2.explanation!!.contains("Article 243M"))
        assertTrue(pyq2024Q2.explanation!!.contains("243B"))

        // 3. Science Paper — Q4 (Circular seating: D is immediate left of C -> A)
        val science = McqParser.parse(SampleData.SAMPLE_SCIENCE_PAPER)
        val scienceQ4 = science.questions.first { it.num == 4 }
        assertEquals("A", scienceQ4.correctAnswer)
        assertNotNull(scienceQ4.explanation)
        assertTrue(scienceQ4.explanation!!.contains("C(1)"))
        assertTrue(scienceQ4.explanation!!.contains("D(2)"))

        // 4. Next Pattern — Q3 (Pol II canonical mRNA termination wording, answer B: 1, 3 and 4 only)
        val nextPattern = McqParser.parse(SampleData.SAMPLE_NEXT_PATTERN)
        val nextQ3 = nextPattern.questions.first { it.num == 3 }
        assertEquals("B", nextQ3.correctAnswer)
        assertTrue(nextQ3.question.contains("RNA polymerase II"))
        assertTrue(nextQ3.question.contains("torpedo model"))
    }

    @Test
    fun allSamplePapers_haveCompleteAnswerKeysAndExplanations() {
        val papers = listOf(
            "SAMPLE_STARTER_PROMPT" to SampleData.SAMPLE_STARTER_PROMPT,
            "SAMPLE_BIOLOGY_ADVANCED" to SampleData.SAMPLE_BIOLOGY_ADVANCED,
            "SAMPLE_NEXT_PATTERN" to SampleData.SAMPLE_NEXT_PATTERN,
            "SAMPLE_KOREAN_EXAM" to SampleData.SAMPLE_KOREAN_EXAM,
            "SAMPLE_SCIENCE_PAPER" to SampleData.SAMPLE_SCIENCE_PAPER,
            "SAMPLE_HINDI_PAPER" to SampleData.SAMPLE_HINDI_PAPER,
            "SAMPLE_CIVIL_MIXED_PATTERN" to SampleData.SAMPLE_CIVIL_MIXED_PATTERN,
            "SAMPLE_UPSC_GS1_FULL_MOCK_1" to SampleData.SAMPLE_UPSC_GS1_FULL_MOCK_1,
            "SAMPLE_UPSC_GS1_PYQ_2024" to SampleData.SAMPLE_UPSC_GS1_PYQ_2024,
            "SAMPLE_UPSC_GS1_PYQ_2023" to SampleData.SAMPLE_UPSC_GS1_PYQ_2023,
            "SAMPLE_UPSC_GS1_SECTIONAL_POLITY" to SampleData.SAMPLE_UPSC_GS1_SECTIONAL_POLITY
        )
        for ((name, raw) in papers) {
            val parsed = McqParser.parse(raw)
            assertTrue("$name should have questions", parsed.questions.isNotEmpty())
            for (q in parsed.questions) {
                assertTrue(
                    "$name Q${q.num} must have valid answer key A-D (got ${q.correctAnswer})",
                    q.correctAnswer in setOf("A", "B", "C", "D")
                )
                assertTrue(
                    "$name Q${q.num} must have non-blank explanation",
                    !q.explanation.isNullOrBlank()
                )
            }
        }
    }

    @Test
    fun cloudOAuthRedirectUri_matchesCurrentApplicationId() {
        val expected = "${BuildConfig.APPLICATION_ID}:/oauth2redirect"
        assertEquals(expected, CloudBackupClients.REDIRECT_URI)
        assertFalse(
            "Redirect URI must not reference stale tbqpeh package ID",
            CloudBackupClients.REDIRECT_URI.contains("tbqpeh")
        )
    }

    @Test
    fun linkedCloudFileStatus_isLinkedReflectsUriPresence() {
        assertFalse(LinkedCloudFileStatus().isLinked)
        assertFalse(LinkedCloudFileStatus(uriString = "   ").isLinked)
        assertTrue(
            LinkedCloudFileStatus(
                uriString = "content://com.google.android.apps.docs.storage/document/acc%3D1",
                displayName = "ExamSet_Live_Sync.examset.zip",
                lastSyncedAt = 1_700_000_000_000L
            ).isLinked
        )
    }
}
