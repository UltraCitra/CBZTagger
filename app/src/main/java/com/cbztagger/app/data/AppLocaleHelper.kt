package com.cbztagger.app.data

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object AppLanguage {
    const val SYSTEM = "system"
    const val KOREAN = "ko"
    const val ENGLISH = "en"

    fun normalize(raw: String?): String = when (raw?.lowercase(Locale.US)) {
        KOREAN -> KOREAN
        ENGLISH -> ENGLISH
        else -> SYSTEM
    }
}

object AppLocaleHelper {
    fun resolveEffectiveLanguage(context: Context, savedLanguage: String): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(LocaleManager::class.java)
            val appLocales = localeManager?.applicationLocales
            if (appLocales != null) {
                if (appLocales.isEmpty) return AppLanguage.SYSTEM
                val first = appLocales[0]
                return when (first?.language?.lowercase(Locale.US)) {
                    AppLanguage.KOREAN -> AppLanguage.KOREAN
                    AppLanguage.ENGLISH -> AppLanguage.ENGLISH
                    else -> AppLanguage.normalize(savedLanguage)
                }
            }
        }
        return AppLanguage.normalize(savedLanguage)
    }

    fun applyLanguage(activity: Activity?, appContext: Context, languageCode: String) {
        val normalized = AppLanguage.normalize(languageCode)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = appContext.getSystemService(LocaleManager::class.java)
            if (localeManager != null) {
                val targetList = when (normalized) {
                    AppLanguage.KOREAN -> LocaleList.forLanguageTags("ko")
                    AppLanguage.ENGLISH -> LocaleList.forLanguageTags("en")
                    else -> LocaleList.getEmptyLocaleList()
                }
                if (localeManager.applicationLocales != targetList) {
                    localeManager.applicationLocales = targetList
                }
                return
            }
        }
        activity?.recreate()
    }

    fun wrapContext(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return base
        }
        val prefs = base.getSharedPreferences(AppPreferences.PREFS_NAME, Context.MODE_PRIVATE)
        val lang = AppLanguage.normalize(prefs.getString(AppPreferences.KEY_APP_LANGUAGE, AppLanguage.SYSTEM))
        if (lang == AppLanguage.SYSTEM) {
            return base
        }
        val locale = Locale.forLanguageTag(lang)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }
}
