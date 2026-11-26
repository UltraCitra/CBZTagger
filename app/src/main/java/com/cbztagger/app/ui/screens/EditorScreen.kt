package com.cbztagger.app.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cbztagger.app.core.ComicInfo
import com.cbztagger.app.core.FileRenamer
import com.cbztagger.app.core.PageBatchHelper
import com.cbztagger.app.core.PageInfo
import com.cbztagger.app.core.PagePreviewNavigation
import com.cbztagger.app.core.PageType
import com.cbztagger.app.data.AppSettings
import com.cbztagger.app.ui.EditorState
import com.cbztagger.app.ui.MainViewModel
import com.cbztagger.app.ui.components.AuthorCreditInput
import com.cbztagger.app.ui.components.MangaSelectorRow
import com.cbztagger.app.ui.dialogs.BatchBookmarkDialog
import com.cbztagger.app.ui.dialogs.PageRangeSelectDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    editor: EditorState,
    settings: AppSettings,
    coverThumbnail: Bitmap?,
    onBack: () -> Unit,
    onDraftChange: ((ComicInfo) -> ComicInfo) -> Unit,
    onCompressionLevelChange: (Int) -> Unit,
    onParseFilename: () -> Unit,
    onSave: () -> Unit,
    onDeleteTags: () -> Unit,
    onRenameFile: (String) -> Unit,
    onLoadPagePreview: suspend (Uri, String) -> Bitmap?,
    onLoadPageThumbnail: suspend (Uri, String) -> Bitmap?,
) {
    val itemKey = editor.item.uri.toString()
    var tabIndex by rememberSaveable(itemKey) { mutableIntStateOf(0) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var previewPageIndex by rememberSaveable(itemKey) { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = editor.item.displayName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "${editor.item.pageCount}p · ${MainViewModel.formatBytes(editor.item.sizeBytes)} · ${editor.item.compressionSummary}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    IconButton(onClick = onParseFilename) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = "파일명에서 태그 추출")
                    }
                    IconButton(onClick = { showRenameDialog = true }) {
                        Icon(Icons.Default.DriveFileRenameOutline, contentDescription = "파일 이름 변경")
                    }
                    if (editor.item.hasComicInfo) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "태그 삭제")
                        }
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 4.dp, shadowElevation = 8.dp) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Button(
                        onClick = onSave,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        val saveLabel = if (settings.zipCompressionLevel < 0) {
                            "태그 저장 (기존 유지)"
                        } else {
                            "태그 저장 (압축 ${settings.zipCompressionLevel})"
                        }
                        Text(saveLabel)
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TabRow(selectedTabIndex = tabIndex) {
                Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("기본 정보") })
                Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("상세 · 크레딧") })
                Tab(selected = tabIndex == 2, onClick = { tabIndex = 2 }, text = { Text("페이지 (${editor.pageNames.size})") })
            }

            when (tabIndex) {
                0 -> BasicInfoTab(
                    info = editor.draft,
                    coverThumbnail = coverThumbnail,
                    onDraftChange = onDraftChange,
                    onParseFilename = onParseFilename,
                )
                1 -> CreditsAndDetailsTab(
                    itemUri = editor.item.uri,
                    info = editor.draft,
                    onDraftChange = onDraftChange,
                )
                2 -> PagesTab(
                    itemUri = editor.item.uri,
                    pageNames = editor.pageNames,
                    pages = editor.draft.pages,
                    showThumbnails = settings.showPageListThumbnails,
                    onLoadThumbnail = onLoadPageThumbnail,
                    onUpdatePage = { index, updated ->
                        onDraftChange { info ->
                            val current = PageBatchHelper.ensurePagesSize(info.pages, editor.pageNames.size).toMutableList()
                            if (index in current.indices) {
                                current[index] = updated
                            }
                            info.copy(pages = current)
                        }
                    },
                    onReplaceAllPages = { updatedPages ->
                        onDraftChange { info -> info.copy(pages = updatedPages) }
                    },
                    onPreviewClick = { index -> previewPageIndex = index },
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("ComicInfo.xml 삭제") },
            text = { Text("이 CBZ 파일에서 ComicInfo.xml 태그를 삭제하시겠습니까?") },
            confirmButton = {
                Button(onClick = {
                    showDeleteConfirm = false
                    onDeleteTags()
                }) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("취소") }
            },
        )
    }

    if (showRenameDialog) {
        val suggested = FileRenamer.formatFilename(
            info = editor.draft,
            template = settings.renameTemplate,
            issuePadding = settings.issuePadding,
            smartCleanup = settings.smartCleanup,
            useTitleForOneshot = settings.useTitleForOneshotRename,
        )
        var newName by remember(suggested) { mutableStateOf(suggested) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("파일 이름 변경") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("현재: ${editor.item.displayName}", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("새 파일명 (.cbz)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRenameDialog = false
                        val finalName = if (newName.endsWith(".cbz", true)) newName else "$newName.cbz"
                        onRenameFile(finalName)
                    },
                ) { Text("변경") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("취소") }
            },
        )
    }

    val indexToPreview = previewPageIndex
    if (indexToPreview != null && indexToPreview in editor.pageNames.indices) {
        val isRtlPreview = PagePreviewNavigation.isRtlDirection(
            mangaTag = editor.draft.manga,
            followMangaTag = settings.previewFollowMangaDirection,
        )
        PageImagePreviewDialog(
            uri = editor.item.uri,
            pageNames = editor.pageNames,
            pages = editor.draft.pages,
            initialIndex = indexToPreview,
            isRtl = isRtlPreview,
            onLoad = onLoadPagePreview,
            onDismiss = { previewPageIndex = null },
        )
    }
}

