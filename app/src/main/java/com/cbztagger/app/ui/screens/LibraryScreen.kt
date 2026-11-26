package com.cbztagger.app.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cbztagger.app.core.BatchTagPatch
import com.cbztagger.app.core.FileRenamer
import com.cbztagger.app.core.ZipCompressionOption
import com.cbztagger.app.data.CbzDocumentItem
import com.cbztagger.app.ui.MainUiState
import com.cbztagger.app.ui.MainViewModel
import com.cbztagger.app.ui.components.AuthorCreditInput
import com.cbztagger.app.ui.components.MangaSelectorRow
import com.cbztagger.app.ui.components.ZipCompressionControl
import com.cbztagger.app.ui.components.ZipCompressionSlider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: MainUiState,
    onPickFolder: () -> Unit,
    onPickFiles: () -> Unit,
    onImportExternal: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenEditor: (CbzDocumentItem) -> Unit,
    onToggleSelect: (Uri) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onBatchRecompress: (Int) -> Unit,
    onBatchApplyTags: (BatchTagPatch, Int) -> Unit,
    onBatchParseFilenames: (Boolean, Int) -> Unit,
    onBatchRename: (String, Int, Boolean, Boolean) -> Unit,
) {
    var showBatchCompress by remember { mutableStateOf(false) }
    var showBatchTags by remember { mutableStateOf(false) }
    var showBatchParse by remember { mutableStateOf(false) }
    var showBatchRename by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (state.isSelectionMode) {
                TopAppBar(
                    title = { Text("${state.selectedUris.size}개 선택됨") },
                    navigationIcon = {
                        IconButton(onClick = onClearSelection) {
                            Icon(Icons.Default.Close, contentDescription = "선택 해제")
                        }
                    },
                    actions = {
                        IconButton(onClick = onSelectAll) {
                            Icon(Icons.Default.SelectAll, contentDescription = "전체 선택")
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "CBZTagger",
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "기본 압축: ${ZipCompressionOption.fromLevel(state.settings.zipCompressionLevel).label}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onPickFolder) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "폴더 열기")
                        }
                        IconButton(onClick = onPickFiles) {
                            Icon(Icons.Default.Add, contentDescription = "CBZ 파일 추가")
                        }
                        IconButton(onClick = onImportExternal) {
                            Icon(Icons.Default.LibraryAdd, contentDescription = "외부 파일 불러오기")
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "설정")
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (state.isSelectionMode) {
                NavigationBar {
                    NavigationBarItem(
                        selected = false,
                        onClick = { showBatchCompress = true },
                        icon = { Icon(Icons.Default.Compress, contentDescription = null) },
                        label = { Text("일괄 압축") },
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = { showBatchTags = true },
                        icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        label = { Text("일괄 태그") },
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = { showBatchParse = true },
                        icon = { Icon(Icons.Default.AutoFixHigh, contentDescription = null) },
                        label = { Text("파일명 파싱") },
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = { showBatchRename = true },
                        icon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) },
                        label = { Text("일괄 리네임") },
                    )
                }
            }
        },
    ) { padding ->
        if (state.items.isEmpty()) {
            EmptyLibraryView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onPickFolder = onPickFolder,
                onPickFiles = onPickFiles,
                onImportExternal = onImportExternal,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.items, key = { it.uri.toString() }) { item ->
                    CbzItemCard(
                        item = item,
                        thumbnail = state.thumbnails[item.uri],
                        selected = item.uri in state.selectedUris,
                        selectionMode = state.isSelectionMode,
                        onClick = {
                            if (state.isSelectionMode) {
                                onToggleSelect(item.uri)
                            } else {
                                onOpenEditor(item)
                            }
                        },
                        onLongClick = { onToggleSelect(item.uri) },
                    )
                }
            }
        }
    }

    if (showBatchCompress) {
        BatchCompressDialog(
            count = state.selectedItems.size,
            initialLevel = state.settings.lastBatchCompressLevel,
            onDismiss = { showBatchCompress = false },
            onConfirm = { level ->
                showBatchCompress = false
                onBatchRecompress(level)
            },
        )
    }

    if (showBatchTags) {
        BatchTagEditDialog(
            count = state.selectedItems.size,
            initialCompressionLevel = state.settings.zipCompressionLevel,
            initialSliderLevel = state.settings.lastCompressionSliderLevel,
            onDismiss = { showBatchTags = false },
            onConfirm = { patch, level ->
                showBatchTags = false
                onBatchApplyTags(patch, level)
            },
        )
    }

    if (showBatchParse) {
        BatchFilenameParseDialog(
            count = state.selectedItems.size,
            initialCompressionLevel = state.settings.zipCompressionLevel,
            initialSliderLevel = state.settings.lastCompressionSliderLevel,
            onDismiss = { showBatchParse = false },
            onConfirm = { overwrite, level ->
                showBatchParse = false
                onBatchParseFilenames(overwrite, level)
            },
        )
    }

    if (showBatchRename) {
        BatchRenameDialog(
            items = state.selectedItems,
            initialTemplate = state.settings.renameTemplate,
            initialPadding = state.settings.issuePadding,
            initialSmartCleanup = state.settings.smartCleanup,
            initialUseTitleForOneshot = state.settings.useTitleForOneshotRename,
            onDismiss = { showBatchRename = false },
            onConfirm = { tpl, pad, clean, oneshotTitle ->
                showBatchRename = false
                onBatchRename(tpl, pad, clean, oneshotTitle)
            },
        )
    }
}

