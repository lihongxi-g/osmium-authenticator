package com.safekey.authenticator.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safekey.authenticator.MainViewModel
import com.safekey.authenticator.R
import com.safekey.authenticator.model.Account
import com.safekey.authenticator.security.ClipboardHelper
import com.safekey.authenticator.ui.components.AppIcons
import com.safekey.authenticator.ui.components.IconButtonCompat
import com.safekey.authenticator.ui.components.SimpleTopBar
import com.safekey.authenticator.ui.theme.monospaceFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    vm: MainViewModel,
    accountId: String,
    onEdit: (Account) -> Unit,
    onDeleted: () -> Unit,
    onBack: () -> Unit,
    onRequireBiometric: ((onSuccess: () -> Unit) -> Unit)? = null
) {
    val uiList by vm.accountUiList.collectAsState()
    val ui = uiList.firstOrNull { it.account.id == accountId }
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showSecret by remember { mutableStateOf(false) }

    val account = ui?.account

    Scaffold(
        topBar = {
            SimpleTopBar(
                title = stringResource(R.string.account_detail),
                onBack = onBack,
                actions = {
                    if (account != null) {
                        IconButtonCompat(
                            icon = AppIcons.Edit,
                            contentDescription = stringResource(R.string.edit),
                            onClick = { onEdit(account) }
                        )
                        IconButtonCompat(
                            icon = AppIcons.Delete,
                            contentDescription = stringResource(R.string.delete),
                            onClick = { showDeleteDialog = true }
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (account == null || ui == null) return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = account.displayTitle,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = account.displaySubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            ClipboardHelper.copy(
                                context, ui.code, vm.settings.value.clipboardClearSeconds
                            )
                            vm.showToast(context.getString(R.string.code_copied))
                        }
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = ui.code,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = monospaceFamily,
                            fontSize = 36.sp,
                            letterSpacing = 4.sp
                        ),
                        color = if (ui.remainingSeconds <= 5) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.seconds_remaining, ui.remainingSeconds),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = AppIcons.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.copy_code),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.totp_params, account.algorithm, account.digits, account.period),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))
            if (!showSecret) {
                OutlinedButton(
                    onClick = {
                        val requireAuth = onRequireBiometric
                        if (requireAuth != null) {
                            requireAuth { showSecret = true }
                        } else {
                            showSecret = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = AppIcons.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.show_secret),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.secret_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = account.secret,
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = monospaceFamily),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButtonCompat(
                            icon = AppIcons.ContentCopy,
                            contentDescription = stringResource(R.string.copy_code),
                            onClick = {
                                ClipboardHelper.copy(
                                    context, account.secret, vm.settings.value.clipboardClearSeconds
                                )
                                vm.showToast(context.getString(R.string.code_copied))
                            },
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            OutlinedButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = AppIcons.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = stringResource(R.string.delete),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }

    if (showDeleteDialog && account != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_message, account.displayTitle)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    vm.deleteAccount(account)
                    onDeleted()
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}
