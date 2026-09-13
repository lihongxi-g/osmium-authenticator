package com.safekey.authenticator.legal

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegalLangTest {

    @Test
    fun storedTags_mapToSiteCodes() {
        assertEquals("zh-Hans", LegalLang.siteCode("zh"))
        assertEquals("zh-Hant", LegalLang.siteCode("zh-TW"))
        assertEquals("zh-Hant", LegalLang.siteCode("zh-Hant"))
        assertEquals("zh-Hant", LegalLang.siteCode("zh-HK"))
        assertEquals("en", LegalLang.siteCode("en"))
        assertEquals("de", LegalLang.siteCode("de"))
        assertEquals("es", LegalLang.siteCode("es"))
        assertEquals("fr", LegalLang.siteCode("fr"))
        assertEquals("hi", LegalLang.siteCode("hi"))
        assertEquals("ja", LegalLang.siteCode("ja"))
        assertEquals("ko", LegalLang.siteCode("ko"))
        assertEquals("ru", LegalLang.siteCode("ru"))
        // Unknown or "follow system" values fall back to English.
        assertEquals("en", LegalLang.siteCode(null))
        assertEquals("en", LegalLang.siteCode(""))
        assertEquals("en", LegalLang.siteCode("pt-BR"))
        assertEquals("en", LegalLang.siteCode("xx"))
    }

    @Test
    fun systemLocales_mapToSiteCodes() {
        assertEquals("zh-Hans", LegalLang.siteCode(Locale.SIMPLIFIED_CHINESE))
        assertEquals("zh-Hant", LegalLang.siteCode(Locale.TRADITIONAL_CHINESE))
        assertEquals(
            "zh-Hant",
            LegalLang.siteCode(Locale.Builder().setLanguage("zh").setScript("Hant").build())
        )
        assertEquals("zh-Hant", LegalLang.siteCode(Locale.forLanguageTag("zh-HK")))
        assertEquals("zh-Hans", LegalLang.siteCode(Locale.forLanguageTag("zh-SG")))
        assertEquals("ja", LegalLang.siteCode(Locale.JAPANESE))
        assertEquals("de", LegalLang.siteCode(Locale.GERMAN))
        assertEquals("en", LegalLang.siteCode(Locale.US))
        assertEquals("en", LegalLang.siteCode(Locale.forLanguageTag("pt-BR")))
    }

    @Test
    fun refreshCooldown() {
        assertTrue(LegalDocsRepository.isDue(0L, 1_000L))
        assertFalse(LegalDocsRepository.isDue(10_000L, 10_500L))
        assertTrue(LegalDocsRepository.isDue(10_000L, 10_000L + 10 * 60 * 1000L))
    }

    @Test
    fun urls() {
        assertEquals(
            "https://osmium.im/useragreement/zh-Hans.txt",
            LegalDocsRepository.textUrl(LegalDoc.TERMS, "zh-Hans")
        )
        assertEquals(
            "https://osmium.im/privacypolicy/en.txt",
            LegalDocsRepository.textUrl(LegalDoc.PRIVACY, "en")
        )
        assertEquals(
            "https://osmium.im/useragreement/de/",
            LegalDocsRepository.pageUrl(LegalDoc.TERMS, "de")
        )
    }
}
