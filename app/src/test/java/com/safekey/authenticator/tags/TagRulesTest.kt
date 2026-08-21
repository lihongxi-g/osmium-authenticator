package com.safekey.authenticator.tags

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagRulesTest {

    @Test
    fun `normalizes surrounding whitespace without changing inner spaces`() {
        assertEquals("工作 重要", TagRules.normalizeName("  工作 重要  "))
    }

    @Test
    fun `blank names are invalid`() {
        assertFalse(TagRules.isValidName("   "))
    }

    @Test
    fun `names over twenty characters are invalid`() {
        assertFalse(TagRules.isValidName("123456789012345678901"))
    }

    @Test
    fun `names at twenty characters are valid`() {
        assertTrue(TagRules.isValidName("12345678901234567890"))
    }

    @Test
    fun `duplicate names are compared case insensitively`() {
        assertTrue(TagRules.isDuplicate("Work", listOf(" work ", "Games")))
        assertFalse(TagRules.isDuplicate("School", listOf("Work", "Games")))
    }
}
