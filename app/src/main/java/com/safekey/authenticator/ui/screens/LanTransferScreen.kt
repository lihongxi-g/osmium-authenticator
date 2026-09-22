package com.safekey.authenticator.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
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
import androidx.core.content.ContextCompat
import com.safekey.authenticator.MainViewModel
import com.safekey.authenticator.R
import com.safekey.authenticator.SafeKeyApp
import com.safekey.authenticator.data.LanBlock
import com.safekey.authenticator.data.LanBlockRules
import com.safekey.authenticator.model.VaultFile
import com.safekey.authenticator.network.DiscoveredDevice
import com.safekey.authenticator.network.LanGuard
import com.safekey.authenticator.network.LanPeer
import com.safekey.authenticator.network.LanSendOutcome
import com.safekey.authenticator.network.LanSession
import com.safekey.authenticator.network.LanThreatReport
import com.safekey.authenticator.network.LanThreatSeverity
import com.safekey.authenticator.network.LanTransferClient
import com.safekey.authenticator.network.LanTransferServer
import com.safekey.authenticator.ui.components.AppIcons
import com.safekey.authenticator.ui.components.IconButtonCompat
import com.safekey.authenticator.ui.components.SimpleTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runtime permission Android 17 (API 37) introduced for local-network traffic.
 * Declared in the manifest already; requested only on platforms that enforce it.
 */
private const val ANDROID_LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

