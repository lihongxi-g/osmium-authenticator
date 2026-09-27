package com.safekey.authenticator.ui.screens

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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.safekey.authenticator.MainViewModel
import com.safekey.authenticator.R
import com.safekey.authenticator.legal.LegalDoc
import com.safekey.authenticator.legal.LegalDocState
import com.safekey.authenticator.legal.LegalDocsRepository
import com.safekey.authenticator.legal.LegalLang
import com.safekey.authenticator.security.ClipboardHelper
import com.safekey.authenticator.ui.components.SimpleTopBar
import com.safekey.authenticator.ui.theme.LocalExpressiveDesign
import kotlinx.coroutines.launch

/**
 * One donation address: the asset and network it belongs to, plus the address itself, which copies on
 * a tap. Nothing here is stored or sent anywhere — the addresses are constants, and the whole page
 * works offline apart from the optional full-terms fetch below.
 */
private data class DonationAddress(val asset: String, val network: String, val address: String)

private val BYBIT_ADDRESSES = listOf(
    DonationAddress("USDT", "Tron", "THutAdwtE1mA4VbhTwfE4gzCtWeQbhTUnf"),
    DonationAddress("USDT", "Ethereum", "0x2d78107b4b9e113dc71bc5f9264719c29b34e7f6"),
    DonationAddress("USDT", "Arbitrum One", "0x2d78107b4b9e113dc71bc5f9264719c29b34e7f6"),
    DonationAddress("BTC", "Bitcoin", "1CqSPJZ4GpLXZuMsxxLU9u7yERucnR5K1T"),
    DonationAddress("ETH", "ERC20", "0x2d78107b4b9e113dc71bc5f9264719c29b34e7f6"),
)

private val OKX_ADDRESSES = listOf(
    DonationAddress("USDT", "Tron", "TH186AydEHkvm4MCF61ibvztcHBGKC3Jsr"),
    DonationAddress("USDT", "Ethereum", "0x61f5006256323c3c82a82ec41cfd30098ad93f67"),
    DonationAddress("USDT", "Arbitrum One", "0x61f5006256323c3c82a82ec41cfd30098ad93f67"),
    DonationAddress("BTC", "Bitcoin", "bc1qwjyxsfn36gny8je8mn7zrtsl5hyfntla605rtguwgfsy7xc2m4aqvl5l6t"),
    DonationAddress("ETH", "Ethereum", "0x61f5006256323c3c82a82ec41cfd30098ad93f67"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonateScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    // The address most recently copied, so its row can confirm in place instead of pushing the
    // confirmation somewhere the user has to look for it.
    var copiedAddress by remember { mutableStateOf<String?>(null) }
    var showTerms by remember { mutableStateOf(false) }
    val legalStates by LegalDocsRepository.states.collectAsState()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { SimpleTopBar(title = stringResource(R.string.donate_title), onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.donate_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            DonationGroup(
                title = "Bybit",
                addresses = BYBIT_ADDRESSES,
                copiedAddress = copiedAddress,
                onCopy = { address ->
                    ClipboardHelper.copy(context, address)
                    copiedAddress = address
                    vm.showToast(context.getString(R.string.donate_copied))
                }
            )
            DonationGroup(
                title = "OKX",
                addresses = OKX_ADDRESSES,
                copiedAddress = copiedAddress,
                onCopy = { address ->
                    ClipboardHelper.copy(context, address)
                    copiedAddress = address
                    vm.showToast(context.getString(R.string.donate_copied))
                }
            )

            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.donate_notice_short),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showTerms = true },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text = stringResource(R.string.donate_agreement_row),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp, horizontal = 4.dp),
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showTerms) {
        LegalDialog(
            title = stringResource(R.string.donate_notice_title),
            state = legalStates[LegalDoc.DONATION] ?: LegalDocState.Loading,
            onOpenWebsite = {
                val code = LegalLang.currentSiteCode(context)
                val url = LegalDocsRepository.pageUrl(LegalDoc.DONATION, code)
                runCatching {
                    context.startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(url)
                        )
                    )
                }
                vm.showToast(url)
            },
            onRetry = { scope.launch { LegalDocsRepository.refreshNow(context.applicationContext) } },
            onDismiss = { showTerms = false }
        )
    }
}

@Composable
private fun DonationGroup(
    title: String,
    addresses: List<DonationAddress>,
    copiedAddress: String?,
    onCopy: (String) -> Unit
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)
    )
    addresses.forEach { entry ->
        DonationRow(
            entry = entry,
            copied = copiedAddress == entry.address,
            onCopy = onCopy
        )
    }
}

@Composable
private fun DonationRow(
    entry: DonationAddress,
    copied: Boolean,
    onCopy: (String) -> Unit
) {
    val expressive = LocalExpressiveDesign.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onCopy(entry.address) },
        colors = CardDefaults.cardColors(
            containerColor = if (expressive) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.asset,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "  ·  " + entry.network,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(
                        if (copied) R.string.donate_copied else R.string.donate_tap_to_copy
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (copied) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = entry.address,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
