package com.safekey.authenticator.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.dynamiccolor.DynamicColor
import com.materialkolor.dynamiccolor.MaterialDynamicColors
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.SchemeContent
import com.materialkolor.scheme.SchemeExpressive
import com.materialkolor.scheme.SchemeFidelity
import com.materialkolor.scheme.SchemeFruitSalad
import com.materialkolor.scheme.SchemeMonochrome
import com.materialkolor.scheme.SchemeNeutral
import com.materialkolor.scheme.SchemeRainbow
import com.materialkolor.scheme.SchemeTonalSpot
import com.materialkolor.scheme.SchemeVibrant

/**
 * Palette styles exposed in the appearance settings. The list mirrors the one
 * KernelSU offers in its theme settings; the colours themselves are generated
 * by Material Color Utilities (the `material-color-utilities` Kotlin port that
 * KernelSU's theme is built on), so every style produces a real
 * Material-You-style tonal mapping instead of a hand-tuned approximation.
 */
enum class PaletteVariant(val id: String) {
    TONAL_SPOT("tonal_spot"),
    NEUTRAL("neutral"),
    VIBRANT("vibrant"),
    EXPRESSIVE("expressive"),
    RAINBOW("rainbow"),
    FRUIT_SALAD("fruit_salad"),
    MONOCHROME("monochrome"),
    FIDELITY("fidelity"),
    CONTENT("content");

    companion object {
        fun fromId(id: String?): PaletteVariant =
            entries.firstOrNull { it.id == id } ?: TONAL_SPOT
    }
}

/**
 * Builds the MCU dynamic scheme for a seed colour. Each style re-maps the
 * seed's hue/chroma onto its own tonal palettes (this is what makes Vibrant,
 * Rainbow, Monochrome, … look different from TonalSpot for the same seed).
 */
private fun dynamicScheme(
    seedArgb: Int,
    variant: PaletteVariant,
    dark: Boolean,
    contrastLevel: Double
): DynamicScheme {
    val seed = Hct.fromInt(seedArgb)
    return when (variant) {
        PaletteVariant.TONAL_SPOT -> SchemeTonalSpot(seed, dark, contrastLevel)
        PaletteVariant.NEUTRAL -> SchemeNeutral(seed, dark, contrastLevel)
        PaletteVariant.VIBRANT -> SchemeVibrant(seed, dark, contrastLevel)
        PaletteVariant.EXPRESSIVE -> SchemeExpressive(seed, dark, contrastLevel)
        PaletteVariant.RAINBOW -> SchemeRainbow(seed, dark, contrastLevel)
        PaletteVariant.FRUIT_SALAD -> SchemeFruitSalad(seed, dark, contrastLevel)
        PaletteVariant.MONOCHROME -> SchemeMonochrome(seed, dark, contrastLevel)
        PaletteVariant.FIDELITY -> SchemeFidelity(seed, dark, contrastLevel)
        PaletteVariant.CONTENT -> SchemeContent(seed, dark, contrastLevel)
    }
}

/**
 * Generates the full Compose Material 3 colour scheme from a seed colour.
 * Any failure (an unexpected seed, a library change) falls back to the
 * baseline Material 3 scheme rather than crashing the UI.
 */
internal fun mcuColorScheme(
    seedArgb: Int,
    variantId: String?,
    dark: Boolean,
    contrastLevel: Double = 0.0,
    amoled: Boolean = false
): ColorScheme {
    val fallback = if (dark) darkColorScheme() else lightColorScheme()
    val generated = runCatching {
        val scheme = dynamicScheme(seedArgb, PaletteVariant.fromId(variantId), dark, contrastLevel)
        val colors = MaterialDynamicColors()
        fun role(value: DynamicColor) = Color(value.getArgb(scheme))
        fallback.copy(
            primary = role(colors.primary()),
            onPrimary = role(colors.onPrimary()),
            primaryContainer = role(colors.primaryContainer()),
            onPrimaryContainer = role(colors.onPrimaryContainer()),
            inversePrimary = role(colors.inversePrimary()),
            secondary = role(colors.secondary()),
            onSecondary = role(colors.onSecondary()),
            secondaryContainer = role(colors.secondaryContainer()),
            onSecondaryContainer = role(colors.onSecondaryContainer()),
            tertiary = role(colors.tertiary()),
            onTertiary = role(colors.onTertiary()),
            tertiaryContainer = role(colors.tertiaryContainer()),
            onTertiaryContainer = role(colors.onTertiaryContainer()),
            background = role(colors.background()),
            onBackground = role(colors.onBackground()),
            surface = role(colors.surface()),
            onSurface = role(colors.onSurface()),
            surfaceVariant = role(colors.surfaceVariant()),
            onSurfaceVariant = role(colors.onSurfaceVariant()),
            surfaceTint = role(colors.surfaceTint()),
            inverseSurface = role(colors.inverseSurface()),
            inverseOnSurface = role(colors.inverseOnSurface()),
            error = role(colors.error()),
            onError = role(colors.onError()),
            errorContainer = role(colors.errorContainer()),
            onErrorContainer = role(colors.onErrorContainer()),
            outline = role(colors.outline()),
            outlineVariant = role(colors.outlineVariant()),
            scrim = role(colors.scrim()),
            surfaceBright = role(colors.surfaceBright()),
            surfaceDim = role(colors.surfaceDim()),
            surfaceContainer = role(colors.surfaceContainer()),
            surfaceContainerHigh = role(colors.surfaceContainerHigh()),
            surfaceContainerHighest = role(colors.surfaceContainerHighest()),
            surfaceContainerLow = role(colors.surfaceContainerLow()),
            surfaceContainerLowest = role(colors.surfaceContainerLowest())
        )
    }.getOrNull()
    val scheme = generated ?: fallback
    return if (amoled && dark) scheme.amoledSurface() else scheme
}

/**
 * True-black surfaces for AMOLED panels. Only the surface roles are cleared —
 * text/outline roles keep their MCU values so contrast stays intact.
 */
internal fun ColorScheme.amoledSurface(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color.Black,
    surfaceContainer = Color.Black,
    surfaceContainerHigh = Color.Black,
    surfaceContainerHighest = Color.Black
)

/**
 * Seed colours offered in the appearance settings (same set KernelSU ships).
 */
internal val seedColorOptions: List<Long> = listOf(
    0xFFF44336L, 0xFFE91E63L, 0xFF9C27B0L, 0xFF673AB7L, 0xFF3F51B5L,
    0xFF2196F3L, 0xFF00BCD4L, 0xFF009688L, 0xFF4FAF50L, 0xFFFFEB3BL,
    0xFFFFC107L, 0xFFFF9800L, 0xFF795548L, 0xFF607D8FL, 0xFFFF9CA8L
)
