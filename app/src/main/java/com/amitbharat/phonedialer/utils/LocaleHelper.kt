package com.amitbharat.phonedialer.utils

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

object LocaleHelper {

    private const val PREFS_NAME = "app_language_prefs"
    private const val KEY_LANGUAGE = "selected_language"

    fun isHindi(context: Context): Boolean {
        return getLanguage(context).startsWith("hi")
    }

    fun getLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, "en") ?: "en"
    }

    fun wrapContext(context: Context): Context {
        val lang = getLanguage(context)
        val locale = Locale(lang)
        Locale.setDefault(locale)

        val res = context.resources
        val config = Configuration(res.configuration)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale)
            val localeList = LocaleList(locale)
            LocaleList.setDefault(localeList)
            config.setLocales(localeList)
            context.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
            @Suppress("DEPRECATION")
            res.updateConfiguration(config, res.displayMetrics)
            context
        }
    }

    fun setLocale(context: Context, languageTag: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, languageTag).apply()

        val locale = Locale(languageTag)
        Locale.setDefault(locale)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            val res = context.resources
            val config = Configuration(res.configuration)
            @Suppress("DEPRECATION")
            config.locale = locale
            @Suppress("DEPRECATION")
            res.updateConfiguration(config, res.displayMetrics)
        }

        try {
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(languageTag)
            )
        } catch (_: Exception) {
        }

        if (context is Activity) {
            context.recreate()
        }
    }
}
