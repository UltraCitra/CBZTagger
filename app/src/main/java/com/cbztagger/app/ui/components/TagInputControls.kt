package com.cbztagger.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Dropdown selector for ComicInfo `<Manga>` tag with full XML tag names and explanations
 * in order: `Yes`, `YesAndRightToLeft`, `No`.
 */
@Composable
fun MangaSelectorRow(
    manga: String,
    onMangaChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    emptyLabel: String = "지정 안 함",
) {
    val options = remember(emptyLabel) {
        listOf(
            "" to emptyLabel,
            "Yes" to "Yes (망가)",
            "YesAndRightToLeft" to "YesAndRightToLeft (망가 · 우→좌)",
            "No" to "No (일반 · 좌→우)",
        )
    }
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = options.firstOrNull { it.first.equals(manga, ignoreCase = true) }?.second
        ?: manga.ifBlank { emptyLabel }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("읽기 방향 (Manga)", style = MaterialTheme.typography.bodyMedium)
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = currentLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            expanded = false
                            onMangaChange(value)
                        },
                    )
                }
            }
        }
    }
}

/**
 * Combined Penciller & Writer input by default, with an adjacent checkbox to split into
 * separate Penciller (그림작가) and Writer (글작가) fields when they differ.
 *
 * Standard ComicInfo.xml convention:
 * - Penciller = 그림작가 (art/pencils) — prioritized first
 * - Writer = 글작가 (story/script)
 */
@Composable
fun AuthorCreditInput(
    writer: String,
    penciller: String,
    resetKey: Any? = Unit,
    onChange: (writer: String, penciller: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val initialSeparate = remember(resetKey) {
        writer.isNotBlank() && penciller.isNotBlank() && !writer.trim().equals(penciller.trim(), ignoreCase = true)
    }
    var separateAuthors by remember(resetKey) { mutableStateOf(initialSeparate) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!separateAuthors) {
            val combinedValue = penciller.ifBlank { writer }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedTextField(
                    value = combinedValue,
                    onValueChange = { v -> onChange(v, v) },
                    label = { Text("작가 (그림·글)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable {
                            separateAuthors = true
                            if (writer.isBlank() && penciller.isNotBlank()) {
                                onChange(penciller, penciller)
                            } else if (penciller.isBlank() && writer.isNotBlank()) {
                                onChange(writer, writer)
                            }
                        }
                        .padding(horizontal = 2.dp),
                ) {
                    Checkbox(
                        checked = false,
                        onCheckedChange = { checked ->
                            if (checked) {
                                separateAuthors = true
                                if (writer.isBlank() && penciller.isNotBlank()) {
                                    onChange(penciller, penciller)
                                } else if (penciller.isBlank() && writer.isNotBlank()) {
                                    onChange(writer, writer)
                                }
                            }
                        },
                    )
                    Text(
                        text = "그림·글 다름",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        separateAuthors = false
                        val unified = penciller.ifBlank { writer }
                        onChange(unified, unified)
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = true,
                    onCheckedChange = { checked ->
                        if (!checked) {
                            separateAuthors = false
                            val unified = penciller.ifBlank { writer }
                            onChange(unified, unified)
                        }
                    },
                )
                Text(
                    text = "그림작가와 글작가가 다릅니다",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedTextField(
                value = penciller,
                onValueChange = { v -> onChange(writer, v) },
                label = { Text("그림작가 (Penciller)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = writer,
                onValueChange = { v -> onChange(v, penciller) },
                label = { Text("글작가 (Writer)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
