package com.cbztagger.app.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cbztagger.app.core.BookmarkRowDraft
import com.cbztagger.app.core.PageBatchHelper
import com.cbztagger.app.core.PageInfo
import kotlinx.coroutines.launch

/**
 * Dialog for selecting a 1-based start..end page range in batch page attribute editing mode.
 */
@Composable
fun PageRangeSelectDialog(
    pageCount: Int,
    onApplyRange: (Set<Int>) -> Unit,
    onDismiss: () -> Unit,
) {
    var startText by remember { mutableStateOf("1") }
    var endText by remember { mutableStateOf(pageCount.coerceAtLeast(1).toString()) }

    val startNum = startText.toIntOrNull()
    val endNum = endText.toIntOrNull()
    val isValid = startNum != null && endNum != null && startNum in 1..pageCount && endNum in 1..pageCount

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("페이지 범위 선택") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "선택할 시작~끝 페이지 번호를 입력하세요 (1 ~ ${pageCount}p).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = startText,
                        onValueChange = { startText = it.filter(Char::isDigit).take(5) },
                        label = { Text("시작") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = startText.isNotEmpty() && (startNum == null || startNum !in 1..pageCount),
                        modifier = Modifier.weight(1f),
                    )
                    Text("~", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = endText,
                        onValueChange = { endText = it.filter(Char::isDigit).take(5) },
                        label = { Text("끝") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = endText.isNotEmpty() && (endNum == null || endNum !in 1..pageCount),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (startNum != null && endNum != null) {
                        onApplyRange(PageBatchHelper.parseRangeIndices(startNum, endNum, pageCount))
                    }
                },
                enabled = isValid,
            ) {
                Text("선택")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        },
    )
}

/**
 * Bulk bookmark editor dialog using a stable `LazyColumn` + `SnapshotStateList` with
 * `decorFitsSystemWindows = false` and `safeDrawingPadding()` so the list and action bar
 * always stay cleanly visible above the soft keyboard in both portrait and landscape modes.
 */
@Composable
fun BatchBookmarkDialog(
    pageCount: Int,
    pages: List<PageInfo>,
    onSave: (List<Pair<Int, String>>) -> Unit,
    onDismiss: () -> Unit,
) {
    var nextId by remember { mutableLongStateOf(1000L) }
    val rows = remember(pages) {
        val existing = pages
            .filter { it.bookmark.isNotBlank() && it.image in 0 until pageCount }
            .sortedBy { it.image }
            .mapIndexed { idx, p ->
                BookmarkRowDraft(
                    id = idx.toLong(),
                    pageText = (p.image + 1).toString(),
                    name = p.bookmark,
                )
            }
        mutableStateListOf<BookmarkRowDraft>().apply {
            if (existing.isNotEmpty()) {
                addAll(existing)
            } else {
                add(BookmarkRowDraft(id = 0L, pageText = "", name = ""))
            }
        }
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val hasEmptyRow = rows.any { it.pageText.isBlank() || it.name.isBlank() }
    val hasInvalidPage = rows.any { row ->
        if (row.pageText.isBlank() && row.name.isBlank()) {
            false
        } else {
            val n = row.pageText.toIntOrNull()
            n == null || n !in 1..pageCount
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "북마크 지정 (1 ~ ${pageCount}p)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                    HorizontalDivider()

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(max = 360.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (rows.isEmpty()) {
                            item(key = "empty_hint") {
                                Text(
                                    text = "지정된 북마크가 없습니다. 아래 + 버튼으로 북마크를 추가하세요.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                )
                            }
                        }

                        itemsIndexed(
                            items = rows,
                            key = { _, row -> row.id },
                        ) { index, row ->
                            val pageNum = row.pageText.toIntOrNull()
                            val pageError = row.pageText.isNotEmpty() && (pageNum == null || pageNum !in 1..pageCount)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                OutlinedTextField(
                                    value = row.pageText,
                                    onValueChange = { raw ->
                                        val digits = raw.filter(Char::isDigit).take(5)
                                        if (index in rows.indices && rows[index].id == row.id) {
                                            rows[index] = rows[index].copy(pageText = digits)
                                        }
                                    },
                                    placeholder = { Text("p") },
                                    singleLine = true,
                                    isError = pageError,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.width(68.dp),
                                )
                                OutlinedTextField(
                                    value = row.name,
                                    onValueChange = { text ->
                                        if (index in rows.indices && rows[index].id == row.id) {
                                            rows[index] = rows[index].copy(name = text)
                                        }
                                    },
                                    placeholder = { Text("북마크 이름") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = {
                                        if (index in rows.indices && rows[index].id == row.id) {
                                            rows.removeAt(index)
                                        }
                                    },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Remove,
                                        contentDescription = "북마크 삭제",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (hasInvalidPage) {
                            Text(
                                text = "페이지 번호는 1 ~ $pageCount 사이여야 합니다.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (!hasEmptyRow) {
                                OutlinedButton(
                                    onClick = {
                                        val id = nextId++
                                        rows.add(BookmarkRowDraft(id = id, pageText = "", name = ""))
                                        coroutineScope.launch {
                                            if (rows.isNotEmpty()) {
                                                listState.animateScrollToItem(rows.lastIndex)
                                            }
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "북마크 추가",
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("추가")
                                }
                            } else {
                                Spacer(Modifier.width(1.dp))
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = onDismiss) {
                                    Text("취소")
                                }
                                Spacer(Modifier.width(4.dp))
                                Button(
                                    onClick = {
                                        val validEntries = rows.mapNotNull { row ->
                                            val pageNum = row.pageText.toIntOrNull()
                                            val trimmed = row.name.trim()
                                            if (pageNum != null && pageNum in 1..pageCount && trimmed.isNotEmpty()) {
                                                pageNum to trimmed
                                            } else {
                                                null
                                            }
                                        }
                                        onSave(validEntries)
                                    },
                                    enabled = !hasInvalidPage,
                                ) {
                                    Text("저장")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
