package com.safekey.authenticator.ui.theme

import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safekey.authenticator.data.AppSettings

private val FallbackLight = lightColorScheme(
    primary = Color(0xFF005AC1), onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E3FF), onPrimaryContainer = Color(0xFF001B3F)
)
private val FallbackDark = darkColorScheme(
    primary = Color(0xFFADC6FF), onPrimary = Color(0xFF002E69),
    primaryContainer = Color(0xFF004494), onPrimaryContainer = Color(0xFFD7E3FF)
)

/** Baseline Material 3 shape scale, unchanged from the original design. */
private val MaterialShapes = Shapes()

/** Expressive shape scale — noticeably rounder, larger corner radii. */
private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(30.dp),
    extraLarge = RoundedCornerShape(38.dp)
)

/** Expressive type scale: heavier titles, slightly larger headings. */
private val ExpressiveTypography = AppTypography.copy(
    headlineSmall = AppTypography.headlineSmall.copy(
        fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold
    ),
    titleLarge = AppTypography.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = AppTypography.titleMedium.copy(fontWeight = FontWeight.Bold),
    labelLarge = AppTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
)

/**
 * Transition specs for the two design systems. Material 3 uses the standard
 * ease-based specs; the expressive system uses springy ones (the same idea as
 * the official expressive motion scheme, implemented on the stable API so the
 * release stays on the app's existing Compose toolchain).
 */
class MotionTokens(
    val enter: FiniteAnimationSpec<IntOffset>,
    val exit: FiniteAnimationSpec<IntOffset>,
    val fadeIn: FiniteAnimationSpec<Float>,
    val fadeOut: FiniteAnimationSpec<Float>
)

private val StandardMotion = MotionTokens(
    enter = tween(280),
    exit = tween(260),
    fadeIn = tween(220),
    fadeOut = tween(200)
)

private val ExpressiveMotion = MotionTokens(
    enter = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
    exit = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium),
    fadeIn = tween(180),
    fadeOut = tween(150)
)

/** Current motion tokens; expressive mode swaps in the springy set. */
val LocalOsmiumMotion = staticCompositionLocalOf { StandardMotion }

/**
 * Selectable design system. Both modes drive the same Material 3 components,
 * business state, navigation and accessibility semantics — only the colour,
 * shape, type and motion tokens differ.
 */
@Composable
fun SafeKeyTheme(
    themeMode: String,
    dynamicColor: Boolean,
    designSystem: String = AppSettings.DESIGN_MATERIAL_3,
    paletteSeed: Long = AppSettings.DEFAULT_PALETTE_SEED,
    paletteStyle: String = AppSettings.PALETTE_TONAL_SPOT,
    pureBlack: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val darkTheme = when (themeMode) {
        AppSettings.THEME_LIGHT -> false
        AppSettings.THEME_DARK -> true
        else -> isSystemInDarkTheme()
    }
    val expressive = designSystem == AppSettings.DESIGN_EXPRESSIVE
    val systemDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val generated = when {
        systemDynamic && darkTheme -> dynamicDarkColorScheme(context)
        systemDynamic -> dynamicLightColorScheme(context)
        dynamicColor && darkTheme -> FallbackDark
        dynamicColor -> FallbackLight
        else -> createPaletteScheme(
            seed = paletteSeed,
            style = paletteStyle,
            dark = darkTheme,
            pureBlack = pureBlack && darkTheme,
            expressive = expressive
        )
    }
    val colorScheme = if (pureBlack && darkTheme) generated.amoled() else generated

    CompositionLocalProvider(
        LocalExpressiveDesign provides expressive,
        LocalOsmiumMotion provides if (expressive) ExpressiveMotion else StandardMotion
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = if (expressive) ExpressiveTypography else AppTypography,
            shapes = if (expressive) ExpressiveShapes else MaterialShapes,
            content = content
        )
    }
}

/** True-black surfaces for AMOLED panels (keeps the tinted card tiers dark). */
private fun ColorScheme.amoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color.Black,
    surfaceContainer = Color.Black,
    surfaceContainerHigh = Color.Black,
    surfaceContainerHighest = Color.Black
)

/**
 * Palette generation from a seed colour and a KernelSU-style palette style.
 * The expressive design system raises chroma and tints the surface tiers, so
 * the two systems are visibly different without changing any component code.
 */
