package com.cbztagger.app.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bookmark
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cbztagger.app.R
import com.cbztagger.app.core.CbzArchive
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
import com.cbztagger.app.ui.components.FastScrollIndicator
import com.cbztagger.app.ui.components.LazyListFastScrollbar
import com.cbztagger.app.ui.components.MangaSelectorRow
import com.cbztagger.app.ui.components.localizeCompressionSummary
import com.cbztagger.app.ui.components.localizedLabel
import com.cbztagger.app.ui.dialogs.BatchBookmarkDialog
import com.cbztagger.app.ui.dialogs.PageRangeSelectDialog
import kotlin.math.roundToInt

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
    onSetSequentialPageRename: (Boolean) -> Unit,
    onNotifyPagesAlreadySequential: () -> Unit,
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
                            text = "${editor.item.pageCount}p · ${MainViewModel.formatBytes(editor.item.sizeBytes)} · ${localizeCompressionSummary(editor.item.compressionSummary)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = onParseFilename) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = stringResource(R.string.editor_cd_parse_filename))
                    }
                    IconButton(onClick = { showRenameDialog = true }) {
                        Icon(Icons.Default.DriveFileRenameOutline, contentDescription = stringResource(R.string.editor_cd_rename_file))
                    }
                    if (editor.item.hasComicInfo) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.editor_cd_delete_tags))
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
                            stringResource(R.string.editor_save_keep)
                        } else {
                            stringResource(R.string.editor_save_level, settings.zipCompressionLevel)
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
                Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text(stringResource(R.string.editor_tab_basic)) })
                Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text(stringResource(R.string.editor_tab_details)) })
                Tab(selected = tabIndex == 2, onClick = { tabIndex = 2 }, text = { Text(stringResource(R.string.editor_tab_pages, editor.pageNames.size)) })
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
                    displayPageNames = editor.displayedPageNames,
                    pendingSequentialPageRename = editor.pendingSequentialPageRename,
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
                    onSetSequentialPageRename = onSetSequentialPageRename,
                    onNotifyPagesAlreadySequential = onNotifyPagesAlreadySequential,
                    onPreviewClick = { index -> previewPageIndex = index },
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.editor_delete_confirm_title)) },
            text = { Text(stringResource(R.string.editor_delete_confirm_message)) },
            confirmButton = {
                Button(onClick = {
                    showDeleteConfirm = false
                    onDeleteTags()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
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
            title = { Text(stringResource(R.string.editor_rename_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.editor_rename_current, editor.item.displayName), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(stringResource(R.string.editor_rename_new_label)) },
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
                ) { Text(stringResource(R.string.action_change)) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text(stringResource(R.string.action_cancel)) }
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
            displayPageNames = editor.displayedPageNames,
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
                        contentDescription = stringResource(R.string.editor_cd_cover),
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
                    label = { Text(stringResource(R.string.editor_field_series)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = onParseFilename, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.editor_btn_auto_fill))
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = info.number,
                onValueChange = { v -> onDraftChange { it.copy(number = v) } },
                label = { Text(stringResource(R.string.editor_field_number)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = info.count?.toString().orEmpty(),
                onValueChange = { v -> onDraftChange { it.copy(count = v.filter(Char::isDigit).toIntOrNull()) } },
                label = { Text(stringResource(R.string.editor_field_count)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = info.volume?.toString().orEmpty(),
                onValueChange = { v -> onDraftChange { it.copy(volume = v.filter(Char::isDigit).toIntOrNull()) } },
                label = { Text(stringResource(R.string.editor_field_volume)) },
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
                label = { Text(stringResource(R.string.editor_field_title)) },
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
                Text(stringResource(R.string.editor_btn_series_number), style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = info.publisher,
                onValueChange = { v -> onDraftChange { it.copy(publisher = v) } },
                label = { Text(stringResource(R.string.editor_field_publisher)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = info.imprint,
                onValueChange = { v -> onDraftChange { it.copy(imprint = v) } },
                label = { Text(stringResource(R.string.editor_field_imprint)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = info.year?.toString().orEmpty(),
                onValueChange = { v -> onDraftChange { it.copy(year = v.filter(Char::isDigit).take(4).toIntOrNull()) } },
                label = { Text(stringResource(R.string.editor_field_year)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = info.month?.toString().orEmpty(),
                onValueChange = { v -> onDraftChange { it.copy(month = v.filter(Char::isDigit).take(2).toIntOrNull()) } },
                label = { Text(stringResource(R.string.editor_field_month)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = info.day?.toString().orEmpty(),
                onValueChange = { v -> onDraftChange { it.copy(day = v.filter(Char::isDigit).take(2).toIntOrNull()) } },
                label = { Text(stringResource(R.string.editor_field_day)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = info.genre,
                onValueChange = { v -> onDraftChange { it.copy(genre = v) } },
                label = { Text(stringResource(R.string.editor_field_genre)) },
                singleLine = true,
                modifier = Modifier.weight(2f),
            )
            OutlinedTextField(
                value = info.languageIso,
                onValueChange = { v -> onDraftChange { it.copy(languageIso = v) } },
                label = { Text(stringResource(R.string.editor_field_language)) },
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
            Text(stringResource(R.string.editor_field_bw), style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = info.blackAndWhite == true,
                onCheckedChange = { checked -> onDraftChange { it.copy(blackAndWhite = if (checked) true else null) } },
            )
        }

        OutlinedTextField(
            value = info.summary,
            onValueChange = { v -> onDraftChange { it.copy(summary = v) } },
            label = { Text(stringResource(R.string.editor_field_summary)) },
            minLines = 3,
            maxLines = 6,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = info.notes,
            onValueChange = { v -> onDraftChange { it.copy(notes = v) } },
            label = { Text(stringResource(R.string.editor_field_notes)) },
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
        Text(stringResource(R.string.editor_section_credits), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        AuthorCreditInput(
            writer = info.writer,
            penciller = info.penciller,
            resetKey = itemUri,
            onChange = { w, p -> onDraftChange { it.copy(writer = w, penciller = p) } },
        )
        OutlinedTextField(value = info.inker, onValueChange = { v -> onDraftChange { it.copy(inker = v) } }, label = { Text(stringResource(R.string.editor_field_inker)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = info.colorist, onValueChange = { v -> onDraftChange { it.copy(colorist = v) } }, label = { Text(stringResource(R.string.editor_field_colorist)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = info.letterer, onValueChange = { v -> onDraftChange { it.copy(letterer = v) } }, label = { Text(stringResource(R.string.editor_field_letterer)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = info.coverArtist, onValueChange = { v -> onDraftChange { it.copy(coverArtist = v) } }, label = { Text(stringResource(R.string.editor_field_cover_artist)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = info.editor, onValueChange = { v -> onDraftChange { it.copy(editor = v) } }, label = { Text(stringResource(R.string.editor_field_editor)) }, singleLine = true, modifier = Modifier.fillMaxWidth())

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Text(stringResource(R.string.editor_section_extra), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        OutlinedTextField(value = info.storyArc, onValueChange = { v -> onDraftChange { it.copy(storyArc = v) } }, label = { Text(stringResource(R.string.editor_field_story_arc)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = info.seriesGroup, onValueChange = { v -> onDraftChange { it.copy(seriesGroup = v) } }, label = { Text(stringResource(R.string.editor_field_series_group)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = info.characters, onValueChange = { v -> onDraftChange { it.copy(characters = v) } }, label = { Text(stringResource(R.string.editor_field_characters)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = info.teams, onValueChange = { v -> onDraftChange { it.copy(teams = v) } }, label = { Text(stringResource(R.string.editor_field_teams)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = info.locations, onValueChange = { v -> onDraftChange { it.copy(locations = v) } }, label = { Text(stringResource(R.string.editor_field_locations)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(value = info.ageRating, onValueChange = { v -> onDraftChange { it.copy(ageRating = v) } }, label = { Text(stringResource(R.string.editor_field_age_rating)) }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(value = info.format, onValueChange = { v -> onDraftChange { it.copy(format = v) } }, label = { Text(stringResource(R.string.editor_field_format)) }, singleLine = true, modifier = Modifier.weight(1f))
        }
        OutlinedTextField(value = info.scanInformation, onValueChange = { v -> onDraftChange { it.copy(scanInformation = v) } }, label = { Text(stringResource(R.string.editor_field_scan_info)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = info.web, onValueChange = { v -> onDraftChange { it.copy(web = v) } }, label = { Text(stringResource(R.string.editor_field_web)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PagesTab(
    itemUri: Uri,
    pageNames: List<String>,
    displayPageNames: List<String>,
    pendingSequentialPageRename: Boolean,
    pages: List<PageInfo>,
    showThumbnails: Boolean,
    onLoadThumbnail: suspend (Uri, String) -> Bitmap?,
    onUpdatePage: (Int, PageInfo) -> Unit,
    onReplaceAllPages: (List<PageInfo>) -> Unit,
    onSetSequentialPageRename: (Boolean) -> Unit,
    onNotifyPagesAlreadySequential: () -> Unit,
    onPreviewClick: (Int) -> Unit,
) {
    if (pageNames.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.pages_empty))
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
    var showSequentialRenameDialog by remember { mutableStateOf(false) }
    var bookmarkDialogPageIndex by remember { mutableStateOf<Int?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Sticky top action bar for batch page attribute editing, bulk bookmarks & sequential filename renumbering
        Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            AnimatedContent(
                targetState = batchMode,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(220)) + expandVertically(animationSpec = tween(220), expandFrom = Alignment.Top))
                        .togetherWith(
                            fadeOut(animationSpec = tween(160)) + shrinkVertically(animationSpec = tween(200), shrinkTowards = Alignment.Top),
                        )
                        .using(SizeTransform(clip = true))
                },
                label = "pages_batch_mode_bar",
            ) { inBatchMode ->
                if (!inBatchMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilledTonalButton(
                            onClick = {
                                batchMode = true
                                selectedIndices = emptySet()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Checklist, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.pages_btn_batch_edit), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                                text = stringResource(R.string.pages_batch_header, selectedIndices.size, pageCount),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            TextButton(
                                onClick = {
                                    batchMode = false
                                    selectedIndices = emptySet()
                                },
                            ) {
                                Text(stringResource(R.string.action_done))
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
                                label = {
                                    Text(
                                        stringResource(
                                            if (selectedIndices.size == pageCount) R.string.action_deselect_all else R.string.action_select_all,
                                        ),
                                    )
                                },
                            )
                            AssistChip(
                                onClick = { showRangeDialog = true },
                                label = { Text(stringResource(R.string.pages_chip_range)) },
                            )
                            AssistChip(
                                onClick = {
                                    val (updated, _) = PageBatchHelper.fillEmptyTypesWithStory(normalizedPages, pageCount)
                                    onReplaceAllPages(updated)
                                },
                                enabled = emptyTypeCount > 0,
                                label = { Text(stringResource(R.string.pages_chip_fill_story, emptyTypeCount)) },
                            )
                            AssistChip(
                                onClick = { showBatchBookmarkDialog = true },
                                label = { Text(stringResource(R.string.pages_btn_bookmarks)) },
                            )
                            AssistChip(
                                onClick = {
                                    if (pendingSequentialPageRename) {
                                        onSetSequentialPageRename(false)
                                    } else if (!CbzArchive.needsSequentialPageRename(pageNames)) {
                                        onNotifyPagesAlreadySequential()
                                    } else {
                                        showSequentialRenameDialog = true
                                    }
                                },
                                label = {
                                    Text(
                                        stringResource(
                                            if (pendingSequentialPageRename) {
                                                R.string.pages_chip_renumber_files_pending
                                            } else {
                                                R.string.pages_chip_renumber_files
                                            },
                                        ),
                                    )
                                },
                            )
                        }

                        Box(modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { showBatchTypeMenu = true },
                                enabled = selectedIndices.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.pages_btn_apply_type, selectedIndices.size))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                            DropdownMenu(
                                expanded = showBatchTypeMenu,
                                onDismissRequest = { showBatchTypeMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.pages_type_clear)) },
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
                                        text = { Text(pt.localizedLabel()) },
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
                                    text = { Text(stringResource(R.string.pages_double_set_batch)) },
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
                                    text = { Text(stringResource(R.string.pages_double_clear_batch)) },
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
        }
        HorizontalDivider()

        val listState = rememberLazyListState()
        var fastScrollIndicator by remember { mutableStateOf<FastScrollIndicator?>(null) }
        var listContainerHeightPx by remember { mutableIntStateOf(0) }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { listContainerHeightPx = it.height },
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
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
                    AnimatedVisibility(
                        visible = batchMode,
                        enter = expandHorizontally(animationSpec = tween(200), expandFrom = Alignment.Start) + fadeIn(tween(180)),
                        exit = shrinkHorizontally(animationSpec = tween(180), shrinkTowards = Alignment.Start) + fadeOut(tween(140)),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    selectedIndices = if (checked) selectedIndices + idx else selectedIndices - idx
                                },
                            )
                            if (showThumbnails) {
                                Spacer(Modifier.width(4.dp))
                            }
                        }
                    }
                    if (showThumbnails) {
                        PageListThumbnail(
                            uri = itemUri,
                            entryName = entryName,
                            onLoad = onLoadThumbnail,
                        )
                    } else if (!batchMode) {
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
                                text = "· ${(displayPageNames.getOrNull(idx) ?: entryName).substringAfterLast('/')}",
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (hasBookmark) {
                            val doublePageLabel = stringResource(R.string.pages_double_label)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bookmark,
                                    contentDescription = stringResource(R.string.pages_cd_bookmark),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = buildString {
                                        append(": ${pageInfo.bookmark}")
                                        if (pageInfo.doublePage == true) append(" · $doublePageLabel")
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        } else {
                            val subtitle = when {
                                pageInfo.doublePage == true -> stringResource(R.string.pages_double_label)
                                batchMode -> stringResource(R.string.pages_subtitle_tap_select)
                                else -> stringResource(R.string.pages_subtitle_hint)
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
                    AnimatedContent(
                        targetState = batchMode,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
                        label = "page_row_trailing",
                    ) { inBatchMode ->
                        val normalLabel = stringResource(R.string.pages_type_normal)
                        if (inBatchMode) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (pageInfo.type.isNotBlank()) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                            ) {
                                Text(
                                    text = pageInfo.type.ifBlank { normalLabel },
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        } else {
                            Box {
                                TextButton(onClick = { typeExpanded = true }) {
                                    Text(pageInfo.type.ifBlank { normalLabel })
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                                DropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.pages_type_none)) },
                                        onClick = {
                                            typeExpanded = false
                                            onUpdatePage(idx, pageInfo.copy(type = ""))
                                        },
                                    )
                                    PageType.entries.forEach { pt ->
                                        DropdownMenuItem(
                                            text = { Text(pt.localizedLabel()) },
                                            onClick = {
                                                typeExpanded = false
                                                onUpdatePage(idx, pageInfo.copy(type = pt.xmlValue))
                                            },
                                        )
                                    }
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(
                                                    if (pageInfo.doublePage == true) R.string.pages_double_clear else R.string.pages_double_set,
                                                ),
                                            )
                                        },
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
                }
                HorizontalDivider()
            }
                }
                LazyListFastScrollbar(
                    listState = listState,
                    totalItems = pageCount,
                    onIndicatorChange = { fastScrollIndicator = it },
                    modifier = Modifier.fillMaxHeight(),
                )
            }

            val indicator = fastScrollIndicator
            if (indicator != null) {
                val density = LocalDensity.current
                val pillHalfHeightPx = with(density) { 16.dp.toPx() }
                val topMarginPx = with(density) { 8.dp.toPx() }
                val maxY = (listContainerHeightPx - pillHalfHeightPx * 2f - topMarginPx)
                    .coerceAtLeast(topMarginPx)
                val offsetY = (indicator.centerYPx - pillHalfHeightPx).coerceIn(topMarginPx, maxY)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 32.dp)
                        .offset { IntOffset(0, offsetY.roundToInt()) },
                ) {
                    Text(
                        text = "#${indicator.pageNumber} / ${indicator.totalPages}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
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

    if (showSequentialRenameDialog) {
        val plannedPairs = remember(pageNames) { CbzArchive.planSequentialPageRenames(pageNames) }
        AlertDialog(
            onDismissRequest = { showSequentialRenameDialog = false },
            title = { Text(stringResource(R.string.pages_renumber_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.pages_renumber_dialog_desc, pageCount),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            val previewRows = if (plannedPairs.size <= 6) {
                                plannedPairs
                            } else {
                                plannedPairs.take(5)
                            }
                            previewRows.forEach { (oldName, newName) ->
                                Text(
                                    text = "${oldName.substringAfterLast('/')} → $newName",
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (plannedPairs.size > 6) {
                                Text(
                                    text = stringResource(R.string.pages_renumber_dialog_more, plannedPairs.size - 6),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                val (lastOld, lastNew) = plannedPairs.last()
                                Text(
                                    text = "${lastOld.substringAfterLast('/')} → $lastNew",
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSequentialRenameDialog = false
                        onSetSequentialPageRename(true)
                    },
                ) {
                    Text(stringResource(R.string.action_apply))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSequentialRenameDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
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
        title = { Text(stringResource(R.string.pages_bookmark_dialog_title, pageNumber)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = bookmarkName,
                    onValueChange = { bookmarkName = it },
                    label = { Text(stringResource(R.string.pages_bookmark_name_label)) },
                    placeholder = { Text(stringResource(R.string.pages_bookmark_name_placeholder)) },
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
                        Text(stringResource(R.string.pages_bookmark_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(bookmarkName) },
                enabled = bookmarkName.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
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
    displayPageNames: List<String>,
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
    val displayEntryName = displayPageNames.getOrNull(currentIndex) ?: entryName
    val currentPageInfo = pages.firstOrNull { it.image == currentIndex }
    val doubleShortLabel = stringResource(R.string.pages_double_short)
    val bookmarkText = currentPageInfo?.bookmark?.takeIf { it.isNotBlank() }?.let {
        stringResource(R.string.preview_bookmark_prefix, it)
    }
    val pageMetaText = buildList {
        val typeVal = currentPageInfo?.type.orEmpty()
        if (typeVal.isNotBlank()) add(typeVal)
        if (currentPageInfo?.doublePage == true) add(doubleShortLabel)
        if (!bookmarkText.isNullOrBlank()) add(bookmarkText)
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
                                text = "#${currentIndex + 1} / ${pageNames.size} · ${displayEntryName.substringAfterLast('/')}",
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
                            text = stringResource(
                                if (isRtl) R.string.preview_direction_rtl else R.string.preview_direction_ltr,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
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
                            contentDescription = stringResource(if (isRtl) R.string.preview_cd_next else R.string.preview_cd_prev),
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
                            loadFailed -> Text(stringResource(R.string.preview_load_failed))
                        }
                    }

                    IconButton(
                        onClick = { rightTarget?.let { currentIndex = it } },
                        enabled = rightTarget != null,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = stringResource(if (isRtl) R.string.preview_cd_prev else R.string.preview_cd_next),
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
            }
        }
    }
}