/**
 * LAN transfer (protocol v3).
 *
 * The receiving device hosts the session and shows a 12-digit code; the sending
 * device types that code and proves it before any data is accepted. Both sides
 * scan the local network silently and are warned when either side sees a risk;
 * the peer can then be refused for 24 hours (encrypted block list).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanTransferScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onRequireBiometric: ((onSuccess: () -> Unit) -> Unit)? = null,
    onRequireCredential: ((onSuccess: () -> Unit) -> Unit)? = null
) {
    val context = LocalContext.current
    val app = context.applicationContext as SafeKeyApp
    val lan = remember { LanStrings.forContext(context) }
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    var receivedVault by remember { mutableStateOf<VaultFile?>(null) }
    var blocks by remember { mutableStateOf<List<LanBlock>>(emptyList()) }

    /** Blocking a device is a security decision: verify identity first. */
    fun requireIdentity(action: () -> Unit) {
        val biometric = onRequireBiometric
        val credential = onRequireCredential
        when {
            biometric != null -> biometric(action)
            credential != null -> credential(action)
            else -> action()
        }
    }

    fun onBlockPeer(peer: LanPeer) {
        requireIdentity {
            scope.launch {
                app.lanBlockRepository.block(peer.ip, peer.mac, peer.deviceId, peer.name)
                vm.showToast(lan.blockDone)
            }
        }
    }

    fun onUnblock(block: LanBlock) {
        requireIdentity {
            scope.launch {
                app.lanBlockRepository.unblock(block.ip, block.mac, block.deviceId)
                vm.showToast(lan.unblocked)
            }
        }
    }

    LaunchedEffect(Unit) {
        app.lanBlockRepository.blocks.collect { blocks = it }
    }

    // Silent local-network inspection on entry (no user-visible scanning).
    LaunchedEffect(Unit) {
        LanGuard.refresh(context)
    }

    // Android 17 (API 37) puts local-network traffic behind a runtime permission
    // for apps targeting 37+. Asking for it here keeps transfers and WebDAV
    // working once the target SDK is raised; on older platforms the check never
    // fires, and the permission is optional while we still target 34.
    val lanPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 37) {
            val granted = ContextCompat.checkSelfPermission(
                context, ANDROID_LOCAL_NETWORK_PERMISSION
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) lanPermissionLauncher.launch(ANDROID_LOCAL_NETWORK_PERMISSION)
        }
    }

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
                    onDone = { onBack() },
                    onBackToPassword = { receivedVault = null }
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
                    SendTabContent(
                        vm = vm,
                        lan = lan,
                        blocks = blocks,
                        onBlockPeer = { onBlockPeer(it) },
                        onUnblock = { onUnblock(it) }
                    )
                } else {
                    ReceiveTabContent(
                        vm = vm,
                        lan = lan,
                        blocks = blocks,
                        onVaultReceived = { receivedVault = it },
                        onBlockPeer = { onBlockPeer(it) },
                        onUnblock = { onUnblock(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SendTabContent(
    vm: MainViewModel,
    lan: LanStrings,
    blocks: List<LanBlock>,
    onBlockPeer: (LanPeer) -> Unit,
    onUnblock: (LanBlock) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as SafeKeyApp
    val scope = rememberCoroutineScope()

    val discoveredDevices = remember { mutableStateListOf<DiscoveredDevice>() }
    var selectedDevice by remember { mutableStateOf<DiscoveredDevice?>(null) }
    var useManualIp by remember { mutableStateOf(false) }
    var manualIp by remember { mutableStateOf("") }
    var codeInput by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var peer by remember { mutableStateOf<LanPeer?>(null) }
    var showRiskDialog by remember { mutableStateOf(false) }
    var localReport by remember { mutableStateOf(LanGuard.report.value) }

    val client = remember { LanTransferClient(context) }

    LaunchedEffect(Unit) {
        localReport = LanGuard.refresh(context)
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

    fun blockedByMe(host: String, deviceId: String?): LanBlock? =
        blocks.firstOrNull { LanBlockRules.matches(it, host, null, deviceId) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = lan.sendCodeHint,
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
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
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
                    val block = blockedByMe(dev.host, null)
                    val selected = selectedDevice == dev && block == null
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                block != null -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                selected -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (block != null) {
                                    // A blocked device still shows up, but stays
                                    // unusable until the user removes the block.
                                    if (selectedDevice == dev) {
                                        onUnblock(block)
                                    } else {
                                        vm.showToast(lan.blockedByMe)
                                    }
                                } else {
                                    selectedDevice = dev
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selected, onClick = { selectedDevice = dev })
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = dev.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (block != null) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else if (selected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                Text(
                                    text = if (block != null) {
                                        lan.blockedByMe
                                    } else {
                                        "${dev.host}:${dev.port}"
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
            value = LanSession.groupedCode(codeInput),
            onValueChange = { codeInput = LanSession.normalizeCode(it).take(LanSession.PAIRING_CODE_DIGITS) },
            label = { Text(stringResource(R.string.lan_pairing_code_label)) },
            placeholder = { Text(stringResource(R.string.lan_pairing_code_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            isError = errorText != null,
            modifier = Modifier.fillMaxWidth()
        )

        errorText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        statusText.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Button(
            onClick = {
                errorText = null
                statusText = ""
                val targetHost: String
                val targetPort: Int

                if (useManualIp) {
                    val parts = manualIp.split(":")
                    if (parts.size != 2 || parts[1].toIntOrNull() == null) {
                        errorText = context.getString(R.string.lan_error_invalid_ip)
                        return@Button
                    }
                    targetHost = parts[0].trim()
                    targetPort = parts[1].trim().toInt()
                } else {
                    val dev = selectedDevice
                    if (dev == null) {
                        errorText = context.getString(R.string.lan_error_no_device_selected)
                        return@Button
                    }
                    targetHost = dev.host
                    targetPort = dev.port
                }

                if (!LanSession.isWellFormedCode(codeInput)) {
                    errorText = context.getString(R.string.lan_error_invalid_code_length)
                    return@Button
                }

                val block = blockedByMe(targetHost, null)
                if (block != null) {
                    errorText = lan.blockedByMe
                    return@Button
                }

                isSending = true
                scope.launch {
                    val pin = vm.pinManager.getPinHashForExport()
                    val export = withContext(Dispatchers.IO) {
                        app.accountRepository.exportVault(
                            pin?.first ?: "",
                            pin?.second ?: ""
                        )
                    }
                    if (export.dropped > 0) {
                        isSending = false
                        errorText = "${export.dropped} accounts could not be decrypted — transfer aborted"
                        return@launch
                    }
                    val deviceId = app.lanBlockRepository.deviceId()
                    val selfReport = LanGuard.refresh(context)
                    val outcome = client.sendVault(
                        host = targetHost,
                        port = targetPort,
                        pairingCode = codeInput,
                        vault = export.vault,
                        selfDeviceId = deviceId,
                        selfName = android.os.Build.MODEL ?: "Osmium",
                        selfThreatCodes = selfReport?.codes.orEmpty(),
                        isBlocked = { ip, mac, peerId ->
                            app.lanBlockRepository.findActive(ip, mac, peerId) != null
                        }
                    )
                    isSending = false
                    localReport = LanGuard.report.value ?: localReport
                    when (outcome) {
                        is LanSendOutcome.Delivered -> {
                            peer = outcome.peer
                            statusText = context.getString(
                                R.string.lan_status_done_send, export.vault.accounts.size
                            )
                            if (outcome.peer.threatCodes.isNotEmpty() ||
                                localReport?.hasThreats == true
                            ) {
                                showRiskDialog = true
                            }
                        }
                        is LanSendOutcome.PeerBlocked -> {
                            peer = outcome.peer
                            errorText = lan.blockedByMe
                        }
                        is LanSendOutcome.RefusedByPeer -> {
                            peer = outcome.peer
                            errorText = lan.blockedByPeer
                        }
                        LanSendOutcome.ProofFailed -> {
                            errorText = context.getString(R.string.lan_error_wrong_code)
                        }
                        is LanSendOutcome.Failed -> {
                            errorText = context.getString(
                                R.string.lan_error_connection, outcome.message
                            )
                        }
                    }
                }
            },
            enabled = !isSending && LanSession.isWellFormedCode(codeInput) &&
                (selectedDevice != null || (useManualIp && manualIp.isNotBlank())),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isSending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(stringResource(R.string.lan_connect_btn))
        }

        if (showRiskDialog) {
            LanRiskDialog(
                lan = lan,
                localReport = localReport,
                peer = peer,
                onBlock = {
                    showRiskDialog = false
                    peer?.let(onBlockPeer)
                },
                onDismiss = { showRiskDialog = false }
            )
        }
    }
}

@Composable
private fun ReceiveTabContent(
    vm: MainViewModel,
    lan: LanStrings,
    blocks: List<LanBlock>,
    onVaultReceived: (VaultFile) -> Unit,
    onBlockPeer: (LanPeer) -> Unit,
    onUnblock: (LanBlock) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as SafeKeyApp
    val scope = rememberCoroutineScope()

    var localIp by remember { mutableStateOf("") }
    var port by remember { mutableIntStateOf(0) }
    var pairingCode by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("") }
    var isTransferring by remember { mutableStateOf(false) }
    var isDone by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var peer by remember { mutableStateOf<LanPeer?>(null) }
    var showRiskDialog by remember { mutableStateOf(false) }
    var localReport by remember { mutableStateOf(LanGuard.report.value) }

    val server = remember { LanTransferServer(context, scope) }

    fun startServer() {
        isDone = false
        errorText = null
        statusText = context.getString(R.string.lan_status_waiting)
        scope.launch {
            val deviceId = app.lanBlockRepository.deviceId()
            val report = LanGuard.refresh(context)
            localReport = report
            server.start(
                selfDeviceId = deviceId,
                selfName = android.os.Build.MODEL ?: "Osmium",
                selfThreatCodes = report?.codes.orEmpty(),
                isBlocked = { ip, mac, peerId ->
                    app.lanBlockRepository.findActive(ip, mac, peerId) != null
                },
                onClientConnected = {
                    isTransferring = true
                    statusText = context.getString(R.string.lan_status_transferring)
                },
                onVaultReceived = { vault, sender ->
                    isTransferring = false
                    isDone = true
                    peer = sender
                    // Receiver wording: the accounts came *in* on this device.
                    statusText = context.getString(
                        R.string.lan_status_done_receive, vault.accounts.size
                    )
                    // Both ends are alerted: warn here and let the user refuse
                    // this device for the next 24 hours.
                    if (sender.threatCodes.isNotEmpty() ||
                        LanGuard.report.value?.hasThreats == true
                    ) {
                        localReport = LanGuard.report.value
                        showRiskDialog = true
                    }
                    onVaultReceived(vault)
                },
                onError = { err ->
                    isTransferring = false
                    when (err) {
                        LanTransferServer.ERR_NO_WIFI ->
                            errorText = context.getString(R.string.lan_error_no_wifi)
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
            text = lan.receiveCodeHint,
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = LanSession.groupedCode(pairingCode),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButtonCompat(
                            icon = AppIcons.ContentCopy,
                            contentDescription = stringResource(R.string.copy_code),
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                    as? ClipboardManager
                                clipboard?.setPrimaryClip(
                                    ClipData.newPlainText("Pairing Code", pairingCode)
                                )
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
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (!isDone) {
                        OutlinedButton(onClick = { startServer() }) {
                            Icon(
                                AppIcons.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
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
                    Icon(
                        AppIcons.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isDone) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Devices this side has blocked still appear, but cannot be used again
        // until the block is lifted (identity verification required).
        blocks.filter { !it.label.isNullOrBlank() }.takeIf { it.isNotEmpty() }?.let { list ->
            Text(
                text = lan.blockedTapHint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            list.forEach { block ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onUnblock(block) }
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = block.label.ifBlank { block.ip },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = lan.blockedByMe,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Text(
            text = stringResource(R.string.lan_security_tip),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp)
        )

        if (showRiskDialog) {
            LanRiskDialog(
                lan = lan,
                localReport = localReport,
                peer = peer,
                onBlock = {
                    showRiskDialog = false
                    peer?.let(onBlockPeer)
                },
                onDismiss = { showRiskDialog = false }
            )
        }
    }
}

/** Both ends are told about risks; the peer can be refused for 24 hours. */
@Composable
private fun LanRiskDialog(
    lan: LanStrings,
    localReport: LanThreatReport?,
    peer: LanPeer?,
    onBlock: () -> Unit,
    onDismiss: () -> Unit
) {
    val local = localReport?.threats.orEmpty()
    val remote = peer?.threatCodes.orEmpty()
    val worst = listOfNotNull(localReport?.worst, peer?.worstThreat).maxByOrNull { it.ordinal }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = lan.riskTitle,
                color = if (worst == LanThreatSeverity.HIGH) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (local.isEmpty()) {
                    Text(lan.riskNone, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(lan.riskLocalHeader, style = MaterialTheme.typography.labelLarge)
                    local.take(6).forEach { threat ->
                        Text(
                            text = "• ${lan.threat(threat.code)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (remote.isNotEmpty()) {
                    Text(lan.riskPeerHeader, style = MaterialTheme.typography.labelLarge)
                    remote.take(6).forEach { code ->
                        Text(
                            text = "• ${lan.threat(code)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onBlock, enabled = peer != null) {
                Text(lan.blockAction)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(lan.riskContinue)
            }
        }
    )
}
