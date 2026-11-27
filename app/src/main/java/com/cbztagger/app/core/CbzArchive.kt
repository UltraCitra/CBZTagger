package com.cbztagger.app.core

import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class CbzSummary(
    val pageNames: List<String>,
    val hasComicInfo: Boolean,
    val comicInfo: ComicInfo,
    val compressionSummary: String,
    val totalCompressedBytes: Long,
    val totalUncompressedBytes: Long,
)

data class CbzWriteResult(
    val beforeBytes: Long,
    val afterBytes: Long,
    val pageCount: Int,
)

/**
 * Pure JVM/Android CBZ (ZIP) reader & writer with configurable ZIP compression levels:
 * -1: keep original entry compression method (STORED stays STORED, DEFLATED stays DEFLATED)
 *  0: ZIP_STORED (no compression, recompresses all entries)
 * 1-9: ZIP_DEFLATED with Deflater level 1..9 (recompresses all entries)
 */
object CbzArchive {
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")

    fun isImageEntry(name: String): Boolean {
        val fileName = name.substringAfterLast('/')
        if (fileName.isEmpty() || fileName.startsWith(".") || name.contains("__MACOSX/")) return false
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in IMAGE_EXTENSIONS
    }

    fun inspect(file: File): CbzSummary {
        ZipFile(file).use { zf ->
            val pages = mutableListOf<String>()
            var hasXml = false
            var comicInfo = ComicInfo()
            var storedCount = 0
            var deflatedCount = 0
            var totalCompressed = 0L
            var totalUncompressed = 0L

            val entries = zf.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) continue

                if (entry.compressedSize >= 0) totalCompressed += entry.compressedSize
                if (entry.size >= 0) totalUncompressed += entry.size

                if (entry.name.equals(ComicInfoXml.FILENAME, ignoreCase = true)) {
                    val xmlBytes = zf.getInputStream(entry).use { it.readBytes() }
                    val parsed = ComicInfoXml.parse(xmlBytes)
                    if (parsed != null) {
                        hasXml = true
                        comicInfo = parsed
                    }
                } else if (isImageEntry(entry.name)) {
                    pages += entry.name
                    when (entry.method) {
                        ZipEntry.STORED -> storedCount++
                        ZipEntry.DEFLATED -> deflatedCount++
                    }
                }
            }

            pages.sortWith(NaturalOrderComparator)
            val compressionSummary = when {
                storedCount > 0 && deflatedCount == 0 -> "Store (무압축)"
                deflatedCount > 0 && storedCount == 0 -> "Deflate"
                storedCount > 0 && deflatedCount > 0 -> "혼합 (Store/Deflate)"
                else -> "없음"
            }

            val mergedPages = mergeDefaultPages(comicInfo.pages, pages.size)
            val enriched = comicInfo.copy(
                pageCount = comicInfo.pageCount ?: pages.size,
                pages = mergedPages,
            )