@Composable
private fun BasicInfoTab(
    info: ComicInfo,
    coverThumbnail: Bitmap?,
    onDraftChange: ((ComicInfo) -> ComicInfo) -> Unit,
    onParseFilename: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(width = 76.dp, height = 108.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (coverThumbnail != null) {
                    Image(
                        bitmap = coverThumbnail.asImageBitmap(),
                        contentDescription = "표지",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(32.dp))
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = info.series,
                    onValueChange = { v -> onDraftChange { it.copy(series = v) } },
                    label = { Text("시리즈 (Series)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = onParseFilename, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("파일명에서 자동 채우기")
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = info.number,
                onValueChange = { v -> onDraftChange { it.copy(number = v) } },
                label = { Text("번호 (Number)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = info.count?.toString().orEmpty(),
                onValueChange = { v -> onDraftChange { it.copy(count = v.filter(Char::isDigit).toIntOrNull()) } },
                label = { Text("전체 (Count)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = info.volume?.toString().orEmpty(),
                onValueChange = { v -> onDraftChange { it.copy(volume = v.filter(Char::isDigit).toIntOrNull()) } },
                label = { Text("볼륨 (Volume)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = info.title,
                onValueChange = { v -> onDraftChange { it.copy(title = v) } },
                label = { Text("제목 (Title)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            val canGenerateTitle = info.series.isNotBlank() && info.number.isNotBlank()
            FilledTonalButton(
                onClick = {
                    val generated = "${info.series.trim()} ${info.number.trim()}"
                    onDraftChange { it.copy(title = generated) }
                },
                enabled = canGenerateTitle,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
            ) {
                Text("시리즈+번호", style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = info.publisher,
                onValueChange = { v -> onDraftChange { it.copy(publisher = v) } },
                label = { Text("출판사 (Publisher)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = info.imprint,
                onValueChange = { v -> onDraftChange { it.copy(imprint = v) } },
                label = { Text("임프린트 (Imprint)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = info.year?.toString().orEmpty(),
                onValueChange = { v -> onDraftChange { it.copy(year = v.filter(Char::isDigit).take(4).toIntOrNull()) } },
                label = { Text("연도") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = info.month?.toString().orEmpty(),
                onValueChange = { v -> onDraftChange { it.copy(month = v.filter(Char::isDigit).take(2).toIntOrNull()) } },
                label = { Text("월") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = info.day?.toString().orEmpty(),
                onValueChange = { v -> onDraftChange { it.copy(day = v.filter(Char::isDigit).take(2).toIntOrNull()) } },
                label = { Text("일") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = info.genre,
                onValueChange = { v -> onDraftChange { it.copy(genre = v) } },
                label = { Text("장르 (쉼표 구분)") },
                singleLine = true,
                modifier = Modifier.weight(2f),
            )
            OutlinedTextField(
                value = info.languageIso,
                onValueChange = { v -> onDraftChange { it.copy(languageIso = v) } },
                label = { Text("언어 (ko/en/ja)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        MangaSelectorRow(
            manga = info.manga,
            onMangaChange = { v -> onDraftChange { it.copy(manga = v) } },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("흑백 만화 (BlackAndWhite)", style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = info.blackAndWhite == true,
                onCheckedChange = { checked -> onDraftChange { it.copy(blackAndWhite = if (checked) true else null) } },
            )
        }

        OutlinedTextField(
            value = info.summary,
            onValueChange = { v -> onDraftChange { it.copy(summary = v) } },
            label = { Text("줄거리 (Summary)") },
            minLines = 3,
            maxLines = 6,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = info.notes,
            onValueChange = { v -> onDraftChange { it.copy(notes = v) } },
            label = { Text("메모 (Notes)") },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CreditsAndDetailsTab(
    itemUri: Uri,
    info: ComicInfo,
    onDraftChange: ((ComicInfo) -> ComicInfo) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("제작진 (크레딧)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        AuthorCreditInput(
            writer = info.writer,
            penciller = info.penciller,
            resetKey = itemUri,
            onChange = { w, p -> onDraftChange { it.copy(writer = w, penciller = p) } },
        )
        OutlinedTextField(value = info.inker, onValueChange = { v -> onDraftChange { it.copy(inker = v) } }, label = { Text("잉커 (Inker)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = info.colorist, onValueChange = { v -> onDraftChange { it.copy(colorist = v) } }, label = { Text("채색 (Colorist)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = info.letterer, onValueChange = { v -> onDraftChange { it.copy(letterer = v) } }, label = { Text("식자 (Letterer)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = info.coverArtist, onValueChange = { v -> onDraftChange { it.copy(coverArtist = v) } }, label = { Text("표지작가 (CoverArtist)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = info.editor, onValueChange = { v -> onDraftChange { it.copy(editor = v) } }, label = { Text("편집자 (Editor)") }, singleLine = true, modifier = Modifier.fillMaxWidth())

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Text("추가 분류 정보", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        OutlinedTextField(value = info.storyArc, onValueChange = { v -> onDraftChange { it.copy(storyArc = v) } }, label = { Text("스토리 아크 (StoryArc)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = info.seriesGroup, onValueChange = { v -> onDraftChange { it.copy(seriesGroup = v) } }, label = { Text("시리즈 그룹 (SeriesGroup)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = info.characters, onValueChange = { v -> onDraftChange { it.copy(characters = v) } }, label = { Text("등장인물 (Characters)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = info.teams, onValueChange = { v -> onDraftChange { it.copy(teams = v) } }, label = { Text("팀 (Teams)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = info.locations, onValueChange = { v -> onDraftChange { it.copy(locations = v) } }, label = { Text("장소 (Locations)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(value = info.ageRating, onValueChange = { v -> onDraftChange { it.copy(ageRating = v) } }, label = { Text("연령 등급") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(value = info.format, onValueChange = { v -> onDraftChange { it.copy(format = v) } }, label = { Text("포맷 (TPB 등)") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        OutlinedTextField(value = info.scanInformation, onValueChange = { v -> onDraftChange { it.copy(scanInformation = v) } }, label = { Text("스캔 정보 (ScanInformation)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = info.web, onValueChange = { v -> onDraftChange { it.copy(web = v) } }, label = { Text("웹 링크 (Web)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PagesTab(
    itemUri: Uri,
    pageNames: List<String>,
    pages: List<PageInfo>,
    showThumbnails: Boolean,
    onLoadThumbnail: suspend (Uri, String) -> Bitmap?,
    onUpdatePage: (Int, PageInfo) -> Unit,
    onReplaceAllPages: (List<PageInfo>) -> Unit,
    onPreviewClick: (Int) -> Unit,
) {
    if (pageNames.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("페이지 목록을 불러오는 중이거나 이미지가 없습니다.")
        }
        return
    }

    val pageCount = pageNames.size
    val normalizedPages = remember(pages, pageCount) { PageBatchHelper.ensurePagesSize(pages, pageCount) }
    val emptyTypeCount = remember(normalizedPages) { normalizedPages.count { it.type.isBlank() } }

    var batchMode by rememberSaveable(itemUri.toString()) { mutableStateOf(false) }
    var selectedIndices by remember { mutableStateOf(emptySet<Int>()) }
    var showRangeDialog by remember { mutableStateOf(false) }
    var showBatchTypeMenu by remember { mutableStateOf(false) }
    var showBatchBookmarkDialog by remember { mutableStateOf(false) }
    var bookmarkDialogPageIndex by remember { mutableStateOf<Int?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Sticky top action bar for batch page attribute editing & bulk bookmarks
        Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            if (!batchMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalButton(
                        onClick = {
                            batchMode = true
                            selectedIndices = emptySet()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Checklist, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("일괄 속성 편집", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = { showBatchBookmarkDialog = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("북마크 지정", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "일괄 속성 편집 (${selectedIndices.size}/${pageCount} 선택)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        TextButton(
                            onClick = {
                                batchMode = false
                                selectedIndices = emptySet()
                            },
                        ) {
                            Text("완료")
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AssistChip(
                            onClick = {
                                selectedIndices = if (selectedIndices.size == pageCount) {
                                    emptySet()
                                } else {
                                    (0 until pageCount).toSet()
                                }
                            },
                            label = { Text(if (selectedIndices.size == pageCount) "전체 해제" else "전체 선택") },
                        )
                        AssistChip(
                            onClick = { showRangeDialog = true },
                            label = { Text("범위 선택 (시작~끝)") },
                        )
                        AssistChip(
                            onClick = {
                                val (updated, _) = PageBatchHelper.fillEmptyTypesWithStory(normalizedPages, pageCount)
                                onReplaceAllPages(updated)
                            },
                            enabled = emptyTypeCount > 0,
                            label = { Text("빈 페이지 일괄 Story ($emptyTypeCount)") },
                        )
                        AssistChip(
                            onClick = { showBatchBookmarkDialog = true },
                            label = { Text("북마크 지정") },
                        )
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { showBatchTypeMenu = true },
                            enabled = selectedIndices.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("선택한 ${selectedIndices.size}페이지에 속성 일괄 적용")
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = showBatchTypeMenu,
                            onDismissRequest = { showBatchTypeMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("일반 (속성 초기화)") },
                                onClick = {
                                    showBatchTypeMenu = false
                                    onReplaceAllPages(
                                        PageBatchHelper.applyTypeToSelection(normalizedPages, pageCount, selectedIndices, ""),
                                    )
                                    selectedIndices = emptySet()
                                },
                            )
                            PageType.entries.forEach { pt ->
                                DropdownMenuItem(
                                    text = { Text(pt.labelKo) },
                                    onClick = {
                                        showBatchTypeMenu = false
                                        onReplaceAllPages(
                                            PageBatchHelper.applyTypeToSelection(
                                                normalizedPages,
                                                pageCount,
                                                selectedIndices,
                                                pt.xmlValue,
                                            ),
                                        )
                                        selectedIndices = emptySet()
                                    },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("양면(DoublePage) 일괄 설정") },
                                onClick = {
                                    showBatchTypeMenu = false
                                    onReplaceAllPages(
                                        PageBatchHelper.applyDoublePageToSelection(
                                            normalizedPages,
                                            pageCount,
                                            selectedIndices,
                                            true,
                                        ),
                                    )
                                    selectedIndices = emptySet()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("양면(DoublePage) 일괄 해제") },
                                onClick = {
                                    showBatchTypeMenu = false
                                    onReplaceAllPages(
                                        PageBatchHelper.applyDoublePageToSelection(
                                            normalizedPages,
                                            pageCount,
                                            selectedIndices,
                                            null,
                                        ),
                                    )
                                    selectedIndices = emptySet()
                                },
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider()

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(pageNames) { idx, entryName ->
                val pageInfo = normalizedPages[idx]
                var typeExpanded by remember { mutableStateOf(false) }
                val hasBookmark = pageInfo.bookmark.isNotBlank()
                val isSelected = idx in selectedIndices

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (batchMode && isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        )
                        .combinedClickable(
                            onClick = {
                                if (batchMode) {
                                    selectedIndices = if (isSelected) selectedIndices - idx else selectedIndices + idx
                                } else {
                                    onPreviewClick(idx)
                                }
                            },
                            onLongClick = { bookmarkDialogPageIndex = idx },
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (batchMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { checked ->
                                selectedIndices = if (checked) selectedIndices + idx else selectedIndices - idx
                            },
                        )
                        if (showThumbnails) {
                            Spacer(Modifier.width(4.dp))
                            PageListThumbnail(
                                uri = itemUri,
                                entryName = entryName,
                                onLoad = onLoadThumbnail,
                            )
                        }
                    } else if (showThumbnails) {
                        PageListThumbnail(
                            uri = itemUri,
                            entryName = entryName,
                            onLoad = onLoadThumbnail,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "#${idx + 1}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "· ${entryName.substringAfterLast('/')}",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (hasBookmark) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bookmark,
                                    contentDescription = "북마크",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = buildString {
                                        append(": ${pageInfo.bookmark}")
                                        if (pageInfo.doublePage == true) append(" · 양면 페이지")
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        } else {
                            val subtitle = if (pageInfo.doublePage == true) {
                                "양면 페이지"
                            } else if (batchMode) {
                                "탭하여 선택"
                            } else {
                                "탭하여 미리보기 · 길게 눌러 북마크 편집"
                            }
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (batchMode) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (pageInfo.type.isNotBlank()) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        ) {
                            Text(
                                text = pageInfo.type.ifBlank { "일반" },
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    } else {
                        Box {
                            TextButton(onClick = { typeExpanded = true }) {
                                Text(pageInfo.type.ifBlank { "일반" })
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                            DropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text("일반 (지정 안 함)") },
                                    onClick = {
                                        typeExpanded = false
                                        onUpdatePage(idx, pageInfo.copy(type = ""))
                                    },
                                )
                                PageType.entries.forEach { pt ->
                                    DropdownMenuItem(
                                        text = { Text(pt.labelKo) },
                                        onClick = {
                                            typeExpanded = false
                                            onUpdatePage(idx, pageInfo.copy(type = pt.xmlValue))
                                        },
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(if (pageInfo.doublePage == true) "양면 해제" else "양면(DoublePage) 설정") },
                                    onClick = {
                                        typeExpanded = false
                                        val next = if (pageInfo.doublePage == true) null else true
                                        onUpdatePage(idx, pageInfo.copy(doublePage = next))
                                    },
                                )
                            }
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }

    if (showRangeDialog) {
        PageRangeSelectDialog(
            pageCount = pageCount,
            onApplyRange = { indices ->
                selectedIndices = selectedIndices + indices
                showRangeDialog = false
            },
            onDismiss = { showRangeDialog = false },
        )
    }

    if (showBatchBookmarkDialog) {
        BatchBookmarkDialog(
            pageCount = pageCount,
            pages = normalizedPages,
            onSave = { entries ->
                showBatchBookmarkDialog = false
                onReplaceAllPages(PageBatchHelper.applyBulkBookmarks(normalizedPages, pageCount, entries))
            },
            onDismiss = { showBatchBookmarkDialog = false },
        )
    }

    val targetIdx = bookmarkDialogPageIndex
    if (targetIdx != null) {
        val currentPageInfo = normalizedPages[targetIdx]
        PageBookmarkDialog(
            pageNumber = targetIdx + 1,
            existingBookmark = currentPageInfo.bookmark,
            onSave = { newBookmark ->
                bookmarkDialogPageIndex = null
                onUpdatePage(targetIdx, currentPageInfo.copy(bookmark = newBookmark.trim()))
            },
            onDelete = {
                bookmarkDialogPageIndex = null
                onUpdatePage(targetIdx, currentPageInfo.copy(bookmark = ""))
            },
            onDismiss = { bookmarkDialogPageIndex = null },
        )
    }
}

@Composable
private fun PageBookmarkDialog(
    pageNumber: Int,
    existingBookmark: String,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var bookmarkName by remember(pageNumber, existingBookmark) { mutableStateOf(existingBookmark) }
    val hasExisting = existingBookmark.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("페이지 #$pageNumber 북마크") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = bookmarkName,
                    onValueChange = { bookmarkName = it },
                    label = { Text("북마크 이름") },
                    placeholder = { Text("예: 1화, 목차, 챕터 시작") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (hasExisting) {
                    TextButton(
                        onClick = onDelete,
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("북마크 삭제", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(bookmarkName) },
                enabled = bookmarkName.isNotBlank(),
            ) {
                Text("저장")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        },
    )
}

@Composable
private fun PageListThumbnail(
    uri: Uri,
    entryName: String,
    onLoad: suspend (Uri, String) -> Bitmap?,
) {
    // Loaded strictly on demand while row is visible; never cached in ViewModel or disk
    var bitmap by remember(uri, entryName) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(uri, entryName) {
        bitmap = null
        bitmap = onLoad(uri, entryName)
    }

    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 62.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = entryName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun PageImagePreviewDialog(
    uri: Uri,
    pageNames: List<String>,
    pages: List<PageInfo>,
    initialIndex: Int,
    isRtl: Boolean,
    onLoad: suspend (Uri, String) -> Bitmap?,
    onDismiss: () -> Unit,
) {
    var currentIndex by rememberSaveable(uri.toString(), initialIndex) {
        mutableIntStateOf(initialIndex.coerceIn(0, (pageNames.size - 1).coerceAtLeast(0)))
    }
    val entryName = pageNames.getOrNull(currentIndex) ?: return
    val currentPageInfo = pages.firstOrNull { it.image == currentIndex }
    val pageMetaText = buildList {
        val typeVal = currentPageInfo?.type.orEmpty()
        if (typeVal.isNotBlank()) add(typeVal)
        if (currentPageInfo?.doublePage == true) add("양면")
        val bm = currentPageInfo?.bookmark.orEmpty()
        if (bm.isNotBlank()) add("북마크: $bm")
    }.joinToString(" · ")

    // Keep previous bitmap visible during decode so turning pages never collapses/re-animates the dialog
    var displayedBitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var loading by remember(uri) { mutableStateOf(true) }
    var loadFailed by remember(uri) { mutableStateOf(false) }

    val leftTarget = PagePreviewNavigation.leftArrowTargetIndex(currentIndex, pageNames.size, isRtl)
    val rightTarget = PagePreviewNavigation.rightArrowTargetIndex(currentIndex, pageNames.size, isRtl)

    LaunchedEffect(uri, entryName) {
        loading = true
        loadFailed = false
        val loaded = onLoad(uri, entryName)
        displayedBitmap = loaded
        loadFailed = loaded == null
        loading = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.82f),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "#${currentIndex + 1} / ${pageNames.size} · ${entryName.substringAfterLast('/')}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (pageMetaText.isNotBlank()) {
                                Text(
                                    text = pageMetaText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Text(
                            text = if (isRtl) {
                                "← 다음 · 우→좌 (Manga) · 이전 →"
                            } else {
                                "← 이전 · 좌→우 · 다음 →"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "닫기")
                    }
                }
                HorizontalDivider()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 4.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { leftTarget?.let { currentIndex = it } },
                        enabled = leftTarget != null,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = if (isRtl) "다음 페이지" else "이전 페이지",
                            modifier = Modifier.size(30.dp),
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .pointerInput(currentIndex, pageNames.size, isRtl) {
                                var totalDrag = 0f
                                detectHorizontalDragGestures(
                                    onDragStart = { totalDrag = 0f },
                                    onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
                                    onDragEnd = {
                                        PagePreviewNavigation.swipeTargetIndex(
                                            currentIndex = currentIndex,
                                            pageCount = pageNames.size,
                                            dragDeltaX = totalDrag,
                                            isRtl = isRtl,
                                        )?.let { target ->
                                            currentIndex = target
                                        }
                                        totalDrag = 0f
                                    },
                                    onDragCancel = { totalDrag = 0f },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        val bmp = displayedBitmap
                        when {
                            bmp != null -> Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = entryName,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )
                            loading -> CircularProgressIndicator()
                            loadFailed -> Text("이미지를 표시할 수 없습니다.")
                        }
                    }

                    IconButton(
                        onClick = { rightTarget?.let { currentIndex = it } },
                        enabled = rightTarget != null,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = if (isRtl) "이전 페이지" else "다음 페이지",
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
            }
        }
    }
}
