package com.cbztagger.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComicInfoXmlTest {

    @Test
    fun roundTripSerializeAndParse() {
        val original = ComicInfo(
            series = "원피스",
            number = "1",
            count = 110,
            title = "Romance Dawn",
            volume = 1,
            publisher = "대원씨아이",
            year = 1997,
            month = 12,
            day = 24,
            writer = "오다 에이치로",
            penciller = "오다 에이치로",
            genre = "Action, Adventure",
            languageIso = "ko",
            manga = "YesAndRightToLeft",
            blackAndWhite = true,
            summary = "해적왕을 꿈꾸는 소년의 모험",
            pageCount = 2,
            pages = listOf(
                PageInfo(image = 0, type = PageType.FRONT_COVER.xmlValue, bookmark = "표지"),
                PageInfo(image = 1, type = PageType.STORY.xmlValue, doublePage = true, bookmark = "1화 시작"),
            ),
        )

        val xmlBytes = ComicInfoXml.serialize(original)
        val parsed = ComicInfoXml.parse(xmlBytes)
        assertNotNull(parsed)
        assertEquals("원피스", parsed!!.series)
        assertEquals("1", parsed.number)
        assertEquals(110, parsed.count)
        assertEquals("Romance Dawn", parsed.title)
        assertEquals(1997, parsed.year)
        assertEquals("대원씨아이", parsed.publisher)
        assertEquals("YesAndRightToLeft", parsed.manga)
        assertEquals(true, parsed.blackAndWhite)
        assertEquals(2, parsed.pages.size)
        assertEquals("FrontCover", parsed.pages[0].type)
        assertEquals("표지", parsed.pages[0].bookmark)
        assertEquals(true, parsed.pages[1].doublePage)
        assertEquals("1화 시작", parsed.pages[1].bookmark)
    }

    @Test
    fun preservesUnknownCustomXmlTags() {
        val existingXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <ComicInfo>
              <Series>Old Series</Series>
              <CustomExtraField>keep-me</CustomExtraField>
            </ComicInfo>
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val updated = ComicInfo(series = "New Series", number = "5")
        val xmlStr = ComicInfoXml.serialize(updated, existingXml).toString(Charsets.UTF_8)

        assertTrue(xmlStr.contains("<Series>New Series</Series>"))
        assertTrue(xmlStr.contains("<Number>5</Number>"))
        assertTrue(xmlStr.contains("<CustomExtraField>keep-me</CustomExtraField>"))
    }

    @Test
    fun rejectsInvalidRootElement() {
        val invalid = "<NotComicInfo><Series>Test</Series></NotComicInfo>".toByteArray()
        assertNull(ComicInfoXml.parse(invalid))
    }
}
