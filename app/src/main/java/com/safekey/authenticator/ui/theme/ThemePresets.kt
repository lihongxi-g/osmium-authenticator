package com.safekey.authenticator.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.safekey.authenticator.R

/**
 * Seven preset theme colors (红橙黄绿青蓝紫), each with its own light/dark
 * primary-family override on top of the neutral M3 baseline.
 * Selected via Settings; only applies when Dynamic Color is off.
 */
data class ThemePreset(
    val nameResKey: Int,
    val color: Color,
    val light: ColorScheme,
    val dark: ColorScheme
) {
    fun scheme(darkTheme: Boolean): ColorScheme = if (darkTheme) dark else light
}

val themePresets: List<ThemePreset> = listOf(
    preset(
        nameResKey = R.string.color_red,
        accent = Color(0xFFB3261E),
        light = lightColorScheme(
            primary = Color(0xFFB3261E), onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFF9DEDC), onPrimaryContainer = Color(0xFF410E0B)
        ),
        dark = darkColorScheme(
            primary = Color(0xFFF2B8B5), onPrimary = Color(0xFF601410),
            primaryContainer = Color(0xFF8C1D18), onPrimaryContainer = Color(0xFFF9DEDC)
        )
    ),
    preset(
        nameResKey = R.string.color_orange,
        accent = Color(0xFF8B5000),
        light = lightColorScheme(
            primary = Color(0xFF8B5000), onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFFFDCC2), onPrimaryContainer = Color(0xFF2D1600)
        ),
        dark = darkColorScheme(
            primary = Color(0xFFFFB77C), onPrimary = Color(0xFF4E2800),
            primaryContainer = Color(0xFF6F3C00), onPrimaryContainer = Color(0xFFFFDCC2)
        )
    ),
    preset(
        nameResKey = R.string.color_yellow,
        accent = Color(0xFF6A5E00),
        light = lightColorScheme(
            primary = Color(0xFF6A5E00), onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFEFE500), onPrimaryContainer = Color(0xFF1F1C00)
        ),
        dark = darkColorScheme(
            primary = Color(0xFFD3C500), onPrimary = Color(0xFF373400),
            primaryContainer = Color(0xFF4F4700), onPrimaryContainer = Color(0xFFEFE500)
        )
    ),
    preset(
        nameResKey = R.string.color_green,
        accent = Color(0xFF386A20),
        light = lightColorScheme(
            primary = Color(0xFF386A20), onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFB7F397), onPrimaryContainer = Color(0xFF072100)
        ),
        dark = darkColorScheme(
            primary = Color(0xFF9CD67E), onPrimary = Color(0xFF0F3900),
            primaryContainer = Color(0xFF205107), onPrimaryContainer = Color(0xFFB7F397)
        )
    ),
    preset(
        nameResKey = R.string.color_cyan,
        accent = Color(0xFF006A6A),
        light = lightColorScheme(
            primary = Color(0xFF006A6A), onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFF6FF7F7), onPrimaryContainer = Color(0xFF002020)
        ),
        dark = darkColorScheme(
            primary = Color(0xFF4CDADC), onPrimary = Color(0xFF003737),
            primaryContainer = Color(0xFF004F4F), onPrimaryContainer = Color(0xFF6FF7F7)
        )
    ),
    preset(
        nameResKey = R.string.color_blue,
        accent = Color(0xFF005AC1),
        light = lightColorScheme(
            primary = Color(0xFF005AC1), onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFD7E3FF), onPrimaryContainer = Color(0xFF001B3F)
        ),
        dark = darkColorScheme(
            primary = Color(0xFFADC6FF), onPrimary = Color(0xFF002E69),
            primaryContainer = Color(0xFF004494), onPrimaryContainer = Color(0xFFD7E3FF)
        )
    ),
    preset(
        nameResKey = R.string.color_purple,
        accent = Color(0xFF6750A4),
        light = lightColorScheme(
            primary = Color(0xFF6750A4), onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFEADDFF), onPrimaryContainer = Color(0xFF21005D)
        ),
        dark = darkColorScheme(
            primary = Color(0xFFD0BCFF), onPrimary = Color(0xFF381E72),
            primaryContainer = Color(0xFF4F378B), onPrimaryContainer = Color(0xFFEADDFF)
        )
    )
)

private fun preset(
    nameResKey: Int,
    accent: Color,
    light: ColorScheme,
    dark: ColorScheme
) = ThemePreset(nameResKey, accent, light, dark)
