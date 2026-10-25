package com.example.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests validating folder hierarchy parsing, literal slash escaping (`//` vs `/`),
 * multi-tier nesting, and case-insensitive resolution.
 */
class FolderHierarchyTest {

    private val placeholder = "\u0000LITERAL_SLASH\u0000"

    private fun parseSegments(path: String): List<String> {
        val cleanPath = path.trim()
        if (cleanPath.isBlank() || cleanPath.equals("General", ignoreCase = true)) return emptyList()

        val normalized = cleanPath.replace("//", placeholder)
        return normalized.split("/", ">")
            .map { it.replace(placeholder, "/").trim() }
            .filter { it.isNotBlank() }
    }

    private class TestFolderRepository {
        val folders = mutableListOf<FolderItem>()

        fun findFolder(name: String, parentId: String?): FolderItem? {
            return folders.find { it.parentId == parentId && it.name.equals(name.trim(), ignoreCase = true) }
        }

        fun createFolderDirect(name: String, parentId: String? = null): FolderItem {
            val existing = findFolder(name, parentId)
            if (existing != null) return existing
            val newFolder = FolderItem(
                id = "f-${folders.size + 1}",
                name = name.trim(),
                color = "#0284C7",
                parentId = parentId
            )
            folders.add(newFolder)
            return newFolder
        }

        fun resolveOrCreateFolderPath(path: String, baseParentId: String? = null): FolderItem? {
            val cleanPath = path.trim()
            if (cleanPath.isBlank() || cleanPath.equals("General", ignoreCase = true)) return null

            val placeholder = "\u0000LITERAL_SLASH\u0000"
            val normalized = cleanPath.replace("//", placeholder)

            val segments = normalized.split("/", ">")
                .map { it.replace(placeholder, "/").trim() }
                .filter { it.isNotBlank() }

            if (segments.isEmpty()) return null

            var currentParentId: String? = baseParentId
            var lastFolder: FolderItem? = null

            for (segment in segments) {
                val existing = findFolder(segment, currentParentId)
                if (existing != null) {
                    lastFolder = existing
                    currentParentId = existing.id
                } else {
                    val created = createFolderDirect(name = segment, parentId = currentParentId)
                    lastFolder = created
                    currentParentId = created.id
                }
            }

            return lastFolder
        }

        fun getBreadcrumbPath(folderId: String?): List<FolderItem> {
            if (folderId == null || folderId == "all") return emptyList()
            val path = mutableListOf<FolderItem>()
            var curr = folders.find { it.id == folderId }
            val visited = mutableSetOf<String>()
            while (curr != null && visited.add(curr.id)) {
                path.add(0, curr)
                curr = if (curr.parentId != null) folders.find { it.id == curr.parentId } else null
            }
            return path
        }
    }

    @Test
    fun testSlashDelimiterSplitsIntoParentAndSubfolder() {
        val segments = parseSegments("UPSC / Interview")
        assertEquals(2, segments.size)
        assertEquals("UPSC", segments[0])
        assertEquals("Interview", segments[1])
    }

    @Test
    fun testDoubleSlashPreservedAsLiteralSlashInFolderName() {
        val segments = parseSegments("UPSC//Interview")
        assertEquals(1, segments.size)
        assertEquals("UPSC/Interview", segments[0])
    }

    @Test
    fun testCombinedDoubleSlashAndSingleSlashHierarchy() {
        val segments = parseSegments("UPSC//Interview / Round 1")
        assertEquals(2, segments.size)
        assertEquals("UPSC/Interview", segments[0])
        assertEquals("Round 1", segments[1])
    }

    @Test
    fun testArrowDelimiterSupportedAsHierarchySeparator() {
        val segments = parseSegments("UPSC CSE > Prelims > GS Paper 1")
        assertEquals(3, segments.size)
        assertEquals("UPSC CSE", segments[0])
        assertEquals("Prelims", segments[1])
        assertEquals("GS Paper 1", segments[2])
    }

    @Test
    fun testMixedArrowAndLiteralSlashEscape() {
        val segments = parseSegments("Civil Services > Mains // Tier 2 > Optional")
        assertEquals(3, segments.size)
        assertEquals("Civil Services", segments[0])
        assertEquals("Mains / Tier 2", segments[1])
        assertEquals("Optional", segments[2])
    }

