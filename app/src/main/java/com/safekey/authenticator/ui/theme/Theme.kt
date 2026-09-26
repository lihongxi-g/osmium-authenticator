package com.safekey.authenticator.ui.theme

import android.os.Build
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSp
import androidx.compose.ui.unit.sp
import com.safekey.authenticator.data.AppSettings

/** Used when Material You is on but the platform predates Android 12. */
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
 * Scales the type scale only — layout metrics keep their dp values, so a
 * larger scale grows the text without re-flowing rows off-screen.
 */
private fun Typography.scaled(scale: Float): Typography {
    if (scale == 1f) return this
    fun TextStyle.scaledStyle() = copy(
        fontSize = if (fontSize.isSp) fontSize * scale else fontSize,
        lineHeight = if (lineHeight.isSp) lineHeight * scale else lineHeight
    )
    return copy(
        displayLarge = displayLarge.scaledStyle(),
        displayMedium = displayMedium.scaledStyle(),
        displaySmall = displaySmall.scaledStyle(),
        headlineLarge = headlineLarge.scaledStyle(),
        headlineMedium = headlineMedium.scaledStyle(),
        headlineSmall = headlineSmall.scaledStyle(),
        titleLarge = titleLarge.scaledStyle(),
        titleMedium = titleMedium.scaledStyle(),
        titleSmall = titleSmall.scaledStyle(),
        bodyLarge = bodyLarge.scaledStyle(),
        bodyMedium = bodyMedium.scaledStyle(),
        bodySmall = bodySmall.scaledStyle(),
        labelLarge = labelLarge.scaledStyle(),
        labelMedium = labelMedium.scaledStyle(),
        labelSmall = labelSmall.scaledStyle()
    )
}

/**
 * Selectable design system. Both modes drive the same Material 3 components,
 * business state, navigation and accessibility semantics — only the colour,
 * shape, type, motion and scale tokens differ.
 */
@Composable
fun SafeKeyTheme(
    themeMode: String,
    dynamicColor: Boolean,
    designSystem: String = AppSettings.DESIGN_MATERIAL_3,
    paletteSeed: Long = AppSettings.DEFAULT_PALETTE_SEED,
    paletteStyle: String = AppSettings.PALETTE_TONAL_SPOT,
    pureBlack: Boolean = false,
    uiScale: Float = 1f,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val darkTheme = when (themeMode) {
        AppSettings.THEME_LIGHT -> false
        AppSettings.THEME_DARK -> true
        else -> isSystemInDarkTheme()
    }
    val expressive = designSystem == AppSettings.DESIGN_EXPRESSIVE
    val monetSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = remember(
        dynamicColor, monetSupported, darkTheme, paletteSeed, paletteStyle, pureBlack
    ) {
        when {
            // Material You (wallpaper colours) wins when the platform has it.
            dynamicColor && monetSupported -> {
                val monet = if (darkTheme) {
                    dynamicDarkColorScheme(context)
                } else {
                    dynamicLightColorScheme(context)
                }
                if (pureBlack && darkTheme) monet.amoledSurface() else monet
            }
            dynamicColor -> {
                val fallback = if (darkTheme) FallbackDark else FallbackLight
                if (pureBlack && darkTheme) fallback.amoledSurface() else fallback
            }
            // Seed colour + palette style, generated with Material Color Utilities.
            else -> mcuColorScheme(
                seedArgb = paletteSeed.toInt(),
                variantId = paletteStyle,
                dark = darkTheme,
                contrastLevel = 0.0,
                amoled = pureBlack
            )
        }
    }
    val baseTypography = if (expressive) ExpressiveTypography else AppTypography
    val typography = remember(baseTypography, uiScale) { baseTypography.scaled(uiScale) }
    CompositionLocalProvider(
        LocalExpressiveDesign provides expressive,
        LocalOsmiumMotion provides if (expressive) ExpressiveMotion else StandardMotion
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = if (expressive) ExpressiveShapes else MaterialShapes,
            content = content
        )
    }
}
