package com.safekey.authenticator.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** Typography token scaling: sp is multiplied, nothing else is touched. */
class TypographyScaleTest {

    @Test
    fun `font size and line height are multiplied`() {
        val scaled = AppTypography.scaledTo(1.3f)
        assertEquals(24f * 1.3f, scaled.headlineSmall.fontSize.value, 0.01f)
        assertEquals(32f * 1.3f, scaled.headlineSmall.lineHeight.value, 0.01f)
    }

    @Test
    fun `letter spacing is multiplied too`() {
        val scaled = AppTypography.scaledTo(1.3f)
        assertEquals(0.5f * 1.3f, scaled.bodyLarge.letterSpacing.value, 0.01f)
        assertEquals(0.1f * 1.3f, scaled.titleMedium.letterSpacing.value, 0.01f)
    }

    @Test
    fun `scaling by one returns the same typography`() {
        assertSame(AppTypography, AppTypography.scaledTo(1f))
    }

    @Test
    fun `unspecified values survive untouched`() {
        val base = Typography(headlineSmall = TextStyle(fontSize = TextUnit.Unspecified))
        val scaled = base.scaledTo(1.3f)
        assertEquals(TextUnitType.Unspecified, scaled.headlineSmall.fontSize.type)
    }
}
