package com.safekey.authenticator.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.sp
import com.safekey.authenticator.R
import com.safekey.authenticator.data.AppSettings

/** Monospace font for verification codes. */
val monospaceFamily = FontFamily.Monospace

@OptIn(ExperimentalTextApi::class)
private fun variableFamily(resId: Int, maxWeight: Int, boost: Int): FontFamily {
    fun settingsFor(base: Int): FontVariation.Settings {
        val weight = (base + boost).coerceIn(100, maxWeight)
        return FontVariation.Settings(FontVariation.weight(weight))
    }

    // Every weight is bound explicitly: Manrope and Noto Serif SC both default
    // to their ExtraLight instance, so a plain file binding would render the
    // whole interface hairline-thin.
    return FontFamily(
        Font(resId, weight = FontWeight.Normal, variationSettings = settingsFor(400)),
        Font(resId, weight = FontWeight.Medium, variationSettings = settingsFor(500)),
        Font(resId, weight = FontWeight.SemiBold, variationSettings = settingsFor(600)),
        Font(resId, weight = FontWeight.Bold, variationSettings = settingsFor(700))
    )
}

/**
 * Stored setting key -> typeface, with [boost] added to every weight.
 *
 * The bundled faces are variable fonts, so a boost of 0-150 thickens the whole
 * interface without swapping files. The platform font is not variable, so it
 * ignores the boost. Unknown keys mean the system font.
 *
 * Callers should `remember` the result: a new FontFamily instance per
 * recomposition would defeat Compose's text caching.
 */
fun interfaceFontFamily(key: String, boost: Int = 0): FontFamily = when (key) {
    AppSettings.FONT_FAMILY_LORA -> variableFamily(R.font.lora, 700, boost)
    AppSettings.FONT_FAMILY_MANROPE -> variableFamily(R.font.manrope, 800, boost)
    AppSettings.FONT_FAMILY_LITERATA -> variableFamily(R.font.literata, 900, boost)
    AppSettings.FONT_FAMILY_HAN_SERIF -> variableFamily(R.font.hanserif, 900, boost)
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
