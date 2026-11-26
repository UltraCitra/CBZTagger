package com.cbztagger.app.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cbztagger.app.core.BatchTagPatch
import com.cbztagger.app.core.ComicInfo
import com.cbztagger.app.core.ExternalFolderImporter
import com.cbztagger.app.core.FileRenamer
import com.cbztagger.app.core.FilenameParser
import com.cbztagger.app.core.NaturalOrderComparator
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
) {
    val hasUnsavedChanges: Boolean
        get() = isDirty && draft != item.comicInfo
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

    private val _uiState = MutableStateFlow(MainUiState(settings = prefs.load()))
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        val lastFolder = _uiState.value.settings.lastFolderUri
        if (!lastFolder.isNullOrBlank()) {
            openFolder(Uri.parse(lastFolder), persist = false)
        }
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
                    progress = ProgressState("CBZ 폴더 스캔 중..."),
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
                            statusMessage = "CBZ ${loaded.size}개 파일을 불러왔습니다.",
                        )
                    }
                    loadThumbnailsFor(loaded)
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            progress = null,
                            statusMessage = "폴더 열기 실패: ${err.localizedMessage ?: err.message}",
                        )
                    }
                }
        }
    }

    fun addIndividualFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(progress = ProgressState("CBZ 파일 읽는 중...", total = uris.size)) }
            val existing = _uiState.value.items.associateBy { it.uri }.toMutableMap()
            var added = 0
            uris.forEachIndexed { index, uri ->
                _uiState.update {
                    it.copy(progress = ProgressState("CBZ 파일 읽는 중...", current = index + 1, total = uris.size))
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
                    statusMessage = "CBZ ${added}개 파일을 추가했습니다.",
                )
            }
            loadThumbnailsFor(list)
        }
    }

    fun openSingleFromIntent(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(progress = ProgressState("CBZ 파일 여는 중...")) }
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
                            statusMessage = "파일 열기 실패: ${err.localizedMessage ?: err.message}",
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
                statusMessage = "파일명에서 시리즈/권수/연도를 추출했습니다.",
            )
        }
    }

    fun saveEditorComicInfo(navigateBackAfterSave: Boolean = false) {
        val ed = _uiState.value.editor ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(progress = ProgressState("태그 저장 및 압축 중...", detail = ed.item.displayName))
            }
            runCatching {
                val res = repo.saveComicInfo(
                    uri = ed.item.uri,
                    comicInfo = ed.draft,
                    compressionLevel = _uiState.value.settings.zipCompressionLevel,
                ) { done, total ->
                    _uiState.update { s ->
                        s.copy(
                            progress = ProgressState(
                                title = "태그 저장 중...",
                                detail = "${ed.item.displayName} ($done/$total)",
                                current = done,
                                total = total,
                            ),
                        )
                    }
                }
                val refreshed = repo.inspectUri(ed.item.uri, ed.item.displayName)
                res to refreshed
            }.onSuccess { (res, refreshed) ->
                if (navigateBackAfterSave) {
                    repo.closeEditorSession()
                }
                _uiState.update { state ->
                    state.copy(
                        screen = if (navigateBackAfterSave) ActiveScreen.LIBRARY else state.screen,
                        items = state.items.map { if (it.uri == refreshed.uri) refreshed else it },
                        editor = ed.copy(item = refreshed, draft = refreshed.comicInfo, isDirty = false),
                        progress = null,
                        statusMessage = "저장 완료 (${formatBytes(res.beforeBytes)} → ${formatBytes(res.afterBytes)})",
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        progress = null,
                        statusMessage = "저장 실패: ${err.localizedMessage ?: err.message}",
                    )
                }
            }
        }
    }

    fun deleteEditorComicInfo() {
        val ed = _uiState.value.editor ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(progress = ProgressState("ComicInfo.xml 삭제 중...", detail = ed.item.displayName)) }
            runCatching {
                repo.removeComicInfo(ed.item.uri, compressionLevel = -1)
                repo.inspectUri(ed.item.uri, ed.item.displayName)
            }.onSuccess { refreshed ->
                _uiState.update { state ->
                    state.copy(
                        items = state.items.map { if (it.uri == refreshed.uri) refreshed else it },
                        editor = ed.copy(item = refreshed, draft = refreshed.comicInfo, isDirty = false),
                        progress = null,
                        statusMessage = "ComicInfo.xml 태그를 삭제했습니다.",
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        progress = null,
                        statusMessage = "태그 삭제 실패: ${err.localizedMessage ?: err.message}",
                    )
                }
            }
        }
    }

    fun renameEditorFile(newName: String) {
        val ed = _uiState.value.editor ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(progress = ProgressState("파일 이름 변경 중...", detail = newName)) }
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
                        statusMessage = "이름 변경 완료: $actualName",
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        progress = null,
                        statusMessage = "파일 이름 변경 실패 (권한 또는 동일 파일명 존재)",
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
                .getOrElse { "외부 폴더" }
            val updated = _uiState.value.settings.copy(
                externalImportFolderUri = treeUri.toString(),
                externalImportFolderName = folderName,
            )
            prefs.save(updated)
            _uiState.update {
                it.copy(
                    settings = updated,
                    statusMessage = if (openImportDialogAfter) null else "외부 불러오기 경로 설정: $folderName",
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
                statusMessage = "외부 불러오기 경로가 초기화되었습니다.",
            )
        }
    }

    fun openExternalImportDialog() {
        val extUriStr = _uiState.value.settings.externalImportFolderUri
        if (extUriStr.isNullOrBlank()) {
            _uiState.update { it.copy(statusMessage = "먼저 외부 불러오기 경로를 선택해 주세요.") }
            return
        }
        val sourceUri = Uri.parse(extUriStr)
        val destUri = _uiState.value.settings.lastFolderUri
            ?.takeIf { it.isNotBlank() }
            ?.let { Uri.parse(it) }
            ?: sourceUri

        viewModelScope.launch {
            _uiState.update { it.copy(progress = ProgressState("외부 폴더 스캔 중...")) }
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
                            statusMessage = "외부 경로(${importUi.sourceFolderName})에서 이미지가 포함된 폴더를 찾지 못했습니다.",
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
                        statusMessage = "외부 폴더 스캔 실패: ${err.localizedMessage ?: err.message}",
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
                            title = "외부 파일 불러오는 중 (${idx + 1}/${selectedCandidates.size})",
                            detail = "${candidate.folderName}.cbz 준비 중...",
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
                    ) { done, total ->
                        _uiState.update { s ->
                            s.copy(
                                progress = ProgressState(
                                    title = "외부 파일 불러오는 중 (${idx + 1}/${selectedCandidates.size})",
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
                append("외부 불러오기 완료: ${importedItems.size}/${selectedCandidates.size}개 (무압축 보관)")
                if (lastError != null && importedItems.size < selectedCandidates.size) {
                    append(" · 일부 오류: $lastError")
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
                            title = "일괄 압축 중 (${idx + 1}/${targets.size})",
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
                    statusMessage = "일괄 압축 완료: $successCount/${targets.size}개 (${formatBytes(beforeTotal)} → ${formatBytes(afterTotal)})",
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
                            title = "일괄 태그 저장 중 (${idx + 1}/${targets.size})",
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
                    statusMessage = "일괄 태그 저장 완료: $successCount/${targets.size}개",
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
                            title = "파일명 파싱 및 저장 중 (${idx + 1}/${targets.size})",
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
                    statusMessage = "파일명 파싱 저장 완료: $successCount/${targets.size}개",
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
                            title = "일괄 이름 변경 중 (${idx + 1}/${targets.size})",
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
                append("일괄 이름 변경 완료: ${renamedCount}/${targets.size}개")
                val extras = buildList {
                    if (skippedCount > 0) add("건너뜀 $skippedCount")
                    if (failedCount > 0) add("실패 $failedCount")
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
