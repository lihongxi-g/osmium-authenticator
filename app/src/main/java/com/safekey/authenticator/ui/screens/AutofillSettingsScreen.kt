package com.safekey.authenticator.ui.screens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.autofill.AutofillManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.safekey.authenticator.MainViewModel
import com.safekey.authenticator.R
import com.safekey.authenticator.model.Account
import com.safekey.authenticator.ui.components.AppIcons
import com.safekey.authenticator.ui.components.IconButtonCompat
import com.safekey.authenticator.ui.components.SectionHeader
import com.safekey.authenticator.ui.components.SettingRow
import com.safekey.authenticator.ui.components.SimpleTopBar

/**
 * Autofill configuration: service status, per-app account bindings, and how
 * the feature works. The service itself is opt-in — it only runs after the
 * user picks Osmium as the system autofill service, which this screen
 * explains and deep-links to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutofillSettingsScreen(
    vm: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val accounts by vm.accounts.collectAsState()
    val bindings by vm.autofillBindings.collectAsState()
    var enabled by remember { mutableStateOf(isAutofillEnabled(context)) }

    val systemSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Re-check on return: the user may have just selected Osmium.
        enabled = isAutofillEnabled(context)
    }

    var showAppPicker by remember { mutableStateOf(false) }
    var pendingPackage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        enabled = isAutofillEnabled(context)
    }

    Scaffold(
        topBar = { SimpleTopBar(title = stringResource(R.string.autofill_title), onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SettingRow(
                icon = if (enabled) AppIcons.Check else AppIcons.Warning,
                title = stringResource(
                    if (enabled) R.string.autofill_status_on else R.string.autofill_status_off
                ),
                description = stringResource(
                    if (enabled) R.string.autofill_status_on_desc else R.string.autofill_status_desc
                ),
                trailing = {
                    if (!enabled) {
                        TextButton(
                            onClick = { openAutofillSystemSettings(context, systemSettingsLauncher) }
                        ) {
                            Text(stringResource(R.string.autofill_open_settings))
                        }
                    }
                },
                onClick = if (!enabled) {
                    { openAutofillSystemSettings(context, systemSettingsLauncher) }
                } else null
            )

            SectionHeader(stringResource(R.string.autofill_how_title))

            InfoCard {
                Text(
                    text = stringResource(R.string.autofill_how_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.autofill_auth_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.autofill_privacy_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SectionHeader(stringResource(R.string.autofill_bindings_title))

            if (bindings.isEmpty()) {
                Text(
                    text = stringResource(R.string.autofill_bindings_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            } else {
                bindings.forEach { (packageName, accountId) ->
                    val account = accounts.firstOrNull { it.id == accountId }
                    BindingRow(
                        appLabel = appLabelOf(context, packageName),
                        accountTitle = if (account != null) {
                            "${account.displayTitle} · ${account.displaySubtitle}"
                        } else {
                            stringResource(R.string.autofill_binding_missing_account)
                        },
                        onRemove = {
                            vm.removeAutofillBinding(packageName)
                            vm.showToast(context.getString(R.string.autofill_binding_removed))
                        }
                    )
                }
            }

            SettingRow(
                icon = AppIcons.Add,
                title = stringResource(R.string.autofill_add_binding),
                description = stringResource(R.string.autofill_bindings_desc),
                onClick = { showAppPicker = true }
            )

            Text(
                text = stringResource(R.string.autofill_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
            )
        }
    }

    if (showAppPicker) {
        AppPickerDialog(
            context = context,
            onPick = { packageName ->
                showAppPicker = false
                pendingPackage = packageName
            },
            onDismiss = { showAppPicker = false }
        )
    }

    val pkgToBind = pendingPackage
    if (pkgToBind != null) {
        AccountPickerDialog(
            accounts = accounts,
            onPick = { account ->
                vm.setAutofillBinding(pkgToBind, account.id)
                vm.showToast(context.getString(R.string.autofill_binding_saved))
                pendingPackage = null
            },
            onDismiss = { pendingPackage = null }
        )
    }
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun BindingRow(
    appLabel: String,
    accountTitle: String,
    onRemove: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = AppIcons.Devices,
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
                    text = appLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = accountTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            IconButtonCompat(
                icon = AppIcons.Delete,
                contentDescription = stringResource(R.string.delete),
                onClick = onRemove,
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun AppPickerDialog(
    context: Context,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val apps = remember { loadLauncherApps(context) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.autofill_pick_app)) },
        text = {
            if (apps.isEmpty()) {
                Text(stringResource(R.string.autofill_no_apps))
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                    items(apps) { app ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(app.packageName) }
                                .padding(vertical = 12.dp)
                        ) {
                            Text(
                                text = app.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                            Text(
                                text = app.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun AccountPickerDialog(
    accounts: List<Account>,
    onPick: (Account) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.autofill_pick_account)) },
        text = {
            if (accounts.isEmpty()) {
                Text(stringResource(R.string.autofill_no_accounts))
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                    items(accounts, key = { it.id }) { account ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(account) }
                                .padding(vertical = 12.dp)
                        ) {
                            Text(
                                text = account.displayTitle,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                            Text(
                                text = account.displaySubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

// ------------------------------------------------------------- helpers

private data class SimpleApp(val packageName: String, val label: String)

/** Whether Osmium itself is the enabled autofill service. */
private fun isAutofillEnabled(context: Context): Boolean =
    context.getSystemService(AutofillManager::class.java)?.hasEnabledAutofillServices() == true

