package com.safekey.authenticator.ui.screens

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.os.PersistableBundle
import android.view.WindowManager

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

import com.safekey.authenticator.MainViewModel
import com.safekey.authenticator.R
import com.safekey.authenticator.SafeKeyApp
import com.safekey.authenticator.data.LanBlock
import com.safekey.authenticator.data.LanBlockRules
import com.safekey.authenticator.model.VaultFile
import com.safekey.authenticator.network.DiscoveredDevice
import com.safekey.authenticator.network.LanGuard
import com.safekey.authenticator.network.LanPairingQr
import com.safekey.authenticator.network.LanPeer
import com.safekey.authenticator.network.LanSendOutcome
import com.safekey.authenticator.network.LanSendTarget
import com.safekey.authenticator.network.LanSession
import com.safekey.authenticator.network.LanThreatReport
import com.safekey.authenticator.network.LanThreatSeverity
import com.safekey.authenticator.network.LanTransferClient
import com.safekey.authenticator.network.LanTransferServer
import com.safekey.authenticator.ui.components.AppIcons
import com.safekey.authenticator.ui.components.CodeField
import com.safekey.authenticator.ui.components.IconButtonCompat
import com.safekey.authenticator.ui.components.LocalNetworkPermissionGate
import com.safekey.authenticator.ui.components.QrCameraPreview
import com.safekey.authenticator.ui.components.renderQrBitmap
import com.safekey.authenticator.ui.components.SimpleTopBar
import com.safekey.authenticator.ui.theme.LocalFontScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull


/** A pairing code is a one-shot credential: it expires after this much idling. */
private const val PAIRING_CODE_IDLE_TIMEOUT_MS = 180_000L

/**
 * Pre-Android-13 spelling of [ClipDescription.EXTRA_IS_SENSITIVE]; the platform
 * clip overlay reads this key on Android 10-12.
 */
private const val LEGACY_EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"