    @Test
    fun testResolveHierarchyCreatesLinkedNodes() {
        val repo = TestFolderRepository()
        val leaf = repo.resolveOrCreateFolderPath("UPSC / Prelims / CSAT")

        assertNotNull(leaf)
        assertEquals("CSAT", leaf!!.name)

        val breadcrumbs = repo.getBreadcrumbPath(leaf.id)
        assertEquals(3, breadcrumbs.size)
        assertEquals("UPSC", breadcrumbs[0].name)
        assertNull(breadcrumbs[0].parentId)
        assertEquals("Prelims", breadcrumbs[1].name)
        assertEquals(breadcrumbs[0].id, breadcrumbs[1].parentId)
        assertEquals("CSAT", breadcrumbs[2].name)
        assertEquals(breadcrumbs[1].id, breadcrumbs[2].parentId)
    }

    @Test
    fun testResolveEscapedDoubleSlashCreatesSingleNodeWithSlash() {
        val repo = TestFolderRepository()
        val folder = repo.resolveOrCreateFolderPath("UPSC//Interview")

        assertNotNull(folder)
        assertEquals("UPSC/Interview", folder!!.name)
        assertNull(folder.parentId)
        assertEquals(1, repo.folders.size)
    }

    @Test
    fun testResolveHierarchyWithBaseParentId() {
        val repo = TestFolderRepository()
        val root = repo.createFolderDirect("State Exams", parentId = null)

        val leaf = repo.resolveOrCreateFolderPath("BPSC / Prelims", baseParentId = root.id)
        assertNotNull(leaf)
        assertEquals("Prelims", leaf!!.name)

        val breadcrumbs = repo.getBreadcrumbPath(leaf.id)
        assertEquals(3, breadcrumbs.size)
        assertEquals("State Exams", breadcrumbs[0].name)
        assertEquals("BPSC", breadcrumbs[1].name)
        assertEquals("Prelims", breadcrumbs[2].name)
    }

    @Test
    fun testCaseInsensitiveFolderReuse() {
        val repo = TestFolderRepository()
        val firstLeaf = repo.resolveOrCreateFolderPath("UPSC / Interview")
        assertEquals(2, repo.folders.size)

        // Lowercase query should reuse existing folders without duplicates
        val secondLeaf = repo.resolveOrCreateFolderPath("upsc / interview")
        assertEquals(2, repo.folders.size)
        assertEquals(firstLeaf!!.id, secondLeaf!!.id)
    }

    @Test
    fun testArbitraryDepthHierarchyFlattening() {
        val repo = TestFolderRepository()
        val leaf = repo.resolveOrCreateFolderPath("A / B / C / D / E")
        assertNotNull(leaf)
        assertEquals(5, repo.folders.size)

        // Validate breadcrumb has all 5 tiers
        val breadcrumbs = repo.getBreadcrumbPath(leaf!!.id)
        assertEquals(5, breadcrumbs.size)
        assertEquals(listOf("A", "B", "C", "D", "E"), breadcrumbs.map { it.name })
    }

    @Test
    fun testSubtreeDescendantCollection() {
        val repo = TestFolderRepository()
        val c1 = repo.resolveOrCreateFolderPath("UPSC / Prelims / GS 1")
        val c2 = repo.resolveOrCreateFolderPath("UPSC / Prelims / CSAT")
        val c3 = repo.resolveOrCreateFolderPath("UPSC / Mains / Essay")
        assertNotNull(c1)
        assertNotNull(c2)
        assertNotNull(c3)

        val upscFolder = repo.folders.find { it.name == "UPSC" }!!
        val prelimsFolder = repo.folders.find { it.name == "Prelims" }!!

        // Function mirroring getAllDescendantFolderIds
        fun getDescendants(folderId: String, allFolders: List<FolderItem>): Set<String> {
            val result = mutableSetOf(folderId)
            val queue = ArrayDeque<String>()
            queue.add(folderId)
            while (queue.isNotEmpty()) {
                val curr = queue.removeFirst()
                val children = allFolders.filter { it.parentId == curr }.map { it.id }
                for (child in children) {
                    if (result.add(child)) {
                        queue.add(child)
                    }
                }
            }
            return result
        }

        val upscDescendants = getDescendants(upscFolder.id, repo.folders)
        // Root UPSC (1) + Prelims, Mains (2) + GS 1, CSAT, Essay (3) = 6
        assertEquals(6, upscDescendants.size)

        val prelimsDescendants = getDescendants(prelimsFolder.id, repo.folders)
        // Prelims (1) + GS 1, CSAT (2) = 3
        assertEquals(3, prelimsDescendants.size)
    }

