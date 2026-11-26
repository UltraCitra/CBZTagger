package com.cbztagger.app.core

/**
 * Pure-Kotlin helper for preview page navigation (arrows & horizontal swipe),
 * optionally following the ComicInfo `<Manga>` reading direction tag.
 */
object PagePreviewNavigation {
    fun isRtlDirection(mangaTag: String, followMangaTag: Boolean): Boolean {
        if (!followMangaTag) return false
        val trimmed = mangaTag.trim()
        return trimmed.equals("YesAndRightToLeft", ignoreCase = true) ||
            trimmed.equals("Yes", ignoreCase = true)
    }

    /** Target page index when tapping the physical left arrow (`<`), or null if out of bounds. */
    fun leftArrowTargetIndex(currentIndex: Int, pageCount: Int, isRtl: Boolean): Int? {
        if (pageCount <= 0 || currentIndex !in 0 until pageCount) return null
        val next = if (isRtl) currentIndex + 1 else currentIndex - 1
        return next.takeIf { it in 0 until pageCount }
    }

    /** Target page index when tapping the physical right arrow (`>`), or null if out of bounds. */
    fun rightArrowTargetIndex(currentIndex: Int, pageCount: Int, isRtl: Boolean): Int? {
        if (pageCount <= 0 || currentIndex !in 0 until pageCount) return null
        val next = if (isRtl) currentIndex - 1 else currentIndex + 1
        return next.takeIf { it in 0 until pageCount }
    }

    /**
     * Target page index after a horizontal drag gesture:
     * - `dragDeltaX < 0` (finger swiped right-to-left): LTR -> next, RTL -> prev
     * - `dragDeltaX > 0` (finger swiped left-to-right): LTR -> prev, RTL -> next
     */
    fun swipeTargetIndex(
        currentIndex: Int,
        pageCount: Int,
        dragDeltaX: Float,
        isRtl: Boolean,
        thresholdPx: Float = 60f,
    ): Int? {
        if (pageCount <= 0 || currentIndex !in 0 until pageCount) return null
        return when {
            dragDeltaX <= -thresholdPx -> {
                val target = if (isRtl) currentIndex - 1 else currentIndex + 1
                target.takeIf { it in 0 until pageCount }
            }
            dragDeltaX >= thresholdPx -> {
                val target = if (isRtl) currentIndex + 1 else currentIndex - 1
                target.takeIf { it in 0 until pageCount }
            }
            else -> null
        }
    }
}