@Composable
private fun EmptyLibraryView(
    modifier: Modifier,
    onPickFolder: () -> Unit,
    onPickFiles: () -> Unit,
    onImportExternal: () -> Unit,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.MenuBook,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "오프라인 CBZ 태그 & 압축 도구",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "폴더 또는 CBZ 파일을 열어 ComicInfo.xml 태그를 편집하고 ZIP 압축 단계(0~9)를 조절하세요.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onPickFolder, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.FolderOpen, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("CBZ 폴더 열기")
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onPickFiles, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("CBZ 파일 선택")
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onImportExternal, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.LibraryAdd, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("외부 폴더에서 CBZ 불러오기")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CbzItemCard(
    item: CbzDocumentItem,
    thumbnail: Bitmap?,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onClick() },
                )
            }
            Box(
                modifier = Modifier
                    .size(width = 56.dp, height = 78.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail.asImageBitmap(),
                        contentDescription = item.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val metaLine = buildString {
                    if (item.comicInfo.series.isNotBlank()) {
                        append(item.comicInfo.series)
                        if (item.comicInfo.number.isNotBlank()) append(" #${item.comicInfo.number}")
                        if (item.comicInfo.year != null) append(" (${item.comicInfo.year})")
                    } else {
                        append("메타데이터 없음")
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = metaLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SmallBadge(
                        text = if (item.hasComicInfo) "ComicInfo" else "태그 없음",
                        highlighted = item.hasComicInfo,
                    )
                    SmallBadge(text = "${item.pageCount}p", highlighted = false)
                    SmallBadge(text = MainViewModel.formatBytes(item.sizeBytes), highlighted = false)
                    SmallBadge(text = item.compressionSummary, highlighted = false)
                }
            }
        }
    }
}

