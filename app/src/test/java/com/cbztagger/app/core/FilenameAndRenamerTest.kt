package com.cbztagger.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class FilenameAndRenamerTest {

    @Test
    fun parsesWesternComicFilename() {
        val parsed = FilenameParser.parse("Spider-Man v02 #012 (2023) (Digital).cbz")
        assertEquals("Spider-Man", parsed.series)
        assertEquals("12", parsed.number)
        assertEquals(2, parsed.volume)
        assertEquals(2023, parsed.year)
    }

    @Test
    fun parsesKoreanVolumeFilename() {
        val parsed = FilenameParser.parse("원피스 015권 (2000).cbz")
        assertEquals("원피스", parsed.series)
        assertEquals("15", parsed.number)
        assertEquals(2000, parsed.year)
    }

    @Test
    fun parsesDashSeparatedFilenameWithTitle() {
        val parsed = FilenameParser.parse("Invincible - 005 - Family Matters (2003).cbz")
        assertEquals("Invincible", parsed.series)
        assertEquals("5", parsed.number)
        assertEquals("Family Matters", parsed.title)
        assertEquals(2003, parsed.year)
    }

    @Test
    fun formatsFilenameWithPaddingAndCleanup() {
        val info = ComicInfo(series = "Batman: Year One", number = "2", year = 1987)
        val formatted = FileRenamer.formatFilename(info, "{series} #{issue} ({year})", issuePadding = 3)
        assertEquals("Batman Year One #002 (1987).cbz", formatted)

        val noYear = ComicInfo(series = "원피스", number = "7")
        val cleaned = FileRenamer.formatFilename(noYear, "{series} {issue}권 ({year})", issuePadding = 2)
        assertEquals("원피스 07권.cbz", cleaned)

        val noNumberOrVolume = ComicInfo(series = "단권만화")
        val cleanedEmpty = FileRenamer.formatFilename(noNumberOrVolume, "{series} v{volume} #{issue} ({year})", issuePadding = 3)
        assertEquals("단권만화.cbz", cleanedEmpty)

        val pagesOnly = ComicInfo(pages = listOf(PageInfo(image = 0, type = PageType.FRONT_COVER.xmlValue)))
        assertEquals(false, FileRenamer.hasMetadataForTemplate(pagesOnly, "{series} #{issue} ({year})"))
        assertEquals(true, FileRenamer.hasMetadataForTemplate(noYear, "{series} #{issue} ({year})"))

        assertEquals(
            "원피스 07권_1.cbz",
            FileRenamer.uniqueTargetFilename("원피스 07권.cbz", setOf("원피스 07권.cbz")),
        )

        val oneshot = ComicInfo(title = "룩 백")
        assertEquals(false, FileRenamer.hasMetadataForTemplate(oneshot, "{series} #{issue} ({year})", useTitleForOneshot = false))
        assertEquals(true, FileRenamer.hasMetadataForTemplate(oneshot, "{series} #{issue} ({year})", useTitleForOneshot = true))
        assertEquals(
            "룩 백.cbz",
            FileRenamer.formatFilename(oneshot, "{series} #{issue} ({year})", issuePadding = 3, useTitleForOneshot = true),
        )
        assertEquals(
            "원피스 #007.cbz",
            FileRenamer.formatFilename(noYear, "{series} #{issue} ({year})", issuePadding = 3, useTitleForOneshot = true),
        )
    }

    @Test
    fun batchTagPatchOnlyOverwritesNonBlankFields() {
        val existing = ComicInfo(series = "Old", number = "1", publisher = "DC", year = 2020)
        val patch = BatchTagPatch(series = "New Series", genre = "Action")
        val merged = existing.applyBatchPatch(patch)

        assertEquals("New Series", merged.series)
        assertEquals("1", merged.number)
        assertEquals("DC", merged.publisher)
        assertEquals("Action", merged.genre)
        assertEquals(2020, merged.year)
    }
}
