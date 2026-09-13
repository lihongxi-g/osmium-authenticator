package com.safekey.authenticator.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.safekey.authenticator.BuildConfig
import com.safekey.authenticator.MainViewModel
import com.safekey.authenticator.R
import com.safekey.authenticator.legal.LegalDoc
import com.safekey.authenticator.legal.LegalDocState
import com.safekey.authenticator.legal.LegalDocsRepository
import com.safekey.authenticator.legal.LegalLang
import com.safekey.authenticator.security.AppLog
import com.safekey.authenticator.security.ClipboardHelper
import com.safekey.authenticator.ui.components.AppIcons
import com.safekey.authenticator.ui.components.SimpleTopBar
import com.safekey.authenticator.ui.dev.DevStrings
import com.safekey.authenticator.ui.navigation.Screen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onRequireBiometric: ((onSuccess: () -> Unit) -> Unit)? = null,
    onRequireCredential: ((onSuccess: () -> Unit) -> Unit)? = null
) {
    val context = LocalContext.current
    var showTerms by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    val legalStates by LegalDocsRepository.states.collectAsState()
    val scope = rememberCoroutineScope()
    val dev = DevStrings.forContext(context)
    val haptics = LocalHapticFeedback.current

    // Hidden entry: seven taps on the app name unlock developer mode.
    var devTapCount by remember { mutableStateOf(0) }
    var showDevAuthDialog by remember { mutableStateOf(false) }
    var showDevPinEntry by remember { mutableStateOf(false) }
    var devPinError by remember { mutableStateOf<String?>(null) }

    fun enableDeveloperMode() {
        vm.setDevModeEnabled(true)
        vm.showToast(dev.devEnabled)
    }

    Scaffold(
        topBar = { SimpleTopBar(title = stringResource(R.string.settings_about), onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Osmium",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    devTapCount++
                    if (devTapCount >= 3) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    if (devTapCount >= 7) {
                        devTapCount = 0
                        showDevAuthDialog = true
                    }
                }
            )
            Text(
                text = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))

            // check updates → opens the GitHub Releases page in the browser.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/lihongxi-g/osmium-authenticator/releases")
                        )
                        try {
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            vm.showToast(context.getString(R.string.no_browser))
                        }
                    }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.check_updates),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = AppIcons.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))
            AboutLinkRow(stringResource(R.string.about_terms_title)) { showTerms = true }
            AboutLinkRow(stringResource(R.string.about_privacy_title)) { showPrivacy = true }
            AboutLinkRow(stringResource(R.string.attributions_title)) { vm.nav.push(Screen.Attributions) }

            Spacer(Modifier.height(24.dp))
            Divider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.about_follow),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly
            ) {
                SocialIcon(AppIcons.Telegram, stringResource(R.string.social_telegram), "https://t.me/osmium2fa") { url ->
                    openUrl(context, url, vm)
                }
                SocialIcon(AppIcons.XLogo, stringResource(R.string.social_x), "https://x.com/lihongxi_l") { url ->
                    openUrl(context, url, vm)
                }
                SocialIcon(AppIcons.GitHub, stringResource(R.string.social_github), "https://github.com/lihongxi-g/osmium-authenticator") { url ->
                    openUrl(context, url, vm)
                }
                SocialIcon(AppIcons.Mail, stringResource(R.string.social_mail), "") {
                    sendFeedbackEmail(context, vm)
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.about_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showTerms) {
        LegalDialog(
            title = stringResource(R.string.about_terms_title),
            state = legalStates[LegalDoc.TERMS] ?: LegalDocState.Loading,
            onOpenWebsite = {
                openUrl(
                    context,
                    LegalDocsRepository.pageUrl(LegalDoc.TERMS, LegalLang.currentSiteCode(context)),
                    vm
                )
            },
            onRetry = { scope.launch { LegalDocsRepository.refreshNow(context.applicationContext) } },
            onDismiss = { showTerms = false }
        )
    }
    if (showPrivacy) {
        LegalDialog(
            title = stringResource(R.string.about_privacy_title),
            state = legalStates[LegalDoc.PRIVACY] ?: LegalDocState.Loading,
            onOpenWebsite = {
                openUrl(
                    context,
                    LegalDocsRepository.pageUrl(LegalDoc.PRIVACY, LegalLang.currentSiteCode(context)),
                    vm
                )
            },
            onRetry = { scope.launch { LegalDocsRepository.refreshNow(context.applicationContext) } },
            onDismiss = { showPrivacy = false }
        )
    }

    // ---- hidden developer-mode entry (seven taps on the app name) ----

    if (showDevAuthDialog) {
        AlertDialog(
            onDismissRequest = { showDevAuthDialog = false },
            title = { Text(dev.verifyTitle) },
            text = {
                Column {
                    if (onRequireBiometric != null) {
                        DevVerifyOptionRow(AppIcons.Fingerprint, stringResource(R.string.verify_biometric)) {
                            showDevAuthDialog = false
                            onRequireBiometric { enableDeveloperMode() }
                        }
                    }
                    if (onRequireCredential != null) {
                        DevVerifyOptionRow(AppIcons.Security, stringResource(R.string.verify_credential)) {
                            showDevAuthDialog = false
                            onRequireCredential { enableDeveloperMode() }
                        }
                    }
                    if (vm.hasLocalPin()) {
                        DevVerifyOptionRow(AppIcons.Keyboard, stringResource(R.string.verify_pin)) {
                            showDevAuthDialog = false
                            devPinError = null
                            showDevPinEntry = true
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDevAuthDialog = false }) {
                    Text(dev.cancel)
                }
            }
        )
    }

    if (showDevPinEntry) {
        PinVerifyScreen(
            title = stringResource(R.string.pin_verify_title),
            subtitle = stringResource(R.string.pin_verify_subtitle),
            error = devPinError,
            remainingAttempts = vm.remainingAttempts(),
            onVerify = { pin ->
                if (vm.onPinEntered(pin)) {
                    showDevPinEntry = false
                    devPinError = null
                    enableDeveloperMode()
                } else {
                    devPinError = context.getString(R.string.pin_wrong)
                    vm.checkSelfDestructPin(pin)
                }
            },
            onCancel = {
                showDevPinEntry = false
                devPinError = null
            }
        )
    }
}

@Composable
private fun DevVerifyOptionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
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

/** A plain text row that opens the legal-document dialog. */
@Composable
private fun AboutLinkRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = AppIcons.ArrowBack,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Shows the fetched legal document. Three states: loading, the fetched
 * text, or — when the fetch failed — a "Request failed" notice with a
 * retry button and a link to the website.
 */
@Composable
private fun LegalDialog(
    title: String,
    state: LegalDocState,
    onOpenWebsite: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val failed = state is LegalDocState.Failed
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                when (state) {
                    LegalDocState.Loading -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.legal_loading),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    is LegalDocState.Loaded -> {
                        Text(
                            text = state.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    LegalDocState.Failed -> {
                        Text(
                            text = stringResource(R.string.legal_fetch_failed),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.legal_fetch_failed_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (failed) {
                Row {
                    TextButton(onClick = onRetry) {
                        Text(stringResource(R.string.legal_retry))
                    }
                    TextButton(onClick = onOpenWebsite) {
                        Text(stringResource(R.string.legal_open_website))
                    }
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.close))
                }
            }
        },
        dismissButton = if (failed) {
            {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.close))
                }
            }
        } else {
            null
        }
    )
}

private fun openUrl(context: android.content.Context, url: String, vm: MainViewModel) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        vm.showToast(context.getString(R.string.no_browser))
    }
}

/** Opens the mail app with the recent log pre-filled in the body, and also
 *  copies the FULL log to the clipboard as a fallback (mailto URI has a
 *  practical length limit, so only the tail goes into the body). */
private fun sendFeedbackEmail(context: android.content.Context, vm: MainViewModel) {
    val log = AppLog.exportText()
    val tail = log.lines().takeLast(40).joinToString("\n")
    val subject = Uri.encode("Osmium v${BuildConfig.VERSION_NAME} Feedback")
    val body = Uri.encode(tail)
    val uri = "mailto:zhif0776@hotmail.com?subject=$subject&body=$body"
    try {
        context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse(uri)))
        ClipboardHelper.copy(context, log)
        vm.showToast(context.getString(R.string.email_log_copied))
    } catch (_: Exception) {
        vm.showToast(context.getString(R.string.no_email_app))
    }
}

@Composable
private fun SocialIcon(icon: ImageVector, label: String, url: String, onOpen: (String) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onOpen(url) }
            .padding(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(36.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
