package com.cbztagger.app.data

import android.content.Context
import android.content.SharedPreferences
import com.cbztagger.app.core.FileRenamer

data class AppSettings(
    val zipCompressionLevel: Int = -1,
    val lastCompressionSliderLevel: Int = 6,
    val lastBatchCompressLevel: Int = 6,
    val renameTemplate: String = FileRenamer.DEFAULT_TEMPLATE,
    val issuePadding: Int = 3,
    val smartCleanup: Boolean = true,
    val useTitleForOneshotRename: Boolean = false,
    val lastFolderUri: String? = null,
    val externalImportFolderUri: String? = null,
    val externalImportFolderName: String? = null,
    val sequentialRenameOnExternalImport: Boolean = false,
    val deleteExternalSourceAfterImport: Boolean = false,
    val showPageListThumbnails: Boolean = false,
    val previewFollowMangaDirection: Boolean = true,
    val appLanguage: String = AppLanguage.SYSTEM,
)

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): AppSettings {
        val zipLevel = prefs.getInt(KEY_ZIP_LEVEL, -1).coerceIn(-1, 9)
        val defaultSlider = if (zipLevel in 0..9) zipLevel else 6
        return AppSettings(
            zipCompressionLevel = zipLevel,
            lastCompressionSliderLevel = prefs.getInt(KEY_LAST_SLIDER_LEVEL, defaultSlider).coerceIn(0, 9),
            lastBatchCompressLevel = prefs.getInt(KEY_LAST_BATCH_COMPRESS_LEVEL, defaultSlider).coerceIn(0, 9),
            renameTemplate = prefs.getString(KEY_RENAME_TEMPLATE, FileRenamer.DEFAULT_TEMPLATE)
                ?: FileRenamer.DEFAULT_TEMPLATE,
            issuePadding = prefs.getInt(KEY_ISSUE_PADDING, 3).coerceIn(0, 6),
            smartCleanup = prefs.getBoolean(KEY_SMART_CLEANUP, true),
            useTitleForOneshotRename = prefs.getBoolean(KEY_USE_TITLE_FOR_ONESHOT, false),
            lastFolderUri = prefs.getString(KEY_LAST_FOLDER_URI, null),
            externalImportFolderUri = prefs.getString(KEY_EXTERNAL_IMPORT_URI, null),
            externalImportFolderName = prefs.getString(KEY_EXTERNAL_IMPORT_NAME, null),
            sequentialRenameOnExternalImport = prefs.getBoolean(KEY_SEQUENTIAL_RENAME_EXTERNAL_IMPORT, false),
            deleteExternalSourceAfterImport = prefs.getBoolean(KEY_DELETE_EXTERNAL_SOURCE, false),
            showPageListThumbnails = prefs.getBoolean(KEY_SHOW_PAGE_LIST_THUMBNAILS, false),
            previewFollowMangaDirection = prefs.getBoolean(KEY_PREVIEW_FOLLOW_MANGA_DIR, true),
            appLanguage = AppLanguage.normalize(prefs.getString(KEY_APP_LANGUAGE, AppLanguage.SYSTEM)),
        )
    }

    fun save(settings: AppSettings) {
        val clampedZip = settings.zipCompressionLevel.coerceIn(-1, 9)
        val clampedSlider = if (clampedZip in 0..9) {
            clampedZip
        } else {
            settings.lastCompressionSliderLevel.coerceIn(0, 9)
        }
        // Use commit() so settings are flushed synchronously to disk before process kill
        prefs.edit()
            .putInt(KEY_ZIP_LEVEL, clampedZip)
            .putInt(KEY_LAST_SLIDER_LEVEL, clampedSlider)
            .putInt(KEY_LAST_BATCH_COMPRESS_LEVEL, settings.lastBatchCompressLevel.coerceIn(0, 9))
            .putString(KEY_RENAME_TEMPLATE, settings.renameTemplate)
            .putInt(KEY_ISSUE_PADDING, settings.issuePadding.coerceIn(0, 6))
            .putBoolean(KEY_SMART_CLEANUP, settings.smartCleanup)
            .putBoolean(KEY_USE_TITLE_FOR_ONESHOT, settings.useTitleForOneshotRename)
            .putString(KEY_LAST_FOLDER_URI, settings.lastFolderUri)
            .putString(KEY_EXTERNAL_IMPORT_URI, settings.externalImportFolderUri)
            .putString(KEY_EXTERNAL_IMPORT_NAME, settings.externalImportFolderName)
            .putBoolean(KEY_SEQUENTIAL_RENAME_EXTERNAL_IMPORT, settings.sequentialRenameOnExternalImport)
            .putBoolean(KEY_DELETE_EXTERNAL_SOURCE, settings.deleteExternalSourceAfterImport)
            .putBoolean(KEY_SHOW_PAGE_LIST_THUMBNAILS, settings.showPageListThumbnails)
            .putBoolean(KEY_PREVIEW_FOLLOW_MANGA_DIR, settings.previewFollowMangaDirection)
            .putString(KEY_APP_LANGUAGE, AppLanguage.normalize(settings.appLanguage))
            .commit()
    }

    companion object {
        const val PREFS_NAME = "comictag_mobile_prefs"
        const val KEY_APP_LANGUAGE = "app_language"
        private const val KEY_ZIP_LEVEL = "zip_compression_level"
        private const val KEY_LAST_SLIDER_LEVEL = "last_compression_slider_level"
        private const val KEY_LAST_BATCH_COMPRESS_LEVEL = "last_batch_compress_level"
        private const val KEY_RENAME_TEMPLATE = "rename_template"
        private const val KEY_ISSUE_PADDING = "issue_padding"
        private const val KEY_SMART_CLEANUP = "smart_cleanup"
        private const val KEY_USE_TITLE_FOR_ONESHOT = "use_title_for_oneshot_rename"
        private const val KEY_LAST_FOLDER_URI = "last_folder_uri"
        private const val KEY_EXTERNAL_IMPORT_URI = "external_import_folder_uri"
        private const val KEY_EXTERNAL_IMPORT_NAME = "external_import_folder_name"
        private const val KEY_SEQUENTIAL_RENAME_EXTERNAL_IMPORT = "sequential_rename_external_import"
        private const val KEY_DELETE_EXTERNAL_SOURCE = "delete_external_source_after_import"
        private const val KEY_SHOW_PAGE_LIST_THUMBNAILS = "show_page_list_thumbnails"
        private const val KEY_PREVIEW_FOLLOW_MANGA_DIR = "preview_follow_manga_direction"
    }
}
