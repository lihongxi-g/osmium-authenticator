package com.safekey.authenticator.ui.screens

import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.safekey.authenticator.MainViewModel
import com.safekey.authenticator.R
import com.safekey.authenticator.data.AppSettings
import com.safekey.authenticator.ui.components.AppIcons
import com.safekey.authenticator.ui.components.SectionHeader
import com.safekey.authenticator.ui.components.SettingRow
import com.safekey.authenticator.ui.components.SimpleTopBar
import com.safekey.authenticator.ui.theme.LocalExpressiveDesign
import com.safekey.authenticator.ui.theme.PaletteVariant
import com.safekey.authenticator.ui.theme.seedColorOptions
import kotlin.math.roundToInt

/** Palette-style names shown in the style dropdown. */
private val PaletteVariant.labelRes: Int
    get() = when (this) {
        PaletteVariant.TONAL_SPOT -> R.string.palette_tonal_spot
        PaletteVariant.NEUTRAL -> R.string.palette_neutral
        PaletteVariant.VIBRANT -> R.string.palette_vibrant
        PaletteVariant.EXPRESSIVE -> R.string.palette_expressive
        PaletteVariant.RAINBOW -> R.string.palette_rainbow
        PaletteVariant.FRUIT_SALAD -> R.string.palette_fruit_salad
        PaletteVariant.MONOCHROME -> R.string.palette_monochrome
        PaletteVariant.FIDELITY -> R.string.palette_fidelity
        PaletteVariant.CONTENT -> R.string.palette_content
    }

/**
 * Appearance subpage: design system, Material You palette style and seed
 * colour, AMOLED, predictive back and UI scale. The structure (style list,
 * key-colour picker, predictive-back switch, UI scale) follows KernelSU's
 * theme settings; the palettes come from Material Color Utilities.
 */
@Composable
fun AppearanceScreen(vm: MainViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsState()
    var customPickerVisible by remember { mutableStateOf(false) }
    val predictiveSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

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
                icon = AppIcons.DarkMode,
                title = stringResource(R.string.dynamic_color),
                description = stringResource(R.string.dynamic_color_desc),
                trailing = {
                    Switch(
                        checked = settings.dynamicColor,
                        onCheckedChange = { vm.setDynamicColor(it) }
                    )
                }
            )
            PaletteStyleRow(
                selectedId = settings.paletteStyle,
                onSelect = vm::setPaletteStyle
            )

            Text(
                text = stringResource(R.string.appearance_seed_color),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            )
            SeedColorRow(
                selectedSeed = settings.paletteSeed,
                dynamicColor = settings.dynamicColor,
                onSelectAuto = { vm.setDynamicColor(true) },
                onSelectSeed = { seed ->
                    vm.setDynamicColor(false)
                    vm.setPaletteSeed(seed)
                },
                onCustom = { customPickerVisible = true }
            )

            SectionHeader(stringResource(R.string.appearance_behavior))
            SettingRow(
                icon = AppIcons.DarkMode,
                title = stringResource(R.string.appearance_amoled),
                description = stringResource(R.string.appearance_amoled_desc),
                trailing = {
                    Switch(
                        checked = settings.pureBlack,
                        onCheckedChange = { vm.setPureBlack(it) }
                    )
                }
            )
            SettingRow(
                icon = AppIcons.ArrowBack,
                title = stringResource(R.string.appearance_predictive_back),
                description = stringResource(
                    if (predictiveSupported) {
                        R.string.appearance_predictive_back_desc
                    } else {
                        R.string.appearance_predictive_back_note
                    }
                ),
                trailing = {
                    Switch(
                        checked = settings.predictiveBack,
                        onCheckedChange = { vm.setPredictiveBack(it) }
                    )
                }
            )
            UiScaleRow(
                scale = settings.uiScale,
                onScale = vm::setUiScale
            )

            Text(
                text = stringResource(R.string.appearance_security_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )
        }
    }

    if (customPickerVisible) {
        CustomColorDialog(
            initial = settings.paletteSeed,
            onConfirm = { seed ->
                vm.setDynamicColor(false)
                vm.setPaletteSeed(seed)
                customPickerVisible = false
            },
            onDismiss = { customPickerVisible = false }
        )
    }
}

