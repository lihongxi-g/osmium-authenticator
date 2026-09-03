package com.safekey.authenticator.tags

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagColorTest {

    @Test
    fun `accepts and normalizes custom six digit hex colors`() {
        assertTrue(TagColor.isValid("#1a2B3c"))
        assertEquals("#1A2B3C", TagColor.normalize("  #1a2B3c  "))
    }

    @Test
    fun `keeps legacy named colors compatible`() {
        assertTrue(TagColor.isValid(TagColor.RED))
        assertEquals(TagColor.RED, TagColor.normalize(TagColor.RED))
        assertEquals("#BA1A1A", TagColor.hexFor(TagColor.RED))
    }

    @Test
    fun `invalid colors fall back to blue`() {
        assertFalse(TagColor.isValid("not-a-color"))
        assertEquals(TagColor.BLUE, TagColor.normalize("not-a-color"))
    }

    @Test
    fun `palette contains selectable muted colors`() {
        assertTrue(TagColor.PALETTE.isNotEmpty())
        assertTrue(TagColor.PALETTE.all(TagColor::isValid))
    }
}
