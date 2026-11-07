package com.safekey.authenticator.tags

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertEquals("#E05450", TagColor.hexFor(TagColor.RED))
    }

    @Test
    fun `invalid colors fall back to blue`() {
        assertFalse(TagColor.isValid("not-a-color"))
        assertEquals(TagColor.BLUE, TagColor.normalize("not-a-color"))
    }

    @Test
    fun `palette holds twelve selectable colors`() {
        assertEquals(12, TagColor.PALETTE.size)
        assertEquals(TagColor.PALETTE.size, TagColor.PALETTE.map { it.uppercase() }.toSet().size)
        assertTrue(TagColor.PALETTE.all(TagColor::isValid))
    }

    @Test
    fun `named presets map onto a palette swatch`() {
        // The editor lights up the swatch that matches the stored color, so a
        // legacy "blue" tag must resolve to an entry in the grid.
        TagColor.ALL.forEach { named ->
            assertEquals(TagColor.hexFor(named), TagColor.paletteEntryFor(named))
        }
        assertNull(TagColor.paletteEntryFor("#123456"))
    }
}
