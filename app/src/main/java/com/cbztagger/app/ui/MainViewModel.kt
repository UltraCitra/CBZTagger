package com.cbztagger.app.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cbztagger.app.R
import com.cbztagger.app.core.BatchTagPatch
import com.cbztagger.app.core.CbzArchive
import com.cbztagger.app.core.ComicInfo
import com.cbztagger.app.core.ExternalFolderImporter
import com.cbztagger.app.core.FileRenamer
import com.cbztagger.app.core.FilenameParser
import com.cbztagger.app.core.NaturalOrderComparator
import com.cbztagger.app.data.AppLanguage
import com.cbztagger.app.data.AppLocaleHelper
import com.cbztagger.app.data.AppPreferences
import com.cbztagger.app.data.AppSettings
import com.cbztagger.app.data.CbzDocumentItem
import com.cbztagger.app.data.ExternalFolderCandidate
import com.cbztagger.app.data.SafCbzRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

enum class ActiveScreen {
    LIBRARY,
    EDITOR,
    SETTINGS,
}

data class ProgressState(
    val title: String,
    val detail: String = "",
    val current: Int = 0,
    val total: Int = 0,
)

data class EditorState(
    val item: CbzDocumentItem,
    val draft: ComicInfo,
    val pageNames: List<String> = emptyList(),
    val compressionLevel: Int = -1,
    val isDirty: Boolean = false,
    val pendingSequentialPageRename: Boolean = false,
) {
    val hasUnsavedChanges: Boolean
        get() = pendingSequentialPageRename || (isDirty && draft != item.comicInfo)

    val displayedPageNames: List<String>
        get() = if (pendingSequentialPageRename) {
            CbzArchive.planSequentialPageRenames(pageNames).map { it.second }
        } else {
            pageNames
        }
}

data class ExternalImportUiState(
    val sourceTreeUri: Uri,
    val sourceFolderName: String,
    val destTreeUri: Uri,
    val destFolderName: String,
    val isDestSameAsSource: Boolean,
    val candidates: List<ExternalFolderCandidate>,
    val alreadyImportedNames: Set<String>,
)