/**
 * LAN transfer (protocol v4).
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

                LocalNetworkPermissionGate {
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
    // Pairing code: digits are the source of truth, the TextFieldValue keeps
    // text + caret so the grouping never moves the caret (see CodeField).
    var showScanner by remember { mutableStateOf(false) }
    var codeField by remember { mutableStateOf(TextFieldValue("")) }
    val codeInput = CodeField.digits(codeField.text, LanSession.PAIRING_CODE_DIGITS)
    var isSending by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    // True when the local network could not be verified (the normal state on
    // Android 10+, where the device table is hidden): shown as a standing note.
    var scanUnverified by remember { mutableStateOf(false) }
    val confirmRisk = rememberLanRiskConfirmation(lan, onBlockPeer)

    val client = remember { LanTransferClient(context) }

    LaunchedEffect(Unit) {
        scanUnverified = LanGuard.refresh(context)?.arpChecksRan != true
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
            client.cancelTransfer()
        }
    }

    fun blockedByMe(host: String, deviceId: String?): LanBlock? =
        blocks.firstOrNull { LanBlockRules.matches(it, host, null, deviceId) }

    if (showScanner) {
        LanPairingScanner(
            lan = lan,
            onCancel = { showScanner = false },
            onRaw = { raw ->
                val payload = LanPairingQr.parse(raw)
                if (payload == null) {
                    vm.showToast(lan.scanQrInvalid)
                } else {
                    // One scan fills in everything the send path needs: address,
                    // port and pairing code. The address still goes through the
                    // same local-network check as a typed one.
                    useManualIp = true
                    manualIp = "${payload.host}:${payload.port}"
                    val shown = LanSession.groupedCode(payload.code)
                    codeField = TextFieldValue(shown, TextRange(shown.length))
                    errorText = null
                    statusText = "${lan.scanQrPaired}: ${payload.host}:${payload.port}"
                    showScanner = false
                }
            }
        )
        return
    }

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

        Button(
            onClick = { showScanner = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(lan.scanQrPair)
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
            value = codeField,
            onValueChange = { incoming ->
                val resolved = CodeField.resolve(
                    previous = codeField.text,
                    previousCaret = codeField.selection.max,
                    incoming = incoming.text,
                    caret = incoming.selection.max,
                    maxDigits = LanSession.PAIRING_CODE_DIGITS
                )
                codeField = TextFieldValue(resolved.text, TextRange(resolved.caret))
            },
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

        if (scanUnverified) LanScanNote(lan)

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

                // A transfer belongs on the local network: refuse a public,
                // multicast or loopback target before anything is opened. A host
                // name is resolved and checked again on the IO path.
                if (LanSendTarget.literalIsLocalUnicast(targetHost) == false) {
                    errorText = lan.targetNotLocal
                    return@Button
                }

                val block = blockedByMe(targetHost, null)
                if (block != null) {
                    errorText = lan.blockedByMe
                    return@Button
                }

                isSending = true
                val transferCode = codeInput
                scope.launch {
                    try {
                    val selfReport = LanGuard.refresh(context)
                    scanUnverified = selfReport?.arpChecksRan != true
                    if (!confirmRisk(selfReport, null)) {
                        statusText = lan.cancelled
                        return@launch
                    }
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
                    val outcome = client.sendVault(
                        host = targetHost,
                        port = targetPort,
                        pairingCode = transferCode,
                        vault = export.vault,
                        selfDeviceId = deviceId,
                        selfName = android.os.Build.MODEL ?: "Osmium",
                        selfThreatCodes = selfReport?.codes.orEmpty(),
                        isBlocked = { ip, mac, peerId ->
                            app.lanBlockRepository.findActive(ip, mac, peerId) != null
                        },
                        confirmPeer = { candidate ->
                            val latest = LanGuard.refresh(context)
                            val newLocalRisk = latest?.codes.orEmpty().any { it !in selfReport?.codes.orEmpty() }
                            if (candidate.threatCodes.isNotEmpty() || newLocalRisk) {
                                confirmRisk(latest, candidate)
                            } else true
                        }
                    )
                    when (outcome) {
                        LanSendOutcome.Cancelled -> statusText = lan.cancelled
                        LanSendOutcome.Incompatible -> errorText = lan.updateBoth
                        is LanSendOutcome.Delivered -> {
                            statusText = context.getString(
                                R.string.lan_status_done_send, export.vault.accounts.size
                            )
                        }
                        is LanSendOutcome.PeerBlocked -> {
                            errorText = lan.blockedByMe
                        }
                        is LanSendOutcome.RefusedByPeer -> {
                            errorText = lan.blockedByPeer
                        }
                        LanSendOutcome.NotLocal -> errorText = lan.targetNotLocal
                        LanSendOutcome.ProofFailed -> {
                            errorText = context.getString(R.string.lan_error_wrong_code)
                        }
                        is LanSendOutcome.Failed -> {
                            errorText = context.getString(
                                R.string.lan_error_connection, outcome.message
                            )
                        }
                    }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        errorText = context.getString(R.string.lan_error_connection, e.message ?: "Error")
                    } finally {
                        isSending = false
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
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // Read once per recomposition: only used to restore the app-wide screenshot
    // policy when this page goes away.
    val allowScreenshots = vm.settings.collectAsState().value.allowScreenshots

    var localIp by remember { mutableStateOf("") }
    var port by remember { mutableIntStateOf(0) }
    var pairingCode by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("") }
    var isTransferring by remember { mutableStateOf(false) }
    var isDone by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    // True when the local network could not be verified (the normal state on
    // Android 10+, where the device table is hidden): shown as a standing note.
    var scanUnverified by remember { mutableStateOf(false) }
    val confirmRisk = rememberLanRiskConfirmation(lan, onBlockPeer)

    val server = remember { LanTransferServer(context, scope) }
    var startJob by remember { mutableStateOf<Job?>(null) }

    fun startServer() {
        startJob?.cancel()
        server.stop()
        pairingCode = ""
        port = 0
        isTransferring = false
        isDone = false
        errorText = null
        statusText = lan.waitingSender
        startJob = scope.launch {
            try {
            val deviceId = app.lanBlockRepository.deviceId()
            val report = LanGuard.refresh(context)
            scanUnverified = report?.arpChecksRan != true
            if (!confirmRisk(report, null)) {
                statusText = lan.cancelled
                return@launch
            }
            server.start(
                selfDeviceId = deviceId,
                selfName = android.os.Build.MODEL ?: "Osmium",
                selfThreatCodes = report?.codes.orEmpty(),
                isBlocked = { ip, mac, peerId ->
                    app.lanBlockRepository.findActive(ip, mac, peerId) != null
                },
                confirmPeer = { sender ->
                    val latest = LanGuard.refresh(context)
                    val newLocalRisk = latest?.codes.orEmpty().any { it !in report?.codes.orEmpty() }
                    if (sender.threatCodes.isNotEmpty() || newLocalRisk) {
                        confirmRisk(latest, sender)
                    } else true
                },
                onListening = { ip, listeningPort, code ->
                    localIp = ip
                    port = listeningPort
                    pairingCode = code
                },
                onClientConnected = {
                    isTransferring = true
                    statusText = context.getString(R.string.lan_status_transferring)
                },
                onVaultReceived = { vault, _ ->
                    isTransferring = false
                    isDone = true
                    // The code that just moved a vault is spent: drop it, and close
                    // the socket once the handler that called us has finished.
                    pairingCode = ""
                    scope.launch {
                        delay(250)
                        server.stop()
                        localIp = ""
                        port = 0
                    }
                    // Receiver wording: the accounts came *in* on this device.
                    statusText = context.getString(
                        R.string.lan_status_done_receive, vault.accounts.size
                    )
                    onVaultReceived(vault)
                },
                onError = { err ->
                    isTransferring = false
                    when (err) {
                        LanTransferServer.ERR_NO_WIFI ->
                            errorText = context.getString(R.string.lan_error_no_wifi)
                        LanTransferServer.ERR_TOO_MANY_FAILED_ATTEMPTS ->
                            errorText = context.getString(R.string.lan_error_too_many_attempts)
                        LanTransferServer.ERR_DECLINED -> statusText = lan.cancelled
                        LanTransferServer.ERR_PROOF ->
                            errorText = context.getString(R.string.lan_error_wrong_code)
                        LanTransferServer.ERR_PROTOCOL -> errorText = lan.updateBoth
                        else -> errorText = err
                    }
                }
            )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorText = context.getString(R.string.lan_error_connection, e.message ?: "Error")
            }
        }
    }

    /**
     * Ends the session on this device: socket down, mDNS withdrawn, code
     * discarded. Whatever [message] says is what the user is left looking at.
     */
    fun stopListening(message: String?) {
        server.stop()
        pairingCode = ""
        localIp = ""
        port = 0
        isTransferring = false
        errorText = null
        if (message != null) statusText = message
    }

    LaunchedEffect(Unit) {
        startServer()
    }

    DisposableEffect(Unit) {
        onDispose {
            startJob?.cancel()
            server.stop()
        }
    }

    // A pairing code is a one-shot credential, so it must not outlive its
    // welcome: after the idle window the session closes on its own. The effect
    // restarts (and therefore resets) whenever a peer connects, the code changes
    // or a transfer completes.
    LaunchedEffect(pairingCode, isTransferring, isDone) {
        if (pairingCode.isEmpty() || isTransferring || isDone) return@LaunchedEffect
        delay(PAIRING_CODE_IDLE_TIMEOUT_MS)
        stopListening(lan.idleStopped)
    }

    // A code on a screen nobody is looking at is a code an attacker can still
    // use, so leaving the foreground ends the session. Coming back shows the
    // refresh button: the next code has to be asked for on purpose.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                if (pairingCode.isNotEmpty() || isTransferring) {
                    stopListening(lan.idleStopped)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // The 12 digits on this page are the last key to the vault, so the page
    // blocks screenshots and screen recording even when the user allowed them
    // app-wide. The policy is restored on the way out.
    DisposableEffect(view, allowScreenshots) {
        val window = view.context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            if (allowScreenshots) {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
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
                                letterSpacing = 2.sp * LocalFontScale.current
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButtonCompat(
                            icon = AppIcons.ContentCopy,
                            contentDescription = stringResource(R.string.copy_code),
                            onClick = {
                                copyPairingCode(context, pairingCode)
                                vm.showToast(context.getString(R.string.code_copied))
                            },
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    LanPairingQrCard(lan, localIp, port, pairingCode)
                } else if (startJob?.isActive == true && errorText == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    TextButton(onClick = { startServer() }) {
                        Text(stringResource(R.string.refresh))
                    }
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

        if (scanUnverified) LanScanNote(lan)

        Text(
            text = stringResource(R.string.lan_security_tip),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp)
        )

    }
}