internal fun createPaletteScheme(
    seed: Long,
    style: String,
    dark: Boolean,
    pureBlack: Boolean,
    expressive: Boolean = false
): ColorScheme {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(seed.toInt(), hsv)
    val hue = hsv[0]
    val styleScale = when (style) {
        AppSettings.PALETTE_NEUTRAL -> 0.16f
        AppSettings.PALETTE_VIBRANT -> 1f
        AppSettings.PALETTE_EXPRESSIVE -> 0.86f
        AppSettings.PALETTE_RAINBOW -> 0.9f
        AppSettings.PALETTE_FRUIT_SALAD -> 0.74f
        AppSettings.PALETTE_MONOCHROME -> 0f
        else -> 0.5f
    }
    val saturation = (
        maxOf(hsv[1], 0.55f) * styleScale * if (expressive) 1.25f else 1f
        ).coerceIn(0f, 1f)

    val secondaryHue = when (style) {
        AppSettings.PALETTE_RAINBOW -> hue + 120f
        AppSettings.PALETTE_FRUIT_SALAD -> hue + 35f
        AppSettings.PALETTE_EXPRESSIVE -> hue + 45f
        else -> hue + 55f
    }
    val tertiaryHue = when (style) {
        AppSettings.PALETTE_RAINBOW -> hue + 240f
        AppSettings.PALETTE_FRUIT_SALAD -> hue - 35f
        AppSettings.PALETTE_EXPRESSIVE -> hue - 45f
        else -> hue + 210f
    }
    fun tone(colorHue: Float, chroma: Float, value: Float) = Color(
        AndroidColor.HSVToColor(
            floatArrayOf(
                (colorHue % 360f + 360f) % 360f,
                chroma.coerceIn(0f, 1f),
                value.coerceIn(0f, 1f)
            )
        )
    )
    fun onColor(background: Color) =
        if (luminance(background) > 0.42) Color(0xFF111318) else Color.White

    val primary = tone(hue, saturation, if (dark) 0.82f else 0.48f)
    val primaryContainer = tone(hue, saturation * 0.65f, if (dark) 0.32f else 0.91f)
    val secondary = tone(secondaryHue, saturation * 0.72f, if (dark) 0.8f else 0.45f)
    val secondaryContainer = tone(secondaryHue, saturation * 0.4f, if (dark) 0.3f else 0.92f)
    val tertiary = tone(tertiaryHue, saturation * 0.75f, if (dark) 0.82f else 0.44f)
    val tertiaryContainer = tone(tertiaryHue, saturation * 0.4f, if (dark) 0.3f else 0.92f)

    // Expressive surfaces carry a hint of the seed hue; standard ones stay neutral.
    val surfaceTintChroma = if (expressive) saturation * 0.07f else 0f
    fun tier(value: Float, fallback: Color): Color =
        if (pureBlack) Color.Black
        else if (expressive) tone(hue, surfaceTintChroma, value)
        else fallback

    val darkTiers = listOf(
        tier(0.07f, Color(0xFF0C0E13)),
        tier(0.11f, Color(0xFF191B20)),
        tier(0.14f, Color(0xFF1D1F24)),
        tier(0.18f, Color(0xFF272A30)),
        tier(0.23f, Color(0xFF32343A))
    )
    val lightTiers = listOf(
        tier(1f, Color.White),
        tier(0.97f, Color(0xFFF7F6FC)),
        tier(0.95f, Color(0xFFF1F0F6)),
        tier(0.93f, Color(0xFFEBEAF0)),
        tier(0.91f, Color(0xFFE5E4EA))
    )
    val background = if (pureBlack) Color.Black else if (dark) Color(0xFF111318) else Color(0xFFF9F9FF)
    val onSurface = if (dark) Color(0xFFE3E2E9) else Color(0xFF191A20)
    val onSurfaceVariant = if (dark) Color(0xFFC5C6D0) else Color(0xFF45464F)
    val surfaceVariant = if (dark) Color(0xFF292B32) else Color(0xFFE4E2EA)
    val outlineVariant = if (dark) Color(0xFF44464F) else Color(0xFFC7C6D0)

    return (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = primary,
        onPrimary = onColor(primary),
        primaryContainer = primaryContainer,
        onPrimaryContainer = onColor(primaryContainer),
        secondary = secondary,
        onSecondary = onColor(secondary),
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onColor(secondaryContainer),
        tertiary = tertiary,
        onTertiary = onColor(tertiary),
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onColor(tertiaryContainer),
        background = background,
        onBackground = onSurface,
        surface = background,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        outlineVariant = outlineVariant,
        surfaceContainerLowest = if (dark) darkTiers[0] else lightTiers[0],
        surfaceContainerLow = if (dark) darkTiers[1] else lightTiers[1],
        surfaceContainer = if (dark) darkTiers[2] else lightTiers[2],
        surfaceContainerHigh = if (dark) darkTiers[3] else lightTiers[3],
        surfaceContainerHighest = if (dark) darkTiers[4] else lightTiers[4]
    )
}

private fun luminance(color: Color): Double {
    fun linear(value: Float): Double {
        val component = value.toDouble()
        return if (component <= 0.04045) component / 12.92
        else Math.pow((component + 0.055) / 1.055, 2.4)
    }
    return 0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)
}