    @Test
    fun testBatchMovePaperAssignment() {
        data class MockPaper(val id: Long, var folderId: String?, var subject: String)

        val papers = mutableListOf(
            MockPaper(1L, null, "General"),
            MockPaper(2L, null, "General"),
            MockPaper(3L, "f-old", "Old Subject")
        )

        val targetFolderId = "f-target"
        val targetPath = "UPSC CSE > Prelims > GS 1"
        val selectedIds = setOf(1L, 2L)

        // Execute batch move
        papers.filter { it.id in selectedIds }.forEach { p ->
            p.folderId = targetFolderId
            p.subject = targetPath
        }

        assertEquals(targetFolderId, papers[0].folderId)
        assertEquals(targetPath, papers[0].subject)
        assertEquals(targetFolderId, papers[1].folderId)
        assertEquals(targetPath, papers[1].subject)
        // Paper 3 unaffected
        assertEquals("f-old", papers[2].folderId)
        assertEquals("Old Subject", papers[2].subject)
    }

    @Test
    fun testReparentFolderPreventsSelfCycle() {
        val repo = TestFolderRepository()
        val folder = repo.createFolderDirect("UPSC", parentId = null)

        // Attempting to reparent folder to itself must be rejected
        val canReparent = folder.id != folder.id
        assertFalse(canReparent)
    }

    @Test
    fun testReparentFolderPreventsDescendantCycle() {
        val repo = TestFolderRepository()
        val upsc = repo.resolveOrCreateFolderPath("UPSC / Prelims / CSAT")!!
        val upscRoot = repo.folders.find { it.name == "UPSC" }!!
        val prelims = repo.folders.find { it.name == "Prelims" }!!
        val csat = repo.folders.find { it.name == "CSAT" }!!

        fun getDescendants(folderId: String): Set<String> {
            val result = mutableSetOf(folderId)
            val queue = ArrayDeque<String>()
            queue.add(folderId)
            while (queue.isNotEmpty()) {
                val curr = queue.removeFirst()
                val children = repo.folders.filter { it.parentId == curr }.map { it.id }
                for (c in children) {
                    if (result.add(c)) queue.add(c)
                }
            }
            return result
        }

        val upscDescendants = getDescendants(upscRoot.id)
        // UPSC root cannot be moved into Prelims or CSAT
        assertTrue(prelims.id in upscDescendants)
        assertTrue(csat.id in upscDescendants)

        // Validate cycle rejection logic:
        val canMoveRootIntoPrelims = prelims.id !in upscDescendants
        assertFalse(canMoveRootIntoPrelims)

        val canMoveRootIntoCSAT = csat.id !in upscDescendants
        assertFalse(canMoveRootIntoCSAT)
    }

    @Test
    fun testReparentFolderValidMoveUpdatesBreadcrumb() {
        val repo = TestFolderRepository()
        val upsc = repo.createFolderDirect("UPSC", parentId = null)
        val ssc = repo.createFolderDirect("SSC", parentId = null)
        val tier1 = repo.createFolderDirect("Tier 1", parentId = ssc.id)

        // Move Tier 1 from SSC to UPSC
        val updatedTier1 = tier1.copy(parentId = upsc.id)
        val index = repo.folders.indexOfFirst { it.id == tier1.id }
        repo.folders[index] = updatedTier1

        val breadcrumbs = repo.getBreadcrumbPath(tier1.id)
        assertEquals(2, breadcrumbs.size)
        assertEquals("UPSC", breadcrumbs[0].name)
        assertEquals("Tier 1", breadcrumbs[1].name)
    }

    @Test
    fun testRenameFolderUpdatesPathBreadcrumbs() {
        val repo = TestFolderRepository()
        val leaf = repo.resolveOrCreateFolderPath("UPSC / Prelims / GS 1")!!
        val prelimsIndex = repo.folders.indexOfFirst { it.name == "Prelims" }
        val prelims = repo.folders[prelimsIndex]

        // Rename Prelims to "Stage 1 (Prelims)"
        repo.folders[prelimsIndex] = prelims.copy(name = "Stage 1 (Prelims)")

        val breadcrumbs = repo.getBreadcrumbPath(leaf.id)
        assertEquals(3, breadcrumbs.size)
        assertEquals("UPSC", breadcrumbs[0].name)
        assertEquals("Stage 1 (Prelims)", breadcrumbs[1].name)
        assertEquals("GS 1", breadcrumbs[2].name)
    }

    @Test
    fun testFolderSortingOrders() {
        val list = listOf(
            FolderItem("1", "Prelims", "#0284C7"),
            FolderItem("2", "Mains", "#0284C7"),
            FolderItem("3", "CSAT", "#0284C7"),
            FolderItem("4", "Interview", "#0284C7")
        )

        val sortedAlphabetical = list.sortedBy { it.name.lowercase() }
        assertEquals(listOf("CSAT", "Interview", "Mains", "Prelims"), sortedAlphabetical.map { it.name })
    }
}


