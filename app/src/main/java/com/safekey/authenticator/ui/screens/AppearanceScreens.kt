package com.safekey.authenticator.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safekey.authenticator.MainViewModel
import com.safekey.authenticator.R
import com.safekey.authenticator.data.AppSettings
import com.safekey.authenticator.data.LanguagePrefs
import com.safekey.authenticator.ui.components.AppIcons
import com.safekey.authenticator.ui.components.SettingRow
import com.safekey.authenticator.ui.components.SimpleTopBar
import com.safekey.authenticator.ui.theme.LocalFontScale
import com.safekey.authenticator.ui.theme.monospaceFamily

/**
 * Secondary settings pages for appearance.
 *
 * Theme and language used to be modal dialogs inside the Settings list. Both
 * keep growing (more design-system options, more languages), so each gets its
 * own page behind a single entry row: adding a new option no longer means
 * touching the main settings list, and the sub-page can take more rows.
 */
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
 * Four named steps instead of a percentage slider: the platform applies a
 * non-linear font-scale curve above 105%, so a "130%" label would not be the
 * 130% the user actually gets. Names stay true at every system setting. The
 * page re-renders at the chosen step, and that is the live preview.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontSizeSettingsScreen(
    vm: MainViewModel,
    onBack: () -> Unit
) {
    val settings by vm.settings.collectAsState()

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
            FontSizePreview()

            FONT_SIZE_CHOICES.forEach { (id, labelRes) ->
                SettingsChoiceRow(
                    title = stringResource(labelRes),
                    selected = settings.fontSize == id,
                    onClick = { vm.setFontSize(id) }
                )
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
            .padding(horizontal = 8.dp, vertical = 8.dp)
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

/** Steps in display order; shared with the settings entry row. */
internal val FONT_SIZE_CHOICES: List<Pair<String, Int>> = listOf(
    AppSettings.FONT_SMALL to R.string.font_size_small,
    AppSettings.FONT_DEFAULT to R.string.font_size_default,
    AppSettings.FONT_LARGE to R.string.font_size_large,
    AppSettings.FONT_XLARGE to R.string.font_size_xlarge
)

/** Label for a stored step id; used as the trailing value on the entry row. */
internal fun fontSizeLabelRes(id: String): Int =
    FONT_SIZE_CHOICES.firstOrNull { it.first == id }?.second ?: R.string.font_size_default

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
