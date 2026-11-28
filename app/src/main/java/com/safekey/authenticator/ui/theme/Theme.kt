package com.safekey.authenticator.ui.theme

import android.os.Build
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.safekey.authenticator.data.AppSettings

// Fallback palette (no Dynamic Color support, i.e. Android < 12):
// Material 3 blue primary family on standard white / near-black backgrounds.
private val FallbackLight = lightColorScheme(
    primary = Color(0xFF005AC1),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD7E3FF),
    onPrimaryContainer = Color(0xFF001B3F)
)
private val FallbackDark = darkColorScheme(
    primary = Color(0xFFADC6FF),
    onPrimary = Color(0xFF002E69),
    primaryContainer = Color(0xFF004494),
    onPrimaryContainer = Color(0xFFD7E3FF)
)

/** Baseline Material 3 shape scale — what the app shipped with. */
private val StandardShapes = Shapes()

/**
 * Material 3 Expressive shape scale.
 *
 * Every step is noticeably rounder than the baseline, so cards, sheets,
 * dialogs, menus and settings rows pick up the expressive silhouette through
 * `MaterialTheme.shapes` alone — layout metrics stay exactly where they were.
 */
private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(30.dp),
    extraLarge = RoundedCornerShape(38.dp)
)

/**
 * True when the expressive design system is selected.
 *
 * Read by the few components whose expressive treatment is more than a token
 * swap (the settings icon bubbles, the page transitions).
 */
val LocalExpressiveDesign = staticCompositionLocalOf { false }

/**
 * Heavier heading tokens on top of the user's font weight.
 *
 * A relative step (not an absolute weight) so the font-weight setting still
 * does something in expressive mode, and the face's available weights are
 * never overrun.
 */
private fun Typography.expressiveEmphasis(): Typography {
    fun TextStyle.bolder(step: Int): TextStyle = copy(
        fontWeight = FontWeight(
            ((fontWeight ?: FontWeight.Normal).weight + step).coerceAtMost(900)
        )
    )
    return copy(
        headlineSmall = headlineSmall.bolder(200),
        titleLarge = titleLarge.bolder(200),
        titleMedium = titleMedium.bolder(100),
        labelLarge = labelLarge.bolder(100)
    )
}

/**
 * Theme strategy (user decision): Dynamic Color from the wallpaper when
 * available (Android 12+); otherwise a blue-on-white / blue-on-black scheme.
 * No custom background colors.
 */
@Composable
fun SafeKeyTheme(
    themeMode: String,
    dynamicColor: Boolean,
    designSystem: String = AppSettings.DESIGN_MATERIAL_3,
    fontScaleEnabled: Boolean = false,
    fontScalePercent: Int = AppSettings.FONT_SCALE_PERCENT_DEFAULT,
    fontFamily: String = AppSettings.FONT_FAMILY_SYSTEM,
    fontWeightBody: Int = AppSettings.FONT_WEIGHT_BODY_DEFAULT,
    hapticEnabled: Boolean = true,
    hapticIntensityPercent: Int = AppSettings.HAPTIC_INTENSITY_DEFAULT,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val darkTheme = when (themeMode) {
        AppSettings.THEME_LIGHT -> false
        AppSettings.THEME_DARK -> true
        else -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> FallbackDark
        else -> FallbackLight
    }

    // App text step, saturated against the system scale. The PIN keypad is a
    // fixed 72dp circle and its three-column grid cannot grow sideways, so the
    // text has to stop at a combined 2.0 instead of clipping.
    val systemFontScale = LocalDensity.current.fontScale
    val fontScale = remember(fontScaleEnabled, fontScalePercent, systemFontScale) {
        AppSettings.effectiveFontScale(fontScaleEnabled, fontScalePercent, systemFontScale)
    }
    val typeface = remember(fontFamily) { interfaceFontFamily(fontFamily) }
    // The slider moves the type tokens, not a font axis: the bundled faces ship
    // fixed weight files, so the change always lands on real glyphs.
    val weightDelta = remember(fontFamily, fontWeightBody) {
        AppSettings.fontWeightDelta(fontWeightBody, fontFamily)
    }
    val expressive = designSystem == AppSettings.DESIGN_EXPRESSIVE
    val typography = remember(fontScale, typeface, weightDelta, expressive) {
        val base = AppTypography.scaledTo(fontScale).withFamily(typeface).withWeightDelta(weightDelta)
        if (expressive) base.expressiveEmphasis() else base
    }
    val hapticTick = rememberHapticTick(hapticEnabled, hapticIntensityPercent)
    val latestTick = rememberUpdatedState(hapticTick)

    CompositionLocalProvider(
        LocalFontScale provides fontScale,
        LocalExpressiveDesign provides expressive
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = if (expressive) ExpressiveShapes else StandardShapes
        ) {
            // Clickable surfaces get the tick through the indication, so only
            // real controls vibrate (buttons, switches, rows, the keypad,
            // dialog actions) while the ripple keeps drawing exactly as before.
            val base = ripple()
            val indication = remember(base, latestTick) {
                HapticIndication({ latestTick.value() }, base)
            }
            CompositionLocalProvider(LocalIndication provides indication) {
                content()
            }
        }
    }
}

// Fixed "yes, this is granted / allowed" green.
//
// Deliberately NOT a theme role: `tertiary` follows the wallpaper under Dynamic
// Color, and on Android 12+ that came out yellow-olive on the user's device, so
// a granted permission read as a warning. Fixed tones keep "ok" green in every
// theme, light or dark.
private val SuccessOnLight = Color(0xFF1B6B33)
private val SuccessOnDark = Color(0xFF7DD495)

/** Green for positive status text; picks the tone that reads on this theme. */
@Composable
fun successContentColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) SuccessOnDark
    else SuccessOnLight
