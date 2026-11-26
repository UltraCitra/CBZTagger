package com.cbztagger.app.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cbztagger.app.core.ExternalFolderImporter
import com.cbztagger.app.data.ExternalFolderCandidate
import com.cbztagger.app.ui.ExternalImportUiState
import com.cbztagger.app.ui.MainViewModel

@Composable
fun ExternalImportDialog(
    importState: ExternalImportUiState,
    initialDeleteSource: Boolean = false,
    onChangeSourceFolder: () -> Unit,
    onChangeDestFolder: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (selected: List<ExternalFolderCandidate>, deleteSourceAfterImport: Boolean) -> Unit,
) {
    val defaultSelected = remember(importState.candidates, importState.alreadyImportedNames) {
        importState.candidates
            .filterNot { it.folderName in importState.alreadyImportedNames }
            .map { it.folderName }
            .toSet()
    }
    var selectedFolderNames by remember(importState.sourceTreeUri, importState.candidates) {
        mutableStateOf(defaultSelected)
    }
    var deleteSourceAfterImport by remember(initialDeleteSource) { mutableStateOf(initialDeleteSource) }

    val selectedCandidates = importState.candidates.filter { it.folderName in selectedFolderNames }
    val canDeleteAnySource = selectedCandidates.any { !it.isRootFolderItself }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("외부 파일 불러오기") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "외부 경로 (원본)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = importState.sourceFolderName,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            TextButton(onClick = onChangeSourceFolder) {
                                Text("변경")
                            }
                        }

                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "CBZ 저장 폴더",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = if (importState.isDestSameAsSource) {
                                        "${importState.destFolderName} (외부 경로와 동일)"
                                    } else {
                                        importState.destFolderName
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            TextButton(onClick = onChangeDestFolder) {
                                Text("변경")
                            }
                        }

                        Text(
                            text = "무압축 보관(Store) · 원본 이미지 파일명 유지",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
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
                        onClick = { selectedFolderNames = importState.candidates.map { it.folderName }.toSet() },
                        label = { Text("전체 (${importState.candidates.size})") },
                    )
                    val newOnlyCount = importState.candidates.count { it.folderName !in importState.alreadyImportedNames }
                    AssistChip(
                        onClick = {
                            selectedFolderNames = importState.candidates
                                .filterNot { it.folderName in importState.alreadyImportedNames }
                                .map { it.folderName }
                                .toSet()
                        },
                        label = { Text("신규만 ($newOnlyCount)") },
                    )
                    AssistChip(
                        onClick = { selectedFolderNames = emptySet() },
                        label = { Text("해제") },
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    importState.candidates.forEach { candidate ->
                        val checked = candidate.folderName in selectedFolderNames
                        val isImported = candidate.folderName in importState.alreadyImportedNames
                        val cbzName = "${ExternalFolderImporter.cleanFilename(candidate.folderName)}.cbz"

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedFolderNames = if (checked) {
                                        selectedFolderNames - candidate.folderName
                                    } else {
                                        selectedFolderNames + candidate.folderName
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { next ->
                                    selectedFolderNames = if (next) {
                                        selectedFolderNames + candidate.folderName
                                    } else {
                                        selectedFolderNames - candidate.folderName
                                    }
                                },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = candidate.folderName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "→ $cbzName · 이미지 ${candidate.imageCount}장 · ${MainViewModel.formatBytes(candidate.totalSizeBytes)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (isImported || candidate.hasExistingXml) {
                                    Spacer(Modifier.height(2.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        if (isImported) {
                                            ImportTagBadge("이미 불러옴", highlighted = false)
                                        }
                                        if (candidate.hasExistingXml) {
                                            ImportTagBadge("기존 XML 있음", highlighted = true)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (canDeleteAnySource) {
                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { deleteSourceAfterImport = !deleteSourceAfterImport },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = deleteSourceAfterImport,
                            onCheckedChange = { deleteSourceAfterImport = it },
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "불러오기 완료 후 원본 폴더 삭제",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedCandidates, deleteSourceAfterImport && canDeleteAnySource) },
                enabled = selectedCandidates.isNotEmpty(),
            ) {
                Text("불러오기 (${selectedCandidates.size}개)")
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
private fun ImportTagBadge(text: String, highlighted: Boolean) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = if (highlighted) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (highlighted) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
        )
    }
}
