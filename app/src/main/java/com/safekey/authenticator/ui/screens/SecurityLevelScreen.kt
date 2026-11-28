package com.safekey.authenticator.ui.screens

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.safekey.authenticator.MainViewModel
import com.safekey.authenticator.R
import com.safekey.authenticator.security.BackupEncryptionGate
import com.safekey.authenticator.security.BackupEncryptionLevel
import com.safekey.authenticator.ui.components.AppIcons
import com.safekey.authenticator.ui.components.SettingLinkRow
import com.safekey.authenticator.ui.components.SimpleTopBar
import com.safekey.authenticator.ui.components.scrollBelowTopBar
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Settings → Security → Security level.
 *
 * One slider per write path, because the right trade-off differs: a scheduled
 * backup is restored on whatever phone is at hand, while a one-off file export
 * can be deliberately written for a stronger phone. Restoring never reads
 * these settings — the parameters stored in the file are the only input.
 *
 * Stops this phone cannot run are unreachable: each slider's range ends at
 * [BackupEncryptionGate.highestSupported]. Writing a backup this phone cannot
 * open again would hand the user a file they can never restore.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityLevelScreen(
    vm: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    val ignore = settings.ignoreBackupMemoryLimit
    val highest = remember(context, ignore) {
        BackupEncryptionGate.highestSupported(context, ignore)
    }
    var showMemoryDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            SimpleTopBar(title = stringResource(R.string.security_level_title), onBack = onBack)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .scrollBelowTopBar(padding)
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            LevelSection(
                title = stringResource(R.string.security_level_export),
                stored = BackupEncryptionLevel.fromId(settings.backupKdfExportLevel),
                highest = highest,
                onChange = { vm.setBackupExportLevel(it.id) }
            )
            LevelSection(
                title = stringResource(R.string.security_level_webdav),
                stored = BackupEncryptionLevel.fromId(settings.backupKdfWebDavLevel),
                highest = highest,
                onChange = { vm.setBackupWebDavLevel(it.id) }
            )
            LevelSection(
                title = stringResource(R.string.security_level_autobackup),
                stored = BackupEncryptionLevel.fromId(settings.backupKdfAutoLevel),
                highest = highest,
                onChange = { vm.setBackupAutoLevel(it.id) }
            )

            SettingLinkRow(
                icon = AppIcons.Info,
                title = stringResource(R.string.security_level_memory_link),
                value = "",
                onClick = {
                    // System page when this build has one; otherwise the
                    // numbers are read directly, which always works.
                    if (!openRunningServices(context)) showMemoryDialog = true
                },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            Text(
                text = stringResource(R.string.backup_kdf_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }

    if (showMemoryDialog) {
        MemoryUsageDialog(onDismiss = { showMemoryDialog = false })
    }
}

/**
 * Fallback when the OEM build has no reachable Running-services screen:
 * the same numbers, read from ActivityManager once a second.
 */
@Composable
private fun MemoryUsageDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val snapshot by produceState(initialValue = readMemory(context), context) {
        while (true) {
            value = readMemory(context)
            delay(1_000)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.memory_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.memory_total, formatBytes(snapshot.totalBytes)))
                Text(stringResource(R.string.memory_available, formatBytes(snapshot.availableBytes)))
                Text(
                    stringResource(
                        R.string.memory_app_heap,
                        formatBytes(snapshot.heapUsedBytes),
                        formatBytes(snapshot.heapLimitBytes)
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    )
}

private data class MemorySnapshot(
    val totalBytes: Long,
    val availableBytes: Long,
    val heapUsedBytes: Long,
    val heapLimitBytes: Long
)

private fun readMemory(context: Context): MemorySnapshot {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val info = ActivityManager.MemoryInfo()
    am?.getMemoryInfo(info)
    val runtime = Runtime.getRuntime()
    return MemorySnapshot(
        totalBytes = info.totalMem,
        availableBytes = info.availMem,
        heapUsedBytes = runtime.totalMemory() - runtime.freeMemory(),
        heapLimitBytes = runtime.maxMemory()
    )
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f GB", bytes / 1024.0 / 1024 / 1024)
    else -> String.format(Locale.US, "%.0f MB", bytes / 1024.0 / 1024)
}

@Composable
private fun LevelSection(
    title: String,
    stored: BackupEncryptionLevel,
    highest: BackupEncryptionLevel,
    onChange: (BackupEncryptionLevel) -> Unit
) {
    // A value carried over from a phone with more RAM is shown at the strongest
    // level this one can actually run; nothing is written back until the user
    // moves the slider.
    val selected = if (stored.ordinal > highest.ordinal) highest else stored

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = backupLevelName(selected),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (highest.ordinal > 0) {
            Slider(
                value = selected.ordinal.toFloat(),
                onValueChange = { raw ->
                    val level = BackupEncryptionLevel.entries[
                        raw.roundToInt().coerceIn(0, highest.ordinal)
                    ]
                    if (level != stored) onChange(level)
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
        } else {
            Text(
                text = stringResource(R.string.security_level_locked),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Localised tier name, shared by every screen that shows a level. */
@Composable
fun backupLevelName(level: BackupEncryptionLevel): String = stringResource(
    when (level) {
        BackupEncryptionLevel.STANDARD -> R.string.backup_kdf_level_standard
        BackupEncryptionLevel.MEDIUM -> R.string.backup_kdf_level_medium
        BackupEncryptionLevel.HIGH -> R.string.backup_kdf_level_high
    }
)

/**
 * Opens the "Running services" screen under Developer options.
 *
 * There is no public action for it, so the known Settings activity names are
 * tried in order (AOSP and the path used by recent releases). When none
 * resolves — some OEM skins drop or rename it — the app's own details page is
 * opened instead, which still shows live memory use.
 */
private fun openRunningServices(context: Context): Boolean {
    val settingsPackage = "com.android.settings"
    val fragment = ":settings:show_fragment"

    // AOSP hosts Running services as a fragment inside SubSettings, so the
    // supported way in is the fragment extra — not an activity name. Every
    // entry is tried in order; a wrong guess is a no-op, not a crash.
    val intents = buildList {
        // Current AOSP: com.android.settings.applications.RunningServices.
        add(
            Intent()
                .setComponent(ComponentName(settingsPackage, "$settingsPackage.SubSettings"))
                .putExtra(fragment, "$settingsPackage.applications.RunningServices")
        )
        // Older AOSP kept the fragment one package up.
        add(
            Intent()
                .setComponent(ComponentName(settingsPackage, "$settingsPackage.SubSettings"))
                .putExtra(fragment, "$settingsPackage.RunningServices")
        )
        // Builds that do publish a dedicated activity.
        add(Intent().setComponent(ComponentName(settingsPackage, "$settingsPackage.RunningServices")))
        add(Intent().setComponent(ComponentName(settingsPackage, "$settingsPackage.Settings\$RunningServicesActivity")))
    }

    for (intent in intents) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            if (context.packageManager.resolveActivity(intent, 0) != null) {
                context.startActivity(intent)
                return true
            }
        } catch (_: Exception) {
            // Not exported or not present on this build; try the next one.
        }
    }
    return try {
        // Last resort: developer options, where the user can open the same
        // page one tap further in.
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (_: Exception) {
        false
    }
}