/** Grouped card used by the appearance rows. */
@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    val expressive = LocalExpressiveDesign.current
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (expressive) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaletteStyleRow(selectedId: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val current = PaletteVariant.fromId(selectedId)
    SettingsCard {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = AppIcons.Palette,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp, end = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.appearance_palette_style),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(current.labelRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ExposedDropdownMenuDefaults.TrailingIcon(expanded)
            }
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                PaletteVariant.entries.forEach { variant ->
                    DropdownMenuItem(
                        text = { Text(stringResource(variant.labelRes)) },
                        onClick = {
                            onSelect(variant.id)
                            expanded = false
                        },
                        leadingIcon = if (variant == current) {
                            {
                                Icon(
                                    imageVector = AppIcons.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            null
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SeedColorRow(
    selectedSeed: Long,
    dynamicColor: Boolean,
    onSelectAuto: () -> Unit,
    onSelectSeed: (Long) -> Unit,
    onCustom: () -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SwatchButton(
                color = MaterialTheme.colorScheme.primary,
                icon = AppIcons.DarkMode,
                selected = dynamicColor,
                onClick = onSelectAuto
            )
        }
        items(seedColorOptions) { seed ->
            SwatchButton(
                color = Color(seed.toInt()),
                selected = !dynamicColor && seed == selectedSeed,
                onClick = { onSelectSeed(seed) }
            )
        }
        item {
            SwatchButton(
                color = MaterialTheme.colorScheme.surfaceVariant,
                icon = AppIcons.Edit,
                selected = false,
                onClick = onCustom
            )
        }
    }
}

@Composable
private fun SwatchButton(
    color: Color,
    selected: Boolean,
    icon: ImageVector = AppIcons.Check,
    onClick: () -> Unit
) {
    val expressive = LocalExpressiveDesign.current
    val shape = if (expressive) RoundedCornerShape(16.dp) else CircleShape
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(shape)
            .background(color)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = shape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun UiScaleRow(scale: Float, onScale: (Float) -> Unit) {
    SettingsCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = AppIcons.DragIndicator,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp, end = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.appearance_ui_scale),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.appearance_ui_scale_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${(scale * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Slider(
                value = scale,
                onValueChange = onScale,
                valueRange = AppSettings.UI_SCALE_MIN..AppSettings.UI_SCALE_MAX,
                steps = 7
            )
        }
    }
}

@Composable
private fun CustomColorDialog(
    initial: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val start = remember(initial) {
        FloatArray(3).also { AndroidColor.colorToHSV(initial.toInt(), it) }
    }
    var hue by remember(start) { mutableStateOf(start[0]) }
    var saturation by remember(start) { mutableStateOf(start[1]) }
    var brightness by remember(start) { mutableStateOf(start[2]) }
    val preview = Color(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness)))
    val expressive = LocalExpressiveDesign.current
    val shape = if (expressive) RoundedCornerShape(16.dp) else RoundedCornerShape(12.dp)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.appearance_custom_color)) },
        text = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(shape)
                        .background(preview)
                )
                Spacer(Modifier.height(12.dp))
                PickerSlider(
                    label = stringResource(R.string.appearance_hue),
                    value = hue,
                    range = 0f..360f
                ) { hue = it }
                PickerSlider(
                    label = stringResource(R.string.appearance_saturation),
                    value = saturation,
                    range = 0f..1f
                ) { saturation = it }
                PickerSlider(
                    label = stringResource(R.string.appearance_brightness),
                    value = brightness,
                    range = 0.05f..1f
                ) { brightness = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val argb = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, brightness))
                    onConfirm(argb.toLong() and 0xFFFFFFFFL)
                }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun PickerSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range
        )
    }
}

@Composable
private fun DesignSystemCard(selected: String, onSelect: (String) -> Unit) {
    SettingsCard {
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
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }
                )
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
