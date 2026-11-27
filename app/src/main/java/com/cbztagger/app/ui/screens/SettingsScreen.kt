package com.cbztagger.app.ui.screens

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cbztagger.app.R
import com.cbztagger.app.core.FileRenamer
import com.cbztagger.app.data.AppLanguage
import com.cbztagger.app.data.AppSettings
import com.cbztagger.app.ui.components.ZipCompressionControl
import com.cbztagger.app.ui.components.localizedRenamePresetLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onUpdateSettings: (AppSettings) -> Unit,
    onChangeLanguage: (String) -> Unit,
    onPickExternalFolder: () -> Unit,
    onClearExternalFolder: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val appVersionName = remember(context) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0),
                ).versionName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }
        }.getOrNull().orEmpty()
    }
    var showLicenseDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = stringResource(R.string.settings_zip_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.settings_zip_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    ZipCompressionControl(
                        level = settings.zipCompressionLevel,
                        initialSliderLevel = settings.lastCompressionSliderLevel,
                        onLevelChange = { newLevel ->
                            onUpdateSettings(
                                settings.copy(
                                    zipCompressionLevel = newLevel,
                                    lastCompressionSliderLevel = if (newLevel in 0..9) {
                                        newLevel
                                    } else {
                                        settings.lastCompressionSliderLevel
                                    },
                                ),
                            )
                        },
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_external_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.settings_external_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(
                            R.string.settings_external_current,
                            settings.externalImportFolderName ?: stringResource(R.string.settings_external_unset),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(onClick = onPickExternalFolder) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(
                                    if (settings.externalImportFolderUri.isNullOrBlank()) {
                                        R.string.settings_external_pick
                                    } else {
                                        R.string.settings_external_change
                                    },
                                ),
                            )
                        }
                        if (!settings.externalImportFolderUri.isNullOrBlank()) {
                            TextButton(onClick = onClearExternalFolder) {
                                Text(stringResource(R.string.action_reset))
                            }
                        }
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onUpdateSettings(
                                    settings.copy(
                                        deleteExternalSourceAfterImport = !settings.deleteExternalSourceAfterImport,
                                    ),
                                )
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = settings.deleteExternalSourceAfterImport,
                            onCheckedChange = { checked ->
                                onUpdateSettings(settings.copy(deleteExternalSourceAfterImport = checked))
                            },
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_external_delete_title),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = stringResource(R.string.settings_external_delete_desc),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_pages_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = stringResource(R.string.settings_thumbnails_title),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = stringResource(R.string.settings_thumbnails_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = settings.showPageListThumbnails,
                            onCheckedChange = { checked ->
                                onUpdateSettings(settings.copy(showPageListThumbnails = checked))
                            },
                        )
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = stringResource(R.string.settings_manga_dir_title),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = stringResource(R.string.settings_manga_dir_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = settings.previewFollowMangaDirection,
                            onCheckedChange = { checked ->
                                onUpdateSettings(settings.copy(previewFollowMangaDirection = checked))
                            },
                        )
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_rename_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    OutlinedTextField(
                        value = settings.renameTemplate,
                        onValueChange = { onUpdateSettings(settings.copy(renameTemplate = it)) },
                        label = { Text(stringResource(R.string.settings_rename_template_label)) },
                        supportingText = { Text(stringResource(R.string.settings_rename_template_help)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FileRenamer.PRESET_TEMPLATES.forEach { (tpl, desc) ->
                            AssistChip(
                                onClick = { onUpdateSettings(settings.copy(renameTemplate = tpl)) },
                                label = { Text(localizedRenamePresetLabel(tpl, desc)) },
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedTextField(
                            value = settings.issuePadding.toString(),
                            onValueChange = { raw ->
                                val parsed = raw.filter(Char::isDigit).take(1).toIntOrNull() ?: 0
                                onUpdateSettings(settings.copy(issuePadding = parsed.coerceIn(0, 6)))
                            },
                            label = { Text(stringResource(R.string.settings_rename_padding_label)) },
                            singleLine = true,
                            modifier = Modifier.width(120.dp),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = settings.smartCleanup,
                                onCheckedChange = { onUpdateSettings(settings.copy(smartCleanup = it)) },
                            )
                            Text(
                                text = stringResource(R.string.settings_rename_cleanup),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onUpdateSettings(
                                    settings.copy(useTitleForOneshotRename = !settings.useTitleForOneshotRename),
                                )
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = settings.useTitleForOneshotRename,
                            onCheckedChange = { onUpdateSettings(settings.copy(useTitleForOneshotRename = it)) },
                        )
                        Text(
                            text = stringResource(R.string.batch_rename_oneshot_title),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_language_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.settings_language_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val languageOptions = listOf(
                        AppLanguage.SYSTEM to stringResource(R.string.settings_language_system),
                        AppLanguage.KOREAN to "한국어",
                        AppLanguage.ENGLISH to "English",
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        languageOptions.forEachIndexed { index, (code, label) ->
                            SegmentedButton(
                                selected = settings.appLanguage == code,
                                onClick = {
                                    if (settings.appLanguage != code) {
                                        onChangeLanguage(code)
                                    }
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = languageOptions.size),
                                modifier = Modifier.height(40.dp),
                            ) {
                                Text(
                                    text = label,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.settings_about_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (appVersionName.isNotBlank()) {
                                Text(
                                    text = "v$appVersionName",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { showLicenseDialog = true },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Gavel,
                                        contentDescription = stringResource(R.string.settings_license_cd),
                                        modifier = Modifier.size(12.dp),
                                    )
                                    Text(
                                        text = "MIT",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.settings_about_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showLicenseDialog) {
        AlertDialog(
            onDismissRequest = { showLicenseDialog = false },
            title = { Text("MIT License") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = MIT_LICENSE_TEXT,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicenseDialog = false }) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }
}

private val MIT_LICENSE_TEXT = """
MIT License

Copyright (c) 2026 UltraCitra

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
""".trimIndent()
