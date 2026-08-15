package com.safekey.authenticator.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.safekey.authenticator.R
import com.safekey.authenticator.ui.components.SectionHeader
import com.safekey.authenticator.ui.components.SimpleTopBar

/** In-app manual: feature guide and important notes, bilingual. */
@Composable
fun ManualScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            SimpleTopBar(
                title = stringResource(R.string.manual_title),
                onBack = onBack
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
            ManualSection(R.string.manual_add_title, R.string.manual_add_body)
            ManualSection(R.string.manual_codes_title, R.string.manual_codes_body)
            ManualSection(R.string.manual_sort_title, R.string.manual_sort_body)
            ManualSection(R.string.manual_hidecodes_title, R.string.manual_hidecodes_body)
            ManualSection(R.string.manual_steam_title, R.string.manual_steam_body)
            ManualSection(R.string.manual_hotp_title, R.string.manual_hotp_body)
            ManualSection(R.string.manual_security_title, R.string.manual_security_body)
            ManualSection(R.string.manual_backup_title, R.string.manual_backup_body)
            ManualSection(R.string.manual_notes_title, R.string.manual_notes_body)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ManualSection(titleRes: Int, bodyRes: Int) {
    Spacer(Modifier.height(20.dp))
    SectionHeader(stringResource(titleRes))
    Spacer(Modifier.height(6.dp))
    Text(
        text = stringResource(bodyRes),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
