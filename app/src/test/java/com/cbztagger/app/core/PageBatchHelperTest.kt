package com.cbztagger.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageBatchHelperTest {

    @Test
    fun applyTypeToSelectedPagesOnly() {
        val initial = listOf(
            PageInfo(image = 0, type = PageType.FRONT_COVER.xmlValue),
            PageInfo(image = 1, type = ""),
            PageInfo(image = 2, type = ""),
            PageInfo(image = 3, type = PageType.BACK_COVER.xmlValue),
        )
        val updated = PageBatchHelper.applyTypeToSelection(
            existing = initial,
            pageCount = 4,
            selectedIndices = setOf(1, 2),
            pageTypeXmlValue = PageType.STORY.xmlValue,
        )
        assertEquals("FrontCover", updated[0].type)
        assertEquals("Story", updated[1].type)
        assertEquals("Story", updated[2].type)
        assertEquals("BackCover", updated[3].type)
    }

    @Test
    fun fillEmptyTypesWithStoryPreservesExistingTypes() {
        val initial = listOf(
            PageInfo(image = 0, type = PageType.FRONT_COVER.xmlValue),
            PageInfo(image = 1, type = ""),
            PageInfo(image = 2, type = PageType.ADVERTISEMENT.xmlValue),
            PageInfo(image = 3, type = ""),
        )
        val (updated, changedCount) = PageBatchHelper.fillEmptyTypesWithStory(initial, 4)
        assertEquals(2, changedCount)
        assertEquals("FrontCover", updated[0].type)
        assertEquals("Story", updated[1].type)
        assertEquals("Advertisement", updated[2].type)
        assertEquals("Story", updated[3].type)
    }

    @Test
    fun parseRangeHandlesReversedAndClampedBounds() {
        val range = PageBatchHelper.parseRangeIndices(10, 2, pageCount = 5)
        assertEquals(setOf(1, 2, 3, 4), range)
    }

    @Test
    fun applyBulkBookmarksUpdatesAndClearsWhilePreservingTypes() {
        val initial = listOf(
            PageInfo(image = 0, type = PageType.FRONT_COVER.xmlValue, bookmark = "기존표지"),
            PageInfo(image = 1, type = PageType.STORY.xmlValue, bookmark = "삭제될북마크"),
            PageInfo(image = 2, type = PageType.STORY.xmlValue),
        )
        val updated = PageBatchHelper.applyBulkBookmarks(
            existing = initial,
            pageCount = 3,
            entries = listOf(1 to "새 표지", 3 to "1화 시작"),
        )
        assertEquals("FrontCover", updated[0].type)
        assertEquals("새 표지", updated[0].bookmark)
        assertEquals("Story", updated[1].type)
        assertEquals("", updated[1].bookmark)
        assertEquals("Story", updated[2].type)
        assertEquals("1화 시작", updated[2].bookmark)
    }

    @Test
    fun previewNavigationRespectsMangaDirectionToggle() {
        assertTrue(PagePreviewNavigation.isRtlDirection("YesAndRightToLeft", followMangaTag = true))
        assertTrue(PagePreviewNavigation.isRtlDirection("Yes", followMangaTag = true))
        assertFalse(PagePreviewNavigation.isRtlDirection("No", followMangaTag = true))
        assertFalse(PagePreviewNavigation.isRtlDirection("", followMangaTag = true))
        assertFalse(PagePreviewNavigation.isRtlDirection("YesAndRightToLeft", followMangaTag = false))

        // LTR: left = prev, right = next; swipe left (negative) = next, swipe right (positive) = prev
        assertEquals(0, PagePreviewNavigation.leftArrowTargetIndex(1, 5, isRtl = false))
        assertEquals(2, PagePreviewNavigation.rightArrowTargetIndex(1, 5, isRtl = false))
        assertNull(PagePreviewNavigation.leftArrowTargetIndex(0, 5, isRtl = false))
        assertEquals(2, PagePreviewNavigation.swipeTargetIndex(1, 5, dragDeltaX = -100f, isRtl = false))
        assertEquals(0, PagePreviewNavigation.swipeTargetIndex(1, 5, dragDeltaX = 100f, isRtl = false))

        // RTL (Manga): left = next, right = prev; swipe right (positive) = next, swipe left (negative) = prev
        assertEquals(2, PagePreviewNavigation.leftArrowTargetIndex(1, 5, isRtl = true))
        assertEquals(0, PagePreviewNavigation.rightArrowTargetIndex(1, 5, isRtl = true))
        assertNull(PagePreviewNavigation.rightArrowTargetIndex(0, 5, isRtl = true))
        assertEquals(2, PagePreviewNavigation.swipeTargetIndex(1, 5, dragDeltaX = 100f, isRtl = true))
        assertEquals(0, PagePreviewNavigation.swipeTargetIndex(1, 5, dragDeltaX = -100f, isRtl = true))
    }
}
