package com.safekey.authenticator.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.safekey.authenticator.BuildConfig
import com.safekey.authenticator.R
import com.safekey.authenticator.update.ReleaseNotes

/**
 * "New version available" dialog, shared by the silent startup check and the
 * on-demand check in About.
 *
 * Shows the version pair, then the release notes GitHub returned for that
 * release ([notes] is the raw Markdown body, cleaned by [ReleaseNotes]) in a
 * capped, scrollable block so a long changelog cannot push the buttons off
 * screen. Both entry points use this one dialog, so the changelog is worded
 * and formatted identically wherever it is opened from.
 */
@Composable
fun UpdateAvailableDialog(
    tag: String,
    notes: String,
    url: String,
    onOpenReleasePage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val cleaned = ReleaseNotes.clean(notes)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_available_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(
                        R.string.update_available_body, tag, BuildConfig.VERSION_NAME
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (cleaned.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.update_available_notes_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = cleaned,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onOpenReleasePage(url) }) {
                Text(stringResource(R.string.update_go_github))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.update_later))
            }
        }
    )
}
