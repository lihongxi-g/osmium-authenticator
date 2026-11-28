package com.safekey.authenticator.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.safekey.authenticator.MainViewModel
import com.safekey.authenticator.R
import com.safekey.authenticator.icons.InstalledIconPack
import com.safekey.authenticator.ui.components.AppIcons
import com.safekey.authenticator.ui.components.SectionHeader
import com.safekey.authenticator.ui.components.SettingRow
import com.safekey.authenticator.ui.components.SimpleTopBar
import com.safekey.authenticator.ui.components.scrollBelowTopBar

/**
 * Settings → Appearance → Icons.
 *
 * Plain settings rows: the "show icons" switch (off by default), the import
 * row, then one row per installed pack (name, version/icon count, delete).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconSettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val packs by vm.iconPacks.collectAsState()
    val context = LocalContext.current
    var importing by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<InstalledIconPack?>(null) }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importing = true
            vm.installIconPack(uri) { ok, error ->
                importing = false
                if (ok) {
                    vm.showToast(context.getString(R.string.icon_pack_imported))
                } else {
                    vm.showToast(context.getString(R.string.icon_pack_import_failed, error.orEmpty()))
                }
            }
        }
    }

    Scaffold(
        topBar = { SimpleTopBar(title = stringResource(R.string.icon_settings_title), onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .scrollBelowTopBar(padding)
                .padding(horizontal = 8.dp)
        ) {
            SettingRow(
                icon = AppIcons.ImageIcon,
                title = stringResource(R.string.icon_show_title),
                description = stringResource(R.string.icon_show_desc),
                trailing = {
                    Switch(
                        checked = settings.showIcons,
                        onCheckedChange = { vm.setShowIcons(it) }
                    )
                }
            )

            SectionHeader(stringResource(R.string.icon_packs_title))

            SettingRow(
                icon = AppIcons.Add,
                title = stringResource(R.string.icon_pack_import),
                description = stringResource(R.string.icon_pack_import_desc),
                onClick = { if (!importing) importer.launch(arrayOf("*/*")) }
            )

            if (importing) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
                Text(
                    text = stringResource(R.string.icon_pack_importing),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            if (packs.isEmpty()) {
                Text(
                    text = stringResource(R.string.icon_packs_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
                Text(
                    text = stringResource(R.string.icon_packs_empty_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            } else {
                packs.forEach { pack ->
                    SettingRow(
                        icon = AppIcons.Palette,
                        title = pack.name,
                        description = stringResource(
                            R.string.icon_pack_version,
                            pack.version,
                            pack.iconCount
                        ),
                        trailing = {
                            IconButton(onClick = { pendingDelete = pack }) {
                                Icon(
                                    imageVector = AppIcons.Delete,
                                    contentDescription = stringResource(R.string.icon_pack_delete)
                                )
                            }
                        }
                    )
                }

                SettingRow(
                    icon = AppIcons.Refresh,
                    title = stringResource(R.string.icon_auto_assign),
                    description = stringResource(R.string.icon_auto_assign_desc),
                    onClick = { vm.autoAssignIcons() }
                )
            }
        }
    }

    pendingDelete?.let { pack ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.icon_pack_delete_title)) },
            text = { Text(stringResource(R.string.icon_pack_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.removeIconPack(pack)
                    pendingDelete = null
                }) {
                    Text(stringResource(R.string.icon_pack_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