/**
 * Opens the system screen for choosing the autofill service.
 *
 * The platform contract (Settings#ACTION_REQUEST_SET_AUTOFILL_SERVICE)
 * requires the intent to carry a data URI of scheme "package" pointing at
 * the caller (e.g. "package:com.my.app"). Modern ROMs register the picker
 * with a matching <data android:scheme="package"/> filter, so an intent
 * without this URI resolves to nothing and the user ends up on the generic
 * settings home. Older ROMs register the action without a data constraint
 * and accept the same intent, so this form is the safe superset.
 */
private fun openAutofillSystemSettings(
    context: Context,
    launcher: ActivityResultLauncher<Intent>
) {
    val pkgUri = Uri.parse("package:${context.packageName}")
    val attempts = listOf(
        // 1) Spec-compliant request: action + "package:" data.
        Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).setData(pkgUri),
        // 2) + 3) Forced targets for ROMs whose picker filter differs from
        // the documented one (skipped automatically when not present).
        Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).setData(pkgUri).setComponent(
            ComponentName(
                "com.android.settings",
                "com.android.settings.applications.credentials.CredentialsPickerActivity"
            )
        ),
        Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).setData(pkgUri).setComponent(
            ComponentName(
                "com.android.settings",
                "com.android.settings.applications.autofill.AutofillPickerActivity"
            )
        ),
        // 4) Last resort: the top-level settings screen.
        Intent(Settings.ACTION_SETTINGS),
    )
    for (attempt in attempts) {
        try {
            launcher.launch(attempt)
            return
        } catch (_: Exception) {
            // Try the next candidate.
        }
    }
}

@Suppress("DEPRECATION") // int-flags queryIntentActivities: still fine at minSdk 26.
private fun loadLauncherApps(context: Context): List<SimpleApp> {
    val pm = context.packageManager
    val resolved = try {
        pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            0
        )
    } catch (_: Exception) {
        return emptyList()
    }
    return resolved
        .map { it.activityInfo.packageName }
        .distinct()
        .filter { it != context.packageName }
        .map { packageName -> SimpleApp(packageName, appLabelOf(context, packageName)) }
        .sortedBy { it.label.lowercase() }
}

@Suppress("DEPRECATION")
private fun appLabelOf(context: Context, packageName: String): String = try {
    val pm = context.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
} catch (_: Exception) {
    packageName.split('.').lastOrNull() ?: packageName
}
