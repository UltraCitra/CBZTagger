package com.cbztagger.app.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cbztagger.app.core.FileRenamer
import com.cbztagger.app.core.ZipCompressionOption
import com.cbztagger.app.data.AppSettings
import com.cbztagger.app.ui.components.ZipCompressionControl

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onUpdateSettings: (AppSettings) -> Unit,
    onPickExternalFolder: () -> Unit,
    onClearExternalFolder: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
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
                        text = "기본 ZIP 압축 단계 (태그 저장 시)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "태그를 저장할 때 적용할 기본 ZIP 압축 옵션입니다. 설정은 앱을 종료해도 자동으로 유지됩니다.",
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
                        text = "외부 파일 불러오기 경로",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "설정된 경로/폴더명/이미지 파일들 구조의 폴더를 폴더명.cbz(무압축 보관)로 불러옵니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "현재 경로: ${settings.externalImportFolderName ?: "설정되지 않음"}",
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
                            Text(if (settings.externalImportFolderUri.isNullOrBlank()) "경로 선택" else "경로 변경")
                        }
                        if (!settings.externalImportFolderUri.isNullOrBlank()) {
                            TextButton(onClick = onClearExternalFolder) {
                                Text("초기화")
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
                                text = "외부파일을 성공적으로 불러온 후 원본파일을 삭제합니다",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = "불러오기에 성공한 원본 폴더만 삭제하며, 실패한 항목은 유지합니다.",
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
                        text = "페이지 목록 및 미리보기 설정",
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
                                text = "페이지 목록에서 이미지 보이기",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = "메타데이터 편집 > 페이지 탭 목록에 썸네일을 표시합니다. (캐시되지 않음)",
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
                                text = "미리보기 이동 시 Manga 태그 방향 따르기",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = "켜면 Manga 태그가 Yes / YesAndRightToLeft일 때 우→좌(오른쪽에서 왼쪽)로 넘깁니다. 끄면 항상 좌→우로 이동합니다.",
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
                        text = "파일명 변경 (Rename) 기본 설정",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    OutlinedTextField(
                        value = settings.renameTemplate,
                        onValueChange = { onUpdateSettings(settings.copy(renameTemplate = it)) },
                        label = { Text("기본 템플릿") },
                        supportingText = { Text("사용 가능: {series}, {issue}, {volume}, {year}, {title}, {publisher}") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FileRenamer.PRESET_TEMPLATES.forEach { (tpl, desc) ->
                            AssistChip(
                                onClick = { onUpdateSettings(settings.copy(renameTemplate = tpl)) },
                                label = { Text(desc) },
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
                            label = { Text("권수 0-패딩") },
                            singleLine = true,
                            modifier = Modifier.width(120.dp),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = settings.smartCleanup,
                                onCheckedChange = { onUpdateSettings(settings.copy(smartCleanup = it)) },
                            )
                            Text("빈 괄호·공백 자동 정리", style = MaterialTheme.typography.bodySmall)
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
                            text = "시리즈 없음(단권) 시 {title}을 파일명으로 사용",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("앱 정보", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("CBZTagger (Android Native · 완전 오프라인)", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "• CBZ(ZIP) 전용 · ComicInfo.xml 스키마 호환\n• 인터넷 권한 없음 (완전 오프라인 동작)\n• 외부 이미지 폴더 → 무압축 CBZ 불러오기, 태그 저장 시 ZIP 압축률 설정(기존 유지 / 0~9), 일괄 작업 지원",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
