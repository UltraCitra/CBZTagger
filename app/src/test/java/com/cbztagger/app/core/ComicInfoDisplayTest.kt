package com.cbztagger.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ComicInfoDisplayTest {
    @Test
    fun formatAuthorsDisplay_sameAuthorShownOnce() {
        val info = ComicInfo(writer = "오다 에이치로", penciller = "오다 에이치로")
        assertEquals("오다 에이치로", info.formatAuthorsDisplay())
    }

    @Test
    fun formatAuthorsDisplay_differentAuthorsPrioritizesPenciller() {
        val info = ComicInfo(writer = "오바 츠구미", penciller = "오바타 타케시")
        assertEquals("오바타 타케시 / 오바 츠구미", info.formatAuthorsDisplay())
    }

    @Test
    fun formatAuthorsDisplay_singleFieldOnly() {
        assertEquals("그림작가A", ComicInfo(penciller = "그림작가A").formatAuthorsDisplay())
        assertEquals("글작가B", ComicInfo(writer = "글작가B").formatAuthorsDisplay())
        assertEquals("", ComicInfo().formatAuthorsDisplay())
        assertFalse(ComicInfo(penciller = "그림작가A").isEmpty)
    }

    @Test
    fun formatLibraryPrimaryMeta_seriesAndTitleFallback() {
        assertEquals(
            "원피스 #001 (2024)",
            ComicInfo(series = "원피스", number = "001", year = 2024).formatLibraryPrimaryMeta(),
        )
        assertEquals(
            "단편 제목 (2025)",
            ComicInfo(title = "단편 제목", year = 2025).formatLibraryPrimaryMeta(),
        )
        assertEquals(
            "단편 제목",
            ComicInfo(title = "단편 제목").formatLibraryPrimaryMeta(),
        )
        assertEquals("", ComicInfo().formatLibraryPrimaryMeta())
    }
}
