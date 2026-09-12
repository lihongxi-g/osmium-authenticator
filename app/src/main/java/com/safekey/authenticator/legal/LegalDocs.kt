package com.safekey.authenticator.legal

import android.content.Context
import com.safekey.authenticator.data.LanguagePrefs
import java.util.Locale

/** The two legal documents that are fetched from the website. */
enum class LegalDoc(val path: String) {
    TERMS("useragreement"),
    PRIVACY("privacypolicy"),
}

/** UI state of one legal document. */
sealed class LegalDocState {
    /** No text available yet; a fetch is in flight (or about to start). */
    object Loading : LegalDocState()

    /** Latest successfully fetched text. */
    data class Loaded(val text: String) : LegalDocState()

    /** Fetch failed and no text is available to show. */
    object Failed : LegalDocState()
}

/**
 * Maps the app's language to the language code used by the website
 * (`https://osmium.im/useragreement/<code>.txt` and `.../privacypolicy/<code>.txt`).
 *
 * The published set is: en, zh-Hans, zh-Hant, de, es, fr, hi, ja, ko, ru.
 * Anything else falls back to English (the site also lets browsers pick).
 */
object LegalLang {

    private const val DEFAULT_CODE = "en"

    private val PLAIN_CODES = setOf("en", "de", "es", "fr", "hi", "ja", "ko", "ru")

    /** Maps a stored app-language tag (see [LanguagePrefs]) to a site code. */
    fun siteCode(tag: String?): String {
        if (tag.isNullOrBlank()) return DEFAULT_CODE
        return when (tag.lowercase()) {
            "zh" -> "zh-Hans"
            "zh-tw", "zh-hant", "zh-hk", "zh-mo" -> "zh-Hant"
            else -> tag.lowercase().takeIf { it in PLAIN_CODES } ?: DEFAULT_CODE
        }
    }

    /** Maps a system locale to a site code (used when following the system language). */
    fun siteCode(locale: Locale): String {
        if (locale.language.equals("zh", ignoreCase = true)) {
            val traditional = locale.script.equals("Hant", ignoreCase = true) ||
                locale.country.uppercase(Locale.ROOT) in setOf("TW", "HK", "MO")
            return if (traditional) "zh-Hant" else "zh-Hans"
        }
        return locale.language.lowercase(Locale.ROOT).takeIf { it in PLAIN_CODES } ?: DEFAULT_CODE
    }

    /** The site code for the language the app is currently showing. */
    fun currentSiteCode(context: Context): String {
        val stored = LanguagePrefs.get(context)
        if (stored != null) return siteCode(stored)
        val locales = context.resources.configuration.locales
        val locale = if (locales.isEmpty) Locale.getDefault() else locales[0]
        return siteCode(locale)
    }
}
