package com.safekey.authenticator.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.safekey.authenticator.MainViewModel
import com.safekey.authenticator.R
import com.safekey.authenticator.link.LinkAccountView
import com.safekey.authenticator.link.LinkConnectionState
import com.safekey.authenticator.link.LinkDiscovery
import com.safekey.authenticator.link.LinkTransport
import com.safekey.authenticator.link.LinkTrustStore
import com.safekey.authenticator.ui.components.SimpleTopBar

@Composable
fun LinkScreen(vm: MainViewModel, onBack: () -> Unit) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
        SimpleTopBar(stringResource(R.string.link_title), onBack)
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val discovery = remember(context) { LinkDiscovery(context) }
    val trustStore = remember(context) { LinkTrustStore(context) }
    val transport = remember(scope) {
        LinkTransport(scope, Build.MODEL ?: "Android") {
            vm.accountUiList.value.map { ui ->
                LinkAccountView(ui.account.id, ui.account.issuer, ui.account.label, ui.code, ui.remainingSeconds, ui.account.type, ui.account.tags.map { it.name })
            }
        }
    }
    val peers by discovery.peers.collectAsState()
    val connection by transport.state.collectAsState()
    var enabled by remember { mutableStateOf(false) }
    var selectedPeer by remember { mutableStateOf<android.net.nsd.NsdServiceInfo?>(null) }
    var pairingCode by remember { mutableStateOf("") }
    var showTrustConfirm by remember { mutableStateOf(false) }

    DisposableEffect(enabled) {
        if (enabled) {
            val port = transport.startServer()
            discovery.start(Build.MODEL ?: "Android", port)
        } else {
            discovery.stop(); transport.stop()
        }
        onDispose { discovery.stop(); transport.stop() }
    }

    Column(Modifier.fillMaxSize()) {
        SimpleTopBar(stringResource(R.string.link_title), onBack)
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.link_allow_discovery), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.link_trust_warning), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = enabled, onCheckedChange = { enabled = it })
        }
        when (val state = connection) {
            is LinkConnectionState.Connected -> {
                Text(stringResource(R.string.link_remote_accounts), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
                Text("${state.deviceName} · ${state.fingerprint}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (trustStore.isTrusted(state.fingerprint)) stringResource(R.string.link_trusted) else "", color = MaterialTheme.colorScheme.error)
                    if (!trustStore.isTrusted(state.fingerprint)) TextButton(onClick = { showTrustConfirm = true }) { Text(stringResource(R.string.link_trusted), color = MaterialTheme.colorScheme.error) }
                }
                LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.accounts, key = { it.id }) { account ->
                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text(account.issuer.ifBlank { account.label }); Text(account.label, style = MaterialTheme.typography.bodySmall); Text(account.code, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary) } }
                    }
                }
                TextButton(onClick = { transport.stop() }) { Text(stringResource(R.string.link_disconnect)) }
            }
            else -> {
                if (peers.isEmpty()) Text(stringResource(R.string.link_no_devices), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(peers, key = { it.serviceName }) { peer ->
                        Card(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(peer.serviceName.removePrefix("Osmium-").substringBeforeLast('-'), Modifier.weight(1f)); Button(onClick = { selectedPeer = peer }) { Text(stringResource(R.string.link_pair)) } } }
                    }
                }
            }
        }
        val accepted = connection as? LinkConnectionState.Incoming
        if (accepted != null) {
            AlertDialog(
                onDismissRequest = { transport.rejectIncoming() },
                title = { Text(accepted.deviceName) },
                text = { Text("${stringResource(R.string.link_pair_code)}: ${accepted.code}") },
                confirmButton = { TextButton(onClick = { transport.acceptIncoming() }) { Text(stringResource(R.string.link_pair)) } },
                dismissButton = { TextButton(onClick = { transport.rejectIncoming() }) { Text(stringResource(R.string.cancel)) } }
            )
        }

    if (showTrustConfirm) {
        AlertDialog(
            onDismissRequest = { showTrustConfirm = false },
            title = { Text(stringResource(R.string.link_trusted), color = MaterialTheme.colorScheme.error) },
            text = { Text(stringResource(R.string.link_trust_warning), color = MaterialTheme.colorScheme.error) },
            confirmButton = { TextButton(onClick = { (connection as? LinkConnectionState.Connected)?.let { trustStore.setTrusted(it.fingerprint, true); transport.trustCurrent() }; showTrustConfirm = false }) { Text(stringResource(R.string.link_trusted), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showTrustConfirm = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }


    selectedPeer?.let { peer ->
        AlertDialog(
            onDismissRequest = { selectedPeer = null },
            title = { Text(stringResource(R.string.link_pair_code)) },
            text = { OutlinedTextField(pairingCode, { pairingCode = it.filter(Char::isDigit).take(6) }, singleLine = true, label = { Text(stringResource(R.string.link_pair_code)) }) },
            confirmButton = { TextButton(enabled = pairingCode.length == 6, onClick = { transport.connect(peer.host.hostAddress ?: return@TextButton, peer.port, pairingCode) { result -> result.onSuccess { accounts -> transport.publishConnected(peer.serviceName, accounts) }; result.onFailure { transport.publishError(it.message ?: "Link failed") } }; selectedPeer = null; pairingCode = "" }) { Text(stringResource(R.string.link_pair)) } },
            dismissButton = { TextButton(onClick = { selectedPeer = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    }
}
