package com.safekey.authenticator.ui.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.safekey.authenticator.MainViewModel
import com.safekey.authenticator.R
import com.safekey.authenticator.ui.components.AppIcons
import com.safekey.authenticator.ui.components.SectionHeader
import com.safekey.authenticator.ui.components.SettingRow
import com.safekey.authenticator.ui.components.SimpleTopBar

/** Second-level tag settings page: feature switch plus tag management entry. */
@Composable
fun TagSettingsScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onManageTags: () -> Unit
) {
    val settings by vm.settings.collectAsState()

    Scaffold(
        topBar = { SimpleTopBar(stringResource(R.string.tags_title), onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SectionHeader(stringResource(R.string.tags_settings_section))
            SettingRow(
                icon = AppIcons.Palette,
                title = stringResource(R.string.tags_feature),
                description = stringResource(R.string.tags_feature_desc),
                trailing = {
                    Switch(
                        checked = settings.tagsEnabled,
                        onCheckedChange = { enabled -> vm.setTagsEnabled(enabled) }
                    )
                }
            )
            if (settings.tagsEnabled) {
                SettingRow(
                    icon = AppIcons.Palette,
                    title = stringResource(R.string.tags_manage),
                    description = stringResource(R.string.tags_manage_desc),
                    onClick = onManageTags
                )
            }
        }
    }
}
