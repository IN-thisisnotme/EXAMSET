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
        assertTrue(filtered.any { it.fullPath == "Engineering > Thermodynamics" })
        assertTrue(filtered.any { it.fullPath == "Engineering > Thermodynamics > Mock Tests" })
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
    }
}
