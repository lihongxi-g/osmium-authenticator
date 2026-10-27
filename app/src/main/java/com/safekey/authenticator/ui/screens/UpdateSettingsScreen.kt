package com.safekey.authenticator.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.safekey.authenticator.BuildConfig
import com.safekey.authenticator.MainViewModel
import com.safekey.authenticator.R
import com.safekey.authenticator.ui.components.AppIcons
import com.safekey.authenticator.ui.components.SettingRow
import com.safekey.authenticator.ui.components.SimpleTopBar

/**
 * Secondary settings page: which update channels to check.
 *
 * It was a single "auto-check for updates" switch in the settings list. With a
 * preview channel added there are two independent choices, so they get their
 * own page instead of two switches stacked under About.
 *
 * Both are off out of the box — the app makes no update request until the user
 * enables a channel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSettingsScreen(
    vm: MainViewModel,
    onBack: () -> Unit
) {
    val settings by vm.settings.collectAsState()

    Scaffold(
        topBar = {
            SimpleTopBar(title = stringResource(R.string.update_settings_title), onBack = onBack)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            SettingRow(
                icon = AppIcons.Refresh,
                title = stringResource(R.string.update_stable_title),
                description = stringResource(R.string.update_stable_desc),
                trailing = {
                    Switch(
                        checked = settings.autoCheckUpdates,
                        onCheckedChange = { vm.setAutoCheckUpdates(it) }
                    )
                }
            )

            SettingRow(
                icon = AppIcons.Science,
                title = stringResource(R.string.update_preview_title),
                description = stringResource(R.string.update_preview_desc),
                trailing = {
                    Switch(
                        checked = settings.checkPreviewUpdates,
                        onCheckedChange = { vm.setCheckPreviewUpdates(it) }
                    )
                }
            )

            Spacer(Modifier.height(16.dp))
            // What the two switches are compared against.
            Text(
                text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }
    }
}
