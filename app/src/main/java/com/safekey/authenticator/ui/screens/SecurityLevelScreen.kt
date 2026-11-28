package com.safekey.authenticator.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.safekey.authenticator.MainViewModel
import com.safekey.authenticator.R
import com.safekey.authenticator.security.BackupEncryptionGate
import com.safekey.authenticator.security.BackupEncryptionLevel
import com.safekey.authenticator.ui.components.SimpleTopBar
import com.safekey.authenticator.ui.components.scrollBelowTopBar
import kotlin.math.roundToInt

/**
 * Second-level page shared by every place that writes an encrypted backup.
 *
 * One stored level, one page: file export, WebDAV and scheduled backup all read
 * [com.safekey.authenticator.data.AppSettings.backupKdfLevel]. The page is a
 * footer link rather than an inline control on each screen, because the
 * strength is a property of the backup file, not of the screen the user
 * happens to be on.
 *
 * Stops the phone cannot run are unreachable: the slider's range ends at
 * [BackupEncryptionGate.highestSupported]. That is a hard requirement, not a
 * hint — writing a 128 MiB backup on a phone that cannot open it again would
 * hand the user a file they can never restore.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityLevelScreen(
    vm: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    val highest = remember(context) { BackupEncryptionGate.highestSupported(context) }
    val stored = BackupEncryptionLevel.fromId(settings.backupKdfLevel)
    val selected = if (stored.ordinal > highest.ordinal) highest else stored
    val totalGb = remember(context) { BackupEncryptionGate.totalMemGb(context) }

    Scaffold(
        topBar = {
            SimpleTopBar(title = stringResource(R.string.security_level_title), onBack = onBack)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .scrollBelowTopBar(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.security_level_intro),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(
                    R.string.security_level_this_phone,
                    totalGb.toString()
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (highest.ordinal > 0) {
                Slider(
                    value = selected.ordinal.toFloat(),
                    onValueChange = { raw ->
                        val level = BackupEncryptionLevel.entries[
                            raw.roundToInt().coerceIn(0, highest.ordinal)
                        ]
                        if (level != stored) vm.setBackupKdfLevel(level.id)
                    },
                    valueRange = 0f..highest.ordinal.toFloat(),
                    steps = (highest.ordinal - 1).coerceAtLeast(0),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    BackupEncryptionLevel.entries.take(highest.ordinal + 1).forEach { level ->
                        Text(
                            text = backupLevelName(level),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (level == selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text(
                text = stringResource(levelDescription(selected)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (stored.ordinal > highest.ordinal) {
                // The saved value came from a phone with more RAM (restored
                // settings, or the user moved the slider before we gated it).
                Text(
                    text = stringResource(R.string.security_level_fallback, backupLevelName(highest)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Text(
                text = stringResource(R.string.backup_kdf_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.security_level_lan_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Localised tier name, shared by the page and the footer rows. */
@Composable
fun backupLevelName(level: BackupEncryptionLevel): String = stringResource(
    when (level) {
        BackupEncryptionLevel.STANDARD -> R.string.backup_kdf_level_standard
        BackupEncryptionLevel.MEDIUM -> R.string.backup_kdf_level_medium
        BackupEncryptionLevel.HIGH -> R.string.backup_kdf_level_high
    }
)

private fun levelDescription(level: BackupEncryptionLevel): Int = when (level) {
    BackupEncryptionLevel.STANDARD -> R.string.security_level_standard_desc
    BackupEncryptionLevel.MEDIUM -> R.string.security_level_medium_desc
    BackupEncryptionLevel.HIGH -> R.string.security_level_high_desc
}
