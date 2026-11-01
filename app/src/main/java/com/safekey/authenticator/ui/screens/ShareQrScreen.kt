package com.safekey.authenticator.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.safekey.authenticator.MainViewModel
import com.safekey.authenticator.R
import com.safekey.authenticator.totp.toOtpUri
import com.safekey.authenticator.ui.components.renderQrBitmap
import com.safekey.authenticator.ui.components.SimpleTopBar

/**
 * Shares ONE account as an otpauth:// QR code (no backup password needed —
 * the caller has already verified identity). One account at a time by design.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareQrScreen(
    vm: MainViewModel,
    accountId: String,
    onBack: () -> Unit
) {
    val uiList by vm.accountUiList.collectAsState()
    val ui = uiList.firstOrNull { it.account.id == accountId }

    Scaffold(
        topBar = { SimpleTopBar(title = stringResource(R.string.share_account), onBack = onBack) }
    ) { padding ->
        val account = ui?.account
        if (account == null) return@Scaffold
        val uri = remember(account.secret) { account.toOtpUri() }
        val qrBitmap = remember(uri) { renderQrBitmap(uri, 640) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = account.displayTitle,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = account.displaySubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            Image(
                bitmap = qrBitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.share_qr_desc),
                modifier = Modifier
                    .size(280.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .padding(12.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.share_qr_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
