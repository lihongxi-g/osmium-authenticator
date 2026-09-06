package com.safekey.authenticator.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.safekey.authenticator.MainViewModel
import com.safekey.authenticator.R
import com.safekey.authenticator.ui.components.AppIcons
import com.safekey.authenticator.ui.components.SettingRow
import com.safekey.authenticator.ui.components.SimpleTopBar
import com.safekey.authenticator.ui.navigation.Screen

/**
 * Migration source picker (second-level page): every supported third-party
 * authenticator is one row; tapping a row opens that app's import flow.
 * Google Authenticator migrates via QR codes, the others via export files.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThirdPartyImportScreen(
    vm: MainViewModel,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = { SimpleTopBar(title = stringResource(R.string.thirdparty_import_title), onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.thirdparty_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )

            SettingRow(
                icon = AppIcons.QrCodeScanner,
                title = "Google Authenticator",
                description = stringResource(R.string.migration_desc),
                onClick = { vm.nav.push(Screen.GoogleImport) },
                trailing = { RowChevron() }
            )
            SettingRow(
                icon = AppIcons.FileDownload,
                title = "Aegis",
                description = stringResource(R.string.thirdparty_row_aegis_desc),
                onClick = { vm.nav.push(Screen.FileImport) },
                trailing = { RowChevron() }
            )
            SettingRow(
                icon = AppIcons.FileDownload,
                title = "2FAS",
                description = stringResource(R.string.thirdparty_row_2fas_desc),
                onClick = { vm.nav.push(Screen.FileImport) },
                trailing = { RowChevron() }
            )
            SettingRow(
                icon = AppIcons.FileDownload,
                title = "Raivo OTP",
                description = stringResource(R.string.thirdparty_row_raivo_desc),
                onClick = { vm.nav.push(Screen.FileImport) },
                trailing = { RowChevron() }
            )

            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.thirdparty_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Forward chevron for rows that navigate deeper. */
@Composable
private fun RowChevron() {
    Icon(
        imageVector = AppIcons.ArrowBack,
        contentDescription = null,
        modifier = Modifier.rotate(180f),
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
