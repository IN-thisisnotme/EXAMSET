package com.example.model

import org.junit.Assert.assertEquals
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
}
