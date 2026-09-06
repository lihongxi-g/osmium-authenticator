package com.safekey.authenticator.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.safekey.authenticator.MainViewModel
import com.safekey.authenticator.R
import com.safekey.authenticator.ui.components.AppIcons
import com.safekey.authenticator.ui.components.SimpleTopBar

/**
 * Attributions page: documents the open-source projects and public format
 * references behind the third-party import feature (license compliance is
 * visible in-app), with links that open in the system browser.
 *
 * Osmium reuses no code from these projects — the parsers are original Kotlin
 * written against the publicly documented export formats. Real export samples
 * taken from the Aegis test suite (GPL-3.0, same license family as Osmium)
 * are used as unit-test fixtures; this is stated in the repository too.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttributionsScreen(
    vm: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val open: (String) -> Unit = { url ->
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            vm.showToast(context.getString(R.string.no_browser))
        }
    }

    Scaffold(
        topBar = { SimpleTopBar(title = stringResource(R.string.attributions_title), onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.attributions_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            AttributionCard(
                title = "Aegis Authenticator (GPL-3.0)",
                description = stringResource(R.string.attributions_aegis_desc),
                onClick = { open("https://github.com/beemdevelopment/Aegis") }
            )
            AttributionCard(
                title = "2FAS (twofas/2fas-android)",
                description = stringResource(R.string.attributions_2fas_desc),
                onClick = { open("https://github.com/twofas/2fas-android") }
            )
            AttributionCard(
                title = "OtpTranslate (tygertec.com)",
                description = stringResource(R.string.attributions_raivo_desc),
                onClick = { open("https://tygertec.com/aegis-raivo-otp-translator") }
            )
            AttributionCard(
                title = "Osmium (GPL-3.0-or-later)",
                description = stringResource(R.string.attributions_osmium_desc),
                onClick = { open("https://github.com/lihongxi-g/osmium-authenticator") }
            )

            Text(
                text = stringResource(R.string.attributions_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp, bottom = 24.dp)
            )
        }
    }
}

@Composable
private fun AttributionCard(title: String, description: String, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = AppIcons.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.rotate(180f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
