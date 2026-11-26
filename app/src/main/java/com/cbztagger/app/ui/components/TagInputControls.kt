package com.cbztagger.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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

    fun setSeparate(checked: Boolean) {
        if (checked == separateAuthors) return
        separateAuthors = checked
        if (checked) {
            if (writer.isBlank() && penciller.isNotBlank()) {
                onChange(penciller, penciller)
            } else if (penciller.isBlank() && writer.isNotBlank()) {
                onChange(writer, writer)
            }
        } else {
            val unified = penciller.ifBlank { writer }
            onChange(unified, unified)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedTextField(
                value = if (separateAuthors) penciller else penciller.ifBlank { writer },
                onValueChange = { v ->
                    if (separateAuthors) {
                        onChange(writer, v)
                    } else {
                        onChange(v, v)
                    }
                },
                label = {
                    Text(if (separateAuthors) "그림작가 (Penciller)" else "작가 (그림·글)")
                },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable { setSeparate(!separateAuthors) }
                    .padding(horizontal = 2.dp),
            ) {
                Checkbox(
                    checked = separateAuthors,
                    onCheckedChange = { checked -> setSeparate(checked) },
                )
                Text(
                    text = "그림·글 다름",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
        }

        AnimatedVisibility(
            visible = separateAuthors,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
        ) {
            OutlinedTextField(
                value = writer,
                onValueChange = { v -> onChange(v, penciller) },
                label = { Text("글작가 (Writer)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
    }
}
