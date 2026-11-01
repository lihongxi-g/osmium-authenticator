package com.safekey.authenticator.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safekey.authenticator.MainViewModel
import com.safekey.authenticator.R
import com.safekey.authenticator.data.AppSettings
import com.safekey.authenticator.data.LanguagePrefs
import com.safekey.authenticator.ui.components.AppIcons
import com.safekey.authenticator.ui.components.SettingRow
import com.safekey.authenticator.ui.components.SimpleTopBar
import com.safekey.authenticator.ui.navigation.Screen
import com.safekey.authenticator.ui.theme.AppTypography
import com.safekey.authenticator.ui.theme.LocalFontScale
import com.safekey.authenticator.ui.theme.interfaceFontFamily
import com.safekey.authenticator.ui.theme.monospaceFamily
import com.safekey.authenticator.ui.theme.previewHaptic
import com.safekey.authenticator.ui.theme.scaledTo
import kotlin.math.roundToInt

/**
 * Secondary settings pages for appearance.
 *
 * Theme and language used to be modal dialogs inside the Settings list. Both
 * keep growing (more design-system options, more languages), so each gets its
 * own page behind a single entry row: adding a new option no longer means
 * touching the main settings list, and the sub-page can take more rows.
 */
/**
 * Second-level settings page: appearance.
 *
 * The appearance section had grown to six rows inside the main settings list.
 * It is now one entry that opens this page, and each row here opens the page
 * that actually adjusts the setting — so the list keeps growing without
 * pushing the main settings screen off the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(
    vm: MainViewModel,
    hiddenFeatures: Set<String>,
    onBack: () -> Unit
) {
    val settings by vm.settings.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = { SimpleTopBar(title = stringResource(R.string.settings_appearance), onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp)
        ) {
            if ("theme" !in hiddenFeatures) SettingRow(
                icon = if (settings.themeMode == AppSettings.THEME_DARK) AppIcons.DarkMode else AppIcons.LightMode,
                title = stringResource(R.string.theme_mode),
                trailing = {
                    Text(
                        text = when (settings.themeMode) {
                            AppSettings.THEME_LIGHT -> stringResource(R.string.theme_light)
                            AppSettings.THEME_DARK -> stringResource(R.string.theme_dark)
                            else -> stringResource(R.string.theme_system)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onClick = { vm.nav.push(Screen.ThemeSettings) }
            )

            if ("fontFamily" !in hiddenFeatures) SettingRow(
                icon = AppIcons.TextFields,
                title = stringResource(R.string.font_family_title),
                description = stringResource(R.string.font_family_desc),
                trailing = {
                    Text(
                        text = if (settings.fontFamily == AppSettings.FONT_FAMILY_LORA) {
                            stringResource(R.string.font_family_lora)
                        } else {
                            stringResource(R.string.font_family_system)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onClick = { vm.nav.push(Screen.FontFamilySettings) }
            )

            if ("fontSize" !in hiddenFeatures) SettingRow(
                icon = AppIcons.FormatSize,
                title = stringResource(R.string.font_size_title),
                description = stringResource(R.string.font_size_desc),
                trailing = {
                    Text(
                        text = if (settings.fontScaleEnabled) "${settings.fontScalePercent}%"
                        else stringResource(R.string.font_size_off),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onClick = { vm.nav.push(Screen.FontSettings) }
            )

            if ("haptic" !in hiddenFeatures) SettingRow(
                icon = AppIcons.Vibration,
                title = stringResource(R.string.haptic_title),
                trailing = {
                    Text(
                        text = when {
                            settings.hapticEnabled -> "${settings.hapticIntensityPercent}%"
                            else -> stringResource(R.string.haptic_off)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onClick = { vm.nav.push(Screen.HapticSettings) }
            )

            if ("sort" !in hiddenFeatures) SettingRow(
                icon = AppIcons.SwapVert,
                title = stringResource(R.string.sort_mode),
                description = stringResource(R.string.sort_mode_desc),
                onClick = { vm.nav.push(Screen.SortOrder) }
            )

            if ("tags" !in hiddenFeatures) SettingRow(
                icon = AppIcons.Palette,
                title = stringResource(R.string.tags_title),
                description = stringResource(R.string.tags_settings_desc),
                onClick = { vm.nav.push(Screen.TagSettings) }
            )

            if ("language" !in hiddenFeatures) SettingRow(
                icon = AppIcons.Language,
                title = stringResource(R.string.language),
                trailing = {
                    Text(
                        text = when (LanguagePrefs.get(context)) {
                            "zh" -> stringResource(R.string.lang_zh)
                            "zh-TW", "zh-Hant" -> stringResource(R.string.lang_zh_tw)
                            "en" -> stringResource(R.string.lang_en)
                            "es" -> stringResource(R.string.lang_es)
                            "ja" -> stringResource(R.string.lang_ja)
                            "ko" -> stringResource(R.string.lang_ko)
                            "de" -> stringResource(R.string.lang_de)
                            "ru" -> stringResource(R.string.lang_ru)
                            "fr" -> stringResource(R.string.lang_fr)
                            "hi" -> stringResource(R.string.lang_hi)
                            else -> stringResource(R.string.lang_system)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onClick = { vm.nav.push(Screen.LanguageSettings) }
            )
        }
    }
}

/**
 * Third-level settings page: interface typeface.
 *
 * Plain rows, no cards. Each option is drawn in the font it names, so the
 * difference is visible before it is applied. The licence/coverage small print
 * lives one level down, on [FontNotesScreen], to keep this page to a list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontFamilySettingsScreen(
    vm: MainViewModel,
    onBack: () -> Unit
) {
    val settings by vm.settings.collectAsState()
    val sample = stringResource(R.string.font_family_sample)

    Scaffold(
        topBar = { SimpleTopBar(title = stringResource(R.string.font_family_title), onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp)
        ) {
            FONT_CHOICES.forEach { choice ->
                val preview = remember(choice.key, settings.fontWeightBoost) {
                    interfaceFontFamily(choice.key, settings.fontWeightBoost)
                }
                FontChoiceRow(
                    title = stringResource(choice.nameRes),
                    sample = sample,
                    selected = settings.fontFamily == choice.key,
                    family = preview,
                    onClick = { vm.setFontFamily(choice.key) }
                )
            }

            FontWeightRow(
                boost = settings.fontWeightBoost,
                enabled = settings.fontFamily != AppSettings.FONT_FAMILY_SYSTEM,
                onChange = { vm.setFontWeightBoost(it) }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { vm.nav.push(Screen.FontNotes) }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.font_notes_title),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.font_notes_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = AppIcons.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.rotate(180f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Fourth-level page: what the bundled fonts are, which scripts each one covers
 * and under which licence they travel with the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontNotesScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = { SimpleTopBar(title = stringResource(R.string.font_notes_title), onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Text(
                text = stringResource(R.string.font_notes_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )

            FONT_NOTES.forEach { note ->
                Column(modifier = Modifier.padding(vertical = 10.dp)) {
                    Text(
                        text = stringResource(note.nameRes),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    // Resolved one by one: `joinToString` is not inline, so a
                    // composable call inside its lambda would not compile.
                    val scriptLabels = mutableListOf<String>()
                    for (res in note.scripts) scriptLabels.add(stringResource(res))
                    Text(
                        text = FONT_LICENSE + " · " + scriptLabels.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = stringResource(R.string.font_family_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 14.dp, bottom = 28.dp)
            )
        }
    }
}

/**
 * Weight slider: adds 0-150 to every type token. Only the bundled variable
 * faces can do this, so it is disabled (with a hint) on the platform font.
 */
