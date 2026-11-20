package com.safekey.authenticator.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.safekey.authenticator.MainViewModel
import com.safekey.authenticator.R
import com.safekey.authenticator.data.AppSettings
import com.safekey.authenticator.ui.components.AppIcons
import com.safekey.authenticator.ui.components.SettingRow
import com.safekey.authenticator.ui.components.SimpleTopBar

/**
 * One row on the verification page: which action, and whether its identity
 * check can be turned off at all.
 */
private data class VerifyItem(
    val id: String,
    val titleRes: Int,
    val descriptionRes: Int,
    val icon: ImageVector,
    /** Shown as a fixed "always verified" label instead of a switch. */
    val locked: Boolean = false
)

private val VERIFY_ITEMS: List<VerifyItem> = listOf(
    VerifyItem(
        AppSettings.VERIFY_SCREENSHOT, R.string.allow_screenshots, R.string.allow_screenshots_desc,
        AppIcons.Visibility
    ),
    VerifyItem(
        AppSettings.VERIFY_HIDE_CODES, R.string.hide_codes, R.string.hide_codes_desc,
        AppIcons.VisibilityOff
    ),
    VerifyItem(
        AppSettings.VERIFY_EXPORT, R.string.export_vault, R.string.export_vault_desc,
        AppIcons.FileUpload
    ),
    VerifyItem(
        AppSettings.VERIFY_EXPORT_OTHER, R.string.export_other_title, R.string.export_other_desc,
        AppIcons.ImportExport
    ),
    VerifyItem(
        AppSettings.VERIFY_IMPORT, R.string.import_vault, R.string.import_vault_desc,
        AppIcons.FileDownload
    ),
    VerifyItem(
        AppSettings.VERIFY_LAN, R.string.lan_transfer_title, R.string.lan_transfer_desc,
        AppIcons.Wifi
    ),
    VerifyItem(
        AppSettings.VERIFY_WEBDAV, R.string.webdav_title, R.string.webdav_desc,
        AppIcons.Dns
    ),
    VerifyItem(
        AppSettings.VERIFY_AUTO_BACKUP, R.string.auto_backup_title, R.string.auto_backup_desc,
        AppIcons.Timer
    ),
    VerifyItem(
        AppSettings.VERIFY_DEVELOPER, R.string.verify_feature_developer,
        R.string.verify_developer_locked_desc, AppIcons.Security, locked = true
    )
)

/**
 * Second-level page under Settings → Security: which actions still ask for
 * identity verification before they run.
 *
 * The page is a sensitive place in itself (every switch here weakens a check),
 * so it is opened only after a verified identity check and it is unavailable
 * while the device looks compromised — see Screen.isRootBlocked() and the
 * rootRestricted overlay in MainViewModel, which also ignores the opt-outs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerifySettingsScreen(
    vm: MainViewModel,
    hiddenFeatures: Set<String>,
    onBack: () -> Unit
) {
    val settings by vm.settings.collectAsState()
    val rootRestricted by vm.rootRestricted.collectAsState()

    Scaffold(
        topBar = {
            SimpleTopBar(title = stringResource(R.string.verify_settings_title), onBack = onBack)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.verify_settings_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            if (rootRestricted) {
                // Normally unreachable: the nav host leaves this screen as soon
                // as the restriction appears. Kept so a restored state or a
                // late scan cannot leave the switches live.
                Text(
                    text = stringResource(R.string.root_section_locked),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            Spacer(Modifier.height(4.dp))

            VERIFY_ITEMS.forEach { item ->
                if (item.id in hiddenFeatures) return@forEach
                SettingRow(
                    icon = item.icon,
                    title = stringResource(item.titleRes),
                    description = stringResource(item.descriptionRes),
                    trailing = {
                        if (item.locked) {
                            Text(
                                text = stringResource(R.string.verify_always),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Switch(
                                checked = item.id !in settings.verifyOptOut,
                                enabled = !rootRestricted,
                                onCheckedChange = { required ->
                                    vm.setVerifyRequired(item.id, required)
                                }
                            )
                        }
                    }
                )
            }
        }
    }
}
