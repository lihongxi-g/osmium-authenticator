package com.safekey.authenticator.data

import android.content.Context
import java.util.Locale

/**
 * App language preference. Kept in SharedPreferences (NOT DataStore) because
 * attachBaseContext must read it synchronously before the Activity is created.
 */
object LanguagePrefs {

    private const val PREFS = "safekey_lang"
    private const val KEY = "language"

    /** null = follow system */
    fun get(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)

    fun set(context: Context, language: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (language == null) remove(KEY) else putString(KEY, language)
        }.apply()
    }

    fun localeFor(context: Context): Locale? {
        val lang = get(context) ?: return null
        return Locale.forLanguageTag(lang)
    }

    /** The tag actually in effect — the stored choice, or the system language. */
    fun effectiveTag(context: Context): String =
        get(context) ?: Locale.getDefault().language

    /**
     * True when the interface is Chinese. "Follow system" counts only when the
     * system language is Chinese.
     */
    fun isChinese(context: Context): Boolean =
        effectiveTag(context).lowercase().startsWith("zh")

    /** The tag a language choice would result in ([tag] null = follow system). */
    fun resolvesToChinese(context: Context, tag: String?): Boolean =
        (tag ?: Locale.getDefault().language).lowercase().startsWith("zh")
}
