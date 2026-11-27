package com.cbztagger.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.annotation.StringRes
import androidx.documentfile.provider.DocumentFile
import com.cbztagger.app.R
import com.cbztagger.app.core.CbzArchive
import com.cbztagger.app.core.CbzSummary
import com.cbztagger.app.core.CbzWriteResult
import com.cbztagger.app.core.ComicInfo
import com.cbztagger.app.core.ExternalFolderImporter
import com.cbztagger.app.core.NaturalOrderComparator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale

data class CbzDocumentItem(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val pageCount: Int,
    val hasComicInfo: Boolean,
    val comicInfo: ComicInfo,
    val compressionSummary: String,
    val coverEntryName: String?,
)

data class ExternalFolderCandidate(
    val folderName: String,
    val folderUri: Uri,
    val folderDocId: String,
    val isRootFolderItself: Boolean,
    val imageCount: Int,
    val hasExistingXml: Boolean,
    val totalSizeBytes: Long,
)

private data class SafChildDoc(
    val docId: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
) {
    val isDirectory: Boolean get() = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
}

class SafCbzRepository(private val context: Context) {
    private fun str(@StringRes resId: Int, vararg args: Any): String =
        AppLocaleHelper.wrapContext(context).getString(resId, *args)

    private val sessionMutex = Mutex()
    @Volatile
    private var editorSessionUri: Uri? = null
    @Volatile
    private var editorSessionFile: File? = null

    private suspend fun ensureEditorSessionFile(uri: Uri): File = sessionMutex.withLock {
        val currentFile = editorSessionFile
        if (editorSessionUri == uri && currentFile != null && currentFile.exists()) {
            return currentFile
        }
        currentFile?.delete()
        editorSessionUri = null
        editorSessionFile = null

        val tempFile = File.createTempFile("editor_session_", ".cbz", context.cacheDir)
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException(str(R.string.err_cannot_open_file, uri.toString()))
            input.use { src ->
                FileOutputStream(tempFile).use { dst ->
                    src.copyTo(dst, bufferSize = 64 * 1024)
                }
            }
            editorSessionUri = uri
            editorSessionFile = tempFile
            tempFile
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }

    suspend fun closeEditorSession() = withContext(Dispatchers.IO) {
        sessionMutex.withLock {
            editorSessionFile?.delete()
            editorSessionFile = null
            editorSessionUri = null
        }
    }

    fun closeEditorSessionImmediate() {
        editorSessionFile?.delete()
        editorSessionFile = null
        editorSessionUri = null
    }

    suspend fun listFolder(treeUri: Uri): List<CbzDocumentItem> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
        val cbzFiles = root.listFiles()
            .filter { doc ->
                doc.isFile && isCbzFilename(doc.name.orEmpty())
            }
            .sortedWith(compareBy(NaturalOrderComparator) { it.name.orEmpty() })

