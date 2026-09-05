package com.safekey.authenticator.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safekey.authenticator.MainViewModel
import com.safekey.authenticator.R
import com.safekey.authenticator.SafeKeyApp
import com.safekey.authenticator.model.VaultFile
import com.safekey.authenticator.network.DiscoveredDevice
import com.safekey.authenticator.network.LanTransferClient
import com.safekey.authenticator.network.LanTransferServer
import com.safekey.authenticator.security.VaultFormatException
import com.safekey.authenticator.ui.components.AppIcons
import com.safekey.authenticator.ui.components.IconButtonCompat
import com.safekey.authenticator.ui.components.SimpleTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanTransferScreen(
    vm: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var receivedVault by remember { mutableStateOf<VaultFile?>(null) }

    Scaffold(
        topBar = {
            SimpleTopBar(
                title = stringResource(R.string.lan_transfer_title),
                onBack = onBack
            )
        }
    ) { padding ->
        val currentVault = receivedVault
        if (currentVault != null) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                VaultImportFlow(
                    vm = vm,
                    vault = currentVault,
                    onDone = {
                        vm.showToast(context.getString(R.string.import_done, currentVault.accounts.size))
                        onBack()
                    },
                    onBackToPassword = {
                        receivedVault = null
                    }
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(stringResource(R.string.lan_send_tab)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(stringResource(R.string.lan_receive_tab)) }
                    )
                }

                if (selectedTab == 0) {
                    SendTabContent(vm = vm)
                } else {
                    ReceiveTabContent(
                        vm = vm,
                        onVaultReceived = { receivedVault = it }
                    )
                }
            }
        }
    }
}

@Composable
private fun SendTabContent(vm: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var localIp by remember { mutableStateOf("") }
    var port by remember { mutableIntStateOf(0) }
    var pairingCode by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("") }
    var isTransferring by remember { mutableStateOf(false) }
    var isDone by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val server = remember { LanTransferServer(context, scope) }

    fun startServer() {
        isDone = false
        errorText = null
        statusText = context.getString(R.string.lan_status_waiting)
        scope.launch {
            val app = context.applicationContext as SafeKeyApp
            val pin = vm.pinManager.getPinHashForExport()
            val vault = withContext(Dispatchers.IO) {
                app.accountRepository.exportVault(pin?.first ?: "", pin?.second ?: "")
            }
            server.start(
                vault = vault,
                onClientConnected = {
                    isTransferring = true
                    statusText = context.getString(R.string.lan_status_transferring)
                },
                onTransferSuccess = { count ->
                    isTransferring = false
                    isDone = true
                    statusText = context.getString(R.string.lan_status_done_send, count)
                },
                onError = { err ->
                    isTransferring = false
                    when (err) {
                        "NO_WIFI" -> errorText = context.getString(R.string.lan_error_no_wifi)
                        LanTransferServer.ERR_TOO_MANY_FAILED_ATTEMPTS ->
                            errorText = context.getString(R.string.lan_error_too_many_attempts)
                        else -> errorText = err
                    }
                }
            )
            localIp = server.localIp
            port = server.port
            pairingCode = server.pairingCode
        }
    }

    LaunchedEffect(Unit) {
        startServer()
    }

    DisposableEffect(Unit) {
        onDispose {
            server.stop()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.lan_transfer_send_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.lan_pairing_code_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(12.dp))

                if (pairingCode.isNotEmpty()) {
                    val formattedCode = if (pairingCode.length == 6) {
                        "${pairingCode.substring(0, 3)} ${pairingCode.substring(3, 6)}"
                    } else pairingCode

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = formattedCode,
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 4.sp
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButtonCompat(
                            icon = AppIcons.ContentCopy,
                            contentDescription = stringResource(R.string.copy_code),
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(ClipData.newPlainText("Pairing Code", pairingCode))
                                vm.showToast(context.getString(R.string.code_copied))
                            },
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        if (localIp.isNotEmpty() && port > 0) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.lan_server_ip_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$localIp:$port",
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (!isDone) {
                        OutlinedButton(onClick = { startServer() }) {
                            Icon(AppIcons.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.refresh))
                        }
                    }
                }
            }
        }

        errorText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }

        if (errorText == null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                if (isTransferring) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                } else if (isDone) {
                    Icon(AppIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = stringResource(R.string.lan_security_tip),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp)
        )
    }
}

