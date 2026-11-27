package com.cbztagger.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class ExternalFolderImporterTest {

    @Test
    fun planImageEntriesPreservesOriginalFilenamesInNaturalOrder() {
        val rawNames = listOf(
            "page_10.jpg",
            "page_2.jpg",
            "cover.png",
            "page_1.webp",
            "ComicInfo.xml",
            ".nomedia",
            "readme.txt",
        )

        val planned = ExternalFolderImporter.planImageEntries(rawNames) { it }
        assertEquals(
            listOf(
                "cover.png",
                "page_1.webp",
                "page_2.jpg",
                "page_10.jpg",
            ),
            planned.map { it.targetEntryName },
        )
        assertEquals(planned.map { it.originalName }, planned.map { it.targetEntryName })
    }

    @Test
    fun planImageEntriesSequentialRenameNumbersInNaturalOrderFromZero() {
        val rawNames = listOf(
            "page_10.jpg",
            "page_2.jpg",
            "cover.png",
            "page_1.webp",
            "ComicInfo.xml",
            ".nomedia",
            "readme.txt",
        )

        val planned = ExternalFolderImporter.planImageEntries(rawNames, sequentialRename = true) { it }
        assertEquals(
            listOf(
                "000.png",
                "001.webp",
                "002.jpg",
                "003.jpg",
            ),
            planned.map { it.targetEntryName },
        )
        assertEquals(
            listOf("cover.png", "page_1.webp", "page_2.jpg", "page_10.jpg"),
            planned.map { it.originalName },
        )
    }

    @Test
    fun planImageEntriesSequentialRenameExpandsPaddingOver1000Images() {
        val rawNames = (1..1005).map { "img_$it.jpg" }.reversed()
        val planned = ExternalFolderImporter.planImageEntries(rawNames, sequentialRename = true) { it }
        assertEquals(1005, planned.size)
        assertEquals("0000.jpg", planned.first().targetEntryName)
        assertEquals("img_1.jpg", planned.first().originalName)
        assertEquals("1004.jpg", planned.last().targetEntryName)
        assertEquals("img_1005.jpg", planned.last().originalName)
    }

    @Test
    fun cleanFilenameAndUniqueCbzReservation() {
        assertEquals("My Comic Vol 1", ExternalFolderImporter.cleanFilename("  My Comic Vol 1. "))
        assertEquals("volume1", ExternalFolderImporter.cleanFilename("volume:1/*?"))
        assertEquals("comic", ExternalFolderImporter.cleanFilename("..."))

        val existing = setOf("vol1.cbz", "vol1_1.cbz")
        assertEquals("vol1_2.cbz", ExternalFolderImporter.uniqueCbzFilename("vol1", existing))
        assertEquals("vol2.cbz", ExternalFolderImporter.uniqueCbzFilename("vol2", existing))
    }

    @Test
    fun packEntriesToStoredCbzWithoutXmlCreatesPureStoredArchive() {
        val tempDir = Files.createTempDirectory("external_folder_import_test").toFile()
        try {
            val srcDir = File(tempDir, "Sample Comic").apply { mkdirs() }
            val img1 = File(srcDir, "001.jpg").apply { writeBytes("page-1-bytes".repeat(50).toByteArray()) }
            val img2 = File(srcDir, "002.webp").apply { writeBytes("page-2-bytes".repeat(50).toByteArray()) }
            val img10 = File(srcDir, "010.png").apply { writeBytes("page-10-bytes".repeat(50).toByteArray()) }

            val planned = ExternalFolderImporter.planImageEntries(listOf(img10, img1, img2)) { it.name }
            val cbzFile = File(tempDir, "Sample Comic.cbz")

            ExternalFolderImporter.packEntriesToStoredCbz(
                entries = planned,
                comicInfoXmlBytes = null,
                outputFile = cbzFile,
                scratchDir = tempDir,
                openStream = { it.inputStream() },
            )

            ZipFile(cbzFile).use { zf ->
                val entries = zf.entries().toList()
                assertEquals(listOf("001.jpg", "002.webp", "010.png"), entries.map { it.name })
                assertTrue(entries.all { it.method == ZipEntry.STORED })
            }

            val summary = CbzArchive.inspect(cbzFile)
            assertFalse(summary.hasComicInfo)
            assertEquals(3, summary.pageNames.size)
            assertEquals("Store (무압축)", summary.compressionSummary)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun packEntriesToStoredCbzPreservesExistingComicInfoXml() {
        val tempDir = Files.createTempDirectory("external_folder_xml_test").toFile()
        try {
            val srcDir = File(tempDir, "ExistingXmlBook").apply { mkdirs() }
            val img1 = File(srcDir, "p1.jpg").apply { writeBytes("img-bytes".repeat(40).toByteArray()) }
            val xmlBytes = ComicInfoXml.serialize(ComicInfo(series = "테스트 시리즈", number = "1"))

            val planned = ExternalFolderImporter.planImageEntries(listOf(img1)) { it.name }
            val cbzFile = File(tempDir, "ExistingXmlBook.cbz")

            ExternalFolderImporter.packEntriesToStoredCbz(
                entries = planned,
                comicInfoXmlBytes = xmlBytes,
                outputFile = cbzFile,
                scratchDir = tempDir,
                openStream = { it.inputStream() },
            )

            val summary = CbzArchive.inspect(cbzFile)
            assertTrue(summary.hasComicInfo)
            assertEquals("테스트 시리즈", summary.comicInfo.series)
            assertEquals("1", summary.comicInfo.number)
            assertEquals("", summary.comicInfo.notes)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
