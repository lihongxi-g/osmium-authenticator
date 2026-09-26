package com.safekey.authenticator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.safekey.authenticator.MainViewModel
import com.safekey.authenticator.R
import com.safekey.authenticator.data.AppSettings
import com.safekey.authenticator.ui.components.AppIcons
import com.safekey.authenticator.ui.components.SectionHeader
import com.safekey.authenticator.ui.components.SettingRow
import com.safekey.authenticator.ui.components.SimpleTopBar
import com.safekey.authenticator.ui.theme.LocalExpressiveDesign

private data class SeedOption(val color: Color, val seed: Long)

private val seedOptions = listOf(
    SeedOption(Color(0xFF4F5D92), 0xFF4F5D92L),
    SeedOption(Color(0xFF006A6A), 0xFF006A6AL),
    SeedOption(Color(0xFF7C4D00), 0xFF7C4D00L),
    SeedOption(Color(0xFF8B4057), 0xFF8B4057L),
    SeedOption(Color(0xFF496A20), 0xFF496A20L),
    SeedOption(Color(0xFF675080), 0xFF675080L)
)

private data class StyleOption(val key: String, val label: Int)

private val styles = listOf(
    StyleOption(AppSettings.PALETTE_TONAL_SPOT, R.string.palette_tonal_spot),
    StyleOption(AppSettings.PALETTE_NEUTRAL, R.string.palette_neutral),
    StyleOption(AppSettings.PALETTE_VIBRANT, R.string.palette_vibrant),
    StyleOption(AppSettings.PALETTE_EXPRESSIVE, R.string.palette_expressive),
    StyleOption(AppSettings.PALETTE_RAINBOW, R.string.palette_rainbow),
    StyleOption(AppSettings.PALETTE_FRUIT_SALAD, R.string.palette_fruit_salad),
    StyleOption(AppSettings.PALETTE_MONOCHROME, R.string.palette_monochrome)
)

@Composable
fun AppearanceScreen(vm: MainViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val expressive = LocalExpressiveDesign.current

    Scaffold(
        topBar = { SimpleTopBar(stringResource(R.string.appearance_title), onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            SectionHeader(stringResource(R.string.appearance_design_system))
            DesignSystemCard(
                selected = settings.designSystem,
                onSelect = vm::setDesignSystem
            )

            SectionHeader(stringResource(R.string.appearance_colors))
            SettingRow(
                icon = AppIcons.Palette,
                title = stringResource(R.string.dynamic_color),
                description = stringResource(R.string.dynamic_color_desc),
                trailing = {
                    Switch(
                        checked = settings.dynamicColor,
                        onCheckedChange = vm::setDynamicColor
                    )
                }
            )

            if (!settings.dynamicColor) {
                Text(
                    text = stringResource(R.string.appearance_seed_color),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    seedOptions.forEach { option ->
                        SeedButton(
                            option = option,
                            selected = option.seed == settings.paletteSeed,
                            radius = if (expressive) 16.dp else 999.dp,
                            onClick = { vm.setPaletteSeed(option.seed) }
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.appearance_palette_style),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    styles.forEach { style ->
                        FilterChip(
                            selected = settings.paletteStyle == style.key,
                            onClick = { vm.setPaletteStyle(style.key) },
                            label = { Text(stringResource(style.label)) },
                            shape = if (expressive) {
                                RoundedCornerShape(16.dp)
                            } else {
                                MaterialTheme.shapes.small
                            }
                        )
                    }
                }
            }

            if (expressive) {
                SectionHeader(stringResource(R.string.appearance_expressive_options))
                SettingRow(
                    icon = AppIcons.DarkMode,
                    title = stringResource(R.string.appearance_amoled),
                    description = stringResource(R.string.appearance_amoled_desc),
                    trailing = {
                        Switch(
                            checked = settings.pureBlack,
                            onCheckedChange = vm::setPureBlack
                        )
                    }
                )
            }

            Text(
                text = stringResource(R.string.appearance_security_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )
        }
    }
}

@Composable
private fun DesignSystemCard(selected: String, onSelect: (String) -> Unit) {
    val expressive = LocalExpressiveDesign.current
    Surface(
        shape = if (expressive) MaterialTheme.shapes.extraLarge else MaterialTheme.shapes.large,
        color = if (expressive) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            DesignOption(
                title = stringResource(R.string.appearance_material3),
                description = stringResource(R.string.appearance_material3_desc),
                selected = selected == AppSettings.DESIGN_MATERIAL_3,
                onClick = { onSelect(AppSettings.DESIGN_MATERIAL_3) }
            )
            Spacer(Modifier.height(8.dp))
            DesignOption(
                title = stringResource(R.string.appearance_expressive),
                description = stringResource(R.string.appearance_expressive_desc),
                selected = selected == AppSettings.DESIGN_EXPRESSIVE,
                onClick = { onSelect(AppSettings.DESIGN_EXPRESSIVE) }
            )
        }
    }
}

@Composable
private fun DesignOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val expressive = LocalExpressiveDesign.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(if (expressive) MaterialTheme.shapes.large else MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SeedButton(option: SeedOption, selected: Boolean, radius: Dp, onClick: () -> Unit) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(shape)
            .background(option.color)
            .clickable(onClick = onClick)
            .padding(if (selected) 4.dp else 0.dp)
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
            )
        }
    }
}
