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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.safekey.authenticator.MainViewModel
import com.safekey.authenticator.R
import com.safekey.authenticator.integrity.IntegrityCheck
import com.safekey.authenticator.integrity.IntegrityLevel
import com.safekey.authenticator.integrity.IntegrityReport
import com.safekey.authenticator.integrity.attestation.RevocationData
import com.safekey.authenticator.security.RootState
import com.safekey.authenticator.ui.components.SectionHeader
import com.safekey.authenticator.ui.components.SimpleTopBar
import com.safekey.authenticator.ui.components.integrityCheckTitle
import com.safekey.authenticator.ui.components.integrityLevelDesc
import com.safekey.authenticator.ui.components.integrityLevelLabel
import com.safekey.authenticator.ui.components.integrityStatusColor
import com.safekey.authenticator.ui.components.integrityStatusRes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Engine check ids that belong to the K2 consistency group. */
private val CONSISTENCY_IDS = setOf("mount_cross", "file_cross", "state_drift")

/**
 * "Android device integrity report" — second-level page under
 * Settings → Security ("entry card -> subpage"). Shows the overall level
 * card, the grouped K1 local checks and the K3 hardware-proof rows with
 * expandable raw values, the revocation-data status with a manual refresh
 * (offline-first; a failed refresh never blocks verification), and a
 * copy-to-clipboard action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntegrityScreen(
    vm: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val report by vm.rootReport.collectAsState()
    val scope = rememberCoroutineScope()
    var rescanning by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var refreshMessage by remember { mutableStateOf<String?>(null) }
    var refreshFailed by remember { mutableStateOf(false) }
    var dataDate by remember { mutableStateOf(RevocationData.currentDateLabel(context)) }
    var expandedIds by remember { mutableStateOf(emptySet<String>()) }

    Scaffold(
        topBar = {
            SimpleTopBar(
                title = stringResource(R.string.integrity_title),
                onBack = onBack,
                actions = {
                    TextButton(
                        enabled = !rescanning,
                        onClick = {
                            scope.launch {
                                rescanning = true
                                try {
                                    RootState.refresh(context, force = true)
                                } finally {
                                    rescanning = false
                                }
                            }
                        }
                    ) {
                        Text(
                            if (rescanning) stringResource(R.string.integrity_scanning)
                            else stringResource(R.string.integrity_rescan)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            LevelCard(report, rescanning)

            val current = report
            if (current == null) {
                Text(
                    text = stringResource(R.string.integrity_report_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp)
                )
            } else {
                val (hardware, rest) = current.checks.partition { it.id.startsWith("attestation") }
                val (consistency, local) = rest.partition { it.id in CONSISTENCY_IDS }

                SectionHeader(stringResource(R.string.integrity_section_local))
                CheckGroup(local, expandedIds) { id ->
                    expandedIds = if (id in expandedIds) expandedIds - id else expandedIds + id
                }

                if (consistency.isNotEmpty()) {
                    SectionHeader(stringResource(R.string.integrity_section_consistency))
                    CheckGroup(consistency, expandedIds) { id ->
                        expandedIds = if (id in expandedIds) expandedIds - id else expandedIds + id
                    }
                }

                SectionHeader(stringResource(R.string.integrity_section_attestation))
                CheckGroup(hardware, expandedIds) { id ->
                    expandedIds = if (id in expandedIds) expandedIds - id else expandedIds + id
                }

                RevocationCard(
                    dataDate = dataDate,
                    refreshing = refreshing,
                    message = refreshMessage,
                    failed = refreshFailed,
                    onRefresh = {
                        scope.launch {
                            refreshing = true
                            val result = withContext(Dispatchers.IO) { RevocationData.refresh(context) }
                            when (result) {
                                is RevocationData.RefreshResult.Updated -> {
                                    dataDate = RevocationData.currentDateLabel(context)
                                    refreshMessage = context.getString(
                                        R.string.integrity_refresh_ok, result.count
                                    )
                                    refreshFailed = false
                                }
                                is RevocationData.RefreshResult.Failed -> {
                                    val date = RevocationData.currentDateLabel(context)
                                        ?: context.getString(R.string.integrity_k3_data_builtin)
                                    refreshMessage = context.getString(
                                        R.string.integrity_refresh_failed, date
                                    )
                                    refreshFailed = true
                                }
                            }
                            refreshing = false
                        }
                    }
                )

                Spacer(Modifier.height(16.dp))
                CopyReportButton(report = current) { text ->
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Osmium integrity report", text))
                    vm.showToast(context.getString(R.string.integrity_copied))
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun LevelCard(report: IntegrityReport?, rescanning: Boolean) {
    val level = report?.level
    val container = when (level) {
        IntegrityLevel.COMPROMISED -> MaterialTheme.colorScheme.errorContainer
        IntegrityLevel.SUSPICIOUS -> MaterialTheme.colorScheme.tertiaryContainer
        IntegrityLevel.UNVERIFIED -> MaterialTheme.colorScheme.secondaryContainer
        IntegrityLevel.CLEAN -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val onContainer = when (level) {
        IntegrityLevel.COMPROMISED -> MaterialTheme.colorScheme.onErrorContainer
        IntegrityLevel.SUSPICIOUS -> MaterialTheme.colorScheme.onTertiaryContainer
        IntegrityLevel.UNVERIFIED -> MaterialTheme.colorScheme.onSecondaryContainer
        IntegrityLevel.CLEAN -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.integrity_final_rating),
                style = MaterialTheme.typography.labelMedium,
                color = onContainer
            )
            Text(
                text = integrityLevelLabel(level),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = onContainer
            )
            val descLevel = level
            if (descLevel != null) {
                Text(
                    text = integrityLevelDesc(descLevel),
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (report != null) {
                val checked = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    .format(Date(report.checkedAt))
                Text(
                    text = stringResource(R.string.integrity_checked_at, checked),
                    style = MaterialTheme.typography.labelSmall,
                    color = onContainer,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (rescanning) {
                Text(
                    text = stringResource(R.string.integrity_scanning),
                    style = MaterialTheme.typography.labelSmall,
                    color = onContainer,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun CheckGroup(
    checks: List<IntegrityCheck>,
    expandedIds: Set<String>,
    onToggle: (String) -> Unit
) {
    if (checks.isEmpty()) return
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            for (check in checks) {
                CheckRow(check, expanded = check.id in expandedIds) { onToggle(check.id) }
            }
        }
    }
}

@Composable
private fun CheckRow(
    check: IntegrityCheck,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val statusColor = integrityStatusColor(check.hit, check.severity)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = integrityCheckTitle(check.id),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(integrityStatusRes(check)),
                style = MaterialTheme.typography.labelLarge,
                color = statusColor
            )
        }
        if (expanded && check.detail.isNotBlank()) {
            Text(
                text = check.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun RevocationCard(
    dataDate: String?,
    refreshing: Boolean,
    message: String?,
    failed: Boolean,
    onRefresh: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.integrity_k3_data_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = dataDate?.let { stringResource(R.string.integrity_k3_data_date, it) }
                    ?: stringResource(R.string.integrity_k3_data_builtin),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
            TextButton(onClick = onRefresh, enabled = !refreshing) {
                Text(
                    if (refreshing) stringResource(R.string.integrity_refresh_running)
                    else stringResource(R.string.integrity_refresh_data)
                )
            }
            val msg = message
            if (msg != null) {
                Text(
                    text = msg,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
        }
    }
}

@Composable
private fun CopyReportButton(report: IntegrityReport, onCopy: (String) -> Unit) {
    val label = stringResource(R.string.integrity_copy_report)
    // Build the plain-text report; stringResource calls stay in the
    // composable body (click handlers cannot call composable functions).
    val text = StringBuilder()
    text.appendLine(stringResource(R.string.integrity_title))
    text.appendLine(stringResource(R.string.integrity_copy_level, integrityLevelLabel(report.level)))
    val checked = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        .format(Date(report.checkedAt))
    text.appendLine(stringResource(R.string.integrity_checked_at, checked))
    for (check in report.checks) {
        text.append("\u2022 ")
            .append(integrityCheckTitle(check.id))
            .append(": ")
            .append(stringResource(integrityStatusRes(check)))
        if (check.detail.isNotBlank()) {
            text.append(" \u2014 ").append(check.detail)
        }
        text.appendLine()
    }
    OutlinedButton(
        onClick = { onCopy(text.toString()) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label)
    }
}