@Composable
private fun FontWeightRow(boost: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    var draft by remember(boost) { mutableFloatStateOf(boost.toFloat()) }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.font_weight_title),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${400 + draft.roundToInt()}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = draft,
            onValueChange = { draft = it },
            onValueChangeFinished = {
                onChange(AppSettings.snapFontWeightBoost(draft.roundToInt()))
            },
            valueRange = AppSettings.FONT_WEIGHT_BOOST_MIN.toFloat()..
                AppSettings.FONT_WEIGHT_BOOST_MAX.toFloat(),
            steps = (AppSettings.FONT_WEIGHT_BOOST_MAX - AppSettings.FONT_WEIGHT_BOOST_MIN) /
                AppSettings.FONT_WEIGHT_BOOST_STEP - 1,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = stringResource(
                if (enabled) R.string.font_weight_desc else R.string.font_weight_system_hint
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** One selectable typeface, previewed in its own face. */
@Composable
private fun FontChoiceRow(
    title: String,
    sample: String,
    selected: Boolean,
    family: FontFamily,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = family),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = sample,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = family),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        RadioButton(selected = selected, onClick = onClick)
    }
}

/** Stored key and label of every interface font choice. */
private class FontChoice(val key: String, val nameRes: Int)

private val FONT_CHOICES: List<FontChoice> = listOf(
    FontChoice(AppSettings.FONT_FAMILY_SYSTEM, R.string.font_family_system),
    FontChoice(AppSettings.FONT_FAMILY_LORA, R.string.font_family_lora),
    FontChoice(AppSettings.FONT_FAMILY_MANROPE, R.string.font_family_manrope),
    FontChoice(AppSettings.FONT_FAMILY_LITERATA, R.string.font_family_literata),
    FontChoice(AppSettings.FONT_FAMILY_HAN_SERIF, R.string.font_family_hanserif)
)

/** Bundled font and the scripts it covers (string resources). */
private class FontNote(val nameRes: Int, val scripts: List<Int>)

