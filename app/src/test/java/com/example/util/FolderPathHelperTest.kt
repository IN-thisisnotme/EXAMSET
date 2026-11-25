package com.example.util

import com.example.model.FolderItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderPathHelperTest {

    @Test
    fun `searchUserFolders returns empty when user has no folders`() {
        assertTrue(FolderPathHelper.searchUserFolders("", emptyList()).isEmpty())
        assertTrue(FolderPathHelper.searchUserFolders("UPSC", emptyList()).isEmpty())
        assertTrue(FolderPathHelper.searchUserFolders("Engineering", emptyList()).isEmpty())
    }

    @Test
    fun `searchUserFolders builds Parent Child Sub-Child hierarchy exclusively from user folders`() {
        val folders = listOf(
            FolderItem(id = "p1", name = "Engineering", color = "#0284C7", parentId = null),
            FolderItem(id = "c1", name = "Thermodynamics", color = "#0284C7", parentId = "p1"),
            FolderItem(id = "sc1", name = "Mock Tests", color = "#0284C7", parentId = "c1"),
            FolderItem(id = "p2", name = "IGNOU", color = "#059669", parentId = null),
            FolderItem(id = "c2", name = "BCS-012", color = "#059669", parentId = "p2")
        )

        val allSuggestions = FolderPathHelper.searchUserFolders("", folders)
        assertEquals(
            listOf(
                "Engineering",
                "Engineering > Thermodynamics",
                "Engineering > Thermodynamics > Mock Tests",
                "IGNOU",
                "IGNOU > BCS-012"
            ),
            allSuggestions.map { it.fullPath }
        )
        assertEquals(listOf("Parent", "Child", "Sub-Child", "Parent", "Child"), allSuggestions.map { it.levelLabel })

        val filtered = FolderPathHelper.searchUserFolders("Engineering > Thermo", folders)
        assertEquals(
            listOf(
                "Engineering > Thermodynamics",
                "Engineering > Thermodynamics > Mock Tests"
            ),
            filtered.map { it.fullPath }
        )
    }

    @Test
    fun `searchUserFolders enforces strict starting-character prefix matching without middle contains fallback`() {
        val folders = listOf(
            FolderItem(id = "p1", name = "xyz", color = "#0284C7", parentId = null),
            FolderItem(id = "c1", name = "alpha", color = "#0284C7", parentId = "p1"),
            FolderItem(id = "sc1", name = "beta", color = "#0284C7", parentId = "c1"),
            FolderItem(id = "l4", name = "gamma", color = "#0284C7", parentId = "sc1"),
            FolderItem(id = "l5", name = "delta", color = "#0284C7", parentId = "l4")
        )

        // Starting with "x" matches "xyz" and its descendants in Parent > Child > Sub-Child > L4 > L5 order
        val xMatches = FolderPathHelper.searchUserFolders("x", folders)
        assertEquals(
            listOf(
                "xyz",
                "xyz > alpha",
                "xyz > alpha > beta",
                "xyz > alpha > beta > gamma",
                "xyz > alpha > beta > gamma > delta"
            ),
            xMatches.map { it.fullPath }
        )
        assertEquals(listOf("Parent", "Child", "Sub-Child", "L4", "L5"), xMatches.map { it.levelLabel })

        // Middle character "y" or descendant name without parent prefix must NOT match "xyz"
        assertTrue(FolderPathHelper.searchUserFolders("y", folders).isEmpty())
        assertTrue(FolderPathHelper.searchUserFolders("z", folders).isEmpty())
        assertTrue(FolderPathHelper.searchUserFolders("alpha", folders).isEmpty())

        // Trailing separator lists direct children and deeper descendants under Parent
        val afterSeparator = FolderPathHelper.searchUserFolders("xyz > ", folders)
        assertEquals(4, afterSeparator.size)
        assertEquals("xyz > alpha", afterSeparator.first().fullPath)

        // Strict child prefix matching
        val childPrefix = FolderPathHelper.searchUserFolders("xyz > al", folders)
        assertEquals(4, childPrefix.size)
        assertTrue(FolderPathHelper.searchUserFolders("xyz > lpha", folders).isEmpty())

        // Strict sub-child prefix matching
        val subChildPrefix = FolderPathHelper.searchUserFolders("xyz > alpha > be", folders)
        assertEquals(3, subChildPrefix.size)
        assertTrue(FolderPathHelper.searchUserFolders("xyz > alpha > eta", folders).isEmpty())
    }

    @Test
    fun `getFolderHierarchyPath and formatDisplayPath use arrow separator while preserving double slash escape`() {
        val folders = listOf(
            FolderItem(id = "p1", name = "Physics", color = "#0284C7", parentId = null),
            FolderItem(id = "c1", name = "Optics/Waves", color = "#0284C7", parentId = "p1"),
            FolderItem(id = "sc1", name = "Problem Sets", color = "#0284C7", parentId = "c1")
        )

        assertEquals(
            "Physics > Optics/Waves > Problem Sets",
            FolderPathHelper.getFolderHierarchyPath("sc1", folders)
        )
        assertEquals(
            "Science > Physics > Optics",
            FolderPathHelper.formatDisplayPath("Science / Physics / Optics")
        )
        assertEquals(
            "Physics > Optics/Waves > Problem Sets",
            FolderPathHelper.formatDisplayPath("Physics > Optics//Waves > Problem Sets")
        )
        assertEquals(
            listOf("Physics > Optics/Waves", "Physics > Optics/Waves > Problem Sets"),
            FolderPathHelper.searchUserFolders("Physics > Optics//", folders).map { it.fullPath }
        )
    }
}
