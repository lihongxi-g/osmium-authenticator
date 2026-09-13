package com.safekey.authenticator.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.safekey.authenticator.MainViewModel
import com.safekey.authenticator.R
import com.safekey.authenticator.security.KeystoreTools
import com.safekey.authenticator.security.RootState
import com.safekey.authenticator.ui.components.AppIcons
import com.safekey.authenticator.ui.components.SectionHeader
import com.safekey.authenticator.ui.components.SettingRow
import com.safekey.authenticator.ui.components.SimpleTopBar
import com.safekey.authenticator.ui.components.integrityCheckTitle
import com.safekey.authenticator.ui.components.integrityStatusColor
import com.safekey.authenticator.ui.dev.DevStrings
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Developer-mode panel — English / Simplified Chinese only (see [DevStrings]).
 *
 * Entering requires identity verification from Settings (every time), and
 * every risky action runs the full ceremony: identity verification → risk
 * disclaimer → typed confirmation phrase.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onRequireBiometric: ((onSuccess: () -> Unit) -> Unit)? = null,
    onRequireCredential: ((onSuccess: () -> Unit) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by vm.settings.collectAsState()
    val rootReport by vm.rootReport.collectAsState()
    val dev = DevStrings.forContext(context)

    var scanning by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }
    var showKeystore by remember { mutableStateOf(false) }
    var keystoreStatus by remember { mutableStateOf<KeystoreTools.KeystoreStatus?>(null) }
    var keystoreLoading by remember { mutableStateOf(false) }
    var showHideList by remember { mutableStateOf(false) }
    var reencrypting by remember { mutableStateOf(false) }

    // Ceremony state machine: null = closed, otherwise the target action.
    var ceremony by remember { mutableStateOf<String?>(null) }
    var ceremonyStage by remember { mutableStateOf(0) } // 0 auth, 1 disclaimer, 2 phrase
    var disclaimerAccepted by remember { mutableStateOf(false) }
    var phraseInput by remember { mutableStateOf("") }
    var phraseError by remember { mutableStateOf(false) }
    var showPinEntry by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf<String?>(null) }

    fun ceremonyPhrase(): String = when (ceremony) {
        "disable" -> dev.phraseDisableRootSecurity
        "plaintext" -> dev.phrasePlaintextExport
        "reencrypt" -> dev.phraseReencrypt
        else -> ""
    }

    fun resetCeremony() {
        ceremony = null
        ceremonyStage = 0
        disclaimerAccepted = false
        phraseInput = ""
        phraseError = false
    }

    fun runReencrypt() {
        reencrypting = true
        scope.launch {
            val result = KeystoreTools.reencryptAll(context)
            reencrypting = false
            vm.showToast(
                if (result.ok) {
                    dev.reencryptDonePrefix + result.fields
                } else {
                    dev.reencryptFailed + (result.error?.let { " ($it)" } ?: "")
                }
            )
        }
    }

    fun finishCeremony() {
        when (ceremony) {
            "disable" -> vm.setDevDisableRootSecurity(true)
            "plaintext" -> vm.setDevPlaintextExport(true)
            "reencrypt" -> runReencrypt()
            "exit" -> {
                vm.disableDeveloperMode()
                vm.showToast(dev.devDisabled)
                resetCeremony()
                onBack()
                return
            }
        }
        resetCeremony()
    }

    fun afterAuth() {
        if (ceremony == "exit") finishCeremony() else ceremonyStage = 1
    }

    fun openCeremony(target: String) {
        ceremony = target
        ceremonyStage = 0
        disclaimerAccepted = false
        phraseInput = ""
        phraseError = false
    }

    Scaffold(
        topBar = { SimpleTopBar(title = dev.sectionTitle, onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            val report = rootReport

            SectionHeader(dev.rootStatusLabel)
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = when {
                        report == null -> dev.rootStatusUnknown
                        report.compromised -> dev.rootStatusDetected
                        else -> dev.rootStatusClean
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (report?.compromised == true) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                if (report != null) {
                    Text(
                        text = dev.lastChecked.replace(
                            "%s",
                            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                                .format(Date(report.checkedAt))
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (report?.compromised == true) {
                    Text(
                        text = if (settings.devDisableRootSecurity) {
                            dev.restrictionLifted
                        } else {
                            dev.restrictionActive
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (settings.devDisableRootSecurity) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            SettingRow(
                icon = AppIcons.Search,
                title = dev.runRootCheck,
                description = if (scanning) dev.scanning else dev.runRootCheckDesc,
                onClick = {
                    if (!scanning) {
                        scanning = true
                        scope.launch {
                            RootState.refresh(context, force = true)
                            scanning = false
                            showReport = true
                        }
                    }
                }
            )

            SettingRow(
                icon = AppIcons.VpnKey,
                title = dev.keystoreStatus,
                description = if (keystoreLoading) dev.scanning else dev.keystoreStatusDesc,
                onClick = {
                    showKeystore = true
                    keystoreLoading = true
                    keystoreStatus = null
                    scope.launch {
                        keystoreStatus = KeystoreTools.status(context)
                        keystoreLoading = false
                    }
                }
            )

            SettingRow(
                icon = AppIcons.Warning,
                title = dev.disableRootSecurity,
                description = dev.disableRootSecurityDesc,
                trailing = { DevStateText(settings.devDisableRootSecurity, dev) },
                onClick = {
                    if (settings.devDisableRootSecurity) {
                        // Turning the restrictions back ON needs no ceremony.
                        vm.setDevDisableRootSecurity(false)
                    } else {
                        openCeremony("disable")
                    }
                }
            )

            SettingRow(
                icon = AppIcons.FileUpload,
                title = dev.plaintextExport,
                description = dev.plaintextExportDesc,
                trailing = { DevStateText(settings.devPlaintextExport, dev) },
                onClick = {
                    if (settings.devPlaintextExport) {
                        vm.setDevPlaintextExport(false)
                    } else {
                        openCeremony("plaintext")
                    }
                }
            )

            SettingRow(
                icon = AppIcons.Refresh,
                title = dev.reencrypt,
                description = if (reencrypting) dev.reencryptRunning else dev.reencryptDesc,
                onClick = { if (!reencrypting) openCeremony("reencrypt") }
            )

            SettingRow(
                icon = AppIcons.Visibility,
                title = dev.hideFeatures,
                description = dev.hideFeaturesDesc,
                onClick = { showHideList = true }
            )

            SettingRow(
                icon = AppIcons.Keyboard,
                title = dev.extraDigits,
                description = dev.extraDigitsDesc,
                trailing = { DevStateText(settings.devExtraDigits, dev) },
                onClick = { vm.setDevExtraDigits(!settings.devExtraDigits) }
            )

            SettingRow(
                icon = AppIcons.Info,
                title = dev.detailedLogging,
                description = dev.detailedLoggingDesc,
                trailing = { DevStateText(settings.devDetailedLogging, dev) },
                onClick = { vm.setDevDetailedLogging(!settings.devDetailedLogging) }
            )

            Spacer(Modifier.height(24.dp))

            SettingRow(
                icon = AppIcons.ArrowBack,
                title = dev.exitDevMode,
                description = dev.exitDevModeDesc,
                onClick = { openCeremony("exit") }
            )

            Spacer(Modifier.height(24.dp))
        }
    }

    // ------------------------------------------------------------ report dialog

    if (showReport) {
        AlertDialog(
            onDismissRequest = { showReport = false },
            title = { Text(dev.reportTitle) },
            text = {
                val report = rootReport
                if (report == null) {
                    Text(dev.reportEmpty)
                } else {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = dev.reportHint,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        report.checks.forEach { s ->
                            Row(modifier = Modifier.padding(vertical = 3.dp)) {
                                Text(
                                    text = if (s.hit) dev.hitLabel else dev.missLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = integrityStatusColor(s.hit, s.severity),
                                    modifier = Modifier.width(64.dp)
                                )
                                Column {
                                    // A quiet check reads PASS — the severity
                                    // classes are firing weights, not verdicts
                                    // for checks that did not fire.
                                    val status = if (s.hit) s.severity.name else "PASS"
                                    Text(
                                        text = "${integrityCheckTitle(s.id)}  [$status]",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = integrityStatusColor(s.hit, s.severity)
                                    )
                                    if (s.detail.isNotBlank()) {
                                        Text(
                                            text = s.detail,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        val report = rootReport
                        if (report != null) {
                            val text = buildString {
                                appendLine("Osmium root report")
                                appendLine("checkedAt=${report.checkedAt}")
                                report.checks.forEach { s ->
                                    val status = if (s.hit) s.severity.name else "PASS"
                                    appendLine(
                                        "${s.id} $status" +
                                            if (s.detail.isNotBlank()) " detail=${s.detail}" else ""
                                    )
                                }
                            }
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Osmium root report", text))
                            vm.showToast(dev.copied)
                        }
                    }) { Text(dev.copy) }
                    TextButton(onClick = { showReport = false }) { Text(dev.close) }
                }
            }
        )
    }

    // ---------------------------------------------------------- keystore dialog

    if (showKeystore) {
        AlertDialog(
            onDismissRequest = { showKeystore = false },
            title = { Text(dev.keystoreStatus) },
            text = {
                val ks = keystoreStatus
                if (ks == null) {
                    Text(dev.scanning)
                } else {
                    Column {
                        KvRow(dev.ksAlias, ks.alias)
                        if (ks.present) {
                            val level = buildString {
                                append(ks.securityLevel ?: "—")
                                ks.keySize?.let {
                                    append(" · ")
                                    append(it)
                                    append(" bit")
                                }
                            }
                            KvRow(dev.ksSecurityLevel, level)
                        } else {
                            KvRow(dev.ksPresent, dev.ksMissing)
                        }
                        KvRow(dev.ksSelfTest, if (ks.roundtripOk == true) dev.ksOk else dev.ksFail)
                        KvRow(
                            dev.ksDbFields,
                            "${ks.rowsOk}/${ks.accountCount} · " +
                                if (ks.rowsOk == ks.accountCount) dev.ksAllEncrypted else dev.ksIssuesFound
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showKeystore = false }) { Text(dev.close) }
            }
        )
    }

    // ------------------------------------------------------- hide entries dialog

    if (showHideList) {
        val current = settings.devHiddenFeatures
        var checked by remember { mutableStateOf(current) }
        AlertDialog(
            onDismissRequest = { showHideList = false },
            title = { Text(dev.hideFeatures) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = dev.hideHint,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    HIDEABLE_ITEMS.forEach { (id, labelRes) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    checked = if (id in checked) checked - id else checked + id
                                }
                        ) {
                            Checkbox(
                                checked = id in checked,
                                onCheckedChange = { on ->
                                    checked = if (on) checked + id else checked - id
                                }
                            )
                            Text(text = stringResource(labelRes))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setDevHiddenFeatures(checked)
                    vm.showToast(dev.hideSaved)
                    showHideList = false
                }) { Text(dev.confirm) }
            },
            dismissButton = {
                TextButton(onClick = { showHideList = false }) { Text(dev.cancel) }
            }
        )
    }

    // ---------------------------------------------------------------- ceremony

    val target = ceremony
    if (target != null && !showPinEntry) {
        when (ceremonyStage) {
            0 -> AlertDialog(
                onDismissRequest = { resetCeremony() },
                title = { Text(dev.verifyTitle) },
                text = {
                    Column {
                        if (onRequireBiometric != null) {
                            DevOptionRow(AppIcons.Fingerprint, stringResource(R.string.verify_biometric)) {
                                onRequireBiometric { afterAuth() }
                            }
                        }
                        if (onRequireCredential != null) {
                            DevOptionRow(AppIcons.Security, stringResource(R.string.verify_credential)) {
                                onRequireCredential { afterAuth() }
                            }
                        }
                        if (vm.hasLocalPin()) {
                            DevOptionRow(AppIcons.Keyboard, stringResource(R.string.verify_pin)) {
                                pinError = null
                                showPinEntry = true
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { resetCeremony() }) { Text(dev.cancel) }
                }
            )

            1 -> AlertDialog(
                onDismissRequest = { resetCeremony() },
                title = { Text(dev.disclaimerTitle) },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(text = dev.disclaimerBody, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { disclaimerAccepted = !disclaimerAccepted }
                        ) {
                            Checkbox(
                                checked = disclaimerAccepted,
                                onCheckedChange = { disclaimerAccepted = it }
                            )
                            Text(text = dev.disclaimerAccept, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = disclaimerAccepted,
                        onClick = { ceremonyStage = 2 }
                    ) { Text(dev.continueLabel) }
                },
                dismissButton = {
                    TextButton(onClick = { resetCeremony() }) { Text(dev.cancel) }
                }
            )

            2 -> {
                val phrase = ceremonyPhrase()
                AlertDialog(
                    onDismissRequest = { resetCeremony() },
                    title = { Text(dev.phraseTitle) },
                    text = {
                        Column {
                            Text(
                                text = dev.phraseInstruction,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(text = phrase, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = phraseInput,
                                onValueChange = {
                                    phraseInput = it
                                    phraseError = false
                                },
                                singleLine = true,
                                isError = phraseError,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (phraseError) {
                                Text(
                                    text = dev.phraseMismatch,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            if (phraseInput.trim() == phrase) finishCeremony() else phraseError = true
                        }) { Text(dev.enable) }
                    },
                    dismissButton = {
                        TextButton(onClick = { resetCeremony() }) { Text(dev.cancel) }
                    }
                )
            }
        }
    }

    if (showPinEntry) {
        PinVerifyScreen(
            title = stringResource(R.string.pin_verify_title),
            subtitle = stringResource(R.string.pin_verify_subtitle),
            error = pinError,
            remainingAttempts = vm.remainingAttempts(),
            onVerify = { pin ->
                if (vm.onPinEntered(pin)) {
                    showPinEntry = false
                    afterAuth()
                } else {
                    pinError = context.getString(R.string.pin_wrong)
                    vm.checkSelfDestructPin(pin)
                }
            },
            onCancel = { showPinEntry = false }
        )
    }
}

/** Rows the "hide settings entries" tool can hide (id → label resource). */
private val HIDEABLE_ITEMS: List<Pair<String, Int>> = listOf(
    "theme" to R.string.theme_mode,
    "dynamicColor" to R.string.dynamic_color,
    "sort" to R.string.sort_mode,
    "tags" to R.string.tags_title,
    "language" to R.string.language,
    "gate" to R.string.gate_on_open,
    "screenshots" to R.string.allow_screenshots,
    "hideCodes" to R.string.hide_codes,
    "timeOffset" to R.string.time_offset,
    "integrity" to R.string.integrity_entry_title,
    "pin" to R.string.settings_pin,
    "destroy" to R.string.settings_destroy,
    "thirdparty" to R.string.thirdparty_import_title,
    "export" to R.string.export_vault,
    "import" to R.string.import_vault,
    "lan" to R.string.lan_transfer_title,
    "webdav" to R.string.webdav_title,
    "autobackup" to R.string.auto_backup_title,
    "log" to R.string.export_log,
    "manual" to R.string.manual_title,
    "updates" to R.string.update_check_label
)

@Composable
private fun DevStateText(on: Boolean, dev: DevStrings) {
    Text(
        text = if (on) dev.on else dev.off,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun DevOptionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
private fun KvRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(150.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}
