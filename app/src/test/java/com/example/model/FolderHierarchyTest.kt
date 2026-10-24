package com.example.model

import com.example.data.PaperEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderHierarchyTest {

    @Test
    fun testFolderHierarchyAndBreadcrumbs() {
        // Build UPSC CSE hierarchy:
        // UPSC CSE (root) -> Prelims (sub) -> GS Paper 1 & CSAT (sub-sub)
        val root = FolderItem(id = "f-upsc", name = "UPSC CSE", color = "#0284C7", parentId = null)
        val prelims = FolderItem(id = "f-prelims", name = "Prelims", color = "#16A34A", parentId = root.id)
        val csat = FolderItem(id = "f-csat", name = "CSAT Paper 2", color = "#7C3AED", parentId = prelims.id)
        val gs1 = FolderItem(id = "f-gs1", name = "GS Paper 1", color = "#0284C7", parentId = prelims.id)

        val folders = listOf(root, prelims, csat, gs1)

        // 1. Root folders query
        val roots = folders.filter { it.parentId == null }
        assertEquals(1, roots.size)
        assertEquals("UPSC CSE", roots[0].name)

        // 2. Immediate child sub-folders for root
        val rootChildren = folders.filter { it.parentId == root.id }
        assertEquals(1, rootChildren.size)
        assertEquals("Prelims", rootChildren[0].name)

        // 3. Immediate child sub-folders for Prelims
        val prelimsChildren = folders.filter { it.parentId == prelims.id }
        assertEquals(2, prelimsChildren.size)
        assertTrue(prelimsChildren.any { it.name == "CSAT Paper 2" })
        assertTrue(prelimsChildren.any { it.name == "GS Paper 1" })

        // 4. Breadcrumb resolution function
        fun resolveBreadcrumbs(targetId: String?): List<FolderItem> {
            if (targetId == null) return emptyList()
            val path = mutableListOf<FolderItem>()
            var curr = folders.find { it.id == targetId }
            val visited = mutableSetOf<String>()
            while (curr != null && visited.add(curr.id)) {
                path.add(0, curr)
                curr = if (curr.parentId != null) folders.find { it.id == curr.parentId } else null
            }
            return path
        }

        val csatBreadcrumbs = resolveBreadcrumbs(csat.id)
        assertEquals(3, csatBreadcrumbs.size)
        assertEquals("UPSC CSE", csatBreadcrumbs[0].name)
        assertEquals("Prelims", csatBreadcrumbs[1].name)
        assertEquals("CSAT Paper 2", csatBreadcrumbs[2].name)

        val pathString = csatBreadcrumbs.joinToString(" > ") { it.name }
        assertEquals("UPSC CSE > Prelims > CSAT Paper 2", pathString)
    }

    @Test
    fun testSetSeriesNormalization() {
        fun normalizeSet(raw: String): String {
            val trimmed = raw.trim()
            return when {
                trimmed.isBlank() -> "Set 01 Series"
                trimmed.startsWith("Set", ignoreCase = true) -> trimmed.uppercase()
                trimmed.all { it.isDigit() } -> "Set ${trimmed.padStart(2, '0')}"
                else -> "Set ${trimmed.uppercase()}"
            }
        }

        assertEquals("Set 01", normalizeSet("1"))
        assertEquals("Set 02", normalizeSet("02"))
        assertEquals("SET A", normalizeSet("Set A"))
        assertEquals("SET B", normalizeSet("set b"))
        assertEquals("Set 01 Series", normalizeSet(""))
    }

    @Test
    fun testPaperSegregationGrouping() {
        fun getStageCategory(paper: PaperEntity): String {
            val combined = "${paper.title} ${paper.subject} ${paper.topic}".lowercase()
            return when {
                "csat" in combined || "paper 2" in combined -> "CSAT / Paper II"
                "gs 1" in combined || "gs-1" in combined -> "GS Paper I"
                "ethics" in combined -> "GS Paper IV (Ethics)"
                "reasoning" in combined -> "Reasoning & Mental Ability"
                else -> "General / Mixed Sets"
            }
        }

        val p1 = PaperEntity(
            title = "CSAT Full Mock Test 01",
            subject = "CSAT Paper 2",
            topic = "Logical Reasoning",
            setNumber = "01",
            questionCount = 80,
            answerKeyCount = 80,
            explanationCount = 80,
            rawText = ""
        )

        val p2 = PaperEntity(
            title = "Modern Indian History & Polity",
            subject = "GS 1",
            topic = "History",
            setNumber = "02",
            questionCount = 100,
            answerKeyCount = 100,
            explanationCount = 100,
            rawText = ""
        )

        val p3 = PaperEntity(
            title = "Ethics Integrity Aptitude Case Studies",
            subject = "General Studies",
            topic = "Ethics",
            setNumber = "01",
            questionCount = 50,
            answerKeyCount = 50,
            explanationCount = 50,
            rawText = ""
        )

        assertEquals("CSAT / Paper II", getStageCategory(p1))
        assertEquals("GS Paper I", getStageCategory(p2))
        assertEquals("GS Paper IV (Ethics)", getStageCategory(p3))
    }
}
