package com.example.model

import com.example.data.PaperEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
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

    @Test
    fun testExamCatalogueRegistryMultiTokenSearch() {
        val jeResults = com.example.util.ExamCatalogueRegistry.searchCatalogue("je")
        assertTrue("Should return JE exams", jeResults.isNotEmpty())
        assertTrue("Should contain SSC JE or RRB JE", jeResults.any { it.fullPath.contains("JE") })

        val bpscResults = com.example.util.ExamCatalogueRegistry.searchCatalogue("bpsc")
        assertTrue("Should return BPSC exams", bpscResults.isNotEmpty())
        assertTrue("Should contain Bihar BPSC", bpscResults.any { it.fullPath.contains("BPSC") })

        val sscResults = com.example.util.ExamCatalogueRegistry.searchCatalogue("ssc cgl")
        assertTrue("Should return SSC CGL", sscResults.isNotEmpty())
        assertTrue("First match should be CGL", sscResults[0].fullPath.contains("CGL"))

        val neetResults = com.example.util.ExamCatalogueRegistry.searchCatalogue("neet")
        assertTrue("Should match Medical NEET", neetResults.any { it.category == "Medical" && it.fullPath.contains("NEET") })
    }

    @Test
    fun testDynamicSlashPathResolution() {
        val testFolders = mutableListOf<FolderItem>()
        fun resolveOrCreate(path: String): FolderItem? {
            val segments = path.split("/", ">").map { it.trim() }.filter { it.isNotBlank() }
            if (segments.isEmpty()) return null
            var parentId: String? = null
            var leaf: FolderItem? = null
            for (seg in segments) {
                val exist = testFolders.find { it.parentId == parentId && it.name.equals(seg, ignoreCase = true) }
                if (exist != null) {
                    leaf = exist
                    parentId = exist.id
                } else {
                    val created = FolderItem(id = "f-${testFolders.size + 1}", name = seg, color = "#0284C7", parentId = parentId)
                    testFolders.add(created)
                    leaf = created
                    parentId = created.id
                }
            }
            return leaf
        }

        val leaf = resolveOrCreate("State AE-JE / PWD / Civil Engineering")
        assertNotNull(leaf)
        assertEquals("Civil Engineering", leaf!!.name)
        assertEquals(3, testFolders.size)

        // Root folder
        val root = testFolders.find { it.name == "State AE-JE" }
        assertNotNull(root)
        assertNull(root!!.parentId)

        // Middle subfolder
        val sub = testFolders.find { it.name == "PWD" }
        assertNotNull(sub)
        assertEquals(root.id, sub!!.parentId)

        // Leaf subfolder
        assertEquals(sub.id, leaf.parentId)
    }

    @Test
    fun testUserFolderWithCatalogueSearch() {
        val customFolders = listOf(
            FolderItem(id = "cf-1", name = "My Personal UPSC Prelims", color = "#0284C7")
        )
        val searchCustom = com.example.util.ExamCatalogueRegistry.searchCatalogue("personal upsc", customFolders)
        assertTrue(searchCustom.any { it.fullPath == "My Personal UPSC Prelims" && it.category == "Custom Folder" })
    }

    @Test
    fun testBatchMoveAndDeepSearch() {
        val root = FolderItem(id = "root-1", name = "SSC", color = "#2563EB")
        val sub1 = FolderItem(id = "sub-1", name = "CGL", color = "#1D4ED8", parentId = root.id)
        val sub2 = FolderItem(id = "sub-2", name = "CHSL", color = "#0284C7", parentId = root.id)
        val allFolders = listOf(root, sub1, sub2)

        val papers = listOf(
            PaperEntity(id = 1L, title = "CGL Mock 1", subject = "CGL", topic = "Maths", setNumber = "01", questionCount = 25, answerKeyCount = 25, explanationCount = 25, rawText = "", folderId = sub1.id),
            PaperEntity(id = 2L, title = "CGL Mock 2", subject = "CGL", topic = "Reasoning", setNumber = "02", questionCount = 25, answerKeyCount = 25, explanationCount = 25, rawText = "", folderId = sub1.id),
            PaperEntity(id = 3L, title = "CHSL Mock 1", subject = "CHSL", topic = "English", setNumber = "01", questionCount = 25, answerKeyCount = 25, explanationCount = 25, rawText = "", folderId = sub2.id)
        )

        // 1. Test batch selection
        val selectedIds = setOf(1L, 2L)
        val movedPapers = papers.map { p ->
            if (p.id in selectedIds) {
                p.copy(folderId = sub2.id, subject = "SSC > CHSL")
            } else p
        }
        assertEquals(3, movedPapers.count { it.folderId == sub2.id })

        // 2. Test deep search across all subfolders
        val query = "Mock 1"
        val deepMatches = movedPapers.filter { it.title.contains(query) }
        assertEquals(2, deepMatches.size)
    }

    @Test
    fun testExamStageFilterClassification() {
        val prelimsPaper = PaperEntity(
            title = "UPSC CSE Prelims Mock Paper 1",
            subject = "General Studies 1",
            topic = "Polity",
            setNumber = "01",
            questionCount = 100,
            answerKeyCount = 100,
            explanationCount = 100,
            rawText = ""
        )
        val csatPaper = PaperEntity(
            title = "CSAT Aptitude & Comprehension",
            subject = "Paper 2",
            topic = "Logical Reasoning",
            setNumber = "01",
            questionCount = 80,
            answerKeyCount = 80,
            explanationCount = 80,
            rawText = ""
        )
        val mainsPaper = PaperEntity(
            title = "BPSC Mains Tier 2 Practice Set",
            subject = "Descriptive",
            topic = "Economy",
            setNumber = "01",
            questionCount = 20,
            answerKeyCount = 20,
            explanationCount = 20,
            rawText = ""
        )
        val techPaper = PaperEntity(
            title = "SSC JE Mechanical Engineering Paper 1",
            subject = "Technical",
            topic = "Thermodynamics",
            setNumber = "01",
            questionCount = 100,
            answerKeyCount = 100,
            explanationCount = 100,
            rawText = ""
        )

        // Test ALL filter
        assertTrue(com.example.ui.screens.matchesStageFilter(prelimsPaper, com.example.ui.screens.ExamStageFilter.ALL))
        assertTrue(com.example.ui.screens.matchesStageFilter(csatPaper, com.example.ui.screens.ExamStageFilter.ALL))

        // Test PRELIMS_TIER1 filter
        assertTrue(com.example.ui.screens.matchesStageFilter(prelimsPaper, com.example.ui.screens.ExamStageFilter.PRELIMS_TIER1))
        assertTrue(!com.example.ui.screens.matchesStageFilter(mainsPaper, com.example.ui.screens.ExamStageFilter.PRELIMS_TIER1))

        // Test MAINS_TIER2 filter
        assertTrue(com.example.ui.screens.matchesStageFilter(mainsPaper, com.example.ui.screens.ExamStageFilter.MAINS_TIER2))
        assertTrue(!com.example.ui.screens.matchesStageFilter(csatPaper, com.example.ui.screens.ExamStageFilter.MAINS_TIER2))

        // Test GS_PAPERS filter
        assertTrue(com.example.ui.screens.matchesStageFilter(prelimsPaper, com.example.ui.screens.ExamStageFilter.GS_PAPERS))

        // Test CSAT_APTITUDE filter
        assertTrue(com.example.ui.screens.matchesStageFilter(csatPaper, com.example.ui.screens.ExamStageFilter.CSAT_APTITUDE))
        assertTrue(!com.example.ui.screens.matchesStageFilter(techPaper, com.example.ui.screens.ExamStageFilter.CSAT_APTITUDE))

        // Test TECHNICAL_OPTIONAL filter
        assertTrue(com.example.ui.screens.matchesStageFilter(techPaper, com.example.ui.screens.ExamStageFilter.TECHNICAL_OPTIONAL))
    }

    @Test
    fun testFolderExportPackaging() {
        val p1 = PaperEntity(
            id = 10L,
            title = "Ancient History Set 1",
            subject = "GS 1",
            topic = "Indus Valley",
            setNumber = "01",
            exam = "UPSC CSE",
            questionCount = 10,
            answerKeyCount = 10,
            explanationCount = 10,
            rawText = "1. Question 1?\n(A) Opt A\n(B) Opt B\n(C) Opt C\n(D) Opt D\nAnswer: A\n",
            folderId = "f-history"
        )
        val p2 = PaperEntity(
            id = 11L,
            title = "Medieval History Set 1",
            subject = "GS 1",
            topic = "Mughals",
            setNumber = "02",
            exam = "UPSC CSE",
            questionCount = 10,
            answerKeyCount = 10,
            explanationCount = 10,
            rawText = "1. Question 2?\n(A) Opt A\n(B) Opt B\n(C) Opt C\n(D) Opt D\nAnswer: B\n",
            folderId = "f-history"
        )

        val folderPapers = listOf(p1, p2)
        val questionPapers = folderPapers.map { entity ->
            val parsed = com.example.util.McqParser.parse(entity.rawText)
            parsed.copy(
                id = entity.id,
                metadata = com.example.model.PaperMetadata(
                    id = "paper-${entity.id}",
                    title = entity.title,
                    subject = entity.subject,
                    topic = entity.topic,
                    setNumber = entity.setNumber,
                    exam = entity.exam,
                    folderId = entity.folderId
                )
            )
        }

        val jsonBundle = com.example.util.ExamJsonHelper.bundleToJson("History Package", "f-history", questionPapers)
        assertNotNull(jsonBundle)
        assertTrue(jsonBundle.contains("History Package"))
        assertTrue(jsonBundle.contains("f-history"))
        assertTrue(jsonBundle.contains("Ancient History Set 1"))
        assertTrue(jsonBundle.contains("Medieval History Set 1"))

        // Formatted text test
        val formattedSummary = buildString {
            folderPapers.forEachIndexed { idx, entity ->
                append("EXAM SET ${idx + 1}: ${entity.title}\n")
                append("Subject: ${entity.subject} | Set: ${entity.setNumber}\n")
                append(entity.rawText.trim())
                append("\n\n")
            }
        }
        assertTrue(formattedSummary.contains("EXAM SET 1: Ancient History Set 1"))
        assertTrue(formattedSummary.contains("EXAM SET 2: Medieval History Set 1"))
    }
}