        cbzFiles.mapNotNull { doc ->
            runCatching { inspectUri(doc.uri, doc.name ?: resolveDisplayName(doc.uri)) }.getOrNull()
        }
    }

    suspend fun resolveFolderDisplayName(treeUri: Uri): String = withContext(Dispatchers.IO) {
        val doc = DocumentFile.fromTreeUri(context, treeUri)
        val name = doc?.name?.takeIf { it.isNotBlank() }
        if (name != null) return@withContext name

        runCatching {
            val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
            treeDocId.substringAfter(':').ifBlank { treeDocId }
        }.getOrElse {
            treeUri.lastPathSegment?.substringAfterLast('/') ?: str(R.string.selected_folder_fallback)
        }
    }

    /**
     * Scans `<configured_path>/<folder_name>/<image_files>` via fast `DocumentsContract` queries.
     * Falls back to treating `<configured_path>` itself as a single folder if it directly
     * contains images and has no image-bearing child directories.
     */
    suspend fun scanExternalImportFolder(treeUri: Uri): List<ExternalFolderCandidate> = withContext(Dispatchers.IO) {
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootChildren = queryChildren(treeUri, rootDocId)

        val candidates = mutableListOf<ExternalFolderCandidate>()
        for (child in rootChildren) {
            if (!child.isDirectory || child.name.startsWith(".")) continue
            val subFiles = queryChildren(treeUri, child.docId)
            var imgCount = 0
            var totalBytes = 0L
            var hasXml = false
            for (f in subFiles) {
                if (f.isDirectory) continue
                when {
                    ExternalFolderImporter.isSupportedImageFilename(f.name) -> {
                        imgCount++
                        if (f.sizeBytes > 0) totalBytes += f.sizeBytes
                    }
                    ExternalFolderImporter.isComicInfoFilename(f.name) -> {
                        hasXml = true
                    }
                }
            }
            if (imgCount > 0) {
                candidates += ExternalFolderCandidate(
                    folderName = child.name.trim(),
                    folderUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, child.docId),
                    folderDocId = child.docId,
                    isRootFolderItself = false,
                    imageCount = imgCount,
                    hasExistingXml = hasXml,
                    totalSizeBytes = totalBytes,
                )
            }
        }

        if (candidates.isEmpty()) {
            var imgCount = 0
            var totalBytes = 0L
            var hasXml = false
            for (f in rootChildren) {
                if (f.isDirectory) continue
                when {
                    ExternalFolderImporter.isSupportedImageFilename(f.name) -> {
                        imgCount++
                        if (f.sizeBytes > 0) totalBytes += f.sizeBytes
                    }
                    ExternalFolderImporter.isComicInfoFilename(f.name) -> {
                        hasXml = true
                    }
                }
            }
            if (imgCount > 0) {
                val rootName = resolveFolderDisplayName(treeUri).substringAfterLast('/').trim()
                candidates += ExternalFolderCandidate(
                    folderName = rootName.ifBlank { "comic" },
                    folderUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId),
                    folderDocId = rootDocId,
                    isRootFolderItself = true,
                    imageCount = imgCount,
                    hasExistingXml = hasXml,
                    totalSizeBytes = totalBytes,
                )
            }
        }

        candidates.sortWith(compareBy(NaturalOrderComparator) { it.folderName })
        candidates
    }

    suspend fun listExistingFileNamesLower(treeUri: Uri): MutableSet<String> = withContext(Dispatchers.IO) {
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        queryChildren(treeUri, rootDocId)
            .filterNot { it.isDirectory }
            .mapTo(mutableSetOf()) { it.name.lowercase(Locale.US) }
    }

    /**
     * Imports a single `<folder_name>` directory into `<destTreeUri>/<folder_name>.cbz` using ZIP_STORED (보관).
     */
    suspend fun importExternalFolderToTree(
        candidate: ExternalFolderCandidate,
        sourceTreeUri: Uri,
        destTreeUri: Uri,
        existingNamesLower: MutableSet<String>,
        deleteSourceAfterImport: Boolean,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): CbzDocumentItem = withContext(Dispatchers.IO) {
        val children = queryChildren(sourceTreeUri, candidate.folderDocId).filterNot { it.isDirectory }
        val plannedImages = ExternalFolderImporter.planImageEntries(children) { it.name }
        if (plannedImages.isEmpty()) {
            throw IllegalStateException(str(R.string.err_no_images_in_folder, candidate.folderName))
        }

        val existingXmlDoc = children.firstOrNull { ExternalFolderImporter.isComicInfoFilename(it.name) }
        val existingXmlBytes = existingXmlDoc?.let { xmlDoc ->
            val xmlUri = DocumentsContract.buildDocumentUriUsingTree(sourceTreeUri, xmlDoc.docId)
            runCatching {
                context.contentResolver.openInputStream(xmlUri)?.use { it.readBytes() }
            }.getOrNull()
        }

        val targetFileName = ExternalFolderImporter.uniqueCbzFilename(candidate.folderName, existingNamesLower)
        existingNamesLower += targetFileName.lowercase(Locale.US)

        val tempCbz = File.createTempFile("import_folder_", ".cbz", context.cacheDir)
        try {
            ExternalFolderImporter.packEntriesToStoredCbz(
                entries = plannedImages,
                comicInfoXmlBytes = existingXmlBytes,
                outputFile = tempCbz,
                scratchDir = context.cacheDir,
                openStream = { childDoc ->
                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(sourceTreeUri, childDoc.docId)
                    context.contentResolver.openInputStream(fileUri)
                        ?: throw IllegalStateException(str(R.string.err_cannot_read_image, childDoc.name))
                },
                onProgress = onProgress,
            )

            val createdUri = createCbzDocument(destTreeUri, targetFileName)
            writeBackToUri(tempCbz, createdUri)
            val actualName = resolveDisplayName(createdUri)

            val summary = CbzArchive.inspect(tempCbz)
            val item = CbzDocumentItem(
                uri = createdUri,
                displayName = actualName,
                sizeBytes = tempCbz.length(),
                pageCount = summary.pageNames.size,
                hasComicInfo = summary.hasComicInfo,
                comicInfo = summary.comicInfo,
                compressionSummary = summary.compressionSummary,
                coverEntryName = summary.pageNames.firstOrNull(),
            )

            if (deleteSourceAfterImport && !candidate.isRootFolderItself) {
                deleteFolderSafely(sourceTreeUri, candidate.folderDocId, candidate.folderUri)
            }

            item
        } finally {
            tempCbz.delete()
        }
    }

    suspend fun inspectUri(uri: Uri, knownName: String? = null): CbzDocumentItem = withContext(Dispatchers.IO) {
        val displayName = knownName ?: resolveDisplayName(uri)
        withTempCopy(uri) { tempFile ->
            val summary: CbzSummary = CbzArchive.inspect(tempFile)
            CbzDocumentItem(
                uri = uri,
                displayName = displayName,
                sizeBytes = tempFile.length(),
                pageCount = summary.pageNames.size,
                hasComicInfo = summary.hasComicInfo,
                comicInfo = summary.comicInfo,
                compressionSummary = summary.compressionSummary,
                coverEntryName = summary.pageNames.firstOrNull(),
            )
        }
    }

    suspend fun loadPageNames(uri: Uri): List<String> = withContext(Dispatchers.IO) {
        val sessionFile = ensureEditorSessionFile(uri)
        CbzArchive.inspect(sessionFile).pageNames
    }

    /**
     * Reads and decodes a single page bitmap on demand without caching bitmaps in memory or on disk.
     * Uses the active editor archive session so scrolling/swiping avoids re-copying the entire CBZ over SAF.
     */
    suspend fun loadPageBitmapUncached(uri: Uri, entryName: String, maxDimension: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                val sessionFile = ensureEditorSessionFile(uri)
                val bytes = CbzArchive.readEntryBytes(sessionFile, entryName) ?: return@withContext null
                decodeSampledBitmap(bytes, maxDimension, maxDimension)
            }.getOrNull()
        }

    suspend fun loadThumbnail(uri: Uri, entryName: String, maxDimension: Int = 360): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                withTempCopy(uri) { tempFile ->
                    val bytes = CbzArchive.readEntryBytes(tempFile, entryName) ?: return@withTempCopy null
                    decodeSampledBitmap(bytes, maxDimension, maxDimension)
                }
            }.getOrNull()
        }

    suspend fun saveComicInfo(
        uri: Uri,
        comicInfo: ComicInfo,
        compressionLevel: Int,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): CbzWriteResult = withContext(Dispatchers.IO) {
        withTempCopy(uri) { tempFile ->
            val result = CbzArchive.writeComicInfo(tempFile, comicInfo, compressionLevel, onProgress)
            writeBackToUri(tempFile, uri)
            result
        }
    }

    suspend fun removeComicInfo(
        uri: Uri,
        compressionLevel: Int = -1,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): CbzWriteResult = withContext(Dispatchers.IO) {
        withTempCopy(uri) { tempFile ->
            val result = CbzArchive.removeComicInfo(tempFile, compressionLevel, onProgress)
            writeBackToUri(tempFile, uri)
            result
        }
    }

    suspend fun recompressOnly(
        uri: Uri,
        compressionLevel: Int,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): CbzWriteResult = withContext(Dispatchers.IO) {
        withTempCopy(uri) { tempFile ->
            val result = CbzArchive.recompressOnly(tempFile, compressionLevel, onProgress)
            writeBackToUri(tempFile, uri)
            result
        }
    }

    /**
     * Renames a SAF document URI using `DocumentsContract.renameDocument` directly.
     * Note: `DocumentFile.fromSingleUri(...).renameTo(...)` throws `UnsupportedOperationException`
     * in AndroidX `SingleDocumentFile`, so `DocumentsContract.renameDocument` must be used.
     */
    suspend fun renameDocument(uri: Uri, newDisplayName: String): Pair<Uri, String>? = withContext(Dispatchers.IO) {
        val currentName = resolveDisplayName(uri)
        if (currentName == newDisplayName) return@withContext (uri to currentName)

        val renamedUri = runCatching {
            DocumentsContract.renameDocument(context.contentResolver, uri, newDisplayName)
        }.getOrNull() ?: return@withContext null

        val actualName = resolveDisplayName(renamedUri)
        renamedUri to actualName
    }

    private fun deleteFolderSafely(treeUri: Uri, folderDocId: String, folderUri: Uri) {
        val directOk = runCatching {
            DocumentsContract.deleteDocument(context.contentResolver, folderUri)
        }.getOrDefault(false)
        if (directOk) return

        // Fallback for providers that require deleting children before deleting the directory
        runCatching {
            val children = queryChildren(treeUri, folderDocId)
            for (child in children) {
                val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, child.docId)
                if (child.isDirectory) {
                    deleteFolderSafely(treeUri, child.docId, childUri)
                } else {
                    runCatching { DocumentsContract.deleteDocument(context.contentResolver, childUri) }
                }
            }
            DocumentsContract.deleteDocument(context.contentResolver, folderUri)
        }
    }

    private fun queryChildren(treeUri: Uri, parentDocId: String): List<SafChildDoc> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        val result = mutableListOf<SafChildDoc>()
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            while (cursor.moveToNext()) {
                val docId = if (idIdx >= 0) cursor.getString(idIdx) else null
                val name = if (nameIdx >= 0) cursor.getString(nameIdx) else null
                val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx) else ""
                val size = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else 0L
                if (!docId.isNullOrBlank() && !name.isNullOrBlank()) {
                    result += SafChildDoc(docId, name, mime.orEmpty(), size)
                }
            }
        }
        return result
    }

    private fun createCbzDocument(destTreeUri: Uri, targetFileName: String): Uri {
        val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(
            destTreeUri,
            DocumentsContract.getTreeDocumentId(destTreeUri),
        )
        val created = runCatching {
            DocumentsContract.createDocument(
                context.contentResolver,
                parentDocUri,
                "application/vnd.comicbook+zip",
                targetFileName,
            )
        }.getOrNull()
            ?: DocumentsContract.createDocument(
                context.contentResolver,
                parentDocUri,
                "application/octet-stream",
                targetFileName,
            )
            ?: throw IllegalStateException(str(R.string.err_cannot_create_cbz, targetFileName))

        val actualName = resolveDisplayName(created)
        if (!actualName.equals(targetFileName, ignoreCase = true) && !actualName.lowercase(Locale.US).endsWith(".cbz")) {
            val renamed = runCatching {
                DocumentsContract.renameDocument(context.contentResolver, created, targetFileName)
            }.getOrNull()
            if (renamed != null) return renamed
        }
        return created
    }

    private fun isCbzFilename(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".cbz") || lower.endsWith(".zip")
    }

    private fun resolveDisplayName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) {
                    val name = cursor.getString(idx)
                    if (!name.isNullOrBlank()) return name
                }
            }
        return DocumentFile.fromSingleUri(context, uri)?.name
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: "comic.cbz"
    }

    private inline fun <T> withTempCopy(uri: Uri, block: (File) -> T): T {
        val tempFile = File.createTempFile("saf_work_", ".cbz", context.cacheDir)
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException(str(R.string.err_cannot_open_file, uri.toString()))
            input.use { src ->
                FileOutputStream(tempFile).use { dst ->
                    src.copyTo(dst, bufferSize = 64 * 1024)
                }
            }
            return block(tempFile)
        } finally {
            tempFile.delete()
        }
    }

    private fun writeBackToUri(sourceFile: File, targetUri: Uri) {
        val output = context.contentResolver.openOutputStream(targetUri, "wt")
            ?: context.contentResolver.openOutputStream(targetUri, "w")
            ?: throw IllegalStateException(str(R.string.err_no_write_permission, targetUri.toString()))
        output.use { dst ->
            FileInputStream(sourceFile).use { src ->
                src.copyTo(dst, bufferSize = 64 * 1024)
            }
            dst.flush()
        }
    }

    private fun decodeSampledBitmap(bytes: ByteArray, reqWidth: Int, reqHeight: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        val halfH = bounds.outHeight / 2
        val halfW = bounds.outWidth / 2
        while (halfH / sampleSize >= reqHeight && halfW / sampleSize >= reqWidth) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
    }
}
