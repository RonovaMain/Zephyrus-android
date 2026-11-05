package dev.ronova.zephyrus

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Язык интерфейса. По умолчанию приложение следует языку системы; если такого перевода нет, берётся английский
 * (он лежит в res/values). Пользователь может выбрать язык вручную в Настройках.
 *
 * Android 13+: используется системный выбор языка приложения (он же виден в настройках телефона).
 * Android 10–12: выбор хранится в SharedPreferences и применяется в attachBaseContext.
 */
object AppLocale {
    data class Lang(val tag: String, val nativeName: String)

    val SUPPORTED = listOf(
        Lang("en", "English"),
        Lang("ru", "Русский"),
        Lang("uk", "Українська"),
        Lang("be", "Беларуская"),
    )

    private const val PREFS = "settings"
    private const val KEY = "appLanguage"

    /** Выбранный язык: тег из [SUPPORTED] или пустая строка — «как в системе». */
    fun current(context: Context): String {
        if (Build.VERSION.SDK_INT >= 33) {
            val tags = context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
            return SUPPORTED.firstOrNull { tags.isNotEmpty() && tags.startsWith(it.tag) }?.tag ?: ""
        }
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
    }

    fun set(activity: Activity, tag: String) {
        if (Build.VERSION.SDK_INT >= 33) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tag).apply()
            activity.recreate()
        }
    }

    /** Для Android 10–12: наложить выбранный язык на контекст. На 13+ ничего не делает. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val tag = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
        if (tag.isEmpty()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }
}
