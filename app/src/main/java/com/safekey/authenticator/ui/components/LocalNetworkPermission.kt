package com.safekey.authenticator.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.safekey.authenticator.R
import com.safekey.authenticator.network.LocalNetworkAccess

@Composable
fun rememberLocalNetworkPermissionRequest(onDenied: () -> Unit): ((() -> Unit) -> Unit) {
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pending
        pending = null
        if (granted) action?.invoke() else onDenied()
    }
    return { action ->
        if (LocalNetworkAccess.isGranted(context)) action()
        else if (pending != null) {
            // A prompt is already on screen: say so instead of dropping the
            // request silently, so the caller can show its own feedback.
            onDenied()
        } else {
            pending = action
            launcher.launch(LocalNetworkAccess.PERMISSION)
        }
    }
}

@Composable
fun LocalNetworkPermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(LocalNetworkAccess.isGranted(context)) }
    val request = rememberLocalNetworkPermissionRequest(onDenied = { granted = false })
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = LocalNetworkAccess.isGranted(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    if (granted) content()
    else Column(
        Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.local_network_permission_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.local_network_permission_desc))
        Button(onClick = { request { granted = true } }) {
            Text(stringResource(R.string.grant_permission))
        }
        TextButton(onClick = {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")))
        }) { Text(stringResource(R.string.local_network_open_settings)) }
    }
}