data class MainUiState(
    val screen: ActiveScreen = ActiveScreen.LIBRARY,
    val settings: AppSettings = AppSettings(),
    val currentFolderName: String? = null,
    val items: List<CbzDocumentItem> = emptyList(),
    val selectedUris: Set<Uri> = emptySet(),
    val thumbnails: Map<Uri, Bitmap> = emptyMap(),
    val editor: EditorState? = null,
    val externalImport: ExternalImportUiState? = null,
    val progress: ProgressState? = null,
    val statusMessage: String? = null,
) {
    val isSelectionMode: Boolean get() = selectedUris.isNotEmpty()
    val selectedItems: List<CbzDocumentItem> get() = items.filter { it.uri in selectedUris }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = AppPreferences(application)
    private val repo = SafCbzRepository(application)

    private fun str(@StringRes resId: Int, vararg args: Any): String =
        AppLocaleHelper.wrapContext(getApplication()).getString(resId, *args)

    private val _uiState = MutableStateFlow(
        MainUiState(
            settings = prefs.load().let { loaded ->
                val effective = AppLocaleHelper.resolveEffectiveLanguage(application, loaded.appLanguage)
                if (effective != loaded.appLanguage) loaded.copy(appLanguage = effective) else loaded
            },
        ),
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        val lastFolder = _uiState.value.settings.lastFolderUri
        if (!lastFolder.isNullOrBlank()) {
            openFolder(Uri.parse(lastFolder), persist = false)
        }
    }

    fun updateAppLanguage(languageCode: String) {
        val normalized = AppLanguage.normalize(languageCode)
        val updated = _uiState.value.settings.copy(appLanguage = normalized)
        prefs.save(updated)
        _uiState.update { it.copy(settings = updated) }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun navigateTo(screen: ActiveScreen) {
        val leavingEditor = _uiState.value.screen == ActiveScreen.EDITOR && screen != ActiveScreen.EDITOR
        _uiState.update { it.copy(screen = screen) }
        if (leavingEditor) {
            viewModelScope.launch { repo.closeEditorSession() }
        }
    }

    override fun onCleared() {
        super.onCleared()
        repo.closeEditorSessionImmediate()
    }

    fun updateSettings(newSettings: AppSettings) {
        val normalized = newSettings.copy(
            zipCompressionLevel = newSettings.zipCompressionLevel.coerceIn(-1, 9),
            lastCompressionSliderLevel = if (newSettings.zipCompressionLevel in 0..9) {
                newSettings.zipCompressionLevel
            } else {
                newSettings.lastCompressionSliderLevel.coerceIn(0, 9)
            },
        )
        prefs.save(normalized)
        _uiState.update { state ->
            state.copy(
                settings = normalized,
                editor = state.editor?.copy(compressionLevel = normalized.zipCompressionLevel),
            )
        }
    }

    fun openFolder(treeUri: Uri, persist: Boolean = true) {
        if (persist) {
            val updated = _uiState.value.settings.copy(lastFolderUri = treeUri.toString())
            prefs.save(updated)
            _uiState.update { it.copy(settings = updated) }
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    progress = ProgressState(str(R.string.progress_scanning_folder)),
                    selectedUris = emptySet(),
                )
            }
            runCatching {
                val folderName = repo.resolveFolderDisplayName(treeUri)
                val loaded = repo.listFolder(treeUri)
                folderName to loaded
            }
                .onSuccess { (folderName, loaded) ->
                    _uiState.update {
                        it.copy(
                            currentFolderName = folderName,
                            items = loaded,
                            progress = null,
                            statusMessage = str(R.string.msg_folder_loaded, loaded.size),
                        )
                    }
                    loadThumbnailsFor(loaded)
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            progress = null,
                            statusMessage = str(R.string.msg_folder_open_failed, err.localizedMessage ?: err.message ?: ""),
                        )
                    }
                }
        }
    }

    fun addIndividualFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(progress = ProgressState(str(R.string.progress_reading_files), total = uris.size)) }
            val existing = _uiState.value.items.associateBy { it.uri }.toMutableMap()
            var added = 0
            uris.forEachIndexed { index, uri ->
                _uiState.update {
                    it.copy(progress = ProgressState(str(R.string.progress_reading_files), current = index + 1, total = uris.size))
                }
                runCatching { repo.inspectUri(uri) }
                    .onSuccess { item ->
                        existing[item.uri] = item
                        added++
                    }
            }
            val list = existing.values.toList()
            _uiState.update {
                it.copy(
                    items = list,
                    progress = null,
                    statusMessage = str(R.string.msg_files_added, added),
                )
            }
            loadThumbnailsFor(list)
        }
    }

    fun openSingleFromIntent(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(progress = ProgressState(str(R.string.progress_opening_file))) }
            runCatching { repo.inspectUri(uri) }
                .onSuccess { item ->
                    val merged = (_uiState.value.items.filterNot { it.uri == uri }) + item
                    _uiState.update { it.copy(items = merged, progress = null) }
                    openEditor(item)
                    loadThumbnailsFor(listOf(item))
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            progress = null,
                            statusMessage = str(R.string.msg_file_open_failed, err.localizedMessage ?: err.message ?: ""),
                        )
                    }
                }
        }
    }

    private fun loadThumbnailsFor(items: List<CbzDocumentItem>) {
        viewModelScope.launch {
            for (item in items) {
                if (_uiState.value.thumbnails.containsKey(item.uri)) continue
                val cover = item.coverEntryName ?: continue
                val bmp = repo.loadThumbnail(item.uri, cover, maxDimension = 280) ?: continue
                _uiState.update { state ->
                    state.copy(thumbnails = state.thumbnails + (item.uri to bmp))
                }
            }
        }
    }

    fun toggleSelection(uri: Uri) {
        _uiState.update { state ->
            val next = if (uri in state.selectedUris) state.selectedUris - uri else state.selectedUris + uri
            state.copy(selectedUris = next)
        }
    }

    fun selectAll() {
        _uiState.update { state ->
            state.copy(selectedUris = state.items.map { it.uri }.toSet())
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedUris = emptySet()) }
    }

    fun openEditor(item: CbzDocumentItem) {
        val defaultLevel = _uiState.value.settings.zipCompressionLevel
        _uiState.update {
            it.copy(
                screen = ActiveScreen.EDITOR,
                editor = EditorState(
                    item = item,
                    draft = item.comicInfo,
                    compressionLevel = defaultLevel,
                    isDirty = false,
                ),
            )
        }
        viewModelScope.launch {
            val pages = runCatching { repo.loadPageNames(item.uri) }.getOrDefault(emptyList())
            _uiState.update { state ->
                val ed = state.editor ?: return@update state
                if (ed.item.uri != item.uri) return@update state
                state.copy(editor = ed.copy(pageNames = pages))
            }
        }
    }

    fun updateEditorDraft(transform: (ComicInfo) -> ComicInfo) {
        _uiState.update { state ->
            val ed = state.editor ?: return@update state
            state.copy(editor = ed.copy(draft = transform(ed.draft), isDirty = true))
        }
    }

    fun setEditorCompressionLevel(level: Int) {
        _uiState.update { state ->
            val ed = state.editor ?: return@update state
            state.copy(editor = ed.copy(compressionLevel = level.coerceIn(-1, 9)))
        }
    }

    fun parseCurrentFilenameIntoEditor(overwriteExisting: Boolean = true) {
        val ed = _uiState.value.editor ?: return
        val updated = FilenameParser.applyToComicInfo(ed.draft, ed.item.displayName, overwriteExisting)
        _uiState.update {
            it.copy(
                editor = ed.copy(draft = updated, isDirty = true),
                statusMessage = str(R.string.msg_filename_parsed),
            )
        }
    }

    fun setEditorSequentialPageRename(enabled: Boolean) {
        val ed = _uiState.value.editor ?: return
        _uiState.update { state ->
            state.copy(
                editor = ed.copy(pendingSequentialPageRename = enabled),
                statusMessage = str(
                    if (enabled) R.string.msg_pages_rename_queued else R.string.msg_pages_rename_cancelled,
                ),
            )
        }
    }

    fun notifyPagesAlreadySequential() {
        _uiState.update { it.copy(statusMessage = str(R.string.msg_pages_already_sequential)) }
    }

    fun saveEditorComicInfo(navigateBackAfterSave: Boolean = false) {
        val ed = _uiState.value.editor ?: return
        val renameMap = if (ed.pendingSequentialPageRename) {
            CbzArchive.planSequentialPageRenames(ed.pageNames).toMap()
        } else {
            emptyMap()
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(progress = ProgressState(str(R.string.progress_saving_compressing), detail = ed.item.displayName))
            }
            runCatching {
                val res = repo.saveComicInfo(
                    uri = ed.item.uri,
                    comicInfo = ed.draft,
                    compressionLevel = _uiState.value.settings.zipCompressionLevel,
                    entryRenames = renameMap,
                ) { done, total ->
                    _uiState.update { s ->
                        s.copy(
                            progress = ProgressState(
                                title = str(R.string.progress_saving_tags),
                                detail = "${ed.item.displayName} ($done/$total)",
                                current = done,
                                total = total,
                            ),
                        )
                    }
                }
                if (renameMap.isNotEmpty() || navigateBackAfterSave) {
                    repo.closeEditorSession()
                }
                val refreshed = repo.inspectUri(ed.item.uri, ed.item.displayName)
                val updatedPageNames = if (renameMap.isNotEmpty() && !navigateBackAfterSave) {
                    runCatching { repo.loadPageNames(ed.item.uri) }.getOrElse { ed.displayedPageNames }
                } else if (renameMap.isNotEmpty()) {
                    ed.displayedPageNames
                } else {
                    ed.pageNames
                }
                Triple(res, refreshed, updatedPageNames)
            }.onSuccess { (res, refreshed, updatedPageNames) ->
                _uiState.update { state ->
                    state.copy(
                        screen = if (navigateBackAfterSave) ActiveScreen.LIBRARY else state.screen,
                        items = state.items.map { if (it.uri == refreshed.uri) refreshed else it },
                        editor = ed.copy(
                            item = refreshed,
                            draft = refreshed.comicInfo,
                            pageNames = updatedPageNames,
                            pendingSequentialPageRename = false,
                            isDirty = false,
                        ),
                        progress = null,
                        statusMessage = str(R.string.msg_save_complete, formatBytes(res.beforeBytes), formatBytes(res.afterBytes)),
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        progress = null,
                        statusMessage = str(R.string.msg_save_failed, err.localizedMessage ?: err.message ?: ""),
                    )
                }
            }
        }
    }

    fun deleteEditorComicInfo() {
        val ed = _uiState.value.editor ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(progress = ProgressState(str(R.string.progress_deleting_xml), detail = ed.item.displayName)) }
            runCatching {
                repo.removeComicInfo(ed.item.uri, compressionLevel = -1)
                repo.inspectUri(ed.item.uri, ed.item.displayName)
            }.onSuccess { refreshed ->
                _uiState.update { state ->
                    state.copy(
                        items = state.items.map { if (it.uri == refreshed.uri) refreshed else it },
                        editor = ed.copy(item = refreshed, draft = refreshed.comicInfo, isDirty = false),
                        progress = null,
                        statusMessage = str(R.string.msg_xml_deleted),
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        progress = null,
                        statusMessage = str(R.string.msg_xml_delete_failed, err.localizedMessage ?: err.message ?: ""),
                    )
                }
            }
        }
    }

    fun renameEditorFile(newName: String) {
        val ed = _uiState.value.editor ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(progress = ProgressState(str(R.string.progress_renaming_file), detail = newName)) }
            val renamed = repo.renameDocument(ed.item.uri, newName)
            if (renamed != null) {
                val (renamedUri, actualName) = renamed
                val refreshed = runCatching { repo.inspectUri(renamedUri, actualName) }.getOrElse {
                    ed.item.copy(uri = renamedUri, displayName = actualName)
                }
                _uiState.update { state ->
                    val oldThumb = state.thumbnails[ed.item.uri]
                    val nextThumbs = if (oldThumb != null) {
                        state.thumbnails - ed.item.uri + (renamedUri to oldThumb)
                    } else {
                        state.thumbnails
                    }
                    state.copy(
                        items = state.items.map { if (it.uri == ed.item.uri) refreshed else it }
                            .sortedWith(compareBy(NaturalOrderComparator) { it.displayName }),
                        thumbnails = nextThumbs,
                        editor = ed.copy(item = refreshed),
                        progress = null,
                        statusMessage = str(R.string.msg_rename_complete, actualName),
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        progress = null,
                        statusMessage = str(R.string.msg_rename_failed),
                    )
                }
            }
        }
    }

    suspend fun loadPagePreview(uri: Uri, entryName: String): Bitmap? =
        repo.loadPageBitmapUncached(uri, entryName, maxDimension = 1280)

    suspend fun loadPageListThumbnail(uri: Uri, entryName: String): Bitmap? =
        repo.loadPageBitmapUncached(uri, entryName, maxDimension = 160)

    // --- External Folder Import (<path>/<folder_name>/<images> -> <folder_name>.cbz) ---

    fun setExternalImportFolder(treeUri: Uri, openImportDialogAfter: Boolean = false) {
        viewModelScope.launch {
            val folderName = runCatching { repo.resolveFolderDisplayName(treeUri) }
                .getOrElse { str(R.string.external_folder_fallback) }
            val updated = _uiState.value.settings.copy(
                externalImportFolderUri = treeUri.toString(),
                externalImportFolderName = folderName,
            )
            prefs.save(updated)
            _uiState.update {
                it.copy(
                    settings = updated,
                    statusMessage = if (openImportDialogAfter) null else str(R.string.msg_external_path_set, folderName),
                )
            }
            if (openImportDialogAfter) {
                openExternalImportDialog()
            }
        }
    }

    fun clearExternalImportFolder() {
        val updated = _uiState.value.settings.copy(
            externalImportFolderUri = null,
            externalImportFolderName = null,
        )
        prefs.save(updated)
        _uiState.update {
            it.copy(
                settings = updated,
                externalImport = null,
                statusMessage = str(R.string.msg_external_path_cleared),
            )
        }
    }

    fun openExternalImportDialog() {
        val extUriStr = _uiState.value.settings.externalImportFolderUri
        if (extUriStr.isNullOrBlank()) {
            _uiState.update { it.copy(statusMessage = str(R.string.msg_external_path_required)) }
            return
        }
        val sourceUri = Uri.parse(extUriStr)
        val destUri = _uiState.value.settings.lastFolderUri
            ?.takeIf { it.isNotBlank() }
            ?.let { Uri.parse(it) }
            ?: sourceUri

        viewModelScope.launch {
            _uiState.update { it.copy(progress = ProgressState(str(R.string.progress_scanning_external))) }
            runCatching {
                val sourceName = _uiState.value.settings.externalImportFolderName
                    ?: repo.resolveFolderDisplayName(sourceUri)
                val destName = if (destUri == sourceUri) {
                    sourceName
                } else {
                    _uiState.value.currentFolderName ?: repo.resolveFolderDisplayName(destUri)
                }
                val candidates = repo.scanExternalImportFolder(sourceUri)
                val existingDestFiles = runCatching { repo.listExistingFileNamesLower(destUri) }
                    .getOrDefault(mutableSetOf())

                val alreadyImported = candidates.mapNotNull { cand ->
                    val expectedCbz = "${ExternalFolderImporter.cleanFilename(cand.folderName)}.cbz".lowercase(Locale.US)
                    if (expectedCbz in existingDestFiles) cand.folderName else null
                }.toSet()

                ExternalImportUiState(
                    sourceTreeUri = sourceUri,
                    sourceFolderName = sourceName,
                    destTreeUri = destUri,
                    destFolderName = destName,
                    isDestSameAsSource = destUri == sourceUri,
                    candidates = candidates,
                    alreadyImportedNames = alreadyImported,
                )
            }.onSuccess { importUi ->
                if (importUi.candidates.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            progress = null,
                            externalImport = null,
                            statusMessage = str(R.string.msg_external_none_found, importUi.sourceFolderName),
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            progress = null,
                            externalImport = importUi,
                        )
                    }
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        progress = null,
                        externalImport = null,
                        statusMessage = str(R.string.msg_external_scan_failed, err.localizedMessage ?: err.message ?: ""),
                    )
                }
            }
        }
    }

    fun setExternalImportDestination(destTreeUri: Uri) {
        val current = _uiState.value.externalImport ?: return
        viewModelScope.launch {
            runCatching {
                val destName = repo.resolveFolderDisplayName(destTreeUri)
                val existingDestFiles = runCatching { repo.listExistingFileNamesLower(destTreeUri) }
                    .getOrDefault(mutableSetOf())
                val alreadyImported = current.candidates.mapNotNull { cand ->
                    val expectedCbz = "${ExternalFolderImporter.cleanFilename(cand.folderName)}.cbz".lowercase(Locale.US)
                    if (expectedCbz in existingDestFiles) cand.folderName else null
                }.toSet()
                current.copy(
                    destTreeUri = destTreeUri,
                    destFolderName = destName,
                    isDestSameAsSource = destTreeUri == current.sourceTreeUri,
                    alreadyImportedNames = alreadyImported,
                )
            }.onSuccess { updated ->
                _uiState.update { it.copy(externalImport = updated) }
            }
        }
    }

    fun dismissExternalImportDialog() {
        _uiState.update { it.copy(externalImport = null) }
    }

    fun runExternalImport(
        selectedCandidates: List<ExternalFolderCandidate>,
        deleteSourceAfterImport: Boolean,
        sequentialRename: Boolean = false,
    ) {
        val importState = _uiState.value.externalImport ?: return
        if (selectedCandidates.isEmpty()) return

        _uiState.update { it.copy(externalImport = null) }

        viewModelScope.launch {
            val existingNamesLower = runCatching {
                repo.listExistingFileNamesLower(importState.destTreeUri)
            }.getOrDefault(mutableSetOf())

            val importedItems = mutableListOf<CbzDocumentItem>()
            var lastError: String? = null

            selectedCandidates.forEachIndexed { idx, candidate ->
                _uiState.update {
                    it.copy(
                        progress = ProgressState(
                            title = str(R.string.progress_importing_external, idx + 1, selectedCandidates.size),
                            detail = str(R.string.progress_preparing_cbz, candidate.folderName),
                            current = idx,
                            total = selectedCandidates.size,
                        ),
                    )
                }
                runCatching {
                    repo.importExternalFolderToTree(
                        candidate = candidate,
                        sourceTreeUri = importState.sourceTreeUri,
                        destTreeUri = importState.destTreeUri,
                        existingNamesLower = existingNamesLower,
                        deleteSourceAfterImport = deleteSourceAfterImport,
                        sequentialRename = sequentialRename,
                    ) { done, total ->
                        _uiState.update { s ->
                            s.copy(
                                progress = ProgressState(
                                    title = str(R.string.progress_importing_external, idx + 1, selectedCandidates.size),
                                    detail = "${candidate.folderName}.cbz ($done/$total)",
                                    current = done,
                                    total = total,
                                ),
                            )
                        }
                    }
                }.onSuccess { createdItem ->
                    importedItems += createdItem
                }.onFailure { err ->
                    lastError = err.localizedMessage ?: err.message
                }
            }

            val prevSettings = _uiState.value.settings
            val shouldAdoptDestAsLibrary = prevSettings.lastFolderUri.isNullOrBlank() && importedItems.isNotEmpty()
            val updatedSettings = if (shouldAdoptDestAsLibrary) {
                prevSettings.copy(lastFolderUri = importState.destTreeUri.toString()).also { prefs.save(it) }
            } else {
                prevSettings
            }

            val shouldMergeIntoList = shouldAdoptDestAsLibrary ||
                prevSettings.lastFolderUri == importState.destTreeUri.toString()

            val mergedItems = if (shouldMergeIntoList && importedItems.isNotEmpty()) {
                val byUri = _uiState.value.items.associateBy { it.uri }.toMutableMap()
                importedItems.forEach { byUri[it.uri] = it }
                byUri.values.sortedWith(compareBy(NaturalOrderComparator) { it.displayName })
            } else {
                _uiState.value.items
            }

            val summaryMsg = buildString {
                append(str(R.string.msg_import_complete, importedItems.size, selectedCandidates.size))
                if (lastError != null && importedItems.size < selectedCandidates.size) {
                    append(str(R.string.msg_import_partial_error, lastError ?: ""))
                }
            }

            _uiState.update {
                it.copy(
                    settings = updatedSettings,
                    currentFolderName = if (shouldAdoptDestAsLibrary) importState.destFolderName else it.currentFolderName,
                    items = mergedItems,
                    progress = null,
                    statusMessage = summaryMsg,
                )
            }

            if (shouldMergeIntoList && importedItems.isNotEmpty()) {
                loadThumbnailsFor(importedItems)
            }
        }
    }

    // --- Batch Operations ---

    fun batchRecompress(targetLevel: Int) {
        val targets = _uiState.value.selectedItems
        if (targets.isEmpty()) return
        val clampedLevel = targetLevel.coerceIn(0, 9)
        val updatedSettings = _uiState.value.settings.copy(lastBatchCompressLevel = clampedLevel)
        prefs.save(updatedSettings)
        _uiState.update { it.copy(settings = updatedSettings) }
        viewModelScope.launch {
            var beforeTotal = 0L
            var afterTotal = 0L
            var successCount = 0
            val updatedMap = _uiState.value.items.associateBy { it.uri }.toMutableMap()

            targets.forEachIndexed { idx, item ->
                _uiState.update {
                    it.copy(
                        progress = ProgressState(
                            title = str(R.string.progress_batch_compressing, idx + 1, targets.size),
                            detail = item.displayName,
                            current = idx + 1,
                            total = targets.size,
                        ),
                    )
                }
                runCatching {
                    val res = repo.recompressOnly(item.uri, targetLevel)
                    beforeTotal += res.beforeBytes
                    afterTotal += res.afterBytes
                    updatedMap[item.uri] = repo.inspectUri(item.uri, item.displayName)
                    successCount++
                }
            }

            _uiState.update {
                it.copy(
                    items = it.items.map { item -> updatedMap[item.uri] ?: item },
                    selectedUris = emptySet(),
                    progress = null,
                    statusMessage = str(
                        R.string.msg_batch_compress_done,
                        successCount,
                        targets.size,
                        formatBytes(beforeTotal),
                        formatBytes(afterTotal),
                    ),
                )
            }
        }
    }

    fun batchApplyTags(patch: BatchTagPatch, compressionLevel: Int) {
        val targets = _uiState.value.selectedItems
        if (targets.isEmpty() || !patch.hasChanges) return
        viewModelScope.launch {
            var successCount = 0
            val updatedMap = _uiState.value.items.associateBy { it.uri }.toMutableMap()

            targets.forEachIndexed { idx, item ->
                _uiState.update {
                    it.copy(
                        progress = ProgressState(
                            title = str(R.string.progress_batch_saving_tags, idx + 1, targets.size),
                            detail = item.displayName,
                            current = idx + 1,
                            total = targets.size,
                        ),
                    )
                }
                runCatching {
                    val merged = item.comicInfo.applyBatchPatch(patch)
                    repo.saveComicInfo(item.uri, merged, compressionLevel)
                    updatedMap[item.uri] = repo.inspectUri(item.uri, item.displayName)
                    successCount++
                }
            }

            _uiState.update {
                it.copy(
                    items = it.items.map { item -> updatedMap[item.uri] ?: item },
                    selectedUris = emptySet(),
                    progress = null,
                    statusMessage = str(R.string.msg_batch_tags_done, successCount, targets.size),
                )
            }
        }
    }

    fun batchParseFilenamesAndSave(overwriteExisting: Boolean, compressionLevel: Int) {
        val targets = _uiState.value.selectedItems
        if (targets.isEmpty()) return
        viewModelScope.launch {
            var successCount = 0
            val updatedMap = _uiState.value.items.associateBy { it.uri }.toMutableMap()

            targets.forEachIndexed { idx, item ->
                _uiState.update {
                    it.copy(
                        progress = ProgressState(
                            title = str(R.string.progress_batch_parsing, idx + 1, targets.size),
                            detail = item.displayName,
                            current = idx + 1,
                            total = targets.size,
                        ),
                    )
                }
                runCatching {
                    val parsed = FilenameParser.applyToComicInfo(item.comicInfo, item.displayName, overwriteExisting)
                    repo.saveComicInfo(item.uri, parsed, compressionLevel)
                    updatedMap[item.uri] = repo.inspectUri(item.uri, item.displayName)
                    successCount++
                }
            }

            _uiState.update {
                it.copy(
                    items = it.items.map { item -> updatedMap[item.uri] ?: item },
                    selectedUris = emptySet(),
                    progress = null,
                    statusMessage = str(R.string.msg_batch_parse_done, successCount, targets.size),
                )
            }
        }
    }

    fun batchRename(
        template: String,
        issuePadding: Int,
        smartCleanup: Boolean,
        useTitleForOneshot: Boolean,
    ) {
        val targets = _uiState.value.selectedItems
        if (targets.isEmpty()) return
        val updatedSettings = _uiState.value.settings.copy(
            renameTemplate = template,
            issuePadding = issuePadding.coerceIn(0, 6),
            smartCleanup = smartCleanup,
            useTitleForOneshotRename = useTitleForOneshot,
        )
        prefs.save(updatedSettings)
        _uiState.update { it.copy(settings = updatedSettings) }
        viewModelScope.launch {
            var renamedCount = 0
            var skippedCount = 0
            var failedCount = 0
            val currentItems = _uiState.value.items.toMutableList()
            val thumbs = _uiState.value.thumbnails.toMutableMap()

            val existingNamesLower = _uiState.value.settings.lastFolderUri
                ?.takeIf { it.isNotBlank() }
                ?.let { runCatching { repo.listExistingFileNamesLower(Uri.parse(it)) }.getOrNull() }
                ?: currentItems.mapTo(mutableSetOf()) { it.displayName.lowercase(Locale.US) }

            targets.forEachIndexed { idx, item ->
                if (!FileRenamer.hasMetadataForTemplate(item.comicInfo, template, useTitleForOneshot)) {
                    skippedCount++
                    return@forEachIndexed
                }

                val desiredName = FileRenamer.formatFilename(
                    info = item.comicInfo,
                    template = template,
                    issuePadding = issuePadding,
                    smartCleanup = smartCleanup,
                    useTitleForOneshot = useTitleForOneshot,
                )
                if (desiredName == item.displayName) {
                    skippedCount++
                    return@forEachIndexed
                }

                val oldLower = item.displayName.lowercase(Locale.US)
                existingNamesLower.remove(oldLower)
                val targetName = FileRenamer.uniqueTargetFilename(desiredName, existingNamesLower)

                _uiState.update {
                    it.copy(
                        progress = ProgressState(
                            title = str(R.string.progress_batch_renaming, idx + 1, targets.size),
                            detail = "${item.displayName} → $targetName",
                            current = idx + 1,
                            total = targets.size,
                        ),
                    )
                }

                val renamed = repo.renameDocument(item.uri, targetName)
                if (renamed != null) {
                    val (newUri, actualName) = renamed
                    existingNamesLower.add(actualName.lowercase(Locale.US))
                    val pos = currentItems.indexOfFirst { it.uri == item.uri }
                    if (pos >= 0) {
                        currentItems[pos] = item.copy(uri = newUri, displayName = actualName)
                    }
                    thumbs.remove(item.uri)?.let { thumbs[newUri] = it }
                    renamedCount++
                } else {
                    existingNamesLower.add(oldLower)
                    failedCount++
                }
            }

            currentItems.sortWith(compareBy(NaturalOrderComparator) { it.displayName })

            val summaryMsg = buildString {
                append(str(R.string.msg_batch_rename_done, renamedCount, targets.size))
                val extras = buildList {
                    if (skippedCount > 0) add(str(R.string.msg_batch_rename_skipped, skippedCount))
                    if (failedCount > 0) add(str(R.string.msg_batch_rename_failed, failedCount))
                }
                if (extras.isNotEmpty()) {
                    append(" (${extras.joinToString(", ")})")
                }
            }

            _uiState.update {
                it.copy(
                    items = currentItems,
                    thumbnails = thumbs,
                    selectedUris = emptySet(),
                    progress = null,
                    statusMessage = summaryMsg,
                )
            }
        }
    }

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes < 1024) return "${bytes}B"
            val kb = bytes / 1024.0
            if (kb < 1024) return String.format("%.1fKB", kb)
            val mb = kb / 1024.0
            if (mb < 1024) return String.format("%.2fMB", mb)
            return String.format("%.2fGB", mb / 1024.0)
        }
    }
}