/** The licence every bundled face uses; SPDX-ish short name, not translated. */
private const val FONT_LICENSE = "SIL OFL 1.1"

private val FONT_NOTES: List<FontNote> = listOf(
    FontNote(
        R.string.font_family_lora,
        listOf(R.string.font_cover_latin, R.string.font_cover_cyrillic, R.string.font_cover_vietnamese)
    ),
    FontNote(
        R.string.font_family_manrope,
        listOf(
            R.string.font_cover_latin,
            R.string.font_cover_cyrillic,
            R.string.font_cover_greek,
            R.string.font_cover_vietnamese
        )
    ),
    FontNote(
        R.string.font_family_literata,
        listOf(
            R.string.font_cover_latin,
            R.string.font_cover_cyrillic,
            R.string.font_cover_greek,
            R.string.font_cover_vietnamese
        )
    ),
    FontNote(
        R.string.font_family_hanserif,
        listOf(
            R.string.font_cover_latin,
            R.string.font_cover_cyrillic,
            R.string.font_cover_greek,
            R.string.font_cover_han
        )
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen(
    vm: MainViewModel,
    hiddenFeatures: Set<String>,
    onBack: () -> Unit
) {
    val settings by vm.settings.collectAsState()

    Scaffold(
        topBar = { SimpleTopBar(title = stringResource(R.string.theme_mode), onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp)
        ) {
            SettingsChoiceRow(
                title = stringResource(R.string.theme_system),
                selected = settings.themeMode == AppSettings.THEME_SYSTEM,
                onClick = { vm.setThemeMode(AppSettings.THEME_SYSTEM) }
            )
            SettingsChoiceRow(
                title = stringResource(R.string.theme_light),
                selected = settings.themeMode == AppSettings.THEME_LIGHT,
                onClick = { vm.setThemeMode(AppSettings.THEME_LIGHT) }
            )
            SettingsChoiceRow(
                title = stringResource(R.string.theme_dark),
                selected = settings.themeMode == AppSettings.THEME_DARK,
                onClick = { vm.setThemeMode(AppSettings.THEME_DARK) }
            )

            if ("dynamicColor" !in hiddenFeatures) {
                SettingRow(
                    icon = AppIcons.Palette,
                    title = stringResource(R.string.dynamic_color),
                    description = stringResource(R.string.dynamic_color_desc),
                    trailing = {
                        Switch(
                            checked = settings.dynamicColor,
                            onCheckedChange = { vm.setDynamicColor(it) }
                        )
                    }
                )
            }
        }
    }
}

/**
 * Secondary settings page: app language.
 *
 * The choice is applied by recreating the Activity; this page stays on the
 * navigation stack, so the user sees the new language in place instead of being
 * thrown back to the settings list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSettingsScreen(
    onBack: () -> Unit,
    onLanguageChanged: ((String?) -> Unit)? = null
) {
    val context = LocalContext.current
    val current = LanguagePrefs.get(context) ?: LANGUAGE_SYSTEM

    Scaffold(
        topBar = { SimpleTopBar(title = stringResource(R.string.language), onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp)
        ) {
            LANGUAGE_CHOICES.forEach { (tag, labelRes) ->
                SettingsChoiceRow(
                    title = stringResource(labelRes),
                    selected = current == tag,
                    onClick = {
                        // Re-applying the current language would recreate the
                        // Activity for nothing.
                        if (tag != current) {
                            onLanguageChanged?.invoke(tag.takeIf { it != LANGUAGE_SYSTEM })
                        }
                    }
                )
            }
        }
    }
}

/**
 * Secondary settings page: text size.
 *
 * A percent slider rather than named steps: a name can only be a fixed point
 * on the line, and the request was to set the size directly. The sample card
 * is wrapped in its own typography, so dragging previews the result before the
 * value is saved; releasing the slider saves it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontSizeSettingsScreen(
    vm: MainViewModel,
    onBack: () -> Unit
) {
    val settings by vm.settings.collectAsState()
    var draft by remember(settings.fontScalePercent) {
        mutableFloatStateOf(settings.fontScalePercent.toFloat())
    }

    Scaffold(
        topBar = { SimpleTopBar(title = stringResource(R.string.font_size_title), onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp)
        ) {
            SettingRow(
                icon = AppIcons.FormatSize,
                title = stringResource(R.string.font_size_enable),
                description = stringResource(R.string.font_size_enable_desc),
                trailing = {
                    Switch(
                        checked = settings.fontScaleEnabled,
                        onCheckedChange = { vm.setFontScaleEnabled(it) }
                    )
                }
            )

            // Preview at the value being dragged, not the saved one; when the
            // master switch is off the app is at Osmium's default size.
            val draftScale = if (settings.fontScaleEnabled) draft / 100f else 1f
            CompositionLocalProvider(LocalFontScale provides draftScale) {
                MaterialTheme(typography = AppTypography.scaledTo(draftScale)) {
                    FontSizePreview()
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Slider(
                    value = draft,
                    onValueChange = { draft = it },
                    onValueChangeFinished = { vm.setFontScalePercent(draft.roundToInt()) },
                    valueRange = AppSettings.FONT_SCALE_PERCENT_MIN.toFloat()..AppSettings.FONT_SCALE_PERCENT_MAX.toFloat(),
                    steps = (AppSettings.FONT_SCALE_PERCENT_MAX - AppSettings.FONT_SCALE_PERCENT_MIN) /
                        AppSettings.FONT_SCALE_PERCENT_STEP - 1,
                    enabled = settings.fontScaleEnabled,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${draft.roundToInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FONT_PRESETS.forEach { (percent, labelRes) ->
                    FilterChip(
                        selected = draft.roundToInt() == percent,
                        onClick = {
                            draft = percent.toFloat()
                            vm.setFontScalePercent(percent)
                        },
                        enabled = settings.fontScaleEnabled,
                        label = { Text(stringResource(labelRes)) }
                    )
                }
            }

            Text(
                text = stringResource(R.string.font_size_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )
        }
    }
}

/** Sample code + account row, rendered at the step currently in effect. */
@Composable
private fun FontSizePreview() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            // Same 16dp inset as SettingRow: the preview used 8dp and came out
            // wider than the switch card above it.
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "123 456",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = monospaceFamily,
                    letterSpacing = 2.sp * LocalFontScale.current
                ),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "alice@example.com",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** Quick jumps on the slider; the labels are the old step names. */
internal val FONT_PRESETS: List<Pair<Int, Int>> = listOf(
    90 to R.string.font_size_small,
    100 to R.string.font_size_default,
    115 to R.string.font_size_large,
    130 to R.string.font_size_xlarge
)

/**
 * Secondary settings page: vibration feedback.
 *
 * Three controls that must never contradict each other:
 *  - "Follow system" hands haptics to the platform. Turning it on also turns
 *    the custom switch off (one transaction in the repository) and locks the
 *    intensity slider, so the page can never show two "on" states at once.
 *  - The custom switch is the master for our own VibrationEffect.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HapticSettingsScreen(
    vm: MainViewModel,
    onBack: () -> Unit
) {
    val settings by vm.settings.collectAsState()
    var draft by remember(settings.hapticIntensityPercent) {
        mutableFloatStateOf(settings.hapticIntensityPercent.toFloat())
    }
    val sliderEnabled = settings.hapticEnabled
    val context = LocalContext.current

    Scaffold(
        topBar = { SimpleTopBar(title = stringResource(R.string.haptic_title), onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp)
        ) {
            SettingRow(
                icon = AppIcons.Vibration,
                title = stringResource(R.string.haptic_title),
                description = stringResource(R.string.haptic_desc),
                trailing = {
                    Switch(
                        checked = settings.hapticEnabled,
                        onCheckedChange = { vm.setHapticEnabled(it) }
                    )
                }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.haptic_intensity),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${draft.roundToInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Slider(
                value = draft,
                onValueChange = { draft = it },
                onValueChangeFinished = {
                    vm.setHapticIntensityPercent(draft.roundToInt())
                    // Feel the step that was just picked, without leaving the page.
                    previewHaptic(context, draft.roundToInt())
                },
                valueRange = AppSettings.HAPTIC_INTENSITY_MIN.toFloat()..
                    AppSettings.HAPTIC_INTENSITY_MAX.toFloat(),
                steps = (AppSettings.HAPTIC_INTENSITY_MAX - AppSettings.HAPTIC_INTENSITY_MIN) /
                    AppSettings.HAPTIC_INTENSITY_STEP - 1,
                enabled = sliderEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            )

            Text(
                text = stringResource(R.string.haptic_intensity_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }
    }
}

/** Radio-style row shared by the appearance sub-pages. */
@Composable
private fun SettingsChoiceRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        RadioButton(selected = selected, onClick = onClick)
    }
}

/** Stored tag meaning "follow the system"; [LanguagePrefs] keeps null for it. */
private const val LANGUAGE_SYSTEM = "system"

private val LANGUAGE_CHOICES: List<Pair<String, Int>> = listOf(
    LANGUAGE_SYSTEM to R.string.lang_system,
    "zh" to R.string.lang_zh,
    "zh-TW" to R.string.lang_zh_tw,
    "en" to R.string.lang_en,
    "es" to R.string.lang_es,
    "ja" to R.string.lang_ja,
    "ko" to R.string.lang_ko,
    "de" to R.string.lang_de,
    "ru" to R.string.lang_ru,
    "fr" to R.string.lang_fr,
    "hi" to R.string.lang_hi
)
