package com.safekey.authenticator.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.safekey.authenticator.MainViewModel
import com.safekey.authenticator.R
import com.safekey.authenticator.data.AppSettings
import com.safekey.authenticator.ui.components.SimpleTopBar

/** Secondary settings page: account sort mode. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortOrderScreen(
    vm: MainViewModel,
    onBack: () -> Unit
) {
    val settings by vm.settings.collectAsState()

    Scaffold(
        topBar = { SimpleTopBar(title = stringResource(R.string.sort_mode), onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp)
        ) {
            SortOption(
                title = stringResource(R.string.sort_random),
                description = stringResource(R.string.sort_random_desc),
                selected = settings.sortMode == AppSettings.SORT_RANDOM,
                onClick = { vm.setSortMode(AppSettings.SORT_RANDOM) }
            )
            SortOption(
                title = stringResource(R.string.sort_alpha),
                description = stringResource(R.string.sort_alpha_desc),
                selected = settings.sortMode == AppSettings.SORT_ALPHA,
                onClick = { vm.setSortMode(AppSettings.SORT_ALPHA) }
            )
            SortOption(
                title = stringResource(R.string.sort_added),
                description = stringResource(R.string.sort_added_desc),
                selected = settings.sortMode == AppSettings.SORT_ADDED,
                onClick = { vm.setSortMode(AppSettings.SORT_ADDED) }
            )
            SortOption(
                title = stringResource(R.string.sort_copies),
                description = stringResource(R.string.sort_copies_desc),
                selected = settings.sortMode == AppSettings.SORT_COPIES,
                onClick = { vm.setSortMode(AppSettings.SORT_COPIES) }
            )
        }
    }
}

@Composable
private fun SortOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        RadioButton(selected = selected, onClick = onClick)
    }
}
