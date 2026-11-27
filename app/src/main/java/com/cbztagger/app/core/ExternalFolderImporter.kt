package com.cbztagger.app.core

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class PlannedImageEntry<T>(
    val source: T,
    val originalName: String,
    val targetEntryName: String,
)

/**
 * Pure-Kotlin helper for importing `<path>/<folder_name>/<image_files>` into
 * `<folder_name>.cbz` archives:
 * - Preserves original image filenames without renaming
 * - Orders entries naturally inside the CBZ root
 * - Preserves an existing `ComicInfo.xml` if present in the source folder
 * - Packs entries using `ZipEntry.STORED` (uncompressed / 보관) for fast import
 */
object ExternalFolderImporter {
    private val IMAGE_EXTENSIONS = setOf("webp", "jpg", "jpeg", "png", "gif", "bmp")

    // Android ICU-safe regex
    private val ILLEGAL_FILENAME_CHARS = Regex("""[\\/:*?"<>|]""")

    fun isSupportedImageFilename(name: String): Boolean {
        val base = name.substringAfterLast('/')
        if (base.isBlank() || base.startsWith(".")) return false
        val ext = base.substringAfterLast('.', "").lowercase(Locale.US)
        return ext in IMAGE_EXTENSIONS
    }

    fun isComicInfoFilename(name: String): Boolean =
        name.substringAfterLast('/').equals(ComicInfoXml.FILENAME, ignoreCase = true)

    fun cleanFilename(name: String): String {
        val cleaned = name.replace(ILLEGAL_FILENAME_CHARS, "").trim().trimEnd('.', ' ')
        return cleaned.ifBlank { "comic" }
    }

    fun uniqueCbzFilename(folderName: String, existingNamesLower: Set<String>): String {
        val base = cleanFilename(folderName)
        val first = "$base.cbz"
        if (first.lowercase(Locale.US) !in existingNamesLower) {
            return first
        }
        var counter = 1
        while (true) {
            val candidate = "${base}_${counter}.cbz"
            if (candidate.lowercase(Locale.US) !in existingNamesLower) {
                return candidate
            }
            counter++
        }
    }

    /**
     * Collects all supported image files sorted in natural filename order.
     * When [sequentialRename] is true, renames entries sequentially starting at `000`
     * (`000.ext`, `001.ext`, `002.ext`, ...) while preserving original file extensions.
     * Otherwise preserves original filenames and prevents case-insensitive duplicates.
     */
    fun <T> planImageEntries(
        items: List<T>,
        sequentialRename: Boolean = false,
        nameSelector: (T) -> String,
    ): List<PlannedImageEntry<T>> {
        val filtered = items.mapNotNull { item ->
            val name = nameSelector(item).substringAfterLast('/')
            if (isSupportedImageFilename(name)) item to name else null
        }.sortedWith(compareBy(NaturalOrderComparator) { it.second })

        if (sequentialRename) {
            val padWidth = maxOf(3, (filtered.size - 1).coerceAtLeast(0).toString().length)
            return filtered.mapIndexed { index, (item, originalName) ->
                val extWithDot = extractExtWithDot(originalName)
                val target = String.format(Locale.US, "%0${padWidth}d%s", index, extWithDot)
                PlannedImageEntry(item, originalName, target)
            }
        }

        val usedLower = mutableSetOf(ComicInfoXml.FILENAME.lowercase(Locale.US))
        val planned = mutableListOf<PlannedImageEntry<T>>()

        fun reserveEntryName(desired: String): String {
            val key = desired.lowercase(Locale.US)
            if (key !in usedLower) {
                usedLower += key
                return desired
            }
            val dotIdx = desired.lastIndexOf('.')
            val stem = if (dotIdx > 0) desired.substring(0, dotIdx) else desired
            val ext = if (dotIdx > 0) desired.substring(dotIdx) else ""
            var counter = 1
            while (true) {
                val candidate = "${stem}_${counter}$ext"
                val candKey = candidate.lowercase(Locale.US)
                if (candKey !in usedLower) {
                    usedLower += candKey
                    return candidate
                }
                counter++
            }
        }

        for ((item, originalName) in filtered) {
            val target = reserveEntryName(originalName)
            planned += PlannedImageEntry(item, originalName, target)
        }

        planned.sortWith(compareBy(NaturalOrderComparator) { it.targetEntryName })
        return planned
    }

    private fun extractExtWithDot(filename: String): String {
        val dotIdx = filename.lastIndexOf('.')
        return if (dotIdx >= 0) filename.substring(dotIdx) else ""
    }

    /**
     * Packs all planned image entries (and an optional existing `ComicInfo.xml`) into `outputFile`
     * at archive root using `ZipEntry.STORED` (uncompressed / 보관).
     * Uses a single scratch file so memory usage stays constant regardless of image sizes.
     */
    fun <T> packEntriesToStoredCbz(
        entries: List<PlannedImageEntry<T>>,
        comicInfoXmlBytes: ByteArray?,
        outputFile: File,
        scratchDir: File,
        openStream: (T) -> InputStream,
        onProgress: (processed: Int, total: Int) -> Unit = { _, _ -> },
    ): Long {
        val scratchFile = File.createTempFile("import_entry_", ".tmp", scratchDir)
        val hasXml = comicInfoXmlBytes != null && comicInfoXmlBytes.isNotEmpty()
        val totalSteps = entries.size + if (hasXml) 1 else 0
        var processed = 0
        val buffer = ByteArray(64 * 1024)

        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(outputFile), 64 * 1024)).use { zout ->
                for (planned in entries) {
                    processed++
                    onProgress(processed, totalSteps)

                    val crc = CRC32()
                    var entrySize = 0L
                    openStream(planned.source).use { input ->
                        FileOutputStream(scratchFile).use { scratchOut ->
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                crc.update(buffer, 0, read)
                                scratchOut.write(buffer, 0, read)
                                entrySize += read
                            }
                        }
                    }

                    val zipEntry = ZipEntry(planned.targetEntryName).apply {
                        time = System.currentTimeMillis()
                        method = ZipEntry.STORED
                        size = entrySize
                        compressedSize = entrySize
                        this.crc = crc.value
                    }
                    zout.putNextEntry(zipEntry)
                    FileInputStream(scratchFile).use { scratchIn ->
                        while (true) {
                            val read = scratchIn.read(buffer)
                            if (read <= 0) break
                            zout.write(buffer, 0, read)
                        }
                    }
                    zout.closeEntry()
                }

                if (hasXml && comicInfoXmlBytes != null) {
                    processed++
                    onProgress(processed, totalSteps)
                    val xmlCrc = CRC32().apply { update(comicInfoXmlBytes) }.value
                    val xmlEntry = ZipEntry(ComicInfoXml.FILENAME).apply {
                        time = System.currentTimeMillis()
                        method = ZipEntry.STORED
                        size = comicInfoXmlBytes.size.toLong()
                        compressedSize = comicInfoXmlBytes.size.toLong()
                        crc = xmlCrc
                    }
                    zout.putNextEntry(xmlEntry)
                    zout.write(comicInfoXmlBytes)
                    zout.closeEntry()
                }
            }
            return outputFile.length()
        } finally {
            scratchFile.delete()
        }
    }
}
