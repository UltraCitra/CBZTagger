package com.cbztagger.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.cbztagger.app.R
import com.cbztagger.app.core.PageType
import com.cbztagger.app.core.ZipCompressionOption

@Composable
fun PageType.localizedLabel(): String = when (this) {
    PageType.FRONT_COVER -> stringResource(R.string.page_type_front_cover)
    PageType.INNER_COVER -> stringResource(R.string.page_type_inner_cover)
    PageType.ROUNDUP -> stringResource(R.string.page_type_roundup)
    PageType.STORY -> stringResource(R.string.page_type_story)
    PageType.ADVERTISEMENT -> stringResource(R.string.page_type_advertisement)
    PageType.EDITORIAL -> stringResource(R.string.page_type_editorial)
    PageType.LETTERS -> stringResource(R.string.page_type_letters)
    PageType.PREVIEW -> stringResource(R.string.page_type_preview)
    PageType.BACK_COVER -> stringResource(R.string.page_type_back_cover)
    PageType.OTHER -> stringResource(R.string.page_type_other)
    PageType.DELETED -> stringResource(R.string.page_type_deleted)
}

@Composable
fun ZipCompressionOption.localizedLabel(): String = when (this) {
    ZipCompressionOption.KEEP_ORIGINAL -> stringResource(R.string.compression_opt_keep)
    ZipCompressionOption.STORE -> stringResource(R.string.compression_opt_store)
    ZipCompressionOption.LEVEL_1 -> stringResource(R.string.compression_opt_1)
    ZipCompressionOption.LEVEL_6 -> stringResource(R.string.compression_opt_6)
    ZipCompressionOption.LEVEL_9 -> stringResource(R.string.compression_opt_9)
    else -> stringResource(R.string.compression_opt_n, level)
}

@Composable
fun localizeCompressionSummary(raw: String): String = when (raw) {
    "Store (무압축)" -> stringResource(R.string.compression_summary_store)
    "혼합 (Store/Deflate)" -> stringResource(R.string.compression_summary_mixed)
    "없음" -> stringResource(R.string.compression_summary_none)
    else -> raw
}

@Composable
fun localizedRenamePresetLabel(template: String, fallback: String): String = when (template) {
    "{series} #{issue} ({year})" -> stringResource(R.string.rename_preset_default)
    "{series} {issue}권 ({year})" -> stringResource(R.string.rename_preset_kr_vol)
    "{series} v{volume} #{issue} ({year})" -> stringResource(R.string.rename_preset_volume)
    "{series} #{issue} - {title}" -> stringResource(R.string.rename_preset_title)
    "{series} {issue}" -> stringResource(R.string.rename_preset_simple)
    else -> fallback
}
