package com.cbztagger.app.core

data class BookmarkRowDraft(
    val id: Long,
    val pageText: String,
    val name: String,
)

/**
 * Pure-Kotlin helpers for batch page attribute editing and bulk bookmark management.
 */
object PageBatchHelper {

    fun ensurePagesSize(existing: List<PageInfo>, pageCount: Int): List<PageInfo> {
        if (pageCount <= 0) return emptyList()
        val byIndex = existing.associateBy { it.image }
        return (0 until pageCount).map { idx ->
            byIndex[idx] ?: PageInfo(image = idx)
        }
    }

    fun applyTypeToSelection(
        existing: List<PageInfo>,
        pageCount: Int,
        selectedIndices: Set<Int>,
        pageTypeXmlValue: String,
    ): List<PageInfo> {
        val normalized = ensurePagesSize(existing, pageCount)
        return normalized.mapIndexed { idx, page ->
            if (idx in selectedIndices) page.copy(type = pageTypeXmlValue) else page
        }
    }

    fun applyDoublePageToSelection(
        existing: List<PageInfo>,
        pageCount: Int,
        selectedIndices: Set<Int>,
        doublePage: Boolean?,
    ): List<PageInfo> {
        val normalized = ensurePagesSize(existing, pageCount)
        return normalized.mapIndexed { idx, page ->
            if (idx in selectedIndices) page.copy(doublePage = doublePage) else page
        }
    }

    /**
     * Sets all pages whose `type` is currently blank to "Story", keeping already-tagged pages untouched.
     */
    fun fillEmptyTypesWithStory(
        existing: List<PageInfo>,
        pageCount: Int,
    ): Pair<List<PageInfo>, Int> {
        val normalized = ensurePagesSize(existing, pageCount)
        var changedCount = 0
        val updated = normalized.map { page ->
            if (page.type.isBlank()) {
                changedCount++
                page.copy(type = PageType.STORY.xmlValue)
            } else {
                page
            }
        }
        return updated to changedCount
    }

    fun countEmptyTypes(existing: List<PageInfo>, pageCount: Int): Int {
        val normalized = ensurePagesSize(existing, pageCount)
        return normalized.count { it.type.isBlank() }
    }

    fun parseRangeIndices(startPage1Based: Int, endPage1Based: Int, pageCount: Int): Set<Int> {
        if (pageCount <= 0) return emptySet()
        val start = minOf(startPage1Based, endPage1Based).coerceIn(1, pageCount)
        val end = maxOf(startPage1Based, endPage1Based).coerceIn(1, pageCount)
        return ((start - 1)..(end - 1)).toSet()
    }

    /**
     * Applies bulk bookmark entries (1-based page number -> bookmark name) to the page list.
     * Pages not present in the valid entries have their bookmarks cleared; other page fields are preserved.
     */
    fun applyBulkBookmarks(
        existing: List<PageInfo>,
        pageCount: Int,
        entries: List<Pair<Int, String>>,
    ): List<PageInfo> {
        val normalized = ensurePagesSize(existing, pageCount)
        val bookmarkByZeroIndex = mutableMapOf<Int, String>()
        for ((page1Based, rawName) in entries) {
            val trimmed = rawName.trim()
            val zeroIdx = page1Based - 1
            if (zeroIdx in 0 until pageCount && trimmed.isNotEmpty()) {
                bookmarkByZeroIndex[zeroIdx] = trimmed
            }
        }
        return normalized.mapIndexed { idx, page ->
            page.copy(bookmark = bookmarkByZeroIndex[idx].orEmpty())
        }
    }
}
