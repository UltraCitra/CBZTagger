package com.cbztagger.app.core

import com.cbztagger.app.ui.components.FastScrollbarMath
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
        assertEquals("오바타 타케시 | 오바 츠구미", info.formatAuthorsDisplay())
    }

    @Test
    fun formatAuthorsDisplay_singleFieldOnly() {
        assertEquals("그림작가A", ComicInfo(penciller = "그림작가A").formatAuthorsDisplay())
        assertEquals("글작가B", ComicInfo(writer = "글작가B").formatAuthorsDisplay())
        assertEquals("", ComicInfo().formatAuthorsDisplay())
        assertFalse(ComicInfo(penciller = "그림작가A").isEmpty)
    }

    @Test
    fun formatLibraryMetaLine_seriesTitleAndAuthorsInline() {
        assertEquals(
            "원피스 #001 (2024) | 오다 에이치로",
            ComicInfo(series = "원피스", number = "001", year = 2024, writer = "오다 에이치로", penciller = "오다 에이치로").formatLibraryMetaLine(),
        )
        assertEquals(
            "데스노트 #001 | 오바타 타케시 | 오바 츠구미",
            ComicInfo(series = "데스노트", number = "001", writer = "오바 츠구미", penciller = "오바타 타케시").formatLibraryMetaLine(),
        )
        assertEquals(
            "단편 제목 (2025) | 홍길동",
            ComicInfo(title = "단편 제목", year = 2025, penciller = "홍길동").formatLibraryMetaLine(),
        )
        assertEquals("홍길동", ComicInfo(penciller = "홍길동").formatLibraryMetaLine())
        assertEquals("메타데이터 없음", ComicInfo().formatLibraryMetaLine())
    }

    @Test
    fun formatLibraryCardLines_promotesMetadataOnlyWhenTitleOrSeriesAndNumberPresent() {
        // Series + Number present -> metadata on top, filename underneath
        assertEquals(
            "원피스 #001 (2024) | 오다 에이치로" to "onepiece_001.cbz",
            ComicInfo(series = "원피스", number = "001", year = 2024, penciller = "오다 에이치로")
                .formatLibraryCardLines("onepiece_001.cbz"),
        )
        // Title present (one-shot) -> metadata on top, filename underneath
        assertEquals(
            "단편 제목 (2025) | 홍길동" to "oneshot.cbz",
            ComicInfo(title = "단편 제목", year = 2025, penciller = "홍길동")
                .formatLibraryCardLines("oneshot.cbz"),
        )
        // Series without Number and without Title -> insufficient, keep filename on top
        assertEquals(
            "raw_01.cbz" to "원피스 | 오다 에이치로",
            ComicInfo(series = "원피스", penciller = "오다 에이치로")
                .formatLibraryCardLines("raw_01.cbz"),
        )
        // Author only -> insufficient, keep filename on top
        assertEquals(
            "raw_02.cbz" to "홍길동",
            ComicInfo(penciller = "홍길동")
                .formatLibraryCardLines("raw_02.cbz"),
        )
        // Empty metadata -> filename on top, fallback underneath
        assertEquals(
            "raw_03.cbz" to "No metadata",
            ComicInfo().formatLibraryCardLines("raw_03.cbz", emptyFallback = "No metadata"),
        )
    }

    @Test
    fun fastScrollbarMath_handlesZeroAndNormalTrackHeights() {
        val zeroThumb = FastScrollbarMath.computeThumbHeight(0f, 24f, 132f, 0.1f)
        assertEquals(1f, zeroThumb, 0.001f)

        val thumb = FastScrollbarMath.computeThumbHeight(1048f, 24f, 132f, 0.1f)
        assertEquals(132f, thumb, 0.001f)

        assertEquals(0f, FastScrollbarMath.pointerYToFraction(24f, 1048f, 24f, thumb), 0.001f)
        assertEquals(0.5f, FastScrollbarMath.pointerYToFraction(524f, 1048f, 24f, thumb), 0.001f)
        assertEquals(1f, FastScrollbarMath.pointerYToFraction(1024f, 1048f, 24f, thumb), 0.001f)
    }
}
