package com.cbztagger.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cbztagger.app.data.AppLocaleHelper
import com.cbztagger.app.ui.ActiveScreen
import com.cbztagger.app.ui.MainViewModel
import com.cbztagger.app.ui.ProgressState
import com.cbztagger.app.ui.dialogs.ExternalImportDialog
import com.cbztagger.app.ui.screens.EditorScreen
import com.cbztagger.app.ui.screens.LibraryScreen
import com.cbztagger.app.ui.screens.SettingsScreen
import com.cbztagger.app.ui.theme.ComicTagTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocaleHelper.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIncomingIntent(intent)

        setContent {
            ComicTagTheme {
                ComicTagApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { uri ->
                viewModel.openSingleFromIntent(uri)
            }
        }
    }
}

@Composable
private fun ComicTagApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val activity = context as? Activity
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showExitConfirm by remember { mutableStateOf(false) }
    var showUnsavedExitConfirm by remember { mutableStateOf(false) }

    val handleEditorBack: () -> Unit = {
        val ed = state.editor
        if (ed != null && ed.hasUnsavedChanges) {
            showUnsavedExitConfirm = true
        } else {
            viewModel.navigateTo(ActiveScreen.LIBRARY)
        }
    }

    BackHandler(enabled = !showExitConfirm && !showUnsavedExitConfirm) {
        when {
            state.progress != null -> {
                // Ignore back press while file I/O is in progress
            }
            state.screen == ActiveScreen.EDITOR -> {
                handleEditorBack()
            }
            state.screen != ActiveScreen.LIBRARY -> {
                viewModel.navigateTo(ActiveScreen.LIBRARY)
            }
            state.isSelectionMode -> {
                viewModel.clearSelection()
            }
            else -> {
                showExitConfirm = true
            }
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri ->
        if (treeUri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(treeUri, flags) }
            viewModel.openFolder(treeUri)
        }
    }

    val filesPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            uris.forEach { uri ->
                runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            }
            viewModel.addIndividualFiles(uris)
        }
    }

    var openImportDialogAfterPick by remember { mutableStateOf(false) }

    val externalFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri ->
        val shouldOpenDialog = openImportDialogAfterPick
        openImportDialogAfterPick = false
        if (treeUri != null) {
            val rwFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            val tookRw = runCatching {
                context.contentResolver.takePersistableUriPermission(treeUri, rwFlags)
            }.isSuccess
            if (!tookRw) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        treeUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
            }
            viewModel.setExternalImportFolder(treeUri, openImportDialogAfter = shouldOpenDialog)
        }
    }

    val importDestFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri ->
        if (treeUri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(treeUri, flags) }
            viewModel.setExternalImportDestination(treeUri)
        }
    }

    LaunchedEffect(state.statusMessage) {
        val msg = state.statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        AnimatedContent(
            targetState = state.screen,
            transitionSpec = {
                val isForward = initialState == ActiveScreen.LIBRARY && targetState != ActiveScreen.LIBRARY
                val animSpec = tween<androidx.compose.ui.unit.IntOffset>(
                    durationMillis = 250,
                    easing = FastOutSlowInEasing,
                )
                if (isForward) {
                    ContentTransform(
                        targetContentEnter = slideInHorizontally(
                            animationSpec = animSpec,
                            initialOffsetX = { fullWidth -> fullWidth },
                        ),
                        initialContentExit = slideOutHorizontally(
                            animationSpec = animSpec,
                            targetOffsetX = { fullWidth -> -fullWidth / 4 },
                        ),
                        targetContentZIndex = 1f,
                    )
                } else {
                    ContentTransform(
                        targetContentEnter = slideInHorizontally(
                            animationSpec = animSpec,
                            initialOffsetX = { fullWidth -> -fullWidth / 4 },
                        ),
                        initialContentExit = slideOutHorizontally(
                            animationSpec = animSpec,
                            targetOffsetX = { fullWidth -> fullWidth },
                        ),
                        targetContentZIndex = 0f,
                    )
                }
            },
            label = "screen_transition",
        ) { targetScreen ->
            when (targetScreen) {
                ActiveScreen.LIBRARY -> LibraryScreen(
                    state = state,
                    onPickFolder = { folderPicker.launch(null) },
                    onPickFiles = {
                        filesPicker.launch(
                            arrayOf(
                                "application/vnd.comicbook+zip",
                                "application/x-cbz",
                                "application/zip",
                                "application/octet-stream",
                            ),
                        )
                    },
                    onImportExternal = {
                        if (state.settings.externalImportFolderUri.isNullOrBlank()) {
                            openImportDialogAfterPick = true
                            externalFolderPicker.launch(null)
                        } else {
                            viewModel.openExternalImportDialog()
                        }
                    },
                    onOpenSettings = { viewModel.navigateTo(ActiveScreen.SETTINGS) },
                    onOpenEditor = { viewModel.openEditor(it) },
                    onToggleSelect = { viewModel.toggleSelection(it) },
                    onSelectAll = { viewModel.selectAll() },
                    onClearSelection = { viewModel.clearSelection() },
                    onBatchRecompress = { viewModel.batchRecompress(it) },
                    onBatchApplyTags = { patch, level -> viewModel.batchApplyTags(patch, level) },
                    onBatchParseFilenames = { overwrite, level -> viewModel.batchParseFilenamesAndSave(overwrite, level) },
                    onBatchRename = { tpl, pad, clean, oneshotTitle -> viewModel.batchRename(tpl, pad, clean, oneshotTitle) },
                )

                ActiveScreen.EDITOR -> {
                    val ed = state.editor
                    if (ed != null) {
                        EditorScreen(
                            editor = ed,
                            settings = state.settings,
                            coverThumbnail = state.thumbnails[ed.item.uri],
                            onBack = handleEditorBack,
                            onDraftChange = { viewModel.updateEditorDraft(it) },
                            onCompressionLevelChange = { viewModel.setEditorCompressionLevel(it) },
                            onParseFilename = { viewModel.parseCurrentFilenameIntoEditor(overwriteExisting = true) },
                            onSave = { viewModel.saveEditorComicInfo() },
                            onDeleteTags = { viewModel.deleteEditorComicInfo() },
                            onRenameFile = { viewModel.renameEditorFile(it) },
                            onLoadPagePreview = { uri, entry -> viewModel.loadPagePreview(uri, entry) },
                            onLoadPageThumbnail = { uri, entry -> viewModel.loadPageListThumbnail(uri, entry) },
                        )
                    } else {
                        LaunchedEffect(Unit) {
                            viewModel.navigateTo(ActiveScreen.LIBRARY)
                        }
                        Box(modifier = Modifier.fillMaxSize())
                    }
                }

                ActiveScreen.SETTINGS -> SettingsScreen(
                    settings = state.settings,
                    onUpdateSettings = { viewModel.updateSettings(it) },
                    onChangeLanguage = { languageCode ->
                        viewModel.updateAppLanguage(languageCode)
                        AppLocaleHelper.applyLanguage(activity, context.applicationContext, languageCode)
                    },
                    onPickExternalFolder = {
                        openImportDialogAfterPick = false
                        externalFolderPicker.launch(null)
                    },
                    onClearExternalFolder = { viewModel.clearExternalImportFolder() },
                    onBack = { viewModel.navigateTo(ActiveScreen.LIBRARY) },
                )
            }
        }

        val externalImport = state.externalImport
        if (externalImport != null) {
            ExternalImportDialog(
                importState = externalImport,
                initialDeleteSource = state.settings.deleteExternalSourceAfterImport,
                onChangeSourceFolder = {
                    openImportDialogAfterPick = true
                    externalFolderPicker.launch(null)
                },
                onChangeDestFolder = {
                    importDestFolderPicker.launch(null)
                },
                onDismiss = { viewModel.dismissExternalImportDialog() },
                onConfirm = { selected, deleteSource ->
                    viewModel.runExternalImport(selected, deleteSource)
                },
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 80.dp, start = 12.dp, end = 12.dp),
        )

        val progress = state.progress
        if (progress != null) {
            ProgressOverlayDialog(progress)
        }

        if (showUnsavedExitConfirm) {
            AlertDialog(
                onDismissRequest = { showUnsavedExitConfirm = false },
                title = { Text(stringResource(R.string.unsaved_changes_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.unsaved_changes_message))
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = {
                                showUnsavedExitConfirm = false
                                viewModel.saveEditorComicInfo(navigateBackAfterSave = true)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.unsaved_save_and_leave))
                        }
                        OutlinedButton(
                            onClick = {
                                showUnsavedExitConfirm = false
                                viewModel.navigateTo(ActiveScreen.LIBRARY)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = stringResource(R.string.unsaved_discard_and_leave),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        TextButton(
                            onClick = { showUnsavedExitConfirm = false },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.unsaved_keep_editing))
                        }
                    }
                },
                confirmButton = {},
            )
        }

        if (showExitConfirm) {
            val appName = stringResource(R.string.app_name)
            AlertDialog(
                onDismissRequest = { showExitConfirm = false },
                title = { Text(stringResource(R.string.exit_app_title)) },
                text = { Text(stringResource(R.string.exit_app_message, appName)) },
                confirmButton = {
                    Button(
                        onClick = {
                            showExitConfirm = false
                            activity?.finish()
                        },
                    ) {
                        Text(stringResource(R.string.action_exit))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExitConfirm = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun ProgressOverlayDialog(progress: ProgressState) {
    AlertDialog(
        onDismissRequest = { /* non-cancelable during file write */ },
        title = { Text(progress.title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (progress.total > 0) {
                    LinearProgressIndicator(
                        progress = { (progress.current.toFloat() / progress.total.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    CircularProgressIndicator()
                }
                if (progress.detail.isNotBlank()) {
                    Text(
                        text = progress.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {},
    )
}