private class PendingLanRisk(
    val report: LanThreatReport?,
    val peer: LanPeer?,
    val result: CompletableDeferred<Boolean> = CompletableDeferred()
)

@Composable
private fun rememberLanRiskConfirmation(
    lan: LanStrings,
    onBlock: (LanPeer) -> Unit
): suspend (LanThreatReport?, LanPeer?) -> Boolean {
    var pending by remember { mutableStateOf<PendingLanRisk?>(null) }
    DisposableEffect(Unit) {
        onDispose { pending?.result?.complete(false) }
    }
    pending?.let { request ->
        LanRiskDialog(
            lan, request.report, request.peer,
            onBlock = {
                request.result.complete(false)
                request.peer?.let(onBlock)
            },
            onContinue = { request.result.complete(true) },
            onDismiss = { request.result.complete(false) }
        )
    }
    return { report, peer ->
        // Only a real finding interrupts a transfer. An unverifiable scan is not
        // a reason to stop the user every single time: on Android 10+ the device
        // table is hidden permanently, so that state is reported as a standing
        // note on the page instead of a dialog (see LanScanNote), which keeps the
        // claim honest without nagging.
        if (report?.hasThreats != true && peer?.threatCodes.isNullOrEmpty()) true
        else if (pending != null) false
        else {
            val request = PendingLanRisk(report, peer)
            pending = request
            try {
                withTimeoutOrNull(90_000) { request.result.await() } ?: false
            } finally {
                if (pending === request) pending = null
            }
        }
    }
}

