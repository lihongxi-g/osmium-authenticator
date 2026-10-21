package com.safekey.authenticator.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.safekey.authenticator.R
import com.safekey.authenticator.notify.BackupNotifier
import com.safekey.authenticator.ui.components.AppIcons
import com.safekey.authenticator.ui.components.SettingRow
import com.safekey.authenticator.ui.components.SimpleTopBar

/**
 * Every permission and special access Osmium can ask for, in one place.
 *
 * The list is rebuilt on every resume (returning from a system dialog has to
 * update the rows). A granted row turns green and stops being clickable; an
 * ungranted one asks. Rows this OS version does not need say so instead of
 * pretending to be deniable — a permanently "denied" entry in a list like this
 * would be a lie.
 */
@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh++ }

    fun requestBatteryExemption() {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(direct)
        } catch (_: Exception) {
            runCatching { context.startActivity(fallback) }
        }
    }

    val items = remember(refresh) {
        buildItems(
            context = context,
            onRequest = { permission -> launcher.launch(permission) },
            onRequestNotificationsSystem = { BackupNotifier.openNotificationSettings(context) },
            onRequestBattery = { requestBatteryExemption() }
        )
    }

    Scaffold(
        topBar = {
            SimpleTopBar(title = stringResource(R.string.permissions_title), onBack = onBack)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.permissions_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            )

            items.forEach { item ->
                val statusText = when (item.state) {
                    PermissionState.GRANTED -> stringResource(R.string.permission_granted)
                    PermissionState.NOT_NEEDED -> stringResource(R.string.permission_not_needed)
                    PermissionState.DENIED -> stringResource(R.string.permission_denied)
                }
                val statusColor = when (item.state) {
                    PermissionState.GRANTED -> MaterialTheme.colorScheme.tertiary
                    PermissionState.NOT_NEEDED -> MaterialTheme.colorScheme.onSurfaceVariant
                    PermissionState.DENIED -> MaterialTheme.colorScheme.error
                }
                SettingRow(
                    icon = item.icon,
                    title = stringResource(item.titleRes),
                    description = stringResource(item.descRes),
                    trailing = {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelLarge,
                            color = statusColor
                        )
                    },
                    onClick = if (item.state == PermissionState.DENIED) item.request else null
                )
            }
        }
    }
}

private enum class PermissionState { GRANTED, DENIED, NOT_NEEDED }

private data class PermissionItem(
    val titleRes: Int,
    val descRes: Int,
    val icon: ImageVector,
    val state: PermissionState,
    val request: (() -> Unit)? = null
)

private fun granted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

/** The live list; OS-version gating stays plain Kotlin, readable at a glance. */
private fun buildItems(
    context: Context,
    onRequest: (String) -> Unit,
    onRequestNotificationsSystem: () -> Unit,
    onRequestBattery: () -> Unit
): List<PermissionItem> {
    val items = mutableListOf<PermissionItem>()

    // Camera — QR scanning to add accounts.
    val cameraGranted = granted(context, Manifest.permission.CAMERA)
    items += PermissionItem(
        titleRes = R.string.permission_camera_title,
        descRes = R.string.permission_camera_desc,
        icon = AppIcons.Camera,
        state = if (cameraGranted) PermissionState.GRANTED else PermissionState.DENIED,
        request = { onRequest(Manifest.permission.CAMERA) }
    )

    // Notifications — backup result messages (runtime permission since API 33).
    val notifyGranted = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 ||
            granted(context, "android.permission.POST_NOTIFICATIONS"))
    items += PermissionItem(
        titleRes = R.string.permission_notifications_title,
        descRes = R.string.permission_notifications_desc,
        icon = AppIcons.Notifications,
        state = if (notifyGranted) PermissionState.GRANTED else PermissionState.DENIED,
        request = {
            if (Build.VERSION.SDK_INT >= 33 &&
                !granted(context, "android.permission.POST_NOTIFICATIONS")
            ) {
                onRequest("android.permission.POST_NOTIFICATIONS")
            } else {
                // Permission granted but notifications (or our channel) are off.
                onRequestNotificationsSystem()
            }
        }
    )

    // Biometrics — a normal permission, granted at install, not requestable.
    items += PermissionItem(
        titleRes = R.string.permission_biometric_title,
        descRes = R.string.permission_biometric_desc,
        icon = AppIcons.Fingerprint,
        state = if (granted(context, Manifest.permission.USE_BIOMETRIC)) PermissionState.GRANTED
        else PermissionState.NOT_NEEDED
    )

    // Local network — Android 17+ only (LAN transfer, local WebDAV server).
    val networkNeeded = Build.VERSION.SDK_INT >= 37
    items += PermissionItem(
        titleRes = R.string.permission_network_title,
        descRes = R.string.permission_network_desc,
        icon = AppIcons.Wifi,
        state = when {
            !networkNeeded -> PermissionState.NOT_NEEDED
            granted(context, "android.permission.ACCESS_LOCAL_NETWORK") -> PermissionState.GRANTED
            else -> PermissionState.DENIED
        },
        request = { onRequest("android.permission.ACCESS_LOCAL_NETWORK") }
    )

    // Storage — only Android 8/9 need the write permission for Download/Osmium.
    val storageNeeded = Build.VERSION.SDK_INT <= 28
    items += PermissionItem(
        titleRes = R.string.permission_storage_title,
        descRes = R.string.permission_storage_desc,
        icon = AppIcons.FileDownload,
        state = when {
            !storageNeeded -> PermissionState.NOT_NEEDED
            granted(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) -> PermissionState.GRANTED
            else -> PermissionState.DENIED
        },
        request = { onRequest(Manifest.permission.WRITE_EXTERNAL_STORAGE) }
    )

    // Battery optimisation exemption — special access with its own dialog.
    val powerManager = context.getSystemService(PowerManager::class.java)
    val ignoringBattery = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    items += PermissionItem(
        titleRes = R.string.permission_battery_title,
        descRes = R.string.permission_battery_desc,
        icon = AppIcons.Battery,
        state = if (ignoringBattery) PermissionState.GRANTED else PermissionState.DENIED,
        request = { onRequestBattery() }
    )

    return items
}
