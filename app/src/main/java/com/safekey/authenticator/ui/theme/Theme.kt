package com.safekey.authenticator.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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

/**
 * Theme strategy (user decision): Dynamic Color from the wallpaper when
 * available (Android 12+); otherwise a blue-on-white / blue-on-black scheme.
 * No custom background colors.
 */
@Composable
fun SafeKeyTheme(
    themeMode: String,
    dynamicColor: Boolean,
    fontScaleEnabled: Boolean = false,
    fontScalePercent: Int = AppSettings.FONT_SCALE_PERCENT_DEFAULT,
    hapticEnabled: Boolean = true,
    hapticFollowSystem: Boolean = false,
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
    val typography = remember(fontScale) { AppTypography.scaledTo(fontScale) }
    val hapticTick = rememberHapticTick(hapticEnabled, hapticFollowSystem, hapticIntensityPercent)

    CompositionLocalProvider(
        LocalFontScale provides fontScale,
        LocalHapticTick provides hapticTick
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
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
