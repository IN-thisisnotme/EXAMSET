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

    /**
     * Builds all user folder paths preserving Parent > Child > Sub-Child tree order.
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
                val levelLabel = when (depth) {
                    1 -> "Parent"
                    2 -> "Child"
                    else -> "Sub-Child"
                }
                ordered.add(
                    FolderPathSuggestion(
                        levelLabel = levelLabel,
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
                    val levelLabel = when (depth) {
                        1 -> "Parent"
                        2 -> "Child"
                        else -> "Sub-Child"
                    }
                    ordered.add(
                        FolderPathSuggestion(
                            levelLabel = levelLabel,
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
     * Searches strictly within the user's own folders (Parent > Child > Sub-Child).
     * Returns an empty list if the user has not created any folders yet.
     */
    fun searchUserFolders(
        query: String,
        folders: List<FolderItem>,
        maxResults: Int = 12
    ): List<FolderPathSuggestion> {
        val pool = buildUserFolderSuggestions(folders)
        if (pool.isEmpty()) return emptyList()

        val cleanQ = query.trim().replace("/", ">").lowercase()
        if (cleanQ.isBlank()) {
            return pool.take(maxResults)
        }

        val isSubfolderQuery = cleanQ.contains(">")
        val activeSegment = if (isSubfolderQuery) cleanQ.substringAfterLast(">").trim() else cleanQ
        val parentSegment = if (isSubfolderQuery) cleanQ.substringBeforeLast(">").trim() else ""

        return pool
            .asSequence()
            .map { entry ->
                val fullLower = entry.fullPath.lowercase()
                val segments = fullLower.split(">").map { it.trim() }
                val words = fullLower.split(" ", ">", "/", "-", "(", ")", ",").filter { it.isNotBlank() }

                var score = 0
                if (isSubfolderQuery) {
                    val parentMatches = parentSegment.isBlank() || fullLower.contains(parentSegment)
                    if (parentMatches) {
                        score += 50
                        if (activeSegment.isBlank()) {
                            score += 30
                        } else {
                            val segmentMatchesPrefix = segments.any { it.startsWith(activeSegment) }
                            val wordMatchesPrefix = words.any { it.startsWith(activeSegment) }
                            if (segmentMatchesPrefix) score += 60
                            else if (wordMatchesPrefix) score += 40
                            else if (fullLower.contains(activeSegment)) score += 10
                            else score = 0
                        }
                    } else {
                        score = 0
                    }
                } else {
                    val startsWithExact = fullLower.startsWith(cleanQ)
                    val leafStartsWith = entry.leafName.lowercase().startsWith(cleanQ)
                    val segmentStartsWith = segments.any { it.startsWith(cleanQ) }
                    val wordStartsWith = words.any { it.startsWith(cleanQ) }

                    if (startsWithExact) {
                        score += 100
                    } else if (leafStartsWith) {
                        score += 90
                    } else if (segmentStartsWith) {
                        score += 80
                    } else if (wordStartsWith) {
                        score += 60
                    } else if (fullLower.contains(cleanQ)) {
                        score += 20
                    }
                }

                entry to score
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
            .distinctBy { it.fullPath }
            .take(maxResults)
            .toList()
    }
}