            return CbzSummary(
                pageNames = pages,
                hasComicInfo = hasXml,
                comicInfo = enriched,
                compressionSummary = compressionSummary,
                totalCompressedBytes = totalCompressed,
                totalUncompressedBytes = totalUncompressed,
            )
        }
    }

    fun readEntryBytes(file: File, entryName: String): ByteArray? {
        ZipFile(file).use { zf ->
            val entry = zf.getEntry(entryName) ?: return null
            return zf.getInputStream(entry).use { it.readBytes() }
        }
    }

    fun planSequentialPageRenames(pageNames: List<String>): List<Pair<String, String>> {
        if (pageNames.isEmpty()) return emptyList()
        val padWidth = maxOf(3, (pageNames.size - 1).coerceAtLeast(0).toString().length)
        return pageNames.mapIndexed { index, oldName ->
            val base = oldName.substringAfterLast('/')
            val dotIdx = base.lastIndexOf('.')
            val extWithDot = if (dotIdx >= 0) base.substring(dotIdx) else ""
            val newName = String.format(Locale.US, "%0${padWidth}d%s", index, extWithDot)
            oldName to newName
        }
    }

    fun needsSequentialPageRename(pageNames: List<String>): Boolean =
        planSequentialPageRenames(pageNames).any { (old, new) -> old != new }

    fun writeComicInfo(
        file: File,
        comicInfo: ComicInfo,
        compressionLevel: Int = -1,
        entryRenames: Map<String, String> = emptyMap(),
        onProgress: (processed: Int, total: Int) -> Unit = { _, _ -> },
    ): CbzWriteResult {
        val level = compressionLevel.coerceIn(-1, 9)
        val beforeBytes = file.length()

        var existingXml: ByteArray? = null
        var pageCount = 0
        ZipFile(file).use { zf ->
            val entries = zf.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) continue
                if (entry.name.equals(ComicInfoXml.FILENAME, ignoreCase = true)) {
                    existingXml = zf.getInputStream(entry).use { it.readBytes() }
                } else if (isImageEntry(entry.name)) {
                    pageCount++
                }
            }
        }

        val finalInfo = comicInfo.copy(
            pageCount = pageCount,
            pages = mergeDefaultPages(comicInfo.pages, pageCount),
        )
        val xmlBytes = ComicInfoXml.serialize(finalInfo, existingXml)

        rebuildZip(
            sourceFile = file,
            compressionLevel = level,
            replaceEntries = mapOf(ComicInfoXml.FILENAME to xmlBytes),
            removeEntryNames = emptySet(),
            entryRenames = entryRenames,
            onProgress = onProgress,
        )

        return CbzWriteResult(
            beforeBytes = beforeBytes,
            afterBytes = file.length(),
            pageCount = pageCount,
        )
    }

    fun removeComicInfo(
        file: File,
        compressionLevel: Int = -1,
        onProgress: (processed: Int, total: Int) -> Unit = { _, _ -> },
    ): CbzWriteResult {
        val level = compressionLevel.coerceIn(-1, 9)
        val beforeBytes = file.length()
        val summary = inspect(file)

        rebuildZip(
            sourceFile = file,
            compressionLevel = level,
            replaceEntries = emptyMap(),
            removeEntryNames = setOf(ComicInfoXml.FILENAME),
            onProgress = onProgress,
        )

        return CbzWriteResult(
            beforeBytes = beforeBytes,
            afterBytes = file.length(),
            pageCount = summary.pageNames.size,
        )
    }

    fun recompressOnly(
        file: File,
        compressionLevel: Int,
        onProgress: (processed: Int, total: Int) -> Unit = { _, _ -> },
    ): CbzWriteResult {
        val level = compressionLevel.coerceIn(0, 9)
        val beforeBytes = file.length()
        val summary = inspect(file)

        rebuildZip(
            sourceFile = file,
            compressionLevel = level,
            replaceEntries = emptyMap(),
            removeEntryNames = emptySet(),
            onProgress = onProgress,
        )

        return CbzWriteResult(
            beforeBytes = beforeBytes,
            afterBytes = file.length(),
            pageCount = summary.pageNames.size,
        )
    }

    private fun rebuildZip(
        sourceFile: File,
        compressionLevel: Int,
        replaceEntries: Map<String, ByteArray>,
        removeEntryNames: Set<String>,
        entryRenames: Map<String, String> = emptyMap(),
        onProgress: (processed: Int, total: Int) -> Unit,
    ) {
        val parentDir = sourceFile.parentFile ?: File(".")
        val tempFile = File.createTempFile("comictag_", ".cbz.tmp", parentDir)
        val skipLower = (replaceEntries.keys + removeEntryNames).map { it.lowercase() }.toSet()

        try {
            ZipFile(sourceFile).use { zin ->
                val rawEntries = zin.entries().toList()
                val orderedEntries = if (entryRenames.isEmpty()) {
                    rawEntries
                } else {
                    val renamedFiles = mutableListOf<Pair<ZipEntry, String>>()
                    val otherFiles = mutableListOf<ZipEntry>()
                    val dirEntries = mutableListOf<ZipEntry>()
                    for (entry in rawEntries) {
                        if (!entry.isDirectory && entry.name.lowercase() in skipLower) continue
                        when {
                            entry.isDirectory -> dirEntries += entry
                            entry.name in entryRenames -> renamedFiles += entry to entryRenames.getValue(entry.name)
                            else -> otherFiles += entry
                        }
                    }
                    renamedFiles.sortWith(compareBy(NaturalOrderComparator) { it.second })
                    val keptOtherNames = otherFiles.map { it.name }
                    val keptDirs = dirEntries.filter { dir ->
                        keptOtherNames.any { it.startsWith(dir.name) }
                    }
                    renamedFiles.map { it.first } + keptDirs + otherFiles
                }

                val totalSteps = orderedEntries.size + replaceEntries.size
                var processed = 0

                ZipOutputStream(BufferedOutputStream(FileOutputStream(tempFile), 64 * 1024)).use { zout ->
                    if (compressionLevel in 1..9) {
                        zout.setLevel(compressionLevel)
                    } else {
                        zout.setLevel(Deflater.DEFAULT_COMPRESSION)
                    }

                    val buffer = ByteArray(64 * 1024)
                    for (entry in orderedEntries) {
                        processed++
                        onProgress(processed, totalSteps)

                        if (!entry.isDirectory && entry.name.lowercase() in skipLower) {
                            continue
                        }

                        if (entry.isDirectory) {
                            val dirEntry = ZipEntry(entry.name).apply {
                                time = entry.time
                                method = ZipEntry.STORED
                                size = 0L
                                compressedSize = 0L
                                crc = 0L
                            }
                            zout.putNextEntry(dirEntry)
                            zout.closeEntry()
                            continue
                        }

                        val targetEntryName = entryRenames[entry.name] ?: entry.name
                        val targetMethod = when {
                            compressionLevel == 0 -> ZipEntry.STORED
                            compressionLevel in 1..9 -> ZipEntry.DEFLATED
                            else -> entry.method // -1: keep original method per entry
                        }

                        if (targetMethod == ZipEntry.STORED) {
                            // Need exact uncompressed size and CRC32 for STORED entries
                            val data = readStreamBytes(zin.getInputStream(entry), buffer)
                            val crc32 = CRC32().apply { update(data) }.value
                            val outEntry = ZipEntry(targetEntryName).apply {
                                time = entry.time
                                comment = entry.comment
                                method = ZipEntry.STORED
                                size = data.size.toLong()
                                compressedSize = data.size.toLong()
                                crc = crc32
                            }
                            zout.putNextEntry(outEntry)
                            zout.write(data)
                            zout.closeEntry()
                        } else {
                            val outEntry = ZipEntry(targetEntryName).apply {
                                time = entry.time
                                comment = entry.comment
                                method = ZipEntry.DEFLATED
                            }
                            zout.putNextEntry(outEntry)
                            zin.getInputStream(entry).use { input ->
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read <= 0) break
                                    zout.write(buffer, 0, read)
                                }
                            }
                            zout.closeEntry()
                        }
                    }

                    // Write replacement/new entries (e.g. ComicInfo.xml)
                    val xmlMethod = if (compressionLevel == 0) ZipEntry.STORED else ZipEntry.DEFLATED
                    for ((name, bytes) in replaceEntries) {
                        processed++
                        onProgress(processed, totalSteps)
                        val outEntry = ZipEntry(name).apply {
                            time = System.currentTimeMillis()
                            method = xmlMethod
                            if (xmlMethod == ZipEntry.STORED) {
                                size = bytes.size.toLong()
                                compressedSize = bytes.size.toLong()
                                crc = CRC32().apply { update(bytes) }.value
                            }
                        }
                        zout.putNextEntry(outEntry)
                        zout.write(bytes)
                        zout.closeEntry()
                    }
                }
            }

            replaceAtomically(tempFile, sourceFile)
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }

    private fun readStreamBytes(input: java.io.InputStream, buffer: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        input.use { stream ->
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                out.write(buffer, 0, read)
            }
        }
        return out.toByteArray()
    }

    private fun replaceAtomically(tempFile: File, targetFile: File) {
        if (tempFile.renameTo(targetFile)) return
        // Fallback for Windows / filesystems where renameTo fails if destination exists
        if (targetFile.exists() && !targetFile.delete()) {
            // Stream overwrite as last resort
            tempFile.inputStream().use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            tempFile.delete()
            return
        }
        if (!tempFile.renameTo(targetFile)) {
            tempFile.copyTo(targetFile, overwrite = true)
            tempFile.delete()
        }
    }

    private fun mergeDefaultPages(existing: List<PageInfo>, pageCount: Int): List<PageInfo> {
        if (pageCount <= 0) return emptyList()
        val byIndex = existing.associateBy { it.image }
        return (0 until pageCount).map { idx ->
            byIndex[idx] ?: PageInfo(
                image = idx,
                type = if (idx == 0) PageType.FRONT_COVER.xmlValue else "",
            )
        }
    }
}

object NaturalOrderComparator : Comparator<String> {
    private val TOKEN_REGEX = Regex("\\d+|\\D+")

    override fun compare(a: String, b: String): Int {
        val tokensA = TOKEN_REGEX.findAll(a.lowercase()).map { it.value }.toList()
        val tokensB = TOKEN_REGEX.findAll(b.lowercase()).map { it.value }.toList()
        val limit = minOf(tokensA.size, tokensB.size)

        for (i in 0 until limit) {
            val ta = tokensA[i]
            val tb = tokensB[i]
            if (ta == tb) continue
            val na = ta.toBigIntegerOrNull()
            val nb = tb.toBigIntegerOrNull()
            val cmp = if (na != null && nb != null) {
                na.compareTo(nb)
            } else {
                ta.compareTo(tb)
            }
            if (cmp != 0) return cmp
        }
        return tokensA.size.compareTo(tokensB.size)
    }
}
