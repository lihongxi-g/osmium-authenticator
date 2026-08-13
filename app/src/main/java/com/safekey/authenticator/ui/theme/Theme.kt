package com.safekey.authenticator.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.safekey.authenticator.data.AppSettings

/**
 * App theme: system/light/dark mode + Material You dynamic color (Android 12+)
 * or one of the seven preset colors (红橙黄绿青蓝紫), each with light/dark
 * variants defined in ThemePresets.kt.
 */
@Composable
fun SafeKeyTheme(
    themeMode: String,
    dynamicColor: Boolean,
    themeColorIndex: Int,
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
        else -> {
            val preset = themePresets.getOrElse(themeColorIndex) { themePresets[3] }
            preset.scheme(darkTheme)
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