@Composable
private fun ReceiveTabContent(
    vm: MainViewModel,
    onVaultReceived: (VaultFile) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val discoveredDevices = remember { mutableStateListOf<DiscoveredDevice>() }
    var selectedDevice by remember { mutableStateOf<DiscoveredDevice?>(null) }
    var useManualIp by remember { mutableStateOf(false) }
    var manualIp by remember { mutableStateOf("") }
    var pairingCodeInput by remember { mutableStateOf("") }
    var isFetching by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val client = remember { LanTransferClient(context) }

    LaunchedEffect(Unit) {
        discoveredDevices.clear()
        client.startDiscovery(
            onDeviceFound = { dev ->
                if (discoveredDevices.none { it.host == dev.host && it.port == dev.port }) {
                    discoveredDevices.add(dev)
                    if (selectedDevice == null) selectedDevice = dev
                }
            },
            onDeviceLost = { name ->
                discoveredDevices.removeAll { it.name == name || it.host == name }
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            client.stopDiscovery()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.lan_transfer_receive_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = stringResource(R.string.lan_select_device_title),
            style = MaterialTheme.typography.titleMedium
        )

        if (!useManualIp) {
            if (discoveredDevices.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.lan_status_searching),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                discoveredDevices.forEach { dev ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedDevice == dev)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedDevice = dev }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedDevice == dev,
                                onClick = { selectedDevice = dev }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = dev.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (selectedDevice == dev)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${dev.host}:${dev.port}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = if (selectedDevice == dev)
                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = { useManualIp = !useManualIp }) {
                Text(
                    text = if (useManualIp) stringResource(R.string.lan_use_auto_discovery)
                    else stringResource(R.string.lan_status_manual_ip)
                )
            }
        }

        if (useManualIp) {
            OutlinedTextField(
                value = manualIp,
                onValueChange = { manualIp = it.trim() },
                label = { Text(stringResource(R.string.lan_ip_port_label)) },
                placeholder = { Text(stringResource(R.string.lan_ip_port_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        OutlinedTextField(
            value = pairingCodeInput,
            onValueChange = {
                if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                    pairingCodeInput = it
                }
            },
            label = { Text(stringResource(R.string.lan_pairing_code_label)) },
            placeholder = { Text(stringResource(R.string.lan_pairing_code_hint)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            isError = errorMessage != null,
            modifier = Modifier.fillMaxWidth()
        )

        errorMessage?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                errorMessage = null
                val targetHost: String
                val targetPort: Int

                if (useManualIp) {
                    val parts = manualIp.split(":")
                    if (parts.size != 2 || parts[1].toIntOrNull() == null) {
                        errorMessage = context.getString(R.string.lan_error_invalid_ip)
                        return@Button
                    }
                    targetHost = parts[0].trim()
                    targetPort = parts[1].trim().toInt()
                } else {
                    val dev = selectedDevice
                    if (dev == null) {
                        errorMessage = context.getString(R.string.lan_error_no_device_selected)
                        return@Button
                    }
                    targetHost = dev.host
                    targetPort = dev.port
                }

                if (pairingCodeInput.length != 6) {
                    errorMessage = context.getString(R.string.lan_error_invalid_code_length)
                    return@Button
                }

                isFetching = true
                scope.launch {
                    val result = client.fetchVault(targetHost, targetPort, pairingCodeInput)
                    isFetching = false
                    result.onSuccess { vault ->
                        onVaultReceived(vault)
                    }.onFailure { err ->
                        if (err is VaultFormatException && err.wrongPassword) {
                            errorMessage = context.getString(R.string.lan_error_wrong_code)
                        } else {
                            errorMessage = context.getString(R.string.lan_error_connection, err.message ?: "Unknown error")
                        }
                    }
                }
            },
            enabled = !isFetching && pairingCodeInput.length == 6 && (selectedDevice != null || (useManualIp && manualIp.isNotBlank())),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isFetching) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(stringResource(R.string.lan_connect_btn))
        }
    }
}
