package com.cbztagger.app.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class CbzArchiveTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createStoredCbz(name: String, pageBytes: ByteArray): File {
        val file = tempFolder.newFile(name)
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            listOf("001.jpg", "002.jpg", "010.jpg").forEach { entryName ->
                val entry = ZipEntry(entryName).apply {
                    method = ZipEntry.STORED
                    size = pageBytes.size.toLong()
                    compressedSize = pageBytes.size.toLong()
                    crc = CRC32().apply { update(pageBytes) }.value
                }
                zos.putNextEntry(entry)
                zos.write(pageBytes)
                zos.closeEntry()
            }
        }
        return file
    }

    @Test
    fun writeComicInfoWithCompressionLevels() {
        val compressiblePage = ByteArray(80_000) { (it % 4).toByte() }
        val cbz = createStoredCbz("test.cbz", compressiblePage)

        // 1. Keep original (-1): existing STORED pages stay STORED
        val info = ComicInfo(series = "Batman", number = "1", year = 2024)
        CbzArchive.writeComicInfo(cbz, info, compressionLevel = -1)
        ZipFile(cbz).use { zf ->
            assertEquals(ZipEntry.STORED, zf.getEntry("001.jpg").method)
            assertEquals(ZipEntry.DEFLATED, zf.getEntry(ComicInfoXml.FILENAME).method)
            assertArrayEquals(compressiblePage, zf.getInputStream(zf.getEntry("001.jpg")).readBytes())
        }

        // 2. Level 9 (Maximum): entire archive recompressed with DEFLATED
        val res9 = CbzArchive.writeComicInfo(cbz, info.copy(number = "2"), compressionLevel = 9)
        ZipFile(cbz).use { zf ->
            assertEquals(ZipEntry.DEFLATED, zf.getEntry("001.jpg").method)
            assertEquals(ZipEntry.DEFLATED, zf.getEntry("002.jpg").method)
            assertEquals(ZipEntry.DEFLATED, zf.getEntry(ComicInfoXml.FILENAME).method)
            assertArrayEquals(compressiblePage, zf.getInputStream(zf.getEntry("001.jpg")).readBytes())
        }

        // 3. Level 0 (Store): entire archive stored uncompressed
        val res0 = CbzArchive.writeComicInfo(cbz, info.copy(number = "3"), compressionLevel = 0)
        ZipFile(cbz).use { zf ->
            assertEquals(ZipEntry.STORED, zf.getEntry("001.jpg").method)
            assertEquals(ZipEntry.STORED, zf.getEntry("002.jpg").method)
            assertEquals(ZipEntry.STORED, zf.getEntry(ComicInfoXml.FILENAME).method)
            assertArrayEquals(compressiblePage, zf.getInputStream(zf.getEntry("001.jpg")).readBytes())
        }

        assertTrue("Expected level 9 (${res9.afterBytes}) < level 0 (${res0.afterBytes})", res9.afterBytes < res0.afterBytes)

        val summary = CbzArchive.inspect(cbz)
        assertTrue(summary.hasComicInfo)
        assertEquals("Batman", summary.comicInfo.series)
        assertEquals("3", summary.comicInfo.number)
        assertEquals(listOf("001.jpg", "002.jpg", "010.jpg"), summary.pageNames)
    }

    @Test
    fun removeComicInfoRemovesOnlyMetadata() {
        val page = ByteArray(1024) { 7 }
        val cbz = createStoredCbz("remove_test.cbz", page)
        CbzArchive.writeComicInfo(cbz, ComicInfo(series = "Test"), compressionLevel = 6)
        assertTrue(CbzArchive.inspect(cbz).hasComicInfo)

        CbzArchive.removeComicInfo(cbz, compressionLevel = -1)
        val after = CbzArchive.inspect(cbz)
        assertFalse(after.hasComicInfo)
        assertEquals(3, after.pageNames.size)
    }

    @Test
    fun recompressOnlyPreservesComicInfo() {
        val page = ByteArray(40_000) { (it % 3).toByte() }
        val cbz = createStoredCbz("recompress.cbz", page)
        CbzArchive.writeComicInfo(cbz, ComicInfo(series = "Preserved", number = "7"), compressionLevel = 0)
        val storedSize = cbz.length()

        val result = CbzArchive.recompressOnly(cbz, compressionLevel = 9)
        assertTrue(result.afterBytes < storedSize)
        val after = CbzArchive.inspect(cbz)
        assertTrue(after.hasComicInfo)
        assertEquals("Preserved", after.comicInfo.series)
        assertEquals("7", after.comicInfo.number)
    }
}