/** Confirmation is suspended before any vault payload is sent or accepted. */
@Composable
private fun LanRiskDialog(
    lan: LanStrings,
    localReport: LanThreatReport?,
    peer: LanPeer?,
    onBlock: () -> Unit,
    onContinue: () -> Unit,
    onDismiss: () -> Unit
) {
    val local = localReport?.threats.orEmpty()
    val remote = peer?.threatCodes.orEmpty()
    val worst = listOfNotNull(localReport?.worst, peer?.worstThreat).maxByOrNull { it.ordinal }

    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                text = lan.riskTitle,
                color = if (worst == LanThreatSeverity.HIGH) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            TextButton(onClick = onContinue) {
                Text(lan.riskContinue)
            }
        },
        dismissButton = {
            Column {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                if (peer != null) TextButton(onClick = onBlock) { Text(lan.blockAction) }
            }
        }
    )
}

/**
 * The receiving side's QR code: address, port and pairing code in one picture, so
 * the sending device pairs with a scan instead of typing twelve digits and
 * hunting for an IP.
 *
 * Rendering is black-on-white on purpose ([renderQrBitmap]) — a scanner needs the
 * contrast, whatever the card behind it looks like.
 */
@Composable
private fun LanPairingQrCard(lan: LanStrings, host: String, port: Int, code: String) {
    if (host.isEmpty() || port <= 0) return
    val payload = remember(host, port, code) { LanPairingQr.encode(host, port, code) } ?: return
    val bitmap = remember(payload) { renderQrBitmap(payload, 480) }
    Spacer(Modifier.height(16.dp))
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = lan.pairingQrHint,
        modifier = Modifier.size(200.dp)
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = lan.pairingQrHint,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        textAlign = TextAlign.Center
    )
}

/**
 * Camera view for pairing: the sender scans the receiver's QR code.
 *
 * A foreign or malformed payload never reaches the transfer path — it is parsed
 * by [LanPairingQr], which rejects anything that is not a local unicast target
 * with a well-formed code.
 */
@Composable
private fun LanPairingScanner(
    lan: LanStrings,
    onCancel: () -> Unit,
    onRaw: (String) -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (hasPermission) {
            QrCameraPreview(
                enabled = true,
                onRawCode = onRaw,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = lan.scanQrInstruction,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        } else {
            Text(
                text = stringResource(R.string.camera_permission_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text(stringResource(R.string.grant_permission))
            }
        }
        TextButton(onClick = onCancel, modifier = Modifier.padding(top = 8.dp)) {
            Text(lan.scanQrCancel)
        }
    }
}

/**
 * Standing note for "the local network could not be verified".
 *
 * This replaces a confirmation dialog that fired on every single transfer on
 * Android 10+, where /proc/net/arp is hidden from apps: the user cannot change
 * that state, so asking again and again only trains them to tap through it. The
 * sentence stays on the page, so the app still never claims the network was
 * checked — it just stops interrupting.
 */
@Composable
private fun LanScanNote(lan: LanStrings) {
    Text(
        text = lan.scanUnavailableNote,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    )
}

/** Walks the context chain: a Compose screen does not always see the Activity. */
private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/**
 * Puts the pairing code on the clipboard as sensitive content.
 *
 * The sensitive flag keeps the code out of the system clip overlay and the
 * keyboard preview. Nothing clears the clipboard afterwards: the code is only
 * useful while this screen is up, and a timed wipe that runs behind the user's
 * back is worse than the exposure it prevents (scanning the QR is the normal
 * path anyway).
 */
private fun copyPairingCode(context: Context, code: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    val clip = ClipData.newPlainText("Pairing Code", code)
    clip.description.extras = PersistableBundle().apply {
        if (Build.VERSION.SDK_INT >= 33) {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        } else {
            putBoolean(LEGACY_EXTRA_IS_SENSITIVE, true)
        }
    }
    clipboard.setPrimaryClip(clip)
}
