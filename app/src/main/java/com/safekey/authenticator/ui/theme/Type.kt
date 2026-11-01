package com.safekey.authenticator.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.sp
import com.safekey.authenticator.R
import com.safekey.authenticator.data.AppSettings

/** Monospace font for verification codes. */
val monospaceFamily = FontFamily.Monospace

/**
 * The bundled faces ship as **static instances**, one file per weight step.
 *
 * Earlier revisions used the variable files and bound weights through
 * `FontVariation` settings; the platform silently ignored them (Noto Serif
 * SC's ExtraLight default rendered even with 400 requested), so the weight
 * slider did nothing. Fixed files remove the question entirely: Compose picks a
 * file by weight and cannot fall back to ignoring an axis.
 *
 * The slider therefore moves the *type tokens* (see [withWeightDelta]), and the
 * stored value is an absolute body weight that matches one of these files.
 */
val LoraFamily: FontFamily = FontFamily(
    Font(R.font.lora, weight = FontWeight.Normal),
    Font(R.font.lora_medium, weight = FontWeight.Medium),
    Font(R.font.lora_semibold, weight = FontWeight.SemiBold),
    Font(R.font.lora_bold, weight = FontWeight.Bold)
)

val ManropeFamily: FontFamily = FontFamily(
    Font(R.font.manrope, weight = FontWeight.Normal),
    Font(R.font.manrope_medium, weight = FontWeight.Medium),
    Font(R.font.manrope_semibold, weight = FontWeight.SemiBold),
    Font(R.font.manrope_bold, weight = FontWeight.Bold),
    Font(R.font.manrope_extrabold, weight = FontWeight.ExtraBold)
)

val LiterataFamily: FontFamily = FontFamily(
    Font(R.font.literata, weight = FontWeight.Normal),
    Font(R.font.literata_medium, weight = FontWeight.Medium),
    Font(R.font.literata_semibold, weight = FontWeight.SemiBold),
    Font(R.font.literata_bold, weight = FontWeight.Bold),
    Font(R.font.literata_extrabold, weight = FontWeight.ExtraBold),
    Font(R.font.literata_black, weight = FontWeight.Black)
)

/** Stored setting key -> typeface. Unknown keys mean the system font. */
fun interfaceFontFamily(key: String): FontFamily = when (key) {
    AppSettings.FONT_FAMILY_LORA -> LoraFamily
    AppSettings.FONT_FAMILY_MANROPE -> ManropeFamily
    AppSettings.FONT_FAMILY_LITERATA -> LiterataFamily
    else -> FontFamily.Default
}

val AppTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

/**
 * App-level text scale, on top of the system font scale.
 *
 * Deliberately a CompositionLocal instead of an override of
 * `LocalDensity.fontScale`: a density override also feeds Compose's non-linear
 * font-scaling curve (active above 1.05), so the chosen step would not be the
 * step the user sees, and it would resize sp values inside components we do
 * not own. This local only multiplies the type tokens, plus the few literal sp
 * values that carry real information (the verification code).
 */
val LocalFontScale = staticCompositionLocalOf { 1f }

/**
 * Copy of this typography with every sp-valued token multiplied by [scale].
 *
 * Only `sp` is touched: `em` is relative to the parent size and multiplying it
 * would double-apply, while an unspecified value has nothing to multiply.
 * Both are returned unchanged instead of going through an arithmetic that
 * throws.
 */
internal fun Typography.scaledTo(scale: Float): Typography {
    if (scale == 1f) return this

    fun TextStyle.scaled(): TextStyle = copy(
        fontSize = fontSize.scaledBy(scale),
        lineHeight = lineHeight.scaledBy(scale),
        letterSpacing = letterSpacing.scaledBy(scale)
    )

    return Typography(
        displayLarge = displayLarge.scaled(),
        displayMedium = displayMedium.scaled(),
        displaySmall = displaySmall.scaled(),
        headlineLarge = headlineLarge.scaled(),
        headlineMedium = headlineMedium.scaled(),
        headlineSmall = headlineSmall.scaled(),
        titleLarge = titleLarge.scaled(),
        titleMedium = titleMedium.scaled(),
        titleSmall = titleSmall.scaled(),
        bodyLarge = bodyLarge.scaled(),
        bodyMedium = bodyMedium.scaled(),
        bodySmall = bodySmall.scaled(),
        labelLarge = labelLarge.scaled(),
        labelMedium = labelMedium.scaled(),
        labelSmall = labelSmall.scaled()
    )
}

private fun TextUnit.scaledBy(scale: Float): TextUnit =
    if (type == TextUnitType.Sp) this * scale else this

/**
 * The same type tokens rendered in [family].
 *
 * Only the typeface changes: sizes, weights and letter spacing are the ones
 * the design already uses, so switching the font cannot shift the layout.
 */
/**
 * Every token's weight raised by [delta] (clamped to 100-900).
 *
 * This is what the weight slider drives. Tokens keep their relative order, so
 * body text never overtakes a title, and because the faces ship fixed weight
 * files the change always lands on real glyphs.
 */
internal fun Typography.withWeightDelta(delta: Int): Typography {
    if (delta == 0) return this

    fun TextStyle.heavier(): TextStyle {
        val base = (fontWeight ?: FontWeight.Normal).weight
        return copy(fontWeight = FontWeight((base + delta).coerceIn(100, 900)))
    }

    return Typography(
        displayLarge = displayLarge.heavier(),
        displayMedium = displayMedium.heavier(),
        displaySmall = displaySmall.heavier(),
        headlineLarge = headlineLarge.heavier(),
        headlineMedium = headlineMedium.heavier(),
        headlineSmall = headlineSmall.heavier(),
        titleLarge = titleLarge.heavier(),
        titleMedium = titleMedium.heavier(),
        titleSmall = titleSmall.heavier(),
        bodyLarge = bodyLarge.heavier(),
        bodyMedium = bodyMedium.heavier(),
        bodySmall = bodySmall.heavier(),
        labelLarge = labelLarge.heavier(),
        labelMedium = labelMedium.heavier(),
        labelSmall = labelSmall.heavier()
    )
}

internal fun Typography.withFamily(family: FontFamily): Typography {
    if (family == FontFamily.Default) return this

    fun TextStyle.inFamily(): TextStyle = copy(fontFamily = family)

    return Typography(
        displayLarge = displayLarge.inFamily(),
        displayMedium = displayMedium.inFamily(),
        displaySmall = displaySmall.inFamily(),
        headlineLarge = headlineLarge.inFamily(),
        headlineMedium = headlineMedium.inFamily(),
        headlineSmall = headlineSmall.inFamily(),
        titleLarge = titleLarge.inFamily(),
        titleMedium = titleMedium.inFamily(),
        titleSmall = titleSmall.inFamily(),
        bodyLarge = bodyLarge.inFamily(),
        bodyMedium = bodyMedium.inFamily(),
        bodySmall = bodySmall.inFamily(),
        labelLarge = labelLarge.inFamily(),
        labelMedium = labelMedium.inFamily(),
        labelSmall = labelSmall.inFamily()
    )
}
