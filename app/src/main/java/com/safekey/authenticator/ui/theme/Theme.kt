package com.safekey.authenticator.ui.theme

import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.expressiveDarkColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

/** Selectable Material 3 and expressive-style Material 3 token sets. */
@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    val dynamicScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else null
    val expressiveScheme = if (expressive) {
        if (darkTheme) expressiveDarkColorScheme() else expressiveLightColorScheme()
    } else null
    val colorScheme = when {
        dynamicScheme != null -> dynamicScheme
        dynamicColor && expressiveScheme != null -> expressiveScheme
        dynamicColor && darkTheme -> FallbackDark
        dynamicColor -> FallbackLight
        else -> createPaletteScheme(paletteSeed, paletteStyle, darkTheme, pureBlack && darkTheme)
    }.let { scheme ->
        if (pureBlack && darkTheme) scheme.copy(background = Color.Black, surface = Color.Black)
        else scheme
    }
    val themeContent: @Composable () -> Unit = {
        CompositionLocalProvider(LocalExpressiveDesign provides expressive) {
            if (expressive) {
                MaterialExpressiveTheme(
                    colorScheme = colorScheme,
                    typography = expressiveTypography(),
                    shapes = ExpressiveShapes,
                    motionScheme = MotionScheme.expressive(),
                    content = content
                )
            } else {
                MaterialTheme(
                    colorScheme = colorScheme,
                    typography = AppTypography,
                    content = content
                )
            }
        }
    }
    themeContent()
}

internal fun createPaletteScheme(
    seed: Long,
    style: String,
    dark: Boolean,
    pureBlack: Boolean
): androidx.compose.material3.ColorScheme {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(seed.toInt(), hsv)
    val hue = hsv[0]
    val saturationScale = when (style) {
        AppSettings.PALETTE_NEUTRAL -> 0.18f
        AppSettings.PALETTE_VIBRANT -> 1f
        AppSettings.PALETTE_EXPRESSIVE -> 0.82f
        AppSettings.PALETTE_RAINBOW -> 0.88f
        AppSettings.PALETTE_FRUIT_SALAD -> 0.72f
        AppSettings.PALETTE_MONOCHROME -> 0f
        else -> 0.48f
    }
    val saturation = (maxOf(hsv[1], 0.55f) * saturationScale).coerceIn(0f, 1f)
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
        AndroidColor.HSVToColor(floatArrayOf((colorHue % 360f + 360f) % 360f, chroma, value))
    )
    fun onColor(bg: Color) = if (luminance(bg) > 0.42) Color(0xFF111318) else Color.White

    val primary = tone(hue, saturation, if (dark) 0.78f else 0.58f)
    val primaryContainer = tone(hue, saturation * 0.44f, if (dark) 0.36f else 0.96f)
    val secondary = tone(secondaryHue, saturation * 0.68f, if (dark) 0.72f else 0.48f)
    val secondaryContainer = tone(secondaryHue, saturation * 0.38f, if (dark) 0.32f else 0.95f)
    val tertiary = tone(tertiaryHue, saturation * 0.72f, if (dark) 0.72f else 0.48f)
    val tertiaryContainer = tone(tertiaryHue, saturation * 0.4f, if (dark) 0.32f else 0.95f)
    val background = if (pureBlack) Color.Black else if (dark) Color(0xFF111318) else Color(0xFFF9F9FF)

    return if (dark) darkColorScheme(
        primary = primary, onPrimary = onColor(primary), primaryContainer = primaryContainer,
        onPrimaryContainer = onColor(primaryContainer), secondary = secondary, onSecondary = onColor(secondary),
        secondaryContainer = secondaryContainer, onSecondaryContainer = onColor(secondaryContainer),
        tertiary = tertiary, onTertiary = onColor(tertiary), tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onColor(tertiaryContainer), background = background, surface = background,
        surfaceVariant = Color(0xFF292B32), onSurface = Color(0xFFE3E2E9), onSurfaceVariant = Color(0xFFC5C6D0),
        surfaceContainerLowest = if (pureBlack) Color.Black else Color(0xFF0C0E13),
        surfaceContainerLow = if (pureBlack) Color.Black else Color(0xFF191B20),
        surfaceContainer = if (pureBlack) Color.Black else Color(0xFF1D1F24),
        surfaceContainerHigh = if (pureBlack) Color.Black else Color(0xFF272A30),
        surfaceContainerHighest = if (pureBlack) Color.Black else Color(0xFF32343A)
    ) else lightColorScheme(
        primary = primary, onPrimary = onColor(primary), primaryContainer = primaryContainer,
        onPrimaryContainer = onColor(primaryContainer), secondary = secondary, onSecondary = onColor(secondary),
        secondaryContainer = secondaryContainer, onSecondaryContainer = onColor(secondaryContainer),
        tertiary = tertiary, onTertiary = onColor(tertiary), tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onColor(tertiaryContainer), background = background, surface = background,
        surfaceVariant = Color(0xFFE4E2EA), onSurface = Color(0xFF191A20), onSurfaceVariant = Color(0xFF45464F),
        surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF7F6FC),
        surfaceContainer = Color(0xFFF1F0F6), surfaceContainerHigh = Color(0xFFEBEAF0),
        surfaceContainerHighest = Color(0xFFE5E4EA)
    )
}

private fun luminance(color: Color): Double {
    fun linear(value: Float): Double {
        val component = value.toDouble()
        return if (component <= 0.04045) component / 12.92 else Math.pow((component + 0.055) / 1.055, 2.4)
    }
    return 0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)
}

private fun expressiveTypography() = AppTypography.copy(
    headlineSmall = AppTypography.headlineSmall.copy(fontSize = 28.sp, lineHeight = 36.sp),
    titleLarge = AppTypography.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp)
)