@Composable
private fun SmallBadge(text: String, highlighted: Boolean) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = if (highlighted) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (highlighted) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun BatchCompressDialog(
    count: Int,
    initialLevel: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var selectedLevel by remember { mutableIntStateOf(initialLevel.coerceIn(0, 9)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("일괄 ZIP 압축 변환 ($count 개)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "선택한 CBZ 파일의 모든 이미지를 지정한 ZIP 압축 단계(0~9)로 다시 압축합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ZipCompressionSlider(
                    level = selectedLevel,
                    onLevelChange = { selectedLevel = it },
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedLevel) }) { Text("변환 시작") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}

@Composable
private fun BatchTagEditDialog(
    count: Int,
    initialCompressionLevel: Int,
    initialSliderLevel: Int,
    onDismiss: () -> Unit,
    onConfirm: (BatchTagPatch, Int) -> Unit,
) {
    var series by remember { mutableStateOf("") }
    var publisher by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("") }
    var writer by remember { mutableStateOf("") }
    var penciller by remember { mutableStateOf("") }
    var yearText by remember { mutableStateOf("") }
    var languageIso by remember { mutableStateOf("") }
    var manga by remember { mutableStateOf("") }
    var compressionLevel by remember { mutableIntStateOf(initialCompressionLevel) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("일괄 태그 편집 ($count 개)") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "입력한 항목만 일괄 덮어쓰기 됩니다. 비워둔 항목은 각 파일의 기존 태그를 유지합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(value = series, onValueChange = { series = it }, label = { Text("시리즈 (Series)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = publisher, onValueChange = { publisher = it }, label = { Text("출판사 (Publisher)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = genre, onValueChange = { genre = it }, label = { Text("장르 (Genre)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                AuthorCreditInput(
                    writer = writer,
                    penciller = penciller,
                    resetKey = "batch_tag_dialog",
                    onChange = { w, p ->
                        writer = w
                        penciller = p
                    },
                )
                OutlinedTextField(value = yearText, onValueChange = { yearText = it.filter(Char::isDigit) }, label = { Text("발행 연도 (Year)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = languageIso, onValueChange = { languageIso = it }, label = { Text("언어 코드 (예: ko, en, ja)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                MangaSelectorRow(
                    manga = manga,
                    onMangaChange = { manga = it },
                    emptyLabel = "변경 안 함",
                )
                HorizontalDivider()
                ZipCompressionControl(
                    level = compressionLevel,
                    initialSliderLevel = initialSliderLevel,
                    onLevelChange = { compressionLevel = it },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val patch = BatchTagPatch(
                        series = series.ifBlank { null },
                        publisher = publisher.ifBlank { null },
                        genre = genre.ifBlank { null },
                        writer = writer.ifBlank { null },
                        penciller = penciller.ifBlank { null },
                        year = yearText.toIntOrNull(),
                        languageIso = languageIso.ifBlank { null },
                        manga = manga.ifBlank { null },
                    )
                    onConfirm(patch, compressionLevel)
                },
            ) { Text("일괄 적용") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}

@Composable
private fun BatchFilenameParseDialog(
    count: Int,
    initialCompressionLevel: Int,
    initialSliderLevel: Int,
    onDismiss: () -> Unit,
    onConfirm: (Boolean, Int) -> Unit,
) {
    var overwriteExisting by remember { mutableStateOf(false) }
    var compressionLevel by remember { mutableIntStateOf(initialCompressionLevel) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("일괄 파일명 파싱 ($count 개)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "각 CBZ 파일명에서 시리즈, 권수/이슈, 볼륨, 연도를 자동 추출하여 ComicInfo.xml에 저장합니다.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("기존 태그 값 덮어쓰기", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = overwriteExisting, onCheckedChange = { overwriteExisting = it })
                }
                ZipCompressionControl(
                    level = compressionLevel,
                    initialSliderLevel = initialSliderLevel,
                    onLevelChange = { compressionLevel = it },
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(overwriteExisting, compressionLevel) }) { Text("파싱 및 저장") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}

@Composable
private fun BatchRenameDialog(
    items: List<CbzDocumentItem>,
    initialTemplate: String,
    initialPadding: Int,
    initialSmartCleanup: Boolean,
    initialUseTitleForOneshot: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, Int, Boolean, Boolean) -> Unit,
) {
    var template by remember { mutableStateOf(initialTemplate) }
    var paddingText by remember { mutableStateOf(initialPadding.toString()) }
    var smartCleanup by remember { mutableStateOf(initialSmartCleanup) }
    var useTitleForOneshot by remember { mutableStateOf(initialUseTitleForOneshot) }
    var previewExpanded by remember { mutableStateOf(false) }
    val paddingVal = paddingText.toIntOrNull()?.coerceIn(0, 6) ?: 3

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("일괄 파일명 변경 (${items.size}개)") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = template,
                    onValueChange = { template = it },
                    label = { Text("템플릿 ({series}, {issue}, {volume}, {year}, {title})") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FileRenamer.PRESET_TEMPLATES.forEach { (preset, _) ->
                        AssistChip(
                            onClick = { template = preset },
                            label = { Text(preset, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = paddingText,
                        onValueChange = { paddingText = it.filter(Char::isDigit).take(1) },
                        label = { Text("0-패딩 자릿수") },
                        singleLine = true,
                        modifier = Modifier.width(120.dp),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = smartCleanup, onCheckedChange = { smartCleanup = it })
                        Text("빈 괄호 정리", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = useTitleForOneshot,
                        onCheckedChange = { useTitleForOneshot = it },
                    )
                    Text(
                        text = "시리즈 없음(단권) 시 {title}을 파일명으로 사용",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { previewExpanded = !previewExpanded }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "미리보기 (최대 5개)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Icon(
                        imageVector = if (previewExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (previewExpanded) "미리보기 접기" else "미리보기 펼치기",
                    )
                }
                AnimatedVisibility(visible = previewExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items.take(5).forEach { item ->
                            val nextName = if (!FileRenamer.hasMetadataForTemplate(item.comicInfo, template, useTitleForOneshot)) {
                                "(태그 없음 - 건너뜀)"
                            } else {
                                FileRenamer.formatFilename(
                                    info = item.comicInfo,
                                    template = template,
                                    issuePadding = paddingVal,
                                    smartCleanup = smartCleanup,
                                    useTitleForOneshot = useTitleForOneshot,
                                )
                            }
                            Text(
                                text = "${item.displayName}\n  → $nextName",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(template, paddingVal, smartCleanup, useTitleForOneshot) }) { Text("이름 변경") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}
