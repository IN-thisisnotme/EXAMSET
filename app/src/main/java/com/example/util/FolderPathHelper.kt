package com.example.util

import com.example.model.FolderItem

/**
 * Dynamic user-folder hierarchy suggestion built exclusively from the user's
 * Parent > Child > Sub-Child folders (with zero static exam catalogue dependency).
 */
data class FolderPathSuggestion(
    val levelLabel: String,
    val fullPath: String,
    val leafName: String,
    val accentColor: String = "#0284C7",
    val depth: Int = 1
)

object FolderPathHelper {

    private const val LITERAL_SLASH_PLACEHOLDER = "\u0000LITERAL_SLASH\u0000"

    /**
     * Normalizes user-entered `/` or `>` hierarchy separators into canonical `Parent > Child > Sub-Child` format.
     */
    fun normalizeFolderPath(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw
            .replace("/", " > ")
            .split(">")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" > ")
    }

    /**
     * Formats a stored subject/folder string for UI display using `>` separators instead of `/`,
     * while preserving escaped `//` as a literal `/` inside folder names.
     */
    fun formatDisplayPath(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val withEscapedPlaceholders = raw.replace("//", LITERAL_SLASH_PLACEHOLDER)
        return withEscapedPlaceholders
            .replace("/", " > ")
            .split(">")
            .map { it.replace(LITERAL_SLASH_PLACEHOLDER, "/").trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" > ")
    }

    /**
     * Resolves the full `Parent > Child > Sub-Child` breadcrumb path for a folder ID.
     */
    fun getFolderHierarchyPath(folderId: String?, folders: List<FolderItem>): String {
        if (folderId.isNullOrBlank() || folderId == "all" || folders.isEmpty()) return ""
        val folderById = folders.associateBy { it.id }
        val chain = mutableListOf<String>()
        var curr = folderById[folderId]
        val visited = mutableSetOf<String>()
        while (curr != null && visited.add(curr.id)) {
            val cleanName = curr.name.trim()
            if (cleanName.isNotEmpty()) {
                chain.add(0, cleanName)
            }
            curr = curr.parentId?.let { folderById[it] }
        }
        return chain.joinToString(" > ")
    }

    private fun levelLabelForDepth(depth: Int): String = when (depth) {
        1 -> "Parent"
        2 -> "Child"
        3 -> "Sub-Child"
        else -> "L$depth"
    }

    /**
     * Builds all user folder paths preserving Parent > Child > Sub-Child > L4... tree order.
     */
    fun buildUserFolderSuggestions(folders: List<FolderItem>): List<FolderPathSuggestion> {
        if (folders.isEmpty()) return emptyList()
        val ordered = mutableListOf<FolderPathSuggestion>()
        val visited = mutableSetOf<String>()

        fun traverse(parentId: String?, prefixSegments: List<String>) {
            val children = folders.filter { it.parentId == parentId }
            for (child in children) {
                if (!visited.add(child.id)) continue
                val cleanName = child.name.trim()
                if (cleanName.isEmpty()) continue
                val nextSegments = prefixSegments + cleanName
                val fullPath = nextSegments.joinToString(" > ")
                val depth = nextSegments.size
                ordered.add(
                    FolderPathSuggestion(
                        levelLabel = levelLabelForDepth(depth),
                        fullPath = fullPath,
                        leafName = cleanName,
                        accentColor = child.color,
                        depth = depth
                    )
                )
                if (depth < 25) {
                    traverse(child.id, nextSegments)
                }
            }
        }

        traverse(parentId = null, prefixSegments = emptyList())

        // Fallback for any orphaned folder whose parentId is missing
        for (folder in folders) {
            if (visited.add(folder.id)) {
                val fullPath = getFolderHierarchyPath(folder.id, folders).ifBlank { folder.name.trim() }
                if (fullPath.isNotEmpty()) {
                    val segments = fullPath.split(">").map { it.trim() }.filter { it.isNotEmpty() }
                    val depth = segments.size.coerceAtLeast(1)
                    ordered.add(
                        FolderPathSuggestion(
                            levelLabel = levelLabelForDepth(depth),
                            fullPath = fullPath,
                            leafName = segments.lastOrNull() ?: fullPath,
                            accentColor = folder.color,
                            depth = depth
                        )
                    )
                }
            }
        }

        return ordered.distinctBy { it.fullPath }
    }

    /**
     * Searches strictly within the user's own folders (Parent > Child > Sub-Child > L4...)
     * using unambiguous starting-character prefix matching:
     * 1. Parent folder name first
     * 2. Followed by Child folder name
     * 3. Then Sub-Child / L4+
     * No substring contains() fallback is used; e.g. typing "y" will not match "xyz".
     */
    fun searchUserFolders(
        query: String,
        folders: List<FolderItem>,
        maxResults: Int = Int.MAX_VALUE
    ): List<FolderPathSuggestion> {
        val pool = buildUserFolderSuggestions(folders)
        if (pool.isEmpty()) return emptyList()

        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return if (maxResults == Int.MAX_VALUE) pool else pool.take(maxResults)
        }

        val escaped = trimmed.replace("//", LITERAL_SLASH_PLACEHOLDER)
        val hasTrailingSeparator = escaped.endsWith(">") || escaped.endsWith("/")
        val rawSegments = escaped
            .replace("/", ">")
            .split(">")
            .map { it.replace(LITERAL_SLASH_PLACEHOLDER, "/").trim() }
            .filter { it.isNotEmpty() }

        if (rawSegments.isEmpty()) {
            return if (maxResults == Int.MAX_VALUE) pool else pool.take(maxResults)
        }

        val querySegments = if (hasTrailingSeparator) rawSegments + "" else rawSegments

        val matches = pool.filter { entry ->
            val entrySegments = entry.fullPath.split(">").map { it.trim() }
            if (entrySegments.size < querySegments.size) return@filter false

            for (i in 0 until (querySegments.size - 1)) {
                if (!entrySegments[i].equals(querySegments[i], ignoreCase = true)) {
                    return@filter false
                }
            }

            val activePrefix = querySegments.last()
            if (activePrefix.isEmpty()) {
                true
            } else {
                entrySegments[querySegments.size - 1].startsWith(activePrefix, ignoreCase = true)
            }
        }

        return if (maxResults == Int.MAX_VALUE) matches else matches.take(maxResults)
    }
}
